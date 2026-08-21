package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OffsetPanelTest {

    @Test
    public void formatsSignedSecondsWithoutTrailingZeros() {
        assertEquals("0 s", OffsetPanel.format(0));
        assertEquals("+2.5 s", OffsetPanel.format(2.5));
        assertEquals("-10 s", OffsetPanel.format(-10));
    }
}
