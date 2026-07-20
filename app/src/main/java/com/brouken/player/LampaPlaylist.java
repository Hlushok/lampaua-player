package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * LampaUA's player-side episode queue. Entries may contain a ready media URL or
 * a Lampac resolver URL. Resolver responses are cached briefly because stream
 * links are commonly signed and must not be resolved for an entire season too
 * early.
 */
final class LampaPlaylist {
    static final String EXTRA_PLAYLIST_JSON = "lampaua.playlist_json";
    static final String EXTRA_PLAYLIST_INDEX = "lampaua.playlist_index";
    static final String EXTRA_AUTO_NEXT = "lampaua.auto_next";
    static final String EXTRA_CURRENT_URL = "lampaua.current_url";
    static final String EXTRA_PLAYBACK_RESULTS = "lampaua.playback_results";

    private static final String PREFS_NAME = "lampaua_resolver_cache";
    private static final long DEFAULT_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long MAX_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(2);
    private static final long SEGMENT_CACHE_TTL_MS = TimeUnit.DAYS.toMillis(7);
    private static final String LAMPAC_SKIP_URL = "https://kinohub.uk/lite/lampauaskip/segments";

    interface ResolveCallback {
        void onResolved(Item item);
        void onError(String message);
    }

    interface SegmentCallback {
        void onLoaded(Item item);
    }

    private interface JsonCallback {
        void onComplete(JSONObject json);
    }

    static final class Subtitle {
        String url;
        String label;
        String language;
    }

    static final class Segment {
        String type;
        String kind;
        String source;
        long startMs;
        long endMs;

        boolean contains(long positionMs) {
            return positionMs >= startMs && positionMs < endMs;
        }
    }

    static final class Item {
        String id;
        String url;
        String resolverUrl;
        String title;
        String thumbnail;
        String mimeType;
        String imdbId;
        int tmdbId = -1;
        int kpId = -1;
        int malId = -1;
        String mediaType;
        String originalTitle;
        int year = -1;
        boolean anime;
        int season = -1;
        int episode = -1;
        long positionMs;
        long resolvedAt;
        boolean segmentLookupStarted;
        long cacheTtlMs = DEFAULT_CACHE_TTL_MS;
        final HashMap<String, String> headers = new HashMap<>();
        final HashMap<String, String> resolverHeaders = new HashMap<>();
        final HashMap<String, String> quality = new HashMap<>();
        final List<Subtitle> subtitles = new ArrayList<>();
        final List<Segment> segments = new ArrayList<>();

        boolean isResolved() {
            return url != null && !url.trim().isEmpty();
        }

        String displayTitle(int index) {
            return title == null || title.trim().isEmpty() ? "Серія " + (index + 1) : title;
        }
    }

    private final Context context;
    private final SharedPreferences cache;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    private final List<Item> items = new ArrayList<>();
    private final JSONArray playbackResults = new JSONArray();
    private int currentIndex;
    private boolean autoNext = true;

