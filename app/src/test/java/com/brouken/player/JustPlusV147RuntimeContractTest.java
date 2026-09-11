package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class JustPlusV147RuntimeContractTest {
    private static String readProjectFile(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("app").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue("Missing start marker: " + start, from >= 0);
        assertTrue("Missing end marker: " + end, to > from);
        return source.substring(from, to);
    }

    @Test public void endedMediaAlwaysPersistsTheBeginning() throws Exception {
        String source = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String save = section(source, "private void savePlayer()", "private void saveRecoveryPosition()");
        assertTrue(save.contains("player.getPlaybackState() == Player.STATE_ENDED\n"
                + "                            ? 0 : player.getCurrentPosition()"));
    }

    @Test public void passthroughFailureKeepsTheExistingVideoDecoderWhenPossible() throws Exception {
        String source = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String recovery = section(source, "private boolean recoverFromAudioTrackFailure",
                "private class PlayerListener");
        assertTrue(recovery.contains("mPrefs.decoderPriority\n"
                + "                == DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF"));
        assertTrue(recovery.contains("pendingStuckRecovery = true"));
        assertTrue(recovery.contains("player.prepare()"));
        assertTrue(source.contains("audioRecoveryState.resetForNewPlayback()"));
        assertTrue(source.contains("playerView.postDelayed(rebufferArmRunnable, REBUFFER_ARM_MS)"));
    }

    @Test public void lockedTvInputAndOrientationFollowTheDonorContract() throws Exception {
        String source = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String dispatch = section(source, "public boolean dispatchKeyEvent", "public boolean onGenericMotionEvent");
        assertTrue(dispatch.indexOf("if (locked && event.getKeyCode() != KeyEvent.KEYCODE_BACK)")
                < dispatch.indexOf("isSkipActionEnabled()"));
        String keyDown = section(source, "public boolean onKeyDown", "public boolean onKeyUp");
        assertTrue(keyDown.contains("focusedUp.focusSearch(View.FOCUS_UP)"));
        assertTrue(keyDown.contains("focusedDown.focusSearch(View.FOCUS_DOWN)"));
        String lock = section(source, "private void lockScreen()",
                "private void updatebuttonAspectRatioIcon");
        assertTrue(lock.contains("SCREEN_ORIENTATION_REVERSE_PORTRAIT"));
        assertTrue(lock.contains("SCREEN_ORIENTATION_REVERSE_LANDSCAPE"));
        assertTrue(lock.contains("Utils.setOrientation(this, mPrefs.orientation)"));
    }

    @Test public void pickerRotationDebtSurvivesProcessDeath() throws Exception {
        String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        assertTrue(prefs.contains("PREF_KEY_RESTORE_AUTO_ROTATE"));
        assertTrue(prefs.contains("setRestoreAutoRotate"));
        assertTrue(prefs.contains("sharedPreferencesEditor.commit()"));
        assertTrue(section(activity, "public void onResume()", "public void onWindowFocusChanged")
                .contains("restoreRotationLock()"));
        assertTrue(section(activity, "private void enableRotation()", "boolean useMediaStore()")
                .contains("mPrefs.setRestoreAutoRotate(true)"));
    }

    @Test public void donorScrimsCoverTheFrameAndBottomControls() throws Exception {
        String layout = readProjectFile("src/main/res/layout/exo_player_control_view.xml");
        String colors = readProjectFile("src/main/res/values/colors.xml");
        String bottom = readProjectFile("src/main/res/drawable/scrim_bottom.xml");
        assertTrue(layout.contains("android:background=\"@color/controls_scrim\""));
        assertTrue(layout.contains("android:background=\"@drawable/scrim_bottom\""));
        assertTrue(colors.contains("name=\"controls_scrim\""));
        assertTrue(bottom.contains("<gradient"));
    }
}
