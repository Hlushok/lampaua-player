package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SleepTimerControllerTest {
    private static final class FakeClock implements SleepTimerController.Clock {
        long now;
        @Override public long now() { return now; }
    }

    @Test public void durationCountsDownWithoutSleeping() {
        FakeClock clock = new FakeClock();
        SleepTimerController timer = new SleepTimerController(clock);
        timer.armAfter(15 * 60_000L);
        clock.now = 14 * 60_000L;
        assertEquals(60_000L, timer.tick(false).remainingMs);
        clock.now = 15 * 60_000L;
        assertTrue(timer.tick(false).fire);
    }

    @Test public void finalThirtySecondsFadeLinearly() {
        FakeClock clock = new FakeClock();
        SleepTimerController timer = new SleepTimerController(clock);
        timer.armAfter(60_000L);
        clock.now = 45_000L;
        assertEquals(0.5f, timer.tick(false).volumeFactor, 0.001f);
    }

    @Test public void mediaEndAndCancellationAreIndependent() {
        FakeClock clock = new FakeClock();
        SleepTimerController timer = new SleepTimerController(clock);
        timer.armAtMediaEnd();
        assertFalse(timer.tick(false).fire);
        assertTrue(timer.tick(true).fire);
        timer.cancel();
        assertFalse(timer.isArmed());
    }
}
