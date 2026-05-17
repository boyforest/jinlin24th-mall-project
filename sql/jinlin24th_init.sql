-- =============================================
-- 金霖二十四养 - 数据库初始化脚本（含二级分销）
-- 创建时间：2026-04-02
-- 数据库名：jinlin24th
-- 字符集：utf8mb4
-- =============================================

-- 创建并使用数据库
CREATE DATABASE IF NOT EXISTS `jinlin24th`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `jinlin24th`;

-- =============================================
-- 第一批：基础表（无依赖）
-- =============================================

-- 1. 会员等级表
CREATE TABLE IF NOT EXISTS `member_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '等级ID',
  `name` varchar(32) NOT NULL COMMENT '等级名称，如：青铜、白银、黄金',
  `min_points` int DEFAULT 0 COMMENT '升级所需最低累计积分',
  `discount` decimal(3,2) DEFAULT 1.00 COMMENT '折扣率，1.00=不打折，0.90=9折',
  `icon` varchar(255) DEFAULT NULL COMMENT '等级图标',
  `sort` int DEFAULT 0 COMMENT '排序，越小越靠前',
  `status` tinyint DEFAULT 1 COMMENT '1-启用，0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级表';

-- 2. 商品分类表
CREATE TABLE IF NOT EXISTS `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT 0 COMMENT '父分类ID，0=顶级分类',
  `name` varchar(64) NOT NULL COMMENT '分类名称',
  `icon` varchar(255) DEFAULT NULL COMMENT '分类图标',
  `image` varchar(255) DEFAULT NULL COMMENT '分类图片',
  `sort` int DEFAULT 0 COMMENT '排序',
  `status` tinyint DEFAULT 1 COMMENT '1-启用，0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 3. 仓库表
CREATE TABLE IF NOT EXISTS `warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
  `name` varchar(64) NOT NULL COMMENT '仓库名称',
  `address` varchar(255) DEFAULT NULL COMMENT '仓库地址',
  `contact` varchar(32) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` tinyint DEFAULT 1 COMMENT '1-启用，0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库表';

-- 4. 分销全局配置表
CREATE TABLE IF NOT EXISTS `distribution_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `level1_rate` int NOT NULL DEFAULT 10 COMMENT '一级佣金比例，如10表示10%',
  `level2_rate` int NOT NULL DEFAULT 5 COMMENT '二级佣金比例，如5表示5%',
  `min_withdraw` decimal(10,2) DEFAULT 100.00 COMMENT '最低提现金额',
  `settle_days` int DEFAULT 7 COMMENT '订单完成后几天可结算',
  `status` tinyint DEFAULT 1 COMMENT '1-启用分销，0-关闭分销',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销全局配置表';

-- =============================================
-- 第二批：商品表
-- =============================================

-- 5. 商品表
CREATE TABLE IF NOT EXISTS `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `name` varchar(128) NOT NULL COMMENT '商品名称',
  `subtitle` varchar(255) DEFAULT NULL COMMENT '副标题/卖点',
  `main_image` varchar(255) DEFAULT NULL COMMENT '主图URL',
  `images` text COMMENT '图片列表，逗号分隔',
  `video_url` varchar(255) DEFAULT NULL COMMENT '视频URL',
  `detail` text COMMENT '商品详情（富文本HTML）',
  `effects` varchar(500) DEFAULT NULL COMMENT '功效说明',
  `precautions` varchar(500) DEFAULT NULL COMMENT '注意事项',
  `sales` int DEFAULT 0 COMMENT '销量（虚拟销量+真实销量）',
  `status` tinyint DEFAULT 1 COMMENT '1-上架，0-下架',
  `sort` int DEFAULT 0 COMMENT '排序',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 5.1 运营活动/公告位表
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

-- 6. 商品SKU表
CREATE TABLE IF NOT EXISTS `product_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_name` varchar(128) NOT NULL COMMENT '规格名称，如：500g/盒',
  `price` decimal(10,2) NOT NULL COMMENT '售价',
  `member_price` decimal(10,2) DEFAULT NULL COMMENT '会员价',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存数量',
  `sku_image` varchar(255) DEFAULT NULL COMMENT 'SKU图片',
  `status` tinyint DEFAULT 1 COMMENT '1-启用，0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- =============================================
-- 第三批：用户/客户表
-- =============================================

