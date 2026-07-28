package com.hivi.launcher.wifi.model;

import android.net.wifi.ScanResult;
import android.text.TextUtils;

/**
 * Immutable Wi-Fi network data rendered by the network settings page.
 *
 * <p>The model deliberately carries only the information needed by the UI and by the legacy
 * {@code WifiConfiguration}-based connection flow. Passwords are never retained here.</p>
 */
public final class WifiNetwork {
    private final String mSsid;
    private final String mCapabilities;
    private final int mSignalLevel;
    private final boolean mSecure;
    private final boolean mEnterprise;
    private final boolean mSaved;
    private final boolean mConnected;
    private final boolean mConnecting;

    WifiNetwork(String ssid, String capabilities, int signalLevel, boolean secure,
            boolean enterprise, boolean saved, boolean connected, boolean connecting) {
        mSsid = ssid;
        mCapabilities = capabilities;
        mSignalLevel = signalLevel;
        mSecure = secure;
        mEnterprise = enterprise;
        mSaved = saved;
        mConnected = connected;
        mConnecting = connecting;
    }

    static WifiNetwork fromScanResult(ScanResult result, boolean saved, String connectedSsid,
            String connectingSsid) {
        String ssid = result.SSID == null ? "" : result.SSID.trim();
        String capabilities = result.capabilities == null ? "" : result.capabilities;
        String upperCapabilities = capabilities.toUpperCase();
        boolean enterprise = upperCapabilities.contains("EAP");
        boolean secure = upperCapabilities.contains("WEP")
                || upperCapabilities.contains("PSK")
                || upperCapabilities.contains("SAE")
                || enterprise;
        return new WifiNetwork(ssid, capabilities, result.level, secure, enterprise, saved,
                TextUtils.equals(ssid, connectedSsid), TextUtils.equals(ssid, connectingSsid));
    }

    public String getSsid() {
        return mSsid;
    }

    public String getCapabilities() {
        return mCapabilities;
    }

    public int getSignalLevel() {
        return mSignalLevel;
    }

    public boolean isSecure() {
        return mSecure;
    }

    public boolean isEnterprise() {
        return mEnterprise;
    }

    public boolean isSaved() {
        return mSaved;
    }

    public boolean isConnected() {
        return mConnected;
    }

    public boolean isConnecting() {
        return mConnecting;
    }
}
