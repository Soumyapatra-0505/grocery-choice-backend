package com.grocerychoice.backend.service.impl;

import com.grocerychoice.backend.entity.Order;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.service.EmailNotificationService;
import com.grocerychoice.backend.service.NotificationService;
import com.grocerychoice.backend.service.SmsNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Default orchestration service for customer order confirmation notifications.
 * Enforces contact channel rules, failure isolation, and duplicate notification prevention.
 */
@Service
public class DefaultNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationService.class);

    private final SmsNotificationService smsNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final OrderRepository orderRepository;

    public DefaultNotificationService(
            SmsNotificationService smsNotificationService,
            EmailNotificationService emailNotificationService,
            OrderRepository orderRepository) {
        this.smsNotificationService = smsNotificationService;
        this.emailNotificationService = emailNotificationService;
        this.orderRepository = orderRepository;
    }

    @Override
    public void sendOrderConfirmation(Order order) {
        if (order == null) {
            log.warn("Cannot send order confirmation: order is null");
            return;
        }

        // Duplicate-notification protection
        if (order.isNotificationSent()) {
            log.info("Order confirmation notification already sent for order #{}. Skipping duplicate.",
                    order.getOrderNumber());
            return;
        }

        User customer = order.getUser();
        if (customer == null) {
            log.warn("Cannot send order confirmation: order #{} has no associated customer record",
                    order.getOrderNumber());
            return;
        }

        String phone = customer.getPhone();
        String email = customer.getEmail();

        boolean hasValidPhone = isValidPhone(phone);
        boolean hasValidEmail = isValidEmail(email);

        // Neither contact channel available
        if (!hasValidPhone && !hasValidEmail) {
            log.info("Customer ID: {} on order #{} has neither a valid phone nor email. Notification condition logged safely.",
                    customer.getId(), order.getOrderNumber());
            return;
        }

        boolean anyAttempted = false;

        // 1. Phone available -> Send SMS
        if (hasValidPhone) {
            anyAttempted = true;
            try {
                smsNotificationService.sendOrderConfirmationSms(phone, order);
            } catch (Exception e) {
                log.error("SMS notification encountered unexpected error for order #{}: {}",
                        order.getOrderNumber(), e.getMessage());
            }
        }

        // 2. Email available -> Send Email
        if (hasValidEmail) {
            anyAttempted = true;
            try {
                emailNotificationService.sendOrderConfirmationEmail(email, order);
            } catch (Exception e) {
                log.error("Email notification encountered unexpected error for order #{}: {}",
                        order.getOrderNumber(), e.getMessage());
            }
        }

        // Record notification state to prevent duplicates on retries
        if (anyAttempted) {
            try {
                order.setNotificationSent(true);
                order.setNotificationSentAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("Order confirmation notification state recorded for order #{}", order.getOrderNumber());
            } catch (Exception e) {
                log.error("Failed to update notification state for order #{}: {}",
                        order.getOrderNumber(), e.getMessage());
            }
        }
    }

    /**
     * Checks if phone number is valid (contains at least 10 digits).
     */
    public boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() >= 10 && digits.length() <= 15;
    }

    /**
     * Checks if email address is valid and not an internal synthetic placeholder.
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String trimmed = email.trim();
        // Exclude synthetic internal placeholder for phone-only OTP accounts
        if (trimmed.endsWith("@customer.grocerychoice.in")) {
            return false;
        }
        return trimmed.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
