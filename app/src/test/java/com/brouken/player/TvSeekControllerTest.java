package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TvSeekControllerTest {
    @Test public void singlePressCommitsThreeSecondsOnce() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.FORWARD, 1000);
        assertEquals(53_000, seek.previewTarget(50_000, 100_000));
        assertEquals(53_000, seek.consumeTarget(50_000, 100_000));
        assertFalse(seek.isArmed());
    }

    @Test public void repeatedPressesAccelerateAndClamp() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.FORWARD, 1000);
        seek.press(TvSeekController.FORWARD, 1200);
        seek.hold(TvSeekController.FORWARD, 1300);
        assertEquals(56_000, seek.consumeTarget(40_000, 100_000));
    }

    @Test public void backwardNeverCrossesBeginning() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.BACKWARD, 1000);
        seek.hold(TvSeekController.BACKWARD, 1100);
        assertTrue(seek.isArmed());
        assertEquals(9_000, seek.consumeTarget(15_000, 100_000));
    }

    @Test public void slowPressStartsNewBurst() {
        TvSeekController seek = new TvSeekController();
        seek.press(TvSeekController.FORWARD, 1000);
        seek.press(TvSeekController.FORWARD, 2000);
        assertEquals(6_000, seek.consumeTarget(0, 100_000));
    }
}
