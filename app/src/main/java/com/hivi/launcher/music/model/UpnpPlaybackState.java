package com.hivi.launcher.music.model;

import android.text.TextUtils;

public class UpnpPlaybackState {
    private final CharSequence title;
    private final CharSequence artist;
    private final String lyric;
    private final String coverUrl;
    private final long positionMs;
    private final long durationMs;
    private final boolean playing;
    private final boolean preparing;

    public UpnpPlaybackState(CharSequence title, CharSequence artist, String lyric, String coverUrl,
            long positionMs, long durationMs, boolean playing, boolean preparing) {
        this.title = TextUtils.isEmpty(title) ? "Sleep Music" : title;
        this.artist = TextUtils.isEmpty(artist) ? "WiiM" : artist;
        this.lyric = lyric == null ? "" : lyric;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.positionMs = Math.max(0L, positionMs);
        this.durationMs = Math.max(0L, durationMs);
        this.playing = playing;
        this.preparing = preparing;
    }

    public static UpnpPlaybackState empty() {
        return new UpnpPlaybackState("Sleep Music", "WiiM", "Can you give me that Can", "",
                95_000L, 295_000L, false, false);
    }

    public CharSequence getTitle() {
        return title;
    }

    public CharSequence getArtist() {
        return artist;
    }

    public String getLyric() {
        return lyric;
    }

    public String getCoverUrl() {
        return coverUrl;
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

    public boolean isPreparing() {
        return preparing;
    }

    public boolean hasRealSong() {
        return !TextUtils.isEmpty(title) && !"Sleep Music".contentEquals(title);
    }
}
