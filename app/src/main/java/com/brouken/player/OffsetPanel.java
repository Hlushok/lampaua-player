package com.brouken.player;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/** Subtitle-only signed offset panel adapted from Just+ Player PR #130. */
final class OffsetPanel {
    interface Listener {
        void onOffsetChanged(double sec);
    }

    static final class Line {
        final String title;
        final double initialSec;
        final Listener listener;

        Line(String title, double initialSec, Listener listener) {
            this.title = title;
            this.initialSec = initialSec;
            this.listener = listener;
        }
    }

    private OffsetPanel() { }

    static AlertDialog create(Context context, String title, double maxSec, double stepSec,
                              double initialSec, Listener listener) {
        return create(context, title, maxSec, stepSec,
                new Line(null, initialSec, listener));
    }

    static AlertDialog create(Context context, String title, double maxSec, double stepSec,
                              Line... lines) {
        int progressMax = (int) Math.round(2 * maxSec / stepSec);
        int middle = progressMax / 2;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Utils.dpToPx(20);
        root.setPadding(pad, Utils.dpToPx(8), pad, 0);

        SeekBar firstSeek = null;
        for (Line line : lines) {
            SeekBar seek = addLine(context, root, line, progressMax, middle, stepSec);
            if (firstSeek == null) firstSeek = seek;
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        SeekBar focus = firstSeek;
        dialog.setOnShowListener(ignored -> {
            UaDialogStyler.style(context, dialog, UaDialogStyler.FocusTarget.CONTENT, -1);
            if (focus != null) focus.post(focus::requestFocus);
        });
        return dialog;
    }

    private static SeekBar addLine(Context context, LinearLayout root, Line line,
                                   int progressMax, int middle, double stepSec) {
        double[] current = {line.initialSec};

        if (line.title != null) {
            TextView caption = new TextView(context);
            caption.setText(line.title);
            caption.setTextColor(Color.WHITE);
            caption.setTextSize(16);
            caption.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            caption.setPadding(0, Utils.dpToPx(8), 0, 0);
            root.addView(caption);
        }

        TextView value = new TextView(context);
        value.setTextColor(Color.rgb(240, 183, 38));
        value.setTextSize(28);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setGravity(Gravity.CENTER);
        value.setText(format(line.initialSec));
        root.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(64)));

        SeekBar seek = new SeekBar(context);
        seek.setMax(progressMax);
        seek.setKeyProgressIncrement(1);
        seek.setProgress((int) Math.round(line.initialSec / stepSec) + middle);

        Button minus = actionButton(context, "-");
        Button plus = actionButton(context, "+");
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(minus, new LinearLayout.LayoutParams(
                Utils.dpToPx(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(seek, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(
                Utils.dpToPx(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(row);

        LinearLayout direction = new LinearLayout(context);
        TextView earlier = hint(context, R.string.subtitle_offset_earlier, Gravity.START);
        TextView later = hint(context, R.string.subtitle_offset_later, Gravity.END);
        direction.addView(earlier, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        direction.addView(later, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(direction);

        Button reset = actionButton(context, context.getString(R.string.subtitle_offset_reset));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.gravity = Gravity.CENTER_HORIZONTAL;
        resetParams.topMargin = Utils.dpToPx(8);
        reset.setLayoutParams(resetParams);
        root.addView(reset);

        Runnable apply = () -> {
            value.setText(format(current[0]));
            value.setTextColor(Math.abs(current[0]) < 0.001
                    ? Color.WHITE : Color.rgb(240, 183, 38));
            if (line.listener != null) line.listener.onOffsetChanged(current[0]);
        };
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                current[0] = (progress - middle) * stepSec;
                apply.run();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        minus.setOnClickListener(view -> {
            seek.setProgress(Math.max(0, seek.getProgress() - 1));
            current[0] = (seek.getProgress() - middle) * stepSec;
            apply.run();
        });
        plus.setOnClickListener(view -> {
            seek.setProgress(Math.min(progressMax, seek.getProgress() + 1));
            current[0] = (seek.getProgress() - middle) * stepSec;
            apply.run();
        });
        reset.setOnClickListener(view -> {
            seek.setProgress(middle);
            current[0] = 0;
            apply.run();
        });

        return seek;
    }

    private static Button actionButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(Color.rgb(240, 183, 38));
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.ua_dialog_button_background);
        return button;
    }

    private static TextView hint(Context context, int text, int gravity) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Color.rgb(145, 178, 219));
        view.setTextSize(13);
        view.setGravity(gravity);
        view.setPadding(Utils.dpToPx(8), 0, Utils.dpToPx(8), 0);
        return view;
    }

    static String format(double sec) {
        if (Math.abs(sec) < 0.001) return "0 s";
        String value = String.format(Locale.US, "%+.2f", sec);
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') end--;
        if (end > 0 && value.charAt(end - 1) == '.') end--;
        return value.substring(0, end) + " s";
    }
}
