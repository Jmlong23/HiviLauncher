package com.hivi.launcher.settings.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.hivi.launcher.R;

public final class ScreenSaverSettings {
    private static final String PREFERENCES_NAME = "screen_saver_settings";
    private static final String KEY_TIMEOUT = "timeout";
    private static final String KEY_STYLE = "style";
    private static final String KEY_SIMPLE_WALLPAPER = "simple_wallpaper";

    private ScreenSaverSettings() {
    }

    public static int getTimeout(Context context) {
        return preferences(context).getInt(KEY_TIMEOUT,
                SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER);
    }

    public static int getStyle(Context context) {
        int style = preferences(context).getInt(KEY_STYLE,
                SettingsModel.SCREEN_SAVER_STYLE_SIMPLE);
        return style == 1 ? SettingsModel.SCREEN_SAVER_STYLE_SIMPLE : style;
    }

    public static void setTimeout(Context context, int timeout) {
        preferences(context).edit().putInt(KEY_TIMEOUT, timeout).apply();
    }

    public static void setStyle(Context context, int style) {
        preferences(context).edit().putInt(KEY_STYLE, style).apply();
    }

    public static int getSimpleWallpaper(Context context) {
        return preferences(context).getInt(KEY_SIMPLE_WALLPAPER, 0);
    }

    public static void setSimpleWallpaper(Context context, int wallpaper) {
        preferences(context).edit().putInt(KEY_SIMPLE_WALLPAPER, wallpaper).apply();
    }

    public static int getSimpleWallpaperResource(int wallpaper) {
        switch (wallpaper) {
            case 1:
                return R.drawable.img_simple_clock_style2;
            case 2:
                return R.drawable.img_simple_clock_style3;
            case 3:
                return R.drawable.img_simple_clock_style4;
            case 4:
                return R.drawable.img_simple_clock_style5;
            case 5:
                return R.drawable.img_simple_clock_style6;
            case 6:
                return R.drawable.img_simple_clock_style7;
            case 0:
            default:
                return R.drawable.img_simple_clock_style1;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
