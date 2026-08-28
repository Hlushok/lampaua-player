package com.brouken.player.skip;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkipSessionPolicyTest {
    @Test public void sessionModeOverridesIntroAndCreditsButNotAds() {
        SkipSegment intro = new SkipSegment(10_000, 20_000,
                SkipSegment.Kind.INTRO, "server");
        SkipSegment credits = new SkipSegment(80_000, 90_000,
                SkipSegment.Kind.CREDITS, "server");
        SkipSegment ad = new SkipSegment(30_000, 35_000,
                SkipSegment.Kind.AD, "server");

        assertEquals(SkipPolicy.Mode.OFF, SkipSessionPolicy.modeFor(
                "off", "brief", "auto",
                intro, 90_000));
        assertEquals(SkipPolicy.Mode.OFF, SkipSessionPolicy.modeFor(
                "off", "brief", "auto",
                credits, 90_000));
        assertEquals(SkipPolicy.Mode.AUTO, SkipSessionPolicy.modeFor(
                "off", "brief", "auto",
                ad, 90_000));
    }

    @Test public void offsetMovesSegmentsAndClipsAtMediaBounds() {
        List<SkipSegment> shifted = SkipSessionPolicy.shiftAndValidate(Arrays.asList(
                new SkipSegment(0, 10_000, SkipSegment.Kind.INTRO, "server"),
                new SkipSegment(85_000, 90_000, SkipSegment.Kind.CREDITS, "server")),
                90_000, 3.5);

        assertEquals(2, shifted.size());
        assertEquals(3_500, shifted.get(0).startMs);
        assertEquals(13_500, shifted.get(0).endMs);
        assertEquals(88_500, shifted.get(1).startMs);
        assertEquals(90_000, shifted.get(1).endMs);
    }

    @Test public void inheritedChoiceOnlyExistsWhenGlobalModesAgree() {
        assertEquals("auto", SkipSessionPolicy.inheritedMode("auto", "auto"));
        assertNull(SkipSessionPolicy.inheritedMode(
                "brief", "auto"));
        assertTrue(SkipSessionPolicy.hasUserControlledSegments(Arrays.asList(
                new SkipSegment(0, 1000, SkipSegment.Kind.AD, "server"),
                new SkipSegment(2000, 3000, SkipSegment.Kind.RECAP, "server"))));
    }

    @Test public void offsetCannotMakeAnInvalidSourceSegmentValid() {
        List<SkipSegment> shifted = SkipSessionPolicy.shiftAndValidate(Arrays.asList(
                new SkipSegment(0, 80_000, SkipSegment.Kind.INTRO, "server")),
                90_000, -30);

        assertTrue(shifted.isEmpty());
    }
}
