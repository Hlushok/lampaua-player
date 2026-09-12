package com.brouken.player;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Converts the LampaUA JSON playlist into the public launcher extras understood by the player core.
 * The core remains the only playback implementation; this class only translates the launch contract.
 */
final class LampaPlaylistBridge {

    static final String EXTRA_PLAYLIST_JSON = "lampaua.playlist_json";
    static final String EXTRA_PLAYLIST_INDEX = "lampaua.playlist_index";
    static final String EXTRA_AUTO_NEXT = "lampaua.auto_next";
    static final String EXTRA_CURRENT_URL = "lampaua.current_url";
    static final String EXTRA_PLAYBACK_RESULTS = "lampaua.playback_results";
    static final String EXTRA_SERIES_TITLE = "lampaua.series_title";

    private LampaPlaylistBridge() {
    }

    /**
     * Normalizes a JSON launch in place. Returns false for a normal launcher intent or malformed JSON;
     * either case is deliberately fail-open so a direct media URI still plays.
     */
    static boolean normalize(final Intent intent) {
        if (intent == null) {
            return false;
        }
        final String raw = firstExtra(intent, EXTRA_PLAYLIST_JSON, "playlist_json");
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }

        try {
            final String trimmed = raw.trim();
            final JSONObject root = trimmed.startsWith("[") ? null : new JSONObject(trimmed);
            final JSONArray source = root == null ? new JSONArray(trimmed) : firstArray(root,
                    "items", "playlist", "data");
            if (source == null || source.length() == 0) {
                return false;
            }

            int requestedIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0);
            if (root != null) {
                requestedIndex = root.optInt("current_index",
                        root.optInt("currentIndex", requestedIndex));
            }
            final String requestedUrl = firstExtra(intent, EXTRA_CURRENT_URL, "current_url");

            final List<Item> items = new ArrayList<>();
            int normalizedIndex = 0;
            for (int i = 0; i < source.length(); i++) {
                final JSONObject value = source.optJSONObject(i);
                final Item item = Item.parse(value);
                if (item == null) {
                    continue;
                }
                if (i == requestedIndex || requestedUrl != null && requestedUrl.equals(item.url)) {
                    normalizedIndex = items.size();
                }
                items.add(item);
            }
            if (items.isEmpty()) {
                return false;
            }
            normalizedIndex = Math.max(0, Math.min(normalizedIndex, items.size() - 1));

            final int size = items.size();
            final String[] urls = new String[size];
            final String[] names = new String[size];
            final String[] filenames = new String[size];
            final String[] thumbnails = new String[size];
            final String[] segments = new String[size];
            final String[] seasons = new String[size];
            final String[] episodes = new String[size];
            final String[] imdbIds = new String[size];
            final String[] tmdbIds = new String[size];
            final Parcelable[] subtitles = new Parcelable[size];

            for (int i = 0; i < size; i++) {
                final Item item = items.get(i);
                urls[i] = item.url;
                names[i] = item.title;
                filenames[i] = item.filename;
                thumbnails[i] = item.thumbnail;
                segments[i] = item.segments;
                seasons[i] = positiveText(item.season);
                episodes[i] = positiveText(item.episode);
                imdbIds[i] = item.imdbId;
                tmdbIds[i] = item.tmdbId;
                subtitles[i] = item.subtitleBundle();
                item.putQuality(intent, i);
            }

