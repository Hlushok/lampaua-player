package com.brouken.player;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;

/** One reversible fallback per playlist item, never a change to the viewer's preference. */
final class TunnelingAudioRecovery {
    private boolean disabled;
    private String mediaKey;

    boolean recover(int errorCode, String sinkMime, boolean actuallyTunneling) {
        if (disabled || !actuallyTunneling
                || errorCode != PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                || !MimeTypes.AUDIO_RAW.equals(sinkMime)) return false;
        disabled = true;
        return true;
    }

    boolean isDisabled() { return disabled; }

    void restore(boolean value) { disabled = value; }

    String mediaKey() { return mediaKey; }

    void recordMedia(String key) { mediaKey = key; }

    boolean moveTo(String key, boolean explicitItemChange) {
        if (!disabled) return false;
        // A PLAYLIST_CHANGED can be either our recovery rebuild or a newly opened link.
        if (explicitItemChange || (mediaKey != null && !mediaKey.equals(key))) {
            reset();
            return true;
        }
        return false;
    }

    void reset() { disabled = false; mediaKey = null; }
}
