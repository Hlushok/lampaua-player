package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.*;

public class PlaylistResumePolicyTest {
    @Test public void shortPositionIsDiscardedOnlyWhenAsking() {
        assertDecision(0, false, 29_999, 0, null, true);
        assertDecision(30_000, true, 30_000, 0, null, true);
        assertDecision(12_000, false, 12_000, 0, null, false);
        assertDecision(0, false, -1, 0, null, true);
    }

    @Test public void watchedTailAlwaysRestartsIncludingWhenAskingIsOff() {
        for (boolean ask : new boolean[]{true, false}) {
            assertDecision(949_999, ask, 949_999, 1_000_000, null, ask);
            assertDecision(0, false, 950_000, 1_000_000, null, ask);
            assertDecision(0, false, Long.MAX_VALUE, 1_000_000, null, ask);
        }
    }

    @Test public void credibleCreditsRestartButIntroAdsAndBadMarkersDoNot() {
        assertDecision(0, false, 900_000, 1_000_000, segments("skip", 900, 1000), true);
        assertDecision(899_000, true, 899_000, 1_000_000, segments("skip", 900, 1000), true);
        assertDecision(900_000, true, 900_000, 1_000_000, segments("ad", 900, 1000), true);
        assertDecision(900_000, true, 900_000, 1_000_000, segments("skip", 0, 1000), true);
        assertDecision(900_000, true, 900_000, 1_000_000, segments("skip", 850, 949), true);
        assertDecision(0, false, 850_000, 1_000_000, segments("skip", 850, 1000), true);
    }

    @Test public void unknownLengthAndMalformedSegmentsCannotEraseResume() {
        assertDecision(900_000, true, 900_000, 0, segments("skip", 900, 1000), true);
        assertDecision(50_000, true, 50_000, 100_000, "not JSON", true);
        assertDecision(50_000, false, 50_000, 0, null, false);
    }

    @Test public void durationPriorityIsPlayerThenSessionThenSegments() {
        String json = "{\"duration_ms\":\"1000000\"}";
        assertEquals(600_000, PlaylistResumePolicy.duration(600_000, 700_000, json));
        assertEquals(700_000, PlaylistResumePolicy.duration(-1, 700_000, json));
        assertEquals(1_000_000, PlaylistResumePolicy.duration(0, -1, json));
        assertEquals(0, PlaylistResumePolicy.duration(0, 0, "{\"duration_ms\":\"Infinity\"}"));
        assertEquals(0, PlaylistResumePolicy.duration(0, 0, "bad"));
    }

    @Test public void largeDurationAndWatchedBoundaryRemainExactToTheMillisecond() {
        long duration = 9_007_199_254_740_993L;
        assertEquals(duration, PlaylistResumePolicy.duration(0, 0,
                "{\"duration_ms\":\"9007199254740993\"}"));
        long boundary = 8_556_839_292_003_944L;
        assertDecision(boundary - 1, false, boundary - 1, duration, null, false);
        assertDecision(0, false, boundary, duration, null, false);
    }

    private static String segments(String type, int start, int end) {
        return "{\"duration_ms\":1000000,\"" + type + "\":[{\"start\":" + start + ",\"end\":" + end + "}]}";
    }

    private static void assertDecision(long expected, boolean ask, long position, long duration,
                                       String segments, boolean askResume) {
        PlaylistResumePolicy.Decision decision = PlaylistResumePolicy.decide(position, duration, segments, askResume);
        assertEquals(expected, decision.positionMs);
        assertEquals(ask, decision.ask);
    }
}
