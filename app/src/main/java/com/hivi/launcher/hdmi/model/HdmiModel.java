package com.hivi.launcher.hdmi.model;

import com.hivi.launcher.audio.SpectrumSubscription;

public final class HdmiModel {
    public interface Listener {
        void onHdmiFftData(byte[] fftData);
    }

    private final SpectrumSubscription mSpectrumSubscription = new SpectrumSubscription();

    /**
     * HDMI audio is mixed by the MCU/DSP and never enters Android's audio framework, so the
     * speaker reference channels of the wakeup PCM stream are the only FFT source available.
     */
    public void start(Listener listener) {
        mSpectrumSubscription.start(listener::onHdmiFftData);
    }

    public void stop() {
        mSpectrumSubscription.stop();
    }
}
