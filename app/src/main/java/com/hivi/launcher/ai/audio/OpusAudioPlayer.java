package com.hivi.launcher.ai.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import com.hivi.launcher.utils.log.AppLog;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.jaredmdobson.concentus.OpusDecoder;

/**
 * Low-latency 16 kHz mono Opus TTS player used by the AI WebSocket response stream.
 */
public final class OpusAudioPlayer {
    private static final String TAG = "AiOpusAudioPlayer";
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNEL_COUNT = 1;
    private static final int MAX_DECODE_SAMPLES = SAMPLE_RATE / 50 * 12;
    private static final long IDLE_STOP_DELAY_MS = 500L;

    public interface Listener {
        void onPlaybackFinished();
    }

    private final Object mLock = new Object();
    private final LinkedBlockingQueue<byte[]> mQueue = new LinkedBlockingQueue<>(128);
    private final short[] mDecodeBuffer = new short[MAX_DECODE_SAMPLES];
    private final byte[] mPcmBuffer = new byte[MAX_DECODE_SAMPLES * 2];
    private final OpusDecoder mDecoder;
    private final Listener mListener;

    private AudioTrack mAudioTrack;
    private Thread mPlaybackThread;
    private volatile boolean mPlaying;
    private volatile long mLastEnqueueTimeMs;

    public OpusAudioPlayer(Listener listener) {
        mListener = listener;
        try {
            mDecoder = new OpusDecoder(SAMPLE_RATE, CHANNEL_COUNT);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize Opus decoder", exception);
        }
    }

    public void play(byte[] opusData) {
        if (opusData == null || opusData.length == 0) {
            return;
        }
        boolean startPlaybackThread = false;
        synchronized (mLock) {
            if (!mQueue.offer(Arrays.copyOf(opusData, opusData.length))) {
                mQueue.poll();
                mQueue.offer(Arrays.copyOf(opusData, opusData.length));
            }
            mLastEnqueueTimeMs = System.currentTimeMillis();
            if (!mPlaying) {
                mPlaying = true;
                mPlaybackThread = new Thread(this::playbackLoop, "ai-opus-player");
                startPlaybackThread = true;
            }
        }
        if (startPlaybackThread) {
            mPlaybackThread.start();
        }
    }

    public boolean isPlaying() {
        return mPlaying;
    }

    public void stop() {
        Thread playbackThread;
        synchronized (mLock) {
            mPlaying = false;
            mQueue.clear();
            playbackThread = mPlaybackThread;
            mPlaybackThread = null;
        }
        if (playbackThread != null && playbackThread != Thread.currentThread()) {
            playbackThread.interrupt();
        }
        pauseAndFlushTrack();
    }

    public void release() {
        stop();
        synchronized (mLock) {
            if (mAudioTrack != null) {
                try {
                    mAudioTrack.release();
                } catch (IllegalStateException ignored) {
                }
                mAudioTrack = null;
            }
        }
    }

    private void playbackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        try {
            ensureAudioTrack();
            while (mPlaying && !Thread.currentThread().isInterrupted()) {
                byte[] opusData = mQueue.poll(150L, TimeUnit.MILLISECONDS);
                if (!mPlaying) {
                    break;
                }
                if (opusData == null) {
                    if (System.currentTimeMillis() - mLastEnqueueTimeMs >= IDLE_STOP_DELAY_MS) {
                        break;
                    }
                    continue;
                }
                playPacket(opusData);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            AppLog.w(TAG, "AI TTS playback failed", throwable);
        } finally {
            boolean notifyFinished;
            synchronized (mLock) {
                notifyFinished = mPlaying;
                mPlaying = false;
                if (mPlaybackThread == Thread.currentThread()) {
                    mPlaybackThread = null;
                }
            }
            pauseAndFlushTrack();
            if (notifyFinished && mListener != null) {
                mListener.onPlaybackFinished();
            }
        }
    }

    private void ensureAudioTrack() {
        synchronized (mLock) {
            if (mAudioTrack != null && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                mAudioTrack.flush();
                mAudioTrack.play();
                return;
            }
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(minBufferSize > 0 ? minBufferSize * 2 : 0, 4_096);
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            mAudioTrack = new AudioTrack(attributes, format, bufferSize, AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            mAudioTrack.play();
        }
    }

    private void playPacket(byte[] opusData) {
        int decodedSamples;
        try {
            decodedSamples = mDecoder.decode(opusData, 0, opusData.length, mDecodeBuffer, 0,
                    mDecodeBuffer.length, false);
        } catch (Exception exception) {
            AppLog.w(TAG, "Skipping invalid AI Opus packet", exception);
            return;
        }
        if (decodedSamples <= 0) {
            return;
        }

        int pcmLength = decodedSamples * 2;
        for (int i = 0; i < decodedSamples; i++) {
            mPcmBuffer[i * 2] = (byte) (mDecodeBuffer[i] & 0xFF);
            mPcmBuffer[i * 2 + 1] = (byte) ((mDecodeBuffer[i] >> 8) & 0xFF);
        }
        synchronized (mLock) {
            if (mPlaying && mAudioTrack != null
                    && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                mAudioTrack.write(mPcmBuffer, 0, pcmLength);
            }
        }
    }

    private void pauseAndFlushTrack() {
        synchronized (mLock) {
            if (mAudioTrack == null) {
                return;
            }
            try {
                mAudioTrack.pause();
                mAudioTrack.flush();
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
