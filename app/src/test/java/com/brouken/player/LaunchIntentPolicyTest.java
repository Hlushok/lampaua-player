package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LaunchIntentPolicyTest {

    @Test
    public void freshLauncherStartSuppressesRememberedMedia() {
        assertTrue(LaunchIntentPolicy.shouldSuppressResume(
                "android.intent.action.MAIN", false, false));
    }

    @Test
    public void externalMediaAndRoomInvitesKeepTheirPlaybackPath() {
        assertFalse(LaunchIntentPolicy.shouldSuppressResume(
                "android.intent.action.VIEW", true, false));
        assertFalse(LaunchIntentPolicy.shouldSuppressResume(
                "android.intent.action.MAIN", false, true));
        assertFalse(LaunchIntentPolicy.shouldSuppressResume(
                "com.lampaua.player.action.SHORTCUT_VIDEOS", false, false));
        assertFalse(LaunchIntentPolicy.shouldSuppressResume(
                "android.intent.action.SEND", false, false));
    }
}
