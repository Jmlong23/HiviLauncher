package com.ljm.audiotoollib.upnpserver.entity;

import android.util.Log;
import android.util.JsonReader;
import android.util.JsonToken;
import com.google.gson.Gson;
import com.ljm.audiotoollib.upnpserver.utils.DomHelper;

import java.io.StringReader;

public class TrackINFO_Type {
    private static final String TAG = "TrackINFO_Type";
    private static final boolean ENABLE_VERBOSE_PARSE_LOG = false;
    private static final boolean ENABLE_APP_PUSH_FORMAT_LOG = false;
    private static final int LOG_CHUNK_SIZE = 3000;
    String URL;
    String MetaData;
    String Id;
    String Key;
    String Source;
    MediaInfo mediaInfo;
    MusicDataBean musicDataBean;
    Gson gson;
    boolean metadataParsed = false;


    public TrackINFO_Type() {
        gson = new Gson();
        mediaInfo = new MediaInfo();
        musicDataBean = new MusicDataBean();
    }

    public void setURL(String value) {
        URL = value;
    }

    public String getURL() {
        return URL;
    }

    public void setMetaData(String value) {
        if (value != null && value.equals(MetaData)) {
            return;
        }
        MetaData = value;
        metadataParsed = false;
        musicDataBean = new MusicDataBean();
    }

    private MusicDataBean parseMusicDataLightweight(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        MusicDataBean bean = new MusicDataBean();
        boolean hasAnyField = false;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("id".equals(name)) {
                    bean.setId(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("name".equals(name)) {
                    bean.setName(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("artist".equals(name)) {
                    bean.setArtist(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("playUrl".equals(name)) {
                    bean.setPlayUrl(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("coverUrl".equals(name)) {
                    bean.setCoverUrl(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("dataType".equals(name)) {
                    bean.setDataType(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("musicType".equals(name)) {
                    bean.setMusicType(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("authorization".equals(name)) {
                    bean.setAuthorization(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("bitrate".equals(name)) {
                    bean.setBitrate(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("snCode".equals(name)) {
                    bean.setSnCode(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("mvId".equals(name)) {
                    bean.setMvId(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("createTime".equals(name)) {
                    bean.setCreateTime(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("isVip".equals(name) || "vip".equals(name)) {
                    bean.setVip(nextBooleanCompat(reader));
                    hasAnyField = true;
                } else if ("isCollected".equals(name) || "collected".equals(name)) {
                    bean.setCollected(nextBooleanCompat(reader));
                    hasAnyField = true;
                } else if ("matchLyric".equals(name)) {
                    bean.setMatchLyric(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("upload_terrace_type".equals(name) || "terraceType".equals(name)) {
                    bean.setUpload_terrace_type(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("UploadType".equals(name)) {
                    bean.setUploadType(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("fmTypeCode".equals(name)) {
                    bean.setFmTypeCode(nextStringCompat(reader));
                    hasAnyField = true;
                } else if ("alg".equals(name)) {
                    bean.setAlg(nextStringCompat(reader));
                    hasAnyField = true;
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (Exception e) {
            return null;
        }
        return hasAnyField ? bean : null;
    }

    private static String nextStringCompat(JsonReader reader) throws Exception {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return "";
        }
        if (token == JsonToken.BOOLEAN) {
            return String.valueOf(reader.nextBoolean());
        }
        return reader.nextString();
    }

    private static boolean nextBooleanCompat(JsonReader reader) throws Exception {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return false;
        }
        if (token == JsonToken.BOOLEAN) {
            return reader.nextBoolean();
        }
        String value = reader.nextString();
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    public String getMetaData() {
        return MetaData;
    }

    public void setId(String value) {
        Id = value;
    }

    public String getId() {
        return Id;
    }

    public void setKey(String value) {
        Key = value;
    }

    public String getKey() {
        return Key;
    }

    public void setSource(String value) {
        Source = value;
    }

    public String getSource() {
        return Source;
    }

    public MediaInfo getMediaInfo() {
        ensureMetaParsed();
        return mediaInfo;
    }


    public MusicDataBean getMusicDataBean() {
        ensureMetaParsed();
        return musicDataBean;
    }

    private void ensureMetaParsed() {
        if (metadataParsed) {
            return;
        }
        metadataParsed = true;
        if (MetaData == null || MetaData.isEmpty()) {
            return;
        }
        try {
            logLongString("APP_PUSH_FORMAT MetaData", MetaData);
            mediaInfo.parseMetaData(MetaData);
            String albumArtURI = mediaInfo.getAlbumArtURI();
            if (ENABLE_APP_PUSH_FORMAT_LOG) {
                Log.i(TAG, "APP_PUSH_FORMAT parsed title=" + mediaInfo.getTitle()
                        + ", artist=" + mediaInfo.getArtist()
                        + ", albumArtUriLen=" + (albumArtURI == null ? 0 : albumArtURI.length()));
            }
            logLongString("APP_PUSH_FORMAT raw albumArtURI(trackImage)", albumArtURI);
            String decodedUrl = DomHelper.decodeAlbumArtUriToJson(albumArtURI);
            logLongString("APP_PUSH_FORMAT decoded albumArtURI(trackImage) json", decodedUrl);
            MusicDataBean parsedBean = parseMusicDataLightweight(decodedUrl);
            if (parsedBean == null && decodedUrl != null && decodedUrl.trim().startsWith("{")) {
                parsedBean = gson.fromJson(decodedUrl, MusicDataBean.class);
            }
            if (parsedBean != null) {
                musicDataBean = parsedBean;
            }
            if (ENABLE_VERBOSE_PARSE_LOG && musicDataBean != null) {
                Log.d(TAG, "parseMetaData ok, id=" + musicDataBean.getId() + ", name=" + musicDataBean.getName());
            }
        } catch (Exception e) {
            String uri = mediaInfo != null ? mediaInfo.getAlbumArtURI() : null;
            Log.e(TAG, "parseMetaData failed, albumArtUriLen=" + (uri == null ? 0 : uri.length()), e);
        }
    }

    private static void logLongString(String label, String value) {
        if (!ENABLE_APP_PUSH_FORMAT_LOG) {
            return;
        }
        if (value == null) {
            Log.i(TAG, label + "=null");
            return;
        }
        value = sanitizeForLog(value);
        int length = value.length();
        if (length == 0) {
            Log.i(TAG, label + " is empty");
            return;
        }
        int partCount = (length + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE;
        for (int start = 0, part = 1; start < length; start += LOG_CHUNK_SIZE, part++) {
            int end = Math.min(length, start + LOG_CHUNK_SIZE);
            Log.i(TAG, label + " part " + part + "/" + partCount + ": " + value.substring(start, end));
        }
    }

    private static String sanitizeForLog(String value) {
        return value
                .replaceAll("(?i)(\"authorization\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(&quot;authorization&quot;\\s*:\\s*&quot;).*?(&quot;)", "$1***$2")
                .replaceAll("(?i)(&amp;quot;authorization&amp;quot;\\s*:\\s*&amp;quot;).*?(&amp;quot;)", "$1***$2");
    }

}
