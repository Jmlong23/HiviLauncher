package com.hivi.launcher.ai.wakeup;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.ai.wakeup.recorder.AudioData;
import com.hivi.launcher.ai.wakeup.recorder.AudioRecorder;
import com.hivi.launcher.ai.wakeup.recorder.SingleAlsaRecorder;
import com.hivi.launcher.utils.log.AppLog;
import com.ivoice.jni.IVtnHelper;
import com.ivoice.jni.SolutionType;
import com.ivoice.jni.VtnApiJni;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;

/**
 * 语音唤醒入口：ALSA 多麦采集 -> VTN 前端处理/唤醒判决 -> 播放唤醒提示音 -> 通知上层开麦。
 *
 * <p>唤醒后 VTN 输出的降噪识别音频通过 {@link #setMicOpusStreamer} 注册的
 * {@link com.hivi.launcher.ai.audio.MicOpusStreamer} 上传给 AI 服务。</p>
 */
public final class FlyAiIVW {
    private static final String TAG = "FlyAiIVW";
    private static final String[] DEFAULT_WORDS = {"你好小威", "你好小默", "你好小夜"};
    private static final String RES_IDENTIFIER = "xfxf";
    private static final SolutionType SOLUTION_TYPE = SolutionType.CIRCLE4_STD_31712;
    /** 防止 MediaPlayer 未回调完成/错误导致 isWakePromptPlaying 永久为 true。 */
    private static final long WAKE_PROMPT_TIMEOUT_MS = 12_000L;
    private static final long WAKEUP_DETAIL_REUSE_MS = 3_000L;
    private static final long PCM_LOG_INTERVAL_MS = 1_000L;
    private static final int WAKEUP_CM_THRESHOLD = 650;
    private static final byte[] CM_THRESHOLD_KEY =
            "wdec_param_nCmThreshold".getBytes(StandardCharsets.US_ASCII);

    private static volatile FlyAiIVW sInstance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable wakePromptTimeoutRunnable = this::onWakePromptTimeout;
    private final VtnStepAudioAssembler vtnStepAssembler = new VtnStepAudioAssembler();

    private Context context;
    private FlyAiIVWListener listener;
    private MediaPlayer mediaPlayer;
    private IVtnHelper vtnApi;
    private VtnApiJni.VtnInteractParam vtnInteractParam;
    private AudioRecorder recorder;
    private int customWordResourceId = -1;
    private String workDir;
    private String[] keywords = DEFAULT_WORDS;
    private volatile boolean isWakePromptPlaying;
    private volatile boolean isAuthorized;
    private volatile WakeupInfo lastWakeupInfo;
    private volatile long lastWakeupInfoAtMs;
    private volatile WakeupInfo activeWakeupInfo;
    private long lastPcmInputLogAtMs;
    private long pcmInputCallbackCount;
    private long pcmInputBytes;
    private boolean firstPcmInputLogged;

    /** 注册后，VTN 降噪识别音频将转发给它。 */
    private volatile com.hivi.launcher.ai.audio.MicOpusStreamer micOpusStreamer;

    private final AudioData dataListener = new AudioData() {
        @Override
        public void onData(byte[] data, int dataLength) {
            if (vtnInteractParam == null || vtnApi == null) {
                AppLog.w(TAG, "drop PCM before VTN is ready: params="
                        + (vtnInteractParam != null) + ", engine=" + (vtnApi != null));
                return;
            }
            if (data == null || dataLength <= 0) {
                AppLog.w(TAG, "drop invalid PCM input: bytes=" + dataLength);
                return;
            }
            int actualLength = Math.min(dataLength, data.length);
            if (actualLength != dataLength) {
                AppLog.w(TAG, "PCM input length exceeds buffer: declared=" + dataLength
                        + ", actual=" + actualLength);
            }
            logPcmInput(actualLength);
            vtnStepAssembler.feed(data, actualLength, vtnInteractParam);
        }
    };

