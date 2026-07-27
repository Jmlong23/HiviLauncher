package com.hivi.launcher.settings.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.settings.model.SettingsModel;
import com.hivi.launcher.settings.ui.SettingsView;

public final class SettingsPresenter extends BasePresenter<SettingsView> {
    private final SettingsModel mModel = new SettingsModel();
    public static final int SECTION_NETWORK = 0;
    public static final int SECTION_DISPLAY = 1;
    public static final int SECTION_SYSTEM = 2;
    public static final int SECTION_ABOUT = 3;
    public static final int SECTION_MAINTENANCE = 4;

    public SettingsPresenter(SettingsView view) {
        super(view);
    }

    public void init() {
        renderSelectedSection();
    }

    public void onSectionSelected(int section) {
        if (!isValidSection(section) || mModel.getSelectedSection() == section) {
            return;
        }
        mModel.setSelectedSection(section);
        renderSelectedSection();
    }

    private void renderSelectedSection() {
        SettingsView view = getView();
        if (view != null) {
            view.renderSettingsSection(mModel.getSelectedSection());
        }
    }

    private boolean isValidSection(int section) {
        return section >= SECTION_NETWORK && section <= SECTION_MAINTENANCE;
    }
}
