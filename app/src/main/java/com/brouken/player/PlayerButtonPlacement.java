package com.brouken.player;

enum PlayerButtonPlacement {
    BAR,
    MORE,
    HIDDEN;

    static PlayerButtonPlacement resolve(boolean pinned, boolean available) {
        if (!available) return HIDDEN;
        return pinned ? BAR : MORE;
    }
}
