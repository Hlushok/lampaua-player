package com.brouken.player;

import java.util.Locale;
import java.util.Map;

/** Decides when an HTTP source needs an initial whole-resource Range request. */
final class RangeRequestPolicy {
    static final String WHOLE_RESOURCE = "bytes=0-";

    private RangeRequestPolicy() {}

    static boolean shouldSeed(String uri, String mimeType, Map<String, String> headers) {
        if (uri == null) return false;
        String normalizedUri = uri.trim().toLowerCase(Locale.US);
        if (!normalizedUri.startsWith("http://") && !normalizedUri.startsWith("https://")) {
            return false;
        }
        if (containsHeader(headers, "Range") || isAdaptiveManifest(normalizedUri, mimeType)) {
            return false;
        }
        return true;
    }

    private static boolean containsHeader(Map<String, String> headers, String name) {
        if (headers == null) return false;
        for (String key : headers.keySet()) {
            if (key != null && name.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private static boolean isAdaptiveManifest(String uri, String mimeType) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        if (mime.contains("mpegurl") || mime.contains("dash+xml") || mime.contains("sstr+xml")) {
            return true;
        }
        return uri.contains(".m3u8") || uri.contains(".mpd")
                || uri.contains(".ism/manifest") || uri.contains(".isml/manifest")
                || uri.endsWith(".ism") || uri.endsWith(".isml");
    }
}
