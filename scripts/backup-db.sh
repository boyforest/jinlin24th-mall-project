#!/bin/bash
# ============================================================
# 金霖二十四养 — MySQL 备份脚本
#
# 用法：
#   ./scripts/backup-db.sh                 # 备份到 ./backups
#   BACKUP_DIR=/data/backups ./scripts/backup-db.sh
#
# 自动化（服务器 crontab -e，每天 03:30 执行并保留 14 天）：
#   30 3 * * * cd /opt/jinlin24th && ./scripts/backup-db.sh >> /var/log/jinlin-backup.log 2>&1
#
# 建议：备份文件再同步到另一台机器或对象存储（异地保留），
#       否则宿主机故障时备份与数据会一起丢失。
#
# 恢复演练（务必定期真实执行一次，否则备份等于没有）：
#   gunzip -c backups/jinlin24th-YYYYmmdd-HHMMSS.sql.gz \
#     | docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "错误：未找到 .env" >&2
  exit 1
fi

# shellcheck disable=SC1091
set -a; . ./.env; set +a

CONTAINER="${MYSQL_CONTAINER:-jinlin-mysql}"
DB_NAME="${MYSQL_DATABASE:-jinlin24th}"
ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

if [ -z "$ROOT_PASSWORD" ]; then
  echo "错误：.env 中 MYSQL_ROOT_PASSWORD 为空" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}-${TIMESTAMP}.sql.gz"

echo ">>> [$(date '+%F %T')] 开始备份 ${DB_NAME} → ${OUT_FILE}"

# --single-transaction：InnoDB 一致性快照，不锁表
# --routines --triggers --events：连同存储过程/触发器/事件一起备份
if ! docker exec -i "$CONTAINER" mysqldump \
      -uroot -p"${ROOT_PASSWORD}" \
      --single-transaction \
      --quick \
      --routines --triggers --events \
      --default-character-set=utf8mb4 \
      "$DB_NAME" | gzip -9 > "$OUT_FILE"; then
  echo "错误：备份失败" >&2
  rm -f "$OUT_FILE"
  exit 1
fi

# 校验备份文件非空且能被 gzip 正常解析
if [ ! -s "$OUT_FILE" ] || ! gzip -t "$OUT_FILE" 2>/dev/null; then
  echo "错误：备份文件为空或已损坏：${OUT_FILE}" >&2
  rm -f "$OUT_FILE"
  exit 1
fi

SIZE="$(du -h "$OUT_FILE" | cut -f1)"
echo ">>> 备份完成，大小：${SIZE}"

# 清理过期备份
DELETED="$(find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -type f -mtime "+${RETENTION_DAYS}" -print -delete | wc -l | tr -d ' ')"
echo ">>> 已清理 ${DELETED} 个超过 ${RETENTION_DAYS} 天的旧备份"
echo ">>> 当前备份文件数：$(find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -type f | wc -l | tr -d ' ')"
