package com.hivi.launcher.systemapps.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.systemapps.model.AppEntry;

import java.util.List;

public interface SystemAppsView extends BaseView {
    void showApps(List<AppEntry> apps);

    void closePage();
}
