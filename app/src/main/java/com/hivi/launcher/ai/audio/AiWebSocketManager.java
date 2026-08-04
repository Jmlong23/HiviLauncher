package com.hivi.launcher.ai.audio;

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
 * Lightweight implementation of the AI WebSocket protocol shared with the audio application.
 *
 * <p>The server starts a {@code listen/auto} session after the WebSocket connects. Opus upload
 * remains disabled until the server acknowledges {@code param(closeAudio=3)}.</p>
 */
public final class AiWebSocketManager {
    private static final String TAG = "AiWebSocketManager";
    private static final long MAX_AUDIO_QUEUE_BYTES = 512L * 1024L;

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
    }

    private final Object mLock = new Object();
    private final OkHttpClient mClient;
    private final String mAuthorization;
    private final String mWebSocketUrl;

    private volatile WebSocket mWebSocket;
    private volatile Listener mListener;
    private volatile boolean mReadyForAudio;
    private volatile boolean mClosing;
    private volatile boolean mListenStartSent;

    public AiWebSocketManager(String authorization, String webSocketUrl) {
        mAuthorization = authorization;
        mWebSocketUrl = webSocketUrl;
        mClient = new OkHttpClient.Builder()
                .connectTimeout(10L, TimeUnit.SECONDS)
                .readTimeout(0L, TimeUnit.MILLISECONDS)
                .writeTimeout(10L, TimeUnit.SECONDS)
                .build();
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
            mClosing = false;
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
                synchronized (mLock) {
                    if (mWebSocket == webSocket) {
                        mWebSocket = null;
                    }
                    mReadyForAudio = false;
                }
                if (!mClosing) {
                    notifyError("AI connection failed");
                    notifyClosed();
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                synchronized (mLock) {
                    if (mWebSocket == webSocket) {
                        mWebSocket = null;
                    }
                    mReadyForAudio = false;
                }
                if (!mClosing) {
                    notifyClosed();
                }
            }
        });
    }

    public boolean isConnected() {
        return mWebSocket != null && !mClosing;
    }

    public boolean isReadyForAudio() {
        return isConnected() && mReadyForAudio;
    }

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
        JSONObject message = new JSONObject();
        try {
            message.put("type", "listen");
            message.put("state", "stop");
            message.put("mode", "auto");
            send(message);
        } catch (Exception ignored) {
            // The socket will be closed immediately afterwards when the page is released.
        }
    }

    public void sendAbort() {
        JSONObject message = new JSONObject();
        try {
            message.put("type", "abort");
            send(message);
        } catch (Exception ignored) {
            notifyError("Unable to interrupt AI conversation");
        }
    }

    /**
     * The server keeps the same listening session after an abort. Re-enable local upload once
     * the previous response has been discarded.
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
            return false;
        }
        if (webSocket.queueSize() > MAX_AUDIO_QUEUE_BYTES) {
            AppLog.w(TAG, "Skipping AI audio frame because the WebSocket queue is full");
            return false;
        }
        return webSocket.send(ByteString.of(audioData, 0, Math.min(length, audioData.length)));
    }

    public void release() {
        WebSocket webSocket;
        synchronized (mLock) {
            mClosing = true;
            mReadyForAudio = false;
            mListenStartSent = false;
            webSocket = mWebSocket;
            mWebSocket = null;
        }
        if (webSocket != null) {
            try {
                JSONObject message = new JSONObject();
                message.put("type", "listen");
                message.put("state", "stop");
                message.put("mode", "auto");
                webSocket.send(message.toString());
            } catch (Exception ignored) {
                // Close is still sufficient when a network failure prevents the final command.
            }
            webSocket.close(1000, "page closed");
        }
        mClient.dispatcher().executorService().shutdown();
        mClient.connectionPool().evictAll();
    }

    private boolean send(JSONObject message) {
        WebSocket webSocket = mWebSocket;
        if (webSocket == null || mClosing) {
            return false;
        }
        boolean sent = webSocket.send(message.toString());
        if (!sent) {
            notifyError("AI message send failed");
        }
        return sent;
    }

    private void handleTextMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");
            if ("session".equals(type)) {
                Listener listener = mListener;
                if (!mClosing && listener != null) {
                    listener.onSessionCreated(json.optString("sessionId"));
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
            listener.onTtsStopped();
        } else if ("insufficient_balance".equals(state)) {
            notifyError("AI quota is insufficient");
        }
    }

    private void handleListen(JSONObject json) {
        String state = json.optString("state");
        String mode = json.optString("mode");
        boolean executed = readExecuted(json);
        if ("start".equals(state) && "auto".equals(mode) && executed) {
            JSONObject param = new JSONObject();
            try {
                param.put("type", "param");
                param.put("closeAudio", "3");
                send(param);
            } catch (Exception ignored) {
                notifyError("Unable to initialize AI audio");
            }
        }
        Listener listener = mListener;
        if (!mClosing && listener != null) {
            listener.onListenStateChanged(state, mode, executed);
        }
    }

    private void handleParam(JSONObject json) {
        String closeAudio = json.optString("closeAudio");
        if ("3".equals(closeAudio) && readExecuted(json)) {
            mReadyForAudio = true;
            Listener listener = mListener;
            if (!mClosing && listener != null) {
                listener.onAudioUploadReady();
            }
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

    private void notifyClosed() {
        Listener listener = mListener;
        if (!mClosing && listener != null) {
            listener.onClosed();
        }
    }
}
