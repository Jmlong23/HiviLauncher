package com.hivi.launcher.systemapps.model;

public final class AppDownloadTask {
    private AppDownloadState mState = AppDownloadState.NOT_INSTALLED;
    private long mDownloadedBytes;
    private long mTotalBytes = -1L;
    private int mGeneration;

    public synchronized AppDownloadState getState() {
        return mState;
    }

    public synchronized int getProgress() {
        return mTotalBytes > 0L
                ? (int) Math.min(100L, mDownloadedBytes * 100L / mTotalBytes) : -1;
    }

    public synchronized int enqueue() {
        if (mState == AppDownloadState.WAITING || mState == AppDownloadState.DOWNLOADING
                || mState == AppDownloadState.INSTALLING || mState == AppDownloadState.INSTALLED) {
            return -1;
        }
        mState = AppDownloadState.WAITING;
        return ++mGeneration;
    }

    public synchronized boolean start(int generation) {
        if (!isCurrent(generation) || mState != AppDownloadState.WAITING) {
            return false;
        }
        mState = AppDownloadState.DOWNLOADING;
        return true;
    }

    public synchronized boolean pause() {
        if (mState != AppDownloadState.WAITING && mState != AppDownloadState.DOWNLOADING) {
            return false;
        }
        mState = AppDownloadState.PAUSED;
        ++mGeneration;
        return true;
    }

    public synchronized boolean updateProgress(int generation, long downloaded, long total) {
        if (!isCurrent(generation) || mState != AppDownloadState.DOWNLOADING) {
            return false;
        }
        mDownloadedBytes = Math.max(0L, downloaded);
        mTotalBytes = total;
        return true;
    }

    public synchronized boolean beginInstalling(int generation) {
        if (!isCurrent(generation) || mState != AppDownloadState.DOWNLOADING) {
            return false;
        }
        mState = AppDownloadState.INSTALLING;
        return true;
    }

    public synchronized boolean complete(int generation) {
        if (isCurrent(generation) && mState == AppDownloadState.INSTALLING) {
            mState = AppDownloadState.INSTALLED;
            return true;
        }
        return false;
    }

    public synchronized void fail(int generation) {
        if (isCurrent(generation) && (mState == AppDownloadState.DOWNLOADING
                || mState == AppDownloadState.INSTALLING)) {
            mState = mState == AppDownloadState.INSTALLING
                    ? AppDownloadState.INSTALL_FAILED : AppDownloadState.FAILED;
        }
    }

    public synchronized boolean isCurrent(int generation) {
        return generation == mGeneration;
    }

    public synchronized void restorePaused(long downloaded, long total) {
        mDownloadedBytes = Math.max(0L, downloaded);
        mTotalBytes = total;
        mState = AppDownloadState.PAUSED;
        ++mGeneration;
    }

    public synchronized void reconcileInstalled(boolean installed) {
        if (installed && mState != AppDownloadState.INSTALLED
                && mState != AppDownloadState.INSTALLING) {
            mState = AppDownloadState.INSTALLED;
            ++mGeneration;
        } else if (!installed && mState == AppDownloadState.INSTALLED) {
            mState = AppDownloadState.NOT_INSTALLED;
            mDownloadedBytes = 0L;
            mTotalBytes = -1L;
            ++mGeneration;
        }
    }
}
