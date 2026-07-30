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
        renderDisplaySettings();
    }

    public void onSectionSelected(int section) {
        if (!isValidSection(section)) {
            return;
        }
        boolean sectionChanged = mModel.getSelectedSection() != section;
        dismissDisplayDropdowns();
        if (!sectionChanged) {
            renderDisplaySettings();
            return;
        }
        mModel.setSelectedSection(section);
        renderSelectedSection();
        renderDisplaySettings();
    }

    public void onLanguageSelected() {
        boolean expanded = !mModel.isLanguageOptionsExpanded();
        mModel.setLanguageOptionsExpanded(expanded);
        if (expanded) {
            mModel.setScreenSaverTimeoutOptionsExpanded(false);
        }
        renderDisplaySettings();
    }

    public void onLanguageOptionSelected(int language) {
        if (!isValidLanguage(language)) {
            return;
        }
        mModel.setLanguage(language);
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    public void onScreenSaverToggled() {
        boolean enabled = !mModel.isScreenSaverEnabled();
        mModel.setScreenSaverEnabled(enabled);
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    public void onScreenSaverTimeoutSelected() {
        if (!mModel.isScreenSaverEnabled()) {
            return;
        }
        boolean expanded = !mModel.isScreenSaverTimeoutOptionsExpanded();
        mModel.setScreenSaverTimeoutOptionsExpanded(expanded);
        if (expanded) {
            mModel.setLanguageOptionsExpanded(false);
        }
        renderDisplaySettings();
    }

    public void onScreenSaverTimeoutOptionSelected(int timeout) {
        if (!mModel.isScreenSaverEnabled() || !isValidScreenSaverTimeout(timeout)) {
            return;
        }
        mModel.setScreenSaverTimeout(timeout);
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    public void onDisplayDropdownDismissed() {
        if (!mModel.isLanguageOptionsExpanded()
                && !mModel.isScreenSaverTimeoutOptionsExpanded()) {
            return;
        }
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    private void renderSelectedSection() {
        SettingsView view = getView();
        if (view != null) {
            view.renderSettingsSection(mModel.getSelectedSection());
        }
    }

    private void renderDisplaySettings() {
        SettingsView view = getView();
        if (view != null) {
            view.renderDisplaySettings(mModel.getLanguage(), mModel.isLanguageOptionsExpanded(),
                    mModel.isScreenSaverEnabled(), mModel.getScreenSaverTimeout(),
                    mModel.isScreenSaverTimeoutOptionsExpanded());
        }
    }

    private void dismissDisplayDropdowns() {
        mModel.setLanguageOptionsExpanded(false);
        mModel.setScreenSaverTimeoutOptionsExpanded(false);
    }

    private boolean isValidSection(int section) {
        return section >= SECTION_NETWORK && section <= SECTION_MAINTENANCE;
    }

    private boolean isValidLanguage(int language) {
        return language == SettingsModel.LANGUAGE_CHINESE
                || language == SettingsModel.LANGUAGE_ENGLISH;
    }

    private boolean isValidScreenSaverTimeout(int timeout) {
        return timeout >= SettingsModel.SCREEN_SAVER_TIMEOUT_ONE_MINUTE
                && timeout <= SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER;
    }
}
