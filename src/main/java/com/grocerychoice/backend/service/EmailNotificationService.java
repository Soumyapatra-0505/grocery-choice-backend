package com.grocerychoice.backend.service;

import com.grocerychoice.backend.entity.Order;

/**
 * Service interface for dispatching order-related Email notifications.
 */
public interface EmailNotificationService {

    /**
     * Sends an order confirmation Email to the given email address.
     *
     * @param email destination email address
     * @param order the confirmed order
     * @return true if successfully dispatched or queued, false otherwise
     */
    boolean sendOrderConfirmationEmail(String email, Order order);
}
