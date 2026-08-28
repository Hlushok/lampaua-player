package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class OpenSubtitlesTest {

    @Test
    public void preferredLanguageBeatsDownloadCount() {
        List<OpenSubtitles.Candidate> candidates = Arrays.asList(
                new OpenSubtitles.Candidate("en", 1, 50_000, "popular"),
                new OpenSubtitles.Candidate("uk", 2, 10, "wanted"),
                new OpenSubtitles.Candidate("uk", 3, 20, "better wanted"));

        assertEquals(3, OpenSubtitles.pick(candidates,
                Arrays.asList("uk", "en")).fileId);
    }

    @Test
    public void humanTranslationBeatsMorePopularMachineTranslation() {
        List<OpenSubtitles.Candidate> candidates = Arrays.asList(
                new OpenSubtitles.Candidate("uk", 1, 50_000, "machine", true),
                new OpenSubtitles.Candidate("uk", 2, 10, "human", false));

        assertEquals(2, OpenSubtitles.pick(candidates,
                Arrays.asList("uk"), true).fileId);
        assertEquals(2, OpenSubtitles.pick(candidates,
                Arrays.asList("uk"), false).fileId);
    }

    @Test
    public void convertsStoredIsoThreeCodesForTheApi() {
        assertEquals(Arrays.asList("uk", "de", "en"),
                OpenSubtitles.toIso639_1(Arrays.asList("ukr", "deu", "eng")));
    }
}
