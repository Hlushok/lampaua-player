package com.brouken.player.update;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdatePolicyTest {
    private static UpdateInfo release(String version, String url) {
        return new UpdateInfo(UpdatePolicy.versionCode(version), "v" + version,
                version, "Changes", url, 100);
    }

    @Test public void offersOnlyNewUnskippedStableApk() {
        int current = UpdatePolicy.versionCode("1.6.0");
        UpdateInfo newer = release("1.7.0", "https://github.com/a/UA-Player.apk");
        assertTrue(UpdatePolicy.shouldOffer(current, newer, 0, false));
        assertFalse(UpdatePolicy.shouldOffer(current, newer, newer.versionCode, false));
        assertFalse(UpdatePolicy.shouldOffer(current, newer, 0, true));
        assertFalse(UpdatePolicy.shouldOffer(current, release("1.6.0", newer.apkUrl), 0, false));
        assertFalse(UpdatePolicy.shouldOffer(current, release("1.7.0", "http://bad/update.apk"), 0, false));
        assertFalse(UpdatePolicy.shouldOffer(current, release("1.7.0", "https://example.com/readme"), 0, false));
    }

    @Test public void malformedVersionsAreNeverNewer() {
        assertFalse(UpdatePolicy.shouldOffer(UpdatePolicy.versionCode("1.6.0"),
                release("broken", "https://example.com/UA-Player.apk"), 0, false));
    }
}
