package com.jlxc.mikucarhudreceiver;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

/**
 * 代码内置的 HUD 数字字形库。
 *
 * 不依赖外部 ttf/otf 文件，专门给旧安卓设备使用。
 * 设计方向：窄体、硬朗、斜切角，接近德系原厂仪表 / HUD 数字观感；
 * 不是七段数码管字体。
 */
public class AudiOemNumberFont {
    private static final float GLYPH_W = 0.62f;
    private static final float TRACKING = 0.08f;
    private static final float DOT_W = 0.18f;
    private static final float SPACE_W = 0.26f;

    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();

    public AudiOemNumberFont() {
        glyphPaint.setStyle(Paint.Style.STROKE);
        glyphPaint.setStrokeCap(Paint.Cap.SQUARE);
        glyphPaint.setStrokeJoin(Paint.Join.MITER);
        fallbackPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        fallbackPaint.setStyle(Paint.Style.FILL);
    }

    public float measureText(String text, float textSize) {
        if (text == null || text.isEmpty()) return 0f;
        float total = 0f;
        fallbackPaint.setTextSize(textSize);
        fallbackPaint.setTextScaleX(0.88f);
        for (int i = 0; i < text.length(); i++) {
            total += advanceOf(text.charAt(i), textSize);
        }
        return total;
    }

    public void drawText(Canvas canvas, String text, float x, float baseline, float textSize,
                         Paint.Align align, int color, float glowRadius) {
        drawText(canvas, text, x, baseline, textSize, align, color, glowRadius, 1.0f);
    }

