package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyOtpRequest {

    @NotBlank(message = "Identifier (mobile number or email) is required")
    private String identifier;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 numeric digits")
    private String otp;

    private OtpPurpose purpose = OtpPurpose.LOGIN;

    @com.fasterxml.jackson.annotation.JsonAlias("name")
    private String fullName;

    public void setName(String name) {
        if (this.fullName == null || this.fullName.trim().isEmpty()) {
            this.fullName = name;
        }
    }

    public VerifyOtpRequest() {
    }

    public VerifyOtpRequest(String identifier, String otp, OtpPurpose purpose, String fullName) {
        this.identifier = identifier;
        this.otp = otp;
        this.purpose = purpose != null ? purpose : OtpPurpose.LOGIN;
        this.fullName = fullName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
