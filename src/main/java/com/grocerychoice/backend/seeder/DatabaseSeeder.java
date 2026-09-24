package com.grocerychoice.backend.seeder;

import com.grocerychoice.backend.entity.Address;
import com.grocerychoice.backend.entity.Category;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.repository.AddressRepository;
import com.grocerychoice.backend.repository.CategoryRepository;
import com.grocerychoice.backend.entity.Product;
import com.grocerychoice.backend.entity.ProductUnit;
import com.grocerychoice.backend.repository.ProductRepository;
import com.grocerychoice.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
    private final ProductRepository productRepository;

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
                          AddressRepository addressRepository,
                          ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        seedCategories();
        seedDefaultCustomer();
        seedDefaultOwner();
        seedDefaultAdmin();
        seedProducts();
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

    private record SeedProduct(
        String sku,
        String name,
        String description,
        String categoryName,
        String imageUrl,
        ProductUnit unit,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        Integer stockQuantity,
        Boolean active
    ) {}

    private static final List<SeedProduct> SEED_PRODUCTS = List.of(
        // 1. Fresh Organic Robusta Bananas
        new SeedProduct(
            "GC-PROD-FV-01",
            "Fresh Organic Robusta Bananas",
            "Sweet, naturally ripened Robusta bananas rich in potassium and dietary fiber. Harvested locally with zero chemical ripening agents.",
            "Fruits & Vegetables",
            "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=600&auto=format&fit=crop&q=80",
            ProductUnit.KG,
            new BigDecimal("65.00"),
            new BigDecimal("48.00"),
            42,
            true
        ),
        // 2. Farm Fresh Roma Vine Tomatoes
        new SeedProduct(
            "GC-PROD-FV-02",
            "Farm Fresh Roma Vine Tomatoes",
            "Plump, firm, and juicy vine-ripened tomatoes ideal for rich curries, gravies, and fresh gourmet salads.",
            "Fruits & Vegetables",
            "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=600&auto=format&fit=crop&q=80",
            ProductUnit.KG,
            new BigDecimal("50.00"),
            new BigDecimal("38.00"),
            30,
            true
        ),
        // 3. Crisp Baby Spinach Bunch
        new SeedProduct(
            "GC-PROD-FV-03",
            "Crisp Baby Spinach Bunch",
            "Tender baby spinach leaves triple-washed and hydro-cooled to lock in crispness, iron, and essential vitamins.",
            "Fruits & Vegetables",
            "https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=600&auto=format&fit=crop&q=80",
            ProductUnit.GRAM,
            new BigDecimal("35.00"),
            new BigDecimal("24.00"),
            6,
            true
        ),
        // 4. Premium Hass Avocados
        new SeedProduct(
            "GC-PROD-FV-04",
            "Premium Hass Avocados",
            "Buttery and creamy imported Hass avocados loaded with healthy monounsaturated fats. Perfect for toast and guacamole.",
            "Fruits & Vegetables",
            "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=600&auto=format&fit=crop&q=80",
            ProductUnit.PIECE,
            new BigDecimal("240.00"),
            new BigDecimal("189.00"),
            18,
            true
        ),
        // 5. Chakki Fresh Shudh Whole Wheat Atta
        new SeedProduct(
            "GC-PROD-RAG-01",
            "Chakki Fresh Shudh Whole Wheat Atta",
            "100% whole wheat grain flour ground using traditional stone chakki to retain natural dietary fiber and make rotis delightfully soft.",
            "Rice, Atta & Grains",
            "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=600&auto=format&fit=crop&q=80",
            ProductUnit.KG,
            new BigDecimal("285.00"),
            new BigDecimal("229.00"),
            50,
            true
        ),
        // 6. Aged Royal Basmati Rozana Rice
        new SeedProduct(
            "GC-PROD-RAG-02",
            "Aged Royal Basmati Rozana Rice",
            "Long slender basmati rice grains aged for over 2 years for an unmistakable floral aroma and non-sticky texture.",
            "Rice, Atta & Grains",
            "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=600&auto=format&fit=crop&q=80",
            ProductUnit.KG,
            new BigDecimal("420.00"),
            new BigDecimal("335.00"),
            25,
            true
        ),
        // 7. Organic Unpolished Toor Dal
        new SeedProduct(
            "GC-PROD-RAG-03",
            "Organic Unpolished Toor Dal",
            "Unpolished yellow pigeon peas free from synthetic dyes and additives. High in clean plant protein and easy to digest.",
            "Rice, Atta & Grains",
            "https://images.unsplash.com/photo-1546833999-b9f581a1996d?w=600&auto=format&fit=crop&q=80",
            ProductUnit.KG,
            new BigDecimal("175.00"),
            new BigDecimal("139.00"),
            38,
            true
        ),
        // 8. Farm Fresh Homogenized Cow Milk
        new SeedProduct(
            "GC-PROD-DB-01",
            "Farm Fresh Homogenized Cow Milk",
            "Pure, pasteurized cow milk sourced daily from ethical partner dairies. Rich in calcium and fortified with Vitamin D.",
            "Dairy & Breakfast",
            "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=600&auto=format&fit=crop&q=80",
            ProductUnit.LITRE,
            new BigDecimal("68.00"),
            new BigDecimal("56.00"),
            65,
            true
        ),
        // 9. Artisanal Natural Greek Yogurt
        new SeedProduct(
            "GC-PROD-DB-02",
            "Artisanal Natural Greek Yogurt",
            "Thick, velvety strained yogurt packed with 12g protein per serving and active gut-friendly probiotic cultures.",
            "Dairy & Breakfast",
            "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=600&auto=format&fit=crop&q=80",
            ProductUnit.GRAM,
            new BigDecimal("150.00"),
            new BigDecimal("119.00"),
            22,
            true
        ),
        // 10. Free-Range Brown Eggs (Pack of 6)
        new SeedProduct(
            "GC-PROD-DB-03",
            "Free-Range Brown Eggs (Pack of 6)",
            "Certified antibiotic-free brown eggs laid by cruelty-free hens fed on organic grain and flaxseed diets.",
            "Dairy & Breakfast",
            "https://images.unsplash.com/photo-1516448620398-c5f44bf9f441?w=600&auto=format&fit=crop&q=80",
            ProductUnit.PACK,
            new BigDecimal("90.00"),
            new BigDecimal("72.00"),
            45,
            true
        ),
        // 11. Artisan Crusty Sourdough Boule
        new SeedProduct(
            "GC-PROD-DB-04",
            "Artisan Crusty Sourdough Boule",
            "Slow-fermented for 36 hours with a golden caramelized crust and airy, aromatic crumb. Baked fresh every sunrise.",
            "Dairy & Breakfast",
            "https://images.unsplash.com/photo-1589367920969-ab8e050bbb04?w=600&auto=format&fit=crop&q=80",
            ProductUnit.GRAM,
            new BigDecimal("140.00"),
            new BigDecimal("110.00"),
            4,
            true
        ),
        // 12. Crunchy Slow-Roasted Salted Almonds
        new SeedProduct(
            "GC-PROD-SN-01",
            "Crunchy Slow-Roasted Salted Almonds",
            "Hand-picked California almonds gently dry-roasted in Himalayan pink salt. High in vitamin E, magnesium, and crunch.",
            "Snacks",
            "https://images.unsplash.com/photo-1508746829417-e6f548d8d6ed?w=600&auto=format&fit=crop&q=80",
            ProductUnit.GRAM,
            new BigDecimal("299.00"),
            new BigDecimal("219.00"),
            34,
            true
        ),
        // 13. Baked Multigrain Tortilla Crisps
        new SeedProduct(
            "GC-PROD-SN-02",
            "Baked Multigrain Tortilla Crisps",
            "Crispy oven-baked crisps crafted from corn, oats, and chia seeds seasoned with zesty salsa herbs. 40% less fat.",
            "Snacks",
            "https://images.unsplash.com/photo-1513456852971-30c0b8199d4d?w=600&auto=format&fit=crop&q=80",
            ProductUnit.GRAM,
            new BigDecimal("95.00"),
            new BigDecimal("75.00"),
            40,
            true
        ),
        // 14. Dark Chocolate & Cranberry Granola Bar
        new SeedProduct(
            "GC-PROD-SN-03",
            "Dark Chocolate & Cranberry Granola Bar",
            "Wholesome rolled oats blended with 70% dark chocolate chunks and wild dried cranberries for sustained midday energy.",
            "Snacks",
            "https://images.unsplash.com/photo-1622484216809-54316a3f9e9d?w=600&auto=format&fit=crop&q=80",
            ProductUnit.PACK,
            new BigDecimal("199.00"),
            new BigDecimal("149.00"),
            28,
            true
        ),
        // 15. 100% Pure Tender Coconut Water
        new SeedProduct(
            "GC-PROD-BEV-01",
            "100% Pure Tender Coconut Water",
            "Refreshing isotonic coconut water harvested from tender green coconuts. Zero added sugar and no preservatives.",
            "Beverages",
            "https://images.unsplash.com/photo-1551024709-8f23befc6f87?w=600&auto=format&fit=crop&q=80",
            ProductUnit.LITRE,
            new BigDecimal("160.00"),
            new BigDecimal("119.00"),
            50,
            true
        ),
        // 16. Cold Pressed Valencia Orange Juice
        new SeedProduct(
            "GC-PROD-BEV-02",
            "Cold Pressed Valencia Orange Juice",
            "100% freshly cold-squeezed sun-ripened oranges preserving natural pulp, enzymes, and daily immunity-boosting Vitamin C.",
            "Beverages",
            "https://images.unsplash.com/photo-1613478223719-2ab802602423?w=600&auto=format&fit=crop&q=80",
            ProductUnit.MILLILITRE,
            new BigDecimal("120.00"),
            new BigDecimal("95.00"),
            20,
            true
        ),
        // 17. Single Estate Assam Orthodox Gold Tea
        new SeedProduct(
            "GC-PROD-BEV-03",
            "Single Estate Assam Orthodox Gold Tea",
            "Robust, malty black tea leaves tipped with golden buds from pristine upper Assam tea gardens.",
            "Beverages",
            "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?w=600&auto=format&fit=crop&q=80",
            ProductUnit.GRAM,
            new BigDecimal("320.00"),
            new BigDecimal("245.00"),
            16,
            true
        ),
        // 18. Organic Neem & Tea Tree Purifying Face Wash
        new SeedProduct(
            "GC-PROD-PC-01",
            "Organic Neem & Tea Tree Purifying Face Wash",
            "Sulfate-free botanical cleanser designed to control excess sebum and clear blemishes without stripping natural skin oils.",
            "Personal Care",
            "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=600&auto=format&fit=crop&q=80",
            ProductUnit.MILLILITRE,
            new BigDecimal("225.00"),
            new BigDecimal("175.00"),
            30,
            true
        ),
        // 19. Cold Pressed Virgin Coconut Hair & Body Oil
        new SeedProduct(
            "GC-PROD-PC-02",
            "Cold Pressed Virgin Coconut Hair & Body Oil",
            "Extracted from fresh coconut milk using cold centrifuge technology. 100% pure, fragrant, and deep-conditioning.",
            "Personal Care",
            "https://images.unsplash.com/photo-1608248597359-bb5e7e171b3e?w=600&auto=format&fit=crop&q=80",
            ProductUnit.MILLILITRE,
            new BigDecimal("280.00"),
            new BigDecimal("215.00"),
            24,
            true
        ),
        // 20. Pure Soothing Aloe Vera Gel 99%
        new SeedProduct(
            "GC-PROD-PC-03",
            "Pure Soothing Aloe Vera Gel 99%",
            "Lightweight, ultra-hydrating multipurpose gel made from fresh inner aloe leaves. Relieves irritation, burns, and dryness.",
            "Personal Care",
            "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=600&auto=format&fit=crop&q=80",
            ProductUnit.GRAM,
            new BigDecimal("180.00"),
            new BigDecimal("135.00"),
            5,
            true
        ),
        // 21. Ultra Absorbent 2-Ply Kitchen Paper Towels
        new SeedProduct(
            "GC-PROD-HE-01",
            "Ultra Absorbent 2-Ply Kitchen Paper Towels",
            "Heavy duty, food-safe embossed paper towels engineered to soak grease and liquid spills rapidly without disintegrating.",
            "Household Essentials",
            "https://images.unsplash.com/photo-1583947215259-38e31be8751f?w=600&auto=format&fit=crop&q=80",
            ProductUnit.PACK,
            new BigDecimal("175.00"),
            new BigDecimal("129.00"),
            60,
            true
        ),
        // 22. 100% Oxo-Biodegradable Garbage Bags
        new SeedProduct(
            "GC-PROD-HE-02",
            "100% Oxo-Biodegradable Garbage Bags",
            "Tear-resistant leak-proof waste disposal bags with practical tie string handles. Eco-friendly and odorless.",
            "Household Essentials",
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&auto=format&fit=crop&q=80",
            ProductUnit.PACK,
            new BigDecimal("195.00"),
            new BigDecimal("145.00"),
            48,
            true
        ),
        // 23. Food Grade Heavy Aluminium Foil Roll
        new SeedProduct(
            "GC-PROD-HE-03",
            "Food Grade Heavy Aluminium Foil Roll",
            "Extra thick food wrap foil with built-in metal cutter edge. Keeps meal packs piping warm and moisture fresh.",
            "Household Essentials",
            "https://images.unsplash.com/photo-1584813470613-5b1c1cad3d69?w=600&auto=format&fit=crop&q=80",
            ProductUnit.PIECE,
            new BigDecimal("240.00"),
            new BigDecimal("185.00"),
            35,
            true
        ),
        // 24. Plant-Powered Citrus Dishwash Liquid Gel
        new SeedProduct(
            "GC-PROD-CS-01",
            "Plant-Powered Citrus Dishwash Liquid Gel",
            "Natural lemon & lime formula that cuts through stubborn grease and burnt stains effortlessly while remaining gentle on hands.",
            "Cleaning Supplies",
            "https://images.unsplash.com/photo-1585421514738-01798e348b17?w=600&auto=format&fit=crop&q=80",
            ProductUnit.MILLILITRE,
            new BigDecimal("165.00"),
            new BigDecimal("125.00"),
            55,
            true
        ),
        // 25. Pine & Eucalyptus Disinfectant Surface Cleaner
        new SeedProduct(
            "GC-PROD-CS-02",
            "Pine & Eucalyptus Disinfectant Surface Cleaner",
            "Kills 99.9% of bacteria and germs. Leaves tile and marble floors sparkling clean with an uplifting natural woodland scent.",
            "Cleaning Supplies",
            "https://images.unsplash.com/photo-1584813470613-5b1c1cad3d69?w=600&auto=format&fit=crop&q=80",
            ProductUnit.LITRE,
            new BigDecimal("199.00"),
            new BigDecimal("155.00"),
            40,
            true
        ),
        // 26. Multi-Surface Antibacterial Kitchen Spray
        new SeedProduct(
            "GC-PROD-CS-03",
            "Multi-Surface Antibacterial Kitchen Spray",
            "Fast-evaporating degreaser spray safe for food prep countertops, microwaves, induction tops, and stainless steel sinks.",
            "Cleaning Supplies",
            "https://images.unsplash.com/photo-1563453392212-326f5e854473?w=600&auto=format&fit=crop&q=80",
            ProductUnit.MILLILITRE,
            new BigDecimal("180.00"),
            new BigDecimal("139.00"),
            0,
            true
        )
    );

    private void seedProducts() {
        log.info("Checking product catalog initialization...");
        int addedCount = 0;
        int skippedCount = 0;

        for (SeedProduct sp : SEED_PRODUCTS) {
            if (productRepository.existsBySku(sp.sku())) {
                log.debug("Product with SKU '{}' already exists. Skipping.", sp.sku());
                skippedCount++;
                continue;
            }

            Optional<Category> categoryOpt = categoryRepository.findByName(sp.categoryName());
            if (categoryOpt.isEmpty()) {
                log.warn("Category '{}' not found for product '{}' (SKU: {}). Skipping.",
                    sp.categoryName(), sp.name(), sp.sku());
                continue;
            }

            Product product = new Product(
                sp.name(),
                sp.description(),
                sp.sku(),
                categoryOpt.get(),
                cleanImageUrl(sp.imageUrl()),
                sp.unit(),
                sp.mrp(),
                sp.sellingPrice(),
                sp.stockQuantity(),
                sp.active()
            );

            productRepository.save(product);
            addedCount++;
            log.info("Seeded product: '{}' (SKU: {}, Category: '{}')",
                product.getName(), product.getSku(), sp.categoryName());
        }

        if (addedCount > 0) {
            log.info("Product seeding completed. Added {} new products. Total products in DB: {}",
                addedCount, productRepository.count());
        } else {
            log.info("All {} seed products already present in database ({} skipped). Total in DB: {}",
                SEED_PRODUCTS.size(), skippedCount, productRepository.count());
        }
    }

    private static String cleanImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.startsWith("![") && url.contains("(") && url.endsWith(")")) {
            url = url.substring(url.indexOf("(") + 1, url.length() - 1).trim();
        }
        return url;
    }
}
