package com.hivi.launcher.settings.model;

import android.content.Context;
import android.content.SharedPreferences;

public final class ScreenSaverSettings {
    private static final String PREFERENCES_NAME = "screen_saver_settings";
    private static final String KEY_TIMEOUT = "timeout";
    private static final String KEY_STYLE = "style";

    private ScreenSaverSettings() {
    }

    public static int getTimeout(Context context) {
        return preferences(context).getInt(KEY_TIMEOUT,
                SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER);
    }

    public static int getStyle(Context context) {
        return preferences(context).getInt(KEY_STYLE,
                SettingsModel.SCREEN_SAVER_STYLE_SIMPLE);
    }

    public static void setTimeout(Context context, int timeout) {
        preferences(context).edit().putInt(KEY_TIMEOUT, timeout).apply();
    }

    public static void setStyle(Context context, int style) {
        preferences(context).edit().putInt(KEY_STYLE, style).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
