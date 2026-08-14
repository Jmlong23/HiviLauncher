package com.hivi.launcher.ai.audio;

import android.os.Process;
import android.os.SystemClock;

import com.hivi.launcher.ai.wakeup.VtnEngine;
import com.hivi.launcher.utils.log.AppLog;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;

/**
 * 从 ALSA 多麦阵列 + VTN 前端处理管线接收音频，编码为 Opus 后交给 {@link AudioSink} 发送。
 *
 * <p>音频来源有两条路径（运行时自动选择）：</p>
 * <ol>
 *   <li><b>VTN 处理后音频（优先）</b> —— 经过 AEC、降噪、波束合成的单声道 16kHz PCM，
 *       由 {@link VtnEngine.ProcessedAudioListener} 回调送入。</li>
 *   <li><b>VTN 降噪后识别音频（兜底）</b> —— 若上面未产出，则使用
 *       {@code VTN_CALLBACK_TYPE_AUDIO_REC} 回调的降噪单声道 PCM，
 *       由 {@link com.hivi.launcher.ai.wakeup.FlyAiIVW} 调用 {@link #feedRecAudio} 注入。</li>
 * </ol>
 *
 * <p>本类不自行管理 {@code AudioRecord}；VTN 回调线程只做轻量拷贝入队，
 * Opus 编码与网络发送在独立线程完成。</p>
 */
public final class MicOpusStreamer {
    private static final String TAG = "MicOpusStreamer";
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNEL_COUNT = 1;
    private static final int AUDIO_QUEUE_CAPACITY = 32;
    private static final int OPUS_BUFFER_SIZE = 4_000;
    private static final long VOLUME_CALLBACK_INTERVAL_MS = 66L;
    /** 距上次 VTN 处理后音频不足该时长时，忽略兜底 REC 路径，避免两路重复上传。 */
    private static final long VTN_PROCESSED_STALE_MS = 300L;
    /** 开麦前保留的音频帧数（20ms/帧，共 500ms），开麦后补发，避免丢失句首。 */
    private static final int PREBUFFER_FRAME_COUNT = 25;
    private static final long WATCHDOG_LOG_INTERVAL_MS = 1_000L;
    private static final long NO_AUDIO_WARN_MS = 1_000L;

    public interface AudioSink {
        boolean send(byte[] opusData, int length);
    }

    public interface VolumeListener {
        void onVolumeChanged(float volume);
    }

    private final Object mStateLock = new Object();
    private final AudioSink mAudioSink;
    private final VolumeListener mVolumeListener;
    private final int mFrameSize = SAMPLE_RATE / 50;

    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private final AtomicBoolean mAudioSendingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean mForceSendSilence = new AtomicBoolean(false);

    private final ArrayBlockingQueue<AudioPacket> mAudioQueue =
            new ArrayBlockingQueue<>(AUDIO_QUEUE_CAPACITY);
    private final ArrayBlockingQueue<AudioPacket> mAudioPacketPool =
            new ArrayBlockingQueue<>(AUDIO_QUEUE_CAPACITY);

    private OpusEncoder mEncoder;
    private short[] mPcmAccumulator;
    private int mAccumulatorPos;
    private byte[] mOpusOutBuffer;
    private short[][] mPrebufferFrames;
    private int mPrebufferWriteIndex;
    private int mPrebufferStoredFrames;
    private short[] mSilenceFrame;
    private short[] mReusableMonoBuffer;

    private volatile Thread mAudioWorkerThread;
    private volatile boolean mHasAudioSendingBeenEnabled;
    private volatile long mLastVtnProcessedAudioMs;
    private volatile long mLastInputAudioMs;
    private volatile long mLastOpusSendMs;
    private volatile long mLastWatchdogLogMs;
    private volatile long mLastVolumeCallbackMs;
    private boolean mLoggedVtnProcessedPath;
    private boolean mLoggedFallbackPath;
    private int mDroppedAudioPackets;

