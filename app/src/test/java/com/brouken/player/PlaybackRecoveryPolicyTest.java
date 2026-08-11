package com.brouken.player;

import org.junit.Test;

import static com.brouken.player.PlaybackRecoveryPolicy.Action.FAIL;
import static com.brouken.player.PlaybackRecoveryPolicy.Action.LOWER_QUALITY;
import static com.brouken.player.PlaybackRecoveryPolicy.Action.RETRY_COMPATIBILITY;
import static com.brouken.player.PlaybackRecoveryPolicy.Action.RETRY_SOURCE;
import static com.brouken.player.PlaybackRecoveryPolicy.FailureKind.DECODER;
import static com.brouken.player.PlaybackRecoveryPolicy.FailureKind.NETWORK_READ;
import static com.brouken.player.PlaybackRecoveryPolicy.FailureKind.TRUNCATED_LOCAL_FILE;
import static org.junit.Assert.assertEquals;

public class PlaybackRecoveryPolicyTest {

    @Test
    public void transientNetworkReadGetsThreeAttempts() {
        assertEquals(RETRY_SOURCE,
                PlaybackRecoveryPolicy.decide(NETWORK_READ, false, 0, 0, false));
        assertEquals(RETRY_SOURCE,
                PlaybackRecoveryPolicy.decide(NETWORK_READ, false, 2, 0, false));
        assertEquals(FAIL,
                PlaybackRecoveryPolicy.decide(NETWORK_READ, false, 3, 0, false));
    }

    @Test
    public void decoderMovesFromCompatibilityToLowerQuality() {
        assertEquals(RETRY_COMPATIBILITY,
                PlaybackRecoveryPolicy.decide(DECODER, false, 3, 0, false));
        assertEquals(LOWER_QUALITY,
                PlaybackRecoveryPolicy.decide(DECODER, true, 3, 1, true));
        assertEquals(FAIL,
                PlaybackRecoveryPolicy.decide(DECODER, true, 3, 1, false));
    }

    @Test
    public void corruptLocalInputIsNeverRetriedAsNetwork() {
        assertEquals(FAIL,
                PlaybackRecoveryPolicy.decide(TRUNCATED_LOCAL_FILE, false, 0, 0, true));
    }
}
