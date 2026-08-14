package com.hivi.launcher.ai.wakeup.utils;

/**
 * 音频通道过滤。
 *
 * <p>示例：{@code AudioFilter.convert(audio, 8, "7,-1")} 表示输入 8 声道，输出 2 声道，
 * 输出的 0 号声道数据来源于输入的 7 号声道，输出的 1 号声道为空数据（-1 表示空数据）。</p>
 */
public final class AudioFilter {
    /** 2 表示 16bit 数据，4 表示 32bit 数据。 */
    private static final int BYTES_PER_SAMPLE = 2;

    private static int[] sNeedChannels;
    private static int sInputLength;
    private static String sFilterParams = "";
    private static byte[] sOutputData;
    private static int sOriginSample;
    private static int sOutputChannel;
    private static int sOutputDataLengthPerSample;
    private static int sInputDataLengthPerSample;

    private AudioFilter() {
    }

    /**
     * @param inputChannel 输入音频声道数量
     * @param filterParams 输出保留的声道编号，-1 表示空数据
     */
    public static byte[] convert(byte[] inputData, int inputChannel, String filterParams) {
        // 初始化一次，避免重复创建
        if (!sFilterParams.equals(filterParams)) {
            sFilterParams = filterParams;
            String[] channels = filterParams.split(",");
            sNeedChannels = new int[channels.length];
            for (int i = 0; i < channels.length; i++) {
                sNeedChannels[i] = Integer.parseInt(channels[i]);
            }
            sOutputChannel = sNeedChannels.length;
            sOriginSample = inputData.length / (inputChannel * BYTES_PER_SAMPLE);
            sInputDataLengthPerSample = inputChannel * BYTES_PER_SAMPLE;
            sOutputDataLengthPerSample = sOutputChannel * BYTES_PER_SAMPLE;
        }

        // 输入长度变化时重建输出缓冲区
        if (sInputLength != inputData.length) {
            sInputLength = inputData.length;
            sOriginSample = inputData.length / (inputChannel * BYTES_PER_SAMPLE);
            sOutputData = new byte[inputData.length / inputChannel * sOutputChannel];
        }

        for (int i = 0; i < sOutputChannel; i++) {
            int channelId = sNeedChannels[i];
            if (channelId < 0) {
                // 配置 -1 不处理输入数据，输出数据默认为 0
                for (int j = 0; j < sOriginSample; j++) {
                    int resultPos = sOutputDataLengthPerSample * j + i * BYTES_PER_SAMPLE;
                    sOutputData[resultPos] = 0;
                    sOutputData[resultPos + 1] = 0;
                }
                continue;
            }
            for (int j = 0; j < sOriginSample; j++) {
                int resultPos = sOutputDataLengthPerSample * j + i * BYTES_PER_SAMPLE;
                int srcPos = sInputDataLengthPerSample * j + channelId * BYTES_PER_SAMPLE;
                sOutputData[resultPos] = inputData[srcPos];
                sOutputData[resultPos + 1] = inputData[srcPos + 1];
            }
        }
        return sOutputData;
    }
}
