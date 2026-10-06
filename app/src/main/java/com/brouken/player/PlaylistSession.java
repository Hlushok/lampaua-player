package com.brouken.player;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Base64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Launch-scoped progress journal, deliberately separate from the player's resume database. */
final class PlaylistSession {
    static final String ID = "com.lampaua.player.playlist_session";
    final PlaylistApi request;
    final String id;
    final Context context;
    final int[] positions, durations;
    final String[] sourceUris;
    final List<Bundle> history = new ArrayList<>();
    final Bundle[] choices;
    final boolean[] audioApplied, textApplied;
    PlaylistApi.Keys viewerAudio, viewerText;
    String viewerVoice, error;
    int index, visitIndex = -1;
    int qualityLines;
    int positionSec, durationSec;
    boolean played, complete;
    String uri;

    PlaylistSession(Context context, PlaylistApi request, Intent launch) {
        this.context = context.getApplicationContext();
        this.request = request;
        String incoming = launch.getStringExtra(ID);
        id = incoming == null ? UUID.randomUUID().toString() : incoming;
        launch.putExtra(ID, id);
        positions = new int[request.items.size()]; Arrays.fill(positions, -1);
        durations = new int[positions.length]; Arrays.fill(durations, -1);
        sourceUris = new String[positions.length];
        choices = new Bundle[positions.length];
        audioApplied = new boolean[positions.length]; textApplied = new boolean[positions.length];
        for (int i = 0; i < choices.length; i++) choices[i] = new Bundle();
        index = request.startIndex;
        uri = request.items.get(index).currentSource().uri;
        positionSec = seconds(request.items.get(index).positionMs);
        restore();
    }

    void capture(int current, String currentUri, long positionMs, long durationMs, boolean playing) {
        if (current < 0 || current >= positions.length) return;
        index = current; uri = currentUri; sourceUris[current] = currentUri;
        positionSec = seconds(positionMs); durationSec = durationMs > 0 ? seconds(durationMs) : 0;
        if (playing) {
            error = null; played = true;
            if (visitIndex != current) openVisit(current);
        }
        if (visitIndex == current) {
            positions[current] = seconds(positionMs);
            if (durationMs > 0) durations[current] = seconds(durationMs);
            updateVisit();
        }
    }

    void leave(int oldIndex, long positionMs, boolean finished) {
        if (oldIndex < 0 || oldIndex >= positions.length || positions[oldIndex] < 0) return;
        positions[oldIndex] = finished && durations[oldIndex] >= 0
                ? durations[oldIndex] : seconds(positionMs);
        if (visitIndex == oldIndex) { updateVisit(); visitIndex = -1; }
        if (index == oldIndex) positionSec = positions[oldIndex];
    }

    void transition(int oldIndex, int nextIndex, String nextUri, long oldPositionMs,
                    boolean finished, long nextPositionMs, long nextDurationMs, boolean playing) {
        leave(oldIndex, oldPositionMs, finished);
        // Once playback has begun, a paused manual jump still opens a new visit.
        if (played && nextIndex >= 0 && nextIndex < positions.length) openVisit(nextIndex);
        capture(nextIndex, nextUri, nextPositionMs, nextDurationMs, playing);
    }

    private void openVisit(int value) {
        Bundle visit = new Bundle();
        visit.putInt("index", value); visit.putLong("started_at", System.currentTimeMillis() / 1000);
        visit.putInt("position_sec", Math.max(0, positions[value]));
        visit.putInt("duration_sec", durations[value]);
        history.add(visit);
        while (history.size() > 500) history.remove(0);
        visitIndex = value;
    }

    private void updateVisit() {
        if (history.isEmpty() || visitIndex < 0) return;
        Bundle visit = history.get(history.size() - 1);
        visit.putInt("position_sec", positions[visitIndex]);
        visit.putInt("duration_sec", durations[visitIndex]);
    }

    Intent result(String packageName) {
        Intent result = new Intent(packageName + ".result");
        if (uri != null) result.setData(Uri.parse(uri));
        Bundle extras = new Bundle(choices[index]);
        extras.remove("audio_keys"); extras.remove("subtitle_keys");
        extras.putString("uri", uri); extras.putInt("index", index);
        extras.putInt("position_sec", positionSec);
        extras.putInt("duration_sec", durationSec);
        extras.putIntArray("positions_sec", positions.clone());
        extras.putParcelableArray("history", history.toArray(new Bundle[0]));
        extras.putString("end_by", error != null ? "error" : complete ? "completion" : played ? "user" : "cancelled");
        if (error != null) extras.putString("error_message", error);
        PlaylistApi.Item item = request.items.get(index);
        if (!item.voices.isEmpty()) extras.putString("voice_label", item.currentSource().label);
        List<String> warnings = request.warnings;
        if (!warnings.isEmpty()) {
            List<String> bounded = new ArrayList<>(warnings.subList(0, Math.min(20, warnings.size())));
            if (warnings.size() > 20) bounded.set(19, "… " + (warnings.size() - 19) + " more");
            extras.putStringArray("warnings", bounded.toArray(new String[0]));
        }
        result.putExtras(extras);
        return result;
    }

