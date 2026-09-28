package com.grocerychoice.backend.service;

import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.service.impl.DefaultNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SmsNotificationService smsNotificationService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private OrderRepository orderRepository;

    private DefaultNotificationService notificationService;

    private User sampleUser;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        notificationService = new DefaultNotificationService(
                smsNotificationService,
                emailNotificationService,
                orderRepository
        );

        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setFullName("Ananya Sen");
        sampleUser.setEmail("ananya.sen@example.com");
        sampleUser.setPhone("+91 98765 43210");
        sampleUser.setRole(Role.CUSTOMER);

        sampleOrder = new Order();
        sampleOrder.setId(101L);
        sampleOrder.setOrderNumber("GC-20260926-000101");
        sampleOrder.setUser(sampleUser);
        sampleOrder.setSubtotal(new BigDecimal("459.00"));
        sampleOrder.setDeliveryCharge(BigDecimal.ZERO);
        sampleOrder.setTotalAmount(new BigDecimal("459.00"));
        sampleOrder.setStatus(OrderStatus.PLACED);
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        sampleOrder.setPaymentMethod("Online (Razorpay)");
        sampleOrder.setNotificationSent(false);
    }

    @Test
    @DisplayName("Rule 1 & 2 & 3: Both valid Phone and Email -> SMS and Email are both attempted")
    void testSendOrderConfirmation_PhoneAndEmailBothAttempted() {
        when(smsNotificationService.sendOrderConfirmationSms(anyString(), any(Order.class))).thenReturn(true);
        when(emailNotificationService.sendOrderConfirmationEmail(anyString(), any(Order.class))).thenReturn(true);

        notificationService.sendOrderConfirmation(sampleOrder);

        verify(smsNotificationService, times(1)).sendOrderConfirmationSms(eq("+91 98765 43210"), eq(sampleOrder));
        verify(emailNotificationService, times(1)).sendOrderConfirmationEmail(eq("ananya.sen@example.com"), eq(sampleOrder));
        verify(orderRepository, times(1)).save(sampleOrder);

        assertThat(sampleOrder.isNotificationSent()).isTrue();
        assertThat(sampleOrder.getNotificationSentAt()).isNotNull();
    }

    @Test
    @DisplayName("Rule 1: Phone only -> SMS attempted, Email not attempted")
    void testSendOrderConfirmation_PhoneOnly() {
        sampleUser.setEmail(null);
        when(smsNotificationService.sendOrderConfirmationSms(anyString(), any(Order.class))).thenReturn(true);

        notificationService.sendOrderConfirmation(sampleOrder);

        verify(smsNotificationService, times(1)).sendOrderConfirmationSms(eq("+91 98765 43210"), eq(sampleOrder));
        verify(emailNotificationService, never()).sendOrderConfirmationEmail(anyString(), any(Order.class));
        verify(orderRepository, times(1)).save(sampleOrder);

        assertThat(sampleOrder.isNotificationSent()).isTrue();
    }

    @Test
    @DisplayName("Rule 1: Phone with synthetic placeholder email -> SMS attempted, Email not attempted")
    void testSendOrderConfirmation_PhoneWithSyntheticPlaceholderEmail() {
        sampleUser.setEmail("9876543210@customer.grocerychoice.in"); // Synthetic OTP placeholder
        when(smsNotificationService.sendOrderConfirmationSms(anyString(), any(Order.class))).thenReturn(true);

        notificationService.sendOrderConfirmation(sampleOrder);

        verify(smsNotificationService, times(1)).sendOrderConfirmationSms(eq("+91 98765 43210"), eq(sampleOrder));
        verify(emailNotificationService, never()).sendOrderConfirmationEmail(anyString(), any(Order.class));
        verify(orderRepository, times(1)).save(sampleOrder);

        assertThat(sampleOrder.isNotificationSent()).isTrue();
    }

    @Test
    @DisplayName("Rule 2: Email only -> Email attempted, SMS not attempted")
    void testSendOrderConfirmation_EmailOnly() {
        sampleUser.setPhone(null);
        when(emailNotificationService.sendOrderConfirmationEmail(anyString(), any(Order.class))).thenReturn(true);

        notificationService.sendOrderConfirmation(sampleOrder);

        verify(emailNotificationService, times(1)).sendOrderConfirmationEmail(eq("ananya.sen@example.com"), eq(sampleOrder));
        verify(smsNotificationService, never()).sendOrderConfirmationSms(anyString(), any(Order.class));
        verify(orderRepository, times(1)).save(sampleOrder);

        assertThat(sampleOrder.isNotificationSent()).isTrue();
    }

    @Test
    @DisplayName("Rule 4: Neither phone nor email -> No notification attempt, order succeeds safely")
    void testSendOrderConfirmation_NeitherPhoneNorEmail() {
        sampleUser.setPhone(null);
        sampleUser.setEmail(null);

        assertDoesNotThrow(() -> notificationService.sendOrderConfirmation(sampleOrder));

        verify(smsNotificationService, never()).sendOrderConfirmationSms(anyString(), any(Order.class));
        verify(emailNotificationService, never()).sendOrderConfirmationEmail(anyString(), any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));

        assertThat(sampleOrder.isNotificationSent()).isFalse();
    }

    @Test
    @DisplayName("Rule 8: SMS provider failure -> Order remains successful, does not propagate exception")
    void testSendOrderConfirmation_SmsProviderFailureDoesNotFailOrder() {
        when(smsNotificationService.sendOrderConfirmationSms(anyString(), any(Order.class)))
                .thenThrow(new RuntimeException("MSG91 gateway timeout"));
        when(emailNotificationService.sendOrderConfirmationEmail(anyString(), any(Order.class))).thenReturn(true);

        assertDoesNotThrow(() -> notificationService.sendOrderConfirmation(sampleOrder));

        // Email was still attempted
        verify(emailNotificationService, times(1)).sendOrderConfirmationEmail(eq("ananya.sen@example.com"), eq(sampleOrder));
        // Order state was recorded
        verify(orderRepository, times(1)).save(sampleOrder);
        assertThat(sampleOrder.isNotificationSent()).isTrue();
    }

    @Test
    @DisplayName("Rule 9: Email provider failure -> Order remains successful, does not propagate exception")
    void testSendOrderConfirmation_EmailProviderFailureDoesNotFailOrder() {
        when(smsNotificationService.sendOrderConfirmationSms(anyString(), any(Order.class))).thenReturn(true);
        when(emailNotificationService.sendOrderConfirmationEmail(anyString(), any(Order.class)))
                .thenThrow(new RuntimeException("SMTP connection refused"));

        assertDoesNotThrow(() -> notificationService.sendOrderConfirmation(sampleOrder));

        verify(smsNotificationService, times(1)).sendOrderConfirmationSms(eq("+91 98765 43210"), eq(sampleOrder));
        verify(orderRepository, times(1)).save(sampleOrder);
        assertThat(sampleOrder.isNotificationSent()).isTrue();
    }

    @Test
    @DisplayName("Rule 10: Duplicate/repeated confirmation -> Notification is not sent twice")
    void testSendOrderConfirmation_DuplicateCallSkipped() {
        when(smsNotificationService.sendOrderConfirmationSms(anyString(), any(Order.class))).thenReturn(true);
        when(emailNotificationService.sendOrderConfirmationEmail(anyString(), any(Order.class))).thenReturn(true);

        // First call
        notificationService.sendOrderConfirmation(sampleOrder);
        verify(smsNotificationService, times(1)).sendOrderConfirmationSms(anyString(), any(Order.class));
        verify(emailNotificationService, times(1)).sendOrderConfirmationEmail(anyString(), any(Order.class));
        verify(orderRepository, times(1)).save(sampleOrder);

        // Second call with order already marked as notificationSent
        notificationService.sendOrderConfirmation(sampleOrder);

        // Verify counts did not increase
        verify(smsNotificationService, times(1)).sendOrderConfirmationSms(anyString(), any(Order.class));
        verify(emailNotificationService, times(1)).sendOrderConfirmationEmail(anyString(), any(Order.class));
        verify(orderRepository, times(1)).save(sampleOrder);
    }

    @Test
    @DisplayName("Null order or user handled gracefully without exception")
    void testSendOrderConfirmation_NullHandling() {
        assertDoesNotThrow(() -> notificationService.sendOrderConfirmation(null));

        sampleOrder.setUser(null);
        assertDoesNotThrow(() -> notificationService.sendOrderConfirmation(sampleOrder));

        verifyNoInteractions(smsNotificationService);
        verifyNoInteractions(emailNotificationService);
    }
}
