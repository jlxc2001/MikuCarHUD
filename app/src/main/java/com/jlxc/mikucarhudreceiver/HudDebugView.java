package com.jlxc.mikucarhudreceiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.util.Locale;

public class HudDebugView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();

    private VehicleData data;
    private long lastPacketAtMs = 0L;
    private String statusText = "正在启动 UDP 接收端";
    private String senderText = "";
    private int packetCount = 0;
    private int invalidPacketCount = 0;
    private int fontScale = 100;
    private int listenPort = AppPrefs.DEFAULT_PORT;

    public HudDebugView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
    }

    public void setVehicleData(VehicleData data, String sender, int packetCount, long receivedAtMs) {
        this.data = data;
        this.senderText = sender == null ? "" : sender;
        this.packetCount = packetCount;
        this.lastPacketAtMs = receivedAtMs;
        invalidate();
    }

    public void setStatus(String statusText) {
        this.statusText = statusText == null ? "" : statusText;
        invalidate();
    }

    public void setInvalidPacketCount(int invalidPacketCount) {
        this.invalidPacketCount = invalidPacketCount;
        invalidate();
    }

    public void setFontScale(int fontScale) {
        this.fontScale = Math.max(60, Math.min(180, fontScale));
        invalidate();
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        long now = System.currentTimeMillis();
        boolean timedOut = lastPacketAtMs <= 0 || now - lastPacketAtMs > 2000;

        canvas.drawColor(Color.BLACK);

        float scale = fontScale / 100f;
        float speedSize = h * 0.43f * scale;
        float labelSize = h * 0.055f * scale;
        float metricSize = h * 0.088f * scale;
        float warningSize = h * 0.07f * scale;
        float smallSize = h * 0.04f * scale;

        drawTopBar(canvas, w, h, labelSize, timedOut, now);
        drawTurnSignals(canvas, w, h, now);
        drawSpeed(canvas, w, h, speedSize, labelSize);
        drawMetrics(canvas, w, h, metricSize, labelSize);
        drawWarnings(canvas, w, h, warningSize);
        drawDebug(canvas, w, h, smallSize, timedOut, now);
    }

    private void drawTopBar(Canvas canvas, int w, int h, float textSize, boolean timedOut, long now) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(textSize);
        paint.setColor(timedOut ? Color.rgb(255, 210, 80) : Color.rgb(80, 255, 180));

        String title;
        if (timedOut) {
            title = "等待车机数据";
        } else {
            title = "MikuCarLauncher 数据已连接";
        }
        canvas.drawText(title, w * 0.035f, h * 0.09f, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.rgb(170, 210, 210));
        canvas.drawText("UDP:" + listenPort + "  长按设置", w * 0.965f, h * 0.09f, paint);
    }

    private void drawSpeed(Canvas canvas, int w, int h, float speedSize, float labelSize) {
        int speed = data == null ? 0 : data.speedKmh;
        String speedText = String.valueOf(speed);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(speedSize);
        paint.setColor(Color.WHITE);
        paint.getTextBounds(speedText, 0, speedText.length(), textBounds);
        float speedY = h * 0.55f + textBounds.height() / 2f;
        canvas.drawText(speedText, w * 0.5f, speedY, paint);

        paint.setTextSize(labelSize);
        paint.setColor(Color.rgb(150, 220, 255));
        canvas.drawText("km/h", w * 0.5f, speedY + h * 0.105f, paint);
    }

    private void drawMetrics(Canvas canvas, int w, int h, float valueSize, float labelSize) {
        int rpm = data == null ? 0 : data.rpm;
        int range = data == null ? 0 : data.rangeKm;
        int fuel = data == null ? 0 : data.fuelLevel;
        long odo = data == null ? 0 : data.totalMileageKm;

        drawMetric(canvas, "RPM", String.valueOf(rpm), w * 0.18f, h * 0.34f, valueSize, labelSize);
        drawMetric(canvas, "RANGE", range + " km", w * 0.82f, h * 0.34f, valueSize, labelSize);
        drawMetric(canvas, "FUEL", fuel + "%", w * 0.18f, h * 0.74f, valueSize, labelSize);
        drawMetric(canvas, "ODO", odo + " km", w * 0.82f, h * 0.74f, valueSize, labelSize);
    }

    private void drawMetric(Canvas canvas, String label, String value, float x, float y, float valueSize, float labelSize) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextSize(labelSize);
        paint.setColor(Color.rgb(120, 180, 200));
        canvas.drawText(label, x, y - valueSize * 0.65f, paint);

        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(valueSize);
        paint.setColor(Color.rgb(235, 255, 255));
        canvas.drawText(value, x, y, paint);
    }

    private void drawTurnSignals(Canvas canvas, int w, int h, long now) {
        if (data == null) return;
        boolean blinkOn = ((now / 350) % 2) == 0;
        boolean showLeft = data.hazard || data.leftTurn;
        boolean showRight = data.hazard || data.rightTurn;
        if (!blinkOn) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(h * 0.18f * (fontScale / 100f));
        paint.setColor(Color.rgb(80, 255, 120));
        if (showLeft) {
            canvas.drawText("◀", w * 0.08f, h * 0.55f, paint);
        }
        if (showRight) {
            canvas.drawText("▶", w * 0.92f, h * 0.55f, paint);
        }
    }

    private void drawWarnings(Canvas canvas, int w, int h, float warningSize) {
        if (data == null) return;
        String warning = data.buildWarningText();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(warningSize);
        paint.setColor(data.hasAnyWarning() ? Color.rgb(255, 90, 70) : Color.rgb(110, 255, 180));
        canvas.drawText(warning, w * 0.5f, h * 0.92f, paint);
    }

    private void drawDebug(Canvas canvas, int w, int h, float smallSize, boolean timedOut, long now) {
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextSize(smallSize);
        paint.setColor(Color.rgb(110, 145, 150));

        long age = lastPacketAtMs <= 0 ? -1 : now - lastPacketAtMs;
        String ageText = age < 0 ? "N/A" : age + "ms";
        String debug1 = String.format(Locale.CHINA,
                "status=%s  packets=%d  invalid=%d  age=%s  sender=%s",
                statusText, packetCount, invalidPacketCount, ageText, senderText);
        canvas.drawText(debug1, w * 0.035f, h * 0.965f, paint);

        if (data != null) {
            paint.setTextAlign(Paint.Align.RIGHT);
            String source = data.dataSource == null || data.dataSource.isEmpty() ? "" : "  src=" + data.dataSource;
            String debug = data.debugText == null || data.debugText.isEmpty() ? "" : "  dbg=" + trimDebug(data.debugText);
            String debug2 = "seq=" + data.seq
                    + "  valid=" + data.valid
                    + "  raw=" + data.rawBaseInfo.size()
                    + source
                    + debug
                    + "  " + data.radarSummary();
            canvas.drawText(debug2, w * 0.965f, h * 0.965f, paint);
        } else if (timedOut) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.rgb(255, 220, 120));
            canvas.drawText("未收到符合 protocol/version/source 的 JSON 数据", w * 0.5f, h * 0.72f, paint);
        }
    }
    private static String trimDebug(String value) {
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= 32) {
            return oneLine;
        }
        return oneLine.substring(0, 32) + "...";
    }

}
