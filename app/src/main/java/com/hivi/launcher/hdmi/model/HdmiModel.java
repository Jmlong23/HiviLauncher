package com.hivi.launcher.hdmi.model;

import android.media.audiofx.Visualizer;

import androidx.annotation.Nullable;

import com.hivi.launcher.utils.log.AppLog;

import java.util.Arrays;

public final class HdmiModel {
    private static final String TAG = "HdmiModel";
    private static final int GLOBAL_MIX_AUDIO_SESSION_ID = 0;

    public interface Listener {
        void onHdmiFftData(byte[] fftData);
    }

    @Nullable
    private Visualizer mVisualizer;
    @Nullable
    private volatile Listener mListener;

    /**
     * Captures the global audio mix, which contains the HDMI stream while HDMI is the active
     * input mode. This uses the same Visualizer FFT source as the legacy Hivi-Audio screens.
     */
    public void start(Listener listener) {
        mListener = listener;
        stopVisualizer();
        Visualizer visualizer = null;
        try {
            visualizer = new Visualizer(GLOBAL_MIX_AUDIO_SESSION_ID);
            int[] captureSizeRange = Visualizer.getCaptureSizeRange();
            if (captureSizeRange == null || captureSizeRange.length == 0) {
                AppLog.w(TAG, "HDMI spectrum capture size is unavailable");
                return;
            }
            int captureSize = captureSizeRange[captureSizeRange.length - 1];
            if (visualizer.setCaptureSize(captureSize) != Visualizer.SUCCESS) {
                AppLog.w(TAG, "Unable to configure HDMI spectrum capture size");
                return;
            }
            int captureRate = Math.max(1, Visualizer.getMaxCaptureRate() / 2);
            if (visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer ignored, byte[] waveform,
                        int samplingRate) {
                    // HDMI uses FFT data only.
                }

                @Override
                public void onFftDataCapture(Visualizer ignored, byte[] fftData,
                        int samplingRate) {
                    Listener currentListener = mListener;
                    if (currentListener != null && fftData != null) {
                        currentListener.onHdmiFftData(Arrays.copyOf(fftData, fftData.length));
                    }
                }
            }, captureRate, false, true) != Visualizer.SUCCESS) {
                AppLog.w(TAG, "Unable to register HDMI spectrum capture listener");
                return;
            }
            if (visualizer.setEnabled(true) != Visualizer.SUCCESS) {
                AppLog.w(TAG, "Unable to enable HDMI spectrum capture");
                return;
            }
            mVisualizer = visualizer;
            visualizer = null;
        } catch (RuntimeException exception) {
            AppLog.w(TAG, "HDMI spectrum capture is unavailable: " + exception.getMessage());
        } finally {
            releaseVisualizer(visualizer);
        }
    }

    public void stop() {
        mListener = null;
        stopVisualizer();
    }

    private void stopVisualizer() {
        Visualizer visualizer = mVisualizer;
        mVisualizer = null;
        if (visualizer == null) {
            return;
        }
        try {
            visualizer.setEnabled(false);
        } catch (RuntimeException ignored) {
            // The effect may already have been released by the audio system.
        }
        releaseVisualizer(visualizer);
    }

    private void releaseVisualizer(@Nullable Visualizer visualizer) {
        if (visualizer == null) {
            return;
        }
        try {
            visualizer.release();
        } catch (RuntimeException ignored) {
            // The effect may already have been released by the audio system.
        }
    }
}
