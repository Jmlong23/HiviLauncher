package com.hivi.launcher.wifi.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.wifi.model.WifiNetwork;

import java.util.List;

public interface WifiView extends BaseView {
    void renderWifiNetworks(List<WifiNetwork> networks, String connectedSsid);

    void setWifiRefreshing(boolean refreshing);

    void showWifiEmptyState(String message);

    void showWifiPasswordDialog(WifiNetwork network);
}
