package com.brouken.player;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AudioLanguagePriorityTest {
    @Test public void normalizesTagsAliasesAndDuplicates() {
        assertEquals(Arrays.asList("ukr", "deu", "eng"),
                AudioLanguagePriority.parse("uk, uk-UA, ger, deu, en-US"));
    }

    @Test public void serializationPreservesPreferenceOrder() {
        assertEquals("eng,ukr", AudioLanguagePriority.serialize(
                Arrays.asList("en", "uk-UA", "eng")));
    }

    @Test public void selectsFirstPreferredLanguageThatExists() {
        List<TrackMetadata> tracks = Arrays.asList(
                new TrackMetadata(1, "Deutsch", "ger", TrackMetadata.Type.AUDIO),
                new TrackMetadata(2, "English", "en-US", TrackMetadata.Type.AUDIO));
        assertEquals(1, AudioLanguagePriority.select(Arrays.asList("ukr", "eng"), tracks));
    }

    @Test public void fallsBackToFirstTrackWhenNothingMatches() {
        List<TrackMetadata> tracks = Arrays.asList(
                new TrackMetadata(1, "Deutsch", "deu", TrackMetadata.Type.AUDIO));
        assertEquals(0, AudioLanguagePriority.select(Arrays.asList("ukr"), tracks));
    }
}
