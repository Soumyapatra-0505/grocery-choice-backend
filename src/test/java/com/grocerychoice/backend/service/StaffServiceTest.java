package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.CreateStaffRequest;
import com.grocerychoice.backend.dto.UpdateContactRequest;
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

    @Test
    @DisplayName("Primary Owner can update another staff member's email")
    void testUpdateContact_PrimaryOwnerCanChangeEmail() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(userRepository.findByEmailIgnoreCase("newstaff@grocerychoice.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail("newstaff@grocerychoice.com");

        UserSummaryResponse res = staffService.updateContact(10L, req, primaryOwnerPrincipal);

        assertEquals("newstaff@grocerychoice.com", res.getEmail());
        assertEquals(10L, res.getId());
        assertEquals(Role.STAFF, res.getRole());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Primary Owner can update another staff member's phone")
    void testUpdateContact_PrimaryOwnerCanChangePhone() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(userRepository.findAllByCleanPhone("9876511111")).thenReturn(List.of());
        when(userRepository.findByPhone("+91 98765 11111")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("9876511111");

        UserSummaryResponse res = staffService.updateContact(10L, req, primaryOwnerPrincipal);

        assertEquals("+91 98765 11111", res.getPhone());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Primary Owner can update both email and phone simultaneously")
    void testUpdateContact_PrimaryOwnerCanChangeBoth() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(userRepository.findByEmailIgnoreCase("both@grocerychoice.com")).thenReturn(Optional.empty());
        when(userRepository.findAllByCleanPhone("9876522222")).thenReturn(List.of());
        when(userRepository.findByPhone("+91 98765 22222")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Updated Name");
        req.setEmail("both@grocerychoice.com");
        req.setPhone("+91 98765 22222");

        UserSummaryResponse res = staffService.updateContact(10L, req, primaryOwnerPrincipal);

        assertEquals("Updated Name", res.getFullName());
        assertEquals("both@grocerychoice.com", res.getEmail());
        assertEquals("+91 98765 22222", res.getPhone());
        assertEquals(10L, res.getId());
        assertEquals(Role.STAFF, res.getRole());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Duplicate email is rejected with 409 Conflict")
    void testUpdateContact_DuplicateEmailRejected() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);
        User existingOther = new User("taken@grocerychoice.com", "+91 98765 99999", "Other User", "hash", Role.CUSTOMER);
        existingOther.setId(99L);

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(userRepository.findByEmailIgnoreCase("taken@grocerychoice.com")).thenReturn(Optional.of(existingOther));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail("taken@grocerychoice.com");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(10L, req, primaryOwnerPrincipal));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Email already belongs to another account"));
    }

    @Test
    @DisplayName("Duplicate phone is rejected with 409 Conflict")
    void testUpdateContact_DuplicatePhoneRejected() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);
        User existingOther = new User("other@grocerychoice.com", "+91 98765 33333", "Other User", "hash", Role.CUSTOMER);
        existingOther.setId(88L);

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(userRepository.findAllByCleanPhone("9876533333")).thenReturn(List.of(existingOther));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("9876533333");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(10L, req, primaryOwnerPrincipal));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Phone number already belongs to another account"));
    }

    @Test
    @DisplayName("Invalid email format is rejected with 400 Bad Request")
    void testUpdateContact_InvalidEmailRejected() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail("not-an-email");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(10L, req, primaryOwnerPrincipal));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid email address"));
    }

    @Test
    @DisplayName("Invalid phone format is rejected with 400 Bad Request")
    void testUpdateContact_InvalidPhoneRejected() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setPhone("12345");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(10L, req, primaryOwnerPrincipal));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid phone number"));
    }

    @Test
    @DisplayName("Non-Primary Owner cannot modify another Owner's contact information")
    void testUpdateContact_NonPrimaryOwnerCannotModifyAnotherOwner() {
        User secondaryOwner = new User("coowner@grocerychoice.com", "+91 98765 44444", "Co-Owner", "hash", Role.OWNER);
        secondaryOwner.setId(20L);
        secondaryOwner.setPrimaryOwner(false);

        User targetOwner = new User("targetowner@grocerychoice.com", "+91 98765 55555", "Target Owner", "hash", Role.OWNER);
        targetOwner.setId(21L);
        targetOwner.setPrimaryOwner(false);

        UserPrincipal secondaryOwnerPrincipal = UserPrincipal.create(secondaryOwner);

        when(userRepository.findById(21L)).thenReturn(Optional.of(targetOwner));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Attempted Hack");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(21L, req, secondaryOwnerPrincipal));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Owner cannot modify another Owner's contact information"));
    }

    @Test
    @DisplayName("Admin cannot modify Owner contact information")
    void testUpdateContact_AdminCannotModifyOwner() {
        User targetOwner = new User("targetowner@grocerychoice.com", "+91 98765 55555", "Target Owner", "hash", Role.OWNER);
        targetOwner.setId(21L);
        targetOwner.setPrimaryOwner(false);

        when(userRepository.findById(21L)).thenReturn(Optional.of(targetOwner));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Admin Attempt");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(21L, req, adminPrincipal));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Admin cannot modify Owner contact information"));
    }

    @Test
    @DisplayName("Admin cannot change another user's email address")
    void testUpdateContact_AdminCannotChangeEmail() {
        User staff = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staff.setId(10L);

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setEmail("newemail@grocerychoice.com");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(10L, req, adminPrincipal));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Only the Primary Owner can change another user's email address"));
    }

    @Test
    @DisplayName("Staff cannot modify another user's contact information")
    void testUpdateContact_StaffCannotModifyAnotherUser() {
        User staffUser = new User("staff@grocerychoice.com", "+91 98765 00000", "Staff User", "hash", Role.STAFF);
        staffUser.setId(10L);
        UserPrincipal staffPrincipal = UserPrincipal.create(staffUser);

        User targetStaff = new User("otherstaff@grocerychoice.com", "+91 98765 11111", "Other Staff", "hash", Role.STAFF);
        targetStaff.setId(11L);

        when(userRepository.findById(11L)).thenReturn(Optional.of(targetStaff));

        UpdateContactRequest req = new UpdateContactRequest();
        req.setFullName("Staff Attempt");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                staffService.updateContact(11L, req, staffPrincipal));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
