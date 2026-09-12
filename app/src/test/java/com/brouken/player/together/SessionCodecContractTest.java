package com.brouken.player.together;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class SessionCodecContractTest {
    private static String read(final String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("app").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void sessionCarriesLauncherArraysAndBundles() throws Exception {
        final String codec = read("src/main/java/com/brouken/player/together/SessionCodec.java");
        final String bridge = read("src/main/java/com/brouken/player/LampaPlaylistBridge.java");

        assertTrue(codec.contains("value instanceof String[]"));
        assertTrue(codec.contains("value instanceof Parcelable[]"));
        assertTrue(codec.contains("value instanceof Bundle"));
        assertTrue(codec.contains("out.putParcelableArray(key, items)"));
        assertTrue(bridge.contains("PlayerActivity.API_VIDEO_LIST_SUBTITLES"));
        assertTrue(bridge.contains("PlayerActivity.API_VIDEO_LIST_QUALITY_URLS"));
    }

    @Test
    public void mediaChangesReachBothRoomProtocols() throws Exception {
        final String manager = read("src/main/java/com/brouken/player/together/TogetherManager.java");
        final int method = manager.indexOf("public void changeMedia");
        final int thin = manager.indexOf("relay.send(LpartyCodec.url", method);
        final int rich = manager.indexOf("relay.send(LpartyCodec.session", method);

        assertTrue(method >= 0);
        assertTrue(thin > method);
        assertTrue(rich > method);
    }

    @Test
    public void roomDiscoverySortsByMemberCount() throws Exception {
        final String lobby = read("src/main/java/com/brouken/player/together/Lobby.java");
        assertTrue(lobby.contains("Collections.sort(rooms"));
        assertTrue(lobby.contains("optInt(\"members\")"));
    }
}
