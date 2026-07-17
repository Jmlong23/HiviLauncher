package com.hivi.launcher.music.presenter;

import android.content.Context;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.music.model.BluetoothPlaybackState;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.music.ui.MusicView;

public class MusicPresenter extends BasePresenter<MusicView>
        implements UpnpPlaybackManager.Listener, BluetoothMediaController.Listener {
    private final Context mContext;
    private final UpnpPlaybackManager mPlaybackManager;
    private final BluetoothMediaController mBluetoothMediaController;

    public MusicPresenter(Context context, MusicView view) {
        super(view);
        mContext = context.getApplicationContext();
        mPlaybackManager = UpnpPlaybackManager.getInstance();
        mBluetoothMediaController = BluetoothMediaController.getInstance();
    }

    public void init() {
        mPlaybackManager.start(mContext);
        mPlaybackManager.addListener(this);
        mBluetoothMediaController.start(mContext);
        mBluetoothMediaController.addListener(this);
    }

    public void togglePlay() {
        if (mBluetoothMediaController.isPlaybackControlAvailable()) {
            mBluetoothMediaController.playOrPause();
        } else {
            mPlaybackManager.playOrPause();
        }
    }

    public void previous() {
        if (mBluetoothMediaController.isPlaybackControlAvailable()) {
            mBluetoothMediaController.previous();
        } else {
            mPlaybackManager.previous();
        }
    }

    public void next() {
        if (mBluetoothMediaController.isPlaybackControlAvailable()) {
            mBluetoothMediaController.next();
        } else {
            mPlaybackManager.next();
        }
    }

    public void seekTo(long positionMs) {
        if (mBluetoothMediaController.isPlaybackControlAvailable()) {
            mBluetoothMediaController.seekTo(positionMs);
        } else {
            mPlaybackManager.seekTo(positionMs);
        }
    }

    @Override
    public void onPlaybackChanged(UpnpPlaybackState state) {
        if (mBluetoothMediaController.isPlaybackControlAvailable()) {
            return;
        }
        MusicView view = getView();
        if (view != null) {
            view.renderPlayback(state);
        }
    }

    @Override
    public void onBluetoothPlaybackChanged(BluetoothPlaybackState state) {
        if (!mBluetoothMediaController.isPlaybackControlAvailable()) {
            MusicView view = getView();
            if (view != null) {
                view.renderPlayback(mPlaybackManager.getCurrentState());
            }
            return;
        }
        MusicView view = getView();
        if (view != null) {
            view.renderPlayback(new UpnpPlaybackState(state.getTitle(), state.getArtist(),
                    state.getAlbum(), String.valueOf(state.getLyric()), "",
                    state.getPositionMs(), state.getDurationMs(),
                    state.isPlaying(), false));
        }
    }

    @Override
    public void detach() {
        mPlaybackManager.removeListener(this);
        mBluetoothMediaController.removeListener(this);
        super.detach();
    }
}
