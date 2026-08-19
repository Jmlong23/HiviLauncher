package com.hivi.launcher.ai.presenter;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;

import com.hivi.launcher.R;
import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.ai.audio.AiWebSocketManager;
import com.hivi.launcher.ai.audio.MicOpusStreamer;
import com.hivi.launcher.ai.audio.OpusAudioPlayer;
import com.hivi.launcher.ai.iot.AiIotCommandExecutor;
import com.hivi.launcher.ai.ui.AiView;
import com.hivi.launcher.ai.wakeup.AiWakeupController;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.customview.ParticleVisualizerView;
import com.hivi.launcher.utils.Constants;
import com.hivi.launcher.utils.network.AuthorizationStore;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * AI voice conversation state machine.
 *
 * <p>It keeps the protocol used by HiviAudio (listen/start -> param(closeAudio=3) -> Opus
 * upstream, STT -> TTS/Opus downstream). The microphone audio comes from the ALSA multi-mic
 * array processed by the VTN front end ({@link MicOpusStreamer}), and voice wake-up events are
 * delivered through {@link AiWakeupController.Consumer}.</p>
 *
 * <p>The presenter is a process-wide singleton owned by the wakeup flow: voice wake-up first
 * drives the top-left listening bar through the headless view (no AI page), a normal dialogue
 * answer hands rendering over to {@link com.hivi.launcher.ai.ui.AiFragment}, and IoT commands
 * (volume / music transport) are executed in place without ever opening the AI page — the same
 * behaviour as HiviAudio.</p>
 */
