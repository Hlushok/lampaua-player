package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

public class MatroskaMetadataReaderTest {
    private static final byte[] MKV_VIDEO_TRACK = new byte[] {
            0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, (byte) 0x80,
            0x18, 0x53, (byte) 0x80, 0x67, (byte) 0xff,
            0x16, 0x54, (byte) 0xae, 0x6b, (byte) 0x9c,
            (byte) 0xae, (byte) 0x9a,
            (byte) 0xd7, (byte) 0x81, 0x01,
            (byte) 0x83, (byte) 0x81, 0x01,
            0x23, (byte) 0xe3, (byte) 0x83, (byte) 0x84, 0x02, 0x62, 0x5a, 0x00,
            0x53, 0x6e, (byte) 0x82, 0x56, 0x31,
            0x22, (byte) 0xb5, (byte) 0x9c, (byte) 0x83, 0x75, 0x6b, 0x72
    };

    @Test
    public void markerBitsAndDefaultDurationAreRead() {
        List<TrackMetadata> tracks = MatroskaMetadataReader.parse(
                new ByteArrayInputStream(MKV_VIDEO_TRACK));

        assertEquals(1, tracks.size());
        assertEquals(1, tracks.get(0).trackId);
        assertEquals("V1", tracks.get(0).name);
        assertEquals("ukr", tracks.get(0).language);
        assertEquals(25f, tracks.get(0).frameRate, 0.001f);
    }
}
