package com.brouken.player;

final class BackExitGuard {
    private final long timeoutMs;
    private long armedAtMs = Long.MIN_VALUE;

    BackExitGuard(long timeoutMs) {
        if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be positive");
        this.timeoutMs = timeoutMs;
    }

    boolean shouldExit(long nowMs) {
        if (armedAtMs != Long.MIN_VALUE && nowMs >= armedAtMs
                && nowMs - armedAtMs <= timeoutMs) {
            reset();
            return true;
        }
        armedAtMs = nowMs;
        return false;
    }

    void reset() {
        armedAtMs = Long.MIN_VALUE;
    }
}
