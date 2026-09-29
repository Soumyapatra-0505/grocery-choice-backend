-- ==============================================================================
-- Grocery Choice: Staff Management & Ownership Management Schema Migration
-- Version: V2
-- Description: Adds designations, audit_logs tables and staff/ownership columns to users.
-- Compatible with MySQL 8.0+ / MariaDB
-- Safe, non-destructive, and idempotent.
-- ==============================================================================

-- 1. Create Designations Table
CREATE TABLE IF NOT EXISTS `designations` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(100) NOT NULL UNIQUE,
    `description` VARCHAR(500) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create Audit Logs Table
CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `action` VARCHAR(100) NOT NULL,
    `actor_id` BIGINT NULL,
    `actor_email` VARCHAR(150) NULL,
    `actor_name` VARCHAR(150) NULL,
    `target_id` BIGINT NULL,
    `target_email` VARCHAR(150) NULL,
    `target_name` VARCHAR(150) NULL,
    `details` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_audit_logs_action` (`action`),
    INDEX `idx_audit_logs_actor_id` (`actor_id`),
    INDEX `idx_audit_logs_target_id` (`target_id`),
    INDEX `idx_audit_logs_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Add Columns to users table (Check if columns exist before altering)
-- NOTE: In MySQL 8.0.19+, you can use `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.
-- For earlier versions, run these ALTER statements if the columns do not yet exist:

ALTER TABLE `users` 
    ADD COLUMN IF NOT EXISTS `primary_owner` BOOLEAN NOT NULL DEFAULT FALSE AFTER `role`,
    ADD COLUMN IF NOT EXISTS `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER `primary_owner`,
    ADD COLUMN IF NOT EXISTS `designation` VARCHAR(100) NULL AFTER `status`,
    ADD COLUMN IF NOT EXISTS `store_hub` VARCHAR(100) NULL AFTER `designation`,
    ADD COLUMN IF NOT EXISTS `custom_permissions` TEXT NULL AFTER `store_hub`;

-- 4. Create index on primary_owner and status for fast lookup
CREATE INDEX IF NOT EXISTS `idx_users_primary_owner` ON `users` (`primary_owner`);
CREATE INDEX IF NOT EXISTS `idx_users_status` ON `users` (`status`);

-- 5. Seed Initial Business Designations (Ignore duplicates)
INSERT IGNORE INTO `designations` (`title`, `description`, `created_at`, `updated_at`) VALUES
('Store Owner', 'Overall ownership and governance of Grocery Choice operations', NOW(), NOW()),
('Store Manager', 'Operational management of store hub, inventory, and staff execution', NOW(), NOW()),
('Inventory Manager', 'Stock auditing, inventory replenishment, and supplier receiving', NOW(), NOW()),
('Sales Manager', 'Promotions, customer pricing, sales campaigns, and conversion optimization', NOW(), NOW()),
('Operations Manager', 'Fulfillment scheduling, dispatch logistics, and workflow coordination', NOW(), NOW()),
('Accountant', 'Financial auditing, payment gateway reconciliations, and bookkeeping', NOW(), NOW()),
('Customer Support', 'Customer resolution, order tracking inquiries, and ticket handling', NOW(), NOW()),
('Delivery Manager', 'Fleet routing, driver dispatching, and delivery time management', NOW(), NOW()),
('Warehouse Manager', 'Warehouse bin organization, shelf audits, and packing quality control', NOW(), NOW());

-- 6. Designate Primary Owner (if not already set)
-- Sets primary_owner = TRUE for the first existing OWNER account (e.g. Soumya / owner@grocerychoice.com)
UPDATE `users` 
SET `primary_owner` = TRUE, `designation` = 'Store Owner'
WHERE `role` = 'OWNER' 
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM `users` WHERE `primary_owner` = TRUE) AS po)
LIMIT 1;

-- 7. Ensure default admin has designation if empty
UPDATE `users`
SET `designation` = 'Store Manager'
WHERE `role` = 'ADMIN' AND (`designation` IS NULL OR `designation` = '');
