package com.brouken.player;

/** Width-budget decisions for responsive picker rows. Values are expressed in dp. */
final class PickerRowPolicy {
    private PickerRowPolicy() { }

    static int textColumnWidthDp(int panelWidthDp, int horizontalPaddingDp,
                                 int leadingWidthDp, int trailingWidthDp, int gapsDp) {
        return Math.max(0, panelWidthDp
                - Math.max(0, horizontalPaddingDp)
                - Math.max(0, leadingWidthDp)
                - Math.max(0, trailingWidthDp)
                - Math.max(0, gapsDp));
    }

    static boolean showOptionalTrailingText(int textColumnWidthDp, int minimumTitleWidthDp) {
        return textColumnWidthDp >= Math.max(0, minimumTitleWidthDp);
    }

    static int compactPosterWidthDp(int panelWidthDp) {
        return panelWidthDp >= 400 ? 42 : 34;
    }
}
