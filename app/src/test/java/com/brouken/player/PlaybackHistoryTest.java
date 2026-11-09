package com.brouken.player;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PlaybackHistoryTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
    private Context context;

    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        context.deleteFile("positions");
    }

    private Uri stream(String name, String query) {
        return Uri.parse("http://server:8090/stream/" + name + "?" + query);
    }

    @Test public void renamedFileAndReorderedParametersKeepOnePositionAcrossRestart() {
        Prefs prefs = new Prefs(context);
        prefs.updatePosition(stream("old.mkv", "link=" + HASH + "&index=02&play"), 91_234);
        Uri renamed = stream("new.mkv", "preload&index=2&link=" + HASH.toUpperCase() + "&play#name");
        assertEquals(91_234, new Prefs(context).getPosition(renamed));
    }

    @Test public void nativeBrowserAndHttpStreamShareTheFileIdentity() {
        Uri nativeFile = Uri.parse("torr://server:8090/Film/a.mkv?b=http%3A%2F%2Fserver%3A8090&h=" + HASH + "&i=2");
        Prefs.rememberPosition(context, nativeFile, 44_567);
        assertEquals(44_567, new Prefs(context).getPosition(stream("b.mkv", "index=2&play&link=" + HASH)));
    }

    @Test public void explicitDefaultPortAndMagnetHashKeepTheSamePosition() {
        Uri original = Uri.parse("https://SERVER:443/proxy/stream/a?link=" + HASH + "&index=1&play");
        Uri magnet = Uri.parse("https://server/proxy/stream/b?index=1&link="
                + Uri.encode("magnet:?xt=urn:btih:" + HASH + "&dn=film") + "&play");
        Prefs.rememberPosition(context, original, 77_789);
        assertEquals(77_789, new Prefs(context).getPosition(magnet));
    }

    @Test public void differentFilesServersAndProxyPrefixesNeverSharePositions() {
        Uri original = stream("a", "link=" + HASH + "&index=1&play");
        Prefs.rememberPosition(context, original, 55_123);
        Prefs prefs = new Prefs(context);
        for (String changed : new String[]{original.toString().replace("index=1", "index=2"),
                original.toString().replace("server:", "other:"), original.toString().replace(":8090", ":8091"),
                original.toString().replace("/stream/", "/proxy/stream/"), original.toString().replace("http:", "https:")}) {
            assertEquals(changed, 0, prefs.getPosition(Uri.parse(changed)));
        }
    }

    @Test public void ambiguousOrOrdinaryUrlsRetainTheirExactIdentity() {
        Prefs prefs = new Prefs(context);
        for (String query : new String[]{"link=" + HASH, "link=" + HASH + "&index=0",
                "link=not-a-hash&index=1", "link=" + HASH + "&index=99999999999999999999"}) {
            Uri first = stream("a", query);
            prefs.updatePosition(first, 1000);
            assertEquals(0, prefs.getPosition(stream("b", query)));
        }
        Uri ordinary = Uri.parse("https://cdn/video.mkv?token=one");
        prefs.updatePosition(ordinary, Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE - 1, new Prefs(context).getPosition(ordinary));
        assertEquals(0, prefs.getPosition(Uri.parse("https://cdn/video.mkv?token=two")));
    }

    @Test public void legacyDuplicatesMigrateUsingTheLastSavedEntry() throws Exception {
        LinkedHashMap<String, Long> legacy = new LinkedHashMap<>();
        legacy.put(stream("old", "link=" + HASH + "&index=1&play").toString(), 10_999L);
        legacy.put(stream("new", "play&index=1&link=" + HASH).toString(), 20_777L);
        write(legacy);
        assertEquals(20_777, new Prefs(context).getPosition(stream("another", "link=" + HASH + "&index=1")));
        assertEquals(1, Prefs.readPositions(context).size());
    }

    @Test public void historyKeepsExactly1000MostRecentlyUpdatedEntries() throws Exception {
        LinkedHashMap<String, Long> legacy = new LinkedHashMap<>();
        for (int i = 0; i < 1000; i++) legacy.put("https://cdn/video/" + i, (long) i);
        write(legacy);
        Prefs prefs = new Prefs(context);
        prefs.updatePosition(Uri.parse("https://cdn/video/0"), 3333);
        prefs.updatePosition(Uri.parse("https://cdn/video/1000"), 4444);
        assertEquals(1000, Prefs.readPositions(context).size());
        Prefs restored = new Prefs(context);
        assertEquals(3333, restored.getPosition(Uri.parse("https://cdn/video/0")));
        assertEquals(0, restored.getPosition(Uri.parse("https://cdn/video/1")));
        assertEquals(4444, restored.getPosition(Uri.parse("https://cdn/video/1000")));
    }

    @Test public void nonPersistentLaunchPositionStillWinsOverLocalHistoryIncludingZero() {
        Uri uri = stream("a", "link=" + HASH + "&index=1&play");
        Prefs.rememberPosition(context, uri, 90_000);
        Prefs prefs = new Prefs(context);
        prefs.setPersistent(false);
        prefs.updateMedia(context, uri, null);
        prefs.updatePosition(0);
        assertEquals(0, prefs.getPosition());
        assertEquals(90_000, new Prefs(context).getPosition(uri));
    }

    private void write(LinkedHashMap<String, Long> values) throws Exception {
        try (ObjectOutputStream out = new ObjectOutputStream(context.openFileOutput("positions", Context.MODE_PRIVATE))) {
            out.writeObject(values);
        }
    }
}
