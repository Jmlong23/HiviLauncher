package com.hivi.launcher.main.presenter;

import android.content.Context;
import android.bluetooth.BluetoothDevice;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.main.data.MainStatusRepository;
import com.hivi.launcher.main.ui.MainView;
import com.hivi.launcher.music.model.BluetoothMediaController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainPresenter extends BasePresenter<MainView> {
    public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

    private final Context mContext;
    private final MainStatusRepository mStatusRepository;
    private final BluetoothMediaController mBluetoothMediaController;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            runOnUiThreadDelayed(this, 1000L);
        }
    };

    public MainPresenter(Context context, MainView view) {
        super(view);
        mContext = context.getApplicationContext();
        mStatusRepository = new MainStatusRepository(mContext);
        mBluetoothMediaController = BluetoothMediaController.getInstance();
    }

    public void init() {
        mBluetoothMediaController.start(mContext);
        updateClock();
        updateDeviceStatus();
    }

    public void startTicker() {
        removeUiThreadRunnable(mTicker);
        mTicker.run();
    }

    public void stopTicker() {
        removeUiThreadRunnable(mTicker);
    }

    public void onSystemStateChanged() {
        updateDeviceStatus();
    }

    public void onBluetoothStateChanged(BluetoothDevice device, String action, int connectionState) {
        mBluetoothMediaController.onBluetoothConnectionStateChanged(device, action,
                connectionState);
        updateDeviceStatus();
    }

    public void updateClock() {
        MainView view = getView();
        if (view == null) {
            return;
        }
        Locale locale = mContext.getResources().getConfiguration().locale;
        Date now = new Date();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", locale);
        SimpleDateFormat weekFormat = new SimpleDateFormat("E", locale);
        view.updateClock(timeFormat.format(now),
                dateFormat.format(now) + "   " + weekFormat.format(now));
    }

    public void updateConnectivity() {
        MainView view = getView();
        if (view != null) {
            view.updateConnectivity(mStatusRepository.getWifiLabel(),
                    mStatusRepository.isBluetoothConnected(),
                    mStatusRepository.getBluetoothDeviceName());
        }
    }

    public void updateVolume() {
        MainView view = getView();
        if (view != null) {
            view.updateVolume(mStatusRepository.getVolumePercent());
        }
    }

    public void adjustVolume(int direction) {
        mStatusRepository.adjustVolume(direction);
        updateVolume();
    }

    public void showAuthorizationDialog() {
        MainView view = getView();
        if (view != null) {
            view.showAuthorization();
        }
    }

    private void updateDeviceStatus() {
        MainView view = getView();
        if (view != null) {
            view.updateConnectivity(mStatusRepository.getWifiLabel(),
                    mStatusRepository.isBluetoothConnected(),
                    mStatusRepository.getBluetoothDeviceName());
            view.updateVolume(mStatusRepository.getVolumePercent());
        }
    }

}
