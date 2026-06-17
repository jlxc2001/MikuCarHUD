package com.jlxc.mikucarhudreceiver;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends Activity implements HudUdpReceiver.Listener {
    private HudDebugView hudView;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setupFullscreen();
        hudView = new HudDebugView(this);
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
    protected void onResume() {
        super.onResume();
        setupFullscreen();
        loadSettings();
        acquireMulticastLock();
        startReceiver();
        mainHandler.removeCallbacks(invalidateTicker);
        mainHandler.post(invalidateTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(invalidateTicker);
        stopReceiver();
        releaseMulticastLock();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

        if (hudView != null) {
            hudView.setScaleX(mirror ? -1f : 1f);
            hudView.setFontScale(fontScale);
            hudView.setListenPort(listenPort);
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

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void onVehicleData(VehicleData data, String senderAddress) {
        mainHandler.post(() -> {
            packetCount++;
            lastPacketAtMs = System.currentTimeMillis();
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
