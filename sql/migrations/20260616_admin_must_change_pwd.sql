-- =============================================
-- 金霖二十四养 - 管理员首次登录强制改密
-- 日期：2026-06-16
-- 用途：为 sys_admin 表新增 must_change_pwd 字段，默认管理员设为 1
-- =============================================

USE `jinlin24th`;

-- 1. 新增字段（仅当不存在时添加，兼容 MySQL 各版本）
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS addMustChangePwdCol()
BEGIN
  IF NOT EXISTS (
    SELECT * FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'jinlin24th'
      AND TABLE_NAME = 'sys_admin'
      AND COLUMN_NAME = 'must_change_pwd'
  ) THEN
    ALTER TABLE `sys_admin`
      ADD COLUMN `must_change_pwd` tinyint NOT NULL DEFAULT 0
        COMMENT '首次登录强制改密：1-是，0-否'
        AFTER `status`;
  END IF;
END //
DELIMITER ;
CALL addMustChangePwdCol();
DROP PROCEDURE IF EXISTS addMustChangePwdCol;

-- 2. 将已有的默认管理员 admin 标记为强制改密
UPDATE `sys_admin`
  SET `must_change_pwd` = 1
  WHERE `username` = 'admin'
    AND `id` = 1
    AND `must_change_pwd` = 0;
