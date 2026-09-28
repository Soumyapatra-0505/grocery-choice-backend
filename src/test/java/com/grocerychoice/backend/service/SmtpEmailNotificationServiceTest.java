package com.grocerychoice.backend.service;

import com.grocerychoice.backend.entity.Order;
import com.grocerychoice.backend.entity.PaymentStatus;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.service.impl.SmtpEmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailNotificationServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    private Order sampleOrder;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setFullName("Aditi Rao");
        sampleUser.setEmail("aditi.rao@example.com");

        sampleOrder = new Order();
        sampleOrder.setId(100L);
        sampleOrder.setOrderNumber("GC-2026-00099");
        sampleOrder.setUser(sampleUser);
        sampleOrder.setCreatedAt(LocalDateTime.of(2026, 9, 26, 14, 30));
        sampleOrder.setSubtotal(new BigDecimal("350.00"));
        sampleOrder.setDeliveryCharge(new BigDecimal("40.00"));
        sampleOrder.setTotalAmount(new BigDecimal("390.00"));
        sampleOrder.setPaymentMethod("Online (Razorpay)");
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        sampleOrder.setDeliverySlot("Standard Delivery (30-45 mins)");
        sampleOrder.setDeliveryAddressText("Flat 101, Green Acres, Pune - 411001");
    }

    @Test
    @DisplayName("buildEmailBody includes all mandatory order confirmation details")
    void testBuildEmailBody_ContainsAllRequiredFields() {
        SmtpEmailNotificationService service = new SmtpEmailNotificationService(
                Optional.empty(), "orders@grocerychoice.com", "Grocery Choice"
        );

        String body = service.buildEmailBody("Aditi Rao", sampleOrder);

        // Required fields verification
        assertThat(body).contains("Grocery Choice");
        assertThat(body).contains("Aditi Rao"); // customer name
        assertThat(body).contains("GC-2026-00099"); // order number
        assertThat(body).contains("26 Sep 2026"); // order date/time
        assertThat(body).contains("390.00"); // order amount
        assertThat(body).contains("Online (Razorpay)"); // payment method
        assertThat(body).contains("PAID"); // payment status
        assertThat(body).contains("Flat 101, Green Acres, Pune - 411001"); // delivery address
        assertThat(body).contains("Rs.40.00"); // delivery charge
        assertThat(body).contains("Thank you for shopping with Grocery Choice!"); // thank-you message
    }

    @Test
    @DisplayName("Unconfigured JavaMailSender (Optional.empty) executes simulated email log and returns true")
    void testSendOrderConfirmationEmail_UnconfiguredSender() {
        SmtpEmailNotificationService service = new SmtpEmailNotificationService(
                Optional.empty(), "orders@grocerychoice.com", "Grocery Choice"
        );

        boolean result = service.sendOrderConfirmationEmail("aditi.rao@example.com", sampleOrder);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Configured JavaMailSender constructs correct SimpleMailMessage and sends email successfully")
    void testSendOrderConfirmationEmail_Success() {
        SmtpEmailNotificationService service = new SmtpEmailNotificationService(
                Optional.of(javaMailSender), "orders@grocerychoice.com", "Grocery Choice"
        );

        boolean result = service.sendOrderConfirmationEmail("aditi.rao@example.com", sampleOrder);

        assertThat(result).isTrue();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getTo()).containsExactly("aditi.rao@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("Grocery Choice: Order Confirmed #GC-2026-00099");
        assertThat(sentMessage.getFrom()).contains("Grocery Choice");
        assertThat(sentMessage.getFrom()).contains("orders@grocerychoice.com");
        assertThat(sentMessage.getText()).contains("GC-2026-00099");
        assertThat(sentMessage.getText()).contains("Aditi Rao");
    }

    @Test
    @DisplayName("SMTP transmission failure (MailException) caught safely without throwing")
    void testSendOrderConfirmationEmail_MailExceptionSafelyHandled() {
        doThrow(new MailSendException("SMTP relay timeout"))
                .when(javaMailSender).send(any(SimpleMailMessage.class));

        SmtpEmailNotificationService service = new SmtpEmailNotificationService(
                Optional.of(javaMailSender), "orders@grocerychoice.com", "Grocery Choice"
        );

        boolean result = assertDoesNotThrow(() ->
                service.sendOrderConfirmationEmail("aditi.rao@example.com", sampleOrder));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Null or blank email or order handled safely and returns false")
    void testSendOrderConfirmationEmail_NullHandling() {
        SmtpEmailNotificationService service = new SmtpEmailNotificationService(
                Optional.of(javaMailSender), "orders@grocerychoice.com", "Grocery Choice"
        );

        assertThat(service.sendOrderConfirmationEmail(null, sampleOrder)).isFalse();
        assertThat(service.sendOrderConfirmationEmail("   ", sampleOrder)).isFalse();
        assertThat(service.sendOrderConfirmationEmail("aditi@example.com", null)).isFalse();
        verifyNoInteractions(javaMailSender);
    }
}
