package com.hivi.launcher.settings.ui;

import com.hivi.launcher.base.BaseView;

public interface SettingsView extends BaseView {
    void renderSettingsSection(int section);

    void renderDisplaySettings(int language, boolean languageOptionsExpanded,
            boolean screenSaverEnabled, int screenSaverTimeout,
            boolean screenSaverTimeoutOptionsExpanded);
}