    private LampaPlaylist(Context context) {
        this.context = context.getApplicationContext();
        cache = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static LampaPlaylist fromJson(Context context, String raw, int requestedIndex, boolean requestedAutoNext) throws JSONException {
        LampaPlaylist playlist = new LampaPlaylist(context);
        if (raw == null || raw.trim().isEmpty()) {
            return playlist;
        }

        String trimmed = raw.trim();
        JSONArray source;
        if (trimmed.startsWith("[")) {
            source = new JSONArray(trimmed);
            playlist.currentIndex = requestedIndex;
            playlist.autoNext = requestedAutoNext;
        } else {
            JSONObject root = new JSONObject(trimmed);
            source = root.optJSONArray("items");
            if (source == null) source = root.optJSONArray("playlist");
            if (source == null) source = root.optJSONArray("data");
            if (source == null) source = new JSONArray();
            playlist.currentIndex = root.optInt("current_index", root.optInt("currentIndex", requestedIndex));
            playlist.autoNext = root.has("auto_next")
                    ? root.optBoolean("auto_next", requestedAutoNext)
                    : root.optBoolean("autoNext", requestedAutoNext);
        }

        for (int i = 0; i < source.length(); i++) {
            JSONObject json = source.optJSONObject(i);
            if (json != null) playlist.items.add(parseItem(json));
        }
        if (!playlist.items.isEmpty()) {
            playlist.currentIndex = Math.max(0, Math.min(playlist.currentIndex, playlist.items.size() - 1));
        } else {
            playlist.currentIndex = 0;
        }
        return playlist;
    }

    private static Item parseItem(JSONObject json) {
        Item item = new Item();
        item.id = optText(json, "id");
        item.url = firstText(json, "url", "media_url", "stream_url");
        item.resolverUrl = firstText(json, "resolver_url", "resolver", "call_url");
        item.title = firstText(json, "title", "name");
        item.thumbnail = firstText(json, "thumbnail", "poster", "image");
        item.mimeType = firstText(json, "mime_type", "mimeType", "content_type");
        item.imdbId = firstText(json, "imdb_id", "imdbId");
        item.tmdbId = firstPositiveInt(json, "tmdb_id", "tmdbId");
        item.kpId = firstPositiveInt(json, "kp_id", "kinopoisk_id", "kinopoiskId");
        item.malId = firstPositiveInt(json, "mal_id", "malId");
        item.mediaType = firstText(json, "media_type", "mediaType");
        item.originalTitle = firstText(json, "original_title", "originalTitle");
        item.year = firstPositiveInt(json, "year", "release_year");
        item.anime = json.optBoolean("is_anime", json.optBoolean("anime", false));
        item.season = firstPositiveInt(json, "season", "season_number", "seasonNumber");
        item.episode = firstPositiveInt(json, "episode", "episode_number", "episodeNumber");
        item.positionMs = Math.max(0, json.optLong("position_ms", json.optLong("position", 0)));
        long requestedTtl = json.optLong("cache_ttl_ms", DEFAULT_CACHE_TTL_MS);
        item.cacheTtlMs = Math.max(TimeUnit.MINUTES.toMillis(1), Math.min(requestedTtl, MAX_CACHE_TTL_MS));
        readStringMap(json.optJSONObject("headers"), item.headers);
        readStringMap(json.optJSONObject("resolver_headers"), item.resolverHeaders);
        readStringMap(json.optJSONObject("quality"), item.quality);
        readSubtitles(json.optJSONArray("subtitles"), item.subtitles);
        readSegments(json.optJSONObject("segments"), item.segments);
        if ((item.url == null || item.url.isEmpty()) && !item.quality.isEmpty()) {
            item.url = item.quality.values().iterator().next();
        }
        return item;
    }

    private static void readSegments(JSONObject source, List<Segment> target) {
        if (source == null) return;
        readSegmentsOfType(source.optJSONArray("skip"), "skip", target);
        readSegmentsOfType(source.optJSONArray("ad"), "ad", target);
    }

    private static void readSegmentsOfType(JSONArray source, String type, List<Segment> target) {
        if (source == null) return;
        for (int i = 0; i < source.length(); i++) {
            JSONObject json = source.optJSONObject(i);
            if (json == null) continue;
            double start = json.optDouble("start", -1);
            double end = json.optDouble("end", -1);
            if (start < 0 || end <= start) continue;
            Segment segment = new Segment();
            segment.type = type;
            segment.kind = type;
            segment.source = "lampa";
            segment.startMs = Math.round(start * 1000d);
            segment.endMs = Math.round(end * 1000d);
            target.add(segment);
        }
    }

    int size() { return items.size(); }
    boolean isEmpty() { return items.isEmpty(); }
    boolean isAutoNext() { return autoNext; }
    int getCurrentIndex() { return currentIndex; }
    Item getCurrent() { return get(currentIndex); }
    Item get(int index) { return index >= 0 && index < items.size() ? items.get(index) : null; }
    boolean hasNext() { return currentIndex + 1 < items.size(); }

    String useLowerQuality(Item item) {
        if (item == null || item.quality.isEmpty()) return null;
        int currentScore = Integer.MAX_VALUE;
        for (String label : item.quality.keySet()) {
            if (item.quality.get(label).equals(item.url)) {
                currentScore = qualityScore(label);
                break;
            }
        }
        String selected = null;
        int selectedScore = -1;
        for (String label : item.quality.keySet()) {
            String candidate = item.quality.get(label);
            if (candidate == null || candidate.equals(item.url)) continue;
            int score = qualityScore(label);
            boolean isLower = currentScore == Integer.MAX_VALUE ? score <= 1080 : score < currentScore;
            if (isLower && score > selectedScore) {
                selected = candidate;
                selectedScore = score;
            }
        }
        if (selected != null) item.url = selected;
        return selected;
    }

    private static int qualityScore(String label) {
        if (label == null) return 0;
        String digits = label.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try { return Integer.parseInt(digits); } catch (NumberFormatException ignored) { }
        }
        String normalized = label.toLowerCase();
        if (normalized.contains("4k") || normalized.contains("uhd")) return 2160;
        if (normalized.contains("fhd")) return 1080;
        if (normalized.contains("hd")) return 720;
        return 0;
    }

