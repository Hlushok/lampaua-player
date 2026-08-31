package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.DefaultLoadControl;

import org.junit.Test;

public class RebufferPolicyTest {
    @Test
    public void streamingUsesTheUpstreamTimeBoundedWindow() {
        RebufferPolicy.Config config = RebufferPolicy.forStreaming(false);

        assertEquals(15_000, config.minBufferMs);
        assertEquals(50_000, config.maxBufferMs);
        assertEquals(500, config.bufferForPlaybackMs);
        assertEquals(5_000, config.bufferForPlaybackAfterRebufferMs);
        assertTrue(config.prioritizeTimeOverSizeThresholds);
    }

    @Test
    public void localMediaKeepsMedia3Defaults() {
        RebufferPolicy.Config config = RebufferPolicy.forLocal();

        assertEquals(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                config.bufferForPlaybackAfterRebufferMs);
        assertFalse(config.prioritizeTimeOverSizeThresholds);
    }

    @Test
    public void optimized4kUsesTheSameStableStreamingPolicy() {
        RebufferPolicy.Config config = RebufferPolicy.forStreaming(true);

        assertEquals(15_000, config.minBufferMs);
        assertEquals(50_000, config.maxBufferMs);
        assertEquals(500, config.bufferForPlaybackMs);
        assertEquals(5_000, config.bufferForPlaybackAfterRebufferMs);
        assertTrue(config.prioritizeTimeOverSizeThresholds);
    }
}
