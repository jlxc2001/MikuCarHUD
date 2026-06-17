package com.jlxc.mikucarhudreceiver;

import android.content.Context;
import android.graphics.Typeface;

/**
 * HUD 数字字体加载器。
 *
 * 优先从 APK assets/fonts/hud_oem.ttf 加载硬朗窄体字体；
 * 如果本地没有字体文件，则回退到系统字体，保证旧安卓设备也能运行。
 */
public final class HudFont {
    private static final String ASSET_FONT_PATH = "fonts/hud_oem.ttf";
    private static Typeface cachedNumberTypeface;
    private static boolean loadedFromAsset = false;

    private HudFont() {}

    public static Typeface getNumberTypeface(Context context) {
        if (cachedNumberTypeface != null) {
            return cachedNumberTypeface;
        }
        try {
            cachedNumberTypeface = Typeface.createFromAsset(context.getAssets(), ASSET_FONT_PATH);
            loadedFromAsset = true;
        } catch (Throwable ignored) {
            cachedNumberTypeface = Typeface.create("sans-serif-condensed", Typeface.BOLD);
            loadedFromAsset = false;
        }
        return cachedNumberTypeface;
    }

    public static boolean isLoadedFromAsset() {
        return loadedFromAsset;
    }

    public static String getAssetFontPath() {
        return ASSET_FONT_PATH;
    }
}
