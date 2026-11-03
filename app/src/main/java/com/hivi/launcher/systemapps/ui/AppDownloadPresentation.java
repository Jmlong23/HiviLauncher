package com.hivi.launcher.systemapps.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.systemapps.model.AppDownloadStatus;

public final class AppDownloadPresentation {
    private final AppDownloadStatus mStatus;

    public AppDownloadPresentation(AppDownloadStatus status) {
        mStatus = status;
    }

    public boolean showsDownloadIcon() {
        switch (mStatus.getState()) {
            case NOT_DOWNLOADED:
            case DOWNLOAD_FAILED:
            case INSTALL_FAILED:
                return true;
            default:
                return false;
        }
    }

    public boolean showsProgress() {
        switch (mStatus.getState()) {
            case DOWNLOADING:
            case PAUSED:
            case INSTALLING:
                return true;
            default:
                return false;
        }
    }

    public boolean isDimmed() {
        return mStatus.getState() != AppDownloadStatus.State.INSTALLED;
    }

    public int getLabelResId() {
        switch (mStatus.getState()) {
            case WAITING:
                return R.string.system_apps_status_waiting;
            case DOWNLOADING:
                return R.string.system_apps_status_loading;
            case PAUSED:
                return R.string.system_apps_status_paused;
            case INSTALLING:
                return R.string.system_apps_status_installing;
            case DOWNLOAD_FAILED:
                return R.string.system_apps_status_download_failed_short;
            case INSTALL_FAILED:
                return R.string.system_apps_status_install_failed;
            default:
                return 0;
        }
    }
}
