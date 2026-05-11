USE `jinlin24th`;

INSERT INTO `product_category` (`parent_id`, `name`, `sort`, `status`) VALUES
(0, '养生茶饮', 1, 1),
(0, '滋补养生', 2, 1);

INSERT INTO `product` (
  `category_id`, `name`, `subtitle`, `main_image`, `images`, `detail`, `sales`, `status`, `sort`, `deleted`
) VALUES (
  1, '枸杞红枣茶', '日常养护', 'https://example.com/product-1.jpg',
  'https://example.com/product-1.jpg', '<p>demo商品</p>', 128, 1, 1, 0
);

INSERT INTO `product_sku` (
  `product_id`, `sku_name`, `price`, `member_price`, `stock`, `sku_image`, `status`
) VALUES (
  1, '500g/盒', 69.90, 59.90, 200, 'https://example.com/sku-1.jpg', 1
);

INSERT INTO `app_user` (
  `nickname`, `avatar`, `gender`, `openid`, `member_level_id`, `points`, `total_points`, `total_amount`, `order_count`, `status`, `deleted`
) VALUES (
  '演示用户', 'https://example.com/avatar.png', 0, 'demo-openid', 1, 100, 100, 69.90, 1, 1, 0
);

INSERT INTO `biz_customer` (
  `name`, `contact_name`, `contact_phone`, `source`, `level`, `admin_id`, `total_amount`, `order_count`, `tags`, `remark`, `status`, `deleted`
) VALUES (
  '演示客户公司', '李四', '13900139000', 2, 2, 1, 1000.00, 3, '重点客户,私域', 'demo客户', 1, 0
);

INSERT INTO `inventory` (
  `warehouse_id`, `sku_id`, `stock`, `warning_stock`
) VALUES (
  1, 1, 200, 20
);