    private final VtnApiJni.VtnEventListener vtnListener = new VtnApiJni.VtnEventListener() {
        @Override
        public int onVtnEvent(int eventType, byte[] data, int dataLen, byte[] param, int paramLen) {
            switch (eventType) {
                case VTN_CALLBACK_TYPE_AUTHORIZATION:
                    handleAuthorizationEvent(data, dataLen);
                    break;
                case VTN_CALLBACK_TYPE_WAKEUP_DETAIL:
                    WakeupInfo detailInfo = parseWakeupInfo(data, dataLen, eventType, "detail");
                    if (detailInfo != null) {
                        rememberWakeupInfo(detailInfo);
                        AppLog.i(TAG, "VTN_WAKE_DETAIL " + detailInfo.toLogString());
                    } else {
                        AppLog.d(TAG, "VTN_WAKE_DETAIL without parseable result: "
                                + decodeVtnPayload(data, dataLen));
                    }
                    break;
                case VTN_CALLBACK_TYPE_WAKEUP:
                    WakeupInfo wakeupInfo = parseWakeupInfo(data, dataLen, eventType, "wakeup");
                    if (wakeupInfo != null) {
                        rememberWakeupInfo(wakeupInfo);
                        AppLog.i(TAG, "VTN_WAKE_FINAL " + wakeupInfo.toLogString());
                    } else {
                        wakeupInfo = getRecentWakeupInfo();
                        if (wakeupInfo != null) {
                            wakeupInfo = wakeupInfo.copyForStage(eventType, "wakeup_reuse_detail");
                            AppLog.i(TAG, "VTN_WAKE_FINAL reuseDetail " + wakeupInfo.toLogString());
                        } else {
                            AppLog.d(TAG, "VTN_WAKE_FINAL without parseable result: "
                                    + decodeVtnPayload(data, dataLen));
                        }
                    }
                    // 同一次物理唤醒可能先回调 detail 再回调 wakeup，只有 wakeup 才启动提示音流程。
                    if (!isConfiguredWakeWord(wakeupInfo)) {
                        AppLog.w(TAG, "ignore final wakeup from unsupported keyword: "
                                + (wakeupInfo == null ? "(no detail payload)"
                                : wakeupInfo.toLogString()));
                        break;
                    }
                    handleWakeup(wakeupInfo);
                    break;
                case VTN_CALLBACK_TYPE_AUDIO_REC:
                    com.hivi.launcher.ai.audio.MicOpusStreamer streamer = micOpusStreamer;
                    if (streamer != null && streamer.isRunning()) {
                        streamer.feedRecAudio(data, dataLen);
                    }
                    break;
                default:
                    AppLog.d(TAG, "VTN event ignored: type=" + eventType
                            + ", dataLen=" + dataLen + ", paramLen=" + paramLen);
                    break;
            }
            return 0;
        }
    };

    /**
     * 一次唤醒判决的详细信息，用于日志与上层决策。
     */
    public static final class WakeupInfo {
        public final int eventType;
        public final String callbackStage;
        public final int resultIndex;
        public final String sid;
        public final String version;
        public final String keyword;
        public final int ncm;
        public final int ncmThreshold;
        public final int resourceId;
        public final long iStart;
        public final int iDuration;
        public final long estimatedDurationMs;
        public final long fillerScore;
        public final long keywordScore;
        public final int startOffset;
        public final String rawJson;

        private WakeupInfo(int eventType, String callbackStage, int resultIndex, String sid,
                String version, String keyword, int ncm, int ncmThreshold, int resourceId,
                long iStart, int iDuration, long fillerScore, long keywordScore, int startOffset,
                String rawJson) {
            this.eventType = eventType;
            this.callbackStage = callbackStage;
            this.resultIndex = resultIndex;
            this.sid = sid;
            this.version = version;
            this.keyword = keyword;
            this.ncm = ncm;
            this.ncmThreshold = ncmThreshold;
            this.resourceId = resourceId;
            this.iStart = iStart;
            this.iDuration = iDuration;
            this.estimatedDurationMs = iDuration >= 0 ? iDuration * 10L : -1L;
            this.fillerScore = fillerScore;
            this.keywordScore = keywordScore;
            this.startOffset = startOffset;
            this.rawJson = rawJson;
        }

