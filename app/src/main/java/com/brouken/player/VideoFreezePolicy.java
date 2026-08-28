package com.brouken.player;

/** Detects a stalled video renderer while the playback clock continues to advance. */
final class VideoFreezePolicy {
    private static final long MIN_WINDOW_MS = 1_500L;
    private static final long MIN_POSITION_PROGRESS_MS = 1_000L;
    private static final int FRAME_WINDOW = 20;
    private static final int MAX_RECOVERIES = 2;

    enum Action {
        BASELINE,
        WAIT,
        SEEK_BACK_ONE_MS,
        PREPARE,
        EXHAUSTED
    }

    static final class Sample {
        final long nowMs;
        final long positionMs;
        final int outputCount;
        final float frameRate;
        final boolean seekable;
        final boolean eligible;

        Sample(long nowMs, long positionMs, int outputCount, float frameRate,
               boolean seekable, boolean eligible) {
            this.nowMs = nowMs;
            this.positionMs = positionMs;
            this.outputCount = outputCount;
            this.frameRate = frameRate;
            this.seekable = seekable;
            this.eligible = eligible;
        }
    }

    private int outputSeen = -1;
    private long outputSeenAtMs;
    private long outputSeenAtPositionMs;
    private int recoveries;
    private boolean exhaustedEmitted;

    Action evaluate(Sample sample) {
        if (sample == null || !sample.eligible || sample.outputCount < 0) {
            resetWindow();
            return Action.BASELINE;
        }
        if (outputSeen < 0 || sample.outputCount != outputSeen
                || sample.positionMs < outputSeenAtPositionMs) {
            baseline(sample);
            return Action.BASELINE;
        }

        long frameWindowMs = sample.frameRate > 0f
                ? (long) Math.ceil(FRAME_WINDOW * 1_000d / sample.frameRate) : 0L;
        long windowMs = Math.max(MIN_WINDOW_MS, frameWindowMs);
        if (sample.nowMs - outputSeenAtMs < windowMs
                || sample.positionMs - outputSeenAtPositionMs < MIN_POSITION_PROGRESS_MS) {
            return Action.WAIT;
        }

        resetWindow();
        if (recoveries >= MAX_RECOVERIES) {
            if (exhaustedEmitted) return Action.WAIT;
            exhaustedEmitted = true;
            return Action.EXHAUSTED;
        }
        recoveries++;
        return recoveries == 1 && sample.seekable
                ? Action.SEEK_BACK_ONE_MS : Action.PREPARE;
    }

    void resetWindow() {
        outputSeen = -1;
        outputSeenAtMs = 0L;
        outputSeenAtPositionMs = 0L;
    }

    void resetItem() {
        resetWindow();
        recoveries = 0;
        exhaustedEmitted = false;
    }

    int recoveries() {
        return recoveries;
    }

    private void baseline(Sample sample) {
        outputSeen = sample.outputCount;
        outputSeenAtMs = sample.nowMs;
        outputSeenAtPositionMs = sample.positionMs;
    }
}
