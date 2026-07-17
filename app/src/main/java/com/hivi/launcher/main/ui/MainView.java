package com.hivi.launcher.main.ui;

import com.hivi.launcher.base.BaseView;

public interface MainView extends BaseView {
    void updateClock(String time, String date);

    void updateConnectivity(String wifiLabel, boolean bluetoothConnected, String bluetoothDeviceName);

    void updateVolume(int volumePercent);

    void updateMusic(CharSequence title, CharSequence artist);

    void openMusicPlayer();

    void openSystemApps();

    void showAuthorization();
}
