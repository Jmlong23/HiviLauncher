package com.hivi.launcher.ai.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;

/**
 * Captures 16 kHz mono PCM from Android's microphone path and forwards 20 ms Opus frames.
 */
public final class AudioRecordOpusStreamer {
    private static final String TAG = "AudioRecordOpusStreamer";
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNEL_COUNT = 1;
    private static final int FRAME_SAMPLES = SAMPLE_RATE / 50;
    private static final int FRAME_BYTES = FRAME_SAMPLES * 2;
    private static final int OPUS_BUFFER_SIZE = 4_000;
    private static final long VOLUME_CALLBACK_INTERVAL_MS = 66L;

    public interface AudioSink {
        boolean send(byte[] opusData, int length);
    }

    public interface VolumeListener {
        void onVolumeChanged(float volume);
    }

    private final Object mLock = new Object();
    private final AudioSink mAudioSink;
    private final VolumeListener mVolumeListener;

    private volatile boolean mRunning;
    private volatile boolean mAudioSendingEnabled;
    private AudioRecord mAudioRecord;
    private Thread mWorkerThread;

    public AudioRecordOpusStreamer(AudioSink audioSink, VolumeListener volumeListener) {
        mAudioSink = audioSink;
        mVolumeListener = volumeListener;
    }

    public boolean start() {
        synchronized (mLock) {
            if (mRunning) {
                return true;
            }

            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBufferSize <= 0) {
                Log.e(TAG, "AudioRecord does not support 16 kHz mono input");
                return false;
            }

            AudioRecord recorder;
            try {
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBufferSize, FRAME_BYTES * 8));
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to create AudioRecord", exception);
                return false;
            }
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder.release();
                Log.e(TAG, "AudioRecord failed to initialize");
                return false;
            }

            try {
                recorder.startRecording();
            } catch (IllegalStateException | SecurityException exception) {
                recorder.release();
                Log.e(TAG, "Unable to start microphone capture", exception);
                return false;
            }

            mAudioRecord = recorder;
            mRunning = true;
            mAudioSendingEnabled = false;
            mWorkerThread = new Thread(this::captureLoop, "ai-record-opus");
            mWorkerThread.start();
            return true;
        }
    }

    public void setAudioSendingEnabled(boolean enabled) {
        mAudioSendingEnabled = enabled;
    }

    public boolean isRunning() {
        return mRunning;
    }

    public void stop() {
        AudioRecord recorder;
        Thread worker;
        synchronized (mLock) {
            mRunning = false;
            mAudioSendingEnabled = false;
            recorder = mAudioRecord;
            mAudioRecord = null;
            worker = mWorkerThread;
            mWorkerThread = null;
        }

        if (recorder != null) {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
            }
            recorder.release();
        }
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
            try {
                worker.join(500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        OpusEncoder encoder;
        try {
            encoder = new OpusEncoder(SAMPLE_RATE, CHANNEL_COUNT, OpusApplication.OPUS_APPLICATION_VOIP);
            encoder.setBitrate(24_000);
            encoder.setComplexity(3);
        } catch (Throwable throwable) {
            Log.e(TAG, "Unable to initialize Opus encoder", throwable);
            stop();
            return;
        }

        AudioRecord recorder;
        synchronized (mLock) {
            recorder = mAudioRecord;
        }
        if (recorder == null) {
            return;
        }

        byte[] readBuffer = new byte[FRAME_BYTES * 2];
        byte[] frameBytes = new byte[FRAME_BYTES];
        short[] pcmFrame = new short[FRAME_SAMPLES];
        byte[] opusBuffer = new byte[OPUS_BUFFER_SIZE];
        int frameByteCount = 0;
        long lastVolumeCallbackMs = 0L;

        while (mRunning && !Thread.currentThread().isInterrupted()) {
            int read;
            try {
                read = recorder.read(readBuffer, 0, readBuffer.length);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Microphone capture stopped", exception);
                break;
            }
            if (read <= 0) {
                continue;
            }

            int sourceOffset = 0;
            while (sourceOffset < read && mRunning) {
                int copyLength = Math.min(FRAME_BYTES - frameByteCount, read - sourceOffset);
                System.arraycopy(readBuffer, sourceOffset, frameBytes, frameByteCount, copyLength);
                frameByteCount += copyLength;
                sourceOffset += copyLength;
                if (frameByteCount < FRAME_BYTES) {
                    continue;
                }

                float volume = toPcmAndMeasureVolume(frameBytes, pcmFrame);
                long now = System.currentTimeMillis();
                if (mVolumeListener != null && now - lastVolumeCallbackMs >= VOLUME_CALLBACK_INTERVAL_MS) {
                    lastVolumeCallbackMs = now;
                    mVolumeListener.onVolumeChanged(volume);
                }

                if (mAudioSendingEnabled && mAudioSink != null) {
                    try {
                        int encodedLength = encoder.encode(pcmFrame, 0, FRAME_SAMPLES,
                                opusBuffer, 0, opusBuffer.length);
                        if (encodedLength > 0) {
                            mAudioSink.send(opusBuffer, encodedLength);
                        }
                    } catch (Throwable throwable) {
                        Log.w(TAG, "Unable to encode microphone audio", throwable);
                    }
                }
                frameByteCount = 0;
            }
        }
    }

    private float toPcmAndMeasureVolume(byte[] pcmBytes, short[] pcmFrame) {
        double sumSquares = 0.0;
        int peak = 0;
        for (int i = 0; i < FRAME_SAMPLES; i++) {
            int offset = i * 2;
            short sample = (short) ((pcmBytes[offset] & 0xFF) | (pcmBytes[offset + 1] << 8));
            pcmFrame[i] = sample;
            int absolute = Math.abs((int) sample);
            peak = Math.max(peak, absolute);
            float normalized = absolute / 32768f;
            sumSquares += normalized * normalized;
        }
        float rms = (float) Math.sqrt(sumSquares / FRAME_SAMPLES);
        float raw = rms * 0.82f + peak / 32768f * 0.18f;
        final float noiseFloor = 0.018f;
        return Math.min(1f, Math.max(0f, (raw - noiseFloor) * 8f));
    }
}
