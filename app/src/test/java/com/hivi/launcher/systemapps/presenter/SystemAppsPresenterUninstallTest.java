package com.hivi.launcher.systemapps.presenter;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import com.hivi.launcher.systemapps.model.AppDownloadStatus;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.ui.SystemAppsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class SystemAppsPresenterUninstallTest {
    private RecordingView mView;
    private RecordingPresenter mPresenter;
    private AppEntry mApp;

    @Before
    public void setUp() {
        mView = new RecordingView();
        mPresenter = new RecordingPresenter(new TestContext(), mView);
        mApp = installedApp("first", true);
    }

    @After
    public void tearDown() {
        mPresenter.detach();
    }

    @Test
    public void uninstallImmediatelyPublishesBusyStateBeforeRequest() {
        mPresenter.uninstallApp(mApp);

        assertSame(mApp, mView.mChangedApp);
        assertEquals(AppDownloadStatus.State.UNINSTALLING, mView.mStatus.getState());
        assertEquals(-1, mView.mStatus.getProgress());
        assertEquals(AppDownloadStatus.State.UNINSTALLING, mPresenter.mStateAtRequest);
        assertEquals(1, mPresenter.mRequestCount);
    }

    @Test
    public void reloadedEntryKeepsUninstallingState() {
        mPresenter.uninstallApp(mApp);

        assertEquals(AppDownloadStatus.State.UNINSTALLING,
                mPresenter.getDownloadStatus(installedApp("first", true)).getState());
        assertEquals(AppDownloadStatus.State.INSTALLED,
                mPresenter.getDownloadStatus(installedApp("second", true)).getState());
    }

    @Test
    public void uninstallBlocksLaunchLongPressAndDuplicateRequests() {
        mPresenter.uninstallApp(mApp);

        mPresenter.launchApp(mApp);
        mPresenter.onAppLongPressed(mApp);
        mPresenter.uninstallApp(mApp);
        mPresenter.uninstallApp(installedApp("second", true));

        assertNull(mView.mActionsApp);
        assertEquals(0, mView.mCloseCount);
        assertEquals(1, mPresenter.mRequestCount);
    }

    @Test
    public void downloadStatusCallbackCannotOverwriteUninstallingState() {
        mPresenter.uninstallApp(mApp);

        mPresenter.onChanged(mApp, new AppDownloadStatus(AppDownloadStatus.State.INSTALLED, 100));

        assertEquals(AppDownloadStatus.State.UNINSTALLING, mView.mStatus.getState());
    }

    @Test
    public void successReloadsAppsAndClearsBusyState() {
        mPresenter.uninstallApp(mApp);

        mPresenter.notifyUninstallResult(mApp, true);

        assertEquals(1, mPresenter.mLoadCount);
        assertEquals(1, mView.mToastCount);
        assertEquals(AppDownloadStatus.State.INSTALLED,
                mPresenter.getDownloadStatus(mApp).getState());
        mPresenter.onAppLongPressed(mApp);
        assertSame(mApp, mView.mActionsApp);
    }

    @Test
    public void failureRestoresInstalledStateAndAllowsRetry() {
        mPresenter.uninstallApp(mApp);

        mPresenter.notifyUninstallResult(mApp, false);

        assertEquals(AppDownloadStatus.State.INSTALLED, mView.mStatus.getState());
        assertEquals(100, mView.mStatus.getProgress());
        assertEquals(1, mView.mToastCount);
        mPresenter.uninstallApp(mApp);
        assertEquals(2, mPresenter.mRequestCount);
    }

    @Test
    public void protectedAndUninstalledAppsCannotBeUninstalled() {
        mPresenter.uninstallApp(installedApp("protected", false));
        mPresenter.uninstallApp(new AppEntry("missing", new String[]{"missing"}, null,
                "https://example.com/app.apk", true));

        assertEquals(0, mPresenter.mRequestCount);
        assertNull(mView.mStatus);
    }

    @Test
    public void resultAfterDetachDoesNotTouchView() {
        mPresenter.uninstallApp(mApp);
        mPresenter.detach();

        mPresenter.notifyUninstallResult(mApp, true);

        assertEquals(0, mPresenter.mLoadCount);
        assertEquals(0, mView.mToastCount);
    }

    private static AppEntry installedApp(String packageName, boolean uninstallable) {
        return new AppEntry(packageName, packageName, packageName + ".MainActivity", null,
                uninstallable);
    }

    private static final class TestContext extends ContextWrapper {
        private final Resources mResources = new Resources(null, null, null);

        private TestContext() {
            super(null);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public Resources getResources() {
            return mResources;
        }
    }

    private static final class RecordingPresenter extends SystemAppsPresenter {
        private int mRequestCount;
        private int mLoadCount;
        private AppDownloadStatus.State mStateAtRequest;

        private RecordingPresenter(Context context, SystemAppsView view) {
            super(context, view);
        }

        @Override
        void requestUninstall(AppEntry app) {
            mStateAtRequest = getDownloadStatus(app).getState();
            mRequestCount++;
        }

        @Override
        public void loadApps() {
            mLoadCount++;
        }
    }

    private static final class RecordingView implements SystemAppsView {
        private AppEntry mChangedApp;
        private AppEntry mActionsApp;
        private AppDownloadStatus mStatus;
        private int mCloseCount;
        private int mToastCount;

        @Override
        public void showApps(List<AppEntry> apps) {
        }

        @Override
        public void showDownloadStatus(AppEntry app, AppDownloadStatus status) {
            mChangedApp = app;
            mStatus = status;
        }

        @Override
        public void showAppActions(AppEntry app) {
            mActionsApp = app;
        }

        @Override
        public void closePage() {
            mCloseCount++;
        }

        @Override
        public void showToast(String message) {
            mToastCount++;
        }
    }
}
