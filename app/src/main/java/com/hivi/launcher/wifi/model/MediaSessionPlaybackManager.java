package com.hivi.launcher.wifi.model;

import android.app.ActivityManager;
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

    /** Pauses the playing music-app session; used to yield audio to the UPnP cast. */
    public void pauseActivePlayback() {
        MediaController controller = pickActiveController();
        if (controller == null || !isPlaying(controller)
                || controller.getTransportControls() == null) {
            return;
        }
        controller.getTransportControls().pause();
        AppLog.i(TAG, "paused music app to yield audio: " + controller.getPackageName());
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
                if (WifiMusicApp.fromPackageName(packageName) == null) {
                    continue;
                }
                // A closed app can linger as a playing session: swiping the task away may
                // keep the process (and its playback service) alive, which pinned the last
                // song on the home UI, kept the music going and routed controls to a dead
                // session (test issue #3). Pause the lingering audio before dropping it.
                if (!isAppAlive(packageName)) {
                    pauseLingeringSession(holders.get(packageName));
                    continue;
                }
                wanted.put(packageName, session);
            }

            for (String packageName : new ArrayList<>(holders.keySet())) {
                if (!wanted.containsKey(packageName)) {
                    unregisterController(packageName);
                }
            }
            for (Map.Entry<String, MediaController> entry : wanted.entrySet()) {
                MediaController fresh = entry.getValue();
                ControllerHolder existing = holders.get(entry.getKey());
                if (existing == null) {
                    registerController(entry.getKey(), fresh);
                } else if (!existing.controller.getSessionToken()
                        .equals(fresh.getSessionToken())) {
                    // Music apps recreate their session per track; keeping the old
                    // controller pins the previous song's metadata (test issue #2).
                    unregisterController(entry.getKey());
                    registerController(entry.getKey(), fresh);
                }
            }
            notifyStateChanged();
        } catch (Throwable e) {
            AppLog.e(TAG, "query active media sessions failed", e);
        }
    }

    /**
     * Whether the music app still counts as open: its process must be alive and its task must
     * still exist. Swiping the app away on this device can keep the process (and therefore the
     * paused session) alive, so the removed task is what tells us the app was closed. Only the
     * system uid sees the full lists; when a query is unavailable the session is kept so
     * behavior never regresses.
     */
    private boolean isAppAlive(String packageName) {
        ActivityManager activityManager = (ActivityManager)
                appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return true;
        }
        List<ActivityManager.RunningAppProcessInfo> processes =
                activityManager.getRunningAppProcesses();
        boolean processAlive = processes == null || processes.isEmpty();
        if (!processAlive) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process != null && packageName.equals(process.processName)) {
                    processAlive = true;
                    break;
                }
            }
        }
        if (!processAlive) {
            return false;
        }
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(100);
        if (tasks == null || tasks.isEmpty()) {
            return true;
        }
        // A restricted task list (missing even our own task) cannot be trusted to judge
        // other apps; keep the session instead of clearing playback that is still real.
        boolean sawLauncherTask = false;
        String launcherPackage = appContext.getPackageName();
        for (ActivityManager.RunningTaskInfo task : tasks) {
            if (task == null || task.baseActivity == null) {
                continue;
            }
            if (packageName.equals(task.baseActivity.getPackageName())) {
                return true;
            }
            if (launcherPackage.equals(task.baseActivity.getPackageName())) {
                sawLauncherTask = true;
            }
        }
        return !sawLauncherTask;
    }

    /**
     * Pauses a still-playing session whose app was closed: killing the task does not stop the
     * app's playback service, so the speaker would keep playing music with no way back to the
     * app. Harmless no-op when the process died with the task (session already gone).
     */
    private void pauseLingeringSession(@Nullable ControllerHolder holder) {
        if (holder == null || holder.controller.getTransportControls() == null
                || !isPlaying(holder.controller)) {
            return;
        }
        try {
            holder.controller.getTransportControls().pause();
            AppLog.i(TAG, "paused lingering session of closed app: "
                    + holder.controller.getPackageName());
        } catch (Throwable e) {
            AppLog.w(TAG, "pause lingering session failed: " + e.getMessage());
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
