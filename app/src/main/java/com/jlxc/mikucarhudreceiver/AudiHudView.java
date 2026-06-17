package com.jlxc.mikucarhudreceiver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

public class AudiHudView extends View {
    private static final long DATA_TIMEOUT_MS = 2000L;
    private static final float DESIGN_W = 1672f;
    private static final float DESIGN_H = 941f;

    // 这三个点对应背景图里的厂字型转速条：0 -> 3 为斜坡，3 -> 8 为水平段。
    // 后续如果你换了一张背景，只需要微调这里的原图坐标。
    private static final PointF RPM_0 = new PointF(150f, 672f);
    private static final PointF RPM_3 = new PointF(488f, 344f);
    private static final PointF RPM_8 = new PointF(1576f, 345f);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect textBounds = new Rect();
    private final RectF bgDst = new RectF();
    private final Path tempPath = new Path();

    private final Bitmap tachBackground;

    private VehicleData data;
    private long lastPacketAtMs = 0L;
    private String statusText = "正在启动 UDP 接收端";
    private String senderText = "";
    private int packetCount = 0;
    private int invalidPacketCount = 0;
    private int fontScale = 100;
    private int listenPort = AppPrefs.DEFAULT_PORT;

    public AudiHudView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        // 转速进度的发光描边用软件层更稳定，低端机也能跑。
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        tachBackground = BitmapFactory.decodeResource(getResources(), R.drawable.hud_tach_bg);
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
        if (w <= 0 || h <= 0) return;

        long now = System.currentTimeMillis();
        boolean timedOut = lastPacketAtMs <= 0L || now - lastPacketAtMs > DATA_TIMEOUT_MS;
        float fs = fontScale / 100f;

