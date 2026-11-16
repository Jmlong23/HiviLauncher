package com.hivi.launcher.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.annotation.Nullable;

import com.hivi.launcher.utils.log.AppLog;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point for playback spectrum frames captured through Android's {@link AudioRecord},
 * matching the Hivi-Audio application's visualization path.
 *
 * <p>On this hardware the i2s/dummy-codec capture carries the DSP playback mix, so the
 * Bluetooth/HDMI/coax/line/optical music shows up here even though that audio never enters
 * Android's own playback path. It is a separate PCM device from the PDM microphone array used
 * by the wakeup engine, so the wakeup recorder and this capture do not share samples or
 * interfere with each other.</p>
 *
 * <p>Input-mode pages subscribe only while visible, so several subscriptions can briefly
 * overlap during page transitions. The capture runs only while at least one listener exists.</p>
 */
public final class PlaybackSpectrumSource {
    private static final String TAG = "PlaybackSpectrumSource";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_COUNT = 1;
    private static final int READ_SAMPLE_COUNT = 2048;
    private static final long READER_JOIN_TIMEOUT_MS = 300L;

    private static final Object LOCK = new Object();
    private static final PcmSpectrumAnalyzer ANALYZER = new PcmSpectrumAnalyzer();
    private static final CopyOnWriteArrayList<PcmSpectrumAnalyzer.Listener> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final PcmSpectrumAnalyzer.Listener DISPATCHER = fftData -> {
        for (PcmSpectrumAnalyzer.Listener listener : LISTENERS) {
            listener.onFftData(fftData);
        }
    };

    private static AudioRecord sAudioRecord;
    private static Thread sReaderThread;
    private static volatile boolean sRunning;

    private PlaybackSpectrumSource() {
    }

    public static void addListener(@Nullable PcmSpectrumAnalyzer.Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (LOCK) {
            if (!LISTENERS.addIfAbsent(listener)) {
                return;
            }
            ANALYZER.setListener(DISPATCHER);
            if (!sRunning) {
                startCaptureLocked();
            }
        }
    }

    public static void removeListener(@Nullable PcmSpectrumAnalyzer.Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (LOCK) {
            LISTENERS.remove(listener);
            if (!LISTENERS.isEmpty()) {
                return;
            }
            ANALYZER.setListener(null);
            stopCaptureLocked();
        }
    }

    private static void startCaptureLocked() {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) {
            AppLog.w(TAG, "AudioRecord unavailable, minBufferSize=" + minBufferSize);
            return;
        }
        int bufferSize = Math.max(minBufferSize, READ_SAMPLE_COUNT * 2 * 2);
        AudioRecord record;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        } catch (SecurityException | IllegalArgumentException e) {
            AppLog.w(TAG, "Unable to open AudioRecord for spectrum", e);
            return;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            AppLog.w(TAG, "AudioRecord did not initialize, state=" + record.getState());
            record.release();
            return;
        }
        sAudioRecord = record;
        sRunning = true;
        sReaderThread = new Thread(() -> readCapture(record), "playback-spectrum-reader");
        try {
            record.startRecording();
        } catch (IllegalStateException e) {
            AppLog.w(TAG, "Unable to start AudioRecord for spectrum", e);
            sRunning = false;
            sAudioRecord = null;
            sReaderThread = null;
            record.release();
            return;
        }
        AppLog.i(TAG, "Playback spectrum capture started, bufferSize=" + bufferSize);
        sReaderThread.start();
    }

    private static void stopCaptureLocked() {
        sRunning = false;
        AudioRecord record = sAudioRecord;
        sAudioRecord = null;
        Thread reader = sReaderThread;
        sReaderThread = null;
        if (record != null) {
            try {
                record.stop();
            } catch (IllegalStateException ignored) {
                // The reader thread may already have stopped the record.
            }
        }
        if (reader != null && reader != Thread.currentThread()) {
            try {
                reader.join(READER_JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void readCapture(AudioRecord record) {
        short[] buffer = new short[READ_SAMPLE_COUNT];
        try {
            while (sRunning) {
                int read = record.read(buffer, 0, buffer.length);
                if (read > 0) {
                    ANALYZER.feed(buffer, read, CHANNEL_COUNT, 0, 0);
                } else if (read < 0) {
                    AppLog.w(TAG, "AudioRecord read failed, code=" + read);
                    break;
                }
            }
        } catch (IllegalStateException e) {
            AppLog.w(TAG, "AudioRecord read stopped unexpectedly", e);
        } finally {
            try {
                record.stop();
            } catch (IllegalStateException ignored) {
                // The record was already stopped by removeListener.
            }
            record.release();
            AppLog.i(TAG, "Playback spectrum capture released");
        }
    }
}
