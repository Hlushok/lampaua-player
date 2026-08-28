package com.brouken.player;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reusable signed-offset panel, adapted from the Just+ subtitle and v1.3 session panels.
 * UA Player keeps the behavior while using its own native navy-and-gold dialog surface.
 */
final class OffsetPanel {
    interface Listener {
        void onOffsetChanged(double sec);
    }

    static final class Line {
        final CharSequence title;
        final double initialSec;
        final Listener listener;

        Line(CharSequence title, double initialSec, Listener listener) {
            this.title = title;
            this.initialSec = initialSec;
            this.listener = listener;
        }
    }

    static final class Choice {
        interface Listener {
            void onPicked(String value);
        }

        final CharSequence[] labels;
        final String[] values;
        final String current;
        final String inherited;
        final Listener listener;

        Choice(CharSequence[] labels, String[] values, String current,
               String inherited, Listener listener) {
            this.labels = labels;
            this.values = values;
            this.current = current;
            this.inherited = inherited;
            this.listener = listener;
        }
    }

    private OffsetPanel() {}

    static AlertDialog create(Context context, String title, double maxSec, double stepSec,
                              double initialSec, Listener listener) {
        return create(context, title, maxSec, stepSec, (Choice[]) null,
                new Line(null, initialSec, listener));
    }

    static AlertDialog create(Context context, String title, double maxSec, double stepSec,
                              Line... lines) {
        return create(context, title, maxSec, stepSec, (Choice[]) null, lines);
    }

    static AlertDialog create(Context context, String title, double maxSec, double stepSec,
                              Choice[] choices, Line... lines) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = Utils.dpToPx(20);
        root.setPadding(side, Utils.dpToPx(8), side, Utils.dpToPx(8));

        List<Runnable> resets = new ArrayList<>();
        View firstChoice = null;
        if (choices != null) {
            for (Choice choice : choices) {
                View selected = addChoice(context, root, choice, resets);
                if (firstChoice == null) firstChoice = selected;
            }
        }

        SeekBar firstSeek = null;
        boolean compact = firstChoice != null || lines.length > 1;
        for (Line line : lines) {
            SeekBar seek = addLine(context, root, line, maxSec, stepSec, compact, resets);
            if (firstSeek == null) firstSeek = seek;
        }

