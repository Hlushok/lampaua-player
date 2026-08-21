package com.brouken.player;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads the first usable subtitle candidate and stores a local UTF-8 copy. */
class SubtitleFetcher {

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int READ_TIMEOUT_SEC = 20;
    private static final long TOTAL_BUDGET_MS = 40_000L;
    private static final long MAX_BYTES = 2_000_000L;

    private static final OkHttpClient CLIENT = SubtitleSearch.CLIENT.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    private final PlayerActivity activity;
    private final List<Uri> urls;
    private final String cacheName;

    SubtitleFetcher(PlayerActivity activity, List<Uri> urls) {
        this(activity, urls, null);
    }

    SubtitleFetcher(PlayerActivity activity, List<Uri> urls, String cacheName) {
        this.activity = activity;
        this.urls = urls;
        this.cacheName = cacheName;
    }

    /** Fire and forget for manually supplied remote subtitle URLs. */
    void start() {
        Thread worker = new Thread(() -> {
            Uri file = fetchNow();
            if (file == null || Thread.currentThread().isInterrupted()) return;
            activity.runOnUiThread(() -> {
                activity.mPrefs.updateSubtitle(file);
                activity.addSubtitleTrack(file);
            });
        }, "SubtitleFetcher");
        worker.setDaemon(true);
        worker.start();
    }

    /** Downloads on the current worker thread so a failed source can give way to the next one. */
    Uri fetchNow() {
        long deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS;
        for (Uri url : urls) {
            if (Thread.currentThread().isInterrupted()
                    || System.currentTimeMillis() >= deadline) {
                return null;
            }
            Uri file = fetch(url);
            if (file != null) return file;
        }
        return null;
    }

    private Uri fetch(Uri url) {
        if (url == null || HttpUrl.parse(url.toString()) == null) return null;
        Request request = new Request.Builder()
                .url(url.toString())
                .header("User-Agent", "UA-Player/" + BuildConfig.VERSION_NAME)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null
                    || body.contentLength() > MAX_BYTES) {
                return null;
            }
            InputStream stream = body.byteStream();
            String path = url.getPath();
            if (path != null && path.toLowerCase(java.util.Locale.US).endsWith(".gz")) {
                stream = new GZIPInputStream(stream);
            }
            return Utils.convertInputStreamToUTF(activity, url, stream, cacheName);
        } catch (IOException | RuntimeException e) {
            Utils.log("subtitle download: " + e);
            return null;
        }
    }
}
