package com.brouken.player.skip;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SkipPolicyTest {
    @Test public void rejectsInvalidAndImplausiblyLongSegments() {
        List<SkipSegment> valid = SkipPolicy.validate(Arrays.asList(
                new SkipSegment(-1, 1000, SkipSegment.Kind.INTRO, "a"),
                new SkipSegment(5000, 4000, SkipSegment.Kind.INTRO, "a"),
                new SkipSegment(0, 40_000, SkipSegment.Kind.INTRO, "a"),
                new SkipSegment(10_000, 20_000, SkipSegment.Kind.INTRO, "a")), 90_000);
        assertEquals(1, valid.size());
        assertEquals(10_000, valid.get(0).startMs);
    }

    @Test public void clampsSmallEndToleranceAndMergesSameSource() {
        List<SkipSegment> valid = SkipPolicy.validate(Arrays.asList(
                new SkipSegment(70_000, 80_000, SkipSegment.Kind.CREDITS, "server"),
                new SkipSegment(79_000, 90_900, SkipSegment.Kind.CREDITS, "server")), 90_000);
        assertEquals(1, valid.size());
        assertEquals(90_000, valid.get(0).endMs);
    }

    @Test public void briefButtonExpiresFiveSecondsIntoSegment() {
        SkipController controller = new SkipController();
        List<SkipSegment> segments = Collections.singletonList(
                new SkipSegment(10_000, 30_000, SkipSegment.Kind.INTRO, "server"));
        assertEquals(SkipController.State.AVAILABLE,
                controller.update(segments, 12_000, 60_000, false,
                        SkipPolicy.Mode.BRIEF_BUTTON, 0).state);
        assertEquals(SkipController.State.HIDDEN,
                controller.update(segments, 15_001, 60_000, false,
                        SkipPolicy.Mode.BRIEF_BUTTON, 0).state);
    }

    @Test public void automaticSkipCanBeCancelled() {
        SkipController controller = new SkipController();
        List<SkipSegment> segments = Collections.singletonList(
                new SkipSegment(10_000, 20_000, SkipSegment.Kind.INTRO, "server"));
        SkipController.Model pending = controller.update(segments, 10_000, 60_000, false,
                SkipPolicy.Mode.AUTO, 1000);
        assertEquals(SkipController.State.AUTO_PENDING, pending.state);
        assertEquals(SkipController.State.HIDDEN,
                controller.activate(pending, 10_000, 60_000, false, 1100).state);
        assertEquals(SkipController.State.HIDDEN,
                controller.update(segments, 12_000, 60_000, false,
                        SkipPolicy.Mode.AUTO, 5000).state);
    }

    @Test public void sessionOffModeDoesNotOfferOrTriggerSegment() {
        SkipController controller = new SkipController();
        List<SkipSegment> segments = Collections.singletonList(
                new SkipSegment(10_000, 20_000, SkipSegment.Kind.INTRO, "server"));
        assertEquals(SkipController.State.HIDDEN,
                controller.update(segments, 12_000, 60_000, false,
                        SkipPolicy.Mode.OFF, 1000).state);
    }

    @Test public void skippedCreditsAdvanceAndCanUndo() {
        SkipController controller = new SkipController();
        List<SkipSegment> segments = Collections.singletonList(
                new SkipSegment(50_000, 60_000, SkipSegment.Kind.CREDITS, "server"));
        SkipController.Model available = controller.update(segments, 50_000, 60_000, true,
                SkipPolicy.Mode.FULL_BUTTON, 1000);
        SkipController.Model action = controller.activate(available, 50_000, 60_000, true, 1000);
        assertEquals(SkipController.Action.PLAY_NEXT, action.action);
        SkipController.Model undo = controller.update(segments, 0, 60_000, true,
                SkipPolicy.Mode.FULL_BUTTON, 2000);
        assertEquals(SkipController.State.UNDO_AVAILABLE, undo.state);
        assertEquals(SkipController.Action.RESTORE_POSITION,
                controller.activate(undo, 0, 60_000, true, 2000).action);
    }
}
