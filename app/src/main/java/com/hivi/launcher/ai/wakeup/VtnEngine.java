package com.hivi.launcher.ai.wakeup;

import android.content.Context;
import android.os.SystemClock;

import com.hivi.launcher.ai.wakeup.utils.VtnFileUtils;
import com.hivi.launcher.utils.log.AppLog;
import com.ivoice.jni.AbstractVtnHelper;
import com.ivoice.jni.IVtnHelper;
import com.ivoice.jni.SolutionType;
import com.ivoice.jni.VtnApiJni;
import com.ivoice.jni.VtnLoaderHelper;

import org.json.JSONObject;

import java.io.File;

/**
 * 讯飞 VTN 引擎的 Java 封装：初始化、feed 音频、自定义唤醒词管理。
 */
public final class VtnEngine {
    private static final String TAG = "VtnEngine";
    private static final long PCM_LOG_INTERVAL_MS = 1_000L;

    private static IVtnHelper sVtnApi;
    private static VtnApiJni.VtnParam sVtnParam;
    private static int sMicNum;
    private static int sRefNum;
    private static long sLastPcmLogAtMs;
    private static long sPcmFeedCount;
    private static long sPcmFeedBytes;
    private static int sConsecutivePcmFeedErrors;

    /**
     * VTN 前端处理后（AEC/NR/波束合成）的单声道音频回调。
     */
    public interface ProcessedAudioListener {
        void onProcessedAudio(byte[] audioData, int size);
    }

    private static volatile ProcessedAudioListener sProcessedAudioListener;

    private VtnEngine() {
    }

    public static void setProcessedAudioListener(ProcessedAudioListener listener) {
        sProcessedAudioListener = listener;
    }

    /**
     * @param appid         aiui 平台 appid，关乎平台能力授权
     * @param sn            设备 sn（需保证设备唯一），关乎设备计量
     * @param resIdentifier 唤醒资源文件夹名称
     * @param workDir       引擎工作目录
     * @param solutionType  vtn 引擎方案，需与 aar 引擎相匹配
     * @param listener      结果回调事件
     */
    public static IVtnHelper getInstance(Context context, String appid, String sn,
            String resIdentifier, String workDir, SolutionType solutionType,
            VtnApiJni.VtnEventListener listener) {
        if (sVtnApi != null && sVtnParam != null) {
            return sVtnApi;
        }
        synchronized (VtnEngine.class) {
            if (sVtnApi != null && sVtnParam != null) {
                return sVtnApi;
            }
            if (context == null) {
                AppLog.e(TAG, "getInstance: context is null");
                return null;
            }
            String fullLibPath = AbstractVtnHelper.generateSolutionLibPath(context, solutionType);
            AppLog.i(TAG, "getInstance: solutionLib=" + fullLibPath);

            sVtnParam = new VtnApiJni.VtnParam(appid, sn, resIdentifier, workDir, listener);
            sVtnApi = new VtnLoaderHelper(fullLibPath, sVtnParam);

            // 回调获取降噪算法 mic 和 ref 数量
            sMicNum = sVtnParam.getEngineInfo().getParams().geDataInfo().getMic();
            sRefNum = sVtnParam.getEngineInfo().getParams().geDataInfo().getRef();
            int expectedStepBytes = sVtnParam.getEngineInfo().getParams().getFrameInfo()
                    .getExpectedStepAudioInByteSize();
            AppLog.i(TAG, "VTN engine initialized: micNum=" + sMicNum
                    + ", refNum=" + sRefNum
                    + ", expectedStepBytes=" + expectedStepBytes);
            return sVtnApi;
        }
    }

    public static int getMicNum() {
        return sMicNum;
    }

    public static int getRefNum() {
        return sRefNum;
    }

    /**
     * 向 vtn 引擎送入音频数据，并尝试获取处理后的音频输出（AEC/NR/波束合成）。
     */
    public static void writeAudio(VtnApiJni.VtnInteractParam audioParams, byte[] audioData) {
        IVtnHelper vtnApi = sVtnApi;
        if (vtnApi == null || audioParams == null || audioData == null || audioData.length == 0) {
            logPcmFeedFailure("invalid VTN PCM feed: engine=" + (vtnApi != null)
                    + ", params=" + (audioParams != null)
                    + ", bytes=" + (audioData == null ? 0 : audioData.length));
            return;
        }
        audioParams.setInRawObj(audioData);
        int ret = vtnApi.interact(audioParams);
        if (ret != 0) {
            logPcmFeedFailure("VTN_PCM_FEED failed: ret=" + ret
                    + ", frameBytes=" + audioData.length);
            return;
        }
        logPcmFeedSuccess(audioData.length);

        ProcessedAudioListener listener = sProcessedAudioListener;
        if (listener == null) {
            return;
        }
        try {
            int withOut = audioParams.getWithOut();
            if ((withOut & VtnApiJni.VtnInteractOutputType.RAW.getValue()) != 0) {
                byte[] processed = audioParams.getOutRawObj();
                int size = audioParams.getOutRawObjSize();
                if (processed != null && size > 0) {
                    listener.onProcessedAudio(processed, size);
                }
            }
        } catch (Throwable throwable) {
            AppLog.w(TAG, "unable to read VTN processed audio", throwable);
        }
    }

    private static void logPcmFeedSuccess(int bytes) {
        sPcmFeedCount++;
        sPcmFeedBytes += bytes;
        sConsecutivePcmFeedErrors = 0;
        long now = SystemClock.elapsedRealtime();
        if (sLastPcmLogAtMs != 0L && now - sLastPcmLogAtMs < PCM_LOG_INTERVAL_MS) {
            return;
        }
        sLastPcmLogAtMs = now;
        // AppLog.i(TAG, "VTN_PCM_FEED: calls=" + sPcmFeedCount
        //         + ", bytes=" + sPcmFeedBytes
        //         + ", frameBytes=" + bytes
        //         + ", result=0");
        sPcmFeedCount = 0L;
        sPcmFeedBytes = 0L;
    }

