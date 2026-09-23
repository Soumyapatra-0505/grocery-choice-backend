package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.PaymentVerificationRequest;
import com.grocerychoice.backend.dto.PaymentVerificationResponse;
import com.grocerychoice.backend.dto.RazorpayOrderResponse;
import com.grocerychoice.backend.entity.Order;
import com.grocerychoice.backend.entity.OrderStatus;
import com.grocerychoice.backend.entity.PaymentStatus;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service managing payment order creation and server-side verification.
 * 
 * CRITICAL BUSINESS RULES:
 * 1. Authoritative Pricing: The payment amount is always computed from the MySQL Order total.
 *    Client-supplied amounts are never accepted or trusted.
 * 2. Stock Safety: Payment verification only updates payment status and transaction IDs.
 *    It NEVER touches or decrements product inventory again.
 * 3. Zero-Trust Access: Customer A cannot initiate or verify payments for Customer B's orders.
 * 4. Duplicate Verification Prevention: An already PAID order cannot be verified again.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final RazorpayService razorpayService;

    public PaymentService(OrderRepository orderRepository, RazorpayService razorpayService) {
        this.orderRepository = orderRepository;
        this.razorpayService = razorpayService;
    }

    /**
     * Creates a Razorpay Test Order for an existing Grocery Choice order.
     * Enforces customer ownership and authoritative server-side pricing.
     */
    @Transactional
    public RazorpayOrderResponse createPaymentOrder(Long orderId, UserPrincipal principal) {
        if (orderId == null) {
            throw new InvalidDataException("Order ID is required to initiate payment");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        // Enforce ownership: customer can only pay for their own order
        if (principal != null && Role.CUSTOMER.equals(principal.getRole())) {
            if (!principal.getId().equals(order.getUser().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to pay for this order");
            }
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidDataException("Cannot initiate payment for a cancelled order");
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidDataException("Order has already been paid");
        }

        // Authoritative order total from database in paise (1 INR = 100 paise)
        BigDecimal totalAmount = order.getTotalAmount();
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDataException("Order total amount must be greater than zero");
        }

        long amountInPaise = totalAmount.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        // Create Razorpay Test Order via Gateway API
        String razorpayOrderId = razorpayService.createRazorpayOrder(amountInPaise, order.getOrderNumber(), order.getId());

        // Associate Razorpay order ID with the database Order
        order.setRazorpayOrderId(razorpayOrderId);
        orderRepository.save(order);

        log.info("Payment order initiated for Order #{} (Razorpay Order: {}, Amount: {} paise)",
                order.getOrderNumber(), razorpayOrderId, amountInPaise);

        // Return only the required fields: Razorpay order ID, public Key ID, amount, currency, order ID
        return new RazorpayOrderResponse(
                razorpayOrderId,
                razorpayService.getKeyId(),
                amountInPaise,
                "INR",
                order.getId()
        );
    }

    /**
     * Verifies the Razorpay payment server-side and marks the order as PAID.
     * Strictly verifies order ownership, order ID matching, and HMAC-SHA256 signature.
     * Prevents duplicate verification and NEVER alters product inventory.
     */
    @Transactional
    public PaymentVerificationResponse verifyPayment(PaymentVerificationRequest request, UserPrincipal principal) {
        if (request == null) {
            throw new InvalidDataException("Payment verification request cannot be null");
        }
        if (request.getRazorpayOrderId() == null || request.getRazorpayOrderId().isBlank()) {
            throw new InvalidDataException("razorpay_order_id is required");
        }
        if (request.getRazorpayPaymentId() == null || request.getRazorpayPaymentId().isBlank()) {
            throw new InvalidDataException("razorpay_payment_id is required");
        }
        if (request.getRazorpaySignature() == null || request.getRazorpaySignature().isBlank()) {
            throw new InvalidDataException("razorpay_signature is required");
        }

        // Resolve order by orderId or razorpayOrderId
        Order order = null;
        if (request.getOrderId() != null) {
            order = orderRepository.findById(request.getOrderId()).orElse(null);
        }
        if (order == null) {
            order = orderRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("No order found matching Razorpay Order ID: " + request.getRazorpayOrderId()));
        }

        // Enforce customer ownership
        if (principal != null && Role.CUSTOMER.equals(principal.getRole())) {
            if (!principal.getId().equals(order.getUser().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to verify payment for this order");
            }
        }

        // Verify Razorpay Order ID matches the stored order
        if (order.getRazorpayOrderId() == null || !order.getRazorpayOrderId().equals(request.getRazorpayOrderId())) {
            throw new InvalidDataException("Razorpay order ID does not match Grocery Choice order");
        }

        // Prevent duplicate verification
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidDataException("Payment has already been verified for this order");
        }

        // Server-side cryptographic signature verification
        boolean isValid = razorpayService.verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        if (!isValid) {
            log.warn("Invalid Razorpay payment signature received for order #{}", order.getOrderNumber());
            throw new InvalidDataException("Invalid payment signature");
        }

        // Mark as PAID and store transaction references
        // NOTE: Stock is NOT modified here; it was already safely decremented during order creation.
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setRazorpayPaymentId(request.getRazorpayPaymentId());
        order.setRazorpaySignature(request.getRazorpaySignature());
        order.setPaymentMethod("Online (Razorpay)");

        Order saved = orderRepository.save(order);
        log.info("Payment verified successfully for Order #{}. PaymentStatus updated to PAID.", saved.getOrderNumber());

        return new PaymentVerificationResponse(
                true,
                "Payment verified successfully",
                saved.getId(),
                saved.getOrderNumber(),
                PaymentStatus.PAID
        );
    }
}
