package com.hivi.launcher.audio;

import androidx.annotation.Nullable;

/**
 * Per-page subscription to {@link PlaybackSpectrumSource} that drops frames after detach.
 */
public final class SpectrumSubscription {
    public interface Listener {
        void onFftData(byte[] fftData);
    }

    private final PcmSpectrumAnalyzer.Listener mForwarder = this::dispatch;

    @Nullable
    private volatile Listener mListener;

    public void start(Listener listener) {
        mListener = listener;
        PlaybackSpectrumSource.addListener(mForwarder);
    }

    public void stop() {
        PlaybackSpectrumSource.removeListener(mForwarder);
        mListener = null;
    }

    private void dispatch(byte[] fftData) {
        Listener listener = mListener;
        if (listener != null) {
            listener.onFftData(fftData);
        }
    }
}