    String[] getDisplayTitles() {
        String[] titles = new String[items.size()];
        for (int i = 0; i < items.size(); i++) titles[i] = items.get(i).displayTitle(i);
        return titles;
    }

    void setCurrentIndex(int index) {
        if (index >= 0 && index < items.size()) currentIndex = index;
    }

    void recordResult(Item item, long positionMs, long durationMs, boolean ended) {
        if (item == null) return;
        JSONObject result = new JSONObject();
        try {
            result.put("index", currentIndex);
            result.put("id", item.id == null ? JSONObject.NULL : item.id);
            result.put("url", item.url == null ? JSONObject.NULL : item.url);
            result.put("position", Math.max(0, positionMs));
            result.put("duration", Math.max(0, durationMs));
            result.put("ended", ended);
            playbackResults.put(result);
        } catch (JSONException ignored) {
        }
    }

    String getPlaybackResultsJson() {
        return playbackResults.toString();
    }

    void fetchRemoteSegments(Item item, long durationMs, SegmentCallback callback) {
        if (item != null && item.segmentLookupStarted) {
            mainHandler.post(() -> callback.onLoaded(item));
            return;
        }
        if (item == null) {
            mainHandler.post(() -> callback.onLoaded(item));
            return;
        }
        item.segmentLookupStarted = true;
        // Segments supplied by LAMPA/balancer are authoritative and avoid any
        // database request. The server is queried only when metadata is absent.
        if (!item.segments.isEmpty()) {
            mainHandler.post(() -> callback.onLoaded(item));
            return;
        }
        if ((item.imdbId == null || !item.imdbId.matches("tt\\d+"))
                && item.tmdbId <= 0 && item.kpId <= 0 && item.malId <= 0) {
            mainHandler.post(() -> callback.onLoaded(item));
            return;
        }
        fetchSegmentDatabases(item, durationMs, callback);
    }

    private void fetchSegmentDatabases(Item item, long durationMs, SegmentCallback callback) {
        String key = segmentCacheKey(item);
        String cached = cache.getString(key + ".json", null);
        long cachedAt = cache.getLong(key + ".time", 0);
        if (cached != null) {
            try {
                JSONArray cachedArray = new JSONArray(cached);
                long ttl = cachedArray.length() > 0
                        ? SEGMENT_CACHE_TTL_MS : TimeUnit.HOURS.toMillis(12);
                if (System.currentTimeMillis() - cachedAt <= ttl) {
                    mergeSegments(item.segments, segmentsFromCache(cachedArray));
                    mainHandler.post(() -> callback.onLoaded(item));
                    return;
                }
            } catch (JSONException ignored) { }
        }

        String url = buildSegmentUrl(item, durationMs);
        requestJson(url, json -> {
            ArrayList<Segment> remote = new ArrayList<>();
            if (json != null) parseUnifiedSegments(json, remote);
            mergeSegments(item.segments, remote);
            if (json != null) {
                cache.edit()
                        .putString(key + ".json", segmentsToJson(remote).toString())
                        .putLong(key + ".time", System.currentTimeMillis())
                        .apply();
            }
            mainHandler.post(() -> callback.onLoaded(item));
        });
    }

