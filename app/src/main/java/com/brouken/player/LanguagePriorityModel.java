package com.brouken.player;

import java.util.List;

/** Pure language preference normalization shared by settings and subtitle search. */
final class LanguagePriorityModel {
    private LanguagePriorityModel() {}

    static List<String> normalize(List<String> languages) {
        return AudioLanguagePriority.parse(AudioLanguagePriority.serialize(languages));
    }
}
