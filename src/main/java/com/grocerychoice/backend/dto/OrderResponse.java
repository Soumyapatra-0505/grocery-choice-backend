package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.Order;
import com.grocerychoice.backend.entity.OrderStatus;
import com.grocerychoice.backend.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OrderResponse {

    private Long id;
    private String orderNumber;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Long deliveryAddressId;
    private String deliveryAddressText;
    private BigDecimal subtotal;
    private BigDecimal deliveryCharge;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private String paymentMethod;
    private String deliverySlot;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private Long assignedDeliveryPartnerId;
    private String assignedDeliveryPartnerName;
    private String assignedDeliveryPartnerPhone;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private String deliveryNotes;
    private String deliveryOtp;
    private Boolean deliveryOtpVerified;
    private Boolean codCollected;
    private LocalDateTime codCollectedAt;
    private Double deliveryAddressLatitude;
    private Double deliveryAddressLongitude;
    private String deliveryAddressLandmark;
    private List<OrderItemResponse> items = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OrderResponse() {
    }

    public OrderResponse(Long id, String orderNumber, Long customerId, String customerName,
                         String customerEmail, String customerPhone, Long deliveryAddressId,
                         String deliveryAddressText, BigDecimal subtotal, BigDecimal deliveryCharge,
                         BigDecimal discount, BigDecimal totalAmount, OrderStatus status,
                         PaymentStatus paymentStatus, String paymentMethod, String deliverySlot,
                         List<OrderItemResponse> items, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.deliveryAddressId = deliveryAddressId;
        this.deliveryAddressText = deliveryAddressText;
        this.subtotal = subtotal;
        this.deliveryCharge = deliveryCharge;
        this.discount = discount;
        this.totalAmount = totalAmount;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.paymentMethod = paymentMethod;
        this.deliverySlot = deliverySlot;
        this.items = items != null ? items : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OrderResponse fromEntity(Order order) {
        if (order == null) {
            return null;
        }

        Long custId = order.getUser() != null ? order.getUser().getId() : null;
        String custName = order.getUser() != null ? order.getUser().getFullName() : null;
        String custEmail = order.getUser() != null ? order.getUser().getEmail() : null;
        String custPhone = order.getUser() != null ? order.getUser().getPhone() : null;

        Long addrId = order.getDeliveryAddress() != null ? order.getDeliveryAddress().getId() : null;

        List<OrderItemResponse> itemResponses = order.getOrderItems() != null
                ? order.getOrderItems().stream()
                .map(OrderItemResponse::fromEntity)
                .collect(Collectors.toList())
                : new ArrayList<>();

        OrderResponse resp = new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                custId,
                custName,
                custEmail,
                custPhone,
                addrId,
                order.getDeliveryAddressText(),
                order.getSubtotal(),
                order.getDeliveryCharge(),
                order.getDiscount(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethod(),
                order.getDeliverySlot(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
        resp.setRazorpayOrderId(order.getRazorpayOrderId());
        resp.setRazorpayPaymentId(order.getRazorpayPaymentId());
        if (order.getDeliveryPartner() != null) {
            resp.setAssignedDeliveryPartnerId(order.getDeliveryPartner().getId());
            resp.setAssignedDeliveryPartnerName(order.getDeliveryPartner().getFullName());
            resp.setAssignedDeliveryPartnerPhone(order.getDeliveryPartner().getPhone());
        }
        resp.setAssignedAt(order.getAssignedAt());
        resp.setAcceptedAt(order.getAcceptedAt());
        resp.setPickedUpAt(order.getPickedUpAt());
        resp.setDeliveredAt(order.getDeliveredAt());
        resp.setDeliveryNotes(order.getDeliveryNotes());
        resp.setDeliveryOtp(order.getDeliveryOtp());
        resp.setDeliveryOtpVerified(order.getDeliveryOtpVerifiedAt() != null);
        resp.setCodCollected(order.getCodCollected() != null ? order.getCodCollected() : false);
        resp.setCodCollectedAt(order.getCodCollectedAt());
        if (order.getDeliveryAddress() != null) {
            resp.setDeliveryAddressLatitude(order.getDeliveryAddress().getLatitude());
            resp.setDeliveryAddressLongitude(order.getDeliveryAddress().getLongitude());
            resp.setDeliveryAddressLandmark(order.getDeliveryAddress().getLandmark());
        }
        return resp;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public Long getDeliveryAddressId() {
        return deliveryAddressId;
    }

    public void setDeliveryAddressId(Long deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }

    public String getDeliveryAddressText() {
        return deliveryAddressText;
    }

    public void setDeliveryAddressText(String deliveryAddressText) {
        this.deliveryAddressText = deliveryAddressText;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDeliveryCharge() {
        return deliveryCharge;
    }

    public void setDeliveryCharge(BigDecimal deliveryCharge) {
        this.deliveryCharge = deliveryCharge;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDeliverySlot() {
        return deliverySlot;
    }

    public void setDeliverySlot(String deliverySlot) {
        this.deliverySlot = deliverySlot;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public Long getAssignedDeliveryPartnerId() {
        return assignedDeliveryPartnerId;
    }

    public void setAssignedDeliveryPartnerId(Long assignedDeliveryPartnerId) {
        this.assignedDeliveryPartnerId = assignedDeliveryPartnerId;
    }

    public String getAssignedDeliveryPartnerName() {
        return assignedDeliveryPartnerName;
    }

    public void setAssignedDeliveryPartnerName(String assignedDeliveryPartnerName) {
        this.assignedDeliveryPartnerName = assignedDeliveryPartnerName;
    }

    public String getAssignedDeliveryPartnerPhone() {
        return assignedDeliveryPartnerPhone;
    }

    public void setAssignedDeliveryPartnerPhone(String assignedDeliveryPartnerPhone) {
        this.assignedDeliveryPartnerPhone = assignedDeliveryPartnerPhone;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    /**
     * Sanitizes response by clearing deliveryOtp so that riders cannot see the OTP code.
     */
    public OrderResponse maskDeliveryOtp() {
        this.deliveryOtp = null;
        return this;
    }

    public LocalDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(LocalDateTime pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getDeliveryNotes() {
        return deliveryNotes;
    }

    public void setDeliveryNotes(String deliveryNotes) {
        this.deliveryNotes = deliveryNotes;
    }

    public String getDeliveryOtp() {
        return deliveryOtp;
    }

    public void setDeliveryOtp(String deliveryOtp) {
        this.deliveryOtp = deliveryOtp;
    }

    public Boolean getDeliveryOtpVerified() {
        return deliveryOtpVerified;
    }

    public void setDeliveryOtpVerified(Boolean deliveryOtpVerified) {
        this.deliveryOtpVerified = deliveryOtpVerified;
    }

    public Boolean getCodCollected() {
        return codCollected;
    }

    public void setCodCollected(Boolean codCollected) {
        this.codCollected = codCollected;
    }

    public LocalDateTime getCodCollectedAt() {
        return codCollectedAt;
    }

    public void setCodCollectedAt(LocalDateTime codCollectedAt) {
        this.codCollectedAt = codCollectedAt;
    }

    public Double getDeliveryAddressLatitude() {
        return deliveryAddressLatitude;
    }

    public void setDeliveryAddressLatitude(Double deliveryAddressLatitude) {
        this.deliveryAddressLatitude = deliveryAddressLatitude;
    }

    public Double getDeliveryAddressLongitude() {
        return deliveryAddressLongitude;
    }

    public void setDeliveryAddressLongitude(Double deliveryAddressLongitude) {
        this.deliveryAddressLongitude = deliveryAddressLongitude;
    }

    public String getDeliveryAddressLandmark() {
        return deliveryAddressLandmark;
    }

    public void setDeliveryAddressLandmark(String deliveryAddressLandmark) {
        this.deliveryAddressLandmark = deliveryAddressLandmark;
    }
}
