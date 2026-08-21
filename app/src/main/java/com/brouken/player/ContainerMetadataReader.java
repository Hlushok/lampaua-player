package com.brouken.player;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Dispatches a bounded container header to the matching metadata parser. */
final class ContainerMetadataReader {
    static final int SIGNATURE_BYTES = 12;

    private enum Container {
        MATROSKA(256 * 1024),
        AVI(64 * 1024),
        MP4(512 * 1024);

        final int headerBytes;

        Container(int headerBytes) {
            this.headerBytes = headerBytes;
        }
    }

    private ContainerMetadataReader() { }

    private static Container signature(byte[] header) {
        if (header == null || header.length < SIGNATURE_BYTES) return null;
        if ((header[0] & 0xff) == 0x1a && (header[1] & 0xff) == 0x45
                && (header[2] & 0xff) == 0xdf && (header[3] & 0xff) == 0xa3) {
            return Container.MATROSKA;
        }
        if ("ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII))) {
            return Container.MP4;
        }
        if ("RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                && "AVI ".equals(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
            return Container.AVI;
        }
        return null;
    }

    static int headerBudget(byte[] header) {
        Container container = signature(header);
        return container == null ? 0 : container.headerBytes;
    }

    static List<TrackMetadata> parse(InputStream inputStream) {
        byte[] header = new byte[SIGNATURE_BYTES];
        PushbackInputStream pushback = new PushbackInputStream(inputStream, header.length);
        try {
            new DataInputStream(pushback).readFully(header);
            pushback.unread(header);
        } catch (IOException error) {
            return Collections.emptyList();
        }

        Container container = signature(header);
        if (container == null) return Collections.emptyList();
        try {
            switch (container) {
                case MATROSKA:
                    return MatroskaMetadataReader.parse(pushback);
                case MP4:
                    return Mp4MetadataReader.parse(pushback);
                default:
                    return AviMetadataReader.parse(pushback);
            }
        } catch (RuntimeException error) {
            return Collections.emptyList();
        }
    }
}
