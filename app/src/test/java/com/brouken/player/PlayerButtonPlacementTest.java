package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayerButtonPlacementTest {

    @Test
    public void availablePinnedActionStaysOnTheBar() {
        assertEquals(PlayerButtonPlacement.BAR,
                PlayerButtonPlacement.resolve(true, true));
    }

    @Test
    public void availableUnpinnedActionMovesToMore() {
        assertEquals(PlayerButtonPlacement.MORE,
                PlayerButtonPlacement.resolve(false, true));
    }

    @Test
    public void unavailableActionIsShownNowhere() {
        assertEquals(PlayerButtonPlacement.HIDDEN,
                PlayerButtonPlacement.resolve(true, false));
        assertEquals(PlayerButtonPlacement.HIDDEN,
                PlayerButtonPlacement.resolve(false, false));
    }
}
