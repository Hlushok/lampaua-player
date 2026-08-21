package com.brouken.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

public class UkrainianSubtitleTranslatorTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void retriesOneTransientFailureThenCreatesCompleteTarget() throws Exception {
        File source = write("source.srt", simpleSource(2));
        File target = new File(temporary.getRoot(), "translated.srt");
        File stale = write("translated.srt.tmp", "stale");
        AtomicInteger calls = new AtomicInteger();
        SubtitleTranslationTransport transport = (from, to, payload) -> {
            assertFalse(target.exists());
            assertFalse(stale.exists());
            if (calls.getAndIncrement() == 0) throw new IOException("temporary");
            return payload.replace("Cue", "Репліка");
        };

        assertTrue(UkrainianSubtitleTranslator.translate(
                source, target, "eng", transport));
        assertTrue(target.isFile());
        assertFalse(stale.exists());
        assertTrue(read(target).contains("Репліка 1"));
        assertTrue(read(target).contains("Репліка 2"));
        assertTrue(calls.get() >= 2);
    }

    @Test
    public void recursivelySplitsOnlyMarkerDamagedBatches() throws Exception {
        File source = write("source.srt", simpleSource(3));
        File target = new File(temporary.getRoot(), "translated.srt");
        AtomicInteger calls = new AtomicInteger();
        SubtitleTranslationTransport transport = (from, to, payload) -> {
            calls.incrementAndGet();
            if (payload.contains("[[[UA_PLAYER_")) {
                return payload.replace("[[[UA_PLAYER_", "[[[BROKEN_");
            }
            return "Переклад " + payload;
        };

        assertTrue(UkrainianSubtitleTranslator.translate(
                source, target, "eng", transport));
        assertTrue(read(target).contains("Переклад Cue 3"));
        assertTrue(calls.get() > 3);
    }

    @Test
    public void irrecoverableCueLeavesNoTargetOrTemporaryFile() throws Exception {
        File source = write("source.srt", simpleSource(1));
        File target = new File(temporary.getRoot(), "translated.srt");
        SubtitleTranslationTransport transport = (from, to, payload) -> "";

        assertFalse(UkrainianSubtitleTranslator.translate(
                source, target, "eng", transport));
        assertFalse(target.exists());
        assertFalse(new File(temporary.getRoot(), "translated.srt.tmp").exists());
    }

    @Test
    public void rejectsUnsupportedSourceWithoutCallingTransport() throws Exception {
        File source = write("source.srt", simpleSource(1));
        File target = new File(temporary.getRoot(), "translated.srt");
        SubtitleTranslationTransport transport = (from, to, payload) -> {
            throw new AssertionError("Transport must not be called");
        };

        assertFalse(UkrainianSubtitleTranslator.translate(
                source, target, "../eng", transport));
        assertFalse(target.exists());
    }

    private File write(String name, String value) throws IOException {
        File file = new File(temporary.getRoot(), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String simpleSource(int count) {
        StringBuilder source = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            source.append(index)
                    .append("\n00:00:00,000 --> 00:00:01,000\nCue ")
                    .append(index);
            if (index < count) source.append("\n\n");
        }
        return source.toString();
    }
}
