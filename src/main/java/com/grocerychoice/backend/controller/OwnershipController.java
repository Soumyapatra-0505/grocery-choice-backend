package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.AuditLogResponse;
import com.grocerychoice.backend.dto.TransferOwnershipRequest;
import com.grocerychoice.backend.dto.UserSummaryResponse;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.OwnershipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ownership")
public class OwnershipController {

    private final OwnershipService ownershipService;

    public OwnershipController(OwnershipService ownershipService) {
        this.ownershipService = ownershipService;
    }

    /**
     * Retrieves the current Primary Owner.
     */
    @GetMapping("/primary-owner")
    public ResponseEntity<UserSummaryResponse> getCurrentPrimaryOwner() {
        UserSummaryResponse owner = ownershipService.getCurrentPrimaryOwner();
        if (owner == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No Primary Owner found in the system");
        }
        return ResponseEntity.ok(owner);
    }

    /**
     * Lists active OWNER accounts eligible to receive Primary Ownership.
     */
    @GetMapping("/eligible-owners")
    public ResponseEntity<List<UserSummaryResponse>> getEligibleOwners(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(ownershipService.getEligibleNewOwners());
    }

    /**
     * Transfers Primary Ownership to another designated active OWNER account.
     * Transactional and strictly enforced for the current Primary Owner.
     */
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transferPrimaryOwnership(
            @Valid @RequestBody TransferOwnershipRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Map<String, Object> result = ownershipService.transferPrimaryOwnership(request, principal);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves security and ownership audit events.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(ownershipService.getAuditLogs());
    }
}
