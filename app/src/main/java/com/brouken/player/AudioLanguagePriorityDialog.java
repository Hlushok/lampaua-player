package com.brouken.player;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.List;

/** Compatibility facade; all ordered priority lists now share LanguagePriorityDialog. */
final class AudioLanguagePriorityDialog {
    interface Listener { void onLanguagesPicked(List<String> languages); }

    private AudioLanguagePriorityDialog() { }

    static void show(Context context, int titleRes, int emptyRes, List<String> initial,
                     LinkedHashMap<String, String> allLanguages, List<String> pinned,
                     boolean withSearch, Listener listener) {
        LanguagePriorityDialog.show(context, context.getString(titleRes), emptyRes,
                R.string.pref_language_audio_add, initial, allLanguages, pinned,
                listener::onLanguagesPicked);
    }
}
