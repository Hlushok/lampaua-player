package com.brouken.player;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Translates a complete SubRip document and publishes it atomically in one directory. */
final class UkrainianSubtitleTranslator {
    private UkrainianSubtitleTranslator() { }

    static boolean translate(File source, File target, String sourceIso3,
                             SubtitleTranslationTransport transport) {
        if (source == null || target == null || transport == null
                || !source.isFile() || source.length() <= 0
                || source.length() > SubRipDocument.MAX_INPUT_BYTES) {
            return false;
        }
        String normalizedSource = UkrainianSubtitlePolicy.normalizeIso3(sourceIso3);
        String sourceIso2 = UkrainianSubtitlePolicy.sourceIso2(normalizedSource);
        if (normalizedSource == null || UkrainianSubtitlePolicy.SEARCH_LANGUAGE.equals(normalizedSource)
                || sourceIso2 == null
                || UkrainianSubtitlePolicy.targetIso2().equals(sourceIso2)) {
            return false;
        }
        File directory = target.getParentFile();
        if (directory == null || !directory.isDirectory()) return false;
        File temporary = new File(directory, target.getName() + ".tmp");
        boolean published = false;
        try {
            if (source.getCanonicalFile().equals(target.getCanonicalFile())) return false;
            if (temporary.exists() && !temporary.delete()) return false;
            if (target.exists() && !target.delete()) return false;

            SubRipDocument document = SubRipDocument.parse(readBounded(source));
            int fromCue = 0;
            while (fromCue < document.cueCount()) {
                if (Thread.currentThread().isInterrupted()) return false;
                SubRipDocument.Batch batch = document.batch(
                        fromCue, document.cueCount() - fromCue);
                if (!translateBatch(document, batch, sourceIso2, transport)) return false;
                fromCue = batch.toCue;
            }

            byte[] rendered = document.renderUtf8();
            SubRipDocument.parse(rendered);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(rendered);
                output.flush();
                output.getFD().sync();
            }
            if (Thread.currentThread().isInterrupted() || !temporary.renameTo(target)) {
                return false;
            }
            published = true;
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        } finally {
            if (!published && temporary.exists()) temporary.delete();
        }
    }

    private static boolean translateBatch(SubRipDocument document, SubRipDocument.Batch batch,
                                          String sourceIso2,
                                          SubtitleTranslationTransport transport) {
        String translated = requestWithOneRetry(transport, sourceIso2, batch.payload);
        if (translated == null) return false;
        if (document.acceptTranslation(batch, translated)) return true;

        int count = batch.toCue - batch.fromCue;
        if (count <= 1 || Thread.currentThread().isInterrupted()) return false;
        int leftCount = count / 2;
        SubRipDocument.Batch left = document.batch(batch.fromCue, leftCount);
        SubRipDocument.Batch right = document.batch(left.toCue, batch.toCue - left.toCue);
        return translateBatch(document, left, sourceIso2, transport)
                && translateBatch(document, right, sourceIso2, transport);
    }

    private static String requestWithOneRetry(SubtitleTranslationTransport transport,
                                              String sourceIso2, String payload) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (Thread.currentThread().isInterrupted()) return null;
            try {
                return transport.translate(sourceIso2,
                        UkrainianSubtitlePolicy.targetIso2(), payload);
            } catch (IOException error) {
                if (attempt == 1) return null;
            }
        }
        return null;
    }

    private static byte[] readBounded(File source) throws IOException {
        try (InputStream input = new FileInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     (int) Math.min(source.length(), SubRipDocument.MAX_INPUT_BYTES))) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > SubRipDocument.MAX_INPUT_BYTES) {
                    throw new IOException("Subtitle input too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
