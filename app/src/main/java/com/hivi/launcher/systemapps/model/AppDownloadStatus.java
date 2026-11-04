package com.hivi.launcher.systemapps.model;

public final class AppDownloadStatus {
    public enum State {
        NOT_DOWNLOADED,
        WAITING,
        DOWNLOADING,
        PAUSED,
        INSTALLING,
        UNINSTALLING,
        DOWNLOAD_FAILED,
        INSTALL_FAILED,
        INSTALLED
    }

    private final State mState;
    private final int mProgress;

    public AppDownloadStatus(State state, int progress) {
        mState = state;
        mProgress = Math.max(-1, Math.min(100, progress));
    }

    public State getState() {
        return mState;
    }

    public int getProgress() {
        return mProgress;
    }
}
