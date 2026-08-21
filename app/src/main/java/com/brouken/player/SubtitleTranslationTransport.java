package com.brouken.player;

import java.io.IOException;

/** Isolates the unofficial translation endpoint from subtitle policy and storage. */
interface SubtitleTranslationTransport {
    String translate(String sourceIso2, String targetIso2, String payload) throws IOException;
}
