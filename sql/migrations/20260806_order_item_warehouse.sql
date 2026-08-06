-- =============================================
-- 金霖二十四养 - 订单明细增加库存仓库快照
-- 日期：2026-08-06
-- 用途：下单时把实际扣减库存的仓库ID快照到 order_item.warehouse_id，
--       取消/超时取消时按原仓库恢复库存，避免多仓库存错乱。
-- 说明：脚本幂等，可重复执行；全新库由 jinlin24th_init.sql 直接包含该字段。
-- =============================================

USE `jinlin24th`;

-- 1. 增加 warehouse_id 列（列不存在时，连同索引一起新增）
SET @has_warehouse_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'order_item'
    AND COLUMN_NAME = 'warehouse_id'
);

SET @sql := IF(
  @has_warehouse_id = 0,
  'ALTER TABLE `order_item`
     ADD COLUMN `warehouse_id` bigint DEFAULT NULL
       COMMENT ''下单时占用库存的仓库ID快照'' AFTER `sku_id`,
     ADD KEY `idx_order_no_warehouse` (`order_no`, `warehouse_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 确保索引存在（列已存在但索引缺失时补齐）
SET @has_warehouse_idx := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'order_item'
    AND INDEX_NAME = 'idx_order_no_warehouse'
);

SET @sql := IF(
  @has_warehouse_idx = 0,
  'ALTER TABLE `order_item` ADD KEY `idx_order_no_warehouse` (`order_no`, `warehouse_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
