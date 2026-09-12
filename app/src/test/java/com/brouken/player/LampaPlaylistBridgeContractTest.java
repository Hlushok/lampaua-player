package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class LampaPlaylistBridgeContractTest {

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) {
            path = Paths.get("app").resolve(relativePath);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void jsonPlaylistIsNormalizedBeforeTheCoreReadsIt() throws Exception {
        final String bridge = read("src/main/java/com/brouken/player/LampaPlaylistBridge.java");
        final String player = read("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(bridge.contains("lampaua.playlist_json"));
        assertTrue(bridge.contains("PlayerActivity.API_VIDEO_LIST"));
        assertTrue(bridge.contains("PlayerActivity.API_VIDEO_LIST_SUBTITLES"));
        assertTrue(bridge.contains("PlayerActivity.API_VIDEO_LIST_QUALITY_URLS"));
        assertTrue(bridge.contains("resolver_url"));
        assertTrue(bridge.contains("position_ms"));
        assertTrue(bridge.contains("subtitleLanguages"));
        assertTrue(bridge.contains("putStringArray(\"languages\""));
        assertTrue(player.contains("getSmartStringArray(item, \"languages\")"));
        assertTrue(player.contains("SubtitleUtils.buildSubtitle(this, (Uri) uris[i], name, language, false)"));
        assertTrue(player.contains("LampaPlaylistBridge.normalize(getIntent())"));
        assertTrue(player.contains("LampaPlaylistBridge.normalize(intent)"));
    }

    @Test
    public void seriesTitlePrefillsManualSubtitleSearch() throws Exception {
        final String bridge = read("src/main/java/com/brouken/player/LampaPlaylistBridge.java");
        final String player = read("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(bridge.contains("series_title"));
        assertTrue(bridge.contains("collectionTitle"));
        assertTrue(player.contains("apiSeriesTitle"));
        assertTrue(player.contains("subtitleSearchInitialTitle()"));
        assertTrue(player.contains("query.setText(initialTitle)"));
    }
}
