package com.brouken.player;

final class HoldSpeedPolicy {

    private static final float ANCHOR_SPEED = 2f;
    private static final float STEP_DP = 40f;
    private static final float MAX_SPEED = 4f;
    private static final float REWIND_EXIT = 1.1f;

    enum Direction {
        FORWARD,
        REWIND
    }

    static final class State {
        final Direction direction;
        final float speed;

        State(Direction direction, float speed) {
            this.direction = direction;
            this.speed = speed;
        }
    }

    private HoldSpeedPolicy() { }

    static State evaluate(float deltaDp, boolean wasRewinding) {
        float value = ANCHOR_SPEED + deltaDp / STEP_DP;
        boolean rewind = wasRewinding ? value < REWIND_EXIT : value < 1f;
        float speed = rewind
                ? ANCHOR_SPEED + (1f - value)
                : Math.max(1f, value);
        speed = Math.min(MAX_SPEED, Math.max(1f, speed));
        speed = Math.round(speed * 10f) / 10f;
        return new State(rewind ? Direction.REWIND : Direction.FORWARD, speed);
    }
}
