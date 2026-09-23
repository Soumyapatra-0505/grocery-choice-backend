package com.grocerychoice.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard RFC 7519 HMAC-SHA256 (HS256) JWT Token Provider.
 * Generates and cryptographically validates signed stateless tokens.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String jwtSecret;
    private final long jwtExpirationMs;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(
            @Value("${jwt.secret:grocery_choice_secure_jwt_secret_key_2026_at_least_256_bits_long_for_hmac_sha256}") String jwtSecret,
            @Value("${jwt.expiration:86400000}") long jwtExpirationMs,
            ObjectMapper objectMapper) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a signed JWT token for the authenticated user.
     */
    public String generateToken(User user) {
        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + jwtExpirationMs;

        try {
            // Header
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String headerEncoded = base64UrlEncode(objectMapper.writeValueAsBytes(header));

            // Claims Payload
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", String.valueOf(user.getId()));
            claims.put("role", user.getRole().name());
            claims.put("email", user.getEmail());
            claims.put("phone", user.getPhone());
            claims.put("fullName", user.getFullName());
            claims.put("iat", nowMillis / 1000);
            claims.put("exp", expMillis / 1000);
            String payloadEncoded = base64UrlEncode(objectMapper.writeValueAsBytes(claims));

            // Signature
            String contentToSign = headerEncoded + "." + payloadEncoded;
            String signatureEncoded = createSignature(contentToSign);

            return contentToSign + "." + signatureEncoded;
        } catch (Exception e) {
            log.error("Failed to generate JWT token for user {}", user.getId(), e);
            throw new RuntimeException("Could not generate authentication token", e);
        }
    }

    /**
     * Cryptographically validates the token signature and checks expiration.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        try {
            String contentToSign = parts[0] + "." + parts[1];
            String expectedSignature = createSignature(contentToSign);

            // Constant-time comparison to prevent timing attacks
            byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = parts[2].getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
                log.debug("JWT signature mismatch");
                return false;
            }

            // Check expiration
            Map<String, Object> claims = getClaims(token);
            Number expNumber = (Number) claims.get("exp");
            if (expNumber == null) {
                return false;
            }

            long expSeconds = expNumber.longValue();
            long nowSeconds = System.currentTimeMillis() / 1000;
            if (nowSeconds > expSeconds) {
                log.debug("JWT token has expired");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payloadBytes, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JWT claims", e);
        }
    }

    public Long getUserIdFromToken(String token) {
        Map<String, Object> claims = getClaims(token);
        Object sub = claims.get("sub");
        return sub != null ? Long.parseLong(sub.toString()) : null;
    }

    public String getRoleFromToken(String token) {
        Map<String, Object> claims = getClaims(token);
        Object role = claims.get("role");
        return role != null ? role.toString() : null;
    }

    public String getEmailFromToken(String token) {
        Map<String, Object> claims = getClaims(token);
        Object email = claims.get("email");
        return email != null ? email.toString() : null;
    }

    public String getFullNameFromToken(String token) {
        Map<String, Object> claims = getClaims(token);
        Object fullName = claims.get("fullName");
        return fullName != null ? fullName.toString() : null;
    }

    private String createSignature(String content) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(hmacBytes);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
