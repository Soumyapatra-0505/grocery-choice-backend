package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.*;
import com.grocerychoice.backend.entity.Permission;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    /**
     * Lists all staff, admin, and owner members.
     */
    @GetMapping
    public ResponseEntity<List<UserSummaryResponse>> getAllStaff() {
        return ResponseEntity.ok(staffService.getAllStaffMembers());
    }

    /**
     * Retrieves a single staff member by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> getStaffById(@PathVariable Long id) {
        return ResponseEntity.ok(staffService.getStaffMemberById(id));
    }

    /**
     * Creates or promotes an account to staff/admin/owner.
     */
    @PostMapping
    public ResponseEntity<UserSummaryResponse> createStaff(
            @Valid @RequestBody CreateStaffRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageStaff(principal);
        UserSummaryResponse response = staffService.createStaff(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates staff profile information (name, phone, designation, store hub).
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStaffRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageStaff(principal);
        return ResponseEntity.ok(staffService.updateStaff(id, request, principal));
    }

    /**
     * Changes system role for a staff member.
     */
    @RequestMapping(value = "/{id}/role", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<UserSummaryResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageStaff(principal);
        return ResponseEntity.ok(staffService.changeRole(id, request.getRole(), principal));
    }

    /**
     * Enables or disables a staff member account.
     */
    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<UserSummaryResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageStaff(principal);
        return ResponseEntity.ok(staffService.changeStatus(id, request.getStatus(), principal));
    }

    /**
     * Changes business designation for a staff member.
     */
    @RequestMapping(value = "/{id}/designation", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<UserSummaryResponse> changeDesignation(
            @PathVariable Long id,
            @Valid @RequestBody ChangeDesignationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageStaff(principal);
        return ResponseEntity.ok(staffService.changeDesignation(id, request.getDesignation(), principal));
    }

    /**
     * Updates custom granular permissions for a staff member.
     */
    @RequestMapping(value = "/{id}/permissions", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<UserSummaryResponse> updatePermissions(
            @PathVariable Long id,
            @RequestBody UpdatePermissionsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageStaff(principal);
        return ResponseEntity.ok(staffService.updatePermissions(id, request.getPermissions(), principal));
    }

    /**
     * Revokes staff access (restores account safely to standard customer without data loss).
     */
    @RequestMapping(value = {"/{id}", "/{id}/access"}, method = RequestMethod.DELETE)
    public ResponseEntity<UserSummaryResponse> removeStaffAccess(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageStaff(principal);
        return ResponseEntity.ok(staffService.removeStaffAccess(id, principal));
    }

    private void ensureCanManageStaff(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!principal.isPrimaryOwner() && !principal.isOwner() && !principal.isAdmin() && !principal.hasPermission(Permission.MANAGE_STAFF)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions to manage staff members");
        }
    }
}
