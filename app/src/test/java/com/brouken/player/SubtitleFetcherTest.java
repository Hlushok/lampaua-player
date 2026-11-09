package com.brouken.player;

import android.app.Application;
import android.net.Uri;

import androidx.media3.common.C;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class SubtitleFetcherTest {
    private static final String SRT = "1\n00:00:01,000 --> 00:00:03,000\nУкраїнські субтитри\n\n";

    private PlayerActivity context() {
        // Attached context only: no player, codecs, search providers or lifecycle network requests.
        return Robolectric.buildActivity(PlayerActivity.class).get();
    }

    private Uri fetch(PlayerActivity activity, Uri... urls) {
        return new SubtitleFetcher(activity, Arrays.asList(urls), "candidate.ukr.srt", C.TIME_UNSET).fetchNow();
    }

    @Test public void incompleteResponseFallsThroughToTheNextSourceAndKeepsItsLanguage() throws Exception {
        try (Server broken = new Server(SRT, 500, false); Server valid = new Server(SRT, 0, false)) {
            Uri file = fetch(context(), broken.uri(), valid.uri());
            assertNotNull(file);
            assertEquals("file", file.getScheme());
            assertTrue(file.getLastPathSegment().contains("ukr"));
            assertEquals(SRT, new String(Files.readAllBytes(new File(file.getPath()).toPath()), StandardCharsets.UTF_8));
            assertEquals(1, broken.requests.get());
            assertEquals(1, valid.requests.get());
        }
    }

    @Test public void emptyOrIncompleteCandidatesNeverBecomeAnEmptyTrackOrRemoteFallback() throws Exception {
        try (Server empty = new Server("", 0, false); Server broken = new Server(SRT, 500, false)) {
            assertNull(fetch(context(), empty.uri(), broken.uri()));
        }
    }

    @Test public void slowTricklingDownloadUsesAWholeCallBudgetThenTriesTheNextSource() throws Exception {
        try (Server slow = new Server(SRT, 100_000, true); Server valid = new Server(SRT, 0, false)) {
            long started = System.nanoTime();
            Uri file = fetch(context(), slow.uri(), valid.uri());
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertNotNull(file);
            assertEquals("file", file.getScheme());
            assertEquals(1, valid.requests.get());
            assertTrue("Download must move on within its 8 second budget, took " + elapsedMs, elapsedMs < 12_000);
        }
    }

    @Test public void validAssStillKeepsItsDetectedFormatAndPreferredLanguageName() throws Exception {
        String ass = "[Script Info]\nTitle: test\n[Events]\n"
                + "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                + "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Test\n";
        try (Server valid = new Server(ass, 0, false)) {
            Uri file = fetch(context(), valid.uri());
            assertNotNull(file);
            assertTrue(file.getLastPathSegment().endsWith(".ukr.ass"));
        }
    }

    @Test public void failedLaterCandidateCannotReturnAnOverwrittenWrongCutCache() throws Exception {
        String wrongCut = "1\n10:00:01,000 --> 10:00:03,000\nDifferent cut\n\n";
        try (Server wrong = new Server(wrongCut, 0, false); Server empty = new Server("", 0, false)) {
            assertNull(new SubtitleFetcher(context(), Arrays.asList(wrong.uri(), empty.uri()),
                    "wrong-cut.ukr.srt", 60_000).fetchNow());
        }
    }

    /** Loopback only. An announced body can end early or keep arriving below a read timeout. */
    private static final class Server implements AutoCloseable {
        final ServerSocket listener;
        final Thread worker;
        final AtomicInteger requests = new AtomicInteger();
        volatile Socket active;
        Server(String body, int missingBytes, boolean trickle) throws Exception {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout(20_000);
            worker = new Thread(() -> {
                try (Socket socket = listener.accept()) {
                    active = socket;
                    socket.setSoTimeout(5000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    requests.incrementAndGet();
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n"
                            + "Connection: close\r\nContent-Length: " + (bytes.length + missingBytes) + "\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(bytes);
                    socket.getOutputStream().flush();
                    if (trickle) for (int i = 0; i < 150; i++) {
                        Thread.sleep(100);
                        socket.getOutputStream().write(' ');
                        socket.getOutputStream().flush();
                    }
                } catch (Exception ignored) { /* Client cancellation and fixture shutdown are expected. */ }
            }, "subtitle-test-server");
            worker.setDaemon(true);
            worker.start();
        }
        Uri uri() { return Uri.parse("http://127.0.0.1:" + listener.getLocalPort() + "/download"); }
        @Override public void close() throws Exception {
            listener.close();
            if (active != null) active.close();
            worker.interrupt();
            worker.join(1000);
        }
    }
}
