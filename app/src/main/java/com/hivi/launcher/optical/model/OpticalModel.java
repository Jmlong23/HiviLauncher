package com.hivi.launcher.optical.model;

import com.hivi.launcher.audio.SpectrumSubscription;

public final class OpticalModel {
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