public final class AiPresenter extends BasePresenter<AiView>
        implements AiWakeupController.Consumer {
    private static final String TAG = "AiConversationPresenter";
    private static final String SESSION_PREFERENCES = "ai_conversation";
    private static final String BOOT_WALL_TIME_KEY = "boot_wall_time";
    private static final String BOOT_ELAPSED_TIME_KEY = "boot_elapsed_time";
    private static final int DEFAULT_PARTICLE_ROLE_ID = 476;
    /** IoT 提示音播完后的会话收尾兜底超时（与 HiviAudio AUDIO_PLAYBACK_WAIT_TIMEOUT_MS 一致）。 */
    private static final long IOT_SESSION_FINISH_TIMEOUT_MS = 30_000L;
    /**
     * 监听中无输入的收尾超时。HiviAudio 依赖服务端"无输入超时断开"来隐藏悬浮条，
     * 这里再兜底一次，保证服务端不断开时悬浮条也能按时隐藏。
     */
    private static final long LISTENING_NO_INPUT_TIMEOUT_MS = 20_000L;
    /** 收到 STT 后等待 TTS 开始的超时。 */
    private static final long THINKING_TIMEOUT_MS = 20_000L;
    /** TTS 播放无进展的兜底超时（与 HiviAudio AUDIO_PLAYBACK_WAIT_TIMEOUT_MS 一致）。 */
    private static final long SPEAKING_TIMEOUT_MS = 30_000L;

    private static AiPresenter sSharedInstance;

    private final Context mContext;
    private final AiWakeupController mWakeupController;
    /** 会话无进展看门狗：超时未收到 STT/TTS 即收尾会话并隐藏悬浮条。 */
    private final Handler mSessionWatchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable mSessionWatchdogRunnable;
    private AiWebSocketManager mWebSocketManager;
    private MicOpusStreamer mMicrophoneStreamer;
    private OpusAudioPlayer mAudioPlayer;

    private ParticleVisualizerView.State mParticleState = ParticleVisualizerView.State.IDLE;
    private String mLastStatusText = "";
    private boolean mInitialized;
    private boolean mReleased;
    private boolean mWaitingForRecordPermission;
    private boolean mTtsStopped;
    private boolean mInterruptPending;
    private boolean mWakePromptPlaying;
    private boolean mAwaitingAudioUploadReady;
    private boolean mIotCommandActive;
    private Integer mPendingIotVolume;
    /** 提示语已通过 detect 发给服务端，等待其 TTS 音频回来（同 HiviAudio pendingDisconnectAfterIotPromptTts）。 */
    private boolean mIotPromptPending;
    /** 提示音 TTS 文本/音频已到达，播完即可收尾。 */
    private boolean mIotPromptArrived;
    private int mCallbackGeneration;
    private String mLastUserCommand = "";
    /** 已下发的回答正文缓冲，headless → AI 页切换时回放给新视图。 */
    private String mAssistantResponseBuffer = "";

    public AiPresenter(Context context, AiView view) {
        super(view);
        mContext = context.getApplicationContext();
        mWakeupController = AiWakeupController.getInstance(mContext);
    }

    /**
     * 进程级共享实例：MainActivity 启动时以 headless 视图创建并常驻注册为唤醒 Consumer；
     * AiFragment 显示时把自己的视图挂上来接管渲染。
     */
    public static AiPresenter obtainShared(Context context, AiView initialView) {
        if (sSharedInstance == null) {
            synchronized (AiPresenter.class) {
                if (sSharedInstance == null) {
                    AiPresenter presenter =
                            new AiPresenter(context.getApplicationContext(), initialView);
                    presenter.initAsConversationOwner();
                    sSharedInstance = presenter;
                }
            }
        }
        sSharedInstance.attachConversationView(initialView);
        return sSharedInstance;
    }

    public static AiPresenter peekShared() {
        return sSharedInstance;
    }

    private void initAsConversationOwner() {
        mInitialized = true;
        mWakeupController.setConsumer(this);
    }

    /** 挂接新的渲染视图（headless ↔ AiFragment 切换）并回放当前状态与已显示的回答。 */
    public void attachConversationView(AiView view) {
        attach(view);
        renderState(mParticleState, mLastStatusText);
        if (!TextUtils.isEmpty(mAssistantResponseBuffer)) {
            view.clearAssistantResponse();
            view.appendAssistantResponse(mAssistantResponseBuffer);
        }
    }

    public void init() {
        if (!mInitialized) {
            initAsConversationOwner();
        }
        if (mWebSocketManager == null) {
            renderState(ParticleVisualizerView.State.IDLE, R.string.ai_conversation_welcome);
            clearAssistantResponse();
        }
    }

    // ────────────────── 唤醒回调（AiWakeupController.Consumer） ──────────────────

    /**
     * 唤醒提示音开始播放：提前建立会话，并把上行改为静音帧，避免唤醒词与提示音被上传。
     */
    @Override
    public void onWakeupPromptStarted() {
        runOnUiThread(() -> {
            if (mReleased) {
                return;
            }
            // 新一轮唤醒：清掉上一轮 IoT 遗留的抑制与延迟音量状态（同 HiviAudio）。
            mIotCommandActive = false;
            mPendingIotVolume = null;
            mIotPromptPending = false;
            mIotPromptArrived = false;
            mWakePromptPlaying = true;
            scheduleSessionWatchdog(LISTENING_NO_INPUT_TIMEOUT_MS, "wakePrompt");
            if (mAudioPlayer != null) {
                mAudioPlayer.stop();
            }
            if (mWebSocketManager == null) {
                openConversation();
            } else {
                boolean microphoneOpen = mMicrophoneStreamer != null
                        && mMicrophoneStreamer.isRunning()
                        && mMicrophoneStreamer.isAudioSendingEnabled();
                mTtsStopped = false;
                if (mMicrophoneStreamer != null && mMicrophoneStreamer.isRunning()) {
                    mMicrophoneStreamer.setForceSendSilence(true);
                }
                boolean canAbortActiveConversation = mWebSocketManager.isConnected()
                        && !mAwaitingAudioUploadReady;
                mInterruptPending = canAbortActiveConversation;
                if (canAbortActiveConversation) {
                    AppLog.i(TAG, "wake prompt: abort active AI session, microphoneOpen="
                            + microphoneOpen);
                    mWebSocketManager.sendAbort(microphoneOpen);
                } else {
                    AppLog.i(TAG, "wake prompt: waiting for initial AI audio handshake");
                }
                renderState(ParticleVisualizerView.State.LISTENING,
                        R.string.ai_conversation_preparing);
            }
        });
    }

    /**
     * 唤醒提示音播放完毕，开麦上传。
     */
    @Override
    public void onWakeupMicrophoneReady() {
        runOnUiThread(() -> {
            if (mReleased) {
                return;
            }
            mWakePromptPlaying = false;
            if (mWebSocketManager == null) {
                openConversation();
                return;
            }
            enableListening();
        });
    }

    /**
     * Removes the persisted AI session timestamps created by this presenter.
     */
    public static boolean clearPersistedSession(Context context) {
        if (context == null) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        Context preferencesContext = applicationContext == null ? context : applicationContext;
        return preferencesContext.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    public void onParticleClicked() {
        if (mReleased) {
            return;
        }
        if (!AuthorizationStore.hasToken(mContext)) {
            renderState(ParticleVisualizerView.State.IDLE, R.string.ai_conversation_welcome);
            AiView view = getView();
            if (view != null) {
                view.showToast(mContext.getString(R.string.ai_conversation_authorize_required));
            }
            return;
        }
        if (mContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            mWaitingForRecordPermission = true;
            AiView view = getView();
            if (view != null) {
                view.requestRecordAudioPermission();
            }
            return;
        }

        if (mWebSocketManager == null) {
            openConversation();
        } else if (mParticleState == ParticleVisualizerView.State.THINKING
                || mParticleState == ParticleVisualizerView.State.SPEAKING) {
            interruptAndResumeListening();
        }
    }

    public void onRecordAudioPermissionResult(boolean granted) {
        if (!mWaitingForRecordPermission || mReleased) {
            return;
        }
        mWaitingForRecordPermission = false;
        if (granted) {
            openConversation();
            return;
        }
        renderState(ParticleVisualizerView.State.IDLE, R.string.ai_conversation_welcome);
        AiView view = getView();
        if (view != null) {
            view.showToast(mContext.getString(R.string.ai_conversation_microphone_denied));
        }
    }

    /**
     * 结束当前会话（停麦、断开 WebSocket），但保留唤醒 Consumer 注册，
     * 下一次唤醒直接重新开场。离开 AI 页 / IoT 收尾 / 服务端断开都会走到这里。
     */
    public void endConversation() {
        mWaitingForRecordPermission = false;
        mInterruptPending = false;
        mTtsStopped = false;
        mWakePromptPlaying = false;
        mAwaitingAudioUploadReady = false;
        mIotCommandActive = false;
        mPendingIotVolume = null;
        mIotPromptPending = false;
        mIotPromptArrived = false;
        mCallbackGeneration++;
        cancelSessionWatchdog();
        stopAudioResources();
        releaseWebSocket();
        mLastUserCommand = "";
        mAssistantResponseBuffer = "";
        renderState(ParticleVisualizerView.State.IDLE, R.string.ai_conversation_welcome);
    }

    /**
     * 会话无进展看门狗：HiviAudio 靠服务端超时断开来隐藏悬浮条（其本地超时代码从未启用），
     * 这里在监听无输入、思考无响应、播报无进展时兜底收尾，行为对齐但不再依赖服务端。
     */
    private void scheduleSessionWatchdog(long delayMs, String reason) {
        cancelSessionWatchdog();
        mSessionWatchdogRunnable = () -> {
            mSessionWatchdogRunnable = null;
            if (mReleased) {
                return;
            }
            AppLog.w(TAG, "AI session watchdog fired: " + reason);
            endConversation();
            AiView view = getView();
            if (view != null) {
                view.requestHomeNavigation();
            }
        };
        mSessionWatchdogHandler.postDelayed(mSessionWatchdogRunnable, delayMs);
    }

    private void cancelSessionWatchdog() {
        if (mSessionWatchdogRunnable != null) {
            mSessionWatchdogHandler.removeCallbacks(mSessionWatchdogRunnable);
            mSessionWatchdogRunnable = null;
        }
    }

    /** 完全销毁并注销唤醒 Consumer，仅进程退出级别使用。 */
    public void release() {
        if (mReleased) {
            return;
        }
        mReleased = true;
        mWakeupController.clearConsumer(this);
        endConversation();
    }

    private void openConversation() {
        if (mReleased || mWebSocketManager != null) {
            return;
        }
        final int generation = ++mCallbackGeneration;
        mAwaitingAudioUploadReady = true;
        scheduleSessionWatchdog(LISTENING_NO_INPUT_TIMEOUT_MS, "connecting");
        renderState(ParticleVisualizerView.State.LISTENING,
                R.string.ai_conversation_connecting);

        ensureAudioPlayer();
        AiWebSocketManager manager = new AiWebSocketManager(AuthorizationStore.getToken(mContext),
                Constants.getCurrentWsUrl(mContext));
        mWebSocketManager = manager;
        manager.setListener(new AiWebSocketManager.Listener() {
            @Override
            public void onSessionCreated(String sessionId) {
                dispatch(generation, () -> AppLog.d(TAG, "AI session created"));
            }

            @Override
            public void onSttResult(String text) {
                dispatch(generation, () -> {
                    if (mInterruptPending) {
                        return;
                    }
                    setMicrophoneSendingEnabled(false);
                    scheduleSessionWatchdog(THINKING_TIMEOUT_MS, "thinking");
                    String result = TextUtils.isEmpty(text)
                            ? mContext.getString(R.string.ai_conversation_recognizing)
                            : text;
                    mLastUserCommand = result;
                    clearAssistantResponse();
                    renderState(ParticleVisualizerView.State.THINKING, result);
                });
            }

            @Override
            public void onTtsStarted(String text) {
                dispatch(generation, () -> {
                    if (mInterruptPending) {
                        return;
                    }
                    mTtsStopped = false;
                    setMicrophoneSendingEnabled(false);
                    if (mIotCommandActive) {
                        // IoT 提示音 TTS：不展示 AI 页面与文案，仅播放（同 HiviAudio 抑制逻辑）。
                        return;
                    }
                    scheduleSessionWatchdog(SPEAKING_TIMEOUT_MS, "speaking");
                    clearAssistantResponse();
                    renderState(ParticleVisualizerView.State.SPEAKING,
                            getCurrentConversationStatus());
                });
            }

            @Override
            public void onTtsSentenceStarted(String text) {
                dispatch(generation, () -> {
                    if (mInterruptPending) {
                        return;
                    }
                    if (AiIotCommandExecutor.isIotCommandText(text)) {
                        handleIotCommand(text);
                        return;
                    }
                    if (mIotCommandActive) {
                        // IoT 提示音的正文（如"声音已调到50"）已到达：不上屏、不进 AI 页，
                        // 音频照常播放，播完后收尾会话。
                        mIotPromptArrived = true;
                        return;
                    }
                    scheduleSessionWatchdog(SPEAKING_TIMEOUT_MS, "speaking");
                    String displayText = extractDisplayText(text);
                    if (!TextUtils.isEmpty(displayText)) {
                        renderState(ParticleVisualizerView.State.SPEAKING,
                                getCurrentConversationStatus());
                        appendAssistantResponse(displayText);
                    }
                });
            }

            @Override
            public void onTtsStopped() {
                dispatch(generation, () -> {
                    if (mInterruptPending) {
                        return;
                    }
                    mTtsStopped = true;
                    resumeListeningAfterPlayback();
                });
            }

            @Override
            public void onAudioData(byte[] audioData) {
                if (mInterruptPending || audioData == null || audioData.length == 0) {
                    return;
                }
                ensureAudioPlayer();
                mAudioPlayer.play(audioData);
                dispatch(generation, () -> {
                    if (mInterruptPending) {
                        return;
                    }
                    if (mIotCommandActive) {
                        // 提示音音频开始到达（部分回包可能没有 sentence_start）。
                        mIotPromptArrived = true;
                    }
                    scheduleSessionWatchdog(SPEAKING_TIMEOUT_MS, "speaking");
                    setMicrophoneSendingEnabled(false);
                    if (mParticleState != ParticleVisualizerView.State.SPEAKING) {
                        renderState(ParticleVisualizerView.State.SPEAKING,
                                getCurrentConversationStatus());
                    }
                });
            }

            @Override
            public void onAbort() {
                dispatch(generation, () -> {
                    if (mAudioPlayer != null) {
                        mAudioPlayer.stop();
                    }
                    mInterruptPending = false;
                    enableListening();
                });
            }

            @Override
            public void onListenStateChanged(String state, String mode, boolean executed) {
                dispatch(generation, () -> {
                    if ("start".equals(state) && executed) {
                        if (!ensureMicrophoneStarted()) {
                            showConversationError(R.string.ai_conversation_microphone_error);
                            return;
                        }
                        renderState(ParticleVisualizerView.State.LISTENING,
                                R.string.ai_conversation_preparing);
                    } else if ("stop".equals(state) || ("start".equals(state) && !executed)) {
                        setMicrophoneSendingEnabled(false);
                    }
                });
            }

            @Override
            public void onAudioUploadReady() {
                dispatch(generation, () -> {
                    mAwaitingAudioUploadReady = false;
                    enableListening();
                });
            }

            @Override
            public void onError(String message) {
                dispatch(generation, () -> {
                    AppLog.w(TAG, "AI conversation error: " + message);
                    showConversationError(resolveErrorMessage(message));
                });
            }

            @Override
            public void onClosed() {
                dispatch(generation, () -> {
                    if (mReleased) {
                        return;
                    }
                    AppLog.i(TAG, "AI conversation closed by service");
                    endConversation();
                    AiView view = getView();
                    if (view != null) {
                        view.requestHomeNavigation();
                    }
                });
            }

            @Override
            public void onAuthorizationRejected() {
                dispatch(generation, () -> {
                    AppLog.w(TAG, "AI authorization rejected by the active server, clearing local token");
                    AuthorizationStore.clearToken(mContext);
                    showConversationError(R.string.ai_conversation_authorize_required);
                });
            }
        });
        manager.connect(buildWebSocketHeaders());
    }

    // ────────────────── IoT 指令（不进入 AI 页面） ──────────────────

    private void handleIotCommand(String text) {
        AppLog.i(TAG, "AI IoT command received, executing without opening the AI page");
        mIotCommandActive = true;
        // IoT 分支自带提示音播完后的 30 秒收尾兜底，看门狗无需重复计时。
        cancelSessionWatchdog();
        setMicrophoneSendingEnabled(false);
        AiIotCommandExecutor.Result result = AiIotCommandExecutor.execute(mContext, text);
        mPendingIotVolume = result.deferredVolume;
        AiView view = getView();
        if (view != null) {
            view.onIotCommandHandled();
        }
        if (!TextUtils.isEmpty(result.promptText) && mWebSocketManager != null) {
            // 提示语走 WS detect 文本，由服务器合成 TTS 播报（同 HiviAudio）。
            // 原始回答的 tts stop 会先于提示音 TTS 到达，需等提示音播完再收尾。
            mIotPromptPending = true;
            mIotPromptArrived = false;
            mWebSocketManager.sendTextMessage(result.promptText);
            final int generation = mCallbackGeneration;
            runOnUiThreadDelayed(() -> {
                if (!mReleased && generation == mCallbackGeneration && mIotCommandActive) {
                    AppLog.w(TAG, "IoT prompt playback timeout, finishing session anyway");
                    finishIotCommandSession();
                }
            }, IOT_SESSION_FINISH_TIMEOUT_MS);
        } else {
            mIotPromptPending = false;
            mIotPromptArrived = false;
            finishIotCommandSession();
        }
    }

    private void finishIotCommandSession() {
        if (mPendingIotVolume != null) {
            AiIotCommandExecutor.applyVolume(mPendingIotVolume);
            mPendingIotVolume = null;
        }
        boolean wasIot = mIotCommandActive;
        mIotCommandActive = false;
        mIotPromptPending = false;
        mIotPromptArrived = false;
        AiView view = getView();
        if (view != null && view.isConversationPageActive()) {
            // AI 页面上收到 IoT 指令：仅执行指令与提示音，之后继续对话，不退出页面。
            enableListening();
            return;
        }
        endConversation();
        if (wasIot && view != null) {
            view.requestHomeNavigation();
        }
    }

    private void interruptAndResumeListening() {
        AiWebSocketManager manager = mWebSocketManager;
        if (manager == null || mInterruptPending) {
            return;
        }
        mInterruptPending = true;
        mTtsStopped = false;
        // 打断时是否处于开麦状态决定 abort 是否携带 mode=2（服务端会收到唤醒词音频）。
        boolean microphoneOpen = mMicrophoneStreamer != null
                && mMicrophoneStreamer.isRunning()
                && mMicrophoneStreamer.isAudioSendingEnabled();
        setMicrophoneSendingEnabled(false);
        if (mAudioPlayer != null) {
            mAudioPlayer.stop();
        }
        renderState(ParticleVisualizerView.State.LISTENING,
                R.string.ai_conversation_interrupting);
        manager.sendAbort(microphoneOpen);

        final int generation = mCallbackGeneration;
        runOnUiThreadDelayed(() -> {
            if (!mReleased && generation == mCallbackGeneration && mInterruptPending) {
                mInterruptPending = false;
                enableListening();
            }
        }, 1_200L);
    }

    private void enableListening() {
        if (mReleased || mWebSocketManager == null) {
            return;
        }
        if (!ensureMicrophoneStarted()) {
            showConversationError(R.string.ai_conversation_microphone_error);
            return;
        }
        if (mWakePromptPlaying || mInterruptPending || mAwaitingAudioUploadReady) {
            if (mMicrophoneStreamer != null && mMicrophoneStreamer.isRunning()) {
                mMicrophoneStreamer.setForceSendSilence(true);
            }
            AppLog.i(TAG, "microphone upload held: wakePrompt=" + mWakePromptPlaying
                    + ", interruptPending=" + mInterruptPending
                    + ", awaitingAudioReady=" + mAwaitingAudioUploadReady);
            scheduleSessionWatchdog(LISTENING_NO_INPUT_TIMEOUT_MS, "listeningHeld");
            renderState(ParticleVisualizerView.State.LISTENING,
                    R.string.ai_conversation_preparing);
            return;
        }
        mWebSocketManager.enableAudioSending();
        if (mMicrophoneStreamer != null && mMicrophoneStreamer.isRunning()) {
            mMicrophoneStreamer.setForceSendSilence(false);
        }
        setMicrophoneSendingEnabled(true);
        mTtsStopped = false;
        scheduleSessionWatchdog(LISTENING_NO_INPUT_TIMEOUT_MS, "listening");
        renderState(ParticleVisualizerView.State.LISTENING,
                R.string.ai_conversation_listening);
    }

    private void resumeListeningAfterPlayback() {
        if (!mTtsStopped || (mAudioPlayer != null && mAudioPlayer.isPlaying())) {
            // 音频尚未播完：OpusAudioPlayer 的完成回调会再次进入这里。
            return;
        }
        if (mIotCommandActive) {
            if (mIotPromptPending && !mIotPromptArrived) {
                // 原始回答已结束，但提示音 TTS 尚未回来：继续等待（30 秒兜底超时）。
                return;
            }
            finishIotCommandSession();
            return;
        }
        enableListening();
    }

    private boolean ensureMicrophoneStarted() {
        if (mMicrophoneStreamer != null && mMicrophoneStreamer.isRunning()) {
            return true;
        }
        AiWebSocketManager manager = mWebSocketManager;
        if (manager == null) {
            return false;
        }
        MicOpusStreamer streamer = new MicOpusStreamer(manager::sendAudio,
                volume -> runOnUiThread(() -> {
                    if (!mReleased && mParticleState == ParticleVisualizerView.State.LISTENING) {
                        AiView view = getView();
                        if (view != null) {
                            view.setParticleVolume(volume);
                        }
                    }
                }));
        if (!streamer.start()) {
            return false;
        }
        mMicrophoneStreamer = streamer;
        // 让 VTN 的降噪识别音频回调（兜底路径）也能进入编码器。
        mWakeupController.attachStreamer(streamer);
        if (mWakePromptPlaying || mInterruptPending || mAwaitingAudioUploadReady) {
            streamer.setForceSendSilence(true);
        }
        return true;
    }

    private void ensureAudioPlayer() {
        if (mAudioPlayer == null) {
            mAudioPlayer = new OpusAudioPlayer(() -> runOnUiThread(this::resumeListeningAfterPlayback));
            // 同 HiviAudio：TTS 播报期间由播放器心跳驱动 WS ping 保活；监听阶段不发 ping，
            // 服务端才能对无输入的会话超时断开，唤醒悬浮条随之隐藏。
            mAudioPlayer.setHeartbeatAction(() -> {
                AiWebSocketManager manager = mWebSocketManager;
                if (manager != null) {
                    manager.sendPing();
                }
            });
        }
    }

    private void setMicrophoneSendingEnabled(boolean enabled) {
        if (mMicrophoneStreamer != null) {
            mMicrophoneStreamer.setAudioSendingEnabled(enabled);
        }
        AiView view = getView();
        if (!enabled && view != null) {
            view.setParticleVolume(0f);
        }
    }

    private void showConversationError(int messageResId) {
        if (mReleased) {
            return;
        }
        mCallbackGeneration++;
        mInterruptPending = false;
        mTtsStopped = false;
        mAwaitingAudioUploadReady = false;
        mIotCommandActive = false;
        mPendingIotVolume = null;
        mIotPromptPending = false;
        mIotPromptArrived = false;
        cancelSessionWatchdog();
        stopAudioResources();
        releaseWebSocket();
        renderState(ParticleVisualizerView.State.IDLE, R.string.ai_conversation_unavailable);
        AiView view = getView();
        if (view != null) {
            view.showToast(mContext.getString(messageResId));
        }
    }

    private int resolveErrorMessage(String message) {
        if (!TextUtils.isEmpty(message)
                && (message.contains("quota") || message.contains("余额") || message.contains("额度"))) {
            return R.string.ai_conversation_quota_error;
        }
        return R.string.ai_conversation_connection_error;
    }

    private void stopAudioResources() {
        if (mMicrophoneStreamer != null) {
            mWakeupController.attachStreamer(null);
            mMicrophoneStreamer.stop();
            mMicrophoneStreamer = null;
        }
        if (mAudioPlayer != null) {
            mAudioPlayer.release();
            mAudioPlayer = null;
        }
    }

    private void releaseWebSocket() {
        if (mWebSocketManager != null) {
            mWebSocketManager.release();
            mWebSocketManager = null;
        }
    }

    private void dispatch(int generation, Runnable action) {
        runOnUiThread(() -> {
            if (!mReleased && generation == mCallbackGeneration) {
                action.run();
            }
        });
    }

    private void renderState(ParticleVisualizerView.State state, int statusResId) {
        renderState(state, mContext.getString(statusResId));
    }

    private void renderState(ParticleVisualizerView.State state, String statusText) {
        mParticleState = state;
        mLastStatusText = statusText == null ? "" : statusText;
        AiView view = getView();
        if (view != null) {
            view.renderConversationState(state, statusText);
        }
    }

    private void clearAssistantResponse() {
        mAssistantResponseBuffer = "";
        AiView view = getView();
        if (view != null) {
            view.clearAssistantResponse();
        }
    }

    private void appendAssistantResponse(String responseText) {
        if (!TextUtils.isEmpty(responseText)) {
            mAssistantResponseBuffer += responseText;
        }
        AiView view = getView();
        if (view != null) {
            view.appendAssistantResponse(responseText);
        }
    }

    private String getCurrentConversationStatus() {
        return TextUtils.isEmpty(mLastUserCommand)
                ? mContext.getString(R.string.ai_conversation_answering)
                : mLastUserCommand;
    }

    private String extractDisplayText(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            JSONObject object = new JSONObject(trimmed);
            String[] keys = new String[] {"text", "message", "content", "answer"};
            for (String key : keys) {
                String value = object.optString(key);
                if (!TextUtils.isEmpty(value) && !value.startsWith("{")) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // If the service sends non-JSON text that begins with "{", do not expose the payload.
        }
        return "";
    }

    private Map<String, String> buildWebSocketHeaders() {
        Map<String, String> headers = new HashMap<>();
        String uuid = SWDeviceStatus.getUUID().toString();
        AuthorizedUserInfo userInfo = AuthorizationStore.getUserInfo(mContext);
        String accountId = userInfo == null ? "" : userInfo.getId();
        headers.put("sessionId", uuid + (accountId == null ? "" : accountId.trim())
                + getBootWallTimeMs());
        headers.put("modelId", "");
        headers.put("voiceId", "");
        headers.put("closeAudio", "3");
        headers.put("iso", "0");
        headers.put("chipModelName", Build.HARDWARE);
        headers.put("version", Build.DISPLAY);
        headers.put("type", "MG100");
        headers.put("uuid", uuid);
        headers.put("roleId", String.valueOf(DEFAULT_PARTICLE_ROLE_ID));

        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            try {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    String ssid = wifiInfo.getSSID();
                    if (!TextUtils.isEmpty(ssid) && !"<unknown ssid>".equals(ssid)) {
                        headers.put("wifiName", ssid.replace("\"", ""));
                    }
                    int ip = wifiInfo.getIpAddress();
                    if (ip != 0) {
                        headers.put("ip", String.format("%d.%d.%d.%d", ip & 0xFF,
                                ip >> 8 & 0xFF, ip >> 16 & 0xFF, ip >> 24 & 0xFF));
                    }
                }
            } catch (SecurityException ignored) {
                // Wi-Fi context is optional for an AI conversation.
            }
        }
        return headers;
    }

    private long getBootWallTimeMs() {
        SharedPreferences preferences = mContext.getSharedPreferences(SESSION_PREFERENCES,
                Context.MODE_PRIVATE);
        long currentElapsed = SystemClock.elapsedRealtime();
        long storedElapsed = preferences.getLong(BOOT_ELAPSED_TIME_KEY, -1L);
        long storedBootWallTime = preferences.getLong(BOOT_WALL_TIME_KEY, -1L);
        if (storedBootWallTime <= 0L || storedElapsed < 0L || currentElapsed < storedElapsed) {
            long bootWallTime = System.currentTimeMillis() - currentElapsed;
            preferences.edit()
                    .putLong(BOOT_WALL_TIME_KEY, bootWallTime)
                    .putLong(BOOT_ELAPSED_TIME_KEY, currentElapsed)
                    .apply();
            return bootWallTime;
        }
        return storedBootWallTime;
    }
}
