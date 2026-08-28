package com.brouken.player;

final class LoadingUiPolicy {
    static final class Decision {
        final boolean showIndicator;
        final boolean showRecoveryNotice;

        Decision(boolean showIndicator, boolean showRecoveryNotice) {
            this.showIndicator = showIndicator;
            this.showRecoveryNotice = showRecoveryNotice;
        }
    }

    private LoadingUiPolicy() {}

    static Decision decide(boolean haveMedia, boolean buffering, boolean playing,
                           boolean delayElapsed, boolean recoveryPending) {
        boolean show = haveMedia && buffering && delayElapsed;
        return new Decision(show, show && recoveryPending && !playing);
    }
}