-- 7. 小程序用户表（含分销上级字段）
CREATE TABLE IF NOT EXISTS `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `gender` tinyint DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
  `openid` varchar(128) NOT NULL COMMENT '微信openid',
  `unionid` varchar(128) DEFAULT NULL COMMENT '微信unionid',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `phone_bind_time` datetime DEFAULT NULL COMMENT '手机绑定时间',
  `member_level_id` bigint DEFAULT 0 COMMENT '会员等级ID，0-普通用户',
  `parent_user_id` bigint DEFAULT NULL COMMENT '上级推荐人ID（分销用）',
  `points` int DEFAULT 0 COMMENT '当前积分',
  `total_points` int DEFAULT 0 COMMENT '累计积分',
  `total_amount` decimal(10,2) DEFAULT 0.00 COMMENT '累计消费金额',
  `order_count` int DEFAULT 0 COMMENT '订单数',
  `is_distributor` tinyint NOT NULL DEFAULT 0 COMMENT '是否分销商：0-否，1-是',
  `distributor_enabled_time` datetime DEFAULT NULL COMMENT '分销资格开启时间',
  `distributor_disabled_time` datetime DEFAULT NULL COMMENT '分销资格关闭时间',
  `status` tinyint DEFAULT 1 COMMENT '1-正常，0-禁用',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_phone` (`phone`),
  KEY `idx_unionid` (`unionid`),
  KEY `idx_parent_user_id` (`parent_user_id`),
  KEY `idx_is_distributor` (`is_distributor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序用户表';

-- 8. 商家客户表
CREATE TABLE IF NOT EXISTS `biz_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `name` varchar(64) NOT NULL COMMENT '客户名称/公司名',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系人电话',
  `source` tinyint DEFAULT 1 COMMENT '来源：1-小程序注册，2-销售录入，3-转介绍',
  `level` tinyint DEFAULT 1 COMMENT '客户等级：1-普通，2-重要，3-VIP',
  `admin_id` bigint DEFAULT NULL COMMENT '绑定的销售ID',
  `total_amount` decimal(10,2) DEFAULT 0.00 COMMENT '累计消费金额',
  `order_count` int DEFAULT 0 COMMENT '累计订单数',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签，逗号分隔',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` tinyint DEFAULT 1 COMMENT '1-正常，0-禁用',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家客户表';

-- =============================================
-- 第四批：营销/购物车/跟进
-- =============================================

-- 9. 优惠券表
CREATE TABLE IF NOT EXISTS `coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
  `name` varchar(64) NOT NULL COMMENT '优惠券名称',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '类型：1-满减券，2-折扣券，3-固定金额券',
  `min_amount` decimal(10,2) DEFAULT 0.00 COMMENT '使用门槛金额',
  `discount_value` decimal(10,2) NOT NULL COMMENT '优惠值（满减=减多少，折扣=几折如90=9折）',
  `stock` int NOT NULL DEFAULT 0 COMMENT '发放总量',
  `received_count` int DEFAULT 0 COMMENT '已领取数量',
  `used_count` int DEFAULT 0 COMMENT '已使用数量',
  `start_time` datetime NOT NULL COMMENT '生效时间',
  `end_time` datetime NOT NULL COMMENT '过期时间',
  `member_level_id` bigint DEFAULT NULL COMMENT '限会员等级，NULL=不限',
  `status` tinyint DEFAULT 1 COMMENT '1-启用，0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

-- 10. 用户优惠券表
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `coupon_id` bigint NOT NULL COMMENT '优惠券ID',
  `order_id` bigint DEFAULT NULL COMMENT '使用的订单ID',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-未使用，1-已使用，2-已过期',
  `receive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `use_time` datetime DEFAULT NULL COMMENT '使用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_coupon_id` (`coupon_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- 11. 购物车表
CREATE TABLE IF NOT EXISTS `cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `checked` tinyint DEFAULT 1 COMMENT '1-选中，0-未选中',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- 12. 跟进记录表
CREATE TABLE IF NOT EXISTS `follow_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `admin_id` bigint NOT NULL COMMENT '跟进人ID',
  `content` varchar(500) NOT NULL COMMENT '跟进内容',
  `next_time` datetime DEFAULT NULL COMMENT '下次跟进时间',
  `type` tinyint DEFAULT 1 COMMENT '方式：1-电话，2-微信，3-上门，4-其他',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_admin_id` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进记录表';

-- =============================================
-- 第五批：订单+库存+分销
-- =============================================

-- 13. 订单主表
CREATE TABLE IF NOT EXISTS `order_master` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `recommender_user_id` bigint DEFAULT NULL COMMENT '下单时一级推荐官快照',
  `level2_recommender_user_id` bigint DEFAULT NULL COMMENT '下单时二级推荐官快照',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `freight_amount` decimal(10,2) DEFAULT 0.00 COMMENT '运费',
  `discount_amount` decimal(10,2) DEFAULT 0.00 COMMENT '优惠金额',
  `points_used` int DEFAULT 0 COMMENT '使用积分',
  `points_gained` int DEFAULT 0 COMMENT '获得积分',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '订单状态：0-待付款，10-待发货，20-待收货，30-已完成，40-已取消，50-退款中，60-已退款',
  `pay_type` tinyint DEFAULT NULL COMMENT '支付方式：1-微信支付',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '发货时间',
  `receive_time` datetime DEFAULT NULL COMMENT '收货时间',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货电话',
  `receiver_address` varchar(255) NOT NULL COMMENT '收货地址',
  `remark` varchar(255) DEFAULT NULL COMMENT '订单备注',
  `admin_id` bigint DEFAULT NULL COMMENT '处理的管理员ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_recommender_user_id` (`recommender_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- 14. 订单明细表
CREATE TABLE IF NOT EXISTS `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `product_name` varchar(128) NOT NULL COMMENT '商品名称（快照）',
  `sku_name` varchar(128) DEFAULT NULL COMMENT '规格名称（快照）',
  `product_image` varchar(255) DEFAULT NULL COMMENT '商品图片（快照）',
  `price` decimal(10,2) NOT NULL COMMENT '单价（快照）',
  `quantity` int NOT NULL COMMENT '数量',
  `total_price` decimal(10,2) NOT NULL COMMENT '小计',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 15. 分销佣金记录表
CREATE TABLE IF NOT EXISTS `distribution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `buyer_user_id` bigint NOT NULL COMMENT '下单人ID',
  `buyer_amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `level1_user_id` bigint DEFAULT NULL COMMENT '一级上级ID',
  `level1_rate` int NOT NULL COMMENT '一级佣金比例（快照）',
  `level1_amount` decimal(10,2) NOT NULL COMMENT '一级佣金金额',
  `level2_user_id` bigint DEFAULT NULL COMMENT '二级上级ID',
  `level2_rate` int NOT NULL COMMENT '二级佣金比例（快照）',
  `level2_amount` decimal(10,2) NOT NULL COMMENT '二级佣金金额',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待结算，1-可结算，2-已结算，3-已退回',
  `settle_time` datetime DEFAULT NULL COMMENT '结算时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_buyer_user_id` (`buyer_user_id`),
  KEY `idx_level1_user_id` (`level1_user_id`),
  KEY `idx_level2_user_id` (`level2_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销佣金记录表';

-- 16. 库存表
CREATE TABLE IF NOT EXISTS `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `stock` int NOT NULL DEFAULT 0 COMMENT '当前库存',
  `warning_stock` int DEFAULT 10 COMMENT '库存预警值',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_warehouse_sku` (`warehouse_id`, `sku_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- 17. 库存流水表
CREATE TABLE IF NOT EXISTS `inventory_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `type` tinyint NOT NULL COMMENT '类型：1-入库，2-出库，3-盘点调整',
  `quantity` int NOT NULL COMMENT '变动数量（正=入，负=出）',
  `before_stock` int NOT NULL COMMENT '变动前库存',
  `after_stock` int NOT NULL COMMENT '变动后库存',
  `order_no` varchar(32) DEFAULT NULL COMMENT '关联订单号',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表';

-- 18. 支付记录表
CREATE TABLE IF NOT EXISTS `payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `payment_no` varchar(64) NOT NULL COMMENT '支付流水号',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `pay_type` tinyint NOT NULL DEFAULT 1 COMMENT '支付类型：1-微信支付',
  `trade_type` varchar(32) DEFAULT NULL COMMENT '交易类型：JSAPI、NATIVE、APP等',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '微信支付订单号',
  `prepay_id` varchar(64) DEFAULT NULL COMMENT '微信预支付ID',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付，1-支付成功，2-支付失败，3-已退款，4-退款失败',
  `pay_time` datetime DEFAULT NULL COMMENT '支付成功时间',
  `refund_amount` decimal(10,2) DEFAULT NULL COMMENT '退款金额',
  `refund_status` tinyint NOT NULL DEFAULT 0 COMMENT '退款状态：0-未退款，1-退款中，2-退款成功，3-退款失败',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `refund_id` varchar(64) DEFAULT NULL COMMENT '退款订单号',
  `reason` varchar(255) DEFAULT NULL COMMENT '支付/退款原因',
  `notify_time` datetime DEFAULT NULL COMMENT '回调通知时间',
  `extend_info` text COMMENT '扩展信息(JSON格式)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_transaction_id` (`transaction_id`),
  KEY `idx_refund_id` (`refund_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- =============================================
-- 第六批：系统后台表（B 端账号、角色、权限、配置、审计）
-- =============================================

-- 19. 管理员表
CREATE TABLE IF NOT EXISTS `sys_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(255) NOT NULL COMMENT '密码哈希',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-启用，0-禁用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后登录IP',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统管理员表';

-- 20. 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码',
  `role_name` varchar(64) NOT NULL COMMENT '角色名称',
  `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-启用，0-禁用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 21. 权限表
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_code` varchar(128) NOT NULL COMMENT '权限编码',
  `permission_name` varchar(64) NOT NULL COMMENT '权限名称',
  `module` varchar(64) NOT NULL COMMENT '所属模块',
  `type` tinyint NOT NULL DEFAULT 2 COMMENT '类型：1-菜单，2-按钮/API',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- 22. 管理员角色关联表
CREATE TABLE IF NOT EXISTS `sys_admin_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `admin_id` bigint NOT NULL COMMENT '管理员ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_role` (`admin_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员角色关联表';

-- 23. 角色权限关联表
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 24. 操作日志表
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `admin_id` bigint DEFAULT NULL COMMENT '管理员ID',
  `username` varchar(64) DEFAULT NULL COMMENT '管理员账号快照',
  `module` varchar(64) DEFAULT NULL COMMENT '模块',
  `operation` varchar(64) DEFAULT NULL COMMENT '操作类型',
  `request_method` varchar(16) DEFAULT NULL COMMENT '请求方法',
  `request_uri` varchar(255) DEFAULT NULL COMMENT '请求路径',
  `request_params` text COMMENT '请求参数',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP地址',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-成功，0-失败',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `cost_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';

-- 25. 用户收货地址表
CREATE TABLE IF NOT EXISTS `user_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货电话',
  `province` varchar(64) DEFAULT NULL COMMENT '省份',
  `city` varchar(64) DEFAULT NULL COMMENT '城市',
  `district` varchar(64) DEFAULT NULL COMMENT '区县',
  `detail_address` varchar(255) NOT NULL COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '1-默认，0-非默认',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_default` (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

-- 26. 退款单表
CREATE TABLE IF NOT EXISTS `refund_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '退款单ID',
  `refund_no` varchar(64) NOT NULL COMMENT '退款单号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `payment_no` varchar(64) DEFAULT NULL COMMENT '支付流水号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `refund_amount` decimal(10,2) NOT NULL COMMENT '退款金额',
  `reason` varchar(255) DEFAULT NULL COMMENT '退款原因',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-申请中，1-退款中，2-退款成功，3-退款失败，4-已拒绝',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '微信支付订单号',
  `refund_id` varchar(64) DEFAULT NULL COMMENT '微信退款单号',
  `apply_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `operator_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

-- 27. 系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` varchar(128) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `description` varchar(255) DEFAULT NULL COMMENT '配置说明',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-启用，0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- =============================================
-- 初始化数据
-- =============================================

-- 会员等级
INSERT INTO `member_level` (`id`, `name`, `min_points`, `discount`, `sort`, `status`) VALUES
(1, '普通会员', 0, 1.00, 1, 1),
(2, '青铜会员', 100, 0.98, 2, 1),
(3, '白银会员', 500, 0.95, 3, 1),
(4, '黄金会员', 2000, 0.90, 4, 1),
(5, '钻石会员', 5000, 0.85, 5, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `min_points` = VALUES(`min_points`),
  `discount` = VALUES(`discount`),
  `sort` = VALUES(`sort`),
  `status` = VALUES(`status`);

-- 默认仓库
INSERT INTO `warehouse` (`id`, `name`, `address`, `contact`, `phone`, `status`) VALUES
(1, '主仓库', '默认仓库', '管理员', '13800138000', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `address` = VALUES(`address`),
  `contact` = VALUES(`contact`),
  `phone` = VALUES(`phone`),
  `status` = VALUES(`status`);

-- 商品分类
INSERT INTO `product_category` (`id`, `parent_id`, `name`, `sort`, `status`) VALUES
(1, 0, '养生茶饮', 1, 1),
(2, 0, '滋补养生', 2, 1),
(3, 0, '健康食品', 3, 1),
(4, 0, '礼盒套装', 4, 1)
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `name` = VALUES(`name`),
  `sort` = VALUES(`sort`),
  `status` = VALUES(`status`);

-- 分销配置
INSERT INTO `distribution_config` (`id`, `level1_rate`, `level2_rate`, `min_withdraw`, `settle_days`, `status`) VALUES
(1, 10, 5, 100.00, 7, 1)
ON DUPLICATE KEY UPDATE
  `level1_rate` = VALUES(`level1_rate`),
  `level2_rate` = VALUES(`level2_rate`),
  `min_withdraw` = VALUES(`min_withdraw`),
  `settle_days` = VALUES(`settle_days`),
  `status` = VALUES(`status`);

-- 默认系统角色和权限骨架
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `status`, `sort`) VALUES
(1, 'SUPER_ADMIN', '超级管理员', '拥有全部后台权限', 1, 1),
(2, 'OPERATOR', '运营', '商品、订单、客户运营', 1, 2),
(3, 'WAREHOUSE', '仓储', '库存和发货管理', 1, 3),
(4, 'SALES', '销售', '客户和跟进管理', 1, 4)
ON DUPLICATE KEY UPDATE
  `role_name` = VALUES(`role_name`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`),
  `sort` = VALUES(`sort`);

INSERT INTO `sys_permission` (`id`, `permission_code`, `permission_name`, `module`, `type`, `sort`) VALUES
(1, 'dashboard:view', '查看工作台', 'dashboard', 1, 1),
(2, 'product:manage', '商品管理', 'product', 1, 10),
(3, 'order:manage', '订单管理', 'order', 1, 20),
(4, 'customer:manage', '客户管理', 'customer', 1, 30),
(5, 'inventory:manage', '库存管理', 'inventory', 1, 40),
(6, 'coupon:manage', '优惠券管理', 'coupon', 1, 50),
(7, 'distribution:manage', '分销管理', 'distribution', 1, 60),
(8, 'system:manage', '系统管理', 'system', 1, 90)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module` = VALUES(`module`),
  `type` = VALUES(`type`),
  `sort` = VALUES(`sort`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `sys_permission` p
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- 默认后台管理员：账号 admin，初始密码 123123
-- 注意：password_hash 为 BCrypt 哈希，生产环境上线前必须登录后修改初始密码。
INSERT INTO `sys_admin` (`id`, `username`, `password_hash`, `real_name`, `status`) VALUES
(1, 'admin', '$2a$10$KwwYK1vaw.XZiv.NXF1nbuy1ZOsrEJuNSQOGXJOZsGql.l.YjHka.', '超级管理员', 1)
ON DUPLICATE KEY UPDATE
  `real_name` = VALUES(`real_name`),
  `status` = VALUES(`status`);

INSERT INTO `sys_admin_role` (`admin_id`, `role_id`) VALUES
(1, 1)
ON DUPLICATE KEY UPDATE `admin_id` = VALUES(`admin_id`);

INSERT INTO `system_config` (`config_key`, `config_value`, `description`, `status`) VALUES
('mall.name', '金霖二十四养', '商城名称', 1),
('mall.freight.default', '0.00', '默认运费', 1),
('points.rate', '1', '消费积分倍率：每消费1元获得多少积分', 1)
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`);

-- =============================================
-- 完成
-- =============================================
-- 共创建 27 张表：18 张当前代码实体表 + 9 张后台扩展表
-- 已初始化：会员等级、默认仓库、分类、分销配置、后台角色权限骨架、系统配置
