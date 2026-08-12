#!/bin/bash
# ============================================================
# TechDevHub 服务器初始化脚本（阿里云轻量 2C2G）
# 用法：把部署包解压到 /opt/TechDevHub 后，执行：
#   sudo bash /opt/TechDevHub/deploy/init-server.sh
# 适用：Alibaba Cloud Linux 3 / CentOS 8+ / RHEL 8+
# ============================================================
set -euo pipefail

PROJECT_DIR="/opt/TechDevHub"

echo "============================================="
echo "  TechDevHub 服务器初始化"
echo "  项目目录：$PROJECT_DIR"
echo "============================================="

# ---- 1. 系统更新 ----
echo "[1/6] 更新系统..."
dnf update -y

# ---- 2. 安装基础工具 ----
echo "[2/6] 安装基础工具..."
dnf install -y git vim wget curl net-tools htop fail2ban

# ---- 3. 关闭不必要的服务 ----
echo "[3/6] 关闭不必要的服务（用阿里云安全组代替 firewalld）..."
systemctl disable --now firewalld 2>/dev/null || true
systemctl disable --now NetworkManager-wait-online 2>/dev/null || true

# ---- 4. 内核参数调优 ----
echo "[4/6] 调整内核参数..."
grep -q "vm.swappiness=10" /etc/sysctl.conf || cat >> /etc/sysctl.conf << 'EOF'
vm.swappiness=10
net.core.somaxconn=1024
net.ipv4.tcp_tw_reuse=1
EOF
sysctl -p

# ---- 5. 创建 Swap（2G 机器必备，OOM 兜底）----
echo "[5/6] 创建 2GB Swap..."
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q swapfile /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
    echo "Swap 已创建并启用"
else
    echo "Swap 已存在，跳过"
fi

# ---- 6. Docker + Compose ----
echo "[6/6] 安装 Docker..."
if ! command -v docker &>/dev/null; then
    dnf install -y dnf-utils
    dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
    dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl enable --now docker
    echo "Docker 安装完成: $(docker --version)"
else
    echo "Docker 已安装: $(docker --version)"
fi

# ---- 完成 ----
echo ""
echo "============================================="
echo "  ✅ 初始化完成！"
echo ""
echo "  接下来请执行："
echo "  1. 把 DDL 放进 $PROJECT_DIR/deploy/init-sql/（每库一个 .sql 文件）"
echo "  2. cp $PROJECT_DIR/deploy/.env.example $PROJECT_DIR/deploy/.env  并填入 DASHSCOPE_API_KEY"
echo "  3. cd $PROJECT_DIR"
echo "     docker compose -f deploy/docker-compose.micro.yml up -d --build"
echo ""
echo "  当前系统资源:"
free -h
echo ""
df -h /
