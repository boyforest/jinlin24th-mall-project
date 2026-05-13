# 金霖二十四养 B2B2C 商城项目

🫡这是一个软工学生从 0 开始学习和完善的全栈项目，目标是逐步做成一套商用上线形态的私域电商系统。
当前仓库包含 Spring Boot 后端和 React 管理后台。

## 功能范围

- C 端微信小程序 API：商品浏览、购物车、在线下单、会员体系、分销邀请。
- B 端商家管理后台：商品、分类、订单、用户、分销、仓储、客户管理。
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

## 项目结构

```text
.
├── src/main/java/com/jinlin24th/jinlin   # Spring Boot 后端源码
├── src/main/resources                    # 后端配置
├── sql                                   # 初始化 SQL 与数据库文档
├── admin-web                             # React 管理后台
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

- 重构管理后台为 React Router 多页面模式，并接入 Ant Design。
- 新增分销管理页面和管理端分销接口联调能力。
- 管理端登录从配置账号升级为数据库管理员档案。
- 完善 Redis 登录态、限流、缓存工具和商品缓存策略。
- 补齐 RocketMQ 核心 Demo：短信、订单超时、操作日志。
- 完善数据库初始化脚本，补齐管理员、角色、权限、分销字段。
