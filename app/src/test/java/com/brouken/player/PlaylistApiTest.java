package com.brouken.player;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.media3.common.MediaItem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PlaylistApiTest {
    Context context;
    @Before public void clear() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
        context.getSharedPreferences("playlist_dubs", 0).edit().clear().commit();
    }
    static Bundle item(String uri) { Bundle item = new Bundle(); item.putString("uri", uri); return item; }
    static Bundle request(Bundle... items) { Bundle root = new Bundle(); root.putParcelableArray("items", items); return root; }
    static PlaylistTrackRules.Track track(int index, String language, String label, boolean selected) {
        return new PlaylistTrackRules.Track(index, language, label, true, selected, false, false, false);
    }
    @Test public void acceptsBothParcelableShapesAndInheritance() throws Exception {
        Bundle first = item("https://a/1.mkv"), second = item("https://a/2.mkv");
        Bundle root = request(first, second); root.putString("title", "Series"); root.putInt("start_index", 1);
        root.putStringArray("headers", new String[]{"Referer", "common", "User-Agent", "player", "odd"});
        second.putStringArray("headers", new String[]{"referer", "episode", null, "skip"});
        PlaylistApi api = new PlaylistApi(root);
        assertEquals("Series", api.items.get(1).title); assertEquals(1, api.startIndex);
        assertEquals("episode", api.items.get(1).headers.get("referer")); assertEquals(2, api.items.get(1).headers.size());
        root.putParcelableArrayList("items", new ArrayList<>(Arrays.asList(first, second)));
        assertEquals(2, new PlaylistApi(root).items.size());
    }
    @Test public void qualitiesSelectedBeforeExplicitUriAndFractionalClips() throws Exception {
        Bundle episode = item("https://a/default");
        Bundle quality = item("https://a/hd"); quality.putString("label", "1080p"); quality.putBoolean("selected", true);
        episode.putParcelableArray("qualities", new Bundle[]{quality}); episode.putDouble("clip_start_sec", 1.25);
        episode.putDouble("clip_end_sec", 21.5); episode.putInt("position_sec", 900);
        PlaylistApi api = new PlaylistApi(request(episode));
        assertEquals("https://a/hd", api.items.get(0).currentSource().uri);
        assertEquals(20250, api.items.get(0).positionMs);
        MediaItem media = api.items.get(0).mediaItem(context);
        assertEquals(1250, media.clippingConfiguration.startPositionMs);
        assertEquals(21500, media.clippingConfiguration.endPositionMs);
    }
    @Test public void voiceSubtitlesReplaceSharedEvenWhenEmpty() throws Exception {
        Bundle shared = item("https://a/shared.srt"), voice = item("https://a/voice"); voice.putString("label", "Voice");
        voice.putParcelableArrayList("subtitles", new ArrayList<Bundle>());
        Bundle episode = new Bundle(); episode.putParcelableArray("voices", new Bundle[]{voice});
        episode.putParcelableArray("subtitles", new Bundle[]{shared});
        assertTrue(new PlaylistApi(request(episode)).items.get(0).currentSubtitles().isEmpty());
    }
    @Test public void malformedOptionalKeysWarnButPositionsStaySeconds() throws Exception {
        Bundle episode = item("https://a/video"); episode.putDouble("audio_index", 2.5);
        episode.putStringArray("audio_languages", new String[]{"uk-UA", "eng", "zz", "ukr"});
        episode.putString("position_sec", "12.9");
        PlaylistApi api = new PlaylistApi(request(episode));
        assertNull(api.items.get(0).audio.index); assertEquals(12000, api.items.get(0).positionMs);
        assertEquals(Arrays.asList("ukr", "eng"), api.items.get(0).audio.languages);
        assertEquals(2, api.warnings.size());
    }
    @Test public void badSubtitleCannotShiftExplicitIndex() throws Exception {
        Bundle episode = item("https://a/video"); episode.putParcelableArray("subtitles", new Bundle[]{new Bundle()});
        assertEquals(1, new PlaylistApi(request(episode)).warnings.size());
        episode.putInt("subtitle_index", 0);
        try { new PlaylistApi(request(episode)); fail(); } catch (PlaylistApi.Invalid expected) { assertTrue(expected.getMessage().contains("subtitle_index")); }
    }
    @Test public void refusesEmptyConflictingVoicesAndWrongStart() throws Exception {
        for (Bundle input : Arrays.asList(request(), request(new Bundle()))) {
            try { new PlaylistApi(input); fail(); } catch (PlaylistApi.Invalid expected) { assertNotNull(expected.getMessage()); }
        }
        Bundle episode = item("https://a/video"); episode.putParcelableArray("voices", new Bundle[0]);
        try { new PlaylistApi(request(episode)); fail(); } catch (PlaylistApi.Invalid expected) { assertTrue(expected.getMessage().contains("both")); }
        Bundle root = request(item("https://a/video")); root.putInt("start_index", -1);
        try { new PlaylistApi(root); fail(); } catch (PlaylistApi.Invalid expected) { assertTrue(expected.getMessage().contains("out of range")); }
    }
    @Test public void emptySubtitleListMeansOffButEmptyAudioDoesNot() throws Exception {
        Bundle root = request(item("https://a/video")); root.putStringArray("subtitle_languages", new String[0]);
        root.putStringArray("audio_languages", new String[0]);
        PlaylistApi api = new PlaylistApi(root); assertTrue(api.text.off); assertFalse(api.audio.off);
        assertNull(api.audio.languages); assertTrue(api.suppressSubtitleDiscovery(0));
    }
    @Test public void periodicReportingIsOffUnlessRequestedAndClampsAtThirty() throws Exception {
        Bundle root = request(item("https://a/video")); assertEquals(0, new PlaylistApi(root).reportInterval);
        root.putInt("report_interval_sec", 1); assertEquals(30, new PlaylistApi(root).reportInterval);
        root.putInt("report_interval_sec", 120); assertEquals(120, new PlaylistApi(root).reportInterval);
    }
    @Test public void headerMappingWorksForPrefetchedVoiceQualityAndSubtitle() throws Exception {
        Bundle a = item("https://a/first"), b = item("https://b/second");
        a.putStringArray("headers", new String[]{"Token", "a"}); b.putStringArray("headers", new String[]{"Token", "b"});
        b.putParcelableArray("subtitles", new Bundle[]{item("https://b/sub")});
        PlaylistApi api = new PlaylistApi(request(a, b));
        assertEquals("b", PlaylistHeaders.forUri(api, 0, "https://b/second").get("token"));
        assertEquals("b", PlaylistHeaders.forUri(api, 0, "https://b/sub").get("token"));
        assertEquals("a", PlaylistHeaders.forUri(api, 0, "https://cdn/chunk.ts").get("token"));
    }
    @Test public void journalDoesNotConfuseUnvisitedResumeInputsWithWatchedProgress() throws Exception {
        Bundle a = item("https://a/1"), b = item("https://a/2"), c = item("https://a/3"); a.putInt("position_sec", 42);
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(request(a, b, c)), new Intent());
        assertArrayEquals(new int[]{-1, -1, -1}, session.result("com.lampaua.player").getIntArrayExtra("positions_sec"));
        session.capture(0, "https://a/1", 43000, 60000, true); session.leave(0, 60000, true);
        session.capture(2, "https://a/3", 8000, -1, true);
        Intent result = session.result("com.lampaua.player");
        assertEquals("com.lampaua.player.result", result.getAction());
        assertArrayEquals(new int[]{60, -1, 8}, result.getIntArrayExtra("positions_sec"));
        assertEquals(0, result.getIntExtra("duration_sec", -1)); assertEquals("user", result.getStringExtra("end_by"));
        Parcelable[] history = result.getParcelableArrayExtra("history"); assertEquals(2, history.length);
        assertEquals(-1, ((Bundle) history[1]).getInt("duration_sec"));
    }
    @Test public void rebuildContinuesVisitAndProcessRestoreRetainsJournal() throws Exception {
        Bundle root = request(item("https://a/1")); Intent launch = new Intent();
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(root), launch);
        session.capture(0, "https://a/hd", 10000, 100000, true);
        session.capture(0, "https://a/voice", 11000, 100000, true); session.qualityLines = 1080; session.persist();
        PlaylistSession restored = new PlaylistSession(context, new PlaylistApi(root), launch);
        assertEquals(1, restored.history.size()); assertEquals(11, restored.positions[0]); assertEquals("https://a/voice", restored.uri);
        assertEquals("https://a/voice", restored.sourceUris[0]); assertEquals(1080, restored.qualityLines);
        restored.capture(0, restored.uri, 12000, 100000, true); assertEquals(1, restored.history.size());
        assertFalse(context.getSharedPreferences("playlist_sessions", 0).getAll().isEmpty());
    }
    @Test public void pausedJumpOpensVisitButAnUnplayedLaunchRemainsCancelled() throws Exception {
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(request(item("https://a/1"), item("https://a/2"))), new Intent());
        session.capture(0, session.uri, 42000, 100000, false);
        Intent cancelled = session.result("com.lampaua.player");
        assertEquals("cancelled", cancelled.getStringExtra("end_by"));
        assertEquals(42, cancelled.getIntExtra("position_sec", -1)); assertEquals(100, cancelled.getIntExtra("duration_sec", -1));
        assertArrayEquals(new int[]{-1, -1}, session.positions); assertEquals(0, session.history.size());
        session.capture(0, session.uri, 43000, 100000, true);
        session.transition(0, 1, "https://a/2", 43000, false, 7000, 60000, false);
        assertEquals(2, session.history.size()); assertArrayEquals(new int[]{43, 7}, session.positions);
        assertEquals("user", session.result("com.lampaua.player").getStringExtra("end_by"));
    }
    @Test public void journalsKeepFiveHundredVisitsWithoutTruncatingPositions() throws Exception {
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(request(item("https://a/1"), item("https://a/2"))), new Intent());
        for (int i = 0; i < 510; i++) { session.capture(i % 2, "https://a/" + i % 2, i * 1000L, -1, true); session.leave(i % 2, i * 1000L, false); }
        assertEquals(500, session.history.size()); assertArrayEquals(new int[]{508, 509}, session.positions);
    }
    @Test public void errorsClearOnlyAfterRealPlaybackAndRefusalsHaveNoWarnings() throws Exception {
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(request(item("https://a/1"))), new Intent());
        session.error = "Network"; assertEquals("error", session.result("com.lampaua.player").getStringExtra("end_by"));
        session.capture(0, session.uri, 0, -1, false); assertNotNull(session.error);
        session.capture(0, session.uri, 1000, -1, true); assertNull(session.error);
        Intent refused = PlaylistSession.rejected("com.lampaua.player", "playlist has no items");
        assertEquals(-1, refused.getIntExtra("index", 0)); assertNull(refused.getData()); assertNull(refused.getStringArrayExtra("warnings"));
        assertEquals(0, refused.getIntArrayExtra("positions_sec").length);
    }
    @Test public void callbackPreservesCallerIdentityAndCollidingExtras() throws Exception {
        Intent identity = new Intent("caller.result").setPackage(context.getPackageName()).putExtra("caller_session", "session").putExtra("index", 99);
        PendingIntent callback = PendingIntent.getBroadcast(context, 19, identity, PendingIntent.FLAG_UPDATE_CURRENT);
        Bundle root = request(item("https://a/1")); root.putParcelable("result_callback", callback);
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(root), new Intent());
        session.capture(0, session.uri, 1000, 10000, true); session.report();
        java.util.List<Intent> broadcasts = org.robolectric.Shadows.shadowOf((Application) context).getBroadcastIntents();
        Intent report = broadcasts.get(broadcasts.size() - 1);
        assertEquals("caller.result", report.getAction()); assertEquals("session", report.getStringExtra("caller_session"));
        assertEquals(99, report.getIntExtra("index", -1)); assertEquals("https://a/1", report.getStringExtra("uri"));
    }
    @Test public void boundedWarningsAndPrivateChoiceKeysStayOutOfPublicReport() throws Exception {
        PlaylistApi api = new PlaylistApi(request(item("https://a/1")));
        for (int i = 0; i < 26; i++) api.warnings.add("warning " + i);
        PlaylistSession session = new PlaylistSession(context, api, new Intent());
        session.selected("audio", Collections.singletonList(track(0, "uk", "Dub", true)),
                new PlaylistTrackRules.Pick(track(0, "uk", "Dub", true), "languages"), false);
        Intent report = session.result("com.lampaua.player");
        assertFalse(report.hasExtra("audio_keys")); assertEquals(20, report.getStringArrayExtra("warnings").length);
        assertEquals("… 7 more", report.getStringArrayExtra("warnings")[19]);
        assertEquals("uk", report.getStringExtra("audio_language"));
    }
    @Test public void tracksWithoutLanguageStillReportKnownOrdinalAndCount() throws Exception {
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(request(item("https://a/1"))), new Intent());
        PlaylistTrackRules.Track unknown = track(0, null, "Dub", true);
        session.selected("audio", Collections.singletonList(unknown), new PlaylistTrackRules.Pick(unknown, "player"), false);
        Intent report = session.result("com.lampaua.player");
        assertNull(report.getStringExtra("audio_language"));
        assertEquals(0, report.getIntExtra("audio_language_ordinal", -1));
        assertEquals(1, report.getIntExtra("audio_language_count", -1));
        PlaylistTrackRules.Track inferred = track(1, "und", "English", true);
        session.selected("audio", Arrays.asList(unknown, inferred), new PlaylistTrackRules.Pick(inferred, "player"), false);
        assertNull(session.result("com.lampaua.player").getStringExtra("audio_language"));
        assertEquals(1, session.result("com.lampaua.player").getIntExtra("audio_language_ordinal", -1));
        assertEquals(2, session.result("com.lampaua.player").getIntExtra("audio_language_count", -1));
    }
    @Test public void episodeTitleDoesNotReplaceSeriesSearchTitle() throws Exception {
        Bundle root = request(item("https://a/1")); root.putString("title", "Series");
        ((Bundle) root.getParcelableArray("items")[0]).putString("episode_title", "Episode 5");
        PlaylistApi.Item episode = new PlaylistApi(root).items.get(0); assertEquals("Series", episode.title);
        assertEquals("", episode.episodeLine()); episode.season = 2; episode.episode = 5; assertEquals("S2 · E5", episode.episodeLine());
    }
    @Test public void dubMemoryIsCappedAndCannotStorePlaybackProgress() throws Exception {
        PlaylistAudioMemory memory = new PlaylistAudioMemory(context); PlaylistApi.Keys keys = new PlaylistApi.Keys();
        keys.label = "Dub"; keys.languages = Collections.singletonList("ukr");
        for (int i = 0; i < 305; i++) memory.remember("imdb:" + i, keys, "Voice");
        assertNull(memory.audio("imdb:0")); assertEquals("Dub", memory.audio("imdb:304").label);
        assertEquals(301, context.getSharedPreferences("playlist_dubs", 0).getAll().size());
        memory.learn("ukr", "LostFilm"); assertEquals("MVO | LostFilm", memory.habit("ukr", Arrays.asList("NewStudio", "MVO | LostFilm")));
    }
}
