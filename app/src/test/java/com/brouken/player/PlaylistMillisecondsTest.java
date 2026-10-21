package com.brouken.player;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

import androidx.media3.common.MediaItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PlaylistMillisecondsTest {
    private Context context;

    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
    }

    private static Bundle item() { return PlaylistApiTest.item("https://example.invalid/episode.mkv"); }
    private static PlaylistApi api(Bundle episode) throws Exception {
        return new PlaylistApi(PlaylistApiTest.request(episode));
    }

    @Test public void millisecondsWinAndFractionalSecondsKeepTheirMilliseconds() throws Exception {
        Bundle episode = item();
        episode.putDouble("position_sec", 12.9879);
        assertEquals(12_987, api(episode).items.get(0).positionMs);
        episode.putString("position_ms", "34567.99");
        assertEquals(34_567, api(episode).items.get(0).positionMs);
        episode.putLong("position_ms", 0);
        assertEquals(0, api(episode).items.get(0).positionMs);
        assertTrue(api(episode).items.get(0).hasPosition);
    }

    @Test public void malformedMillisecondsFallBackToSecondsButValidNegativeDoesNot() throws Exception {
        Bundle episode = item(); episode.putString("position_sec", "12.3456");
        for (Object bad : new Object[]{"bad", "NaN", "Infinity", true, new Bundle()}) {
            if (bad instanceof String) episode.putString("position_ms", (String) bad);
            else if (bad instanceof Boolean) episode.putBoolean("position_ms", (Boolean) bad);
            else episode.putBundle("position_ms", (Bundle) bad);
            assertEquals(12_345, api(episode).items.get(0).positionMs);
        }
        episode.putLong("position_ms", -1);
        assertEquals(0, api(episode).items.get(0).positionMs);
        episode.putString("position_ms", "bad"); episode.putString("position_sec", "bad");
        assertFalse(api(episode).items.get(0).hasPosition);
    }

    @Test public void longMillisecondsAreNotRoundedThroughDoubleOrOverflowed() throws Exception {
        Bundle episode = item();
        episode.putLong("position_ms", 9_007_199_254_740_993L);
        assertEquals(9_007_199_254_740_993L, api(episode).items.get(0).positionMs);
        episode.putLong("position_ms", Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, api(episode).items.get(0).positionMs);
        episode.putString("position_ms", "1e100");
        assertEquals(Long.MAX_VALUE, api(episode).items.get(0).positionMs);
        episode.putString("position_ms", "-1e100");
        assertEquals(0, api(episode).items.get(0).positionMs);
        episode.putString("position_ms", "1e-2147483647");
        assertEquals(0, api(episode).items.get(0).positionMs);
        episode.putString("position_ms", "-1e-2147483647");
        assertEquals(0, api(episode).items.get(0).positionMs);
    }

    @Test public void clipUnitsCanBeMixedAndMillisecondsWinIndependently() throws Exception {
        Bundle episode = item();
        episode.putDouble("clip_start_sec", 5.5); episode.putLong("clip_start_ms", 1251);
        episode.putDouble("clip_end_sec", 21.501); episode.putString("clip_end_ms", "bad");
        episode.putLong("position_ms", 90_000);
        PlaylistApi.Item parsed = api(episode).items.get(0);
        MediaItem media = parsed.mediaItem(context);
        assertEquals(1251, media.clippingConfiguration.startPositionMs);
        assertEquals(21_501, media.clippingConfiguration.endPositionMs);
        assertEquals(20_250, parsed.positionMs);
        episode.putLong("clip_start_ms", 0); episode.putLong("clip_end_ms", 1001);
        media = api(episode).items.get(0).mediaItem(context);
        assertEquals(0, media.clippingConfiguration.startPositionMs);
        assertEquals(1001, media.clippingConfiguration.endPositionMs);
    }

    @Test public void reportIntervalKeepsMillisecondsAndTheThirtySecondMinimum() throws Exception {
        Bundle root = PlaylistApiTest.request(item()); root.putInt("report_interval_sec", 120);
        root.putString("report_interval_ms", "30500.99");
        assertEquals(30_500L, (long) ReflectionHelpers.getField(new PlaylistApi(root), "reportIntervalMs"));
        root.putLong("report_interval_ms", 1);
        assertEquals(30_000L, (long) ReflectionHelpers.getField(new PlaylistApi(root), "reportIntervalMs"));
        root.putLong("report_interval_ms", 0);
        assertEquals(0L, (long) ReflectionHelpers.getField(new PlaylistApi(root), "reportIntervalMs"));
        root.putString("report_interval_ms", "bad"); root.putDouble("report_interval_sec", 31.2349);
        assertEquals(31_234L, (long) ReflectionHelpers.getField(new PlaylistApi(root), "reportIntervalMs"));
    }

    @Test public void oversizedNumericRepresentationsFallBackBeforeArbitraryPrecisionParsing() throws Exception {
        Bundle episode = item(); episode.putString("position_sec", "12.345");
        String huge = new String(new char[300]).replace('\0', '9');
        episode.putString("position_ms", huge);
        assertEquals(12_345, api(episode).items.get(0).positionMs);
        episode.putSerializable("position_ms", new java.math.BigDecimal(huge));
        assertEquals(12_345, api(episode).items.get(0).positionMs);
        episode.putSerializable("position_ms", new java.math.BigInteger(huge));
        assertEquals(12_345, api(episode).items.get(0).positionMs);
    }

    @Test public void hugeClipBoundsDoNotOverflowMedia3MicrosecondConversion() throws Exception {
        Bundle episode = item(); episode.putLong("clip_end_ms", Long.MAX_VALUE);
        PlaylistApi.Item parsed = api(episode).items.get(0);
        assertEquals(Long.MAX_VALUE, parsed.clipEndMs); // The API model is still exact.
        MediaItem media = parsed.mediaItem(context);
        assertEquals(Long.MAX_VALUE / 1000, media.clippingConfiguration.endPositionMs);
        episode.putLong("clip_start_ms", Long.MAX_VALUE - 1);
        media = api(episode).items.get(0).mediaItem(context);
        assertEquals(Long.MAX_VALUE / 1000, media.clippingConfiguration.startPositionMs);
        assertEquals(Long.MAX_VALUE / 1000, PlaylistApi.engineTimeMs(Long.MAX_VALUE));
        assertEquals(12_345, PlaylistApi.engineTimeMs(12_345));
        assertEquals(androidx.media3.common.C.TIME_UNSET,
                PlaylistApi.engineTimeMs(androidx.media3.common.C.TIME_UNSET));
    }

    @Test public void resultsAndHistoryCarryBothUnitsWithoutLosingUnvisitedSentinels() throws Exception {
        PlaylistSession session = new PlaylistSession(context,
                new PlaylistApi(PlaylistApiTest.request(item(), item(), item())), new Intent());
        session.capture(0, session.uri, 12_987, 90_765, true);
        session.transition(0, 2, "https://example.invalid/third", 13_456, false, 23_999, -1, false);
        Intent report = session.result(context.getPackageName());
        assertEquals(23_999L, report.getLongExtra("position_ms", -1));
        assertEquals(23, report.getIntExtra("position_sec", -1));
        assertEquals(0L, report.getLongExtra("duration_ms", -1));
        assertArrayEquals(new long[]{13_456, -1, 23_999}, report.getLongArrayExtra("positions_ms"));
        assertArrayEquals(new int[]{13, -1, 23}, report.getIntArrayExtra("positions_sec"));
        Parcelable[] visits = report.getParcelableArrayExtra("history");
        assertEquals(2, visits.length);
        assertEquals(13_456L, ((Bundle) visits[0]).getLong("position_ms"));
        assertEquals(90_765L, ((Bundle) visits[0]).getLong("duration_ms"));
        assertEquals(90, ((Bundle) visits[0]).getInt("duration_sec"));
        assertEquals(-1L, ((Bundle) visits[1]).getLong("duration_ms"));
        assertEquals(-1, ((Bundle) visits[1]).getInt("duration_sec"));
        session.leave(2, 25_501, false);
        assertEquals(25_501L, session.result(context.getPackageName()).getLongExtra("position_ms", -1));
    }

    @Test public void exactSessionPositionsAndPendingResumeSurvivePersistence() throws Exception {
        Bundle episode = item(); episode.putLong("position_ms", 31_234);
        Intent launch = new Intent(); PlaylistApi api = api(episode);
        PlaylistSession session = new PlaylistSession(context, api, launch);
        session.capture(0, session.uri, 32_987, 90_765, true);
        session.resumeInitialized = true; session.pendingResume(0, 32_987, false);
        PlaylistSession restored = new PlaylistSession(context, api, launch);
        assertEquals(32_987L, restored.result(context.getPackageName()).getLongExtra("position_ms", -1));
        assertEquals(90_765L, restored.result(context.getPackageName()).getLongExtra("duration_ms", -1));
        assertArrayEquals(new long[]{32_987}, restored.result(context.getPackageName()).getLongArrayExtra("positions_ms"));
        assertEquals(32_987L, restored.resumePendingMs);
        assertEquals(1, restored.history.size());
    }

    @Test public void legacySecondBasedJournalMigratesWithoutChangingIdentityOrVisits() throws Exception {
        Bundle legacy = new Bundle();
        legacy.putInt("index", 0); legacy.putString("uri", "https://example.invalid/quality");
        legacy.putIntArray("positions", new int[]{12, -1});
        legacy.putIntArray("durations", new int[]{90, -1});
        legacy.putInt("positionSec", 13); legacy.putInt("durationSec", 90);
        legacy.putBoolean("played", true); legacy.putInt("visit", 0);
        Bundle visit = new Bundle(); visit.putInt("index", 0); visit.putLong("started_at", 1234);
        visit.putInt("position_sec", 12); visit.putInt("duration_sec", 90);
        java.util.ArrayList<Bundle> visits = new java.util.ArrayList<>(); visits.add(visit);
        legacy.putParcelableArrayList("history", visits);
        storeLegacy("old-session", legacy);
        Intent launch = new Intent().putExtra(PlaylistSession.ID, "old-session");
        PlaylistSession restored = new PlaylistSession(context,
                new PlaylistApi(PlaylistApiTest.request(item(), item())), launch);
        assertEquals("old-session", restored.id);
        Intent report = restored.result(context.getPackageName());
        assertEquals(13_000L, report.getLongExtra("position_ms", -1));
        assertArrayEquals(new long[]{12_000, -1}, report.getLongArrayExtra("positions_ms"));
        Bundle migrated = (Bundle) report.getParcelableArrayExtra("history")[0];
        assertEquals(12_000L, migrated.getLong("position_ms"));
        assertEquals(90_000L, migrated.getLong("duration_ms"));
        assertEquals(1234, migrated.getLong("started_at"));
        restored.capture(0, restored.uri, 13_987, 90_765, true); restored.persist();
        restored = new PlaylistSession(context, new PlaylistApi(PlaylistApiTest.request(item(), item())), launch);
        assertEquals(1, restored.history.size());
        assertEquals(13_987L, restored.result(context.getPackageName()).getLongExtra("position_ms", -1));
    }

    @Test public void completionAndRefusalExposeBothUnitShapes() throws Exception {
        PlaylistSession session = new PlaylistSession(context, api(item()), new Intent());
        session.capture(0, session.uri, 10_456, 20_999, true); session.leave(0, 20_998, true);
        Intent report = session.result(context.getPackageName());
        assertArrayEquals(new long[]{20_999}, report.getLongArrayExtra("positions_ms"));
        assertArrayEquals(new int[]{20}, report.getIntArrayExtra("positions_sec"));
        Intent refused = PlaylistSession.rejected(context.getPackageName(), "invalid");
        assertEquals(0L, refused.getLongExtra("position_ms", -1));
        assertEquals(0L, refused.getLongExtra("duration_ms", -1));
        assertArrayEquals(new long[0], refused.getLongArrayExtra("positions_ms"));
    }

    @Test public void secondProjectionSaturatesWithoutDamagingPreciseOutput() throws Exception {
        PlaylistSession session = new PlaylistSession(context, api(item()), new Intent());
        session.capture(0, session.uri, Long.MAX_VALUE - 1, Long.MAX_VALUE, true);
        Intent report = session.result(context.getPackageName());
        assertEquals(Integer.MAX_VALUE, report.getIntExtra("position_sec", -1));
        assertEquals(Long.MAX_VALUE - 1, report.getLongExtra("position_ms", -1));
        assertArrayEquals(new long[]{Long.MAX_VALUE - 1}, report.getLongArrayExtra("positions_ms"));
        session.persist();
        report.getLongArrayExtra("positions_ms")[0] = 0;
        assertArrayEquals(new long[]{Long.MAX_VALUE - 1}, session.result(context.getPackageName()).getLongArrayExtra("positions_ms"));
    }

    @Test public void callbacksRetainCallerIdentityAndExactProgress() throws Exception {
        Intent identity = new Intent("caller.ms.result").setPackage(context.getPackageName()).putExtra("caller_session", "id");
        PendingIntent callback = PendingIntent.getBroadcast(context, 99, identity, PendingIntent.FLAG_UPDATE_CURRENT);
        Bundle root = PlaylistApiTest.request(item()); root.putParcelable("result_callback", callback);
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(root), new Intent());
        session.capture(0, session.uri, 12_987, 90_765, true); session.report();
        java.util.List<Intent> sent = Shadows.shadowOf((Application) context).getBroadcastIntents();
        Intent report = sent.get(sent.size() - 1);
        assertEquals("caller.ms.result", report.getAction());
        assertEquals("id", report.getStringExtra("caller_session"));
        assertEquals(12_987L, report.getLongExtra("position_ms", -1));
        assertEquals(12, report.getIntExtra("position_sec", -1));
    }

    private void storeLegacy(String id, Bundle state) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(state);
            context.getSharedPreferences("playlist_sessions", 0).edit().putString(id,
                    Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)).commit();
        } finally { parcel.recycle(); }
    }
}
