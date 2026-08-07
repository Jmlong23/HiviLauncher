package com.hivi.launcher.onboarding.presenter;

import android.content.Context;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.onboarding.ui.OnboardingView;
import com.hivi.launcher.wifi.model.WifiModel;
import com.hivi.launcher.wifi.model.WifiNetwork;

import java.util.List;

/**
 * Owns the hardware-facing work used by the first-use guide. The guide reuses the same Wi-Fi
 * model and amplifier controller as the regular settings and home screens.
 */
public final class OnboardingPresenter extends BasePresenter<OnboardingView> {
    private final AudioRouteController mAudioRouteController = AudioRouteController.getInstance();
    private final AudioRouteController.AmplifierVolumeListener mAmplifierVolumeListener =
            (volumePercent, muted) -> {
                OnboardingView view = getView();
                if (view != null) {
                    view.renderAmplifierVolume(volumePercent, muted);
                }
            };

    private Context mContext;
    private WifiModel mWifiModel;
    private WifiNetwork mSelectedNetwork;
    private boolean mConnectionInProgress;

    public OnboardingPresenter(OnboardingView view) {
        super(view);
    }

    public void init(Context context) {
        if (context == null) {
            return;
        }
        mContext = context.getApplicationContext();
        mAudioRouteController.initialize(mContext);
        mAudioRouteController.addAmplifierVolumeListener(mAmplifierVolumeListener);
    }

    public void startWifiSetup() {
        if (mContext == null) {
            return;
        }
        if (mWifiModel == null) {
            mWifiModel = new WifiModel(mContext, new WifiModel.Callback() {
                @Override
                public void onWifiNetworksChanged(List<WifiNetwork> networks,
                        String connectedSsid) {
                    OnboardingView view = getView();
                    if (view != null) {
                        view.renderWifiNetworks(networks);
                    }
                }

                @Override
                public void onRefreshStateChanged(boolean refreshing) {
                    OnboardingView view = getView();
                    if (view != null) {
                        view.setWifiRefreshing(refreshing);
                    }
                }

                @Override
                public void onConnectionStateChanged(String ssid, WifiModel.ConnectionState state,
                        boolean authenticationFailure) {
                    handleWifiConnectionState(ssid, state, authenticationFailure);
                }

                @Override
                public void onWifiError(WifiModel.Error error) {
                    handleWifiError(error);
                }
            });
            mWifiModel.start();
        }
        refreshWifiNetworks();
    }

    public void refreshWifiNetworks() {
        if (mWifiModel != null) {
            mWifiModel.refresh();
        }
    }

    public void selectWifiNetwork(WifiNetwork network) {
        if (network == null || network.isConnected() || mWifiModel == null) {
            return;
        }
        mSelectedNetwork = network;
        if (network.isEnterprise()) {
            OnboardingView view = getView();
            if (view != null) {
                view.showWifiUnavailable(getString(R.string.settings_wifi_unsupported_security));
            }
            return;
        }
        if (network.isSecure() && !network.isSaved()) {
            return;
        }
        connectSelectedNetwork(null);
    }

    public boolean selectedWifiNetworkNeedsPassword() {
        return mSelectedNetwork != null && mSelectedNetwork.isSecure()
                && !mSelectedNetwork.isSaved();
    }

    public WifiNetwork getSelectedWifiNetwork() {
        return mSelectedNetwork;
    }

    public void connectSelectedNetwork(String password) {
        if (mWifiModel == null || mSelectedNetwork == null) {
            return;
        }
        if (mSelectedNetwork.isSecure() && !mSelectedNetwork.isSaved()
                && TextUtils.isEmpty(password)) {
            OnboardingView view = getView();
            if (view != null) {
                view.showToast(getString(R.string.settings_wifi_password_required));
            }
            return;
        }
        mConnectionInProgress = true;
        mWifiModel.connect(mSelectedNetwork, password);
    }

    public void cancelWifiConnection() {
        mConnectionInProgress = false;
        if (mWifiModel != null) {
            mWifiModel.cancelConnection();
        }
    }

    public void adjustAmplifierVolume(int direction) {
        mAudioRouteController.adjustAmplifierVolume(direction);
    }

    public void setAmplifierVolume(int volumePercent) {
        mAudioRouteController.setAmplifierVolume(volumePercent);
    }

    public void toggleAmplifierMute() {
        mAudioRouteController.toggleAmplifierMute();
    }

    public int getAmplifierVolume() {
        return mAudioRouteController.getAmplifierVolumePercent();
    }

    public boolean isAmplifierMuted() {
        return mAudioRouteController.isAmplifierMuted();
    }

    public void selectInputMode(MainPage page) {
        mAudioRouteController.selectMode(page);
    }

    public void destroy() {
        if (mWifiModel != null) {
            mWifiModel.destroy();
            mWifiModel = null;
        }
        mAudioRouteController.removeAmplifierVolumeListener(mAmplifierVolumeListener);
        mContext = null;
        detach();
    }

    private void handleWifiConnectionState(String ssid, WifiModel.ConnectionState state,
            boolean authenticationFailure) {
        if (!mConnectionInProgress) {
            return;
        }
        OnboardingView view = getView();
        if (view == null) {
            return;
        }
        if (state == WifiModel.ConnectionState.CONNECTING) {
            view.showWifiConnecting(ssid);
        } else if (state == WifiModel.ConnectionState.CONNECTED) {
            mConnectionInProgress = false;
            view.showWifiConnected(ssid);
        } else if (state == WifiModel.ConnectionState.FAILED) {
            mConnectionInProgress = false;
            view.showWifiConnectionFailed(mSelectedNetwork, authenticationFailure);
        }
    }

    private void handleWifiError(WifiModel.Error error) {
        if (error == WifiModel.Error.CONNECTION_START_FAILED && mConnectionInProgress) {
            mConnectionInProgress = false;
            OnboardingView view = getView();
            if (view != null) {
                view.showWifiConnectionFailed(mSelectedNetwork, false);
            }
            return;
        }

        OnboardingView view = getView();
        if (view == null) {
            return;
        }
        switch (error) {
            case LOCATION_PERMISSION_REQUIRED:
                view.showWifiUnavailable(getString(R.string.settings_wifi_permission_required));
                break;
            case WIFI_ENABLE_FAILED:
                view.showWifiUnavailable(getString(R.string.settings_wifi_enable_failed));
                break;
            case CONNECTION_START_FAILED:
                view.showWifiUnavailable(getString(R.string.settings_wifi_connection_failed));
                break;
            default:
                break;
        }
    }

    private String getString(int resId) {
        return mContext == null ? "" : mContext.getString(resId);
    }
}
