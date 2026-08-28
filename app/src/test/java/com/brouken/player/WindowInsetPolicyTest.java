package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WindowInsetPolicyTest {

    @Test
    public void hiddenTransientStatusBarKeepsStableTopInset() {
        assertEquals(72, WindowInsetPolicy.stableTopInset(0, 72));
        assertEquals(72, WindowInsetPolicy.stableTopInset(72, 0));
        assertEquals(84, WindowInsetPolicy.stableTopInset(72, 84));
    }

    @Test
    public void negativeInsetValuesAreIgnored() {
        assertEquals(0, WindowInsetPolicy.stableTopInset(-1, -20));
        assertEquals(0, WindowInsetPolicy.symmetricHorizontalInset(-4, -8));
    }

    @Test
    public void asymmetricHorizontalInsetsBecomeSymmetric() {
        assertEquals(64, WindowInsetPolicy.symmetricHorizontalInset(64, 12));
        assertEquals(64, WindowInsetPolicy.symmetricHorizontalInset(8, 64));
    }
}
