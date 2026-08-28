package com.brouken.player;

/** Donor-authoritative placement for player controls. */
final class PlayerControlPolicy {
    enum Surface {
        MEDIA_BAR,
        DISPLAY_CLUSTER,
        TIME_ROW,
        MORE_PLAYBACK,
        MORE_SESSION,
        MORE_SYSTEM,
        HIDDEN
    }

    private PlayerControlPolicy() { }

    private static Surface media(boolean available) {
        return available ? Surface.MEDIA_BAR : Surface.HIDDEN;
    }

    static Surface quality(boolean available) { return media(available); }
    static Surface audio(boolean available) { return media(available); }
    static Surface subtitles(boolean available) { return media(available); }
    static Surface playlist(boolean available) { return media(available); }
    static Surface repeat(boolean available) { return media(available); }

    static Surface displayMode(boolean television, boolean available) {
        if (!available) return Surface.HIDDEN;
        return television ? Surface.MEDIA_BAR : Surface.DISPLAY_CLUSTER;
    }

    static Surface lock(boolean television, boolean available) {
        return available && !television ? Surface.TIME_ROW : Surface.HIDDEN;
    }

    static Surface speed(boolean available) {
        return available ? Surface.MORE_PLAYBACK : Surface.HIDDEN;
    }

    static Surface together(boolean available) {
        return available ? Surface.MORE_SESSION : Surface.HIDDEN;
    }

    static Surface settings() {
        return Surface.MORE_SYSTEM;
    }
}
