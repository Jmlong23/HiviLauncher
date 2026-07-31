package com.hivi.launcher.settings.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.update.SystemUpdateInfo;

public interface SettingsView extends BaseView {
    void renderSettingsSection(int section);

    void renderDisplaySettings(int language, boolean languageOptionsExpanded,
            boolean screenSaverEnabled, int screenSaverTimeout,
            boolean screenSaverTimeoutOptionsExpanded);

    void renderSystemUpdate(SystemUpdateInfo updateInfo, boolean updateInProgress);

    void showSystemUpdateConfirmation(SystemUpdateInfo updateInfo);

    void showSystemUpdateProgress(int progress, String status);

    void dismissSystemUpdateProgress();
}
