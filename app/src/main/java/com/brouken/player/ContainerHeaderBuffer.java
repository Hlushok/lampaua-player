package com.brouken.player;

import java.io.ByteArrayOutputStream;

/** Collects only the bounded prefix that a supported container parser can use. */
final class ContainerHeaderBuffer {
    private final byte[] signature = new byte[ContainerMetadataReader.SIGNATURE_BYTES];
    private int signatureLength;
    private int budget;
    private ByteArrayOutputStream header;
    private boolean done;
    private boolean emitted;

    void append(byte[] input, int offset, int length) {
        if (done || length <= 0) return;
        if (input == null || offset < 0 || length < 0 || offset > input.length - length) {
            throw new IndexOutOfBoundsException();
        }

        if (budget == 0) {
            int taken = Math.min(length, signature.length - signatureLength);
            System.arraycopy(input, offset, signature, signatureLength, taken);
            signatureLength += taken;
            offset += taken;
            length -= taken;
            if (signatureLength < signature.length) return;

            budget = ContainerMetadataReader.headerBudget(signature);
            if (budget == 0) {
                done = true;
                return;
            }
            header = new ByteArrayOutputStream(Math.min(budget, 64 * 1024));
            header.write(signature, 0, signature.length);
        }

        int remaining = budget - header.size();
        if (remaining > 0 && length > 0) {
            header.write(input, offset, Math.min(length, remaining));
        }
        if (header.size() >= budget) done = true;
    }

    byte[] finish() {
        if (emitted) return null;
        emitted = true;
        done = true;
        if (header == null) return null;
        byte[] result = header.toByteArray();
        header = null;
        return result;
    }

    boolean isDone() {
        return done;
    }
}
