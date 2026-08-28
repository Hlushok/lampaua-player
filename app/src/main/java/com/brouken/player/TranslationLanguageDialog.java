package com.brouken.player;

import android.app.AlertDialog;
import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Single-language picker for the automatic subtitle translation target. */
final class TranslationLanguageDialog {
    interface Listener { void onLanguagePicked(String language); }

    private TranslationLanguageDialog() {}

    static void show(Context context, int titleRes, String current,
                     LinkedHashMap<String, String> languages, List<String> pinned,
                     Listener listener) {
        List<String> codes = new ArrayList<>();
        for (String code : pinned) if (!codes.contains(code)) codes.add(code);
        for (String code : languages.keySet()) if (!codes.contains(code)) codes.add(code);
        String[] labels = new String[codes.size()];
        for (int index = 0; index < codes.size(); index++) {
            String label = languages.get(codes.get(index));
            labels[index] = label == null ? codes.get(index) : label;
        }
        int selected = Math.max(0, codes.indexOf(
                LanguagePriorityModel.targetOrUkrainian(current)));
        final int[] picked = {selected};
        new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> picked[0] = which)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!codes.isEmpty()) listener.onLanguagePicked(codes.get(picked[0]));
                })
                .show();
    }
}
