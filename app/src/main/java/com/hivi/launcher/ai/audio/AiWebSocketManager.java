package com.hivi.launcher.ai.audio;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.hivi.launcher.utils.log.AppLog;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * AI 对话 WebSocket 协议实现，与 HiviAudio 的 {@code AIWebSocketManager} 保持一致。
 *
 * <p>上行时序：连接成功后自动发送 {@code listen/start/auto} → 服务端 ack →
 * 发送 {@code param{closeAudio:"3"}} → 服务端 ack 后才允许上传 Opus。</p>
 *
 * <p>下行消息：{@code session} / {@code stt} / {@code tts} / {@code listen} /
 * {@code param} / {@code abort} / {@code error}，二进制帧为 16kHz 单声道 Opus。</p>
 */
public final class AiWebSocketManager {
    private static final String TAG = "AiWebSocketManager";
    private static final long QUEUE_WARN_BYTES = 256L * 1024L;
    private static final long QUEUE_DROP_BYTES = 512L * 1024L;
    /** 收到 listen/stop ack 后再关闭连接，给服务端留出收尾时间。 */
    private static final long CLOSE_AFTER_STOP_ACK_MS = 200L;
    /** 未收到 listen/stop ack 时的兜底关闭时间。 */
    private static final long CLOSE_FALLBACK_MS = 1_500L;
    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;

    public interface Listener {
        void onSessionCreated(String sessionId);

        void onSttResult(String text);

        void onTtsStarted(String text);

        void onTtsSentenceStarted(String text);

        void onTtsStopped();

        void onAudioData(byte[] audioData);

        void onAbort();

        void onListenStateChanged(String state, String mode, boolean executed);

        void onAudioUploadReady();

        void onError(String message);

        void onClosed();

        /** 服务端确认参数更新（modelId / voiceId / closeAudio）。 */
        default void onParamUpdated(String modelId, String voiceId, String closeAudio,
                boolean executed) {
        }

        /** TTS 结束时服务端附带的整段回答音频链接。 */
        default void onTtsTextAudioUrl(String audioUrl) {
        }

        /** 鉴权被拒（HTTP 403），上层应清理本地 token 并要求重新授权。 */
        default void onAuthorizationRejected() {
        }
    }

    private final Object mLock = new Object();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient mClient;
    private final String mAuthorization;
    private final String mWebSocketUrl;
    private final Runnable mCloseFallbackRunnable = this::performClose;
    private final Runnable mHeartbeatRunnable = this::sendHeartbeat;

    private volatile WebSocket mWebSocket;
    private volatile Listener mListener;
    private volatile String mSessionId = "";
    private volatile boolean mReadyForAudio;
    private volatile boolean mClosing;
    private volatile boolean mListenStartSent;
    private volatile boolean mWaitingForStopAck;
    private volatile boolean mClosedNotified;
    private volatile boolean mHeartbeatActive;
    private volatile String mListenState = "";
    private volatile String mListenMode = "";
    private int mDroppedAudioPackets;
    private int mAudioPacketCount;

    public AiWebSocketManager(String authorization, String webSocketUrl) {
        mAuthorization = normalizeAuthorization(authorization);
        mWebSocketUrl = webSocketUrl;
        mClient = new OkHttpClient.Builder()
                .connectTimeout(10L, TimeUnit.SECONDS)
                .readTimeout(0L, TimeUnit.MILLISECONDS)
                .writeTimeout(10L, TimeUnit.SECONDS)
                .build();
    }

