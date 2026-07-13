package com.ljm.audiotoollib.upnpserver.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class UpnpNetworkUtils {
    WifiManager wifiManager;
    WifiInfo wifiInfo;

    public UpnpNetworkUtils(Context context) {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiInfo = wifiManager.getConnectionInfo();
    }

    public  String getWlanMACAddress() {
        @SuppressLint("MissingPermission") String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        return macAddress;
    }


    public  String getWlanIpAddress() {
        int ipAddress = wifiInfo.getIpAddress();
        String tmpIP = String.format("%d.%d.%d.%d", (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff));
        return tmpIP;
    }

}