        private WakeupInfo copyForStage(int eventType, String callbackStage) {
            return new WakeupInfo(eventType, callbackStage, resultIndex, sid, version, keyword,
                    ncm, ncmThreshold, resourceId, iStart, iDuration, fillerScore, keywordScore,
                    startOffset, rawJson);
        }

        public String toLogString() {
            return "stage=" + callbackStage
                    + ", eventType=" + eventType
                    + ", resultIndex=" + resultIndex
                    + ", keyword=\"" + keyword + "\""
                    + ", ncm=" + ncm
                    + ", ncmThreshold=" + ncmThreshold
                    + ", resourceId=" + resourceId
                    + ", iStart=" + iStart
                    + ", iDuration=" + iDuration
                    + ", estimatedDurationMs=" + estimatedDurationMs
                    + ", keywordScore=" + keywordScore
                    + ", fillerScore=" + fillerScore
                    + ", startOffset=" + startOffset
                    + ", sid=" + sid
                    + ", version=" + version;
        }
    }

    public static String[] getDefaultWakeWords() {
        return DEFAULT_WORDS.clone();
    }

    public static synchronized FlyAiIVW getInstance(Context context, String[] words,
            FlyAiIVWListener listener) {
        if (sInstance == null) {
            sInstance = new FlyAiIVW(context, words, listener);
        } else {
            sInstance.bindContext(context, words, listener);
            if (sInstance.vtnApi == null || sInstance.vtnInteractParam == null) {
                sInstance.initSDK();
            }
            if (sInstance.isAuthorized) {
                sInstance.startRecord();
            }
        }
        return sInstance;
    }

    public static FlyAiIVW peekInstance() {
        return sInstance;
    }

    private FlyAiIVW(Context context, String[] words, FlyAiIVWListener listener) {
        bindContext(context, words, listener);
        initSDK();
    }

    private void bindContext(Context context, String[] words, FlyAiIVWListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.keywords = mergeWords(DEFAULT_WORDS, words);
        this.workDir = initWorkDir(this.context);
    }

    /**
     * 注册 MicOpusStreamer 以接收 VTN 降噪后的识别音频；传 null 取消转发。
     */
    public void setMicOpusStreamer(com.hivi.launcher.ai.audio.MicOpusStreamer streamer) {
        this.micOpusStreamer = streamer;
    }

    public boolean isAuthorized() {
        return isAuthorized;
    }

    private static String initWorkDir(Context context) {
        return context.getFilesDir().getAbsolutePath() + File.separator + "iflytek" + File.separator;
    }

    private void initSDK() {
        if (context == null) {
            return;
        }
        isAuthorized = false;
        AppLog.i(TAG, "initSDK begin: ALSA card=" + EngineConstants.CARD
                + ", device=" + EngineConstants.DEVICE
                + ", channels=" + EngineConstants.CHANNEL
                + ", sampleRate=" + EngineConstants.HW_SAMPLE_RATE
                + ", channelMap=" + EngineConstants.CHANNEL_PARAMS
                + ", wakeWords=" + buildKeywordCsv(keywords));
        copyAssetsIfNeeded();
        EngineConstants.serialNumber = "iflytek_test_sn2";
        vtnApi = VtnEngine.getInstance(
                context,
                EngineConstants.APPID,
                EngineConstants.serialNumber,
                RES_IDENTIFIER,
                workDir,
                SOLUTION_TYPE,
                vtnListener);
        if (vtnApi == null) {
            AppLog.e(TAG, "initSDK: 唤醒引擎初始化失败");
            return;
        }

        int micNum = VtnEngine.getMicNum();
        int refNum = VtnEngine.getRefNum();
        vtnInteractParam = VtnEngine.createAudioParams();
        if (vtnInteractParam == null || vtnInteractParam.getInRawObj() == null) {
            AppLog.e(TAG, "initSDK: 录音参数初始化失败");
            return;
        }

        if (recorder == null) {
            recorder = SingleAlsaRecorder.getInstance(dataListener, micNum, refNum);
        }
        byte[] inRaw = vtnInteractParam.getInRawObj();
        vtnStepAssembler.reset(inRaw.length, inRaw);
        AppLog.i(TAG, "initSDK success micNum=" + micNum + ", refNum=" + refNum
                + ", vtnStepBytes=" + inRaw.length);
    }

