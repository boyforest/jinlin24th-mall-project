-- 咖啡阶段 A：仅目录与购物车，无订单、支付、库存或分销数据。
-- MySQL 8.0.16+（CHECK 约束需启用）；在目标数据库内执行。
-- CREATE TABLE IF NOT EXISTS 可重复执行；不修复同名旧表的结构漂移。
-- DDL 隐式提交，不能整体事务回滚。先备份，再逐表核验。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_store (
  id BIGINT NOT NULL AUTO_INCREMENT,
  primary_slot BIGINT NOT NULL DEFAULT 1,
  name VARCHAR(128) NOT NULL,
  address VARCHAR(255) NOT NULL,
  opening_hours VARCHAR(255) NOT NULL,
  time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  business_status VARCHAR(16) NOT NULL,
  announcement VARCHAR(1000) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_primary_slot (primary_slot),
  CONSTRAINT ck_coffee_primary_slot CHECK (primary_slot = 1),
  CONSTRAINT ck_coffee_store_status CHECK (status IN (0,1)),
  CONSTRAINT ck_coffee_timezone CHECK (time_zone = 'Asia/Shanghai'),
  CONSTRAINT ck_coffee_business_status CHECK (business_status IN ('OPEN','CLOSED','PAUSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coffee_category (
  id BIGINT NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_category_store (id, store_id),
  KEY idx_coffee_category_sort (store_id, sort, id),
  CONSTRAINT fk_coffee_category_store FOREIGN KEY (store_id) REFERENCES coffee_store(id),
  CONSTRAINT ck_coffee_category_status CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coffee_product (
  id BIGINT NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  image_url VARCHAR(1024) NULL,
  description VARCHAR(2000) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_product_store (id, store_id),
  KEY idx_coffee_product_sort (store_id, sort, id),
  KEY idx_coffee_product_category (category_id, store_id),
  CONSTRAINT fk_coffee_product_category FOREIGN KEY (category_id, store_id) REFERENCES coffee_category(id, store_id),
  CONSTRAINT ck_coffee_product_status CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coffee_sku (
  id BIGINT NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  temperature VARCHAR(8) NOT NULL,
  sugar VARCHAR(8) NOT NULL,
  price_cent BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  available BOOLEAN NOT NULL DEFAULT TRUE,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_sku_combination (product_id, temperature, sugar),
  UNIQUE KEY uk_coffee_sku_store (id, store_id),
  KEY idx_coffee_sku_product (product_id, store_id),
  CONSTRAINT fk_coffee_sku_product FOREIGN KEY (product_id, store_id) REFERENCES coffee_product(id, store_id),
  CONSTRAINT ck_coffee_sku_price CHECK (price_cent BETWEEN 1 AND 9007199254740991),
  CONSTRAINT ck_coffee_sku_status CHECK (status IN (0,1)),
  CONSTRAINT ck_coffee_sku_available CHECK (available IN (FALSE,TRUE)),
  CONSTRAINT ck_coffee_sku_temperature CHECK (temperature IN ('HOT','ICED')),
  CONSTRAINT ck_coffee_sku_sugar CHECK (sugar IN ('REGULAR','LESS','NONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coffee_cart (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  checked TINYINT NOT NULL DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_cart_user_store_sku (user_id, store_id, sku_id),
  KEY idx_coffee_cart_sku_store (sku_id, store_id),
  CONSTRAINT fk_coffee_cart_sku FOREIGN KEY (sku_id, store_id) REFERENCES coffee_sku(id, store_id),
  CONSTRAINT ck_coffee_cart_quantity CHECK (quantity BETWEEN 1 AND 99),
  CONSTRAINT ck_coffee_cart_checked CHECK (checked IN (0,1)),
  CONSTRAINT ck_coffee_cart_version CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
