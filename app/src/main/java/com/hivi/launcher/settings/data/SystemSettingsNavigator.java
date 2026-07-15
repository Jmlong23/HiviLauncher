package com.hivi.launcher.settings.data;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Product-level entry points for Android settings pages.
 *
 * <p>The launcher currently delegates settings UI to the preloaded Android system. Keeping these
 * intents in the settings feature prevents the home page from depending directly on platform
 * settings actions and leaves a single replacement point for future in-app settings pages.</p>
 */
public final class SystemSettingsNavigator {
    private final Context mContext;

    public SystemSettingsNavigator(Context context) {
        mContext = context.getApplicationContext();
    }

    public boolean openSystemSettings() {
        return start(Settings.ACTION_SETTINGS);
    }

    public boolean openScreensaverSettings() {
        return start(Settings.ACTION_DREAM_SETTINGS);
    }

    private boolean start(String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        }
    }
}
