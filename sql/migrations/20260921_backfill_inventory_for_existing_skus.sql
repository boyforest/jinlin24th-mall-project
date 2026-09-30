-- =============================================
-- 金霖二十四养 - 为存量 SKU 回填库存记录（单仓部署）
-- 日期：2026-09-21
-- 用途：
--   修复"新建 SKU 无法设置库存"的问题。此前全项目**没有任何创建 inventory 行的入口**
--   （后端无 POST 接口、管理端无「新增」按钮），演示环境之所以正常，是因为
--   sql/demo_seed.sql 恰好插入了配套的库存行；正式运营路径下新建商品永远无货可卖。
--
--   配套代码变更：新建 SKU 时自动补建库存行（InventoryMaintenanceService）。
--   但那只覆盖"今后的新 SKU"，**本脚本负责存量 SKU**：为所有尚无库存记录的 SKU
--   在单仓部署下补建一行 stock=0，运营随后到「库存」页填写数量即可。
--
-- 触发条件（重要）：
--   仅当**启用仓库恰好 1 个**时才执行回填。多仓部署下无法替运营决定放哪个仓，
--   脚本会什么都不做（此时需人工在「库存」页指定仓库）。
--
-- 执行方式（幂等，可重复执行）：
--   docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th \
--     < sql/migrations/20260921_backfill_inventory_for_existing_skus.sql
--
-- 或进入容器后：
--   source /docker-entrypoint-initdb.d/sql/migrations/20260921_backfill_inventory_for_existing_skus.sql;
-- =============================================

USE `jinlin24th`;

-- 1. 执行前自检：有多少 SKU 缺少库存记录（可单独执行查看）
SELECT COUNT(*) AS `缺少库存记录的 SKU 数`
FROM `product_sku` s
WHERE NOT EXISTS (SELECT 1 FROM `inventory` i WHERE i.sku_id = s.id);

-- 2. 回填（幂等：NOT EXISTS 保证重复执行不会重复插入）
--    条件：启用仓库恰好 1 个 → 视为单仓部署，全部补到该仓
--    新增行 stock = 0，等运营在「库存」页填写真实数量；
--    不修改任何已存在的库存行（NOT EXISTS 已排除）。
INSERT INTO `inventory` (`warehouse_id`, `sku_id`, `stock`, `warning_stock`, `update_time`)
SELECT w.id, s.id, 0, 10, NOW()
FROM `product_sku` s
JOIN `warehouse` w ON w.status = 1
WHERE (SELECT COUNT(*) FROM `warehouse` WHERE status = 1) = 1
  AND NOT EXISTS (SELECT 1 FROM `inventory` i WHERE i.sku_id = s.id);

-- 3. 执行后自检：应返回 0；若仍 > 0 说明不是单仓部署（需人工指定仓库）
SELECT COUNT(*) AS `回填后仍缺少库存记录的 SKU 数`
FROM `product_sku` s
WHERE NOT EXISTS (SELECT 1 FROM `inventory` i WHERE i.sku_id = s.id);
