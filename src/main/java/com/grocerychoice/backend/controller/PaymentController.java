package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.CreatePaymentOrderRequest;
import com.grocerychoice.backend.dto.PaymentVerificationRequest;
import com.grocerychoice.backend.dto.PaymentVerificationResponse;
import com.grocerychoice.backend.dto.RazorpayOrderResponse;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller handling Razorpay test order creation and server-side payment verification.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Creates a Razorpay Test Order.
     * Requires customer authentication.
     * Calculates authoritative amount from MySQL database.
     */
    @PostMapping("/create-order")
    public ResponseEntity<RazorpayOrderResponse> createPaymentOrder(
            @Valid @RequestBody CreatePaymentOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to initiate payment");
        }
        RazorpayOrderResponse response = paymentService.createPaymentOrder(request.getOrderId(), principal);
        return ResponseEntity.ok(response);
    }

    /**
     * Verifies Razorpay payment signature server-side.
     * Updates PaymentStatus to PAID only upon valid HMAC-SHA256 signature.
     */
    @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(
            @Valid @RequestBody PaymentVerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to verify payment");
        }
        PaymentVerificationResponse response = paymentService.verifyPayment(request, principal);
        return ResponseEntity.ok(response);
    }
}
