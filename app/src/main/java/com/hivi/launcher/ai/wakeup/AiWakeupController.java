package com.hivi.launcher.ai.wakeup;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import com.hivi.launcher.ai.audio.MicOpusStreamer;
import com.hivi.launcher.R;
import com.hivi.launcher.utils.log.AppLog;
import com.hivi.launcher.utils.network.AuthorizationStore;

/**
 * 唤醒链路与 Launcher UI 的桥梁：进程内单例，持有 {@link FlyAiIVW}。
 *
 * <p>职责划分：</p>
 * <ul>
 *   <li>{@link Navigator}（MainActivity 实现）—— 被唤醒时把界面切到 AI 页；</li>
 *   <li>{@link Consumer}（AiPresenter 实现）—— 建立 WebSocket、提示音结束后开麦。</li>
 * </ul>
 *
 * <p>唤醒时序：VTN 判决 → {@link #onFlyAIWakeupDetected} 请求跳转 AI 页并返回等待时长
 * → {@link #onFlyAIPreWakeStop} 校验网络/授权并通知 Consumer 预热
 * → 播放唤醒提示音 → {@link #onFlyAIResponse} 通知 Consumer 开麦。</p>
 */
public final class AiWakeupController implements FlyAiIVWListener {
    private static final String TAG = "AiWakeupController";
    /** 唤醒后等待 AI 页面就绪（Fragment 事务 + 粒子预热）再播放提示音。 */
    private static final long NAVIGATION_SETTLE_DELAY_MS = 400L;

    private static volatile AiWakeupController sInstance;

    /** 被唤醒时负责把界面切到 AI 页。 */
    public interface Navigator {
        /**
         * @return true 表示已经（或即将）显示 AI 页，唤醒流程继续
         */
        boolean onWakeupRequestAiPage();

        /** 网络或授权不满足时提示用户。 */
        void onWakeupRejected(Reason reason);
    }

    /** AI 会话侧回调，由 AiPresenter 实现。 */
    public interface Consumer {
        /** 提示音即将播放：可提前建立 WebSocket，并屏蔽唤醒词上传。 */
        void onWakeupPromptStarted();

        /** 提示音播放结束（或被取消/超时）：此时开麦上传。 */
        void onWakeupMicrophoneReady();
    }

    public enum Reason {
        NO_NETWORK,
        NOT_AUTHORIZED
    }

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private volatile Navigator mNavigator;
    private volatile Consumer mConsumer;
    private volatile boolean mStarted;
    private volatile boolean mWakeConversationAccepted;
    /** 提示音播完时 AI 页尚未就绪，等 Consumer 挂上后补发开麦。 */
    private volatile boolean mPendingMicrophoneReady;
    private volatile ConnectivityManager.NetworkCallback mNetworkCallback;
    private MediaPlayer mHintPlayer;

    private AiWakeupController(Context context) {
        mContext = context.getApplicationContext();
    }

    public static AiWakeupController getInstance(Context context) {
        AiWakeupController instance = sInstance;
        if (instance == null) {
            synchronized (AiWakeupController.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new AiWakeupController(context);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    public static AiWakeupController peekInstance() {
        return sInstance;
    }

    // ────────────────────── 生命周期 ──────────────────────

    /**
     * 启动唤醒引擎（ALSA 采集 + VTN），可重复调用。
     *
     * <p>VTN 初始化包含联网鉴权，断网时原生层会无限重试（每次都打
     * {@code V3_VTN_AUTHORIZE failed(1002) on post}），既拿不到授权也白耗 CPU，
     * 因此必须等网络就绪再初始化。此处注册网络监听，联网后自动补启动。</p>
     */
    public void start() {
        registerNetworkCallback();
        if (isNetworkAvailable()) {
            AppLog.i(TAG, "validated network available; starting wake SDK initialization");
            startEngineOnce();
        } else {
            AppLog.i(TAG, "wake SDK initialization deferred: waiting for validated network");
        }
    }

    /**
     * 首次启动要复制约 9 MB 引擎资源并做原生初始化（含联网鉴权），耗时可达数秒，
     * 因此放在后台线程，避免阻塞 Launcher 启动。
     */
    private void startEngineOnce() {
        synchronized (this) {
            if (mStarted) {
                return;
            }
            mStarted = true;
        }
        Thread initThread = new Thread(() -> {
            try {
                AppLog.i(TAG, "wake SDK initialization begin after validated network");
                FlyAiIVW.getInstance(mContext, FlyAiIVW.getDefaultWakeWords(), this);
                AppLog.i(TAG, "wake SDK initialized; waiting for VTN authorization callback");
            } catch (Throwable throwable) {
                mStarted = false;
                AppLog.e(TAG, "start wake engine failed", throwable);
            }
        }, "ai-wakeup-init");
        initThread.setPriority(Thread.NORM_PRIORITY - 1);
        initThread.start();
    }

    /**
     * 监听网络恢复：断网启动或鉴权失败时，联网后自动重试初始化。
     */
    private void registerNetworkCallback() {
        if (mNetworkCallback != null) {
            return;
        }
        ConnectivityManager manager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return;
        }
        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network network,
                            NetworkCapabilities capabilities) {
                        boolean validated = isValidatedInternet(capabilities);
                        AppLog.i(TAG, "network capabilities changed: network=" + network
                                + ", validated=" + validated
                                + ", capabilities=" + capabilities);
                        if (validated) {
                            onNetworkReady();
                        }
                    }

                    @Override
                    public void onLost(Network network) {
                        AppLog.i(TAG, "network lost: " + network);
                    }
                };
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try {
            manager.registerNetworkCallback(request, callback);
            mNetworkCallback = callback;
        } catch (Throwable throwable) {
            AppLog.w(TAG, "register network callback failed", throwable);
        }
    }

