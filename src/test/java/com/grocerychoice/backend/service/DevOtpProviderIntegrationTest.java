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
 * Verifies that when otp.provider=dev is specified (or default),
 * Spring Boot instantiates DevOtpDeliveryService and Msg91OtpDeliveryService is not activated.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "otp.provider=dev",
        "app.dev-mode=true"
})
class DevOtpProviderIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthService authService;

    @Autowired
    private Optional<OtpDeliveryService> otpDeliveryService;

    @Test
    @DisplayName("When otp.provider=dev, DevOtpDeliveryService bean is loaded and Msg91OtpDeliveryService is absent")
    void testDevOtpDeliveryServiceIsActiveBean() {
        assertThat(otpDeliveryService).isPresent();
        assertThat(otpDeliveryService.get()).isInstanceOf(DevOtpDeliveryService.class);

        assertThat(applicationContext.getBeansOfType(DevOtpDeliveryService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(Msg91OtpDeliveryService.class)).isEmpty();
    }

    @Test
    @DisplayName("When otp.provider=dev and app.dev-mode=true, dev OTP delivery works and getDevOtp retrieves OTP")
    void testDevOtpFlowWorks() {
        String testPhone = "919876541111";
        SendOtpResponse response = authService.sendOtp(new SendOtpRequest(testPhone, OtpPurpose.LOGIN));

        assertThat(response.isSuccess()).isTrue();
        String devOtp = authService.getDevOtp(testPhone);
        assertThat(devOtp).isNotNull();
        assertThat(devOtp).matches("^\\d{6}$");
    }
}
