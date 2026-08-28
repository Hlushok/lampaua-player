package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubtitleSettingsPolicyTest {

    @Test
    public void primaryLanguagePriorityAlwaysRemainsAvailable() {
        assertTrue(SubtitleSettingsPolicy.primaryLanguagesEnabled(Prefs.SEARCH_OFF));
        assertTrue(SubtitleSettingsPolicy.primaryLanguagesEnabled(Prefs.SEARCH_FIRST));
        assertTrue(SubtitleSettingsPolicy.primaryLanguagesEnabled(Prefs.SEARCH_NONE));
    }

    @Test
    public void secondLineControlsFollowItsModeAndOnlineSearch() {
        assertFalse(SubtitleSettingsPolicy.secondaryLanguagesEnabled(Prefs.SECONDARY_OFF));
        assertTrue(SubtitleSettingsPolicy.secondaryLanguagesEnabled(Prefs.SECONDARY_ALWAYS));
        assertTrue(SubtitleSettingsPolicy.secondaryLanguagesEnabled(Prefs.SECONDARY_DEMAND));
        assertFalse(SubtitleSettingsPolicy.secondarySearchEnabled(
                Prefs.SECONDARY_OFF, Prefs.SEARCH_FIRST));
        assertFalse(SubtitleSettingsPolicy.secondarySearchEnabled(
                Prefs.SECONDARY_ALWAYS, Prefs.SEARCH_OFF));
        assertTrue(SubtitleSettingsPolicy.secondarySearchEnabled(
                Prefs.SECONDARY_ALWAYS, Prefs.SEARCH_FIRST));
    }

    @Test
    public void searchAndTranslationRowsStayDiscoverableButRespectMode() {
        assertFalse(SubtitleSettingsPolicy.translationEnabled(Prefs.SEARCH_OFF));
        assertTrue(SubtitleSettingsPolicy.translationEnabled(Prefs.SEARCH_FIRST));
        assertTrue(SubtitleSettingsPolicy.translationEnabled(Prefs.SEARCH_NONE));
        assertFalse(SubtitleSettingsPolicy.translationBackendsVisible(false, true));
        assertTrue(SubtitleSettingsPolicy.translationBackendsVisible(true, true));
        assertTrue(SubtitleSettingsPolicy.translationBackendsVisible(true, false));
    }

    @Test
    public void translationTargetIsAlwaysUkrainian() {
        assertEquals("ukr", SubtitleSettingsPolicy.fixedTranslationTarget());
    }
}
