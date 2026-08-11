package com.brouken.player;

final class TvFocusPolicy {
    enum Target { NONE, SKIP, PLAY_PAUSE }

    private TvFocusPolicy() { }

    static Target choose(boolean tvDevice, boolean controllerVisible,
                         boolean skipAvailable, boolean playPauseVisible) {
        if (!tvDevice || !controllerVisible) return Target.NONE;
        if (skipAvailable) return Target.SKIP;
        return playPauseVisible ? Target.PLAY_PAUSE : Target.NONE;
    }
}
