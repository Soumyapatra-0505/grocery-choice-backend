package com.grocerychoice.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.entity.Order;
import com.grocerychoice.backend.entity.PaymentStatus;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.service.impl.Msg91SmsNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Msg91SmsNotificationServiceTest {

    private ObjectMapper objectMapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        User user = new User();
        user.setFullName("Rahul Sharma");
        user.setPhone("9876543210");

        sampleOrder = new Order();
        sampleOrder.setId(1L);
        sampleOrder.setOrderNumber("GC-2026-0001");
        sampleOrder.setUser(user);
        sampleOrder.setTotalAmount(new BigDecimal("349.00"));
        sampleOrder.setPaymentMethod("Cash on Delivery");
        sampleOrder.setPaymentStatus(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("buildSmsContent formats concise COD confirmation text correctly")
    void testBuildSmsContent_Cod() {
        Msg91SmsNotificationService service = new Msg91SmsNotificationService(
                "", "", "GRCHCE", "", objectMapper, httpClient
        );

        sampleOrder.setPaymentMethod("Cash on Delivery");
        String message = service.buildSmsContent(sampleOrder);

        assertThat(message).contains("Grocery Choice:");
        assertThat(message).contains("Your COD order GC-2026-0001 has been confirmed");
        assertThat(message).contains("Amount: Rs.349.00");
        assertThat(message).contains("Thank you for shopping with Grocery Choice.");
    }

    @Test
    @DisplayName("buildSmsContent formats concise Online PAID confirmation text correctly")
    void testBuildSmsContent_OnlinePaid() {
        Msg91SmsNotificationService service = new Msg91SmsNotificationService(
                "", "", "GRCHCE", "", objectMapper, httpClient
        );

        sampleOrder.setPaymentMethod("Online (Razorpay)");
        sampleOrder.setPaymentStatus(PaymentStatus.PAID);
        String message = service.buildSmsContent(sampleOrder);

        assertThat(message).contains("Grocery Choice:");
        assertThat(message).contains("Your order GC-2026-0001 has been confirmed");
        assertThat(message).contains("Amount: Rs.349.00");
        assertThat(message).contains("Payment: PAID");
        assertThat(message).contains("Thank you for shopping with Grocery Choice.");
    }

    @Test
    @DisplayName("Unconfigured MSG91 authKey executes simulated log and returns true without throwing")
    void testSendOrderConfirmationSms_UnconfiguredAuthKey() {
        Msg91SmsNotificationService service = new Msg91SmsNotificationService(
                "", "https://control.msg91.com/api/v5/flow/", "GRCHCE", "", objectMapper, httpClient
        );

        boolean result = service.sendOrderConfirmationSms("+91 98765 43210", sampleOrder);

        assertThat(result).isTrue();
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Configured Flow ID sends HTTP request via HttpClient and returns true on HTTP 200")
    void testSendOrderConfirmationSms_FlowSuccess() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        Msg91SmsNotificationService service = new Msg91SmsNotificationService(
                "mock_auth_key", "https://control.msg91.com/api/v5/flow/", "GRCHCE", "flow_12345", objectMapper, httpClient
        );

        boolean result = service.sendOrderConfirmationSms("9876543210", sampleOrder);

        assertThat(result).isTrue();
        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("HTTP error response returns false without throwing exception")
    void testSendOrderConfirmationSms_FlowHttpError() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"message\":\"Invalid mobile number\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        Msg91SmsNotificationService service = new Msg91SmsNotificationService(
                "mock_auth_key", "https://control.msg91.com/api/v5/flow/", "GRCHCE", "flow_12345", objectMapper, httpClient
        );

        boolean result = service.sendOrderConfirmationSms("invalid_phone", sampleOrder);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Network failure (IOException) caught safely and returns false")
    void testSendOrderConfirmationSms_NetworkFailure() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection reset by peer"));

        Msg91SmsNotificationService service = new Msg91SmsNotificationService(
                "mock_auth_key", "https://control.msg91.com/api/v5/flow/", "GRCHCE", "flow_12345", objectMapper, httpClient
        );

        boolean result = assertDoesNotThrow(() -> service.sendOrderConfirmationSms("9876543210", sampleOrder));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Null or blank phone returns false gracefully")
    void testSendOrderConfirmationSms_NullPhone() {
        Msg91SmsNotificationService service = new Msg91SmsNotificationService(
                "mock_auth_key", "https://control.msg91.com/api/v5/flow/", "GRCHCE", "flow_12345", objectMapper, httpClient
        );

        assertThat(service.sendOrderConfirmationSms(null, sampleOrder)).isFalse();
        assertThat(service.sendOrderConfirmationSms("  ", sampleOrder)).isFalse();
        assertThat(service.sendOrderConfirmationSms("9876543210", null)).isFalse();
    }
}
