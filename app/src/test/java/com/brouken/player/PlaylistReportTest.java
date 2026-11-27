package com.brouken.player;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PlaylistReportTest {
    private Context context;
    private static final String ACTION = "test.playback.callback";

    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("playlist_sessions", 0).edit().clear().commit();
    }

    private Bundle request() {
        Bundle root = PlaylistApiTest.request(PlaylistApiTest.item("https://media/one"),
                PlaylistApiTest.item("https://media/two"), PlaylistApiTest.item("https://media/three"));
        root.putParcelable("result_callback", PendingIntent.getBroadcast(context, 42,
                new Intent(ACTION).setPackage(context.getPackageName()).putExtra("lampa_activity", "card-A"),
                PendingIntent.FLAG_UPDATE_CURRENT));
        return root;
    }

    private List<Intent> reports() {
        return Shadows.shadowOf((Application) context).getBroadcastIntents().stream()
                .filter(intent -> ACTION.equals(intent.getAction())).collect(Collectors.toList());
    }

    @Test public void intervalAndFinalCallbacksKeepLaunchIdentityAndNeverInventVisits() throws Exception {
        PlaylistSession session = new PlaylistSession(context, new PlaylistApi(request()), new Intent());
        session.capture(2, "https://media/three", 45_001, 90_000, true);
        session.report(true);
        Intent interval = reports().get(0);
        assertEquals("interval", interval.getStringExtra("end_by"));
        assertEquals("card-A", interval.getStringExtra("lampa_activity"));
        assertArrayEquals(new long[]{-1, -1, 45_001}, interval.getLongArrayExtra("positions_ms"));
        session.report();
        assertEquals("user", reports().get(1).getStringExtra("end_by"));
        session.report();
        assertEquals(2, reports().size());
    }

    @Test public void unchangedSnapshotIsNotRepeatedAfterActivityRecreation() throws Exception {
        Bundle root = request(); Intent launch = new Intent();
        PlaylistSession first = new PlaylistSession(context, new PlaylistApi(root), launch);
        first.capture(0, "https://media/one", 12_000, 90_000, true);
        first.report(); first.report();
        assertEquals(1, reports().size());
        PlaylistSession restored = new PlaylistSession(context, new PlaylistApi(root), launch);
        restored.report();
        assertEquals(1, reports().size());
        restored.capture(0, "https://media/one", 15_000, 90_000, true);
        restored.report();
        assertEquals(2, reports().size());
    }

    @Test public void headerLinesAndPairsOverrideCaseInsensitively() throws Exception {
        Bundle root = request();
        root.putStringArray("headers", new String[]{"User-Agent: UA", "Referer", "https://site/", "X-Token: a:b"});
        Bundle item = (Bundle) root.getParcelableArray("items")[0];
        item.putStringArray("headers", new String[]{"referer: https://episode/"});
        PlaylistApi api = new PlaylistApi(root);
        assertEquals("UA", api.items.get(0).headers.get("user-agent"));
        assertEquals("https://episode/", api.items.get(0).headers.get("referer"));
        assertEquals("a:b", api.items.get(0).headers.get("x-token"));
    }
}
