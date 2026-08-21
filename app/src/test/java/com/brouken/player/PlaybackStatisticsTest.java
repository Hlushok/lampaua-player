package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackStatisticsTest {
    @Test
    public void contentLengthAndDurationBecomeAverageBitsPerSecond() {
        assertEquals(8_000_000L, PlaybackStatistics.averageBitrate(
                1_000_000L, 1_000L));
        assertEquals(8_000L, PlaybackStatistics.averageBitrate(
                Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(0L, PlaybackStatistics.averageBitrate(0L, 1_000L));
        assertEquals(0L, PlaybackStatistics.averageBitrate(1_000L, 0L));
    }
}
