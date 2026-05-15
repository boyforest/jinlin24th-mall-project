# 金霖二十四养 B2B2C 商城项目

这是一个软工学生从 0 开始学习和完善的全栈项目，目标是逐步做成一套商用上线形态的私域电商系统。
当前仓库采用 monorepo 方式管理，包含 Spring Boot 后端、React 管理端和 uni-app 微信小程序端。

## 三端概览

### 后端服务

后端位于 `src/main/java/com/jinlin24th/jinlin`，基于 Spring Boot 3.2 + MyBatis-Plus，负责提供管理端和小程序端共用的业务接口。

当前后端覆盖：

- 用户登录、JWT 登录态、Redis 会话增强。
- 商品、分类、SKU、购物车、订单、库存、优惠券等商城基础能力。
- 管理端 RBAC、管理员登录、角色权限、操作日志。
- 微信小程序登录、微信支付预下单、支付/退款回调。
- 二级推荐官/分销 MVP：邀请绑定、推荐官资格、订单佣金、退款回退、佣金导出。
- Redis 缓存、接口限流、短信验证码缓存。
- RocketMQ 示例能力：短信、订单超时取消、操作日志异步处理。

### 管理端

管理端位于 `admin-web`，基于 React 19 + Vite + React Router + Ant Design。

当前管理端覆盖：

- 管理员登录和基础后台布局。
- 商品、SKU、分类、订单、用户、客户、仓储等运营管理页面。
- 分销管理：分销配置、佣金记录、CSV 导出、手动结算。
- 用户分销资格开关：可将用户开通为推荐官/分销员。
- 仪表盘统计和常用业务入口。

### 小程序端

小程序端位于 `c-uniapp`，基于 uni-app + Vue 3 + Pinia，目标平台为微信小程序。

当前小程序端覆盖：

- 微信登录、本地 token 管理、邀请参数捕获。
- 首页新中式草本视觉、二十四节气动态展示。
- 养物归集商品列表：左侧分类、右侧商品、商品详情、加入一篮养物。
- 全局购物车状态、底部悬浮购物车栏、抽屉式购物车面板。
- 订单创建、订单列表、订单详情。
- 推荐官确认 MVP：结算页展示当前推荐官，未绑定时可填写推荐官 ID 绑定。

## 功能范围

- C 端微信小程序 API：商品浏览、购物车、在线下单、会员体系、推荐官邀请。
- B 端商家管理后台：商品、分类、订单、用户、分销、仓储、客户管理。
- 微信小程序端：新中式草本视觉、二十四节气首页、养物归集、全局购物车、推荐官确认。
- 管理端登录：数据库管理员档案 + BCrypt 密码哈希 + JWT + Redis 登录态。
- Redis 能力：登录态增强、登录限流、接口限流、商品缓存、验证码缓存。
- RocketMQ 能力：短信验证码异步发送、订单超时取消、操作日志异步记录。
- 分销能力：分销商资格开关、支付成功生成佣金、退款回退、佣金导出。
- 统一状态码和业务码：`Result`、`BizCode`、`GlobalExceptionHandler`。

## 技术栈

- Java 17
- Spring Boot 3.2
- MyBatis-Plus
- MySQL 8
- Redis
- RocketMQ
- React 19 + Vite + React Router v6
- Ant Design v5
- uni-app + Vue 3 + Pinia
- 微信小程序

## 项目结构

```text
.
├── src/main/java/com/jinlin24th/jinlin   # Spring Boot 后端源码
├── src/main/resources                    # 后端配置
├── sql                                   # 初始化 SQL 与数据库文档
├── admin-web                             # React 管理后台
├── c-uniapp                              # uni-app 微信小程序端
└── docs                                  # Demo 文档和接口示例
```

## 本地启动

1. 初始化数据库：

```bash
mysql -uroot -p < sql/jinlin24th_init.sql
```

2. 准备本地配置：

```bash
cp src/main/resources/application-dev.example.yml src/main/resources/application-dev.yml
```

然后修改 `application-dev.yml` 中的 MySQL、Redis、JWT、微信配置。这个文件已被 `.gitignore` 忽略，不要提交真实密码或密钥。

3. 启动后端：

```bash
mvn spring-boot:run
```

后端默认端口：

```text
http://localhost:7878
```

4. 启动管理后台：

```bash
cd admin-web
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

Vite 会把 `/admin/**` 请求代理到 `http://localhost:7878`。

5. 启动或构建小程序端：

```bash
cd c-uniapp
npm install
npm run dev:mp-weixin
```

微信开发者工具导入：

```text
c-uniapp/dist/dev/mp-weixin
```

需要生成构建产物时：

```bash
npm run build:mp-weixin
```

构建产物目录：

```text
c-uniapp/dist/build/mp-weixin
```

## 默认后台账号

初始化 SQL 会写入一个默认超级管理员：

```text
账号：admin
密码：123123
```

密码在数据库中使用 BCrypt 哈希存储，不保存明文。正式部署或演示前请第一时间修改默认密码。

## 环境变量

生产或共享环境建议使用环境变量覆盖敏感配置：

```bash
export DB_URL='jdbc:mysql://localhost:3306/jinlin24th?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export DB_USERNAME='root'
export DB_PASSWORD='your-password'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
export JWT_SECRET='replace-with-a-strong-secret-at-least-32-bytes'
```

管理端账号不再通过配置文件维护，而是存储在 `sys_admin`、`sys_role`、`sys_permission` 等 RBAC 表中。

## RocketMQ 说明

本地开发默认不强制启动 RocketMQ：

```yaml
app:
  mq:
    enabled: false
```

需要联调消息能力时，启用 `mq` profile 并配置 `rocketmq.name-server`。

## 开源安全说明

本仓库不应提交以下内容：

- `application-dev.yml`、`application-prod.yml`
- `.env` / `.env.*`
- 微信支付证书、私钥、商户号密钥
- 数据库真实密码
- 个人学习笔记和草稿
- 构建产物、IDE 配置、日志文件

如果真实密钥曾经进入 Git 历史，请先清理历史或轮换密钥后再公开仓库。

## 版本重点

- `v0.1.0-mvp`：三端跑通版本，后端、管理端、小程序端进入同一仓库管理。
- 新增 uni-app 微信小程序端，完成首页、商品、购物车、订单和推荐官确认 MVP。
- 小程序首页接入品牌 Logo、新中式草本视觉和二十四节气动态展示。
- 商品列表升级为“养物归集”形态，支持分类切换、商品详情和全局购物车抽屉。
- 新增推荐官确认/绑定接口，订单保存推荐官快照，支付成功后继续复用分销佣金逻辑。
- 重构管理后台为 React Router 多页面模式，并接入 Ant Design。
- 新增分销管理页面和管理端分销接口联调能力。
- 管理端登录从配置账号升级为数据库管理员档案。
- 完善 Redis 登录态、限流、缓存工具和商品缓存策略。
- 补齐 RocketMQ 核心 Demo：短信、订单超时、操作日志。
- 完善数据库初始化脚本，补齐管理员、角色、权限、分销字段。
