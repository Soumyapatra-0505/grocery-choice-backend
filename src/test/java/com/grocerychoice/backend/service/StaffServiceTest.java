package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.CreateStaffRequest;
import com.grocerychoice.backend.dto.UpdateStaffRequest;
import com.grocerychoice.backend.dto.UserSummaryResponse;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StaffService staffService;

    private User primaryOwnerUser;
    private User adminUser;
    private User customerUser;
    private UserPrincipal primaryOwnerPrincipal;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        primaryOwnerUser = new User("owner@grocerychoice.com", "+91 98765 43211", "Primary Owner", "hash", Role.OWNER);
        primaryOwnerUser.setId(1L);
        primaryOwnerUser.setPrimaryOwner(true);
        primaryOwnerUser.setDesignation("Store Owner");

        adminUser = new User("admin@grocerychoice.com", "+91 98765 43212", "Store Manager", "hash", Role.ADMIN);
        adminUser.setId(2L);
        adminUser.setPrimaryOwner(false);
        adminUser.setDesignation("Store Manager");

        customerUser = new User("customer@example.com", "+91 98765 43210", "Priya Patel", null, Role.CUSTOMER);
        customerUser.setId(3L);

        primaryOwnerPrincipal = UserPrincipal.create(primaryOwnerUser);
        adminPrincipal = UserPrincipal.create(adminUser);
    }

    @Test
    @DisplayName("Primary Owner can create new staff member")
    void testCreateStaff_NewStaff() {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setFullName("Amit Kumar");
        req.setEmail("amit@grocerychoice.com");
        req.setPhone("+91 91234 56789");
        req.setRole(Role.STAFF);
        req.setDesignation("Inventory Manager");

        when(userRepository.findByEmailIgnoreCase("amit@grocerychoice.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+91 91234 56789")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserSummaryResponse response = staffService.createStaff(req, primaryOwnerPrincipal);

        assertNotNull(response);
        assertEquals("Amit Kumar", response.getFullName());
        assertEquals(Role.STAFF, response.getRole());
        assertEquals("Inventory Manager", response.getDesignation());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Creating staff promotes existing customer account without deleting data")
    void testCreateStaff_PromotesExistingCustomer() {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setFullName("Priya Patel");
        req.setEmail("customer@example.com");
        req.setPhone("+91 98765 43210");
        req.setRole(Role.ADMIN);
        req.setDesignation("Operations Manager");

        when(userRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(customerUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse response = staffService.createStaff(req, primaryOwnerPrincipal);

        assertEquals(Role.ADMIN, response.getRole());
        assertEquals("Operations Manager", response.getDesignation());
        assertEquals(3L, response.getId()); // Preserves ID and existing customer record
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Admin cannot create an OWNER account")
    void testCreateStaff_AdminCannotCreateOwner() {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setFullName("New Owner");
        req.setEmail("newowner@grocerychoice.com");
        req.setRole(Role.OWNER);

        assertThrows(ResponseStatusException.class, () -> staffService.createStaff(req, adminPrincipal));
    }

    @Test
    @DisplayName("Cannot change role of Primary Owner")
    void testChangeRole_CannotDemotePrimaryOwner() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryOwnerUser));

        assertThrows(ResponseStatusException.class, () ->
                staffService.changeRole(1L, Role.ADMIN, primaryOwnerPrincipal));
    }

    @Test
    @DisplayName("Primary Owner can promote staff to OWNER")
    void testChangeRole_PromoteToOwner() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff", "hash", Role.STAFF);
        staff.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(staff));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse res = staffService.changeRole(5L, Role.OWNER, primaryOwnerPrincipal);

        assertEquals(Role.OWNER, res.getRole());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Cannot disable Primary Owner account")
    void testChangeStatus_CannotDisablePrimaryOwner() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryOwnerUser));

        assertThrows(ResponseStatusException.class, () ->
                staffService.changeStatus(1L, UserStatus.DISABLED, adminPrincipal));
    }

    @Test
    @DisplayName("Cannot disable own account")
    void testChangeStatus_CannotDisableSelf() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

        assertThrows(ResponseStatusException.class, () ->
                staffService.changeStatus(2L, UserStatus.DISABLED, adminPrincipal));
    }

    @Test
    @DisplayName("Revoking staff access demotes to CUSTOMER without deleting historical records")
    void testRemoveStaffAccess_DemotesToCustomer() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff", "hash", Role.STAFF);
        staff.setId(5L);
        staff.setDesignation("Cashier");

        when(userRepository.findById(5L)).thenReturn(Optional.of(staff));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSummaryResponse res = staffService.removeStaffAccess(5L, primaryOwnerPrincipal);

        assertEquals(Role.CUSTOMER, res.getRole());
        assertNull(res.getDesignation());
        assertEquals(5L, res.getId());
        verify(auditLogRepository, times(1)).save(any());
    }
}
