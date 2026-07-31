package com.hivi.launcher.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.text.TextUtils;

/**
 * Receives PackageInstaller's terminal update result and brings the launcher to the foreground.
 */
public final class SystemUpdateInstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_STATUS =
            "com.hivi.launcher.action.SYSTEM_UPDATE_INSTALL_STATUS";
    public static final String EXTRA_UPDATE_SUCCEEDED =
            "com.hivi.launcher.extra.SYSTEM_UPDATE_SUCCEEDED";
    public static final String EXTRA_UPDATE_ERROR =
            "com.hivi.launcher.extra.SYSTEM_UPDATE_ERROR";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TextUtils.equals(ACTION_INSTALL_STATUS, intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(
                context.getPackageName());
        if (launchIntent == null) {
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launchIntent.putExtra(EXTRA_UPDATE_SUCCEEDED, status == PackageInstaller.STATUS_SUCCESS);
        if (status != PackageInstaller.STATUS_SUCCESS) {
            launchIntent.putExtra(EXTRA_UPDATE_ERROR, TextUtils.isEmpty(statusMessage)
                    ? "Package installer status " + status : statusMessage);
        }
        context.startActivity(launchIntent);
    }
}
