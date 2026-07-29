package com.hivi.launcher.bluetooth.presenter;

import android.content.Context;

import com.hivi.launcher.R;
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

    public void disconnect() {
        showActionResult(mModel.disconnectConnectedDevice(),
                R.string.bluetooth_disconnect_requested, R.string.bluetooth_disconnect_failed);
    }

    public void reset() {
        showActionResult(mModel.resetConnectedDevice(),
                R.string.bluetooth_reset_requested, R.string.bluetooth_reset_failed);
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

    private void showActionResult(boolean succeeded, int successMessageResId, int failureMessageResId) {
        BluetoothView view = getView();
        if (view != null && mContext != null) {
            view.showToast(mContext.getString(succeeded ? successMessageResId : failureMessageResId));
        }
    }
}
