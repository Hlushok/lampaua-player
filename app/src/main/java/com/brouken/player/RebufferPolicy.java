package com.brouken.player;

import androidx.media3.exoplayer.DefaultLoadControl;

/** Buffer targets that keep bursty network sources from repeatedly restarting too early. */
final class RebufferPolicy {
    private static final int STREAM_REBUFFER_MS = 15_000;
    private static final int OPTIMIZED_4K_MIN_BUFFER_MS = 20_000;
    private static final int OPTIMIZED_4K_MAX_BUFFER_MS = 90_000;
    private static final int OPTIMIZED_4K_START_MS = 5_000;
    private static final int OPTIMIZED_4K_REBUFFER_MS = 8_000;

    private RebufferPolicy() {}

    static Config forMedia(boolean streaming, boolean optimize4k) {
        if (optimize4k) {
            return optimized4k(streaming);
        }
        return streaming ? forStreaming(false) : forLocal();
    }

    static Config forStreaming(boolean optimize4k) {
        if (optimize4k) return optimized4k(true);
        return new Config(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                STREAM_REBUFFER_MS,
                false);
    }

    static Config forLocal() {
        return new Config(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                false);
    }

    private static Config optimized4k(boolean streaming) {
        return new Config(
                OPTIMIZED_4K_MIN_BUFFER_MS,
                OPTIMIZED_4K_MAX_BUFFER_MS,
                OPTIMIZED_4K_START_MS,
                streaming ? STREAM_REBUFFER_MS : OPTIMIZED_4K_REBUFFER_MS,
                true);
    }

    static final class Config {
        final int minBufferMs;
        final int maxBufferMs;
        final int bufferForPlaybackMs;
        final int bufferForPlaybackAfterRebufferMs;
        final boolean prioritizeTimeOverSizeThresholds;

        Config(int minBufferMs, int maxBufferMs, int bufferForPlaybackMs,
               int bufferForPlaybackAfterRebufferMs,
               boolean prioritizeTimeOverSizeThresholds) {
            this.minBufferMs = minBufferMs;
            this.maxBufferMs = maxBufferMs;
            this.bufferForPlaybackMs = bufferForPlaybackMs;
            this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
            this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
        }

        DefaultLoadControl build() {
            return new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(minBufferMs, maxBufferMs,
                            bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
                    .setPrioritizeTimeOverSizeThresholds(prioritizeTimeOverSizeThresholds)
                    .build();
        }
    }
}
