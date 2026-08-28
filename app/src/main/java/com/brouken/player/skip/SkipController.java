package com.brouken.player.skip;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SkipController {
    public enum State { HIDDEN, COUNTDOWN, AVAILABLE, AUTO_PENDING, UNDO_AVAILABLE }
    public enum Action { NONE, SEEK_TO_END, PLAY_NEXT, RESTORE_POSITION }

    public static final class Model {
        public final State state;
        public final Action action;
        public final SkipSegment segment;
        public final long targetMs;
        public final int progress;
        public final long seconds;

        private Model(State state, Action action, SkipSegment segment, long targetMs,
                      int progress, long seconds) {
            this.state = state;
            this.action = action;
            this.segment = segment;
            this.targetMs = targetMs;
            this.progress = progress;
            this.seconds = seconds;
        }

        public boolean enabled() {
            return state == State.AVAILABLE || state == State.AUTO_PENDING
                    || state == State.UNDO_AVAILABLE;
        }
    }

    private static final long PREVIEW_MS = 5000;
    private static final long BRIEF_MS = 5000;
    private static final long AUTO_COUNTDOWN_MS = 3000;
    private static final long UNDO_MS = 5000;
    private final Set<String> dismissed = new HashSet<>();
    private final Set<String> completed = new HashSet<>();
    private SkipSegment autoSegment;
    private long autoDeadlineMs;
    private long undoUntilMs;
    private long undoPositionMs;
    private SkipSegment currentSegment;

    public void reset() {
        dismissed.clear(); completed.clear(); autoSegment = null;
        undoUntilMs = 0; currentSegment = null;
    }

    public Model update(List<SkipSegment> segments, long positionMs, long durationMs,
                        boolean hasNext, SkipPolicy.Mode mode, long nowMs) {
        if (mode == SkipPolicy.Mode.OFF) {
            autoSegment = null;
            return hidden();
        }
        if (undoUntilMs > nowMs) {
            return model(State.UNDO_AVAILABLE, Action.NONE, currentSegment,
                    undoPositionMs, undoUntilMs - nowMs, UNDO_MS);
        }
        if (autoSegment != null) {
            if (!autoSegment.contains(positionMs) || dismissed.contains(autoSegment.key())) {
                autoSegment = null;
            } else if (nowMs >= autoDeadlineMs) {
                SkipSegment fired = autoSegment;
                autoSegment = null;
                return beginSkip(fired, positionMs, durationMs, hasNext, nowMs);
            } else {
                return model(State.AUTO_PENDING, Action.NONE, autoSegment, autoSegment.endMs,
                        autoDeadlineMs - nowMs, AUTO_COUNTDOWN_MS);
            }
        }
        SkipSegment upcoming = null;
        for (SkipSegment segment : segments) {
            if (completed.contains(segment.key()) || dismissed.contains(segment.key())) continue;
            if (segment.contains(positionMs)) {
                if (mode == SkipPolicy.Mode.BRIEF_BUTTON
                        && positionMs >= segment.startMs + BRIEF_MS) return hidden();
                if (mode == SkipPolicy.Mode.AUTO) {
                    autoSegment = segment;
                    autoDeadlineMs = nowMs + AUTO_COUNTDOWN_MS;
                    return model(State.AUTO_PENDING, Action.NONE, segment, segment.endMs,
                            AUTO_COUNTDOWN_MS, AUTO_COUNTDOWN_MS);
                }
                currentSegment = segment;
                return model(State.AVAILABLE, Action.NONE, segment, segment.endMs,
                        segment.endMs - positionMs, segment.endMs - segment.startMs);
            }
            long startsIn = segment.startMs - positionMs;
            if (startsIn > 0 && startsIn <= PREVIEW_MS
                    && (upcoming == null || segment.startMs < upcoming.startMs)) upcoming = segment;
        }
        if (upcoming != null) {
            return model(State.COUNTDOWN, Action.NONE, upcoming, upcoming.endMs,
                    upcoming.startMs - positionMs, PREVIEW_MS);
        }
        return hidden();
    }

    public Model activate(Model visible, long positionMs, long durationMs,
                          boolean hasNext, long nowMs) {
        if (visible == null) return hidden();
        if (visible.state == State.AUTO_PENDING && visible.segment != null) {
            dismissed.add(visible.segment.key());
            autoSegment = null;
            return hidden();
        }
        if (visible.state == State.UNDO_AVAILABLE) {
            undoUntilMs = 0;
            return new Model(State.HIDDEN, Action.RESTORE_POSITION, currentSegment,
                    undoPositionMs, 0, 0);
        }
        if (visible.state == State.AVAILABLE && visible.segment != null) {
            return beginSkip(visible.segment, positionMs, durationMs, hasNext, nowMs);
        }
        return visible;
    }

    private Model beginSkip(SkipSegment segment, long positionMs, long durationMs,
                            boolean hasNext, long nowMs) {
        completed.add(segment.key());
        currentSegment = segment;
        undoPositionMs = positionMs;
        undoUntilMs = nowMs + UNDO_MS;
        boolean reachesEnd = durationMs > 0 && segment.endMs >= durationMs - 1500;
        Action action = reachesEnd && hasNext ? Action.PLAY_NEXT : Action.SEEK_TO_END;
        return new Model(State.HIDDEN, action, segment, segment.endMs, 0, 0);
    }

    private static Model model(State state, Action action, SkipSegment segment, long target,
                               long remaining, long total) {
        int progress = total <= 0 ? 0 : (int) Math.max(0,
                Math.min(1000, remaining * 1000 / total));
        long seconds = Math.max(1, (remaining + 999) / 1000);
        return new Model(state, action, segment, target, progress, seconds);
    }

    private static Model hidden() {
        return new Model(State.HIDDEN, Action.NONE, null, 0, 0, 0);
    }
}