    private static final class AudioPacket {
        byte[] data;
        int size;
        boolean fromProcessed;

        void ensureCapacity(int minSize) {
            if (data == null || data.length < minSize) {
                data = new byte[minSize];
            }
        }
    }

    public MicOpusStreamer(AudioSink audioSink, VolumeListener volumeListener) {
        mAudioSink = audioSink;
        mVolumeListener = volumeListener;
    }

    // ────────────────────── 生命周期 ──────────────────────

    public boolean start() {
        synchronized (mStateLock) {
            if (mRunning.get()) {
                return true;
            }
            try {
                mEncoder = new OpusEncoder(SAMPLE_RATE, CHANNEL_COUNT,
                        OpusApplication.OPUS_APPLICATION_VOIP);
                mEncoder.setBitrate(24_000);
                mEncoder.setComplexity(3);
            } catch (Throwable throwable) {
                AppLog.e(TAG, "Unable to create Opus encoder", throwable);
                return false;
            }

            mRunning.set(true);
            mAudioSendingEnabled.set(false);
            mForceSendSilence.set(false);
            mHasAudioSendingBeenEnabled = false;
            mLastVtnProcessedAudioMs = 0L;
            mLoggedVtnProcessedPath = false;
            mLoggedFallbackPath = false;
            mAccumulatorPos = 0;
            mLastVolumeCallbackMs = 0L;
            mLastInputAudioMs = 0L;
            mLastOpusSendMs = 0L;
            mLastWatchdogLogMs = 0L;

            mPcmAccumulator = new short[mFrameSize * 4];
            mOpusOutBuffer = new byte[OPUS_BUFFER_SIZE];
            mPrebufferFrames = new short[PREBUFFER_FRAME_COUNT][mFrameSize];
            clearPrebufferLocked();
            prepareAudioQueues();

            Thread worker = new Thread(this::audioWorkerLoop, "ai-mic-opus-worker");
            mAudioWorkerThread = worker;
            worker.start();
        }

        VtnEngine.setProcessedAudioListener(this::onVtnProcessedAudio);
        AppLog.i(TAG, "started (ALSA+VTN), frameSize=" + mFrameSize);
        return true;
    }

    public void stop() {
        if (!mRunning.getAndSet(false)) {
            return;
        }
        AppLog.i(TAG, "stopped");
        mAudioSendingEnabled.set(false);
        mForceSendSilence.set(false);
        VtnEngine.setProcessedAudioListener(null);
        Thread worker = mAudioWorkerThread;
        mAudioWorkerThread = null;
        if (worker != null) {
            worker.interrupt();
        }
        synchronized (mStateLock) {
            mAccumulatorPos = 0;
            clearPrebufferLocked();
            clearQueuedAudioPackets();
        }
    }

    public boolean isRunning() {
        return mRunning.get();
    }

    // ────────────────────── 开关控制 ──────────────────────

    public void setAudioSendingEnabled(boolean enabled) {
        if (enabled) {
            enableAudioSending();
        } else {
            disableAudioSending();
        }
    }

    public void enableAudioSending() {
        synchronized (mStateLock) {
            if (!mRunning.get()) {
                return;
            }
            boolean wasEnabled = mAudioSendingEnabled.getAndSet(true);
            if (!mHasAudioSendingBeenEnabled) {
                mHasAudioSendingBeenEnabled = true;
                AppLog.i(TAG, "audio sending enabled, flushing prebuffer frames="
                        + mPrebufferStoredFrames);
                flushPrebufferLocked();
            } else if (!wasEnabled) {
                AppLog.i(TAG, "audio sending re-enabled");
            }
        }
    }

    public void disableAudioSending() {
        mAudioSendingEnabled.set(false);
        synchronized (mStateLock) {
            if (mHasAudioSendingBeenEnabled) {
                clearPrebufferLocked();
            }
        }
    }

