package com.brouken.player;

import androidx.media3.common.C;

/** Pure timing calculations used by the subtitle renderer wrapper. */
final class SubtitleOffsetPolicy {
    private SubtitleOffsetPolicy() { }

    static long rendererPositionUs(long positionUs, double offsetSec, boolean timelineActive) {
        return !timelineActive && offsetSec < 0
                ? positionUs - (long) (offsetSec * C.MICROS_PER_SECOND)
                : positionUs;
    }

    static long timelinePositionUs(long positionMs, double offsetSec) {
        return positionMs * 1000L - (long) (offsetSec * C.MICROS_PER_SECOND);
    }

    static long cueDueMs(long presentationTimeUs, double offsetSec) {
        return presentationTimeUs / 1000L + (long) (offsetSec * 1000L);
    }
}
