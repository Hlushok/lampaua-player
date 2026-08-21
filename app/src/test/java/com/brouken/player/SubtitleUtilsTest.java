package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SubtitleUtilsTest {

    @Test
    public void generatedTranslationIsAlwaysLabeledUkrainian() {
        assertEquals("ukr", SubtitleUtils.getSubtitleLanguageFromPath(
                "/cache/subs.tt123.auto-ukr.eng.srt"));
        assertEquals("eng", SubtitleUtils.getSubtitleLanguageFromPath(
                "/cache/subs.tt123.eng.srt"));
    }
}
