-- =============================================
-- 金霖二十四养 - 商品运营能力增强
-- 日期：2026-05-17
-- 用途：补商品功效/注意事项、首页活动/公告位
-- =============================================

USE `jinlin24th`;

SET @has_effects := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'effects'
);

SET @sql := IF(
  @has_effects = 0,
  'ALTER TABLE `product` ADD COLUMN `effects` varchar(500) DEFAULT NULL COMMENT ''功效说明'' AFTER `detail`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_precautions := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'precautions'
);

SET @sql := IF(
  @has_precautions = 0,
  'ALTER TABLE `product` ADD COLUMN `precautions` varchar(500) DEFAULT NULL COMMENT ''注意事项'' AFTER `effects`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `marketing_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '活动ID',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `subtitle` varchar(255) DEFAULT NULL COMMENT '副标题',
  `image_url` varchar(255) DEFAULT NULL COMMENT '活动图',
  `content` varchar(500) DEFAULT NULL COMMENT '活动内容',
  `position` varchar(64) NOT NULL DEFAULT 'home_banner' COMMENT '投放位置：home_banner/home_notice',
  `link_type` varchar(32) DEFAULT 'none' COMMENT '跳转类型：none/product/category/page',
  `link_value` varchar(255) DEFAULT NULL COMMENT '跳转值',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `sort` int DEFAULT 0 COMMENT '排序',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_position_status` (`position`, `status`),
  KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营活动/公告位表';

INSERT INTO `marketing_activity`
  (`title`, `subtitle`, `image_url`, `content`, `position`, `link_type`, `link_value`, `status`, `sort`)
SELECT '顺时而养，草本新生', '节气养物上新', NULL, '精选节气滋补好物，慢养日常元气。', 'home_notice', 'none', NULL, 1, 10
WHERE NOT EXISTS (
  SELECT 1 FROM `marketing_activity`
  WHERE `position` = 'home_notice'
    AND `title` = '顺时而养，草本新生'
);
