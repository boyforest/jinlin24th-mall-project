-- =============================================
-- 金霖二十四养 - 数据库初始化脚本（含二级分销）
-- 创建时间：2026-04-02
-- 数据库名：jinlin24th
-- 字符集：utf8mb4
-- =============================================

-- 重要：强制客户端和服务端都用 utf8mb4，否则 Docker 初始化时会用 latin1 读文件导致乱码
SET NAMES utf8mb4;

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
  -- 已废弃：不参与任何业务。下单占用/归还库存只认 inventory 表（按仓库），
  -- 可售库存展示也改为实时汇总 inventory。保留该列仅为兼容历史数据，
  -- 请勿再读写它——它不随销售变化，照它做补货或展示会造成误导。
  `stock` int NOT NULL DEFAULT 0 COMMENT '【已废弃】库存数量，请使用 inventory.stock',
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
  `warehouse_id` bigint DEFAULT NULL COMMENT '下单时占用库存的仓库ID快照',
  `product_name` varchar(128) NOT NULL COMMENT '商品名称（快照）',
  `sku_name` varchar(128) DEFAULT NULL COMMENT '规格名称（快照）',
  `product_image` varchar(255) DEFAULT NULL COMMENT '商品图片（快照）',
  `price` decimal(10,2) NOT NULL COMMENT '单价（快照）',
  `quantity` int NOT NULL COMMENT '数量',
  `total_price` decimal(10,2) NOT NULL COMMENT '小计',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_order_no_warehouse` (`order_no`, `warehouse_id`)
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
  `refund_id` varchar(64) DEFAULT NULL COMMENT '退款订单号（微信 refund_id）',
  `refund_remark` varchar(255) DEFAULT NULL COMMENT '人工处理标记/说明，如 MANUAL_REFUND_PENDING-待人工退款',
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
  KEY `idx_refund_remark` (`refund_remark`),
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
  `must_change_pwd` tinyint NOT NULL DEFAULT 0 COMMENT '首次登录强制改密：1-是，0-否',
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

-- 26.（已移除）退款单表 refund_order
-- 该表从未被任何 entity / mapper / service 使用：退款状态实际记录在
-- payment_record（refund_status / refund_id / refund_amount / refund_time / refund_remark）。
-- 保留一张空表会让人误以为有独立的退款单流程，故从初始化脚本中删除。
-- 注意：已存在的库不会自动删表（无破坏性迁移），如确认无用可手工 DROP。

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
-- 注意：password_hash 为 BCrypt 哈希，must_change_pwd=1 强制首次登录改密。
INSERT INTO `sys_admin` (`id`, `username`, `password_hash`, `real_name`, `status`, `must_change_pwd`) VALUES
(1, 'admin', '$2a$10$KwwYK1vaw.XZiv.NXF1nbuy1ZOsrEJuNSQOGXJOZsGql.l.YjHka.', '超级管理员', 1, 1)
ON DUPLICATE KEY UPDATE
  `real_name` = VALUES(`real_name`),
  `status` = VALUES(`status`),
  `must_change_pwd` = VALUES(`must_change_pwd`);

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
