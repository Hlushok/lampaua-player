package com.brouken.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Decides which preferred subtitle languages are worth looking up online. */
final class SubtitleLanguagePolicy {
    private SubtitleLanguagePolicy() { }

    static List<String> missing(List<String> preferred, Set<String> present, boolean strict) {
        int bestPresent = preferred.size();
        for (int i = 0; i < preferred.size(); i++) {
            if (present.contains(preferred.get(i))) {
                bestPresent = i;
                break;
            }
        }
        if (bestPresent == 0 || (strict && bestPresent < preferred.size())) {
            return Collections.emptyList();
        }
        return new ArrayList<>(preferred.subList(0, bestPresent));
    }
}