    public void updateKeywords(String[] words) {
        keywords = mergeWords(DEFAULT_WORDS, words);
        if (vtnApi == null || vtnInteractParam == null) {
            initSDK();
        }
        if (isAuthorized) {
            reloadCustomKeywords();
            startRecord();
        } else {
            AppLog.i(TAG, "updateKeywords deferred until authorization success");
        }
    }

    public void destroy() {
        AppLog.i(TAG, "destroy");
        stopRecord();
        vtnStepAssembler.clear();
        if (recorder != null) {
            recorder.destroyRecord();
            recorder = null;
        }
        removeCustomKeywords();
        if (vtnApi != null) {
            VtnEngine.destroy();
            vtnApi = null;
        }
        vtnInteractParam = null;
        isAuthorized = false;
        micOpusStreamer = null;
        mainHandler.removeCallbacks(wakePromptTimeoutRunnable);
        releaseMediaPlayerOnly();
        clearWakePromptOccupiedAndNotify();
        listener = null;
        context = null;
        sInstance = null;
    }

    private void startRecord() {
        if (recorder == null || vtnInteractParam == null) {
            return;
        }
        vtnStepAssembler.discardPending();
        int ret = recorder.startRecord();
        AppLog.i(TAG, "startRecord ret=" + ret);
    }

    private void stopRecord() {
        if (recorder != null) {
            try {
                recorder.stopRecord();
            } catch (Throwable throwable) {
                AppLog.w(TAG, "stopRecord failed", throwable);
            }
        }
        vtnStepAssembler.discardPending();
    }

    private void logPcmInput(int bytes) {
        pcmInputCallbackCount++;
        pcmInputBytes += bytes;
        long now = SystemClock.elapsedRealtime();
        if (!firstPcmInputLogged) {
            firstPcmInputLogged = true;
            AppLog.i(TAG, "PCM_TO_VTN first input: bytes=" + bytes
                    + ", expectedStepBytes=" + vtnStepAssembler.getStepBytes());
        }
        if (lastPcmInputLogAtMs != 0L && now - lastPcmInputLogAtMs < PCM_LOG_INTERVAL_MS) {
            return;
        }
        lastPcmInputLogAtMs = now;
        // AppLog.i(TAG, "PCM_TO_VTN input: callbacks=" + pcmInputCallbackCount
        //         + ", bytes=" + pcmInputBytes
        //         + ", currentBytes=" + bytes
        //         + ", expectedStepBytes=" + vtnStepAssembler.getStepBytes());
        pcmInputCallbackCount = 0L;
        pcmInputBytes = 0L;
    }

