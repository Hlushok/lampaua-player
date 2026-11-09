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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class M3uPlaylistTest {
    private final Uri base = Uri.parse("http://127.0.0.1:8090/playlist/series.m3u?hash=abc&fromlast");

    private Bundle parse(String text) throws Exception {
        return M3uPlaylist.parse(text, base, base, Collections.emptyMap());
    }

    @Test public void torrServeContinueIsRecognizedDespiteGenericVideoMimeAndQuery() {
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(base, "video/*");
        assertTrue(M3uPlaylist.isCandidate(intent));
        assertEquals("hash=abc&fromlast", intent.getData().getQuery());
        intent.putExtra(M3uPlaylist.CHECKED, true);
        assertFalse(M3uPlaylist.isCandidate(intent));
    }

    @Test public void declaredPlaylistApisAreNeverProbedAsM3u() {
        for (String key : new String[]{"playlist", "video_list", "lampaua.playlist_json", "playlist_json"}) {
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(base, "video/*").putExtra(key, "present");
            assertFalse(M3uPlaylist.isCandidate(intent));
        }
        assertFalse(M3uPlaylist.isCandidate(new Intent(Intent.ACTION_VIEW, Uri.parse("http://server/video.mkv"))));
        assertTrue(M3uPlaylist.isCandidate(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("https://server/list"), "audio/x-mpegurl")));
    }

    @Test public void entriesKeepServerOrderNamesAndRelativeUrlsWithoutInventingACallerPosition() throws Exception {
        Bundle result = parse("\uFEFF#EXTM3U\r\n#EXTINF:-1,Серія 5\r\n../stream/episode5.mkv?index=5&play\r\n"
                + "#EXTINF:-1 tvg-logo=\"https://a/img,a.png\",Серія 6, частина 2\nhttps://cdn/episode6.mkv\n");
        PlaylistApi api = new PlaylistApi(result);
        assertEquals(2, api.items.size());
        assertEquals(0, api.startIndex);
        assertEquals("Серія 5", api.items.get(0).title);
        assertEquals("http://127.0.0.1:8090/stream/episode5.mkv?index=5&play", api.items.get(0).source.uri);
        assertEquals("Серія 6, частина 2", api.items.get(1).title);
        assertFalse(api.items.get(0).hasPosition);
        assertEquals(0, api.items.get(0).positionMs);
        assertEquals("Серія 5", api.items.get(0).mediaItem(RuntimeEnvironment.getApplication()).mediaMetadata.title);
    }

    @Test public void hlsMasterAndMediaPlaylistsNeverBecomeEpisodeLists() throws Exception {
        assertNull(parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nhd.m3u8\n"));
        assertNull(parse("#EXTM3U\n#EXTINF:6,\nsegment.ts\n#EXT-X-TARGETDURATION:6\n"));
        assertNull(parse("#extm3u\n#ext-x-version:3\n#EXTINF:6,\nsegment.ts\n"));
        assertNull(parse("not a playlist"));
    }

    @Test public void credentialsStayOnTheirOriginWhileNonSecretHeadersCanBeInherited() throws Exception {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Authorization", "Basic test"); headers.put("Cookie", "session=test"); headers.put("User-Agent", "UA-test");
        Bundle result = M3uPlaylist.parse("#EXTM3U\n#EXTINF:-1,A\n/stream/a\n#EXTINF:-1,B\nhttp://other.test/b\n",
                base, base, headers);
        PlaylistApi api = new PlaylistApi(result);
        assertEquals("Basic test", api.items.get(0).headers.get("authorization"));
        assertNull(api.items.get(1).headers.get("authorization"));
        assertNull(api.items.get(1).headers.get("cookie"));
        assertEquals("UA-test", api.items.get(1).headers.get("user-agent"));
        assertTrue(M3uPlaylist.sameOrigin(Uri.parse("https://a/list"), Uri.parse("https://a:443/a")));
        assertFalse(M3uPlaylist.sameOrigin(Uri.parse("https://a/list"), Uri.parse("http://a/a")));
    }

    @Test public void remotePlaylistsCannotOpenLocalFilesAndInvalidListsFailExplicitly() throws Exception {
        for (String bad : new String[]{"#EXTM3U\n", "#EXTM3U\nfile:///data/private.mp4\n",
                "#EXTM3U\ncontent://private/video\n", "#EXTM3U\njavascript:alert(1)\n"}) {
            try { parse(bad); fail("Expected refusal"); } catch (IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }

    @Test public void oversizedAndExcessiveListsAreRejectedRatherThanSilentlyTruncated() throws Exception {
        StringBuilder text = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i <= M3uPlaylist.MAX_ITEMS; i++) text.append("#EXTINF:-1,Episode\nhttp://server/").append(i).append('\n');
        try { parse(text.toString()); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("many")); }
        char[] huge = new char[M3uPlaylist.MAX_BYTES + 1];
        try { parse(new String(huge)); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("large")); }
    }
}
