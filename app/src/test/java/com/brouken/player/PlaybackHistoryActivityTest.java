package com.brouken.player;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "uk-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackHistoryActivityTest {
    public static class HistoryActivity extends PlayerActivity {
        @Override public void initializePlayer() { /* No codec or HTTP request needed. */ }
    }

    private static final Uri FIRST = Uri.parse("https://example.test/first.mkv");
    private static final Uri SECOND = Uri.parse("https://example.test/second.mkv");

    @Before public void reset() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().putBoolean("autoUpdate", false).commit();
        app.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
        app.deleteFile("positions");
    }

    private Intent launch(boolean suppliedPosition) {
        Bundle first = new Bundle(); first.putString("uri", FIRST.toString()); first.putLong("clip_start_ms", 2000);
        if (suppliedPosition) first.putLong("position_ms", 0);
        Bundle second = new Bundle(); second.putString("uri", SECOND.toString());
        Bundle playlist = new Bundle(); playlist.putParcelableArray("items", new Bundle[]{first, second});
        return new Intent(Intent.ACTION_VIEW).putExtra("playlist", playlist);
    }

    @Test public void localProgressUsesAbsoluteFileTimeWithoutChangingApiTimesOrUnvisitedItems() {
        try (ActivityController<HistoryActivity> controller = Robolectric.buildActivity(HistoryActivity.class, launch(false)).setup()) {
            HistoryActivity activity = controller.get();
            PlaylistSession session = ReflectionHelpers.getField(activity, "playlistSession");
            session.capture(0, FIRST.toString(), 15_234, 60_000, true);
            remember(activity, 0, 15_234);
            remember(activity, 1, 99_000);
            assertEquals(17_234, new Prefs(activity).getPosition(FIRST));
            assertFalse(Prefs.readPositions(activity).containsKey(SECOND.toString()));
            assertEquals(15_234, session.positionsMs[0]);
            assertEquals(-1, session.positionsMs[1]);
        }
    }

    @Test public void explicitApiZeroWinsAndMissingPositionFallsBackToClipRelativeHistory() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs.rememberPosition(app, FIRST, 17_234);
        try (ActivityController<HistoryActivity> controller = Robolectric.buildActivity(HistoryActivity.class, launch(true)).setup()) {
            assertEquals(0, controller.get().mPrefs.getPosition());
        }
        app.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
        try (ActivityController<HistoryActivity> controller = Robolectric.buildActivity(HistoryActivity.class, launch(false)).setup()) {
            assertEquals(15_234, controller.get().mPrefs.getPosition());
        }
    }

    private void remember(HistoryActivity activity, int index, long position) {
        ReflectionHelpers.callInstanceMethod(activity, "rememberLocalPlaylistPosition",
                ClassParameter.from(int.class, index), ClassParameter.from(long.class, position));
    }
}
