package com.brouken.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

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
        list.setPadding(Utils.dpToPx(16), Utils.dpToPx(8),
                Utils.dpToPx(16), Utils.dpToPx(8));
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
            TextView empty = new TextView(context);
            empty.setText(emptyRes);
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(0, Utils.dpToPx(8), 0, Utils.dpToPx(16));
            list.addView(empty);
        }
        for (int index = 0; index < languages.size(); index++) {
            int rowIndex = index;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.ua_preference_item_background);

            TextView label = new TextView(context);
            label.setText(label(allLanguages, languages.get(index)));
            label.setTextColor(Color.WHITE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            label.setPadding(Utils.dpToPx(12), 0, Utils.dpToPx(8), 0);
            label.setMaxLines(2);
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            ColorStateList tint = ColorStateList.valueOf(label.getCurrentTextColor());
            final ImageButton up = iconButton(context, R.drawable.ic_arrow_upward_24dp,
                    context.getString(R.string.pref_language_audio_move_up), tint);
            Utils.setButtonEnabled(context, up, index > 0);
            up.setFocusable(up.isEnabled());
            up.setOnClickListener(view -> {
                Collections.swap(languages, rowIndex, rowIndex - 1);
                rebuild(rowIndex - 1, UP);
            });
            final ImageButton down = iconButton(context, R.drawable.ic_arrow_downward_24dp,
                    context.getString(R.string.pref_language_audio_move_down), tint);
            Utils.setButtonEnabled(context, down, index < languages.size() - 1);
            down.setFocusable(down.isEnabled());
            down.setOnClickListener(view -> {
                Collections.swap(languages, rowIndex, rowIndex + 1);
                rebuild(rowIndex + 1, DOWN);
            });
            final ImageButton remove = iconButton(context, R.drawable.ic_close_24dp,
                    context.getString(R.string.pref_language_audio_remove), tint);
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

        TextView add = new TextView(context);
        add.setText(addRes);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setTextColor(context.getColor(R.color.ua_gold));
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        add.setClickable(true);
        add.setFocusable(true);
        add.setCompoundDrawablePadding(Utils.dpToPx(12));
        add.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_add_24dp, 0, 0, 0);
        add.setCompoundDrawableTintList(ColorStateList.valueOf(context.getColor(R.color.ua_gold)));
        add.setPadding(Utils.dpToPx(12), Utils.dpToPx(12),
                Utils.dpToPx(12), Utils.dpToPx(12));
        add.setBackgroundResource(themeAttr(context, android.R.attr.selectableItemBackground));
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

    private static ImageButton iconButton(Context context, int iconRes,
                                          String description, ColorStateList tint) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setImageTintList(tint);
        button.setContentDescription(description);
        button.setBackground(null);
        button.setForeground(ContextCompat.getDrawable(context,
                themeAttr(context, android.R.attr.selectableItemBackgroundBorderless)));
        button.setPadding(Utils.dpToPx(12), Utils.dpToPx(12),
                Utils.dpToPx(12), Utils.dpToPx(12));
        button.setLayoutParams(new LinearLayout.LayoutParams(
                Utils.dpToPx(48), Utils.dpToPx(48)));
        return button;
    }

    private static int themeAttr(Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId;
    }

    private static String label(LinkedHashMap<String, String> languages, String code) {
        String value = languages.get(code);
        return value == null ? code : value;
    }
}
