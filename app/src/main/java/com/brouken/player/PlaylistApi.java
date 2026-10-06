package com.brouken.player;

import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed external playlist contract. Playback remains owned by the regular player. */
final class PlaylistApi {
    static final String EXTRA = "playlist";

    static final class Invalid extends Exception {
        Invalid(String message) { super(message); }
    }

    static final class Keys {
        Integer index, ordinal, count;
        String label;
        List<String> languages;
        boolean off;
    }

    static final class Subtitle {
        String uri, label, language, mime;
        boolean selected;
    }

    static final class Source {
        String uri, label;
        boolean selected;
        final LinkedHashMap<String, String> qualities = new LinkedHashMap<>();
        List<Subtitle> subtitles;
    }

    static final class Item {
        String title, episodeTitle, logo, background, thumbnail, imdb, tmdb, segments;
        Integer season, episode;
        long positionMs;
        double clipStart, clipEnd = -1;
        Keys audio, text;
        Map<String, String> headers;
        Source source;
        final List<Source> voices = new ArrayList<>();
        int voiceIndex;
        List<Subtitle> subtitles;

        Source currentSource() { return voices.isEmpty() ? source : voices.get(voiceIndex); }
        List<Subtitle> currentSubtitles() {
            Source value = currentSource();
            return value.subtitles != null ? value.subtitles : subtitles;
        }
        String titleKey() { return imdb != null ? "imdb:" + imdb : tmdb != null ? "tmdb:" + tmdb : null; }
        String episodeLine() {
            final List<String> parts = new ArrayList<>();
            if (season != null && season > 0) parts.add("S" + season);
            if (episode != null && episode > 0) parts.add("E" + episode);
            if (episodeTitle != null && !episodeTitle.matches("(?iu)(episode|серія|эпизод|серия|e)\\s*0*\\d+")) {
                parts.add(episodeTitle);
            }
            return PlaylistTrackRules.join(" · ", parts);
        }
        MediaItem mediaItem(Context context) {
            Source selected = currentSource();
            MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                    .setTitle(title).setDisplayTitle(episodeLine().isEmpty() ? title : episodeLine());
            if (thumbnail != null) metadata.setArtworkUri(Uri.parse(thumbnail));
            List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
            for (Subtitle subtitle : currentSubtitles()) {
                MediaItem.SubtitleConfiguration config = SubtitleUtils.buildSubtitle(context,
                        Uri.parse(subtitle.uri), subtitle.label, subtitle.language, subtitle.selected);
                if (subtitle.mime != null) config = config.buildUpon().setMimeType(subtitle.mime).build();
                configs.add(config);
            }
            MediaItem.Builder media = new MediaItem.Builder().setUri(selected.uri)
                    .setMediaMetadata(metadata.build()).setSubtitleConfigurations(configs);
            if (clipStart > 0 || clipEnd > clipStart) {
                MediaItem.ClippingConfiguration.Builder clip = new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(Math.round(clipStart * 1000));
                if (clipEnd > clipStart) clip.setEndPositionMs(Math.round(clipEnd * 1000));
                media.setClippingConfiguration(clip.build());
            }
            return media.build();
        }
    }

    final Bundle original;
    final List<Item> items = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    final Keys audio, text;
    final PendingIntent callback;
    final int startIndex, reportInterval;

