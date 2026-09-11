package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class LampaIntentContractTest {
    private static String readProjectFile(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("app").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test public void acceptsOfficialLampaPlaylistAndExtendedMetadata() throws Exception {
        String source = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(source.contains("\"video_list\""));
        assertTrue(source.contains("\"video_list.name\""));
        assertTrue(source.contains("\"video_list.filename\""));
        assertTrue(source.contains("\"video_list.thumbnail\""));
        assertTrue(source.contains("\"video_list.segments\""));
        assertTrue(source.contains("\"video_list.season\""));
        assertTrue(source.contains("\"video_list.episode\""));
        assertTrue(source.contains("\"video_list.imdb_id\""));
        assertTrue(source.contains("\"video_list.id\""));
        assertTrue(source.contains("\"video_list.subtitles\""));
        assertTrue(source.contains("\"quality_levels\""));
        assertTrue(source.contains("\"quality_urls\""));
        assertTrue(source.contains("\"video_list.quality_levels.\" + i"));
        assertTrue(source.contains("\"video_list.quality_urls.\" + i"));
        assertTrue(source.contains("getParcelableArrayExtra(\"video_list\")"));
        assertTrue(source.contains("getStringArrayExtra(\"video_list\")"));
        assertTrue(source.contains("bundle.getStringArray(API_HEADERS)"));
        assertTrue(source.contains("bundle.getStringArrayList(API_HEADERS)"));
    }

    @Test public void playlistIntentOwnsOnlyItsSessionBeforeMediaIsWritten() throws Exception {
        String source = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String createFlow = source.substring(source.indexOf("final String action = launchIntent.getAction()"),
                source.indexOf("restoreApiSession("));
        int createIsolation = createFlow.indexOf("isLampaSessionIntent(launchIntent)");
        int createMediaWrite = createFlow.indexOf("mPrefs.updateMedia");
        assertTrue(createIsolation >= 0 && createIsolation < createMediaWrite);

        String applyFlow = source.substring(source.indexOf("private void applyViewIntent"),
                source.indexOf("void resetApiAccess()"));
        int applyIsolation = applyFlow.indexOf("isLampaSessionIntent(intent)");
        int applyMediaWrite = applyFlow.indexOf("mPrefs.updateMedia");
        assertTrue(applyIsolation >= 0 && applyIsolation < applyMediaWrite);

        String detector = source.substring(source.indexOf("private boolean isLampaSessionIntent"),
                source.indexOf("private void readLampaPlaylist"));
        assertTrue(detector.contains("LampaPlaylist.EXTRA_PLAYLIST_JSON"));
        assertTrue(detector.contains("\"playlist_json\""));
        assertTrue(detector.contains("\"video_list\""));
        assertTrue(detector.contains("\"lampaua.imdb_id\""));
        assertTrue(detector.contains("\"quality_levels\""));
        assertTrue(detector.contains("\"segments\""));
    }

    @Test public void returnsMxCompatiblePlaybackResultToLampa() throws Exception {
        String source = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(source.contains("new Intent(\"com.mxtech.intent.result.VIEW\")"));
        assertTrue(source.contains("intent.putExtra(API_END_BY"));
        assertTrue(source.contains("intent.putExtra(API_POSITION"));
        assertTrue(source.contains("intent.putExtra(API_DURATION"));
        assertTrue(source.contains("intent.setData(resultUri)"));
        assertTrue(source.contains("reportDuration > 0"));
        assertTrue(source.contains("rememberPlaybackReport()"));
        assertTrue(source.contains("setResult(Activity.RESULT_OK, intent)"));
        assertTrue(source.contains("LampaPlaylist.EXTRA_PLAYBACK_RESULTS"));
        assertTrue(source.contains("bundle.getBoolean(API_RETURN_RESULT)"));
    }

    @Test public void packageAndManifestRemainDiscoverableByAndroidLampa() throws Exception {
        String gradle = readProjectFile("build.gradle");
        String manifest = readProjectFile("src/main/AndroidManifest.xml");

        assertTrue(gradle.contains("applicationId \"com.lampaua.player\""));
        assertTrue(manifest.contains("android.intent.action.VIEW"));
        assertTrue(manifest.contains("android.intent.category.BROWSABLE"));
        assertTrue(manifest.contains("android:mimeType=\"video/*\""));
        assertTrue(manifest.contains("android.intent.category.LEANBACK_LAUNCHER"));
        assertTrue(manifest.contains("android:exported=\"true\""));
    }
}
