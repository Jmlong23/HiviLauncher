package com.hivi.launcher.ai.wakeup;

import com.ivoice.jni.VtnApiJni;

/**
 * 将 ALSA 回调产生的变长 PCM 块，按 VTN {@code createFeedOriginAudioParam} 声明的
 * {@link VtnApiJni.VtnInteractParam#getInRawObj()} 长度（引擎期望步长）切分后送入
 * {@link VtnEngine#writeAudio}，避免引擎内部分片带来的额外分配与 GC。
 *
 * <p>仅在录音线程调用，不做同步；{@code vtnBuffer} 须为 {@code param.getInRawObj()} 同一引用。</p>
 */
public final class VtnStepAudioAssembler {

    private int stepBytes;
    private byte[] vtnBuffer;
    private int filled;

    /**
     * @param stepBytes   与引擎 {@code getExpectedStepAudioInByteSize()} 一致
     * @param engineInRaw {@link VtnApiJni.VtnInteractParam#getInRawObj()}，写入目标缓冲区
     */
    public void reset(int stepBytes, byte[] engineInRaw) {
        if (stepBytes <= 0 || engineInRaw == null || engineInRaw.length < stepBytes) {
            clear();
            return;
        }
        this.stepBytes = stepBytes;
        this.vtnBuffer = engineInRaw;
        this.filled = 0;
    }

    public void clear() {
        stepBytes = 0;
        vtnBuffer = null;
        filled = 0;
    }

    public int getStepBytes() {
        return stepBytes;
    }

    /** 丢弃未凑满一帧的尾部（停录时调用，避免下次启动拼上陈旧数据）。 */
    public void discardPending() {
        filled = 0;
    }

    /**
     * 追加一段已重排声道后的 PCM（16bit LE），凑满 {@code stepBytes} 即 feed 一次引擎。
     */
    public void feed(byte[] src, int len, VtnApiJni.VtnInteractParam param) {
        if (stepBytes <= 0 || vtnBuffer == null || param == null || src == null || len <= 0) {
            return;
        }
        int pos = 0;
        while (pos < len) {
            int copyLength = Math.min(stepBytes - filled, len - pos);
            System.arraycopy(src, pos, vtnBuffer, filled, copyLength);
            filled += copyLength;
            pos += copyLength;
            if (filled == stepBytes) {
                VtnEngine.writeAudio(param, vtnBuffer);
                filled = 0;
            }
        }
    }
}
