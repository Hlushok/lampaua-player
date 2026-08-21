package com.brouken.player;

import androidx.media3.common.PlaybackException;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamTypeFallbackPolicyTest {

    @Test
    public void directMp4IsNeverRetriedAsHls() {
        assertFalse(StreamTypeFallbackPolicy.canTryHls(false,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                null, "https://media.example.test/signed/title.mp4"));
    }

    @Test
    public void playbackThatWasReadyKeepsItsStreamType() {
        assertFalse(StreamTypeFallbackPolicy.canTryHls(true,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                null, "https://media.example.test/stream"));
        assertFalse(StreamTypeFallbackPolicy.canTryAlternate(true,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                "https://media.example.test/live.m3u8"));
    }

    @Test
    public void transientNetworkReadsDoNotChangeStreamType() {
        assertFalse(StreamTypeFallbackPolicy.canTryHls(false,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                null, "https://media.example.test/stream"));
    }

    @Test
    public void ambiguousStartupParsingFailureCanTryHls() {
        assertTrue(StreamTypeFallbackPolicy.canTryHls(false,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                null, "https://media.example.test/stream"));
        assertTrue(StreamTypeFallbackPolicy.canTryHls(false,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                null, "http://localhost:8090/playlist/title.m3u"));
    }

    @Test
    public void anExplicitMimeTypeDoesNotNeedHlsFallback() {
        assertFalse(StreamTypeFallbackPolicy.canTryHls(false,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                "video/mp4", "https://media.example.test/stream"));
    }
}
