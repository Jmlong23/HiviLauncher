package com.ljm.audiotoollib.upnpserver.entity;



public class MusicDataBean implements MultiItemEntity {
    boolean isWyyPrivateRomaing = false;
    boolean isDefault = false;
    boolean isFamily = false;
    boolean isSelected = false;
    boolean isCollected = false;
    boolean isVip = false;
    boolean isPodcast = false;
    String alg = "";//alg字段除了每日推荐、私人漫游、场景电台等场景分发外，需要增加上游数据来源，回传歌单id、艺人id、专辑id等
    String createTime = "";
    String coverUrl = "";
    String name = "";
    String playUrl = "";
    String artist = "";
    String dataType = "music";
    String id = "";
    String musicType = "";
    String fmTypeCode = "";
    String fmDescription = "";
    String UploadType = "0";//区分最近播放上传的类型 （0:歌曲 1:歌手 2:专辑 3:歌单 4:电台）
    String upload_id = "";
    String upload_pic = "";
    String upload_name = "";
    String upload_song_num = "";
    String upload_create_time = "";
    String upload_create_name = "";
    String city;
    String upload_terrace_type = "1";
    String unplayable_msg = "找不到音乐资源";
    String authorization = "";
    String bitrate = "";
    String snCode = "";
    String mvId = "";
    String matchLyric = "";
    long urlPlayTime = 0;
    private int itemType = 0;

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public boolean isPodcast() {
        return isPodcast;
    }

    public void setPodcast(boolean podcast) {
        isPodcast = podcast;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }

    public String getBitrate() {
        return bitrate;
    }

    public void setBitrate(String bitrate) {
        this.bitrate = bitrate;
    }

    public String getSnCode() {
        return snCode;
    }

    public void setSnCode(String snCode) {
        this.snCode = snCode;
    }

    public String getMvId() {
        return mvId;
    }

    public void setMvId(String mvId) {
        this.mvId = mvId;
    }

    public String getMatchLyric() {
        return matchLyric;
    }

    public void setMatchLyric(String matchLyric) {
        this.matchLyric = matchLyric;
    }

    public boolean isWyyPrivateRomaing() {
        return isWyyPrivateRomaing;
    }

    public void setWyyPrivateRomaing(boolean wyyPrivateRomaing) {
        isWyyPrivateRomaing = wyyPrivateRomaing;
    }

    public long getUrlPlayTime() {
        return urlPlayTime;
    }

    public void setUrlPlayTime(long urlPlayTime) {
        this.urlPlayTime = urlPlayTime;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUnplayable_msg() {
        return unplayable_msg;
    }

    public void setUnplayable_msg(String unplayable_msg) {
        this.unplayable_msg = unplayable_msg;
    }

    public String getUnload_terrace_type() {
        return upload_terrace_type;
    }

    public void setUnload_terrace_type(String upload_terrace_type) {
        this.upload_terrace_type = upload_terrace_type;
    }

    public boolean isFamily() {
        return isFamily;
    }

    public void setFamily(boolean family) {
        isFamily = family;
    }

    public String getUpload_terrace_type() {
        return upload_terrace_type;
    }

    public void setUpload_terrace_type(String upload_terrace_type) {
        this.upload_terrace_type = upload_terrace_type;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getUploadType() {
        return UploadType;
    }

    public void setUploadType(String uploadType) {
        UploadType = uploadType;
    }

    public String getUpload_id() {
        return upload_id;
    }

    public void setUpload_id(String upload_id) {
        this.upload_id = upload_id;
    }

    public String getUpload_pic() {
        return upload_pic;
    }

    public void setUpload_pic(String upload_pic) {
        this.upload_pic = upload_pic;
    }

    public String getUpload_name() {
        return upload_name;
    }

    public void setUpload_name(String upload_name) {
        this.upload_name = upload_name;
    }

    public String getUpload_song_num() {
        return upload_song_num;
    }

    public void setUpload_song_num(String upload_song_num) {
        this.upload_song_num = upload_song_num;
    }

    public String getUpload_create_time() {
        return upload_create_time;
    }

    public void setUpload_create_time(String upload_create_time) {
        this.upload_create_time = upload_create_time;
    }

    public String getUpload_create_name() {
        return upload_create_name;
    }

    public void setUpload_create_name(String upload_create_name) {
        this.upload_create_name = upload_create_name;
    }

    public boolean isVip() {
        return isVip;
    }

    public void setVip(boolean vip) {
        isVip = vip;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public boolean isCollected() {
        return isCollected;
    }

    public void setCollected(boolean collected) {
        isCollected = collected;
    }

    public String getFmTypeCode() {
        return fmTypeCode;
    }

    public void setFmTypeCode(String fmTypeCode) {
        this.fmTypeCode = fmTypeCode;
    }

    public String getFmDescription() {
        return fmDescription;
    }

    public void setFmDescription(String fmDescription) {
        this.fmDescription = fmDescription;
    }

    public String getMusicType() {
        return musicType;
    }

    public void setMusicType(String musicType) {
        this.musicType = musicType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlayUrl() {
        return playUrl;
    }

    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    @Override
    public int getItemType() {
        return itemType;
    }

    public void setItemType(int itemType) {
        this.itemType = itemType;
    }
}