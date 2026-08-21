package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class UkrainianSubtitlePolicyTest {

    @Test
    public void directSearchIsUkrainianOnlyWhenEnabled() {
        assertEquals(Collections.singletonList("ukr"),
                UkrainianSubtitlePolicy.directLanguages(Arrays.asList("ukr", "eng")));
        assertEquals(Collections.emptyList(),
                UkrainianSubtitlePolicy.directLanguages(Collections.singletonList("eng")));
    }

    @Test
    public void fallbackUsesUserOrderThenDefaultsWithoutDuplicates() {
        assertEquals(Arrays.asList("deu", "eng", "rus", "pol"),
                UkrainianSubtitlePolicy.fallbackLanguages(
                        Arrays.asList("ukr", "deu", "eng", "deu")));
    }

    @Test
    public void translationRequiresUkrainianPreference() {
        assertTrue(UkrainianSubtitlePolicy.enabled(true, Arrays.asList("eng", "ukr")));
        assertFalse(UkrainianSubtitlePolicy.enabled(true, Arrays.asList("eng", "rus")));
        assertFalse(UkrainianSubtitlePolicy.enabled(false, Collections.singletonList("ukr")));
    }

    @Test
    public void targetAndGeneratedCacheNameAreFixedAndPathSafe() {
        assertEquals("uk", UkrainianSubtitlePolicy.targetIso2());
        assertEquals("subs.tt123.auto-ukr.eng.srt",
                UkrainianSubtitlePolicy.translatedCacheName("subs.tt123", " ENG "));
        assertNull(UkrainianSubtitlePolicy.translatedCacheName("subs.tt123", "../eng"));
        assertNull(UkrainianSubtitlePolicy.translatedCacheName("subs.tt123", "ukr"));
    }

    @Test
    public void originalUkrainianCacheNeverMasqueradesAsGenerated() {
        assertTrue(UkrainianSubtitlePolicy.isDirectUkrainianCache(
                new File("subs.tt123.ukr.srt")));
        assertFalse(UkrainianSubtitlePolicy.isDirectUkrainianCache(
                new File("subs.tt123.auto-ukr.eng.srt")));
        assertFalse(UkrainianSubtitlePolicy.isDirectUkrainianCache(
                new File("subs.tt123.rus.srt")));
    }
}
