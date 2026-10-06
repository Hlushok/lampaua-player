package com.brouken.player;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class PlaylistTrackRulesTest {
    static PlaylistTrackRules.Track track(int index, String language, String label, boolean supported, boolean selected, boolean external) {
        return new PlaylistTrackRules.Track(index, language, label, supported, selected, external, false, false);
    }
    @Test public void languagesNormalizeWithoutTreatingNamesAsCallerCodes() {
        assertEquals("ukr", PlaylistTrackRules.language("uk-UA")); assertEquals("deu", PlaylistTrackRules.language("ger"));
        assertNull(PlaylistTrackRules.language("und")); assertNull(PlaylistTrackRules.language("russian"));
        assertEquals("eng", PlaylistTrackRules.inferredLanguage("und", "English commentary"));
    }
    @Test public void aliasesKindsAndWholeWordsAvoidWrongDubs() {
        for (String label : Arrays.asList("MVO | LostFilm", "LostFilm [AC3]", "Lost Film")) assertTrue(PlaylistTrackRules.labelsMatch("LostFilm", label));
        assertTrue(PlaylistTrackRules.labelsMatch("Rezka", "HDrezka Studio"));
        assertTrue(PlaylistTrackRules.labelsMatch("VoiceProject", "Voice Project"));
        assertTrue(PlaylistTrackRules.labelsMatch("Dub", "Дубляж"));
        assertFalse(PlaylistTrackRules.labelsMatch("Studio", "NewStudio"));
        assertFalse(PlaylistTrackRules.labelsMatch("English", "English forced"));
        assertFalse(PlaylistTrackRules.labelsMatch("Rezka", "HDrezka Studio 18+"));
        assertFalse(PlaylistTrackRules.labelsMatch("Dub", "Dub commentary"));
    }
    @Test public void viewerOutranksItemAndUnsupportedIndicesFallBackToLabels() {
        List<PlaylistTrackRules.Track> tracks = Arrays.asList(track(0, "uk", "Dub", true, true, false), track(1, "en", "Original", false, false, false), track(2, "uk", "Studio", true, false, false));
        PlaylistApi.Keys item = new PlaylistApi.Keys(), root = new PlaylistApi.Keys(), viewer = new PlaylistApi.Keys();
        item.index = 1; item.label = "Studio";
        PlaylistTrackRules.Pick pick = PlaylistTrackRules.choose(tracks, item, root, null, null, false);
        assertEquals(2, pick.track.index); assertEquals("label", pick.by);
        viewer.label = "Dub";
        assertEquals("viewer", PlaylistTrackRules.choose(tracks, item, root, viewer, null, false).by);
    }
    @Test public void selectedSubtitleBeatsRootOffButNotItemOff() {
        List<PlaylistTrackRules.Track> tracks = Collections.singletonList(track(0, "uk", "Full", true, false, true));
        PlaylistApi.Keys item = new PlaylistApi.Keys(), root = new PlaylistApi.Keys(); root.off = true; root.index = -1;
        assertEquals("selected", PlaylistTrackRules.choose(tracks, item, root, null, null, true).by);
        item.off = true; item.languages = Collections.emptyList();
        assertTrue(PlaylistTrackRules.choose(tracks, item, root, null, null, true).off);
        assertEquals("languages", PlaylistTrackRules.choose(tracks, item, root, null, null, true).by);
    }
    @Test public void ordinalCountsUnsupportedTracksAndCountGuardsIt() {
        List<PlaylistTrackRules.Track> tracks = Arrays.asList(track(0, "uk", "A", false, false, false), track(1, "uk", "B", true, true, false));
        PlaylistApi.Keys item = new PlaylistApi.Keys(); item.languages = Collections.singletonList("ukr"); item.ordinal = 1; item.count = 2;
        assertEquals("language_ordinal", PlaylistTrackRules.choose(tracks, item, new PlaylistApi.Keys(), null, null, false).by);
        item.count = 3;
        assertEquals("languages", PlaylistTrackRules.choose(tracks, item, new PlaylistApi.Keys(), null, null, false).by);
    }
    @Test public void fullSubtitleWinsOverForcedInSameLanguage() {
        List<PlaylistTrackRules.Track> tracks = Arrays.asList(new PlaylistTrackRules.Track(0, "uk", "Forced", true, true, false, false, true), track(1, "uk", "Full", true, false, false));
        PlaylistApi.Keys item = new PlaylistApi.Keys(); item.languages = Collections.singletonList("ukr");
        assertEquals(1, PlaylistTrackRules.choose(tracks, item, new PlaylistApi.Keys(), null, null, true).track.index);
    }
    @Test public void rememberedAudioLanguageIsKeptWhenStudioIsMissing() {
        List<PlaylistTrackRules.Track> tracks = Arrays.asList(track(0, "en", "Original", true, true, false), track(1, "uk", "Other", true, false, false));
        PlaylistApi.Keys memory = new PlaylistApi.Keys(); memory.label = "Missing studio"; memory.languages = Collections.singletonList("ukr");
        PlaylistTrackRules.Pick pick = PlaylistTrackRules.choose(tracks, new PlaylistApi.Keys(), new PlaylistApi.Keys(), null, memory, false);
        assertEquals(1, pick.track.index); assertEquals("remembered", pick.by);
    }
}
