package com.brouken.player;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.text.Cue;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.Arrays;

public class SubtitleV13UtilityTest {

    @Test
    public void normalizesAndDeduplicatesStoredLanguages() {
        assertEquals(Arrays.asList("ukr", "eng"),
                Utils.splitLanguages(" ukr,eng,ukr,,"));
        assertEquals("ukr", Utils.toIso3Language("uk-UA"));
    }

    @Test
    public void exposesTimelineBlocksForTranslation() {
        Cue cue = new Cue.Builder().setText("line").build();
        SubtitleTimeline timeline = new SubtitleTimeline(
                new long[]{1_000_000L},
                new long[]{2_000_000L},
                Arrays.asList(ImmutableList.of(cue)));

        assertEquals(1, timeline.size());
        assertEquals(1_000_000L, timeline.startUs(0));
        assertEquals(2_000_000L, timeline.endUs(0));
        assertEquals(ImmutableList.of(cue), timeline.cuesAt(0));
    }
}
