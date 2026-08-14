package com.hivi.launcher.ai.wakeup.utils;

/**
 * 音频振幅放大，只处理 16bit 数据。
 */
public final class AudioAmplify {

    private AudioAmplify() {
    }

    /**
     * 放大或缩小所有声道的音量，放大后的音频直接覆盖输入数据。
     *
     * @param multiple 放大倍数，如 1.5f
     */
    public static void amplifyAll(byte[] input, float multiple) {
        for (int i = 0, length = input.length; i < length; i += 2) {
            amplifyOneSample(input, i, multiple);
        }
    }

    private static void amplifyOneSample(byte[] input, int srcPos, float multiple) {
        int volume = (int) (byteToShort(input, srcPos) * multiple);
        if (volume < -32767) {
            volume = -32767;
        } else if (volume > 32767) {
            volume = 32767;
        }
        input[srcPos] = (byte) (volume & 0xFF);
        input[srcPos + 1] = (byte) ((volume >> 8) & 0xFF);
    }

    private static int byteToShort(byte[] audio, int index) {
        return (audio[index] & 0xFF) | (audio[index + 1] << 8);
    }
}
