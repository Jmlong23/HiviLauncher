package com.hivi.launcher.wifi.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.wifi.model.WifiMusicApp;
import com.hivi.launcher.wifi.ui.WifiMusicView;

/**
 * Owns Wi-Fi music-page interactions. Playback metadata and controls are supplied exclusively by
 * the local UPnP renderer.
 */
public final class WifiMusicPresenter extends BasePresenter<WifiMusicView>
        implements UpnpPlaybackManager.Listener {
    private final Context mContext;
    private final UpnpPlaybackManager mPlaybackManager;

    public WifiMusicPresenter(Context context, WifiMusicView view) {
        super(view);
        mContext = context.getApplicationContext();
        mPlaybackManager = UpnpPlaybackManager.getInstance();
    }

    public void init() {
        mPlaybackManager.start(mContext);
        mPlaybackManager.addListener(this);
    }

    public void selectMusicApp(WifiMusicApp app) {
        if (app == null) {
            return;
        }

        WifiMusicView view = getView();
        if (view != null) {
            view.renderMusicAppSelection(app);
        }
        launchMusicApp(app);
    }

    public void togglePlayback() {
        mPlaybackManager.playOrPause();
    }

    public void nextTrack() {
        mPlaybackManager.next();
    }

    @Override
    public void onPlaybackChanged(UpnpPlaybackState state) {
        WifiMusicView view = getView();
        if (view != null) {
            view.renderWifiPlayback(state);
        }
    }

    @Override
    public void detach() {
        mPlaybackManager.removeListener(this);
        super.detach();
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
