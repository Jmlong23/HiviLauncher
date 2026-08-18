package com.hivi.launcher.wifi.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.wifi.model.MediaSessionPlaybackManager;
import com.hivi.launcher.wifi.model.MediaSessionPlaybackState;
import com.hivi.launcher.wifi.model.WifiMusicApp;
import com.hivi.launcher.wifi.ui.WifiMusicView;

/**
 * Owns Wi-Fi music-page interactions. Playback is fed by two sources: the media sessions of the
 * known music apps (QQ Music / NetEase / Kugou playing on this device) take priority, and the
 * local UPnP renderer is the fallback for DLNA-cast playback. Transport controls and the app
 * launched by tapping the player card follow whichever source is currently on screen.
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
        launchMusicApp(app);
    }

    /** Opens the app that currently owns playback, or the app the user picked last. */
    public void openPlayingMusicApp() {
        WifiMusicApp target = mediaSessionActive() && mMediaState.getApp() != null
                ? mMediaState.getApp() : mSelectedApp;
        if (target != null) {
            launchMusicApp(target);
        }
    }

    public void togglePlayback() {
        if (mediaSessionActive()) {
            mMediaSessionManager.playOrPause();
        } else {
            mPlaybackManager.playOrPause();
        }
    }

    public void nextTrack() {
        if (mediaSessionActive()) {
            mMediaSessionManager.next();
        } else {
            mPlaybackManager.next();
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
