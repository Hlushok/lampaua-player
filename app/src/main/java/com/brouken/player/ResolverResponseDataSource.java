package com.brouken.player;

import android.net.Uri;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inspects the headers of network media responses before a container parser consumes them.
 * This lets the activity distinguish a temporary Lampac JSON control response from video and
 * learn the real manifest type of extensionless URLs after redirects.
 */
final class ResolverResponseDataSource implements DataSource {
    interface Listener {
        void onResolverControlResponse(Uri requestedUri);
        void onManifestTypeDetected(Uri requestedUri, String mimeType);
    }

    private final DataSource upstream;
    private final Listener listener;

    ResolverResponseDataSource(DataSource upstream, Listener listener) {
        this.upstream = upstream;
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        long length = upstream.open(dataSpec);
        if (dataSpec.position != 0 || dataSpec.uri == null) return length;

        Map<String, List<String>> headers = upstream.getResponseHeaders();
        String contentType = contentType(headers);
        if (contentType != null && contentType.contains("json")) {
            listener.onResolverControlResponse(dataSpec.uri);
            closeQuietly();
            throw ParserException.createForMalformedManifest(
                    "Media endpoint returned a temporary JSON control response", null);
        }

        String manifestType = manifestMimeType(contentType, upstream.getUri());
        if (manifestType != null) listener.onManifestTypeDetected(dataSpec.uri, manifestType);
        return length;
    }

    private static String contentType(Map<String, List<String>> headers) {
        if (headers == null) return null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || !"Content-Type".equalsIgnoreCase(entry.getKey())) continue;
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty() || values.get(0) == null) return null;
            String value = values.get(0).toLowerCase(Locale.US);
            int separator = value.indexOf(';');
            return separator >= 0 ? value.substring(0, separator).trim() : value.trim();
        }
        return null;
    }

    private static String manifestMimeType(String contentType, Uri finalUri) {
        if (contentType != null) {
            if (contentType.contains("mpegurl")) return MimeTypes.APPLICATION_M3U8;
            if (contentType.equals("application/dash+xml")) return MimeTypes.APPLICATION_MPD;
            if (contentType.equals("application/vnd.ms-sstr+xml")) return MimeTypes.APPLICATION_SS;
        }
        String path = finalUri == null ? null : finalUri.getPath();
        if (path == null) return null;
        String normalized = path.toLowerCase(Locale.US);
        if (normalized.endsWith(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        if (normalized.endsWith(".mpd")) return MimeTypes.APPLICATION_MPD;
        if (normalized.endsWith(".ism") || normalized.endsWith(".isml")) {
            return MimeTypes.APPLICATION_SS;
        }
        return null;
    }

    private void closeQuietly() {
        try {
            upstream.close();
        } catch (IOException ignored) { }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return upstream.read(buffer, offset, length);
    }

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        upstream.close();
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
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
            return new ResolverResponseDataSource(upstreamFactory.createDataSource(), listener);
        }
    }
}
