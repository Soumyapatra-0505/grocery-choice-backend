-- ==============================================================================
-- Grocery Choice: Delivery Lifecycle Schema Migration
-- Version: V4
-- Description: Adds pickup, OTP verification, COD, and delivery completion columns.
-- Compatible with MySQL 8.0+ / MariaDB
-- Safe, non-destructive, and idempotent.
-- ==============================================================================

-- 1. Add delivery lifecycle columns to orders table
ALTER TABLE `orders`
    ADD COLUMN IF NOT EXISTS `picked_up_at` DATETIME NULL AFTER `accepted_at`,
    ADD COLUMN IF NOT EXISTS `delivered_at` DATETIME NULL AFTER `picked_up_at`,
    ADD COLUMN IF NOT EXISTS `delivery_notes` VARCHAR(500) NULL AFTER `delivered_at`,
    ADD COLUMN IF NOT EXISTS `delivery_otp` VARCHAR(10) NULL AFTER `delivery_notes`,
    ADD COLUMN IF NOT EXISTS `delivery_otp_verified_at` DATETIME NULL AFTER `delivery_otp`,
    ADD COLUMN IF NOT EXISTS `cod_collected` BOOLEAN NULL DEFAULT FALSE AFTER `delivery_otp_verified_at`,
    ADD COLUMN IF NOT EXISTS `cod_collected_at` DATETIME NULL AFTER `cod_collected`;

-- 2. Create indexes for fast lookup and filtering
CREATE INDEX IF NOT EXISTS `idx_orders_picked_up_at` ON `orders` (`picked_up_at`);
CREATE INDEX IF NOT EXISTS `idx_orders_delivered_at` ON `orders` (`delivered_at`);
