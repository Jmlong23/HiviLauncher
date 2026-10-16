package com.hivi.launcher.bluetooth.presenter;

import android.content.Context;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.bluetooth.model.BluetoothModel;
import com.hivi.launcher.bluetooth.ui.BluetoothView;

public final class BluetoothPresenter extends BasePresenter<BluetoothView> {
    private final BluetoothModel mModel = new BluetoothModel();
    private Context mContext;

    public BluetoothPresenter(BluetoothView view) {
        super(view);
    }

    public void init(Context context) {
        if (context == null) {
            return;
        }
        mContext = context.getApplicationContext();
        mModel.start(mContext, state -> {
            BluetoothView view = getView();
            if (view != null) {
                view.renderBluetoothPage(state);
            }
        });
    }

    public void togglePlayback() {
        mModel.togglePlayback();
    }

    public void nextTrack() {
        mModel.nextTrack();
    }

    public void previousTrack() {
        mModel.previousTrack();
    }

    public void disconnect() {
        mModel.disconnect();
    }

    public void connect() {
        mModel.connect();
    }

    public void reset() {
        mModel.reset();
    }

    public void adjustVolume(int direction) {
        mModel.adjustVolume(direction);
    }

    public void setVolumePercent(int volumePercent) {
        mModel.setVolumePercent(volumePercent);
    }

    public void toggleMute() {
        mModel.toggleMute();
    }

    @Override
    public void detach() {
        mModel.stop();
        mContext = null;
        super.detach();
    }
}
