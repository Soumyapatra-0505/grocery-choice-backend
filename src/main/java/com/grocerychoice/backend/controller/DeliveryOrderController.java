package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.DeliveryCompletionRequest;
import com.grocerychoice.backend.dto.DeliveryOtpVerifyRequest;
import com.grocerychoice.backend.dto.OrderResponse;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.DeliveryAssignmentService;
import com.grocerychoice.backend.service.DeliveryLifecycleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery/orders")
public class DeliveryOrderController {

    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryLifecycleService deliveryLifecycleService;

    public DeliveryOrderController(DeliveryAssignmentService deliveryAssignmentService,
                                   DeliveryLifecycleService deliveryLifecycleService) {
        this.deliveryAssignmentService = deliveryAssignmentService;
        this.deliveryLifecycleService = deliveryLifecycleService;
    }

    /**
     * Get active orders assigned to the currently authenticated delivery partner.
     * Allowed role: DELIVERY only.
     */
    @GetMapping("/assigned")
    public ResponseEntity<List<OrderResponse>> getAssignedOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(deliveryAssignmentService.getAssignedOrdersForDeliveryUser(principal.getId()));
    }

    /**
     * Accept assignment for an assigned order.
     * Allowed role: DELIVERY only.
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<OrderResponse> acceptAssignment(@PathVariable Long id,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(deliveryAssignmentService.acceptDeliveryAssignment(id, principal));
    }

    /**
     * Confirm package pickup from store hub.
     * Transitions PROCESSING -> OUT_FOR_DELIVERY and generates delivery verification code.
     * Allowed role: DELIVERY only.
     */
    @PostMapping("/{id}/pickup")
    public ResponseEntity<OrderResponse> pickupOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(deliveryLifecycleService.pickupOrder(id, principal));
    }

    /**
     * Verify customer's delivery code at doorstep.
     * Allowed role: DELIVERY only.
     */
    @PostMapping("/{id}/verify-otp")
    public ResponseEntity<OrderResponse> verifyDeliveryOtp(
            @PathVariable Long id,
            @Valid @RequestBody DeliveryOtpVerifyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(deliveryLifecycleService.verifyDeliveryOtp(id, request.getOtp(), principal));
    }

    /**
     * Complete order delivery (settles COD if applicable, records deliveredAt, marks DELIVERED).
     * Allowed role: DELIVERY only.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<OrderResponse> completeDelivery(
            @PathVariable Long id,
            @RequestBody(required = false) DeliveryCompletionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(deliveryLifecycleService.completeDelivery(id, request, principal));
    }
}
