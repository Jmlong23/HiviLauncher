package com.hivi.audionativelib.manager.himusic;

public class MediaInfo {
    // DataUnit 类型在 Java 中表示为 String
    public String assetId;                      // 媒体ID
    public String title;                        // 标题
    public String artist;                       // 艺术家
    public String author;                       // 专辑作者
    public String avQueueName;                  // 歌单(歌曲列表)名称
    public String avQueueId;                    // 歌单唯一标识id
    public String avQueueImage;                 // 歌单封面图是否存在
    public String avQueueImageUri;              // 歌单封面图uri
    public String album;                        // 专辑名称
    public String writer;                       // 词作者
    public String composer;                     // 曲作者
    public int duration;                        // 媒体时长
    public byte[] mediaImage;                   // 媒体图片是否存在
    public String imageType;                    // 媒体图片类型
    public String mediaImageUri;                // 媒体图片uri
    public String publishDate;                  // 发行日期
    public String subTitle;                     // 子标题
    public String description;                  // 媒体描述
    public String lyric;                        // 媒体歌词内容
    public String previousAssetId;              // 上一首媒体ID
    public String nextAssetId;                  // 下一首媒体ID
    public int skipIntervals;                   // 快进快退支持的时间间隔
    public int filter;                          // 当前session支持的协议
    public int mediaLength;                     // 媒体长度
    public int avQueueLength;                   // 歌单长度
    public int displayTags;                     // 媒体资源金标类型
    public String drmSchemes;                   // 当前session支持的DRM方案
    public String bundleIcon;                   // 应用图标是否存在
    public String bundleIconUri;                // 应用图标uri
    public String singleLyricText;              // 单条媒体歌词内容

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAvQueueName() {
        return avQueueName;
    }

    public void setAvQueueName(String avQueueName) {
        this.avQueueName = avQueueName;
    }

    public String getAvQueueId() {
        return avQueueId;
    }

    public void setAvQueueId(String avQueueId) {
        this.avQueueId = avQueueId;
    }

    public String getAvQueueImage() {
        return avQueueImage;
    }

    public void setAvQueueImage(String avQueueImage) {
        this.avQueueImage = avQueueImage;
    }

    public String getAvQueueImageUri() {
        return avQueueImageUri;
    }

    public void setAvQueueImageUri(String avQueueImageUri) {
        this.avQueueImageUri = avQueueImageUri;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getWriter() {
        return writer;
    }

    public void setWriter(String writer) {
        this.writer = writer;
    }

    public String getComposer() {
        return composer;
    }

    public void setComposer(String composer) {
        this.composer = composer;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public byte[] getMediaImage() {
        return mediaImage;
    }

    public void setMediaImage(byte[] mediaImage) {
        this.mediaImage = mediaImage;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public String getMediaImageUri() {
        return mediaImageUri;
    }

    public void setMediaImageUri(String mediaImageUri) {
        this.mediaImageUri = mediaImageUri;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public String getPreviousAssetId() {
        return previousAssetId;
    }

    public void setPreviousAssetId(String previousAssetId) {
        this.previousAssetId = previousAssetId;
    }

    public String getNextAssetId() {
        return nextAssetId;
    }

    public void setNextAssetId(String nextAssetId) {
        this.nextAssetId = nextAssetId;
    }

    public int getSkipIntervals() {
        return skipIntervals;
    }

    public void setSkipIntervals(int skipIntervals) {
        this.skipIntervals = skipIntervals;
    }

    public int getFilter() {
        return filter;
    }

    public void setFilter(int filter) {
        this.filter = filter;
    }

    public int getMediaLength() {
        return mediaLength;
    }

    public void setMediaLength(int mediaLength) {
        this.mediaLength = mediaLength;
    }

    public int getAvQueueLength() {
        return avQueueLength;
    }

    public void setAvQueueLength(int avQueueLength) {
        this.avQueueLength = avQueueLength;
    }

    public int getDisplayTags() {
        return displayTags;
    }

    public void setDisplayTags(int displayTags) {
        this.displayTags = displayTags;
    }

    public String getDrmSchemes() {
        return drmSchemes;
    }

    public void setDrmSchemes(String drmSchemes) {
        this.drmSchemes = drmSchemes;
    }

    public String getBundleIcon() {
        return bundleIcon;
    }

    public void setBundleIcon(String bundleIcon) {
        this.bundleIcon = bundleIcon;
    }

    public String getBundleIconUri() {
        return bundleIconUri;
    }

    public void setBundleIconUri(String bundleIconUri) {
        this.bundleIconUri = bundleIconUri;
    }

    public String getSingleLyricText() {
        return singleLyricText;
    }

    public void setSingleLyricText(String singleLyricText) {
        this.singleLyricText = singleLyricText;
    }
}
