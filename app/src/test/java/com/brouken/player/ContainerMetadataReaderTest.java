package com.brouken.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ContainerMetadataReaderTest {
    @Test
    public void knownContainersGetBoundedHeaderBudgets() {
        byte[] mkv = new byte[12];
        mkv[0] = 0x1a;
        mkv[1] = 0x45;
        mkv[2] = (byte) 0xdf;
        mkv[3] = (byte) 0xa3;

        byte[] mp4 = new byte[12];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, mp4, 4, 4);

        byte[] avi = new byte[12];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, avi, 0, 4);
        System.arraycopy("AVI ".getBytes(StandardCharsets.US_ASCII), 0, avi, 8, 4);

        assertEquals(256 * 1024, ContainerMetadataReader.headerBudget(mkv));
        assertEquals(512 * 1024, ContainerMetadataReader.headerBudget(mp4));
        assertEquals(64 * 1024, ContainerMetadataReader.headerBudget(avi));
        assertEquals(0, ContainerMetadataReader.headerBudget(new byte[12]));
    }
}
