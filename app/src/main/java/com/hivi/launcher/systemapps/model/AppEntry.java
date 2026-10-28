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
    private final boolean mUninstallable;

    /** 已安装应用：由 PackageManager 查询结果构造。 */
    public AppEntry(String label, String packageName, String activityName, Drawable icon) {
        this(label, packageName, new String[]{packageName}, activityName, icon, true, null, true);
    }

    /** 已安装的预置应用：uninstallable=false 表示受保护应用，长按菜单不提供卸载。 */
    public AppEntry(String label, String packageName, String activityName, Drawable icon,
            boolean uninstallable) {
        this(label, packageName, new String[]{packageName}, activityName, icon, true, null,
                uninstallable);
    }

    /** 预置未安装应用：由预置目录构造，展示占位图标并支持点击下载安装。 */
    public AppEntry(String label, String[] candidatePackages, Drawable icon, String downloadUrl,
            boolean uninstallable) {
        this(label, candidatePackages[0], candidatePackages, null, icon, false, downloadUrl,
                uninstallable);
    }

    private AppEntry(String label, String packageName, String[] candidatePackages,
            String activityName, Drawable icon, boolean installed, String downloadUrl,
            boolean uninstallable) {
        mLabel = label;
        mPackageName = packageName;
        mCandidatePackages = candidatePackages;
        mActivityName = activityName;
        mIcon = icon;
        mInstalled = installed;
        mDownloadUrl = downloadUrl;
        mUninstallable = uninstallable;
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

    /** false 表示受保护的预置应用（如 QQ音乐车机版），长按菜单不提供卸载。 */
    public boolean isUninstallable() {
        return mUninstallable;
    }

    @Nullable
    public String getDownloadUrl() {
        return mDownloadUrl;
    }
}
