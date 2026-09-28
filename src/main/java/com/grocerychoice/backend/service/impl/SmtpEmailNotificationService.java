package com.grocerychoice.backend.service.impl;

import com.grocerychoice.backend.entity.Order;
import com.grocerychoice.backend.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * SMTP email delivery service for order confirmation notifications.
 * Uses Spring JavaMailSender and environment-configured SMTP credentials.
 */
@Service
public class SmtpEmailNotificationService implements EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", java.util.Locale.ENGLISH);

    private final Optional<JavaMailSender> mailSender;
    private final String mailFrom;
    private final String senderName;

    @Autowired
    public SmtpEmailNotificationService(
            Optional<JavaMailSender> mailSender,
            @Value("${notification.mail.from:noreply@grocerychoice.com}") String mailFrom,
            @Value("${notification.mail.sender-name:Grocery Choice}") String senderName) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom != null && !mailFrom.isBlank() ? mailFrom.trim() : "noreply@grocerychoice.com";
        this.senderName = senderName != null && !senderName.isBlank() ? senderName.trim() : "Grocery Choice";
    }

    @Override
    public boolean sendOrderConfirmationEmail(String email, Order order) {
        if (email == null || email.isBlank() || order == null) {
            log.warn("Invalid parameters for Email notification: email or order is null");
            return false;
        }

        String recipientEmail = email.trim();
        String customerName = order.getUser() != null && order.getUser().getFullName() != null
                ? order.getUser().getFullName()
                : "Valued Customer";

        String subject = String.format("Grocery Choice: Order Confirmed #%s", order.getOrderNumber());
        String body = buildEmailBody(customerName, order);

        // If JavaMailSender bean is not configured (e.g. SMTP host not set in local dev), log and return success
        if (mailSender.isEmpty()) {
            log.info("[SIMULATED EMAIL via SMTP] To: {} | Subject: {} | Order: {}",
                    recipientEmail, subject, order.getOrderNumber());
            return true;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", senderName, mailFrom));
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.get().send(message);
            log.info("Order confirmation email successfully sent to {} for order #{}", recipientEmail, order.getOrderNumber());
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver order confirmation email to {} for order #{}: {}",
                    recipientEmail, order.getOrderNumber(), e.getMessage());
            return false;
        }
    }

    /**
     * Builds comprehensive email body containing all required order confirmation details.
     */
    public String buildEmailBody(String customerName, Order order) {
        String formattedDate = order.getCreatedAt() != null
                ? order.getCreatedAt().format(DATE_TIME_FORMATTER)
                : "Recently Placed";

        String deliveryAddress = order.getDeliveryAddressText() != null && !order.getDeliveryAddressText().isBlank()
                ? order.getDeliveryAddressText()
                : "Standard Delivery Address";

        String deliveryChargeStr = order.getDeliveryCharge() != null
                ? String.format("Rs.%s", order.getDeliveryCharge().toPlainString())
                : "Rs.0.00";

        String totalAmountStr = order.getTotalAmount() != null
                ? order.getTotalAmount().toPlainString()
                : "0.00";

        String paymentMethodStr = order.getPaymentMethod() != null
                ? order.getPaymentMethod()
                : "Cash on Delivery";

        String paymentStatusStr = order.getPaymentStatus() != null
                ? order.getPaymentStatus().name()
                : "PENDING";

        String deliverySlotStr = order.getDeliverySlot() != null
                ? order.getDeliverySlot()
                : "Standard Delivery (30-45 mins)";

        return String.format(
                "Dear %s,\n\n" +
                "Thank you for shopping with Grocery Choice! Your grocery order has been successfully confirmed.\n\n" +
                "--------------------------------------------------\n" +
                "ORDER SUMMARY\n" +
                "--------------------------------------------------\n" +
                "Order Number:      %s\n" +
                "Order Date & Time: %s\n" +
                "Payment Method:    %s\n" +
                "Payment Status:    %s\n" +
                "Delivery Slot:     %s\n" +
                "Delivery Address:  %s\n\n" +
                "Delivery Charge:   %s\n" +
                "Total Amount:      Rs.%s\n" +
                "--------------------------------------------------\n\n" +
                "We are carefully picking your items from our fresh local inventory.\n\n" +
                "Warm regards,\n" +
                "Team Grocery Choice\n" +
                "https://grocerychoice.in",
                customerName,
                order.getOrderNumber(),
                formattedDate,
                paymentMethodStr,
                paymentStatusStr,
                deliverySlotStr,
                deliveryAddress,
                deliveryChargeStr,
                totalAmountStr
        );
    }
}
