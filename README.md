# 金霖二十四养 B2B2C Spring Boot 电商后端

🫡这是一个软工大学生第一次从头开始学习的全栈项目，有好多破烂的地方....会继续不断的学习和完善项目的！🫶
面向客户，管理者的完整私域电商系统，包含：

- C 端微信小程序 API：商品浏览、下单、会员体系（未完成）
- B 端商家管理后台 API：客户、订单、商品、仓储管理
- Spring Boot RESTful 后端
- React + Vite 管理后台 （demo）
- Redis 登录态增强、商品缓存、登录限流
- RocketMQ 订单消息  开关

## 技术栈

- Java 17
- Spring Boot 3.2
- MyBatis-Plus
- MySQL 8
- Redis
- RocketMQ
- React 19 + Vite

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

## 环境变量

生产或共享环境建议使用环境变量覆盖敏感配置：

```bash
export DB_URL='jdbc:mysql://localhost:3306/jinlin24th?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export DB_USERNAME='root'
export DB_PASSWORD='your-password'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
export JWT_SECRET='replace-with-a-strong-secret-at-least-32-bytes'
export ADMIN_USERNAME='admin'
export ADMIN_PASSWORD='change-me'
```

## 开源安全说明

本仓库不应提交以下内容：

- `application-dev.yml`、`application-prod.yml`
- `.env` / `.env.*`
- 微信支付证书、私钥、商户号密钥
- 数据库真实密码
- 构建产物、IDE 配置、日志文件

如果真实密钥曾经进入 Git 历史，请先清理历史或轮换密钥后再公开仓库。
