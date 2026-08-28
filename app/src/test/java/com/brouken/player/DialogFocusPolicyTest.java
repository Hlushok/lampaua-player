package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DialogFocusPolicyTest {
    @Test public void checkedRowWins() {
        assertEquals(3, DialogFocusPolicy.preferredListIndex(3, 6));
    }

    @Test public void firstRowIsTheFallback() {
        assertEquals(0, DialogFocusPolicy.preferredListIndex(-1, 6));
        assertEquals(0, DialogFocusPolicy.preferredListIndex(8, 6));
    }

    @Test public void emptyListHasNoFocusTarget() {
        assertEquals(-1, DialogFocusPolicy.preferredListIndex(0, 0));
    }
}
