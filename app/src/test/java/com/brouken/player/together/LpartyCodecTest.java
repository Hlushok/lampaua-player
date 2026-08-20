package com.brouken.player.together;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LpartyCodecTest {
    @Test
    public void actionFrameRoundTripsSeek() {
        JSONObject frame = LpartyCodec.transport(
                LpartyCodec.T_ACT,
                "p1",
                7,
                42_000L,
                true,
                1f,
                LpartyCodec.V_SEEKED,
                "UA TV"
        );

        assertEquals("act", LpartyCodec.type(frame));
        assertEquals(42_000L, LpartyCodec.positionMs(frame));
        assertEquals(RoomAction.SEEKED, LpartyCodec.act(frame));
        assertEquals(7L, LpartyCodec.seq(frame));
    }

    @Test
    public void lampaFrameWithoutSpeedDoesNotOverrideUserSpeed() throws Exception {
        JSONObject frame = new JSONObject()
                .put("t", "sync")
                .put("u", "peer")
                .put("s", "playing")
                .put("p", 12.5);

        assertFalse(LpartyCodec.hasSpeed(frame));
        assertTrue(LpartyCodec.playing(frame));
        assertEquals(12_500L, LpartyCodec.positionMs(frame));
    }
}
