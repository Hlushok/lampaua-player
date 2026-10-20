package com.brouken.player;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioMixingUtil;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;

import java.nio.ByteBuffer;

/** Changes channel count only, preserving the sample rate and tunneled audio timestamps. */
final class TunnelingStereoProcessor extends BaseAudioProcessor {
    private final boolean stereoRoute;
    private volatile boolean tunneling;
    private ChannelMixingMatrix matrix;
    private ChannelMixingMatrix pendingMatrix;

    TunnelingStereoProcessor(boolean stereoRoute) {
        this.stereoRoute = stereoRoute;
    }

    void setTunneling(boolean enabled) {
        tunneling = enabled;
    }

    static int routeChannels(Iterable<Integer> speakerMasks) {
        int channels = 0;
        for (int mask : speakerMasks) channels = Math.max(channels, Integer.bitCount(mask));
        return channels;
    }

    @Override protected AudioFormat onConfigure(AudioFormat input) {
        pendingMatrix = null;
        if (!stereoRoute || !tunneling || input.channelCount < 3 || input.channelCount > 8
                || (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT)) {
            return AudioFormat.NOT_SET;
        }
        pendingMatrix = stereoMatrix(input.channelCount);
        Utils.log("audio downmix: " + input.channelCount + " -> 2 channels for tunneling");
        return new AudioFormat(input.sampleRate, 2, input.encoding);
    }

    private static ChannelMixingMatrix stereoMatrix(int channels) {
        // Android PCM order: FL, FR, FC, LFE, then rear/side speakers in mask order.
        // Quad has no FC/LFE; five channels have no LFE. Preserve dialogue in both outputs.
        final float[] weights = new float[channels * 2];
        weights[0] = 1f;
        weights[3] = 1f;
        if (channels != 4) pair(weights, 2, 0.70710677f, 0.70710677f);
        if (channels == 4) {
            pair(weights, 2, 0.70710677f, 0f);
            pair(weights, 3, 0f, 0.70710677f);
        } else if (channels == 5) {
            pair(weights, 3, 0.70710677f, 0f);
            pair(weights, 4, 0f, 0.70710677f);
        } else if (channels >= 6) {
            pair(weights, 3, 0.5f, 0.5f);
            if (channels == 7) {
                pair(weights, 4, 0.5f, 0.5f); // Back centre precedes the side speakers.
                pair(weights, 5, 0.70710677f, 0f);
                pair(weights, 6, 0f, 0.70710677f);
            } else {
                pair(weights, 4, 0.70710677f, 0f);
                pair(weights, 5, 0f, 0.70710677f);
                if (channels == 8) {
                    pair(weights, 6, 0.70710677f, 0f);
                    pair(weights, 7, 0f, 0.70710677f);
                }
            }
        }
        return new ChannelMixingMatrix(channels, 2, weights);
    }

    private static void pair(float[] weights, int channel, float left, float right) {
        weights[channel * 2] = left;
        weights[channel * 2 + 1] = right;
    }

    @Override public void queueInput(ByteBuffer input) {
        final int frames = input.remaining() / inputAudioFormat.bytesPerFrame;
        final ByteBuffer output = replaceOutputBuffer(frames * outputAudioFormat.bytesPerFrame);
        AudioMixingUtil.mix(input, inputAudioFormat, output, outputAudioFormat, matrix,
                frames, /* accumulate= */ false, /* clipFloatOutput= */ true);
        output.flip();
    }

    @Override protected void onFlush() {
        // configure() may run while the old track still has PCM to drain.
        matrix = pendingMatrix;
    }

    @Override protected void onReset() {
        matrix = null;
        pendingMatrix = null;
        // Tunneling belongs to the sink's enable/disable calls, not processor buffer resets.
    }
}
