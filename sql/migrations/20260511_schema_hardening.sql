-- =============================================
-- 金霖二十四养 - 现有库结构增强脚本
-- 日期：2026-05-11
-- 用途：在不删库、不清数据的前提下，把旧版库补齐到当前初始化脚本结构
-- =============================================

USE `jinlin24th`;

-- 支付记录：商用支付回调需要按订单号幂等处理，新库已是唯一索引；旧库可能仍是普通索引。
SET @idx_order_no_non_unique := (
  SELECT COALESCE(MAX(NON_UNIQUE), 1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'payment_record'
    AND INDEX_NAME = 'idx_order_no'
);

SET @sql := IF(
  @idx_order_no_non_unique = 1,
  'ALTER TABLE `payment_record` DROP INDEX `idx_order_no`, ADD UNIQUE KEY `uk_order_no` (`order_no`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 购物车：同一用户同一 SKU 应只有一条记录，重复加购由业务累加数量。
SET @has_cart_unique := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'cart'
    AND INDEX_NAME = 'uk_user_sku'
);

SET @sql := IF(
  @has_cart_unique = 0,
  'ALTER TABLE `cart` ADD UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`), ADD KEY `idx_sku_id` (`sku_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 用户优惠券：同一用户同一优惠券默认只领取一次。
SET @has_user_coupon_unique := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_coupon'
    AND INDEX_NAME = 'uk_user_coupon'
);

SET @sql := IF(
  @has_user_coupon_unique = 0,
  'ALTER TABLE `user_coupon` ADD UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`), ADD KEY `idx_order_id` (`order_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
