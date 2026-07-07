package com.hivi.launcher.main.model;

public class MusicInfo {
    private final CharSequence title;
    private final CharSequence artist;

    public MusicInfo(CharSequence title, CharSequence artist) {
        this.title = title;
        this.artist = artist;
    }

    public CharSequence getTitle() {
        return title;
    }

    public CharSequence getArtist() {
        return artist;
    }
}
