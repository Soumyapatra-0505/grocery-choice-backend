package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.DesignationRequest;
import com.grocerychoice.backend.entity.Designation;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.DesignationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/designations")
public class DesignationController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    /**
     * Lists all business designations.
     */
    @GetMapping
    public ResponseEntity<List<Designation>> getAllDesignations() {
        return ResponseEntity.ok(designationService.getAllDesignations());
    }

    /**
     * Creates a new business designation.
     */
    @PostMapping
    public ResponseEntity<Designation> createDesignation(
            @Valid @RequestBody DesignationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageDesignations(principal);
        Designation created = designationService.createDesignation(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing business designation.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Designation> updateDesignation(
            @PathVariable Long id,
            @Valid @RequestBody DesignationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageDesignations(principal);
        return ResponseEntity.ok(designationService.updateDesignation(id, request, principal));
    }

    /**
     * Deletes a business designation.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDesignation(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureCanManageDesignations(principal);
        designationService.deleteDesignation(id, principal);
        return ResponseEntity.noContent().build();
    }

    private void ensureCanManageDesignations(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!principal.isPrimaryOwner() && !principal.isOwner() && !principal.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions to manage designations");
        }
    }
}
