package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SubtitleTranslateTest {

    @Test
    public void normalizesKnownBackendsAndRemovesDuplicates() {
        assertEquals("mozhi,google", SubtitleTranslate.normalize(
                "mozhi-bloat,unknown,google,mozhi-ducks"));
    }

    @Test
    public void translatesToUkrainianFromReadableFallbacksOnly() {
        assertEquals(Arrays.asList("rus", "eng"), SubtitleTranslate.sourcesFor("ukr"));
        assertEquals(Collections.emptyList(), SubtitleTranslate.sourcesFor("eng"));
        assertNull(UkrainianSubtitlePolicy.translatedCacheName("subs.movie", "ukr"));
    }
}
