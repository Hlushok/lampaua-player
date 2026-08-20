package com.brouken.player;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Minimal client for the OpenSubtitles.com REST API. */
final class OpenSubtitles {
    private OpenSubtitles() { }

    private static final String API = "https://api.opensubtitles.com/api/v1";
    // Public consumer key carried by the upstream Just+ implementation.
    private static final String KEY = "IxrxupVBKx7dhBkAAtW7QbwnhDMgOdEO";
    private static final String UA = "UA-Player/" + BuildConfig.VERSION_NAME;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int TIMEOUT_SEC = 10;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    static final class Candidate {
        final String language;
        final long fileId;
        final int downloads;
        final String release;

        Candidate(String language, long fileId, int downloads, String release) {
            this.language = language;
            this.fileId = fileId;
            this.downloads = downloads;
            this.release = release;
        }
    }

    static List<Candidate> search(MediaId id, List<String> languages) {
        return search(id, languages, null);
    }

    static List<Candidate> search(MediaId id, List<String> languages,
                                  AtomicBoolean answered) {
        if (id == null || id.isEmpty() || languages.isEmpty()) {
            return Collections.emptyList();
        }
        TreeMap<String, String> params = new TreeMap<>();
        params.put("languages", TextUtils.join(",", languages));
        if (id.imdbNumeric() != null) {
            params.put("imdb_id", id.imdbNumeric());
        } else {
            params.put("tmdb_id", id.tmdb);
        }
        if (!id.isMovie()) {
            params.put("season_number", String.valueOf(id.season));
            if (id.episode > 0) params.put("episode_number", String.valueOf(id.episode));
        }

        StringBuilder url = new StringBuilder(API).append("/subtitles?");
        boolean first = true;
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (!first) url.append('&');
            first = false;
            url.append(param.getKey()).append('=')
                    .append(Uri.encode(param.getValue(), ","));
        }
        String body = get(url.toString(), answered);
        if (body == null) return Collections.emptyList();

        List<Candidate> candidates = new ArrayList<>();
        try {
            JSONArray data = new JSONObject(body).optJSONArray("data");
            if (data == null) return candidates;
            for (int i = 0; i < data.length(); i++) {
                JSONObject attributes = data.getJSONObject(i).optJSONObject("attributes");
                if (attributes == null || attributes.optBoolean("foreign_parts_only")
                        || attributes.optBoolean("machine_translated")
                        || attributes.optBoolean("ai_translated")) {
                    continue;
                }
                JSONArray files = attributes.optJSONArray("files");
                if (files == null || files.length() == 0) continue;
                long fileId = files.getJSONObject(0).optLong("file_id", -1);
                if (fileId < 0) continue;
                int downloads = attributes.optInt("new_download_count",
                        attributes.optInt("download_count"));
                candidates.add(new Candidate(attributes.optString("language"), fileId,
                        downloads, attributes.optString("release")));
            }
        } catch (Exception e) {
            Utils.log("OpenSubtitles: " + e);
            return Collections.emptyList();
        }
        return candidates;
    }

    static Candidate pick(List<Candidate> candidates, List<String> languages) {
        Candidate best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Candidate candidate : candidates) {
            if (candidate.language == null) continue;
            int rank = languages.indexOf(candidate.language.toLowerCase(Locale.US));
            if (rank < 0) continue;
            if (rank < bestRank || (rank == bestRank
                    && best != null && candidate.downloads > best.downloads)) {
                best = candidate;
                bestRank = rank;
            }
        }
        return best;
    }

    static String link(long fileId) {
        Request request = auth(new Request.Builder()
                .url(API + "/download")
                .post(RequestBody.create("{\"file_id\":" + fileId + "}", JSON))).build();
        String body = execute(request, null);
        if (body == null) return null;
        try {
            JSONObject json = new JSONObject(body);
            String link = json.optString("link", null);
            if (link != null) {
                Utils.log("OpenSubtitles: " + json.optInt("remaining", -1)
                        + " downloads remaining");
            }
            return link;
        } catch (Exception e) {
            Utils.log("OpenSubtitles: " + e);
            return null;
        }
    }

    static List<String> toIso639_1(List<String> languages) {
        List<String> result = new ArrayList<>();
        for (String language : languages) {
            String two = twoLetter(language);
            if (two != null && !result.contains(two)) result.add(two);
        }
        return result;
    }

    private static String twoLetter(String language) {
        if (language == null || language.isEmpty()) return null;
        String lower = language.toLowerCase(Locale.US);
        if (lower.length() == 2) return lower;
        try {
            for (String code : Locale.getISOLanguages()) {
                if (lower.equals(new Locale(code).getISO3Language())) return code;
            }
        } catch (MissingResourceException ignored) {
            return null;
        }
        return null;
    }

    private static String get(String url, AtomicBoolean answered) {
        return execute(auth(new Request.Builder().url(url)).build(), answered);
    }

    private static Request.Builder auth(Request.Builder builder) {
        return builder.header("Api-Key", KEY)
                .header("User-Agent", UA)
                .header("Accept", "application/json");
    }

    private static String execute(Request request, AtomicBoolean answered) {
        try (Response response = CLIENT.newCall(request).execute()) {
            if (answered != null) answered.set(true);
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                Utils.log("OpenSubtitles: " + response.code() + " "
                        + request.url().encodedPath());
                return null;
            }
            return body.string();
        } catch (IOException e) {
            Utils.log("OpenSubtitles: " + e);
            return null;
        }
    }
}
