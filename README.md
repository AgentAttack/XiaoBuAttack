# XiaoBuAttack — 小布助手漏洞PoC

**安装权限声明: 无 (AndroidManifest未声明任何 &lt;uses-permission&gt;) | 包名: com.attacker.xiaobu | APK大小: ~25KB**

## 功能

点击按钮触发全自动攻击链注入 → 无障碍点击发送 → 跳转跟踪 → 窃取文本+截图。

## 安装

```bash
adb install xiaobu_attack.apk
```

安装后**必须手动开启无障碍服务一次**（设置 → 辅助功能 → 已安装的服务 → 系统服务 → 开启）。

## 使用

从桌面打开 "系统服务" App:

| 按钮                | 功能                                 |
| ------------------- | ------------------------------------ |
| 1. 打开支付宝付款码 | SEND注入 → 打开Alipay付款页         |
| 2. 查看通话记录     | SEND注入 → 打开通话记录 → 窃取号码 |
| 3. 拨打 19179193039 | SEND注入 → 直接拨号                 |
| 📊 查看窃取结果     | 查看所有攻击产出                     |

## 自定义指令

```bash
adb shell am start -n com.attacker.xiaobu/.MainActivity -e payload "你的指令"
```

## 演示流程

### 漏洞1演示 (SEND注入+无障碍)

1. 打开"系统服务"App，点按钮1/2/3
2. 观察小布被自动打开、指令注入、自动执行
3. 点📊查看窃取的文字结果和截图

### 漏洞2演示 (NER日志泄露)

1. 终端清空日志: `adb logcat -c`
2. 在小布输入框输入自然语言含敏感信息: `帮我查一下张三的电话13800138000`
3. 终端抓取结果:
   ```powershell
   # PowerShell
   adb logcat -d -s "AIUnit-Service-LOG" | Select-String "decoded result"
   ```
   ```bash
   # Bash
   adb logcat -d | grep "AIUnit-Service-LOG.*decoded result"
   ```
4. 终端输出: `NAME: 张三`, `PHONE: 13800138000`

### 漏洞3演示 (DeepLink劫持)

1. 对小布说"打开支付宝付款码"
2. 弹出选择框 → 选"系统服务"
3. 观察伪造的支付宝蓝色钓鱼页面（显示截获的UserID）

## 结果文件

| 文件位置                          | 内容             |
| --------------------------------- | ---------------- |
| `/sdcard/attacker_output.txt`   | 文字窃取结果     |
| `/sdcard/attacker_screen_*.png` | 屏幕截图         |
| `/sdcard/ner_leak_evidence.txt` | NER日志泄露证据  |
| `/sdcard/deeplink_hijack.txt`   | DeepLink截获日志 |

## 注意事项

- 每次重装APK后需重新手动开关无障碍服务
- 部分页面（支付宝付款码）有防截屏保护
- 仅供安全研究和授权测试使用

## 技术架构

```
MainActivity               ← SEND intent注入
AttackAccessibilityService ← 点击发送 + UI跟踪 + 文本提取 + 截图
DeepLinkHijackActivity     ← alipays:// DeepLink拦截
AlipayPhishActivity        ← 伪造支付宝钓鱼页面
ResultViewerActivity       ← 结果查看器
```
