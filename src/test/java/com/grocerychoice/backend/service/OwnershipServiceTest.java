package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.TransferOwnershipRequest;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.repository.AuditLogRepository;
import com.grocerychoice.backend.repository.UserRepository;
import com.grocerychoice.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnershipServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OwnershipService ownershipService;

    private User primaryOwnerUser;
    private User secondOwnerUser;
    private User adminUser;
    private UserPrincipal primaryOwnerPrincipal;
    private UserPrincipal nonPrimaryOwnerPrincipal;

    @BeforeEach
    void setUp() {
        primaryOwnerUser = new User("soumya@grocerychoice.com", "+91 98765 43211", "Soumya", "hash", Role.OWNER);
        primaryOwnerUser.setId(1L);
        primaryOwnerUser.setPrimaryOwner(true);
        primaryOwnerUser.setDesignation("Store Owner");

        secondOwnerUser = new User("rahul@grocerychoice.com", "+91 98765 43212", "Rahul", "hash", Role.OWNER);
        secondOwnerUser.setId(2L);
        secondOwnerUser.setPrimaryOwner(false);
        secondOwnerUser.setDesignation("Co-Owner");

        adminUser = new User("admin@grocerychoice.com", "+91 98765 43213", "Admin User", "hash", Role.ADMIN);
        adminUser.setId(3L);

        primaryOwnerPrincipal = UserPrincipal.create(primaryOwnerUser);
        nonPrimaryOwnerPrincipal = UserPrincipal.create(secondOwnerUser);
    }

    @Test
    @DisplayName("Successful Primary Ownership transfer from Soumya to Rahul (previous owner remains OWNER)")
    void testTransferPrimaryOwnership_Success() {
        TransferOwnershipRequest request = new TransferOwnershipRequest(2L, Role.OWNER, "TRANSFER");

        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryOwnerUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(secondOwnerUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = ownershipService.transferPrimaryOwnership(request, primaryOwnerPrincipal);

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));

        // Verify Rahul is now Primary Owner
        assertTrue(secondOwnerUser.isPrimaryOwner());
        assertEquals(Role.OWNER, secondOwnerUser.getRole());

        // Verify Soumya is no longer Primary Owner and remains OWNER
        assertFalse(primaryOwnerUser.isPrimaryOwner());
        assertEquals(Role.OWNER, primaryOwnerUser.getRole());

        // Verify audit log created
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Successful Primary Ownership transfer with previous owner stepping down to CUSTOMER")
    void testTransferPrimaryOwnership_StepDownToCustomer() {
        TransferOwnershipRequest request = new TransferOwnershipRequest(2L, Role.CUSTOMER, "TRANSFER");

        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryOwnerUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(secondOwnerUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = ownershipService.transferPrimaryOwnership(request, primaryOwnerPrincipal);

        assertTrue((Boolean) result.get("success"));
        assertFalse(primaryOwnerUser.isPrimaryOwner());
        assertEquals(Role.CUSTOMER, primaryOwnerUser.getRole());
        assertNull(primaryOwnerUser.getDesignation());
    }

    @Test
    @DisplayName("Non-primary owner cannot initiate ownership transfer")
    void testTransferPrimaryOwnership_ForbiddenForNonPrimaryOwner() {
        TransferOwnershipRequest request = new TransferOwnershipRequest(1L, Role.OWNER, "TRANSFER");

        when(userRepository.findById(2L)).thenReturn(Optional.of(secondOwnerUser));

        assertThrows(ResponseStatusException.class, () ->
                ownershipService.transferPrimaryOwnership(request, nonPrimaryOwnerPrincipal));
    }

    @Test
    @DisplayName("Cannot transfer ownership without exact 'TRANSFER' confirmation keyword")
    void testTransferPrimaryOwnership_InvalidKeyword() {
        TransferOwnershipRequest request = new TransferOwnershipRequest(2L, Role.OWNER, "WRONG_KEYWORD");

        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryOwnerUser));

        assertThrows(InvalidDataException.class, () ->
                ownershipService.transferPrimaryOwnership(request, primaryOwnerPrincipal));
    }

    @Test
    @DisplayName("Cannot transfer ownership to a non-OWNER account")
    void testTransferPrimaryOwnership_TargetNotOwner() {
        TransferOwnershipRequest request = new TransferOwnershipRequest(3L, Role.OWNER, "TRANSFER");

        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryOwnerUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(adminUser));

        assertThrows(InvalidDataException.class, () ->
                ownershipService.transferPrimaryOwnership(request, primaryOwnerPrincipal));
    }

    @Test
    @DisplayName("Cannot transfer ownership to disabled account")
    void testTransferPrimaryOwnership_TargetDisabled() {
        secondOwnerUser.setStatus(UserStatus.DISABLED);
        TransferOwnershipRequest request = new TransferOwnershipRequest(2L, Role.OWNER, "TRANSFER");

        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryOwnerUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(secondOwnerUser));

        assertThrows(InvalidDataException.class, () ->
                ownershipService.transferPrimaryOwnership(request, primaryOwnerPrincipal));
    }
}
