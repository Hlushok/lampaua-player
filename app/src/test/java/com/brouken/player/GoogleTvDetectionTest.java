package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoogleTvDetectionTest {
    @Test public void everyPlatformTvSignalSelectsTheTelevisionUi() {
        assertTrue(Utils.hasTelevisionSignal(true, false, false, false));
        assertTrue(Utils.hasTelevisionSignal(false, true, false, false));
        assertTrue(Utils.hasTelevisionSignal(false, false, true, false));
        assertTrue(Utils.hasTelevisionSignal(false, false, false, true));
        assertFalse(Utils.hasTelevisionSignal(false, false, false, false));
    }
}
