package com.hivi.launcher.settings.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.update.SystemUpdateInfo;

import java.io.File;

public interface SettingsView extends BaseView {
    void renderSettingsSection(int section);

    void renderDisplaySettings(int language, boolean languageOptionsExpanded,
            int screenSaverTimeout,
            boolean screenSaverTimeoutOptionsExpanded, int screenSaverStyle);

    void renderSystemUpdate(SystemUpdateInfo updateInfo, boolean updateInProgress);

    void showSystemUpdateConfirmation(SystemUpdateInfo updateInfo);

    void showSystemUpdateProgress(int progress, String status);

    void dismissSystemUpdateProgress();

    void launchSystemUpdateInstaller(File packageFile);

    void showLogUploadConfirmation();

    void showLogUploadProgress(int progress, String status);

    void showLogUploadSuccess();

    void showLogUploadFailure();

    void showFactoryResetConfirmation();

    void showFactoryResetFinalConfirmation();

    void showFactoryResetProgress(int progress, String status);

    void showFactoryResetSuccess();

    void showFactoryResetFailure();
}
