package com.grocerychoice.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ChangeDesignationRequest {

    @NotBlank(message = "Designation is required")
    private String designation;

    public ChangeDesignationRequest() {
    }

    public ChangeDesignationRequest(String designation) {
        this.designation = designation;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }
}
