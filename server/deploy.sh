#!/bin/bash
# XiaoBu Attack - 服务器部署脚本
# 在 192.144.228.237 上运行
# 用法: bash deploy.sh

cd "$(dirname "$0")"
mkdir -p data

echo "=== 安装依赖 ==="
pip3 install flask

echo "=== 启动接收服务器 ==="
echo "服务端将在 http://0.0.0.0:8080 运行"
echo "数据面板: http://192.144.228.237:8080/"
echo ""
nohup python3 receiver.py > server.log 2>&1 &
echo "PID: $!"
echo "日志: server.log"
echo ""
echo "=== 验证 ==="
sleep 1
curl -s http://localhost:8080/health | python3 -m json.tool
