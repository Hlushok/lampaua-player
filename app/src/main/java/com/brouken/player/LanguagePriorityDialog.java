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

/** Shared ordered-list editor used for audio, subtitle and translation priorities. */
final class LanguagePriorityDialog {
    interface Listener { void onLanguagesPicked(List<String> languages); }

    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int REMOVE = 3;

    private final Context context;
    private final int emptyRes;
    private final int addRes;
    private final LinkedHashMap<String, String> allLanguages;
    private final List<String> pinned;
    private final List<String> languages;
    private final LinearLayout list;

    private LanguagePriorityDialog(Context context, int emptyRes, int addRes,
                                   List<String> initial,
                                   LinkedHashMap<String, String> allLanguages,
                                   List<String> pinned) {
        this.context = context;
        this.emptyRes = emptyRes;
        this.addRes = addRes;
        this.allLanguages = allLanguages;
        this.pinned = pinned;
        languages = new ArrayList<>(initial);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = Utils.dpToPx(16);
        list.setPadding(padding, Utils.dpToPx(8), padding, Utils.dpToPx(8));
    }

    static void show(Context context, String title, int emptyRes, int addRes,
                     List<String> initial, LinkedHashMap<String, String> allLanguages,
                     List<String> pinned, Listener listener) {
        LanguagePriorityDialog editor = new LanguagePriorityDialog(
                context, emptyRes, addRes, initial, allLanguages, pinned);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(editor.list);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (ignored, which) -> listener.onLanguagesPicked(editor.languages))
                .create();
        editor.rebuild(-1, 0);
        dialog.setOnShowListener(ignored -> UaDialogStyler.style(context, dialog,
                UaDialogStyler.FocusTarget.POSITIVE, -1));
        dialog.show();
    }

    private void rebuild(int focusRow, int focusChild) {
        list.removeAllViews();
        if (languages.isEmpty()) {
            TextView empty = text(context, context.getString(emptyRes));
            empty.setTextColor(Color.LTGRAY);
            list.addView(empty);
        }
        for (int index = 0; index < languages.size(); index++) {
            int rowIndex = index;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Utils.dpToPx(8), Utils.dpToPx(4),
                    Utils.dpToPx(8), Utils.dpToPx(4));
            row.setBackgroundResource(R.drawable.ua_preference_item_background);

            TextView label = text(context, label(allLanguages, languages.get(index)));
            label.setMaxLines(2);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button up = action(context, "\u2191", R.string.pref_language_audio_move_up);
            up.setEnabled(index > 0);
            up.setFocusable(up.isEnabled());
            up.setOnClickListener(view -> {
                Collections.swap(languages, rowIndex, rowIndex - 1);
                rebuild(rowIndex - 1, UP);
            });
            Button down = action(context, "\u2193", R.string.pref_language_audio_move_down);
            down.setEnabled(index < languages.size() - 1);
            down.setFocusable(down.isEnabled());
            down.setOnClickListener(view -> {
                Collections.swap(languages, rowIndex, rowIndex + 1);
                rebuild(rowIndex + 1, DOWN);
            });
            Button remove = action(context, "\u00D7", R.string.pref_language_audio_remove);
            remove.setOnClickListener(view -> {
                languages.remove(rowIndex);
                rebuild(Math.max(0, Math.min(rowIndex, languages.size() - 1)), REMOVE);
            });
            row.addView(up);
            row.addView(down);
            row.addView(remove);
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Button add = new Button(context);
        add.setAllCaps(false);
        add.setText(addRes);
        add.setTextColor(context.getColor(R.color.ua_gold));
        add.setMinHeight(Utils.dpToPx(48));
        add.setBackgroundResource(R.drawable.ua_dialog_button_background);
        add.setOnClickListener(view -> showPicker());
        list.addView(add);
        restoreFocus(list, focusRow, focusChild, add);
    }

    private void showPicker() {
        List<String> codes = new ArrayList<>();
        for (String code : pinned) {
            if (!languages.contains(code) && !codes.contains(code)) codes.add(code);
        }
        for (String code : allLanguages.keySet()) {
            if (!languages.contains(code) && !codes.contains(code)) codes.add(code);
        }
        String[] labels = new String[codes.size()];
        for (int index = 0; index < codes.size(); index++) {
            labels[index] = label(allLanguages, codes.get(index));
        }
        AlertDialog picker = new AlertDialog.Builder(context)
                .setTitle(addRes)
                .setItems(labels, (dialog, which) -> {
                    languages.add(codes.get(which));
                    rebuild(languages.size() - 1, UP);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        picker.setOnShowListener(ignored -> UaDialogStyler.style(context, picker,
                UaDialogStyler.FocusTarget.LIST, 0));
        picker.show();
    }

    private static void restoreFocus(LinearLayout list, int focusRow, int focusChild,
                                     View fallback) {
        if (focusRow < 0) return;
        View row = focusRow < list.getChildCount() ? list.getChildAt(focusRow) : null;
        View target = null;
        if (row instanceof ViewGroup) {
            for (int child : new int[]{focusChild, UP, DOWN, REMOVE}) {
                View candidate = ((ViewGroup) row).getChildAt(child);
                if (candidate != null && candidate.isFocusable()) {
                    target = candidate;
                    break;
                }
            }
        }
        View focus = target == null ? fallback : target;
        focus.post(focus::requestFocus);
    }

    private static TextView text(Context context, String value) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(17);
        text.setPadding(Utils.dpToPx(12), Utils.dpToPx(12),
                Utils.dpToPx(12), Utils.dpToPx(12));
        return text;
    }

    private static Button action(Context context, String glyph, int description) {
        Button button = new Button(context);
        button.setText(glyph);
        button.setTextSize(20);
        button.setContentDescription(context.getString(description));
        button.setMinWidth(Utils.dpToPx(48));
        button.setMinHeight(Utils.dpToPx(48));
        button.setBackgroundResource(R.drawable.ua_dialog_button_background);
        return button;
    }

    private static String label(LinkedHashMap<String, String> languages, String code) {
        String value = languages.get(code);
        return value == null ? code : value;
    }
}
