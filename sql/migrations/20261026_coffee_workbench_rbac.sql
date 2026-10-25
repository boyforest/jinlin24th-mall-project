-- 咖啡工作台与后台分权增量迁移；不更新旧账号、密码、角色绑定。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_admin_coffee_store (
  id bigint NOT NULL AUTO_INCREMENT,
  admin_id bigint NOT NULL,
  store_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_store (admin_id),
  KEY idx_binding_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_order (
  id bigint NOT NULL AUTO_INCREMENT,
  order_no varchar(40) NOT NULL,
  user_id bigint NOT NULL,
  store_id bigint NOT NULL,
  store_name varchar(128) NOT NULL,
  request_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash char(64) NOT NULL,
  pickup_code varchar(16) NOT NULL,
  pickup_date date NOT NULL,
  fulfillment_status varchar(24) NOT NULL,
  payment_status varchar(16) NOT NULL,
  refund_status varchar(16) NOT NULL,
  refund_reason varchar(255) DEFAULT NULL,
  remark varchar(255) DEFAULT NULL,
  item_count int NOT NULL,
  total_amount_cent bigint NOT NULL,
  payable_amount_cent bigint NOT NULL,
  paid_amount_cent bigint DEFAULT NULL,
  refunded_amount_cent bigint NOT NULL DEFAULT 0,
  version bigint NOT NULL DEFAULT 0,
  created_at datetime NOT NULL,
  expires_at datetime NOT NULL,
  paid_at datetime DEFAULT NULL,
  accepted_at datetime DEFAULT NULL,
  making_at datetime DEFAULT NULL,
  ready_at datetime DEFAULT NULL,
  completed_at datetime DEFAULT NULL,
  stage_entered_at datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_order_no (order_no),
  UNIQUE KEY uk_coffee_request (user_id,request_key),
  UNIQUE KEY uk_coffee_pickup (store_id,pickup_date,pickup_code),
  KEY idx_coffee_workbench (store_id,refund_status,fulfillment_status,stage_entered_at,id),
  KEY idx_coffee_history (store_id,created_at),
  KEY idx_coffee_expiry (payment_status,expires_at),
  CHECK (payable_amount_cent > 0 AND payable_amount_cent <= 9007199254740991),
  CHECK (version >= 0 AND version <= 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_order_item (
  id bigint NOT NULL AUTO_INCREMENT,
  order_id bigint NOT NULL,
  product_id bigint NOT NULL,
  sku_id bigint NOT NULL,
  product_name varchar(128) NOT NULL,
  spec_name varchar(128) NOT NULL,
  image_url varchar(512) DEFAULT NULL,
  temperature varchar(32) NOT NULL,
  sugar varchar(32) NOT NULL,
  unit_price_cent bigint NOT NULL,
  quantity int NOT NULL,
  line_total_cent bigint NOT NULL,
  PRIMARY KEY (id),
  KEY idx_coffee_item_order (order_id),
  CHECK (quantity BETWEEN 1 AND 99),
  CHECK (unit_price_cent >= 0 AND line_total_cent >= 0 AND line_total_cent <= 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_payment (
  id bigint NOT NULL AUTO_INCREMENT,
  order_id bigint NOT NULL,
  merchant_order_no varchar(40) NOT NULL,
  merchant_id varchar(64) NOT NULL,
  app_id varchar(64) NOT NULL,
  transaction_id varchar(64) DEFAULT NULL,
  amount_cent bigint NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'UNPAID',
  initiated boolean NOT NULL DEFAULT FALSE,
  close_requested boolean NOT NULL DEFAULT FALSE,
  prepay_id varchar(128) DEFAULT NULL,
  paid_at datetime DEFAULT NULL,
  next_query_at datetime NOT NULL,
  last_error varchar(128) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_payment_order (order_id),
  UNIQUE KEY uk_coffee_merchant_no (merchant_order_no),
  UNIQUE KEY uk_coffee_transaction (transaction_id),
  KEY idx_coffee_payment_recovery (status,next_query_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_refund (
  id bigint NOT NULL AUTO_INCREMENT,
  order_id bigint NOT NULL,
  refund_no varchar(40) NOT NULL,
  request_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash char(64) NOT NULL,
  reason varchar(255) NOT NULL,
  operator_id bigint DEFAULT NULL,
  amount_cent bigint NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  channel_refund_id varchar(64) DEFAULT NULL,
  submitted boolean NOT NULL DEFAULT FALSE,
  created_at datetime NOT NULL,
  succeeded_at datetime DEFAULT NULL,
  next_query_at datetime NOT NULL,
  last_error varchar(128) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_refund_order (order_id),
  UNIQUE KEY uk_coffee_refund_no (refund_no),
  UNIQUE KEY uk_coffee_refund_request (request_key),
  KEY idx_coffee_refund_recovery (status,next_query_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_order_event (
  id bigint NOT NULL AUTO_INCREMENT,
  order_id bigint NOT NULL,
  action varchar(32) NOT NULL,
  operator_id bigint DEFAULT NULL,
  operator_name varchar(64) NOT NULL,
  reason varchar(255) DEFAULT NULL,
  created_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_coffee_event_order (order_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_pickup_sequence (
  id bigint NOT NULL AUTO_INCREMENT,
  store_id bigint NOT NULL,
  pickup_date date NOT NULL,
  next_number bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_sequence (store_id,pickup_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coffee_notification_task (
  id bigint NOT NULL AUTO_INCREMENT,
  order_id bigint NOT NULL,
  ready_event_id bigint NOT NULL,
  template_type varchar(32) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  created_at datetime NOT NULL,
  updated_at datetime NOT NULL,
  reason varchar(128) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_ready_notification (order_id,ready_event_id,template_type),
  KEY idx_coffee_notification_recovery (status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_role(role_code,role_name,description,status,sort) SELECT 'SUPER_ADMIN','超级管理员','全部后台权限',1,10 WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='SUPER_ADMIN');
INSERT INTO sys_role(role_code,role_name,description,status,sort) SELECT 'GOODS_ADMIN','商品管理员','原商品业务',1,10 WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='GOODS_ADMIN');
INSERT INTO sys_role(role_code,role_name,description,status,sort) SELECT 'COFFEE_ADMIN','咖啡管理员','绑定门店的咖啡业务',1,10 WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='COFFEE_ADMIN');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'dashboard:view','dashboard:view','dashboard',2,100 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='dashboard:view');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'product:manage','product:manage','product',2,101 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='product:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'order:manage','order:manage','order',2,102 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='order:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'inventory:manage','inventory:manage','inventory',2,103 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='inventory:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'customer:manage','customer:manage','customer',2,104 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='customer:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coupon:manage','coupon:manage','coupon',2,105 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coupon:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'distribution:manage','distribution:manage','distribution',2,106 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='distribution:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'admin:accounts:manage','admin:accounts:manage','admin',2,107 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='admin:accounts:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'marketing:manage','marketing:manage','marketing',2,108 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='marketing:manage');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coffee:menu:read','coffee:menu:read','coffee',2,109 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coffee:menu:read');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coffee:menu:write','coffee:menu:write','coffee',2,110 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coffee:menu:write');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coffee:store:write','coffee:store:write','coffee',2,111 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coffee:store:write');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coffee:orders:read','coffee:orders:read','coffee',2,112 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coffee:orders:read');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coffee:orders:operate','coffee:orders:operate','coffee',2,113 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coffee:orders:operate');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coffee:orders:refund','coffee:orders:refund','coffee',2,114 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coffee:orders:refund');
INSERT INTO sys_permission(permission_code,permission_name,module,type,sort) SELECT 'coffee:ledger:read','coffee:ledger:read','coffee',2,115 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='coffee:ledger:read');
INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='SUPER_ADMIN' AND p.permission_code IN ('dashboard:view','product:manage','order:manage','inventory:manage','customer:manage','coupon:manage','distribution:manage','admin:accounts:manage','marketing:manage','coffee:menu:read','coffee:menu:write','coffee:store:write','coffee:orders:read','coffee:orders:operate','coffee:orders:refund','coffee:ledger:read') AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='GOODS_ADMIN' AND p.permission_code IN ('dashboard:view','product:manage','order:manage','inventory:manage','customer:manage','coupon:manage','distribution:manage') AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='COFFEE_ADMIN' AND p.permission_code IN ('coffee:menu:read','coffee:menu:write','coffee:store:write','coffee:orders:read','coffee:orders:operate','coffee:orders:refund','coffee:ledger:read') AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
