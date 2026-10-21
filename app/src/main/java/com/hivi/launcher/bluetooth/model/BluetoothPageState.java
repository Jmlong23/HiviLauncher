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
    private final String trackTitle;
    private final String trackLyric;

    public BluetoothPageState(boolean connected, String deviceName,
            boolean playbackControlAvailable, boolean playing,
            int volumePercent, boolean muted,
            String trackTitle, String trackLyric) {
        this.connected = connected;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.playbackControlAvailable = playbackControlAvailable;
        this.playing = playing;
        this.volumePercent = Math.max(0, Math.min(100, volumePercent));
        this.muted = muted;
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

    public int getVolumePercent() {
        return volumePercent;
    }

    public boolean isMuted() {
        return muted;
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
