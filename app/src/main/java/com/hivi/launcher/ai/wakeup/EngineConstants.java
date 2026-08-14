package com.hivi.launcher.ai.wakeup;

/**
 * 讯飞 VTN 唤醒引擎与 ALSA 录音的固定配置，与 HiviAudio 保持一致。
 */
public final class EngineConstants {
    /** aiui 平台 appid，关乎平台能力授权。 */
    public static final String APPID = "e4cddea8";
    public static final String APPKEY = "3574253046f936c3594c4baf7a8f5356";
    /** aiui_6.6xx 版本支持大模型新增了 API_SECREY，旧版本可填 ""。 */
    public static final String API_SECREY = "M2NkNWVhNTUwZjFhYTFlNGViM2U2ODUz";

    /** 设备 sn，需要保证每台设备唯一，关乎设备计量。 */
    public static String serialNumber;

    // *********Alsa 单声卡配置，代码在 SingleAlsaRecorder 中*********
    /** 声卡号。 */
    public static final int CARD = 0;
    /** 声卡设备号。 */
    public static final int DEVICE = 1;
    /** 硬件采样率。 */
    public static final int HW_SAMPLE_RATE = 16_000;
    /**
     * PDM 阵列硬件输出的声道数量。
     *
     * <p>当前 RK3566 设备的 {@code card 0 / device 1} 只输出 6 路数据：前 4 路为麦克风，
     * 后 2 路为播放参考。该值必须同时与 ALSA 打开参数和 {@link #CHANNEL_PARAMS} 一致。</p>
     */
    public static final int CHANNEL = 6;
    /** true 表示按 {@link #CHANNEL_PARAMS} 重排输入音频声道。 */
    public static final boolean CHANGE_CHANNEL = true;
    /**
     * 要保留的原始音频声道号，第 1 声道是声道 0，-1 表示保留声道但数据清空。
     * "0,1,2,3,4,5" 即当前硬件的 4 路 mic + 2 路回采。
     */
    public static final String CHANNEL_PARAMS = "0,1,2,3,4,5";

    /** 原始音频音量倍数，默认 1.0f。 */
    public static final float RAW_AUDIO_GAIN = 1.0f;

    /** 录音机工作状态。 */
    public static volatile boolean isRecording = false;

    private EngineConstants() {
    }
}
