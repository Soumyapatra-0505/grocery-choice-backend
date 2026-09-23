package com.grocerychoice.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AddressRequest {

    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Postal code is required")
    private String postalCode;

    private String landmark;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault = false;

    public AddressRequest() {
    }

    public AddressRequest(String addressLine1, String addressLine2, String city, String state, String postalCode, String landmark, Double latitude, Double longitude, Boolean isDefault) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.landmark = landmark;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isDefault = isDefault != null ? isDefault : false;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    @com.fasterxml.jackson.annotation.JsonSetter("pincode")
    public void setPincode(String pincode) {
        if (this.postalCode == null || this.postalCode.trim().isEmpty()) {
            this.postalCode = pincode;
        }
    }

    @com.fasterxml.jackson.annotation.JsonSetter("zipCode")
    public void setZipCode(String zipCode) {
        if (this.postalCode == null || this.postalCode.trim().isEmpty()) {
            this.postalCode = zipCode;
        }
    }

    public String getLandmark() {
        return landmark;
    }

    public void setLandmark(String landmark) {
        this.landmark = landmark;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean aDefault) {
        isDefault = aDefault;
    }
}
