package com.brouken.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class SubRipDocumentTest {
    private static final String FORMATTED =
            "1\r\n00:00:01,000 --> 00:00:03,000\r\n"
                    + "<i>Hello</i>\r\nworld\r\n\r\n"
                    + "2\r\n00:00:02,500 --> 00:00:04,000\r\n"
                    + "{\\an8}<b>Good bye</b>";

    @Test
    public void preservesStructureWhileReplacingOnlyCueText() {
        SubRipDocument doc = SubRipDocument.parse(
                FORMATTED.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, doc.cueCount());

        SubRipDocument.Batch batch = doc.batch(0, doc.cueCount());
        assertFalse(batch.payload.contains("00:00:01,000 --> 00:00:03,000"));
        assertFalse(batch.payload.contains("<i>"));
        assertFalse(batch.payload.contains("{\\an8}"));
        assertTrue(batch.payload.contains("[[[UA_PLAYER_000001]]]"));

        String translated = batch.payload
                .replace("Hello", "Привіт")
                .replace("world", "світ")
                .replace("Good bye", "До побачення");
        assertTrue(doc.acceptTranslation(batch, translated));

        String rendered = new String(doc.renderUtf8(), StandardCharsets.UTF_8);
        assertTrue(rendered.contains(
                "1\r\n00:00:01,000 --> 00:00:03,000\r\n<i>Привіт</i>\r\nсвіт"));
        assertTrue(rendered.contains(
                "2\r\n00:00:02,500 --> 00:00:04,000\r\n{\\an8}<b>До побачення</b>"));
        assertFalse(rendered.endsWith("\n"));
    }

    @Test
    public void validatesEveryBoundaryExactlyOnceAndInOrder() {
        SubRipDocument missing = simpleDocument(3);
        SubRipDocument.Batch missingBatch = missing.batch(0, 3);
        assertFalse(missing.acceptTranslation(missingBatch,
                missingBatch.payload.replace("[[[UA_PLAYER_000001]]]", "")));

        SubRipDocument duplicate = simpleDocument(3);
        SubRipDocument.Batch duplicateBatch = duplicate.batch(0, 3);
        assertFalse(duplicate.acceptTranslation(duplicateBatch,
                duplicateBatch.payload.replace("[[[UA_PLAYER_000001]]]",
                        "[[[UA_PLAYER_000001]]][[[UA_PLAYER_000001]]]")));

        SubRipDocument reordered = simpleDocument(3);
        SubRipDocument.Batch reorderedBatch = reordered.batch(0, 3);
        String swapped = reorderedBatch.payload
                .replace("[[[UA_PLAYER_000001]]]", "[[[UA_SWAP]]]")
                .replace("[[[UA_PLAYER_000002]]]", "[[[UA_PLAYER_000001]]]")
                .replace("[[[UA_SWAP]]]", "[[[UA_PLAYER_000002]]]");
        assertFalse(reordered.acceptTranslation(reorderedBatch, swapped));
    }

    @Test
    public void rejectsChangedFormattingPlaceholderAndForeignBatch() {
        SubRipDocument formatted = SubRipDocument.parse(
                FORMATTED.getBytes(StandardCharsets.UTF_8));
        SubRipDocument.Batch batch = formatted.batch(0, 1);
        assertFalse(formatted.acceptTranslation(batch,
                batch.payload.replace("UA_FMT", "UA_BROKEN")));
        assertArrayEquals(FORMATTED.getBytes(StandardCharsets.UTF_8), formatted.renderUtf8());

        SubRipDocument other = simpleDocument(1);
        assertFalse(formatted.acceptTranslation(other.batch(0, 1), "переклад"));
    }

    @Test
    public void rejectsMalformedBinaryAndOversizedInput() {
        rejects("1\nnot a timing line\nText".getBytes(StandardCharsets.UTF_8));
        rejects("1\n00:00:01,000 --> 00:00:02,000\nA\u0000B"
                .getBytes(StandardCharsets.UTF_8));
        rejects(new byte[2 * 1024 * 1024 + 1]);
    }

    @Test
    public void rejectsMoreThanTenThousandCues() {
        StringBuilder source = new StringBuilder();
        for (int index = 1; index <= 10_001; index++) {
            source.append(index)
                    .append("\n00:00:00,000 --> 00:00:01,000\nText\n\n");
        }
        rejects(source.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void batchRespectsCueAndUtf8Limits() {
        SubRipDocument many = simpleDocument(45);
        SubRipDocument.Batch cueBounded = many.batch(0, 45);
        assertEquals(0, cueBounded.fromCue);
        assertEquals(40, cueBounded.toCue);
        assertTrue(cueBounded.payload.getBytes(StandardCharsets.UTF_8).length <= 3_500);

        StringBuilder source = new StringBuilder();
        for (int index = 1; index <= 4; index++) {
            source.append(index)
                    .append("\n00:00:00,000 --> 00:00:01,000\n")
                    .append(repeat("довгий текст ", 80)).append("\n\n");
        }
        SubRipDocument byteBounded = SubRipDocument.parse(
                source.toString().getBytes(StandardCharsets.UTF_8));
        SubRipDocument.Batch batch = byteBounded.batch(0, 4);
        assertTrue(batch.toCue < 4);
        assertTrue(batch.payload.getBytes(StandardCharsets.UTF_8).length <= 3_500);
    }

    private static SubRipDocument simpleDocument(int count) {
        StringBuilder source = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            source.append(index)
                    .append("\n00:00:00,000 --> 00:00:01,000\nCue ")
                    .append(index);
            if (index < count) source.append("\n\n");
        }
        return SubRipDocument.parse(source.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }

    private static void rejects(byte[] data) {
        try {
            SubRipDocument.parse(data);
            fail("Expected malformed SubRip input to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
