package com.brouken.player;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioTrackFailureAdvisorTest {

    @Test
    public void suggestsPassthroughForMultichannelPcmInitFailureOnTv() {
        assertTrue(AudioTrackFailureAdvisor.shouldSuggestPassthrough(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_RAW,
                6,
                true,
                false));
    }

    @Test
    public void doesNotSuggestPassthroughOutsideTheConfirmedFailure() {
        assertFalse(AudioTrackFailureAdvisor.shouldSuggestPassthrough(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_RAW,
                2,
                true,
                false));
        assertFalse(AudioTrackFailureAdvisor.shouldSuggestPassthrough(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_RAW,
                6,
                false,
                false));
        assertFalse(AudioTrackFailureAdvisor.shouldSuggestPassthrough(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_RAW,
                6,
                true,
                true));
        assertFalse(AudioTrackFailureAdvisor.shouldSuggestPassthrough(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_RAW,
                6,
                true,
                false));
        assertFalse(AudioTrackFailureAdvisor.shouldSuggestPassthrough(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_E_AC3,
                6,
                true,
                false));
        assertFalse(AudioTrackFailureAdvisor.shouldSuggestPassthrough(
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_AAC,
                MimeTypes.AUDIO_RAW,
                6,
                true,
                false));
    }
}
