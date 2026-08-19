package com.hivi.launcher.ai.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import com.hivi.launcher.utils.log.AppLog;

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
    /** TTS 播报期间的 WebSocket ping 间隔（同 HiviAudio OpusAudioPlayer）。 */
    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;

    public interface Listener {
        void onPlaybackFinished();
    }

    private final Object mLock = new Object();
    private final LinkedBlockingQueue<byte[]> mQueue = new LinkedBlockingQueue<>();
    private final short[] mDecodeBuffer = new short[MAX_DECODE_SAMPLES];
    private final byte[] mPcmBuffer = new byte[MAX_DECODE_SAMPLES * 2];
    private final OpusDecoder mDecoder;
    private final Listener mListener;
    private final Handler mHeartbeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mHeartbeatStarted) {
                return;
            }
            Runnable action = mHeartbeatAction;
            if (action != null) {
                action.run();
            }
            mHeartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };
    private volatile Runnable mHeartbeatAction;
    private volatile boolean mHeartbeatStarted;

    private AudioTrack mAudioTrack;
    private Thread mPlaybackThread;
    private volatile boolean mPlaying;
    private volatile long mActivePlaybackSessionId = -1L;
    private volatile long mLastEnqueueTimeMs;
    private long mPlaybackSessionId;

    public OpusAudioPlayer(Listener listener) {
        mListener = listener;
        try {
            mDecoder = new OpusDecoder(SAMPLE_RATE, CHANNEL_COUNT);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize Opus decoder", exception);
        }
    }

    /**
     * TTS 播报期间的心跳回调：上层在此发送 WebSocket ping 保活（同 HiviAudio 的
     * OpusAudioPlayerListener.playOpusData）。仅在播放会话存续期间周期触发。
     */
    public void setHeartbeatAction(Runnable action) {
        mHeartbeatAction = action;
    }

    public void play(byte[] opusData) {
        if (opusData == null || opusData.length == 0) {
            return;
        }
        boolean startPlaybackThread = false;
        synchronized (mLock) {
            if (!mPlaying) {
                mPlaying = true;
                mActivePlaybackSessionId = ++mPlaybackSessionId;
                mQueue.clear();
                long sessionId = mActivePlaybackSessionId;
                mPlaybackThread = new Thread(() -> playbackLoop(sessionId), "ai-opus-player");
                startPlaybackThread = true;
            }
            mQueue.offer(opusData);
            mLastEnqueueTimeMs = System.currentTimeMillis();
        }
        if (startPlaybackThread) {
            mPlaybackThread.start();
        }
        startHeartbeatIfNeeded();
    }

    public boolean isPlaying() {
        return mPlaying;
    }

    public void stop() {
        Thread playbackThread;
        synchronized (mLock) {
            ++mPlaybackSessionId;
            mActivePlaybackSessionId = -1L;
            mPlaying = false;
            mQueue.clear();
            playbackThread = mPlaybackThread;
            mPlaybackThread = null;
        }
        stopHeartbeat();
        if (playbackThread != null && playbackThread != Thread.currentThread()) {
            playbackThread.interrupt();
            try {
                playbackThread.join(1_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
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

    private void playbackLoop(long sessionId) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        try {
            if (!isSessionActive(sessionId)) {
                return;
            }
            ensureAudioTrack();
            while (isSessionActive(sessionId) && !Thread.currentThread().isInterrupted()) {
                byte[] opusData = mQueue.poll(150L, TimeUnit.MILLISECONDS);
                if (!isSessionActive(sessionId)) {
                    break;
                }
                if (opusData == null) {
                    if (System.currentTimeMillis() - mLastEnqueueTimeMs >= IDLE_STOP_DELAY_MS) {
                        break;
                    }
                    continue;
                }
                playPacket(opusData, sessionId);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            AppLog.w(TAG, "AI TTS playback failed", throwable);
        } finally {
            boolean notifyFinished;
            synchronized (mLock) {
                notifyFinished = isSessionActiveLocked(sessionId);
                if (notifyFinished) {
                    mPlaying = false;
                    mActivePlaybackSessionId = -1L;
                }
                if (mPlaybackThread == Thread.currentThread()) {
                    mPlaybackThread = null;
                }
            }
            if (notifyFinished) {
                pauseAndFlushTrack();
            }
            stopHeartbeat();
            if (notifyFinished && mListener != null) {
                mListener.onPlaybackFinished();
            }
        }
    }

    /** 有音频入队即启动播报心跳；重复调用无副作用（同 HiviAudio startHeartbeatIfNeeded）。 */
    private void startHeartbeatIfNeeded() {
        synchronized (mLock) {
            if (mHeartbeatStarted) {
                return;
            }
            mHeartbeatStarted = true;
        }
        mHeartbeatHandler.removeCallbacks(mHeartbeatRunnable);
        mHeartbeatHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_INTERVAL_MS);
        AppLog.i(TAG, "TTS playback started, WebSocket ping heartbeat enabled");
    }

    private void stopHeartbeat() {
        boolean wasStarted;
        synchronized (mLock) {
            wasStarted = mHeartbeatStarted;
            mHeartbeatStarted = false;
        }
        mHeartbeatHandler.removeCallbacks(mHeartbeatRunnable);
        if (wasStarted) {
            AppLog.i(TAG, "TTS playback finished, WebSocket ping heartbeat disabled");
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

    private void playPacket(byte[] opusData, long sessionId) {
        if (!isSessionActive(sessionId)) {
            return;
        }
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
            if (isSessionActiveLocked(sessionId) && mAudioTrack != null
                    && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                mAudioTrack.write(mPcmBuffer, 0, pcmLength);
            }
        }
    }

    private boolean isSessionActive(long sessionId) {
        synchronized (mLock) {
            return isSessionActiveLocked(sessionId);
        }
    }

    private boolean isSessionActiveLocked(long sessionId) {
        return mPlaying && sessionId == mActivePlaybackSessionId;
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
