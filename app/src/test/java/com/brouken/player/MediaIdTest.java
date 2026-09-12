package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class MediaIdTest {
    @Test
    public void identityIncludesSeriesCoordinates() throws Exception {
        Path path = Paths.get("src/main/java/com/brouken/player/MediaId.java");
        if (!Files.exists(path)) path = Paths.get("app").resolve(path);
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertTrue(source.contains("final int season"));
        assertTrue(source.contains("final int episode"));
        assertTrue(source.contains("season == other.season"));
        assertTrue(source.contains("episode == other.episode"));
    }
}
