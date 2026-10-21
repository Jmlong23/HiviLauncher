package com.ljm.audiotoollib.upnpserver.entity;

import java.util.ArrayList;
import java.util.List;


public class PlayMusicListType {

    private volatile List<TrackINFO_Type> PQMusicList;
    private volatile int CurPlayIndex;
    volatile String CurPlayListName;
    String CurPlayTitle;
    String CurPlayArtist;
    String CurPlayAlbum;
    LPPlayHeader header;

    play_mode mode;
    UPNP_AUDIO_SRC PlaySrc;

    public PlayMusicListType() {
        header = new LPPlayHeader();
        header.setHeadTitle("My Music");
        header.setMediaType(LPPlayHeader.LPPlayMediaType.LP_SONGLIST_LOCAL);

    }

    public List<TrackINFO_Type> getPQMusicList() {
        if(PQMusicList == null){
            PQMusicList = new ArrayList<TrackINFO_Type>();
        }
        return PQMusicList;
    }

    public void clearPQMusicList() {
        PQMusicList = new ArrayList<TrackINFO_Type>();
        CurPlayIndex = 0;
    }

    /**
     * Replaces the queue in one assignment so readers never observe the transient empty state
     * produced by clear() followed by addAll().
     */
    public void replacePQMusicList(List<TrackINFO_Type> value) {
        PQMusicList = value == null ? new ArrayList<TrackINFO_Type>() : value;
    }

    public void removeListByIndex(int index) {
        PQMusicList.remove(index);
    }



    public void setCurPlayIndex(int value) {
        CurPlayIndex = value;
    }

    public int getCurPlayIndex() {
        return CurPlayIndex;
    }

    public void setHeader(LPPlayHeader value) {
        header = value;
    }

    public LPPlayHeader getHeader() {
        return header;
    }

    public void setCurPlayListName(String value) {
        CurPlayListName = value;
    }

    public String getCurPlayListName() {
        return CurPlayListName;
    }

    public void setCurCurPlayTitle(String value) {
        CurPlayTitle = value;
    }

    public String getCurPlayTitle() {
        return CurPlayTitle;
    }


    public void setCurPlayArtist(String value) {
        CurPlayArtist = value;
    }

    public String getCurPlayArtist() {
        return CurPlayArtist;
    }

    public void setCurPlayAlbum(String value) {
        CurPlayAlbum = value;
    }

    public String getCurPlayAlbum() {
        return CurPlayAlbum;
    }


    public enum MusicPlayIndex{
        PREV_SONG,
        CURRENT_SONG,
        NEXT_SONG,
    }

    public enum play_mode {
        play_mode_loop,

        play_mode_single,

        play_mode_random,

        play_mode_order,
    }

    public enum UPNP_AUDIO_SRC {
        UPNP_SRC_NULL,
        UPNP_SRC_AIRPLAY,
        UPNP_SRC_DLAN,
        UPNP_SRC_QUEUE,
        UPNP_SRC_QPLAY,
    }
}
