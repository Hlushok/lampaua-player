package com.brouken.player;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import org.junit.Test;
import static org.junit.Assert.*;

public class TunnelingAudioRecoveryTest {
    @Test public void decodedPcmFailureRetriesOnlyOnceIncludingAfterASeek() {
        final TunnelingAudioRecovery recovery = new TunnelingAudioRecovery();
        assertTrue(recovery.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_RAW, true));
        assertTrue(recovery.isDisabled());
        assertFalse(recovery.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_RAW, true));
        // A rebuild and activity restoration may not refill the retry budget.
        final TunnelingAudioRecovery restored = new TunnelingAudioRecovery();
        restored.restore(recovery.isDisabled());
        assertFalse(restored.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_RAW, true));
        restored.reset();
        assertTrue(restored.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_RAW, true));
    }

    @Test public void otherFailuresAndNonTunneledAudioDoNotDisableAnything() {
        final TunnelingAudioRecovery recovery = new TunnelingAudioRecovery();
        assertFalse(recovery.recover(PlaybackException.ERROR_CODE_TIMEOUT, MimeTypes.AUDIO_RAW, true));
        assertFalse(recovery.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, MimeTypes.AUDIO_RAW, true));
        assertFalse(recovery.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, MimeTypes.AUDIO_E_AC3, true));
        assertFalse(recovery.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, null, true));
        assertFalse(recovery.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, MimeTypes.AUDIO_RAW, false));
        assertFalse(recovery.isDisabled());
    }

    @Test public void sameItemRebuildKeepsItsGuardButANewLinkOrEpisodeDoesNot() {
        final TunnelingAudioRecovery recovery = new TunnelingAudioRecovery();
        assertTrue(recovery.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                MimeTypes.AUDIO_RAW, true));
        recovery.recordMedia("https://media/episode1.mkv");
        final TunnelingAudioRecovery restored = new TunnelingAudioRecovery();
        restored.restore(recovery.isDisabled());
        restored.recordMedia(recovery.mediaKey());
        assertFalse(restored.moveTo("https://media/episode1.mkv", false));
        assertTrue(restored.moveTo("https://media/episode2.mkv", false));
        assertFalse(restored.isDisabled());
        restored.recover(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, MimeTypes.AUDIO_RAW, true);
        restored.recordMedia("https://media/episode2.mkv");
        assertTrue(restored.moveTo("https://media/episode2.mkv", true));
        assertNull(restored.mediaKey());
    }
}
