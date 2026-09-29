package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.Role;
import jakarta.validation.constraints.NotNull;

public class ChangeRoleRequest {

    @NotNull(message = "New role is required")
    private Role role;

    public ChangeRoleRequest() {
    }

    public ChangeRoleRequest(Role role) {
        this.role = role;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
