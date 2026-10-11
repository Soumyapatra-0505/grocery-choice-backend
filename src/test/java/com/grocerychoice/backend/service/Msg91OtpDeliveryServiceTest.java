package com.grocerychoice.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.entity.OtpPurpose;
import com.grocerychoice.backend.service.impl.Msg91OtpDeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Msg91OtpDeliveryServiceTest {

    private static final String TEST_AUTH_KEY = "test_auth_key_1234567890abcdef";
    private static final String TEST_FLOW_ID = "flow_otp_template_9988";
    private static final String TEST_SMS_URL = "https://control.msg91.com/api/v5/flow/";
    private static final String TEST_SENDER_ID = "GRCHCE";
    private static final String SECRET_OTP = "584932";

    private ObjectMapper objectMapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Attach Logback ListAppender to Msg91OtpDeliveryService logger to assert security of logs
        serviceLogger = (Logger) LoggerFactory.getLogger(Msg91OtpDeliveryService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        serviceLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (serviceLogger != null && listAppender != null) {
            serviceLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("1. Successful MSG91 OTP delivery dispatches HTTP POST and succeeds without error")
    void testDeliverOtp_SuccessfulDelivery() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"type\":\"success\",\"message\":\"Flow request received\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        assertDoesNotThrow(() -> service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN));
        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("2. Normalizes Indian phone number to 91-prefixed 12-digit format")
    void testDeliverOtp_NormalizesIndianPhoneNumber() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"type\":\"success\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        // Test with 10-digit number
        assertThat(service.normalizePhone("9876543210")).isEqualTo("919876543210");
        // Test with +91 and spaces
        assertThat(service.normalizePhone("+91 98765 43210")).isEqualTo("919876543210");
        // Test with already formatted 12-digit number
        assertThat(service.normalizePhone("919876543210")).isEqualTo("919876543210");

        service.deliverOtp("+91 98765-43210", SECRET_OTP, OtpPurpose.LOGIN);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.uri()).isEqualTo(URI.create(TEST_SMS_URL));
        assertThat(sentRequest.headers().firstValue("authkey")).contains(TEST_AUTH_KEY);
    }

    @Test
    @DisplayName("3. Builds correct Flow API payload matching MSG91 specification")
    void testBuildFlowPayload_CorrectStructure() {
        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        Map<String, Object> payload = service.buildFlowPayload("919876543210", SECRET_OTP);

        assertThat(payload.get("template_id")).isEqualTo(TEST_FLOW_ID);
        assertThat(payload.get("short_url")).isEqualTo("0");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recipients = (List<Map<String, Object>>) payload.get("recipients");
        assertThat(recipients).hasSize(1);

        Map<String, Object> recipient = recipients.get(0);
        assertThat(recipient.get("mobiles")).isEqualTo("919876543210");
        assertThat(recipient.get("otp")).isEqualTo(SECRET_OTP);
        assertThat(recipient.get("OTP")).isEqualTo(SECRET_OTP);
    }

    @Test
    @DisplayName("4. MSG91 HTTP failure (e.g. 500 or 400) throws ResponseStatusException with BAD_GATEWAY")
    void testDeliverOtp_HttpFailureThrowsBadGateway() throws Exception {
        when(httpResponse.statusCode()).thenReturn(502);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getReason()).contains("MSG91 SMS gateway returned HTTP 502 error");
    }

    @Test
    @DisplayName("4b. MSG91 HTTP 200 with error JSON body throws BAD_GATEWAY")
    void testDeliverOtp_JsonErrorPayloadThrowsBadGateway() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"type\":\"error\",\"message\":\"Template ID not found or inactive\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getReason()).contains("Template ID not found or inactive");
    }

    @Test
    @DisplayName("5. MSG91 network or timeout failure throws ResponseStatusException with BAD_GATEWAY")
    void testDeliverOtp_NetworkTimeoutThrowsBadGateway() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection timed out"));

        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getReason()).contains("Failed to communicate with MSG91 SMS gateway");
    }

    @Test
    @DisplayName("6a. Missing MSG91_AUTH_KEY throws ResponseStatusException with SERVICE_UNAVAILABLE")
    void testDeliverOtp_MissingAuthKeyThrowsServiceUnavailable() {
        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                "", TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getReason()).contains("missing auth key");
    }

    @Test
    @DisplayName("6b. Missing MSG91_OTP_FLOW_ID throws ResponseStatusException with SERVICE_UNAVAILABLE")
    void testDeliverOtp_MissingFlowIdThrowsServiceUnavailable() {
        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, "", TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getReason()).contains("missing OTP flow ID");
    }

    @Test
    @DisplayName("7 & 8. Security verification: OTP and Auth Key are NEVER written to logs during execution or failure")
    void testDeliverOtp_OtpAndAuthKeyAreNeverLogged() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"type\":\"success\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        // Run successful delivery
        service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN);

        // Also trigger an error run
        when(httpResponse.statusCode()).thenReturn(500);
        assertThrows(ResponseStatusException.class, () ->
                service.deliverOtp("9876543210", SECRET_OTP, OtpPurpose.LOGIN)
        );

        // Assert over all captured log messages
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).isNotEmpty();

        for (ILoggingEvent logEvent : logsList) {
            String formattedMessage = logEvent.getFormattedMessage();
            // Verify OTP is NEVER logged
            assertThat(formattedMessage).doesNotContain(SECRET_OTP);
            // Verify AUTH KEY is NEVER logged
            assertThat(formattedMessage).doesNotContain(TEST_AUTH_KEY);
            // Verify unmasked 10-digit phone is not logged plainly
            assertThat(formattedMessage).doesNotContain("9876543210");
        }
    }

    @Test
    @DisplayName("Security: Email recipient is rejected for SMS delivery with BAD_REQUEST")
    void testDeliverOtp_RejectsEmailRecipient() {
        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deliverOtp("user@example.com", SECRET_OTP, OtpPurpose.LOGIN)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("SMS OTP delivery requires a valid mobile number");
    }

    @Test
    @DisplayName("Security: getDevOtp always returns null in production MSG91 provider")
    void testGetDevOtp_AlwaysReturnsNull() {
        Msg91OtpDeliveryService service = new Msg91OtpDeliveryService(
                TEST_AUTH_KEY, TEST_FLOW_ID, TEST_SMS_URL, TEST_SENDER_ID, objectMapper, httpClient
        );

        assertThat(service.getDevOtp("9876543210")).isNull();
        assertThat(service.getDevOtp(null)).isNull();
    }
}
