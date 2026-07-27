package com.hivi.launcher.settings.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.settings.model.SettingsModel;
import com.hivi.launcher.settings.ui.SettingsView;

public final class SettingsPresenter extends BasePresenter<SettingsView> {
    private final SettingsModel mModel = new SettingsModel();

    public SettingsPresenter(SettingsView view) {
        super(view);
    }
}
