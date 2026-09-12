package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class SubtitleTranslateTest {
    @Test
    public void translationAcceptsAnySourceButKeepsUkrainianTarget() throws Exception {
        Path path = Paths.get("src/main/java/com/brouken/player/SubtitleTranslate.java");
        if (!Files.exists(path)) path = Paths.get("app").resolve(path);
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertTrue(source.contains("TARGET_LANGUAGE = \"ukr\""));
        assertTrue(source.contains("sourcesFor(String targetIso3, List<String> preferredSources)"));
        assertTrue(source.contains("!TARGET_LANGUAGE.equals(normalized)"));
        assertTrue(source.contains("sources.add(\"rus\")"));
        assertTrue(source.contains("sources.add(\"eng\")"));
        assertTrue(source.contains("sources.add(\"pol\")"));
    }
}
