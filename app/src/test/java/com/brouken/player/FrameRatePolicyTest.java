package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FrameRatePolicyTest {
    @Test public void exactNtscMultiplesBeatNearbyIntegerModes() {
        assertTrue(FrameRatePolicy.isWholeMultiple(119.88f, 23.976f));
        assertFalse(FrameRatePolicy.isWholeMultiple(120f, 23.976f));
        assertEquals(119.88f, FrameRatePolicy.bestRate(
                60f, 23.976f, new float[]{24f, 60f, 119.88f, 120f, 144f}), 0.001f);
    }

    @Test public void ordinaryRatesKeepExpectedMultiples() {
        assertTrue(FrameRatePolicy.isWholeMultiple(48f, 24f));
        assertTrue(FrameRatePolicy.isWholeMultiple(59.94f, 29.97f));
        assertTrue(FrameRatePolicy.isWholeMultiple(100f, 50f));
        assertTrue(FrameRatePolicy.isWholeMultiple(120f, 60f));
        assertFalse(FrameRatePolicy.isWholeMultiple(60f, 50f));
        assertEquals(50f, FrameRatePolicy.bestRate(60f, 25f,
                new float[]{24f, 50f, 60f}), 0.001f);
        assertEquals(60f, FrameRatePolicy.bestRate(60f, 30f,
                new float[]{30f, 60f}), 0.001f);
        assertTrue(FrameRatePolicy.isWholeMultiple(119.88f, 59.94f));
    }
}