    private void handleWakeup(WakeupInfo wakeupInfo) {
        if (isWakePromptPlaying) {
            AppLog.w(TAG, "ignore wakeup: wake prompt is still playing");
            return;
        }
        mainHandler.removeCallbacks(wakePromptTimeoutRunnable);
        isWakePromptPlaying = true;
        activeWakeupInfo = wakeupInfo;
        AppLog.i(TAG, "APP_WAKE_HANDLE_START wakeInfo="
                + (wakeupInfo != null ? wakeupInfo.toLogString() : "null"));

        long delayMs = 0L;
        try {
            if (listener != null) {
                delayMs = listener.onFlyAIWakeupDetected(wakeupInfo);
            }
        } catch (Throwable throwable) {
            AppLog.e(TAG, "onFlyAIWakeupDetected error", throwable);
        }

        Runnable wakeAction = () -> {
            boolean shouldPlayPrompt = true;
            try {
                if (listener != null) {
                    shouldPlayPrompt = listener.onFlyAIPreWakeStop(wakeupInfo);
                }
            } catch (Throwable throwable) {
                AppLog.e(TAG, "onFlyAIPreWakeStop error", throwable);
            }
            if (shouldPlayPrompt) {
                playWakePrompt();
            } else {
                AppLog.i(TAG, "取消播放唤醒提示音");
                clearWakePromptOccupiedAndNotify();
            }
        };

        if (delayMs > 0L) {
            mainHandler.postDelayed(wakeAction, delayMs);
        } else {
            mainHandler.post(wakeAction);
        }
        mainHandler.postDelayed(wakePromptTimeoutRunnable, WAKE_PROMPT_TIMEOUT_MS + delayMs);
    }

    private boolean isConfiguredWakeWord(WakeupInfo wakeupInfo) {
        if (wakeupInfo == null || TextUtils.isEmpty(wakeupInfo.keyword) || keywords == null) {
            return false;
        }
        String detectedWord = wakeupInfo.keyword.trim();
        for (String configuredWord : keywords) {
            if (configuredWord != null && detectedWord.equals(configuredWord.trim())) {
                return true;
            }
        }
        return false;
    }

    private void onWakePromptTimeout() {
        if (!isWakePromptPlaying) {
            return;
        }
        AppLog.w(TAG, "唤醒提示音超时，强制结束占用");
        releaseMediaPlayerOnly();
        clearWakePromptOccupiedAndNotify();
    }

    /**
     * 解除「提示音流程」占用并通知上层（与正常播完一致）。可重复调用，幂等。
     */
    private void clearWakePromptOccupiedAndNotify() {
        mainHandler.removeCallbacks(wakePromptTimeoutRunnable);
        if (!isWakePromptPlaying) {
            return;
        }
        isWakePromptPlaying = false;
        WakeupInfo wakeupInfo = activeWakeupInfo;
        activeWakeupInfo = null;
        if (listener != null) {
            listener.onFlyAIResponse(wakeupInfo);
        }
    }

    private void rememberWakeupInfo(WakeupInfo wakeupInfo) {
        lastWakeupInfo = wakeupInfo;
        lastWakeupInfoAtMs = System.currentTimeMillis();
    }

    private WakeupInfo getRecentWakeupInfo() {
        WakeupInfo info = lastWakeupInfo;
        if (info == null) {
            return null;
        }
        return System.currentTimeMillis() - lastWakeupInfoAtMs <= WAKEUP_DETAIL_REUSE_MS
                ? info : null;
    }

    private String decodeVtnPayload(byte[] data, int dataLen) {
        if (data == null || dataLen <= 0) {
            return "";
        }
        int length = Math.min(dataLen, data.length);
        if (length > 0 && data[length - 1] == 0) {
            length--;
        }
        if (length <= 0) {
            return "";
        }
        for (int index = 0; index < length; index++) {
            int value = data[index] & 0xFF;
            if ((value < 0x20 && value != '\n' && value != '\r' && value != '\t')
                    || value == 0x7F) {
                return "(binary " + length + " bytes)";
            }
        }
        return new String(data, 0, length, StandardCharsets.UTF_8);
    }

