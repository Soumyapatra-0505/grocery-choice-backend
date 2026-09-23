package com.grocerychoice.backend.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;

public class OwnerLoginRequest {

    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;

    public OwnerLoginRequest() {
    }

    public OwnerLoginRequest(String identifier, String password) {
        this.identifier = identifier;
        this.password = password;
    }

    public String getIdentifier() {
        return identifier;
    }

    @JsonSetter("identifier")
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @JsonSetter("email")
    public void setEmail(String email) {
        if (this.identifier == null || this.identifier.trim().isEmpty()) {
            this.identifier = email;
        }
    }

    @JsonSetter("username")
    public void setUsername(String username) {
        if (this.identifier == null || this.identifier.trim().isEmpty()) {
            this.identifier = username;
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
