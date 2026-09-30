-- =============================================
-- 金霖二十四养 - 支付记录新增 refund_remark（人工处理标记）
-- 日期：2026-09-21
-- 用途：
--   payment_record 新增 refund_remark 列，用于标记"用户已支付但订单已失效、
--   需要人工退款"等异常场景（值：MANUAL_REFUND_PENDING）。
--   配套代码变更：修复退款回调按 refund_id 查询、支付回调金额校验、
--   回调 CAS 冲突时不再静默吞掉转而落库告警。
--
-- 执行方式（幂等，可重复执行）：
--   docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th \
--     < sql/migrations/20260921_payment_record_refund_remark.sql
--
-- 或进入容器后： source /docker-entrypoint-initdb.d/sql/migrations/20260921_payment_record_refund_remark.sql;
-- =============================================

USE `jinlin24th`;

-- 1. 新增列（仅当不存在时添加）
SET @has_refund_remark := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'payment_record'
    AND COLUMN_NAME = 'refund_remark'
);
SET @sql := IF(
  @has_refund_remark = 0,
  'ALTER TABLE `payment_record` ADD COLUMN `refund_remark` varchar(255) DEFAULT NULL COMMENT ''人工处理标记/说明，如 MANUAL_REFUND_PENDING-待人工退款'' AFTER `refund_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 新增索引（仅当不存在时添加），便于运维按该列捞取待人工处理记录
SET @has_refund_remark_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'payment_record'
    AND INDEX_NAME = 'idx_refund_remark'
);
SET @sql := IF(
  @has_refund_remark_idx = 0,
  'ALTER TABLE `payment_record` ADD KEY `idx_refund_remark` (`refund_remark`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 上线后自检：应能查出"待人工退款"的存量异常单（正常情况下为 0 行）
-- SELECT id, order_no, pay_amount, refund_status, refund_remark, update_time
--   FROM `payment_record`
--  WHERE `refund_remark` IS NOT NULL;