    private void requestJson(String url, JsonCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "UA-Player/1.5")
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onComplete(null);
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onComplete(null);
                        return;
                    }
                    try {
                        callback.onComplete(new JSONObject(response.body().string()));
                    } catch (JSONException ignored) {
                        callback.onComplete(null);
                    }
                }
            }
        });
    }

    private static String buildSegmentUrl(Item item, long durationMs) {
        Uri.Builder builder = Uri.parse(LAMPAC_SKIP_URL).buildUpon();
        if (item.imdbId != null) builder.appendQueryParameter("imdb_id", item.imdbId);
        if (item.tmdbId > 0) builder.appendQueryParameter("tmdb_id", String.valueOf(item.tmdbId));
        if (item.kpId > 0) builder.appendQueryParameter("kp_id", String.valueOf(item.kpId));
        if (item.malId > 0) builder.appendQueryParameter("mal_id", String.valueOf(item.malId));
        builder.appendQueryParameter("type", "movie".equals(item.mediaType) ? "movie" : "tv");
        if (item.season > 0) builder.appendQueryParameter("season", String.valueOf(item.season));
        if (item.episode > 0) builder.appendQueryParameter("episode", String.valueOf(item.episode));
        if (durationMs > 0) {
            builder.appendQueryParameter("duration", String.valueOf(Math.max(1, durationMs / 1000)));
        }
        if (item.title != null) builder.appendQueryParameter("title", item.title);
        if (item.originalTitle != null) builder.appendQueryParameter("original_title", item.originalTitle);
        if (item.year > 0) builder.appendQueryParameter("year", String.valueOf(item.year));
        if (item.anime) builder.appendQueryParameter("is_anime", "true");
        builder.appendQueryParameter("prefetch", "2");
        return builder.build().toString();
    }

    private static void parseUnifiedSegments(JSONObject root, List<Segment> target) {
        JSONArray segments = root.optJSONArray("segments");
        if (segments == null) return;
        for (int i = 0; i < segments.length(); i++) {
            JSONObject json = segments.optJSONObject(i);
            if (json == null) continue;
            long startMs = timeValueMs(json, "start_ms", "start");
            long endMs = timeValueMs(json, "end_ms", "end");
            if (startMs < 0 || endMs <= startMs) continue;
            Segment segment = new Segment();
            segment.type = "skip";
            segment.kind = json.optString("kind", "intro");
            segment.source = json.optString("source", "lampac");
            segment.startMs = startMs;
            segment.endMs = endMs;
            mergeSegments(target, java.util.Collections.singletonList(segment));
        }
    }

    private static void parseIntroDb(JSONObject root, List<Segment> target) {
        addRemoteSegment(root.optJSONObject("intro"), "intro", "introdb", target);
        addRemoteSegment(root.optJSONObject("recap"), "recap", "introdb", target);
        addRemoteSegment(root.optJSONObject("outro"), "outro", "introdb", target);
        addRemoteSegment(root.optJSONObject("preview"), "preview", "introdb", target);
    }

    private static void parseSkipDb(JSONObject root, List<Segment> target) {
        JSONObject segments = root.optJSONObject("segments");
        if (segments == null) return;
        addRemoteSegment(segments.optJSONObject("intro"), "intro", "skipdb", target);
        addRemoteSegment(segments.optJSONObject("recap"), "recap", "skipdb", target);
        addRemoteSegment(segments.optJSONObject("outro"), "outro", "skipdb", target);
        addRemoteSegment(segments.optJSONObject("preview"), "preview", "skipdb", target);
    }

    private static void addRemoteSegment(JSONObject json, String kind, String source, List<Segment> target) {
        if (json == null) return;
        long startMs = timeValueMs(json, "start_ms", "start_sec");
        long endMs = timeValueMs(json, "end_ms", "end_sec");
        if (startMs < 0 || endMs <= startMs) return;
        Segment segment = new Segment();
        segment.type = "skip";
        segment.kind = kind;
        segment.source = source;
        segment.startMs = startMs;
        segment.endMs = endMs;
        mergeSegments(target, java.util.Collections.singletonList(segment));
    }

    private static long timeValueMs(JSONObject json, String msKey, String secKey) {
        if (json.has(msKey) && !json.isNull(msKey)) {
            return Math.max(0, json.optLong(msKey, -1));
        }
        Object seconds = json.opt(secKey);
        if (seconds == null || seconds == JSONObject.NULL) return -1;
        if (seconds instanceof Number) return Math.round(((Number) seconds).doubleValue() * 1000d);
        String value = String.valueOf(seconds).trim();
        try {
            if (!value.contains(":")) return Math.round(Double.parseDouble(value) * 1000d);
            String[] parts = value.split(":");
            double total = 0;
            for (String part : parts) total = total * 60d + Double.parseDouble(part);
            return Math.round(total * 1000d);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void mergeSegments(List<Segment> target, List<Segment> additions) {
        for (Segment candidate : additions) {
            boolean duplicate = false;
            for (Segment existing : target) {
                long overlap = Math.min(existing.endMs, candidate.endMs)
                        - Math.max(existing.startMs, candidate.startMs);
                if (overlap > 0 && (existing.kind == null || candidate.kind == null
                        || existing.kind.equals(candidate.kind))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) target.add(candidate);
        }
        target.sort((left, right) -> Long.compare(left.startMs, right.startMs));
    }

    private static JSONArray segmentsToJson(List<Segment> segments) {
        JSONArray result = new JSONArray();
        for (Segment segment : segments) {
            JSONObject json = new JSONObject();
            try {
                json.put("type", segment.type);
                json.put("kind", segment.kind);
                json.put("source", segment.source);
                json.put("start_ms", segment.startMs);
                json.put("end_ms", segment.endMs);
                result.put(json);
            } catch (JSONException ignored) { }
        }
        return result;
    }

    private static List<Segment> segmentsFromCache(JSONArray source) {
        ArrayList<Segment> result = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject json = source.optJSONObject(i);
            if (json == null) continue;
            Segment segment = new Segment();
            segment.type = json.optString("type", "skip");
            segment.kind = json.optString("kind", "intro");
            segment.source = json.optString("source", "cache");
            segment.startMs = json.optLong("start_ms", -1);
            segment.endMs = json.optLong("end_ms", -1);
            if (segment.startMs >= 0 && segment.endMs > segment.startMs) result.add(segment);
        }
        return result;
    }

    private static String segmentCacheKey(Item item) {
        return "segments_v3_" + item.imdbId + "_" + item.tmdbId + "_"
                + item.kpId + "_" + item.malId + "_"
                + item.season + "_" + item.episode;
    }

    void preResolveNext() {
        if (!hasNext()) return;
        resolve(currentIndex + 1, new ResolveCallback() {
            @Override public void onResolved(Item item) { }
            @Override public void onError(String message) { }
        });
    }

    void resolve(int index, ResolveCallback callback) {
        Item item = get(index);
        if (item == null) {
            callback.onError("Episode is unavailable");
            return;
        }
        if (item.isResolved()) {
            mainHandler.post(() -> callback.onResolved(item));
            return;
        }
        if (item.resolverUrl == null || item.resolverUrl.trim().isEmpty()) {
            callback.onError("Episode has no media or resolver URL");
            return;
        }

        String cacheKey = cacheKey(item.resolverUrl);
        String cached = cache.getString(cacheKey + ".json", null);
        long cachedAt = cache.getLong(cacheKey + ".time", 0);
        if (cached != null && System.currentTimeMillis() - cachedAt <= item.cacheTtlMs) {
            try {
                applyResolvedPayload(item, new JSONObject(cached));
                if (item.isResolved()) {
                    mainHandler.post(() -> callback.onResolved(item));
                    return;
                }
            } catch (JSONException ignored) {
            }
        }

        Request.Builder request = new Request.Builder().url(item.resolverUrl).get();
        for (String name : item.resolverHeaders.keySet()) {
            request.header(name, item.resolverHeaders.get(name));
        }
        client.newCall(request.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage() == null ? "Resolver request failed" : e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        mainHandler.post(() -> callback.onError("Resolver HTTP " + response.code()));
                        return;
                    }
                    String body = response.body().string();
                    try {
                        JSONObject payload = new JSONObject(body);
                        applyResolvedPayload(item, payload);
                        if (!item.isResolved()) {
                            mainHandler.post(() -> callback.onError("Resolver returned no playable URL"));
                            return;
                        }
                        item.resolvedAt = System.currentTimeMillis();
                        cache.edit()
                                .putString(cacheKey + ".json", payload.toString())
                                .putLong(cacheKey + ".time", item.resolvedAt)
                                .apply();
                        mainHandler.post(() -> callback.onResolved(item));
                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onError("Invalid resolver response"));
                    }
                }
            }
        });
    }

    private static void applyResolvedPayload(Item item, JSONObject payload) {
        JSONObject source = payload;
        JSONArray data = payload.optJSONArray("data");
        if (data != null && data.length() > 0 && data.optJSONObject(0) != null) {
            source = data.optJSONObject(0);
        }

        String resolvedUrl = firstText(source, "url", "media_url", "stream_url");
        HashMap<String, String> quality = new HashMap<>();
        readStringMap(source.optJSONObject("quality"), quality);
        if ((resolvedUrl == null || resolvedUrl.isEmpty()) && !quality.isEmpty()) {
            resolvedUrl = quality.values().iterator().next();
        }
        if (resolvedUrl != null && !resolvedUrl.isEmpty()) item.url = resolvedUrl;
        if (!quality.isEmpty()) {
            item.quality.clear();
            item.quality.putAll(quality);
        }
        JSONObject headers = source.optJSONObject("headers");
        if (headers != null) {
            item.headers.clear();
            readStringMap(headers, item.headers);
        }
        JSONArray subtitles = source.optJSONArray("subtitles");
        if (subtitles != null) {
            item.subtitles.clear();
            readSubtitles(subtitles, item.subtitles);
        }
        String title = firstText(source, "title", "name");
        if (title != null && !title.isEmpty()) item.title = title;
        String thumbnail = firstText(source, "thumbnail", "poster", "image");
        if (thumbnail != null && !thumbnail.isEmpty()) item.thumbnail = thumbnail;
    }

    private static void readStringMap(JSONObject json, HashMap<String, String> target) {
        if (json == null) return;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = json.optString(key, null);
            if (value != null && !value.isEmpty()) target.put(key, value);
        }
    }

    private static void readSubtitles(JSONArray json, List<Subtitle> target) {
        if (json == null) return;
        for (int i = 0; i < json.length(); i++) {
            JSONObject source = json.optJSONObject(i);
            if (source == null) continue;
            String url = firstText(source, "url", "src");
            if (url == null || url.isEmpty()) continue;
            Subtitle subtitle = new Subtitle();
            subtitle.url = url;
            subtitle.label = firstText(source, "label", "title", "name");
            subtitle.language = firstText(source, "language", "lang");
            target.add(subtitle);
        }
    }

    private static String cacheKey(String resolverUrl) {
        return "resolver_" + Integer.toHexString(resolverUrl.hashCode());
    }

    private static String firstText(JSONObject json, String... names) {
        for (String name : names) {
            String value = optText(json, name);
            if (value != null && !value.isEmpty()) return value;
        }
        return null;
    }

    private static String optText(JSONObject json, String name) {
        if (json == null || !json.has(name) || json.isNull(name)) return null;
        String value = json.optString(name, null);
        return value == null ? null : value.trim();
    }

    private static int firstPositiveInt(JSONObject json, String... names) {
        if (json == null) return -1;
        for (String name : names) {
            int value = json.optInt(name, -1);
            if (value > 0) return value;
        }
        return -1;
    }
}
