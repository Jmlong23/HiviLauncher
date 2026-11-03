package com.hivi.launcher.systemapps.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.systemapps.model.AppDownloadStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppDownloadPresentationTest {
    @Test
    public void notDownloadedShowsCloudOverDimmedBrandIcon() {
        AppDownloadPresentation presentation = presentation(AppDownloadStatus.State.NOT_DOWNLOADED);
        assertTrue(presentation.showsDownloadIcon());
        assertFalse(presentation.showsProgress());
        assertTrue(presentation.isDimmed());
        assertEquals(0, presentation.getLabelResId());
    }

    @Test
    public void downloadingShowsProgressInsteadOfCloud() {
        AppDownloadPresentation presentation = presentation(AppDownloadStatus.State.DOWNLOADING);
        assertFalse(presentation.showsDownloadIcon());
        assertTrue(presentation.showsProgress());
        assertEquals(R.string.system_apps_status_loading, presentation.getLabelResId());
    }

    @Test
    public void installationNeverShowsDownloadCloud() {
        AppDownloadPresentation presentation = presentation(AppDownloadStatus.State.INSTALLING);
        assertFalse(presentation.showsDownloadIcon());
        assertTrue(presentation.showsProgress());
        assertEquals(R.string.system_apps_status_installing, presentation.getLabelResId());
    }

    @Test
    public void pausedDownloadKeepsProgressAndUsesPausedLabel() {
        AppDownloadPresentation presentation = presentation(AppDownloadStatus.State.PAUSED);
        assertTrue(presentation.showsProgress());
        assertFalse(presentation.showsDownloadIcon());
        assertEquals(R.string.system_apps_status_paused, presentation.getLabelResId());
    }

    @Test
    public void waitingDownloadHasNoMisleadingProgressOrCloud() {
        AppDownloadPresentation presentation = presentation(AppDownloadStatus.State.WAITING);
        assertFalse(presentation.showsProgress());
        assertFalse(presentation.showsDownloadIcon());
        assertEquals(R.string.system_apps_status_waiting, presentation.getLabelResId());
    }

    @Test
    public void installFailureIsDistinctFromDownloadFailure() {
        assertEquals(R.string.system_apps_status_install_failed,
                presentation(AppDownloadStatus.State.INSTALL_FAILED).getLabelResId());
        assertEquals(R.string.system_apps_status_download_failed_short,
                presentation(AppDownloadStatus.State.DOWNLOAD_FAILED).getLabelResId());
    }

    @Test
    public void installedAppHasNoOverlayAndUsesItsName() {
        AppDownloadPresentation presentation = presentation(AppDownloadStatus.State.INSTALLED);
        assertFalse(presentation.isDimmed());
        assertFalse(presentation.showsProgress());
        assertFalse(presentation.showsDownloadIcon());
        assertEquals(0, presentation.getLabelResId());
    }

    private static AppDownloadPresentation presentation(AppDownloadStatus.State state) {
        return new AppDownloadPresentation(new AppDownloadStatus(state, 42));
    }
}
