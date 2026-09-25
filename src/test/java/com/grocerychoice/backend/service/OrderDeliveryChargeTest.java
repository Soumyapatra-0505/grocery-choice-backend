package com.grocerychoice.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDeliveryChargeTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(null, null, null, null);
    }

    @Test
    @DisplayName("Subtotal ₹150 (< ₹199) incurs standard delivery charge of ₹40")
    void testDeliveryChargeForSubtotal150() {
        BigDecimal subtotal = new BigDecimal("150.00");
        BigDecimal deliveryCharge = orderService.calculateDeliveryCharge(subtotal);
        assertEquals(new BigDecimal("40.00"), deliveryCharge, "Subtotal ₹150 must have ₹40 delivery charge");
    }

    @Test
    @DisplayName("Subtotal ₹198.99 (< ₹199) incurs standard delivery charge of ₹40")
    void testDeliveryChargeForSubtotal198Point99() {
        BigDecimal subtotal = new BigDecimal("198.99");
        BigDecimal deliveryCharge = orderService.calculateDeliveryCharge(subtotal);
        assertEquals(new BigDecimal("40.00"), deliveryCharge, "Subtotal ₹198.99 must have ₹40 delivery charge");
    }

    @Test
    @DisplayName("Subtotal ₹199 (exact threshold) qualifies for FREE delivery (₹0)")
    void testDeliveryChargeForSubtotal199Exact() {
        BigDecimal subtotal = new BigDecimal("199.00");
        BigDecimal deliveryCharge = orderService.calculateDeliveryCharge(subtotal);
        assertEquals(BigDecimal.ZERO, deliveryCharge, "Subtotal ₹199 must qualify for FREE delivery (₹0)");
    }

    @Test
    @DisplayName("Subtotal ₹250 (> ₹199) qualifies for FREE delivery (₹0)")
    void testDeliveryChargeForSubtotal250() {
        BigDecimal subtotal = new BigDecimal("250.00");
        BigDecimal deliveryCharge = orderService.calculateDeliveryCharge(subtotal);
        assertEquals(BigDecimal.ZERO, deliveryCharge, "Subtotal ₹250 must qualify for FREE delivery (₹0)");
    }

    @Test
    @DisplayName("Subtotal ₹0 or low positive amount incurs standard delivery charge of ₹40")
    void testDeliveryChargeForZeroOrLowAmount() {
        BigDecimal subtotal = new BigDecimal("49.00");
        BigDecimal deliveryCharge = orderService.calculateDeliveryCharge(subtotal);
        assertEquals(new BigDecimal("40.00"), deliveryCharge, "Subtotal ₹49 must have ₹40 delivery charge");
    }
}