    private WakeupInfo parseWakeupInfo(byte[] data, int dataLen, int eventType,
            String callbackStage) {
        String raw = decodeVtnPayload(data, dataLen);
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray results = root.optJSONArray("rlt");
            JSONObject best = null;
            int bestIndex = -1;
            if (results != null && results.length() > 0) {
                int bestNcm = Integer.MIN_VALUE;
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    int itemNcm = item.optInt("ncm", Integer.MIN_VALUE);
                    if (best == null || itemNcm > bestNcm) {
                        best = item;
                        bestIndex = i;
                        bestNcm = itemNcm;
                    }
                }
            } else {
                best = root.optJSONObject("params");
            }
            if (best == null) {
                return null;
            }
            int ncmThreshold = best.has("ncm_threshold")
                    ? best.optInt("ncm_threshold", -1)
                    : best.has("ncmThresh")
                            ? best.optInt("ncmThresh", -1)
                            : best.optInt("threshold", -1);
            return new WakeupInfo(
                    eventType,
                    callbackStage,
                    bestIndex,
                    best.optString("sid", ""),
                    best.optString("version", ""),
                    best.optString("keyword", ""),
                    best.has("ncm") ? best.optInt("ncm", -1) : best.optInt("score", -1),
                    ncmThreshold,
                    best.optInt("iresid", -1),
                    best.has("istart") ? best.optLong("istart", -1L)
                            : best.optLong("start_ms", -1L),
                    best.has("iduration") ? best.optInt("iduration", -1)
                            : best.optInt("end_ms", -1) - best.optInt("start_ms", -1),
                    best.optLong("nfillerscore", -1L),
                    best.optLong("nkeywordscore", -1L),
                    best.optInt("startOffset", -1),
                    raw);
        } catch (Throwable throwable) {
            AppLog.w(TAG, "parse wakeup info failed: " + raw, throwable);
            return null;
        }
    }

    private void handleAuthorizationEvent(byte[] data, int dataLen) {
        String authResult = decodeVtnPayload(data, dataLen);
        AppLog.i(TAG, "VTN authorization callback: " + authResult);
        if (!isAuthorizationSuccess(authResult)) {
            isAuthorized = false;
            AppLog.e(TAG, "VTN authorization failed: " + authResult);
            return;
        }
        isAuthorized = true;
        AppLog.i(TAG, "VTN authorization succeed: " + authResult);
        mainHandler.post(() -> {
            VtnEngine.throwsRecAudio();
            reloadCustomKeywords();
            if (listener != null) {
                listener.onInitSdkSuccess();
            }
            startRecord();
        });
    }

    private boolean isAuthorizationSuccess(String authResult) {
        if (TextUtils.isEmpty(authResult)) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(authResult);
            JSONObject params = root.optJSONObject("params");
            return params != null ? params.optInt("result", -1) == 0
                    : root.optInt("result", -1) == 0;
        } catch (Throwable throwable) {
            AppLog.w(TAG, "parse auth result failed: " + authResult, throwable);
            return authResult.contains("\"result\":0") || authResult.contains("\"result\": 0");
        }
    }

    private void playWakePrompt() {
        releaseMediaPlayerOnly();
        mediaPlayer = MediaPlayer.create(context, R.raw.ai_default_cn_female);
        if (mediaPlayer == null) {
            clearWakePromptOccupiedAndNotify();
            return;
        }
        mediaPlayer.setOnCompletionListener(player -> {
            releaseMediaPlayer(player);
            clearWakePromptOccupiedAndNotify();
        });
        mediaPlayer.setOnErrorListener((player, what, extra) -> {
            AppLog.e(TAG, "mediaPlayer onError " + what + " " + extra);
            releaseMediaPlayer(player);
            clearWakePromptOccupiedAndNotify();
            return true;
        });
        mediaPlayer.start();
    }

    private void releaseMediaPlayer(MediaPlayer player) {
        try {
            player.release();
        } catch (Throwable ignored) {
        }
        mediaPlayer = null;
    }

    private void reloadCustomKeywords() {
        if (vtnApi == null || TextUtils.isEmpty(workDir)) {
            return;
        }
        removeCustomKeywords();
        String keywordCsv = buildKeywordCsv(keywords);
        if (TextUtils.isEmpty(keywordCsv)) {
            return;
        }
        try {
            File keywordDir = new File(workDir, "userKeywordResource");
            if (!keywordDir.exists() && !keywordDir.mkdirs()) {
                AppLog.w(TAG, "reloadCustomKeywords: unable to create " + keywordDir);
                return;
            }
            File keywordResFile = new File(keywordDir, "my_custom_word_res.bin");
            if (!keywordResFile.exists() && !keywordResFile.createNewFile()) {
                AppLog.w(TAG, "reloadCustomKeywords: unable to create keyword resource file");
                return;
            }
            int genRet = VtnEngine.generateWakeKeyWordSource(
                    keywordResFile.getAbsolutePath(), keywordCsv);
            if (genRet != 0) {
                AppLog.w(TAG, "reloadCustomKeywords generate failed ret=" + genRet);
                return;
            }
            customWordResourceId =
                    VtnEngine.addGenerateWakeKeyWord(keywordResFile.getAbsolutePath());
            AppLog.i(TAG, "reloadCustomKeywords success id=" + customWordResourceId
                    + ", keywords=" + keywordCsv);
        } catch (IOException exception) {
            AppLog.e(TAG, "reloadCustomKeywords failed", exception);
        }
    }

    private void removeCustomKeywords() {
        if (customWordResourceId > 0) {
            VtnEngine.removeGenerateWakeKeyWord(customWordResourceId);
            customWordResourceId = -1;
        }
    }

    /**
     * 首次运行把 assets/res 复制到 workDir。
     *
     * <p>只判断 vtn.ini 是否存在不足以确认上次复制完整：若复制中途被杀，文件会留下正确长度
     * 但内容为空洞（全 0）的残留，引擎解析这种资源会在
     * {@code Wakeup_Word_Init} 里死循环。因此每次都校验关键资源，发现异常就整棵重建。</p>
     */
    private void copyAssetsIfNeeded() {
        File resDir = new File(workDir, "res");
        if (isCopiedResourceUsable(new File(resDir, "vtn/vtn.ini"))
                && isCopiedResourceUsable(
                        new File(resDir, "wake_word_evaluate/wakeup_word_score.bin"))) {
            ensureWakeupThreshold(new File(resDir, "ivw_3.17.12/ivw_g.cfg"));
            return;
        }
        AppLog.i(TAG, "copying VTN resources to " + resDir);
        deleteRecursively(resDir);
        try {
            copyAssetDirectory(context.getAssets(), "res", resDir);
            ensureWakeupThreshold(new File(resDir, "ivw_3.17.12/ivw_g.cfg"));
        } catch (IOException exception) {
            AppLog.e(TAG, "copyAssetsIfNeeded failed", exception);
            // 半成品资源比没有资源更危险，直接删掉，下次启动重新复制。
            deleteRecursively(resDir);
        }
    }

    /** 文件存在、非空，且开头不是全 0（空洞文件的特征）才认为可用。 */
    private boolean isCopiedResourceUsable(File file) {
        if (!file.isFile() || file.length() <= 0L) {
            return false;
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] head = new byte[64];
            int read = input.read(head);
            if (read <= 0) {
                return false;
            }
            for (int i = 0; i < read; i++) {
                if (head[i] != 0) {
                    return true;
                }
            }
            AppLog.w(TAG, "corrupted VTN resource (zero filled): " + file.getAbsolutePath());
            return false;
        } catch (IOException exception) {
            AppLog.w(TAG, "unable to verify " + file.getAbsolutePath(), exception);
            return false;
        }
    }

    private void ensureWakeupThreshold(File configFile) {
        if (configFile == null || !configFile.isFile()) {
            AppLog.w(TAG, "wakeup config missing: "
                    + (configFile == null ? "null" : configFile.getAbsolutePath()));
            return;
        }
        byte[] original;
        try {
            original = readFileBytes(configFile);
        } catch (IOException exception) {
            AppLog.w(TAG, "unable to read wakeup config", exception);
            return;
        }
        int keyStart = indexOf(original, CM_THRESHOLD_KEY);
        if (keyStart < 0) {
            AppLog.w(TAG, "wakeup threshold key missing: " + configFile.getAbsolutePath());
            return;
        }
        int equalsIndex = keyStart + CM_THRESHOLD_KEY.length;
        while (equalsIndex < original.length
                && (original[equalsIndex] == ' ' || original[equalsIndex] == '\t')) {
            equalsIndex++;
        }
        if (equalsIndex >= original.length || original[equalsIndex] != '=') {
            AppLog.w(TAG, "wakeup threshold syntax invalid: " + configFile.getAbsolutePath());
            return;
        }
        int lineEnd = equalsIndex + 1;
        while (lineEnd < original.length && original[lineEnd] != '\r'
                && original[lineEnd] != '\n') {
            lineEnd++;
        }
        String currentValue = new String(original, equalsIndex + 1,
                lineEnd - equalsIndex - 1, StandardCharsets.US_ASCII).trim();
        String desiredValue = String.valueOf(WAKEUP_CM_THRESHOLD);
        if (!desiredValue.equals(currentValue)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    original.length + desiredValue.length() + 1);
            output.write(original, 0, equalsIndex + 1);
            output.write(' ');
            byte[] valueBytes = desiredValue.getBytes(StandardCharsets.US_ASCII);
            output.write(valueBytes, 0, valueBytes.length);
            output.write(original, lineEnd, original.length - lineEnd);
            try (FileOutputStream stream = new FileOutputStream(configFile)) {
                output.writeTo(stream);
                stream.flush();
                stream.getFD().sync();
            } catch (IOException exception) {
                AppLog.w(TAG, "unable to update wakeup threshold", exception);
                return;
            }
            AppLog.i(TAG, "wakeup threshold updated: " + currentValue + " -> "
                    + desiredValue + ", file=" + configFile.getAbsolutePath());
        } else {
            AppLog.i(TAG, "wakeup threshold active: " + desiredValue
                    + ", file=" + configFile.getAbsolutePath());
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private int indexOf(byte[] source, byte[] target) {
        if (source == null || target == null || target.length == 0
                || source.length < target.length) {
            return -1;
        }
        int last = source.length - target.length;
        for (int i = 0; i <= last; i++) {
            boolean matched = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    private void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!target.delete()) {
            AppLog.w(TAG, "unable to delete " + target.getAbsolutePath());
        }
    }

    private void copyAssetDirectory(AssetManager assetManager, String assetPath, File dest)
            throws IOException {
        String[] children = assetManager.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assetManager, assetPath, dest);
            return;
        }
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("create dir failed: " + dest.getAbsolutePath());
        }
        for (String child : children) {
            copyAssetDirectory(assetManager, assetPath + "/" + child, new File(dest, child));
        }
    }

    private void copyAssetFile(AssetManager assetManager, String assetPath, File destFile)
            throws IOException {
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("create parent failed: " + parent.getAbsolutePath());
        }
        try (InputStream input = assetManager.open(assetPath);
             FileOutputStream output = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            // 落盘，避免掉电后留下长度正确但内容为空洞的资源文件。
            output.getFD().sync();
        }
    }

    private String[] mergeWords(String[] first, String[] second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addWords(merged, first);
        addWords(merged, second);
        return merged.toArray(new String[0]);
    }

    private void addWords(LinkedHashSet<String> out, String[] words) {
        if (words == null) {
            return;
        }
        for (String word : words) {
            if (word == null) {
                continue;
            }
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
    }

    private String buildKeywordCsv(String[] words) {
        if (words == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (TextUtils.isEmpty(word)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(word.trim());
        }
        return builder.toString();
    }

    private void releaseMediaPlayerOnly() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.release();
        } catch (Throwable throwable) {
            AppLog.w(TAG, "releaseMediaPlayerOnly failed", throwable);
        }
        mediaPlayer = null;
    }
}
