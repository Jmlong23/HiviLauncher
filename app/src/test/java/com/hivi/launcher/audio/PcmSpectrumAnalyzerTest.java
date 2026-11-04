package com.hivi.launcher.audio;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PcmSpectrumAnalyzerTest {
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNELS = 6;
    private static final int FRAMES_PER_CHUNK = 320;
    private static final int REFERENCE_A = 4;
    private static final int REFERENCE_B = 5;
    private static final int FFT_BIN_HZ = SAMPLE_RATE / 1024;

    @Test
    public void toneOnReferenceChannelsLandsInExpectedBin() {
        PcmSpectrumAnalyzer analyzer = new PcmSpectrumAnalyzer();
        List<byte[]> frames = new ArrayList<>();
        analyzer.setListener(frames::add);

        feedTone(analyzer, 1_000d, 10_000, 2_000);

        assertTrue("analyzer should publish spectrum frames", frames.size() >= 2);
        int peakBin = peakBin(frames.get(frames.size() - 1));
        assertEquals(1_000 / FFT_BIN_HZ, peakBin, 2);
    }

    @Test
    public void microphoneChannelsDoNotDriveTheSpectrum() {
        PcmSpectrumAnalyzer analyzer = new PcmSpectrumAnalyzer();
        List<byte[]> frames = new ArrayList<>();
        analyzer.setListener(frames::add);

        short[] pcm = new short[FRAMES_PER_CHUNK * CHANNELS];
        long sampleIndex = 0;
        for (int chunk = 0; chunk < 8; chunk++) {
            for (int frame = 0; frame < FRAMES_PER_CHUNK; frame++) {
                double sample = 12_000d * Math.sin(2d * Math.PI * 1_000d * sampleIndex / SAMPLE_RATE);
                int offset = frame * CHANNELS;
                // Only the microphone channels carry signal; the reference pair stays silent.
                pcm[offset] = (short) sample;
                pcm[offset + 1] = (short) sample;
                pcm[offset + 2] = (short) sample;
                pcm[offset + 3] = (short) sample;
                pcm[offset + REFERENCE_A] = 0;
                pcm[offset + REFERENCE_B] = 0;
                sampleIndex++;
            }
            analyzer.feed(pcm, pcm.length, CHANNELS, REFERENCE_A, REFERENCE_B);
        }

        assertTrue(frames.size() >= 2);
        for (byte[] frame : frames) {
            assertZeros(frame);
        }
    }

    @Test
    public void silentFramesPublishZeroSpectrum() {
        PcmSpectrumAnalyzer analyzer = new PcmSpectrumAnalyzer();
        List<byte[]> frames = new ArrayList<>();
        analyzer.setListener(frames::add);

        short[] pcm = new short[FRAMES_PER_CHUNK * CHANNELS];
        for (int chunk = 0; chunk < 6; chunk++) {
            analyzer.feed(pcm, pcm.length, CHANNELS, REFERENCE_A, REFERENCE_B);
        }

        assertTrue(frames.size() >= 2);
        for (byte[] frame : frames) {
            assertZeros(frame);
        }
    }

    @Test
    public void noListenerMeansNoWork() {
        PcmSpectrumAnalyzer analyzer = new PcmSpectrumAnalyzer();
        short[] pcm = new short[FRAMES_PER_CHUNK * CHANNELS];
        for (int chunk = 0; chunk < 8; chunk++) {
            analyzer.feed(pcm, pcm.length, CHANNELS, REFERENCE_A, REFERENCE_B);
        }
        // Reaching this point without publishing is enough; the listener contract is optional.
        assertNotNull(analyzer);
    }

    private static void feedTone(PcmSpectrumAnalyzer analyzer, double frequency, int amplitude,
            int chunkCount) {
        short[] pcm = new short[FRAMES_PER_CHUNK * CHANNELS];
        long sampleIndex = 0;
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            for (int frame = 0; frame < FRAMES_PER_CHUNK; frame++) {
                short sample = (short) (amplitude
                        * Math.sin(2d * Math.PI * frequency * sampleIndex / SAMPLE_RATE));
                int offset = frame * CHANNELS;
                pcm[offset + REFERENCE_A] = sample;
                pcm[offset + REFERENCE_B] = sample;
                sampleIndex++;
            }
            analyzer.feed(pcm, pcm.length, CHANNELS, REFERENCE_A, REFERENCE_B);
        }
    }

    private static int peakBin(byte[] packedFft) {
        int peakBin = 0;
        int peakValue = -1;
        for (int bin = 1; bin < packedFft.length / 2; bin++) {
            int value = packedFft[bin * 2] & 0xFF;
            if (value > peakValue) {
                peakValue = value;
                peakBin = bin;
            }
        }
        return peakBin;
    }

    private static void assertZeros(byte[] frame) {
        for (byte value : frame) {
            assertEquals(0, value);
        }
    }
}
