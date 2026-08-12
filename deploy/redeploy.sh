#!/bin/bash
# TechDevHub 一键重发脚本（微服务版）
# 用法：bash /opt/TechDevHub/deploy/redeploy.sh
set -euo pipefail

DEPLOY_DIR="/opt/TechDevHub"
COMPOSE="docker compose -f $DEPLOY_DIR/deploy/docker-compose.micro.yml"

echo "[$(date)] === 开始重发 ==="

cd "$DEPLOY_DIR"

# 1. 拉取最新代码
echo "[1] git pull..."
git pull origin "$(git rev-parse --abbrev-ref HEAD)" || echo "  (git pull 失败，使用本地代码继续)"

# 2. 重新编译
echo "[2] mvn package..."
mvn clean package -DskipTests

# 3. 重建并启动所有服务
echo "[3] docker compose up --build..."
$COMPOSE up -d --build

# 4. 清理悬空镜像（省磁盘）
echo "[4] 清理悬空镜像..."
docker image prune -f >/dev/null 2>&1 || true

echo "[$(date)] === 重发完成 ==="
$COMPOSE ps
