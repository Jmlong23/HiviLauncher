package com.hivi.launcher.music.presenter;

import android.content.Context;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.music.ui.MusicView;

public class MusicPresenter extends BasePresenter<MusicView>
        implements UpnpPlaybackManager.Listener {
    private final Context mContext;
    private final UpnpPlaybackManager mPlaybackManager;

    public MusicPresenter(Context context, MusicView view) {
        super(view);
        mContext = context.getApplicationContext();
        mPlaybackManager = UpnpPlaybackManager.getInstance();
    }

    public void init() {
        mPlaybackManager.start(mContext);
        mPlaybackManager.addListener(this);
    }

    public void togglePlay() {
        mPlaybackManager.playOrPause();
    }

    public void previous() {
        mPlaybackManager.previous();
    }

    public void next() {
        mPlaybackManager.next();
    }

    public void seekTo(long positionMs) {
        mPlaybackManager.seekTo(positionMs);
    }

    @Override
    public void onPlaybackChanged(UpnpPlaybackState state) {
        MusicView view = getView();
        if (view != null) {
            view.renderPlayback(state);
        }
    }

    @Override
    public void detach() {
        mPlaybackManager.removeListener(this);
        super.detach();
    }
}
