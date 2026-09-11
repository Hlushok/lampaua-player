package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioRecoveryStateTest {

    @Test
    public void pauseOnlyArmsRebuildWhenPlaybackResumes() {
        AudioRecoveryState state = new AudioRecoveryState();
        assertFalse(state.onPlaybackStarted());
        state.onPause();
        assertFalse(state.shouldRebuildSink());

        assertTrue(state.onPlaybackStarted());
        assertTrue(state.consumeRebuildRequest());
        assertFalse(state.consumeRebuildRequest());
    }

    @Test
    public void firstStartDoesNotSpendASeekLatchOnTheFreshOutput() {
        AudioRecoveryState state = new AudioRecoveryState();
        state.onSeek();

        assertFalse(state.onPlaybackStarted());
        assertFalse(state.consumeRebuildRequest());
    }

    @Test
    public void aLongRebufferRebuildsOnlyAfterPlaybackHasStartedOnce() {
        AudioRecoveryState state = new AudioRecoveryState();
        assertFalse(state.onPlaybackStarted());

        state.onLongRebuffer();
        assertTrue(state.onPlaybackStarted());
        assertTrue(state.consumeRebuildRequest());

        state.resetForNewPlayback();
        state.onLongRebuffer();
        assertFalse(state.onPlaybackStarted());
        assertFalse(state.consumeRebuildRequest());
    }

    @Test
    public void seekAndOutputChangesCoalesce() {
        AudioRecoveryState state = new AudioRecoveryState();
        assertFalse(state.onPlaybackStarted());
        state.onSeek();
        state.onSeek();
        state.onAudioOutputChanged();

        assertTrue(state.onPlaybackStarted());
        assertTrue(state.consumeRebuildRequest());
        assertFalse(state.consumeRebuildRequest());
    }

    @Test
    public void shortRebufferDoesNotRebuildTheOutput() {
        AudioRecoveryState state = new AudioRecoveryState();
        assertFalse(state.onPlaybackStarted());

        assertFalse(state.onPlaybackStarted());
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
