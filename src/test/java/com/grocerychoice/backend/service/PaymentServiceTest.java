package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.PaymentVerificationRequest;
import com.grocerychoice.backend.dto.PaymentVerificationResponse;
import com.grocerychoice.backend.dto.RazorpayOrderResponse;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RazorpayService razorpayService;

    @Mock
    private NotificationService notificationService;

    private PaymentService paymentService;

    private User customerUser;
    private User otherCustomerUser;
    private UserPrincipal customerPrincipal;
    private UserPrincipal otherCustomerPrincipal;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(orderRepository, razorpayService, notificationService);

        customerUser = new User();
        customerUser.setId(10L);
        customerUser.setFullName("Ananya Sen");
        customerUser.setEmail("ananya@example.com");
        customerUser.setRole(Role.CUSTOMER);

        otherCustomerUser = new User();
        otherCustomerUser.setId(20L);
        otherCustomerUser.setFullName("Other User");
        otherCustomerUser.setEmail("other@example.com");
        otherCustomerUser.setRole(Role.CUSTOMER);

        customerPrincipal = UserPrincipal.create(customerUser);
        otherCustomerPrincipal = UserPrincipal.create(otherCustomerUser);

        sampleOrder = new Order();
        sampleOrder.setId(101L);
        sampleOrder.setOrderNumber("GC-2026-00101");
        sampleOrder.setUser(customerUser);
        sampleOrder.setSubtotal(new BigDecimal("459.00"));
        sampleOrder.setDeliveryCharge(BigDecimal.ZERO);
        sampleOrder.setTotalAmount(new BigDecimal("459.00"));
        sampleOrder.setStatus(OrderStatus.PLACED);
        sampleOrder.setPaymentStatus(PaymentStatus.PENDING);
        sampleOrder.setPaymentMethod("Online (Razorpay)");
    }

    // =========================================================================
    // Create Payment Order Tests
    // =========================================================================

    @Test
    @DisplayName("createPaymentOrder successfully creates Razorpay order and returns correct paise amount")
    void testCreatePaymentOrder_Success() {
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));
        when(razorpayService.createRazorpayOrder(45900L, "GC-2026-00101", 101L))
                .thenReturn("order_rzp_mock_123");
        when(razorpayService.getKeyId()).thenReturn("rzp_test_mockKey");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RazorpayOrderResponse response = paymentService.createPaymentOrder(101L, customerPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getRazorpayOrderId()).isEqualTo("order_rzp_mock_123");
        assertThat(response.getRazorpayKeyId()).isEqualTo("rzp_test_mockKey");
        assertThat(response.getAmount()).isEqualTo(45900L); // 459.00 * 100 paise
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getOrderId()).isEqualTo(101L);

        // Verify order entity was updated with razorpayOrderId
        assertThat(sampleOrder.getRazorpayOrderId()).isEqualTo("order_rzp_mock_123");
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("createPaymentOrder enforces customer ownership: Customer A cannot pay for Customer B's order")
    void testCreatePaymentOrder_ForbiddenForOtherCustomer() {
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> paymentService.createPaymentOrder(101L, otherCustomerPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("You do not have permission to pay for this order");

        verify(razorpayService, never()).createRazorpayOrder(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("createPaymentOrder rejects already paid order")
    void testCreatePaymentOrder_AlreadyPaid() {
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> paymentService.createPaymentOrder(101L, customerPrincipal))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Order has already been paid");
    }

    @Test
    @DisplayName("createPaymentOrder rejects cancelled order")
    void testCreatePaymentOrder_CancelledOrder() {
        sampleOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> paymentService.createPaymentOrder(101L, customerPrincipal))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Cannot initiate payment for a cancelled order");
    }

    @Test
    @DisplayName("createPaymentOrder throws ResourceNotFoundException if order does not exist")
    void testCreatePaymentOrder_NotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPaymentOrder(999L, customerPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with ID: 999");
    }

    // =========================================================================
    // Verify Payment Tests
    // =========================================================================

    @Test
    @DisplayName("verifyPayment successfully verifies signature and marks order as PAID")
    void testVerifyPayment_Success() {
        sampleOrder.setRazorpayOrderId("order_rzp_mock_123");
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));
        when(razorpayService.verifySignature("order_rzp_mock_123", "pay_mock_456", "valid_sig_789"))
                .thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_rzp_mock_123",
                "pay_mock_456",
                "valid_sig_789"
        );

        PaymentVerificationResponse response = paymentService.verifyPayment(request, customerPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.getOrderId()).isEqualTo(101L);
        assertThat(response.getOrderNumber()).isEqualTo("GC-2026-00101");

        // Verify entity state updates
        assertThat(sampleOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(sampleOrder.getRazorpayPaymentId()).isEqualTo("pay_mock_456");
        assertThat(sampleOrder.getRazorpaySignature()).isEqualTo("valid_sig_789");
        assertThat(sampleOrder.getPaymentMethod()).isEqualTo("Online (Razorpay)");

        verify(orderRepository).save(sampleOrder);
        verify(notificationService).sendOrderConfirmation(sampleOrder);
    }

    @Test
    @DisplayName("verifyPayment rejects invalid signature and does not mark order as PAID or send notification")
    void testVerifyPayment_InvalidSignature() {
        sampleOrder.setRazorpayOrderId("order_rzp_mock_123");
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));
        when(razorpayService.verifySignature("order_rzp_mock_123", "pay_mock_456", "fake_sig"))
                .thenReturn(false);

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_rzp_mock_123",
                "pay_mock_456",
                "fake_sig"
        );

        assertThatThrownBy(() -> paymentService.verifyPayment(request, customerPrincipal))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Invalid payment signature");

        // Ensure order was NOT marked as PAID and notification was NOT sent
        assertThat(sampleOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(orderRepository, never()).save(any(Order.class));
        verify(notificationService, never()).sendOrderConfirmation(any());
    }

    @Test
    @DisplayName("Rule 5 & 8/9: verifyPayment triggers notification on success, and notification failure does not affect payment outcome")
    void testVerifyPayment_NotificationFailureDoesNotFailPayment() {
        sampleOrder.setRazorpayOrderId("order_rzp_mock_123");
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));
        when(razorpayService.verifySignature("order_rzp_mock_123", "pay_mock_456", "valid_sig_789"))
                .thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("SMS/Email service down"))
                .when(notificationService).sendOrderConfirmation(any(Order.class));

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_rzp_mock_123",
                "pay_mock_456",
                "valid_sig_789"
        );

        PaymentVerificationResponse response = paymentService.verifyPayment(request, customerPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(sampleOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(notificationService).sendOrderConfirmation(sampleOrder);
    }

    @Test
    @DisplayName("verifyPayment rejects mismatched Razorpay order ID")
    void testVerifyPayment_MismatchedOrderId() {
        sampleOrder.setRazorpayOrderId("order_ORIGINAL_123");
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_DIFFERENT_456",
                "pay_mock_456",
                "sig_123"
        );

        assertThatThrownBy(() -> paymentService.verifyPayment(request, customerPrincipal))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Razorpay order ID does not match Grocery Choice order");

        verify(razorpayService, never()).verifySignature(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("verifyPayment prevents duplicate verification if order is already PAID")
    void testVerifyPayment_AlreadyPaid() {
        sampleOrder.setRazorpayOrderId("order_rzp_mock_123");
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_rzp_mock_123",
                "pay_mock_456",
                "sig_123"
        );

        assertThatThrownBy(() -> paymentService.verifyPayment(request, customerPrincipal))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Payment has already been verified for this order");
    }

    @Test
    @DisplayName("verifyPayment enforces customer ownership")
    void testVerifyPayment_ForbiddenForOtherCustomer() {
        sampleOrder.setRazorpayOrderId("order_rzp_mock_123");
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_rzp_mock_123",
                "pay_mock_456",
                "sig_123"
        );

        assertThatThrownBy(() -> paymentService.verifyPayment(request, otherCustomerPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("You do not have permission to verify payment for this order");
    }

    // =========================================================================
    // Handle Payment Failure Tests
    // =========================================================================

    @Test
    @DisplayName("handlePaymentFailure marks order payment status as FAILED")
    void testHandlePaymentFailure_Success() {
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = paymentService.handlePaymentFailure(
                101L,
                "Payment was declined by issuing bank",
                customerPrincipal
        );

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(sampleOrder.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("handlePaymentFailure does not downgrade an already PAID order")
    void testHandlePaymentFailure_DoesNotDowngradePaidOrder() {
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(sampleOrder));

        PaymentVerificationResponse response = paymentService.handlePaymentFailure(
                101L,
                "Payment failed event",
                customerPrincipal
        );

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(sampleOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderRepository, never()).save(any(Order.class));
    }
}
