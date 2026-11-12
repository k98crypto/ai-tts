package com.storyvoice.overlaydemo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    static final String PREFS = "overlay_demo";
    static final String KEY_SERVER = "server_url";

    private EditText serverInput;
    private TextView permissionStatus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFFF4F6F5);
        getWindow().setNavigationBarColor(0xFFF4F6F5);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(24));
        page.setBackgroundColor(0xFFF4F6F5);

        TextView title = text("剧情配音浮窗", 23, 0xFF1B2926, true);
        page.addView(title);
        page.addView(text("演示版 · DeepSeek Android", 14, 0xFF61706B, false), margin(0, 4, 0, 18));

        TextView notice = text(
                "开启后，本 Demo 只在 DeepSeek 前台显示浮窗。其他应用只用于判断何时隐藏浮窗，不读取其文本或窗口节点。无障碍服务会扫描 DeepSeek 当前屏幕的可见文字，不读取编辑中的输入框；预览可能包含周边聊天文字。确认后才会发送给语音服务器。",
                14, 0xFF34423E, false);
        notice.setPadding(dp(14), dp(13), dp(14), dp(13));
        notice.setBackgroundColor(0xFFE7EFEC);
        page.addView(notice, margin(0, 0, 0, 20));

        page.addView(text("1  显示浮窗", 16, 0xFF1B2926, true), margin(0, 0, 0, 8));
        page.addView(button("前往悬浮窗权限", v -> openOverlaySettings()), margin(0, 0, 0, 8));
        page.addView(text("系统设置通常位于“特殊应用权限 → 显示在其他应用上层”。", 12, 0xFF61706B, false), margin(0, 0, 0, 18));

        page.addView(text("2  允许读取 DeepSeek 可见回复", 16, 0xFF1B2926, true), margin(0, 0, 0, 8));
        page.addView(button("我已了解，前往无障碍设置", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), margin(0, 0, 0, 8));
        page.addView(text("在已下载的应用/已安装的服务中开启“DeepSeek 回复配音”。此权限范围较大；可随时在此处关闭。", 12, 0xFF61706B, false), margin(0, 0, 0, 18));

        page.addView(text("3  连接局域网 TTS", 16, 0xFF1B2926, true), margin(0, 0, 0, 8));
        page.addView(text("语音服务器地址", 12, 0xFF61706B, false), margin(0, 0, 0, 5));
        serverInput = input(getPrefs().getString(KEY_SERVER, "http://192.168.10.10:8766"), "http://192.168.10.10:8766");
        page.addView(serverInput, margin(0, 0, 0, 10));
        page.addView(button("保存设置", v -> saveSettings()), margin(0, 0, 0, 8));

        permissionStatus = text("正在检查权限…", 13, 0xFF61706B, false);
        page.addView(permissionStatus, margin(0, 18, 0, 0));
        page.addView(text("国产系统后台保活", 16, 0xFF1B2926, true), margin(0, 20, 0, 7));
        page.addView(text("若浮窗被系统清理，请在系统设置中允许本应用自启动/后台运行，并将电池策略设为不限制。各厂商入口名称不同；本 Demo 不会替你修改这些设置。", 12, 0xFF61706B, false));
        page.addView(text(vendorGuidance(), 12, 0xFF61706B, false), margin(0, 7, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionStatus != null) {
            boolean overlay = Settings.canDrawOverlays(this);
            boolean accessibility = ReplyAccessibilityService.isEnabled(this);
            permissionStatus.setText("权限状态：悬浮窗 " + (overlay ? "已开启" : "未开启")
                    + " · 无障碍读取 " + (accessibility ? "已开启" : "未开启"));
        }
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void saveSettings() {
        String server = serverInput.getText().toString().trim().replaceAll("/+$", "");
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            serverInput.setError("地址需要以 http:// 或 https:// 开头");
            return;
        }
        getPrefs().edit().putString(KEY_SERVER, server).apply();
        permissionStatus.setText("设置已保存。浮窗只会在 DeepSeek 前台出现。");
    }

    private android.content.SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private EditText input(String value, String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setHint(hint);
        edit.setTextSize(14);
        edit.setPadding(dp(11), dp(8), dp(11), dp(8));
        edit.setBackgroundColor(0xFFFFFFFF);
        return edit;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String vendorGuidance() {
        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
            return "小米/Redmi：应用信息 → 自启动（允许）；电池策略设为“无限制”。不同 HyperOS 版本入口可能不同。";
        }
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            return "华为/荣耀：应用启动管理 → 手动管理，按需允许自启动、关联启动和后台活动；电池优化选择不限制。";
        }
        if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
            return "OPPO/一加/realme：应用信息 → 自启动/后台运行设为允许；电池使用策略设为不限制。";
        }
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return "vivo/iQOO：应用管理 → 后台高耗电或自启动设为允许；必要时将电池策略设为不限制。";
        }
        return "其他 Android：应用信息 → 电池/后台活动，允许后台运行。若浮窗无需该设置即可工作，不必开启。";
    }
}
