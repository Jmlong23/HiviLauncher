package com.hivi.launcher.systemapps.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.model.PresetAppInstaller;
import com.hivi.launcher.systemapps.model.PresetMusicApps;
import com.hivi.launcher.systemapps.ui.SystemAppsView;
import com.hivi.launcher.utils.log.AppLog;
import com.hivi.launcher.wifi.model.BootAutoPlaySuppressor;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SystemAppsPresenter extends BasePresenter<SystemAppsView>
        implements PresetAppInstaller.Listener {
    private static final String TAG = "SystemAppsPresenter";

    private final Context mContext;
    private final PresetAppInstaller mPresetAppInstaller;
    private AppEntry mInstallingApp;
    private boolean mInstalling;

    public SystemAppsPresenter(Context context, SystemAppsView view) {
        super(view);
        mContext = context.getApplicationContext();
        mPresetAppInstaller = new PresetAppInstaller(mContext);
    }

    public void loadApps() {
        PackageManager pm = mContext.getPackageManager();
        List<AppEntry> apps = loadLaunchableApps(pm);
        apps.addAll(loadMissingPresetApps(apps));
        sortApps(apps);
        SystemAppsView view = getView();
        if (view == null) {
            return;
        }
        if (apps.isEmpty()) {
            view.showToast(mContext.getString(R.string.system_apps_empty));
        }
        view.showApps(apps);
    }

    public void launchApp(AppEntry app) {
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
        if (!app.isInstalled()) {
            downloadAndInstall(app);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.getPackageName(), app.getActivityName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mContext.startActivity(intent);
            SystemAppsView view = getView();
            if (view != null) {
                view.closePage();
            }
        } catch (ActivityNotFoundException e) {
            SystemAppsView view = getView();
            if (view != null) {
                view.showToast(mContext.getString(R.string.system_apps_launch_failed, app.getLabel()));
            }
        }
    }

    private void downloadAndInstall(AppEntry app) {
        SystemAppsView view = getView();
        if (mInstalling) {
            return;
        }
        if (TextUtils.isEmpty(app.getDownloadUrl())) {
            if (view != null) {
                view.showToast(mContext.getString(
                        R.string.system_apps_download_unavailable, app.getLabel()));
            }
            return;
        }
        mInstalling = true;
        mInstallingApp = app;
        if (view != null) {
            view.showInstallProgress(mContext.getString(
                    R.string.system_apps_downloading_preparing, app.getLabel()));
        }
        mPresetAppInstaller.downloadAndInstall(app.getDownloadUrl(),
                Arrays.asList(app.getCandidatePackages()), this);
    }

    private List<AppEntry> loadLaunchableApps(PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        List<AppEntry> apps = new ArrayList<>();

        for (ResolveInfo info : activities) {
            if (info.activityInfo == null || TextUtils.isEmpty(info.activityInfo.packageName)
                    || TextUtils.isEmpty(info.activityInfo.name)) {
                continue;
            }
            String label = String.valueOf(info.loadLabel(pm));
            if (TextUtils.isEmpty(label)) {
                label = info.activityInfo.packageName;
            }
            apps.add(new AppEntry(label, info.activityInfo.packageName, info.activityInfo.name,
                    info.loadIcon(pm)));
        }
        return apps;
    }

    /**
     * 收集预置音乐服务中未安装的项，与已安装应用合并展示；已安装的候选包名会让
     * 对应预置项被跳过，列表中由真实应用替代占位。
     */
    private List<AppEntry> loadMissingPresetApps(List<AppEntry> installedApps) {
        Set<String> installedPackages = new HashSet<>();
        for (AppEntry entry : installedApps) {
            installedPackages.add(entry.getPackageName());
        }
        List<AppEntry> missing = new ArrayList<>();
        for (PresetMusicApps preset : PresetMusicApps.values()) {
            if (isAnyPackageInstalled(installedPackages, preset.getCandidatePackages())) {
                continue;
            }
            String label = mContext.getString(preset.getLabelResId());
            Drawable icon = mContext.getDrawable(preset.getIconResId());
            missing.add(new AppEntry(label, preset.getCandidatePackages(), icon,
                    preset.getDownloadUrl()));
        }
        return missing;
    }

    private boolean isAnyPackageInstalled(Set<String> installedPackages, String[] candidates) {
        for (String packageName : candidates) {
            if (installedPackages.contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void sortApps(List<AppEntry> apps) {
        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry left, AppEntry right) {
                return collator.compare(left.getLabel(), right.getLabel());
            }
        });
    }

    @Override
    public void onDownloadProgress(int percent) {
        SystemAppsView view = getView();
        if (view == null || mInstallingApp == null) {
            return;
        }
        view.showInstallProgress(mContext.getString(
                R.string.system_apps_downloading, mInstallingApp.getLabel(), percent));
    }

    @Override
    public void onInstallStarted() {
        SystemAppsView view = getView();
        if (view == null || mInstallingApp == null) {
            return;
        }
        view.showInstallProgress(mContext.getString(
                R.string.system_apps_installing, mInstallingApp.getLabel()));
    }

    @Override
    public void onInstalled(String packageName) {
        AppLog.i(TAG, "Preset app installed: " + packageName);
        mInstalling = false;
        SystemAppsView view = getView();
        if (view != null) {
            view.hideInstallProgress();
        }
        loadApps();
        launchInstalledApp(packageName);
    }

    @Override
    public void onDownloadFailed() {
        finishInstallWithError(R.string.system_apps_download_failed);
    }

    @Override
    public void onInstallFailed() {
        finishInstallWithError(R.string.system_apps_install_failed);
    }

    private void finishInstallWithError(int messageResId) {
        mInstalling = false;
        SystemAppsView view = getView();
        if (view == null) {
            return;
        }
        view.hideInstallProgress();
        view.showToast(mContext.getString(messageResId));
    }

    private void launchInstalledApp(String packageName) {
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mContext.startActivity(intent);
            SystemAppsView view = getView();
            if (view != null) {
                view.closePage();
            }
        } catch (RuntimeException e) {
            AppLog.w(TAG, "Unable to launch freshly installed app: " + packageName);
        }
    }
}
