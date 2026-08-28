package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class LanguagePriorityModelTest {

    @Test
    public void normalizesOrderAndDuplicates() {
        assertEquals(Arrays.asList("ukr", "eng"), LanguagePriorityModel.normalize(
                Arrays.asList("uk", "ukr", "eng", "", "eng")));
    }

    @Test
    public void translationTargetDefaultsToUkrainian() {
        assertEquals("ukr", LanguagePriorityModel.targetOrUkrainian(""));
        assertEquals("deu", LanguagePriorityModel.targetOrUkrainian("de-DE"));
    }
}
