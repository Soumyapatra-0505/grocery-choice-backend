package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.Msg91TokenVerifyRequest;
import com.grocerychoice.backend.dto.SendOtpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Msg91AuthTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(null, null, null, null, null, Optional.empty(), 5, 5, 60);
    }

    @Test
    @DisplayName("Verification fails with 400 Bad Request when identifier is blank")
    void testBlankIdentifierThrowsBadRequest() {
        Msg91TokenVerifyRequest request = new Msg91TokenVerifyRequest("test-token", "", "Test User");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.verifyMsg91WidgetToken(request);
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Verification fails with 400 Bad Request when access token is blank")
    void testBlankAccessTokenThrowsBadRequest() {
        Msg91TokenVerifyRequest request = new Msg91TokenVerifyRequest("", "9876543210", "Test User");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.verifyMsg91WidgetToken(request);
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Verification fails with 503 Service Unavailable when MSG91_AUTH_KEY is not configured on server")
    void testMissingAuthKeyThrowsServiceUnavailable() {
        Msg91TokenVerifyRequest request = new Msg91TokenVerifyRequest("valid-access-token", "9876543210", "Test User");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.verifyMsg91WidgetToken(request);
        });
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @DisplayName("Legacy sendOtp fails with 503 Service Unavailable when OtpDeliveryService is absent")
    void testSendOtpFailsWhenDeliveryServiceAbsent() {
        SendOtpRequest request = new SendOtpRequest("9876543210", null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.sendOtp(request);
        });
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @DisplayName("getDevOtp returns null safely without NPE when OtpDeliveryService is absent")
    void testGetDevOtpReturnsNullWhenDeliveryServiceAbsent() {
        String devOtp = authService.getDevOtp("9876543210");
        assertNull(devOtp);
    }

    @Test
    @DisplayName("Constructor handles null OtpDeliveryService parameter gracefully without NPE")
    void testConstructorHandlesNullOptional() {
        AuthService serviceWithNull = new AuthService(null, null, null, null, null, null, 5, 5, 60);
        assertNull(serviceWithNull.getDevOtp("9876543210"));
    }
}
