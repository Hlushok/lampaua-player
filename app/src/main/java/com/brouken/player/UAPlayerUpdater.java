package com.brouken.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Quiet update discovery through the LampaUA hub. */
final class UAPlayerUpdater {
    private static final String UPDATE_URL = "https://kinohub.uk/lite/uaplayer/update";
    private static final String PREFS = "lampaua_player_updates";
    private static final String LAST_CHECK = "last_check";
    private static final long CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private UAPlayerUpdater() { }

    static void check(Activity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> checkNow(activity), 4000);
    }

    private static void checkNow(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(LAST_CHECK, 0) < CHECK_INTERVAL_MS) return;
        prefs.edit().putLong(LAST_CHECK, now).apply();

        String url = Uri.parse(UPDATE_URL).buildUpon()
                .appendQueryParameter("version_code", String.valueOf(BuildConfig.VERSION_CODE))
                .appendQueryParameter("version_name", BuildConfig.VERSION_NAME)
                .build().toString();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "UA-Player/" + BuildConfig.VERSION_NAME)
                .build();
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    JSONObject json = new JSONObject(response.body().string());
                    int latestCode = json.optInt("version_code", 0);
                    String latestName = json.optString("version_name", "");
                    String downloadUrl = json.optString("download_url", "");
                    String notes = json.optString("notes", "");
                    if (latestCode <= BuildConfig.VERSION_CODE || downloadUrl.isEmpty()) return;
                    activity.runOnUiThread(() -> show(activity, latestName, notes, downloadUrl));
                } catch (Exception ignored) { }
            }
        });
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
