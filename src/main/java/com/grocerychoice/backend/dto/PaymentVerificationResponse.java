package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.PaymentStatus;

public class PaymentVerificationResponse {

    private boolean success;
    private String message;
    private Long orderId;
    private String orderNumber;
    private PaymentStatus paymentStatus;

    public PaymentVerificationResponse() {
    }

    public PaymentVerificationResponse(boolean success, String message, Long orderId, String orderNumber, PaymentStatus paymentStatus) {
        this.success = success;
        this.message = message;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.paymentStatus = paymentStatus;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
