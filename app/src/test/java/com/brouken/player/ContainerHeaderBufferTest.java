package com.brouken.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ContainerHeaderBufferTest {
    private static final byte[] MKV_HEADER = new byte[] {
            0x1a, 0x45, (byte) 0xdf, (byte) 0xa3,
            (byte) 0x80, 0x18, 0x53, (byte) 0x80,
            0x67, (byte) 0xff, 0x16, 0x54,
            (byte) 0xae, 0x6b
    };

    @Test
    public void unknownSignatureStopsWithoutPayloadAllocation() {
        ContainerHeaderBuffer buffer = new ContainerHeaderBuffer();
        byte[] manifest = "#EXTM3U\n1234".getBytes(StandardCharsets.US_ASCII);
        buffer.append(manifest, 0, manifest.length);
        buffer.append(new byte[64 * 1024], 0, 64 * 1024);

        assertTrue(buffer.isDone());
        assertNull(buffer.finish());
    }

    @Test
    public void partialMkvHeaderIsReturnedOnClose() {
        ContainerHeaderBuffer buffer = new ContainerHeaderBuffer();
        buffer.append(MKV_HEADER, 0, MKV_HEADER.length);

        assertArrayEquals(MKV_HEADER, buffer.finish());
        assertTrue(buffer.isDone());
        assertNull(buffer.finish());
    }

    @Test
    public void containerBudgetCapsCollectedBytes() {
        ContainerHeaderBuffer buffer = new ContainerHeaderBuffer();
        byte[] input = new byte[300 * 1024];
        input[0] = 0x1a;
        input[1] = 0x45;
        input[2] = (byte) 0xdf;
        input[3] = (byte) 0xa3;

        buffer.append(input, 0, input.length);

        assertTrue(buffer.isDone());
        assertEquals(256 * 1024, buffer.finish().length);
    }
}
