package com.hivi.launcher.main.model;

public class MainStatus {
    private final String wifiLabel;
    private final boolean bluetoothConnected;
    private final String bluetoothDeviceName;
    private final int volumePercent;
    private final MusicInfo musicInfo;

    public MainStatus(String wifiLabel, boolean bluetoothConnected, String bluetoothDeviceName,
            int volumePercent, MusicInfo musicInfo) {
        this.wifiLabel = wifiLabel;
        this.bluetoothConnected = bluetoothConnected;
        this.bluetoothDeviceName = bluetoothDeviceName;
        this.volumePercent = volumePercent;
        this.musicInfo = musicInfo;
    }

    public String getWifiLabel() {
        return wifiLabel;
    }

    public boolean isBluetoothConnected() {
        return bluetoothConnected;
    }

    public String getBluetoothDeviceName() {
        return bluetoothDeviceName;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public MusicInfo getMusicInfo() {
        return musicInfo;
    }
}
