package com.brouken.player;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class M3uPlaylistLoaderTest {
    private static final class Result implements M3uPlaylistLoader.Callback {
        Bundle playlist;
        boolean failed;
        int calls;
        @Override public void complete(Bundle playlist, boolean failed) {
            this.playlist = playlist; this.failed = failed; calls++;
        }
    }

    private M3uPlaylistLoader.Job job(Intent intent, Result result) {
        return new M3uPlaylistLoader.Job(RuntimeEnvironment.getApplication(), intent, new OkHttpClient(), result);
    }

    @Test public void actualHttpRequestRetainsFromlastAndConvertsTheReturnedItems() throws Exception {
        try (Server server = new Server(200, "#EXTM3U\n#EXTINF:-1,Episode 5\n/stream/5?play\n", "")) {
            Result result = new Result();
            job(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(server.url("/playlist/show.m3u?hash=abc&fromlast")), "video/*"), result).run();
            assertFalse(result.failed); assertNotNull(result.playlist);
            assertTrue(server.request().startsWith("GET /playlist/show.m3u?hash=abc&fromlast HTTP/1.1"));
            PlaylistApi api = new PlaylistApi(result.playlist);
            assertEquals(server.url("/stream/5?play"), api.items.get(0).source.uri);
        }
    }

    @Test public void redirectUsesNewBaseButDoesNotLeakCredentialsAcrossPorts() throws Exception {
        try (Server destination = new Server(200, "#EXTM3U\n#EXTINF:-1,Episode\nvideo.mkv\n", "");
             Server redirect = new Server(302, "", "Location: " + destination.url("/folder/list.m3u") + "\r\n")) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirect.url("/list.m3u")))
                    .putExtra("headers", new String[]{"Authorization", "Basic test-only", "Cookie", "test=1", "User-Agent", "UA-test"});
            Result result = new Result(); job(intent, result).run();
            assertTrue(redirect.request().contains("Authorization: Basic test-only"));
            assertFalse(destination.request().contains("Authorization:"));
            assertFalse(destination.request().contains("Cookie:"));
            PlaylistApi api = new PlaylistApi(result.playlist);
            assertEquals(destination.url("/folder/video.mkv"), api.items.get(0).source.uri);
            assertNull(api.items.get(0).headers.get("authorization"));
            assertEquals("UA-test", api.items.get(0).headers.get("user-agent"));
        }
    }

    @Test public void hlsContentFallsBackAndHttpFailureIsExplicitForFileLists() throws Exception {
        try (Server server = new Server(200, "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg.ts\n", "")) {
            Result result = new Result(); job(new Intent(Intent.ACTION_VIEW, Uri.parse(server.url("/live.m3u8"))), result).run();
            assertEquals(1, result.calls); assertFalse(result.failed); assertNull(result.playlist);
        }
        try (Server server = new Server(401, "denied", "")) {
            Result result = new Result(); job(new Intent(Intent.ACTION_VIEW, Uri.parse(server.url("/list.m3u"))), result).run();
            assertTrue(result.failed); assertNull(result.playlist);
        }
    }

    @Test public void cancelledJobCannotPerformTheRequestOrDeliverResults() {
        Result result = new Result();
        M3uPlaylistLoader.Job job = job(new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:1/list.m3u")), result);
        job.cancel(); job.run();
        assertEquals(0, result.calls);
    }

    /** Tiny loopback fixture: no real TorrServer or torrent traffic is used by these tests. */
    private static final class Server implements AutoCloseable {
        final ServerSocket socket;
        final Thread thread;
        final CountDownLatch read = new CountDownLatch(1);
        final AtomicReference<String> captured = new AtomicReference<>();
        Server(int code, String body, String headers) throws Exception {
            socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            socket.setSoTimeout(5000);
            thread = new Thread(() -> {
                try (Socket connection = socket.accept()) {
                    connection.setSoTimeout(5000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder request = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) request.append(line).append('\n');
                    captured.set(request.toString()); read.countDown();
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    String response = "HTTP/1.1 " + code + " Test\r\nContent-Type: audio/x-mpegurl\r\n"
                            + "Connection: close\r\nContent-Length: " + bytes.length + "\r\n" + headers + "\r\n";
                    connection.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                    connection.getOutputStream().write(bytes); connection.getOutputStream().flush();
                } catch (Exception error) { captured.compareAndSet(null, error.toString()); read.countDown(); }
            }, "m3u-test-server");
            thread.setDaemon(true); thread.start();
        }
        String url(String path) { return "http://127.0.0.1:" + socket.getLocalPort() + path; }
        String request() throws Exception { assertTrue(read.await(5, TimeUnit.SECONDS)); return captured.get(); }
        @Override public void close() throws Exception { socket.close(); thread.join(1000); }
    }
}
