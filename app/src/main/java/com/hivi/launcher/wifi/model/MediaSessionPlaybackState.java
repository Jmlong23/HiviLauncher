package com.hivi.launcher.wifi.model;

import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Snapshot of the playback published through the media session of one of the known music apps.
 */
public final class MediaSessionPlaybackState {
    private final WifiMusicApp app;
    private final String title;
    private final String artist;
    private final String album;
    private final Bitmap artwork;
    private final boolean playing;
    private final boolean activeSession;

    private MediaSessionPlaybackState(WifiMusicApp app, String title, String artist, String album,
            Bitmap artwork, boolean playing, boolean activeSession) {
        this.app = app;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.artwork = artwork;
        this.playing = playing;
        this.activeSession = activeSession;
    }

    public static MediaSessionPlaybackState empty() {
        return new MediaSessionPlaybackState(null, "", "", "", null, false, false);
    }

    public static MediaSessionPlaybackState of(WifiMusicApp app, String title, String artist,
            String album, Bitmap artwork, boolean playing) {
        return new MediaSessionPlaybackState(app, title, artist, album, artwork, playing, true);
    }

    @Nullable
    public WifiMusicApp getApp() {
        return app;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    @Nullable
    public Bitmap getArtwork() {
        return artwork;
    }

    public boolean isPlaying() {
        return playing;
    }

    /** True when one of the known music apps publishes a usable session with track metadata. */
    public boolean hasSession() {
        return activeSession && app != null && !TextUtils.isEmpty(title);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MediaSessionPlaybackState)) {
            return false;
        }
        MediaSessionPlaybackState other = (MediaSessionPlaybackState) obj;
        return app == other.app
                && title.equals(other.title)
                && artist.equals(other.artist)
                && album.equals(other.album)
                && artwork == other.artwork
                && playing == other.playing
                && activeSession == other.activeSession;
    }

    @Override
    public int hashCode() {
        int result = app != null ? app.hashCode() : 0;
        result = 31 * result + title.hashCode();
        result = 31 * result + artist.hashCode();
        result = 31 * result + album.hashCode();
        result = 31 * result + (artwork != null ? artwork.hashCode() : 0);
        result = 31 * result + (playing ? 1 : 0);
        result = 31 * result + (activeSession ? 1 : 0);
        return result;
    }
}
