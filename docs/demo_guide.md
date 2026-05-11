# 金霖二十四养 Demo 说明

## 1. Demo 目标

这套后端已经覆盖三层业务：

- C 端微信小程序：商品浏览、购物车、下单、会员信息
- B 端商家后台：客户管理、订单管理、库存管理
- 后端 Spring Boot API：统一 REST 接口、JWT 鉴权、Redis 登录态

## 2. 现有模块

### C 端

- `POST /user/appUser/login`
- `GET /user/appUser/me`
- `GET /user/product/list`
- `GET /user/product/{id}`
- `GET /user/product/{id}/skus`
- `GET /user/cart/list`
- `POST /user/cart`
- `PUT /user/cart/{id}`
- `DELETE /user/cart/{id}`
- `POST /user/order/create`
- `GET /user/order/list`
- `GET /user/order/{id}`

### B 端

- `POST /admin/login`
- `GET /admin/customer/list`
- `GET /admin/customer/{id}`
- `POST /admin/customer`
- `PUT /admin/customer/{id}`
- `DELETE /admin/customer/{id}`
- `GET /admin/order/list`
- `GET /admin/order/{id}`
- `GET /admin/product/list`
- `POST /admin/product`
- `GET /admin/inventory/list`
- `PUT /admin/inventory/{id}`

### 支付与扩展

- `POST /api/payment/create`
- `GET /api/payment/query/{orderNo}`
- `POST /api/payment/notify`
- `PUT /admin/distribution/settle`
- `GET /admin/distribution/config`

## 3. Demo 演示流程

### Step 1. 初始化环境

- MySQL 导入 `sql/jinlin24th_init.sql`
- Redis 启动并可写入
- 配置 `application.yml` 中数据库、Redis、JWT、小程序参数

### Step 2. 小程序端登录

1. 前端调用微信 `wx.login`
2. 后端用 `code` 换取 `openid`
3. 首次登录创建 `app_user`
4. 返回 JWT，写入 Redis 登录态

### Step 3. 浏览商品

1. 进入商品列表
2. 查看商品详情
3. 查看 SKU 和库存

### Step 4. 加入购物车

1. 选择 SKU
2. 加入购物车
3. 修改数量或勾选状态

### Step 5. 创建订单

1. 提交收货信息
2. 后端生成订单主表和订单明细
3. 返回订单号

### Step 6. 后台管理

1. 管理员登录
2. 查看订单列表
3. 管理客户
4. 管理库存

## 4. 建议的 demo 话术

你可以把项目描述成：

> 这是一个面向养生电商场景的私域系统，前端分为微信小程序 C 端和商家管理 B 端，后端基于 Spring Boot 提供统一 REST API，覆盖商品、订单、会员、客户、库存、支付和分销。

## 5. 当前项目状态

这个仓库已经不是空壳，而是接近可演示的后端骨架：

- 已有登录态和 JWT
- 已有商品、订单、购物车、优惠券、客户、库存等模块
- 已有 MyBatis-Plus、Redis、微信小程序、微信支付依赖
- 已有基础表结构和初始化脚本

但要作为完整 demo 展示，还建议补：

- 一份稳定的 demo 种子数据
- 一份接口调用脚本
- 一份 OpenAPI/Swagger 分组说明
- 统一接口命名和鉴权规则
