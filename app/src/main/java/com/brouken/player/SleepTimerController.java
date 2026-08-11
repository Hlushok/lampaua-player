package com.brouken.player;

final class SleepTimerController {
    interface Clock { long now(); }

    static final class Tick {
        final long remainingMs;
        final float volumeFactor;
        final boolean fire;

        Tick(long remainingMs, float volumeFactor, boolean fire) {
            this.remainingMs = remainingMs;
            this.volumeFactor = volumeFactor;
            this.fire = fire;
        }
    }

    private static final long FADE_MS = 30_000L;
    private final Clock clock;
    private long deadlineMs;
    private boolean atMediaEnd;

    SleepTimerController(Clock clock) {
        this.clock = clock;
    }

    void armAt(long elapsedRealtimeMs) {
        deadlineMs = Math.max(1L, elapsedRealtimeMs);
        atMediaEnd = false;
    }

    void armAfter(long durationMs) {
        armAt(clock.now() + Math.max(0L, durationMs));
    }

    void armAtMediaEnd() {
        deadlineMs = 0L;
        atMediaEnd = true;
    }

    void cancel() {
        deadlineMs = 0L;
        atMediaEnd = false;
    }

    boolean isArmed() { return deadlineMs > 0 || atMediaEnd; }
    boolean isAtMediaEnd() { return atMediaEnd; }

    long remainingMs() {
        return deadlineMs == 0 ? 0 : Math.max(0, deadlineMs - clock.now());
    }

    Tick tick(boolean mediaEnded) {
        if (atMediaEnd) return new Tick(0, 1f, mediaEnded);
        if (deadlineMs == 0) return new Tick(0, 1f, false);
        long remaining = remainingMs();
        if (remaining == 0) return new Tick(0, 0f, true);
        float factor = remaining >= FADE_MS ? 1f : remaining / (float) FADE_MS;
        return new Tick(remaining, Math.max(0f, Math.min(1f, factor)), false);
    }
}
