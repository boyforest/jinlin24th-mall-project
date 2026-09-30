#!/bin/bash
# ============================================================
# 金霖二十四养 — 创建业务数据库账号（最小权限）
#
# 由 MySQL 官方镜像在「数据卷为空、首次初始化」时自动执行。
# 已有数据的库不会执行本脚本，需手工执行 scripts/create-app-db-user.sh。
#
# 目的：backend 不再使用 root 连接数据库。
#   root 拥有 FILE / PROCESS / GRANT / DROP 等实例级权限，
#   一旦应用被 SQL 注入或失陷，攻击者可直接读写宿主机文件、建账号、删库。
#   业务账号只授予 jinlin24th 库的 DML 权限即可满足应用需求。
# ============================================================
set -e

APP_USER="${MYSQL_APP_USER:-jinlin_app}"
APP_PASSWORD="${MYSQL_APP_PASSWORD:-}"
DB_NAME="${MYSQL_DATABASE:-jinlin24th}"

if [ -z "$APP_PASSWORD" ]; then
  echo "[04-app-user] 未设置 MYSQL_APP_PASSWORD，跳过业务账号创建（backend 将无法使用非 root 账号）" >&2
  exit 0
fi

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED BY '${APP_PASSWORD}';
ALTER USER '${APP_USER}'@'%' IDENTIFIED BY '${APP_PASSWORD}';

-- 只给业务库的增删改查权限，不给 DDL / GRANT / FILE
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${DB_NAME}\`.* TO '${APP_USER}'@'%';

FLUSH PRIVILEGES;
SQL

echo "[04-app-user] 已创建业务账号 ${APP_USER}（仅 ${DB_NAME} 库 DML 权限）"
