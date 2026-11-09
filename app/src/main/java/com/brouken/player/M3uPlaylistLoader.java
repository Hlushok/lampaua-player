package com.brouken.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Bounded, cancellable background import. Never reads a playlist on the activity thread. */
final class M3uPlaylistLoader {
    interface Callback { void complete(Bundle playlist, boolean failed); }

    static class Job implements Runnable {
        private final Context context;
        private final Intent intent;
        private final OkHttpClient client;
        private final Callback callback;
        private volatile boolean cancelled;
        private volatile Call call;
        private volatile InputStream input;
        private Thread thread;

        Job(Context context, Intent intent, OkHttpClient client, Callback callback) {
            this.context = context.getApplicationContext();
            this.intent = new Intent(intent);
            this.client = client.newBuilder().connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
                    .followRedirects(false).followSslRedirects(false).build();
            this.callback = callback;
        }

        void start() { thread = new Thread(this, "m3u-import"); thread.start(); }

        void cancel() {
            cancelled = true;
            Call active = call;
            if (active != null) active.cancel();
            InputStream stream = input;
            if (stream != null) try { stream.close(); } catch (IOException ignored) { }
            if (thread != null) thread.interrupt();
        }

        @Override public void run() {
            Bundle playlist = null;
            boolean failed = false;
            Uri original = intent.getData();
            try {
                Map<String, String> headers = M3uPlaylist.headers(intent);
                String scheme = original.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    playlist = http(original, headers);
                } else {
                    try (InputStream stream = context.getContentResolver().openInputStream(original)) {
                        input = stream;
                        playlist = M3uPlaylist.parse(read(stream), original, original, headers);
                    } finally { input = null; }
                }
            } catch (Exception error) {
                // Ambiguous HLS URLs retain Media3's normal network/recovery behavior.
                failed = M3uPlaylist.explicitFileList(original);
                if (!cancelled) Utils.log("M3U import: " + error.getClass().getSimpleName());
            }
            if (!cancelled) callback.complete(playlist, failed);
        }

        private Bundle http(Uri original, Map<String, String> headers) throws IOException {
            Uri target = original;
            for (int redirects = 0; redirects <= 5; redirects++) {
                checkCancelled();
                Request.Builder request = new Request.Builder().url(target.toString());
                for (Map.Entry<String, String> header : M3uPlaylist.safeHeaders(headers, original, target).entrySet()) {
                    request.header(header.getKey(), header.getValue());
                }
                call = client.newCall(request.build());
                checkCancelled();
                try (Response response = call.execute()) {
                    int code = response.code();
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String location = response.header("Location");
                        if (location == null) throw new IOException("Redirect without location");
                        try { target = Uri.parse(new java.net.URI(target.toString()).resolve(location).toString()); }
                        catch (Exception invalid) { throw new IOException("Invalid M3U redirect", invalid); }
                        if (!("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme())))
                            throw new IOException("Unsafe M3U redirect");
                        continue;
                    }
                    if (!response.isSuccessful() || response.body() == null) throw new IOException("M3U HTTP failure");
                    String contentType = response.header("Content-Type", "").toLowerCase(java.util.Locale.ROOT);
                    if (!M3uPlaylist.explicitFileList(original) && !contentType.contains("mpegurl")
                            && (contentType.startsWith("video/") || contentType.startsWith("audio/"))) return null;
                    input = response.body().byteStream();
                    return M3uPlaylist.parse(read(input), target, original, headers);
                } finally { input = null; }
            }
            throw new IOException("Too many M3U redirects");
        }

        private String read(InputStream stream) throws IOException {
            if (stream == null) throw new IOException("No M3U stream");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                checkCancelled();
                if (bytes.size() + count > M3uPlaylist.MAX_BYTES) throw new IOException("M3U too large");
                bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }

        private void checkCancelled() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("M3U cancelled");
        }
    }
}
