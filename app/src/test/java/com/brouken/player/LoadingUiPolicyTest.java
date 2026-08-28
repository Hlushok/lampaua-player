package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoadingUiPolicyTest {
    @Test public void readyPlaybackNeverShowsRecoveryChrome() {
        LoadingUiPolicy.Decision decision = LoadingUiPolicy.decide(
                true, false, true, true, true);

        assertFalse(decision.showIndicator);
        assertFalse(decision.showRecoveryNotice);
    }

    @Test public void shortBufferingWaitStaysInvisible() {
        LoadingUiPolicy.Decision decision = LoadingUiPolicy.decide(
                true, true, false, false, true);

        assertFalse(decision.showIndicator);
        assertFalse(decision.showRecoveryNotice);
    }

    @Test public void sustainedRecoveryBufferingShowsBoth() {
        LoadingUiPolicy.Decision decision = LoadingUiPolicy.decide(
                true, true, false, true, true);

        assertTrue(decision.showIndicator);
        assertTrue(decision.showRecoveryNotice);
    }

    @Test public void ordinaryBufferingDoesNotClaimConnectionWasLost() {
        LoadingUiPolicy.Decision decision = LoadingUiPolicy.decide(
                true, true, false, true, false);

        assertTrue(decision.showIndicator);
        assertFalse(decision.showRecoveryNotice);
    }
}
