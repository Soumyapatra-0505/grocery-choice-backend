package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public class ChangeStatusRequest {

    @NotNull(message = "New status is required")
    private UserStatus status;

    public ChangeStatusRequest() {
    }

    public ChangeStatusRequest(UserStatus status) {
        this.status = status;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