    private void onNetworkReady() {
        if (isAuthorized()) {
            return;
        }
        if (!mStarted) {
            AppLog.i(TAG, "validated network ready, starting wake SDK initialization");
            startEngineOnce();
            return;
        }
        // mStarted 已经在 startEngineOnce() 提前置位。此时 FlyAiIVW 仍为 null 仅表示
        // 初始化线程尚未执行到对象创建，不能重置 mStarted 并再次启动，否则会并发初始化同一个 SDK。
        FlyAiIVW instance = FlyAiIVW.peekInstance();
        if (instance == null) {
            AppLog.i(TAG, "validated network ready while wake SDK initialization is in progress");
        } else if (!instance.isAuthorized()) {
            AppLog.i(TAG, "validated network ready; wake SDK is awaiting VTN authorization");
        }
    }

    /** 释放唤醒引擎，通常只在进程退出或明确关闭语音唤醒时调用。 */
    public void stop() {
        mStarted = false;
        mWakeConversationAccepted = false;
        mPendingMicrophoneReady = false;
        mMainHandler.post(this::releaseHintPlayer);
        unregisterNetworkCallback();
        FlyAiIVW instance = FlyAiIVW.peekInstance();
        if (instance != null) {
            instance.destroy();
        }
        AppLog.i(TAG, "wake engine stopped");
    }

