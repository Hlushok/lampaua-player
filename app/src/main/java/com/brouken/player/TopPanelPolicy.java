package com.brouken.player;

/** Width budgeting for the poster/title/clock controller card. */
final class TopPanelPolicy {
    private TopPanelPolicy() { }

    static int posterWidth(int panelWidth, int preferredPosterWidth,
                           int fixedWidth, int minimumTitleWidth) {
        int available = Math.max(0, panelWidth)
                - Math.max(0, fixedWidth)
                - Math.max(0, minimumTitleWidth);
        return Math.max(0, Math.min(Math.max(0, preferredPosterWidth), available));
    }

    static int titleWidth(int panelWidth, int posterWidth, int fixedWidth) {
        return Math.max(0, Math.max(0, panelWidth)
                - Math.max(0, posterWidth)
                - Math.max(0, fixedWidth));
    }
}
