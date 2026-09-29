package com.grocerychoice.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateStaffRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String phone;
    private String designation;
    private String storeHub;

    public UpdateStaffRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getStoreHub() {
        return storeHub;
    }

    public void setStoreHub(String storeHub) {
        this.storeHub = storeHub;
    }
}
