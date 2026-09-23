package com.grocerychoice.backend.controller;

import com.grocerychoice.backend.dto.CreateOrderRequest;
import com.grocerychoice.backend.dto.OrderResponse;
import com.grocerychoice.backend.dto.OrderStatusUpdateRequest;
import com.grocerychoice.backend.entity.OrderStatus;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create / Place a new Order.
     * Overrides customerId with authenticated user ID to prevent spoofing.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            request.setCustomerId(principal.getId());
        }
        OrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get all orders (Owner/Admin view, newest first).
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /**
     * Get order by ID with ownership verification.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        OrderResponse order = orderService.getOrderById(id);
        if (principal != null && Role.CUSTOMER.equals(principal.getRole())) {
            if (!principal.getId().equals(order.getCustomerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view this order");
            }
        }
        return ResponseEntity.ok(order);
    }

    /**
     * Get order by unique order number.
     */
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable String orderNumber,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        OrderResponse order = orderService.getOrderByNumber(orderNumber);
        if (principal != null && Role.CUSTOMER.equals(principal.getRole())) {
            if (!principal.getId().equals(order.getCustomerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view this order");
            }
        }
        return ResponseEntity.ok(order);
    }

    /**
     * Get orders for the currently authenticated customer.
     */
    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated");
        }
        return ResponseEntity.ok(orderService.getOrdersByCustomerId(principal.getId()));
    }

    /**
     * Get customer orders history with ownership verification.
     * Prevents Customer A from accessing Customer B's order history.
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable Long customerId,
                                                                 @AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null && Role.CUSTOMER.equals(principal.getRole())) {
            if (!principal.getId().equals(customerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view this customer's orders");
            }
        }
        return ResponseEntity.ok(orderService.getOrdersByCustomerId(customerId));
    }

    /**
     * Filter orders by status (Owner/Admin view).
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(@PathVariable OrderStatus status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status));
    }

    /**
     * Update order status (Owner action).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request.getStatus()));
    }

    /**
     * Cancel an order with ownership verification.
     * Customer can only cancel their own order.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null && Role.CUSTOMER.equals(principal.getRole())) {
            OrderResponse order = orderService.getOrderById(id);
            if (!principal.getId().equals(order.getCustomerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to cancel this order");
            }
        }
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
