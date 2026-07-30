package com.hivi.audionativelib.manager.audioAlsa;

import android.os.Environment;
import com.hivi.audionativelib.NativeLog;

import java.io.File;
import java.util.Arrays;

public class AudioAlsaRecorder {
    private static final String TAG = "AlsaRecorder";

    private static final int DEFAULT_CARD = 0;
    private static final int DEFAULT_DEVICE = 1;
    private static final int DEFAULT_FORMAT = 0;
    private static final int HARDWARE_CHANNELS = 8;
    private static final int HARDWARE_SAMPLE_RATE = 16000;

    private AudioAlsaRecorderListener listener;

    private AudioRecorderWav audioRecorder;

    public native boolean startCapture(String filePath, int card, int device, int channels, int sampleRate, int format);

    public native void stopCapture();

    public native boolean startMicCapture(int card,
                                          int device,
                                          int channels,
                                          int sampleRate,
                                          int format);

    public native void stopMicCapture();

    public void onWakePcmRead(short[] pcm, int frameSize) {
        if (listener != null) {
            listener.onWakePcmRead(pcm, frameSize);
        }
    }

    public void onUploadPcmRead(short[] pcm, int frameSize) {
        if (listener != null) {
            listener.onUploadPcmRead(pcm, frameSize);
        }
    }

    public void stopRecording() {
        audioRecorder.stopRecording();
    }
    public String startCapture() {
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/record";
        File logsDir = new File(dir);
        if (!logsDir.exists()) {
            NativeLog.e(TAG, "logsDir.mkdirs() res: " + logsDir.mkdirs());
        }

        // 开始录音
        String name = "record.wav";
        String recordPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/record/record.wav";

        int i = 0;
        do {
            File file = new File(recordPath);
            if (file.exists()) {
                recordPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/record/record(" + i + ").wav";
                name = "record(" + i + ").wav";
                i++;
                NativeLog.i(TAG, "startCapture exist recordPath: " + recordPath);
            } else {
                break;
            }
        } while (true);

        NativeLog.i(TAG, "startCapture recordPath: " + recordPath);
        audioRecorder = new AudioRecorderWav(recordPath);
        audioRecorder.startRecording();
        return name;
    }



    public boolean startMicCapture(AudioAlsaRecorderListener listener,
                                   int card,
                                   int device,
                                   int channels,
                                   int sampleRate,
                                   int format) {
        this.listener = listener;
        NativeLog.i(TAG, "startMicCapture args: card=" + card +
                ", device=" + device +
                ", channels=" + channels +
                ", sampleRate=" + sampleRate +
                ", format=" + format);
        return startMicCapture(card, device, channels, sampleRate, format);
    }

    private String previewSamples(short[] pcm, int frameSize, int limit) {
        if (pcm == null || pcm.length == 0) {
            return "[]";
        }
        int count = Math.min(Math.min(frameSize, pcm.length), Math.max(1, limit));
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(pcm[i]);
        }
        if (count < frameSize && count < pcm.length) {
            sb.append(", ...");
        }
        sb.append("]");
        return sb.toString();
    }
}
