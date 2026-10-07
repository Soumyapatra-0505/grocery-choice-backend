package com.grocerychoice.backend.dto;

public class DeliveryCompletionRequest {

    private String notes;
    private String otp;
    private Boolean codCollected;

    public DeliveryCompletionRequest() {
    }

    public DeliveryCompletionRequest(String notes, String otp, Boolean codCollected) {
        this.notes = notes;
        this.otp = otp;
        this.codCollected = codCollected;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public Boolean getCodCollected() {
        return codCollected;
    }

    public void setCodCollected(Boolean codCollected) {
        this.codCollected = codCollected;
    }
}
