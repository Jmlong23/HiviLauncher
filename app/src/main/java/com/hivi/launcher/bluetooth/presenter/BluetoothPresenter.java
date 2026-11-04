package com.hivi.launcher.bluetooth.presenter;

import android.content.Context;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.bluetooth.model.BluetoothModel;
import com.hivi.launcher.bluetooth.model.BluetoothPageState;
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
        mModel.start(mContext, new BluetoothModel.Listener() {
            @Override
            public void onBluetoothPageStateChanged(BluetoothPageState state) {
                BluetoothView view = getView();
                if (view != null) {
                    view.renderBluetoothPage(state);
                }
            }

            @Override
            public void onBluetoothFftData(byte[] fftData) {
                runOnUiThread(() -> {
                    BluetoothView view = getView();
                    if (view != null) {
                        view.renderBluetoothFftData(fftData);
                    }
                });
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

    @Override
    public void detach() {
        mModel.stop();
        mContext = null;
        super.detach();
    }
}