    private void unregisterNetworkCallback() {
        ConnectivityManager.NetworkCallback callback = mNetworkCallback;
        if (callback == null) {
            return;
        }
        mNetworkCallback = null;
        ConnectivityManager manager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.unregisterNetworkCallback(callback);
        } catch (Throwable throwable) {
            AppLog.w(TAG, "unregister network callback failed", throwable);
        }
    }

    public boolean isAuthorized() {
        FlyAiIVW instance = FlyAiIVW.peekInstance();
        return instance != null && instance.isAuthorized();
    }

    public void setNavigator(Navigator navigator) {
        mNavigator = navigator;
    }

    public void clearNavigator(Navigator navigator) {
        if (mNavigator == navigator) {
            mNavigator = null;
        }
    }

    public void setConsumer(Consumer consumer) {
        mConsumer = consumer;
        // AI 页面创建慢于提示音时，补投一次开麦通知，避免丢掉这次唤醒。
        if (consumer != null && mPendingMicrophoneReady) {
            mPendingMicrophoneReady = false;
            mMainHandler.post(() -> {
                Consumer current = mConsumer;
                if (current != null) {
                    current.onWakeupMicrophoneReady();
                }
            });
        }
    }

    public void clearConsumer(Consumer consumer) {
        if (mConsumer == consumer) {
            mConsumer = null;
        }
    }

    /** AiPresenter 开麦时把编码器挂到 VTN 处理后音频上；传 null 摘除。 */
    public void attachStreamer(MicOpusStreamer streamer) {
        FlyAiIVW instance = FlyAiIVW.peekInstance();
        if (instance != null) {
            instance.setMicOpusStreamer(streamer);
        }
    }

    // ────────────────────── 唤醒回调 ──────────────────────

    @Override
    public void onInitSdkSuccess() {
        AppLog.i(TAG, "wake engine authorized, listening for wake words");
    }

    @Override
    public long onFlyAIWakeupDetected(FlyAiIVW.WakeupInfo wakeupInfo) {
        AppLog.i(TAG, "WAKEUP_CALLBACK detected: " + formatWakeupInfo(wakeupInfo));
        Navigator navigator = mNavigator;
        if (navigator == null) {
            AppLog.w(TAG, "wakeup ignored: no navigator attached");
            return 0L;
        }
        if (!isNetworkAvailable()) {
            mWakeConversationAccepted = false;
            AppLog.w(TAG, "wakeup rejected: network unavailable");
            return 0L;
        }
        if (!AuthorizationStore.hasToken(mContext)) {
            mWakeConversationAccepted = false;
            AppLog.w(TAG, "wakeup rejected: not authorized");
            return 0L;
        }
        AppLog.i(TAG, "WAKEUP_ACCEPTED: requesting AI page");
        // VTN 回调运行在原生线程，界面切换必须回到主线程。
        mMainHandler.post(() -> {
            Navigator current = mNavigator;
            if (current != null) {
                current.onWakeupRequestAiPage();
            }
        });
        // 让出时间给 Fragment 事务与粒子预热，再播放提示音。
        return NAVIGATION_SETTLE_DELAY_MS;
    }

    @Override
    public boolean onFlyAIPreWakeStop(FlyAiIVW.WakeupInfo wakeupInfo) {
        AppLog.i(TAG, "WAKEUP_PRE_PROMPT: " + formatWakeupInfo(wakeupInfo));
        Navigator navigator = mNavigator;
        if (!isNetworkAvailable()) {
            mWakeConversationAccepted = false;
            mPendingMicrophoneReady = false;
            playLocalHint(R.raw.no_network, "no-network");
            notifyRejected(navigator, Reason.NO_NETWORK);
            return false;
        }
        if (!AuthorizationStore.hasToken(mContext)) {
            mWakeConversationAccepted = false;
            mPendingMicrophoneReady = false;
            playLocalHint(R.raw.hint_auth, "authorization");
            notifyRejected(navigator, Reason.NOT_AUTHORIZED);
            return false;
        }
        mWakeConversationAccepted = true;
        // AI 页面此刻可能仍在创建，提示音照常播放，开麦交给 onFlyAIResponse。
        Consumer consumer = mConsumer;
        if (consumer != null) {
            AppLog.i(TAG, "WAKEUP_PRE_PROMPT: preparing AI conversation");
            consumer.onWakeupPromptStarted();
        }
        return true;
    }

    @Override
    public void onFlyAIResponse(FlyAiIVW.WakeupInfo wakeupInfo) {
        AppLog.i(TAG, "WAKEUP_PROMPT_FINISHED: " + formatWakeupInfo(wakeupInfo));
        if (!mWakeConversationAccepted) {
            mPendingMicrophoneReady = false;
            AppLog.i(TAG, "wake flow ended without an AI conversation request");
            return;
        }
        mWakeConversationAccepted = false;
        Consumer consumer = mConsumer;
        if (consumer == null) {
            AppLog.w(TAG, "wake prompt finished before AI page attached, will retry on attach");
            mPendingMicrophoneReady = true;
            return;
        }
        mPendingMicrophoneReady = false;
        consumer.onWakeupMicrophoneReady();
    }

    private void notifyRejected(Navigator navigator, Reason reason) {
        if (navigator == null) {
            return;
        }
        mMainHandler.post(() -> {
            Navigator current = mNavigator;
            if (current != null) {
                current.onWakeupRejected(reason);
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return isValidatedInternet(capabilities);
    }

    private void playLocalHint(int soundResId, String reason) {
        mMainHandler.post(() -> {
            try {
                releaseHintPlayer();
                MediaPlayer player = MediaPlayer.create(mContext, soundResId);
                if (player == null) {
                    AppLog.w(TAG, "unable to create " + reason + " wake hint player");
                    return;
                }
                mHintPlayer = player;
                player.setOnCompletionListener(completedPlayer -> releaseHintPlayer());
                player.setOnErrorListener((errorPlayer, what, extra) -> {
                    AppLog.w(TAG, reason + " wake hint playback failed: " + what + "/" + extra);
                    releaseHintPlayer();
                    return true;
                });
                player.start();
                AppLog.i(TAG, "wake hint playback started: " + reason);
            } catch (Throwable throwable) {
                AppLog.w(TAG, "unable to play " + reason + " wake hint", throwable);
                releaseHintPlayer();
            }
        });
    }

    private void releaseHintPlayer() {
        MediaPlayer player = mHintPlayer;
        mHintPlayer = null;
        if (player == null) {
            return;
        }
        try {
            player.release();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isValidatedInternet(NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static String formatWakeupInfo(FlyAiIVW.WakeupInfo wakeupInfo) {
        return wakeupInfo == null ? "(no detail payload)" : wakeupInfo.toLogString();
    }
}
