package com.brouken.player;

import org.junit.Test;

import static com.brouken.player.PlayerControlPolicy.Surface.DISPLAY_CLUSTER;
import static com.brouken.player.PlayerControlPolicy.Surface.HIDDEN;
import static com.brouken.player.PlayerControlPolicy.Surface.MEDIA_BAR;
import static com.brouken.player.PlayerControlPolicy.Surface.MORE_PLAYBACK;
import static com.brouken.player.PlayerControlPolicy.Surface.MORE_SESSION;
import static com.brouken.player.PlayerControlPolicy.Surface.MORE_SYSTEM;
import static com.brouken.player.PlayerControlPolicy.Surface.TIME_ROW;
import static org.junit.Assert.assertEquals;

public class PlayerControlPolicyTest {

    @Test
    public void mediaPickersUseTheDonorBottomBarOrder() {
        assertEquals(MEDIA_BAR, PlayerControlPolicy.quality(true));
        assertEquals(MEDIA_BAR, PlayerControlPolicy.audio(true));
        assertEquals(MEDIA_BAR, PlayerControlPolicy.subtitles(true));
        assertEquals(MEDIA_BAR, PlayerControlPolicy.playlist(true));
        assertEquals(MEDIA_BAR, PlayerControlPolicy.repeat(true));
    }

    @Test
    public void unavailableMediaControlsAreHiddenInsteadOfMoved() {
        assertEquals(HIDDEN, PlayerControlPolicy.quality(false));
        assertEquals(HIDDEN, PlayerControlPolicy.audio(false));
        assertEquals(HIDDEN, PlayerControlPolicy.subtitles(false));
        assertEquals(HIDDEN, PlayerControlPolicy.playlist(false));
        assertEquals(HIDDEN, PlayerControlPolicy.repeat(false));
    }

    @Test
    public void displayAndLockPlacementDependsOnlyOnDeviceClass() {
        assertEquals(DISPLAY_CLUSTER, PlayerControlPolicy.displayMode(false, true));
        assertEquals(MEDIA_BAR, PlayerControlPolicy.displayMode(true, true));
        assertEquals(TIME_ROW, PlayerControlPolicy.lock(false, true));
        assertEquals(HIDDEN, PlayerControlPolicy.lock(true, true));
        assertEquals(HIDDEN, PlayerControlPolicy.displayMode(false, false));
        assertEquals(HIDDEN, PlayerControlPolicy.lock(false, false));
    }

    @Test
    public void rareActionsStayInSemanticMoreBands() {
        assertEquals(MORE_PLAYBACK, PlayerControlPolicy.speed(true));
        assertEquals(HIDDEN, PlayerControlPolicy.speed(false));
        assertEquals(MORE_SESSION, PlayerControlPolicy.together(true));
        assertEquals(HIDDEN, PlayerControlPolicy.together(false));
        assertEquals(MORE_SYSTEM, PlayerControlPolicy.settings());
    }
}
