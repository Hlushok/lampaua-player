package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AviMetadataReaderTest {
    @Test
    public void mainHeaderMicrosecondsBecomeFrameRate() {
        ByteBuffer bytes = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(28);
        bytes.put("AVI ".getBytes(StandardCharsets.US_ASCII));
        bytes.put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        bytes.put("hdrl".getBytes(StandardCharsets.US_ASCII));
        bytes.put("avih".getBytes(StandardCharsets.US_ASCII)).putInt(4).putInt(40_000);

        List<TrackMetadata> tracks = AviMetadataReader.parse(
                new ByteArrayInputStream(bytes.array()));

        assertEquals(1, tracks.size());
        assertEquals(TrackMetadata.Type.VIDEO, tracks.get(0).type);
        assertEquals(25f, tracks.get(0).frameRate, 0.001f);
    }
}
