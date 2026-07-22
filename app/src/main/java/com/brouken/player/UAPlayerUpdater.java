package com.brouken.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Update discovery backed only by the public UA Player GitHub releases. */
final class UAPlayerUpdater {
    private static final String RELEASE_URL =
            "https://api.github.com/repos/Hlushok/lampaua-player/releases/latest";
    private static final String PREFS = "lampaua_player_updates";
    private static final String LAST_CHECK = "last_check";
    private static final String AUTO_UPDATE = "autoUpdate";
    private static final long CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(3);
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private UAPlayerUpdater() { }

    static void check(Activity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> checkNow(activity, false), 4000);
    }

    static void checkNow(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!manual && !PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(AUTO_UPDATE, true)) return;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - prefs.getLong(LAST_CHECK, 0) < CHECK_INTERVAL_MS) return;
        prefs.edit().putLong(LAST_CHECK, now).apply();

        Request request = new Request.Builder()
                .url(RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "UA-Player/" + BuildConfig.VERSION_NAME)
                .build();
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                if (manual) activity.runOnUiThread(() -> showNoUpdate(activity));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        if (manual) activity.runOnUiThread(() -> showNoUpdate(activity));
                        return;
                    }
                    JSONObject release = new JSONObject(response.body().string());
                    if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                        if (manual) activity.runOnUiThread(() -> showNoUpdate(activity));
                        return;
                    }
                    String tag = release.optString("tag_name", "");
                    String downloadUrl = firstApkUrl(release.optJSONArray("assets"));
                    if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0 || downloadUrl.isEmpty()) {
                        if (manual) activity.runOnUiThread(() -> showNoUpdate(activity));
                        return;
                    }
                    String version = normalizedVersion(tag);
                    String notes = release.optString("body", "");
                    activity.runOnUiThread(() -> show(activity, version, notes, downloadUrl));
                } catch (Exception ignored) {
                    if (manual) activity.runOnUiThread(() -> showNoUpdate(activity));
                }
            }
        });
    }

    private static String firstApkUrl(JSONArray assets) {
        if (assets == null) return "";
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset == null) continue;
            String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
            String url = asset.optString("browser_download_url", "");
            if (name.endsWith(".apk") && !url.isEmpty()) return url;
        }
        return "";
    }

    static int compareVersions(String left, String right) {
        int[] a = versionParts(left);
        int[] b = versionParts(right);
        for (int index = 0; index < 3; index++) {
            int result = Integer.compare(a[index], b[index]);
            if (result != 0) return result;
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        int[] parts = new int[3];
        if (value == null) return parts;
        Matcher matcher = VERSION.matcher(value);
        if (!matcher.find()) return parts;
        for (int index = 0; index < 3; index++) {
            try {
                parts[index] = Integer.parseInt(matcher.group(index + 1));
            } catch (NumberFormatException ignored) { }
        }
        return parts;
    }

    private static String normalizedVersion(String tag) {
        Matcher matcher = VERSION.matcher(tag == null ? "" : tag);
        return matcher.find() ? matcher.group() : tag;
    }

    private static void showNoUpdate(Activity activity) {
        if (!activity.isFinishing() && !activity.isDestroyed()) {
            Toast.makeText(activity, R.string.update_none, Toast.LENGTH_SHORT).show();
        }
    }

    private static void show(Activity activity, String version, String notes, String downloadUrl) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String message = activity.getString(R.string.update_available_message,
                version == null || version.isEmpty() ? "" : version);
        if (notes != null && !notes.trim().isEmpty()) message += "\n\n" + notes.trim();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setNegativeButton(R.string.update_later, null)
                .setPositiveButton(R.string.update_download, (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(activity, R.string.update_open_failed, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
