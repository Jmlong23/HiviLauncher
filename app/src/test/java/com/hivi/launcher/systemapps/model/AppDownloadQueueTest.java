package com.hivi.launcher.systemapps.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class AppDownloadQueueTest {
    private FakeBackend mBackend;
    private AppDownloadQueue mQueue;
    private AppEntry mFirstApp;
    private AppEntry mSecondApp;

    @Before
    public void setUp() {
        mBackend = new FakeBackend();
        mQueue = new AppDownloadQueue(mBackend, new AppDownloadQueue.Listener() {
            @Override
            public void onChanged(AppEntry app, AppDownloadStatus status) {
            }

            @Override
            public void onInstalled(String packageName) {
            }

            @Override
            public void onFailed(boolean installPhase) {
            }
        });
        mFirstApp = app("first");
        mSecondApp = app("second");
    }

    @Test
    public void uninstalledAppShowsDownloadIconStateThenPauses() {
        mQueue.toggle(mFirstApp);
        assertEquals(AppDownloadStatus.State.DOWNLOADING,
                mQueue.getStatus(mFirstApp).getState());

        mQueue.toggle(mFirstApp);
        assertEquals(AppDownloadStatus.State.PAUSED,
                mQueue.getStatus(mFirstApp).getState());
    }

    @Test
    public void secondAppWaitsUntilFirstInstallCompletes() {
        mQueue.toggle(mFirstApp);
        mQueue.toggle(mSecondApp);
        assertEquals(AppDownloadStatus.State.WAITING,
                mQueue.getStatus(mSecondApp).getState());

        mBackend.callback.onInstalled("first");
        assertEquals(AppDownloadStatus.State.DOWNLOADING,
                mQueue.getStatus(mSecondApp).getState());
    }

    @Test
    public void downloadProgressIsPreservedWhenPausedAndResumed() {
        mQueue.toggle(mFirstApp);
        mBackend.callback.onDownloadProgress(47);
        mQueue.toggle(mFirstApp);
        assertEquals(47, mQueue.getStatus(mFirstApp).getProgress());
        mBackend.callback.onDownloadProgress(52);
        assertEquals(47, mQueue.getStatus(mFirstApp).getProgress());

        mBackend.callback.onPaused();
        mQueue.toggle(mFirstApp);
        assertEquals(AppDownloadStatus.State.DOWNLOADING,
                mQueue.getStatus(mFirstApp).getState());
        assertEquals(47, mQueue.getStatus(mFirstApp).getProgress());
    }

    @Test
    public void installationCannotBePausedAndBlocksNextDownload() {
        mQueue.toggle(mFirstApp);
        mQueue.toggle(mSecondApp);
        AppDownloadQueue.Callback callback = mBackend.callback;
        callback.onInstallStarted();
        mQueue.toggle(mFirstApp);

        assertEquals(AppDownloadStatus.State.INSTALLING,
                mQueue.getStatus(mFirstApp).getState());
        assertEquals(100, mQueue.getStatus(mFirstApp).getProgress());
        assertEquals(AppDownloadStatus.State.WAITING,
                mQueue.getStatus(mSecondApp).getState());
        assertSame(callback, mBackend.callback);
    }

    @Test
    public void resumeBeforePauseCallbackDoesNotStartOverlappingDownloads() {
        mQueue.toggle(mFirstApp);
        mBackend.callback.onDownloadProgress(38);
        AppDownloadQueue.Callback callback = mBackend.callback;
        mQueue.toggle(mFirstApp);
        mQueue.toggle(mFirstApp);

        assertEquals(AppDownloadStatus.State.WAITING,
                mQueue.getStatus(mFirstApp).getState());
        assertSame(callback, mBackend.callback);
        callback.onPaused();

        assertEquals(AppDownloadStatus.State.DOWNLOADING,
                mQueue.getStatus(mFirstApp).getState());
        assertEquals(38, mQueue.getStatus(mFirstApp).getProgress());
        callback.onFailed(false);
        assertEquals(AppDownloadStatus.State.DOWNLOADING,
                mQueue.getStatus(mFirstApp).getState());
    }

    @Test
    public void failedInstallShowsInstallFailureAndAdvancesQueue() {
        mQueue.toggle(mFirstApp);
        mQueue.toggle(mSecondApp);
        mBackend.callback.onInstallStarted();
        mBackend.callback.onFailed(true);

        assertEquals(AppDownloadStatus.State.INSTALL_FAILED,
                mQueue.getStatus(mFirstApp).getState());
        assertEquals(AppDownloadStatus.State.DOWNLOADING,
                mQueue.getStatus(mSecondApp).getState());
    }

    @Test
    public void waitingAppCanBePausedWithoutInterruptingActiveDownload() {
        mQueue.toggle(mFirstApp);
        AppDownloadQueue.Callback callback = mBackend.callback;
        mQueue.toggle(mSecondApp);
        mQueue.toggle(mSecondApp);
        callback.onInstalled("first");

        assertEquals(AppDownloadStatus.State.PAUSED,
                mQueue.getStatus(mSecondApp).getState());
        assertSame(callback, mBackend.callback);
    }

    private static AppEntry app(String packageName) {
        return new AppEntry(packageName, new String[]{packageName}, null, "https://example.com/app.apk",
                true);
    }

    private static final class FakeBackend implements AppDownloadQueue.Backend {
        private AppDownloadQueue.Callback callback;

        @Override
        public void start(AppEntry app, AppDownloadQueue.Callback callback) {
            this.callback = callback;
        }

        @Override
        public boolean pause() {
            return true;
        }
    }
}
