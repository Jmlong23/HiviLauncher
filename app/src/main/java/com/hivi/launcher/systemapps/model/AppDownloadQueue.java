package com.hivi.launcher.systemapps.model;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import static com.hivi.launcher.systemapps.model.AppDownloadStatus.State;

public final class AppDownloadQueue {
    public interface Backend {
        void start(AppEntry app, Callback callback);

        boolean pause();
    }

    public interface Callback {
        void onDownloadProgress(int percent);

        void onInstallStarted();

        void onInstalled(String packageName);

        void onPaused();

        void onFailed(boolean installPhase);
    }

    public interface Listener {
        void onChanged(AppEntry app, AppDownloadStatus status);

        void onInstalled(String packageName);

        void onFailed(boolean installPhase);
    }

    private final Backend mBackend;
    private final Listener mListener;
    private final ArrayDeque<AppEntry> mPendingApps = new ArrayDeque<>();
    private final Map<String, AppDownloadStatus> mStatuses = new HashMap<>();
    private AppEntry mActiveApp;
    private long mGeneration;

    public AppDownloadQueue(Backend backend, Listener listener) {
        mBackend = backend;
        mListener = listener;
    }

    public AppDownloadStatus getStatus(AppEntry app) {
        if (app.isInstalled()) {
            return new AppDownloadStatus(State.INSTALLED, 100);
        }
        AppDownloadStatus status = mStatuses.get(app.getPackageName());
        return status == null ? new AppDownloadStatus(State.NOT_DOWNLOADED, -1) : status;
    }

    public void toggle(AppEntry app) {
        AppDownloadStatus status = getStatus(app);
        switch (status.getState()) {
            case INSTALLED:
            case INSTALLING:
                return;
            case DOWNLOADING:
                if (mBackend.pause()) {
                    update(app, State.PAUSED, status.getProgress());
                }
                return;
            case WAITING:
                mPendingApps.removeIf(pending -> pending.getPackageName()
                        .equals(app.getPackageName()));
                update(app, State.PAUSED, status.getProgress());
                return;
            default:
                mPendingApps.addLast(app);
                update(app, State.WAITING, status.getState() == State.PAUSED
                        ? status.getProgress() : -1);
                startNext();
        }
    }

    private void update(AppEntry app, State state, int progress) {
        AppDownloadStatus status = new AppDownloadStatus(state, progress);
        mStatuses.put(app.getPackageName(), status);
        mListener.onChanged(app, status);
    }

    private void startNext() {
        if (mActiveApp != null || mPendingApps.isEmpty()) {
            return;
        }
        final AppEntry app = mPendingApps.removeFirst();
        mActiveApp = app;
        final long generation = ++mGeneration;
        update(app, State.DOWNLOADING, getStatus(app).getProgress());
        Callback callback = new Callback() {
            private boolean isCurrent() {
                return mActiveApp == app && mGeneration == generation;
            }

            @Override
            public void onDownloadProgress(int percent) {
                if (isCurrent() && getStatus(app).getState() == State.DOWNLOADING) {
                    update(app, State.DOWNLOADING, percent);
                }
            }

            @Override
            public void onInstallStarted() {
                if (isCurrent()) {
                    update(app, State.INSTALLING, 100);
                }
            }

            @Override
            public void onInstalled(String packageName) {
                if (!isCurrent()) {
                    return;
                }
                update(app, State.INSTALLED, 100);
                mStatuses.remove(app.getPackageName());
                mActiveApp = null;
                mListener.onInstalled(packageName);
                startNext();
            }

            @Override
            public void onPaused() {
                if (!isCurrent()) {
                    return;
                }
                mActiveApp = null;
                startNext();
            }

            @Override
            public void onFailed(boolean installPhase) {
                if (!isCurrent()) {
                    return;
                }
                mActiveApp = null;
                update(app, installPhase ? State.INSTALL_FAILED : State.DOWNLOAD_FAILED, -1);
                mListener.onFailed(installPhase);
                startNext();
            }
        };
        try {
            mBackend.start(app, callback);
        } catch (RuntimeException exception) {
            callback.onFailed(false);
        }
    }
}
