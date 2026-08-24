package com.hivi.launcher.wifi.model;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter around the platform Wi-Fi service.
 *
 * <p>This is a focused migration of the scan, saved-network, connection-state, and
 * authentication-failure behavior from HiviAudio's Wi-Fi manager. The UI receives immutable
 * network snapshots and never holds or persists a password.</p>
 */
public final class WifiModel {
    private static final long SCAN_TIMEOUT_MS = 5_000L;
    private static final long CONNECTION_TIMEOUT_MS = 25_000L;
    private static final long CONNECTION_DISCONNECTED_GRACE_MS = 3_000L;
    private static final String TAG = "WifiStatus";

    public enum ConnectionState {
        CONNECTING,
        CONNECTED,
        FAILED
    }

    public enum Error {
        LOCATION_PERMISSION_REQUIRED,
        WIFI_ENABLE_FAILED,
        CONNECTION_START_FAILED
    }

    public interface Callback {
        void onWifiNetworksChanged(List<WifiNetwork> networks, String connectedSsid);

        void onRefreshStateChanged(boolean refreshing);

        void onConnectionStateChanged(String ssid, ConnectionState state,
                boolean authenticationFailure);

        void onWifiError(Error error);
    }

    private final Context mContext;
    private final android.net.wifi.WifiManager mWifiManager;
    private final Callback mCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || mDestroyed) {
                return;
            }
            String action = intent.getAction();
            if (android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiStateChanged(intent);
            } else if (android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                publishNetworks();
                setRefreshing(false);
            } else if (android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                handleNetworkStateChanged(intent);
            } else if (android.net.wifi.WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                handleSupplicantStateChanged(intent);
            }
        }
    };
    private final Runnable mScanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mDestroyed && mRefreshing) {
                publishNetworks();
                setRefreshing(false);
            }
        }
    };
    private final Runnable mConnectionTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mDestroyed && !TextUtils.isEmpty(mConnectingSsid)) {
                finishConnectionAttempt(false, false);
            }
        }
    };
    private final Runnable mConnectionStatePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDestroyed || TextUtils.isEmpty(mConnectingSsid)) {
                return;
            }
            if (TextUtils.equals(mConnectingSsid, getConnectedSsid())) {
                finishConnectionAttempt(true, false);
                return;
            }
            mHandler.postDelayed(this, 750L);
        }
    };
    private final Runnable mConnectionDisconnectedRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mDestroyed && mConnectingNetworkActive
                    && !TextUtils.isEmpty(mConnectingSsid)
                    && TextUtils.isEmpty(getConnectedSsid())) {
                AppLog.d(TAG, "Connection lost while connecting. ssid=" + mConnectingSsid);
                finishConnectionAttempt(false, false);
            }
        }
    };

    private List<WifiNetwork> mNetworks = Collections.emptyList();
    private String mConnectingSsid = "";
    private boolean mConnectingNetworkActive;
    private boolean mReceiverRegistered;
    private boolean mRefreshing;
    private boolean mDestroyed;

    public WifiModel(Context context, Callback callback) {
        mContext = context.getApplicationContext();
        mWifiManager = (android.net.wifi.WifiManager) mContext.getSystemService(
                Context.WIFI_SERVICE);
        mCallback = callback;
    }

    public void start() {
        if (mDestroyed || mReceiverRegistered || mWifiManager == null) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(android.net.wifi.WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mWifiReceiver, filter);
        mReceiverRegistered = true;
    }

    public void refresh() {
        if (mDestroyed || mWifiManager == null) {
            return;
        }
        if (!hasScanPermission()) {
            setRefreshing(false);
            mCallback.onWifiError(Error.LOCATION_PERMISSION_REQUIRED);
            return;
        }

        if (!mWifiManager.isWifiEnabled()) {
            setRefreshing(true);
            try {
                if (!mWifiManager.setWifiEnabled(true)) {
                    setRefreshing(false);
                    mCallback.onWifiError(Error.WIFI_ENABLE_FAILED);
                }
            } catch (SecurityException e) {
                setRefreshing(false);
                mCallback.onWifiError(Error.WIFI_ENABLE_FAILED);
            }
            return;
        }

        setRefreshing(true);
        publishNetworks();
        try {
            mWifiManager.startScan();
        } catch (SecurityException e) {
            setRefreshing(false);
            mCallback.onWifiError(Error.LOCATION_PERMISSION_REQUIRED);
            return;
        }
        mHandler.removeCallbacks(mScanTimeoutRunnable);
        mHandler.postDelayed(mScanTimeoutRunnable, SCAN_TIMEOUT_MS);
    }

    public void connect(WifiNetwork network, String password) {
        if (mDestroyed || mWifiManager == null || network == null
                || TextUtils.isEmpty(network.getSsid())) {
            return;
        }
        if (network.isConnected()) {
            return;
        }

        String ssid = network.getSsid();
        mHandler.removeCallbacks(mConnectionTimeoutRunnable);
        mHandler.removeCallbacks(mConnectionDisconnectedRunnable);
        mConnectingSsid = ssid;
        mConnectingNetworkActive = false;
        AppLog.d(TAG, "Connect requested. ssid=" + ssid);
        publishNetworks();
        mCallback.onConnectionStateChanged(ssid, ConnectionState.CONNECTING, false);

        boolean connectionStarted = false;
        try {
            int networkId = findConfiguredNetworkId(ssid);
            if (networkId < 0 || password != null) {
                WifiConfiguration configuration = createConfiguration(network, password);
                if (networkId >= 0) {
                    configuration.networkId = networkId;
                    networkId = mWifiManager.updateNetwork(configuration);
                } else {
                    networkId = mWifiManager.addNetwork(configuration);
                }
                if (networkId >= 0) {
                    mWifiManager.saveConfiguration();
                }
            }
            if (networkId >= 0) {
                connectionStarted = mWifiManager.enableNetwork(networkId, true);
                if (connectionStarted) {
                    mWifiManager.reconnect();
                }
            }
        } catch (SecurityException e) {
            connectionStarted = false;
        }

        if (!connectionStarted) {
            mConnectingSsid = "";
            mConnectingNetworkActive = false;
            publishNetworks();
            mCallback.onWifiError(Error.CONNECTION_START_FAILED);
            return;
        }
        mHandler.postDelayed(mConnectionStatePollRunnable, 750L);
        mHandler.postDelayed(mConnectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
    }

    /**
     * Stops tracking the current user-initiated connection attempt without deleting its saved
     * network configuration. The platform may still complete the connection independently, but
     * callers will no longer receive a success/failure result for the cancelled attempt.
     */
    public void cancelConnection() {
        if (mDestroyed || TextUtils.isEmpty(mConnectingSsid)) {
            return;
        }
        mConnectingSsid = "";
        mConnectingNetworkActive = false;
        mHandler.removeCallbacks(mConnectionTimeoutRunnable);
        mHandler.removeCallbacks(mConnectionStatePollRunnable);
        mHandler.removeCallbacks(mConnectionDisconnectedRunnable);
        publishNetworks();
    }

    public void destroy() {
        if (mDestroyed) {
            return;
        }
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
        if (mReceiverRegistered) {
            try {
                mContext.unregisterReceiver(mWifiReceiver);
            } catch (IllegalArgumentException ignored) {
                // The receiver may already have been unregistered by the system.
            }
            mReceiverRegistered = false;
        }
    }

    private void handleWifiStateChanged(Intent intent) {
        int state = intent.getIntExtra(android.net.wifi.WifiManager.EXTRA_WIFI_STATE,
                android.net.wifi.WifiManager.WIFI_STATE_UNKNOWN);
        if (state == android.net.wifi.WifiManager.WIFI_STATE_ENABLED) {
            refresh();
        } else if (state == android.net.wifi.WifiManager.WIFI_STATE_DISABLED) {
            mNetworks = Collections.emptyList();
            mConnectingSsid = "";
            mConnectingNetworkActive = false;
            mHandler.removeCallbacks(mConnectionTimeoutRunnable);
            mHandler.removeCallbacks(mConnectionStatePollRunnable);
            mHandler.removeCallbacks(mConnectionDisconnectedRunnable);
            mCallback.onWifiNetworksChanged(mNetworks, "");
            setRefreshing(false);
        }
    }

    private void handleNetworkStateChanged(Intent intent) {
        NetworkInfo networkInfo = intent.getParcelableExtra(
                android.net.wifi.WifiManager.EXTRA_NETWORK_INFO);
        if (networkInfo == null) {
            return;
        }
        String connectedSsid = getConnectedSsid();
        NetworkInfo.DetailedState detailedState = networkInfo.getDetailedState();
        AppLog.d(TAG, "Network state changed. detailedState=" + detailedState
                + ", connected=" + networkInfo.isConnected()
                + ", connectedSsid=" + connectedSsid
                + ", connectingSsid=" + mConnectingSsid);
        if (networkInfo.isConnected()) {
            if (TextUtils.equals(connectedSsid, mConnectingSsid)) {
                finishConnectionAttempt(true, false);
            } else if (!TextUtils.isEmpty(mConnectingSsid)
                    && !TextUtils.isEmpty(connectedSsid)) {
                AppLog.d(TAG, "A different Wi-Fi network connected while connecting. connectedSsid="
                        + connectedSsid + ", connectingSsid=" + mConnectingSsid);
                finishConnectionAttempt(false, false);
            } else {
                publishNetworks();
            }
            return;
        }
        if (isConnectionInProgress(detailedState) && !TextUtils.isEmpty(mConnectingSsid)) {
            mConnectingNetworkActive = true;
            mHandler.removeCallbacks(mConnectionDisconnectedRunnable);
        } else if (detailedState == NetworkInfo.DetailedState.DISCONNECTED
                && mConnectingNetworkActive && !TextUtils.isEmpty(mConnectingSsid)) {
            mHandler.removeCallbacks(mConnectionDisconnectedRunnable);
            mHandler.postDelayed(mConnectionDisconnectedRunnable,
                    CONNECTION_DISCONNECTED_GRACE_MS);
        }
        if (detailedState == NetworkInfo.DetailedState.FAILED
                && !TextUtils.isEmpty(mConnectingSsid)) {
            finishConnectionAttempt(false, false);
        } else {
            publishNetworks();
        }
    }

    private void handleSupplicantStateChanged(Intent intent) {
        int error = intent.getIntExtra(android.net.wifi.WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
        if (error == android.net.wifi.WifiManager.ERROR_AUTHENTICATING
                && !TextUtils.isEmpty(mConnectingSsid)) {
            removeSavedNetwork(mConnectingSsid);
            finishConnectionAttempt(false, true);
        }
    }

    private void finishConnectionAttempt(boolean connected, boolean authenticationFailure) {
        String ssid = mConnectingSsid;
        mConnectingSsid = "";
        mConnectingNetworkActive = false;
        mHandler.removeCallbacks(mConnectionTimeoutRunnable);
        mHandler.removeCallbacks(mConnectionStatePollRunnable);
        mHandler.removeCallbacks(mConnectionDisconnectedRunnable);
        AppLog.d(TAG, "Connection attempt finished. ssid=" + ssid + ", connected=" + connected
                + ", authenticationFailure=" + authenticationFailure);
        publishNetworks();
        if (!TextUtils.isEmpty(ssid)) {
            mCallback.onConnectionStateChanged(ssid,
                    connected ? ConnectionState.CONNECTED : ConnectionState.FAILED,
                    authenticationFailure);
        }
    }

    private void setRefreshing(boolean refreshing) {
        if (mRefreshing == refreshing) {
            return;
        }
        mRefreshing = refreshing;
        if (!refreshing) {
            mHandler.removeCallbacks(mScanTimeoutRunnable);
        }
        mCallback.onRefreshStateChanged(refreshing);
    }

    private void publishNetworks() {
        if (mWifiManager == null || mDestroyed) {
            return;
        }
        List<ScanResult> scanResults;
        List<WifiConfiguration> configuredNetworks;
        try {
            scanResults = mWifiManager.getScanResults();
            configuredNetworks = mWifiManager.getConfiguredNetworks();
        } catch (SecurityException e) {
            mCallback.onWifiError(Error.LOCATION_PERMISSION_REQUIRED);
            return;
        }

        Set<String> savedSsids = new HashSet<>();
        if (configuredNetworks != null) {
            for (WifiConfiguration configuration : configuredNetworks) {
                if (configuration != null) {
                    String ssid = normalizeSsid(configuration.SSID);
                    if (!TextUtils.isEmpty(ssid)) {
                        savedSsids.add(ssid);
                    }
                }
            }
        }

        Map<String, ScanResult> strongestResults = new HashMap<>();
        if (scanResults != null) {
            for (ScanResult result : scanResults) {
                if (result == null || TextUtils.isEmpty(result.SSID)) {
                    continue;
                }
                ScanResult existing = strongestResults.get(result.SSID);
                if (existing == null || result.level > existing.level) {
                    strongestResults.put(result.SSID, result);
                }
            }
        }

        String connectedSsid = getConnectedSsid();
        List<WifiNetwork> networks = new ArrayList<>(strongestResults.size());
        for (ScanResult result : strongestResults.values()) {
            networks.add(WifiNetwork.fromScanResult(result, savedSsids.contains(result.SSID),
                    connectedSsid, mConnectingSsid));
        }
        Collections.sort(networks, new Comparator<WifiNetwork>() {
            @Override
            public int compare(WifiNetwork left, WifiNetwork right) {
                if (left.isConnected() != right.isConnected()) {
                    return left.isConnected() ? -1 : 1;
                }
                if (left.isConnecting() != right.isConnecting()) {
                    return left.isConnecting() ? -1 : 1;
                }
                return Integer.compare(right.getSignalLevel(), left.getSignalLevel());
            }
        });
        mNetworks = Collections.unmodifiableList(networks);
        mCallback.onWifiNetworksChanged(mNetworks, connectedSsid);
    }

    private boolean hasScanPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String getConnectedSsid() {
        return WifiConnectionStatus.getConnectedSsid(mContext);
    }

    private static boolean isConnectionInProgress(NetworkInfo.DetailedState state) {
        return state == NetworkInfo.DetailedState.CONNECTING
                || state == NetworkInfo.DetailedState.AUTHENTICATING
                || state == NetworkInfo.DetailedState.OBTAINING_IPADDR;
    }

    private int findConfiguredNetworkId(String ssid) {
        List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();
        if (configurations == null) {
            return -1;
        }
        for (WifiConfiguration configuration : configurations) {
            if (configuration != null && TextUtils.equals(ssid, normalizeSsid(configuration.SSID))) {
                return configuration.networkId;
            }
        }
        return -1;
    }

    private void removeSavedNetwork(String ssid) {
        try {
            int networkId = findConfiguredNetworkId(ssid);
            if (networkId >= 0) {
                mWifiManager.removeNetwork(networkId);
                mWifiManager.saveConfiguration();
            }
        } catch (SecurityException ignored) {
            // The failure is still surfaced to the user and they can re-enter the password.
        }
    }

    private WifiConfiguration createConfiguration(WifiNetwork network, String password) {
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = quoteValue(network.getSsid());
        configuration.status = WifiConfiguration.Status.ENABLED;

        if (!network.isSecure()) {
            configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            return configuration;
        }

        String capabilities = network.getCapabilities().toUpperCase();
        if (capabilities.contains("WEP")) {
            configuration.wepKeys[0] = quoteValue(password);
            configuration.wepTxKeyIndex = 0;
            configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            configuration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        } else if (capabilities.contains("SAE") && !capabilities.contains("PSK")) {
            configuration.preSharedKey = quoteValue(password);
            configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
        } else {
            configuration.preSharedKey = quoteValue(password);
            configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            configuration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            configuration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            configuration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            configuration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            configuration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        }
        return configuration;
    }

    private static String normalizeSsid(String ssid) {
        if (TextUtils.isEmpty(ssid) || "<unknown ssid>".equalsIgnoreCase(ssid)) {
            return "";
        }
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    private static String quoteValue(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\\\"")) + "\"";
    }
}
