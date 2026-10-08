package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.SendOtpRequest;
import com.grocerychoice.backend.entity.OtpPurpose;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import com.grocerychoice.backend.repository.UserRepository;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security Integration Tests verifying that when development mode is explicitly enabled (app.dev-mode=true),
 * the development-only endpoints (/api/auth/owner-token and /api/auth/dev-otp/**) are permitted for local development.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.dev-mode=true",
        "otp.provider=dev"
})
class DevModeEnabledSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Ensure at least one owner user exists
        userRepository.findByEmail("devowner@grocerychoice.com").orElseGet(() -> {
            User u = new User();
            u.setFullName("Dev Owner");
            u.setEmail("devowner@grocerychoice.com");
            u.setPhone("919876599991");
            u.setRole(Role.OWNER);
            u.setStatus(UserStatus.ACTIVE);
            u.setPasswordHash(passwordEncoder.encode("DevOwnerPass123!"));
            return userRepository.save(u);
        });
    }

    @Test
    @DisplayName("DEV MODE: GET /api/auth/owner-token succeeds without authentication when dev mode is explicitly enabled")
    void testOwnerTokenSucceedsWhenDevModeEnabled() throws Exception {
        mockMvc.perform(get("/api/auth/owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.role", is("OWNER")));
    }

    @Test
    @DisplayName("DEV MODE: GET /api/auth/dev-otp/{identifier} succeeds and returns OTP when dev mode is explicitly enabled")
    void testDevOtpSucceedsWhenDevModeEnabled() throws Exception {
        String testPhone = "919876599999";
        SendOtpRequest sendReq = new SendOtpRequest(testPhone, OtpPurpose.LOGIN);

        // Send OTP first to populate dev cache
        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk());

        // Fetch OTP from dev endpoint
        mockMvc.perform(get("/api/auth/dev-otp/" + testPhone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier", is(testPhone)))
                .andExpect(jsonPath("$.otp", notNullValue()));
    }
}
