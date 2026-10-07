-- ==============================================================================
-- Grocery Choice: Delivery Assignment Schema Migration
-- Version: V3
-- Description: Adds delivery partner assignment columns to orders table.
-- Compatible with MySQL 8.0+ / MariaDB
-- Safe, non-destructive, and idempotent.
-- ==============================================================================

-- 1. Add delivery assignment columns to orders table
ALTER TABLE `orders`
    ADD COLUMN IF NOT EXISTS `assigned_delivery_user_id` BIGINT NULL AFTER `delivery_slot`,
    ADD COLUMN IF NOT EXISTS `assigned_at` DATETIME NULL AFTER `assigned_delivery_user_id`,
    ADD COLUMN IF NOT EXISTS `accepted_at` DATETIME NULL AFTER `assigned_at`;

-- 2. Create index on assigned_delivery_user_id and assigned_at for fast lookup
CREATE INDEX IF NOT EXISTS `idx_orders_assigned_delivery_user` ON `orders` (`assigned_delivery_user_id`);
CREATE INDEX IF NOT EXISTS `idx_orders_assigned_at` ON `orders` (`assigned_at`);
