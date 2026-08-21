package com.brouken.player;

import androidx.media3.common.PlaybackException;

import java.util.Locale;
import java.util.regex.Pattern;

final class StreamTypeFallbackPolicy {
    private static final Pattern DIRECT_MEDIA_EXTENSION = Pattern.compile(
            ".*\\.(?:3gp|aac|avi|flac|flv|m2ts|m4a|m4v|mkv|mov|mp3|mp4|mpeg|mpg|ogg|ogv|opus|ts|vob|wav|webm|wmv)$");

    private StreamTypeFallbackPolicy() {
    }

    static boolean canTryHls(boolean playbackEverReady, int errorCode,
                             String currentMimeType, String uri) {
        return currentMimeType == null && canTryAlternate(playbackEverReady, errorCode, uri);
    }

    static boolean canTryAlternate(boolean playbackEverReady, int errorCode, String uri) {
        return !playbackEverReady && isParsingFailure(errorCode) && !isDirectMediaUri(uri);
    }

    private static boolean isParsingFailure(int errorCode) {
        return errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED;
    }

    private static boolean isDirectMediaUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) return false;
        String path = uri.trim().toLowerCase(Locale.ROOT).replace("%2e", ".");
        int query = path.indexOf('?');
        int fragment = path.indexOf('#');
        int end = path.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return DIRECT_MEDIA_EXTENSION.matcher(path.substring(0, end)).matches();
    }
}
