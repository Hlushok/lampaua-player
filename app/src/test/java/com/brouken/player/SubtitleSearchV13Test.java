package com.brouken.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SubtitleSearchV13Test {

    @Test
    public void identifiesForcedTrackNamesWithoutRejectingNormalReleases() {
        assertTrue(SubtitleSearch.forcedName("Movie.2026.forced.srt"));
        assertTrue(SubtitleSearch.forcedName("castellano.forzado.srt"));
        assertFalse(SubtitleSearch.forcedName("signs_and_songs.srt"));
        assertFalse(SubtitleSearch.forcedName("Movie.2026.WEB-DL.uk.srt"));
    }
}
