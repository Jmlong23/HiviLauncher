package com.hivi.launcher.ai.wakeup.recorder;

import android.os.Process;
import android.os.SystemClock;

import com.hivi.audionativelib.AudioNativeManager;
import com.hivi.audionativelib.manager.audioAlsa.AudioAlsaRecorder;
import com.hivi.audionativelib.manager.audioAlsa.AudioAlsaRecorderListener;
import com.hivi.launcher.ai.wakeup.EngineConstants;
import com.hivi.launcher.ai.wakeup.utils.AudioAmplify;
import com.hivi.launcher.ai.wakeup.utils.AudioFilter;
import com.hivi.launcher.utils.log.AppLog;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多麦阵列 ALSA 单声卡录音机。
 *
 * <p>通过 tinyalsa 直接按配置采集音频，再在 Java 层按 {@link EngineConstants#CHANNEL_PARAMS}
 * 重排通道（4mic + 2ref），送入 VTN。ALSA 回调线程只做轻量拷贝入队，重排与 feed 引擎放在
 * 独立线程，避免拖慢底层采集。</p>
 */
public final class SingleAlsaRecorder implements AudioRecorder {
    private static final String TAG = "SingleAlsaRecorder";
    private static final int RAW_QUEUE_CAPACITY = 16;
    private static final long PCM_LOG_INTERVAL_MS = 1_000L;

    private static SingleAlsaRecorder sInstance;
    private static AudioData sAudioData;

    private final AudioAlsaRecorder alsaRecorder;
    private final ArrayBlockingQueue<RawPcmFrame> rawQueue =
            new ArrayBlockingQueue<>(RAW_QUEUE_CAPACITY);
    private final ArrayBlockingQueue<RawPcmFrame> rawFramePool =
            new ArrayBlockingQueue<>(RAW_QUEUE_CAPACITY);

    private volatile Thread vtnWorkerThread;
    private byte[] reusableAudioData;
    private int droppedRawFrames;
    private final AtomicLong rawCallbackCount = new AtomicLong();
    private final AtomicLong rawCallbackSamples = new AtomicLong();
    private final AtomicLong vtnInputCount = new AtomicLong();
    private final AtomicLong vtnInputBytes = new AtomicLong();
    private final long[] channelEnergy = new long[EngineConstants.CHANNEL];
    private final int[] channelPeak = new int[EngineConstants.CHANNEL];
    private long channelSampleCount;
    private long lastPcmFlowLogAtMs;
    private boolean firstRawFrameLogged;

    private static final class RawPcmFrame {
        short[] samples;
        int frameSize;

        void ensureCapacity(int minSize) {
            if (samples == null || samples.length < minSize) {
                samples = new short[minSize];
            }
        }
    }

    private SingleAlsaRecorder() {
        alsaRecorder = AudioNativeManager.instance().getAudioAlsaRecorder();
        for (int i = 0; i < RAW_QUEUE_CAPACITY; i++) {
            rawFramePool.offer(new RawPcmFrame());
        }
    }

    public static synchronized SingleAlsaRecorder getInstance(AudioData dataListener,
            int micNum, int refNum) {
        if (sInstance == null) {
            sInstance = new SingleAlsaRecorder();
            AppLog.i(TAG, "created, micNum=" + micNum + ", refNum=" + refNum);
        }
        sAudioData = dataListener;
        return sInstance;
    }

    @Override
    public int startRecord() {
        if (EngineConstants.isRecording) {
            return 0;
        }
        EngineConstants.isRecording = true;
        rawQueue.clear();
        droppedRawFrames = 0;
        rawCallbackCount.set(0L);
        rawCallbackSamples.set(0L);
        vtnInputCount.set(0L);
        vtnInputBytes.set(0L);
        resetChannelLevels();
        lastPcmFlowLogAtMs = 0L;
        firstRawFrameLogged = false;
        startVtnWorker();

        AppLog.i(TAG, "ALSA capture request: card=" + EngineConstants.CARD
                + ", device=" + EngineConstants.DEVICE
                + ", channels=" + EngineConstants.CHANNEL
                + ", sampleRate=" + EngineConstants.HW_SAMPLE_RATE
                + ", channelMap=" + EngineConstants.CHANNEL_PARAMS);

        new Thread(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            } catch (Throwable ignored) {
            }
            boolean started = alsaRecorder.startMicCapture(
                    new AudioAlsaRecorderListener() {
                        @Override
                        public void onWakePcmRead(short[] pcm, int frameSize) {
                            if (EngineConstants.isRecording) {
                                enqueueRawPcm(pcm, frameSize);
                            }
                        }

                        @Override
                        public void onUploadPcmRead(short[] pcm, int frameSize) {
                        }
                    },
                    EngineConstants.CARD,
                    EngineConstants.DEVICE,
                    EngineConstants.CHANNEL,
                    EngineConstants.HW_SAMPLE_RATE,
                    0);
            if (!started && EngineConstants.isRecording) {
                EngineConstants.isRecording = false;
                Thread worker = vtnWorkerThread;
                if (worker != null) {
                    worker.interrupt();
                }
                rawQueue.clear();
                AppLog.e(TAG, "ALSA capture failed to start; recording state reset");
            } else {
                AppLog.i(TAG, "ALSA capture finished, result=" + started);
            }
        }, "alsa-recorder").start();

        AppLog.i(TAG, "startRecord success");
        return 0;
    }

    @Override
    public void stopRecord() {
        EngineConstants.isRecording = false;
        alsaRecorder.stopMicCapture();
        Thread worker = vtnWorkerThread;
        vtnWorkerThread = null;
        if (worker != null) {
            worker.interrupt();
        }
        rawQueue.clear();
        AppLog.i(TAG, "stopRecord");
    }

    @Override
    public void destroyRecord() {
        stopRecord();
        synchronized (SingleAlsaRecorder.class) {
            sInstance = null;
            sAudioData = null;
        }
        AppLog.i(TAG, "destroyRecord");
    }

    private void startVtnWorker() {
        Thread old = vtnWorkerThread;
        if (old != null && old.isAlive()) {
            return;
        }
        Thread worker = new Thread(this::vtnWorkerLoop, "vtn-audio-worker");
        vtnWorkerThread = worker;
        worker.start();
    }

    private void vtnWorkerLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        } catch (Throwable ignored) {
        }
        Thread current = Thread.currentThread();
        while (EngineConstants.isRecording && current == vtnWorkerThread) {
            RawPcmFrame frame;
            try {
                frame = rawQueue.poll(100L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                continue;
            }
            if (frame == null) {
                continue;
            }
            try {
                processRawFrame(frame.samples, frame.frameSize);
            } finally {
                recycleRawFrame(frame);
            }
        }
    }

    private void enqueueRawPcm(short[] pcm, int frameSize) {
        if (pcm == null || frameSize <= 0) {
            AppLog.w(TAG, "ignore invalid ALSA PCM callback: pcm=" + (pcm != null)
                    + ", frameSize=" + frameSize);
            return;
        }
        int sampleCount = Math.min(frameSize, pcm.length);
        if (sampleCount != frameSize) {
            AppLog.w(TAG, "ALSA PCM callback length mismatch: frameSize=" + frameSize
                    + ", arrayLength=" + pcm.length);
        }
        rawCallbackCount.incrementAndGet();
        rawCallbackSamples.addAndGet(sampleCount);
        if (!firstRawFrameLogged) {
            firstRawFrameLogged = true;
            AppLog.i(TAG, "ALSA first PCM callback: samples=" + sampleCount
                    + ", expectedSamplesPer20ms=" + (EngineConstants.HW_SAMPLE_RATE / 50)
                            * EngineConstants.CHANNEL);
        }
        RawPcmFrame frame = rawFramePool.poll();
        if (frame == null) {
            frame = rawQueue.poll();
            if (frame != null) {
                onRawFrameDropped();
            }
        }
        if (frame == null) {
            frame = new RawPcmFrame();
        }
        frame.ensureCapacity(sampleCount);
        System.arraycopy(pcm, 0, frame.samples, 0, sampleCount);
        frame.frameSize = sampleCount;
        if (rawQueue.offer(frame)) {
            return;
        }
        RawPcmFrame dropped = rawQueue.poll();
        if (dropped != null) {
            onRawFrameDropped();
            recycleRawFrame(dropped);
        }
        if (!rawQueue.offer(frame)) {
            onRawFrameDropped();
            recycleRawFrame(frame);
        }
    }

    private void recycleRawFrame(RawPcmFrame frame) {
        frame.frameSize = 0;
        rawFramePool.offer(frame);
    }

    private void onRawFrameDropped() {
        droppedRawFrames++;
        if (droppedRawFrames == 1 || droppedRawFrames % 50 == 0) {
            AppLog.w(TAG, "raw PCM queue busy, dropped=" + droppedRawFrames);
        }
    }

    private void processRawFrame(short[] pcm, int frameSize) {
        if (frameSize % EngineConstants.CHANNEL != 0) {
            AppLog.w(TAG, "drop unaligned ALSA PCM frame: samples=" + frameSize
                    + ", channels=" + EngineConstants.CHANNEL);
            return;
        }
        accumulateChannelLevels(pcm, frameSize);

        byte[] audioData = ensureAudioData(frameSize * 2);
        shortToBytes(pcm, frameSize, audioData);

        if (EngineConstants.RAW_AUDIO_GAIN != 1.0f) {
            AudioAmplify.amplifyAll(audioData, EngineConstants.RAW_AUDIO_GAIN);
        }

        byte[] vtnData = EngineConstants.CHANGE_CHANNEL
                ? AudioFilter.convert(audioData, EngineConstants.CHANNEL,
                        EngineConstants.CHANNEL_PARAMS)
                : audioData;

        AudioData listener = sAudioData;
        if (listener != null) {
            listener.onData(vtnData, vtnData.length);
            vtnInputCount.incrementAndGet();
            vtnInputBytes.addAndGet(vtnData.length);
            logPcmFlowIfNeeded(vtnData);
        } else {
            AppLog.w(TAG, "drop PCM: VTN listener is not attached");
        }
    }

    private void logPcmFlowIfNeeded(byte[] vtnData) {
        long now = SystemClock.elapsedRealtime();
        if (lastPcmFlowLogAtMs != 0L && now - lastPcmFlowLogAtMs < PCM_LOG_INTERVAL_MS) {
            return;
        }
        lastPcmFlowLogAtMs = now;
        long callbacks = rawCallbackCount.getAndSet(0L);
        long samples = rawCallbackSamples.getAndSet(0L);
        long vtnFrames = vtnInputCount.getAndSet(0L);
        long vtnBytes = vtnInputBytes.getAndSet(0L);
        int peak = calculatePeak(vtnData);
        // AppLog.i(TAG, "PCM_TO_VTN: alsaCallbacks=" + callbacks
        //         + ", alsaSamples=" + samples
        //         + ", vtnFrames=" + vtnFrames
        //         + ", vtnBytes=" + vtnBytes
        //         + ", currentFrameBytes=" + vtnData.length
        //         + ", peak=" + peak
        //         + ", channels=" + EngineConstants.CHANNEL
        //         + ", channelMap=" + EngineConstants.CHANNEL_PARAMS);
        // AppLog.i(TAG, "ALSA_CHANNEL_LEVELS: samplesPerChannel=" + channelSampleCount
        //         + ", rms=" + formatChannelRms()
        //         + ", peak=" + formatChannelPeak()
        //         + ", assumedMic=[0,1,2,3], assumedRef=[4,5]");
        resetChannelLevels();
    }

    private void accumulateChannelLevels(short[] pcm, int frameSize) {
        int channels = EngineConstants.CHANNEL;
        int samplesPerChannel = frameSize / channels;
        for (int sampleIndex = 0; sampleIndex < samplesPerChannel; sampleIndex++) {
            int frameOffset = sampleIndex * channels;
            for (int channelIndex = 0; channelIndex < channels; channelIndex++) {
                int sample = pcm[frameOffset + channelIndex];
                int magnitude = Math.abs(sample);
                channelEnergy[channelIndex] += (long) sample * sample;
                if (magnitude > channelPeak[channelIndex]) {
                    channelPeak[channelIndex] = magnitude;
                }
            }
        }
        channelSampleCount += samplesPerChannel;
    }

    private String formatChannelRms() {
        StringBuilder builder = new StringBuilder("[");
        for (int channelIndex = 0; channelIndex < EngineConstants.CHANNEL; channelIndex++) {
            if (channelIndex > 0) {
                builder.append(',');
            }
            long averageEnergy = channelSampleCount == 0L
                    ? 0L : channelEnergy[channelIndex] / channelSampleCount;
            builder.append((int) Math.sqrt(averageEnergy));
        }
        return builder.append(']').toString();
    }

    private String formatChannelPeak() {
        StringBuilder builder = new StringBuilder("[");
        for (int channelIndex = 0; channelIndex < EngineConstants.CHANNEL; channelIndex++) {
            if (channelIndex > 0) {
                builder.append(',');
            }
            builder.append(channelPeak[channelIndex]);
        }
        return builder.append(']').toString();
    }

    private void resetChannelLevels() {
        channelSampleCount = 0L;
        for (int channelIndex = 0; channelIndex < EngineConstants.CHANNEL; channelIndex++) {
            channelEnergy[channelIndex] = 0L;
            channelPeak[channelIndex] = 0;
        }
    }

    private static int calculatePeak(byte[] pcm) {
        int peak = 0;
        for (int index = 0; index + 1 < pcm.length; index += 2) {
            int sample = (pcm[index] & 0xFF) | (pcm[index + 1] << 8);
            int magnitude = Math.abs((short) sample);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        return peak;
    }

    private byte[] ensureAudioData(int byteLength) {
        if (reusableAudioData == null || reusableAudioData.length != byteLength) {
            reusableAudioData = new byte[byteLength];
        }
        return reusableAudioData;
    }

    private static void shortToBytes(short[] src, int length, byte[] dst) {
        for (int i = 0; i < length; i++) {
            dst[i * 2] = (byte) (src[i] & 0xFF);
            dst[i * 2 + 1] = (byte) ((src[i] >> 8) & 0xFF);
        }
    }
}
