package com.brouken.player.skip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SkipPolicy {
    public enum Mode { BRIEF_BUTTON, FULL_BUTTON, AUTO }

    private SkipPolicy() {}

    public static List<SkipSegment> validate(List<SkipSegment> input, long durationMs) {
        if (input == null || durationMs <= 0) return Collections.emptyList();
        List<SkipSegment> valid = new ArrayList<>();
        for (SkipSegment segment : input) {
            if (segment == null || segment.startMs < 0 || segment.endMs <= segment.startMs) continue;
            if (segment.endMs > durationMs + 1500) continue;
            long end = Math.min(durationMs, segment.endMs);
            if (!segment.wholeContentAd && end - segment.startMs > durationMs / 3) continue;
            valid.add(new SkipSegment(segment.startMs, end, segment.kind,
                    segment.source, segment.wholeContentAd));
        }
        valid.sort(Comparator.comparingLong(value -> value.startMs));
        List<SkipSegment> merged = new ArrayList<>();
        for (SkipSegment segment : valid) {
            if (!merged.isEmpty()) {
                SkipSegment previous = merged.get(merged.size() - 1);
                if (previous.endMs >= segment.startMs && previous.kind == segment.kind
                        && previous.source.equals(segment.source)) {
                    merged.set(merged.size() - 1, new SkipSegment(previous.startMs,
                            Math.max(previous.endMs, segment.endMs), previous.kind,
                            previous.source, previous.wholeContentAd || segment.wholeContentAd));
                    continue;
                }
            }
            merged.add(segment);
        }
        return merged;
    }
}
