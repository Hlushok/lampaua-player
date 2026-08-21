package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HoldSpeedPolicyTest {

    @Test
    public void holdStartsAtTwoAndRightDragRaisesToFour() {
        assertEquals(2f, HoldSpeedPolicy.evaluate(0f, false).speed, 0f);
        assertEquals(4f, HoldSpeedPolicy.evaluate(80f, false).speed, 0f);
        assertEquals(HoldSpeedPolicy.Direction.FORWARD,
                HoldSpeedPolicy.evaluate(80f, false).direction);
    }

    @Test
    public void leftDragCrossesIntoBoundedRewind() {
        HoldSpeedPolicy.State state = HoldSpeedPolicy.evaluate(-80f, false);

        assertEquals(HoldSpeedPolicy.Direction.REWIND, state.direction);
        assertEquals(3f, state.speed, 0f);
        assertEquals(4f, HoldSpeedPolicy.evaluate(-400f, true).speed, 0f);
    }

    @Test
    public void rewindHysteresisPreventsBoundaryThrashing() {
        assertEquals(HoldSpeedPolicy.Direction.REWIND,
                HoldSpeedPolicy.evaluate(-38f, true).direction);
        assertEquals(HoldSpeedPolicy.Direction.FORWARD,
                HoldSpeedPolicy.evaluate(-35f, true).direction);
    }
}
