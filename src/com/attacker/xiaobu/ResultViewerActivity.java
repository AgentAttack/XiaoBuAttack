package com.attacker.xiaobu;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;

/**
 * 演示结果查看器 — 一览三个漏洞的所有窃取数据
 */
public class ResultViewerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 50, 30, 50);

        // 标题
        TextView title = new TextView(this);
        title.setText("📊 攻击成果展示");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);
        root.addView(title);

        // === 漏洞1: SEND+无障碍窃取结果 ===
        root.addView(sectionLabel("🐚 漏洞1: SEND注入+无障碍窃取"));

        // 文字结果
        String textResult = readFile("/sdcard/attacker_output.txt");
        if (textResult != null && !textResult.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(textResult);
            tv.setTextSize(11);
            tv.setTextColor(0xFF333333);
            tv.setBackgroundColor(0xFFF0F0F0);
            tv.setPadding(15, 10, 15, 10);
            tv.setMovementMethod(new ScrollingMovementMethod());
            tv.setMaxHeight(500);
            root.addView(tv);

            // 文件大小
            TextView sz = new TextView(this);
            sz.setText("  attacker_output.txt: " +
                    new File("/sdcard/attacker_output.txt").length() + " bytes");
            sz.setTextSize(11);
            sz.setTextColor(0xFF888888);
            sz.setPadding(0, 5, 0, 15);
            root.addView(sz);
        } else {
            root.addView(emptyHint("暂无窃取数据 (点击按钮攻击后出现)"));
        }

        // 截图列表
        root.addView(sectionLabel("📸 截图"));
        boolean hasShots = false;
        File sdcard = new File("/sdcard/");
        File[] screens = sdcard.listFiles(f ->
                f.getName().startsWith("attacker_screen_") && f.getName().endsWith(".png"));
        if (screens != null && screens.length > 0) {
            hasShots = true;
            LinearLayout shotRow = new LinearLayout(this);
            shotRow.setOrientation(LinearLayout.HORIZONTAL);
            for (File f : screens) {
                addThumbnail(shotRow, f);
            }
            root.addView(shotRow);
            TextView cnt = new TextView(this);
            cnt.setText("  " + screens.length + " 张截图");
            cnt.setTextSize(11);
            cnt.setTextColor(0xFF888888);
            root.addView(cnt);
        } else {
            root.addView(emptyHint("暂无截图"));
        }

        // === 漏洞3: DeepLink劫持 ===
        root.addView(sectionLabel("🎣 漏洞3: 支付宝DeepLink劫持"));

        String hijackData = readFile("/sdcard/deeplink_hijack.txt");
        if (hijackData != null && !hijackData.isEmpty()) {
            TextView hv = new TextView(this);
            hv.setText(hijackData);
            hv.setTextSize(11);
            hv.setTextColor(0xFF333333);
            hv.setBackgroundColor(0xFFFFF3E0);
            hv.setPadding(15, 10, 15, 10);
            hv.setMovementMethod(new ScrollingMovementMethod());
            hv.setMaxHeight(400);
            root.addView(hv);
        } else {
            root.addView(emptyHint("暂无截获 (对小布说'打开支付宝付款码'并选'系统服务')"));
        }

        // === 漏洞2: NER日志泄露 ===
        root.addView(sectionLabel("📋 漏洞2: NER日志明文泄露"));

        TextView nerInfo = new TextView(this);
        nerInfo.setText(
            "触发方式: 在小布输入框自然语言输入含姓名/电话的内容\n" +
            "例: '帮我查一下张三的电话13800138000'\n\n" +
            "泄露到logcat (ADB可见):\n" +
            "  NAME: 张三\n" +
            "  PHONE: 13800138000\n\n" +
            "证据文件: /sdcard/ner_leak_evidence.txt\n" +
            "(通过 adb logcat 实时获取)"
        );
        nerInfo.setTextSize(12);
        nerInfo.setTextColor(0xFF333333);
        nerInfo.setBackgroundColor(0xFFE8F5E9);
        nerInfo.setPadding(15, 10, 15, 10);
        root.addView(nerInfo);

        // 如果有缓存的NER证据
        String nerEvidence = readFile("/sdcard/ner_leak_evidence.txt");
        if (nerEvidence != null && !nerEvidence.isEmpty()) {
            TextView nv = new TextView(this);
            nv.setText(nerEvidence);
            nv.setTextSize(10);
            nv.setBackgroundColor(0xFFF5F5F5);
            nv.setPadding(10, 5, 10, 5);
            nv.setMovementMethod(new ScrollingMovementMethod());
            nv.setMaxHeight(200);
            root.addView(nv);
        }

        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(0xFF1565C0);
        tv.setPadding(0, 20, 0, 8);
        return tv;
    }

    private TextView emptyHint(String text) {
        TextView tv = new TextView(this);
        tv.setText("  ⚠️ " + text);
        tv.setTextSize(12);
        tv.setTextColor(0xFF999999);
        tv.setPadding(0, 5, 0, 15);
        return tv;
    }

    private void addThumbnail(LinearLayout row, File file) {
        try {
            Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bm != null) {
                int h = 200;
                int w = (int) (bm.getWidth() * 200f / bm.getHeight());
                Bitmap thumb = Bitmap.createScaledBitmap(bm, w, h, true);
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(thumb);
                iv.setPadding(5, 0, 5, 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                iv.setLayoutParams(lp);
                row.addView(iv);
            }
        } catch (Exception e) {}
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
