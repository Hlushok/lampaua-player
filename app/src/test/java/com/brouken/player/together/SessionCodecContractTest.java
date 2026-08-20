package com.brouken.player.together;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class SessionCodecContractTest {
    private static String readProjectFile(final String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) {
            path = Paths.get("app").resolve(relativePath);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void sharedSessionKeepsLauncherOwnedLampaFields() throws Exception {
        final String codec = readProjectFile(
                "src/main/java/com/brouken/player/together/SessionCodec.java");
        final String playlist = readProjectFile(
                "src/main/java/com/brouken/player/LampaPlaylist.java");
        final String combined = codec + playlist;

        assertTrue(combined.contains("video_list"));
        assertTrue(combined.contains("headers"));
        assertTrue(combined.contains("subs"));
        assertTrue(combined.contains("segments"));
        assertTrue(combined.contains("season"));
        assertTrue(combined.contains("episode"));
        assertTrue(combined.contains("imdb_id"));
        assertTrue(combined.contains("id"));
        assertTrue(combined.contains("quality_levels"));
        assertTrue(combined.contains("quality_urls"));
    }

    @Test
    public void richSessionIsSentBeforeTheThinLampaFallback() throws Exception {
        final String manager = readProjectFile(
                "src/main/java/com/brouken/player/together/TogetherManager.java");
        final int method = manager.indexOf("public void changeMedia");
        final int rich = manager.indexOf("relay.send(LpartyCodec.session", method);
        final int thin = manager.indexOf("relay.send(LpartyCodec.url", method);

        assertTrue(method >= 0);
        assertTrue(rich > method);
        assertTrue(thin > rich);
        assertTrue(manager.contains("applyRichSession"));
    }
}
