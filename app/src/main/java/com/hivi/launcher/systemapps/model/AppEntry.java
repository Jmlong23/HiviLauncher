package com.hivi.launcher.systemapps.model;

import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

public class AppEntry {
    private final String mLabel;
    private final String mPackageName;
    private final String[] mCandidatePackages;
    private final String mActivityName;
    private final Drawable mIcon;
    private final boolean mInstalled;
    private final String mDownloadUrl;

    /** 已安装应用：由 PackageManager 查询结果构造。 */
    public AppEntry(String label, String packageName, String activityName, Drawable icon) {
        this(label, packageName, new String[]{packageName}, activityName, icon, true, null);
    }

    /** 预置未安装应用：由预置目录构造，展示占位图标并支持点击下载安装。 */
    public AppEntry(String label, String[] candidatePackages, Drawable icon, String downloadUrl) {
        this(label, candidatePackages[0], candidatePackages, null, icon, false, downloadUrl);
    }

    private AppEntry(String label, String packageName, String[] candidatePackages,
            String activityName, Drawable icon, boolean installed, String downloadUrl) {
        mLabel = label;
        mPackageName = packageName;
        mCandidatePackages = candidatePackages;
        mActivityName = activityName;
        mIcon = icon;
        mInstalled = installed;
        mDownloadUrl = downloadUrl;
    }

    public String getLabel() {
        return mLabel;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String[] getCandidatePackages() {
        return mCandidatePackages;
    }

    public String getActivityName() {
        return mActivityName;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    @Nullable
    public String getDownloadUrl() {
        return mDownloadUrl;
    }
}
