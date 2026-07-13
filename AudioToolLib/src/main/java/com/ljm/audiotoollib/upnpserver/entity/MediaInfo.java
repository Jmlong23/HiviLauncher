package com.ljm.audiotoollib.upnpserver.entity;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import android.text.TextUtils;

import java.io.Serializable;

public class MediaInfo implements Serializable, Cloneable {
    private String creator = "";
    private String artist = "";
    private String album = "";
    private String title = "";
    private String song_id = "";
    private String album_id = "";
    private String artist_id = "";
    private String singer_id = "";
    private String trackSource = "";
    private String mediaType = "";
    private String playUri = "";
    private String albumArtURI = "";
    private int bitrate = 0;
    private String subid = "";
    private String subTitle = "";
    private String typeDescription = "";
    private int quality = 0;
    private String trackMetaData = "";
    private long duration = 1L;
    private long totalTime = 0L;
    private long tickTime = 0L;
    private int skiplimit = 0;
    private int controlHex;

    public MediaInfo() {
    }

    public void parseMetaData(String metadata) {
        String var2;
        if (!(var2 = this.trackMetaData).equals(metadata) || TextUtils.isEmpty(var2) || TextUtils.isEmpty(metadata)) {
            MediaInfo var10000 = this;
            this.trackMetaData = metadata;
            MediaInfo metadata1 = null;

            label38: {
                label37: {
                    Exception var7;
                    label36: {
                        boolean var8;
                        boolean var10001;
                        try {
                            var8 = var10000.getMediaType().equals("QPLAY");
                        } catch (Exception var5) {
                            var7 = var5;
                            var10001 = false;
                            break label36;
                        }

                        if (var8) {
                            try {
                                var10000 = MediaInfoParser.convertQplayMeta2AlbumInfo(this.trackMetaData);
                                break label37;
                            } catch (Exception var3) {
                                var7 = var3;
                                var10001 = false;
                            }
                        } else {
                            try {
                                var10000 = MediaInfoParser.convert2MediaInfo(this.trackMetaData);
                                break label37;
                            } catch (Exception var4) {
                                var7 = var4;
                                var10001 = false;
                            }
                        }
                    }

                    var7.printStackTrace();
                    break label38;
                }

                metadata1 = var10000;
            }

            if (metadata1 != null) {
                String var9 = this.title + this.artist;
                MediaInfo var10002 = metadata1;
                MediaInfo var10003 = metadata1;
                MediaInfo var10005 = metadata1;
                MediaInfo var10007 = metadata1;
                MediaInfo var10009 = metadata1;
                metadata = metadata1.title + metadata1.artist;
                var10009.setTotalTime(this.totalTime);
                var10007.setTickTime(this.tickTime);
                var10005.setTrackSource(this.getTrackSource());
                var10003.setMediaType(this.getMediaType());
                this.copyMediaInfo(var10002);
                if (!var9.equals(metadata)) {
                    this.tickTime = 0L;
                    this.totalTime = 0L;
                }
            }
        }

    }

    private void copyMediaInfo(MediaInfo lpMediaInfo) {
        this.creator = lpMediaInfo.creator;
        this.artist = lpMediaInfo.artist;
        this.album = lpMediaInfo.album;
        this.title = lpMediaInfo.title;
        this.song_id = lpMediaInfo.song_id;
        this.album_id = lpMediaInfo.album_id;
        this.artist_id = lpMediaInfo.artist_id;
        this.singer_id = lpMediaInfo.singer_id;
        this.trackSource = lpMediaInfo.trackSource;
        this.mediaType = lpMediaInfo.mediaType;
        this.playUri = lpMediaInfo.playUri;
        this.albumArtURI = lpMediaInfo.albumArtURI;
        this.bitrate = lpMediaInfo.bitrate;
        this.subid = lpMediaInfo.subid;
        this.subTitle = lpMediaInfo.subTitle;
        this.typeDescription = lpMediaInfo.typeDescription;
        this.quality = lpMediaInfo.quality;
        this.trackMetaData = lpMediaInfo.trackMetaData;
        this.totalTime = lpMediaInfo.totalTime;
        this.duration = lpMediaInfo.duration;
        this.tickTime = lpMediaInfo.tickTime;
        this.skiplimit = lpMediaInfo.skiplimit;
        this.controlHex = lpMediaInfo.controlHex;
    }

    public long getDuration() {
        return this.duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getSinger_id() {
        return this.singer_id;
    }

    public void setSinger_id(String singer_id) {
        this.singer_id = singer_id;
    }

    public String getCreator() {
        return this.creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getArtist() {
        return this.artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return this.album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSong_id() {
        return this.song_id;
    }

    public void setSong_id(String song_id) {
        this.song_id = song_id;
    }

    public String getAlbum_id() {
        return this.album_id;
    }

    public void setAlbum_id(String album_id) {
        this.album_id = album_id;
    }

    public String getArtist_id() {
        return this.artist_id;
    }

    public void setArtist_id(String artist_id) {
        this.artist_id = artist_id;
    }

    public String getTrackSource() {
        return this.trackSource;
    }

    public void setTrackSource(String trackSource) {
        this.trackSource = trackSource;
    }

    public String getMediaType() {
        return this.mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getPlayUri() {
        return this.playUri;
    }

    public void setPlayUri(String playUri) {
        this.playUri = playUri;
    }

    public String getAlbumArtURI() {
        return this.albumArtURI;
    }

    public void setAlbumArtURI(String albumArtURI) {
        this.albumArtURI = albumArtURI;
    }

    public int getBitrate() {
        return this.bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public String getSubid() {
        return this.subid;
    }

    public void setSubid(String subid) {
        this.subid = subid;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public String getSubTitle() {
        return this.subTitle;
    }

    public String getTypeDescription() {
        return this.typeDescription;
    }

    public void setTypeDescription(String typeDescription) {
        this.typeDescription = typeDescription;
    }

    public int getQuality() {
        return this.quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public int getSkiplimit() {
        return this.skiplimit;
    }

    public void setSkiplimit(int skiplimit) {
        this.skiplimit = skiplimit;
    }

    public String getTrackMetaData() {
        return this.trackMetaData;
    }

    public void setTrackMetaData(String trackMetaData) {
        this.parseMetaData(trackMetaData);
    }

    public long getTotalTime() {
        return this.totalTime;
    }

    public void setTotalTime(long totalTime) {
        this.totalTime = totalTime;
    }

    public long getTickTime() {
        return this.tickTime;
    }

    public void setTickTime(long tickTime) {
        this.tickTime = tickTime;
    }

    public int getControlHex() {
        return this.controlHex;
    }

    public void setControlHex(int controlHex) {
        this.controlHex = controlHex;
    }

    public MediaInfo clone() {
        MediaInfo var10000 = this;
        MediaInfo var2 = null;

        var10000 = (MediaInfo)var10000.clone();

        var2 = var10000;
        return var2;
    }
}