    /** 服务端要求 {@code Authorization: Bearer <token>}，此处补齐前缀。 */
    private static String normalizeAuthorization(String authorization) {
        if (TextUtils.isEmpty(authorization)) {
            return "";
        }
        String trimmed = authorization.trim();
        return trimmed.startsWith("Bearer ") ? trimmed : "Bearer " + trimmed;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void connect(Map<String, String> headers) {
        synchronized (mLock) {
            if (mWebSocket != null || mClosing) {
                return;
            }
            mReadyForAudio = false;
            mListenStartSent = false;
            mWaitingForStopAck = false;
            mClosedNotified = false;
            mDroppedAudioPackets = 0;
            mAudioPacketCount = 0;
        }

        Request.Builder requestBuilder = new Request.Builder().url(mWebSocketUrl);
        if (!TextUtils.isEmpty(mAuthorization)) {
            requestBuilder.header("Authorization", mAuthorization);
        }
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!TextUtils.isEmpty(header.getKey()) && !TextUtils.isEmpty(header.getValue())) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
            }
        }

        mClient.newWebSocket(requestBuilder.build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                synchronized (mLock) {
                    if (mClosing) {
                        webSocket.close(1000, "closed");
                        return;
                    }
                    mWebSocket = webSocket;
                }
                AppLog.i(TAG, "WebSocket connected, code=" + response.code());
                startHeartbeat();
                sendListenStart(false);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleTextMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Listener listener = mListener;
                if (!mClosing && listener != null) {
                    listener.onAudioData(bytes.toByteArray());
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                stopHeartbeat();
                boolean forbidden = response != null && response.code() == 403;
                synchronized (mLock) {
                    if (mWebSocket == webSocket) {
                        mWebSocket = null;
                    }
                    mReadyForAudio = false;
                }
                AppLog.w(TAG, "WebSocket failure, code="
                        + (response == null ? -1 : response.code()), throwable);
                if (mClosing) {
                    return;
                }
                if (forbidden) {
                    Listener listener = mListener;
                    if (listener != null) {
                        listener.onAuthorizationRejected();
                    }
                }
                notifyError("AI connection failed");
                notifyClosedOnce();
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                stopHeartbeat();
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                stopHeartbeat();
                synchronized (mLock) {
                    if (mWebSocket == webSocket) {
                        mWebSocket = null;
                    }
                    mReadyForAudio = false;
                }
                AppLog.i(TAG, "WebSocket closed, code=" + code + ", reason=" + reason);
                if (!mClosing) {
                    notifyClosedOnce();
                }
            }
        });
    }

    // ────────────────────── 状态查询 ──────────────────────

    public boolean isConnected() {
        return mWebSocket != null && !mClosing;
    }

    public boolean isReadyForAudio() {
        return isConnected() && mReadyForAudio;
    }

    public String getSessionId() {
        return mSessionId;
    }

    public String getListenState() {
        return mListenState;
    }

    public String getListenMode() {
        return mListenMode;
    }

    // ────────────────────── 上行消息 ──────────────────────

    /**
     * @param force abort 之后需要强制重新初始化监听会话
     */
    public void sendListenStart(boolean force) {
        if (!force && mListenStartSent) {
            return;
        }
        JSONObject message = new JSONObject();
        try {
            message.put("type", "listen");
            message.put("state", "start");
            message.put("mode", "auto");
        } catch (Exception ignored) {
            return;
        }
        if (send(message)) {
            mListenStartSent = true;
            mReadyForAudio = false;
        }
    }

    public void sendListenStop() {
        send(buildListenStop());
    }

    public boolean sendPing() {
        WebSocket webSocket = mWebSocket;
        return webSocket != null && !mClosing && webSocket.send("ping");
    }

    /** 关麦状态下的打断：不带 mode，服务端不会收到唤醒词音频。 */
    public void sendAbort() {
        sendAbort(false);
    }

    /**
     * @param microphoneOpen 开麦状态下打断需带 {@code mode=2}，服务端会收到唤醒词音频
     */
    public void sendAbort(boolean microphoneOpen) {
        JSONObject message = new JSONObject();
        try {
            message.put("type", "abort");
            if (microphoneOpen) {
                message.put("mode", "2");
            }
        } catch (Exception ignored) {
            notifyError("Unable to interrupt AI conversation");
            return;
        }
        send(message);
    }

    /** 以文本代替语音发起一轮对话。 */
    public void sendTextMessage(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        JSONObject message = new JSONObject();
        try {
            message.put("type", "listen");
            message.put("state", "detect");
            message.put("mode", "auto");
            message.put("text", text);
        } catch (Exception ignored) {
            notifyError("AI message send failed");
            return;
        }
        send(message);
    }

    public void sendParamUpdate(String modelId, String voiceId, String closeAudio) {
        sendParamUpdate(modelId, voiceId, closeAudio, null);
    }

    public void sendCurrentPlayUpdate(String currentPlay) {
        sendParamUpdate(null, null, null, currentPlay);
    }

    public void sendParamUpdate(String modelId, String voiceId, String closeAudio,
            String currentPlay) {
        JSONObject message = new JSONObject();
        try {
            message.put("type", "param");
            putIfNotEmpty(message, "modelId", modelId);
            putIfNotEmpty(message, "voiceId", voiceId);
            putIfNotEmpty(message, "closeAudio", closeAudio);
            putIfNotEmpty(message, "currentPlay", currentPlay);
        } catch (Exception ignored) {
            notifyError("Unable to update AI parameters");
            return;
        }
        send(message);
    }

    /** 切换 AI 角色（音色/人格）。 */
    public void sendRoleUpdate(String roleId) {
        if (TextUtils.isEmpty(roleId)) {
            return;
        }
        JSONObject message = new JSONObject();
        try {
            message.put("type", "param");
            message.put("roleId", roleId);
        } catch (Exception ignored) {
            notifyError("Unable to update AI role");
            return;
        }
        send(message);
    }

    /**
     * abort 之后服务端保留同一个监听会话，丢弃上一轮回答后即可恢复上传。
     */
    public void enableAudioSending() {
        if (isConnected()) {
            mReadyForAudio = true;
        }
    }

    public boolean sendAudio(byte[] audioData, int length) {
        WebSocket webSocket = mWebSocket;
        if (webSocket == null || mClosing || !mReadyForAudio || audioData == null
                || length <= 0) {
            if (!mReadyForAudio) {
                mDroppedAudioPackets++;
                if (mDroppedAudioPackets % 50 == 0) {
                    AppLog.w(TAG, "audio upload not ready, dropped=" + mDroppedAudioPackets);
                }
            }
            return false;
        }
        long queueSize = webSocket.queueSize();
        if (queueSize > QUEUE_DROP_BYTES) {
            AppLog.w(TAG, "WebSocket queue too large, drop frame. queueSize=" + queueSize);
            return false;
        }
        mAudioPacketCount++;
        if (queueSize > QUEUE_WARN_BYTES && mAudioPacketCount % 25 == 0) {
            AppLog.w(TAG, "WebSocket queue backlog=" + queueSize + " bytes");
        }
        return webSocket.send(ByteString.of(audioData, 0, Math.min(length, audioData.length)));
    }

    // ────────────────────── 关闭 ──────────────────────

    /**
     * 优雅关闭：先发 {@code listen/stop}，收到 ack（或超时兜底）后再关闭 socket。
     */
    public void release() {
        WebSocket webSocket;
        synchronized (mLock) {
            if (mClosing) {
                return;
            }
            mClosing = true;
            mReadyForAudio = false;
            mListenStartSent = false;
            webSocket = mWebSocket;
        }
        stopHeartbeat();
        if (webSocket == null) {
            shutdownClient();
            return;
        }
        boolean sent = false;
        JSONObject stop = buildListenStop();
        if (stop != null) {
            sent = webSocket.send(stop.toString());
        }
        if (sent) {
            mWaitingForStopAck = true;
            mMainHandler.postDelayed(mCloseFallbackRunnable, CLOSE_FALLBACK_MS);
        } else {
            performClose();
        }
    }

    /** 立即关闭，不等待服务端 ack（进程退出/异常恢复时使用）。 */
    public void releaseImmediately() {
        synchronized (mLock) {
            mClosing = true;
            mReadyForAudio = false;
            mListenStartSent = false;
        }
        stopHeartbeat();
        performClose();
    }

    private void performClose() {
        stopHeartbeat();
        WebSocket webSocket;
        synchronized (mLock) {
            mWaitingForStopAck = false;
            webSocket = mWebSocket;
            mWebSocket = null;
        }
        mMainHandler.removeCallbacks(mCloseFallbackRunnable);
        if (webSocket != null) {
            webSocket.close(1000, "page closed");
        }
        shutdownClient();
    }

    private void shutdownClient() {
        mClient.dispatcher().executorService().shutdown();
        mClient.connectionPool().evictAll();
    }

    // ────────────────────── 下行消息 ──────────────────────

    private void startHeartbeat() {
        synchronized (mLock) {
            if (mClosing || mWebSocket == null) {
                return;
            }
            mHeartbeatActive = true;
        }
        mMainHandler.removeCallbacks(mHeartbeatRunnable);
        mMainHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_INTERVAL_MS);
        AppLog.i(TAG, "WebSocket heartbeat started");
    }

    private void sendHeartbeat() {
        WebSocket webSocket;
        synchronized (mLock) {
            if (!mHeartbeatActive || mClosing || mWebSocket == null) {
                return;
            }
            webSocket = mWebSocket;
        }
        if (!webSocket.send("ping")) {
            synchronized (mLock) {
                if (!mClosing && mWebSocket == webSocket) {
                    AppLog.w(TAG, "WebSocket heartbeat send failed");
                }
            }
            stopHeartbeat();
            return;
        }
        synchronized (mLock) {
            if (!mHeartbeatActive || mClosing || mWebSocket != webSocket) {
                return;
            }
            mMainHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_INTERVAL_MS);
        }
    }

    private void stopHeartbeat() {
        boolean wasActive;
        synchronized (mLock) {
            wasActive = mHeartbeatActive;
            mHeartbeatActive = false;
        }
        mMainHandler.removeCallbacks(mHeartbeatRunnable);
        if (wasActive) {
            AppLog.i(TAG, "WebSocket heartbeat stopped");
        }
    }

    private boolean send(JSONObject message) {
        WebSocket webSocket = mWebSocket;
        if (webSocket == null || message == null || mClosing) {
            return false;
        }
        boolean sent = webSocket.send(message.toString());
        if (!sent) {
            notifyError("AI message send failed");
        }
        return sent;
    }

    private JSONObject buildListenStop() {
        JSONObject message = new JSONObject();
        try {
            message.put("type", "listen");
            message.put("state", "stop");
            message.put("mode", "auto");
            return message;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void putIfNotEmpty(JSONObject message, String key, String value)
            throws Exception {
        if (!TextUtils.isEmpty(value)) {
            message.put(key, value);
        }
    }

    private void handleTextMessage(String message) {
        if ("pong".equalsIgnoreCase(message.trim())) {
            return;
        }
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");
            if ("session".equals(type)) {
                mSessionId = json.optString("sessionId");
                Listener listener = mListener;
                if (!mClosing && listener != null) {
                    listener.onSessionCreated(mSessionId);
                }
            } else if ("stt".equals(type)) {
                mReadyForAudio = false;
                Listener listener = mListener;
                if (!mClosing && listener != null) {
                    listener.onSttResult(json.optString("text"));
                }
            } else if ("tts".equals(type)) {
                handleTts(json);
            } else if ("listen".equals(type)) {
                handleListen(json);
            } else if ("param".equals(type)) {
                handleParam(json);
            } else if ("abort".equals(type)) {
                Listener listener = mListener;
                if (!mClosing && listener != null) {
                    listener.onAbort();
                }
            } else if ("error".equals(type)) {
                String detail = json.optString("message");
                String code = json.optString("code");
                AppLog.w(TAG, "server error code=" + code + ", message=" + detail);
                notifyError(TextUtils.isEmpty(detail) ? "AI server error" : detail);
            }
        } catch (Exception exception) {
            AppLog.w(TAG, "Unable to parse AI WebSocket message", exception);
            notifyError("AI response parse failed");
        }
    }

    private void handleTts(JSONObject json) {
        Listener listener = mListener;
        if (mClosing || listener == null) {
            return;
        }
        String state = json.optString("state");
        String text = json.optString("text").replace("*", "");
        if ("start".equals(state)) {
            listener.onTtsStarted(text);
        } else if ("sentence_start".equals(state)) {
            listener.onTtsSentenceStarted(text);
        } else if ("stop".equals(state)) {
            String audioUrl = json.optString("textAudioUrl");
            if (!TextUtils.isEmpty(audioUrl)) {
                listener.onTtsTextAudioUrl(audioUrl);
            }
            listener.onTtsStopped();
        } else if ("insufficient_balance".equals(state)) {
            notifyError("AI quota is insufficient");
        }
    }

    private void handleListen(JSONObject json) {
        String state = json.optString("state");
        String mode = json.optString("mode");
        boolean executed = readExecuted(json);
        mListenState = state;
        mListenMode = mode;

        if ("start".equals(state) && "auto".equals(mode) && executed) {
            sendParamUpdate(null, null, "3");
        }
        if (mWaitingForStopAck && "stop".equals(state) && "auto".equals(mode) && executed) {
            mWaitingForStopAck = false;
            mMainHandler.removeCallbacks(mCloseFallbackRunnable);
            mMainHandler.postDelayed(mCloseFallbackRunnable, CLOSE_AFTER_STOP_ACK_MS);
        }

        Listener listener = mListener;
        if (!mClosing && listener != null) {
            listener.onListenStateChanged(state, mode, executed);
        }
    }

    private void handleParam(JSONObject json) {
        String closeAudio = json.optString("closeAudio");
        String modelId = json.optString("modelId");
        String voiceId = json.has("voiceId")
                ? json.optString("voiceId")
                : json.optString("voiceName");
        boolean executed = readExecuted(json);

        Listener listener = mListener;
        if ("3".equals(closeAudio) && executed) {
            mReadyForAudio = true;
            if (!mClosing && listener != null) {
                listener.onAudioUploadReady();
            }
        }
        if (!mClosing && listener != null) {
            listener.onParamUpdated(modelId, voiceId, closeAudio, executed);
        }
    }

    private boolean readExecuted(JSONObject json) {
        Object value = json.opt("execute");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            return "true".equalsIgnoreCase((String) value) || "1".equals(value);
        }
        return false;
    }

    private void notifyError(String message) {
        Listener listener = mListener;
        if (!mClosing && listener != null) {
            listener.onError(message);
        }
    }

    private void notifyClosedOnce() {
        if (mClosedNotified) {
            return;
        }
        mClosedNotified = true;
        Listener listener = mListener;
        if (!mClosing && listener != null) {
            listener.onClosed();
        }
    }
}
