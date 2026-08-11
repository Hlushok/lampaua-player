package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VideoScaleModeTest {
    @Test public void stablePreferenceKeysStayReadable() {
        assertEquals("fit", VideoScaleMode.FIT.key);
        assertEquals("2_39_1", VideoScaleMode.RATIO_2_39_1.key);
    }

    @Test public void quickCycleReturnsToFitAfterFourByThree() {
        VideoScaleMode mode = VideoScaleMode.FIT;
        mode = VideoScaleMode.nextQuickMode(mode);
        assertEquals(VideoScaleMode.CROP, mode);
        mode = VideoScaleMode.nextQuickMode(mode);
        assertEquals(VideoScaleMode.FILL, mode);
        mode = VideoScaleMode.nextQuickMode(mode);
        assertEquals(VideoScaleMode.RATIO_16_9, mode);
        mode = VideoScaleMode.nextQuickMode(mode);
        assertEquals(VideoScaleMode.RATIO_4_3, mode);
        assertEquals(VideoScaleMode.FIT, VideoScaleMode.nextQuickMode(mode));
    }
}
