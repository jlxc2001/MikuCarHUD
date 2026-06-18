package com.jlxc.mikucarhudreceiver;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.provider.Settings;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText portEdit;
    private CheckBox mirrorCheck;
    private CheckBox debugModeCheck;
    private SeekBar fontSeek;
    private SeekBar brightnessSeek;
    private TextView fontValue;
    private TextView brightnessValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setupWindow();
        buildUi();
        loadPrefs();
    }

    private void setupWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xff000000);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        scrollView.addView(root);

        TextView title = makeText("Miku HUD 接收端设置", 26, true);
        root.addView(title);

        TextView hint = makeText("当前版本先以 UDP 数据接收联调为主。修改端口后返回主页会自动重启监听。", 15, false);
        hint.setPadding(0, dp(6), 0, dp(18));
        root.addView(hint);

        TextView portLabel = makeText("监听端口", 16, true);
        root.addView(portLabel);

        portEdit = new EditText(this);
        portEdit.setTextColor(0xffffffff);
        portEdit.setHintTextColor(0xff777777);
        portEdit.setSingleLine(true);
        portEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        portEdit.setTextSize(24);
        portEdit.setHint(String.valueOf(AppPrefs.DEFAULT_PORT));
        portEdit.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.addView(portEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        mirrorCheck = new CheckBox(this);
        mirrorCheck.setText("HUD 反射模式：水平镜像整个界面");
        mirrorCheck.setTextColor(0xffffffff);
        mirrorCheck.setTextSize(18);
        mirrorCheck.setPadding(0, dp(16), 0, dp(16));
        root.addView(mirrorCheck);

        debugModeCheck = new CheckBox(this);
        debugModeCheck.setText("调试模式：显示 UDP 状态、里程、数据源、底部调试栏");
        debugModeCheck.setTextColor(0xffffffff);
        debugModeCheck.setTextSize(18);
        debugModeCheck.setPadding(0, dp(4), 0, dp(16));
        root.addView(debugModeCheck);

        TextView launcherHint = makeText("桌面模式：本 App 已注册为 Launcher 候选项。点击下面按钮后，在系统里把“车速HUD显示表”设为默认桌面。", 15, false);
        launcherHint.setTextColor(0xffcccccc);
        launcherHint.setPadding(0, dp(2), 0, dp(8));
        root.addView(launcherHint);

        Button launcherButton = new Button(this);
        launcherButton.setText("设置为默认桌面 / Launcher");
        launcherButton.setOnClickListener(v -> openHomeSettings());
        root.addView(launcherButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));


        fontValue = makeText("字体大小", 16, true);
        root.addView(fontValue);
        fontSeek = new SeekBar(this);
        fontSeek.setMax(120); // 实际范围 60-180
        fontSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fontValue.setText("字体大小：" + (progress + 60) + "%");
            }
        });
        root.addView(fontSeek);

        brightnessValue = makeText("亮度", 16, true);
        brightnessValue.setPadding(0, dp(18), 0, 0);
        root.addView(brightnessValue);
        brightnessSeek = new SeekBar(this);
        brightnessSeek.setMax(90); // 实际范围 10-100
        brightnessSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 10;
                brightnessValue.setText("亮度：" + value + "%");
                applyBrightness(value);
            }
        });
        root.addView(brightnessSeek);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.RIGHT);
        buttons.setPadding(0, dp(28), 0, 0);

        Button reset = new Button(this);
        reset.setText("恢复默认");
        reset.setOnClickListener(v -> resetDefaults());
        buttons.addView(reset);

        Button save = new Button(this);
        save.setText("保存并返回");
        save.setOnClickListener(v -> saveAndFinish());
        buttons.addView(save);

        root.addView(buttons);
        setContentView(scrollView);
    }

    private void openHomeSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            startActivity(intent);
        } catch (Exception ignored) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "当前系统没有开放默认桌面设置入口", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private TextView makeText(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(0xffffffff);
        view.setGravity(Gravity.LEFT);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private void loadPrefs() {
        int port = AppPrefs.getPort(this);
        int font = AppPrefs.getFontScale(this);
        int brightness = AppPrefs.getBrightness(this);
        boolean mirror = AppPrefs.getMirror(this);
        boolean debugMode = AppPrefs.getDebugMode(this);

        portEdit.setText(String.valueOf(port));
        mirrorCheck.setChecked(mirror);
        debugModeCheck.setChecked(debugMode);
        fontSeek.setProgress(font - 60);
        brightnessSeek.setProgress(brightness - 10);
        fontValue.setText("字体大小：" + font + "%");
        brightnessValue.setText("亮度：" + brightness + "%");
        applyBrightness(brightness);
    }

    private void resetDefaults() {
        portEdit.setText(String.valueOf(AppPrefs.DEFAULT_PORT));
        mirrorCheck.setChecked(AppPrefs.DEFAULT_MIRROR);
        debugModeCheck.setChecked(AppPrefs.DEFAULT_DEBUG_MODE);
        fontSeek.setProgress(AppPrefs.DEFAULT_FONT_SCALE - 60);
        brightnessSeek.setProgress(AppPrefs.DEFAULT_BRIGHTNESS - 10);
        Toast.makeText(this, "已恢复默认值，点保存生效", Toast.LENGTH_SHORT).show();
    }

    private void saveAndFinish() {
        int port = parsePort(portEdit.getText().toString());
        if (port < 1 || port > 65535) {
            Toast.makeText(this, "端口必须是 1-65535", Toast.LENGTH_SHORT).show();
            return;
        }
        int font = fontSeek.getProgress() + 60;
        int brightness = brightnessSeek.getProgress() + 10;
        SharedPreferences.Editor editor = AppPrefs.get(this).edit();
        editor.putInt(AppPrefs.KEY_PORT, port);
        editor.putBoolean(AppPrefs.KEY_MIRROR, mirrorCheck.isChecked());
        editor.putBoolean(AppPrefs.KEY_DEBUG_MODE, debugModeCheck.isChecked());
        editor.putInt(AppPrefs.KEY_FONT_SCALE, font);
        editor.putInt(AppPrefs.KEY_BRIGHTNESS, brightness);
        editor.apply();
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void applyBrightness(int brightnessPercent) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.1f, Math.min(1f, brightnessPercent / 100f));
        getWindow().setAttributes(lp);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
