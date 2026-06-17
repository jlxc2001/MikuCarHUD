package com.jlxc.mikucarhudreceiver;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    public static final String PREFS_NAME = "miku_car_hud_receiver";
    public static final String KEY_PORT = "listen_port";
    public static final String KEY_MIRROR = "hud_mirror";
    public static final String KEY_FONT_SCALE = "font_scale";
    public static final String KEY_BRIGHTNESS = "brightness";

    public static final int DEFAULT_PORT = 36970;
    public static final boolean DEFAULT_MIRROR = false;
    public static final int DEFAULT_FONT_SCALE = 100;
    public static final int DEFAULT_BRIGHTNESS = 100;

    private AppPrefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getPort(Context context) {
        int port = get(context).getInt(KEY_PORT, DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            return DEFAULT_PORT;
        }
        return port;
    }

    public static boolean getMirror(Context context) {
        return get(context).getBoolean(KEY_MIRROR, DEFAULT_MIRROR);
    }

    public static int getFontScale(Context context) {
        int scale = get(context).getInt(KEY_FONT_SCALE, DEFAULT_FONT_SCALE);
        if (scale < 60) return 60;
        if (scale > 180) return 180;
        return scale;
    }

    public static int getBrightness(Context context) {
        int brightness = get(context).getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS);
        if (brightness < 10) return 10;
        if (brightness > 100) return 100;
        return brightness;
    }
}
