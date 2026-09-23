package com.grocerychoice.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.grocerychoice.backend.exception.InvalidDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

/**
 * Service for Razorpay Test Mode integration.
 * Securely communicates with the Razorpay Orders REST API and performs
 * server-side HMAC-SHA256 signature verification.
 * 
 * SECURITY COMPLIANCE:
 * - RAZORPAY_KEY_SECRET is strictly kept server-side and never logged.
 * - Server-side constant-time comparison prevents timing attacks.
 */
@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);
    private static final String RAZORPAY_ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final String keyId;
    private final String keySecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RazorpayService(
            @Value("${razorpay.key.id:}") String keyId,
            @Value("${razorpay.key.secret:}") String keySecret,
            ObjectMapper objectMapper) {
        this.keyId = keyId != null ? keyId.trim() : "";
        this.keySecret = keySecret != null ? keySecret.trim() : "";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Returns the public Razorpay Key ID (safe for client-side checkout).
     */
    public String getKeyId() {
        return this.keyId;
    }

    /**
     * Creates an authoritative Test Order on Razorpay via HTTPS.
     *
     * @param amountInPaise Total amount in paise (e.g. 54000 for INR 540.00)
     * @param receipt Unique internal receipt identifier (e.g. orderNumber)
     * @param orderId Database Order ID
     * @return Razorpay Order ID (e.g. "order_EKwxwAgItmmXdp")
     */
    public String createRazorpayOrder(long amountInPaise, String receipt, Long orderId) {
        if (keyId.isEmpty() || keySecret.isEmpty()) {
            throw new InvalidDataException("Razorpay credentials are not configured on server.");
        }

        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("amount", amountInPaise);
            rootNode.put("currency", "INR");
            rootNode.put("receipt", receipt);

            ObjectNode notesNode = rootNode.putObject("notes");
            notesNode.put("orderId", String.valueOf(orderId));

            String requestBody = objectMapper.writeValueAsString(rootNode);

            String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RAZORPAY_ORDERS_URL))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Razorpay order creation failed with status {}: {}", response.statusCode(), response.body());
                String errorDescription = "Failed to create Razorpay payment order";
                try {
                    JsonNode errorNode = objectMapper.readTree(response.body());
                    if (errorNode.has("error") && errorNode.get("error").has("description")) {
                        errorDescription = errorNode.get("error").get("description").asText();
                    }
                } catch (Exception ignored) {
                }
                throw new InvalidDataException(errorDescription);
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            if (!responseJson.has("id")) {
                throw new InvalidDataException("Razorpay response did not contain an order ID");
            }

            String razorpayOrderId = responseJson.get("id").asText();
            log.info("Razorpay Test Order created successfully: {} for internal order: {}", razorpayOrderId, orderId);
            return razorpayOrderId;

        } catch (InvalidDataException ide) {
            throw ide;
        } catch (Exception e) {
            log.error("Exception communicating with Razorpay API: {}", e.getMessage());
            throw new InvalidDataException("Error communicating with payment gateway: " + e.getMessage());
        }
    }

    /**
     * Verifies the Razorpay payment signature server-side.
     * Formula: HmacSHA256(razorpay_order_id + "|" + razorpay_payment_id, keySecret) == razorpay_signature
     *
     * @param razorpayOrderId Razorpay Order ID
     * @param razorpayPaymentId Razorpay Payment ID
     * @param razorpaySignature Razorpay Signature received from client
     * @return true if authentic, false otherwise
     */
    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        if (razorpayOrderId == null || razorpayOrderId.isBlank() ||
            razorpayPaymentId == null || razorpayPaymentId.isBlank() ||
            razorpaySignature == null || razorpaySignature.isBlank()) {
            return false;
        }

        if (keySecret.isEmpty()) {
            log.error("Cannot verify signature: Razorpay secret is not configured.");
            return false;
        }

        try {
            String payload = razorpayOrderId + "|" + razorpayPaymentId;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            String calculatedSignature = hexString.toString();

            // Constant-time comparison to guard against timing attacks
            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    razorpaySignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Cryptographic error verifying Razorpay signature: {}", e.getMessage());
            return false;
        }
    }
}