    public void drawText(Canvas canvas, String text, float x, float baseline, float textSize,
                         Paint.Align align, int color, float glowRadius, float alphaScale) {
        if (text == null) text = "";
        alphaScale = Math.max(0f, Math.min(1f, alphaScale));
        float totalW = measureText(text, textSize);
        float cursor = x;
        if (align == Paint.Align.CENTER) {
            cursor = x - totalW / 2f;
        } else if (align == Paint.Align.RIGHT) {
            cursor = x - totalW;
        }

        int drawColor = applyAlpha(color, alphaScale);
        fallbackPaint.setTextSize(textSize);
        fallbackPaint.setTextScaleX(0.88f);
        fallbackPaint.setColor(drawColor);
        fallbackPaint.setShadowLayer(glowRadius, 0f, 0f, drawColor);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                drawDigit(canvas, c, cursor, baseline, textSize, drawColor, glowRadius);
            } else if (c == '.') {
                drawDot(canvas, cursor, baseline, textSize, drawColor, glowRadius);
            } else if (c == ':') {
                drawColon(canvas, cursor, baseline, textSize, drawColor, glowRadius);
            } else if (c == '-') {
                drawDash(canvas, cursor, baseline, textSize, drawColor, glowRadius);
            } else if (c == '%') {
                drawPercent(canvas, cursor, baseline, textSize, drawColor, glowRadius);
            } else if (c != ' ') {
                canvas.drawText(String.valueOf(c), cursor, baseline, fallbackPaint);
            }
            cursor += advanceOf(c, textSize);
        }
        fallbackPaint.clearShadowLayer();
    }

    private float advanceOf(char c, float size) {
        if (c >= '0' && c <= '9') return size * (GLYPH_W + TRACKING);
        if (c == '.') return size * (DOT_W + TRACKING * 0.7f);
        if (c == ':') return size * (DOT_W + TRACKING * 0.9f);
        if (c == '-') return size * (0.36f + TRACKING);
        if (c == '%') return size * (0.70f + TRACKING);
        if (c == ' ') return size * SPACE_W;
        fallbackPaint.setTextSize(size);
        fallbackPaint.setTextScaleX(0.88f);
        return fallbackPaint.measureText(String.valueOf(c)) + size * TRACKING * 0.25f;
    }

    private void drawDigit(Canvas canvas, char c, float left, float baseline, float size, int color, float glowRadius) {
        path.reset();
        buildDigitPath(c, path);
        drawPath(canvas, path, left, baseline - size, size * GLYPH_W, size, color, glowRadius, size * 0.055f);
    }

    private void drawDot(Canvas canvas, float left, float baseline, float size, int color, float glowRadius) {
        glyphPaint.reset();
        glyphPaint.setAntiAlias(true);
        glyphPaint.setDither(true);
        glyphPaint.setStyle(Paint.Style.FILL);
        glyphPaint.setColor(color);
        glyphPaint.setShadowLayer(glowRadius, 0f, 0f, color);
        canvas.drawCircle(left + size * 0.07f, baseline - size * 0.07f, size * 0.035f, glyphPaint);
        glyphPaint.clearShadowLayer();
    }

    private void drawColon(Canvas canvas, float left, float baseline, float size, int color, float glowRadius) {
        glyphPaint.reset();
        glyphPaint.setAntiAlias(true);
        glyphPaint.setDither(true);
        glyphPaint.setStyle(Paint.Style.FILL);
        glyphPaint.setColor(color);
        glyphPaint.setShadowLayer(glowRadius, 0f, 0f, color);
        canvas.drawCircle(left + size * 0.07f, baseline - size * 0.34f, size * 0.035f, glyphPaint);
        canvas.drawCircle(left + size * 0.07f, baseline - size * 0.66f, size * 0.035f, glyphPaint);
        glyphPaint.clearShadowLayer();
    }

    private void drawDash(Canvas canvas, float left, float baseline, float size, int color, float glowRadius) {
        path.reset();
        path.moveTo(0.14f, 0.50f);
        path.lineTo(0.86f, 0.50f);
        drawPath(canvas, path, left, baseline - size, size * 0.36f, size, color, glowRadius, size * 0.052f);
    }

    private void drawPercent(Canvas canvas, float left, float baseline, float size, int color, float glowRadius) {
        path.reset();
        path.moveTo(0.78f, 0.10f);
        path.lineTo(0.18f, 0.90f);
        drawPath(canvas, path, left, baseline - size, size * 0.70f, size, color, glowRadius, size * 0.042f);

        glyphPaint.reset();
        glyphPaint.setAntiAlias(true);
        glyphPaint.setDither(true);
        glyphPaint.setStyle(Paint.Style.STROKE);
        glyphPaint.setStrokeWidth(size * 0.035f);
        glyphPaint.setColor(color);
        glyphPaint.setShadowLayer(glowRadius, 0f, 0f, color);
        canvas.drawCircle(left + size * 0.16f, baseline - size * 0.72f, size * 0.075f, glyphPaint);
        canvas.drawCircle(left + size * 0.50f, baseline - size * 0.23f, size * 0.075f, glyphPaint);
        glyphPaint.clearShadowLayer();
    }

    private void drawPath(Canvas canvas, Path source, float left, float top, float width, float height,
                          int color, float glowRadius, float strokeWidth) {
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(width, height);
        glyphPaint.reset();
        glyphPaint.setAntiAlias(true);
        glyphPaint.setDither(true);
        glyphPaint.setStyle(Paint.Style.STROKE);
        glyphPaint.setStrokeCap(Paint.Cap.SQUARE);
        glyphPaint.setStrokeJoin(Paint.Join.MITER);
        glyphPaint.setStrokeWidth(strokeWidth / Math.max(1f, height));
        glyphPaint.setColor(color);
        glyphPaint.setShadowLayer(glowRadius / Math.max(1f, height), 0f, 0f, color);
        canvas.drawPath(source, glyphPaint);
        glyphPaint.clearShadowLayer();
        canvas.drawPath(source, glyphPaint);
        canvas.restore();
    }

    private static int applyAlpha(int color, float alphaScale) {
        int alpha = Math.round(Color.alpha(color) * alphaScale);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void buildDigitPath(char c, Path p) {
        // 所有坐标都是 0..1 设计坐标。略带右倾和斜切角，避免数码管味道。
        switch (c) {
            case '0':
                p.moveTo(0.23f, 0.08f);
                p.lineTo(0.79f, 0.08f);
                p.lineTo(0.91f, 0.20f);
                p.lineTo(0.83f, 0.82f);
                p.lineTo(0.68f, 0.92f);
                p.lineTo(0.15f, 0.92f);
                p.lineTo(0.06f, 0.79f);
                p.lineTo(0.15f, 0.20f);
                p.close();
                break;
            case '1':
                p.moveTo(0.28f, 0.23f);
                p.lineTo(0.54f, 0.08f);
                p.lineTo(0.48f, 0.92f);
                p.moveTo(0.25f, 0.92f);
                p.lineTo(0.72f, 0.92f);
                break;
            case '2':
                p.moveTo(0.15f, 0.19f);
                p.lineTo(0.28f, 0.08f);
                p.lineTo(0.78f, 0.08f);
                p.lineTo(0.91f, 0.20f);
                p.lineTo(0.86f, 0.38f);
                p.lineTo(0.15f, 0.92f);
                p.lineTo(0.82f, 0.92f);
                break;
            case '3':
                p.moveTo(0.16f, 0.10f);
                p.lineTo(0.78f, 0.10f);
                p.lineTo(0.90f, 0.23f);
                p.lineTo(0.78f, 0.47f);
                p.lineTo(0.32f, 0.47f);
                p.moveTo(0.78f, 0.47f);
                p.lineTo(0.88f, 0.74f);
                p.lineTo(0.73f, 0.92f);
                p.lineTo(0.12f, 0.92f);
                break;
            case '4':
                p.moveTo(0.78f, 0.08f);
                p.lineTo(0.70f, 0.92f);
                p.moveTo(0.18f, 0.10f);
                p.lineTo(0.10f, 0.50f);
                p.lineTo(0.86f, 0.50f);
                break;
            case '5':
                p.moveTo(0.84f, 0.08f);
                p.lineTo(0.24f, 0.08f);
                p.lineTo(0.13f, 0.47f);
                p.lineTo(0.70f, 0.47f);
                p.lineTo(0.88f, 0.61f);
                p.lineTo(0.79f, 0.82f);
                p.lineTo(0.63f, 0.92f);
                p.lineTo(0.09f, 0.92f);
                break;
            case '6':
                p.moveTo(0.82f, 0.10f);
                p.lineTo(0.29f, 0.10f);
                p.lineTo(0.14f, 0.24f);
                p.lineTo(0.07f, 0.75f);
                p.lineTo(0.20f, 0.92f);
                p.lineTo(0.69f, 0.92f);
                p.lineTo(0.84f, 0.78f);
                p.lineTo(0.78f, 0.58f);
                p.lineTo(0.65f, 0.48f);
                p.lineTo(0.13f, 0.48f);
                break;
            case '7':
                p.moveTo(0.14f, 0.08f);
                p.lineTo(0.87f, 0.08f);
                p.lineTo(0.45f, 0.92f);
                break;
            case '8':
                p.moveTo(0.25f, 0.08f);
                p.lineTo(0.76f, 0.08f);
                p.lineTo(0.88f, 0.20f);
                p.lineTo(0.78f, 0.41f);
                p.lineTo(0.61f, 0.49f);
                p.lineTo(0.80f, 0.58f);
                p.lineTo(0.89f, 0.77f);
                p.lineTo(0.73f, 0.92f);
                p.lineTo(0.18f, 0.92f);
                p.lineTo(0.06f, 0.77f);
                p.lineTo(0.16f, 0.58f);
                p.lineTo(0.34f, 0.49f);
                p.lineTo(0.18f, 0.41f);
                p.lineTo(0.10f, 0.20f);
                p.close();
                p.moveTo(0.18f, 0.49f);
                p.lineTo(0.78f, 0.49f);
                break;
            case '9':
                p.moveTo(0.12f, 0.90f);
                p.lineTo(0.66f, 0.90f);
                p.lineTo(0.81f, 0.76f);
                p.lineTo(0.89f, 0.25f);
                p.lineTo(0.76f, 0.08f);
                p.lineTo(0.27f, 0.08f);
                p.lineTo(0.12f, 0.22f);
                p.lineTo(0.18f, 0.42f);
                p.lineTo(0.31f, 0.52f);
                p.lineTo(0.84f, 0.52f);
                break;
            default:
                break;
        }
    }
}
