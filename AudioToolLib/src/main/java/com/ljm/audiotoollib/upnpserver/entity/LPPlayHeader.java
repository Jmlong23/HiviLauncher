package com.ljm.audiotoollib.upnpserver.entity;

import android.text.TextUtils;

public class LPPlayHeader {
    private String headTitle;
    private String headId;
    private String imageUrl;
    private String creator;
    private String mediaType;
    private String mediaSource;
    private String totalTracks;
    private String describe;
    private String searchUrl;
    private int totalPage;
    private int currentPage;
    private int loopMode = 3;
    private int perPage;
    private String quality;
    private int headType;

    public LPPlayHeader() {
    }

    public void setHeadTitle(String var1) {
        this.headTitle = var1;
    }

    public void setHeadId(String var1) {
        this.headId = var1;
    }

    public void setImageUrl(String var1) {
        this.imageUrl = var1;
    }

    public void setCreator(String var1) {
        this.creator = var1;
    }

    public void setMediaType(String var1) {
        this.mediaType = var1;
    }

    public void setMediaSource(String var1) {
        this.mediaSource = var1;
    }

    public void setTotalTracks(String var1) {
        this.totalTracks = var1;
    }

    public void setDescribe(String var1) {
        this.describe = var1;
    }

    public void setSearchUrl(String var1) {
        this.searchUrl = var1;
    }

    public void setTotalPage(int var1) {
        this.totalPage = var1;
    }

    public void setCurrentPage(int var1) {
        this.currentPage = var1;
    }

    public void setLoopMode(int var1) {
        this.loopMode = var1;
    }

    public void setPerPage(int var1) {
        this.perPage = var1;
    }

    public void setQuality(String var1) {
        this.quality = var1;
    }

    public void setHeadType(int var1) {
        this.headType = var1;
    }

    public String getHeadTitle() {
        return this.headTitle;
    }

    public String getHeadId() {
        return this.headId;
    }

    public String getImageUrl() {
        return this.imageUrl;
    }

    public String getCreator() {
        return this.creator;
    }

    public String getMediaType() {
        return this.mediaType;
    }

    public String getMediaSource() {
        return this.mediaSource;
    }

    public String getTotalTracks() {
        return this.totalTracks;
    }

    public String getDescribe() {
        return this.describe;
    }

    public String getSearchUrl() {
        return this.searchUrl;
    }

    public int getTotalPage() {
        return this.totalPage;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public int getLoopMode() {
        return this.loopMode;
    }

    public int getPerPage() {
        return this.perPage;
    }

    public String getQuality() {
        return this.quality;
    }

    public int getHeadType() {
        return this.headType;
    }

    public static class LPPlayMediaType {
        private static String[] values = new String[]{"NONE", "RADIO-NETWORK", "STATION-NETWORK", "SONGLIST-LOCAL", "THIRD-DLNA", "AIRPLAY", "QPLAY", "UNKNOWN", "LINE-IN", "SECORD_LINEIN", "FM", "BLUETOOTH", "EXTERNAL_USB", "EXTERNAL_TFCARD", "OPTICAL", "UDISK", "SPOTIFY", "ALEXA", "RCA", "CO_AXIAL", "ALEXA_PANDORA", "SONGLIST-LOCAL_TF", "ALEXA_AUDIBLE", "ALI-RPC"};
        public static final String LP_NONE = "NONE";
        public static final String LP_RADIO_NETWORK = "RADIO-NETWORK";
        public static final String LP_STATION_NETWORK = "STATION-NETWORK";
        public static final String LP_SONGLIST_NETWORK = "SONGLIST-NETWORK";
        public static final String LP_SONGLIST_LOCAL = "SONGLIST-LOCAL";
        public static final String LP_THIRD_DLNA = "THIRD-DLNA";
        public static final String LP_AIRPLAY = "AIRPLAY";
        public static final String LP_QPLAY = "QPLAY";
        public static final String LP_UNKNOWN = "UNKNOWN";
        public static final String LP_LINE_IN = "LINE-IN";
        public static final String LP_LINE_2IN = "SECORD_LINEIN";
        public static final String LP_FM = "FM";
        public static final String LP_BLUETOOTH = "BLUETOOTH";
        public static final String LP_EXTERNAL_USB = "EXTERNAL_USB";
        public static final String LP_EXTERNAL_TFCARD = "EXTERNAL_TFCARD";
        public static final String LP_OPTICAL = "OPTICAL";
        public static final String LP_UDISK = "UDISK";
        public static final String LP_SPOTIFY = "SPOTIFY";
        public static final String LP_ALEXA = "ALEXA";
        public static final String LP_RCA = "RCA";
        public static final String LP_CO_AXIAL = "CO_AXIAL";
        public static final String LP_ALEXA_PANDORA = "ALEXA_PANDORA";
        public static final String LP_SONGLIST_LOCAL_TF = "SONGLIST-LOCAL_TF";
        public static final String LP_ALEXA_AUDIBLE = "ALEXA_AUDIBLE";
        public static final String LP_ALI_RPC = "ALI-RPC";
        public static final String SPOTIFY_SHOW = "spotify:show";
        public static final String SPOTIFY_EPISODE = "spotify:episode";

        public LPPlayMediaType() {
        }

        public static String byOrdinal(String var0) {
            if (TextUtils.isEmpty(var0)) {
                return "NONE";
            } else {
                String[] var1;
                int var2 = (var1 = values).length;

                for(int var3 = 0; var3 < var2; ++var3) {
                    String var4;
                    if ((var4 = var1[var3]).equals(var0)) {
                        return var4;
                    }
                }

                return "NONE";
            }
        }
    }

    public static class LPLoopModeType {
        public static final int LP_LOOPMODE_PLAYITEM_PLAYLIST = 0;
        public static final int LP_LOOPMODE_PLAYITEM_TRACKURL = 1;
        public static final int LP_LOOPMODE_PLAYHEADER_SEARCHURL = 2;
        public static final int LP_LOOPMODE_NOBACK = 3;

        public LPLoopModeType() {
        }
    }

    public static class LPHeaderType {
        public static final int LP_HEADER_TYPE_COLLECTION = 0;
        public static final int LP_HEADER_TYPE_PLAYLIST = 1;
        public static final int LP_HEADER_TYPE_ALBUM = 2;
        public static final int LP_HEADER_TYPE_ARTIST = 3;
        public static final int LP_HEADER_TYPE_STATION = 4;
        public static final int LP_HEADER_TYPE_SONG = 5;
        public static final int LP_HEADER_TYPE_PODCAST = 6;
        public static final int LP_HEADER_TYPE_SHOW = 7;
        public static final int LP_HEADER_TYPE_EPISODE = 8;

        public LPHeaderType() {
        }
    }
}
