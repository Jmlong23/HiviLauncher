package com.hivi.launcher.wifi.presenter;

import android.content.Context;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.wifi.model.WifiModel;
import com.hivi.launcher.wifi.model.WifiNetwork;
import com.hivi.launcher.wifi.ui.WifiView;

import java.util.Collections;
import java.util.List;

public final class WifiPresenter extends BasePresenter<WifiView> {
    private WifiModel mModel;
    private Context mContext;
    private List<WifiNetwork> mNetworks = Collections.emptyList();
    private boolean mRefreshing;
    private WifiNetwork mLastSelectedNetwork;

    public WifiPresenter(WifiView view) {
        super(view);
    }

    public void init(Context context) {
        mContext = context.getApplicationContext();
        if (mModel == null) {
            mModel = new WifiModel(mContext, new WifiModel.Callback() {
                @Override
                public void onWifiNetworksChanged(List<WifiNetwork> networks,
                        String connectedSsid) {
                    mNetworks = networks;
                    renderNetworks(connectedSsid);
                }

                @Override
                public void onRefreshStateChanged(boolean refreshing) {
                    mRefreshing = refreshing;
                    WifiView view = getView();
                    if (view != null) {
                        view.setWifiRefreshing(refreshing);
                    }
                    if (!refreshing && mNetworks.isEmpty()) {
                        showEmptyState(R.string.settings_no_network_message);
                    }
                }

                @Override
                public void onConnectionStateChanged(String ssid, WifiModel.ConnectionState state,
                        boolean authenticationFailure) {
                    handleConnectionStateChanged(ssid, state, authenticationFailure);
                }

                @Override
                public void onWifiError(WifiModel.Error error) {
                    handleWifiError(error);
                }
            });
            mModel.start();
        }
        refresh();
    }

    public void refresh() {
        if (mModel != null) {
            mModel.refresh();
        }
    }

    public void onWifiNetworkSelected(WifiNetwork network) {
        if (network == null || network.isConnected()) {
            return;
        }
        mLastSelectedNetwork = network;
        WifiView view = getView();
        if (view == null) {
            return;
        }
        if (network.isEnterprise()) {
            view.showToast(getString(R.string.settings_wifi_unsupported_security));
            return;
        }
        if (network.isSecure() && !network.isSaved()) {
            view.showWifiPasswordDialog(network);
            return;
        }
        mModel.connect(network, null);
    }

    public void connectWithPassword(WifiNetwork network, String password) {
        if (network == null || TextUtils.isEmpty(password)) {
            WifiView view = getView();
            if (view != null) {
                view.showToast(getString(R.string.settings_wifi_password_required));
            }
            return;
        }
        mLastSelectedNetwork = network;
        if (mModel != null) {
            mModel.connect(network, password);
        }
    }

    public void showPermissionRequired(Context context) {
        mContext = context.getApplicationContext();
        WifiView view = getView();
        if (view != null) {
            view.setWifiRefreshing(false);
            view.renderWifiNetworks(Collections.<WifiNetwork>emptyList(), "");
            view.showWifiEmptyState(getString(R.string.settings_wifi_permission_required));
        }
    }

    public void destroy() {
        if (mModel != null) {
            mModel.destroy();
            mModel = null;
        }
        detach();
    }

    private void renderNetworks(String connectedSsid) {
        WifiView view = getView();
        if (view == null) {
            return;
        }
        view.renderWifiNetworks(mNetworks, connectedSsid);
        if (!mRefreshing && mNetworks.isEmpty()) {
            showEmptyState(R.string.settings_no_network_message);
        }
    }

    private void handleConnectionStateChanged(String ssid, WifiModel.ConnectionState state,
            boolean authenticationFailure) {
        WifiView view = getView();
        if (view == null) {
            return;
        }
        if (state == WifiModel.ConnectionState.CONNECTED) {
            view.showToast(getString(R.string.settings_wifi_connected_toast, ssid));
            return;
        }
        if (state != WifiModel.ConnectionState.FAILED) {
            return;
        }

        view.showToast(getString(authenticationFailure
                ? R.string.settings_wifi_authentication_failed
                : R.string.settings_wifi_connection_failed));
        if (authenticationFailure && mLastSelectedNetwork != null
                && TextUtils.equals(ssid, mLastSelectedNetwork.getSsid())) {
            view.showWifiPasswordDialog(mLastSelectedNetwork);
        }
    }

    private void handleWifiError(WifiModel.Error error) {
        WifiView view = getView();
        if (view == null) {
            return;
        }
        switch (error) {
            case LOCATION_PERMISSION_REQUIRED:
                showEmptyState(R.string.settings_wifi_permission_required);
                break;
            case WIFI_ENABLE_FAILED:
                view.showToast(getString(R.string.settings_wifi_enable_failed));
                showEmptyState(R.string.settings_wifi_enable_failed);
                break;
            case CONNECTION_START_FAILED:
                view.showToast(getString(R.string.settings_wifi_connection_failed));
                break;
            default:
                break;
        }
    }

    private void showEmptyState(int messageResId) {
        WifiView view = getView();
        if (view != null) {
            view.showWifiEmptyState(getString(messageResId));
        }
    }

    private String getString(int resId, Object... formatArgs) {
        if (mContext == null) {
            return "";
        }
        return formatArgs.length == 0 ? mContext.getString(resId)
                : mContext.getString(resId, formatArgs);
    }
}
