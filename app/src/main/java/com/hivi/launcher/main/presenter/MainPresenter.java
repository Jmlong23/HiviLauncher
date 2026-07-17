package com.hivi.launcher.main.presenter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.bluetooth.BluetoothDevice;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.main.data.MainStatusRepository;
import com.hivi.launcher.main.model.MainStatus;
import com.hivi.launcher.main.model.MusicInfo;
import com.hivi.launcher.main.ui.MainView;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.settings.data.SystemSettingsNavigator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainPresenter extends BasePresenter<MainView> {
    public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

    private static final String[] MUSIC_PACKAGES = {
            "com.tencent.qqmusic",
            "com.netease.cloudmusic",
            "com.kugou.android"
    };

    private final Context mContext;
    private final MainStatusRepository mStatusRepository;
    private final SystemSettingsNavigator mSettingsNavigator;
    private final BluetoothMediaController mBluetoothMediaController;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateMedia();
            runOnUiThreadDelayed(this, 1000L);
        }
    };

    public MainPresenter(Context context, MainView view) {
        super(view);
        mContext = context.getApplicationContext();
        mStatusRepository = new MainStatusRepository(mContext);
        mSettingsNavigator = new SystemSettingsNavigator(mContext);
        mBluetoothMediaController = BluetoothMediaController.getInstance();
    }

    public void init() {
        UpnpPlaybackManager.getInstance().start(mContext);
        mBluetoothMediaController.start(mContext);
        updateClock();
        updateDeviceStatus();
        updateMedia();
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
        updateMedia();
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

    public MainStatus getCurrentStatus() {
        return mStatusRepository.loadStatus();
    }

    public void adjustVolume(int direction) {
        mStatusRepository.adjustVolume(direction);
        updateVolume();
    }

    public void updateMedia() {
        MainView view = getView();
        if (view == null) {
            return;
        }
        MusicInfo musicInfo = mStatusRepository.getMusicInfo();
        if (musicInfo != null) {
            view.updateMusic(musicInfo.getTitle(), musicInfo.getArtist());
        } else {
            view.updateMusic(mContext.getString(R.string.main_music_empty_title),
                    mContext.getString(R.string.main_music_empty_artist));
        }
    }

    public void launchFirstAvailableMusicApp() {
        for (String pkg : MUSIC_PACKAGES) {
            if (launchPackage(pkg)) {
                return;
            }
        }
        MainView view = getView();
        if (view != null) {
            view.showToast(mContext.getString(R.string.main_music_apps_not_installed));
        }
    }

    public void openMusicPlayer() {
        MainView view = getView();
        if (view != null) {
            view.openMusicPlayer();
        }
    }

    public void openSystemApps() {
        MainView view = getView();
        if (view != null) {
            view.openSystemApps();
        }
    }

    public void showAuthorizationDialog() {
        MainView view = getView();
        if (view != null) {
            view.showAuthorization();
        }
    }

    public void openSystemSettings() {
        if (!mSettingsNavigator.openSystemSettings()) {
            showSettingsUnavailable();
        }
    }

    public void openScreensaverSettings() {
        if (!mSettingsNavigator.openScreensaverSettings()) {
            showSettingsUnavailable();
        }
    }

    private void updateDeviceStatus() {
        MainStatus status = mStatusRepository.loadStatus();
        MainView view = getView();
        if (view != null) {
            view.updateConnectivity(status.getWifiLabel(), status.isBluetoothConnected(),
                    status.getBluetoothDeviceName());
            view.updateVolume(status.getVolumePercent());
        }
    }

    private void showSettingsUnavailable() {
        MainView view = getView();
        if (view != null) {
            view.showToast(mContext.getString(R.string.main_settings));
        }
    }

    private boolean launchPackage(String packageName) {
        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            return false;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(launchIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
