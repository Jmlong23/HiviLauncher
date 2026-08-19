package com.hivi.launcher.ai.iot;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * AI 点播音乐指令（自 HiviAudio AiMusicInfoParser 迁移的字段子集）。
 *
 * <p>服务端对"播放某首歌"的应答不是 iotCode，而是 tts sentence_start 里下发的
 * 音乐结果 JSON：{"code":200,"message":..,"success":..,"data":{"song_name":..,
 * "singer_name":..,...}}。与 HiviAudio 一致，音乐 JSON 的识别优先于 IoT 指令。
 * 本机不播 URL，歌名交给 QQ 音乐搜索播放。</p>
 */
public final class AiMusicRequest {
    public final String songName;
    public final String singerName;
    /** 服务端明确返回失败（如未授权音乐平台）时的提示语。 */
    public final boolean success;
    public final String message;

    private AiMusicRequest(String songName, String singerName, boolean success, String message) {
        this.songName = songName;
        this.singerName = singerName;
        this.success = success;
        this.message = message;
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
        if (songName == null && singerName == null
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
                root.optString("message"));
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
