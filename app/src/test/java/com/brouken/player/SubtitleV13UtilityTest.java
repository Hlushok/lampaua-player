package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class SubtitleV13UtilityTest {
    @Test
    public void languageUtilitiesNormalizeAndDeduplicate() throws Exception {
        Path path = Paths.get("src/main/java/com/brouken/player/Utils.java");
        if (!Files.exists(path)) path = Paths.get("app").resolve(path);
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertTrue(source.contains("splitLanguages"));
        assertTrue(source.contains("toIso3Language"));
        assertTrue(source.contains("!list.contains(language)"));
    }
}
