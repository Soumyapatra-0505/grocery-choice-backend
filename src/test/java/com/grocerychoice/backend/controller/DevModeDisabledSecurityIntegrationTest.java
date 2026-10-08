package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.OwnerLoginRequest;
import com.grocerychoice.backend.dto.SendOtpRequest;
import com.grocerychoice.backend.entity.OtpPurpose;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security Integration Tests verifying that when development mode is disabled (app.dev-mode=false),
 * the development-only endpoints (/api/auth/owner-token and /api/auth/dev-otp/**) are completely
 * blocked from unauthenticated and authenticated callers, while legitimate production auth flows remain intact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.dev-mode=false",
        "otp.provider=dev"
})
class DevModeDisabledSecurityIntegrationTest {

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

    private User ownerUser;
    private User customerUser;
    private String customerToken;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        // Create an Owner user if not present
        ownerUser = userRepository.findByEmail("secowner@grocerychoice.com").orElseGet(() -> {
            User u = new User();
            u.setFullName("Security Owner");
            u.setEmail("secowner@grocerychoice.com");
            u.setPhone("919876500001");
            u.setRole(Role.OWNER);
            u.setStatus(UserStatus.ACTIVE);
            u.setPasswordHash(passwordEncoder.encode("OwnerSecurePass123!"));
            return userRepository.save(u);
        });
        ownerToken = jwtTokenProvider.generateToken(ownerUser);

        // Create a Customer user
        customerUser = userRepository.findByEmail("seccustomer@grocerychoice.com").orElseGet(() -> {
            User u = new User();
            u.setFullName("Security Customer");
            u.setEmail("seccustomer@grocerychoice.com");
            u.setPhone("919876500002");
            u.setRole(Role.CUSTOMER);
            u.setStatus(UserStatus.ACTIVE);
            return userRepository.save(u);
        });
        customerToken = jwtTokenProvider.generateToken(customerUser);
    }

    @Test
    @DisplayName("SECURITY ISSUE 1: GET /api/auth/owner-token is BLOCKED (401 Unauthorized) for unauthenticated caller when dev mode is disabled")
    void testOwnerTokenBlockedForUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/auth/owner-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SECURITY ISSUE 1: GET /api/auth/owner-token returns 404 Not Found even for authenticated Customer caller when dev mode is disabled")
    void testOwnerTokenDisabledForCustomerCaller() throws Exception {
        mockMvc.perform(get("/api/auth/owner-token")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SECURITY ISSUE 1: GET /api/auth/owner-token returns 404 Not Found even for authenticated Owner caller when dev mode is disabled")
    void testOwnerTokenDisabledForOwnerCaller() throws Exception {
        mockMvc.perform(get("/api/auth/owner-token")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SECURITY ISSUE 2: GET /api/auth/dev-otp/** is BLOCKED (401 Unauthorized) for unauthenticated caller when dev mode is disabled")
    void testDevOtpBlockedForUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/auth/dev-otp/919876500002"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SECURITY ISSUE 2: GET /api/auth/dev-otp/** returns 404 Not Found even for authenticated caller when dev mode is disabled")
    void testDevOtpDisabledForAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/auth/dev-otp/919876500002")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PRODUCTION AUTH: POST /api/auth/send-otp remains publicly accessible and functioning")
    void testSendOtpRemainsAccessible() throws Exception {
        SendOtpRequest request = new SendOtpRequest("919876500099", OtpPurpose.LOGIN);

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("OTP sent")));
    }

    @Test
    @DisplayName("PRODUCTION AUTH: POST /api/auth/owner-login remains publicly accessible and validates credentials")
    void testOwnerLoginRemainsAccessible() throws Exception {
        // Valid credentials should authenticate
        OwnerLoginRequest validReq = new OwnerLoginRequest("secowner@grocerychoice.com", "OwnerSecurePass123!");
        mockMvc.perform(post("/api/auth/owner-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Invalid credentials should be rejected with 401 Unauthorized
        OwnerLoginRequest invalidReq = new OwnerLoginRequest("secowner@grocerychoice.com", "WrongPassword!");
        mockMvc.perform(post("/api/auth/owner-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PRODUCTION HEALTH: GET /api/health remains publicly accessible")
    void testHealthEndpointRemainsAccessible() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
