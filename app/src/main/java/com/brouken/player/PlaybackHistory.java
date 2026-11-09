package com.brouken.player;

import android.net.Uri;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Local positions use stable file identities; playback and callback URLs remain untouched. */
final class PlaybackHistory {
    static final int LIMIT = 1000;

    private PlaybackHistory() { }

    static String key(String address) {
        if (address == null) return null;
        try {
            Uri uri = Uri.parse(address);
            if (TorrFiles.speaks(uri)) uri = TorrFiles.stream(uri);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) return address;
            String path = uri.getEncodedPath();
            int stream = path == null ? -1 : path.indexOf("/stream/");
            if (stream < 0 && path != null && path.endsWith("/stream")) stream = path.length() - 7;
            if (stream < 0) return address;
            String hash = hash(uri.getQueryParameter("link"));
            String index = uri.getQueryParameter("index");
            if (hash == null || index == null || !index.matches("[0-9]{1,9}")) return address;
            int file = Integer.parseInt(index);
            if (file < 1) return address;
            scheme = scheme.toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
            int port = uri.getPort();
            String authority = host + (port < 0 || port == ("https".equals(scheme) ? 443 : 80) ? "" : ":" + port);
            return uri.buildUpon().scheme(scheme).encodedAuthority(authority)
                    .encodedPath(path.substring(0, stream) + "/stream").clearQuery().fragment(null)
                    .appendQueryParameter("link", hash).appendQueryParameter("index", String.valueOf(file))
                    .build().toString();
        } catch (RuntimeException invalid) {
            return address;
        }
    }

    private static String hash(String value) {
        if (value == null) return null;
        if (value.regionMatches(true, 0, "magnet:?", 0, 8)) {
            Uri magnet = Uri.parse("https://magnet.invalid/?" + value.substring(8));
            for (String xt : magnet.getQueryParameters("xt")) {
                if (xt.regionMatches(true, 0, "urn:btih:", 0, 9)) {
                    String found = xt.substring(9);
                    if (found.matches("[a-fA-F0-9]{40}")) return found.toLowerCase(Locale.ROOT);
                }
            }
            return null;
        }
        return value.matches("[a-fA-F0-9]{40}") ? value.toLowerCase(Locale.ROOT) : null;
    }

    static Long get(Map<String, Long> positions, String address) {
        return address == null ? null : positions.get(key(address));
    }

    static void put(LinkedHashMap<String, Long> positions, String address, long position) {
        String key = key(address);
        if (key == null || position < 0) return;
        positions.remove(key);
        positions.put(key, position);
        while (positions.size() > LIMIT) positions.remove(positions.keySet().iterator().next());
    }

    static LinkedHashMap<String, Long> migrate(Map<?, ?> old) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : old.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof Long) {
                put(result, (String) entry.getKey(), (Long) entry.getValue());
            }
        }
        return result;
    }
}
