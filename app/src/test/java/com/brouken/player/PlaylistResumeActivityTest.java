package com.brouken.player;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.media3.common.Player;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.metadata.MetadataRenderer;

import com.brouken.player.skip.SkipSegment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "uk-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaylistResumeActivityTest {
    /** Real launch/lifecycle/dialog wiring; skip codecs and network after the gate opens. */
    public static class ResumeActivity extends PlayerActivity {
        int opened;
        @Override public void initializePlayer() {
            PlaylistSession session = ReflectionHelpers.getField(this, "playlistSession");
            if (session != null && session.resumePendingIndex >= 0) super.initializePlayer();
            else opened++;
        }
    }

    @Before public void reset() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().putBoolean("autoUpdate", false).commit();
        app.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
        app.deleteFile("positions");
        app.deleteFile("lengths");
    }

    private Intent launch() {
        Bundle item = new Bundle();
        item.putString("uri", "https://example.invalid/episode.mkv");
        item.putInt("position_sec", 123);
        Bundle next = new Bundle();
        next.putString("uri", "https://example.invalid/next.mkv");
        next.putInt("position_sec", 245);
        Bundle playlist = new Bundle();
        playlist.putParcelableArray("items", new Bundle[]{item, next});
        playlist.putBoolean("ask_resume", true);
        return new Intent(Intent.ACTION_VIEW).putExtra("playlist", playlist);
    }

    @Test public void freshLaunchWaitsAndStartOverActuallyUsesZero() {
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, launch()).setup()) {
            ResumeActivity activity = controller.get();
            assertEquals(0, activity.opened);
            AlertDialog dialog = ReflectionHelpers.getField(activity, "playlistResumeDialog");
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, activity.opened);
            assertEquals(0, activity.mPrefs.getPosition());
            assertEquals(-1, ((PlaylistSession) ReflectionHelpers.getField(activity, "playlistSession")).resumePendingIndex);
            activity.initializePlayer();
            assertEquals(2, activity.opened); // No second question on a rebuild.
        }
    }

    @Test public void resumeUsesCallerPositionAndPendingQuestionSurvivesRecreation() {
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, launch()).setup()) {
            controller.recreate();
            ResumeActivity activity = controller.get();
            assertEquals(0, activity.opened);
            AlertDialog dialog = ReflectionHelpers.getField(activity, "playlistResumeDialog");
            assertNotNull(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, activity.opened);
            assertEquals(123_000, activity.mPrefs.getPosition());
        }
    }

    @Test public void manualSelectionAsksBeforeChangingItemAndCancelKeepsPreviousVisit() {
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, launch()).setup()) {
            ResumeActivity activity = controller.get();
            choose(activity, AlertDialog.BUTTON_POSITIVE);
            PlaylistSession session = ReflectionHelpers.getField(activity, "playlistSession");
            session.capture(0, "https://example.invalid/episode.mkv", 150_000, 1_000_000, true);
            ReflectionHelpers.callInstanceMethod(activity, "selectNestedPlaylistItem", ClassParameter.from(int.class, 1));
            assertEquals(1, activity.opened);
            assertEquals(0, session.index);
            assertFalse(session.resumePendingInitial);
            assertEquals(1, session.resumePendingIndex);
            AlertDialog dialog = ReflectionHelpers.getField(activity, "playlistResumeDialog");
            dialog.cancel();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(-1, session.resumePendingIndex);
            assertEquals(0, session.index);
            assertEquals(1, session.history.size());
            assertFalse(activity.isFinishing());
            ReflectionHelpers.callInstanceMethod(activity, "selectNestedPlaylistItem", ClassParameter.from(int.class, 1));
            choose(activity, AlertDialog.BUTTON_POSITIVE);
            assertEquals(3, activity.opened); // Cancel rebuilt the previous (released) item first.
            assertEquals(1, session.index);
            assertEquals(245_000, activity.mPrefs.getPosition());
        }
    }

    @Test public void backCancelsUnplayedLaunchWithoutInventingWatchedHistory() {
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, launch()).setup()) {
            ResumeActivity activity = controller.get();
            AlertDialog dialog = ReflectionHelpers.getField(activity, "playlistResumeDialog");
            dialog.cancel();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(activity.isFinishing());
            assertEquals(0, activity.opened);
            PlaylistSession session = ReflectionHelpers.getField(activity, "playlistSession");
            assertTrue(session.history.isEmpty());
            assertArrayEquals(new long[]{-1, -1}, session.positionsMs);
        }
    }

    @Test public void cancellingManualQuestionAfterRecreationRebuildsPreviousItemPaused() {
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, launch()).setup()) {
            ResumeActivity original = controller.get();
            choose(original, AlertDialog.BUTTON_POSITIVE);
            PlaylistSession session = ReflectionHelpers.getField(original, "playlistSession");
            session.capture(0, "https://example.invalid/episode.mkv", 150_000, 1_000_000, true);
            original.mPrefs.updatePosition(150_000);
            ReflectionHelpers.callInstanceMethod(original, "selectNestedPlaylistItem", ClassParameter.from(int.class, 1));
            controller.recreate();
            ResumeActivity restored = controller.get();
            assertEquals(0, restored.opened);
            AlertDialog dialog = ReflectionHelpers.getField(restored, "playlistResumeDialog");
            assertNotNull(dialog);
            dialog.cancel();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, restored.opened);
            assertEquals(0, ((PlaylistSession) ReflectionHelpers.getField(restored, "playlistSession")).index);
            assertEquals(150_000, restored.mPrefs.getPosition());
            assertTrue(ReflectionHelpers.getField(restored, "sourceSwitchKeepPaused"));
            assertFalse(ReflectionHelpers.getField(restored, "play"));
            assertFalse(restored.isFinishing());
        }
    }

    @Test public void localFullFilePositionBecomesClipRelativeButCallerPositionAlreadyIsRelative() {
        android.net.Uri uri = android.net.Uri.parse("https://example.invalid/episode.mkv");
        Prefs.rememberPosition(RuntimeEnvironment.getApplication(), uri, 200_000);
        Prefs.rememberLength(RuntimeEnvironment.getApplication(), uri, 1_000_000);
        Intent intent = launch();
        Bundle episode = (Bundle) intent.getBundleExtra("playlist").getParcelableArray("items")[0];
        episode.remove("position_sec");
        episode.putDouble("clip_start_sec", 100);
        episode.putDouble("clip_end_sec", 300);
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, intent).setup()) {
            PlaylistSession session = ReflectionHelpers.getField(controller.get(), "playlistSession");
            assertEquals(100_000, session.resumePendingMs);
            choose(controller.get(), AlertDialog.BUTTON_POSITIVE);
            assertEquals(100_000, controller.get().mPrefs.getPosition());
        }
        // An explicit caller position is already relative to the clip; never subtract twice.
        episode.putInt("position_sec", 123);
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, intent).setup()) {
            PlaylistSession session = ReflectionHelpers.getField(controller.get(), "playlistSession");
            assertEquals(123_000, session.resumePendingMs);
        }
    }

    @Test public void absentOptionDoesNotAskAndOldFlatLaunchDoesNotAcquireTheNewGate() {
        Intent nested = launch();
        nested.getBundleExtra("playlist").remove("ask_resume");
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, nested).setup()) {
            assertEquals(1, controller.get().opened);
            assertNull(ReflectionHelpers.getField(controller.get(), "playlistResumeDialog"));
            assertEquals(123_000, controller.get().mPrefs.getPosition());
        }
        Intent flat = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.invalid/flat.mkv"));
        flat.putExtra("position", 45_000);
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, flat).setup()) {
            assertEquals(1, controller.get().opened);
            assertNull(ReflectionHelpers.getField(controller.get(), "playlistSession"));
            assertEquals(45_000, controller.get().mPrefs.getPosition());
        }
    }

    @Test public void creditsAdvanceUsesActualPlayerListenerAndNeverRestoresNextItemsSavedPosition() {
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, launch()).setup()) {
            ResumeActivity activity = controller.get();
            choose(activity, AlertDialog.BUTTON_POSITIVE);
            PlaylistSession session = ReflectionHelpers.getField(activity, "playlistSession");
            // No codecs, preparation or network: exercise Media3's real seek/event ordering on its
            // placeholder timeline, including this activity's actual discontinuity/transition listener.
            ExoPlayer engine = new ExoPlayer.Builder(activity)
                    .setRenderersFactory((handler, video, audio, text, metadata) -> new Renderer[]{
                            new MetadataRenderer(metadata, handler.getLooper())}).build();
            try {
                engine.setMediaItems(ReflectionHelpers.getField(activity, "apiMediaItems"), 0, 900_000);
                PlayerActivity.player = engine;
                engine.addListener(ReflectionHelpers.getField(activity, "playerListener"));
                session.capture(0, "https://example.invalid/episode.mkv", 900_000, 1_000_000, true);
                SkipSegment credits = new SkipSegment(900, 1000, SkipSegment.Type.SKIP);
                credits.reachesEnd = true;
                ReflectionHelpers.callInstanceMethod(activity, "skipSeekTo", ClassParameter.from(SkipSegment.class, credits));
                assertEquals(1, engine.getCurrentMediaItemIndex());
                assertEquals(0, engine.getCurrentPosition());
                assertEquals(-1, session.resumePendingIndex);
                assertEquals(1_000_000, session.positionsMs[0]);
            } finally {
                engine.removeListener(ReflectionHelpers.getField(activity, "playerListener"));
                PlayerActivity.player = null;
                engine.release();
            }
        }
    }

    @Test public void durationUsesActualQualityUriRatherThanTheOriginalSourceCut() {
        Prefs.rememberLength(RuntimeEnvironment.getApplication(),
                android.net.Uri.parse("https://example.invalid/episode.mkv"), 2_000_000);
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, launch()).setup()) {
            ResumeActivity activity = controller.get();
            choose(activity, AlertDialog.BUTTON_POSITIVE);
            PlaylistSession session = ReflectionHelpers.getField(activity, "playlistSession");
            session.capture(0, "https://example.invalid/shorter-cut.mkv", 960_000, 1_000_000, true);
            java.util.List<MediaItem> items = ReflectionHelpers.getField(activity, "apiMediaItems");
            items.set(0, items.get(0).buildUpon().setUri("https://example.invalid/shorter-cut.mkv").build());
            PlaylistResumePolicy.Decision decision = ReflectionHelpers.callInstanceMethod(activity,
                    "nestedResumeDecision", ClassParameter.from(int.class, 0), ClassParameter.from(long.class, 960_000L));
            assertEquals(0, decision.positionMs);
            assertFalse(decision.ask);
        }
    }

    @Test public void millisecondsReachResumeChoiceAndRemainExactAfterActivityRecreation() {
        Intent intent = launch();
        Bundle episode = (Bundle) intent.getBundleExtra("playlist").getParcelableArray("items")[0];
        episode.putLong("position_ms", 123_456);
        try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, intent).setup()) {
            ResumeActivity activity = controller.get();
            PlaylistSession session = ReflectionHelpers.getField(activity, "playlistSession");
            assertEquals(123_456, session.resumePendingMs);
            choose(activity, AlertDialog.BUTTON_POSITIVE);
            assertEquals(123_456, activity.mPrefs.getPosition());
            session.capture(0, session.uri, 234_567, 1_000_123, true);
            activity.mPrefs.updatePosition(234_567);
            controller.recreate();
            activity = controller.get();
            assertEquals(234_567, activity.mPrefs.getPosition());
            assertNull(ReflectionHelpers.getField(activity, "playlistResumeDialog"));
            session = ReflectionHelpers.getField(activity, "playlistSession");
            assertEquals(234_567, session.positionMs);
            assertEquals(1_000_123, session.durationsMs[0]);
            assertEquals(1, session.history.size());
        }
    }

    @Test public void reportSchedulingKeepsFractionalIntervalAndDoesNotOverflowUptime() {
        for (long interval : new long[]{30_500, Long.MAX_VALUE, 0}) {
            Intent intent = launch(); intent.getBundleExtra("playlist").putLong("report_interval_ms", interval);
            try (ActivityController<ResumeActivity> controller = Robolectric.buildActivity(ResumeActivity.class, intent).setup()) {
                ResumeActivity activity = controller.get();
                RecordingHandler handler = new RecordingHandler();
                ReflectionHelpers.setField(activity, "playlistReports", handler);
                long now = SystemClock.uptimeMillis();
                ReflectionHelpers.callInstanceMethod(activity, "schedulePlaylistReport");
                assertEquals(interval == 0 ? -1 : interval == Long.MAX_VALUE ? Long.MAX_VALUE : now + interval,
                        handler.when);
            }
        }
    }

    private static final class RecordingHandler extends Handler {
        long when = -1;
        RecordingHandler() { super(Looper.getMainLooper()); }
        @Override public boolean sendMessageAtTime(Message message, long uptimeMillis) {
            when = uptimeMillis;
            return true;
        }
    }

    private static void choose(ResumeActivity activity, int button) {
        AlertDialog dialog = ReflectionHelpers.getField(activity, "playlistResumeDialog");
        assertNotNull(dialog);
        dialog.getButton(button).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
