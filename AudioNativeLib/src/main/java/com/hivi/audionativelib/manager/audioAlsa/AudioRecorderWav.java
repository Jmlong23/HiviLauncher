package com.hivi.audionativelib.manager.audioAlsa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import com.hivi.audionativelib.NativeLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class AudioRecorderWav {
    private static final String TAG = "AudioRecorderWav";

    // 音频录制参数
    private static final int SAMPLE_RATE = 16000; // CD音质采样率[citation:1]
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // 单声道[citation:6]
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 16位PCM编码[citation:1][citation:6]

    private AudioRecord audioRecord;
    private int bufferSize = 1280;
    private boolean isRecording = false;
    private Thread recordingThread;
    private String outputPath;

    public AudioRecorderWav(String outputPath) {
        this.outputPath = outputPath;
        // 获取最小缓冲区大小[citation:6][citation:7]

        // 初始化AudioRecord[citation:1][citation:6]
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC, // 麦克风输入源
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );
    }

    public void startRecording() {
        if (isRecording) {
            return;
        }

        if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            NativeLog.e(TAG, "AudioRecord初始化失败");
            return;
        }

        try {
            audioRecord.startRecording(); // 开始录制[citation:1]
            isRecording = true;

            // 创建录制线程[citation:8]
            recordingThread = new Thread(new RecordingRunnable(), "AudioRecorder Thread");
            recordingThread.start();

        } catch (IllegalStateException e) {
            NativeLog.e(TAG, "启动录制失败: " + e.getMessage());
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        try {
            if (audioRecord != null) {
                audioRecord.stop(); // 停止录制[citation:7]
            }
        } catch (IllegalStateException e) {
            NativeLog.e(TAG, "停止录制失败: " + e.getMessage());
        }

        // 等待录制线程结束
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                NativeLog.e(TAG, "录制线程结束异常: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        // 释放AudioRecord资源[citation:7]
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    private class RecordingRunnable implements Runnable {
        @Override
        public void run() {
            FileOutputStream fos = null;
            try {
                File file = new File(outputPath);
                file.getParentFile().mkdirs(); // 创建父目录
                if (file.exists()) {
                    file.delete();
                }
                file.createNewFile();

                fos = new FileOutputStream(file);

                // 先写入WAV文件头[citation:7][citation:8]
                writeWavHeader(fos, 0); // 初始文件长度为0，后面会更新

                byte[] buffer = new byte[bufferSize];
                int totalBytesRead = 0;

                while (isRecording && audioRecord != null) {
                    // 读取音频数据[citation:1]
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0 && bytesRead != AudioRecord.ERROR_INVALID_OPERATION) {
                        // 写入PCM数据[citation:7]
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                }

                // 录制完成，更新WAV文件头[citation:7]
                if (totalBytesRead > 0) {
                    updateWavHeader(outputPath, totalBytesRead);
                }

            } catch (IOException e) {
                NativeLog.e(TAG, "文件写入错误: " + e.getMessage());
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        NativeLog.e(TAG, "关闭文件流错误: " + e.getMessage());
                    }
                }
            }
        }
    }

    // 写入WAV文件头[citation:7]
    private void writeWavHeader(FileOutputStream fos, long totalDataLen) throws IOException {
        long sampleRate = SAMPLE_RATE;
        int channels = CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2;
        int bitsPerSample = AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;
        long byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        // WAV文件头固定44字节[citation:6][citation:7]
        byte[] header = new byte[44];

        // RIFF chunk
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        long fileSize = totalDataLen + 36; // 文件总大小 - 8字节
        header[4] = (byte)(fileSize & 0xff);
        header[5] = (byte)((fileSize >> 8) & 0xff);
        header[6] = (byte)((fileSize >> 16) & 0xff);
        header[7] = (byte)((fileSize >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';

        // fmt chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size = 16
        header[20] = 1; header[21] = 0; // AudioFormat = PCM
        header[22] = (byte)channels; header[23] = 0; // Channels
        header[24] = (byte)(sampleRate & 0xff);
        header[25] = (byte)((sampleRate >> 8) & 0xff);
        header[26] = (byte)((sampleRate >> 16) & 0xff);
        header[27] = (byte)((sampleRate >> 24) & 0xff);
        header[28] = (byte)(byteRate & 0xff);
        header[29] = (byte)((byteRate >> 8) & 0xff);
        header[30] = (byte)((byteRate >> 16) & 0xff);
        header[31] = (byte)((byteRate >> 24) & 0xff);
        header[32] = (byte)blockAlign; header[33] = 0; // BlockAlign
        header[34] = (byte)bitsPerSample; header[35] = 0; // BitsPerSample

        // data chunk
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte)(totalDataLen & 0xff);
        header[41] = (byte)((totalDataLen >> 8) & 0xff);
        header[42] = (byte)((totalDataLen >> 16) & 0xff);
        header[43] = (byte)((totalDataLen >> 24) & 0xff);

        fos.write(header, 0, header.length);
    }

    // 更新WAV文件头[citation:7]
    private void updateWavHeader(String filePath, int totalAudioLen) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(filePath, "rw");

            // 更新文件大小信息
            long totalDataLen = totalAudioLen;
            long totalFileLen = totalDataLen + 36;

            // RIFF chunk size (文件总大小 - 8)
            raf.seek(4);
            raf.write((int)(totalFileLen) & 0xff);
            raf.write((int)(totalFileLen >> 8) & 0xff);
            raf.write((int)(totalFileLen >> 16) & 0xff);
            raf.write((int)(totalFileLen >> 24) & 0xff);

            // data chunk size (音频数据大小)
            raf.seek(40);
            raf.write((int)(totalDataLen) & 0xff);
            raf.write((int)(totalDataLen >> 8) & 0xff);
            raf.write((int)(totalDataLen >> 16) & 0xff);
            raf.write((int)(totalDataLen >> 24) & 0xff);

        } catch (IOException e) {
            NativeLog.e(TAG, "更新WAV文件头错误: " + e.getMessage());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    NativeLog.e(TAG, "关闭RandomAccessFile错误: " + e.getMessage());
                }
            }
        }
    }

    public boolean isRecording() {
        return isRecording;
    }
}