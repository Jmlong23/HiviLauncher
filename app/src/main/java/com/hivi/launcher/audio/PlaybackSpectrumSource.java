package com.hivi.launcher.audio;

import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point for spectrum frames derived from the wakeup recorder's PCM stream.
 *
 * <p>The PDM input mixes the four microphones with two speaker reference channels. The
 * reference channels carry the DSP playback mix, so they show Bluetooth/HDMI/coax/line/optical
 * music even though that audio never enters Android's own audio path.</p>
 *
 * <p>Input-mode pages subscribe only while visible, so several subscriptions can briefly
 * overlap during page transitions.</p>
 */
public final class PlaybackSpectrumSource {
    private static final int REFERENCE_CHANNEL_A = 4;
    private static final int REFERENCE_CHANNEL_B = 5;

    private static final PcmSpectrumAnalyzer ANALYZER = new PcmSpectrumAnalyzer();
    private static final CopyOnWriteArrayList<PcmSpectrumAnalyzer.Listener> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final PcmSpectrumAnalyzer.Listener DISPATCHER = fftData -> {
        for (PcmSpectrumAnalyzer.Listener listener : LISTENERS) {
            listener.onFftData(fftData);
        }
    };

    private PlaybackSpectrumSource() {
    }

    public static void addListener(@Nullable PcmSpectrumAnalyzer.Listener listener) {
        if (listener == null || !LISTENERS.addIfAbsent(listener)) {
            return;
        }
        ANALYZER.setListener(DISPATCHER);
    }

    public static void removeListener(@Nullable PcmSpectrumAnalyzer.Listener listener) {
        if (listener == null) {
            return;
        }
        LISTENERS.remove(listener);
        if (LISTENERS.isEmpty()) {
            ANALYZER.setListener(null);
        }
    }

    public static void feed(short[] pcm, int frameSize, int channels) {
        ANALYZER.feed(pcm, frameSize, channels, REFERENCE_CHANNEL_A, REFERENCE_CHANNEL_B);
    }
}
