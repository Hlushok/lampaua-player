package com.brouken.player;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PlaybackIntegrationActivityTest {
    public static class Screen extends PlayerActivity { @Override public void initializePlayer() { } }
    @Before public void reset() {
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit()
                .clear().putBoolean("autoUpdate", false).commit();
    }
    private Intent launch() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("https://media/episode"))
                .putExtra("return_result", true).putExtra("lampaua.playlist_json",
                        "[{\"url\":\"https://media/episode\",\"title\":\"Фільм\"}]");
    }
    @Test public void backActuallyReturnsTheJsonProgressExpectedByInstalledLampa() throws Exception {
        try (ActivityController<Screen> controller = Robolectric.buildActivity(Screen.class, launch()).setup()) {
            Screen activity = controller.get(); PlayerActivity.isTvBox = false;
            PlayerActivity.player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                    new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getCurrentPosition": return 43_210L;
                            case "getDuration": return 120_000L;
                            case "getCurrentMediaItem": return MediaItem.fromUri("https://media/episode");
                            case "isPlaying": return true;
                        }
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                    });
            activity.onBackPressed();
            assertTrue(activity.isFinishing());
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            Intent result = Shadows.shadowOf(activity).getResultIntent();
            JSONArray values = new JSONArray(result.getStringExtra("lampaua.playback_results"));
            assertEquals(1, values.length());
            assertEquals(43_210L, values.getJSONObject(0).getLong("position"));
            assertEquals(120_000L, values.getJSONObject(0).getLong("duration"));
            assertEquals("https://media/episode", values.getJSONObject(0).getString("url"));
            PlayerActivity.player = null;
        }
    }
    @Test public void anotherEpisodeDropsFileIdsAndOverridesButKeepsLanguagePreferences() {
        try (ActivityController<Screen> controller = Robolectric.buildActivity(Screen.class, launch()).setup()) {
            Screen activity = controller.get();
            DefaultTrackSelector selector = new DefaultTrackSelector(activity);
            ReflectionHelpers.setField(activity, "trackSelector", selector);
            TrackGroup audio = new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setId("one").build());
            TrackGroup text = new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).setId("two").build());
            selector.setParameters(selector.buildUponParameters().setPreferredAudioLanguage("uk")
                    .setOverrideForType(new TrackSelectionOverride(audio, 0))
                    .setOverrideForType(new TrackSelectionOverride(text, 0)));
            activity.mPrefs.audioTrackId = "one"; activity.mPrefs.subtitleTrackId = "two";
            activity.resetTrackSelectionForItem("first", Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
            activity.resetTrackSelectionForItem("first", Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT);
            assertEquals(2, selector.getParameters().overrides.size());
            activity.resetTrackSelectionForItem("second", Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
            assertNull(activity.mPrefs.audioTrackId); assertNull(activity.mPrefs.subtitleTrackId);
            assertTrue(selector.getParameters().overrides.isEmpty());
            assertEquals("uk", selector.getParameters().preferredAudioLanguages.get(0));
            selector.release();
        }
    }
}
