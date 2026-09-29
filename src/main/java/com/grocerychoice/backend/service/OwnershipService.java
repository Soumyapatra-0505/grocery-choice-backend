package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.AuditLogResponse;
import com.grocerychoice.backend.dto.TransferOwnershipRequest;
import com.grocerychoice.backend.dto.UserSummaryResponse;
import com.grocerychoice.backend.entity.AuditLog;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.AuditLogRepository;
import com.grocerychoice.backend.repository.UserRepository;
import com.grocerychoice.backend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OwnershipService {

    private static final Logger log = LoggerFactory.getLogger(OwnershipService.class);

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public OwnershipService(UserRepository userRepository,
                            AuditLogRepository auditLogRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserSummaryResponse getCurrentPrimaryOwner() {
        Optional<User> primaryOpt = userRepository.findFirstByPrimaryOwnerTrue();
        if (primaryOpt.isPresent()) {
            return UserSummaryResponse.fromUser(primaryOpt.get());
        }

        // Backward compatibility fallback: if no primary owner flag is set yet,
        // designate the first existing OWNER as Primary Owner
        List<User> owners = userRepository.findByRole(Role.OWNER);
        if (!owners.isEmpty()) {
            User firstOwner = owners.get(0);
            firstOwner.setPrimaryOwner(true);
            if (firstOwner.getDesignation() == null || firstOwner.getDesignation().isBlank()) {
                firstOwner.setDesignation("Store Owner");
            }
            User saved = userRepository.save(firstOwner);
            log.info("Initialized default Primary Owner: {} (ID: {})", saved.getEmail(), saved.getId());
            return UserSummaryResponse.fromUser(saved);
        }

        return null;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getEligibleNewOwners() {
        List<User> owners = userRepository.findByRole(Role.OWNER);
        return owners.stream()
                .filter(u -> !u.isPrimaryOwner())
                .filter(u -> u.getStatus() != UserStatus.DISABLED)
                .map(UserSummaryResponse::fromUser)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> transferPrimaryOwnership(TransferOwnershipRequest request, UserPrincipal currentOwnerPrincipal) {
        if (request == null) {
            throw new InvalidDataException("Transfer request cannot be null");
        }

        // 1. Verify actor is authenticated
        if (currentOwnerPrincipal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to perform ownership transfer");
        }

        // 2. Load current owner from DB and strictly verify Primary Owner status
        User currentOwner = userRepository.findById(currentOwnerPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Current owner record not found"));

        if (!currentOwner.isPrimaryOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the designated Primary Owner can initiate ownership transfer");
        }

        // 3. Strong confirmation keyword validation
        if (request.getConfirmKeyword() == null || !"TRANSFER".equalsIgnoreCase(request.getConfirmKeyword().trim())) {
            throw new InvalidDataException("Confirmation keyword mismatch. You must type 'TRANSFER' to confirm.");
        }

        // 4. Validate post-transfer role choice
        Role postTransferRole = request.getPreviousOwnerNewRole();
        if (postTransferRole != Role.OWNER && postTransferRole != Role.CUSTOMER) {
            throw new InvalidDataException("Post-transfer role for the previous owner must be explicitly selected as either OWNER or CUSTOMER");
        }

        // 5. Re-authentication password check (if owner has a password and provided one)
        if (currentOwner.getPasswordHash() != null && request.getCurrentOwnerPassword() != null && !request.getCurrentOwnerPassword().isBlank()) {
            if (!passwordEncoder.matches(request.getCurrentOwnerPassword().trim(), currentOwner.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current owner password verification failed");
            }
        }

        // 6. Target user verification
        if (request.getNewPrimaryOwnerId() == null) {
            throw new InvalidDataException("Target new primary owner ID is required");
        }

        User targetUser = userRepository.findById(request.getNewPrimaryOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found with ID: " + request.getNewPrimaryOwnerId()));

        if (targetUser.getId().equals(currentOwner.getId())) {
            throw new InvalidDataException("Cannot transfer Primary Ownership to yourself. You are already the Primary Owner.");
        }

        if (targetUser.getRole() != Role.OWNER) {
            throw new InvalidDataException("Target user must already be an OWNER to receive Primary Ownership. Promote them to OWNER first.");
        }

        if (targetUser.getStatus() == UserStatus.DISABLED) {
            throw new InvalidDataException("Target user account is disabled. Only active OWNER accounts can receive Primary Ownership.");
        }

        // 7. Atomic transaction execution
        currentOwner.setPrimaryOwner(false);
        currentOwner.setRole(postTransferRole);
        if (postTransferRole == Role.CUSTOMER) {
            currentOwner.setDesignation(null);
            currentOwner.getCustomPermissions().clear();
        }
        userRepository.save(currentOwner);

        targetUser.setPrimaryOwner(true);
        targetUser.setRole(Role.OWNER);
        if (targetUser.getDesignation() == null || targetUser.getDesignation().isBlank()) {
            targetUser.setDesignation("Store Owner");
        }
        userRepository.save(targetUser);

        // 8. Record immutable security audit log
        String auditDetails = String.format(
                "Primary Ownership transferred from %s (%s, ID: %d) to %s (%s, ID: %d). Previous owner post-transfer role: %s.",
                currentOwner.getFullName(), currentOwner.getEmail(), currentOwner.getId(),
                targetUser.getFullName(), targetUser.getEmail(), targetUser.getId(),
                postTransferRole.name()
        );

        auditLogRepository.save(new AuditLog(
                "PRIMARY_OWNERSHIP_TRANSFER",
                currentOwner.getId(),
                currentOwner.getEmail(),
                currentOwner.getFullName(),
                targetUser.getId(),
                targetUser.getEmail(),
                targetUser.getFullName(),
                auditDetails
        ));

        log.warn("CRITICAL AUDIT EVENT: {}", auditDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Primary Ownership transferred successfully.");
        response.put("newPrimaryOwner", UserSummaryResponse.fromUser(targetUser));
        response.put("previousOwner", UserSummaryResponse.fromUser(currentOwner));

        return response;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(AuditLogResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
