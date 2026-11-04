package com.hivi.launcher.audio;

import androidx.annotation.Nullable;

/**
 * Computes spectrum frames from the interleaved PCM captured by the wakeup ALSA recorder.
 *
 * <p>Bluetooth playback is mixed by the MCU/DSP and never reaches Android's audio framework, so
 * the system {@code Visualizer} cannot see it. The PDM stream opened by the wakeup recorder
 * carries the speaker reference channels, which follow whatever the DSP is currently playing.
 * This analyzer turns those samples into the same packed FFT layout the spectrum views already
 * consume.</p>
 */
public final class PcmSpectrumAnalyzer {
    private static final int FFT_SIZE = 1024;
    private static final int BIN_COUNT = FFT_SIZE / 2;
    private static final int HOP_SIZE = FFT_SIZE / 2;
    private static final int PACKED_FFT_SIZE = 2 + (BIN_COUNT - 1) * 2;
    private static final double BYTE_SCALE_MAX = 127d;
    /** The reference channels idle at roughly +/-24 LSB while playback is paused. */
    private static final int SILENCE_PEAK = 96;

    public interface Listener {
        void onFftData(byte[] fftData);
    }

    private final double[] mWindow = new double[FFT_SIZE];
    private final double[] mReal = new double[FFT_SIZE];
    private final double[] mImag = new double[FFT_SIZE];
    private final short[] mSamples = new short[FFT_SIZE];
    private final byte[] mPackedFft = new byte[PACKED_FFT_SIZE];

    private int mSampleCount;
    private volatile Listener mListener;

    public PcmSpectrumAnalyzer() {
        for (int index = 0; index < FFT_SIZE; index++) {
            mWindow[index] = 0.5 - 0.5 * Math.cos(2d * Math.PI * index / (FFT_SIZE - 1));
        }
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
        synchronized (this) {
            mSampleCount = 0;
        }
    }

    public boolean hasListener() {
        return mListener != null;
    }

    /**
     * Feeds an interleaved PCM frame. Only the two reference channels are mixed down to mono.
     */
    public void feed(short[] pcm, int frameSize, int channels, int referenceChannelA,
            int referenceChannelB) {
        if (pcm == null || mListener == null || channels <= 0) {
            return;
        }
        int frameCount = Math.min(frameSize, pcm.length) / channels;
        if (frameCount <= 0
                || referenceChannelA >= channels
                || referenceChannelB >= channels) {
            return;
        }
        synchronized (this) {
            for (int frame = 0; frame < frameCount; frame++) {
                int offset = frame * channels;
                int left = pcm[offset + referenceChannelA];
                int right = pcm[offset + referenceChannelB];
                mSamples[mSampleCount++] = (short) ((left + right) / 2);
                if (mSampleCount == FFT_SIZE) {
                    publishFrame();
                    System.arraycopy(mSamples, HOP_SIZE, mSamples, 0, FFT_SIZE - HOP_SIZE);
                    mSampleCount = FFT_SIZE - HOP_SIZE;
                }
            }
        }
    }

    private void publishFrame() {
        int peak = 0;
        for (int index = 0; index < FFT_SIZE; index++) {
            int magnitude = Math.abs(mSamples[index]);
            if (magnitude > peak) {
                peak = magnitude;
            }
            mReal[index] = mSamples[index] * mWindow[index];
            mImag[index] = 0d;
        }
        if (peak < SILENCE_PEAK) {
            publishSilence();
            return;
        }
        fft(mReal, mImag);

        double maxMagnitude = 0d;
        for (int bin = 1; bin < BIN_COUNT; bin++) {
            double magnitude = Math.hypot(mReal[bin], mImag[bin]) / FFT_SIZE;
            mReal[bin] = magnitude;
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude;
            }
        }

        // Mirror the reference recorder: keep quiet frames quiet, but compress loud frames into
        // the 0..127 range the spectrum view is calibrated for.
        double scale = maxMagnitude > BYTE_SCALE_MAX ? maxMagnitude / BYTE_SCALE_MAX : 1d;
        mPackedFft[0] = 0;
        mPackedFft[1] = 0;
        for (int bin = 1; bin < BIN_COUNT; bin++) {
            long value = Math.round(mReal[bin] / scale);
            mPackedFft[bin * 2] = (byte) Math.min(BYTE_SCALE_MAX, value);
            mPackedFft[bin * 2 + 1] = 0;
        }

        Listener listener = mListener;
        if (listener != null) {
            byte[] frame = new byte[PACKED_FFT_SIZE];
            System.arraycopy(mPackedFft, 0, frame, 0, PACKED_FFT_SIZE);
            listener.onFftData(frame);
        }
    }

    private void publishSilence() {
        Listener listener = mListener;
        if (listener != null) {
            listener.onFftData(new byte[PACKED_FFT_SIZE]);
        }
    }

    private static void fft(double[] real, double[] imag) {
        int size = real.length;
        int j = 0;
        for (int i = 1; i < size; i++) {
            int bit = size >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) {
                double tempReal = real[i];
                real[i] = real[j];
                real[j] = tempReal;
                double tempImag = imag[i];
                imag[i] = imag[j];
                imag[j] = tempImag;
            }
        }

        for (int length = 2; length <= size; length <<= 1) {
            int halfLength = length >> 1;
            double angleStep = -2d * Math.PI / length;
            for (int start = 0; start < size; start += length) {
                for (int offset = 0; offset < halfLength; offset++) {
                    double angle = angleStep * offset;
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    int evenIndex = start + offset;
                    int oddIndex = evenIndex + halfLength;
                    double oddReal = real[oddIndex] * cos - imag[oddIndex] * sin;
                    double oddImag = real[oddIndex] * sin + imag[oddIndex] * cos;
                    real[oddIndex] = real[evenIndex] - oddReal;
                    imag[oddIndex] = imag[evenIndex] - oddImag;
                    real[evenIndex] += oddReal;
                    imag[evenIndex] += oddImag;
                }
            }
        }
    }
}
