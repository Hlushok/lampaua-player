package com.brouken.player;

/** Pure enablement/visibility rules for the four-row subtitle settings hierarchy. */
final class SubtitleSettingsPolicy {
    private SubtitleSettingsPolicy() { }

    static boolean primaryLanguagesEnabled(String searchMode) {
        // This list also controls embedded-track preference, so it is useful without online search.
        return true;
    }

    static boolean secondaryLanguagesEnabled(String secondaryMode) {
        return !Prefs.SECONDARY_OFF.equals(secondaryMode);
    }

    static boolean secondarySearchEnabled(String secondaryMode, String searchMode) {
        return secondaryLanguagesEnabled(secondaryMode)
                && !Prefs.SEARCH_OFF.equals(searchMode);
    }

    static boolean translationEnabled(String searchMode) {
        return !Prefs.SEARCH_OFF.equals(searchMode);
    }

    static boolean translationBackendsVisible(boolean debugBuild, boolean translationOn) {
        // Turning translation off disables this diagnostic row; it does not make it jump away.
        return debugBuild;
    }

    static String fixedTranslationTarget() {
        return "ukr";
    }
}