    public boolean isAudioSendingEnabled() {
        return mAudioSendingEnabled.get();
    }

    /**
     * 唤醒打断期间 WebSocket 仍需持续收到音频帧，但唤醒词/提示音不应上传。
     * 打开后编码静音帧，唤醒流程结束再关闭。
     */
    public void setForceSendSilence(boolean enabled) {
        synchronized (mStateLock) {
            if (!mRunning.get()) {
                mForceSendSilence.set(false);
                return;
            }
            boolean changed = mForceSendSilence.getAndSet(enabled) != enabled;
            if (!enabled && !changed) {
                return;
            }
            mAccumulatorPos = 0;
            clearPrebufferLocked();
            clearQueuedAudioPackets();
            if (enabled) {
                mAudioSendingEnabled.set(true);
                mHasAudioSendingBeenEnabled = true;
            }
            if (changed) {
                AppLog.i(TAG, enabled
                        ? "force silence enabled for wake flow"
                        : "force silence disabled, resume microphone audio");
            }
        }
    }

    // ────────────────── 音频输入路径 ──────────────────

    /** 路径 1 —— VTN 处理后音频（单声道 16kHz 16bit LE）。 */
    private void onVtnProcessedAudio(byte[] data, int size) {
        if (!mRunning.get() || data == null || size <= 0) {
            return;
        }
        mLastVtnProcessedAudioMs = SystemClock.elapsedRealtime();
        if (!mLoggedVtnProcessedPath) {
            mLoggedVtnProcessedPath = true;
            AppLog.i(TAG, "audio path: VTN processed (AEC/NR/beamforming), size=" + size);
        }
        enqueueAudio(data, size, true);
    }

    /** 路径 2 —— VTN 降噪后识别音频（兜底），由 FlyAiIVW 的 REC 回调注入。 */
    public void feedRecAudio(byte[] data, int size) {
        if (!mRunning.get() || data == null || size <= 0) {
            return;
        }
        if (SystemClock.elapsedRealtime() - mLastVtnProcessedAudioMs < VTN_PROCESSED_STALE_MS) {
            return;
        }
        if (!mLoggedFallbackPath) {
            mLoggedFallbackPath = true;
            AppLog.i(TAG, "audio path: VTN recognition audio (REC fallback)");
        }
        enqueueAudio(data, size, false);
    }

    // ────────────────── 队列与工作线程 ──────────────────

    private void prepareAudioQueues() {
        mAudioQueue.clear();
        mAudioPacketPool.clear();
        for (int i = 0; i < AUDIO_QUEUE_CAPACITY; i++) {
            mAudioPacketPool.offer(new AudioPacket());
        }
        mDroppedAudioPackets = 0;
    }

