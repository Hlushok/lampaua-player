package com.brouken.player;

import java.util.Objects;

/** Stable title and episode identity used by online subtitle lookups. */
final class MediaId {
    final String imdb;
    final String tmdb;
    final int season;
    final int episode;

    MediaId(String imdb, String tmdb, int season, int episode) {
        this.imdb = blankToNull(imdb);
        this.tmdb = blankToNull(tmdb);
        this.season = season;
        this.episode = episode;
    }

    boolean isEmpty() {
        return imdb == null && tmdb == null;
    }

    boolean isMovie() {
        return season < 1;
    }

    String imdbNumeric() {
        if (imdb == null) return null;
        String digits = imdb.startsWith("tt") ? imdb.substring(2) : imdb;
        return digits.isEmpty() ? null : digits;
    }

    boolean sameAs(MediaId other) {
        return other != null && Objects.equals(imdb, other.imdb)
                && Objects.equals(tmdb, other.tmdb)
                && season == other.season && episode == other.episode;
    }

    String key() {
        return String.valueOf(imdb) + "|" + tmdb + "|" + season + "|" + episode;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
