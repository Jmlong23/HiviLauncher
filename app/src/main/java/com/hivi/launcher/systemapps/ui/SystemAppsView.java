package com.hivi.launcher.systemapps.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.model.AppDownloadStatus;

import java.util.List;

public interface SystemAppsView extends BaseView {
    void showApps(List<AppEntry> apps);

    void showDownloadStatus(AppEntry app, AppDownloadStatus status);

    /** 长按已安装应用弹出的操作菜单（卸载应用 / 应用信息）。 */
    void showAppActions(AppEntry app);

    void closePage();
}
