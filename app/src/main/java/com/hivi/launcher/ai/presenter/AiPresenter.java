package com.hivi.launcher.ai.presenter;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;

import com.hivi.launcher.R;
import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.ai.audio.AiWebSocketManager;
import com.hivi.launcher.ai.audio.MicOpusStreamer;
import com.hivi.launcher.ai.audio.OpusAudioPlayer;
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
 */
public final class AiPresenter extends BasePresenter<AiView>
        implements AiWakeupController.Consumer {
    private static final String TAG = "AiConversationPresenter";
    private static final String SESSION_PREFERENCES = "ai_conversation";
    private static final String BOOT_WALL_TIME_KEY = "boot_wall_time";
    private static final String BOOT_ELAPSED_TIME_KEY = "boot_elapsed_time";
    private static final int DEFAULT_PARTICLE_ROLE_ID = 476;

    private final Context mContext;
    private final AiWakeupController mWakeupController;
    private AiWebSocketManager mWebSocketManager;
    private MicOpusStreamer mMicrophoneStreamer;
    private OpusAudioPlayer mAudioPlayer;

    private ParticleVisualizerView.State mParticleState = ParticleVisualizerView.State.IDLE;
    private boolean mReleased;
    private boolean mWaitingForRecordPermission;
    private boolean mTtsStopped;
    private boolean mInterruptPending;
    private boolean mWakePromptPlaying;
    private boolean mAwaitingAudioUploadReady;
    private int mCallbackGeneration;
    private String mLastUserCommand = "";

    public AiPresenter(Context context, AiView view) {
        super(view);
        mContext = context.getApplicationContext();
        mWakeupController = AiWakeupController.getInstance(mContext);
    }

    public void init() {
        renderState(ParticleVisualizerView.State.IDLE, R.string.ai_conversation_welcome);
        clearAssistantResponse();
        mWakeupController.setConsumer(this);
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
            mWakePromptPlaying = true;
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

    public void release() {
        if (mReleased) {
            return;
        }
        mReleased = true;
        mWakeupController.clearConsumer(this);
        mWaitingForRecordPermission = false;
        mInterruptPending = false;
        mTtsStopped = false;
        mWakePromptPlaying = false;
        mAwaitingAudioUploadReady = false;
        mCallbackGeneration++;
        stopAudioResources();
        releaseWebSocket();
    }

    private void openConversation() {
        if (mReleased || mWebSocketManager != null) {
            return;
        }
        final int generation = ++mCallbackGeneration;
        mAwaitingAudioUploadReady = true;
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
                    AppLog.i(TAG, "AI conversation closed by service, returning home");
                    release();
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
        renderState(ParticleVisualizerView.State.LISTENING,
                R.string.ai_conversation_listening);
    }

    private void resumeListeningAfterPlayback() {
        if (!mTtsStopped || (mAudioPlayer != null && mAudioPlayer.isPlaying())) {
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
        AiView view = getView();
        if (view != null) {
            view.renderConversationState(state, statusText);
        }
    }

    private void clearAssistantResponse() {
        AiView view = getView();
        if (view != null) {
            view.clearAssistantResponse();
        }
    }

    private void appendAssistantResponse(String responseText) {
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
