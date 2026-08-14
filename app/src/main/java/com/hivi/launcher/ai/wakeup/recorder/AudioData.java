package com.hivi.launcher.ai.wakeup.recorder;

/**
 * 重排声道后的 PCM 数据回调。
 */
public interface AudioData {
    void onData(byte[] data, int dataLength);
}