    static Intent rejected(String packageName, String message) {
        return new Intent(packageName + ".result").putExtra("index", -1)
                .putExtra("position_sec", 0).putExtra("duration_sec", 0)
                .putExtra("positions_sec", new int[0]).putExtra("history", new Bundle[0])
                .putExtra("uri", (String) null).putExtra("end_by", "error").putExtra("error_message", message);
    }

    void report() { persist(); send(context, request.callback, result(context.getPackageName())); }

    static void send(Context context, PendingIntent callback, Intent result) {
        if (callback == null) return;
        // PendingIntent fills only missing fields; caller extras and identity remain authoritative.
        try { callback.send(context, 0, result); }
        catch (PendingIntent.CanceledException ignored) { Utils.log("playlist callback cancelled"); }
    }

    PlaylistApi.Keys cached(String prefix) { return readKeys(choices[index].getBundle(prefix + "_keys")); }
    boolean applied(boolean text) { return (text ? textApplied : audioApplied)[index]; }

    void selected(String prefix, List<PlaylistTrackRules.Track> tracks, PlaylistTrackRules.Pick pick,
                  boolean viewer) {
        if (pick == null) return;
        Bundle choice = choices[index];
        PlaylistApi.Keys keys = new PlaylistApi.Keys();
        String language = null, label = null;
        if (pick.track != null) {
            PlaylistTrackRules.Track track = pick.track;
            keys.index = track.index; keys.label = track.label;
            if (track.language != null) keys.languages = java.util.Collections.singletonList(track.language);
            int ordinal = 0, count = 0;
            for (PlaylistTrackRules.Track candidate : tracks) {
                if (java.util.Objects.equals(track.language, candidate.language)) {
                    if (candidate.index == track.index) ordinal = count;
                    count++;
                }
            }
            keys.ordinal = ordinal; keys.count = count;
            language = PlaylistTrackRules.language(track.declaredLanguage) == null ? null : track.declaredLanguage;
            label = track.label != null && !track.label.matches("(?i)[a-z]{2,3}\\d+") ? track.label : null;
            int reportedOrdinal = 0, reportedCount = 0;
            String declared = PlaylistTrackRules.language(track.declaredLanguage);
            for (PlaylistTrackRules.Track candidate : tracks) {
                if (java.util.Objects.equals(declared, PlaylistTrackRules.language(candidate.declaredLanguage))) {
                    if (candidate.index == track.index) reportedOrdinal = reportedCount;
                    reportedCount++;
                }
            }
            choice.putInt(prefix + "_language_ordinal", reportedOrdinal);
            choice.putInt(prefix + "_language_count", reportedCount);
            choice.remove(prefix + "_index");
        } else {
            keys.off = true; keys.index = -1;
            choice.remove(prefix + "_language_ordinal"); choice.remove(prefix + "_language_count");
            choice.putInt(prefix + "_index", -1);
        }
        choice.putString(prefix + "_language", language); choice.putString(prefix + "_label", label);
        choice.putString(prefix + "_chosen_by", viewer ? "viewer" : pick.by);
        choice.putBundle(prefix + "_keys", writeKeys(keys));
        if ("subtitle".equals(prefix)) { textApplied[index] = true; if (viewer) viewerText = withoutIndex(keys); }
        else { audioApplied[index] = true; if (viewer) viewerAudio = withoutIndex(keys); }
        persist();
    }

    void externalSubtitle(String label, boolean viewer) {
        Bundle choice = choices[index];
        choice.putString("subtitle_label", label); choice.putString("subtitle_language", null);
        choice.putString("subtitle_chosen_by", viewer ? "viewer" : "player");
        choice.remove("subtitle_index"); choice.remove("subtitle_language_ordinal"); choice.remove("subtitle_language_count");
        PlaylistApi.Keys keys = new PlaylistApi.Keys(); keys.label = label;
        choice.putBundle("subtitle_keys", writeKeys(keys));
        textApplied[index] = true;
        if (viewer) viewerText = keys;
        persist();
    }

    void resetForVoice(boolean ownSubtitles) {
        audioApplied[index] = false; choices[index].remove("audio_keys");
        if (ownSubtitles) { textApplied[index] = false; choices[index].remove("subtitle_keys"); }
    }

    private static PlaylistApi.Keys withoutIndex(PlaylistApi.Keys keys) {
        PlaylistApi.Keys copy = readKeys(writeKeys(keys));
        if (!copy.off) copy.index = null;
        return copy;
    }
    static Bundle writeKeys(PlaylistApi.Keys keys) {
        Bundle bundle = new Bundle();
        if (keys == null) return bundle;
        if (keys.index != null) bundle.putInt("index", keys.index);
        if (keys.ordinal != null) bundle.putInt("ordinal", keys.ordinal);
        if (keys.count != null) bundle.putInt("count", keys.count);
        bundle.putString("label", keys.label); bundle.putBoolean("off", keys.off);
        if (keys.languages != null) bundle.putStringArrayList("languages", new ArrayList<>(keys.languages));
        return bundle;
    }
    static PlaylistApi.Keys readKeys(Bundle bundle) {
        if (bundle == null) return null;
        PlaylistApi.Keys keys = new PlaylistApi.Keys();
        if (bundle.containsKey("index")) keys.index = bundle.getInt("index");
        if (bundle.containsKey("ordinal")) keys.ordinal = bundle.getInt("ordinal");
        if (bundle.containsKey("count")) keys.count = bundle.getInt("count");
        keys.label = bundle.getString("label"); keys.off = bundle.getBoolean("off");
        keys.languages = bundle.getStringArrayList("languages"); return keys;
    }

