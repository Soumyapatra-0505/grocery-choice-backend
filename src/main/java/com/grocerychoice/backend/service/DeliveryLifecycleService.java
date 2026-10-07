package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.DeliveryCompletionRequest;
import com.grocerychoice.backend.dto.OrderResponse;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.AuditLogRepository;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@Transactional
public class DeliveryLifecycleService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;

    public DeliveryLifecycleService(OrderRepository orderRepository,
                                  AuditLogRepository auditLogRepository) {
        this.orderRepository = orderRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Confirms package pickup by the assigned delivery rider.
     * Generates an order-bound delivery verification code and transitions PROCESSING -> OUT_FOR_DELIVERY.
     * Masked OrderResponse returned to rider (no raw OTP exposed).
     */
    public OrderResponse pickupOrder(Long orderId, UserPrincipal rider) {
        validateRiderActor(rider);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        validateOrderOwnership(order, rider);

        // Idempotency: if already picked up and OUT_FOR_DELIVERY, return current state without duplicate audit log
        if (order.getPickedUpAt() != null && order.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
            return OrderResponse.fromEntity(order).maskDeliveryOtp();
        }

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidDataException("Only orders in PROCESSING status can be picked up. Current status: " + order.getStatus());
        }

        if (order.getAcceptedAt() == null) {
            throw new InvalidDataException("Order must be accepted by delivery partner before it can be picked up");
        }

        // Generate cryptographically secure 4-digit numeric delivery code (1000 - 9999)
        int randomCode = 1000 + SECURE_RANDOM.nextInt(9000);
        String deliveryOtp = String.valueOf(randomCode);

        order.setDeliveryOtp(deliveryOtp);
        order.setPickedUpAt(LocalDateTime.now());
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);

        Order saved = orderRepository.save(order);

        auditLogRepository.save(new AuditLog(
                "DELIVERY_PICKED_UP",
                rider.getId(),
                rider.getEmail(),
                rider.getFullName(),
                saved.getId(),
                null,
                saved.getOrderNumber(),
                "Order #" + saved.getId() + " (" + saved.getOrderNumber() + ") picked up by rider " + rider.getFullName() + ". Status updated to OUT_FOR_DELIVERY."
        ));

        return OrderResponse.fromEntity(saved).maskDeliveryOtp();
    }

    /**
     * Verifies the customer's delivery verification code at the doorstep.
     */
    public OrderResponse verifyDeliveryOtp(Long orderId, String submittedOtp, UserPrincipal rider) {
        validateRiderActor(rider);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        validateOrderOwnership(order, rider);

        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new InvalidDataException("Order must be in OUT_FOR_DELIVERY status to verify delivery code. Current status: " + order.getStatus());
        }

        // Idempotency: if already verified, return successfully
        if (order.getDeliveryOtpVerifiedAt() != null) {
            return OrderResponse.fromEntity(order).maskDeliveryOtp();
        }

        if (submittedOtp == null || submittedOtp.isBlank()) {
            throw new InvalidDataException("Delivery verification code is required");
        }

        String actualOtp = order.getDeliveryOtp();
        if (actualOtp == null || !actualOtp.equals(submittedOtp.trim())) {
            throw new InvalidDataException("Invalid delivery verification code");
        }

        order.setDeliveryOtpVerifiedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        auditLogRepository.save(new AuditLog(
                "DELIVERY_OTP_VERIFIED",
                rider.getId(),
                rider.getEmail(),
                rider.getFullName(),
                saved.getId(),
                null,
                saved.getOrderNumber(),
                "Delivery verification code verified for Order #" + saved.getId() + " (" + saved.getOrderNumber() + ")"
        ));

        return OrderResponse.fromEntity(saved).maskDeliveryOtp();
    }

    /**
     * Completes order delivery: verifies doorstep code, validates COD cash collection if applicable,
     * updates paymentStatus to PAID for COD, sets deliveredAt, and transitions OUT_FOR_DELIVERY -> DELIVERED.
     */
    public OrderResponse completeDelivery(Long orderId, DeliveryCompletionRequest request, UserPrincipal rider) {
        validateRiderActor(rider);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        validateOrderOwnership(order, rider);

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidDataException("Order has already been delivered");
        }

        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new InvalidDataException("Order must be in OUT_FOR_DELIVERY status to complete. Current status: " + order.getStatus());
        }

        // Verify OTP: either already verified or verified in this completion request
        if (order.getDeliveryOtpVerifiedAt() == null) {
            if (request != null && request.getOtp() != null && !request.getOtp().isBlank()
                    && order.getDeliveryOtp() != null && order.getDeliveryOtp().equals(request.getOtp().trim())) {
                order.setDeliveryOtpVerifiedAt(LocalDateTime.now());
                auditLogRepository.save(new AuditLog(
                        "DELIVERY_OTP_VERIFIED",
                        rider.getId(),
                        rider.getEmail(),
                        rider.getFullName(),
                        order.getId(),
                        null,
                        order.getOrderNumber(),
                        "Delivery verification code verified during completion for Order #" + order.getId() + " (" + order.getOrderNumber() + ")"
                ));
            } else {
                throw new InvalidDataException("Delivery verification code must be verified before completing delivery");
            }
        }

        // COD vs Prepaid Settlement
        boolean isCod = isCodPayment(order.getPaymentMethod());
        boolean isPrepaid = order.getPaymentStatus() == PaymentStatus.PAID;

        if (isPrepaid) {
            if (request != null && Boolean.TRUE.equals(request.getCodCollected())) {
                throw new InvalidDataException("Prepaid order cannot be marked as Cash on Delivery collected");
            }
        } else if (isCod) {
            if (request == null || !Boolean.TRUE.equals(request.getCodCollected())) {
                throw new InvalidDataException("Cash collection must be confirmed for Cash on Delivery orders");
            }

            order.setCodCollected(true);
            order.setCodCollectedAt(LocalDateTime.now());
            order.setPaymentStatus(PaymentStatus.PAID);

            auditLogRepository.save(new AuditLog(
                    "COD_COLLECTED",
                    rider.getId(),
                    rider.getEmail(),
                    rider.getFullName(),
                    order.getId(),
                    null,
                    order.getOrderNumber(),
                    "Cash collected for Order #" + order.getId() + " (" + order.getOrderNumber() + "): ₹" + order.getTotalAmount()
            ));
        }

        // Finalize delivery
        order.setDeliveredAt(LocalDateTime.now());
        if (request != null && request.getNotes() != null && !request.getNotes().isBlank()) {
            order.setDeliveryNotes(request.getNotes().trim());
        }
        order.setStatus(OrderStatus.DELIVERED);

        Order saved = orderRepository.save(order);

        auditLogRepository.save(new AuditLog(
                "DELIVERY_COMPLETED",
                rider.getId(),
                rider.getEmail(),
                rider.getFullName(),
                saved.getId(),
                null,
                saved.getOrderNumber(),
                "Order #" + saved.getId() + " (" + saved.getOrderNumber() + ") marked as DELIVERED by rider " + rider.getFullName()
        ));

        return OrderResponse.fromEntity(saved).maskDeliveryOtp();
    }

    private void validateRiderActor(UserPrincipal rider) {
        if (rider == null) {
            throw new AccessDeniedException("User must be authenticated");
        }
        if (!rider.isDelivery()) {
            throw new AccessDeniedException("Only delivery partners can perform delivery lifecycle actions");
        }
        if (rider.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Delivery partner account is not active");
        }
    }

    private void validateOrderOwnership(Order order, UserPrincipal rider) {
        if (order.getDeliveryPartner() == null || !order.getDeliveryPartner().getId().equals(rider.getId())) {
            throw new AccessDeniedException("You are not assigned to this order");
        }
    }

    private boolean isCodPayment(String paymentMethod) {
        if (paymentMethod == null) {
            return true;
        }
        String lower = paymentMethod.trim().toLowerCase();
        return lower.contains("cash") || lower.contains("cod");
    }
}
