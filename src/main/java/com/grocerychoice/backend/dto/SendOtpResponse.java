package com.grocerychoice.backend.dto;

public class SendOtpResponse {

    private boolean success;
    private String message;
    private String identifier;
    private String type; // "mobile" or "email"
    private int expiresInSeconds;
    private int cooldownSeconds;

    public SendOtpResponse() {
    }

    public SendOtpResponse(boolean success, String message, String identifier, String type, int expiresInSeconds, int cooldownSeconds) {
        this.success = success;
        this.message = message;
        this.identifier = identifier;
        this.type = type;
        this.expiresInSeconds = expiresInSeconds;
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
