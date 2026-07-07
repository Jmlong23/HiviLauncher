package com.hivi.launcher.base;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

public abstract class BasePresenter<V extends BaseView> {
    protected final String TAG = getClass().getSimpleName();

    private WeakReference<V> mAttachedView;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public BasePresenter(V view) {
        attach(view);
    }

    public void attach(V view) {
        mAttachedView = new WeakReference<>(view);
    }

    public void detach() {
        mMainHandler.removeCallbacksAndMessages(null);
        if (mAttachedView != null) {
            mAttachedView.clear();
            mAttachedView = null;
        }
    }

    public V getView() {
        return mAttachedView == null ? null : mAttachedView.get();
    }

    public boolean isViewAttached() {
        return getView() != null;
    }

    public void runOnUiThread(Runnable action) {
        mMainHandler.post(action);
    }

    public void runOnUiThreadDelayed(Runnable action, long delayMillis) {
        mMainHandler.postDelayed(action, delayMillis);
    }

    public void removeUiThreadRunnable(Runnable action) {
        mMainHandler.removeCallbacks(action);
    }
}
