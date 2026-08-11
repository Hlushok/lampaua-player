package com.brouken.player.skip;

public final class SkipSegment {
    public enum Kind { INTRO, RECAP, AD, OUTRO, CREDITS, PREVIEW, UNKNOWN }

    public final long startMs;
    public final long endMs;
    public final Kind kind;
    public final String source;
    public final boolean wholeContentAd;

    public SkipSegment(long startMs, long endMs, Kind kind, String source) {
        this(startMs, endMs, kind, source, false);
    }

    public SkipSegment(long startMs, long endMs, Kind kind, String source,
                       boolean wholeContentAd) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.kind = kind == null ? Kind.UNKNOWN : kind;
        this.source = source == null ? "" : source;
        this.wholeContentAd = wholeContentAd;
    }

    public boolean contains(long positionMs) {
        return positionMs >= startMs && positionMs < endMs;
    }

    public String key() {
        return startMs + ":" + endMs + ":" + kind + ":" + source;
    }
}
