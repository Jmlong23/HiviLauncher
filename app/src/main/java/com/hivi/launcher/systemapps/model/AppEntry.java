package com.hivi.launcher.systemapps.model;

import android.graphics.drawable.Drawable;

public class AppEntry {
    private final SupportedApp supportedApp;
    private final String label;
    private final String packageName;
    private final String activityName;
    private final Drawable icon;
    private final AppDownloadState downloadState;
    private final int downloadProgress;

    public AppEntry(SupportedApp supportedApp, String label, String packageName,
            String activityName, Drawable icon, AppDownloadState downloadState,
            int downloadProgress) {
        this.supportedApp = supportedApp;
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
        this.downloadState = downloadState;
        this.downloadProgress = downloadProgress;
    }

    public SupportedApp getSupportedApp() {
        return supportedApp;
    }

    public AppDownloadState getDownloadState() {
        return downloadState;
    }

    public int getDownloadProgress() {
        return downloadProgress;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getActivityName() {
        return activityName;
    }

    public Drawable getIcon() {
        return icon;
    }
}
