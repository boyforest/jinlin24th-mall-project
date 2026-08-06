# 金霖二十四养 — 品牌私域商城系统

## 项目概述

金霖二十四养是一个面向草本养生品牌的全栈私域电商系统，覆盖微信小程序（C 端消费者）、商家管理后台（B 端运营）和 Spring Boot 后端服务，采用 Docker Compose 一键部署。

系统从数据库设计到三端联动均从零构建，目前已完成 MVP 上线形态并通过了上线前安全审查，正在等待 ICP 备案后正式发布微信小程序。

## 技术栈

### 后端
| 技术 | 说明 |
|------|------|
| Java 17 + Spring Boot 3.2 | RESTful API 核心框架 |
| MyBatis-Plus | ORM + Lambda 查询 + 分页 |
| MySQL 8.4 | 关系型数据库，27 张业务表 |
| Redis 7.4 | 登录态增强、接口限流、商品缓存、验证码 TTL |
| Spring Security Crypto (BCrypt) | 管理员密码哈希 |
| jjwt (io.jsonwebtoken) | JWT 签发与校验，支持 scope 权限分级 |
| RocketMQ (可选) | 异步短信、订单超时取消、操作日志 |
| Docker Compose | 五容器编排（MySQL、Redis、Backend、Admin、Nginx） |

### 管理后台
| 技术 | 说明 |
|------|------|
| React 19 + Vite | SPA 构建 |
| React Router v6 | 多页面路由 + 登录拦截 |
| Ant Design 5 | 企业级 UI 组件库 |
| Axios | HTTP 请求 + 拦截器（自动 Bearer Token） |
| Nginx (Alpine) | 容器内反向代理 + gzip |

### 微信小程序
| 技术 | 说明 |
|------|------|
| uni-app + Vue 3 (Composition API) | 跨端框架 |
| TypeScript | 类型安全 |
| Pinia | 全局状态管理（购物车、登录态） |
| Vite | 构建工具，生产构建强制 HTTPS 域名检查 |

## 技术难点

### 1. 首次登录强制改密的安全机制设计

**问题**：种子数据默认管理员 `admin/123123` 写入数据库时，如何强制管理员首次登录必须修改密码，同时保证改密接口本身不被滥用？

**方案**：设计了 JWT scope 权限分级机制：
- 登录时检测 `must_change_pwd` 字段，为 `1` 则签发 `scope=password_change` 的受限 token
- 拦截器校验 token scope：受限 token 仅放行 `PUT /admin/password`，其余 `/admin/*` 返回 403
- 改密成功后签发 `scope=full` 的新 token，旧 token 的 jti 被 Redis 覆盖立即失效
- 操作日志拦截器排除密码接口，防止新/旧密码明文泄露到日志表

### 2. C 端订单并发的乐观锁控制

**问题**：在高并发抢购场景下，库存扣减和订单创建存在超卖风险。

**方案**：
- 库存扣减使用 `UPDATE ... WHERE stock >= ?` 条件语句，利用 MySQL 行锁实现 CAS（Compare-And-Swap）
- 订单创建对库存记录做乐观锁版本号校验，更新失败则事务回滚
- 微信支付回调接口处理幂等性：按 `out_trade_no` 唯一索引防重复回调

### 3. 微信支付签名验证与回调防伪造

**问题**：支付回调是攻击者最常伪造的接口，必须严格验证来源。

**方案**：
- 使用微信 API v3 的证书/签名验证机制
- 回调接口先验证签名（WechatPay-* 头部 + 商户证书），再按订单号幂等处理
- 支付成功后才更新订单状态、记录佣金、发送通知

### 4. 三端 Docker 化部署与反向代理架构

**问题**：项目包含后端 Java 服务、React 管理后台静态资源、MySQL、Redis 四个进程，需要统一编排。

**方案**：
- 编写 `docker-compose.yml` 实现五容器（含 nginx）一键部署
- Nginx 容器作为统一入口监听 80/443，反向代理分发到后端和管理后台
- 支持 Let's Encrypt SSL 自动证书（certbot ACME 挑战）
- 环境变量通过 `.env` 文件注入，敏感配置不入仓库

### 5. uni-app 微信小程序 HTTPS 强制约束

**问题**：微信小程序要求所有请求域名必须为 HTTPS 且已备案，本地开发用 localhost 即可，但生产构建必须切换。

**方案**：
- Vite 构建配置区分 `mode`：`dev` 允许 localhost，`production` 强制 `VITE_API_BASE_URL` 为 `https://` 前缀
- 构建时未设置或使用 http 则直接中断构建，避免上线后才发现域名问题

### 6. MySQL 中文编码乱码的根治

**问题**：Docker MySQL 容器默认字符集导致中文数据乱码，且 `character-set-client-handshake` 在 8.4 版本被移除。

**方案**：
- 在建库/建表语句中显式指定 `utf8mb4` 字符集和 `utf8mb4_unicode_ci` 排序规则
- JDBC 连接字符串追加 `useUnicode=true&characterEncoding=utf-8`
- 兼容处理：移除已废弃的 MySQL 8.4 参数

## 技术亮点

### 安全
- **JWT scope 权限分级**：`full` / `password_change` 两级 token，拦截器统一校验
- **BCrypt 自适应哈希**：随机盐，不可逆，防彩虹表
- **Redis 登录态增强**：jti 机制支持主动踢下线 / 单点登录 / 登出立即失效
- **CORS 白名单**：生产环境拒绝 `*` 通配符，`allowCredentials=true` 必须指定具体域名
- **操作日志脱敏**：短信验证码、密码修改等接口不记录请求参数

### 架构
- **三端 monorepo**：后端 + 管理后台 + 小程序在同一仓库协作
- **RBAC 权限模型**：管理员 → 角色 → 权限，支持菜单和按钮级控制
- **事件驱动雏形**：`OrderCreatedEvent` + Spring Event/RocketMQ 双通道，预留横向扩展能力
- **缓存分层**：商品详情 → 30min Redis 缓存 + null 值防穿透

### 运维
- **Docker Compose 五容器编排**：MySQL + Redis + Backend + Admin + Nginx
- **生产配置模板**：`application-prod.example.yml` 含 HikariCP 连接池调优、线程池、日志轮转
- **数据库迁移脚本**：存储过程实现 ALTER TABLE 幂等，兼容存量数据库平滑升级
- **Nginx 安全头**：已预留 HSTS、CSP、X-Frame-Options 配置

## 统计数据

| 指标 | 数值 |
|------|------|
| 数据表 | 27 张 |
| Java 源文件 | 210 个 |
| uni-app 页面 | 8 个 |
| React 管理页面 | 14 个 |
| API 端点 | 120+ |
| 单元测试 | 8 个（MQ / JWT / 消费者） |

## 版本演进

| 版本 | 里程碑 |
|------|--------|
| v0.1.0 | 三端跑通 MVP，monorepo 初始化 |
| v0.2.0 | 微信支付闭环 + 分销推荐官 |
| v0.3.0 | 管理后台多页面重构 + Ant Design |
| v0.4.0 | 品牌视觉升级 + 安全加固 + 强制改密 + Docker 生产部署 |
| v0.5.0 (计划) | SSL 证书 + 小程序正式上线 |
