package com.grocerychoice.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class DesignationRequest {

    @NotBlank(message = "Designation title is required")
    private String title;

    private String description;

    public DesignationRequest() {
    }

    public DesignationRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
