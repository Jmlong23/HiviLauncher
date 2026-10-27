package com.hivi.launcher.systemapps.presenter;

import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.model.PresetAppInstaller;
import com.hivi.launcher.systemapps.model.PresetMusicApps;
import com.hivi.launcher.systemapps.ui.SystemAppsView;
import com.hivi.launcher.utils.log.AppLog;
import com.hivi.launcher.wifi.model.BootAutoPlaySuppressor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 应用菜单固定展示 8 个音乐/音频应用：已安装的显示真实图标并可直接启动，
 * 未安装的显示占位图标与“未安装”角标，点击后下载并静默安装。
 */
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
        List<AppEntry> apps = loadPresetApps(mContext.getPackageManager());
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

    /** 按枚举声明顺序生成固定 8 个应用条目，展示顺序与预设顺序一致。 */
    private List<AppEntry> loadPresetApps(PackageManager pm) {
        List<AppEntry> apps = new ArrayList<>();
        for (PresetMusicApps preset : PresetMusicApps.values()) {
            AppEntry installed = findInstalledEntry(pm, preset);
            if (installed != null) {
                apps.add(installed);
                continue;
            }
            Drawable icon = mContext.getDrawable(preset.getIconResId());
            apps.add(new AppEntry(mContext.getString(preset.getLabelResId()),
                    preset.getCandidatePackages(), icon, preset.getDownloadUrl()));
        }
        return apps;
    }

    /** 查找候选包名中已安装且可启动的应用；命中则返回真实图标条目。 */
    @Nullable
    private AppEntry findInstalledEntry(PackageManager pm, PresetMusicApps preset) {
        for (String packageName : preset.getCandidatePackages()) {
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            ComponentName component = launchIntent == null ? null : launchIntent.getComponent();
            if (component == null || TextUtils.isEmpty(component.getClassName())) {
                continue;
            }
            try {
                Drawable icon = pm.getActivityIcon(component);
                return new AppEntry(mContext.getString(preset.getLabelResId()),
                        packageName, component.getClassName(), icon);
            } catch (PackageManager.NameNotFoundException e) {
                AppLog.w(TAG, "Installed preset app icon unavailable: " + packageName);
            }
        }
        return null;
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
