package com.grocerychoice.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String VALID_SECRET = "secure_test_secret_with_sufficient_entropy_for_testing_purposes_only_2026_at_least_256_bits";

    @Test
    @DisplayName("Valid secret (>= 32 bytes, not default) instantiates JwtTokenProvider and generates valid tokens")
    void testValidSecretSucceeds() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, 86400000L, objectMapper);

        User user = new User();
        user.setId(101L);
        user.setEmail("owner@test.com");
        user.setRole(Role.OWNER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPrimaryOwner(true);

        String token = provider.generateToken(user);
        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals(101L, provider.getUserIdFromToken(token));
        assertEquals("OWNER", provider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Null or empty secret throws IllegalStateException at startup")
    void testNullOrBlankSecretThrowsException() {
        IllegalStateException nullEx = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(null, 86400000L, objectMapper)
        );
        assertTrue(nullEx.getMessage().contains("JWT secret is not configured"));

        IllegalStateException emptyEx = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("", 86400000L, objectMapper)
        );
        assertTrue(emptyEx.getMessage().contains("JWT secret is not configured"));

        IllegalStateException blankEx = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider("   ", 86400000L, objectMapper)
        );
        assertTrue(blankEx.getMessage().contains("JWT secret is not configured"));
    }

    @Test
    @DisplayName("Historical default secret from git repository is rejected with IllegalStateException")
    void testHistoricalDefaultSecretThrowsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(JwtTokenProvider.HISTORICAL_DEFAULT_SECRET, 86400000L, objectMapper)
        );
        assertTrue(ex.getMessage().contains("matches the known historical repository default secret"));
        assertFalse(ex.getMessage().contains(JwtTokenProvider.HISTORICAL_DEFAULT_SECRET));
    }

    @Test
    @DisplayName("Secret shorter than 32 bytes (256 bits) throws IllegalStateException")
    void testShortSecretThrowsException() {
        String shortSecret = "too_short_secret";
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new JwtTokenProvider(shortSecret, 86400000L, objectMapper)
        );
        assertTrue(ex.getMessage().contains("too short"));
        assertTrue(ex.getMessage().contains("requires at least 32 bytes"));
        assertFalse(ex.getMessage().contains(shortSecret), "Exception message must never leak the secret value");
    }

    @Test
    @DisplayName("Exception messages must never reveal secret contents")
    void testErrorMessageNeverExposesSecret() {
        String customShortSecret = "secret_1234567890";
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                JwtTokenProvider.validateSecret(customShortSecret)
        );
        assertFalse(ex.getMessage().contains(customShortSecret));
    }
}
