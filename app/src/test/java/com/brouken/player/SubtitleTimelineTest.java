package com.brouken.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.media3.common.text.Cue;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.Arrays;

public class SubtitleTimelineTest {

    @Test
    public void findsEveryOverlappingCueBlock() {
        Cue first = new Cue.Builder().setText("first").build();
        Cue second = new Cue.Builder().setText("second").build();
        SubtitleTimeline timeline = new SubtitleTimeline(
                new long[]{1_000_000L, 2_000_000L},
                new long[]{3_000_000L, 4_000_000L},
                Arrays.asList(ImmutableList.of(first), ImmutableList.of(second)));

        assertArrayEquals(new int[]{0, 1}, timeline.visibleAt(2_500_000L));
        assertEquals(Arrays.asList(first, second), timeline.cuesOf(new int[]{0, 1}));
    }

    @Test
    public void excludesCuesOutsideTheirHalfOpenIntervals() {
        Cue cue = new Cue.Builder().setText("line").build();
        SubtitleTimeline timeline = new SubtitleTimeline(
                new long[]{1_000_000L},
                new long[]{2_000_000L},
                Arrays.asList(ImmutableList.of(cue)));

        assertArrayEquals(new int[0], timeline.visibleAt(999_999L));
        assertArrayEquals(new int[]{0}, timeline.visibleAt(1_000_000L));
        assertArrayEquals(new int[0], timeline.visibleAt(2_000_000L));
    }
}
