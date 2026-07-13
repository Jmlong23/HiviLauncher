package com.ljm.audiotoollib.upnpserver.utils;

import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpnpMediaTypeUtil {
    private static final Pattern RES_PROTOCOL_INFO_PATTERN =
            Pattern.compile("<res[^>]*protocolInfo=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "flac", "aac", "m4a", "wav", "ogg", "opus", "ape", "wma", "alac"
    ));

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "m4v", "mkv", "avi", "mov", "3gp", "ts", "m2ts", "m3u8", "webm", "flv"
    ));

    public enum MediaKind {
        AUDIO,
        VIDEO,
        UNKNOWN
    }

    private UpnpMediaTypeUtil() {
    }

    public static MediaKind determineMediaKind(String currentURI, String currentURIMetaData) {
        MediaKind metadataKind = determineKindFromMetadata(currentURIMetaData);
        if (metadataKind != MediaKind.UNKNOWN) {
            return metadataKind;
        }
        MediaKind uriKind = determineKindFromUri(currentURI);
        if (uriKind != MediaKind.UNKNOWN) {
            return uriKind;
        }
        return MediaKind.UNKNOWN;
    }

    public static boolean isAudioMedia(String currentURI, String currentURIMetaData) {
        return determineMediaKind(currentURI, currentURIMetaData) == MediaKind.AUDIO;
    }

    public static boolean isVideoMedia(String currentURI, String currentURIMetaData) {
        return determineMediaKind(currentURI, currentURIMetaData) == MediaKind.VIDEO;
    }

    private static MediaKind determineKindFromMetadata(String metadata) {
        if (TextUtils.isEmpty(metadata)) {
            return MediaKind.UNKNOWN;
        }
        String lowerMetadata = metadata.toLowerCase(Locale.US);
        if (lowerMetadata.contains("object.item.videoitem")
                || lowerMetadata.contains("object.item.imageitem")
                || lowerMetadata.contains("video/")) {
            return MediaKind.VIDEO;
        }
        if (lowerMetadata.contains("object.item.audioitem")
                || lowerMetadata.contains("object.item.audioitem.musictrack")
                || lowerMetadata.contains("audio/")) {
            return MediaKind.AUDIO;
        }
        String protocolInfo = extractProtocolInfo(lowerMetadata);
        if (protocolInfo.startsWith("http-get:*:audio/")) {
            return MediaKind.AUDIO;
        }
        if (protocolInfo.startsWith("http-get:*:video/")) {
            return MediaKind.VIDEO;
        }
        return MediaKind.UNKNOWN;
    }

    private static MediaKind determineKindFromUri(String uri) {
        String extension = getUriExtension(uri);
        if (TextUtils.isEmpty(extension)) {
            return MediaKind.UNKNOWN;
        }
        if (AUDIO_EXTENSIONS.contains(extension)) {
            return MediaKind.AUDIO;
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return MediaKind.VIDEO;
        }
        return MediaKind.UNKNOWN;
    }

    public static String getUriExtension(String uri) {
        if (TextUtils.isEmpty(uri)) {
            return "";
        }
        int queryIndex = uri.indexOf('?');
        String sanitizedUri = queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
        int dotIndex = sanitizedUri.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= sanitizedUri.length() - 1) {
            return "";
        }
        return sanitizedUri.substring(dotIndex + 1).toLowerCase(Locale.US);
    }

    private static String extractProtocolInfo(String metadata) {
        Matcher matcher = RES_PROTOCOL_INFO_PATTERN.matcher(metadata);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.US);
        }
        return "";
    }
}
