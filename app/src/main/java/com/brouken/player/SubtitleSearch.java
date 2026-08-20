package com.brouken.player;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Searches the subtitle sources selected by the user until one delivers a usable file. */
final class SubtitleSearch {
    private SubtitleSearch() { }

    static final String SOURCE_OPENSUBTITLES = "openSubtitles";
    static final String SOURCE_SHEGU = "shegu";
    static final String SOURCE_STREMIO = "stremio";
    static final String SOURCE_REST = "restOpenSubtitles";

    private static final String UA = "UA-Player/" + BuildConfig.VERSION_NAME;
    private static final int TIMEOUT_SEC = 10;
    private static final int PARALLEL_TIMEOUT_SEC = 15;
    private static final int MAX_URLS = 3;

    static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    static final class Result {
        final String source;
        final String language;
        final List<String> urls;

        Result(String source, String language, List<String> urls) {
            this.source = source;
            this.language = language;
            this.urls = urls;
        }
    }

    private static final class Candidate {
        final String language;
        final int downloads;
        final String url;

        Candidate(String language, int downloads, String url) {
            this.language = language;
            this.downloads = downloads;
            this.url = url;
        }
    }

    interface Sink {
        boolean accept(Result result);
    }

    static Result find(MediaId id, List<String> preferred, Prefs prefs, Sink sink,
                       AtomicBoolean answered) {
        if (id == null || id.isEmpty() || preferred.isEmpty()) return null;
        if (!id.isMovie() && id.episode < 1) {
            Utils.log("subtitles: no episode number, not searching");
            return null;
        }

        List<Callable<Result>> keyless = new ArrayList<>(3);
        if (prefs.subtitleSourceRest) {
            keyless.add(() -> best(SOURCE_REST,
                    restOpenSubtitles(id, preferred, answered), preferred));
        }
        if (prefs.subtitleSourceStremio) {
            keyless.add(() -> best(SOURCE_STREMIO, stremio(id, answered), preferred));
        }
        if (prefs.subtitleSourceShegu) {
            keyless.add(() -> best(SOURCE_SHEGU, shegu(id, answered), preferred));
        }
        for (Result result : inParallel(keyless)) {
            if (delivered(result, sink)) return result;
        }
        if (!cancelled() && prefs.subtitleSourceOpenSubtitles) {
            Result result = fromOpenSubtitles(id, preferred, answered);
            if (delivered(result, sink)) return result;
        }
        return null;
    }

