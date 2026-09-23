package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.CreateOrderRequest;
import com.grocerychoice.backend.dto.OrderItemRequest;
import com.grocerychoice.backend.dto.OrderResponse;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.AddressRepository;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.repository.ProductRepository;
import com.grocerychoice.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal STANDARD_DELIVERY_CHARGE = new BigDecimal("40.00");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository,
                        AddressRepository addressRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    /**
     * Transactional Order Creation.
     * Validates customer, address, products, stock, calculates authoritative pricing and delivery charge,
     * decrements stock, and saves order with snapshot items.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (request.getCustomerId() == null) {
            throw new InvalidDataException("Customer ID is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidDataException("Order must contain at least one item");
        }

        // 1. Verify customer exists
        User customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + request.getCustomerId()));

        // 2. Resolve delivery address
        Address address = null;
        String addressText = request.getDeliveryAddressText();

        if (request.getAddressId() != null) {
            address = addressRepository.findById(request.getAddressId())
                    .orElse(null);
        }

        if (address == null) {
            // Fallback to customer's default address or first address
            List<Address> userAddresses = addressRepository.findByUserIdOrderByIsDefaultDesc(customer.getId());
            if (!userAddresses.isEmpty()) {
                address = userAddresses.get(0);
            }
        }

        if (addressText == null || addressText.isBlank()) {
            if (address != null) {
                addressText = String.format("%s, %s, %s, %s - %s",
                        address.getAddressLine1(),
                        address.getAddressLine2() != null ? address.getAddressLine2() : "",
                        address.getCity(),
                        address.getState(),
                        address.getPostalCode()).replaceAll(", ,", ",");
            } else {
                addressText = "Standard Delivery Address";
            }
        }

        // 3. Process cart items: check existence, active status, sufficient stock, and snapshot pricing
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<Product> productsToUpdate = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getProductId() == null) {
                throw new InvalidDataException("Product ID is required for each order item");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new InvalidDataException("Item quantity must be greater than zero");
            }

            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + itemReq.getProductId()));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidDataException("Product '" + product.getName() + "' is currently inactive and cannot be ordered");
            }

            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new InvalidDataException(String.format("Insufficient stock for product '%s'. Available: %d, Requested: %d",
                        product.getName(), product.getStockQuantity(), itemReq.getQuantity()));
            }

            // Price snapshot from database (never trust frontend)
            BigDecimal itemPrice = product.getSellingPrice();
            BigDecimal itemSubtotal = itemPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            // Deduct stock
            product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
            productsToUpdate.add(product);

            OrderItem orderItem = new OrderItem(
                    null, // order will be linked below
                    product,
                    product.getName(),
                    product.getUnit(),
                    itemPrice,
                    itemReq.getQuantity(),
                    itemSubtotal
            );
            orderItems.add(orderItem);
        }

        // Save updated product stock quantities
        productRepository.saveAll(productsToUpdate);

        // 4. Calculate Delivery Charge (Rule: >= 500 => 0, < 500 => 40)
        BigDecimal deliveryCharge = calculateDeliveryCharge(subtotal);
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(deliveryCharge).subtract(discount);

        // 5. Generate unique safe Order Number
        String orderNumber = generateUniqueOrderNumber();

        // 6. Build and save Order
        Order order = new Order(
                orderNumber,
                customer,
                address,
                addressText,
                subtotal,
                deliveryCharge,
                discount,
                totalAmount,
                request.getPaymentMethod() != null ? request.getPaymentMethod() : "Cash on Delivery",
                request.getDeliverySlot() != null ? request.getDeliverySlot() : "Standard Delivery (30-45 mins)"
        );

        for (OrderItem item : orderItems) {
            order.addOrderItem(item);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully: {} for customer: {} with total: {}",
                savedOrder.getOrderNumber(), customer.getEmail(), savedOrder.getTotalAmount());

        return OrderResponse.fromEntity(savedOrder);
    }

    /**
     * Authoritative delivery charge calculation rule.
     */
    public BigDecimal calculateDeliveryCharge(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(FREE_DELIVERY_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return STANDARD_DELIVERY_CHARGE;
    }

    /**
     * Generate unique order number in format: GC-YYYYMMDD-XXXXXX
     */
    private synchronized String generateUniqueOrderNumber() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "GC-" + dateStr + "-";
        long countToday = orderRepository.countByOrderNumberStartingWith(prefix) + 1;

        String candidate = String.format("%s%06d", prefix, countToday);
        while (orderRepository.existsByOrderNumber(candidate)) {
            countToday++;
            candidate = String.format("%s%06d", prefix, countToday);
        }
        return candidate;
    }

    /**
     * Fetch order by ID
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));
        return OrderResponse.fromEntity(order);
    }

    /**
     * Fetch order by order number
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with order number: " + orderNumber));
        return OrderResponse.fromEntity(order);
    }

    /**
     * Fetch customer order history (newest first)
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(Long customerId) {
        if (!userRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found with ID: " + customerId);
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Fetch all orders for Owner Portal (newest first)
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Fetch orders filtered by status
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Customer order cancellation.
     * Can only cancel PLACED or CONFIRMED orders.
     * Restores stock to product inventory.
     */
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidDataException("Order is already cancelled");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidDataException("Delivered orders cannot be cancelled");
        }
        if (order.getStatus() != OrderStatus.PLACED && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidDataException(String.format("Orders in status '%s' cannot be cancelled. Only PLACED or CONFIRMED orders can be cancelled.",
                    order.getStatus()));
        }

        // Restore stock
        restoreOrderStock(order);

        order.setStatus(OrderStatus.CANCELLED);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        Order saved = orderRepository.save(order);
        log.info("Order {} has been cancelled. Stock restored.", order.getOrderNumber());
        return OrderResponse.fromEntity(saved);
    }

    /**
     * Owner order status transition.
     * Validates transitions:
     * PLACED -> CONFIRMED -> PROCESSING -> OUT_FOR_DELIVERY -> DELIVERED
     * CANCELLED orders cannot be transitioned.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        if (newStatus == null) {
            throw new InvalidDataException("Order status cannot be null");
        }

        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == OrderStatus.CANCELLED) {
            throw new InvalidDataException("Cannot update status of a cancelled order");
        }

        if (currentStatus == OrderStatus.DELIVERED && newStatus != OrderStatus.DELIVERED) {
            throw new InvalidDataException("Delivered orders cannot transition to another status");
        }

        // Validate allowed progression
        if (newStatus != currentStatus) {
            validateStatusTransition(currentStatus, newStatus);
        }

        // If transitioning to CANCELLED from an active unfulfilled status, restore stock
        if (newStatus == OrderStatus.CANCELLED) {
            restoreOrderStock(order);
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            }
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        log.info("Order {} status updated from {} to {}", order.getOrderNumber(), currentStatus, newStatus);
        return OrderResponse.fromEntity(saved);
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (next == OrderStatus.CANCELLED) {
            // Can cancel from PLACED, CONFIRMED, or PROCESSING
            if (current == OrderStatus.DELIVERED) {
                throw new InvalidDataException("Cannot cancel an already delivered order");
            }
            return;
        }

        boolean isValid = switch (current) {
            case PLACED -> next == OrderStatus.CONFIRMED;
            case CONFIRMED -> next == OrderStatus.PROCESSING;
            case PROCESSING -> next == OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED -> false;
            case CANCELLED -> false;
        };

        if (!isValid) {
            throw new InvalidDataException(String.format("Invalid status transition from '%s' to '%s'", current, next));
        }
    }

    private void restoreOrderStock(Order order) {
        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                    log.debug("Restored {} units to product '{}' (New stock: {})",
                            item.getQuantity(), product.getName(), product.getStockQuantity());
                }
            }
        }
    }
}
