package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.SendOtpRequest;
import com.grocerychoice.backend.dto.SendOtpResponse;
import com.grocerychoice.backend.entity.OtpPurpose;
import com.grocerychoice.backend.service.impl.DevOtpDeliveryService;
import com.grocerychoice.backend.service.impl.Msg91OtpDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that when otp.provider=msg91 is specified in configuration,
 * Spring Boot instantiates Msg91OtpDeliveryService and injects it into AuthService,
 * while DevOtpDeliveryService is not activated.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "otp.provider=msg91",
        "msg91.auth-key=test_auth_key",
        "msg91.otp.flow-id=test_flow_id"
})
class Msg91OtpProviderIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthService authService;

    @Autowired
    private Optional<OtpDeliveryService> otpDeliveryService;

    @Test
    @DisplayName("When otp.provider=msg91, Msg91OtpDeliveryService bean is loaded and DevOtpDeliveryService is absent")
    void testMsg91OtpDeliveryServiceIsActiveBean() {
        assertThat(otpDeliveryService).isPresent();
        assertThat(otpDeliveryService.get()).isInstanceOf(Msg91OtpDeliveryService.class);

        assertThat(applicationContext.getBeansOfType(Msg91OtpDeliveryService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(DevOtpDeliveryService.class)).isEmpty();
    }

    @Test
    @DisplayName("When otp.provider=msg91, getDevOtp returns null (secure in production)")
    void testGetDevOtpReturnsNull() {
        assertThat(authService.getDevOtp("9876543210")).isNull();
    }
}