    private static void logPcmFeedFailure(String message) {
        sConsecutivePcmFeedErrors++;
        if (sConsecutivePcmFeedErrors == 1 || sConsecutivePcmFeedErrors % 50 == 0) {
            AppLog.w(TAG, message + ", consecutiveErrors=" + sConsecutivePcmFeedErrors);
        }
    }

    /**
     * 抛出识别音频（唤醒后的降噪音频），授权成功后调用。
     */
    public static int throwsRecAudio() {
        if (sVtnApi == null || sVtnParam == null) {
            return -1;
        }
        VtnApiJni.VtnInteractParam beamParam = VtnApiJni.VtnInteractParam.createSetBeamParam(
                sVtnParam.getEngineInfo().getParams().getFrameInfo().getTotalBeamCount() > 1 ? 1 : 0);
        int ret = sVtnApi.interact(beamParam);
        AppLog.i(TAG, "throwsRecAudio ret=" + ret);
        return ret;
    }

    /**
     * 创建 feedAudio 参数，其 {@code getInRawObj().length} 即引擎期望的每帧字节数。
     */
    public static VtnApiJni.VtnInteractParam createAudioParams() {
        if (sVtnParam == null) {
            return null;
        }
        VtnApiJni.VtnEngineInfo engineInfo = sVtnParam.getEngineInfo();
        int stepAudioSize = engineInfo.getParams().getFrameInfo().getExpectedStepAudioInByteSize();
        VtnApiJni.VtnInteractParam feedOriginAudioParam =
                VtnApiJni.VtnInteractParam.createFeedOriginAudioParam(stepAudioSize);
        // 告诉 jni 层音频数据的大小
        feedOriginAudioParam.setInRawObjSize(stepAudioSize);
        AppLog.i(TAG, "createAudioParams: expectedStepBytes=" + stepAudioSize);
        return feedOriginAudioParam;
    }

    /**
     * 端侧自定义唤醒词资源生成（仅 ivw3.xx 引擎支持）。
     *
     * @param keywordsPath 生成出来的资源存放路径，后续 add 方法需要
     * @param keywords     自定义唤醒词，多个以英文逗号分隔
     */
    public static int generateWakeKeyWordSource(String keywordsPath, String keywords) {
        if (sVtnApi == null) {
            return -1;
        }
        VtnApiJni.VtnInteractParam genWordParam =
                VtnApiJni.VtnInteractParam.createGenerateWordParam(keywords);
        int ret = sVtnApi.interact(genWordParam);
        if (ret == 0
                && (genWordParam.getWithOut() & VtnApiJni.VtnInteractOutputType.RAW.getValue()) != 0) {
            VtnFileUtils.writeBytesToFile(new File(keywordsPath),
                    genWordParam.getOutRawObj(), genWordParam.getOutRawObjSize());
            return 0;
        }
        return -1;
    }

    /**
     * 给引擎添加自定义唤醒词资源，添加后立即生效。
     *
     * @return 资源 id，-1 代表失败
     */
    public static int addGenerateWakeKeyWord(String keywordsPath) {
        if (sVtnApi == null) {
            return -1;
        }
        File file = new File(keywordsPath);
        if (!file.isFile()) {
            return -1;
        }
        byte[] wordResContent = VtnFileUtils.readFileToBytes(file);
        if (wordResContent == null) {
            return -1;
        }
        VtnApiJni.VtnInteractParam addWordParam =
                VtnApiJni.VtnInteractParam.createAddWordParam(wordResContent, wordResContent.length);
        int ret = sVtnApi.interact(addWordParam);
        if (ret != 0
                || (addWordParam.getWithOut() & VtnApiJni.VtnInteractOutputType.STR.getValue()) == 0) {
            AppLog.w(TAG, "addGenerateWakeKeyWord failed ret=" + ret);
            return -1;
        }
        try {
            JSONObject params = new JSONObject(addWordParam.getOutParams())
                    .getJSONObject("params");
            int customWordResourceId = Integer.parseInt(params.optString("id", "0"));
            AppLog.i(TAG, "addGenerateWakeKeyWord succeed, id=" + customWordResourceId);
            return customWordResourceId;
        } catch (Exception exception) {
            AppLog.e(TAG, "unable to parse addWord result", exception);
            return -1;
        }
    }

    /**
     * 删除端侧自定义唤醒词。
     *
     * @param customWordResourceId {@link #addGenerateWakeKeyWord} 返回的资源 id
     */
    public static int removeGenerateWakeKeyWord(int customWordResourceId) {
        if (sVtnApi == null || customWordResourceId == 0) {
            return -1;
        }
        VtnApiJni.VtnInteractParam removeWordParam =
                VtnApiJni.VtnInteractParam.createRemoveWordParam(String.valueOf(customWordResourceId));
        int ret = sVtnApi.interact(removeWordParam);
        AppLog.i(TAG, "removeGenerateWakeKeyWord ret=" + ret);
        return ret;
    }

    public static void destroy() {
        sProcessedAudioListener = null;
        if (sVtnApi != null) {
            sVtnApi.destroy();
            sVtnApi = null;
        }
        sVtnParam = null;
        sMicNum = 0;
        sRefNum = 0;
        sLastPcmLogAtMs = 0L;
        sPcmFeedCount = 0L;
        sPcmFeedBytes = 0L;
        sConsecutivePcmFeedErrors = 0;
    }
}
