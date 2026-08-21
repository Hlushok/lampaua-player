package com.brouken.player;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Reads the frame rate from the AVI main header without scanning media payloads. */
final class AviMetadataReader {
    private static final long MAX_BYTES_BEFORE_HDRL = 64 * 1024L;

    private AviMetadataReader() { }

    static List<TrackMetadata> parse(InputStream inputStream) {
        DataInputStream stream = new DataInputStream(inputStream);
        try {
            if (!"RIFF".equals(readFourCc(stream))) return Collections.emptyList();
            stream.skipBytes(4);
            if (!"AVI ".equals(readFourCc(stream))) return Collections.emptyList();

            long scanned = 0;
            while (scanned < MAX_BYTES_BEFORE_HDRL) {
                String chunk = readFourCc(stream);
                long size = (readUInt32(stream) + 1L) & ~1L;
                if ("LIST".equals(chunk)) {
                    if (size < 4) return Collections.emptyList();
                    if ("hdrl".equals(readFourCc(stream))) return parseHdrl(stream);
                    skipFully(stream, size - 4);
                } else {
                    skipFully(stream, size);
                }
                scanned += size;
            }
        } catch (IOException ignored) {
            // A short or malformed header carries no trustworthy metadata.
        }
        return Collections.emptyList();
    }

    private static List<TrackMetadata> parseHdrl(DataInputStream stream) throws IOException {
        if (!"avih".equals(readFourCc(stream))) return Collections.emptyList();
        readUInt32(stream);
        long microsecondsPerFrame = readUInt32(stream);
        if (microsecondsPerFrame <= 0) return Collections.emptyList();
        return Collections.singletonList(new TrackMetadata(0, null, "und",
                TrackMetadata.Type.VIDEO, 1_000_000f / microsecondsPerFrame));
    }

    private static String readFourCc(DataInputStream stream) throws IOException {
        byte[] bytes = new byte[4];
        stream.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static long readUInt32(DataInputStream stream) throws IOException {
        return Integer.reverseBytes(stream.readInt()) & 0xffffffffL;
    }

    private static void skipFully(DataInputStream stream, long bytes) throws IOException {
        if (bytes < 0) throw new IOException("Negative AVI chunk size");
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = stream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                if (stream.read() < 0) throw new IOException("Truncated AVI header");
                remaining--;
            }
        }
    }
}
