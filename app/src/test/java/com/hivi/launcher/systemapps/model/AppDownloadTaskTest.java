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
        task.complete(generation);
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
}
