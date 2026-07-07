package com.hivi.launcher.systemapps.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.ui.SystemAppsView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SystemAppsPresenter extends BasePresenter<SystemAppsView> {
    private final Context mContext;

    public SystemAppsPresenter(Context context, SystemAppsView view) {
        super(view);
        mContext = context.getApplicationContext();
    }

    public void loadApps() {
        List<AppEntry> apps = loadLaunchableApps();
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

    private List<AppEntry> loadLaunchableApps() {
        PackageManager pm = mContext.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        final Collator collator = Collator.getInstance(Locale.getDefault());
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

        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry left, AppEntry right) {
                return collator.compare(left.getLabel(), right.getLabel());
            }
        });
        return apps;
    }
}
