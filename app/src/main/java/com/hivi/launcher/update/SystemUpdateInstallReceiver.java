package com.hivi.launcher.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.utils.log.AppLog;

/**
 * Reopens the launcher from the replacement package after an in-place update.
 */
public final class SystemUpdateInstallReceiver extends BroadcastReceiver {
    private static final String PREFERENCES_NAME = "system_update_result";
    private static final String KEY_UPDATE_SUCCEEDED = "update_succeeded";
    private static final String TAG = "SystemUpdateReceiver";
    public static final String EXTRA_UPDATE_SUCCEEDED =
            "com.hivi.launcher.extra.SYSTEM_UPDATE_SUCCEEDED";
    public static final String EXTRA_UPDATE_ERROR =
            "com.hivi.launcher.extra.SYSTEM_UPDATE_ERROR";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !TextUtils.equals(Intent.ACTION_MY_PACKAGE_REPLACED, intent.getAction())) {
            return;
        }
        AppLog.i(TAG, "Launcher package replaced; starting the updated home screen.");
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UPDATE_SUCCEEDED, true)
                .commit();
        Intent launchIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launchIntent.putExtra(EXTRA_UPDATE_SUCCEEDED, true);
        context.startActivity(launchIntent);
    }

    public static boolean consumeUpdateSucceeded(Context context) {
        if (context == null) {
            return false;
        }
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean succeeded = preferences.getBoolean(KEY_UPDATE_SUCCEEDED, false);
        if (succeeded) {
            preferences.edit().remove(KEY_UPDATE_SUCCEEDED).apply();
        }
        return succeeded;
    }
}
