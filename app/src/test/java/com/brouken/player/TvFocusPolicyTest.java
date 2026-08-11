package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TvFocusPolicyTest {
    @Test public void wakeWithVisibleControllerReturnsFocusToPlayPause() {
        assertEquals(TvFocusPolicy.Target.PLAY_PAUSE,
                TvFocusPolicy.choose(true, true, false, true));
    }

    @Test public void activeSkipActionKeepsPriority() {
        assertEquals(TvFocusPolicy.Target.SKIP,
                TvFocusPolicy.choose(true, true, true, true));
    }

    @Test public void loadingAndHiddenControllersDoNotStealFocus() {
        assertEquals(TvFocusPolicy.Target.NONE,
                TvFocusPolicy.choose(true, true, false, false));
        assertEquals(TvFocusPolicy.Target.NONE,
                TvFocusPolicy.choose(true, false, false, true));
        assertEquals(TvFocusPolicy.Target.NONE,
                TvFocusPolicy.choose(false, true, false, true));
    }
}
