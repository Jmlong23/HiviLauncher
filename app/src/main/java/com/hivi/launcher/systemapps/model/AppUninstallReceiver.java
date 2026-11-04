package com.hivi.launcher.systemapps.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import com.hivi.launcher.utils.log.AppLog;

public final class AppUninstallReceiver extends BroadcastReceiver {
    static final String ACTION_UNINSTALL_RESULT = "com.hivi.launcher.systemapps.UNINSTALL_RESULT";
    static final String EXTRA_APP = "uninstall_app";
    static final String EXTRA_GENERATION = "uninstall_generation";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_UNINSTALL_RESULT.equals(intent.getAction())) {
            return;
        }
        String appName = intent.getStringExtra(EXTRA_APP);
        if (appName == null) {
            return;
        }
        SupportedApp app;
        try {
            app = SupportedApp.valueOf(appName);
        } catch (IllegalArgumentException exception) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        AppLog.i("AppUninstallReceiver", "App uninstall " + app.name() + ": status=" + status
                + ", message=" + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        AppDownloadManager.getInstance(context).onUninstallResult(app,
                intent.getIntExtra(EXTRA_GENERATION, -1), status == PackageInstaller.STATUS_SUCCESS);
    }
}
