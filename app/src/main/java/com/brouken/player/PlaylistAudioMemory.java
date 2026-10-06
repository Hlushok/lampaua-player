package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Remembers dubs, not caller-owned resume positions or per-title subtitle preferences. */
final class PlaylistAudioMemory {
    private final SharedPreferences store;
    PlaylistAudioMemory(Context context) { store = context.getSharedPreferences("playlist_dubs", Context.MODE_PRIVATE); }

    PlaylistApi.Keys audio(String title) {
        JSONObject value = get(title);
        if (value == null) return null;
        PlaylistApi.Keys keys = new PlaylistApi.Keys();
        keys.label = PlaylistApi.trimmed(value.optString("label", null));
        String language = value.optString("language", null);
        if (language != null) keys.languages = java.util.Collections.singletonList(language);
        if (value.has("ordinal")) keys.ordinal = value.optInt("ordinal");
        if (value.has("count")) keys.count = value.optInt("count");
        return keys;
    }
    String voice(String title) { JSONObject value = get(title); return value == null ? null : value.optString("voice", null); }
    private JSONObject get(String key) {
        if (key == null || !store.contains(key)) return null;
        try { return new JSONObject(store.getString(key, "{}")); } catch (Exception ignored) { return null; }
    }
    void remember(String title, PlaylistApi.Keys keys, String voice) {
        if (title == null) return;
        try {
            JSONObject value = get(title); if (value == null) value = new JSONObject();
            if (keys != null) {
                value.put("label", keys.label);
                value.put("language", keys.languages == null || keys.languages.isEmpty() ? null : keys.languages.get(0));
                value.put("ordinal", keys.ordinal); value.put("count", keys.count);
            }
            if (voice != null) value.put("voice", voice);
            JSONArray list = new JSONArray(store.getString("titles", "[]")); List<String> titles = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) if (!title.equals(list.optString(i))) titles.add(list.optString(i));
            titles.add(title);
            SharedPreferences.Editor edit = store.edit().putString(title, value.toString());
            while (titles.size() > 300) edit.remove(titles.remove(0));
            edit.putString("titles", new JSONArray(titles).toString()).apply();
        } catch (Exception ignored) { Utils.log("dub memory could not be saved"); }
    }
    void learn(String language, String label) {
        if (language == null || label == null) return;
        try {
            JSONObject habits = new JSONObject(store.getString("habits:" + language, "{}"));
            habits.put(label, Math.min(1000000, habits.optInt(label) + 1));
            if (habits.length() > 300) {
                String least = null; int score = Integer.MAX_VALUE;
                java.util.Iterator<String> keys = habits.keys();
                while (keys.hasNext()) { String key = keys.next(); if (habits.optInt(key) < score) { score = habits.optInt(key); least = key; } }
                if (least != null) habits.remove(least);
            }
            store.edit().putString("habits:" + language, habits.toString()).apply();
        } catch (Exception ignored) { }
    }
    String habit(String language, List<String> labels) {
        try {
            JSONObject habits = new JSONObject(store.getString("habits:" + language, "{}"));
            String best = null; int score = 0;
            for (String label : labels) {
                java.util.Iterator<String> keys = habits.keys();
                while (keys.hasNext()) {
                    String key = keys.next(); int count = habits.optInt(key);
                    if (count > score && PlaylistTrackRules.labelsMatch(label, key)) { best = label; score = count; }
                }
            }
            return best;
        } catch (Exception ignored) { return null; }
    }
}
