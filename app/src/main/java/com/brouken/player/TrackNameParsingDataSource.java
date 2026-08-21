package com.brouken.player;

import android.net.Uri;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.datasource.DataSink;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TeeDataSource;
import androidx.media3.datasource.TransferListener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps an upstream {@link DataSource} and, on the first read of a media item (offset 0), tees the
 * bytes the player reads into a background parser ({@link ContainerMetadataReader}) that recovers
 * per-track container names. Uses only public Media3 API ({@link TeeDataSource} + {@link DataSink}),
 * so it works against the locally-built ExoPlayer AAR without touching its internals.
 *
 * <p>Because it only tees the read that starts at offset 0, it captures metadata that the player
 * reads from the front of the stream (faststart MP4, Matroska headers). A {@code moov} at the end of
 * the file is fetched by the player via a separate seek/reopen and is therefore not seen here вЂ” the
 * caller degrades to the language name in that case.
 */
final class TrackNameParsingDataSource implements DataSource {

    /**
     * Total bytes pulled through the media data sources. The UI samples the delta to show a real
     * transfer rate while buffering: Media3's own BandwidthMeter only updates on completed transfers,
     * so it keeps reporting the last estimate when a stream goes silent вЂ” which is the very case the
     * rate has to expose. Monotonic; readers compare successive samples.
     */
    static final AtomicLong bytesRead = new AtomicLong();

    /** Receives parsed track metadata on the load thread and reports whether it already has it. */
    interface Listener {
        void onMetadataParsed(List<TrackMetadata> tracks);
        boolean isMetadataParsed();

        /**
         * Called (on a load thread) when the real HTTP response for a media item reveals a streaming
         * manifest type вЂ” HLS/DASH/SmoothStreaming вЂ” that the extensionless request URL did not
         * advertise. {@code originalUri} is the URI the player asked for (matches the MediaItem URI).
         */
        void onMediaTypeResolved(Uri originalUri, String mimeType);

        /**
         * Called (on a load thread) when a media request came back as a Lampac stream-resolver control
         * response ({@code Content-Type: application/json}, e.g. the {@code {"rch":вЂ¦}} handshake)
         * instead of real media. Lampac resolves the real stream URL by running client-side code over
         * its WebSocket; this player does not speak that protocol, so the resolver stays not-ready and
         * answers with the control JSON. Reported from the response headers (see {@link #open}) so the
         * load can fail immediately instead of blocking on the long-polled body.
         */
        void onResolverNotReady(Uri originalUri);
    }

    private final DataSource upstream;
    private final Listener listener;
    private final HeaderSink headerSink = new HeaderSink();

    private TeeDataSource teeDataSource;

    TrackNameParsingDataSource(DataSource upstream, Listener listener) {
        this.upstream = upstream;
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        final long length;
        if (dataSpec.position == 0 && !listener.isMetadataParsed()) {
            teeDataSource = new TeeDataSource(upstream, headerSink);
            length = teeDataSource.open(dataSpec);
        } else {
            teeDataSource = null;
            length = upstream.open(dataSpec);
        }
        // Once the connection is open the response headers and the final (post-redirect) URL are
        // known: if they reveal a streaming manifest that the request URL didn't advertise (e.g. an
        // extensionless resolver that returns HLS), report it so the player can re-prepare as HLS.
        if (dataSpec.position == 0 && dataSpec.uri != null) {
            reportResolvedMediaType(dataSpec.uri);
            // A media request answered with a JSON body is a stream-resolver control response (the
            // Lampac "not ready" handshake), never playable media. The resolver sends these headers
            // immediately but then long-polls the body until a read timeout вЂ” so fail now, from the
            // headers, instead of blocking ~8s on a body we already know is not media.
            if (isJsonResponse(upstream.getResponseHeaders())) {
                try {
                    listener.onResolverNotReady(dataSpec.uri);
                } catch (Exception ignored) {
                    // Never let detection disturb the load path.
                }
                closeQuietly();
                throw ParserException.createForMalformedManifest(
                        "Stream resolver returned a non-media JSON response", /* cause= */ null);
            }
        }
        return length;
    }

