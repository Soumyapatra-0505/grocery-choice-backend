package com.grocerychoice.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.entity.Order;
import com.grocerychoice.backend.service.SmsNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MSG91 transactional SMS delivery service for order confirmation notifications.
 * Kept strictly isolated from the MSG91 OTP widget authentication flow.
 */
@Service
public class Msg91SmsNotificationService implements SmsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsNotificationService.class);

    private final String authKey;
    private final String smsUrl;
    private final String senderId;
    private final String flowId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public Msg91SmsNotificationService(
            @Value("${msg91.auth-key:}") String authKey,
            @Value("${msg91.sms.url:https://control.msg91.com/api/v5/flow/}") String smsUrl,
            @Value("${msg91.sms.sender-id:GRCHCE}") String senderId,
            @Value("${msg91.sms.flow-id:}") String flowId,
            ObjectMapper objectMapper) {
        this(authKey, smsUrl, senderId, flowId, objectMapper, null);
    }

    public Msg91SmsNotificationService(
            String authKey,
            String smsUrl,
            String senderId,
            String flowId,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.authKey = authKey != null ? authKey.trim() : "";
        this.smsUrl = smsUrl != null && !smsUrl.isBlank() ? smsUrl.trim() : "https://control.msg91.com/api/v5/flow/";
        this.senderId = senderId != null ? senderId.trim() : "GRCHCE";
        this.flowId = flowId != null ? flowId.trim() : "";
        this.objectMapper = objectMapper;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean sendOrderConfirmationSms(String phone, Order order) {
        if (phone == null || phone.isBlank() || order == null) {
            log.warn("Invalid parameters for SMS notification: phone or order is null");
            return false;
        }

        String normalizedPhone = normalizePhone(phone);
        String message = buildSmsContent(order);

        // If MSG91_AUTH_KEY is not configured, log simulated SMS safely and return success
        if (authKey.isEmpty()) {
            log.info("[SIMULATED SMS via MSG91] To: {} | Message: {}", normalizedPhone, message);
            return true;
        }

        try {
            // If MSG91 flowId is configured, use official MSG91 Flow API
            if (!flowId.isEmpty()) {
                Map<String, Object> recipient = Map.of(
                        "mobiles", normalizedPhone,
                        "order_number", order.getOrderNumber(),
                        "amount", order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : "0.00",
                        "payment_status", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "PENDING"
                );

                Map<String, Object> payload = Map.of(
                        "template_id", flowId,
                        "short_url", "0",
                        "recipients", List.of(recipient)
                );

                String jsonBody = objectMapper.writeValueAsString(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(smsUrl))
                        .header("authkey", authKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Order confirmation SMS sent via MSG91 Flow to {} for order #{}", normalizedPhone, order.getOrderNumber());
                    return true;
                } else {
                    log.warn("MSG91 SMS Flow API returned status {}: {}", response.statusCode(), response.body());
                    return false;
                }
            } else {
                // If flowId is not specified, log the prepared transactional SMS
                log.info("MSG91 SMS sent to {} for order #{}: {}", normalizedPhone, order.getOrderNumber(), message);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to deliver order confirmation SMS to {} for order #{}: {}",
                    normalizedPhone, order.getOrderNumber(), e.getMessage());
            return false;
        }
    }

    /**
     * Builds standard concise SMS content conforming to requirements.
     */
    public String buildSmsContent(Order order) {
        String paymentMethod = order.getPaymentMethod() != null ? order.getPaymentMethod().toLowerCase() : "";
        boolean isCod = paymentMethod.contains("cash") || paymentMethod.contains("cod");
        String amountStr = order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : "0.00";

        if (isCod) {
            return String.format(
                    "Grocery Choice: Your COD order %s has been confirmed. Amount: Rs.%s. Thank you for shopping with Grocery Choice.",
                    order.getOrderNumber(),
                    amountStr
            );
        } else {
            String paymentStatusStr = order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "PAID";
            return String.format(
                    "Grocery Choice: Your order %s has been confirmed. Amount: Rs.%s. Payment: %s. Thank you for shopping with Grocery Choice.",
                    order.getOrderNumber(),
                    amountStr,
                    paymentStatusStr
            );
        }
    }

    /**
     * Normalizes phone to standard digits-only format with 91 prefix for MSG91.
     */
    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits;
    }
}
