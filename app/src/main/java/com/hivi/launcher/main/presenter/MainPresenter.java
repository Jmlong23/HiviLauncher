package com.hivi.launcher.main.presenter;

import android.content.Context;
import android.bluetooth.BluetoothDevice;
import android.os.SystemClock;
import com.hivi.launcher.utils.log.AppLog;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.main.model.MainStatusRepository;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.ui.MainView;
import com.hivi.launcher.music.model.BluetoothMediaController;

import java.text.SimpleDateFormat;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Locale;

public class MainPresenter extends BasePresenter<MainView> {
    public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private final Context mContext;
    private final MainStatusRepository mStatusRepository;
    private final AudioRouteController mAudioRouteController;
    private final BluetoothMediaController mBluetoothMediaController;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            runOnUiThreadDelayed(this, 1000L);
        }
    };
    private final AudioRouteController.AmplifierVolumeListener mAmplifierVolumeListener =
            (volumePercent, muted) -> updateAudioStatus();

    public MainPresenter(Context context, MainView view) {
        super(view);
        mContext = context.getApplicationContext();
        mStatusRepository = new MainStatusRepository(mContext);
        mAudioRouteController = AudioRouteController.getInstance();
        mBluetoothMediaController = BluetoothMediaController.getInstance();
    }

    public void init() {
        mAudioRouteController.addAmplifierVolumeListener(mAmplifierVolumeListener);
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

    public void updateConnectivity(String wifiLabel) {
        MainView view = getView();
        if (view != null) {
            view.updateConnectivity(wifiLabel, mStatusRepository.isBluetoothConnected(),
                    mStatusRepository.getBluetoothDeviceName());
        }
    }

    public void updateVolume() {
        updateAudioStatus();
    }

    public void adjustVolume(int direction) {
        mStatusRepository.adjustVolume(direction);
        updateAudioStatus();
    }

    public void setVolumePercent(int volumePercent) {
        mStatusRepository.setVolumePercent(volumePercent);
        updateAudioStatus();
    }

    public void toggleVolumeMute() {
        mStatusRepository.toggleMusicStreamMute();
        updateAudioStatus();
    }

    public void onBottomNavigationVolumeClicked() {
        AppLog.d(TAG, "Bottom navigation volume clicked");
        MainView view = getView();
        if (view != null) {
            view.showVolumeDialog(mStatusRepository.getVolumePercent(),
                    mStatusRepository.isMusicStreamMuted());
        }
    }

    public void showAuthorizationDialog() {
        MainView view = getView();
        if (view != null) {
            view.showAuthorization();
        }
    }

    public void onBottomNavigationBackClicked() {
        AppLog.d(TAG, "Bottom navigation back clicked");
        MainView view = getView();
        if (view != null) {
            view.navigateBack();
        }
    }

    public void onBottomNavigationHomeClicked() {
        AppLog.d(TAG, "Bottom navigation home clicked");
        MainView view = getView();
        if (view != null) {
            view.showHomePage();
        }
    }

    public void onBottomNavigationRecentsClicked() {
        AppLog.d(TAG, "Bottom navigation recents clicked");
        long downTime = SystemClock.uptimeMillis();
        boolean downInjected = injectSystemKeyEvent(KeyEvent.ACTION_DOWN, downTime, downTime);
        boolean upInjected = injectSystemKeyEvent(KeyEvent.ACTION_UP, downTime,
                SystemClock.uptimeMillis());
        if (!downInjected || !upInjected) {
            AppLog.e(TAG, "Unable to inject recent apps key event");
        }
    }

    private boolean injectSystemKeyEvent(int action, long downTime, long eventTime) {
        KeyEvent event = new KeyEvent(downTime, eventTime, action, KeyEvent.KEYCODE_APP_SWITCH,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD);
        try {
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Object inputManager = inputManagerClass.getMethod("getInstance").invoke(null);
            Method injectInputEvent = inputManagerClass.getMethod("injectInputEvent",
                    Class.forName("android.view.InputEvent"), int.class);
            Object injected = injectInputEvent.invoke(inputManager, event,
                    INJECT_INPUT_EVENT_MODE_ASYNC);
            return injected instanceof Boolean && (Boolean) injected;
        } catch (ReflectiveOperationException | SecurityException e) {
            AppLog.e(TAG, "Unable to inject system key event", e);
            return false;
        }
    }

    public void onBottomNavigationAppsClicked() {
        AppLog.d(TAG, "Bottom navigation apps clicked");
        navigateToPage(MainPage.SYSTEM_APPS);
    }

    public void onBottomNavigationSettingsClicked() {
        AppLog.d(TAG, "Bottom navigation settings clicked");
        navigateToPage(MainPage.SETTINGS);
    }

    public void onAiChatEntryClicked() {
        AppLog.d(TAG, "AI chat entry clicked");
        navigateToPage(MainPage.AI);
    }

    public void onInputModeClicked(MainPage page) {
        AppLog.d(TAG, "Input mode clicked: " + page);
        navigateToPage(page);
    }

    @Override
    public void detach() {
        mAudioRouteController.removeAmplifierVolumeListener(mAmplifierVolumeListener);
        super.detach();
    }

    private void updateDeviceStatus() {
        MainView view = getView();
        if (view != null) {
            view.updateConnectivity(mStatusRepository.getWifiLabel(),
                    mStatusRepository.isBluetoothConnected(),
                    mStatusRepository.getBluetoothDeviceName());
        }
        updateAudioStatus();
    }

    private void updateAudioStatus() {
        MainView view = getView();
        if (view != null) {
            view.updateVolumeMuted(mStatusRepository.isMusicStreamMuted());
            view.updateVolume(mStatusRepository.getVolumePercent());
        }
    }

    private void navigateToPage(MainPage page) {
        if (page == null) {
            return;
        }
        mAudioRouteController.selectMode(page);
        MainView view = getView();
        if (view != null) {
            view.showPage(page);
        }
    }

}
