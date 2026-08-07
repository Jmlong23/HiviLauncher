package com.hivi.launcher.onboarding.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.wifi.model.WifiNetwork;

import java.util.List;

public interface OnboardingView extends BaseView {
    void renderWifiNetworks(List<WifiNetwork> networks);

    void setWifiRefreshing(boolean refreshing);

    void showWifiUnavailable(String message);

    void showWifiConnecting(String ssid);

    void showWifiConnected(String ssid);

    void showWifiConnectionFailed(WifiNetwork network, boolean authenticationFailure);

    void renderAmplifierVolume(int volumePercent, boolean muted);
}
