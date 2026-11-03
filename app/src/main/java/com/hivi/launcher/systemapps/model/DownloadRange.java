package com.hivi.launcher.systemapps.model;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DownloadRange {
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    private DownloadRange() {
    }

    public static long validate(String contentRange, long offset, long contentLength)
            throws IOException {
        Matcher matcher = CONTENT_RANGE.matcher(contentRange == null ? "" : contentRange);
        if (!matcher.matches()) {
            throw new IOException("Missing or invalid Content-Range.");
        }
        try {
            long start = Long.parseLong(matcher.group(1));
            long end = Long.parseLong(matcher.group(2));
            long total = Long.parseLong(matcher.group(3));
            if (start != offset || end < start || end >= total
                    || (contentLength >= 0L && contentLength != end - start + 1L)) {
                throw new IOException("Download range does not match the partial package.");
            }
            return total;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid download range length.", exception);
        }
    }
}
