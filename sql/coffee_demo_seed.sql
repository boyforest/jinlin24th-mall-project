-- 仅本地/测试演示数据，禁止在生产执行；部署初始化目录不引用此文件。
-- 需先执行咖啡迁移。已有真实门店时不写入任何演示数据。
-- 无用户购物车、咖啡订单、支付、库存或佣金演示数据。
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO coffee_store(primary_slot,name,address,opening_hours,time_zone,business_status,status)
SELECT 1,'咖啡演示店（仅本地）','本地测试地址','09:00–18:00','Asia/Shanghai','OPEN',1
WHERE NOT EXISTS (SELECT 1 FROM coffee_store);
SET @coffee_demo_store_id = (SELECT id FROM coffee_store WHERE primary_slot=1 AND name='咖啡演示店（仅本地）');
INSERT INTO coffee_category(store_id,name,sort,status)
SELECT @coffee_demo_store_id,'经典咖啡',10,1 WHERE @coffee_demo_store_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM coffee_category WHERE store_id=@coffee_demo_store_id);
SET @coffee_demo_category_id = (SELECT id FROM coffee_category WHERE store_id=@coffee_demo_store_id AND name='经典咖啡' ORDER BY id LIMIT 1);
INSERT INTO coffee_product(store_id,category_id,name,image_url,description,sort,status)
SELECT @coffee_demo_store_id,@coffee_demo_category_id,'演示拿铁',NULL,'仅用于本地联调',10,1
WHERE @coffee_demo_category_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM coffee_product WHERE store_id=@coffee_demo_store_id);
SET @coffee_demo_product_id = (SELECT id FROM coffee_product WHERE store_id=@coffee_demo_store_id AND name='演示拿铁' ORDER BY id LIMIT 1);
INSERT INTO coffee_sku(store_id,product_id,name,temperature,sugar,price_cent,status,available)
SELECT @coffee_demo_store_id,@coffee_demo_product_id,'热／标准糖','HOT','REGULAR',1800,1,TRUE
WHERE @coffee_demo_product_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM coffee_sku WHERE product_id=@coffee_demo_product_id AND temperature='HOT' AND sugar='REGULAR');
INSERT INTO coffee_sku(store_id,product_id,name,temperature,sugar,price_cent,status,available)
SELECT @coffee_demo_store_id,@coffee_demo_product_id,'冰／无糖（售罄示例）','ICED','NONE',1800,1,FALSE
WHERE @coffee_demo_product_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM coffee_sku WHERE product_id=@coffee_demo_product_id AND temperature='ICED' AND sugar='NONE');
COMMIT;
