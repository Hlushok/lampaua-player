package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackExitGuardTest {
    @Test public void secondPressInsideWindowExits() {
        BackExitGuard guard = new BackExitGuard(2_000L);

        assertFalse(guard.shouldExit(10_000L));
        assertTrue(guard.shouldExit(11_999L));
    }

    @Test public void expiredWindowArmsAgain() {
        BackExitGuard guard = new BackExitGuard(2_000L);

        assertFalse(guard.shouldExit(10_000L));
        assertFalse(guard.shouldExit(12_001L));
        assertTrue(guard.shouldExit(13_000L));
    }

    @Test public void resetRequiresAnotherPair() {
        BackExitGuard guard = new BackExitGuard(2_000L);

        assertFalse(guard.shouldExit(10_000L));
        guard.reset();
        assertFalse(guard.shouldExit(10_500L));
    }
}
