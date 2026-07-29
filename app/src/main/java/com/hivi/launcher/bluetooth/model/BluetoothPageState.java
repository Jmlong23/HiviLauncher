package com.hivi.launcher.bluetooth.model;

import com.hivi.launcher.music.model.BluetoothPlaybackState;

/**
 * Immutable snapshot rendered by the Bluetooth input page.
 */
public final class BluetoothPageState {
    private final boolean connected;
    private final String deviceName;
    private final boolean playbackControlAvailable;
    private final BluetoothPlaybackState playbackState;
    private final int volumePercent;
    private final boolean muted;

    public BluetoothPageState(boolean connected, String deviceName,
            boolean playbackControlAvailable, BluetoothPlaybackState playbackState,
            int volumePercent, boolean muted) {
        this.connected = connected;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.playbackControlAvailable = playbackControlAvailable;
        this.playbackState = playbackState == null
                ? BluetoothPlaybackState.empty() : playbackState;
        this.volumePercent = Math.max(0, Math.min(100, volumePercent));
        this.muted = muted;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public boolean isPlaybackControlAvailable() {
        return playbackControlAvailable;
    }

    public BluetoothPlaybackState getPlaybackState() {
        return playbackState;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public boolean isMuted() {
        return muted;
    }
}
