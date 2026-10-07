package com.grocerychoice.backend.dto;

import jakarta.validation.constraints.NotNull;

public class DeliveryAssignmentRequest {

    @NotNull(message = "Delivery user ID is required")
    private Long deliveryUserId;

    public DeliveryAssignmentRequest() {
    }

    public DeliveryAssignmentRequest(Long deliveryUserId) {
        this.deliveryUserId = deliveryUserId;
    }

    public Long getDeliveryUserId() {
        return deliveryUserId;
    }

    public void setDeliveryUserId(Long deliveryUserId) {
        this.deliveryUserId = deliveryUserId;
    }
}
