package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveRecoveryPolicyTest {
    @Test public void twoImmediateRejoinsAreAllowed() {
        assertTrue(LiveRecoveryPolicy.canRejoin(0, 10_000L, 0L));
        assertTrue(LiveRecoveryPolicy.canRejoin(1, 20_000L, 10_000L));
        assertFalse(LiveRecoveryPolicy.canRejoin(2, 30_000L, 20_000L));
    }

    @Test public void quietMinuteRestoresBudget() {
        assertEquals(0, LiveRecoveryPolicy.effectiveAttempts(2, 80_001L, 20_000L));
        assertTrue(LiveRecoveryPolicy.canRejoin(2, 80_001L, 20_000L));
    }

    @Test public void slidingLiveWindowResetCountsAsPlaybackProgress() {
        assertTrue(LiveRecoveryPolicy.hasPlaybackProgress(5_800L, 900L, true));
        assertTrue(LiveRecoveryPolicy.hasPlaybackProgress(900L, 2_400L, true));
        assertFalse(LiveRecoveryPolicy.hasPlaybackProgress(2_400L, 2_400L, true));
    }

    @Test public void vodPositionMustAdvanceForward() {
        assertTrue(LiveRecoveryPolicy.hasPlaybackProgress(10_000L, 11_500L, false));
        assertFalse(LiveRecoveryPolicy.hasPlaybackProgress(10_000L, 9_000L, false));
    }
}