            intent.putExtra(PlayerActivity.API_VIDEO_LIST, urls);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_NAME, names);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_FILENAME, filenames);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_THUMBNAIL, thumbnails);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_SEGMENTS, segments);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_SEASON, seasons);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_EPISODE, episodes);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_IMDB_ID, imdbIds);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_ID, tmdbIds);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_SUBTITLES, subtitles);

            final Item current = items.get(normalizedIndex);
            intent.setData(Uri.parse(current.url));
            intent.putExtra(EXTRA_PLAYLIST_INDEX, normalizedIndex);
            putCurrentItemExtras(intent, current);

            final String seriesTitle = root == null ? null : firstText(root,
                    "series_title", "seriesTitle", "collection_title", "collectionTitle", "title");
            if (seriesTitle != null) {
                intent.putExtra(EXTRA_SERIES_TITLE, seriesTitle);
                intent.putExtra("series_title", seriesTitle);
                if (!intent.hasExtra(PlayerActivity.API_TITLE)) {
                    intent.putExtra(PlayerActivity.API_TITLE, seriesTitle);
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void putCurrentItemExtras(final Intent intent, final Item item) {
        if (item.headers.length > 0) {
            intent.putExtra(PlayerActivity.API_HEADERS, item.headers);
        }
        if (item.positionMs > 0) {
            intent.putExtra(PlayerActivity.API_POSITION,
                    (int) Math.min(Integer.MAX_VALUE, item.positionMs));
        }
        if (item.thumbnail != null && !intent.hasExtra(PlayerActivity.API_THUMBNAIL)) {
            intent.putExtra(PlayerActivity.API_THUMBNAIL, item.thumbnail);
        }
        if (item.segments != null && !intent.hasExtra(PlayerActivity.API_SEGMENTS)) {
            intent.putExtra(PlayerActivity.API_SEGMENTS, item.segments);
        }
        if (item.season > 0) {
            intent.putExtra(PlayerActivity.API_SEASON, item.season);
        }
        if (item.episode > 0) {
            intent.putExtra(PlayerActivity.API_EPISODE, item.episode);
        }
        if (item.imdbId != null) {
            intent.putExtra(PlayerActivity.API_IMDB_ID, item.imdbId);
        }
        if (item.tmdbId != null) {
            intent.putExtra(PlayerActivity.API_ID, item.tmdbId);
        }
        item.putCurrentQuality(intent);
    }

    private static final class Item {
        final String url;
        final String title;
        final String filename;
        final String thumbnail;
        final String segments;
        final int season;
        final int episode;
        final String imdbId;
        final String tmdbId;
        final long positionMs;
        final String[] headers;
        final String[] qualityLabels;
        final String[] qualityUrls;
        final Uri[] subtitleUris;
        final String[] subtitleNames;
        final String[] subtitleLanguages;

        Item(String url, String title, String filename, String thumbnail, String segments,
             int season, int episode, String imdbId, String tmdbId, long positionMs,
             String[] headers, String[] qualityLabels, String[] qualityUrls,
             Uri[] subtitleUris, String[] subtitleNames, String[] subtitleLanguages) {
            this.url = url;
            this.title = title;
            this.filename = filename;
            this.thumbnail = thumbnail;
            this.segments = segments;
            this.season = season;
            this.episode = episode;
            this.imdbId = imdbId;
            this.tmdbId = tmdbId;
            this.positionMs = positionMs;
            this.headers = headers;
            this.qualityLabels = qualityLabels;
            this.qualityUrls = qualityUrls;
            this.subtitleUris = subtitleUris;
            this.subtitleNames = subtitleNames;
            this.subtitleLanguages = subtitleLanguages;
        }

        static Item parse(final JSONObject json) {
            if (json == null) {
                return null;
            }
            String url = firstText(json, "url", "media_url", "stream_url");
            if (url == null) {
                url = firstText(json, "resolver_url", "resolver", "call_url");
            }
            final JSONObject quality = firstObject(json, "quality", "qualities");
            final String[][] qualityValues = stringMap(quality);
            if (url == null && qualityValues[1].length > 0) {
                url = qualityValues[1][0];
            }
            if (url == null || Uri.parse(url).getScheme() == null) {
                return null;
            }

            final JSONArray subtitleArray = firstArray(json, "subtitles", "subs");
            final ArrayList<Uri> subtitleUris = new ArrayList<>();
            final ArrayList<String> subtitleNames = new ArrayList<>();
            final ArrayList<String> subtitleLanguages = new ArrayList<>();
            if (subtitleArray != null) {
                for (int i = 0; i < subtitleArray.length(); i++) {
                    final JSONObject subtitle = subtitleArray.optJSONObject(i);
                    final String subtitleUrl = firstText(subtitle, "url", "src");
                    if (subtitleUrl == null || Uri.parse(subtitleUrl).getScheme() == null) {
                        continue;
                    }
                    subtitleUris.add(Uri.parse(subtitleUrl));
                    final String language = firstText(subtitle, "language", "lang");
                    final String label = firstText(subtitle, "label", "title", "name", "language", "lang");
                    subtitleNames.add(label);
                    subtitleLanguages.add(language);
                }
            }

            return new Item(url,
                    firstText(json, "title", "name"),
                    firstText(json, "filename", "file_name"),
                    firstText(json, "thumbnail", "poster", "image"),
                    segmentsJson(json),
                    firstPositiveInt(json, "season", "season_number", "seasonNumber"),
                    firstPositiveInt(json, "episode", "episode_number", "episodeNumber"),
                    firstText(json, "imdb_id", "imdbId"),
                    positiveId(json, "tmdb_id", "tmdbId"),
                    Math.max(0, json.optLong("position_ms", json.optLong("position", 0))),
                    flattenMap(json.optJSONObject("headers")),
                    qualityValues[0], qualityValues[1],
                    subtitleUris.toArray(new Uri[0]), subtitleNames.toArray(new String[0]),
                    subtitleLanguages.toArray(new String[0]));
        }

        Bundle subtitleBundle() {
            if (subtitleUris.length == 0) {
                return null;
            }
            final Bundle result = new Bundle();
            result.putParcelableArray("uris", subtitleUris);
            result.putStringArray("names", subtitleNames);
            result.putStringArray("languages", subtitleLanguages);
            return result;
        }

        void putQuality(final Intent intent, final int index) {
            if (qualityLabels.length == 0) {
                return;
            }
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_QUALITY_LEVELS + "." + index,
                    qualityLabels);
            intent.putExtra(PlayerActivity.API_VIDEO_LIST_QUALITY_URLS + "." + index,
                    qualityUrls);
        }

        void putCurrentQuality(final Intent intent) {
            if (qualityLabels.length == 0) {
                return;
            }
            intent.putExtra(PlayerActivity.API_QUALITY_LEVELS, qualityLabels);
            intent.putExtra(PlayerActivity.API_QUALITY_URLS, qualityUrls);
        }
    }

    private static String segmentsJson(final JSONObject item) {
        final Object raw = item.opt("segments");
        if (raw instanceof JSONObject) {
            return raw.toString();
        }
        if (raw instanceof String && !((String) raw).trim().isEmpty()) {
            return ((String) raw).trim();
        }
        final JSONArray session = item.optJSONArray("_session_segments");
        if (session == null || session.length() == 0) {
            return null;
        }
        try {
            final JSONArray skip = new JSONArray();
            for (int i = 0; i < session.length(); i++) {
                final JSONObject segment = session.optJSONObject(i);
                if (segment == null) {
                    continue;
                }
                final long start = segment.optLong("start_ms", -1);
                final long end = segment.optLong("end_ms", -1);
                if (start >= 0 && end > start) {
                    skip.put(new JSONObject().put("start", start / 1000d).put("end", end / 1000d));
                }
            }
            return skip.length() == 0 ? null : new JSONObject().put("skip", skip).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstExtra(final Intent intent, final String... names) {
        for (String name : names) {
            final String value = intent.getStringExtra(name);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static JSONArray firstArray(final JSONObject json, final String... names) {
        if (json == null) {
            return null;
        }
        for (String name : names) {
            final JSONArray value = json.optJSONArray(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static JSONObject firstObject(final JSONObject json, final String... names) {
        if (json == null) {
            return null;
        }
        for (String name : names) {
            final JSONObject value = json.optJSONObject(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(final JSONObject json, final String... names) {
        if (json == null) {
            return null;
        }
        for (String name : names) {
            if (!json.has(name) || json.isNull(name)) {
                continue;
            }
            final String value = json.optString(name, null);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static int firstPositiveInt(final JSONObject json, final String... names) {
        if (json == null) {
            return -1;
        }
        for (String name : names) {
            final int value = json.optInt(name, -1);
            if (value > 0) {
                return value;
            }
        }
        return -1;
    }

    private static String positiveId(final JSONObject json, final String... names) {
        final String value = firstText(json, names);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value) > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String positiveText(final int value) {
        return value > 0 ? String.valueOf(value) : null;
    }

    private static String[] flattenMap(final JSONObject json) {
        if (json == null) {
            return new String[0];
        }
        final ArrayList<String> values = new ArrayList<>();
        final Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final String value = json.optString(key, null);
            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                values.add(key);
                values.add(value);
            }
        }
        return values.toArray(new String[0]);
    }

    private static String[][] stringMap(final JSONObject json) {
        if (json == null) {
            return new String[][]{new String[0], new String[0]};
        }
        final ArrayList<String> labels = new ArrayList<>();
        final ArrayList<String> urls = new ArrayList<>();
        final Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            final String label = keys.next();
            final String url = json.optString(label, null);
            if (label != null && !label.isEmpty() && url != null && Uri.parse(url).getScheme() != null) {
                labels.add(label);
                urls.add(url);
            }
        }
        return new String[][]{labels.toArray(new String[0]), urls.toArray(new String[0])};
    }
}
