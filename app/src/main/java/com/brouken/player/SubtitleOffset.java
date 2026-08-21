package com.brouken.player;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ForwardingRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.text.TextOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Applies a session subtitle offset around Media3's text renderer.
 *
 * <p>Adapted from Just+ Player PR #130 by Oleksandr Zhyzhchenko (Unlicense).
 * Positive values show subtitles later; negative values show them earlier.
 */
final class SubtitleOffset implements TextOutput {
    interface Position {
        long currentMs();
        boolean playing();
    }

    private static final long TICK_MS = 50;
    private static final long HISTORY_MS = 32_000;

    private final TextOutput output;
    private final Position position;
    private final Handler handler;
    private final List<CueGroup> groups = new ArrayList<>();
    private final Runnable release = this::release;
    private int next;
    private volatile SubtitleTimeline timeline;
    private int[] painted;
    private volatile double offsetSec;

    SubtitleOffset(TextOutput output, Looper outputLooper, Position position) {
        this.output = output;
        this.position = position;
        this.handler = new Handler(outputLooper);
    }

    Renderer wrap(Renderer textRenderer) {
        return new OffsetRenderer(textRenderer);
    }

    void setTimeline(SubtitleTimeline timeline) {
        boolean same = this.timeline == timeline;
        this.timeline = timeline;
        if (!same) {
            groups.clear();
            next = 0;
            painted = null;
        }
        release();
    }

    void wake() {
        release();
    }

    void setOffsetSec(double sec) {
        offsetSec = sec;
        long nowMs = position.currentMs();
        while (next > 0 && dueMs(groups.get(next - 1)) > nowMs) next--;
        release();
    }

    void clear() {
        handler.removeCallbacks(release);
        groups.clear();
        next = 0;
        painted = null;
        release();
    }

    @Override
    public void onCues(CueGroup cueGroup) {
        if (timeline != null) return;
        groups.add(cueGroup);
        release();
    }

    @Override
    public void onCues(List<Cue> cues) {
        // Media3 sends this deprecated form immediately before the CueGroup form.
    }

    private void release() {
        handler.removeCallbacks(release);
        long nowMs = position.currentMs();
        SubtitleTimeline own = timeline;
        if (own != null) {
            if (nowMs != C.TIME_UNSET) paint(own, nowMs);
            if (position.playing()) handler.postDelayed(release, TICK_MS);
            return;
        }

        long dueBy = offsetSec <= 0 || nowMs == C.TIME_UNSET ? Long.MAX_VALUE : nowMs;
        while (next < groups.size() && dueMs(groups.get(next)) <= dueBy) {
            CueGroup group = groups.get(next++);
            output.onCues(group.cues);
            output.onCues(group);
        }
        if (next < groups.size()) handler.postDelayed(release, TICK_MS);

        int stale = 0;
        while (stale < next && nowMs != C.TIME_UNSET
                && groups.get(stale).presentationTimeUs / 1000 < nowMs - HISTORY_MS) {
            stale++;
        }
        if (stale > 0) {
            groups.subList(0, stale).clear();
            next -= stale;
        }
    }

    private void paint(SubtitleTimeline timeline, long nowMs) {
        long nowUs = nowMs * 1000L;
        int[] visible = timeline.visibleAt(
                SubtitleOffsetPolicy.timelinePositionUs(nowMs, offsetSec));
        if (Arrays.equals(visible, painted)) return;
        painted = visible;
        CueGroup group = new CueGroup(timeline.cuesOf(visible), nowUs);
        output.onCues(group.cues);
        output.onCues(group);
    }

    private long dueMs(CueGroup group) {
        return SubtitleOffsetPolicy.cueDueMs(group.presentationTimeUs, offsetSec);
    }

    private final class OffsetRenderer extends ForwardingRenderer {
        OffsetRenderer(Renderer textRenderer) {
            super(textRenderer);
        }

        @Override
        public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
            super.render(SubtitleOffsetPolicy.rendererPositionUs(
                    positionUs, offsetSec, timeline != null), elapsedRealtimeUs);
        }

        @Override
        public boolean isEnded() {
            return super.isEnded() || (isCurrentStreamFinal() && hasReadStreamToEnd());
        }
    }
}