    /** Whether the response {@code Content-Type} is JSON (a resolver control response, not media). */
    private static boolean isJsonResponse(Map<String, List<String>> responseHeaders) {
        if (responseHeaders == null) {
            return false;
        }
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (entry.getKey() == null || !"Content-Type".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            final List<String> values = entry.getValue();
            return values != null && !values.isEmpty() && values.get(0) != null
                    && values.get(0).toLowerCase(Locale.US).contains("json");
        }
        return false;
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // Best-effort close before rethrowing the detected not-ready error.
        }
    }

    private void reportResolvedMediaType(Uri originalUri) {
        try {
            final String mimeType = resolveStreamingMimeType(upstream.getResponseHeaders(), upstream.getUri());
            if (mimeType != null) {
                listener.onMediaTypeResolved(originalUri, mimeType);
            }
        } catch (Exception ignored) {
            // Never let media-type sniffing disturb the read path.
        }
    }

    /**
     * Maps an HTTP response to an ExoPlayer streaming-manifest MIME type, preferring the
     * {@code Content-Type} header and falling back to the extension of the final redirected URL.
     * Returns {@code null} for progressive/unknown content (the caller then keeps the default guess).
     */
    static String resolveStreamingMimeType(Map<String, List<String>> responseHeaders, Uri finalUri) {
        if (responseHeaders != null) {
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                if (entry.getKey() == null || !"Content-Type".equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                final List<String> values = entry.getValue();
                if (values != null && !values.isEmpty() && values.get(0) != null) {
                    String contentType = values.get(0);
                    final int semicolon = contentType.indexOf(';');
                    if (semicolon >= 0) {
                        contentType = contentType.substring(0, semicolon);
                    }
                    final String mime = mimeFromContentType(contentType.trim().toLowerCase(Locale.US));
                    if (mime != null) {
                        return mime;
                    }
                }
                break;
            }
        }
        if (finalUri != null) {
            final String path = finalUri.getPath();
            if (path != null) {
                final int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    switch (path.substring(dot + 1).toLowerCase(Locale.US)) {
                        case "m3u8": return MimeTypes.APPLICATION_M3U8;
                        case "mpd": return MimeTypes.APPLICATION_MPD;
                        case "ism": case "isml": return MimeTypes.APPLICATION_SS;
                    }
                }
            }
        }
        return null;
    }

    private static String mimeFromContentType(String contentType) {
        switch (contentType) {
            case "application/x-mpegurl":
            case "application/vnd.apple.mpegurl":
            case "audio/x-mpegurl":
            case "audio/mpegurl":
                return MimeTypes.APPLICATION_M3U8;
            case "application/dash+xml":
                return MimeTypes.APPLICATION_MPD;
            case "application/vnd.ms-sstr+xml":
                return MimeTypes.APPLICATION_SS;
            default:
                return null;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        final int count = teeDataSource != null
                ? teeDataSource.read(buffer, offset, length)
                : upstream.read(buffer, offset, length);
        if (count > 0) {
            bytesRead.addAndGet(count);
        }
        return count;
    }

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public void close() throws IOException {
        try {
            if (teeDataSource != null) {
                teeDataSource.close();
            } else {
                upstream.close();
            }
        } finally {
            headerSink.close();
            teeDataSource = null;
        }
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    private final class HeaderSink implements DataSink {
        private ContainerHeaderBuffer buffer;

        @Override
        public void open(DataSpec dataSpec) {
            buffer = dataSpec.position == 0 && !listener.isMetadataParsed()
                    ? new ContainerHeaderBuffer() : null;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (buffer == null) return;
            try {
                buffer.append(bytes, offset, length);
                if (buffer.isDone()) parseHeader();
            } catch (Throwable ignored) {
                buffer = null;
            }
        }

        @Override
        public void close() {
            try {
                parseHeader();
            } catch (Throwable ignored) {
                buffer = null;
            }
        }

        private void parseHeader() {
            if (buffer == null) return;
            byte[] bytes = buffer.finish();
            buffer = null;
            if (bytes == null) return;
            List<TrackMetadata> tracks = ContainerMetadataReader.parse(
                    new ByteArrayInputStream(bytes));
            if (!tracks.isEmpty()) listener.onMetadataParsed(tracks);
        }
    }

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;
        private final Listener listener;

        Factory(DataSource.Factory upstreamFactory, Listener listener) {
            this.upstreamFactory = upstreamFactory;
            this.listener = listener;
        }

        @Override
        public DataSource createDataSource() {
            return new TrackNameParsingDataSource(upstreamFactory.createDataSource(), listener);
        }
    }
}
