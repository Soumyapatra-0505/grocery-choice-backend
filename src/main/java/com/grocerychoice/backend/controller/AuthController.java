package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.*;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Dispatches a 6-digit OTP to the customer's mobile phone or email.
     * Never exposes the plain-text OTP in the production API response.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<SendOtpResponse> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        SendOtpResponse response = authService.sendOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verifies the 6-digit OTP and authenticates/registers the customer.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticates owner/store manager credentials via secure BCrypt password comparison.
     */
    @PostMapping({"/owner/login", "/owner-login"})
    public ResponseEntity<AuthResponse> ownerLogin(@Valid @RequestBody OwnerLoginRequest request) {
        AuthResponse response = authService.ownerLogin(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Dispatches OTP for Store Owner / Admin login.
     * Rejects Customer accounts attempting to use Owner OTP.
     */
    @PostMapping("/owner/send-otp")
    public ResponseEntity<SendOtpResponse> sendOwnerOtp(@Valid @RequestBody SendOtpRequest request) {
        SendOtpResponse response = authService.sendOwnerOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verifies Owner / Admin OTP and returns Owner JWT session.
     */
    @PostMapping("/owner/verify-otp")
    public ResponseEntity<AuthResponse> verifyOwnerOtp(@Valid @RequestBody OwnerOtpRequest request) {
        AuthResponse response = authService.verifyOwnerOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Helper endpoint for Owner Portal startup / local testing.
     */
    @GetMapping("/owner-token")
    public ResponseEntity<AuthResponse> getOwnerToken() {
        return ResponseEntity.ok(authService.getOwnerToken());
    }

    /**
     * Development mode endpoint to inspect generated OTP for local test automation.
     */
    @GetMapping("/dev-otp/{identifier}")
    public ResponseEntity<Map<String, String>> getDevOtp(@PathVariable String identifier) {
        String otp = authService.getDevOtp(identifier);
        if (otp == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active dev OTP found for identifier");
        }
        return ResponseEntity.ok(Map.of("identifier", identifier, "otp", otp));
    }

    /**
     * Retrieves current authenticated user profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserSummaryResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return ResponseEntity.ok(new UserSummaryResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getPhone(),
                principal.getRole()
        ));
    }
}
