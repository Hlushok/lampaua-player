package com.brouken.player;

import androidx.media3.exoplayer.DefaultLoadControl;

/** Buffer targets that keep bursty network sources from repeatedly restarting too early. */
final class RebufferPolicy {
    private static final int STREAM_MIN_BUFFER_MS = 15_000;
    private static final int STREAM_MAX_BUFFER_MS = 50_000;
    private static final int STREAM_START_MS = 500;
    private static final int STREAM_REBUFFER_MS = 5_000;

    private RebufferPolicy() {}

    static Config forMedia(boolean streaming, boolean optimize4k) {
        return streaming ? forStreaming(optimize4k) : forLocal();
    }

    static Config forStreaming(boolean optimize4k) {
        return new Config(
                STREAM_MIN_BUFFER_MS,
                STREAM_MAX_BUFFER_MS,
                STREAM_START_MS,
                STREAM_REBUFFER_MS,
                true);
    }

    static Config forLocal() {
        return new Config(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                false);
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
