package com.grocerychoice.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.entity.OtpPurpose;
import com.grocerychoice.backend.service.OtpDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production implementation of OtpDeliveryService delivering OTPs via MSG91 Flow API.
 * Activated conditionally when otp.provider=msg91.
 *
 * Adheres strictly to security standards:
 * - Plain-text OTP is never written to application logs.
 * - API keys and credentials are never written to application logs.
 * - Recipient phone numbers are masked in all diagnostic output.
 * - Failures from the upstream SMS gateway propagate as exceptions to prevent false success claims.
 */
@Service
@ConditionalOnProperty(name = "otp.provider", havingValue = "msg91")
public class Msg91OtpDeliveryService implements OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(Msg91OtpDeliveryService.class);

    private final String authKey;
    private final String otpFlowId;
    private final String smsUrl;
    private final String senderId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public Msg91OtpDeliveryService(
            @Value("${msg91.auth-key:}") String authKey,
            @Value("${msg91.otp.flow-id:${MSG91_OTP_FLOW_ID:}}") String otpFlowId,
            @Value("${msg91.sms.url:${MSG91_SMS_URL:https://control.msg91.com/api/v5/flow/}}") String smsUrl,
            @Value("${msg91.sms.sender-id:${MSG91_SMS_SENDER_ID:GRCHCE}}") String senderId,
            ObjectMapper objectMapper) {
        this(authKey, otpFlowId, smsUrl, senderId, objectMapper, null);
    }

    public Msg91OtpDeliveryService(
            String authKey,
            String otpFlowId,
            String smsUrl,
            String senderId,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.authKey = authKey != null ? authKey.trim() : "";
        this.otpFlowId = otpFlowId != null ? otpFlowId.trim() : "";
        this.smsUrl = smsUrl != null && !smsUrl.isBlank() ? smsUrl.trim() : "https://control.msg91.com/api/v5/flow/";
        this.senderId = senderId != null && !senderId.isBlank() ? senderId.trim() : "GRCHCE";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void deliverOtp(String identifier, String otp, OtpPurpose purpose) {
        if (identifier == null || identifier.isBlank() || otp == null || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient identifier and OTP code are required");
        }

        if (identifier.contains("@")) {
            log.warn("MSG91 SMS delivery does not support email recipient: {}", maskIdentifier(identifier));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SMS OTP delivery requires a valid mobile number");
        }

        String normalizedPhone = normalizePhone(identifier);
        if (normalizedPhone.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mobile number format for SMS delivery");
        }

        // Validate configuration
        if (authKey.isEmpty()) {
            log.error("MSG91 OTP delivery failed: MSG91_AUTH_KEY is not configured on the server");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MSG91 SMS delivery service is not configured (missing auth key)"
            );
        }

        if (otpFlowId.isEmpty()) {
            log.error("MSG91 OTP delivery failed: MSG91_OTP_FLOW_ID is not configured on the server");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MSG91 SMS delivery service is not configured (missing OTP flow ID)"
            );
        }

        try {
            Map<String, Object> payload = buildFlowPayload(normalizedPhone, otp);
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
                // Check if response contains an error JSON payload
                if (response.body() != null && !response.body().isBlank()) {
                    try {
                        JsonNode root = objectMapper.readTree(response.body());
                        if (root.has("type") && "error".equalsIgnoreCase(root.get("type").asText())) {
                            String errMsg = root.has("message") ? root.get("message").asText() : "Unknown MSG91 error";
                            log.warn("MSG91 Flow API returned error status for {}: {}", maskIdentifier(normalizedPhone), errMsg);
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_GATEWAY,
                                    "Failed to deliver OTP SMS via MSG91: " + errMsg
                            );
                        }
                    } catch (ResponseStatusException rse) {
                        throw rse;
                    } catch (Exception ignored) {
                        // Response body is not an error JSON object; proceed with success
                    }
                }
                log.info("OTP SMS dispatched successfully via MSG91 Flow to recipient {}", maskIdentifier(normalizedPhone));
            } else {
                log.warn("MSG91 Flow API returned HTTP {} for recipient {}", response.statusCode(), maskIdentifier(normalizedPhone));
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "MSG91 SMS gateway returned HTTP " + response.statusCode() + " error"
                );
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Network or timeout error dispatching OTP SMS via MSG91 to recipient {}: {}",
                    maskIdentifier(normalizedPhone), e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to communicate with MSG91 SMS gateway: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("Unexpected error dispatching OTP SMS via MSG91 to recipient {}: {}",
                    maskIdentifier(normalizedPhone), e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process OTP SMS dispatch: " + e.getMessage()
            );
        }
    }

    /**
     * Builds the MSG91 Flow API payload map with the configured flow template ID and recipient details.
     */
    public Map<String, Object> buildFlowPayload(String normalizedPhone, String otp) {
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("mobiles", normalizedPhone);
        recipient.put("otp", otp);
        recipient.put("OTP", otp);

        return Map.of(
                "template_id", otpFlowId,
                "short_url", "0",
                "recipients", List.of(recipient)
        );
    }

    @Override
    public String getDevOtp(String identifier) {
        return null;
    }

    /**
     * Normalizes phone to standard digits-only format with 91 prefix for MSG91.
     */
    public String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits;
    }

    /**
     * Masks recipient identifier for secure logging without exposing sensitive phone numbers.
     */
    public String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 4) return "****";
        if (identifier.contains("@")) {
            int atIndex = identifier.indexOf('@');
            return identifier.substring(0, Math.min(2, atIndex)) + "***" + identifier.substring(atIndex);
        }
        return identifier.substring(0, 2) + "******" + identifier.substring(identifier.length() - 2);
    }
}
