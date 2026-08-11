package com.brouken.player;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PlaylistIdentity {

    private static final Set<String> TRANSIENT_QUERY_KEYS = new HashSet<>(Arrays.asList(
            "access_token", "auth", "authorization", "e", "et", "exp", "expires",
            "expiry", "hash", "hdnts", "hmac", "jwt", "key-pair-id", "keypairid",
            "md5", "policy", "session", "sessionid", "sig", "signature", "st",
            "timestamp", "token", "ts"
    ));

    private PlaylistIdentity() {
    }

    static String key(String mediaUri, String imdbId, String tmdbId, int season, int episode) {
        String imdb = clean(imdbId);
        String tmdb = clean(tmdbId);
        if (imdb != null || tmdb != null) {
            return "meta:imdb=" + value(imdb)
                    + "|tmdb=" + value(tmdb)
                    + "|season=" + season
                    + "|episode=" + episode;
        }
        return "media:" + normalizeMediaUri(mediaUri);
    }

    private static String normalizeMediaUri(String mediaUri) {
        String input = clean(mediaUri);
        if (input == null) return "unknown";
        try {
            URI uri = new URI(input);
            String scheme = lower(uri.getScheme());
            String host = lower(uri.getHost());
            if (scheme == null || host == null) return input;

            int port = uri.getPort();
            boolean defaultPort = ("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443);
            StringBuilder normalized = new StringBuilder()
                    .append(scheme).append("://").append(host);
            if (port >= 0 && !defaultPort) normalized.append(':').append(port);
            String path = uri.getRawPath();
            normalized.append(path == null || path.isEmpty() ? "/" : path);

            List<String> stableQuery = stableQuery(uri.getRawQuery());
            if (!stableQuery.isEmpty()) {
                normalized.append('?');
                for (int i = 0; i < stableQuery.size(); i++) {
                    if (i > 0) normalized.append('&');
                    normalized.append(stableQuery.get(i));
                }
            }
            return normalized.toString();
        } catch (URISyntaxException ignored) {
            return input;
        }
    }

    private static List<String> stableQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) continue;
            int separator = part.indexOf('=');
            String name = separator >= 0 ? part.substring(0, separator) : part;
            if (!TRANSIENT_QUERY_KEYS.contains(name.toLowerCase(Locale.US))) {
                result.add(part);
            }
        }
        Collections.sort(result);
        return result;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.US);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.US);
    }

    private static String value(String value) {
        return value == null ? "-" : value;
    }
}
