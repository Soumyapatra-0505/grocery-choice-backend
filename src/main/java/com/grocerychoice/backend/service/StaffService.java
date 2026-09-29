package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.CreateStaffRequest;
import com.grocerychoice.backend.dto.UpdateContactRequest;
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
        UpdateContactRequest contactReq = new UpdateContactRequest(
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                request.getDesignation(),
                request.getStoreHub()
        );
        return updateContact(id, contactReq, actor);
    }

    @Transactional
    public UserSummaryResponse updateContact(Long id, UpdateContactRequest request, UserPrincipal actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));

        String targetEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        validateContactUpdateAuthorization(user, targetEmail, actor);

        List<String> changedFields = new java.util.ArrayList<>();
        StringBuilder detailsBuilder = new StringBuilder();

        // 1. Full Name
        if (request.getFullName() != null) {
            String newName = request.getFullName().trim();
            if (newName.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name cannot be blank");
            }
            if (!newName.equals(user.getFullName())) {
                changedFields.add("fullName");
                detailsBuilder.append("Name: '").append(user.getFullName()).append("' -> '").append(newName).append("'; ");
                user.setFullName(newName);
            }
        }

        // 2. Email
        if (request.getEmail() != null) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (newEmail.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address cannot be empty");
            }
            if (!newEmail.matches("^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address");
            }
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                Optional<User> existingEmailUser = userRepository.findByEmailIgnoreCase(newEmail);
                if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(user.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already belongs to another account");
                }
                changedFields.add("email");
                detailsBuilder.append("Email: '").append(user.getEmail()).append("' -> '").append(newEmail).append("'; ");
                user.setEmail(newEmail);
            }
        }

        // 3. Phone
        if (request.getPhone() != null) {
            String rawPhone = request.getPhone().trim();
            if (rawPhone.isEmpty()) {
                if (user.getPhone() != null) {
                    changedFields.add("phone");
                    detailsBuilder.append("Phone: '").append(user.getPhone()).append("' -> null; ");
                    user.setPhone(null);
                }
            } else {
                String digits = rawPhone.replaceAll("\\D", "");
                if (digits.length() == 12 && digits.startsWith("91")) {
                    digits = digits.substring(2);
                } else if (digits.length() == 11 && digits.startsWith("0")) {
                    digits = digits.substring(1);
                }
                if (digits.length() != 10) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone number");
                }
                String formattedPhone = "+91 " + digits.substring(0, 5) + " " + digits.substring(5);

                List<User> existingPhoneUsers = userRepository.findAllByCleanPhone(digits);
                for (User u : existingPhoneUsers) {
                    if (!u.getId().equals(user.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number already belongs to another account");
                    }
                }
                Optional<User> byPhoneExact = userRepository.findByPhone(formattedPhone);
                if (byPhoneExact.isPresent() && !byPhoneExact.get().getId().equals(user.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number already belongs to another account");
                }

                if (!formattedPhone.equals(user.getPhone())) {
                    changedFields.add("phone");
                    detailsBuilder.append("Phone: '").append(user.getPhone()).append("' -> '").append(formattedPhone).append("'; ");
                    user.setPhone(formattedPhone);
                }
            }
        }

        // 4. Designation
        if (request.getDesignation() != null) {
            String newDesignation = request.getDesignation().trim().isEmpty() ? null : request.getDesignation().trim();
            if (!java.util.Objects.equals(newDesignation, user.getDesignation())) {
                changedFields.add("designation");
                detailsBuilder.append("Designation: '").append(user.getDesignation()).append("' -> '").append(newDesignation).append("'; ");
                user.setDesignation(newDesignation);
            }
        }

        // 5. Store Hub
        if (request.getStoreHub() != null) {
            String newStoreHub = request.getStoreHub().trim().isEmpty() ? null : request.getStoreHub().trim();
            if (!java.util.Objects.equals(newStoreHub, user.getStoreHub())) {
                changedFields.add("storeHub");
                detailsBuilder.append("StoreHub: '").append(user.getStoreHub()).append("' -> '").append(newStoreHub).append("'; ");
                user.setStoreHub(newStoreHub);
            }
        }

        User saved = userRepository.save(user);

        if (!changedFields.isEmpty()) {
            String logDetails = "Updated contact info for " + saved.getFullName() +
                    ". Changed fields: [" + String.join(", ", changedFields) + "]. " + detailsBuilder.toString();
            if (logDetails.length() > 950) {
                logDetails = logDetails.substring(0, 950) + "...";
            }
            auditLogRepository.save(new AuditLog(
                    "STAFF_CONTACT_UPDATED",
                    actor != null ? actor.getId() : null,
                    actor != null ? actor.getEmail() : "system",
                    actor != null ? actor.getFullName() : "System",
                    saved.getId(),
                    saved.getEmail(),
                    saved.getFullName(),
                    logDetails
            ));
            log.info("Contact updated for user ID: {} by actor: {}. Changed: {}", saved.getId(), actor != null ? actor.getEmail() : "system", changedFields);
        }

        return UserSummaryResponse.fromUser(saved);
    }

    private void validateContactUpdateAuthorization(User targetUser, String newEmail, UserPrincipal actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        boolean isSelf = actor.getId().equals(targetUser.getId());

        // CUSTOMER or STAFF cannot modify any staff contact information
        if (actor.getRole() == Role.CUSTOMER || actor.getRole() == Role.STAFF) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to modify this account");
        }

        // Target is Primary Owner: Only the Primary Owner can edit their own profile
        if (targetUser.isPrimaryOwner()) {
            if (!actor.isPrimaryOwner()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to modify the Primary Owner's account");
            }
        }

        // Target is non-primary OWNER
        if (targetUser.getRole() == Role.OWNER && !targetUser.isPrimaryOwner()) {
            if (actor.isAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin cannot modify Owner contact information");
            }
            if (!actor.isPrimaryOwner() && !isSelf) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner cannot modify another Owner's contact information");
            }
        }

        // Target is ADMIN
        if (targetUser.getRole() == Role.ADMIN) {
            if (actor.isAdmin() && !isSelf) {
                if (!actor.isPrimaryOwner() && !actor.isOwner() && !actor.hasPermission(Permission.MANAGE_ADMINS)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions to modify an Admin account");
                }
            }
        }

        // Changing another user's email is restricted to the Primary Owner
        if (newEmail != null && !newEmail.equalsIgnoreCase(targetUser.getEmail())) {
            if (!actor.isPrimaryOwner() && !isSelf) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Primary Owner can change another user's email address");
            }
        }
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
