package com.grocerychoice.backend.seeder;

import com.grocerychoice.backend.entity.Category;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import com.grocerychoice.backend.repository.AddressRepository;
import com.grocerychoice.backend.repository.CategoryRepository;
import com.grocerychoice.backend.repository.ProductRepository;
import com.grocerychoice.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseSeederSecurityTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Category sampleCategory = new Category("Fruits & Vegetables", "Sample category", null, true);
        when(categoryRepository.existsByName(anyString())).thenReturn(true);
        when(categoryRepository.findByName(anyString())).thenReturn(Optional.of(sampleCategory));
        when(productRepository.existsBySku(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Default configuration (app.seed-users=false): catalog seeds safely, NO users are seeded")
    void testCatalogSeedingRunsAndUsersNotSeededWhenDisabled() {
        // In an empty product DB, productRepository.existsBySku returns false
        when(productRepository.existsBySku(anyString())).thenReturn(false);

        DatabaseSeeder seeder = new DatabaseSeeder(
                categoryRepository,
                userRepository,
                addressRepository,
                productRepository,
                passwordEncoder,
                false, // seedUsers = false
                null,  // bootstrapOwnerEmail
                null,  // bootstrapOwnerPassword (not provided)
                null,  // bootstrapOwnerPhone
                null,  // bootstrapOwnerName
                null   // devSeedPassword
        );

        when(userRepository.findByRole(Role.OWNER)).thenReturn(Collections.emptyList());

        seeder.run();

        // Catalog was checked and products saved
        verify(categoryRepository, atLeastOnce()).existsByName(anyString());
        verify(productRepository, atLeastOnce()).save(any());

        // Zero user saves occurred because seedUsers=false and bootstrap password is unset
        verify(userRepository, never()).save(any(User.class));
        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Existing users and password hashes are never deleted or modified by seeder")
    void testPreservesExistingUsersAndHashes() {
        String existingHash = "$2a$10$existingHashThatMustNeverBeModifiedOrOverwritten123";
        User existingOwner = new User("owner@grocerychoice.com", "+91 98765 43211", "Existing Owner", existingHash, Role.OWNER);
        existingOwner.setPrimaryOwner(true);
        existingOwner.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByRole(Role.OWNER)).thenReturn(List.of(existingOwner));
        when(userRepository.findByPrimaryOwnerTrue()).thenReturn(Optional.of(existingOwner));

        DatabaseSeeder seeder = new DatabaseSeeder(
                categoryRepository,
                userRepository,
                addressRepository,
                productRepository,
                passwordEncoder,
                false,
                "bootstrap@grocerychoice.com",
                "NewBootstrapPass123!", // Bootstrap supplied but existing owner already present
                "+91 99999 00000",
                "Bootstrap Owner",
                null
        );

        seeder.run();

        // Existing owner was not overwritten or re-saved with a new password
        assertEquals(existingHash, existingOwner.getPasswordHash());
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Initial owner bootstrap provisions owner when no owner exists and valid password provided")
    void testBootstrapOwnerProvisioningWhenNoOwnerExists() {
        when(userRepository.findByRole(Role.OWNER)).thenReturn(Collections.emptyList());
        when(userRepository.findByEmail("admin.boot@grocerychoice.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecureBootstrapPassword123!")).thenReturn("$2a$10$encodedSecureHash");

        DatabaseSeeder seeder = new DatabaseSeeder(
                categoryRepository,
                userRepository,
                addressRepository,
                productRepository,
                passwordEncoder,
                false, // seedUsers=false
                "admin.boot@grocerychoice.com",
                "SecureBootstrapPassword123!",
                "+91 98765 00001",
                "Bootstrapped Owner",
                null
        );

        seeder.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("admin.boot@grocerychoice.com", savedUser.getEmail());
        assertEquals("Bootstrapped Owner", savedUser.getFullName());
        assertEquals(Role.OWNER, savedUser.getRole());
        assertTrue(savedUser.isPrimaryOwner());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertEquals("$2a$10$encodedSecureHash", savedUser.getPasswordHash());
    }

    @Test
    @DisplayName("Initial owner bootstrap rejects password shorter than 12 characters")
    void testBootstrapOwnerRejectsShortPassword() {
        when(userRepository.findByRole(Role.OWNER)).thenReturn(Collections.emptyList());

        DatabaseSeeder seeder = new DatabaseSeeder(
                categoryRepository,
                userRepository,
                addressRepository,
                productRepository,
                passwordEncoder,
                false,
                "admin.boot@grocerychoice.com",
                "ShortPass1!", // Less than 12 chars
                "+91 98765 00001",
                "Bootstrapped Owner",
                null
        );

        seeder.run();

        // No owner created due to short password
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("When app.seed-users=true, development users are seeded without hardcoded passwords")
    void testDevelopmentUserSeedingWhenExplicitlyEnabled() {
        when(userRepository.findByRole(Role.OWNER)).thenReturn(Collections.emptyList());
        when(userRepository.findByRole(Role.CUSTOMER)).thenReturn(Collections.emptyList());
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(Collections.emptyList());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dynamicallyHashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DatabaseSeeder seeder = new DatabaseSeeder(
                categoryRepository,
                userRepository,
                addressRepository,
                productRepository,
                passwordEncoder,
                true, // seedUsers=true
                null,
                null,
                null,
                null,
                null  // Random secure password generated
        );

        seeder.run();

        // 3 users seeded (CUSTOMER, OWNER, ADMIN) + 1 address
        verify(userRepository, times(3)).save(any(User.class));
        verify(addressRepository, times(1)).save(any());
        // Customer does not use password; Owner and Admin use dynamic password
        verify(passwordEncoder, times(2)).encode(anyString());
    }
}
