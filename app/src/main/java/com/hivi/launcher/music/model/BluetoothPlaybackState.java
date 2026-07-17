package com.hivi.launcher.music.model;

import android.text.TextUtils;

/**
 * A snapshot of media exposed by the system Bluetooth AVRCP controller.
 */
public final class BluetoothPlaybackState {
    private final CharSequence title;
    private final CharSequence artist;
    private final CharSequence album;
    private final CharSequence lyric;
    private final long positionMs;
    private final long durationMs;
    private final boolean playing;

    public BluetoothPlaybackState(CharSequence title, CharSequence artist, CharSequence album,
            long positionMs, long durationMs, boolean playing) {
        this(title, artist, album, "", positionMs, durationMs, playing);
    }

    public BluetoothPlaybackState(CharSequence title, CharSequence artist, CharSequence album,
            CharSequence lyric, long positionMs, long durationMs, boolean playing) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.lyric = lyric == null ? "" : lyric;
        this.positionMs = Math.max(0L, positionMs);
        this.durationMs = Math.max(0L, durationMs);
        this.playing = playing;
    }

    public static BluetoothPlaybackState empty() {
        return new BluetoothPlaybackState("", "", "", 0L, 0L, false);
    }

    public CharSequence getTitle() {
        return title;
    }

    public CharSequence getArtist() {
        return artist;
    }

    public CharSequence getAlbum() {
        return album;
    }

    public CharSequence getLyric() {
        return lyric;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean hasMetadata() {
        return !TextUtils.isEmpty(title) || !TextUtils.isEmpty(artist) || !TextUtils.isEmpty(album);
    }
}
