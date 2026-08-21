package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SubtitleLanguagePolicyTest {

    @Test
    public void searchesOnlyLanguagesRankedAboveTheBestPresentTrack() {
        assertEquals(Arrays.asList("ukr", "rus"), SubtitleLanguagePolicy.missing(
                Arrays.asList("ukr", "rus", "eng"),
                Collections.singleton("eng"), false));
    }

    @Test
    public void strictModeDoesNotSearchWhenAnyPreferredLanguageExists() {
        assertEquals(Collections.emptyList(), SubtitleLanguagePolicy.missing(
                Arrays.asList("ukr", "rus", "eng"),
                Collections.singleton("eng"), true));
    }

    @Test
    public void strictModeSearchesAllPreferredLanguagesWhenNoneExist() {
        assertEquals(Arrays.asList("ukr", "rus"), SubtitleLanguagePolicy.missing(
                Arrays.asList("ukr", "rus"),
                Collections.singleton("deu"), true));
    }
}
