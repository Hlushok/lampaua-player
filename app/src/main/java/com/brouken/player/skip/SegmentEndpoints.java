package com.brouken.player.skip;

/**
 * Public skip-service endpoints and the public TMDB read key used for title lookup.
 * Adapted from Just+ Player v1.3.0 (Unlicense).
 */
public final class SegmentEndpoints {

    private SegmentEndpoints() {}

    static final String SKIPDB = "https://api.skipdb.tv/api/segments";
    static final String SKIPME = "https://db.skipme.workers.dev/v1/movies";
    static final String SKIPME_UA = "SkipMe.db/0.0";
    static final String INTROHATER = "https://introhater.com/api/v1/segments/";
    static final String INTROHATER_KEY = "introhater_mpv_client";
    static final String INTRODB = "https://api.introdb.app/segments";
    static final String ARM = "https://arm.haglund.dev/api/v2/imdb";
    static final String ANISKIP = "https://api.aniskip.com/v2/skip-times";
    static final String THEINTRODB = "https://api.theintrodb.org/v3/media";
    static final String TMDB_FIND = "https://api.themoviedb.org/3/find/";
    public static final String TMDB_BASE = "https://api.themoviedb.org/3";
    public static final String TMDB_KEY = "875965c1ae50e299f1c13c8c00c54af8";
}
