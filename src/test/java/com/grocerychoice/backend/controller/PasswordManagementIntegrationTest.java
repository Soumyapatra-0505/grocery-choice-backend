package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.ChangePasswordRequest;
import com.grocerychoice.backend.dto.OwnerLoginRequest;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import com.grocerychoice.backend.repository.UserRepository;
import com.grocerychoice.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testOwner;
    private String ownerJwt;

    private static final String INITIAL_PASSWORD = "CurrentOwnerPassword123!";
    private static final String NEW_PASSWORD = "BrandNewSuperSecurePassword2026!";

    @BeforeEach
    void setUp() {
        String testEmail = "test.owner." + System.currentTimeMillis() + "@grocerychoice.com";
        testOwner = new User(
                testEmail,
                "+91 98765 00199",
                "Test Store Owner",
                passwordEncoder.encode(INITIAL_PASSWORD),
                Role.OWNER
        );
        testOwner.setPrimaryOwner(true);
        testOwner.setStatus(UserStatus.ACTIVE);
        testOwner.setDesignation("Chief Executive Owner");
        testOwner.setStoreHub("Flagship Hub");
        testOwner = userRepository.save(testOwner);

        ownerJwt = jwtTokenProvider.generateToken(testOwner);
    }

    @Test
    @DisplayName("PUT /api/auth/password updates password hash and preserves all user fields")
    void testChangePasswordSuccess() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                INITIAL_PASSWORD,
                NEW_PASSWORD,
                NEW_PASSWORD
        );

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + ownerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Password changed successfully")));

        // Reload user from database and verify attributes
        User updatedUser = userRepository.findById(testOwner.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, updatedUser.getPasswordHash()));
        assertFalse(passwordEncoder.matches(INITIAL_PASSWORD, updatedUser.getPasswordHash()));

        assertEquals(testOwner.getId(), updatedUser.getId());
        assertEquals(testOwner.getEmail(), updatedUser.getEmail());
        assertEquals(testOwner.getPhone(), updatedUser.getPhone());
        assertEquals("Test Store Owner", updatedUser.getFullName());
        assertEquals(Role.OWNER, updatedUser.getRole());
        assertTrue(updatedUser.isPrimaryOwner());
        assertEquals(UserStatus.ACTIVE, updatedUser.getStatus());
        assertEquals("Chief Executive Owner", updatedUser.getDesignation());
        assertEquals("Flagship Hub", updatedUser.getStoreHub());

        // Verify login with new password succeeds
        OwnerLoginRequest loginReq = new OwnerLoginRequest(testOwner.getEmail(), NEW_PASSWORD);
        mockMvc.perform(post("/api/auth/owner/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Verify login with old password fails
        OwnerLoginRequest oldLoginReq = new OwnerLoginRequest(testOwner.getEmail(), INITIAL_PASSWORD);
        mockMvc.perform(post("/api/auth/owner/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLoginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/auth/password without Authorization header is rejected with 401 Unauthorized")
    void testChangePasswordUnauthenticated() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                INITIAL_PASSWORD,
                NEW_PASSWORD,
                NEW_PASSWORD
        );

        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/auth/password with wrong current password returns 400 Bad Request")
    void testChangePasswordWrongCurrentPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "WrongCurrentPassword999!",
                NEW_PASSWORD,
                NEW_PASSWORD
        );

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + ownerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Password hash unchanged
        User user = userRepository.findById(testOwner.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(INITIAL_PASSWORD, user.getPasswordHash()));
    }

    @Test
    @DisplayName("PUT /api/auth/password with weak password (< 12 chars) returns 400 Bad Request")
    void testChangePasswordWeakPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                INITIAL_PASSWORD,
                "TooShort1!",
                "TooShort1!"
        );

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + ownerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/auth/password with confirmation mismatch returns 400 Bad Request")
    void testChangePasswordConfirmationMismatch() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                INITIAL_PASSWORD,
                NEW_PASSWORD,
                "DifferentConfirmation123!"
        );

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + ownerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/auth/password reusing current password returns 400 Bad Request")
    void testChangePasswordReuseCurrentPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                INITIAL_PASSWORD,
                INITIAL_PASSWORD,
                INITIAL_PASSWORD
        );

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + ownerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
