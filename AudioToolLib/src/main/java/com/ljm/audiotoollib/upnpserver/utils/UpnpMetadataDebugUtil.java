package com.ljm.audiotoollib.upnpserver.utils;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.entity.MediaInfo;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpnpMetadataDebugUtil {
    private static final Pattern RES_PROTOCOL_INFO_PATTERN =
            Pattern.compile("<res[^>]*protocolInfo=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private UpnpMetadataDebugUtil() {
    }

    public static void logTransportMetadata(String tag, String currentURI, String currentURIMetaData) {
        Log.i(tag, "metadataInspect uriSummary=" + summarizeValue(currentURI)
                + ", uriExt=" + getUriExtension(currentURI)
                + ", metaSummary=" + summarizeValue(currentURIMetaData));

        if (currentURIMetaData == null || currentURIMetaData.isEmpty()) {
            Log.i(tag, "metadataInspect skipped: metadata empty");
            return;
        }

        MediaInfo parsedInfo = new MediaInfo();
        try {
            parsedInfo.parseMetaData(currentURIMetaData);
            Log.i(tag, "metadataInspect parsed title=" + quote(parsedInfo.getTitle())
                    + ", artist=" + quote(parsedInfo.getArtist())
                    + ", creator=" + quote(parsedInfo.getCreator())
                    + ", album=" + quote(parsedInfo.getAlbum())
                    + ", albumArtSummary=" + summarizeValue(parsedInfo.getAlbumArtURI())
                    + ", protocolInfo=" + quote(extractProtocolInfo(currentURIMetaData)));
        } catch (Throwable t) {
            Log.w(tag, "metadataInspect parseMetaData failed", t);
        }

        inspectAlbumArtPayload(tag, parsedInfo.getAlbumArtURI());
    }

    private static void inspectAlbumArtPayload(String tag, String rawAlbumArt) {
        if (rawAlbumArt == null || rawAlbumArt.isEmpty()) {
            Log.i(tag, "metadataInspect albumArt payload empty");
            return;
        }

        String decodedPayload = DomHelper.decodeAlbumArtUriToJson(rawAlbumArt);
        if (decodedPayload == null || decodedPayload.isEmpty()) {
            Log.i(tag, "metadataInspect decodedAlbumArt empty");
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(decodedPayload);
            StringBuilder keys = new StringBuilder();
            StringBuilder summary = new StringBuilder();
            Iterator<String> iterator = jsonObject.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (keys.length() > 0) {
                    keys.append(",");
                }
                keys.append(key);
                if (summary.length() > 0) {
                    summary.append(", ");
                }
                summary.append(key).append("=").append(summarizeJsonValue(key, jsonObject.opt(key)));
            }
            Log.i(tag, "metadataInspect decodedAlbumArt keys=" + keys);
            Log.i(tag, "metadataInspect decodedAlbumArt summary=" + summary);
        } catch (Throwable t) {
            Log.w(tag, "metadataInspect decodedAlbumArt is not valid json", t);
        }
    }

    private static String summarizeJsonValue(String key, Object value) {
        if (value == null) {
            return "null";
        }
        String stringValue = String.valueOf(value);
        if (isSensitiveKey(key) || looksLikeUrl(stringValue)) {
            return summarizeValue(stringValue);
        }
        if ("matchLyric".equalsIgnoreCase(key)) {
            return summarizeValue(stringValue);
        }
        return quote(stringValue);
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("authorization")
                || lowerKey.contains("token")
                || lowerKey.contains("playurl")
                || lowerKey.contains("coverurl");
    }

    private static boolean looksLikeUrl(String value) {
        if (value == null) {
            return false;
        }
        String lowerValue = value.toLowerCase();
        return lowerValue.startsWith("http://") || lowerValue.startsWith("https://");
    }

    private static String extractProtocolInfo(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        Matcher matcher = RES_PROTOCOL_INFO_PATTERN.matcher(metadata);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String getUriExtension(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        int queryIndex = uri.indexOf('?');
        String sanitizedUri = queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
        int dotIndex = sanitizedUri.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= sanitizedUri.length() - 1) {
            return "";
        }
        return sanitizedUri.substring(dotIndex + 1).toLowerCase();
    }

    private static String summarizeValue(String value) {
        if (value == null) {
            return "null";
        }
        return "len=" + value.length() + ", hash=" + value.hashCode();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value + "'";
    }
}
