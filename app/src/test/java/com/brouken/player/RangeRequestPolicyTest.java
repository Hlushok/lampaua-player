package com.brouken.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class RangeRequestPolicyTest {
    @Test
    public void directHttpMediaGetsAnInitialWholeResourceRange() {
        assertTrue(RangeRequestPolicy.shouldSeed(
                "https://media.example/movie.mp4", "video/mp4", Collections.emptyMap()));
        assertTrue(RangeRequestPolicy.shouldSeed(
                "https://media.example/telegram-stream", null, Collections.emptyMap()));
    }

    @Test
    public void callerRangeAlwaysWinsRegardlessOfHeaderCase() {
        Map<String, String> headers = new HashMap<>();
        headers.put("rAnGe", "bytes=1000-1999");

        assertFalse(RangeRequestPolicy.shouldSeed(
                "https://media.example/movie.mp4", "video/mp4", headers));
    }

    @Test
    public void knownAdaptiveManifestsAreNotSeeded() {
        assertFalse(RangeRequestPolicy.shouldSeed(
                "https://media.example/live.m3u8?token=x", null, Collections.emptyMap()));
        assertFalse(RangeRequestPolicy.shouldSeed(
                "https://media.example/manifest", "application/dash+xml",
                Collections.emptyMap()));
        assertFalse(RangeRequestPolicy.shouldSeed(
                "https://media.example/channel.ism/manifest", null,
                Collections.emptyMap()));
    }

    @Test
    public void nonHttpResourcesAreUntouched() {
        assertFalse(RangeRequestPolicy.shouldSeed(
                "content://provider/movie.mkv", "video/x-matroska", Collections.emptyMap()));
        assertFalse(RangeRequestPolicy.shouldSeed(
                "file:///sdcard/movie.mkv", null, Collections.emptyMap()));
        assertFalse(RangeRequestPolicy.shouldSeed(
                "rtsp://camera/live", null, Collections.emptyMap()));
        assertFalse(RangeRequestPolicy.shouldSeed(
                "udp://239.0.0.1:1234", null, Collections.emptyMap()));
    }
}
