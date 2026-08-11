package com.brouken.player.update;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.core.content.FileProvider;
import android.preference.PreferenceManager;

import com.brouken.player.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * In-app self-updater. Checks the fork's GitHub releases, downloads the universal APK and hands it
 * to the system installer. Mirrors the network/threading style of {@code skip/SegmentFinder}: a
 * static {@link OkHttpClient}, work on interruptible daemon threads, {@code org.json} parsing, and
 * silent failure (any error/timeout yields "no update" вЂ” playback is never affected).
 *
 * <p>Version comparison is by numeric {@code versionCode} (same formula the build uses:
 * {@code major*1_000_000 + minor*1_000 + patch}); only a strictly higher code is offered, so
 * downgrades and {@code draft}/{@code prerelease} builds are never proposed.
 */
public final class Updater {

    /** Receives the newest eligible update (or {@code null} when none / on error), on the worker thread. */
    public interface Callback {
        void onResult(UpdateInfo info);
    }

    /** Reports download progress (0..100) on the worker thread. */
    public interface ProgressListener {
        void onProgress(int percent);
    }

    /** Receives the downloaded APK file (or {@code null} on failure), on the worker thread. */
    public interface DownloadCallback {
        void onDownloaded(File file);
    }

    private Updater() {}

    private static final String RELEASES_URL =
            "https://api.github.com/repos/Hlushok/lampaua-player/releases/latest";
    private static final String STATE_PREFS = "ua_player_update_state";
    private static final String LAST_CHECK = "last_check";
    private static final String SKIPPED_CODE = "skipped_code";
    private static final String PENDING = "pending";
    private static final long CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24);
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    // ---- Check ---------------------------------------------------------------------------------

    /** Starts an async check on a daemon thread; the callback fires on that worker thread. */
    public static Thread find(Callback callback) {
        final Thread thread = new Thread(() -> {
            final UpdateInfo info = check();
            if (!Thread.currentThread().isInterrupted()) {
                callback.onResult(info);
            }
        }, "Updater");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public static Thread find(Context context, boolean manual, Callback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences state = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        if (BuildConfig.DEBUG) {
            callback.onResult(null);
            return null;
        }
        if (!manual && !PreferenceManager.getDefaultSharedPreferences(app)
                .getBoolean("autoUpdate", true)) {
            callback.onResult(pending(app));
            return null;
        }
        long now = System.currentTimeMillis();
        if (!manual && now - state.getLong(LAST_CHECK, 0) < CHECK_INTERVAL_MS) {
            callback.onResult(pending(app));
            return null;
        }
        state.edit().putLong(LAST_CHECK, now).apply();
        return find(info -> {
            int skipped = state.getInt(SKIPPED_CODE, 0);
            int current = UpdatePolicy.versionCode(BuildConfig.VERSION_NAME);
            UpdateInfo eligible = UpdatePolicy.shouldOffer(current, info, skipped, false)
                    ? info : null;
            if (eligible != null) {
                state.edit().putString(PENDING, eligible.toJson()).apply();
            } else if (info != null && info.versionCode <= current) {
                state.edit().remove(PENDING).apply();
            }
            callback.onResult(eligible);
        });
    }

    public static UpdateInfo pending(Context context) {
        SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        UpdateInfo info = UpdateInfo.fromJson(state.getString(PENDING, null));
        return UpdatePolicy.shouldOffer(UpdatePolicy.versionCode(BuildConfig.VERSION_NAME), info,
                state.getInt(SKIPPED_CODE, 0), BuildConfig.DEBUG) ? info : null;
    }

    public static void skip(Context context, UpdateInfo info) {
        if (info == null) return;
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(SKIPPED_CODE, info.versionCode).remove(PENDING).apply();
    }

    /** Synchronous check (call on a background thread). Returns the newest eligible release, or {@code null}. */
    public static UpdateInfo check() {
        final String body = get(RELEASES_URL);
        if (body == null) {
            return null;
        }
        try {
            final JSONObject release = new JSONObject(body);
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null;
            final String tag = release.optString("tag_name", "");
            final int remoteCode = UpdatePolicy.versionCode(tag);
            final JSONObject apk = firstApkAsset(release.optJSONArray("assets"));
            if (apk == null) return null;
            final UpdateInfo info = new UpdateInfo(
                    remoteCode, tag, tag.startsWith("v") ? tag.substring(1) : tag,
                    release.optString("body", ""),
                    apk.optString("browser_download_url", ""), apk.optLong("size", 0));
            if (UpdatePolicy.shouldOffer(UpdatePolicy.versionCode(BuildConfig.VERSION_NAME),
                    info, 0, false)) return info;
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static JSONObject firstApkAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            final JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            final String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
            final String url = asset.optString("browser_download_url", "");
            if (name.endsWith(".apk") && name.contains("player") && name.contains("ua")
                    && !name.contains("legacy") && url.startsWith("https://")) {
                return asset;
            }
        }
        return null;
    }

    /** Parses {@code major*1_000_000 + minor*1_000 + patch} out of a tag like {@code v1.2.3}. Returns 0 if unparseable. */
    static int parseVersionCode(String tag) {
        return UpdatePolicy.versionCode(tag);
    }

    // ---- Download ------------------------------------------------------------------------------

    /** Starts an async download on a daemon thread; both callbacks fire on that worker thread. */
    public static Thread downloadApkAsync(Context context, UpdateInfo info, ProgressListener listener, DownloadCallback callback) {
        final Context appContext = context.getApplicationContext();
        final Thread thread = new Thread(() -> {
            final File file = downloadApk(appContext, info, listener);
            if (!Thread.currentThread().isInterrupted()) {
                callback.onDownloaded(file);
            }
        }, "Updater-download");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** Downloads the APK into the external files dir. Call on a background thread. Returns the file or {@code null}. */
    public static File downloadApk(Context context, UpdateInfo info, ProgressListener listener) {
        final File file = new File(context.getCacheDir(),
                "ua-player-" + info.versionName.replaceAll("[^0-9A-Za-z._-]", "_") + ".apk");
        if (file.exists()) {
            file.delete();
        }
        final Request request = new Request.Builder().url(info.apkUrl).build();
        boolean complete = false;
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            final ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return null;
            }
            final long total = responseBody.contentLength();
            long downloaded = 0;
            try (InputStream in = responseBody.byteStream();
                 FileOutputStream out = new FileOutputStream(file)) {
                final byte[] buffer = new byte[64 * 1024];
                int lastPercent = -1;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        return null;
                    }
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (listener != null && total > 0) {
                        final int percent = (int) (downloaded * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                    }
                }
                out.flush();
            }
            if (downloaded <= 0 || (total > 0 && downloaded != total)) return null;
            complete = true;
            return file;
        } catch (Exception e) {
            return null;
        } finally {
            if (!complete && file.exists()) file.delete();
        }
    }

    // ---- Install -------------------------------------------------------------------------------

    /** Fires the system package installer for a downloaded APK via {@link FileProvider}. */
    public static void installApk(Context context, File file) {
        final Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // ---- HTTP ----------------------------------------------------------------------------------

    private static String get(String url) {
        final Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                // GitHub's API rejects requests without a User-Agent.
                .header("User-Agent", "UA-Player/" + BuildConfig.VERSION_NAME)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            final ResponseBody body = response.body();
            return body != null ? body.string() : null;
        } catch (Exception e) {
            // Timeout / offline / rate-limited вЂ” silently yield nothing.
            return null;
        }
    }
}
