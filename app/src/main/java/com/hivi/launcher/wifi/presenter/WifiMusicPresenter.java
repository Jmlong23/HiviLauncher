package com.hivi.launcher.wifi.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.music.ui.MusicActivity;
import com.hivi.launcher.wifi.model.BootAutoPlaySuppressor;
import com.hivi.launcher.wifi.model.MediaSessionPlaybackManager;
import com.hivi.launcher.wifi.model.MediaSessionPlaybackState;
import com.hivi.launcher.wifi.model.WifiMusicApp;
import com.hivi.launcher.wifi.ui.WifiMusicView;

/**
 * Owns Wi-Fi music-page interactions. Playback is fed by two sources: the media sessions of the
 * known music apps (QQ Music / NetEase / Kugou playing on this device) take priority, and the
 * local UPnP renderer is the fallback for DLNA-cast playback. Transport controls follow whichever
 * source is currently on screen; tapping the player card opens the full player page for a cast,
 * or returns to the owning music app for local app playback.
 */
public final class WifiMusicPresenter extends BasePresenter<WifiMusicView>
        implements UpnpPlaybackManager.Listener, MediaSessionPlaybackManager.Listener {
    private final Context mContext;
    private final UpnpPlaybackManager mPlaybackManager;
    private final MediaSessionPlaybackManager mMediaSessionManager;

    private UpnpPlaybackState mUpnpState;
    private MediaSessionPlaybackState mMediaState;
    private WifiMusicApp mSelectedApp;
    private WifiMusicApp mPlayingApp;

    public WifiMusicPresenter(Context context, WifiMusicView view) {
        super(view);
        mContext = context.getApplicationContext();
        mPlaybackManager = UpnpPlaybackManager.getInstance();
        mMediaSessionManager = MediaSessionPlaybackManager.getInstance();
    }

    public void init() {
        mPlaybackManager.start(mContext);
        mPlaybackManager.addListener(this);
        mMediaSessionManager.start(mContext);
        mMediaSessionManager.addListener(this);
    }

    public void selectMusicApp(WifiMusicApp app) {
        if (app == null) {
            return;
        }

        mSelectedApp = app;
        WifiMusicView view = getView();
        if (view != null) {
            view.renderMusicAppSelection(app);
        }
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
        launchMusicApp(app);
    }

    /** Opens the app that currently owns playback, or the app the user picked last. */
    public void openPlayingMusicApp() {
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
        openCurrentlyPlayingApp();
    }

    /**
     * Player-card tap: the actually-playing source decides the destination — UPnP-cast/AI
     * playback opens the full-screen player page, a playing or paused local music app returns
     * to that app, and with nothing at all the selected app is opened.
     */
    public void openPlayingDetailsOrSourceApp() {
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
        if (upnpPlaying()) {
            openMusicPlayerPage();
            return;
        }
        if (mediaSessionActive()) {
            openCurrentlyPlayingApp();
            return;
        }
        if (mUpnpState != null && mUpnpState.hasRealSong()) {
            openMusicPlayerPage();
            return;
        }
        openCurrentlyPlayingApp();
    }

    private void openMusicPlayerPage() {
        Intent intent = new Intent(mContext, MusicActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mContext.startActivity(intent);
        } catch (RuntimeException e) {
            WifiMusicView view = getView();
            if (view != null) {
                view.showToast(mContext.getString(R.string.wifi_music_app_launch_failed,
                        mContext.getString(R.string.input_mode_wifi_music)));
            }
        }
    }

    private void openCurrentlyPlayingApp() {
        WifiMusicApp target = mediaSessionActive() && mMediaState.getApp() != null
                ? mMediaState.getApp() : mSelectedApp;
        if (target != null) {
            launchMusicApp(target);
        }
    }

    public void togglePlayback() {
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
        if (shouldControlMediaSession()) {
            mMediaSessionManager.playOrPause();
        } else {
            mPlaybackManager.playOrPause();
        }
    }

    public void nextTrack() {
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
        if (shouldControlMediaSession()) {
            mMediaSessionManager.next();
        } else {
            mPlaybackManager.next();
        }
    }

    public void previousTrack() {
        BootAutoPlaySuppressor.getInstance().markUserInteraction();
        if (shouldControlMediaSession()) {
            mMediaSessionManager.previous();
        } else {
            mPlaybackManager.previous();
        }
    }

    @Override
    public void onPlaybackChanged(UpnpPlaybackState state) {
        mUpnpState = state;
        WifiMusicView view = getView();
        if (view != null) {
            view.renderWifiPlayback(state);
        }
    }

    @Override
    public void onMediaPlaybackChanged(MediaSessionPlaybackState state) {
        mMediaState = state;
        WifiMusicView view = getView();
        if (view == null) {
            return;
        }
        view.renderMediaPlayback(state);
        WifiMusicApp playingApp = state.hasSession() ? state.getApp() : null;
        if (playingApp != mPlayingApp) {
            mPlayingApp = playingApp;
            if (playingApp != null) {
                view.renderMusicAppSelection(playingApp);
            }
        }
    }

    @Override
    public void detach() {
        mPlaybackManager.removeListener(this);
        mMediaSessionManager.removeListener(this);
        super.detach();
    }

    private boolean mediaSessionActive() {
        return mMediaState != null && mMediaState.hasSession();
    }

    /** 本机音乐App实际在播（暂停的会话不算在播）。 */
    private boolean mediaSessionPlaying() {
        return mediaSessionActive() && mMediaState.isPlaying();
    }

    /** 投歌/AI 歌曲实际在播或缓冲中。 */
    private boolean upnpPlaying() {
        return mUpnpState != null && (mUpnpState.isPlaying() || mUpnpState.isPreparing());
    }

    /**
     * 传输控制（播放/暂停、上下曲）应作用于哪个音源：在实际播放的音源上操作
     * （本机App在播优先于投歌在播），都没在播时沿用原偏好（有QQ会话控制QQ，否则控制投歌）。
     */
    private boolean shouldControlMediaSession() {
        if (mediaSessionPlaying()) {
            return true;
        }
        if (upnpPlaying()) {
            return false;
        }
        return mediaSessionActive();
    }

    private void launchMusicApp(WifiMusicApp app) {
        WifiMusicView view = getView();
        String packageName = app.getPackageName();
        if (TextUtils.isEmpty(packageName)) {
            if (view != null) {
                view.showToast(mContext.getString(R.string.wifi_music_app_not_configured,
                        mContext.getString(app.getLabelResId())));
            }
            return;
        }

        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            if (view != null) {
                view.showToast(mContext.getString(R.string.wifi_music_app_not_available,
                        mContext.getString(app.getLabelResId())));
            }
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            if (view != null) {
                view.showToast(mContext.getString(R.string.wifi_music_app_launch_failed,
                        mContext.getString(app.getLabelResId())));
            }
        }
    }
}
