package com.brouken.player;

import org.junit.Test;

import static com.brouken.player.LoadWatchdogPolicy.Action.IGNORE;
import static com.brouken.player.LoadWatchdogPolicy.Action.REARM;
import static com.brouken.player.LoadWatchdogPolicy.Action.REPORT_INITIAL_TIMEOUT;
import static com.brouken.player.LoadWatchdogPolicy.Action.REPORT_MIDSTREAM_STALL;
import static com.brouken.player.LoadWatchdogPolicy.SourceKind.LOCAL;
import static com.brouken.player.LoadWatchdogPolicy.SourceKind.NETWORK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoadWatchdogPolicyTest {
    @Test public void activeTorrentLoadGetsAnotherWindow() {
        assertEquals(REARM, LoadWatchdogPolicy.evaluate(
                true, false, 1_000_000L, 1_262_144L, NETWORK));
    }

    @Test public void keepaliveDribbleIsNotPlayableProgress() {
        assertEquals(REPORT_INITIAL_TIMEOUT, LoadWatchdogPolicy.evaluate(
                true, false, 1_000_000L, 1_008_191L, NETWORK));
    }

    @Test public void midFilmSilenceIsReportedAsStall() {
        assertEquals(REPORT_MIDSTREAM_STALL, LoadWatchdogPolicy.evaluate(
                true, true, 5_000_000L, 5_000_000L, NETWORK));
    }

    @Test public void localFileCannotClaimNetworkProgress() {
        assertEquals(REPORT_INITIAL_TIMEOUT, LoadWatchdogPolicy.evaluate(
                true, false, 0L, 500_000L, LOCAL));
    }

    @Test public void callbackAfterReadyIsIgnored() {
        assertEquals(IGNORE, LoadWatchdogPolicy.evaluate(
                false, true, 0L, 0L, NETWORK));
    }

    @Test public void connectedTorrentGetsFourSilentWindowsInTotal() {
        assertTrue(LoadWatchdogPolicy.shouldWaitForConnectedSource(true, 0));
        assertTrue(LoadWatchdogPolicy.shouldWaitForConnectedSource(true, 2));
        assertFalse(LoadWatchdogPolicy.shouldWaitForConnectedSource(true, 3));
        assertFalse(LoadWatchdogPolicy.shouldWaitForConnectedSource(false, 0));
    }

    @Test public void onlyAStartedTvPlaybackKeepsItsDecoder() {
        assertTrue(LoadWatchdogPolicy.shouldHoldTvDecoder(true, true));
        assertFalse(LoadWatchdogPolicy.shouldHoldTvDecoder(true, false));
        assertFalse(LoadWatchdogPolicy.shouldHoldTvDecoder(false, true));
    }
}