        Button reset = new Button(context);
        reset.setAllCaps(false);
        reset.setText(R.string.subtitle_offset_reset);
        reset.setTextColor(context.getColor(R.color.ua_gold));
        reset.setMinHeight(Utils.dpToPx(48));
        reset.setBackgroundResource(R.drawable.ua_dialog_button_background);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.gravity = Gravity.CENTER_HORIZONTAL;
        resetParams.topMargin = Utils.dpToPx(10);
        root.addView(reset, resetParams);
        reset.setOnClickListener(view -> {
            for (Runnable action : resets) action.run();
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(root);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        View preferredFocus = firstChoice != null ? firstChoice : firstSeek;
        dialog.setOnShowListener(ignored -> {
            UaDialogStyler.style(context, dialog, UaDialogStyler.FocusTarget.CONTENT, -1);
            if (preferredFocus != null) preferredFocus.post(preferredFocus::requestFocus);
        });
        return dialog;
    }

    private static View addChoice(Context context, LinearLayout root, Choice choice,
                                  List<Runnable> resets) {
        if (choice == null || choice.labels == null || choice.values == null
                || choice.labels.length == 0 || choice.labels.length != choice.values.length) {
            return null;
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView[] pills = new TextView[choice.values.length];
        int[] picked = {indexOf(choice.values, choice.current)};
        Runnable render = () -> {
            for (int i = 0; i < pills.length; i++) {
                boolean selected = i == picked[0];
                pills[i].setSelected(selected);
                pills[i].setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                pills[i].setTextColor(selected ? Color.WHITE
                        : context.getColor(R.color.ua_secondary));
                pills[i].setBackground(choiceBackground(context, selected));
            }
        };
        for (int i = 0; i < choice.values.length; i++) {
            TextView pill = new TextView(context);
            pill.setText(choice.labels[i]);
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            pill.setGravity(Gravity.CENTER);
            pill.setMaxLines(1);
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setMinHeight(Utils.dpToPx(48));
            pill.setPadding(Utils.dpToPx(3), Utils.dpToPx(8),
                    Utils.dpToPx(3), Utils.dpToPx(8));
            int index = i;
            pill.setOnClickListener(view -> {
                if (picked[0] == index) return;
                picked[0] = index;
                render.run();
                if (choice.listener != null) choice.listener.onPicked(choice.values[index]);
            });
            pill.setOnFocusChangeListener((view, focused) -> view.animate()
                    .scaleX(focused ? 1.05f : 1f)
                    .scaleY(focused ? 1.05f : 1f)
                    .setDuration(120)
                    .start());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) params.setMarginStart(Utils.dpToPx(3));
            row.addView(pill, params);
            pills[i] = pill;
        }
        render.run();
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        resets.add(() -> {
            picked[0] = indexOf(choice.values, choice.inherited);
            render.run();
            if (choice.listener != null) choice.listener.onPicked(null);
        });
        return pills[Math.max(0, picked[0])];
    }

    private static SeekBar addLine(Context context, LinearLayout root, Line line,
                                   double maxSec, double stepSec, boolean compact,
                                   List<Runnable> resets) {
        int progressMax = (int) Math.round(2 * maxSec / stepSec);
        int middle = progressMax / 2;
        double[] current = {line.initialSec};

        if (line.title != null) {
            TextView caption = new TextView(context);
            caption.setText(line.title);
            caption.setTextColor(context.getColor(R.color.ua_secondary));
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, Utils.dpToPx(compact ? 14 : 8), 0, 0);
            root.addView(caption);
        }

        TextView value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 24 : 28);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, Utils.dpToPx(5), 0, Utils.dpToPx(5));
        root.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(compact ? 50 : 60)));

        SeekBar seek = new SeekBar(context);
        seek.setMax(progressMax);
        seek.setKeyProgressIncrement(Math.max(2,
                (int) Math.round(maxSec / 90d / stepSec)));
        seek.setProgress((int) Math.round(line.initialSec / stepSec) + middle);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(
                context.getColor(R.color.ua_gold)));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(
                context.getColor(R.color.ua_gold)));

        Button minus = actionButton(context, "-", R.string.subtitle_offset_earlier);
        Button plus = actionButton(context, "+", R.string.subtitle_offset_later);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(minus, new LinearLayout.LayoutParams(
                Utils.dpToPx(52), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(seek, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(
                Utils.dpToPx(52), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(row);

        Runnable render = () -> {
            value.setText(format(context, current[0]));
            value.setTextColor(isZero(current[0])
                    ? Color.WHITE : context.getColor(R.color.ua_gold));
        };
        Runnable apply = () -> {
            render.run();
            if (line.listener != null) line.listener.onOffsetChanged(current[0]);
        };
        render.run();
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                current[0] = (progress - middle) * stepSec;
                apply.run();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        minus.setOnClickListener(view -> step(seek, -1, progressMax, middle,
                stepSec, current, apply));
        plus.setOnClickListener(view -> step(seek, 1, progressMax, middle,
                stepSec, current, apply));
        resets.add(() -> {
            seek.setProgress(middle);
            current[0] = 0;
            apply.run();
        });
        return seek;
    }

    private static Button actionButton(Context context, String text, int descriptionRes) {
        Button button = new Button(context);
        button.setText(text);
        button.setContentDescription(context.getString(descriptionRes));
        button.setTextColor(context.getColor(R.color.ua_gold));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setMinWidth(Utils.dpToPx(48));
        button.setMinHeight(Utils.dpToPx(48));
        button.setBackgroundResource(R.drawable.ua_dialog_button_background);
        return button;
    }

    private static Drawable choiceBackground(Context context, boolean selected) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                choiceShape(context, selected, true));
        states.addState(StateSet.WILD_CARD, choiceShape(context, selected, false));
        return states;
    }

    private static Drawable choiceShape(Context context, boolean selected, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Utils.dpToPx(10));
        shape.setColor(selected ? Color.argb(225, 10, 39, 76)
                : Color.argb(54, 10, 39, 76));
        if (selected || focused) {
            shape.setStroke(Utils.dpToPx(focused ? 2 : 1),
                    context.getColor(R.color.ua_gold));
        }
        return shape;
    }

    private static int indexOf(String[] values, String value) {
        if (value == null) return -1;
        for (int i = 0; i < values.length; i++) {
            if (value.equals(values[i])) return i;
        }
        return -1;
    }

    private static void step(SeekBar seek, int delta, int progressMax, int middle,
                             double stepSec, double[] current, Runnable apply) {
        int progress = Math.max(0, Math.min(progressMax, seek.getProgress() + delta));
        seek.setProgress(progress);
        current[0] = (progress - middle) * stepSec;
        apply.run();
    }

    private static boolean isZero(double sec) {
        return Math.abs(sec) < 0.001;
    }

    static String format(Context context, double sec) {
        return context.getString(R.string.offset_seconds,
                formatNumber(sec, Locale.getDefault()));
    }

    static String format(double sec) {
        return formatNumber(sec, Locale.US) + " s";
    }

    static String formatNumber(double sec, Locale locale) {
        if (isZero(sec)) return "0";
        String value = String.format(locale, "%+.2f", sec);
        char separator = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') end--;
        if (end > 0 && value.charAt(end - 1) == separator) end--;
        return value.substring(0, end);
    }
}
