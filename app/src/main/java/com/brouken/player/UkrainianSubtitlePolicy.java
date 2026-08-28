package com.brouken.player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;

/** Fixed Ukrainian-first policy for direct and translated online subtitles. */
final class UkrainianSubtitlePolicy {
    static final String SEARCH_LANGUAGE = "ukr";
    static final String TARGET_ISO2 = "uk";
    private static final String[] FALLBACKS = {"eng", "rus", "pol"};

    private UkrainianSubtitlePolicy() { }

    static boolean enabled(boolean preference, List<String> preferred) {
        return preference;
    }

    static List<String> directLanguages(List<String> preferred) {
        return Collections.singletonList(SEARCH_LANGUAGE);
    }

    static List<String> fallbackLanguages(List<String> preferred) {
        Set<String> ordered = normalized(preferred);
        ordered.remove(SEARCH_LANGUAGE);
        Collections.addAll(ordered, FALLBACKS);
        return new ArrayList<>(ordered);
    }

    static List<String> playbackLanguages(boolean searchEnabled, boolean translationEnabled,
                                          List<String> preferred) {
        List<String> ordered = new ArrayList<>(normalized(preferred));
        if (searchEnabled && translationEnabled) {
            ordered.remove(SEARCH_LANGUAGE);
            ordered.add(0, SEARCH_LANGUAGE);
        }
        return ordered;
    }

    static String targetIso2() {
        return TARGET_ISO2;
    }

    static String targetIso3() {
        return SEARCH_LANGUAGE;
    }

    static String sourceIso2(String sourceIso3) {
        String source = normalizeIso3(sourceIso3);
        if (source == null) return null;
        for (String iso2 : Locale.getISOLanguages()) {
            try {
                if (source.equals(new Locale(iso2).getISO3Language())) return iso2;
            } catch (MissingResourceException ignored) {
                // Ignore incomplete locale tables on older Android versions.
            }
        }
        return null;
    }

    static String translatedCacheName(String cachePrefix, String sourceIso3) {
        String source = normalizeIso3(sourceIso3);
        if (source == null || SEARCH_LANGUAGE.equals(source)
                || cachePrefix == null || !cachePrefix.matches("[A-Za-z0-9._-]+")
                || cachePrefix.contains("..")) {
            return null;
        }
        return cachePrefix + ".auto-ukr." + source + ".srt";
    }

    static boolean isDirectUkrainianCache(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".ukr.srt") && !name.contains(".auto-ukr.");
    }

    private static LinkedHashSet<String> normalized(List<String> languages) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (languages == null) return result;
        for (String language : languages) {
            String normalized = normalizeIso3(language);
            if (normalized != null) result.add(normalized);
        }
        return result;
    }

    static String normalizeIso3(String language) {
        if (language == null) return null;
        String normalized = language.trim().toLowerCase(Locale.US);
        return normalized.matches("[a-z]{3}") ? normalized : null;
    }
}
