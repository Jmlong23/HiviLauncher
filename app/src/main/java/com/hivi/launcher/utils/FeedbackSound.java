package com.hivi.launcher.utils;

import android.media.AudioManager;
import android.media.ToneGenerator;

/**
 * Short touch feedback for volume/mode/screen interactions (test issue #10).
 *
 * <p>ToneGenerator on STREAM_MUSIC so the click routes to the amplifier like playback.
 * Creation can fail on some builds; callers then stay silent instead of crashing.</p>
 */
public final class FeedbackSound {
    private static final int TONE_STREAM = AudioManager.STREAM_MUSIC;
    private static final int TONE_VOLUME_PERCENT = 45;
    private static final int CLICK_DURATION_MS = 60;

    private static volatile ToneGenerator sToneGenerator;

    private FeedbackSound() {
    }

    public static void playClick() {
        ToneGenerator generator = obtainGenerator();
        if (generator == null) {
            return;
        }
        try {
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, CLICK_DURATION_MS);
        } catch (RuntimeException ignored) {
        }
    }

    private static ToneGenerator obtainGenerator() {
        if (sToneGenerator == null) {
            synchronized (FeedbackSound.class) {
                if (sToneGenerator == null) {
                    try {
                        sToneGenerator = new ToneGenerator(TONE_STREAM, TONE_VOLUME_PERCENT);
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
            }
        }
        return sToneGenerator;
    }
}
