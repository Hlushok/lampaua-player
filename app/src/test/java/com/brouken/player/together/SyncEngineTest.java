package com.brouken.player.together;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SyncEngineTest {
    @Test
    public void smallDriftUsesSpeedAndLargeDriftUsesSeek() {
        SyncEngine engine = new SyncEngine();
        engine.bind("me", "UA TV");
        engine.seed(10_000L, true, 1_000L);
        engine.tick(1_000L, 10_000L, true, 1f);
        engine.onFrame(LpartyCodec.transport(
                LpartyCodec.T_SYNC,
                "peer",
                1,
                11_000L,
                true,
                1f,
                null,
                null
        ), 1_000L);

        SyncEngine.Action small = engine.tick(1_250L, 10_250L, true, 1f);
        assertTrue(small.seekToMs < 0L);
        assertNotNull(small.speed);

        engine.onFrame(LpartyCodec.transport(
                LpartyCodec.T_SYNC,
                "peer",
                2,
                30_000L,
                true,
                1f,
                null,
                null
        ), 5_000L);

        SyncEngine.Action large = engine.tick(5_250L, 14_250L, true, small.speed);
        assertTrue(large.seekToMs >= 0L);
    }

    @Test
    public void unconfirmedMediaChangeIgnoresOldEpisodeFrames() {
        SyncEngine engine = new SyncEngine();
        engine.bind("me", "UA TV");
        engine.seed(1_000L, true, 1_000L);
        engine.mediaChanged(false, 2_000L);

        engine.onFrame(LpartyCodec.transport(
                LpartyCodec.T_SYNC,
                "peer",
                9,
                80_000L,
                true,
                1f,
                null,
                null
        ), 2_100L);

        SyncEngine.Action action = engine.tick(2_250L, 500L, true, 1f);
        assertTrue(action.seekToMs < 0L);
    }
}