    private void audioWorkerLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        } catch (Throwable throwable) {
            AppLog.w(TAG, "set audio worker priority failed", throwable);
        }
        Thread current = Thread.currentThread();
        while (mRunning.get() && current == mAudioWorkerThread) {
            AudioPacket packet;
            try {
                packet = mAudioQueue.poll(100L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                continue;
            }
            if (packet == null) {
                logAudioWatchdogIfNeeded();
                continue;
            }
            try {
                processAudioPacket(packet);
            } finally {
                recycleAudioPacket(packet);
            }
        }
    }

    private void enqueueAudio(byte[] data, int size, boolean fromProcessed) {
        if (!mRunning.get()) {
            return;
        }
        mLastInputAudioMs = SystemClock.elapsedRealtime();
        if (!mAudioSendingEnabled.get() && mHasAudioSendingBeenEnabled) {
            return;
        }
        int actualSize = Math.min(size, data.length);
        if (actualSize <= 0) {
            return;
        }

        AudioPacket packet = obtainAudioPacket();
        packet.ensureCapacity(actualSize);
        System.arraycopy(data, 0, packet.data, 0, actualSize);
        packet.size = actualSize;
        packet.fromProcessed = fromProcessed;

        if (mAudioQueue.offer(packet)) {
            return;
        }
        AudioPacket dropped = mAudioQueue.poll();
        if (dropped != null) {
            onAudioPacketDropped();
            recycleAudioPacket(dropped);
        }
        if (!mAudioQueue.offer(packet)) {
            onAudioPacketDropped();
            recycleAudioPacket(packet);
        }
    }

    private AudioPacket obtainAudioPacket() {
        AudioPacket packet = mAudioPacketPool.poll();
        if (packet == null) {
            packet = mAudioQueue.poll();
            if (packet != null) {
                onAudioPacketDropped();
            }
        }
        return packet != null ? packet : new AudioPacket();
    }

    private void recycleAudioPacket(AudioPacket packet) {
        packet.size = 0;
        mAudioPacketPool.offer(packet);
    }

    private void clearQueuedAudioPackets() {
        AudioPacket packet;
        while ((packet = mAudioQueue.poll()) != null) {
            recycleAudioPacket(packet);
        }
    }

    private void onAudioPacketDropped() {
        mDroppedAudioPackets++;
        if (mDroppedAudioPackets == 1 || mDroppedAudioPackets % 50 == 0) {
            AppLog.w(TAG, "audio queue busy, dropped packets=" + mDroppedAudioPackets);
        }
    }

    // ────────────────── 核心处理 ──────────────────

    private void processAudioPacket(AudioPacket packet) {
        if (!packet.fromProcessed
                && SystemClock.elapsedRealtime() - mLastVtnProcessedAudioMs
                        < VTN_PROCESSED_STALE_MS) {
            return;
        }
        int sampleCount = Math.min(packet.size, packet.data.length) / 2;
        if (sampleCount <= 0) {
            return;
        }
        short[] mono = ensureMonoBuffer(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            int offset = i * 2;
            mono[i] = (short) ((packet.data[offset] & 0xFF) | (packet.data[offset + 1] << 8));
        }
        feedMonoPcm(mono, sampleCount);
    }

    /** 仅在音频工作线程使用，无需同步。 */
    private short[] ensureMonoBuffer(int minSize) {
        if (mReusableMonoBuffer == null || mReusableMonoBuffer.length < minSize) {
            mReusableMonoBuffer = new short[minSize];
        }
        return mReusableMonoBuffer;
    }

    /**
     * 接收单声道 PCM，回调音量并累积到 Opus 帧大小后编码发送。
     */
    private void feedMonoPcm(short[] pcm, int count) {
        if (!mRunning.get()) {
            return;
        }
        notifyVolumeIfNeeded(pcm, count);

        synchronized (mStateLock) {
            if (!mRunning.get() || mEncoder == null || mPcmAccumulator == null) {
                return;
            }
            if (!mAudioSendingEnabled.get() && mHasAudioSendingBeenEnabled) {
                mAccumulatorPos = 0;
                return;
            }

            int remaining = count;
            int srcPos = 0;
            while (remaining > 0 && mRunning.get()) {
                int toCopy = Math.min(remaining, mFrameSize - mAccumulatorPos);
                System.arraycopy(pcm, srcPos, mPcmAccumulator, mAccumulatorPos, toCopy);
                mAccumulatorPos += toCopy;
                srcPos += toCopy;
                remaining -= toCopy;

                if (mAccumulatorPos >= mFrameSize) {
                    if (mAudioSendingEnabled.get()) {
                        encodeAndSendFrameLocked(mPcmAccumulator);
                    } else if (!mHasAudioSendingBeenEnabled) {
                        storePrebufferFrameLocked(mPcmAccumulator);
                    }
                    mAccumulatorPos = 0;
                }
            }
            if (!mAudioSendingEnabled.get() && mHasAudioSendingBeenEnabled) {
                mAccumulatorPos = 0;
            }
        }
    }

    private void notifyVolumeIfNeeded(short[] pcm, int count) {
        VolumeListener listener = mVolumeListener;
        if (listener == null || count <= 0) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - mLastVolumeCallbackMs < VOLUME_CALLBACK_INTERVAL_MS) {
            return;
        }
        mLastVolumeCallbackMs = now;
        listener.onVolumeChanged(measureVolume(pcm, count));
    }

    /** 与 ParticleVisualizerView 的音量区间保持一致：RMS 与峰值加权后减去噪声底。 */
    private static float measureVolume(short[] pcm, int count) {
        double sumSquares = 0.0;
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int absolute = Math.abs(pcm[i]);
            peak = Math.max(peak, absolute);
            float normalized = absolute / 32768f;
            sumSquares += normalized * normalized;
        }
        float rms = (float) Math.sqrt(sumSquares / count);
        float raw = rms * 0.82f + peak / 32768f * 0.18f;
        final float noiseFloor = 0.018f;
        return Math.min(1f, Math.max(0f, (raw - noiseFloor) * 8f));
    }

    private void encodeAndSendFrameLocked(short[] frame) {
        if (!mRunning.get() || !mAudioSendingEnabled.get() || mEncoder == null
                || mOpusOutBuffer == null || frame == null || mAudioSink == null) {
            return;
        }
        try {
            short[] frameToEncode = frame;
            if (mForceSendSilence.get()) {
                if (mSilenceFrame == null || mSilenceFrame.length < mFrameSize) {
                    mSilenceFrame = new short[mFrameSize];
                }
                frameToEncode = mSilenceFrame;
            }
            int length = mEncoder.encode(frameToEncode, 0, mFrameSize,
                    mOpusOutBuffer, 0, mOpusOutBuffer.length);
            if (length > 0 && mAudioSink.send(mOpusOutBuffer, length)) {
                mLastOpusSendMs = SystemClock.elapsedRealtime();
            }
        } catch (Throwable throwable) {
            AppLog.e(TAG, "Opus encode/send failed", throwable);
        }
    }

    private void storePrebufferFrameLocked(short[] frame) {
        if (mPrebufferFrames == null || frame == null) {
            return;
        }
        System.arraycopy(frame, 0, mPrebufferFrames[mPrebufferWriteIndex], 0, mFrameSize);
        mPrebufferWriteIndex = (mPrebufferWriteIndex + 1) % mPrebufferFrames.length;
        if (mPrebufferStoredFrames < mPrebufferFrames.length) {
            mPrebufferStoredFrames++;
        }
    }

    private void flushPrebufferLocked() {
        if (mPrebufferFrames == null || mPrebufferStoredFrames <= 0) {
            return;
        }
        int frames = mPrebufferStoredFrames;
        int start = (mPrebufferWriteIndex - frames + mPrebufferFrames.length)
                % mPrebufferFrames.length;
        for (int i = 0; i < frames && mRunning.get() && mAudioSendingEnabled.get(); i++) {
            encodeAndSendFrameLocked(mPrebufferFrames[(start + i) % mPrebufferFrames.length]);
        }
        clearPrebufferLocked();
    }

    private void clearPrebufferLocked() {
        mPrebufferWriteIndex = 0;
        mPrebufferStoredFrames = 0;
    }

    private void logAudioWatchdogIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (now - mLastWatchdogLogMs < WATCHDOG_LOG_INTERVAL_MS) {
            return;
        }
        mLastWatchdogLogMs = now;
        if (mLastInputAudioMs > 0L && now - mLastInputAudioMs > NO_AUDIO_WARN_MS) {
            AppLog.w(TAG, "no PCM input for " + (now - mLastInputAudioMs) + "ms");
        }
        if (mAudioSendingEnabled.get() && mLastOpusSendMs > 0L
                && now - mLastOpusSendMs > NO_AUDIO_WARN_MS) {
            AppLog.w(TAG, "no opus sent for " + (now - mLastOpusSendMs) + "ms");
        }
    }
}
