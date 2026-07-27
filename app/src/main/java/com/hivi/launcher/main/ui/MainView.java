package com.hivi.launcher.main.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.main.model.MainPage;

public interface MainView extends BaseView {
    void updateClock(String time, String date);

    void updateConnectivity(String wifiLabel, boolean bluetoothConnected, String bluetoothDeviceName);

    void updateVolume(int volumePercent);

    void showAuthorization();

    void showPage(MainPage page);

    void showHomePage();
}
