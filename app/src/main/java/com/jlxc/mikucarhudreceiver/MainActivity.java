package com.jlxc.mikucarhudreceiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

public class MainActivity extends Activity implements HudUdpReceiver.Listener {
    public static final String ACTION_DEBUG_DATA = "com.jlxc.mikucarhudreceiver.DEBUG_DATA";

    private AudiHudView hudView;
    private HudUdpReceiver receiver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable invalidateTicker = new Runnable() {
        @Override
        public void run() {
            if (hudView != null) {
                hudView.invalidate();
            }
            mainHandler.postDelayed(this, 250);
        }
    };

    private WifiManager.MulticastLock multicastLock;
    private int listenPort = AppPrefs.DEFAULT_PORT;
    private int packetCount = 0;
    private int invalidPacketCount = 0;
    private long lastPacketAtMs = 0L;
    private long downAtMs = 0L;
    private VehicleData lastVehicleData;
    private boolean debugReceiverRegistered = false;

    private final BroadcastReceiver adbDebugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            applyAdbDebugIntent(intent, "ADB broadcast");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setupFullscreen();
        hudView = new AudiHudView(this);
        hudView.setOnLongClickListener(v -> {
            openSettings();
            return true;
        });
        hudView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downAtMs = System.currentTimeMillis();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                long pressed = System.currentTimeMillis() - downAtMs;
                if (pressed < 300) {
                    Toast.makeText(this, "长按打开设置", Toast.LENGTH_SHORT).show();
                }
            }
            return false;
        });
        setContentView(hudView);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDebugIntentIfNeeded(intent, "ADB start");
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupFullscreen();
        loadSettings();
        acquireMulticastLock();
        registerAdbDebugReceiver();
        startReceiver();
        handleDebugIntentIfNeeded(getIntent(), "ADB start");
        mainHandler.removeCallbacks(invalidateTicker);
        mainHandler.post(invalidateTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(invalidateTicker);
        unregisterAdbDebugReceiver();
        stopReceiver();
        releaseMulticastLock();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterAdbDebugReceiver();
        stopReceiver();
        releaseMulticastLock();
    }

    private void setupFullscreen() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void loadSettings() {
        listenPort = AppPrefs.getPort(this);
        boolean mirror = AppPrefs.getMirror(this);
        int fontScale = AppPrefs.getFontScale(this);
        int brightness = AppPrefs.getBrightness(this);
        boolean debugMode = AppPrefs.getDebugMode(this);

        if (hudView != null) {
            hudView.setScaleX(mirror ? -1f : 1f);
            hudView.setFontScale(fontScale);
            hudView.setListenPort(listenPort);
            hudView.setDebugMode(debugMode);
        }
        applyBrightness(brightness);
    }

    private void applyBrightness(int brightnessPercent) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.1f, Math.min(1f, brightnessPercent / 100f));
        getWindow().setAttributes(lp);
    }

    private void startReceiver() {
        stopReceiver();
        packetCount = 0;
        invalidPacketCount = 0;
        if (hudView != null) {
            hudView.setInvalidPacketCount(invalidPacketCount);
            hudView.setStatus("准备监听 UDP " + listenPort);
        }
        receiver = new HudUdpReceiver(listenPort, this);
        receiver.start();
    }

    private void stopReceiver() {
        if (receiver != null) {
            receiver.stop();
            receiver = null;
        }
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager != null && multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("MikuCarHUDReceiver");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignored) {
        }
        multicastLock = null;
    }

    private void registerAdbDebugReceiver() {
        if (debugReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(ACTION_DEBUG_DATA);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(adbDebugReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(adbDebugReceiver, filter);
            }
            debugReceiverRegistered = true;
        } catch (Exception e) {
            if (hudView != null) {
                hudView.setStatus("ADB 调试接收器注册失败: " + e.getMessage());
            }
        }
    }

    private void unregisterAdbDebugReceiver() {
        if (!debugReceiverRegistered) return;
        try {
            unregisterReceiver(adbDebugReceiver);
        } catch (Exception ignored) {
        }
        debugReceiverRegistered = false;
    }

    private void handleDebugIntentIfNeeded(Intent intent, String senderLabel) {
        if (intent != null && ACTION_DEBUG_DATA.equals(intent.getAction())) {
            applyAdbDebugIntent(intent, senderLabel);
            setIntent(new Intent(this, MainActivity.class));
        }
    }

    private void applyAdbDebugIntent(Intent intent, String senderLabel) {
        try {
            VehicleData debugData;
            String json = intent.getStringExtra("json");
            if (json != null && !json.trim().isEmpty()) {
                debugData = VehicleData.fromJson(new JSONObject(json));
            } else {
                debugData = copyVehicleData(lastVehicleData);
            }

            // ADB 模拟数据默认仍按正式协议显示，只把 dataSource/debugText 标记为调试来源。
            debugData.protocol = "MikuCarHUD";
            debugData.version = 1;
            debugData.source = "MikuCarLauncher";
            debugData.valid = true;
            debugData.timestampElapsedMs = SystemClock.elapsedRealtime();
            debugData.seq = readLongExtra(intent, "seq", debugData.seq + 1L);

            applyIntExtra(intent, "speedKmh", value -> debugData.speedKmh = value);
            applyIntExtra(intent, "rpm", value -> debugData.rpm = value);
            applyIntExtra(intent, "rangeKm", value -> debugData.rangeKm = value);
            applyIntExtra(intent, "fuelLevel", value -> debugData.fuelLevel = value);
            debugData.totalMileageKm = readLongExtra(intent, "totalMileageKm", debugData.totalMileageKm);

            applyBooleanExtra(intent, "driverSeatbelt", value -> debugData.driverSeatbelt = value);
            applyBooleanExtra(intent, "passengerSeatbelt", value -> debugData.passengerSeatbelt = value);

            applyBooleanExtra(intent, "leftTurn", value -> debugData.leftTurn = value);
            applyBooleanExtra(intent, "rightTurn", value -> debugData.rightTurn = value);
            applyBooleanExtra(intent, "highBeam", value -> debugData.highBeam = value);
            applyBooleanExtra(intent, "hazard", value -> debugData.hazard = value);

            applyBooleanExtra(intent, "frontLeft", value -> debugData.doors.frontLeft = value);
            applyBooleanExtra(intent, "frontRight", value -> debugData.doors.frontRight = value);
            applyBooleanExtra(intent, "rearLeft", value -> debugData.doors.rearLeft = value);
            applyBooleanExtra(intent, "rearRight", value -> debugData.doors.rearRight = value);
            applyBooleanExtra(intent, "trunk", value -> debugData.doors.trunk = value);
            applyBooleanExtra(intent, "hood", value -> debugData.doors.hood = value);

            // 兼容带 doors. 前缀的 key。
            applyBooleanExtra(intent, "doors.frontLeft", value -> debugData.doors.frontLeft = value);
            applyBooleanExtra(intent, "doors.frontRight", value -> debugData.doors.frontRight = value);
            applyBooleanExtra(intent, "doors.rearLeft", value -> debugData.doors.rearLeft = value);
            applyBooleanExtra(intent, "doors.rearRight", value -> debugData.doors.rearRight = value);
            applyBooleanExtra(intent, "doors.trunk", value -> debugData.doors.trunk = value);
            applyBooleanExtra(intent, "doors.hood", value -> debugData.doors.hood = value);

            applyIntArrayExtra(intent, "frontRadar", debugData.frontRadar);
            applyIntArrayExtra(intent, "rearRadar", debugData.rearRadar);
            applyStringArrayExtra(intent, "rawBaseInfo", debugData.rawBaseInfo);

            String dataSource = intent.getStringExtra("dataSource");
            String debugText = intent.getStringExtra("debugText");
            debugData.dataSource = dataSource == null ? "ADB_DEBUG" : dataSource;
            debugData.debugText = debugText == null ? (senderLabel + " 模拟数据") : debugText;
            debugData.rawJson = "";

            packetCount++;
            lastPacketAtMs = System.currentTimeMillis();
            lastVehicleData = debugData;
            if (hudView != null) {
                hudView.setVehicleData(debugData, senderLabel, packetCount, lastPacketAtMs);
                hudView.setStatus("ADB 模拟数据");
            }
        } catch (Exception e) {
            if (hudView != null) {
                hudView.setStatus("ADB 模拟失败: " + e.getMessage());
            }
            Toast.makeText(this, "ADB 模拟失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    private void applyIntExtra(Intent intent, String key, IntSetter setter) {
        if (intent.hasExtra(key)) {
            setter.set(intent.getIntExtra(key, 0));
        }
    }

    private void applyBooleanExtra(Intent intent, String key, BooleanSetter setter) {
        if (intent.hasExtra(key)) {
            setter.set(intent.getBooleanExtra(key, false));
        }
    }

    private long readLongExtra(Intent intent, String key, long fallback) {
        if (!intent.hasExtra(key)) return fallback;
        try {
            return intent.getLongExtra(key, fallback);
        } catch (Exception ignored) {
            return intent.getIntExtra(key, (int) fallback);
        }
    }

    private void applyIntArrayExtra(Intent intent, String key, List<Integer> out) {
        if (!intent.hasExtra(key)) return;
        int[] values = intent.getIntArrayExtra(key);
        if (values == null) return;
        out.clear();
        for (int value : values) {
            out.add(value);
        }
    }

    private void applyStringArrayExtra(Intent intent, String key, List<String> out) {
        if (!intent.hasExtra(key)) return;
        String[] values = intent.getStringArrayExtra(key);
        if (values == null) return;
        out.clear();
        for (String value : values) {
            out.add(value);
        }
    }

    private VehicleData copyVehicleData(VehicleData source) {
        VehicleData copy = new VehicleData();
        if (source == null) {
            copy.protocol = "MikuCarHUD";
            copy.version = 1;
            copy.source = "MikuCarLauncher";
            copy.valid = true;
            copy.driverSeatbelt = true;
            copy.passengerSeatbelt = true;
            copy.dataSource = "ADB_DEBUG";
            copy.debugText = "等待 ADB 模拟数据";
            return copy;
        }

        copy.protocol = source.protocol;
        copy.version = source.version;
        copy.source = source.source;
        copy.seq = source.seq;
        copy.timestampElapsedMs = source.timestampElapsedMs;
        copy.valid = source.valid;
        copy.speedKmh = source.speedKmh;
        copy.rpm = source.rpm;
        copy.rangeKm = source.rangeKm;
        copy.fuelLevel = source.fuelLevel;
        copy.totalMileageKm = source.totalMileageKm;
        copy.driverSeatbelt = source.driverSeatbelt;
        copy.passengerSeatbelt = source.passengerSeatbelt;
        copy.doors.frontLeft = source.doors.frontLeft;
        copy.doors.frontRight = source.doors.frontRight;
        copy.doors.rearLeft = source.doors.rearLeft;
        copy.doors.rearRight = source.doors.rearRight;
        copy.doors.trunk = source.doors.trunk;
        copy.doors.hood = source.doors.hood;
        copy.leftTurn = source.leftTurn;
        copy.rightTurn = source.rightTurn;
        copy.highBeam = source.highBeam;
        copy.hazard = source.hazard;
        copy.frontRadar.addAll(source.frontRadar);
        copy.rearRadar.addAll(source.rearRadar);
        copy.rawBaseInfo.addAll(source.rawBaseInfo);
        copy.dataSource = source.dataSource;
        copy.debugText = source.debugText;
        copy.rawJson = source.rawJson;
        return copy;
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void onVehicleData(VehicleData data, String senderAddress) {
        mainHandler.post(() -> {
            packetCount++;
            lastPacketAtMs = System.currentTimeMillis();
            lastVehicleData = data;
            if (hudView != null) {
                hudView.setVehicleData(data, senderAddress, packetCount, lastPacketAtMs);
                hudView.setStatus("接收正常");
            }
        });
    }

    @Override
    public void onStatus(String message) {
        mainHandler.post(() -> {
            if (hudView != null) {
                hudView.setStatus(message);
            }
        });
    }

    @Override
    public void onInvalidPacket(String reason, String payloadPreview) {
        mainHandler.post(() -> {
            invalidPacketCount++;
            if (hudView != null) {
                hudView.setInvalidPacketCount(invalidPacketCount);
                hudView.setStatus(reason);
            }
        });
    }
}
