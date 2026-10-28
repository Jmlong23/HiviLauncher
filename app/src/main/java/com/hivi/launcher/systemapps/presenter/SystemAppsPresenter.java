package com.hivi.launcher.systemapps.presenter;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
 * 应用菜单固定展示 9 个音乐/音频应用：已安装的显示真实图标并可直接启动，
 * 未安装的显示占位图标与“未安装”角标，点击后按 APP类型 从公司服务器下载并静默安装。
 */
public class SystemAppsPresenter extends BasePresenter<SystemAppsView>
        implements PresetAppInstaller.Listener {
    private static final String TAG = "SystemAppsPresenter";

    private final Context mContext;
    private final PresetAppInstaller mPresetAppInstaller;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private AppEntry mInstallingApp;
    private boolean mInstalling;

    /** 上一次长按弹菜单的应用，供卸载完成后刷新列表定位。 */
    private AppEntry mUninstallingApp;

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

    /** 长按应用图标：已安装的弹出“卸载应用/应用信息”菜单（受保护应用仅有“应用信息”），其余不响应。 */
    public void onAppLongPressed(AppEntry app) {
        if (mInstalling || !app.isInstalled()) {
            return;
        }
        SystemAppsView view = getView();
        if (view != null) {
            view.showAppActions(app);
        }
    }

    /**
     * 静默卸载。用框架 PackageInstaller API（platform 签名 priv-app + DELETE_PACKAGES 权限），
     * 结果经广播异步回调；API 27 以下的老系统回退 pm 命令。完成后刷新列表。
     */
    public void uninstallApp(final AppEntry app) {
        if (mInstalling || mUninstallingApp != null || !app.isUninstallable()) {
            return;
        }
        mUninstallingApp = app;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            packageInstallerUninstall(app);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = executeShellUninstall(app.getPackageName());
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyUninstallResult(app, success);
                    }
                });
            }
        }, "PresetAppUninstall").start();
    }

    private void packageInstallerUninstall(final AppEntry app) {
        final String action = mContext.getPackageName() + ".systemapps.UNINSTALL_RESULT";
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                try {
                    mContext.unregisterReceiver(this);
                } catch (IllegalArgumentException ignored) {
                }
                AppLog.i(TAG, "Preset app uninstall " + app.getPackageName()
                        + ": status=" + status + ", message=" + message);
                notifyUninstallResult(app, status == PackageInstaller.STATUS_SUCCESS);
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(action));
        try {
            Intent callback = new Intent(action).setPackage(mContext.getPackageName());
            // FLAG_MUTABLE（字面值 1<<25）：系统要往回调 Intent 填 EXTRA_STATUS，不可 IMMUTABLE；
            // 该常量 API 31 才加入而 compileSdk 为 30，故用字面值。
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, callback, 1 << 25);
            mContext.getPackageManager().getPackageInstaller()
                    .uninstall(app.getPackageName(), pendingIntent.getIntentSender());
        } catch (Exception e) {
            AppLog.w(TAG, "Preset app uninstall request failed: " + app.getPackageName() + ", " + e);
            try {
                mContext.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
            mUninstallingApp = null;
            SystemAppsView view = getView();
            if (view != null) {
                view.showToast(mContext.getString(R.string.system_apps_uninstall_failed));
            }
        }
    }

    private void notifyUninstallResult(AppEntry app, final boolean success) {
        mUninstallingApp = null;
        SystemAppsView view = getView();
        if (view == null) {
            return;
        }
        if (success) {
            view.showToast(mContext.getString(R.string.system_apps_uninstalled, app.getLabel()));
            loadApps();
        } else {
            view.showToast(mContext.getString(R.string.system_apps_uninstall_failed));
        }
    }

    /** API 27 以下老系统的兜底：pm 命令卸载。 */
    private boolean executeShellUninstall(String packageName) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = new ProcessBuilder("pm", "uninstall", "--user", "0", packageName)
                    .redirectErrorStream(true)
                    .start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();
            boolean success = exitCode == 0 && output.toString().contains("Success");
            AppLog.i(TAG, "Preset app shell uninstall " + packageName + ": exitCode=" + exitCode
                    + ", output=" + output);
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            AppLog.w(TAG, "Preset app shell uninstall failed: " + packageName + ", " + e);
            return false;
        }
    }

    /** 跳转系统“应用信息”详情页；设备无该界面时提示。 */
    public void openAppInfo(AppEntry app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mContext.startActivity(intent);
        } catch (RuntimeException e) {
            SystemAppsView view = getView();
            if (view != null) {
                view.showToast(mContext.getString(R.string.system_apps_open_info_failed));
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

    /** 按枚举声明顺序生成固定 9 个应用条目，展示顺序与预设顺序一致。 */
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
                    preset.getCandidatePackages(), icon, preset.getDownloadUrl(mContext),
                    preset.isUninstallable()));
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
                        packageName, component.getClassName(), icon, preset.isUninstallable());
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
