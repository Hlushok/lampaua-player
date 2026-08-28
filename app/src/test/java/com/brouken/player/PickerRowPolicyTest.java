package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PickerRowPolicyTest {

    @Test
    public void portraitQualityRowKeepsAReadableWeightedTextColumn() {
        int text = PickerRowPolicy.textColumnWidthDp(
                304, 44, 0, 100, 10);

        assertEquals(150, text);
        assertTrue(PickerRowPolicy.showOptionalTrailingText(text, 112));
    }

    @Test
    public void optionalBitrateDropsBeforeTheTitleCanCollapse() {
        int withBitrate = PickerRowPolicy.textColumnWidthDp(
                260, 44, 22, 100, 32);

        assertEquals(62, withBitrate);
        assertFalse(PickerRowPolicy.showOptionalTrailingText(withBitrate, 112));
        assertEquals(162, PickerRowPolicy.textColumnWidthDp(
                260, 44, 22, 0, 32));
    }

    @Test
    public void geometryIsBoundedForVeryNarrowAndWidePanels() {
        assertEquals(0, PickerRowPolicy.textColumnWidthDp(40, 44, 22, 100, 32));
        assertEquals(34, PickerRowPolicy.compactPosterWidthDp(304));
        assertEquals(42, PickerRowPolicy.compactPosterWidthDp(440));
    }
}
