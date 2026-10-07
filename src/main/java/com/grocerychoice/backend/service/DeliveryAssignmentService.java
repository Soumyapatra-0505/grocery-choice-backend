package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.OrderResponse;
import com.grocerychoice.backend.dto.UserSummaryResponse;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.AuditLogRepository;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.repository.UserRepository;
import com.grocerychoice.backend.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DeliveryAssignmentService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public DeliveryAssignmentService(OrderRepository orderRepository,
                                     UserRepository userRepository,
                                     AuditLogRepository auditLogRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Assigns or reassigns a delivery partner to an order in PROCESSING status.
     * Allowed actors: OWNER, ADMIN, STAFF.
     */
    public OrderResponse assignDeliveryPartner(Long orderId, Long deliveryUserId, UserPrincipal actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidDataException("Only orders in PROCESSING status can be assigned to a delivery partner. Current status: " + order.getStatus());
        }

        User deliveryUser = userRepository.findById(deliveryUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found with id: " + deliveryUserId));

        if (deliveryUser.getRole() != Role.DELIVERY) {
            throw new InvalidDataException("Target user is not a delivery partner. User role: " + deliveryUser.getRole());
        }

        if (deliveryUser.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidDataException("Target delivery partner is not active. Status: " + deliveryUser.getStatus());
        }

        // Idempotency: if the same delivery partner is already assigned, return successfully without duplicate audit
        if (order.getDeliveryPartner() != null && order.getDeliveryPartner().getId().equals(deliveryUserId)) {
            return OrderResponse.fromEntity(order);
        }

        boolean isReassignment = order.getDeliveryPartner() != null;
        order.setDeliveryPartner(deliveryUser);
        order.setAssignedAt(LocalDateTime.now());
        order.setAcceptedAt(null);
        // Order status remains PROCESSING

        Order saved = orderRepository.save(order);

        String action = isReassignment ? "DELIVERY_REASSIGNED" : "DELIVERY_ASSIGNED";
        String details = isReassignment
                ? "Order #" + saved.getId() + " (" + saved.getOrderNumber() + ") reassigned to delivery partner " + deliveryUser.getFullName() + " (ID: " + deliveryUser.getId() + ")"
                : "Order #" + saved.getId() + " (" + saved.getOrderNumber() + ") assigned to delivery partner " + deliveryUser.getFullName() + " (ID: " + deliveryUser.getId() + ")";

        auditLogRepository.save(new AuditLog(
                action,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                deliveryUser.getId(),
                deliveryUser.getEmail(),
                deliveryUser.getFullName(),
                details
        ));

        return OrderResponse.fromEntity(saved);
    }

    /**
     * Retrieves active assigned orders for a specific delivery partner (PROCESSING, OUT_FOR_DELIVERY).
     * Sorted newest first.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAssignedOrdersForDeliveryUser(Long deliveryUserId) {
        List<Order> orders = orderRepository.findByDeliveryPartnerIdAndStatusInOrderByCreatedAtDesc(
                deliveryUserId,
                List.of(OrderStatus.PROCESSING, OrderStatus.OUT_FOR_DELIVERY)
        );
        return orders.stream()
                .map(OrderResponse::fromEntity)
                .map(OrderResponse::maskDeliveryOtp)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves completed delivery history for a specific delivery partner (DELIVERED only).
     * Sorted newest completed orders first, with deliveryOtp masked.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getDeliveryHistoryForDeliveryUser(Long deliveryUserId) {
        List<Order> orders = orderRepository.findByDeliveryPartnerIdAndStatusOrderByCreatedAtDesc(
                deliveryUserId,
                OrderStatus.DELIVERED
        );
        return orders.stream()
                .sorted((a, b) -> {
                    LocalDateTime timeB = b.getDeliveredAt() != null ? b.getDeliveredAt() : b.getCreatedAt();
                    LocalDateTime timeA = a.getDeliveredAt() != null ? a.getDeliveredAt() : a.getCreatedAt();
                    if (timeB != null && timeA != null) {
                        return timeB.compareTo(timeA);
                    }
                    return 0;
                })
                .map(OrderResponse::fromEntity)
                .map(OrderResponse::maskDeliveryOtp)
                .collect(Collectors.toList());
    }

    /**
     * Accepts a delivery assignment by the assigned delivery partner.
     * Order status remains PROCESSING.
     */
    public OrderResponse acceptDeliveryAssignment(Long orderId, UserPrincipal deliveryPartner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidDataException("Only orders in PROCESSING status can be accepted. Current status: " + order.getStatus());
        }

        if (order.getDeliveryPartner() == null || !order.getDeliveryPartner().getId().equals(deliveryPartner.getId())) {
            throw new AccessDeniedException("You are not assigned to this order");
        }

        if (order.getAcceptedAt() != null) {
            throw new InvalidDataException("Order assignment has already been accepted at " + order.getAcceptedAt());
        }

        order.setAcceptedAt(LocalDateTime.now());
        // Status remains PROCESSING

        Order saved = orderRepository.save(order);

        auditLogRepository.save(new AuditLog(
                "DELIVERY_ACCEPTED",
                deliveryPartner.getId(),
                deliveryPartner.getEmail(),
                deliveryPartner.getFullName(),
                saved.getId(),
                null,
                saved.getOrderNumber(),
                "Delivery partner " + deliveryPartner.getFullName() + " accepted assignment for order #" + saved.getId() + " (" + saved.getOrderNumber() + ")"
        ));

        return OrderResponse.fromEntity(saved).maskDeliveryOtp();
    }

    /**
     * Retrieves all eligible delivery partners (Role.DELIVERY and UserStatus.ACTIVE).
     * Returns safe user summaries without sensitive credentials.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getEligibleDeliveryPartners() {
        return userRepository.findByRoleAndStatusOrderByFullNameAsc(Role.DELIVERY, UserStatus.ACTIVE)
                .stream()
                .map(UserSummaryResponse::fromUser)
                .collect(Collectors.toList());
    }
}
