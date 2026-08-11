package com.brouken.player;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

final class BoostAudioProcessor extends BaseAudioProcessor {
    private volatile float gain = 1f;

    void setGain(float gain) {
        this.gain = Math.max(1f, Math.min(2f, gain));
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        return inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
                ? inputAudioFormat : AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) return;
        float currentGain = gain;
        ByteBuffer outputBuffer = replaceOutputBuffer(inputBuffer.remaining());
        if (currentGain == 1f) {
            outputBuffer.put(inputBuffer);
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.hasRemaining()) {
                int sample = Math.round(inputBuffer.getShort() * currentGain);
                outputBuffer.putShort((short) Math.max(Short.MIN_VALUE,
                        Math.min(Short.MAX_VALUE, sample)));
            }
        } else {
            while (inputBuffer.hasRemaining()) {
                outputBuffer.putFloat(Math.max(-1f,
                        Math.min(1f, inputBuffer.getFloat() * currentGain)));
            }
        }
        outputBuffer.flip();
    }
}
