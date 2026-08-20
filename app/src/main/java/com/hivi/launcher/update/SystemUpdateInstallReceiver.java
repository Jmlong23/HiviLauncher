package com.hivi.launcher.update;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.utils.log.AppLog;

/**
 * Reopens the launcher from the replacement package after a system-installer update.
 */
public final class SystemUpdateInstallReceiver extends BroadcastReceiver {
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
        Intent launchIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(context, MainActivity.class))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        launchIntent.putExtra(EXTRA_UPDATE_SUCCEEDED, true);
        context.startActivity(launchIntent);
    }
}
