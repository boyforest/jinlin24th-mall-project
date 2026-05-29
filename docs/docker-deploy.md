# Docker Compose 一键部署

这套部署用于把 Spring Boot 后端、React 管理后台、MySQL、Redis 一起跑起来。宝塔面板只需要负责安装 Docker、放行端口或配置域名反代，不再需要手动上传 jar、点前端构建、配数据库连接。

## 服务器首次部署

1. 安装 Docker 和 Docker Compose 插件。

宝塔可以在软件商店安装 Docker；SSH 里确认：

```bash
docker --version
docker compose version
```

2. 上传或拉取项目代码到服务器，例如：

```bash
cd /www/wwwroot
git clone <你的仓库地址> jinlin24th
cd jinlin24th
```

3. 准备生产环境变量：

```bash
cp .env.example .env
vim .env
```

必须修改：

- `MYSQL_ROOT_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `WX_MINIAPP_APPID` 和 `WX_MINIAPP_SECRET`，如果小程序要真实登录

4. 启动：

```bash
docker compose up -d --build
```

5. 查看状态和日志：

```bash
docker compose ps
docker compose logs -f backend
```

管理后台默认入口：

```text
http://你的服务器IP/
```

默认后台账号来自初始化 SQL：

```text
账号：admin
密码：123123
```

上线后请立刻修改默认密码。

## 宝塔 Nginx 已占用 80 端口

如果宝塔已经有站点占用了 80，把 `.env` 改成：

```env
ADMIN_WEB_HOST_PORT=8080
```

然后重启：

```bash
docker compose up -d
```

再在宝塔网站里加反向代理：

```text
目标 URL: http://127.0.0.1:8080
```

## 日常更新

```bash
cd /www/wwwroot/jinlin24th
git pull
docker compose up -d --build
docker compose logs -f backend
```

## 数据持久化

MySQL、Redis、上传图片、日志都放在 Docker volume 中。重启容器不会清数据。

查看 volume：

```bash
docker volume ls | grep jinlin
```

## 首次初始化 SQL

MySQL 容器只会在第一次创建数据目录时执行 `deploy/mysql/init` 中的脚本：

- `01-schema.sql`
- `02-product-ops.sql`
- `03-demo-seed.sql`

如果服务器已经有旧数据，不要删除 `mysql_data` volume，除非你明确要重建数据库。

## 常用命令

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f admin-web
docker compose restart backend
docker compose down
docker compose up -d --build
```

危险命令：

```bash
docker compose down -v
```

`-v` 会删除数据库 volume，等于清库。除非你很确定，否则不要用。
