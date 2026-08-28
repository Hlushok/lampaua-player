package com.brouken.player;

final class DialogFocusPolicy {
    private DialogFocusPolicy() {}

    static int preferredListIndex(int checkedIndex, int itemCount) {
        if (itemCount <= 0) return -1;
        return checkedIndex >= 0 && checkedIndex < itemCount ? checkedIndex : 0;
    }
}
