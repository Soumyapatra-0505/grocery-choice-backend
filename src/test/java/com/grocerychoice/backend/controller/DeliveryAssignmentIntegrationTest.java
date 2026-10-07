package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.DeliveryAssignmentRequest;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.repository.AuditLogRepository;
import com.grocerychoice.backend.repository.OrderRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 2 Delivery Assignment Integration Tests.
 * Covers requirements T1 through T19:
 * - Assignment by Owner, Admin, Staff
 * - Target role & status validation
 * - Order lifecycle state constraints (PROCESSING only)
 * - Reassignment & idempotency
 * - Isolation of assigned orders by delivery rider
 * - Rider acceptance logic
 * - RBAC security verification
 * - Audit logging
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeliveryAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User ownerUser;
    private User adminUser;
    private User staffUser;
    private User customerUser;
    private User deliveryPartnerA;
    private User deliveryPartnerB;
    private User disabledDeliveryPartner;

    private String ownerToken;
    private String adminToken;
    private String staffToken;
    private String customerToken;
    private String deliveryTokenA;
    private String deliveryTokenB;

    @BeforeEach
    void setUp() {
        String testPasswordHash = passwordEncoder.encode("TestPassword123!");

        ownerUser = getOrCreateUser("owner.phase2@grocerychoice.com", "Main Owner", "+91 99000 00001",
                Role.OWNER, UserStatus.ACTIVE, true, "Store Owner", testPasswordHash);

        adminUser = getOrCreateUser("admin.phase2@grocerychoice.com", "Admin Manager", "+91 99000 00002",
                Role.ADMIN, UserStatus.ACTIVE, false, "Operations Lead", testPasswordHash);

        staffUser = getOrCreateUser("staff.phase2@grocerychoice.com", "Staff Member", "+91 99000 00003",
                Role.STAFF, UserStatus.ACTIVE, false, "Store Associate", testPasswordHash);

        customerUser = getOrCreateUser("customer.phase2@example.com", "Customer One", "+91 99000 00004",
                Role.CUSTOMER, UserStatus.ACTIVE, false, null, testPasswordHash);

        deliveryPartnerA = getOrCreateUser("delivery.a.phase2@grocerychoice.com", "Rider Alpha", "+91 99000 00005",
                Role.DELIVERY, UserStatus.ACTIVE, false, "Rider", testPasswordHash);

        deliveryPartnerB = getOrCreateUser("delivery.b.phase2@grocerychoice.com", "Rider Beta", "+91 99000 00006",
                Role.DELIVERY, UserStatus.ACTIVE, false, "Rider", testPasswordHash);

        disabledDeliveryPartner = getOrCreateUser("delivery.disabled.phase2@grocerychoice.com", "Rider Disabled", "+91 99000 00007",
                Role.DELIVERY, UserStatus.DISABLED, false, "Rider", testPasswordHash);

        ownerToken = "Bearer " + jwtTokenProvider.generateToken(ownerUser);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(adminUser);
        staffToken = "Bearer " + jwtTokenProvider.generateToken(staffUser);
        customerToken = "Bearer " + jwtTokenProvider.generateToken(customerUser);
        deliveryTokenA = "Bearer " + jwtTokenProvider.generateToken(deliveryPartnerA);
        deliveryTokenB = "Bearer " + jwtTokenProvider.generateToken(deliveryPartnerB);
    }

    private User getOrCreateUser(String email, String fullName, String phone, Role role,
                                 UserStatus status, boolean primaryOwner, String designation, String passwordHash) {
        return userRepository.findByEmail(email).map(u -> {
            u.setFullName(fullName);
            u.setPhone(phone);
            u.setRole(role);
            u.setStatus(status);
            u.setPrimaryOwner(primaryOwner);
            u.setDesignation(designation);
            u.setPasswordHash(passwordHash);
            return userRepository.save(u);
        }).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(fullName);
            u.setPhone(phone);
            u.setRole(role);
            u.setStatus(status);
            u.setPrimaryOwner(primaryOwner);
            u.setDesignation(designation);
            u.setPasswordHash(passwordHash);
            return userRepository.save(u);
        });
    }

    private Order createTestOrder(String orderNumber, OrderStatus status, User deliveryPartner) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUser(customerUser);
        order.setDeliveryAddressText("77 MG Road, Bengaluru, KA 560001");
        order.setSubtotal(new BigDecimal("350.00"));
        order.setDeliveryCharge(new BigDecimal("40.00"));
        order.setTotalAmount(new BigDecimal("390.00"));
        order.setStatus(status);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod("UPI");
        order.setDeliverySlot("Standard Delivery");
        order.setDeliveryPartner(deliveryPartner);
        if (deliveryPartner != null) {
            order.setAssignedAt(LocalDateTime.now());
        }
        return orderRepository.save(order);
    }

    // =========================================================================
    // T1 - T3: Assignment by Allowed Roles
    // =========================================================================

    @Test
    @DisplayName("T1: Owner assigns active DELIVERY partner to PROCESSING order")
    void testT1_OwnerAssignsActiveDeliveryPartner() throws Exception {
        Order order = createTestOrder("GC-T1-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(order.getId().intValue())))
                .andExpect(jsonPath("$.assignedDeliveryPartnerId", is(deliveryPartnerA.getId().intValue())))
                .andExpect(jsonPath("$.assignedDeliveryPartnerName", is("Rider Alpha")))
                .andExpect(jsonPath("$.assignedDeliveryPartnerPhone", is("+91 99000 00005")))
                .andExpect(jsonPath("$.assignedAt").isNotEmpty())
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.status", is("PROCESSING")));

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_ASSIGNED");
        assertFalse(logs.isEmpty());
        AuditLog latest = logs.get(0);
        assertEquals(deliveryPartnerA.getId(), latest.getTargetId());
        assertEquals("DELIVERY_ASSIGNED", latest.getAction());
    }

    @Test
    @DisplayName("T2: Admin assigns active DELIVERY partner to PROCESSING order")
    void testT2_AdminAssignsActiveDeliveryPartner() throws Exception {
        Order order = createTestOrder("GC-T2-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedDeliveryPartnerId", is(deliveryPartnerA.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PROCESSING")));
    }

    @Test
    @DisplayName("T3: Staff assigns active DELIVERY partner to PROCESSING order")
    void testT3_StaffAssignsActiveDeliveryPartner() throws Exception {
        Order order = createTestOrder("GC-T3-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedDeliveryPartnerId", is(deliveryPartnerA.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PROCESSING")));
    }

    // =========================================================================
    // T4 - T7: Target Role & Status Validations
    // =========================================================================

    @Test
    @DisplayName("T4: CUSTOMER target rejected (400 Bad Request)")
    void testT4_CustomerTargetRejected() throws Exception {
        Order order = createTestOrder("GC-T4-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(customerUser.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T5: STAFF target rejected (400 Bad Request)")
    void testT5_StaffTargetRejected() throws Exception {
        Order order = createTestOrder("GC-T5-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(staffUser.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T6: OWNER target rejected (400 Bad Request)")
    void testT6_OwnerTargetRejected() throws Exception {
        Order order = createTestOrder("GC-T6-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(ownerUser.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T7: Disabled DELIVERY target rejected (400 Bad Request)")
    void testT7_DisabledDeliveryTargetRejected() throws Exception {
        Order order = createTestOrder("GC-T7-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(disabledDeliveryPartner.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T8 - T11: Order Lifecycle Status Validations (PROCESSING only)
    // =========================================================================

    @Test
    @DisplayName("T8: PLACED order rejected for assignment (400 Bad Request)")
    void testT8_PlacedOrderRejected() throws Exception {
        Order order = createTestOrder("GC-T8-" + System.currentTimeMillis(), OrderStatus.PLACED, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T9: CONFIRMED order rejected for assignment (400 Bad Request)")
    void testT9_ConfirmedOrderRejected() throws Exception {
        Order order = createTestOrder("GC-T9-" + System.currentTimeMillis(), OrderStatus.CONFIRMED, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T10: DELIVERED order rejected for assignment (400 Bad Request)")
    void testT10_DeliveredOrderRejected() throws Exception {
        Order order = createTestOrder("GC-T10-" + System.currentTimeMillis(), OrderStatus.DELIVERED, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T11: CANCELLED order rejected for assignment (400 Bad Request)")
    void testT11_CancelledOrderRejected() throws Exception {
        Order order = createTestOrder("GC-T11-" + System.currentTimeMillis(), OrderStatus.CANCELLED, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T12: Reassignment
    // =========================================================================

    @Test
    @DisplayName("T12: Reassignment Partner A -> Partner B resets acceptedAt and writes DELIVERY_REASSIGNED audit")
    void testT12_ReassignmentPartnerAtoB() throws Exception {
        Order order = createTestOrder("GC-T12-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);
        order.setAcceptedAt(LocalDateTime.now().minusMinutes(5));
        orderRepository.save(order);

        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerB.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedDeliveryPartnerId", is(deliveryPartnerB.getId().intValue())))
                .andExpect(jsonPath("$.assignedDeliveryPartnerName", is("Rider Beta")))
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.status", is("PROCESSING")));

        List<AuditLog> reassignLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_REASSIGNED");
        assertFalse(reassignLogs.isEmpty());
        assertEquals(deliveryPartnerB.getId(), reassignLogs.get(0).getTargetId());
    }

    // =========================================================================
    // T13 - T14: Assigned Orders Visibility Isolation
    // =========================================================================

    @Test
    @DisplayName("T13: Delivery partner sees only own assigned orders")
    void testT13_DeliveryPartnerSeesOwnAssignedOrders() throws Exception {
        Order orderA = createTestOrder("GC-T13-A-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);
        Order orderB = createTestOrder("GC-T13-B-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerB);

        mockMvc.perform(get("/api/delivery/orders/assigned")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].assignedDeliveryPartnerId", everyItem(is(deliveryPartnerA.getId().intValue()))))
                .andExpect(jsonPath("$[*].id", hasItem(orderA.getId().intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(orderB.getId().intValue()))));
    }

    @Test
    @DisplayName("T14: Delivery partner cannot see another rider's assignments")
    void testT14_DeliveryPartnerCannotSeeAnotherRidersOrders() throws Exception {
        Order orderA = createTestOrder("GC-T14-A-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);

        mockMvc.perform(get("/api/delivery/orders/assigned")
                        .header("Authorization", deliveryTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(orderA.getId().intValue()))));
    }

    // =========================================================================
    // T15 - T17: Assignment Acceptance Logic
    // =========================================================================

    @Test
    @DisplayName("T15: Delivery partner accepts own assigned order successfully")
    void testT15_DeliveryPartnerAcceptsOwnAssignedOrder() throws Exception {
        Order order = createTestOrder("GC-T15-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/accept")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(order.getId().intValue())))
                .andExpect(jsonPath("$.assignedDeliveryPartnerId", is(deliveryPartnerA.getId().intValue())))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.status", is("PROCESSING")));

        List<AuditLog> acceptLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_ACCEPTED");
        assertFalse(acceptLogs.isEmpty());
        assertEquals(deliveryPartnerA.getId(), acceptLogs.get(0).getActorId());
    }

    @Test
    @DisplayName("T16: Delivery partner cannot accept another rider's order (403 Forbidden)")
    void testT16_DeliveryPartnerCannotAcceptAnotherRidersOrder() throws Exception {
        Order order = createTestOrder("GC-T16-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/accept")
                        .header("Authorization", deliveryTokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T17: Delivery partner cannot re-accept already accepted order (400 Bad Request)")
    void testT17_DeliveryPartnerCannotReacceptAcceptedOrder() throws Exception {
        Order order = createTestOrder("GC-T17-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);
        order.setAcceptedAt(LocalDateTime.now());
        orderRepository.save(order);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/accept")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T18 - T19: RBAC Security Boundaries
    // =========================================================================

    @Test
    @DisplayName("T18: Customer cannot access delivery endpoints (403 Forbidden)")
    void testT18_CustomerCannotAccessDeliveryEndpoints() throws Exception {
        Order order = createTestOrder("GC-T18-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);

        mockMvc.perform(get("/api/delivery/orders/assigned")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/accept")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T19: Delivery partner cannot assign orders or list eligible partners (403 Forbidden)")
    void testT19_DeliveryPartnerCannotAssignOrders() throws Exception {
        Order order = createTestOrder("GC-T19-" + System.currentTimeMillis(), OrderStatus.PROCESSING, null);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/eligible-delivery-partners")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // T20 - T21: Additional Idempotency & Eligible Partners Tests
    // =========================================================================

    @Test
    @DisplayName("T20: Idempotent assignment of same partner returns 200 without duplicate state")
    void testT20_IdempotentAssignment() throws Exception {
        Order order = createTestOrder("GC-T20-" + System.currentTimeMillis(), OrderStatus.PROCESSING, deliveryPartnerA);
        DeliveryAssignmentRequest req = new DeliveryAssignmentRequest(deliveryPartnerA.getId());

        int initialLogCount = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_ASSIGNED").size();

        mockMvc.perform(post("/api/orders/" + order.getId() + "/delivery-assignment")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedDeliveryPartnerId", is(deliveryPartnerA.getId().intValue())));

        int subsequentLogCount = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_ASSIGNED").size();
        assertEquals(initialLogCount, subsequentLogCount, "Idempotent assignment should not write duplicate audit logs");
    }

    @Test
    @DisplayName("T21: Owner/Admin/Staff can fetch eligible delivery partners (active DELIVERY only)")
    void testT21_EligibleDeliveryPartnersListing() throws Exception {
        mockMvc.perform(get("/api/orders/eligible-delivery-partners")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role", everyItem(is("DELIVERY"))))
                .andExpect(jsonPath("$[*].status", everyItem(is("ACTIVE"))))
                .andExpect(jsonPath("$[*].id", hasItem(deliveryPartnerA.getId().intValue())))
                .andExpect(jsonPath("$[*].id", hasItem(deliveryPartnerB.getId().intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(disabledDeliveryPartner.getId().intValue()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(customerUser.getId().intValue()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(staffUser.getId().intValue()))));
    }
}
