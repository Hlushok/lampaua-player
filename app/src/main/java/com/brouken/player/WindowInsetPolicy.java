package com.brouken.player;

/** Pure window-inset decisions shared by the player overlay and JVM tests. */
final class WindowInsetPolicy {
    private WindowInsetPolicy() { }

    static int stableTopInset(int visibleStatusBarTop, int ignoringVisibilityStatusBarTop) {
        return Math.max(0, Math.max(visibleStatusBarTop, ignoringVisibilityStatusBarTop));
    }

    static int symmetricHorizontalInset(int left, int right) {
        return Math.max(0, Math.max(left, right));
    }
}