    PlaylistApi(Bundle bundle) throws Invalid {
        original = new Bundle(bundle);
        audio = keys(bundle, "audio", "playlist");
        text = keys(bundle, "subtitle", "playlist");
        Object pending = bundle.get("result_callback");
        callback = pending instanceof PendingIntent ? (PendingIntent) pending : null;
        int interval = number(bundle, "report_interval_sec", 0);
        reportInterval = interval <= 0 ? 0 : Math.max(30, interval);
        Object[] values = array(bundle.get("items"));
        if (values == null || values.length == 0) throw new Invalid("playlist has no items");
        final Map<String, String> commonHeaders = headers(bundle.get("headers"));
        for (int i = 0; i < values.length; i++) {
            final String path = "items[" + i + "]";
            if (!(values[i] instanceof Bundle)) throw new Invalid(path + " is not a Bundle");
            Bundle value = (Bundle) values[i];
            Item item = new Item();
            item.audio = keys(value, "audio", path);
            item.text = keys(value, "subtitle", path);
            item.title = fallback(string(value, "title"), string(bundle, "title"));
            item.episodeTitle = string(value, "episode_title");
            item.logo = fallback(string(value, "logo"), string(bundle, "logo"));
            item.background = fallback(string(value, "background"), string(bundle, "background"));
            item.thumbnail = string(value, "thumbnail");
            item.imdb = string(value, "imdb_id");
            item.tmdb = string(value, "tmdb_id");
            item.segments = string(value, "segments");
            item.season = integer(value.get("season"), false);
            item.episode = integer(value.get("episode"), false);
            item.clipStart = Math.max(0, decimal(value.get("clip_start_sec"), 0));
            item.clipEnd = decimal(value.get("clip_end_sec"), -1);
            item.positionMs = Math.max(0, (long) number(value, "position_sec", 0) * 1000L);
            if (item.clipEnd > item.clipStart) {
                item.positionMs = Math.min(item.positionMs, Math.round((item.clipEnd - item.clipStart) * 1000));
            }
            item.headers = new LinkedHashMap<>(commonHeaders);
            item.headers.putAll(headers(value.get("headers")));
            boolean numbered = item.text.index != null && item.text.index >= 0
                    || text.index != null && text.index >= 0;
            item.subtitles = subtitles(value.get("subtitles"), path, numbered);
            Object rawVoices = value.get("voices");
            if (rawVoices != null) {
                if (string(value, "uri") != null || value.containsKey("qualities")) {
                    throw new Invalid(path + " has both voices and uri or qualities");
                }
                Object[] voices = array(rawVoices);
                if (voices == null || voices.length == 0) throw new Invalid(path + " has neither uri nor qualities");
                boolean picked = false;
                for (int j = 0; j < voices.length; j++) {
                    String voicePath = path + " voices[" + j + "]";
                    if (!(voices[j] instanceof Bundle)) throw new Invalid(voicePath + " is not a Bundle");
                    Bundle voice = (Bundle) voices[j];
                    Source source = source(voice);
                    if (source.label == null) throw new Invalid(voicePath + " has no label");
                    if (source.uri == null) throw new Invalid(voicePath + " has neither uri nor qualities");
                    if (voice.containsKey("subtitles")) source.subtitles = subtitles(voice.get("subtitles"), voicePath, numbered);
                    if (source.selected && !picked) { item.voiceIndex = j; picked = true; }
                    item.voices.add(source);
                }
            } else {
                item.source = source(value);
                if (item.source.uri == null) throw new Invalid(path + " has neither uri nor qualities");
            }
            if (item.title == null) item.title = Uri.parse(item.currentSource().uri).getLastPathSegment();
            items.add(item);
        }
        startIndex = number(bundle, "start_index", 0);
        if (startIndex < 0 || startIndex >= items.size()) {
            throw new Invalid("start_index " + startIndex + " is out of range 0.." + (items.size() - 1));
        }
    }

    boolean suppressSubtitleDiscovery(int index) {
        Keys item = items.get(index).text;
        return item.index != null || item.off || text.index != null || text.off;
    }

    private Source source(Bundle value) {
        Source source = new Source();
        source.label = string(value, "label");
        source.selected = bool(value.get("selected"));
        String explicit = string(value, "uri");
        Object[] variants = array(value.get("qualities"));
        String selected = null;
        if (variants != null) {
            for (Object raw : variants) {
                if (!(raw instanceof Bundle)) continue;
                Bundle quality = (Bundle) raw;
                String label = string(quality, "label"), uri = string(quality, "uri");
                if (label == null || uri == null) continue;
                source.qualities.put(label, uri);
                if (selected == null && bool(quality.get("selected"))) selected = uri;
            }
        }
        source.uri = selected != null ? selected : explicit != null ? explicit
                : source.qualities.isEmpty() ? null : source.qualities.values().iterator().next();
        return source;
    }

    private List<Subtitle> subtitles(Object raw, String path, boolean numbered) throws Invalid {
        Object[] values = array(raw);
        List<Subtitle> result = new ArrayList<>();
        if (values == null) return result;
        for (int i = 0; i < values.length; i++) {
            String entryPath = path + " subtitles[" + i + "]";
            Bundle bundle = values[i] instanceof Bundle ? (Bundle) values[i] : null;
            String uri = bundle == null ? null : string(bundle, "uri");
            if (uri == null) {
                String reason = bundle == null ? "is not a Bundle" : "has no uri";
                if (numbered) throw new Invalid(entryPath + " " + reason + ", and subtitle_index counts on it");
                warnings.add(entryPath + " " + reason + "; skipped");
                continue;
            }
            Subtitle subtitle = new Subtitle();
            subtitle.uri = uri;
            subtitle.label = string(bundle, "label");
            subtitle.language = string(bundle, "language");
            subtitle.mime = string(bundle, "mime");
            subtitle.selected = bool(bundle.get("selected"));
            result.add(subtitle);
        }
        return result;
    }

