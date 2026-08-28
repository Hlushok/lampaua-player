package com.brouken.player;

import static com.brouken.player.VideoFreezePolicy.Action.BASELINE;
import static com.brouken.player.VideoFreezePolicy.Action.EXHAUSTED;
import static com.brouken.player.VideoFreezePolicy.Action.PREPARE;
import static com.brouken.player.VideoFreezePolicy.Action.SEEK_BACK_ONE_MS;
import static com.brouken.player.VideoFreezePolicy.Action.WAIT;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VideoFreezePolicyTest {
    @Test
    public void movingOutputKeepsRefreshingTheBaseline() {
        VideoFreezePolicy policy = new VideoFreezePolicy();

        assertEquals(BASELINE, policy.evaluate(active(0, 1_000, 10, 25f, true)));
        assertEquals(BASELINE, policy.evaluate(active(2_000, 3_000, 11, 25f, true)));
        assertEquals(WAIT, policy.evaluate(active(3_000, 4_000, 11, 25f, true)));
    }

    @Test
    public void frozenOutputNeedsBothElapsedTimeAndClockMovement() {
        VideoFreezePolicy policy = new VideoFreezePolicy();
        assertEquals(BASELINE, policy.evaluate(active(0, 10_000, 20, 25f, true)));

        assertEquals(WAIT, policy.evaluate(active(1_600, 10_500, 20, 25f, true)));
        assertEquals(SEEK_BACK_ONE_MS,
                policy.evaluate(active(2_000, 11_100, 20, 25f, true)));
    }

    @Test
    public void lowFrameRateGetsTwentyFrameIntervals() {
        VideoFreezePolicy policy = new VideoFreezePolicy();
        assertEquals(BASELINE, policy.evaluate(active(0, 0, 1, 5f, true)));

        assertEquals(WAIT, policy.evaluate(active(3_999, 3_999, 1, 5f, true)));
        assertEquals(SEEK_BACK_ONE_MS,
                policy.evaluate(active(4_000, 4_000, 1, 5f, true)));
    }

    @Test
    public void pauseSeekAndSurfaceChangesResetTheWindow() {
        VideoFreezePolicy policy = new VideoFreezePolicy();
        assertEquals(BASELINE, policy.evaluate(active(0, 0, 2, 25f, true)));
        assertEquals(BASELINE, policy.evaluate(new VideoFreezePolicy.Sample(
                2_000, 2_000, 2, 25f, true, false)));
        assertEquals(BASELINE, policy.evaluate(active(2_100, 2_100, 2, 25f, true)));
        assertEquals(WAIT, policy.evaluate(active(3_000, 3_000, 2, 25f, true)));
    }

    @Test
    public void recoveryBudgetIsSeekThenPrepareThenOneExhaustedSignal() {
        VideoFreezePolicy policy = new VideoFreezePolicy();

        assertEquals(BASELINE, policy.evaluate(active(0, 0, 3, 25f, true)));
        assertEquals(SEEK_BACK_ONE_MS, policy.evaluate(active(2_000, 2_000, 3, 25f, true)));
        assertEquals(BASELINE, policy.evaluate(active(2_100, 2_100, 3, 25f, true)));
        assertEquals(PREPARE, policy.evaluate(active(4_100, 4_100, 3, 25f, true)));
        assertEquals(BASELINE, policy.evaluate(active(4_200, 4_200, 3, 25f, true)));
        assertEquals(EXHAUSTED, policy.evaluate(active(6_200, 6_200, 3, 25f, true)));
        assertEquals(BASELINE, policy.evaluate(active(8_200, 8_200, 3, 25f, true)));
        assertEquals(WAIT, policy.evaluate(active(10_200, 10_200, 3, 25f, true)));
        assertEquals(2, policy.recoveries());
    }

    @Test
    public void unseekableMediaUsesPrepareAsTheFirstRung() {
        VideoFreezePolicy policy = new VideoFreezePolicy();

        assertEquals(BASELINE, policy.evaluate(active(0, 0, 4, 25f, false)));
        assertEquals(PREPARE, policy.evaluate(active(2_000, 2_000, 4, 25f, false)));
    }

    private static VideoFreezePolicy.Sample active(long now, long position, int output,
                                                    float frameRate, boolean seekable) {
        return new VideoFreezePolicy.Sample(now, position, output, frameRate, seekable, true);
    }
}
