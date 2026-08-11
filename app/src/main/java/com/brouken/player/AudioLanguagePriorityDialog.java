package com.brouken.player;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

final class AudioLanguagePriorityDialog {
    interface Listener { void onLanguagesPicked(List<String> languages); }

    private AudioLanguagePriorityDialog() {}

    static void show(Context context, List<String> initial,
                     LinkedHashMap<String, String> allLanguages, List<String> pinned,
                     Listener listener) {
        List<String> languages = new ArrayList<>(initial);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = Utils.dpToPx(16);
        list.setPadding(padding, Utils.dpToPx(8), padding, Utils.dpToPx(8));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.pref_language_audio)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (ignored, which) -> listener.onLanguagesPicked(languages))
                .create();
        rebuild(context, list, languages, allLanguages, pinned);
        dialog.show();
    }

    private static void rebuild(Context context, LinearLayout list, List<String> selected,
                                LinkedHashMap<String, String> allLanguages, List<String> pinned) {
        list.removeAllViews();
        if (selected.isEmpty()) {
            TextView empty = text(context, context.getString(R.string.pref_language_audio_none));
            empty.setTextColor(Color.LTGRAY);
            list.addView(empty);
        }
        for (int index = 0; index < selected.size(); index++) {
            int rowIndex = index;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Utils.dpToPx(8), Utils.dpToPx(4), Utils.dpToPx(8), Utils.dpToPx(4));
            row.setBackgroundResource(R.drawable.ua_preference_item_background);

            TextView label = text(context, label(allLanguages, selected.get(index)));
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button up = action(context, "\u2191", R.string.pref_language_audio_move_up);
            up.setEnabled(index > 0);
            up.setOnClickListener(view -> {
                Collections.swap(selected, rowIndex, rowIndex - 1);
                rebuild(context, list, selected, allLanguages, pinned);
            });
            Button down = action(context, "\u2193", R.string.pref_language_audio_move_down);
            down.setEnabled(index < selected.size() - 1);
            down.setOnClickListener(view -> {
                Collections.swap(selected, rowIndex, rowIndex + 1);
                rebuild(context, list, selected, allLanguages, pinned);
            });
            Button remove = action(context, "\u00D7", R.string.pref_language_audio_remove);
            remove.setOnClickListener(view -> {
                selected.remove(rowIndex);
                rebuild(context, list, selected, allLanguages, pinned);
            });
            row.addView(up); row.addView(down); row.addView(remove);
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Button add = new Button(context);
        add.setAllCaps(false);
        add.setText(R.string.pref_language_audio_add);
        add.setOnClickListener(view -> showPicker(context, list, selected, allLanguages, pinned));
        list.addView(add);
    }

    private static void showPicker(Context context, LinearLayout list, List<String> selected,
                                   LinkedHashMap<String, String> allLanguages, List<String> pinned) {
        List<String> codes = new ArrayList<>();
        for (String code : pinned) if (!selected.contains(code) && !codes.contains(code)) codes.add(code);
        for (String code : allLanguages.keySet()) if (!selected.contains(code) && !codes.contains(code)) codes.add(code);
        String[] labels = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) labels[i] = label(allLanguages, codes.get(i));
        new AlertDialog.Builder(context)
                .setTitle(R.string.pref_language_audio_add)
                .setItems(labels, (dialog, which) -> {
                    selected.add(codes.get(which));
                    rebuild(context, list, selected, allLanguages, pinned);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static TextView text(Context context, String value) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(17);
        text.setPadding(Utils.dpToPx(12), Utils.dpToPx(12), Utils.dpToPx(12), Utils.dpToPx(12));
        return text;
    }

    private static Button action(Context context, String glyph, int description) {
        Button button = new Button(context);
        button.setText(glyph);
        button.setTextSize(20);
        button.setContentDescription(context.getString(description));
        button.setMinWidth(Utils.dpToPx(48));
        button.setMinHeight(Utils.dpToPx(48));
        return button;
    }

    private static String label(LinkedHashMap<String, String> languages, String code) {
        String value = languages.get(code);
        return value == null ? code : value;
    }
}
