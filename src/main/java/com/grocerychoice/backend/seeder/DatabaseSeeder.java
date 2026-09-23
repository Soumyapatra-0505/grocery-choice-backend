package com.grocerychoice.backend.seeder;

import com.grocerychoice.backend.entity.Address;
import com.grocerychoice.backend.entity.Category;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.repository.AddressRepository;
import com.grocerychoice.backend.repository.CategoryRepository;
import com.grocerychoice.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent Database Seeder for Grocery Choice core data.
 *
 * Populates initial product categories, default customer, and address only if they do not already exist.
 * Repeated executions or Spring Boot restarts will never produce duplicate records.
 */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    private static final List<String> SEED_CATEGORIES = List.of(
        "Fruits & Vegetables",
        "Rice, Atta & Grains",
        "Dairy & Breakfast",
        "Snacks",
        "Beverages",
        "Personal Care",
        "Household Essentials",
        "Cleaning Supplies"
    );

    public DatabaseSeeder(CategoryRepository categoryRepository,
                          UserRepository userRepository,
                          AddressRepository addressRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    @Override
    public void run(String... args) {
        seedCategories();
        seedDefaultCustomer();
        seedDefaultOwner();
        seedDefaultAdmin();
    }

    private void seedDefaultCustomer() {
        if (userRepository.findByRole(Role.CUSTOMER).isEmpty()) {
            log.info("No customer found. Seeding default customer and address...");
            User customer = new User(
                "customer@grocerychoice.com",
                "+91 98765 43210",
                "Rahul Sharma",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Customer@123"),
                Role.CUSTOMER
            );
            customer = userRepository.save(customer);
            log.info("Seeded default customer with ID: {}", customer.getId());

            Address address = new Address(
                customer,
                "Flat 402, Green Glen Apartments",
                "Sector 14 Hub, Main Market Road",
                "Gurugram",
                "Haryana",
                "122001",
                "Near Metro Station",
                28.4595,
                77.0266,
                true
            );
            addressRepository.save(address);
            log.info("Seeded default address for customer.");
        }
    }

    private void seedDefaultOwner() {
        if (userRepository.findByRole(Role.OWNER).isEmpty()) {
            log.info("No owner user found. Seeding default owner user...");
            User owner = new User(
                "owner@grocerychoice.com",
                "+91 98765 43211",
                "Suresh Verma",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Admin@123"),
                Role.OWNER
            );
            owner = userRepository.save(owner);
            log.info("Seeded default owner with ID: {} and email: {}", owner.getId(), owner.getEmail());
        }
    }

    private void seedDefaultAdmin() {
        if (userRepository.findByRole(Role.ADMIN).isEmpty()) {
            log.info("No admin user found. Seeding default admin user...");
            User admin = new User(
                "admin@grocerychoice.com",
                "+91 98765 43212",
                "System Administrator",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Admin@123"),
                Role.ADMIN
            );
            admin = userRepository.save(admin);
            log.info("Seeded default admin with ID: {} and email: {}", admin.getId(), admin.getEmail());
        }
    }

    private void seedCategories() {
        log.info("Checking core categories initialization...");
        int addedCount = 0;

        for (String categoryName : SEED_CATEGORIES) {
            if (!categoryRepository.existsByName(categoryName)) {
                Category category = new Category(
                    categoryName,
                    categoryName + " category essentials for Grocery Choice",
                    null,
                    true
                );
                categoryRepository.save(category);
                addedCount++;
                log.info("Seeded category: '{}'", categoryName);
            } else {
                log.debug("Category already exists: '{}' (skipping)", categoryName);
            }
        }

        if (addedCount > 0) {
            log.info("Category seeding completed. Added {} new categories. Total categories in DB: {}",
                addedCount, categoryRepository.count());
        } else {
            log.info("All {} core categories already present in database. Total in DB: {}",
                SEED_CATEGORIES.size(), categoryRepository.count());
        }
    }
}
