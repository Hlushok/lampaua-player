package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void transferLineRemovesDuplicateFiguresFromOverlayPanel() {
        PlaybackStatistics.Snapshot snapshot = new PlaybackStatistics.Snapshot(
                "MKV", 1920, 1080, "H.264", 24f, 5_000_000L, 12_000L,
                "decoder", "AAC", 8_000_000L, 3);
        PlaybackStatistics.Labels labels = new PlaybackStatistics.Labels(
                "Container", "Video", "FPS", "Bitrate", "Buffer", "Network",
                "Decoder", "Audio", "Dropped");

        String panel = snapshot.render(labels, false);
        assertFalse(panel.contains("Bitrate:"));
        assertFalse(panel.contains("Buffer:"));
        assertFalse(panel.contains("Network:"));
        assertTrue(panel.contains("Decoder: decoder"));
        assertTrue(snapshot.render(labels).contains("Buffer:"));
    }
}
