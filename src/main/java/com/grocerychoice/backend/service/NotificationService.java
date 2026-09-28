package com.grocerychoice.backend.service;

import com.grocerychoice.backend.entity.Order;

/**
 * High-level notification service interface for customer communication.
 */
public interface NotificationService {

    /**
     * Dispatches order confirmation notifications via available channels (SMS and/or Email).
     *
     * @param order the confirmed order
     */
    void sendOrderConfirmation(Order order);
}
