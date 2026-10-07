package com.grocerychoice.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class DeliveryOtpVerifyRequest {

    @NotBlank(message = "OTP is required")
    private String otp;

    public DeliveryOtpVerifyRequest() {
    }

    public DeliveryOtpVerifyRequest(String otp) {
        this.otp = otp;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
