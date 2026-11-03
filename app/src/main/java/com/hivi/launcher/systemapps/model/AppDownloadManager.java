package com.hivi.launcher.systemapps.model;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AppDownloadManager implements AppDownloadQueue.Listener {
    private static AppDownloadManager sInstance;

    private final Set<AppDownloadQueue.Listener> mListeners = new LinkedHashSet<>();
    private final AppDownloadQueue mQueue;

    public static synchronized AppDownloadManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppDownloadManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private AppDownloadManager(Context context) {
        mQueue = new AppDownloadQueue(new PresetAppInstaller(context), this);
    }

    public void addListener(AppDownloadQueue.Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(AppDownloadQueue.Listener listener) {
        mListeners.remove(listener);
    }

    public AppDownloadStatus getStatus(AppEntry app) {
        return mQueue.getStatus(app);
    }

    public void toggle(AppEntry app) {
        mQueue.toggle(app);
    }

    @Override
    public void onChanged(AppEntry app, AppDownloadStatus status) {
        for (AppDownloadQueue.Listener listener : new ArrayList<>(mListeners)) {
            listener.onChanged(app, status);
        }
    }

    @Override
    public void onInstalled(String packageName) {
        for (AppDownloadQueue.Listener listener : new ArrayList<>(mListeners)) {
            listener.onInstalled(packageName);
        }
    }

    @Override
    public void onFailed(boolean installPhase) {
        for (AppDownloadQueue.Listener listener : new ArrayList<>(mListeners)) {
            listener.onFailed(installPhase);
        }
    }
}
