package com.brouken.player;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;

import java.util.Arrays;
import java.util.List;

/** Chooses when an AudioTrack failure has the one user-actionable surround-sound workaround. */
final class AudioTrackFailureAdvisor {

    private static final List<String> PASSTHROUGH_SOURCE_MIMES = Arrays.asList(
            MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS,
            MimeTypes.AUDIO_TRUEHD);

    private AudioTrackFailureAdvisor() {
    }

    static boolean shouldSuggestPassthrough(int errorCode, String sourceMime, String sinkMime,
                                            int channelCount, boolean isTvBox,
                                            boolean passthroughEnabled) {
        return isTvBox
                && !passthroughEnabled
                && errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                && PASSTHROUGH_SOURCE_MIMES.contains(sourceMime)
                && MimeTypes.AUDIO_RAW.equals(sinkMime)
                && channelCount > 2;
    }
}
