package com.hivi.launcher.line.model;

import com.hivi.launcher.audio.SpectrumSubscription;

public final class LineModel {
    public interface Listener {
        void onFftData(byte[] fftData);
    }

    private final SpectrumSubscription mSpectrumSubscription = new SpectrumSubscription();

    public void start(Listener listener) {
        mSpectrumSubscription.start(listener::onFftData);
    }

    public void stop() {
        mSpectrumSubscription.stop();
    }
}
