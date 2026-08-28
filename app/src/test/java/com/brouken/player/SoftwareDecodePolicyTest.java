package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;

import org.junit.Test;

public class SoftwareDecodePolicyTest {
    @Test
    public void knownPlatformAndBundledSoftwareNamesAreRecognized() {
        assertTrue(SoftwareDecodePolicy.isSoftwareName("c2.android.avc.decoder"));
        assertTrue(SoftwareDecodePolicy.isSoftwareName("OMX.google.h264.decoder"));
        assertTrue(SoftwareDecodePolicy.isSoftwareName("libdav1d"));
        assertTrue(SoftwareDecodePolicy.isSoftwareName("ffmpeg-video"));

        assertFalse(SoftwareDecodePolicy.isSoftwareName("OMX.amlogic.avc.decoder.awesome2"));
        assertFalse(SoftwareDecodePolicy.isSoftwareName("c2.qti.avc.decoder"));
        assertFalse(SoftwareDecodePolicy.isSoftwareName(null));
    }

    @Test
    public void healthyOrNetworkStarvedPlaybackIsNeverBlamedOnSoftwareDecode() {
        assertFalse(SoftwareDecodePolicy.evaluate(evidence(true, false,
                10_000, 10_000, 0, 8_000_000, qualities())).slow);
        assertFalse(SoftwareDecodePolicy.evaluate(evidence(true, true,
                10_000, 500, 1_000_000, 20_000_000, qualities())).slow);
        assertFalse(SoftwareDecodePolicy.evaluate(evidence(true, true,
                4_999, 10_000, 0, 8_000_000, qualities())).slow);
    }

    @Test
    public void hardwareAnd1080pDecodersAreExcluded() {
        SoftwareDecodePolicy.Evidence hardware = evidence(false, true,
                10_000, 10_000, 0, 8_000_000, qualities());
        assertFalse(SoftwareDecodePolicy.evaluate(hardware).slow);

        SoftwareDecodePolicy.Evidence fullHd = new SoftwareDecodePolicy.Evidence(
                true, 1920, 1080, 30f, true, true, 10_000,
                10_000, 0, 8_000_000, 0, qualities(), "four-k");
        assertFalse(SoftwareDecodePolicy.evaluate(fullHd).slow);
    }

    @Test
    public void stalled4kSoftwareDecodeChoosesHighestSourceAtOrBelow1080p() {
        SoftwareDecodePolicy.Decision decision = SoftwareDecodePolicy.evaluate(
                evidence(true, true, 10_000, 10_000, 0, 8_000_000, qualities()));

        assertTrue(decision.software);
        assertTrue(decision.slow);
        assertEquals("software_decode_too_slow", decision.reason);
        assertEquals("1080p", decision.fallback.label);
        assertEquals("full-hd", decision.fallback.url);
    }

    @Test
    public void slowDecisionStillExplainsTheCauseWithoutASeparateVariant() {
        SoftwareDecodePolicy.Decision decision = SoftwareDecodePolicy.evaluate(
                evidence(true, true, 10_000, 10_000, 0, 8_000_000,
                        new LinkedHashMap<>()));

        assertTrue(decision.slow);
        assertNull(decision.fallback);
    }

    @Test
    public void decoderInitFailureCanChooseTheSameSafeFallback() {
        SoftwareDecodePolicy.Decision decision = SoftwareDecodePolicy.forDecoderInit(
                true, 3840, 2160, qualities(), "four-k");

        assertTrue(decision.slow);
        assertEquals("full-hd", decision.fallback.url);
        assertFalse(SoftwareDecodePolicy.forDecoderInit(
                false, 3840, 2160, qualities(), "four-k").slow);
    }

    private static SoftwareDecodePolicy.Evidence evidence(
            boolean software, boolean stalled, long activeMs, long bufferedMs,
            long transferBitrate, long videoBitrate, LinkedHashMap<String, String> variants) {
        return new SoftwareDecodePolicy.Evidence(
                software, 3840, 2160, 30f, true, stalled, activeMs,
                bufferedMs, transferBitrate, videoBitrate, 0, variants, "four-k");
    }

    private static LinkedHashMap<String, String> qualities() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("4K", "four-k");
        values.put("1440p", "quad-hd");
        values.put("1080p", "full-hd");
        values.put("720p", "hd");
        return values;
    }
}
