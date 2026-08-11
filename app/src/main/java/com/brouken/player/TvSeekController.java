package com.brouken.player;

final class TvSeekController {
    static final int BACKWARD = -1;
    static final int FORWARD = 1;

    private long offsetMs;
    private long lastEventMs = Long.MIN_VALUE;
    private int lastDirection;
    private int steps;
    private boolean armed;

    void press(int direction, long eventTimeMs) {
        update(direction, eventTimeMs, false);
    }

    void hold(int direction, long eventTimeMs) {
        update(direction, eventTimeMs, true);
    }

    void release(long eventTimeMs) {
        if (armed) lastEventMs = eventTimeMs;
    }

    long previewTarget(long currentPosition, long duration) {
        return clamp(currentPosition + offsetMs, duration);
    }

    long consumeTarget(long currentPosition, long duration) {
        long target = previewTarget(currentPosition, duration);
        reset();
        return target;
    }

    boolean isArmed() {
        return armed;
    }

    void reset() {
        offsetMs = 0;
        lastEventMs = Long.MIN_VALUE;
        lastDirection = 0;
        steps = 0;
        armed = false;
    }

    private void update(int direction, long eventTimeMs, boolean held) {
        direction = direction < 0 ? BACKWARD : FORWARD;
        boolean burst = lastDirection == direction && lastEventMs != Long.MIN_VALUE
                && eventTimeMs - lastEventMs <= 450;
        steps = burst || held ? steps + 1 : 0;
        lastDirection = direction;
        lastEventMs = eventTimeMs;
        offsetMs += direction * stepFor(steps);
        armed = true;
    }

    private static long stepFor(int steps) {
        if (steps < 1) return 10_000L;
        if (steps < 5) return 30_000L;
        if (steps < 12) return 60_000L;
        return 120_000L;
    }

    private static long clamp(long value, long duration) {
        long max = duration <= 0 || duration == Long.MIN_VALUE ? Long.MAX_VALUE : duration;
        return Math.max(0, Math.min(max, value));
    }
}
