package com.hivi.launcher.wifi.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.wifi.model.WifiNetwork;
import com.hivi.launcher.wifi.presenter.WifiPresenter;

import java.util.List;

public final class WifiFragment extends BaseFragment<WifiPresenter>
        implements WifiView {
    @Override
    protected WifiPresenter createPresenter() {
        return new WifiPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_wifi;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_wifi_music;
    }

    /*
     * This fragment is the Wi-Fi music input page. Network configuration is displayed in
     * SettingsFragment, which owns the Wi-Fi network list and password dialog.
     */
    @Override
    public void renderWifiNetworks(List<WifiNetwork> networks, String connectedSsid) {
    }

    @Override
    public void setWifiRefreshing(boolean refreshing) {
    }

    @Override
    public void showWifiEmptyState(String message) {
    }

    @Override
    public void showWifiPasswordDialog(WifiNetwork network) {
    }
}
