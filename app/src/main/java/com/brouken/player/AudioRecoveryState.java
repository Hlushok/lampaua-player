package com.brouken.player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class AudioRecoveryState {
    private final LinkedHashSet<String> blockedMimes = new LinkedHashSet<>();
    private boolean paused;
    private boolean rebuildPending;

    void onPause() {
        paused = true;
    }

    void onResume() {
        if (paused) rebuildPending = true;
        paused = false;
    }

    void onSeek() {
        rebuildPending = true;
    }

    void onAudioOutputChanged() {
        rebuildPending = true;
    }

    void onWriteFailure(String mime) {
        if (mime != null && !mime.trim().isEmpty()) blockedMimes.add(mime.trim());
        rebuildPending = true;
    }

    boolean shouldRebuildSink() {
        return rebuildPending;
    }

    boolean consumeRebuildRequest() {
        boolean result = rebuildPending;
        rebuildPending = false;
        return result;
    }

    Set<String> blockedPassthroughMimeTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(blockedMimes));
    }

    void clearRebuildRequest() {
        rebuildPending = false;
        paused = false;
    }
}
