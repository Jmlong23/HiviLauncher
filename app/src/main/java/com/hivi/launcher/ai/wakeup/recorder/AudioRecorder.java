package com.hivi.launcher.ai.wakeup.recorder;

/**
 * 唤醒链路使用的录音机抽象。
 */
public interface AudioRecorder {
    int startRecord();

    void stopRecord();

    void destroyRecord();
}
