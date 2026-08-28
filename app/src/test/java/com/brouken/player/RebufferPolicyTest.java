package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.DefaultLoadControl;

import org.junit.Test;

public class RebufferPolicyTest {
    @Test
    public void streamingWaitsForFifteenSecondsAfterAStall() {
        RebufferPolicy.Config config = RebufferPolicy.forStreaming(false);

        assertEquals(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, config.minBufferMs);
        assertEquals(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, config.maxBufferMs);
        assertEquals(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                config.bufferForPlaybackMs);
        assertEquals(15_000, config.bufferForPlaybackAfterRebufferMs);
        assertFalse(config.prioritizeTimeOverSizeThresholds);
    }

    @Test
    public void localMediaKeepsMedia3Defaults() {
        RebufferPolicy.Config config = RebufferPolicy.forLocal();

        assertEquals(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                config.bufferForPlaybackAfterRebufferMs);
        assertFalse(config.prioritizeTimeOverSizeThresholds);
    }

    @Test
    public void optimized4kKeepsItsLargeBufferAndUsesTheSaferRebufferTarget() {
        RebufferPolicy.Config config = RebufferPolicy.forStreaming(true);

        assertEquals(20_000, config.minBufferMs);
        assertTrue(config.maxBufferMs >= 90_000);
        assertEquals(5_000, config.bufferForPlaybackMs);
        assertEquals(15_000, config.bufferForPlaybackAfterRebufferMs);
        assertTrue(config.prioritizeTimeOverSizeThresholds);
    }
}
