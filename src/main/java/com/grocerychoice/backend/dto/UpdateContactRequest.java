package com.grocerychoice.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for updating a staff member's contact information and basic business profile.
 */
public class UpdateContactRequest {

    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    private String email;

    private String phone;

    @Size(max = 100, message = "Designation cannot exceed 100 characters")
    private String designation;

    @Size(max = 100, message = "Store Hub cannot exceed 100 characters")
    private String storeHub;

    public UpdateContactRequest() {
    }

    public UpdateContactRequest(String fullName, String email, String phone, String designation, String storeHub) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.designation = designation;
        this.storeHub = storeHub;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
