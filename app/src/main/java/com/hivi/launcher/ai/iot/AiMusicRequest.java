package com.hivi.launcher.ai.iot;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * AI 点播音乐指令（自 HiviAudio AiMusicInfoParser 迁移的字段子集）。
 *
 * <p>服务端对"播放某首歌"的应答不是 iotCode，而是 tts sentence_start 里下发的
 * 音乐结果 JSON：{"code":200,"message":..,"success":..,"data":{...}}。与 HiviAudio 一致，
 * 音乐 JSON 的识别优先于 IoT 指令。有可播放链接时走本机渲染器队列直接播放
 * （同 HiviAudio AI 播歌），无链接信息时回退为把歌名交给 QQ 音乐搜索播放。</p>
 */
public final class AiMusicRequest {
    public final String songName;
    public final String singerName;
    /** 服务端明确返回失败（如未授权音乐平台）时的提示语。 */
    public final boolean success;
    public final String message;

    /** 版权与试听标记：1 表示可播。 */
    public final int playable;
    public final int tryPlayable;
    /** 各音质播放链接（standard/hq/sq/普通）与试听链接。 */
    public final String songPlayUrl;
    public final String songPlayUrlStandard;
    public final String songPlayUrlHq;
    public final String songPlayUrlSq;
    public final String try30sUrl;

    /** 队列与播放页展示所需的元数据。 */
    public final String songId;
    public final String songMid;
    public final String albumName;
    public final String albumPic;
    public final String matchLyric;
    public final String mvId;
    public final String terraceType;
    public final String alg;
    public final String typeName;
    public final boolean vip;
    public final String publicTime;

    private AiMusicRequest(String songName, String singerName, boolean success, String message,
            int playable, int tryPlayable, String songPlayUrl, String songPlayUrlStandard,
            String songPlayUrlHq, String songPlayUrlSq, String try30sUrl,
            String songId, String songMid, String albumName, String albumPic, String matchLyric,
            String mvId, String terraceType, String alg, String typeName, boolean vip,
            String publicTime) {
        this.songName = songName;
        this.singerName = singerName;
        this.success = success;
        this.message = message;
        this.playable = playable;
        this.tryPlayable = tryPlayable;
        this.songPlayUrl = songPlayUrl;
        this.songPlayUrlStandard = songPlayUrlStandard;
        this.songPlayUrlHq = songPlayUrlHq;
        this.songPlayUrlSq = songPlayUrlSq;
        this.try30sUrl = try30sUrl;
        this.songId = songId;
        this.songMid = songMid;
        this.albumName = albumName;
        this.albumPic = albumPic;
        this.matchLyric = matchLyric;
        this.mvId = mvId;
        this.terraceType = terraceType;
        this.alg = alg;
        this.typeName = typeName;
        this.vip = vip;
        this.publicTime = publicTime;
    }

