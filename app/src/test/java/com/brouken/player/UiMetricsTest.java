package com.brouken.player;

import android.content.res.Configuration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UiMetricsTest {

    @Test
    public void classifiesPhoneTabletAndTvIndependentlyOfOrientation() {
        assertEquals(UiMetrics.DeviceClass.PHONE,
                UiMetrics.forTest(3f, 360, 360,
                        Configuration.ORIENTATION_PORTRAIT, false).deviceClass);
        assertEquals(UiMetrics.DeviceClass.TABLET_MEDIUM,
                UiMetrics.forTest(2f, 800, 600,
                        Configuration.ORIENTATION_LANDSCAPE, false).deviceClass);
        assertEquals(UiMetrics.DeviceClass.TABLET_LARGE,
                UiMetrics.forTest(2f, 1280, 720,
                        Configuration.ORIENTATION_LANDSCAPE, false).deviceClass);
        assertEquals(UiMetrics.DeviceClass.TV,
                UiMetrics.forTest(2f, 960, 360,
                        Configuration.ORIENTATION_LANDSCAPE, true).deviceClass);
    }

    @Test
    public void portraitPickerLeavesVisibleVideoStrip() {
        UiMetrics metrics = UiMetrics.forTest(3f, 360, 360,
                Configuration.ORIENTATION_PORTRAIT, false);

        assertEquals(304 * 3,
                metrics.pickerWidthPx(360, Configuration.ORIENTATION_PORTRAIT));
        assertTrue(metrics.rowMinHeight() >= 48 * 3);
    }

    @Test
    public void landscapePickerNeverExceedsSixtyPercentOrPortraitCap() {
        UiMetrics metrics = UiMetrics.forTest(2f, 640, 360,
                Configuration.ORIENTATION_LANDSCAPE, false);

        assertEquals(360 * 2,
                metrics.pickerWidthPx(640, Configuration.ORIENTATION_LANDSCAPE));
        assertTrue(metrics.pickerWidthPx(640, Configuration.ORIENTATION_LANDSCAPE)
                <= (640 - 56) * 2);
    }

    @Test
    public void pickerWidthCannotBecomeNegativeOnNarrowWindows() {
        UiMetrics metrics = UiMetrics.forTest(3f, 40, 40,
                Configuration.ORIENTATION_PORTRAIT, false);

        assertEquals(0,
                metrics.pickerWidthPx(40, Configuration.ORIENTATION_PORTRAIT));
    }

    @Test
    public void sameClassAndWidthAlsoTracksOrientation() {
        UiMetrics portrait = UiMetrics.forTest(3f, 360, 360,
                Configuration.ORIENTATION_PORTRAIT, false);
        UiMetrics same = UiMetrics.forTest(3f, 360, 360,
                Configuration.ORIENTATION_PORTRAIT, false);
        UiMetrics landscape = UiMetrics.forTest(3f, 360, 360,
                Configuration.ORIENTATION_LANDSCAPE, false);

        assertTrue(portrait.sameClassAndWidth(same));
        assertTrue(!portrait.sameClassAndWidth(landscape));
    }
}
