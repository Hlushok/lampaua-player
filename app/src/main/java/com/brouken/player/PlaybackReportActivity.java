package com.brouken.player;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class PlaybackReportActivity extends AppCompatActivity {
    private static final String EXTRA_TITLE = "report_title";
    private static final String EXTRA_SUMMARY = "report_summary";
    private static final String EXTRA_REPORT = "report_body";
    private static final int MAX_REPORT_LENGTH = 64 * 1024;

    public static void show(Context context, String title, String summary, String report) {
        Intent intent = new Intent(context, PlaybackReportActivity.class)
                .putExtra(EXTRA_TITLE, DiagnosticReport.sanitizeText(title))
                .putExtra(EXTRA_SUMMARY, DiagnosticReport.sanitizeText(summary))
                .putExtra(EXTRA_REPORT, DiagnosticReport.sanitizeText(report));
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback_report);

        String title = sanitizedExtra(EXTRA_TITLE);
        String summary = sanitizedExtra(EXTRA_SUMMARY);
        String report = trimReport(deviceHeader() + "\n\n" + sanitizedExtra(EXTRA_REPORT));

        TextView titleView = findViewById(R.id.report_title);
        TextView summaryView = findViewById(R.id.report_summary);
        TextView bodyView = findViewById(R.id.report_body);
        titleView.setText(TextUtils.isEmpty(title)
                ? getString(R.string.playback_report_title) : title);
        summaryView.setText(summary);
        summaryView.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
        bodyView.setText(report);

        findViewById(R.id.report_copy).setOnClickListener(view -> copyReport(report));
        findViewById(R.id.report_share).setOnClickListener(view -> shareReport(title, report));
        findViewById(R.id.report_close).setOnClickListener(view -> finish());
        findViewById(R.id.report_close).requestFocus();
    }

    private String sanitizedExtra(String key) {
        return DiagnosticReport.sanitizeText(getIntent().getStringExtra(key));
    }

    private String deviceHeader() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)
                .format(new Date());
        String flavor = TextUtils.isEmpty(BuildConfig.FLAVOR) ? "universal" : BuildConfig.FLAVOR;
        return "UA Player " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nBuild: " + flavor + "/" + BuildConfig.BUILD_TYPE
                + (BuildConfig.DEBUG ? " debug" : "")
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + " / " + Build.DEVICE
                + "\nABI: " + Arrays.toString(Build.SUPPORTED_ABIS)
                + "\nTimestamp: " + timestamp;
    }

    private String trimReport(String report) {
        if (report.length() <= MAX_REPORT_LENGTH) return report;
        return report.substring(0, MAX_REPORT_LENGTH) + "\n\n[report truncated]";
    }

    private void copyReport(String report) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("UA Player playback report", report));
            Toast.makeText(this, R.string.playback_report_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReport(String title, String report) {
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, TextUtils.isEmpty(title)
                        ? getString(R.string.playback_report_title) : title)
                .putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(share, getString(R.string.playback_report_share)));
    }
}
