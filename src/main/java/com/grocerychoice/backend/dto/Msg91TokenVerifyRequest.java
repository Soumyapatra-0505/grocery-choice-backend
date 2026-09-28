package com.grocerychoice.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for verifying an MSG91 OTP widget access token server-side.
 */
public class Msg91TokenVerifyRequest {

    @NotBlank(message = "MSG91 access token is required")
    private String accessToken;

    @NotBlank(message = "Identifier (mobile number or email) is required")
    private String identifier;

    @JsonAlias("name")
    private String fullName;

    public Msg91TokenVerifyRequest() {
    }

    public Msg91TokenVerifyRequest(String accessToken, String identifier, String fullName) {
        this.accessToken = accessToken;
        this.identifier = identifier;
        this.fullName = fullName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setName(String name) {
        if (this.fullName == null || this.fullName.trim().isEmpty()) {
            this.fullName = name;
        }
    }
}
