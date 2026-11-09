package com.brouken.player;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Converts a file-list M3U to the existing Playlist API; HLS stays with Media3. */
final class M3uPlaylist {
    static final String CHECKED = "com.lampaua.player.m3u_checked";
    static final int MAX_BYTES = 1024 * 1024;
    static final int MAX_ITEMS = 500;

    private M3uPlaylist() { }

    static boolean isCandidate(Intent intent) {
        if (intent == null || intent.getBooleanExtra(CHECKED, false)
                || intent.hasExtra(PlaylistApi.EXTRA) || intent.hasExtra("video_list")
                || intent.hasExtra(LampaPlaylistBridge.EXTRA_PLAYLIST_JSON)
                || intent.hasExtra("playlist_json")) return false;
        Uri uri = intent.getData();
        if (uri == null || !loadable(uri.getScheme())) return false;
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String type = intent.getType();
        return path.endsWith(".m3u") || path.endsWith(".m3u8")
                || type != null && type.toLowerCase(Locale.ROOT).contains("mpegurl");
    }

    static boolean explicitFileList(Uri uri) {
        return uri != null && uri.getPath() != null
                && uri.getPath().toLowerCase(Locale.ROOT).endsWith(".m3u");
    }

    static boolean loadable(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme);
    }

    static Map<String, String> headers(Intent intent) {
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String[] values = intent.getStringArrayExtra("headers");
        if (values != null) for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i] != null && values[i + 1] != null) result.put(values[i], values[i + 1]);
        }
        String credentials = intent.getData() == null ? null : intent.getData().getUserInfo();
        if (credentials != null && credentials.contains(":") && !result.containsKey("Authorization")) {
            result.put("Authorization", "Basic " + Base64.encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        }
        return result;
    }

    /** Network interceptors run for every redirect and HLS segment, after request headers are set. */
    static okhttp3.OkHttpClient mediaClient(okhttp3.OkHttpClient client, Intent intent) {
        if (intent == null || !intent.getBooleanExtra(CHECKED, false) || intent.getData() == null) return client;
        final Uri origin = intent.getData();
        return client.newBuilder().addNetworkInterceptor(chain -> {
            okhttp3.Request request = chain.request();
            if (!sameOrigin(origin, Uri.parse(request.url().toString()))) {
                request = request.newBuilder().removeHeader("Authorization").removeHeader("Cookie")
                        .removeHeader("Proxy-Authorization").build();
            }
            return chain.proceed(request);
        }).build();
    }

    static boolean sameOrigin(Uri first, Uri second) {
        if (first == null || second == null || first.getHost() == null || second.getHost() == null) return false;
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost()) && port(first) == port(second);
    }

    private static int port(Uri uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    static Map<String, String> safeHeaders(Map<String, String> headers, Uri origin, Uri target) {
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        result.putAll(headers);
        if (!sameOrigin(origin, target)) {
            result.remove("Authorization"); result.remove("Cookie"); result.remove("Proxy-Authorization");
        }
        return result;
    }

    /** Null means HLS or not an extended M3U. Invalid/oversized file lists fail explicitly. */
    static Bundle parse(String text, Uri base, Uri credentialOrigin, Map<String, String> headers) throws IOException {
        if (text.length() > MAX_BYTES) throw new IOException("M3U too large");
        boolean extended = false;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String tag = clean(line).toUpperCase(Locale.ROOT);
                if (tag.startsWith("#EXT-X-")) return null;
                if (tag.startsWith("#EXTM3U") || tag.startsWith("#EXTINF:")) extended = true;
            }
        }
        if (!extended) return null;
        List<Bundle> items = new ArrayList<>();
        String title = null;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = clean(line);
                if (line.isEmpty()) continue;
                if (line.regionMatches(true, 0, "#EXTINF:", 0, 8)) {
                    title = title(line);
                } else if (!line.startsWith("#")) {
                    Uri uri;
                    try { uri = Uri.parse(new URI(base.toString()).resolve(line.replace(" ", "%20")).normalize().toASCIIString()); }
                    catch (Exception invalid) { throw new IOException("Invalid M3U URI", invalid); }
                    boolean network = "http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme());
                    if (!loadable(uri.getScheme()) || network && !("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))) throw new IOException("Unsafe M3U URI");
                    Bundle item = new Bundle();
                    item.putString("uri", uri.toString());
                    if (title != null && !title.isEmpty()) item.putString("title", title);
                    // fromlast is resolved by TorrServer. Preserve the returned order and do not
                    // substitute unrelated local resume positions for its first file.
                    item.putInt("position_sec", 0);
                    List<String> pairs = new ArrayList<>();
                    for (Map.Entry<String, String> header : safeHeaders(headers, credentialOrigin, uri).entrySet()) {
                        pairs.add(header.getKey()); pairs.add(header.getValue());
                    }
                    item.putStringArray("headers", pairs.toArray(new String[0]));
                    items.add(item); title = null;
                    if (items.size() > MAX_ITEMS) throw new IOException("Too many M3U items");
                }
            }
        }
        if (items.isEmpty()) throw new IOException("Empty M3U playlist");
        Bundle playlist = new Bundle();
        playlist.putParcelableArray("items", items.toArray(new Bundle[0]));
        playlist.putInt("start_index", 0);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(playlist);
            if (parcel.dataSize() > 256 * 1024) throw new IOException("M3U launch too large");
        } finally { parcel.recycle(); }
        return playlist;
    }

    private static String clean(String value) {
        return value.replace("\uFEFF", "").trim();
    }

    private static String title(String line) {
        boolean quoted = false;
        for (int i = 8; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) return line.substring(i + 1).trim();
        }
        return null;
    }
}
