package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.CreateStaffRequest;
import com.grocerychoice.backend.dto.UpdateStaffRequest;
import com.grocerychoice.backend.dto.UserSummaryResponse;
import com.grocerychoice.backend.entity.*;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffService(UserRepository userRepository,
                        AuditLogRepository auditLogRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getAllStaffMembers() {
        List<User> staff = userRepository.findByRoleInOrderByCreatedAtDesc(
                List.of(Role.OWNER, Role.ADMIN, Role.STAFF)
        );
        return staff.stream()
                .map(UserSummaryResponse::fromUser)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getStaffMemberById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));
        return UserSummaryResponse.fromUser(user);
    }

    @Transactional
    public UserSummaryResponse createStaff(CreateStaffRequest request, UserPrincipal actor) {
        if (request.getRole() == null || request.getRole() == Role.CUSTOMER) {
            throw new InvalidDataException("A valid system role (STAFF, ADMIN, or OWNER) is required");
        }

        // Security check for role assignment
        if (request.getRole() == Role.OWNER) {
            if (actor == null || (!actor.isPrimaryOwner() && !actor.hasPermission(Permission.MANAGE_OWNERS))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can create or add another OWNER account");
            }
        } else if (request.getRole() == Role.ADMIN) {
            if (actor == null || (!actor.isPrimaryOwner() && !actor.isOwner() && !actor.hasPermission(Permission.MANAGE_ADMINS))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions to create an ADMIN account");
            }
        }

        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null;

        // Check if an existing account exists (Unified account architecture)
        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(email);
        if (existingUserOpt.isEmpty() && phone != null) {
            existingUserOpt = userRepository.findByPhone(phone);
        }

        User user;
        String actionType;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (user.isPrimaryOwner()) {
                throw new InvalidDataException("Cannot modify the Primary Owner account through the Add Staff form");
            }
            Role previousRole = user.getRole();
            user.setRole(request.getRole());
            if (request.getFullName() != null && !request.getFullName().isBlank()) {
                user.setFullName(request.getFullName().trim());
            }
            if (phone != null) {
                user.setPhone(phone);
            }
            if (request.getDesignation() != null) {
                user.setDesignation(request.getDesignation().trim());
            }
            if (request.getStoreHub() != null) {
                user.setStoreHub(request.getStoreHub().trim());
            }
            if (request.getStatus() != null) {
                user.setStatus(request.getStatus());
            }
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
            }

            actionType = "STAFF_PROMOTED";
            log.info("Promoted existing user ID: {} from {} to {}", user.getId(), previousRole, user.getRole());
        } else {
            // Create brand new staff account
            String passwordHash = (request.getPassword() != null && !request.getPassword().isBlank())
                    ? passwordEncoder.encode(request.getPassword().trim())
                    : null;

            user = new User(
                    email,
                    phone,
                    request.getFullName().trim(),
                    passwordHash,
                    request.getRole()
            );
            user.setPrimaryOwner(false);
            user.setStatus(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE);
            user.setDesignation(request.getDesignation() != null ? request.getDesignation().trim() : null);
            user.setStoreHub(request.getStoreHub() != null ? request.getStoreHub().trim() : "Flagship Hub");

            actionType = "STAFF_CREATED";
            log.info("Created new staff user: {} with role: {}", email, user.getRole());
        }

        User saved = userRepository.save(user);

        // Record audit event
        auditLogRepository.save(new AuditLog(
                actionType,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                actionType + ": " + saved.getFullName() + " as " + saved.getRole() + " (" + saved.getDesignation() + ")"
        ));

        return UserSummaryResponse.fromUser(saved);
    }

    @Transactional
    public UserSummaryResponse updateStaff(Long id, UpdateStaffRequest request, UserPrincipal actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));

        if (user.isPrimaryOwner() && (actor == null || !actor.isPrimaryOwner())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can edit their own profile");
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getDesignation() != null) {
            user.setDesignation(request.getDesignation().trim());
        }
        if (request.getStoreHub() != null) {
            user.setStoreHub(request.getStoreHub().trim());
        }

        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "STAFF_UPDATED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                "Updated details for: " + saved.getFullName()
        ));

        return UserSummaryResponse.fromUser(saved);
    }

    @Transactional
    public UserSummaryResponse changeRole(Long id, Role newRole, UserPrincipal actor) {
        if (newRole == null) {
            throw new InvalidDataException("New system role is required");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));

        if (user.isPrimaryOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change the role of the Primary Owner here. Use the Transfer Primary Ownership feature.");
        }

        Role previousRole = user.getRole();
        if (previousRole == newRole) {
            return UserSummaryResponse.fromUser(user);
        }

        // Promoting to OWNER requires Primary Owner
        if (newRole == Role.OWNER) {
            if (actor == null || (!actor.isPrimaryOwner() && !actor.hasPermission(Permission.MANAGE_OWNERS))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can promote an account to OWNER");
            }
        }

        // Demoting an OWNER requires Primary Owner
        if (previousRole == Role.OWNER) {
            if (actor == null || (!actor.isPrimaryOwner() && !actor.hasPermission(Permission.MANAGE_OWNERS))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can demote an OWNER account");
            }
        }

        // Changing ADMIN requires Owner
        if ((newRole == Role.ADMIN || previousRole == Role.ADMIN) && (actor == null || (!actor.isPrimaryOwner() && !actor.isOwner()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an Owner can change ADMIN roles");
        }

        user.setRole(newRole);
        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "ROLE_CHANGED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                "Changed role of " + saved.getFullName() + " from " + previousRole + " to " + newRole
        ));

        log.info("Role changed for user ID: {} from {} to {} by actor: {}", id, previousRole, newRole, actor != null ? actor.getEmail() : "system");
        return UserSummaryResponse.fromUser(saved);
    }

    @Transactional
    public UserSummaryResponse changeStatus(Long id, UserStatus newStatus, UserPrincipal actor) {
        if (newStatus == null) {
            throw new InvalidDataException("New status is required (ACTIVE or DISABLED)");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));

        if (user.isPrimaryOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The Primary Owner account cannot be disabled");
        }

        if (actor != null && actor.getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable your own account");
        }

        if (user.getRole() == Role.OWNER && (actor == null || !actor.isPrimaryOwner())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can disable an OWNER account");
        }

        user.setStatus(newStatus);
        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "STATUS_CHANGED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                "Set status of " + saved.getFullName() + " to " + newStatus
        ));

        log.info("Status changed for user ID: {} to {} by actor: {}", id, newStatus, actor != null ? actor.getEmail() : "system");
        return UserSummaryResponse.fromUser(saved);
    }

    @Transactional
    public UserSummaryResponse changeDesignation(Long id, String designation, UserPrincipal actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));

        String clean = designation != null && !designation.isBlank() ? designation.trim() : null;
        user.setDesignation(clean);
        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "DESIGNATION_ASSIGNED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                "Assigned designation '" + clean + "' to " + saved.getFullName()
        ));

        return UserSummaryResponse.fromUser(saved);
    }

    @Transactional
    public UserSummaryResponse updatePermissions(Long id, Set<String> permissions, UserPrincipal actor) {
        if (actor == null || (!actor.isPrimaryOwner() && !actor.hasPermission(Permission.MANAGE_PERMISSIONS))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can customize individual permissions");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));

        if (user.isPrimaryOwner()) {
            throw new InvalidDataException("Primary Owner always possesses all system permissions and cannot be restricted");
        }

        user.setCustomPermissions(permissions);
        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "PERMISSIONS_UPDATED",
                actor.getId(),
                actor.getEmail(),
                actor.getFullName(),
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                "Updated custom permissions for " + saved.getFullName()
        ));

        return UserSummaryResponse.fromUser(saved);
    }

    @Transactional
    public UserSummaryResponse removeStaffAccess(Long id, UserPrincipal actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));

        if (user.isPrimaryOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove staff access from the Primary Owner");
        }

        if (user.getRole() == Role.OWNER && (actor == null || !actor.isPrimaryOwner())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can revoke OWNER access");
        }

        Role oldRole = user.getRole();
        // Demote to CUSTOMER - retains all order history and customer profile safely without data loss!
        user.setRole(Role.CUSTOMER);
        user.setDesignation(null);
        user.getCustomPermissions().clear();

        User saved = userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "STAFF_REMOVED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                "Revoked staff access from " + saved.getFullName() + " (was " + oldRole + "), account restored to standard CUSTOMER"
        ));

        log.info("Revoked staff access for user ID: {} (demoted to CUSTOMER) by actor: {}", id, actor != null ? actor.getEmail() : "system");
        return UserSummaryResponse.fromUser(saved);
    }
}
