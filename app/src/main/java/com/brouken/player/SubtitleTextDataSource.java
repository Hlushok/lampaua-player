package com.brouken.player;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import com.sigpwned.chardet4j.Chardet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Normalizes external text subtitles on the loader thread, including ones supplied by other apps. */
final class SubtitleTextDataSource implements DataSource {
    interface Selector { boolean isSubtitle(Uri uri); }
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private final DataSource upstream;
    private final Selector selector;
    private byte[] text;
    private int position, end;

    SubtitleTextDataSource(DataSource upstream, Selector selector) {
        this.upstream = upstream; this.selector = selector;
    }

    static DataSource.Factory factory(DataSource.Factory upstream, Selector selector) {
        return () -> new SubtitleTextDataSource(upstream.createDataSource(), selector);
    }

    @Override public long open(DataSpec spec) throws IOException {
        text = null;
        if (!selector.isSubtitle(spec.uri)) return upstream.open(spec);
        // DataSpec offsets refer to the UTF-8 file seen by the extractor, not its original encoding.
        long expected = upstream.open(spec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET).build());
        if (expected > MAX_BYTES) throw new IOException("Subtitle exceeds 8 MiB");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024]; int count;
        while ((count = upstream.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Subtitle exceeds 8 MiB");
            bytes.write(buffer, 0, count);
        }
        if (expected >= 0 && expected != bytes.size()) throw new IOException("Incomplete subtitle");
        text = utf8(bytes.toByteArray());
        if (spec.position > text.length) throw new IOException("Subtitle position out of range");
        position = (int) spec.position;
        end = spec.length == C.LENGTH_UNSET ? text.length
                : (int) (position + Math.min(spec.length, text.length - position));
        return end - position;
    }

    static byte[] utf8(byte[] input) throws IOException {
        if (input.length == 0) throw new IOException("Empty subtitle");
        try (Reader reader = Chardet.decode(new ByteArrayInputStream(input), StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder(); char[] buffer = new char[4096]; int count;
            while ((count = reader.read(buffer)) != -1) text.append(buffer, 0, count);
            return text.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        if (text == null) return upstream.read(buffer, offset, length);
        if (length == 0) return 0;
        if (position == end) return C.RESULT_END_OF_INPUT;
        int count = Math.min(length, end - position);
        System.arraycopy(text, position, buffer, offset, count); position += count;
        return count;
    }

    @Override public Uri getUri() { return upstream.getUri(); }
    @Override public Map<String, List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
    @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
    @Override public void close() throws IOException { text = null; upstream.close(); }
}
