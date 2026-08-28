package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TopPanelPolicyTest {

    @Test
    public void narrowPortraitKeepsTheTitleColumnReadableBeforeThePoster() {
        assertEquals(104, TopPanelPolicy.posterWidth(
                332, 107, 144, 84));
        assertEquals(84, TopPanelPolicy.titleWidth(
                332, 104, 144));

        assertEquals(64, TopPanelPolicy.posterWidth(
                292, 107, 144, 84));
        assertEquals(84, TopPanelPolicy.titleWidth(
                292, 64, 144));
    }

    @Test
    public void widePanelsKeepThePreferredPosterAndExtraTitleSpace() {
        assertEquals(107, TopPanelPolicy.posterWidth(
                600, 107, 144, 84));
        assertEquals(349, TopPanelPolicy.titleWidth(
                600, 107, 144));
    }

    @Test
    public void impossibleGeometryIsClampedInsteadOfBecomingNegative() {
        assertEquals(0, TopPanelPolicy.posterWidth(
                120, 107, 144, 84));
        assertEquals(0, TopPanelPolicy.titleWidth(
                120, 0, 144));
    }
}
