package com.hivi.launcher.bluetooth.model;

/**
 * Immutable snapshot rendered by the Bluetooth input page.
 */
public final class BluetoothPageState {
    private final boolean connected;
    private final String deviceName;
    private final boolean playbackControlAvailable;
    private final boolean playing;
    private final int volumePercent;
    private final boolean muted;

    public BluetoothPageState(boolean connected, String deviceName,
            boolean playbackControlAvailable, boolean playing,
            int volumePercent, boolean muted) {
        this.connected = connected;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.playbackControlAvailable = playbackControlAvailable;
        this.playing = playing;
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

    public boolean isPlaying() {
        return playing;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public boolean isMuted() {
        return muted;
    }
}
