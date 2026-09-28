package com.grocerychoice.backend.service;

import com.grocerychoice.backend.entity.Order;

/**
 * Service interface for dispatching order-related SMS notifications.
 */
public interface SmsNotificationService {

    /**
     * Sends an order confirmation SMS to the given phone number.
     *
     * @param phone destination phone number
     * @param order the confirmed order
     * @return true if successfully dispatched or queued, false otherwise
     */
    boolean sendOrderConfirmationSms(String phone, Order order);
}
