package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticReportTest {

    @Test
    public void networkUrisLoseCredentialsQueryAndFragment() {
        String text = "Open https://user:pass@media.example.test:8443/live/movie.m3u8"
                + "?token=secret#episode now";

        String sanitized = DiagnosticReport.sanitizeText(text);

        assertEquals("Open https://media.example.test:8443/[redacted] now", sanitized);
        assertFalse(sanitized.contains("user"));
        assertFalse(sanitized.contains("pass"));
        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("movie.m3u8"));
    }

    @Test
    public void multipleNetworkUrisAreSanitizedIndependently() {
        String text = "https://one.test/a?token=first then "
                + "http://name:password@two.test/b#second";

        assertEquals("https://one.test/[redacted] then http://two.test/[redacted]",
                DiagnosticReport.sanitizeText(text));
    }

    @Test
    public void localUrisOnlyExposeTheirScheme() {
        assertEquals("content (local)", DiagnosticReport.sanitizeNetworkUri(
                "content://com.example.provider/private/movie.mkv"));
        assertEquals("file (local)", DiagnosticReport.sanitizeNetworkUri(
                "file:///storage/emulated/0/Private/movie.mkv"));
    }

    @Test
    public void ordinaryTextIsPreservedButNamedSecretsAreRedacted() {
        assertEquals("Decoder failed\nAuthorization: [redacted]\nApi-Key=[redacted]",
                DiagnosticReport.sanitizeText(
                        "Decoder failed\nAuthorization: Bearer abc123\nApi-Key=xyz789"));
    }

    @Test
    public void deepestNonEmptyCauseMessageWins() {
        Throwable deepest = new IllegalStateException("socket closed");
        Throwable middle = new RuntimeException("", deepest);
        Throwable outer = new RuntimeException("playback failed", middle);

        assertEquals("socket closed", DiagnosticReport.rootMessage(outer));
    }

    @Test
    public void stackTraceIsSanitized() {
        Throwable error = new IllegalStateException(
                "https://user:pass@media.test/file.m3u8?token=abc#part");

        String trace = DiagnosticReport.stackTrace(error);

        assertTrue(trace.contains("https://media.test/[redacted]"));
        assertFalse(trace.contains("file.m3u8"));
        assertFalse(trace.contains("user:pass"));
        assertFalse(trace.contains("token=abc"));
    }

    @Test
    public void signedPathTokensAreNeverExposed() {
        String signed = "https://cdn.test/s/FH_1IhZTTYDW0KHDRPCy6IJ0F/token/title.mp4";

        assertEquals("https://cdn.test/[redacted]",
                DiagnosticReport.sanitizeNetworkUri(signed));
    }

    @Test
    public void traceSanitizerDropsNetworkQueriesAndFragments() {
        assertEquals("load https://media.test/live/movie.m3u8 failed",
                Utils.stripUrlQuery(
                        "load https://media.test/live/movie.m3u8?token=secret#part failed"));
    }
}
