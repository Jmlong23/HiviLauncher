package com.hivi.launcher.systemapps.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppDownloadTaskTest {
    @Test
    public void newTaskHasNoFakeProgress() {
        AppDownloadTask task = new AppDownloadTask();
        assertEquals(AppDownloadState.NOT_INSTALLED, task.getState());
        assertEquals(-1, task.getProgress());
    }

    @Test
    public void downloadMovesFromWaitingToDownloadingInstallingAndInstalled() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        assertEquals(AppDownloadState.WAITING, task.getState());
        assertTrue(task.start(generation));
        assertEquals(AppDownloadState.DOWNLOADING, task.getState());
        assertTrue(task.updateProgress(generation, 35L, 100L));
        assertEquals(35, task.getProgress());
        assertTrue(task.beginInstalling(generation));
        assertEquals(AppDownloadState.INSTALLING, task.getState());
        assertFalse(task.pause());
        assertTrue(task.complete(generation));
        assertEquals(AppDownloadState.INSTALLED, task.getState());
        assertFalse(task.complete(generation));
        task.fail(generation);
        assertEquals(AppDownloadState.INSTALLED, task.getState());
    }

    @Test
    public void queuedTaskCanBePausedWithoutStartingNetworkWork() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        assertTrue(task.pause());
        assertFalse(task.start(generation));
        assertEquals(AppDownloadState.PAUSED, task.getState());
    }

    @Test
    public void staleCallbacksCannotOverwritePauseOrResume() {
        AppDownloadTask task = new AppDownloadTask();
        int firstGeneration = task.enqueue();
        task.start(firstGeneration);
        task.updateProgress(firstGeneration, 40L, 100L);
        task.pause();
        assertFalse(task.updateProgress(firstGeneration, 80L, 100L));
        task.fail(firstGeneration);
        assertEquals(AppDownloadState.PAUSED, task.getState());
        assertEquals(40, task.getProgress());
        int resumedGeneration = task.enqueue();
        assertTrue(task.start(resumedGeneration));
        assertFalse(task.beginInstalling(firstGeneration));
        task.complete(firstGeneration);
        assertEquals(AppDownloadState.DOWNLOADING, task.getState());
        assertTrue(task.updateProgress(resumedGeneration, 60L, 100L));
        assertEquals(60, task.getProgress());
    }

    @Test
    public void repeatedTapsDoNotEnqueueDuplicateDownloads() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        assertEquals(-1, task.enqueue());
        task.start(generation);
        assertEquals(-1, task.enqueue());
        task.beginInstalling(generation);
        assertEquals(-1, task.enqueue());
        task.complete(generation);
        assertEquals(-1, task.enqueue());
    }

    @Test
    public void failedTaskCanBeRetried() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        task.start(generation);
        task.fail(generation);
        assertEquals(AppDownloadState.FAILED, task.getState());
        int retryGeneration = task.enqueue();
        assertTrue(retryGeneration > generation);
        assertTrue(task.start(retryGeneration));
    }

    @Test
    public void partialDownloadCanBeRestoredAfterProcessRestart() {
        AppDownloadTask task = new AppDownloadTask();
        task.restorePaused(250L, 1000L);
        assertEquals(AppDownloadState.PAUSED, task.getState());
        assertEquals(25, task.getProgress());
        assertTrue(task.start(task.enqueue()));
    }

    @Test
    public void unknownLengthIsIndeterminateAndPercentageIsClamped() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        task.start(generation);
        task.updateProgress(generation, 10L, -1L);
        assertEquals(-1, task.getProgress());
        task.updateProgress(generation, 10L, 0L);
        assertEquals(-1, task.getProgress());
        task.updateProgress(generation, -10L, 100L);
        assertEquals(0, task.getProgress());
        task.updateProgress(generation, 200L, 100L);
        assertEquals(100, task.getProgress());
    }

    @Test
    public void externalInstallInvalidatesOldDownloadAndUninstallRestoresDownloadAction() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        task.start(generation);
        task.reconcileInstalled(true);
        task.fail(generation);
        assertEquals(AppDownloadState.INSTALLED, task.getState());
        task.reconcileInstalled(false);
        assertEquals(AppDownloadState.NOT_INSTALLED, task.getState());
        assertEquals(-1, task.getProgress());
    }

    @Test
    public void packageBroadcastDoesNotInvalidateAnActiveInstallation() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        task.start(generation);
        task.beginInstalling(generation);
        task.reconcileInstalled(true);
        assertEquals(AppDownloadState.INSTALLING, task.getState());
        assertTrue(task.isCurrent(generation));
        assertTrue(task.complete(generation));
        assertEquals(AppDownloadState.INSTALLED, task.getState());
    }

    @Test
    public void installationFailureIsDistinctAndCanBeRetried() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        task.start(generation);
        task.beginInstalling(generation);
        task.fail(generation);
        assertEquals(AppDownloadState.INSTALL_FAILED, task.getState());
        int retryGeneration = task.enqueue();
        assertTrue(retryGeneration > generation);
        assertTrue(task.start(retryGeneration));
        assertEquals(AppDownloadState.DOWNLOADING, task.getState());
    }

    @Test
    public void resumedTaskStaysWaitingUntilTheExecutorStartsItsNewGeneration() {
        AppDownloadTask task = new AppDownloadTask();
        int firstGeneration = task.enqueue();
        task.start(firstGeneration);
        task.updateProgress(firstGeneration, 40L, 100L);
        task.pause();
        int resumedGeneration = task.enqueue();
        assertEquals(AppDownloadState.WAITING, task.getState());
        assertEquals(40, task.getProgress());
        assertFalse(task.start(firstGeneration));
        assertFalse(task.updateProgress(firstGeneration, 100L, 100L));
        assertTrue(task.start(resumedGeneration));
        assertEquals(AppDownloadState.DOWNLOADING, task.getState());
    }

    @Test
    public void uninstallingBlocksDownloadsPauseAndRepeatedUninstall() {
        AppDownloadTask task = new AppDownloadTask();
        task.reconcileInstalled(true);
        int generation = task.beginUninstalling();

        assertTrue(generation >= 0);
        assertEquals(AppDownloadState.UNINSTALLING, task.getState());
        assertEquals(-1, task.enqueue());
        assertEquals(-1, task.beginUninstalling());
        assertFalse(task.pause());
        assertFalse(task.start(generation));
        assertFalse(task.beginInstalling(generation));
        assertFalse(task.complete(generation));
    }

    @Test
    public void packageBroadcastsAndReloadsPreserveUninstallingUntilResult() {
        AppDownloadTask task = new AppDownloadTask();
        task.reconcileInstalled(true);
        int generation = task.beginUninstalling();

        task.reconcileInstalled(true);
        task.reconcileInstalled(false);

        assertEquals(AppDownloadState.UNINSTALLING, task.getState());
        assertTrue(task.isCurrent(generation));
        assertTrue(task.finishUninstalling(generation, true));
        assertEquals(AppDownloadState.NOT_INSTALLED, task.getState());
    }

    @Test
    public void uninstallSuccessClearsProgressAndAllowsFreshDownload() {
        AppDownloadTask task = new AppDownloadTask();
        int downloadGeneration = task.enqueue();
        task.start(downloadGeneration);
        task.updateProgress(downloadGeneration, 100L, 100L);
        task.beginInstalling(downloadGeneration);
        task.complete(downloadGeneration);

        int uninstallGeneration = task.beginUninstalling();
        assertTrue(task.finishUninstalling(uninstallGeneration, true));
        assertEquals(AppDownloadState.NOT_INSTALLED, task.getState());
        assertEquals(-1, task.getProgress());
        assertFalse(task.isCurrent(downloadGeneration));
        assertFalse(task.isCurrent(uninstallGeneration));
        assertTrue(task.start(task.enqueue()));
        assertEquals(AppDownloadState.DOWNLOADING, task.getState());
    }

    @Test
    public void uninstallFailureRestoresInstalledAndAllowsRetry() {
        AppDownloadTask task = new AppDownloadTask();
        task.reconcileInstalled(true);
        int firstGeneration = task.beginUninstalling();

        assertTrue(task.finishUninstalling(firstGeneration, false));
        assertEquals(AppDownloadState.INSTALLED, task.getState());
        assertEquals(-1, task.enqueue());
        int retryGeneration = task.beginUninstalling();
        assertTrue(retryGeneration > firstGeneration);
        assertFalse(task.finishUninstalling(firstGeneration, true));
        assertEquals(AppDownloadState.UNINSTALLING, task.getState());
        assertTrue(task.finishUninstalling(retryGeneration, true));
    }

    @Test
    public void duplicateAndStaleResultsCannotChangeFinishedUninstall() {
        AppDownloadTask task = new AppDownloadTask();
        task.reconcileInstalled(true);
        int generation = task.beginUninstalling();

        assertFalse(task.finishUninstalling(generation - 1, true));
        assertEquals(AppDownloadState.UNINSTALLING, task.getState());
        assertTrue(task.finishUninstalling(generation, true));
        assertFalse(task.finishUninstalling(generation, false));
        assertEquals(AppDownloadState.NOT_INSTALLED, task.getState());
    }

    @Test
    public void onlyInstalledAppsCanBeginUninstalling() {
        AppDownloadTask task = new AppDownloadTask();
        assertEquals(-1, task.beginUninstalling());
        int generation = task.enqueue();
        assertEquals(-1, task.beginUninstalling());
        task.start(generation);
        assertEquals(-1, task.beginUninstalling());
        task.pause();
        assertEquals(-1, task.beginUninstalling());
        generation = task.enqueue();
        task.start(generation);
        task.fail(generation);
        assertEquals(-1, task.beginUninstalling());
        generation = task.enqueue();
        task.start(generation);
        task.beginInstalling(generation);
        assertEquals(-1, task.beginUninstalling());
        task.fail(generation);
        assertEquals(-1, task.beginUninstalling());
        assertFalse(task.finishUninstalling(generation, true));
    }

    @Test
    public void staleDownloadCallbacksCannotOverwriteUninstallState() {
        AppDownloadTask task = new AppDownloadTask();
        int generation = task.enqueue();
        task.start(generation);
        task.beginInstalling(generation);
        task.complete(generation);
        int uninstallGeneration = task.beginUninstalling();

        assertFalse(task.updateProgress(generation, 100L, 100L));
        assertFalse(task.complete(generation));
        task.fail(generation);
        assertEquals(AppDownloadState.UNINSTALLING, task.getState());
        assertTrue(task.isCurrent(uninstallGeneration));
    }
}
