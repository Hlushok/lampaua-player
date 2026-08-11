package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioRecoveryStateTest {

    @Test
    public void pauseOnlyArmsRebuildWhenPlaybackResumes() {
        AudioRecoveryState state = new AudioRecoveryState();
        state.onPause();
        assertFalse(state.shouldRebuildSink());

        state.onResume();
        assertTrue(state.consumeRebuildRequest());
        assertFalse(state.consumeRebuildRequest());
    }

    @Test
    public void seekAndOutputChangesCoalesce() {
        AudioRecoveryState state = new AudioRecoveryState();
        state.onSeek();
        state.onSeek();
        state.onAudioOutputChanged();

        assertTrue(state.consumeRebuildRequest());
        assertFalse(state.consumeRebuildRequest());
    }

    @Test
    public void writeFailureBlocksOnlyFailingMimeForSession() {
        AudioRecoveryState state = new AudioRecoveryState();
        state.onWriteFailure("audio/eac3");

        assertTrue(state.blockedPassthroughMimeTypes().contains("audio/eac3"));
        assertFalse(state.blockedPassthroughMimeTypes().contains("audio/ac3"));
        assertTrue(state.consumeRebuildRequest());
    }
}
