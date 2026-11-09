package com.brouken.player;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

import androidx.preference.PreferenceManager;

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
public class M3uImportActivityTest {
    public static class ImportActivity extends PlayerActivity {
        int opened;
        M3uPlaylistLoader.Callback pending;
        Intent requested;
        boolean cancelled;
        @Override boolean handleRoomIntent(Intent intent) {
            // Model a room waiting for its media without opening an external relay connection.
            return com.brouken.player.together.Room.inviteFrom(intent == null ? null : intent.getData()) != null;
        }
        @Override M3uPlaylistLoader.Job newM3uJob(Intent intent, M3uPlaylistLoader.Callback callback) {
            pending = callback; requested = new Intent(intent);
            return new M3uPlaylistLoader.Job(this, intent, MEDIA_HTTP_CLIENT, callback) {
                @Override void start() { }
                @Override void cancel() { cancelled = true; super.cancel(); }
            };
        }
        @Override public void initializePlayer() {
            if ((boolean) ReflectionHelpers.getField(this, "m3uLoading")) super.initializePlayer();
            else opened++; // Real import/lifecycle/playlist wiring; no codecs or network in UI tests.
        }
    }

    @Before public void reset() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().putBoolean("autoUpdate", false).commit();
        app.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
    }

    private Intent launch() {
        return new Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("http://localhost:8090/playlist/show.m3u?hash=abc&fromlast"), "video/*");
    }

    private Bundle playlist() throws Exception {
        Uri uri = launch().getData();
        return M3uPlaylist.parse("#EXTM3U\n#EXTINF:-1,Серія 5\nhttp://localhost:8090/stream/5\n"
                + "#EXTINF:-1,Серія 6\nhttp://localhost:8090/stream/6\n", uri, uri, java.util.Collections.emptyMap());
    }

    @Test public void continueWaitsThenUsesExistingPlaylistWithoutDroppingFromlast() throws Exception {
        try (ActivityController<ImportActivity> controller = Robolectric.buildActivity(ImportActivity.class, launch()).setup()) {
            ImportActivity activity = controller.get();
            assertEquals(0, activity.opened);
            assertEquals("hash=abc&fromlast", activity.requested.getData().getQuery());
            activity.pending.complete(playlist(), false);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, activity.opened);
            PlaylistSession session = ReflectionHelpers.getField(activity, "playlistSession");
            assertNotNull(session);
            assertEquals(2, session.request.items.size());
            assertEquals("Серія 5", session.request.items.get(0).title);
            assertTrue(activity.getIntent().hasExtra(PlaylistApi.EXTRA));
            controller.recreate();
            assertEquals(1, controller.get().opened);
            assertNull(controller.get().pending); // No second fetch after successful import.
        }
    }

    @Test public void hlsFallbackRetainsOriginalUriMimeAndIsNotProbedAgain() {
        Intent hls = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://server/live.m3u8"), "video/*");
        try (ActivityController<ImportActivity> controller = Robolectric.buildActivity(ImportActivity.class, hls).setup()) {
            ImportActivity activity = controller.get();
            activity.pending.complete(null, false);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, activity.opened);
            assertEquals(hls.getData(), activity.mPrefs.mediaUri);
            assertEquals("video/*", activity.getIntent().getType());
            assertNull(ReflectionHelpers.getField(activity, "playlistSession"));
            assertFalse(M3uPlaylist.isCandidate(activity.getIntent()));
        }
    }

    @Test public void lateImportCannotReplaceANewerLaunch() throws Exception {
        try (ActivityController<ImportActivity> controller = Robolectric.buildActivity(ImportActivity.class, launch()).setup()) {
            ImportActivity activity = controller.get();
            M3uPlaylistLoader.Callback old = activity.pending;
            Intent next = new Intent(Intent.ACTION_VIEW, Uri.parse("https://server/new.mkv"));
            ReflectionHelpers.callInstanceMethod(activity, "onNewIntent", ClassParameter.from(Intent.class, next));
            assertTrue(activity.cancelled);
            int opened = activity.opened;
            old.complete(playlist(), false);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(opened, activity.opened);
            assertEquals(next.getData(), activity.mPrefs.mediaUri);
            assertNull(ReflectionHelpers.getField(activity, "playlistSession"));
        }
    }

    @Test public void completedBackgroundImportDoesNotAutoplayUntilForeground() throws Exception {
        try (ActivityController<ImportActivity> controller = Robolectric.buildActivity(ImportActivity.class, launch()).setup()) {
            ImportActivity activity = controller.get();
            controller.pause().stop();
            activity.pending.complete(playlist(), false);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, activity.opened);
            controller.start().resume();
            assertEquals(1, activity.opened);
        }
    }

    @Test public void newerRoomInviteInvalidatesAnOutstandingImport() throws Exception {
        try (ActivityController<ImportActivity> controller = Robolectric.buildActivity(ImportActivity.class, launch()).setup()) {
            ImportActivity activity = controller.get();
            M3uPlaylistLoader.Callback old = activity.pending;
            Intent room = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.test/?room=ABC234"));
            ReflectionHelpers.callInstanceMethod(activity, "onNewIntent", ClassParameter.from(Intent.class, room));
            assertTrue(activity.cancelled);
            old.complete(playlist(), false);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, activity.opened);
            assertNull(ReflectionHelpers.getField(activity, "playlistSession"));
        }
    }

    @Test public void failedImportReportsFailureRatherThanPlayingTheM3uAsVideo() {
        try (ActivityController<ImportActivity> controller = Robolectric.buildActivity(ImportActivity.class, launch()).setup()) {
            ImportActivity activity = controller.get();
            activity.pending.complete(null, true);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(activity.isFinishing());
            assertEquals(0, activity.opened);
        }
    }
}
