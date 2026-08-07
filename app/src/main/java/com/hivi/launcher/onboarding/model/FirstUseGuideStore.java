package com.hivi.launcher.onboarding.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Persists completion of the first-use guide in device-protected storage so the launcher can
 * make its startup decision before the user unlocks the device.
 */
public final class FirstUseGuideStore {
    private static final String PREFERENCES_NAME = "first_use_guide";
    private static final String KEY_COMPLETED = "completed";

    private FirstUseGuideStore() {
    }

    public static boolean isCompleted(Context context) {
        return getPreferences(context).getBoolean(KEY_COMPLETED, false);
    }

    public static void markCompleted(Context context) {
        getPreferences(context).edit().putBoolean(KEY_COMPLETED, true).commit();
    }

    private static SharedPreferences getPreferences(Context context) {
        Context applicationContext = context.getApplicationContext();
        Context preferencesContext = applicationContext == null ? context : applicationContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            preferencesContext = preferencesContext.createDeviceProtectedStorageContext();
        }
        return preferencesContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
