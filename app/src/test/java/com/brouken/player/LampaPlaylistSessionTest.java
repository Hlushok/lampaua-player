package com.brouken.player;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LampaPlaylistSessionTest {
    @Test public void rootSeriesTitleStaysSeparateFromEpisodeTitle() throws Exception {
        JSONObject launch = new JSONObject()
                .put("title", "Назва серіалу")
                .put("items", new org.json.JSONArray().put(
                        new JSONObject().put("title", "Серія 5")));

        String title = LampaPlaylist.playlistTitle(launch);
        JSONObject snapshot = new JSONObject();
        LampaPlaylist.putPlaylistTitle(snapshot, title);

        assertEquals("Назва серіалу", LampaPlaylist.playlistTitle(snapshot));
        assertEquals("Серія 5",
                launch.getJSONArray("items").getJSONObject(0).getString("title"));
    }

    @Test public void sessionItemPreservesResolvedPlaybackState() throws Exception {
        LampaPlaylist.Item source = new LampaPlaylist.Item();
        source.id = "episode-2";
        source.url = "https://cdn.example/video-1080.m3u8";
        source.resolverUrl = "https://resolver.example/episode-2";
        source.title = "Episode 2";
        source.imdbId = "tt1234567";
        source.season = 1;
        source.episode = 2;
        source.positionMs = 42_500;
        source.headers.put("Referer", "https://lampa.example/");
        source.quality.put("1080p", source.url);

        LampaPlaylist.Subtitle subtitle = new LampaPlaylist.Subtitle();
        subtitle.url = "https://cdn.example/uk.vtt";
        subtitle.label = "Українська";
        subtitle.language = "ukr";
        source.subtitles.add(subtitle);

        LampaPlaylist.Segment segment = new LampaPlaylist.Segment();
        segment.type = "skip";
        segment.kind = "intro";
        segment.source = "lampac";
        segment.startMs = 5_000;
        segment.endMs = 65_000;
        source.segments.add(segment);

        JSONObject snapshot = LampaPlaylist.sessionItem(source);
        LampaPlaylist.Item restored = LampaPlaylist.parseItem(snapshot);

        assertEquals(source.url, restored.url);
        assertEquals(source.resolverUrl, restored.resolverUrl);
        assertEquals(42_500, restored.positionMs);
        assertEquals("https://lampa.example/", restored.headers.get("Referer"));
        assertEquals(source.url, restored.quality.get("1080p"));
        assertEquals("ukr", restored.subtitles.get(0).language);
        assertEquals("intro", restored.segments.get(0).kind);
        assertEquals(65_000, restored.segments.get(0).endMs);
        assertTrue(snapshot.has("_session_segments"));
    }
}
