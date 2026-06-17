package com.jlxc.mikucarhudreceiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudiHudView extends View {
    private static final long DATA_TIMEOUT_MS = 2000L;

    // 仍然使用原背景图的 1672x941 坐标系，但 v13 起不再绘制位图背景。
    // 厂字转速表、刻度、数字、进度条全部由 Canvas 同一套坐标生成，避免位图和进度条错位。
    private static final float DESIGN_W = 1672f;
    private static final float DESIGN_H = 941f;

    private static final Typeface OEM_LABEL_TYPEFACE = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private static final Typeface OEM_LABEL_BOLD_TYPEFACE = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final Typeface OEM_STATUS_TYPEFACE = Typeface.create("sans-serif", Typeface.BOLD);

    // 转速表主路径。0-3 为斜坡，3-8 为水平段。静态刻度和动态进度条共用这三个点。
    private static final PointF RPM_0 = new PointF(145f, 682f);
    private static final PointF RPM_3 = new PointF(500f, 345f);
    private static final PointF RPM_8 = new PointF(1575f, 345f);
    private static final float RED_ZONE_START = 5.5f;
    private static final PointF FRAME_BOTTOM_LEFT = new PointF(75f, 700f);
    private static final PointF FRAME_BOTTOM_KNEE_START = new PointF(155f, 700f);
    private static final PointF FRAME_TOP_LEFT = new PointF(75f, 625f);
    private static final PointF FRAME_TOP_KNEE = new PointF(460f, 258f);
    private static final PointF FRAME_TOP_RIGHT = new PointF(1605f, 258f);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final Path tempPath = new Path();
    private final RectF bgDst = new RectF();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);

    private Typeface hardNumberTypeface;
    private VehicleData data;
    private long lastPacketAtMs = 0L;
    private String statusText = "正在启动 UDP 接收端";
    private String senderText = "";
    private int packetCount = 0;
    private int invalidPacketCount = 0;
    private int fontScale = 100;
    private int listenPort = AppPrefs.DEFAULT_PORT;
    private boolean debugMode = AppPrefs.DEFAULT_DEBUG_MODE;

    public AudiHudView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        hardNumberTypeface = HudFont.getNumberTypeface(context);
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

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
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
        computeDesignRect(w, h);

        drawVectorTachometer(canvas, fs);
        drawRpmProgress(canvas);
        drawRpmTicksAndLabels(canvas, fs);

        drawTime(canvas, fs);
        drawTurnSignals(canvas, now, fs);
        if (debugMode) {
            drawStatus(canvas, now, timedOut, fs);
        }
        drawSpeed(canvas, fs);
        drawInfoBlocks(canvas, fs);
        drawWarnings(canvas, fs);
        if (debugMode) {
            drawBottomDebug(canvas, now, timedOut, fs);
        }
    }

    private void computeDesignRect(int viewW, int viewH) {
        float viewRatio = viewW / (float) viewH;
        float designRatio = DESIGN_W / DESIGN_H;
        if (viewRatio > designRatio) {
            float drawW = viewH * designRatio;
            float left = (viewW - drawW) / 2f;
            bgDst.set(left, 0f, left + drawW, viewH);
        } else {
            float drawH = viewW / designRatio;
            float top = (viewH - drawH) / 2f;
            bgDst.set(0f, top, viewW, top + drawH);
        }
    }

    private float scale() {
        return bgDst.height() / DESIGN_H;
    }

    private PointF dp(float x, float y) {
        float sx = bgDst.width() / DESIGN_W;
        float sy = bgDst.height() / DESIGN_H;
        return new PointF(bgDst.left + x * sx, bgDst.top + y * sy);
    }

    private PointF dp(PointF p) {
        return dp(p.x, p.y);
    }

    private void drawVectorTachometer(Canvas canvas, float fs) {
        float s = scale();

        // 厂字外框。此处和内部刻度路径同源生成，不再依赖位图背景。
        Path frame = new Path();
        PointF p1 = dp(75f, 700f);
        PointF p2 = dp(155f, 700f);
        PointF p3 = dp(500f, 365f);
        PointF p4 = dp(1605f, 365f);
        PointF p5 = dp(1632f, 338f);
        PointF p6 = dp(1632f, 285f);
        PointF p7 = dp(1605f, 258f);
        PointF p8 = dp(460f, 258f);
        PointF p9 = dp(75f, 625f);
        frame.moveTo(p1.x, p1.y);
        frame.lineTo(p2.x, p2.y);
        frame.lineTo(p3.x, p3.y);
        frame.lineTo(p4.x, p4.y);
        frame.lineTo(p5.x, p5.y);
        frame.lineTo(p6.x, p6.y);
        frame.lineTo(p7.x, p7.y);
        frame.lineTo(p8.x, p8.y);
        frame.lineTo(p9.x, p9.y);
        frame.close();

        paint.reset();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeMiter(8f);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStrokeWidth(Math.max(1.6f, 2.2f * s));
        paint.setColor(Color.argb(225, 245, 250, 255));
        paint.setShadowLayer(2.5f * s, 0f, 0f, Color.argb(90, 255, 255, 255));
        canvas.drawPath(frame, paint);
        paint.clearShadowLayer();

        // 内侧主刻度基准线。
        Path base = new Path();
        PointF a = dp(RPM_0);
        PointF b = dp(RPM_3);
        PointF c = dp(RPM_8);
        base.moveTo(a.x, a.y);
        base.lineTo(b.x, b.y);
        base.lineTo(c.x, c.y);
        paint.setStrokeWidth(Math.max(1.2f, 1.8f * s));
        paint.setColor(Color.argb(210, 235, 245, 255));
        paint.setShadowLayer(1.8f * s, 0f, 0f, Color.argb(80, 255, 255, 255));
        canvas.drawPath(base, paint);
        paint.clearShadowLayer();

        drawHardText(canvas, "x1000 r/min",
                bgDst.left + bgDst.width() * 0.128f,
                bgDst.top + bgDst.height() * 0.740f,
                bgDst.height() * 0.030f * fs, Paint.Align.LEFT,
                Color.rgb(235, 245, 250), bgDst.height() * 0.0025f, 0.94f, 0.92f);
    }

    private void drawRpmTicksAndLabels(Canvas canvas, float fs) {
        float s = scale();
        int minorCount = 8 * 10;
        for (int i = 0; i <= minorCount; i++) {
            float value = i / 10f;
            boolean major = i % 10 == 0;
            boolean half = i % 5 == 0 && !major;
            float len = major ? 38f : (half ? 28f : 16f);
            float stroke = major ? 3.6f : (half ? 2.4f : 1.7f);
            int color = value >= RED_ZONE_START ? Color.rgb(255, 20, 20) : Color.rgb(238, 246, 250);
            drawTick(canvas, value, len * s, stroke * s, color);
        }

        for (int i = 0; i <= 8; i++) {
            PointF base = pointForValue(i);
            PointF pos;
            if (i < 3) {
                PointF n = normalForValue(i);
                // 斜坡数字沿斜线外侧摆放，保持厂字仪表感。
                pos = new PointF(base.x + n.x * 72f + 18f, base.y + n.y * 72f + 10f);
            } else {
                pos = new PointF(base.x, base.y - 56f);
            }
            PointF screen = dp(pos);
            int color = i >= 7 ? Color.rgb(255, 20, 20) : Color.rgb(245, 248, 250);
            drawHardText(canvas, String.valueOf(i), screen.x, screen.y,
                    bgDst.height() * 0.037f * fs, Paint.Align.CENTER,
                    color, bgDst.height() * 0.0025f, 0.96f, 0.88f);
        }
    }

    private void drawTick(Canvas canvas, float value, float lengthPx, float strokePx, int color) {
        PointF p = pointForValue(value);
        PointF n = normalForValue(value);
        PointF a = dp(p.x, p.y);
        PointF b = dp(p.x + n.x * (lengthPx / scale()), p.y + n.y * (lengthPx / scale()));

        paint.reset();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStrokeWidth(Math.max(1f, strokePx));
        paint.setColor(color);
        if (value >= RED_ZONE_START) {
            paint.setShadowLayer(2.2f * scale(), 0f, 0f, Color.argb(110, 255, 0, 0));
        }
        canvas.drawLine(a.x, a.y, b.x, b.y, paint);
        paint.clearShadowLayer();
    }

    private PointF pointForValue(float value) {
        value = clamp(value, 0f, 8f);
        if (value <= 3f) {
            return lerpPoint(RPM_0, RPM_3, value / 3f);
        }
        return lerpPoint(RPM_3, RPM_8, (value - 3f) / 5f);
    }

    private PointF normalForValue(float value) {
        PointF a;
        PointF b;
        if (value <= 3f) {
            a = RPM_0;
            b = RPM_3;
        } else {
            a = RPM_3;
            b = RPM_8;
        }
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.0001f) return new PointF(0f, -1f);
        if (value <= 3f) {
            // 斜坡段刻度向外上方伸出。
            return new PointF(dy / len, -dx / len);
        }
        return new PointF(0f, -1f);
    }

    private PointF lerpPoint(PointF a, PointF b, float t) {
        t = clamp(t, 0f, 1f);
        return new PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
    }

    private void drawRpmProgress(Canvas canvas) {
        if (data == null) return;
        float rpmValue = clamp(data.rpm / 1000f, 0f, 8f);
        if (rpmValue <= 0.01f) return;

        boolean flashZone = rpmValue >= RED_ZONE_START;
        boolean flashOn = !flashZone || ((System.currentTimeMillis() / 140L) % 2L == 0L);
        if (flashZone) {
            // 硬闪烁：超 5500 转后持续请求重绘，不做渐入渐出。
            postInvalidateDelayed(80L);
        }
        if (!flashOn) {
            return;
        }

        float s = scale();
        paint.reset();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(235, 255, 18, 18));
        paint.setShadowLayer(10f * s, 0f, 0f, Color.argb(145, 255, 0, 0));

        Path fill = buildProgressFillPath(rpmValue);
        canvas.drawPath(fill, paint);
        paint.clearShadowLayer();
    }

    private Path buildProgressFillPath(float value) {
        value = clamp(value, 0f, 8f);
        Path path = new Path();

        PointF p1 = dp(FRAME_BOTTOM_LEFT);
        PointF p2 = dp(FRAME_BOTTOM_KNEE_START);
        PointF r0 = dp(RPM_0);
        PointF r3 = dp(RPM_3);
        PointF topLeft = dp(FRAME_TOP_LEFT);
        PointF topKnee = dp(FRAME_TOP_KNEE);

        path.moveTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
        path.lineTo(r0.x, r0.y);

        if (value <= 3f) {
            PointF lowerEnd = dp(pointForValue(value));
            PointF upperEnd = dp(upperPointForValue(value));
            path.lineTo(lowerEnd.x, lowerEnd.y);
            path.lineTo(upperEnd.x, upperEnd.y);
            path.lineTo(topLeft.x, topLeft.y);
        } else {
            PointF lowerEnd = dp(pointForValue(value));
            PointF upperEnd = dp(upperPointForValue(value));
            path.lineTo(r3.x, r3.y);
            path.lineTo(lowerEnd.x, lowerEnd.y);
            path.lineTo(upperEnd.x, upperEnd.y);
            path.lineTo(topKnee.x, topKnee.y);
            path.lineTo(topLeft.x, topLeft.y);
        }
        path.close();
        return path;
    }

    private PointF upperPointForValue(float value) {
        value = clamp(value, 0f, 8f);
        if (value <= 3f) {
            return lerpPoint(FRAME_TOP_LEFT, FRAME_TOP_KNEE, value / 3f);
        }
        return lerpPoint(FRAME_TOP_KNEE, FRAME_TOP_RIGHT, (value - 3f) / 5f);
    }

    private void drawTime(Canvas canvas, float fs) {
        String time = timeFormat.format(new Date());
        drawHardText(canvas, time,
                bgDst.left + bgDst.width() * 0.035f,
                bgDst.top + bgDst.height() * 0.105f,
                bgDst.height() * 0.129f * fs, Paint.Align.LEFT,
                Color.rgb(235, 255, 255), bgDst.height() * 0.004f, 0.92f, 0.88f);
    }

    private void drawStatus(Canvas canvas, long now, boolean timedOut, float fs) {
        String status = timedOut ? "等待车机数据" : "MikuCarLauncher 已连接";
        int color = timedOut ? Color.rgb(255, 210, 80) : Color.rgb(90, 255, 190);
        float y = bgDst.top + bgDst.height() * 0.105f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(OEM_STATUS_TYPEFACE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(bgDst.height() * 0.040f * fs);
        drawGlowText(canvas, status, bgDst.centerX(), y, color, bgDst.height() * 0.010f);

        String age = lastPacketAtMs <= 0L ? "--" : (now - lastPacketAtMs) + "ms";
        drawHardText(canvas, "UDP " + listenPort + "  " + age,
                bgDst.right - bgDst.width() * 0.035f, y,
                bgDst.height() * 0.025f * fs, Paint.Align.RIGHT,
                Color.rgb(120, 150, 160), 0f, 0.90f, 0.88f);
    }

    private void drawSpeed(Canvas canvas, float fs) {
        int speed = data == null ? 0 : data.speedKmh;
        String speedText = String.valueOf(Math.max(0, speed));
        float centerX = bgDst.left + bgDst.width() * 0.525f;
        float baseline = bgDst.top + bgDst.height() * 0.735f;

        drawHardText(canvas, speedText, centerX, baseline,
                bgDst.height() * 0.305f * fs, Paint.Align.CENTER,
                Color.WHITE, bgDst.height() * 0.006f, 1.0f, 0.82f);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(OEM_LABEL_TYPEFACE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(bgDst.height() * 0.047f * fs);
        paint.setTextScaleX(0.96f);
        drawGlowText(canvas, "km/h", centerX, baseline + bgDst.height() * 0.078f,
                Color.rgb(135, 230, 255), bgDst.height() * 0.008f);
        paint.setTextScaleX(1.0f);
    }

    private void drawInfoBlocks(Canvas canvas, float fs) {
        int range = data == null ? 0 : data.rangeKm;
        long odo = data == null ? 0L : data.totalMileageKm;

        // 左下角 RPM 数值按需求删除，仅保留右侧大号剩余里程。
        drawRangeValueOnly(canvas,
                range + " km",
                getWidth() - 10f,
                bgDst.top + bgDst.height() * 0.840f,
                fs);

        if (debugMode) {
            drawHardText(canvas, "ODO " + odo + " km",
                    bgDst.right - bgDst.width() * 0.035f, bgDst.top + bgDst.height() * 0.945f,
                    bgDst.height() * 0.025f * fs, Paint.Align.RIGHT,
                    Color.rgb(125, 155, 165), 0f, 0.92f, 0.88f);
        }
    }

    private void drawRangeValueOnly(Canvas canvas, String value, float rightX, float y, float fs) {
        float valueSize = bgDst.height() * 0.110f * fs;
        drawHardText(canvas, value, rightX, y,
                valueSize, Paint.Align.RIGHT,
                Color.rgb(235, 255, 255), bgDst.height() * 0.0035f, 1.0f, 0.88f);
    }

    private void drawWarnings(Canvas canvas, float fs) {
        if (data == null) return;
        boolean hasWarning = data.hasAnyWarning();
        if (!debugMode && !hasWarning) return;
        String warning = data.buildWarningText();
        float y = bgDst.top + bgDst.height() * 0.925f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(OEM_STATUS_TYPEFACE);
        paint.setTextSize(bgDst.height() * 0.045f * fs);
        int color = hasWarning ? Color.rgb(255, 80, 55) : Color.rgb(105, 255, 185);
        drawGlowText(canvas, warning, bgDst.centerX(), y, color, bgDst.height() * 0.008f);
    }

    private void drawTurnSignals(Canvas canvas, long now, float fs) {
        if (data == null) return;
        boolean blinkOn = ((now / 330L) % 2L) == 0L;
        boolean showLeft = data.hazard || data.leftTurn;
        boolean showRight = data.hazard || data.rightTurn;
        if (!blinkOn || (!showLeft && !showRight)) return;

        float y = bgDst.top + bgDst.height() * 0.165f;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(OEM_STATUS_TYPEFACE);
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
        long age = lastPacketAtMs <= 0L ? -1L : now - lastPacketAtMs;
        String ageText = age < 0 ? "N/A" : age + "ms";
        String left = String.format(Locale.CHINA,
                "packets=%d  invalid=%d  age=%s  sender=%s",
                packetCount, invalidPacketCount, ageText, senderText);
        drawHardText(canvas, left,
                bgDst.left + bgDst.width() * 0.035f, bgDst.top + bgDst.height() * 0.980f,
                bgDst.height() * 0.022f * fs, Paint.Align.LEFT,
                Color.rgb(85, 110, 118), 0f, 0.85f, 0.82f);

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
        drawHardText(canvas, right,
                bgDst.right - bgDst.width() * 0.035f, bgDst.top + bgDst.height() * 0.980f,
                bgDst.height() * 0.022f * fs, Paint.Align.RIGHT,
                Color.rgb(85, 110, 118), 0f, 0.85f, 0.82f);
    }

    private void drawHardText(Canvas canvas, String text, float x, float baseline, float textSize,
                              Paint.Align align, int color, float glowRadius,
                              float alphaScale, float textScaleX) {
        if (text == null) text = "";
        alphaScale = clamp(alphaScale, 0f, 1f);
        int drawColor = Color.argb(Math.round(Color.alpha(color) * alphaScale),
                Color.red(color), Color.green(color), Color.blue(color));

        if (glowRadius > 0f) {
            drawMixedNumberText(canvas, text, x, baseline, textSize, align,
                    drawColor, textScaleX, glowRadius);
        }
        drawMixedNumberText(canvas, text, x, baseline, textSize, align,
                drawColor, textScaleX, 0f);
    }

    private void drawMixedNumberText(Canvas canvas, String text, float x, float baseline, float textSize,
                                     Paint.Align align, int color, float textScaleX, float glowRadius) {
        if (text == null || text.isEmpty()) return;

        float totalWidth = measureMixedNumberText(text, textSize, textScaleX);
        float cursorX = x;
        if (align == Paint.Align.CENTER) {
            cursorX = x - totalWidth / 2f;
        } else if (align == Paint.Align.RIGHT) {
            cursorX = x - totalWidth;
        }

        numberPaint.reset();
        numberPaint.setAntiAlias(true);
        numberPaint.setDither(true);
        numberPaint.setSubpixelText(true);
        numberPaint.setLinearText(true);
        numberPaint.setStyle(Paint.Style.FILL);
        numberPaint.setTextAlign(Paint.Align.LEFT);
        numberPaint.setTextSize(textSize);
        numberPaint.setTextScaleX(textScaleX);
        numberPaint.setColor(color);
        if (glowRadius > 0f) {
            numberPaint.setShadowLayer(glowRadius, 0f, 0f, color);
        }

        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            numberPaint.setTypeface(isAsciiDigit(cp)
                    ? (hardNumberTypeface == null ? HudFont.getNumberTypeface(getContext()) : hardNumberTypeface)
                    : OEM_LABEL_TYPEFACE);
            canvas.drawText(ch, cursorX, baseline, numberPaint);
            cursorX += numberPaint.measureText(ch);
            i += Character.charCount(cp);
        }

        numberPaint.clearShadowLayer();
        numberPaint.setTextScaleX(1.0f);
    }

    private float measureMixedNumberText(String text, float textSize, float textScaleX) {
        if (text == null || text.isEmpty()) return 0f;
        numberPaint.reset();
        numberPaint.setAntiAlias(true);
        numberPaint.setSubpixelText(true);
        numberPaint.setLinearText(true);
        numberPaint.setTextSize(textSize);
        numberPaint.setTextScaleX(textScaleX);

        float width = 0f;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            numberPaint.setTypeface(isAsciiDigit(cp)
                    ? (hardNumberTypeface == null ? HudFont.getNumberTypeface(getContext()) : hardNumberTypeface)
                    : OEM_LABEL_TYPEFACE);
            width += numberPaint.measureText(ch);
            i += Character.charCount(cp);
        }
        numberPaint.setTextScaleX(1.0f);
        return width;
    }

    private static boolean isAsciiDigit(int codePoint) {
        return codePoint >= '0' && codePoint <= '9';
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
