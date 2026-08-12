#!/bin/bash
# ============================================================
# TechDevHub 自动备份脚本
# 定时任务：0 3 * * * /opt/TechDevHub/deploy/backup.sh >> /opt/TechDevHub/deploy/logs/backup.log 2>&1
# 备份：MySQL 全库 + Redis 快照；清理 7 天前的备份；清理悬空 Docker 镜像
# ============================================================
set -euo pipefail

PROJECT_DIR="/opt/TechDevHub"
BACKUP_DIR="$PROJECT_DIR/backups"
DATE=$(date +%Y%m%d_%H%M%S)

# 从 deploy/.env 读取环境变量（docker-compose 同款机制），若不存在则要求环境中已 export
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/.env"
  set +a
fi

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?请在 deploy/.env 中设置 MYSQL_ROOT_PASSWORD}"

mkdir -p "$BACKUP_DIR"

echo "[$(date)] Backup started"

# MySQL 全库 dump
docker exec tdh-mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --all-databases --single-transaction --routines --triggers \
  | gzip > "$BACKUP_DIR/mysql_$DATE.sql.gz"
echo "MySQL backup: $(du -sh "$BACKUP_DIR/mysql_$DATE.sql.gz" | cut -f1)"

# Redis 快照
docker exec tdh-redis redis-cli BGSAVE >/dev/null 2>&1 || true
sleep 2
if docker exec tdh-redis test -f /data/dump.rdb; then
  docker cp tdh-redis:/data/dump.rdb "$BACKUP_DIR/redis_$DATE.rdb" 2>/dev/null || true
  echo "Redis backup done"
fi

# 清理 7 天前
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +7 -delete 2>/dev/null || true
find "$BACKUP_DIR" -name "*.rdb" -mtime +7 -delete 2>/dev/null || true

# 清理悬空镜像（省磁盘）
docker image prune -f >/dev/null 2>&1 || true

echo "[$(date)] Backup done. Total: $(du -sh "$BACKUP_DIR" | cut -f1)"
