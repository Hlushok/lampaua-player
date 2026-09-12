package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class LampaIntentContractTest {
    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("app").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void acceptsOfficialPlaylistMetadataAndQuality() throws Exception {
        String player = read("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(player.contains("static final String API_VIDEO_LIST = \"video_list\""));
        assertTrue(player.contains("static final String API_VIDEO_LIST_SUBTITLES = \"video_list.subtitles\""));
        assertTrue(player.contains("static final String API_VIDEO_LIST_SEGMENTS = \"video_list.segments\""));
        assertTrue(player.contains("static final String API_VIDEO_LIST_SEASON = \"video_list.season\""));
        assertTrue(player.contains("static final String API_VIDEO_LIST_EPISODE = \"video_list.episode\""));
        assertTrue(player.contains("API_VIDEO_LIST_QUALITY_LEVELS + \".\" + i"));
        assertTrue(player.contains("API_VIDEO_LIST_QUALITY_URLS + \".\" + i"));
        assertTrue(player.contains("getSmartParcelableArray(bundle, API_VIDEO_LIST_SUBTITLES)"));
        assertTrue(player.contains("getSmartStringArray(bundle, \"video_list.tmdb_id\")"));
    }

    @Test
    public void returnsMxCompatiblePlaybackResult() throws Exception {
        String player = read("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(player.contains("new Intent(\"com.mxtech.intent.result.VIEW\")"));
        assertTrue(player.contains("intent.putExtra(API_END_BY"));
        assertTrue(player.contains("intent.putExtra(API_POSITION"));
        assertTrue(player.contains("intent.putExtra(API_DURATION"));
        assertTrue(player.contains("setResult(Activity.RESULT_OK, intent)"));
        assertTrue(player.contains("bundle.getBoolean(API_RETURN_RESULT)"));
    }

    @Test
    public void packageAndManifestRemainDiscoverable() throws Exception {
        String gradle = read("build.gradle");
        String manifest = read("src/main/AndroidManifest.xml");

        assertTrue(gradle.contains("applicationId \"com.lampaua.player\""));
        assertTrue(manifest.contains("android.intent.action.VIEW"));
        assertTrue(manifest.contains("android.intent.category.BROWSABLE"));
        assertTrue(manifest.contains("android:mimeType=\"video/*\""));
        assertTrue(manifest.contains("android.intent.category.LEANBACK_LAUNCHER"));
        assertTrue(manifest.contains("android:exported=\"true\""));
    }
}
