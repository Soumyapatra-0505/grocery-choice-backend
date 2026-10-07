package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.DeliveryCompletionRequest;
import com.grocerychoice.backend.dto.DeliveryOtpVerifyRequest;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.repository.AddressRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3A Delivery Lifecycle Integration Tests.
 * Covers requirements T1 through T36:
 * - Package pickup & transit start (PROCESSING -> OUT_FOR_DELIVERY)
 * - Cryptographically secure order-bound delivery OTP generation & isolation
 * - Doorstep delivery OTP verification
 * - COD cash collection vs. Prepaid validation
 * - Delivery completion (OUT_FOR_DELIVERY -> DELIVERED)
 * - Security & RBAC boundaries across all roles
 * - Audit trail verification (DELIVERY_PICKED_UP, DELIVERY_OTP_VERIFIED, COD_COLLECTED, DELIVERY_COMPLETED)
 * - Destination coordinates & landmark exposure
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeliveryLifecycleIntegrationTest {

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
    private AddressRepository addressRepository;

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

    private Address testAddress;

    private String ownerToken;
    private String adminToken;
    private String staffToken;
    private String customerToken;
    private String deliveryTokenA;
    private String deliveryTokenB;
    private String disabledDeliveryToken;

    @BeforeEach
    void setUp() {
        String testPasswordHash = passwordEncoder.encode("TestPassword123!");

        ownerUser = getOrCreateUser("owner.phase3@grocerychoice.com", "Main Owner", "+91 99000 10001",
                Role.OWNER, UserStatus.ACTIVE, true, "Store Owner", testPasswordHash);

        adminUser = getOrCreateUser("admin.phase3@grocerychoice.com", "Admin Manager", "+91 99000 10002",
                Role.ADMIN, UserStatus.ACTIVE, false, "Operations Lead", testPasswordHash);

        staffUser = getOrCreateUser("staff.phase3@grocerychoice.com", "Staff Member", "+91 99000 10003",
                Role.STAFF, UserStatus.ACTIVE, false, "Store Associate", testPasswordHash);

        customerUser = getOrCreateUser("customer.phase3@example.com", "Customer One", "+91 99000 10004",
                Role.CUSTOMER, UserStatus.ACTIVE, false, null, testPasswordHash);

        deliveryPartnerA = getOrCreateUser("delivery.a.phase3@grocerychoice.com", "Rider Alpha", "+91 99000 10005",
                Role.DELIVERY, UserStatus.ACTIVE, false, "Rider", testPasswordHash);

        deliveryPartnerB = getOrCreateUser("delivery.b.phase3@grocerychoice.com", "Rider Beta", "+91 99000 10006",
                Role.DELIVERY, UserStatus.ACTIVE, false, "Rider", testPasswordHash);

        disabledDeliveryPartner = getOrCreateUser("delivery.disabled.phase3@grocerychoice.com", "Rider Disabled", "+91 99000 10007",
                Role.DELIVERY, UserStatus.DISABLED, false, "Rider", testPasswordHash);

        // Address with explicit geo-coordinates and landmark
        testAddress = new Address();
        testAddress.setUser(customerUser);
        testAddress.setAddressLine1("Flat 502, Green Palms, Indiranagar");
        testAddress.setAddressLine2("100 Feet Road");
        testAddress.setCity("Bengaluru");
        testAddress.setState("Karnataka");
        testAddress.setPostalCode("560038");
        testAddress.setLandmark("Opposite Metro Station");
        testAddress.setLatitude(12.9784);
        testAddress.setLongitude(77.6408);
        testAddress.setIsDefault(true);
        testAddress = addressRepository.save(testAddress);

        ownerToken = "Bearer " + jwtTokenProvider.generateToken(ownerUser);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(adminUser);
        staffToken = "Bearer " + jwtTokenProvider.generateToken(staffUser);
        customerToken = "Bearer " + jwtTokenProvider.generateToken(customerUser);
        deliveryTokenA = "Bearer " + jwtTokenProvider.generateToken(deliveryPartnerA);
        deliveryTokenB = "Bearer " + jwtTokenProvider.generateToken(deliveryPartnerB);
        disabledDeliveryToken = "Bearer " + jwtTokenProvider.generateToken(disabledDeliveryPartner);
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

    private Order createTestOrder(String orderNumber, OrderStatus status, User deliveryPartner,
                                  boolean accepted, String paymentMethod, PaymentStatus paymentStatus) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUser(customerUser);
        order.setDeliveryAddress(testAddress);
        order.setDeliveryAddressText("Flat 502, Green Palms, Indiranagar, Bengaluru, KA 560038");
        order.setSubtotal(new BigDecimal("450.00"));
        order.setDeliveryCharge(new BigDecimal("40.00"));
        order.setTotalAmount(new BigDecimal("490.00"));
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        order.setPaymentMethod(paymentMethod != null ? paymentMethod : "Cash on Delivery");
        order.setDeliverySlot("Evening Slot");
        order.setDeliveryPartner(deliveryPartner);
        if (deliveryPartner != null) {
            order.setAssignedAt(LocalDateTime.now().minusMinutes(30));
            if (accepted) {
                order.setAcceptedAt(LocalDateTime.now().minusMinutes(20));
            }
        }
        return orderRepository.save(order);
    }

    // =========================================================================
    // T1 - T7: Package Pickup & OUT_FOR_DELIVERY
    // =========================================================================

    @Test
    @DisplayName("T1-T3: Accepted rider picks up PROCESSING order -> OUT_FOR_DELIVERY with pickedUpAt")
    void testT1_T3_PickupSuccess() throws Exception {
        Order order = createTestOrder("GC-P3-1-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(order.getId().intValue())))
                .andExpect(jsonPath("$.status", is("OUT_FOR_DELIVERY")))
                .andExpect(jsonPath("$.pickedUpAt").isNotEmpty())
                .andExpect(jsonPath("$.assignedDeliveryPartnerId", is(deliveryPartnerA.getId().intValue())));

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, updated.getStatus());
        assertNotNull(updated.getPickedUpAt());
        assertNotNull(updated.getDeliveryOtp());
    }

    @Test
    @DisplayName("T4: Pickup rejected before acceptance (400 Bad Request)")
    void testT4_PickupRejectedBeforeAcceptance() throws Exception {
        Order order = createTestOrder("GC-P3-4-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, false, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T5: Pickup rejected for unassigned order (403 Forbidden)")
    void testT5_PickupRejectedUnassignedOrder() throws Exception {
        Order order = createTestOrder("GC-P3-5-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, null, false, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T6: Pickup rejected for another rider's order (403 Forbidden)")
    void testT6_PickupRejectedAnotherRider() throws Exception {
        Order order = createTestOrder("GC-P3-6-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T7: Pickup rejected for invalid order status (e.g. PLACED or CONFIRMED) (400 Bad Request)")
    void testT7_PickupRejectedInvalidStatus() throws Exception {
        Order orderPlaced = createTestOrder("GC-P3-7A-" + System.currentTimeMillis(),
                OrderStatus.PLACED, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + orderPlaced.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isBadRequest());

        Order orderConfirmed = createTestOrder("GC-P3-7B-" + System.currentTimeMillis(),
                OrderStatus.CONFIRMED, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + orderConfirmed.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T8 - T13: Delivery OTP Generation & Doorstep Verification
    // =========================================================================

    @Test
    @DisplayName("T8-T9: Delivery OTP generated on pickup and NOT exposed in delivery partner response")
    void testT8_T9_OtpGeneratedAndMaskedForRider() throws Exception {
        Order order = createTestOrder("GC-P3-8-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryOtp").doesNotExist());

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertNotNull(reloaded.getDeliveryOtp());
        assertEquals(4, reloaded.getDeliveryOtp().length());
        assertTrue(reloaded.getDeliveryOtp().matches("^\\d{4}$"));
    }

    @Test
    @DisplayName("T10: Correct OTP verification succeeds")
    void testT10_CorrectOtpVerificationSucceeds() throws Exception {
        Order order = createTestOrder("GC-P3-10-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        order.setPickedUpAt(LocalDateTime.now().minusMinutes(10));
        order.setDeliveryOtp("7294");
        orderRepository.save(order);

        DeliveryOtpVerifyRequest req = new DeliveryOtpVerifyRequest("7294");

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/verify-otp")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryOtpVerified", is(true)))
                .andExpect(jsonPath("$.deliveryOtp").doesNotExist());

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertNotNull(updated.getDeliveryOtpVerifiedAt());
    }

    @Test
    @DisplayName("T11: Incorrect OTP rejected (400 Bad Request)")
    void testT11_IncorrectOtpRejected() throws Exception {
        Order order = createTestOrder("GC-P3-11-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        order.setPickedUpAt(LocalDateTime.now().minusMinutes(10));
        order.setDeliveryOtp("7294");
        orderRepository.save(order);

        DeliveryOtpVerifyRequest req = new DeliveryOtpVerifyRequest("0000");

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/verify-otp")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T12: OTP cannot be verified by another rider (403 Forbidden)")
    void testT12_OtpCannotBeVerifiedByAnotherRider() throws Exception {
        Order order = createTestOrder("GC-P3-12-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        order.setPickedUpAt(LocalDateTime.now().minusMinutes(10));
        order.setDeliveryOtp("7294");
        orderRepository.save(order);

        DeliveryOtpVerifyRequest req = new DeliveryOtpVerifyRequest("7294");

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/verify-otp")
                        .header("Authorization", deliveryTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T13: OTP verification cannot occur before OUT_FOR_DELIVERY (400 Bad Request)")
    void testT13_OtpVerificationFailsBeforeOutForDelivery() throws Exception {
        Order order = createTestOrder("GC-P3-13-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setDeliveryOtp("7294");
        orderRepository.save(order);

        DeliveryOtpVerifyRequest req = new DeliveryOtpVerifyRequest("7294");

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/verify-otp")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T14 - T18: COD Settlement vs. Prepaid Validation
    // =========================================================================

    @Test
    @DisplayName("T14: Prepaid order completes after OTP verification without COD collection")
    void testT14_PrepaidOrderCompletesWithoutCod() throws Exception {
        Order order = createTestOrder("GC-P3-14-" + System.currentTimeMillis(),
                OrderStatus.OUT_FOR_DELIVERY, deliveryPartnerA, true, "Online (Razorpay)", PaymentStatus.PAID);
        order.setPickedUpAt(LocalDateTime.now().minusMinutes(15));
        order.setDeliveryOtp("5555");
        order.setDeliveryOtpVerifiedAt(LocalDateTime.now().minusMinutes(2));
        orderRepository.save(order);

        DeliveryCompletionRequest req = new DeliveryCompletionRequest("Delivered safely", null, null);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERED")))
                .andExpect(jsonPath("$.paymentStatus", is("PAID")))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());
    }

    @Test
    @DisplayName("T15-T17: COD order requires codCollected=true, updates paymentStatus=PAID, logs COD_COLLECTED")
    void testT15_T17_CodOrderRequiresCashAndSetsPaid() throws Exception {
        Order order = createTestOrder("GC-P3-15-" + System.currentTimeMillis(),
                OrderStatus.OUT_FOR_DELIVERY, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setPickedUpAt(LocalDateTime.now().minusMinutes(15));
        order.setDeliveryOtp("6666");
        order.setDeliveryOtpVerifiedAt(LocalDateTime.now().minusMinutes(2));
        orderRepository.save(order);

        // Attempt completion with codCollected = false -> Must fail
        DeliveryCompletionRequest reqWithoutCod = new DeliveryCompletionRequest("Delivered", null, false);
        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqWithoutCod)))
                .andExpect(status().isBadRequest());

        // Attempt completion with codCollected = true -> Must succeed
        DeliveryCompletionRequest reqWithCod = new DeliveryCompletionRequest("Cash received ₹490", null, true);
        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqWithCod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERED")))
                .andExpect(jsonPath("$.paymentStatus", is("PAID")))
                .andExpect(jsonPath("$.codCollected", is(true)))
                .andExpect(jsonPath("$.codCollectedAt").isNotEmpty());

        List<AuditLog> codLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("COD_COLLECTED");
        assertFalse(codLogs.isEmpty());
        assertTrue(codLogs.get(0).getDetails().contains("490.00"));
    }

    @Test
    @DisplayName("T18: Prepaid order cannot be marked as COD collected (400 Bad Request)")
    void testT18_PrepaidOrderCannotBeMarkedCodCollected() throws Exception {
        Order order = createTestOrder("GC-P3-18-" + System.currentTimeMillis(),
                OrderStatus.OUT_FOR_DELIVERY, deliveryPartnerA, true, "Online (Razorpay)", PaymentStatus.PAID);
        order.setDeliveryOtp("7777");
        order.setDeliveryOtpVerifiedAt(LocalDateTime.now().minusMinutes(2));
        orderRepository.save(order);

        DeliveryCompletionRequest req = new DeliveryCompletionRequest("Delivered", null, true);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T19 - T24: Delivery Completion Logic & Validations
    // =========================================================================

    @Test
    @DisplayName("T19-T21: Completion sets DELIVERED, deliveredAt, and deliveryNotes")
    void testT19_T21_CompletionSuccess() throws Exception {
        Order order = createTestOrder("GC-P3-19-" + System.currentTimeMillis(),
                OrderStatus.OUT_FOR_DELIVERY, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setPickedUpAt(LocalDateTime.now().minusMinutes(20));
        order.setDeliveryOtp("8888");
        order.setDeliveryOtpVerifiedAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(order);

        DeliveryCompletionRequest req = new DeliveryCompletionRequest("Left with security guard as requested", null, true);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERED")))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty())
                .andExpect(jsonPath("$.deliveryNotes", is("Left with security guard as requested")));

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.DELIVERED, updated.getStatus());
        assertEquals("Left with security guard as requested", updated.getDeliveryNotes());
        assertNotNull(updated.getDeliveredAt());
    }

    @Test
    @DisplayName("T22: Completion rejected before OTP verification (400 Bad Request)")
    void testT22_CompletionRejectedBeforeOtp() throws Exception {
        Order order = createTestOrder("GC-P3-22-" + System.currentTimeMillis(),
                OrderStatus.OUT_FOR_DELIVERY, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setDeliveryOtp("9999");
        order.setDeliveryOtpVerifiedAt(null);
        orderRepository.save(order);

        DeliveryCompletionRequest req = new DeliveryCompletionRequest("Delivered", null, true);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T23: Completion rejected for another rider's order (403 Forbidden)")
    void testT23_CompletionRejectedAnotherRider() throws Exception {
        Order order = createTestOrder("GC-P3-23-" + System.currentTimeMillis(),
                OrderStatus.OUT_FOR_DELIVERY, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setDeliveryOtp("1234");
        order.setDeliveryOtpVerifiedAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(order);

        DeliveryCompletionRequest req = new DeliveryCompletionRequest("Delivered", null, true);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T24: Already delivered order cannot be completed again (400 Bad Request)")
    void testT24_AlreadyDeliveredCannotBeCompletedAgain() throws Exception {
        Order order = createTestOrder("GC-P3-24-" + System.currentTimeMillis(),
                OrderStatus.DELIVERED, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PAID);
        order.setDeliveredAt(LocalDateTime.now().minusMinutes(5));
        order.setDeliveryOtp("1234");
        order.setDeliveryOtpVerifiedAt(LocalDateTime.now().minusMinutes(6));
        orderRepository.save(order);

        DeliveryCompletionRequest req = new DeliveryCompletionRequest("Duplicate", null, true);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T25 - T28: Security & Role Boundaries
    // =========================================================================

    @Test
    @DisplayName("T25: Customer blocked from rider lifecycle endpoints (403 Forbidden)")
    void testT25_CustomerBlockedFromLifecycleEndpoints() throws Exception {
        Order order = createTestOrder("GC-P3-25-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/verify-otp")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"1234\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codCollected\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T26: Staff blocked from rider lifecycle endpoints (403 Forbidden)")
    void testT26_StaffBlockedFromLifecycleEndpoints() throws Exception {
        Order order = createTestOrder("GC-P3-26-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T27: Owner/Admin blocked from rider lifecycle endpoints (403 Forbidden)")
    void testT27_OwnerAdminBlockedFromLifecycleEndpoints() throws Exception {
        Order order = createTestOrder("GC-P3-27-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", ownerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T28: Disabled delivery partner blocked (403 / 400)")
    void testT28_DisabledRiderBlocked() throws Exception {
        Order order = createTestOrder("GC-P3-28-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, disabledDeliveryPartner, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", disabledDeliveryToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // T29 - T31: Audit Logs Verification
    // =========================================================================

    @Test
    @DisplayName("T29-T31: Lifecycle generates DELIVERY_PICKED_UP, DELIVERY_OTP_VERIFIED, and DELIVERY_COMPLETED audits")
    void testT29_T31_AuditEventsLifecycle() throws Exception {
        Order order = createTestOrder("GC-P3-29-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Online (Razorpay)", PaymentStatus.PAID);

        // 1. Pickup
        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk());

        List<AuditLog> pickupLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_PICKED_UP");
        assertFalse(pickupLogs.isEmpty());
        assertEquals(order.getId(), pickupLogs.get(0).getTargetId());
        assertEquals(deliveryPartnerA.getId(), pickupLogs.get(0).getActorId());
        // Verify OTP is not leaked in audit details
        Order fresh = orderRepository.findById(order.getId()).orElseThrow();
        assertFalse(pickupLogs.get(0).getDetails().contains(fresh.getDeliveryOtp()));

        // 2. Verify OTP
        DeliveryOtpVerifyRequest otpReq = new DeliveryOtpVerifyRequest(fresh.getDeliveryOtp());
        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/verify-otp")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpReq)))
                .andExpect(status().isOk());

        List<AuditLog> otpLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_OTP_VERIFIED");
        assertFalse(otpLogs.isEmpty());
        assertEquals(order.getId(), otpLogs.get(0).getTargetId());

        // 3. Complete
        DeliveryCompletionRequest compReq = new DeliveryCompletionRequest("Delivered safely", null, null);
        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/complete")
                        .header("Authorization", deliveryTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compReq)))
                .andExpect(status().isOk());

        List<AuditLog> compLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_COMPLETED");
        assertFalse(compLogs.isEmpty());
        assertEquals(order.getId(), compLogs.get(0).getTargetId());
    }

    // =========================================================================
    // T32 - T36: Coordinates, Customer OTP Visibility & Idempotency
    // =========================================================================

    @Test
    @DisplayName("T32: Destination latitude/longitude/landmark returned in OrderResponse")
    void testT32_CoordinatesAndLandmarkReturned() throws Exception {
        Order order = createTestOrder("GC-P3-32-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        mockMvc.perform(get("/api/delivery/orders/assigned")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryAddressLatitude", is(12.9784)))
                .andExpect(jsonPath("$[0].deliveryAddressLongitude", is(77.6408)))
                .andExpect(jsonPath("$[0].deliveryAddressLandmark", is("Opposite Metro Station")));
    }

    @Test
    @DisplayName("T35: Customer can see delivery OTP in their order response")
    void testT35_CustomerCanSeeDeliveryOtp() throws Exception {
        Order order = createTestOrder("GC-P3-35-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        order.setDeliveryOtp("4321");
        orderRepository.save(order);

        mockMvc.perform(get("/api/orders/" + order.getId())
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryOtp", is("4321")));
    }

    @Test
    @DisplayName("T36: Idempotent pickup does not create duplicate audit log")
    void testT36_IdempotentPickup() throws Exception {
        Order order = createTestOrder("GC-P3-36-" + System.currentTimeMillis(),
                OrderStatus.PROCESSING, deliveryPartnerA, true, "Cash on Delivery", PaymentStatus.PENDING);

        // First pickup
        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk());

        int countAfterFirst = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_PICKED_UP").size();

        // Second pickup (retry)
        mockMvc.perform(post("/api/delivery/orders/" + order.getId() + "/pickup")
                        .header("Authorization", deliveryTokenA))
                .andExpect(status().isOk());

        int countAfterSecond = auditLogRepository.findByActionOrderByCreatedAtDesc("DELIVERY_PICKED_UP").size();
        assertEquals(countAfterFirst, countAfterSecond, "Idempotent pickup retry must not create duplicate audit events");
    }
}