        canvas.drawColor(Color.BLACK);
        computeBackgroundRect(w, h);
        drawBackground(canvas);
        drawRpmProgress(canvas);
        drawTurnSignals(canvas, now, timedOut, fs);
        drawStatus(canvas, now, timedOut, fs);
        drawSpeed(canvas, fs);
        drawInfoBlocks(canvas, fs);
        drawWarnings(canvas, fs);
        drawBottomDebug(canvas, now, timedOut, fs);
    }

    private void computeBackgroundRect(int viewW, int viewH) {
        float viewRatio = viewW / (float) viewH;
        float bgRatio = DESIGN_W / DESIGN_H;
        if (viewRatio > bgRatio) {
            float drawW = viewH * bgRatio;
            float left = (viewW - drawW) / 2f;
            bgDst.set(left, 0f, left + drawW, viewH);
        } else {
            float drawH = viewW / bgRatio;
            float top = (viewH - drawH) / 2f;
            bgDst.set(0f, top, viewW, top + drawH);
        }
    }

    private void drawBackground(Canvas canvas) {
        if (tachBackground != null && !tachBackground.isRecycled()) {
            canvas.drawBitmap(tachBackground, null, bgDst, bgPaint);
        }
    }

    private PointF mapDesignPoint(PointF p) {
        float sx = bgDst.width() / DESIGN_W;
        float sy = bgDst.height() / DESIGN_H;
        return new PointF(bgDst.left + p.x * sx, bgDst.top + p.y * sy);
    }

    private PointF pointForRpm(float rpm) {
        float v = clamp(rpm / 1000f, 0f, 8f);
        if (v <= 3f) {
            float t = v / 3f;
            return lerpPoint(RPM_0, RPM_3, t);
        } else {
            float t = (v - 3f) / 5f;
            return lerpPoint(RPM_3, RPM_8, t);
        }
    }

    private PointF lerpPoint(PointF a, PointF b, float t) {
        t = clamp(t, 0f, 1f);
        return new PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
    }

    private void drawRpmProgress(Canvas canvas) {
        if (data == null) return;
        float rpm = clamp(data.rpm, 0f, 8000f);
        if (rpm <= 10f) return;

        float baseStroke = Math.max(4f, bgDst.height() * 0.010f);
        float glowStroke = Math.max(12f, bgDst.height() * 0.025f);

        // 先画一层青白色发光底，再画实线。
        drawRpmSegment(canvas, 0f, Math.min(rpm, 6500f), Color.argb(95, 80, 240, 255), glowStroke, true);
        drawRpmSegment(canvas, 0f, Math.min(rpm, 6500f), Color.rgb(235, 255, 255), baseStroke, false);

        // 6500 转以后进入红区。
        if (rpm > 6500f) {
            drawRpmSegment(canvas, 6500f, rpm, Color.argb(120, 255, 0, 0), glowStroke, true);
            drawRpmSegment(canvas, 6500f, rpm, Color.rgb(255, 0, 0), baseStroke, false);
        }

        // 当前转速点。
        PointF end = mapDesignPoint(pointForRpm(rpm));
        boolean red = rpm >= 6500f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(red ? Color.argb(130, 255, 0, 0) : Color.argb(130, 80, 240, 255));
        canvas.drawCircle(end.x, end.y, bgDst.height() * 0.020f, paint);
        paint.setColor(red ? Color.rgb(255, 30, 30) : Color.rgb(240, 255, 255));
        canvas.drawCircle(end.x, end.y, bgDst.height() * 0.009f, paint);
    }

    private void drawRpmSegment(Canvas canvas, float startRpm, float endRpm, int color, float strokeWidth, boolean glow) {
        startRpm = clamp(startRpm, 0f, 8000f);
        endRpm = clamp(endRpm, 0f, 8000f);
        if (endRpm <= startRpm) return;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        if (glow) {
            paint.setShadowLayer(strokeWidth * 0.8f, 0f, 0f, color);
        } else {
            paint.clearShadowLayer();
        }

        if (startRpm < 3000f && endRpm > 3000f) {
            drawRpmSegment(canvas, startRpm, 3000f, color, strokeWidth, glow);
            drawRpmSegment(canvas, 3000f, endRpm, color, strokeWidth, glow);
            return;
        }

        PointF start = mapDesignPoint(pointForRpm(startRpm));
        PointF end = mapDesignPoint(pointForRpm(endRpm));
        tempPath.reset();
        tempPath.moveTo(start.x, start.y);
        tempPath.lineTo(end.x, end.y);
        canvas.drawPath(tempPath, paint);
        paint.clearShadowLayer();
    }

    private void drawStatus(Canvas canvas, long now, boolean timedOut, float fs) {
        String status = timedOut ? "等待车机数据" : "MikuCarLauncher 已连接";
        int color = timedOut ? Color.rgb(255, 210, 80) : Color.rgb(90, 255, 190);
        float y = bgDst.top + bgDst.height() * 0.105f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(bgDst.height() * 0.040f * fs);
        drawGlowText(canvas, status, bgDst.centerX(), y, color, bgDst.height() * 0.010f);

        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextSize(bgDst.height() * 0.025f * fs);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.rgb(120, 150, 160));
        String age = lastPacketAtMs <= 0L ? "--" : (now - lastPacketAtMs) + "ms";
        canvas.drawText("UDP " + listenPort + "  " + age, bgDst.right - bgDst.width() * 0.035f, y, paint);
    }

    private void drawSpeed(Canvas canvas, float fs) {
        int speed = data == null ? 0 : data.speedKmh;
        String speedText = String.valueOf(Math.max(0, speed));
        float centerX = bgDst.left + bgDst.width() * 0.525f;
        float baseline = bgDst.top + bgDst.height() * 0.735f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(bgDst.height() * 0.285f * fs);
        drawGlowText(canvas, speedText, centerX, baseline, Color.WHITE, bgDst.height() * 0.018f);

        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(bgDst.height() * 0.047f * fs);
        drawGlowText(canvas, "km/h", centerX, baseline + bgDst.height() * 0.078f, Color.rgb(135, 230, 255), bgDst.height() * 0.008f);
    }

    private void drawInfoBlocks(Canvas canvas, float fs) {
        int rpm = data == null ? 0 : data.rpm;
        int range = data == null ? 0 : data.rangeKm;
        int fuel = data == null ? 0 : data.fuelLevel;
        long odo = data == null ? 0L : data.totalMileageKm;

        drawInfoBlock(canvas,
                "RPM", String.format(Locale.CHINA, "%04d", Math.max(0, rpm)),
                bgDst.left + bgDst.width() * 0.135f,
                bgDst.top + bgDst.height() * 0.820f,
                fs);

        drawInfoBlock(canvas,
                "RANGE", range + " km",
                bgDst.left + bgDst.width() * 0.775f,
                bgDst.top + bgDst.height() * 0.775f,
                fs);

        drawInfoBlock(canvas,
                "FUEL", fuel + "%",
                bgDst.left + bgDst.width() * 0.775f,
                bgDst.top + bgDst.height() * 0.870f,
                fs);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(bgDst.height() * 0.025f * fs);
        paint.setColor(Color.rgb(125, 155, 165));
        canvas.drawText("ODO " + odo + " km", bgDst.right - bgDst.width() * 0.035f, bgDst.top + bgDst.height() * 0.945f, paint);
    }

    private void drawInfoBlock(Canvas canvas, String label, String value, float x, float y, float fs) {
        float labelSize = bgDst.height() * 0.027f * fs;
        float valueSize = bgDst.height() * 0.055f * fs;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextSize(labelSize);
        paint.setColor(Color.rgb(110, 185, 205));
        canvas.drawText(label, x, y - bgDst.height() * 0.035f, paint);

        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(valueSize);
        drawGlowText(canvas, value, x, y + bgDst.height() * 0.020f, Color.rgb(235, 255, 255), bgDst.height() * 0.006f);
    }

    private void drawWarnings(Canvas canvas, float fs) {
        if (data == null) return;
        String warning = data.buildWarningText();
        boolean hasWarning = data.hasAnyWarning();
        float y = bgDst.top + bgDst.height() * 0.925f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(bgDst.height() * 0.045f * fs);
        int color = hasWarning ? Color.rgb(255, 80, 55) : Color.rgb(105, 255, 185);
        drawGlowText(canvas, warning, bgDst.centerX(), y, color, bgDst.height() * 0.008f);
    }

    private void drawTurnSignals(Canvas canvas, long now, boolean timedOut, float fs) {
        if (data == null) return;
        boolean blinkOn = ((now / 330L) % 2L) == 0L;
        boolean showLeft = data.hazard || data.leftTurn;
        boolean showRight = data.hazard || data.rightTurn;
        if (!blinkOn || (!showLeft && !showRight)) return;

        float y = bgDst.top + bgDst.height() * 0.165f;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(bgDst.height() * 0.105f * fs);
        int color = data.hazard ? Color.rgb(255, 210, 60) : Color.rgb(70, 255, 120);

        if (showLeft) {
            drawGlowText(canvas, "◀", bgDst.left + bgDst.width() * 0.065f, y, color, bgDst.height() * 0.014f);
        }
        if (showRight) {
            drawGlowText(canvas, "▶", bgDst.right - bgDst.width() * 0.065f, y, color, bgDst.height() * 0.014f);
        }
    }

    private void drawBottomDebug(Canvas canvas, long now, boolean timedOut, float fs) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(bgDst.height() * 0.022f * fs);
        paint.setColor(Color.rgb(85, 110, 118));

        long age = lastPacketAtMs <= 0L ? -1L : now - lastPacketAtMs;
        String ageText = age < 0 ? "N/A" : age + "ms";
        String left = String.format(Locale.CHINA,
                "packets=%d  invalid=%d  age=%s  sender=%s",
                packetCount, invalidPacketCount, ageText, senderText);
        canvas.drawText(left, bgDst.left + bgDst.width() * 0.035f, bgDst.top + bgDst.height() * 0.980f, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        String right;
        if (data != null) {
            String dbg = trimDebug(safe(data.debugText), 28);
            String src = trimDebug(safe(data.dataSource), 20);
            right = "seq=" + data.seq
                    + "  " + data.radarSummary()
                    + (src.isEmpty() ? "" : "  src=" + src)
                    + (dbg.isEmpty() ? "" : "  dbg=" + dbg);
        } else {
            right = timedOut ? "未收到符合 MikuCarHUD v1 的数据" : statusText;
        }
        canvas.drawText(right, bgDst.right - bgDst.width() * 0.035f, bgDst.top + bgDst.height() * 0.980f, paint);
    }

    private void drawGlowText(Canvas canvas, String text, float x, float y, int color, float glowRadius) {
        if (text == null) text = "";
        int oldColor = paint.getColor();
        Paint.Style oldStyle = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(color);
        paint.setShadowLayer(glowRadius, 0f, 0f, color);
        canvas.drawText(text, x, y, paint);
        paint.clearShadowLayer();

        paint.setColor(color);
        canvas.drawText(text, x, y, paint);
        paint.setColor(oldColor);
        paint.setStyle(oldStyle);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trimDebug(String value, int max) {
        String oneLine = safe(value).replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= max) return oneLine;
        return oneLine.substring(0, Math.max(0, max)) + "...";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
