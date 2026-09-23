# Grocery Choice - Backend API Service

This directory is reserved for the backend service powering both the **Customer Storefront** and the **Owner Management Portal**.

## Architecture & Technology Stack (Phase 2)

- **Framework**: Spring Boot 3 (Java 21)
- **Database**: MySQL 8.x
- **ORM / Persistence**: Spring Data JPA / Hibernate
- **Security & Authentication**: Spring Security with JWT (JSON Web Tokens)
- **Validation**: Jakarta Validation API
- **Build Tool**: Maven / Gradle
- **API Standard**: RESTful JSON endpoints

---

## Planned Data Schema

1. **`users`**:
   - `id`, `email`, `password_hash`, `full_name`, `phone`, `role` (`CUSTOMER` / `OWNER` / `ADMIN`), `created_at`
2. **`addresses`**:
   - `id`, `user_id`, `type` (`MANUAL` / `GEOLOCATION`), `label` (`HOME` / `WORK` / `OTHER`), `house_building`, `street_area`, `landmark`, `city`, `state`, `pincode`, `latitude`, `longitude`
3. **`categories`**:
   - `id`, `slug`, `name`, `description`, `icon`, `image_url`, `display_order`, `is_active`
4. **`products`**:
   - `id`, `name`, `slug`, `category_id`, `original_price`, `discount_price`, `unit`, `stock_count`, `stock_status`, `rating`, `review_count`, `is_popular`, `is_deal`, `is_household`, `image_url`, `description`
5. **`orders`**:
   - `id`, `user_id`, `subtotal`, `delivery_fee`, `discount_savings`, `total_amount`, `status` (`PLACED`, `PROCESSING`, `OUT_FOR_DELIVERY`, `DELIVERED`, `CANCELLED`), `delivery_slot`, `payment_method`, `delivery_address_json`, `created_at`, `updated_at`
6. **`order_items`**:
   - `id`, `order_id`, `product_id`, `product_name`, `unit_price`, `quantity`, `total_price`

---

## Planned API Endpoints

### Customer Endpoints
- `POST /api/auth/register`: Create customer account
- `POST /api/auth/login`: Customer login & JWT generation
- `GET /api/categories`: List active product categories
- `GET /api/products`: Browse and search products with filters & sorting
- `GET /api/products/{id}`: Fetch product details
- `GET /api/customer/locations`: Retrieve saved delivery locations
- `POST /api/customer/locations`: Save manual or GPS coordinates
- `POST /api/orders`: Submit new order
- `GET /api/orders/my-orders`: List authenticated customer's order history

### Owner / Admin Endpoints
- `POST /api/owner/login`: Owner authentication
- `GET /api/owner/dashboard/stats`: KPI metrics (Total products, orders, sales, low stock count)
- `GET /api/owner/products`: Manage inventory catalog
- `POST /api/owner/products`: Create new product
- `PUT /api/owner/products/{id}`: Update product details
- `DELETE /api/owner/products/{id}`: Soft-delete/remove product
- `PATCH /api/owner/products/{id}/stock`: Inline inventory stock count update
- `GET /api/owner/orders`: Full orders list with filtering by status
- `PATCH /api/owner/orders/{id}/status`: Update order fulfillment status
- `GET /api/owner/customers`: Customer directory & spend summaries
- `GET /api/owner/reports`: Sales summaries and category performance analytics
