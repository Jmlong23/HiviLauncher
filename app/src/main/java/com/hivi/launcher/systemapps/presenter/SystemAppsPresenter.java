package com.hivi.launcher.systemapps.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.model.AppDownloadManager;
import com.hivi.launcher.systemapps.model.AppDownloadState;
import com.hivi.launcher.systemapps.model.AppDownloadTask;
import com.hivi.launcher.systemapps.model.SupportedApp;
import com.hivi.launcher.systemapps.ui.SystemAppsView;
import com.hivi.launcher.wifi.model.BootAutoPlaySuppressor;

import java.util.ArrayList;
import java.util.List;

public class SystemAppsPresenter extends BasePresenter<SystemAppsView> {
    private final Context mContext;
    private final AppDownloadManager mDownloadManager;
    private final AppDownloadManager.Listener mDownloadListener = this::updateApp;

    public SystemAppsPresenter(Context context, SystemAppsView view) {
        super(view);
        mContext = context.getApplicationContext();
        mDownloadManager = AppDownloadManager.getInstance(mContext);
        mDownloadManager.addListener(mDownloadListener);
    }

    public void loadApps() {
        SystemAppsView view = getView();
        if (view == null) {
            return;
        }
        List<AppEntry> apps = new ArrayList<>();
        for (SupportedApp app : SupportedApp.values()) {
            apps.add(createEntry(app));
        }
        view.showApps(apps);
    }

    @Override
    public void detach() {
        mDownloadManager.removeListener(mDownloadListener);
        super.detach();
    }

    public void onAppClicked(AppEntry app) {
        AppDownloadState state = mDownloadManager.getTask(app.getSupportedApp()).getState();
        if (state == AppDownloadState.INSTALLED) {
            launchApp(createEntry(app.getSupportedApp()));
        } else if (state == AppDownloadState.DOWNLOADING || state == AppDownloadState.WAITING) {
            mDownloadManager.pause(app.getSupportedApp());
        } else if (state != AppDownloadState.INSTALLING
                && !mDownloadManager.enqueue(app.getSupportedApp())) {
            SystemAppsView view = getView();
            if (view != null) {
                view.showToast(mContext.getString(R.string.system_app_download_unavailable,
                        app.getLabel()));
            }
        }
    }

    public void launchApp(AppEntry app) {
        if (app.getPackageName().isEmpty() || app.getActivityName().isEmpty()) {
            loadApps();
            return;
        }
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
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
        } catch (ActivityNotFoundException | SecurityException e) {
            SystemAppsView view = getView();
            if (view != null) {
                view.showToast(mContext.getString(R.string.system_apps_launch_failed, app.getLabel()));
            }
            loadApps();
        }
    }

    private AppEntry createEntry(SupportedApp app) {
        PackageManager pm = mContext.getPackageManager();
        Intent launchIntent = null;
        for (String packageName : app.getPackageNames()) {
            launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent != null && launchIntent.getComponent() != null) {
                break;
            }
        }
        boolean installed = launchIntent != null && launchIntent.getComponent() != null;
        AppDownloadTask task = mDownloadManager.getTask(app);
        task.reconcileInstalled(installed);
        Drawable icon = mContext.getDrawable(app.getIconResId());
        String packageName = "";
        String activityName = "";
        if (installed) {
            packageName = launchIntent.getComponent().getPackageName();
            activityName = launchIntent.getComponent().getClassName();
        }
        return new AppEntry(app, mContext.getString(app.getLabelResId()), packageName,
                activityName, icon, task.getState(), task.getProgress());
    }

    private void updateApp(SupportedApp app) {
        SystemAppsView view = getView();
        if (view != null) {
            view.updateApp(createEntry(app));
        }
    }
}
