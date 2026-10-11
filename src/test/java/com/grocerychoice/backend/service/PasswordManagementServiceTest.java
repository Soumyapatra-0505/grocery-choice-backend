package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.ChangePasswordRequest;
import com.grocerychoice.backend.entity.AuditLog;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import com.grocerychoice.backend.repository.AddressRepository;
import com.grocerychoice.backend.repository.AuditLogRepository;
import com.grocerychoice.backend.repository.OtpVerificationRepository;
import com.grocerychoice.backend.repository.UserRepository;
import com.grocerychoice.backend.security.JwtTokenProvider;
import com.grocerychoice.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuthService authService;

    private static final String OLD_RAW_PASSWORD = "CurrentOldPassword123!";
    private static final String OLD_HASH = "$2a$10$existingBcryptHashValueOldPassword";
    private static final String NEW_RAW_PASSWORD = "BrandNewSecurePassword2026!";
    private static final String NEW_HASH = "$2a$10$brandNewBcryptHashValueNewPassword";

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                addressRepository,
                otpVerificationRepository,
                passwordEncoder,
                jwtTokenProvider,
                Optional.empty(),
                Optional.of(auditLogRepository),
                5,
                5,
                60
        );

        when(passwordEncoder.matches(eq(OLD_RAW_PASSWORD), eq(OLD_HASH))).thenReturn(true);
        when(passwordEncoder.matches(eq(NEW_RAW_PASSWORD), eq(OLD_HASH))).thenReturn(false);
        when(passwordEncoder.encode(eq(NEW_RAW_PASSWORD))).thenReturn(NEW_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Successful password change updates hash, preserves all user attributes, and writes audit log")
    void testSuccessfulPasswordChangePreservesUserAttributes() {
        User user = new User("owner@grocerychoice.com", "+91 98765 43211", "Primary Store Owner", OLD_HASH, Role.OWNER);
        user.setId(10L);
        user.setPrimaryOwner(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setDesignation("General Manager");
        user.setStoreHub("Flagship Hub");

        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(OLD_RAW_PASSWORD, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD);

        authService.changePassword(principal, request);

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());

        User saved = savedUserCaptor.getValue();
        // Password hash updated
        assertEquals(NEW_HASH, saved.getPasswordHash());

        // All identity and security attributes preserved
        assertEquals(10L, saved.getId());
        assertEquals("owner@grocerychoice.com", saved.getEmail());
        assertEquals("+91 98765 43211", saved.getPhone());
        assertEquals("Primary Store Owner", saved.getFullName());
        assertEquals(Role.OWNER, saved.getRole());
        assertTrue(saved.isPrimaryOwner());
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertEquals("General Manager", saved.getDesignation());
        assertEquals("Flagship Hub", saved.getStoreHub());

        // Audit log written
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Wrong current password is rejected with BAD_REQUEST (400) and user is not saved")
    void testWrongCurrentPasswordRejected() {
        User user = new User("admin@grocerychoice.com", "+91 98765 43212", "Store Admin", OLD_HASH, Role.ADMIN);
        user.setId(20L);
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        when(passwordEncoder.matches("WrongPassword123!", OLD_HASH)).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword123!", NEW_RAW_PASSWORD, NEW_RAW_PASSWORD);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(principal, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Incorrect current password"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("New password shorter than 12 characters is rejected with BAD_REQUEST (400)")
    void testWeakPasswordRejected() {
        User user = new User("admin@grocerychoice.com", "+91 98765 43212", "Store Admin", OLD_HASH, Role.ADMIN);
        user.setId(20L);
        UserPrincipal principal = UserPrincipal.create(user);

        ChangePasswordRequest request = new ChangePasswordRequest(OLD_RAW_PASSWORD, "Short123!", "Short123!");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(principal, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("at least 12 characters"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Password confirmation mismatch is rejected with BAD_REQUEST (400)")
    void testConfirmationMismatchRejected() {
        User user = new User("admin@grocerychoice.com", "+91 98765 43212", "Store Admin", OLD_HASH, Role.ADMIN);
        user.setId(20L);
        UserPrincipal principal = UserPrincipal.create(user);

        ChangePasswordRequest request = new ChangePasswordRequest(OLD_RAW_PASSWORD, NEW_RAW_PASSWORD, "DifferentConfirmationPassword123!");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(principal, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("do not match"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Reuse of current password as new password is rejected with BAD_REQUEST (400)")
    void testPasswordReuseRejected() {
        User user = new User("admin@grocerychoice.com", "+91 98765 43212", "Store Admin", OLD_HASH, Role.ADMIN);
        user.setId(20L);
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(OLD_RAW_PASSWORD, OLD_RAW_PASSWORD, OLD_RAW_PASSWORD);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(principal, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cannot be the same as the current password"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Unauthenticated request (principal is null) is rejected with UNAUTHORIZED (401)")
    void testUnauthenticatedAccessRejected() {
        ChangePasswordRequest request = new ChangePasswordRequest(OLD_RAW_PASSWORD, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(null, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Customer account attempting password change is rejected with FORBIDDEN (403)")
    void testCustomerAccountCannotChangePassword() {
        User user = new User("customer@grocerychoice.com", "+91 98765 43210", "Customer User", OLD_HASH, Role.CUSTOMER);
        user.setId(30L);
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(OLD_RAW_PASSWORD, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(principal, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("restricted to staff"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Disabled account attempting password change is rejected with FORBIDDEN (403)")
    void testDisabledAccountRejected() {
        User user = new User("staff@grocerychoice.com", "+91 98765 43215", "Disabled Staff", OLD_HASH, Role.STAFF);
        user.setId(40L);
        user.setStatus(UserStatus.DISABLED);
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(40L)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(OLD_RAW_PASSWORD, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(principal, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("disabled"));
        verify(userRepository, never()).save(any(User.class));
    }
}