    void persist() {
        Bundle state = new Bundle();
        state.putInt("index", index); state.putString("uri", uri); state.putString("error", error);
        state.putStringArray("sourceUris", sourceUris); state.putInt("qualityLines", qualityLines);
        state.putInt("positionSec", positionSec); state.putInt("durationSec", durationSec);
        state.putBoolean("played", played); state.putBoolean("complete", complete);
        state.putInt("visit", visitIndex); state.putIntArray("positions", positions); state.putIntArray("durations", durations);
        state.putParcelableArrayList("history", new ArrayList<>(history)); state.putParcelableArray("choices", choices);
        state.putBooleanArray("audioApplied", audioApplied); state.putBooleanArray("textApplied", textApplied);
        state.putBundle("viewerAudio", writeKeys(viewerAudio)); state.putBundle("viewerText", writeKeys(viewerText));
        state.putString("viewerVoice", viewerVoice);
        int[] voices = new int[positions.length];
        for (int i = 0; i < voices.length; i++) voices[i] = request.items.get(i).voiceIndex;
        state.putIntArray("voices", voices);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(state);
            SharedPreferences store = context.getSharedPreferences("playlist_sessions", Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = store.edit().putString(id, Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP));
            // Bound old launch journals independently of normal playback history.
            List<String> ids = new ArrayList<>(Arrays.asList(store.getString("ids", "").split(",")));
            ids.remove(""); ids.remove(id); ids.add(id);
            while (ids.size() > 5) edit.remove(ids.remove(0));
            edit.putString("ids", PlaylistTrackRules.join(",", ids)).apply();
        } finally { parcel.recycle(); }
    }

    private void restore() {
        String encoded = context.getSharedPreferences("playlist_sessions", Context.MODE_PRIVATE).getString(id, null);
        if (encoded == null) return;
        Parcel parcel = Parcel.obtain();
        try {
            byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP); parcel.unmarshall(bytes, 0, bytes.length); parcel.setDataPosition(0);
            Bundle state = parcel.readBundle(PlaylistSession.class.getClassLoader());
            int[] saved = state.getIntArray("positions"), lengths = state.getIntArray("durations");
            if (saved == null || saved.length != positions.length || lengths == null || lengths.length != saved.length) return;
            System.arraycopy(saved, 0, positions, 0, saved.length); System.arraycopy(lengths, 0, durations, 0, lengths.length);
            index = Math.max(0, Math.min(positions.length - 1, state.getInt("index")));
            uri = state.getString("uri"); error = state.getString("error");
            String[] sources = state.getStringArray("sourceUris");
            if (sources != null && sources.length == sourceUris.length) System.arraycopy(sources, 0, sourceUris, 0, sources.length);
            qualityLines = state.getInt("qualityLines");
            positionSec = state.getInt("positionSec", Math.max(0, positions[index]));
            durationSec = state.getInt("durationSec", Math.max(0, durations[index]));
            played = state.getBoolean("played"); complete = state.getBoolean("complete"); visitIndex = state.getInt("visit", -1);
            ArrayList<Bundle> visits = state.getParcelableArrayList("history");
            if (visits != null) history.addAll(visits.subList(Math.max(0, visits.size() - 500), visits.size()));
            android.os.Parcelable[] savedChoices = state.getParcelableArray("choices");
            if (savedChoices != null && savedChoices.length == choices.length) {
                for (int i = 0; i < choices.length; i++) if (savedChoices[i] instanceof Bundle) choices[i] = (Bundle) savedChoices[i];
            }
            boolean[] a = state.getBooleanArray("audioApplied"), t = state.getBooleanArray("textApplied");
            if (a != null && a.length == audioApplied.length) System.arraycopy(a, 0, audioApplied, 0, a.length);
            if (t != null && t.length == textApplied.length) System.arraycopy(t, 0, textApplied, 0, t.length);
            viewerAudio = readKeys(state.getBundle("viewerAudio")); viewerText = readKeys(state.getBundle("viewerText"));
            viewerVoice = state.getString("viewerVoice");
            int[] voices = state.getIntArray("voices");
            if (voices != null && voices.length == positions.length) for (int i = 0; i < voices.length; i++) {
                PlaylistApi.Item item = request.items.get(i);
                if (voices[i] >= 0 && voices[i] < item.voices.size()) item.voiceIndex = voices[i];
            }
        } catch (RuntimeException ignored) { Utils.log("playlist journal unavailable; starting from caller state"); }
        finally { parcel.recycle(); }
    }

    static int seconds(long milliseconds) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, milliseconds / 1000));
    }
}
