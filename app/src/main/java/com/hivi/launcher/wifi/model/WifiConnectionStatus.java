package com.hivi.launcher.wifi.model;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

/**
 * Reads the SSID of the active Wi-Fi connection shared by the settings and top navigation UIs.
 */
public final class WifiConnectionStatus {
    private WifiConnectionStatus() {
    }

    public static String getConnectedSsid(Context context) {
        if (context == null) {
            return "";
        }
        Context appContext = context.getApplicationContext();
        ConnectivityManager connectivityManager = (ConnectivityManager) appContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return "";
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return "";
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null
                || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "";
        }

        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return "";
        }
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo == null ? "" : normalizeSsid(wifiInfo.getSSID());
        } catch (SecurityException ignored) {
            return "";
        }
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
}
