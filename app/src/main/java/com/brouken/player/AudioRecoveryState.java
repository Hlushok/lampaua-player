package com.brouken.player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class AudioRecoveryState {
    private final LinkedHashSet<String> blockedMimes = new LinkedHashSet<>();
    private boolean paused;
    private boolean rebuildPending;
    private boolean audioEverStarted;

    void onPause() {
        paused = true;
    }

    void onResume() {
        if (paused) rebuildPending = true;
        paused = false;
    }

    /**
     * Marks a real transition into playing. The first start owns a fresh AudioTrack and must never
     * spend a seek/route latch that was raised while the item was still opening.
     */
    boolean onPlaybackStarted() {
        if (!audioEverStarted) {
            audioEverStarted = true;
            paused = false;
            rebuildPending = false;
            return false;
        }
        onResume();
        return rebuildPending;
    }

    void onLongRebuffer() {
        rebuildPending = true;
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
        resetForNewPlayback();
    }

    void resetForNewPlayback() {
        rebuildPending = false;
        paused = false;
        audioEverStarted = false;
    }
}
