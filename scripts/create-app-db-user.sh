#!/bin/bash
# ============================================================
# 金霖二十四养 — 为「已有数据」的库创建业务数据库账号
#
# 适用场景：MySQL 数据卷已存在（04-app-user.sh 不会再次执行）。
# 新部署无需运行本脚本，init 脚本会自动创建。
#
# 用法（在项目根目录，服务器上执行）：
#   ./scripts/create-app-db-user.sh
#
# 前置：根目录 .env 中已设置 MYSQL_ROOT_PASSWORD / MYSQL_APP_PASSWORD
#
# 执行后还需要把 backend 切到该账号（docker-compose.yml 已改为读取
# MYSQL_APP_USER / MYSQL_APP_PASSWORD），重启后端即可：
#   docker compose up -d backend
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "错误：未找到 .env，请先 cp .env.example .env 并填写密码" >&2
  exit 1
fi

# shellcheck disable=SC1091
set -a; . ./.env; set +a

APP_USER="${MYSQL_APP_USER:-jinlin_app}"
APP_PASSWORD="${MYSQL_APP_PASSWORD:-}"
ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
DB_NAME="${MYSQL_DATABASE:-jinlin24th}"

if [ -z "$APP_PASSWORD" ]; then
  echo "错误：.env 中 MYSQL_APP_PASSWORD 为空" >&2
  exit 1
fi
if [ -z "$ROOT_PASSWORD" ]; then
  echo "错误：.env 中 MYSQL_ROOT_PASSWORD 为空" >&2
  exit 1
fi

echo ">>> 将为 ${DB_NAME} 创建/更新业务账号：${APP_USER}（仅 DML 权限）"

docker exec -i jinlin-mysql mysql -uroot -p"${ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED BY '${APP_PASSWORD}';
ALTER USER '${APP_USER}'@'%' IDENTIFIED BY '${APP_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${DB_NAME}\`.* TO '${APP_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo ">>> 完成。请确认 backend 使用的账号："
docker exec -i jinlin-backend sh -c 'echo "DB_USERNAME=$DB_USERNAME"' 2>/dev/null || \
  echo "    (backend 容器未运行，跳过回显)"
echo ">>> 重启后端使其生效：docker compose up -d backend"
