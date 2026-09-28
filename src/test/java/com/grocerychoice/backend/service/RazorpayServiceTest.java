package com.grocerychoice.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.exception.InvalidDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RazorpayServiceTest {

    private static final String TEST_KEY_ID = "rzp_test_mockKey12345";
    private static final String TEST_KEY_SECRET = "mockSecret_abcdef123456";

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private ObjectMapper objectMapper;
    private RazorpayService razorpayService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        razorpayService = new RazorpayService(TEST_KEY_ID, TEST_KEY_SECRET, objectMapper, mockHttpClient);
    }

    private String calculateHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // =========================================================================
    // Signature Verification Tests
    // =========================================================================

    @Test
    @DisplayName("verifySignature returns true for valid HMAC-SHA256 signature")
    void testVerifySignature_Valid() throws Exception {
        String orderId = "order_O0123456789";
        String paymentId = "pay_P0123456789";
        String payload = orderId + "|" + paymentId;
        String validSignature = calculateHmac(payload, TEST_KEY_SECRET);

        boolean isValid = razorpayService.verifySignature(orderId, paymentId, validSignature);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("verifySignature returns false for tampered signature")
    void testVerifySignature_TamperedSignature() throws Exception {
        String orderId = "order_O0123456789";
        String paymentId = "pay_P0123456789";
        String tamperedSignature = "invalid_signature_hex_value_0123456789abcdef";

        boolean isValid = razorpayService.verifySignature(orderId, paymentId, tamperedSignature);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifySignature returns false when signed with different secret")
    void testVerifySignature_WrongSecret() throws Exception {
        String orderId = "order_O0123456789";
        String paymentId = "pay_P0123456789";
        String wrongSecret = "completely_different_secret_key";
        String wrongSignature = calculateHmac(orderId + "|" + paymentId, wrongSecret);

        boolean isValid = razorpayService.verifySignature(orderId, paymentId, wrongSignature);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifySignature returns false for mismatched payment ID")
    void testVerifySignature_MismatchedPaymentId() throws Exception {
        String orderId = "order_O0123456789";
        String correctPaymentId = "pay_P0123456789";
        String wrongPaymentId = "pay_WRONG_987654";
        String signature = calculateHmac(orderId + "|" + correctPaymentId, TEST_KEY_SECRET);

        boolean isValid = razorpayService.verifySignature(orderId, wrongPaymentId, signature);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifySignature returns false for null or blank parameters")
    void testVerifySignature_NullOrBlankParams() {
        assertThat(razorpayService.verifySignature(null, "pay_1", "sig_1")).isFalse();
        assertThat(razorpayService.verifySignature("order_1", null, "sig_1")).isFalse();
        assertThat(razorpayService.verifySignature("order_1", "pay_1", null)).isFalse();
        assertThat(razorpayService.verifySignature("", "pay_1", "sig_1")).isFalse();
        assertThat(razorpayService.verifySignature("order_1", "   ", "sig_1")).isFalse();
        assertThat(razorpayService.verifySignature("order_1", "pay_1", "")).isFalse();
    }

    @Test
    @DisplayName("verifySignature returns false when keySecret is not configured")
    void testVerifySignature_EmptySecret() {
        RazorpayService unconfiguredService = new RazorpayService(TEST_KEY_ID, "", objectMapper, mockHttpClient);
        boolean isValid = unconfiguredService.verifySignature("order_1", "pay_1", "sig_1");

        assertThat(isValid).isFalse();
    }

    // =========================================================================
    // Create Razorpay Order Tests
    // =========================================================================

    @Test
    @DisplayName("createRazorpayOrder sends correct payload and returns order ID on 200 OK")
    void testCreateRazorpayOrder_Success() throws Exception {
        String expectedOrderId = "order_EKwxwAgItmmXdp";
        String responseJson = "{\"id\":\"" + expectedOrderId + "\",\"entity\":\"order\",\"amount\":49900,\"currency\":\"INR\",\"status\":\"created\"}";

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        String result = razorpayService.createRazorpayOrder(49900L, "GC-2026-001", 101L);

        assertThat(result).isEqualTo(expectedOrderId);

        // Verify HTTP request structure
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any());

        HttpRequest captured = requestCaptor.getValue();
        assertThat(captured.uri().toString()).isEqualTo("https://api.razorpay.com/v1/orders");
        assertThat(captured.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(captured.headers().firstValue("Authorization")).isPresent();
        assertThat(captured.headers().firstValue("Authorization").get()).startsWith("Basic ");
    }

    @Test
    @DisplayName("createRazorpayOrder throws InvalidDataException when Razorpay returns HTTP 400 error")
    void testCreateRazorpayOrder_GatewayError() throws Exception {
        String errorJson = "{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Amount should be at least 100 paise.\"}}";

        when(mockHttpResponse.statusCode()).thenReturn(400);
        when(mockHttpResponse.body()).thenReturn(errorJson);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        assertThatThrownBy(() -> razorpayService.createRazorpayOrder(50L, "GC-2026-001", 101L))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Amount should be at least 100 paise.");
    }

    @Test
    @DisplayName("createRazorpayOrder throws InvalidDataException when credentials are missing")
    void testCreateRazorpayOrder_MissingCredentials() {
        RazorpayService unconfiguredService = new RazorpayService("", "", objectMapper, mockHttpClient);

        assertThatThrownBy(() -> unconfiguredService.createRazorpayOrder(49900L, "GC-2026-001", 101L))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Razorpay credentials are not configured");
    }

    @Test
    @DisplayName("getKeyId returns configured public test key ID")
    void testGetKeyId() {
        assertThat(razorpayService.getKeyId()).isEqualTo(TEST_KEY_ID);
    }
}
