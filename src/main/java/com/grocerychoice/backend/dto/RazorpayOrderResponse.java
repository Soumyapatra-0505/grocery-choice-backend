package com.grocerychoice.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RazorpayOrderResponse {

    @JsonProperty("razorpayOrderId")
    private String razorpayOrderId;

    @JsonProperty("razorpayKeyId")
    private String razorpayKeyId;

    @JsonProperty("amount")
    private Long amount; // in paise

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("orderId")
    private Long orderId;

    public RazorpayOrderResponse() {
    }

    public RazorpayOrderResponse(String razorpayOrderId, String razorpayKeyId, Long amount, String currency, Long orderId) {
        this.razorpayOrderId = razorpayOrderId;
        this.razorpayKeyId = razorpayKeyId;
        this.amount = amount;
        this.currency = currency;
        this.orderId = orderId;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayKeyId() {
        return razorpayKeyId;
    }

    public void setRazorpayKeyId(String razorpayKeyId) {
        this.razorpayKeyId = razorpayKeyId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
