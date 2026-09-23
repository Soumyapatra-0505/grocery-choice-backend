package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.AddressRequest;
import com.grocerychoice.backend.dto.AddressResponse;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /**
     * Lists addresses belonging exclusively to the authenticated customer.
     */
    @GetMapping
    public ResponseEntity<List<AddressResponse>> getMyAddresses(@AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        List<AddressResponse> addresses = addressService.getCustomerAddresses(principal.getId());
        return ResponseEntity.ok(addresses);
    }

    /**
     * Retrieves a single address by ID with strict customer ownership verification.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AddressResponse> getAddressById(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id) {
        ensureAuthenticated(principal);
        AddressResponse address = addressService.getAddressById(principal.getId(), id);
        return ResponseEntity.ok(address);
    }

    /**
     * Adds a new delivery address for the authenticated customer.
     */
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                         @Valid @RequestBody AddressRequest request) {
        ensureAuthenticated(principal);
        AddressResponse created = addressService.createAddress(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing address with strict ownership validation.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AddressResponse> updateAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody AddressRequest request) {
        ensureAuthenticated(principal);
        AddressResponse updated = addressService.updateAddress(principal.getId(), id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes an address with strict ownership validation.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id) {
        ensureAuthenticated(principal);
        addressService.deleteAddress(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to manage addresses");
        }
    }
}