    private Keys keys(Bundle bundle, String prefix, String path) {
        Keys keys = new Keys();
        keys.index = trackNumber(bundle, prefix + "_index", path, "subtitle".equals(prefix) ? -1 : 0);
        keys.ordinal = trackNumber(bundle, prefix + "_language_ordinal", path, 0);
        keys.count = trackNumber(bundle, prefix + "_language_count", path, 1);
        Object label = bundle.get(prefix + "_label");
        if (label instanceof String) keys.label = trimmed((String) label);
        else if (label != null) warnings.add(path + "." + prefix + "_label: not text");
        String plural = prefix + "_languages", singular = prefix + "_language";
        Object languages = bundle.get(plural);
        if (languages != null && bundle.containsKey(singular)) {
            warnings.add(path + "." + singular + ": ignored: " + plural + " is given");
        }
        if (languages == null) languages = bundle.get(singular);
        List<String> raw = strings(languages);
        if (languages instanceof String) raw = new ArrayList<>();
        if (languages instanceof String && trimmed((String) languages) != null) {
            Collections.addAll(raw, ((String) languages).trim().split("[,\\s]+"));
        }
        if (raw != null) {
            List<String> normalized = new ArrayList<>();
            for (String language : raw) {
                if (trimmed(language) == null) continue;
                String code = PlaylistTrackRules.language(language);
                if (code == null) warnings.add(path + "." + plural + ": invalid language " + language);
                else if (!normalized.contains(code)) normalized.add(code);
            }
            if (!normalized.isEmpty()) keys.languages = normalized;
            else if (raw.isEmpty() && !(languages instanceof String)) {
                if ("subtitle".equals(prefix)) { keys.languages = normalized; keys.off = true; }
            }
        } else if (languages != null) warnings.add(path + "." + plural + ": invalid type");
        if (keys.ordinal != null && (keys.languages == null || keys.languages.isEmpty())) {
            warnings.add(path + "." + prefix + "_language_ordinal: ignored: no language to count in");
            keys.ordinal = null;
        }
        keys.off |= "subtitle".equals(prefix) && Integer.valueOf(-1).equals(keys.index);
        return keys;
    }

    private Integer trackNumber(Bundle bundle, String key, String path, int minimum) {
        Object raw = bundle.get(key);
        Integer value = integer(raw, true);
        if (value != null && value >= minimum) return value;
        if (raw != null && !(raw instanceof String && trimmed((String) raw) == null)) {
            warnings.add(path + "." + key + ": not a valid whole number");
        }
        return null;
    }

    static Integer integer(Object value, boolean strict) {
        double number = decimal(value, Double.NaN);
        if (Double.isNaN(number) || Double.isInfinite(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE
                || strict && number != Math.rint(number)) return null;
        return (int) Math.floor(number);
    }
    static double decimal(Object value, double fallback) {
        if (!(value instanceof Number) && !(value instanceof String)) return fallback;
        try {
            double number = Double.parseDouble(value.toString().trim());
            return !Double.isNaN(number) && !Double.isInfinite(number) ? number : fallback;
        } catch (NumberFormatException ignored) { return fallback; }
    }
    private static int number(Bundle bundle, String key, int fallback) {
        Integer value = integer(bundle.get(key), false);
        return value == null ? fallback : value;
    }
    private static boolean bool(Object value) { return Boolean.TRUE.equals(value) || "true".equals(value); }
    static String string(Bundle bundle, String key) {
        Object value = bundle.get(key);
        return value == null ? null : trimmed(value.toString());
    }
    static String trimmed(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    static String fallback(String value, String fallback) { return value != null ? value : fallback; }
    private static Object[] array(Object raw) {
        if (raw instanceof Parcelable[]) return (Parcelable[]) raw;
        if (raw instanceof ArrayList<?>) return ((ArrayList<?>) raw).toArray();
        return null;
    }
    private static List<String> strings(Object raw) {
        if (raw instanceof String[]) {
            List<String> result = new ArrayList<>(); Collections.addAll(result, (String[]) raw); return result;
        }
        if (raw instanceof ArrayList<?>) {
            List<String> result = new ArrayList<>();
            for (Object value : (ArrayList<?>) raw) {
                if (value != null && !(value instanceof String)) return null;
                result.add((String) value);
            }
            return result;
        }
        return null;
    }
    private static Map<String, String> headers(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> pairs = strings(raw);
        if (pairs != null) for (int i = 0; i + 1 < pairs.size(); i += 2) {
            String name = pairs.get(i), value = pairs.get(i + 1);
            if (name != null && value != null) result.put(name.toLowerCase(java.util.Locale.ROOT), value);
        }
        return result;
    }
}
