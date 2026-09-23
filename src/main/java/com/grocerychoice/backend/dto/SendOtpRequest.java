package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.OtpPurpose;
import jakarta.validation.constraints.NotBlank;

public class SendOtpRequest {

    @NotBlank(message = "Identifier (mobile number or email) is required")
    private String identifier;

    private OtpPurpose purpose = OtpPurpose.LOGIN;

    public SendOtpRequest() {
    }

    public SendOtpRequest(String identifier, OtpPurpose purpose) {
        this.identifier = identifier;
        this.purpose = purpose != null ? purpose : OtpPurpose.LOGIN;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }
}
