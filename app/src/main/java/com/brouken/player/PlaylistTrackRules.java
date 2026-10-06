package com.brouken.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic matching shared by voice and in-stream track selection. */
final class PlaylistTrackRules {
    private static final Map<String, String> LANGUAGES = new HashMap<>();
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Map<String, String> STUDIOS = new LinkedHashMap<>();
    private static final Set<String> NOISE = new HashSet<>(Arrays.asList(
            "ac3", "eac3", "aac", "dts", "truehd", "flac", "mp3", "opus", "pcm", "dd", "ddp",
            "stereo", "mono", "surround", "audio", "track", "доріжка", "дорожка", "ukr", "rus", "eng",
            "ukrainian", "english", "russian", "українська", "русский", "английский",
            "mvo", "dvo", "svo", "vo", "dub", "dubbing", "дубляж", "озвучення", "озвучка"));
    static {
        for (String iso2 : Locale.getISOLanguages()) {
            Locale locale = new Locale(iso2);
            try {
                String iso3 = locale.getISO3Language();
                LANGUAGES.put(iso2, iso3); LANGUAGES.put(iso3, iso3);
                NAMES.put(locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase(Locale.ROOT), iso3);
                NAMES.put(locale.getDisplayLanguage(new Locale("uk")).toLowerCase(Locale.ROOT), iso3);
                NAMES.put(locale.getDisplayLanguage(new Locale("ru")).toLowerCase(Locale.ROOT), iso3);
            } catch (java.util.MissingResourceException ignored) { }
        }
        String[] bibliographic = {"alb:sqi", "arm:hye", "baq:eus", "bur:mya", "chi:zho", "cze:ces",
                "dut:nld", "fre:fra", "geo:kat", "ger:deu", "gre:ell", "ice:isl", "mac:mkd",
                "mao:mri", "may:msa", "per:fas", "rum:ron", "slo:slk", "tib:bod", "wel:cym"};
        for (String pair : bibliographic) { String[] parts = pair.split(":"); LANGUAGES.put(parts[0], parts[1]); }
        alias("lostfilm", "lost film"); alias("hdrezka", "hdrezka studio", "rezka", "rezkastudio", "hdrezkastudio");
        alias("newstudio", "new studio"); alias("voiceproject", "voice project");
        alias("baibako", "байбако"); alias("alexfilm", "alex film"); alias("kubikvkube", "кубик в кубе");
        alias("dubbingpro", "dubbing pro"); alias("varusvideo", "varus video");
    }
    private static void alias(String canonical, String... variants) {
        STUDIOS.put(canonical, canonical);
        for (String value : variants) STUDIOS.put(value, canonical);
    }
    static String language(String raw) {
        if (raw == null) return null;
        String tag = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int separator = tag.indexOf('-');
        return LANGUAGES.get(separator < 0 ? tag : tag.substring(0, separator));
    }
    static String inferredLanguage(String raw, String label) {
        String language = language(raw);
        if (language != null) return language;
        if (label != null) for (String word : words(label)) {
            language = language(word);
            if (language == null) language = NAMES.get(word);
            if (language != null) return language;
        }
        return null;
    }
    static boolean labelsMatch(String left, String right) {
        if (left == null || right == null) return false;
        if (!kind(left).equals(kind(right))) return false;
        Set<String> a = words(left), b = words(right);
        String studioA = studio(a), studioB = studio(b);
        if (studioA != null && studioB != null) return studioA.equals(studioB);
        a.removeAll(NOISE); b.removeAll(NOISE);
        removeNumericNoise(a);
        removeNumericNoise(b);
        if (a.isEmpty() || b.isEmpty()) {
            String dubA = dubKind(left), dubB = dubKind(right);
            return !dubA.isEmpty() && dubA.equals(dubB) || !kind(left).isEmpty() && kind(left).equals(kind(right));
        }
        return a.containsAll(b) || b.containsAll(a)
                || join("", a).equals(join("", b));
    }
    private static void removeNumericNoise(Set<String> words) {
        java.util.Iterator<String> iterator = words.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().matches("\\d+|\\d+k(?:bps)?|\\d+p")) iterator.remove();
        }
    }
    private static String studio(Set<String> words) {
        String all = " " + join(" ", words) + " ";
        for (Map.Entry<String, String> entry : STUDIOS.entrySet()) {
            if (all.contains(" " + entry.getKey() + " ")) return entry.getValue();
        }
        return null;
    }
    static String join(String separator, Iterable<String> values) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String value : values) { if (!first) result.append(separator); result.append(value); first = false; }
        return result.toString();
    }
    private static Set<String> words(String value) {
        return new java.util.LinkedHashSet<>(Arrays.asList(value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split("\\s+")));
    }
    private static String kind(String label) {
        Set<String> words = words(label);
        StringBuilder result = new StringBuilder();
        if (words.contains("forced") || words.contains("форсовані") || words.contains("форсированные")) result.append("forced;");
        if (words.contains("sdh") || words.contains("hearing")) result.append("sdh;");
        if (words.contains("commentary") || words.contains("коментарі") || words.contains("комментарии")) result.append("commentary;");
        if (label.matches(".*(?i)18\\+.*")) result.append("18+;");
        return result.toString();
    }

    private static String dubKind(String label) {
        Set<String> words = words(label);
        if (words.contains("dub") || words.contains("dubbing") || words.contains("дубляж")) return "dub";
        for (String kind : Arrays.asList("mvo", "dvo", "svo", "vo")) if (words.contains(kind)) return kind;
        return "";
    }

    static final class Track {
        final int index;
        final String language, declaredLanguage, label;
        final boolean supported, selected, externalSelected, commentary, limited;
        Track(int index, String language, String label, boolean supported, boolean selected,
              boolean externalSelected, boolean commentary, boolean limited) {
            this.index = index; this.declaredLanguage = language;
            this.language = inferredLanguage(language, label); this.label = label;
            this.supported = supported; this.selected = selected; this.externalSelected = externalSelected;
            this.commentary = commentary; this.limited = limited;
        }
    }
    static final class Pick {
        final Track track;
        final String by;
        final boolean off;
        Pick(Track track, String by) { this.track = track; this.by = by; off = track == null; }
    }
    static Pick choose(List<Track> tracks, PlaylistApi.Keys item, PlaylistApi.Keys root,
                       PlaylistApi.Keys viewer, PlaylistApi.Keys remembered, boolean subtitle) {
        Pick pick;
        if (viewer != null && (pick = explicit(tracks, viewer, subtitle, "viewer")) != null) return pick;
        if ((pick = index(tracks, item, subtitle, "index")) != null) return pick;
        if (subtitle) for (Track track : tracks) {
            if (track.externalSelected && track.supported) return new Pick(track, "selected");
        }
        if ((pick = label(tracks, item, "label")) != null) return pick;
        if ((pick = ordinal(tracks, item, "language_ordinal")) != null) return pick;
        if ((pick = explicit(tracks, root, subtitle, null)) != null) return pick;
        if (!subtitle && remembered != null && (pick = explicit(tracks, remembered, false, "remembered")) != null) return pick;
        List<String> languages = item.languages != null ? item.languages : root.languages;
        boolean memoryLanguage = !subtitle && remembered != null && remembered.languages != null;
        if (memoryLanguage) languages = remembered.languages;
        if (languages != null) for (String code : languages) {
            Track first = null, playing = null;
            for (Track track : tracks) if (track.supported && code.equals(track.language)) {
                if (first == null || (subtitle ? first.limited && !track.limited : first.commentary && !track.commentary)) first = track;
                if (track.selected) playing = track;
            }
            if (first != null) return new Pick(!subtitle && playing != null && !playing.commentary ? playing : first,
                    memoryLanguage ? "remembered" : "languages");
        }
        for (Track track : tracks) if (track.supported && track.selected) return new Pick(track, "player");
        return subtitle ? new Pick(null, "player") : null;
    }
    private static Pick explicit(List<Track> tracks, PlaylistApi.Keys keys, boolean subtitle, String by) {
        Pick pick = index(tracks, keys, subtitle, by == null ? "index" : by);
        if (pick == null) pick = label(tracks, keys, by == null ? "label" : by);
        if (pick == null) pick = ordinal(tracks, keys, by == null ? "language_ordinal" : by);
        return pick;
    }
    private static Pick index(List<Track> tracks, PlaylistApi.Keys keys, boolean subtitle, String by) {
        if (subtitle && keys.off) return new Pick(null, keys.index == null && !"viewer".equals(by) ? "languages" : by);
        if (keys.index != null) for (Track track : tracks) {
            if (track.index == keys.index && track.supported
                    && (keys.label == null || labelsMatch(keys.label, track.label))) return new Pick(track, by);
        }
        return null;
    }
    private static Pick label(List<Track> tracks, PlaylistApi.Keys keys, String by) {
        if (keys.label == null) return null;
        List<String> languages = keys.languages == null ? Collections.singletonList(null) : keys.languages;
        for (String language : languages) for (Track track : tracks) {
            if (track.supported && (language == null || language.equals(track.language))
                    && labelsMatch(keys.label, track.label)) return new Pick(track, by);
        }
        return null;
    }
    private static Pick ordinal(List<Track> tracks, PlaylistApi.Keys keys, String by) {
        if (keys.ordinal == null || keys.languages == null || keys.languages.isEmpty()) return null;
        List<Track> same = new ArrayList<>();
        for (Track track : tracks) if (keys.languages.get(0).equals(track.language)) same.add(track);
        if (keys.count != null && keys.count != same.size()) return null;
        if (keys.ordinal >= same.size() || !same.get(keys.ordinal).supported) return null;
        return new Pick(same.get(keys.ordinal), by);
    }
}
