package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.*;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.repository.AddressRepository;
import com.grocerychoice.backend.repository.OtpVerificationRepository;
import com.grocerychoice.backend.repository.UserRepository;
import com.grocerychoice.backend.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication and OTP Verification Service.
 * Manages OTP generation, BCrypt hashing, rate-limiting, customer auto-registration, and JWT issuance.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpDeliveryService otpDeliveryService;

    private final int otpExpiryMinutes;
    private final int maxAttempts;
    private final int resendCooldownSeconds;

    public AuthService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            OtpVerificationRepository otpVerificationRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            OtpDeliveryService otpDeliveryService,
            @Value("${otp.expiry-minutes:5}") int otpExpiryMinutes,
            @Value("${otp.max-attempts:5}") int maxAttempts,
            @Value("${otp.resend-cooldown-seconds:60}") int resendCooldownSeconds) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.otpVerificationRepository = otpVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.otpDeliveryService = otpDeliveryService;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    /**
     * Sends a 6-digit OTP to the customer's mobile number or email.
     */
    @Transactional
    public SendOtpResponse sendOtp(SendOtpRequest request) {
        String rawIdentifier = request.getIdentifier();
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number or email ID is required");
        }

        IdentifierInfo idInfo = parseIdentifier(rawIdentifier);
        String normalized = idInfo.normalized;
        OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : OtpPurpose.LOGIN;

        // Check resend cooldown
        Optional<OtpVerification> latestOpt = otpVerificationRepository
                .findTopByIdentifierAndPurposeOrderByCreatedAtDesc(normalized, purpose);

        if (latestOpt.isPresent()) {
            OtpVerification latest = latestOpt.get();
            LocalDateTime createdAt = latest.getCreatedAt();
            if (createdAt != null) {
                LocalDateTime cooldownUntil = createdAt.plusSeconds(resendCooldownSeconds);
                if (LocalDateTime.now().isBefore(cooldownUntil)) {
                    long remainingSeconds = Duration.between(LocalDateTime.now(), cooldownUntil).getSeconds();
                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Please wait " + Math.max(1, remainingSeconds) + " seconds before requesting another OTP"
                    );
                }
            }
        }

        // Generate 6-digit numeric OTP (100000 - 999999)
        int randomCode = 100000 + SECURE_RANDOM.nextInt(900000);
        String otpString = String.valueOf(randomCode);

        // Store only the secure BCrypt hash in MySQL
        String otpHash = passwordEncoder.encode(otpString);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpiryMinutes);

        OtpVerification verification = new OtpVerification(normalized, otpHash, purpose, expiresAt);
        otpVerificationRepository.save(verification);

        // Dispatch via clean delivery abstraction
        otpDeliveryService.deliverOtp(normalized, otpString, purpose);

        int totalExpirySeconds = otpExpiryMinutes * 60;
        String successMessage = idInfo.isPhone
                ? "6-digit OTP sent to " + idInfo.formattedDisplay
                : "6-digit OTP sent to " + normalized;

        return new SendOtpResponse(
                true,
                successMessage,
                normalized,
                idInfo.isPhone ? "mobile" : "email",
                totalExpirySeconds,
                resendCooldownSeconds
        );
    }

    /**
     * Verifies the 6-digit OTP, creates the CUSTOMER if new, and returns a signed JWT token.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String rawIdentifier = request.getIdentifier();
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number or email ID is required");
        }

        IdentifierInfo idInfo = parseIdentifier(rawIdentifier);
        String normalized = idInfo.normalized;
        String enteredOtp = request.getOtp() != null ? request.getOtp().trim() : "";
        OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : OtpPurpose.LOGIN;

        if (enteredOtp.length() != 6 || !enteredOtp.matches("^\\d{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP must be exactly 6 numeric digits");
        }

        // Retrieve latest unverified OTP record
        OtpVerification verification = otpVerificationRepository
                .findTopByIdentifierAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(normalized, purpose)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active OTP found. Please request a new OTP."));

        // Check if expired
        if (verification.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired. Please request a fresh OTP.");
        }

        // Check attempt limit
        if (verification.getAttempts() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Increment attempts count
        verification.incrementAttempts();

        // Verify cryptographic hash
        if (!passwordEncoder.matches(enteredOtp, verification.getOtpHash())) {
            otpVerificationRepository.save(verification);
            int remaining = maxAttempts - verification.getAttempts();
            if (remaining <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum verification attempts exceeded. Please request a new OTP.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP. " + remaining + " attempts remaining.");
        }

        // Mark verified so it cannot be reused
        verification.setVerified(true);
        otpVerificationRepository.save(verification);

        // Find or create customer
        User user = findOrCreateCustomer(idInfo, request.getFullName());

        // Generate stateless JWT token
        String jwt = jwtTokenProvider.generateToken(user);

        return new AuthResponse(
                true,
                "Authentication successful",
                jwt,
                UserSummaryResponse.fromUser(user)
        );
    }

    /**
     * Authenticates an owner or admin via password or credentials.
     * Enforces server-side BCrypt hash matching and strictly blocks CUSTOMER accounts.
     */
    @Transactional(readOnly = true)
    public AuthResponse ownerLogin(OwnerLoginRequest request) {
        String rawIdentifier = request.getIdentifier();
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifier (email or mobile) is required");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        IdentifierInfo idInfo = parseIdentifier(rawIdentifier);
        String cleanPhone = idInfo.isPhone ? idInfo.cleanDigits : "none";

        User owner = userRepository.findByIdentifier(idInfo.normalized, cleanPhone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid owner credentials"));

        if (!owner.isOwner() && !owner.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access restricted to store owners and managers. Customer accounts cannot authenticate through Owner portal.");
        }

        boolean passwordMatches = false;
        if (owner.getPasswordHash() != null) {
            passwordMatches = passwordEncoder.matches(request.getPassword(), owner.getPasswordHash());
        }

        if (!passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid owner credentials");
        }

        String jwt = jwtTokenProvider.generateToken(owner);
        return new AuthResponse(
                true,
                "Owner authenticated successfully",
                jwt,
                UserSummaryResponse.fromUser(owner)
        );
    }

    /**
     * Dispatches OTP for Owner / Admin login with rate limiting.
     * Prevents Customers from using Owner OTP.
     */
    @Transactional
    public SendOtpResponse sendOwnerOtp(SendOtpRequest request) {
        String rawIdentifier = request.getIdentifier();
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifier (email or mobile) is required");
        }

        IdentifierInfo idInfo = parseIdentifier(rawIdentifier);
        String cleanPhone = idInfo.isPhone ? idInfo.cleanDigits : "none";

        User user = userRepository.findByIdentifier(idInfo.normalized, cleanPhone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No owner or admin account found with this identifier"));

        if (!user.isOwner() && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access restricted to store owners and managers. Customer accounts cannot authenticate through Owner portal.");
        }

        String dbIdentifier = idInfo.normalized;

        // Check resend cooldown
        Optional<OtpVerification> latestOpt = otpVerificationRepository
                .findTopByIdentifierAndPurposeOrderByCreatedAtDesc(dbIdentifier, OtpPurpose.OWNER_LOGIN);

        if (latestOpt.isPresent()) {
            OtpVerification latest = latestOpt.get();
            LocalDateTime createdAt = latest.getCreatedAt();
            if (createdAt != null) {
                LocalDateTime cooldownUntil = createdAt.plusSeconds(resendCooldownSeconds);
                if (LocalDateTime.now().isBefore(cooldownUntil)) {
                    long remainingSeconds = Duration.between(LocalDateTime.now(), cooldownUntil).getSeconds();
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "Please wait " + Math.max(1, remainingSeconds) + " seconds before requesting another OTP");
                }
            }
        }

        // Generate 6-digit numeric OTP (100000 - 999999)
        int randomCode = 100000 + SECURE_RANDOM.nextInt(900000);
        String plainOtp = String.valueOf(randomCode);

        // Store only the secure BCrypt hash in MySQL
        String otpHash = passwordEncoder.encode(plainOtp);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpiryMinutes);

        OtpVerification verification = new OtpVerification(dbIdentifier, otpHash, OtpPurpose.OWNER_LOGIN, expiresAt);
        otpVerificationRepository.save(verification);

        // Deliver OTP (in dev mode, caches plain OTP for dev endpoint)
        otpDeliveryService.deliverOtp(dbIdentifier, plainOtp, OtpPurpose.OWNER_LOGIN);

        return new SendOtpResponse(
                true,
                "6-digit OTP sent to " + (idInfo.isPhone ? idInfo.formattedDisplay : idInfo.normalized),
                idInfo.isPhone ? idInfo.formattedDisplay : idInfo.normalized,
                idInfo.isPhone ? "mobile" : "email",
                otpExpiryMinutes * 60,
                resendCooldownSeconds
        );
    }

    /**
     * Verifies Owner / Admin OTP and issues JWT token.
     * Enforces attempt caps and expiration; strictly blocks Customers.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse verifyOwnerOtp(OwnerOtpRequest request) {
        String rawIdentifier = request.getIdentifier();
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifier is required");
        }
        if (request.getOtp() == null || !request.getOtp().matches("^\\d{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP must be exactly 6 numeric digits");
        }

        IdentifierInfo idInfo = parseIdentifier(rawIdentifier);
        String cleanPhone = idInfo.isPhone ? idInfo.cleanDigits : "none";

        User user = userRepository.findByIdentifier(idInfo.normalized, cleanPhone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No owner or admin account found with this identifier"));

        if (!user.isOwner() && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access restricted to store owners and managers. Customer accounts cannot authenticate through Owner portal.");
        }

        String dbIdentifier = idInfo.normalized;

        OtpVerification verification = otpVerificationRepository
                .findTopByIdentifierAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(dbIdentifier, OtpPurpose.OWNER_LOGIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No active OTP found. Please request a new OTP."));

        // Check expiration
        if (verification.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OTP has expired. Please request a fresh OTP.");
        }

        // Check attempts limit
        if (verification.getAttempts() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Increment attempts count
        verification.incrementAttempts();

        // Verify cryptographic hash
        if (!passwordEncoder.matches(request.getOtp(), verification.getOtpHash())) {
            otpVerificationRepository.save(verification);
            int remaining = maxAttempts - verification.getAttempts();
            if (remaining <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Maximum verification attempts exceeded. Please request a new OTP.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid OTP. " + remaining + " attempts remaining.");
        }

        // Mark OTP as verified
        verification.setVerified(true);
        otpVerificationRepository.save(verification);

        String jwt = jwtTokenProvider.generateToken(user);
        return new AuthResponse(
                true,
                "Owner authenticated successfully",
                jwt,
                UserSummaryResponse.fromUser(user)
        );
    }

    /**
     * Helper to retrieve or create a valid owner token for Owner Portal initialization / local testing.
     */
    @Transactional(readOnly = true)
    public AuthResponse getOwnerToken() {
        User owner = userRepository.findByRole(Role.OWNER).stream().findFirst()
                .orElseGet(() -> userRepository.findByEmail("owner@grocerychoice.com")
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No owner user found")));

        String jwt = jwtTokenProvider.generateToken(owner);
        return new AuthResponse(true, "Owner token generated", jwt, UserSummaryResponse.fromUser(owner));
    }

    /**
     * Retrieves the dev OTP for local test automation.
     */
    public String getDevOtp(String identifier) {
        return otpDeliveryService.getDevOtp(identifier);
    }

    private User findOrCreateCustomer(IdentifierInfo idInfo, String requestedName) {
        String cleanPhone = idInfo.isPhone ? idInfo.cleanDigits : "none";

        Optional<User> existingUser = userRepository.findByIdentifier(idInfo.normalized, cleanPhone);

        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        // Auto-register new customer - Strictly assigned CUSTOMER role
        String fullName = (requestedName != null && !requestedName.isBlank())
                ? requestedName.trim()
                : (idInfo.isPhone ? "Customer " + idInfo.cleanDigits.substring(Math.max(0, idInfo.cleanDigits.length() - 4)) : idInfo.normalized.split("@")[0]);

        // Capitalize name
        fullName = Character.toUpperCase(fullName.charAt(0)) + fullName.substring(1);

        String email = idInfo.isPhone
                ? (idInfo.cleanDigits + "@customer.grocerychoice.in")
                : idInfo.normalized;

        String phone = idInfo.isPhone ? idInfo.formattedDisplay : null;

        User newCustomer = new User(
                email,
                phone,
                fullName,
                null,
                Role.CUSTOMER // Strictly locked to CUSTOMER role
        );

        User savedUser = userRepository.save(newCustomer);
        log.info("Auto-registered new CUSTOMER with ID: {}, email: {}, phone: {}", savedUser.getId(), email, phone);

        // Seed default address for new customer
        Address defaultAddress = new Address(
                savedUser,
                "Flat 402, Green Glen Apartments",
                "Sector 14 Hub",
                "Gurugram",
                "Haryana",
                "122001",
                "Near Metro Station",
                28.4595,
                77.0266,
                true
        );
        addressRepository.save(defaultAddress);

        return savedUser;
    }

    private IdentifierInfo parseIdentifier(String raw) {
        String trimmed = raw.trim();

        if (trimmed.contains("@")) {
            String lower = trimmed.toLowerCase();
            if (!lower.matches("^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid email address");
            }
            return new IdentifierInfo(false, lower, lower, lower);
        }

        String digits = trimmed.replaceAll("\\D", "");
        // 10 digits or 12 digits starting with 91
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        if (digits.length() != 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid 10-digit Indian mobile number");
        }

        String formatted = "+91 " + digits.substring(0, 5) + " " + digits.substring(5);
        return new IdentifierInfo(true, digits, formatted, "+91" + digits);
    }

    private static class IdentifierInfo {
        final boolean isPhone;
        final String cleanDigits;
        final String formattedDisplay;
        final String normalized;

        IdentifierInfo(boolean isPhone, String cleanDigits, String formattedDisplay, String normalized) {
            this.isPhone = isPhone;
            this.cleanDigits = cleanDigits;
            this.formattedDisplay = formattedDisplay;
            this.normalized = normalized;
        }
    }
}
