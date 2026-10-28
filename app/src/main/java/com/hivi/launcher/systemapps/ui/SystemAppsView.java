package com.hivi.launcher.systemapps.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.systemapps.model.AppEntry;

import java.util.List;

public interface SystemAppsView extends BaseView {
    void showApps(List<AppEntry> apps);

    /** 长按已安装应用弹出的操作菜单（卸载应用 / 应用信息）。 */
    void showAppActions(AppEntry app);

    /** 显示全屏下载/安装进度遮罩，message 为完整状态文案（如“QQ音乐正在下载(50%)”）。 */
    void showInstallProgress(String message);

    void hideInstallProgress();

    void closePage();
}
