package com.hivi.launcher.systemapps.model;

import android.graphics.drawable.Drawable;

public class AppEntry {
    private final String label;
    private final String packageName;
    private final String activityName;
    private final Drawable icon;

    public AppEntry(String label, String packageName, String activityName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
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
