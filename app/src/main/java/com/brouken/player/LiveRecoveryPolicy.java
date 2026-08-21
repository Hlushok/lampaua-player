package com.brouken.player;

final class LiveRecoveryPolicy {
    static final int MAX_ATTEMPTS = 2;
    static final long RESET_AFTER_MS = 60_000L;

    private LiveRecoveryPolicy() {}

    static int effectiveAttempts(int attempts, long nowMs, long lastAttemptAtMs) {
        return lastAttemptAtMs > 0L && nowMs - lastAttemptAtMs >= RESET_AFTER_MS ? 0 : attempts;
    }

    static boolean canRejoin(int attempts, long nowMs, long lastAttemptAtMs) {
        return effectiveAttempts(attempts, nowMs, lastAttemptAtMs) < MAX_ATTEMPTS;
    }
}
