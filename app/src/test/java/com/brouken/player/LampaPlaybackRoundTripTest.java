package com.brouken.player;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class LampaPlaybackRoundTripTest {
    private Intent launch() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("https://media/one"))
                .putExtra("title", "Наш серіал").putExtra("return_result", true)
                .putExtra("lampaua.playlist_json", "{\"items\":["
                        + "{\"url\":\"https://media/one\",\"title\":\"Серія 1\",\"season\":1,\"episode\":1,\"position_ms\":12345},"
                        + "{\"url\":\"https://media/two\",\"title\":\"Серія 2\"},"
                        + "{\"url\":\"https://media/three\",\"title\":\"Серія 3\"}]}");
    }
    @Test public void installedLampaJsonUsesTheSameCoreSessionAsTheNewApi() throws Exception {
        Intent launch = launch(); assertTrue(LampaPlaylistBridge.normalize(launch));
        PlaylistApi api = new PlaylistApi(launch.getBundleExtra("playlist"));
        assertEquals("Наш серіал", api.items.get(0).title);
        assertEquals("Серія 1", api.items.get(0).episodeTitle);
        assertEquals(12_345L, api.items.get(0).positionMs);
        assertFalse(api.items.get(1).hasPosition);
        assertFalse(LampaPlaylistBridge.normalize(launch)); // no second conversion on recreation
    }
    @Test public void jsonResultReportsOnlyTheFilesActuallyVisitedInPlaybackOrder() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
        Intent launch = launch(); LampaPlaylistBridge.normalize(launch);
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(launch.getBundleExtra("playlist")), launch);
        session.capture(0, "https://media/one", 25_000, 100_000, true);
        session.transition(0, 2, "https://media/three", 25_000, false, 0, 120_000, true);
        session.capture(2, "https://media/three", 49_000, 120_000, true);
        Intent result = LampaPlaylistBridge.legacyResult(session, session.result(context.getPackageName()));
        JSONArray entries = new JSONArray(result.getStringExtra("lampaua.playback_results"));
        assertEquals(2, entries.length());
        assertEquals("https://media/one", entries.getJSONObject(0).getString("url"));
        assertEquals("https://media/three", entries.getJSONObject(1).getString("url"));
        assertEquals(49_000L, entries.getJSONObject(1).getLong("position"));
        assertFalse(entries.getJSONObject(1).getBoolean("ended"));
        assertEquals("https://media/three", result.getStringExtra("lampaua.current_url"));
        assertEquals(-1, session.positionsMs[1]);
    }
    @Test public void longFlatPositionAndAnExplicitItemZeroKeepTheirMeaning() throws Exception {
        Intent launch = launch().putExtra("position", 4_000_000_000L)
                .putExtra("lampaua.playlist_index", 1);
        LampaPlaylistBridge.normalize(launch);
        assertEquals(4_000_000_000L, new PlaylistApi(launch.getBundleExtra("playlist")).items.get(1).positionMs);
        launch = launch().putExtra("position", 90_000L)
                .putExtra("lampaua.playlist_json", "[{\"url\":\"https://media/one\",\"position_ms\":0}]");
        LampaPlaylistBridge.normalize(launch);
        assertEquals(0L, new PlaylistApi(launch.getBundleExtra("playlist")).items.get(0).positionMs);
    }
}
