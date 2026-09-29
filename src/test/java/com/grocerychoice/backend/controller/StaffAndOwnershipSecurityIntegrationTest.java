package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.*;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import com.grocerychoice.backend.repository.UserRepository;
import com.grocerychoice.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaffAndOwnershipSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.grocerychoice.backend.repository.AuditLogRepository auditLogRepository;

    private User primaryOwner;
    private User secondaryOwner;
    private User adminUser;
    private User staffUser;
    private User customerUser;
    private User disabledUser;

    private String primaryOwnerToken;
    private String secondaryOwnerToken;
    private String adminToken;
    private String staffToken;
    private String customerToken;
    private String disabledToken;

    @BeforeEach
    void setUp() {
        // Setup isolated test users in database
        // Reset any other primary owner flags to ensure clean test isolation
        for (User u : userRepository.findAllByPrimaryOwnerTrue()) {
            if (!u.getEmail().equalsIgnoreCase("test.primary.owner@grocerychoice.com")) {
                u.setPrimaryOwner(false);
                userRepository.save(u);
            }
        }

        primaryOwner = userRepository.findByEmail("test.primary.owner@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Test Primary Owner");
                    u.setEmail("test.primary.owner@grocerychoice.com");
                    u.setPhone("+91 99000 00001");
                    u.setRole(Role.OWNER);
                    u.setPrimaryOwner(true);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Store Owner");
                    u.setStoreHub("Central Warehouse");
                    return userRepository.save(u);
                });
        primaryOwner.setPrimaryOwner(true);
        primaryOwner.setRole(Role.OWNER);
        primaryOwner.setStatus(UserStatus.ACTIVE);
        userRepository.save(primaryOwner);

        secondaryOwner = userRepository.findByEmail("test.secondary.owner@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Test Co-Owner");
                    u.setEmail("test.secondary.owner@grocerychoice.com");
                    u.setPhone("+91 99000 00002");
                    u.setRole(Role.OWNER);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Operations Director");
                    u.setStoreHub("Central Warehouse");
                    return userRepository.save(u);
                });
        secondaryOwner.setPrimaryOwner(false);
        secondaryOwner.setRole(Role.OWNER);
        secondaryOwner.setStatus(UserStatus.ACTIVE);
        userRepository.save(secondaryOwner);

        adminUser = userRepository.findByEmail("test.admin@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Test Admin");
                    u.setEmail("test.admin@grocerychoice.com");
                    u.setPhone("+91 99000 00003");
                    u.setRole(Role.ADMIN);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Store Manager");
                    u.setStoreHub("Central Warehouse");
                    return userRepository.save(u);
                });

        staffUser = userRepository.findByEmail("test.staff@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Test Staff");
                    u.setEmail("test.staff@grocerychoice.com");
                    u.setPhone("+91 99000 00004");
                    u.setRole(Role.STAFF);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Inventory Associate");
                    u.setStoreHub("North Hub");
                    return userRepository.save(u);
                });

        customerUser = userRepository.findByEmail("test.customer@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Test Customer");
                    u.setEmail("test.customer@grocerychoice.com");
                    u.setPhone("+91 99000 00005");
                    u.setRole(Role.CUSTOMER);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    return userRepository.save(u);
                });

        disabledUser = userRepository.findByEmail("test.disabled@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Test Disabled Staff");
                    u.setEmail("test.disabled@grocerychoice.com");
                    u.setPhone("+91 99000 00006");
                    u.setRole(Role.STAFF);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.DISABLED);
                    u.setDesignation("Former Staff");
                    return userRepository.save(u);
                });

        // Generate authentic signed tokens
        primaryOwnerToken = "Bearer " + jwtTokenProvider.generateToken(primaryOwner);
        secondaryOwnerToken = "Bearer " + jwtTokenProvider.generateToken(secondaryOwner);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(adminUser);
        staffToken = "Bearer " + jwtTokenProvider.generateToken(staffUser);
        customerToken = "Bearer " + jwtTokenProvider.generateToken(customerUser);
        disabledToken = "Bearer " + jwtTokenProvider.generateToken(disabledUser);
    }

    @Test
    @DisplayName("1. GET /api/staff without auth returns 401 Unauthorized")
    void testGetStaff_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/staff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. GET /api/staff with CUSTOMER role returns 403 Forbidden")
    void testGetStaff_CustomerForbidden() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. GET /api/staff with STAFF role returns 403 Forbidden")
    void testGetStaff_StaffForbidden() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. GET /api/staff with ADMIN role returns 200 OK")
    void testGetStaff_AdminAllowed() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)));
    }

    @Test
    @DisplayName("5. GET /api/staff with OWNER role returns 200 OK with staff list")
    void testGetStaff_OwnerAllowed() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", primaryOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$[*].email", hasItem("test.primary.owner@grocerychoice.com")));
    }

    @Test
    @DisplayName("6. POST /api/staff: ADMIN attempting to create OWNER returns 403 Forbidden")
    void testCreateStaff_AdminCannotCreateOwner() throws Exception {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setFullName("Unauthorized Owner");
        req.setEmail("unauth.owner@grocerychoice.com");
        req.setPhone("+91 99000 99999");
        req.setRole(Role.OWNER);
        req.setDesignation("Store Owner");

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Only the Primary Owner")));
    }

    @Test
    @DisplayName("7. POST /api/staff: Primary Owner can add or promote staff successfully")
    void testCreateStaff_PrimaryOwnerSuccess() throws Exception {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setFullName("New Inventory Staff");
        req.setEmail("new.staff." + System.currentTimeMillis() + "@grocerychoice.com");
        req.setPhone("+91 99887 " + (int)(Math.random() * 90000 + 10000));
        req.setRole(Role.STAFF);
        req.setDesignation("Inventory Manager");
        req.setStoreHub("South Hub");

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.designation").value("Inventory Manager"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("8. POST /api/staff: Promoting existing CUSTOMER preserves account in-place")
    void testCreateStaff_PromoteCustomerInPlace() throws Exception {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setFullName("Promoted Customer");
        req.setEmail(customerUser.getEmail());
        req.setPhone(customerUser.getPhone());
        req.setRole(Role.STAFF);
        req.setDesignation("Delivery Manager");
        req.setStoreHub("Central Warehouse");

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(customerUser.getId()))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.designation").value("Delivery Manager"));

        // Verify user in DB still has original ID
        User updated = userRepository.findById(customerUser.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.STAFF, updated.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(customerUser.getId(), updated.getId());
    }

    @Test
    @DisplayName("9. Disabled user token is rejected by backend security")
    void testDisabledUser_AuthenticationRejected() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", disabledToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. PATCH /api/staff/{id}/status: Attempting to disable Primary Owner returns 403")
    void testDisablePrimaryOwner_Rejected() throws Exception {
        ChangeStatusRequest req = new ChangeStatusRequest();
        req.setStatus(UserStatus.DISABLED);

        mockMvc.perform(patch("/api/staff/" + primaryOwner.getId() + "/status")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Primary Owner")));
    }

    @Test
    @DisplayName("11. PATCH /api/staff/{id}/role: Attempting to demote Primary Owner returns 403")
    void testDemotePrimaryOwner_Rejected() throws Exception {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole(Role.STAFF);

        mockMvc.perform(patch("/api/staff/" + primaryOwner.getId() + "/role")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Primary Owner")));
    }

    @Test
    @DisplayName("12. DELETE /api/staff/{id}: Attempting to revoke Primary Owner returns 403")
    void testRevokePrimaryOwner_Rejected() throws Exception {
        mockMvc.perform(delete("/api/staff/" + primaryOwner.getId())
                        .header("Authorization", primaryOwnerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Primary Owner")));
    }

    @Test
    @DisplayName("13. POST /api/ownership/transfer: Non-Primary Owner attempting transfer returns 403")
    void testTransferOwnership_NonPrimaryOwnerForbidden() throws Exception {
        TransferOwnershipRequest req = new TransferOwnershipRequest();
        req.setNewPrimaryOwnerId(secondaryOwner.getId());
        req.setPreviousOwnerNewRole(Role.OWNER);
        req.setConfirmKeyword("TRANSFER");

        mockMvc.perform(post("/api/ownership/transfer")
                        .header("Authorization", secondaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Primary Owner")));
    }

    @Test
    @DisplayName("14. POST /api/ownership/transfer: Wrong confirmation keyword returns 400")
    void testTransferOwnership_WrongKeywordReturns400() throws Exception {
        TransferOwnershipRequest req = new TransferOwnershipRequest();
        req.setNewPrimaryOwnerId(secondaryOwner.getId());
        req.setPreviousOwnerNewRole(Role.OWNER);
        req.setConfirmKeyword("WRONG KEYWORD");

        mockMvc.perform(post("/api/ownership/transfer")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("TRANSFER")));
    }

    @Test
    @DisplayName("15. POST /api/ownership/transfer: Valid transfer succeeds atomically")
    void testTransferOwnership_SuccessAtomic() throws Exception {
        TransferOwnershipRequest req = new TransferOwnershipRequest();
        req.setNewPrimaryOwnerId(secondaryOwner.getId());
        req.setPreviousOwnerNewRole(Role.OWNER);
        req.setConfirmKeyword("TRANSFER");

        mockMvc.perform(post("/api/ownership/transfer")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Primary Ownership")))
                .andExpect(jsonPath("$.newPrimaryOwner.id").value(secondaryOwner.getId()))
                .andExpect(jsonPath("$.newPrimaryOwner.primaryOwner").value(true));

        // Verify single Primary Owner guarantee in DB
        User updatedNewPO = userRepository.findById(secondaryOwner.getId()).orElseThrow();
        User updatedOldPO = userRepository.findById(primaryOwner.getId()).orElseThrow();

        org.junit.jupiter.api.Assertions.assertTrue(updatedNewPO.isPrimaryOwner());
        org.junit.jupiter.api.Assertions.assertFalse(updatedOldPO.isPrimaryOwner());
        org.junit.jupiter.api.Assertions.assertEquals(Role.OWNER, updatedOldPO.getRole());

        // Verify total primary owners in DB is exactly 1
        int poCount = userRepository.findAllByPrimaryOwnerTrue().size();
        org.junit.jupiter.api.Assertions.assertEquals(1, poCount);
    }

    @Test
    @DisplayName("16. GET /api/ownership/audit-logs: Audit logs record actions and contain no secrets")
    void testAuditLogs_NoSecretsExposed() throws Exception {
        mockMvc.perform(get("/api/ownership/audit-logs")
                        .header("Authorization", primaryOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("otp"))));
    }

    @Test
    @DisplayName("17. PUT /api/staff/{id}/contact: Primary Owner can update staff email and phone")
    void testUpdateContact_PrimaryOwnerSuccess() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Updated Staff Name");
        req.setEmail("updated.staff@grocerychoice.com");
        req.setPhone("+91 99000 88888");
        req.setDesignation("Lead Associate");
        req.setStoreHub("Flagship Hub");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(staffUser.getId()))
                .andExpect(jsonPath("$.fullName").value("Updated Staff Name"))
                .andExpect(jsonPath("$.email").value("updated.staff@grocerychoice.com"))
                .andExpect(jsonPath("$.phone").value("+91 99000 88888"))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.designation").value("Lead Associate"));

        // Verify audit log exists
        boolean auditFound = auditLogRepository.findAll().stream()
                .anyMatch(a -> "STAFF_CONTACT_UPDATED".equals(a.getAction()) && staffUser.getId().equals(a.getTargetId()));
        org.junit.jupiter.api.Assertions.assertTrue(auditFound, "Expected STAFF_CONTACT_UPDATED audit log");
    }

    @Test
    @DisplayName("18a. PUT /api/staff/{id}/contact: Duplicate email of another user rejected with 409 Conflict")
    void testUpdateContact_DuplicateEmail_AnotherUser() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail(adminUser.getEmail()); // already taken by admin

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Email address already exists")));
    }

    @Test
    @DisplayName("18b. PUT /api/staff/{id}/contact: Submitting target user's existing email rejected with 409 Conflict")
    void testUpdateContact_DuplicateEmail_SameUser() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail(staffUser.getEmail()); // already belongs to staffUser

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Email address already exists")));
    }

    @Test
    @DisplayName("18c. PUT /api/staff/{id}/contact: Same email with different letter case rejected with 409 Conflict")
    void testUpdateContact_DuplicateEmail_DifferentCase() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail(staffUser.getEmail().toUpperCase());

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Email address already exists")));
    }

    @Test
    @DisplayName("18d. PUT /api/staff/{id}/contact: Existing email with surrounding spaces rejected with 409 Conflict")
    void testUpdateContact_DuplicateEmail_SurroundingSpaces() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail("   " + staffUser.getEmail() + "   ");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Email address already exists")));
    }

    @Test
    @DisplayName("18e. PUT /api/staff/{id}/contact: When email is null/omitted, other fields update successfully")
    void testUpdateContact_EmailOmitted_SavesOtherFields() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail(null);
        req.setFullName("Updated Name Only Staff");
        req.setDesignation("Operations Lead");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name Only Staff"))
                .andExpect(jsonPath("$.email").value(staffUser.getEmail()))
                .andExpect(jsonPath("$.designation").value("Operations Lead"));
    }

    @Test
    @DisplayName("19a. PUT /api/staff/{id}/contact: Duplicate phone of another user rejected with 409 Conflict")
    void testUpdateContact_DuplicatePhone_AnotherUser() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone(adminUser.getPhone()); // already taken by admin

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Mobile number already exists")));
    }

    @Test
    @DisplayName("19b. PUT /api/staff/{id}/contact: Submitting target user's existing phone rejected with 409 Conflict")
    void testUpdateContact_DuplicatePhone_SameUser() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone(staffUser.getPhone()); // already belongs to staffUser

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Mobile number already exists")));
    }

    @Test
    @DisplayName("19c. PUT /api/staff/{id}/contact: Same phone with +91 formatting rejected with 409 Conflict")
    void testUpdateContact_DuplicatePhone_Plus91Formatting() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("+919900000003");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Mobile number already exists")));
    }

    @Test
    @DisplayName("19d. PUT /api/staff/{id}/contact: Same phone with spaces and dashes rejected with 409 Conflict")
    void testUpdateContact_DuplicatePhone_SpacesAndDashes() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("  +91 99000-00003  ");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Mobile number already exists")));
    }

    @Test
    @DisplayName("19e. PUT /api/staff/{id}/contact: Same phone with leading 0 rejected with 409 Conflict")
    void testUpdateContact_DuplicatePhone_LeadingZero() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("09900000003");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Mobile number already exists")));
    }

    @Test
    @DisplayName("19f. PUT /api/staff/{id}/contact: Same phone with leading 091 prefix rejected with 409 Conflict")
    void testUpdateContact_DuplicatePhone_Leading091() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("0919900000003");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Mobile number already exists")));
    }

    @Test
    @DisplayName("19g. PUT /api/staff/{id}/contact: When phone is null/omitted, other fields update successfully")
    void testUpdateContact_PhoneOmitted_SavesOtherFields() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone(null);
        req.setFullName("Updated Phone Omitted Staff");
        req.setDesignation("Dispatch Manager");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Phone Omitted Staff"))
                .andExpect(jsonPath("$.phone").value(staffUser.getPhone()))
                .andExpect(jsonPath("$.designation").value("Dispatch Manager"));
    }

    @Test
    @DisplayName("20. PUT /api/staff/{id}/contact: Invalid email format rejected with 400 Bad Request")
    void testUpdateContact_InvalidEmail() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail("bad-email-format");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid email address")));
    }

    @Test
    @DisplayName("21. PUT /api/staff/{id}/contact: Invalid phone format rejected with 400 Bad Request")
    void testUpdateContact_InvalidPhone() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("123");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", primaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid phone number")));
    }

    @Test
    @DisplayName("22. PUT /api/staff/{id}/contact: Non-Primary Owner cannot modify another Owner")
    void testUpdateContact_NonPrimaryOwnerCannotModifyOwner() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Hacked Owner Name");

        mockMvc.perform(put("/api/staff/" + primaryOwner.getId() + "/contact")
                        .header("Authorization", secondaryOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("23. PUT /api/staff/{id}/contact: Admin cannot modify Owner contact info")
    void testUpdateContact_AdminCannotModifyOwner() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Admin Tampering");

        mockMvc.perform(put("/api/staff/" + secondaryOwner.getId() + "/contact")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("24. PUT /api/staff/{id}/contact: Staff cannot modify another user's contact info")
    void testUpdateContact_StaffCannotModifyOtherUser() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Staff Tampering");

        mockMvc.perform(put("/api/staff/" + adminUser.getId() + "/contact")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("25. PUT /api/staff/{id}/contact: Customer cannot access staff contact endpoint")
    void testUpdateContact_CustomerAccessForbidden() throws Exception {
        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Customer Hack");

        mockMvc.perform(put("/api/staff/" + staffUser.getId() + "/contact")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("26. PUT /api/auth/me: Existing self-profile update continues to work")
    void testSelfProfileUpdate_ContinuesWorking() throws Exception {
        UpdateProfileRequest profileReq = new UpdateProfileRequest();
        profileReq.setFullName("Updated Customer Self");

        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Customer Self"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }
}
