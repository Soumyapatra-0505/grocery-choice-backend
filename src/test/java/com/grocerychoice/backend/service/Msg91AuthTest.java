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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("AuthService delegates OTP delivery to OtpDeliveryService on sendOtp")
    void testSendOtpDelegatesToDeliveryService() {
        com.grocerychoice.backend.repository.OtpVerificationRepository mockRepo =
                mock(com.grocerychoice.backend.repository.OtpVerificationRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder mockEncoder =
                mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        OtpDeliveryService mockDeliveryService = mock(OtpDeliveryService.class);

        when(mockRepo.findTopByIdentifierAndPurposeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(mockEncoder.encode(any())).thenReturn("hashed-otp");

        AuthService service = new AuthService(
                null,
                null,
                mockRepo,
                mockEncoder,
                null,
                Optional.of(mockDeliveryService),
                5,
                5,
                60
        );

        SendOtpRequest request = new SendOtpRequest("9876543210", null);
        com.grocerychoice.backend.dto.SendOtpResponse response = service.sendOtp(request);

        assertTrue(response.isSuccess());
        verify(mockDeliveryService, times(1))
                .deliverOtp(eq("+919876543210"), matches("^\\d{6}$"), eq(com.grocerychoice.backend.entity.OtpPurpose.LOGIN));
    }

    @Test
    @DisplayName("AuthService propagates delivery exception when OtpDeliveryService fails")
    void testSendOtpPropagatesDeliveryException() {
        com.grocerychoice.backend.repository.OtpVerificationRepository mockRepo =
                mock(com.grocerychoice.backend.repository.OtpVerificationRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder mockEncoder =
                mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        OtpDeliveryService mockDeliveryService = mock(OtpDeliveryService.class);

        when(mockRepo.findTopByIdentifierAndPurposeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(mockEncoder.encode(any())).thenReturn("hashed-otp");
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MSG91 failure"))
                .when(mockDeliveryService).deliverOtp(any(), any(), any());

        AuthService service = new AuthService(
                null,
                null,
                mockRepo,
                mockEncoder,
                null,
                Optional.of(mockDeliveryService),
                5,
                5,
                60
        );

        SendOtpRequest request = new SendOtpRequest("9876543210", null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.sendOtp(request));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }
}
