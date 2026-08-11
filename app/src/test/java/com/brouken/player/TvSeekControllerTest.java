package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TvSeekControllerTest {
    @Test public void singlePressCommitsTenSecondsOnce() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.FORWARD, 1000);
        assertEquals(60_000, seek.previewTarget(50_000, 100_000));
        assertEquals(60_000, seek.consumeTarget(50_000, 100_000));
        assertFalse(seek.isArmed());
    }

    @Test public void repeatedPressesAccelerateAndClamp() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.FORWARD, 1000);
        seek.press(TvSeekController.FORWARD, 1200);
        seek.hold(TvSeekController.FORWARD, 1300);
        assertEquals(100_000, seek.consumeTarget(40_000, 100_000));
    }

    @Test public void backwardNeverCrossesBeginning() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.BACKWARD, 1000);
        seek.hold(TvSeekController.BACKWARD, 1100);
        assertTrue(seek.isArmed());
        assertEquals(0, seek.consumeTarget(15_000, 100_000));
    }

    @Test public void slowPressStartsNewBurst() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.FORWARD, 1000);
        seek.press(TvSeekController.FORWARD, 2000);
        assertEquals(20_000, seek.consumeTarget(0, 100_000));
    }
}
