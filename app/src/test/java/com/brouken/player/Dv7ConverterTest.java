package com.brouken.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Dv7ConverterTest {

    @Test
    public void dropsEnhancementLayerButKeepsBaseAndRpu() {
        byte[] sample = new byte[] {
                0, 0, 0, 1, 64, 1, 0x11,
                0, 0, 0, 1, 126, 1, 0x22,
                0, 0, 0, 1, 124, 1, 0x33
        };
        ByteBuffer output = ByteBuffer.allocate(32);

        int written = Dv7Converter.copyWithoutEnhancementLayer(
                sample, sample.length, output);

        assertEquals(14, written);
        assertArrayEquals(new byte[] {
                0, 0, 0, 1, 64, 1, 0x11,
                0, 0, 0, 1, 124, 1, 0x33
        }, Arrays.copyOf(output.array(), written));
    }

    @Test
    public void findsLengthPrefixedRpuOnlyWhenBlockTilesExactly() {
        byte[] block = new byte[] {0, 3, 124, 1, 0x55};
        int[] range = new int[2];

        assertTrue(Dv7Converter.findRpu(block, block.length, 2, range));
        assertArrayEquals(new int[] {2, 3}, range);
        assertFalse(Dv7Converter.findRpu(
                new byte[] {0, 4, 124, 1}, 4, 2, range));
    }
}
