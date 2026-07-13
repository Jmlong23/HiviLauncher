package com.ljm.audiotoollib.upnpserver.entity;

public class PlayStatusBean {

    /**
     * type : 0
     * ch : 0
     * mode : 10
     * loop : 0
     * eq : 0
     * status : pause,pause,load,stop
     * curpos : 69892
     * offset_pts : 69892
     * totlen : 188685
     * Title : E69585E4BA8BE79A84E5BC80E5A4B4E680BBE698AFE69E81E585B7E6B8A9E69F94
     * Artist : E58898E998B3E998B3
     * Album :
     * alarmflag : 0
     * plicount : 22
     * plicurr : 14
     * vol : 13
     * mute : 0
     */

    private String type;
    private String ch;
    private String mode;
    private String loop;
    private String eq;
    private String status;
    private String curpos;
    private String offset_pts;
    private String totlen;
    private String Title;
    private String Artist;
    private String Album;
    private String alarmflag;
    private String plicount;
    private String plicurr;
    private String vol = "0";
    private String mute;

    public PlayStatusBean() {
        type = "0";
        ch = "0";
        mode = "10";
        loop = "0";
        eq = "0";
        status = "pause";
        curpos = "0";
        offset_pts = "0";
        totlen = "100";
        Title = "55555";
        Artist = "5555";
        Album = "";
        alarmflag = "0";
        plicount = "10";
        plicurr = "10";
        vol = "13";
        mute = "0";
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCh() {
        return ch;
    }

    public void setCh(String ch) {
        this.ch = ch;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getLoop() {
        return loop;
    }

    public void setLoop(String loop) {
        this.loop = loop;
    }

    public String getEq() {
        return eq;
    }

    public void setEq(String eq) {
        this.eq = eq;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurpos() {
        return curpos;
    }

    public void setCurpos(String curpos) {
        this.curpos = curpos;
    }

    public String getOffset_pts() {
        return offset_pts;
    }

    public void setOffset_pts(String offset_pts) {
        this.offset_pts = offset_pts;
    }

    public String getTotlen() {
        return totlen;
    }

    public void setTotlen(String totlen) {
        this.totlen = totlen;
    }

    public String getTitle() {
        return Title;
    }

    public void setTitle(String Title) {
        this.Title = Title;
    }

    public String getArtist() {
        return Artist;
    }

    public void setArtist(String Artist) {
        this.Artist = Artist;
    }

    public String getAlbum() {
        return Album;
    }

    public void setAlbum(String Album) {
        this.Album = Album;
    }

    public String getAlarmflag() {
        return alarmflag;
    }

    public void setAlarmflag(String alarmflag) {
        this.alarmflag = alarmflag;
    }

    public String getPlicount() {
        return plicount;
    }

    public void setPlicount(String plicount) {
        this.plicount = plicount;
    }

    public String getPlicurr() {
        return plicurr;
    }

    public void setPlicurr(String plicurr) {
        this.plicurr = plicurr;
    }

    public String getVol() {
        return vol;
    }

    public void setVol(String vol) {
        this.vol = vol;
    }

    public String getMute() {
        return mute;
    }

    public void setMute(String mute) {
        this.mute = mute;
    }
}
