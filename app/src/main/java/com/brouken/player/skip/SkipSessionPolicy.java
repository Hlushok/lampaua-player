package com.brouken.player.skip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Session-only skip choices applied after segments arrive from LAMPA or the configured API. */
public final class SkipSessionPolicy {
    private static final String MODE_BRIEF = "brief";
    private static final String MODE_AUTO = "auto";
    private static final String MODE_OFF = "off";

    private SkipSessionPolicy() {}

    public static List<SkipSegment> shiftAndValidate(List<SkipSegment> segments,
                                                     long durationMs, double offsetSec) {
        if (segments == null || segments.isEmpty() || durationMs <= 0) {
            return Collections.emptyList();
        }
        List<SkipSegment> validated = SkipPolicy.validate(segments, durationMs);
        if (validated.isEmpty()) return Collections.emptyList();

        long offsetMs = Math.round(offsetSec * 1000d);
        List<SkipSegment> shifted = new ArrayList<>(validated.size());
        for (SkipSegment segment : validated) {
            long start = clamp(segment.startMs + offsetMs, 0, durationMs);
            long end = clamp(segment.endMs + offsetMs, 0, durationMs);
            if (end <= start) continue;
            shifted.add(new SkipSegment(start, end, segment.kind, segment.source,
                    segment.wholeContentAd));
        }
        return SkipPolicy.validate(shifted, durationMs);
    }

    public static SkipPolicy.Mode modeFor(String sessionMode, String introMode,
                                          String creditsMode, SkipSegment segment,
                                          long durationMs) {
        if (segment != null && segment.kind == SkipSegment.Kind.AD) {
            return SkipPolicy.Mode.AUTO;
        }
        String value = sessionMode;
        if (value == null) {
            value = isCredits(segment, durationMs) ? creditsMode : introMode;
        }
        if (MODE_OFF.equals(value)) return SkipPolicy.Mode.OFF;
        if (MODE_AUTO.equals(value)) return SkipPolicy.Mode.AUTO;
        if (MODE_BRIEF.equals(value)) return SkipPolicy.Mode.BRIEF_BUTTON;
        return SkipPolicy.Mode.FULL_BUTTON;
    }

    public static String inheritedMode(String introMode, String creditsMode) {
        return introMode != null && introMode.equals(creditsMode) ? introMode : null;
    }

    public static boolean hasUserControlledSegments(List<SkipSegment> segments) {
        if (segments == null) return false;
        for (SkipSegment segment : segments) {
            if (segment != null && segment.kind != SkipSegment.Kind.AD) return true;
        }
        return false;
    }

    private static boolean isCredits(SkipSegment segment, long durationMs) {
        if (segment == null) return false;
        if (segment.kind == SkipSegment.Kind.OUTRO
                || segment.kind == SkipSegment.Kind.CREDITS) return true;
        return durationMs > 0 && segment.endMs >= Math.round(durationMs * 0.75d);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
