package com.brouken.player;

import com.brouken.player.skip.IntentSegmentsSource;
import com.brouken.player.skip.SkipSegment;

import org.json.JSONObject;

/** Resume rules for the nested Playlist API only. All positions are milliseconds. */
final class PlaylistResumePolicy {
    static final class Decision {
        final long positionMs;
        final boolean ask;

        Decision(long positionMs, boolean ask) {
            this.positionMs = positionMs;
            this.ask = ask;
        }
    }

    private PlaylistResumePolicy() { }

    static long duration(long storedMs, long sessionMs, String segments) {
        if (storedMs > 0) return storedMs;
        if (sessionMs > 0) return sessionMs;
        if (segments == null) return 0;
        try {
            Long value = PlaylistApi.millisNumber(new JSONObject(segments).opt("duration_ms"));
            return value == null ? 0 : Math.max(0, value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    static Decision decide(long positionMs, long durationMs, String segments, boolean askResume) {
        long position = Math.max(0, positionMs);
        if (askResume && position < 30_000) return new Decision(0, false);
        if (durationMs > 0) {
            // ceil(duration * 95 / 100), without multiplication overflow or double rounding.
            long watchedAt = durationMs / 100 * 95 + (durationMs % 100 * 95 + 99) / 100;
            if (position >= watchedAt) return new Decision(0, false);
            if (segments != null) {
                for (SkipSegment segment : new IntentSegmentsSource(segments).getSegments(durationMs / 1000.0)) {
                    // Ads and implausibly long/early markers are not closing credits.
                    if (segment.type == SkipSegment.Type.SKIP && segment.startSec >= 0
                            && segment.endSec * 1000 >= durationMs * .95
                            && (segment.endSec - segment.startSec) * 1000 <= durationMs * .15
                            && position >= segment.startSec * 1000) {
                        return new Decision(0, false);
                    }
                }
            }
        }
        return new Decision(position, askResume && position > 0);
    }
}
