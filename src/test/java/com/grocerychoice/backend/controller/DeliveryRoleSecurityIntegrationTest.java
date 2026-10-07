package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.ChangeRoleRequest;
import com.grocerychoice.backend.dto.CreateStaffRequest;
import com.grocerychoice.backend.dto.OwnerLoginRequest;
import com.grocerychoice.backend.dto.TransferOwnershipRequest;
import com.grocerychoice.backend.entity.Permission;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 1 Delivery Role & Security Isolation Integration Tests.
 * Strictly verifies role isolation, authentication flow, profile access,
 * and guarantees that Role.DELIVERY is forbidden from catalog, inventory,
 * store-wide orders, staff, designations, and ownership endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeliveryRoleSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User deliveryUser;
    private User customerUser;
    private User staffUser;
    private User adminUser;
    private User ownerUser;

    private String deliveryToken;
    private String customerToken;
    private String staffToken;
    private String adminToken;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        String testPasswordHash = passwordEncoder.encode("TestPassword123!");

        deliveryUser = userRepository.findByEmail("test.delivery.partner@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Raju Delivery");
                    u.setEmail("test.delivery.partner@grocerychoice.com");
                    u.setPhone("+91 99887 76655");
                    u.setPasswordHash(testPasswordHash);
                    u.setRole(Role.DELIVERY);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Delivery Executive");
                    u.setStoreHub("Central Hub");
                    return userRepository.save(u);
                });
        deliveryUser.setRole(Role.DELIVERY);
        deliveryUser.setStatus(UserStatus.ACTIVE);
        deliveryUser.setPasswordHash(testPasswordHash);
        userRepository.save(deliveryUser);

        customerUser = userRepository.findByEmail("test.delivery.customer@example.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Regular Customer");
                    u.setEmail("test.delivery.customer@example.com");
                    u.setPhone("+91 99887 76650");
                    u.setPasswordHash(testPasswordHash);
                    u.setRole(Role.CUSTOMER);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    return userRepository.save(u);
                });
        customerUser.setRole(Role.CUSTOMER);
        customerUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(customerUser);

        staffUser = userRepository.findByEmail("test.delivery.staff@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Store Staff Member");
                    u.setEmail("test.delivery.staff@grocerychoice.com");
                    u.setPhone("+91 99887 76651");
                    u.setPasswordHash(testPasswordHash);
                    u.setRole(Role.STAFF);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Inventory Associate");
                    return userRepository.save(u);
                });
        staffUser.setRole(Role.STAFF);
        staffUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(staffUser);

        adminUser = userRepository.findByEmail("test.delivery.admin@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Store Admin Manager");
                    u.setEmail("test.delivery.admin@grocerychoice.com");
                    u.setPhone("+91 99887 76652");
                    u.setPasswordHash(testPasswordHash);
                    u.setRole(Role.ADMIN);
                    u.setPrimaryOwner(false);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Operations Manager");
                    return userRepository.save(u);
                });
        adminUser.setRole(Role.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(adminUser);

        ownerUser = userRepository.findByEmail("test.delivery.owner@grocerychoice.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Store Primary Owner");
                    u.setEmail("test.delivery.owner@grocerychoice.com");
                    u.setPhone("+91 99887 76653");
                    u.setPasswordHash(testPasswordHash);
                    u.setRole(Role.OWNER);
                    u.setPrimaryOwner(true);
                    u.setStatus(UserStatus.ACTIVE);
                    u.setDesignation("Owner");
                    return userRepository.save(u);
                });
        ownerUser.setRole(Role.OWNER);
        ownerUser.setPrimaryOwner(true);
        ownerUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(ownerUser);

        deliveryToken = "Bearer " + jwtTokenProvider.generateToken(deliveryUser);
        customerToken = "Bearer " + jwtTokenProvider.generateToken(customerUser);
        staffToken = "Bearer " + jwtTokenProvider.generateToken(staffUser);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(adminUser);
        ownerToken = "Bearer " + jwtTokenProvider.generateToken(ownerUser);
    }

    // ==========================================
    // A. DELIVERY Authentication & Token Tests
    // ==========================================

    @Test
    @DisplayName("A1. DELIVERY account can authenticate via existing owner-login mechanism")
    void testDeliveryAuthentication_Success() throws Exception {
        OwnerLoginRequest loginRequest = new OwnerLoginRequest();
        loginRequest.setIdentifier("test.delivery.partner@grocerychoice.com");
        loginRequest.setPassword("TestPassword123!");

        MvcResult result = mockMvc.perform(post("/api/auth/owner-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role", is("DELIVERY")))
                .andExpect(jsonPath("$.user.email", is("test.delivery.partner@grocerychoice.com")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(responseBody, Map.class);
        String token = (String) map.get("token");

        // Verify token claims
        assertEquals("DELIVERY", jwtTokenProvider.getRoleFromToken(token));
        assertEquals(deliveryUser.getId(), jwtTokenProvider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("A2. DELIVERY entity and principal helper methods verify correctly")
    void testDeliveryRoleHelpers() {
        assertTrue(deliveryUser.isDelivery());
        assertFalse(deliveryUser.isStaff());
        assertFalse(deliveryUser.isAdmin());
        assertFalse(deliveryUser.isOwner());
        assertFalse(deliveryUser.isCustomer());

        assertEquals(Role.DELIVERY, deliveryUser.getRole());
        assertTrue(deliveryUser.getEffectivePermissions().contains(Permission.MANAGE_DELIVERY));
        assertEquals(1, deliveryUser.getEffectivePermissions().size());
        assertFalse(deliveryUser.getEffectivePermissions().contains(Permission.MANAGE_PRODUCTS));
        assertFalse(deliveryUser.getEffectivePermissions().contains(Permission.MANAGE_INVENTORY));
        assertFalse(deliveryUser.getEffectivePermissions().contains(Permission.MANAGE_ORDERS));
        assertFalse(deliveryUser.getEffectivePermissions().contains(Permission.MANAGE_STAFF));
    }

    // ==========================================
    // B. Profile Tests
    // ==========================================

    @Test
    @DisplayName("B1. DELIVERY user can GET /api/auth/me to view personal profile")
    void testDeliveryGetProfile_Success() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", deliveryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(deliveryUser.getId().intValue())))
                .andExpect(jsonPath("$.email", is("test.delivery.partner@grocerychoice.com")))
                .andExpect(jsonPath("$.role", is("DELIVERY")))
                .andExpect(jsonPath("$.fullName", is("Raju Delivery")));
    }

    // ==========================================
    // C. Inventory & Stock Protection Tests
    // ==========================================

    @Test
    @DisplayName("C1. DELIVERY is blocked (403 Forbidden) on PATCH /api/products/{id} (stock update)")
    void testDeliveryCannotUpdateProductStock() throws Exception {
        mockMvc.perform(patch("/api/products/1")
                        .header("Authorization", deliveryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\": 50}"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // D. Catalog Protection Tests
    // ==========================================

    @Test
    @DisplayName("D1. DELIVERY is blocked (403 Forbidden) on POST /api/products")
    void testDeliveryCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", deliveryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Apples\",\"price\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D2. DELIVERY is blocked (403 Forbidden) on POST /api/categories")
    void testDeliveryCannotCreateCategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", deliveryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fresh Dairy\"}"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // E. Store Order Protection Tests
    // ==========================================

    @Test
    @DisplayName("E1. DELIVERY is blocked (403 Forbidden) on GET /api/orders (store-wide order list)")
    void testDeliveryCannotListAllStoreOrders() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", deliveryToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("E2. DELIVERY is blocked (403 Forbidden) on GET /api/orders/status/PROCESSING (status filtering)")
    void testDeliveryCannotFilterStoreOrdersByStatus() throws Exception {
        mockMvc.perform(get("/api/orders/status/PROCESSING")
                        .header("Authorization", deliveryToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // F. Staff Protection Tests
    // ==========================================

    @Test
    @DisplayName("F1. DELIVERY is blocked (403 Forbidden) on GET /api/staff")
    void testDeliveryCannotListStaff() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", deliveryToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("F2. DELIVERY is blocked (403 Forbidden) on POST /api/staff")
    void testDeliveryCannotCreateStaff() throws Exception {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setFullName("Malicious Delivery Creation");
        req.setEmail("hack@grocerychoice.com");
        req.setRole(Role.STAFF);

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", deliveryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("F3. DELIVERY is blocked (403 Forbidden) on PATCH /api/staff/{id}/role")
    void testDeliveryCannotChangeStaffRole() throws Exception {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole(Role.ADMIN);

        mockMvc.perform(patch("/api/staff/" + staffUser.getId() + "/role")
                        .header("Authorization", deliveryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("F4. DELIVERY is blocked (403 Forbidden) on GET /api/designations")
    void testDeliveryCannotAccessDesignations() throws Exception {
        mockMvc.perform(get("/api/designations")
                        .header("Authorization", deliveryToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // G. Ownership Protection Tests
    // ==========================================

    @Test
    @DisplayName("G1. DELIVERY is blocked (403 Forbidden) on GET /api/ownership/primary-owner")
    void testDeliveryCannotAccessPrimaryOwnerEndpoint() throws Exception {
        mockMvc.perform(get("/api/ownership/primary-owner")
                        .header("Authorization", deliveryToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("G2. DELIVERY is blocked (403 Forbidden) on POST /api/ownership/transfer")
    void testDeliveryCannotTransferOwnership() throws Exception {
        TransferOwnershipRequest req = new TransferOwnershipRequest();
        req.setNewPrimaryOwnerId(deliveryUser.getId());
        req.setPreviousOwnerNewRole(Role.STAFF);
        req.setConfirmKeyword("TRANSFER");

        mockMvc.perform(post("/api/ownership/transfer")
                        .header("Authorization", deliveryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // H. Reserved Delivery Endpoints Tests
    // ==========================================

    @Test
    @DisplayName("H1. Reserved /api/delivery/** blocks CUSTOMER (403 Forbidden)")
    void testReservedDeliveryPath_BlocksCustomer() throws Exception {
        mockMvc.perform(get("/api/delivery/test-route")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H2. Reserved /api/delivery/** allows DELIVERY role past security filter (not 403 Forbidden)")
    void testReservedDeliveryPath_AllowsDeliveryPastSecurity() throws Exception {
        // DELIVERY has permission for /api/delivery/** so security filter passes (not 403 Forbidden)
        mockMvc.perform(get("/api/delivery/test-route")
                        .header("Authorization", deliveryToken))
                .andExpect(status().is(not(403)));
    }

    // ==========================================
    // I. Existing Roles Regression Tests
    // ==========================================

    @Test
    @DisplayName("I1. CUSTOMER role behavior unchanged: blocked from staff and store orders")
    void testCustomerRole_RemainsIsolated() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("I2. STAFF role behavior unchanged: can list store orders, blocked from staff management")
    void testStaffRole_RemainsOperational() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", staffToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/staff")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("I3. ADMIN role behavior unchanged: can list staff and store orders")
    void testAdminRole_RemainsOperational() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("I4. OWNER role behavior unchanged: can list staff and store orders")
    void testOwnerRole_RemainsOperational() throws Exception {
        mockMvc.perform(get("/api/staff")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk());
    }
}