    private static List<Result> inParallel(List<Callable<Result>> tasks) {
        List<Result> results = new ArrayList<>(tasks.size());
        if (tasks.isEmpty()) return results;
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            Thread thread = new Thread(runnable, "SubtitleSource");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (Future<Result> future
                    : pool.invokeAll(tasks, PARALLEL_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                try {
                    Result result = future.isCancelled() ? null : future.get();
                    if (result != null) results.add(result);
                } catch (Exception e) {
                    Utils.log("subtitles: source failed " + e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    private static boolean cancelled() {
        return Thread.currentThread().isInterrupted();
    }

    private static boolean delivered(Result result, Sink sink) {
        return result != null && !cancelled() && sink.accept(result);
    }

    private static Result fromOpenSubtitles(MediaId id, List<String> preferred,
                                             AtomicBoolean answered) {
        List<String> codes = OpenSubtitles.toIso639_1(preferred);
        if (codes.isEmpty()) return null;
        OpenSubtitles.Candidate candidate =
                OpenSubtitles.pick(OpenSubtitles.search(id, codes, answered), codes);
        if (candidate == null || cancelled()) return null;
        String link = OpenSubtitles.link(candidate.fileId);
        if (link == null) return null;
        return new Result(SOURCE_OPENSUBTITLES,
                AudioLanguagePriority.normalize(candidate.language),
                Collections.singletonList(link));
    }

    private static List<Candidate> shegu(MediaId id, AtomicBoolean answered) {
        if (id.tmdb == null) return Collections.emptyList();
        StringBuilder url = new StringBuilder("https://subtitles.shegu.st/subtitles?tmdb=")
                .append(Uri.encode(id.tmdb)).append("&type=")
                .append(id.isMovie() ? "movie" : "tv");
        if (!id.isMovie()) {
            url.append("&season=").append(id.season);
            if (id.episode > 0) url.append("&episode=").append(id.episode);
        }
        List<Candidate> candidates = new ArrayList<>();
        try {
            JSONArray subtitles = json(url.toString(), answered).optJSONArray("subtitles");
            if (subtitles == null) return candidates;
            for (int i = 0; i < subtitles.length(); i++) {
                JSONObject entry = subtitles.getJSONObject(i);
                String language = AudioLanguagePriority.normalize(
                        entry.optString("language"));
                String link = entry.optString("url", null);
                if (language != null && link != null) {
                    candidates.add(new Candidate(language, 0, link));
                }
            }
        } catch (Exception e) {
            Utils.log("shegu.st: " + e);
        }
        return candidates;
    }

    private static List<Candidate> stremio(MediaId id, AtomicBoolean answered) {
        if (id.imdb == null) return Collections.emptyList();
        String imdb = id.imdb.startsWith("tt") ? id.imdb : "tt" + id.imdb;
        String path = id.isMovie() ? "movie/" + imdb
                : "series/" + imdb + ":" + id.season + ":" + id.episode;
        List<Candidate> candidates = new ArrayList<>();
        try {
            JSONArray subtitles = json("https://opensubtitles-v3.strem.io/subtitles/"
                    + path + ".json", answered).optJSONArray("subtitles");
            if (subtitles == null) return candidates;
            for (int i = 0; i < subtitles.length(); i++) {
                JSONObject entry = subtitles.getJSONObject(i);
                String language = AudioLanguagePriority.normalize(entry.optString("lang"));
                String link = entry.optString("url", null);
                if (language != null && link != null) {
                    candidates.add(new Candidate(language, 0, link));
                }
            }
        } catch (Exception e) {
            Utils.log("stremio: " + e);
        }
        return candidates;
    }

    private static List<Candidate> restOpenSubtitles(MediaId id, List<String> preferred,
                                                     AtomicBoolean answered) {
        if (id.imdb == null) return Collections.emptyList();
        for (String language : preferred) {
            if (cancelled()) break;
            StringBuilder url = new StringBuilder("https://rest.opensubtitles.org/search/");
            if (!id.isMovie()) url.append("episode-").append(id.episode).append('/');
            url.append("imdbid-").append(id.imdbNumeric()).append('/');
            if (!id.isMovie()) url.append("season-").append(id.season).append('/');
            url.append("sublanguageid-").append(bibliographic(language));

            List<Candidate> candidates = new ArrayList<>();
            try {
                String body = get(url.toString(), answered);
                if (body == null) continue;
                JSONArray entries = new JSONArray(body);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    String link = entry.optString("SubDownloadLink", null);
                    String found = AudioLanguagePriority.normalize(
                            entry.optString("SubLanguageID"));
                    if (link != null && found != null) {
                        candidates.add(new Candidate(found,
                                parseInt(entry.optString("SubDownloadsCnt")), link));
                    }
                }
            } catch (Exception e) {
                Utils.log("rest.opensubtitles.org: " + e);
            }
            if (!candidates.isEmpty()) return candidates;
        }
        return Collections.emptyList();
    }

    private static final Map<String, String> BIBLIOGRAPHIC = new HashMap<>();

    static {
        String[][] codes = {
                {"sqi", "alb"}, {"hye", "arm"}, {"eus", "baq"}, {"mya", "bur"},
                {"zho", "chi"}, {"ces", "cze"}, {"nld", "dut"}, {"fas", "per"},
                {"fra", "fre"}, {"kat", "geo"}, {"deu", "ger"}, {"ell", "gre"},
                {"isl", "ice"}, {"mkd", "mac"}, {"mri", "mao"}, {"msa", "may"},
                {"ron", "rum"}, {"slk", "slo"}, {"bod", "tib"}, {"cym", "wel"},
        };
        for (String[] pair : codes) BIBLIOGRAPHIC.put(pair[0], pair[1]);
    }

    private static String bibliographic(String iso639_2t) {
        String value = BIBLIOGRAPHIC.get(iso639_2t);
        return value == null ? iso639_2t : value;
    }

    private static Result best(String source, List<Candidate> candidates,
                               List<String> preferred) {
        String language = null;
        for (String wanted : preferred) {
            for (Candidate candidate : candidates) {
                if (wanted.equals(candidate.language)) {
                    language = wanted;
                    break;
                }
            }
            if (language != null) break;
        }
        if (language == null) return null;

        List<Candidate> inLanguage = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (language.equals(candidate.language)) inLanguage.add(candidate);
        }
        Collections.sort(inLanguage,
                (left, right) -> Integer.compare(right.downloads, left.downloads));
        List<String> urls = new ArrayList<>();
        for (Candidate candidate : inLanguage) {
            if (urls.size() >= MAX_URLS) break;
            urls.add(candidate.url);
        }
        return new Result(source, language, urls);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static JSONObject json(String url, AtomicBoolean answered) throws Exception {
        String body = get(url, answered);
        return body == null ? new JSONObject() : new JSONObject(body);
    }

    private static String get(String url, AtomicBoolean answered) {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            answered.set(true);
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                Utils.log("subtitles: " + response.code() + " " + request.url().host());
                return null;
            }
            return body.string();
        } catch (IOException e) {
            Utils.log("subtitles: " + e);
            return null;
        }
    }
}
