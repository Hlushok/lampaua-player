package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SubtitleOffsetPolicyTest {

    @Test
    public void negativeOffsetAdvancesEmbeddedRendererClock() {
        assertEquals(12_500_000L,
                SubtitleOffsetPolicy.rendererPositionUs(10_000_000L, -2.5, false));
    }

    @Test
    public void positiveOffsetLeavesEmbeddedRendererClockAlone() {
        assertEquals(10_000_000L,
                SubtitleOffsetPolicy.rendererPositionUs(10_000_000L, 2.5, false));
    }

    @Test
    public void timelineOwnsTimingInsteadOfRendererClock() {
        assertEquals(10_000_000L,
                SubtitleOffsetPolicy.rendererPositionUs(10_000_000L, -2.5, true));
    }

    @Test
    public void positiveOffsetLooksBackInExternalTimeline() {
        assertEquals(7_500_000L,
                SubtitleOffsetPolicy.timelinePositionUs(10_000L, 2.5));
    }

    @Test
    public void positiveOffsetDelaysCue() {
        assertEquals(12_500L,
                SubtitleOffsetPolicy.cueDueMs(10_000_000L, 2.5));
    }
}
