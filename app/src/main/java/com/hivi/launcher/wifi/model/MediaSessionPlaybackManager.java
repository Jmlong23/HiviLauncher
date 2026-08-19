package com.hivi.launcher.wifi.model;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.hivi.launcher.HiviNotificationListener;
import com.hivi.launcher.utils.log.AppLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the media sessions published by the known Wi-Fi music apps (QQ Music, NetEase Cloud
 * Music, Kugou) and exposes their playback metadata plus transport controls.
 *
 * <p>The launcher runs as the system uid and holds {@code MEDIA_CONTENT_CONTROL}, so
 * {@link MediaSessionManager#getActiveSessions(ComponentName)} works with the registered
 * {@link HiviNotificationListener} component. MediaSessionManager offers no push notification
 * for sessions appearing or disappearing, so sessions are re-queried on a short poll; metadata
 * and playback updates of already-known sessions arrive through controller callbacks.</p>
 */
public final class MediaSessionPlaybackManager {
    private static final String TAG = "MediaSessionPlaybackManager";
    private static final long SESSION_POLL_MS = 2_000L;

    private static final MediaSessionPlaybackManager INSTANCE = new MediaSessionPlaybackManager();

    public interface Listener {
        void onMediaPlaybackChanged(MediaSessionPlaybackState state);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<String, ControllerHolder> holders = new HashMap<>();

    private Context appContext;
    private MediaSessionManager mediaSessionManager;
    private MediaSessionPlaybackState lastNotifiedState = MediaSessionPlaybackState.empty();
    private boolean started;

    private static final class ControllerHolder {
        final MediaController controller;
        final MediaController.Callback callback;

        ControllerHolder(MediaController controller, MediaController.Callback callback) {
            this.controller = controller;
            this.callback = callback;
        }
    }

    private final Runnable sessionPoll = new Runnable() {
        @Override
        public void run() {
            refreshSessions();
            mainHandler.postDelayed(this, SESSION_POLL_MS);
        }
    };

    private MediaSessionPlaybackManager() {
    }

    public static MediaSessionPlaybackManager getInstance() {
        return INSTANCE;
    }

    public void start(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        if (mediaSessionManager == null) {
            mediaSessionManager = (MediaSessionManager) appContext
                    .getSystemService(Context.MEDIA_SESSION_SERVICE);
        }
        if (started || mediaSessionManager == null) {
            return;
        }
        started = true;
        mainHandler.removeCallbacks(sessionPoll);
        mainHandler.post(sessionPoll);
    }

    public void addListener(Listener listener) {
        if (listener == null || listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
        listener.onMediaPlaybackChanged(lastNotifiedState);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public MediaSessionPlaybackState getCurrentState() {
        return buildState();
    }

    public void playOrPause() {
        MediaController controller = pickActiveController();
        if (controller == null || controller.getTransportControls() == null) {
            return;
        }
        if (isPlaying(controller)) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    public void next() {
        MediaController controller = pickActiveController();
        if (controller != null && controller.getTransportControls() != null) {
            controller.getTransportControls().skipToNext();
        }
    }

    public void previous() {
        MediaController controller = pickActiveController();
        if (controller != null && controller.getTransportControls() != null) {
            controller.getTransportControls().skipToPrevious();
        }
    }

    private void refreshSessions() {
        if (mediaSessionManager == null || appContext == null) {
            return;
        }
        try {
            List<MediaController> sessions = mediaSessionManager.getActiveSessions(
                    new ComponentName(appContext, HiviNotificationListener.class));
            Map<String, MediaController> wanted = new HashMap<>();
            for (MediaController session : sessions) {
                String packageName = session.getPackageName();
                if (WifiMusicApp.fromPackageName(packageName) != null) {
                    wanted.put(packageName, session);
                }
            }

            for (String packageName : new ArrayList<>(holders.keySet())) {
                if (!wanted.containsKey(packageName)) {
                    unregisterController(packageName);
                }
            }
            for (Map.Entry<String, MediaController> entry : wanted.entrySet()) {
                if (!holders.containsKey(entry.getKey())) {
                    registerController(entry.getKey(), entry.getValue());
                }
            }
            notifyStateChanged();
        } catch (Throwable e) {
            AppLog.e(TAG, "query active media sessions failed", e);
        }
    }

    private void registerController(String packageName, MediaController controller) {
        MediaController.Callback callback = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                scheduleStateUpdate();
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                scheduleStateUpdate();
            }

            @Override
            public void onSessionDestroyed() {
                scheduleStateUpdate();
            }
        };
        try {
            controller.registerCallback(callback);
            holders.put(packageName, new ControllerHolder(controller, callback));
        } catch (Throwable e) {
            AppLog.w(TAG, "register media controller callback failed: " + packageName);
        }
    }

    private void unregisterController(String packageName) {
        ControllerHolder holder = holders.remove(packageName);
        if (holder == null) {
            return;
        }
        try {
            holder.controller.unregisterCallback(holder.callback);
        } catch (Throwable e) {
            AppLog.w(TAG, "unregister media controller callback failed: " + packageName);
        }
    }

    private void scheduleStateUpdate() {
        mainHandler.post(this::notifyStateChanged);
    }

    private void notifyStateChanged() {
        MediaSessionPlaybackState state = buildState();
        if (state.equals(lastNotifiedState)) {
            return;
        }
        lastNotifiedState = state;
        for (int i = listeners.size() - 1; i >= 0; i--) {
            listeners.get(i).onMediaPlaybackChanged(state);
        }
    }

    @Nullable
    private MediaController pickActiveController() {
        MediaController best = null;
        long bestScore = Long.MIN_VALUE;
        for (ControllerHolder holder : holders.values()) {
            MediaController controller = holder.controller;
            PlaybackState playback = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            boolean playing = isPlaying(controller);
            boolean hasTrack = metadata != null
                    && !TextUtils.isEmpty(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
            if (!playing && !hasTrack) {
                continue;
            }
            long score = (playing ? 1L << 40 : 0L)
                    + (playback != null ? playback.getLastPositionUpdateTime() : 0L);
            if (score > bestScore) {
                best = controller;
                bestScore = score;
            }
        }
        return best;
    }

    private MediaSessionPlaybackState buildState() {
        MediaController controller = pickActiveController();
        if (controller == null) {
            return MediaSessionPlaybackState.empty();
        }
        WifiMusicApp app = WifiMusicApp.fromPackageName(controller.getPackageName());
        if (app == null) {
            return MediaSessionPlaybackState.empty();
        }
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) {
            return MediaSessionPlaybackState.empty();
        }
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(title)) {
            return MediaSessionPlaybackState.empty();
        }
        String artist = firstNonEmpty(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
        String album = firstNonEmpty(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM), "");
        android.graphics.Bitmap bitmap = firstNonNull(
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ART));
        return MediaSessionPlaybackState.of(app, title, artist, album, bitmap,
                isPlaying(controller));
    }

    private static boolean isPlaying(MediaController controller) {
        PlaybackState playback = controller.getPlaybackState();
        return playback != null && playback.getState() == PlaybackState.STATE_PLAYING;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
