package com.grocerychoice.backend.dto;

import java.util.Set;

public class UpdatePermissionsRequest {

    private Set<String> permissions;

    public UpdatePermissionsRequest() {
    }

    public UpdatePermissionsRequest(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }
}