    /** 非音乐结果 JSON 返回 null，按普通对话/IoT 流程继续。 */
    public static AiMusicRequest parse(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        JSONObject root;
        try {
            root = new JSONObject(trimmed);
        } catch (JSONException e) {
            return null;
        }
        if (root.optInt("code", -1) != 200) {
            return null;
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return null;
        }
        String songName = firstNonEmpty(data, "song_name", "song_title");
        String singerName = firstNonEmpty(data, "singer_name", "author");
        boolean hasPlayUrlInfo = !TextUtils.isEmpty(firstNonEmpty(data, "song_play_url",
                "song_play_url_standard", "song_play_url_hq", "song_play_url_sq", "try_30s_url"))
                || data.optInt("playable", 0) == 1
                || data.optInt("try_playable", 0) == 1;
        if (songName == null && singerName == null && !hasPlayUrlInfo
                && TextUtils.isEmpty(data.optString("song_mid"))
                && TextUtils.isEmpty(data.optString("song_id"))) {
            // code=200 但没有任何歌曲字段：不是音乐结果，避免误吞其他 JSON 应答。
            return null;
        }
        boolean success = root.has("success") ? root.optBoolean("success", false) : true;
        return new AiMusicRequest(
                songName == null ? "" : songName,
                singerName == null ? "" : singerName,
                success,
                root.optString("message"),
                data.optInt("playable", 0),
                data.optInt("try_playable", 0),
                data.optString("song_play_url", ""),
                data.optString("song_play_url_standard", ""),
                data.optString("song_play_url_hq", ""),
                data.optString("song_play_url_sq", ""),
                data.optString("try_30s_url", ""),
                firstNonEmpty(data, "song_id", "k_song_id", ""),
                firstNonEmpty(data, "song_mid", "k_song_mid", ""),
                data.optString("album_name", ""),
                firstNonEmpty(data, "album_pic_300x300", "album_pic_500x500", "album_pic", ""),
                data.optString("match_lyric", ""),
                firstNonEmpty(data, "mv_id", ""),
                firstNonEmpty(data, "terraceType", "1"),
                data.optString("alg", ""),
                data.optString("typeName", ""),
                data.optInt("vip", 0) == 1,
                data.optString("public_time", ""));
    }

    /** 服务端是否携带了任何播放链接信息（用于决定走渲染器还是 QQ 音乐搜索回退）。 */
    public boolean hasPlayUrlInfo() {
        return playable == 1 || tryPlayable == 1 || !TextUtils.isEmpty(songPlayUrl)
                || !TextUtils.isEmpty(songPlayUrlStandard) || !TextUtils.isEmpty(songPlayUrlHq)
                || !TextUtils.isEmpty(songPlayUrlSq) || !TextUtils.isEmpty(try30sUrl);
    }

    /**
     * 选择播放链接，对齐 HiviAudio getPreferredAiWifiPlayableUrl：
     * 有版权时优先非 FLAC 音源（standard > 非FLAC hq > 非FLAC 普通 > hq > 普通 > sq），
     * 无版权时退回试听链接；完全不可播返回 null。
     */
    public String resolvePreferredPlayUrl() {
        if (playable == 1) {
            String url = preferredFullPlayableUrl();
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
        }
        if (tryPlayable == 1 && !TextUtils.isEmpty(try30sUrl)) {
            return try30sUrl;
        }
        String url = preferredFullPlayableUrl();
        return TextUtils.isEmpty(url) ? null : url;
    }

    private String preferredFullPlayableUrl() {
        if (!TextUtils.isEmpty(songPlayUrlStandard)) {
            return songPlayUrlStandard;
        }
        if (!TextUtils.isEmpty(songPlayUrlHq) && !isFlacUrl(songPlayUrlHq)) {
            return songPlayUrlHq;
        }
        if (!TextUtils.isEmpty(songPlayUrl) && !isFlacUrl(songPlayUrl)) {
            return songPlayUrl;
        }
        if (!TextUtils.isEmpty(songPlayUrlHq)) {
            return songPlayUrlHq;
        }
        if (!TextUtils.isEmpty(songPlayUrl)) {
            return songPlayUrl;
        }
        return songPlayUrlSq;
    }

    private static boolean isFlacUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        int queryIndex = lowerUrl.indexOf('?');
        if (queryIndex >= 0) {
            lowerUrl = lowerUrl.substring(0, queryIndex);
        }
        return lowerUrl.endsWith(".flac") || lowerUrl.contains("/f000");
    }

    /** QQ 音乐搜索关键字：歌名 + 歌手（都缺则空串，表示仅打开应用）。 */
    public String buildSearchKeyword() {
        if (!TextUtils.isEmpty(songName) && !TextUtils.isEmpty(singerName)) {
            return songName + " " + singerName;
        }
        return TextUtils.isEmpty(songName) ? singerName : songName;
    }

    private static String firstNonEmpty(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }
}
