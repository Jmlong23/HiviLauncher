package com.hivi.launcher.wifi.model;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.hivi.launcher.utils.log.AppLog;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the Wi-Fi music apps from auto-resuming playback right after the speaker boots.
 *
 * <p>The car/IoT builds of QQ Music, NetEase Cloud Music and Kugou start themselves when
 * the device powers on and resume the last song, so music plays before anyone asked for
 * it. The launcher shares the system uid, so {@link BootCompletedReceiver} and the
 * application startup both run a sweep that force-stops the three packages — killed
 * before their first audio frame, and a force-stopped app cannot be restarted by
 * broadcasts, so they stay closed for the rest of the boot.</p>
 *
 * <p>Sweeps repeat on a short poll for a few minutes after boot as a safety net (for
 * example against a media-button "resume last player" restart). They end early once the
 * user acts: any playback command or app launch in the launcher calls
 * {@link #markUserInteraction()}. An app whose own activity is in the foreground is never
 * killed, because that means the user opened it from inside the app.</p>
 */
public final class BootAutoPlaySuppressor {
    private static final String TAG = "BootAutoPlaySuppressor";
    /** Auto-play kicks in within the first minute of boot; a few minutes of coverage is plenty. */
    private static final long SUPPRESS_WINDOW_MS = 3 * 60_000L;
    private static final long POLL_INTERVAL_MS = 2_000L;

    private static final BootAutoPlaySuppressor INSTANCE = new BootAutoPlaySuppressor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context appContext;
    private Object appActivityManager;
    private Method forceStopMethod;
    private final Set<String> killLogged = new HashSet<>();
    private boolean forceStopUnavailable;
    private boolean userInteracted;
    private boolean pollScheduled;
    /** Suppression window deadline on the {@link SystemClock#elapsedRealtime()} clock; 0 = unset. */
    private long suppressUntilElapsed;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            pollScheduled = false;
            if (!suppressionActive()) {
                AppLog.i(TAG, "Boot auto-play suppression window ended");
                return;
            }
            sweepAutoStartApps();
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
            pollScheduled = true;
        }
    };

    private BootAutoPlaySuppressor() {
    }

    public static BootAutoPlaySuppressor getInstance() {
        return INSTANCE;
    }

    /** Called at launcher start; only arms the window when the boot is still recent. */
    public void start(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        if (suppressUntilElapsed == 0) {
            if (SystemClock.elapsedRealtime() >= SUPPRESS_WINDOW_MS) {
                AppLog.i(TAG, "Skip boot auto-play suppression: boot was more than "
                        + (SUPPRESS_WINDOW_MS / 1000) + "s ago");
                return;
            }
            suppressUntilElapsed = SUPPRESS_WINDOW_MS;
        }
        beginPolling();
    }

    /** BOOT_COMPLETED anchor: the window (re)starts from the broadcast, however late it is. */
    public void onBootCompleted(Context context) {
        AppLog.i(TAG, "BOOT_COMPLETED received: force-stopping auto-start music apps");
        suppressUntilElapsed = SystemClock.elapsedRealtime() + SUPPRESS_WINDOW_MS;
        start(context);
    }

    /**
     * Records that the user explicitly asked for music (launcher transport buttons, opening
     * a music app, a voice transport command). Ends suppression for the rest of this boot.
     * Main thread only, like every other entry point.
     */
    public void markUserInteraction() {
        userInteracted = true;
        if (pollScheduled) {
            mainHandler.removeCallbacks(poll);
            pollScheduled = false;
            AppLog.i(TAG, "Boot auto-play suppression ended: user interaction");
        }
    }

    private void beginPolling() {
        mainHandler.removeCallbacks(poll);
        mainHandler.post(poll);
        pollScheduled = true;
        AppLog.i(TAG, "Boot auto-play suppression active, remaining="
                + Math.max(0, suppressUntilElapsed - SystemClock.elapsedRealtime()) / 1000 + "s");
    }

    private boolean suppressionActive() {
        return !userInteracted && SystemClock.elapsedRealtime() < suppressUntilElapsed;
    }

    /** Kills any of the known music apps that auto-started since boot. */
    private void sweepAutoStartApps() {
        if (forceStopUnavailable) {
            return;
        }
        String foregroundPackage = getForegroundPackage();
        for (WifiMusicApp app : WifiMusicApp.values()) {
            String packageName = app.getPackageName();
            if (packageName.equals(foregroundPackage)) {
                continue;
            }
            if (!forceStopPackage(packageName)) {
                forceStopUnavailable = true;
                AppLog.w(TAG, "Force-stop is unavailable; boot auto-play suppression stopped");
                return;
            }
        }
    }

    /** Returns the package of the top activity, or null when it cannot be determined. */
    private String getForegroundPackage() {
        try {
            ActivityManager activityManager = (ActivityManager) appContext
                    .getSystemService(Context.ACTIVITY_SERVICE);
            @SuppressWarnings("deprecation")
            List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                return tasks.get(0).topActivity.getPackageName();
            }
        } catch (Throwable e) {
            AppLog.w(TAG, "Query foreground task failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Force-stops a package through the hidden IActivityManager API. The launcher shares
     * the system uid, so hidden API enforcement does not apply (verified on the target
     * device); on failure the whole suppression disables itself with one warning.
     */
    private boolean forceStopPackage(String packageName) {
        try {
            if (forceStopMethod == null) {
                appActivityManager = Class.forName("android.app.ActivityManager")
                        .getMethod("getService").invoke(null);
                forceStopMethod = appActivityManager.getClass()
                        .getMethod("forceStopPackage", String.class, int.class);
            }
            forceStopMethod.invoke(appActivityManager, packageName, 0);
            // Sweeps repeat every couple of seconds and most calls are no-ops on already
            // dead processes, so record only the first kill per app to avoid log flooding.
            if (killLogged.add(packageName)) {
                AppLog.i(TAG, "Force stopped auto-start app: " + packageName);
            }
            return true;
        } catch (Throwable e) {
            AppLog.w(TAG, "Force stop " + packageName + " failed: " + e);
            return false;
        }
    }
}
