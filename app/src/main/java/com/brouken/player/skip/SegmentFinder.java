package com.brouken.player.skip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Shared IMDb/TMDB identity resolver used by subtitle and skip discovery. */
public final class SegmentFinder {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private SegmentFinder() {}

    public static long tmdbFind(String imdbId, boolean isMovie) {
        HttpUrl base = HttpUrl.parse(SegmentEndpoints.TMDB_FIND);
        if (base == null || imdbId == null || imdbId.isEmpty()) return -1;
        HttpUrl url = base.newBuilder()
                .addPathSegment(imdbId)
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY)
                .addQueryParameter("external_source", "imdb_id")
                .build();
        JSONObject root = getJson(url);
        if (root == null) return -1;
        JSONArray results = root.optJSONArray(isMovie ? "movie_results" : "tv_results");
        if (results == null || results.length() == 0) return -1;
        JSONObject first = results.optJSONObject(0);
        return first == null ? -1 : first.optLong("id", -1);
    }

    public static String tmdbExternalImdb(long tmdbId, boolean isMovie) {
        HttpUrl base = HttpUrl.parse(SegmentEndpoints.TMDB_BASE);
        if (base == null || tmdbId < 0) return null;
        HttpUrl url = base.newBuilder()
                .addPathSegment(isMovie ? "movie" : "tv")
                .addPathSegment(String.valueOf(tmdbId))
                .addPathSegment("external_ids")
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY)
                .build();
        JSONObject root = getJson(url);
        String imdb = root == null ? null : root.optString("imdb_id", null);
        return imdb == null || imdb.isEmpty() ? null : imdb;
    }

    private static JSONObject getJson(HttpUrl url) {
        Request request = new Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "UA-Player")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) return null;
            return new JSONObject(body.string());
        } catch (Exception error) {
            return null;
        }
    }
}
