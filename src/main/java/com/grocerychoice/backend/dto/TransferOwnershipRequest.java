package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TransferOwnershipRequest {

    @NotNull(message = "New primary owner ID is required")
    private Long newPrimaryOwnerId;

    @NotNull(message = "Previous owner post-transfer role is required (OWNER or CUSTOMER)")
    private Role previousOwnerNewRole = Role.OWNER;

    @NotBlank(message = "Confirmation keyword is required (must enter 'TRANSFER')")
    private String confirmKeyword;

    private String currentOwnerPassword;

    public TransferOwnershipRequest() {
    }

    public TransferOwnershipRequest(Long newPrimaryOwnerId, Role previousOwnerNewRole, String confirmKeyword) {
        this.newPrimaryOwnerId = newPrimaryOwnerId;
        this.previousOwnerNewRole = previousOwnerNewRole;
        this.confirmKeyword = confirmKeyword;
    }

    public Long getNewPrimaryOwnerId() {
        return newPrimaryOwnerId;
    }

    public void setNewPrimaryOwnerId(Long newPrimaryOwnerId) {
        this.newPrimaryOwnerId = newPrimaryOwnerId;
    }

    public Role getPreviousOwnerNewRole() {
        return previousOwnerNewRole;
    }

    public void setPreviousOwnerNewRole(Role previousOwnerNewRole) {
        this.previousOwnerNewRole = previousOwnerNewRole;
    }

    public String getConfirmKeyword() {
        return confirmKeyword;
    }

    public void setConfirmKeyword(String confirmKeyword) {
        this.confirmKeyword = confirmKeyword;
    }

    public String getCurrentOwnerPassword() {
        return currentOwnerPassword;
    }

    public void setCurrentOwnerPassword(String currentOwnerPassword) {
        this.currentOwnerPassword = currentOwnerPassword;
    }
}
