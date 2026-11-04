package com.hivi.launcher.bluetooth.model;

/**
 * Immutable snapshot rendered by the Bluetooth input page.
 */
public final class BluetoothPageState {
    private final boolean connected;
    private final String deviceName;
    private final boolean playbackControlAvailable;
    private final boolean playing;
    private final String trackTitle;
    private final String trackLyric;

    public BluetoothPageState(boolean connected, String deviceName,
            boolean playbackControlAvailable, boolean playing,
            String trackTitle, String trackLyric) {
        this.connected = connected;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.playbackControlAvailable = playbackControlAvailable;
        this.playing = playing;
        this.trackTitle = trackTitle == null ? "" : trackTitle;
        this.trackLyric = trackLyric == null ? "" : trackLyric;
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

    /** Song title parsed from the MCU now-playing reports, or empty when unknown. */
    public String getTrackTitle() {
        return trackTitle;
    }

    /** Latest lyrics line pushed by the MCU, or empty when none. */
    public String getTrackLyric() {
        return trackLyric;
    }
}
