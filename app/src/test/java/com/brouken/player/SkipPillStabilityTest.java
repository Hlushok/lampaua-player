package com.brouken.player;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.preference.PreferenceManager;
import com.brouken.player.skip.SkipManager;
import com.brouken.player.skip.SkipSegment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SkipPillStabilityTest {
    public static class Screen extends PlayerActivity { @Override public void initializePlayer() { } }
    private static class CountingButton extends Button {
        int fadesFromZero;
        CountingButton(Screen activity) { super(activity); }
        @Override public void setAlpha(float alpha) { if (alpha == 0) fadesFromZero++; super.setAlpha(alpha); }
    }
    @Before public void reset() {
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit()
                .clear().putBoolean("autoUpdate", false).commit();
    }
    private ActivityController<Screen> screen() {
        return Robolectric.buildActivity(Screen.class,
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/video"))).setup();
    }
    private SkipSegment prepare(Screen activity, String mode, long position) {
        PlayerActivity.isTvBox = false;
        activity.mPrefs.skipEnabled = true; activity.mPrefs.skipMode = mode;
        CountingButton button = new CountingButton(activity); button.setVisibility(View.GONE); activity.buttonSkip = button;
        ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(), new Class<?>[]{ExoPlayer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentPosition")) return position;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
        ReflectionHelpers.setField(activity, "player", player);
        SkipSegment segment = new SkipSegment(10, 20, SkipSegment.Type.SKIP,
                SkipSegment.Category.INTRO, SkipSegment.CoordBase.ABSOLUTE, 100);
        SkipManager manager = new SkipManager(); manager.setSource(duration -> Collections.singletonList(segment)); manager.rebuild(100);
        ReflectionHelpers.setField(activity, "skipManager", manager);
        ReflectionHelpers.setField(activity, "controllerVisible", false);
        return segment;
    }
    @Test public void upcomingAutomaticSkipDoesNotRestartItsFadeEveryPoll() {
        try (ActivityController<Screen> controller = screen()) {
            Screen activity = controller.get(); prepare(activity, Prefs.SKIP_MODE_AUTO, 8_000);
            for (int i = 0; i < 8; i++) ReflectionHelpers.callInstanceMethod(activity, "skipTick");
            assertEquals(1, ((CountingButton) activity.buttonSkip).fadesFromZero);
            assertEquals(20_000L, (long) ReflectionHelpers.getField(activity, "skipHeadsUpEndMs"));
            assertEquals("CANCEL", ReflectionHelpers.getField(activity, "skipPill").toString());
        }
    }
    @Test public void openingControlsDuringTheBriefOfferCancelsItsOldHideDeadline() {
        try (ActivityController<Screen> controller = screen()) {
            Screen activity = controller.get(); prepare(activity, Prefs.SKIP_MODE_BRIEF, 12_000);
            ReflectionHelpers.callInstanceMethod(activity, "skipTick");
            ReflectionHelpers.setField(activity, "controllerVisible", true);
            ReflectionHelpers.callInstanceMethod(activity, "skipTick");
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS);
            assertEquals(View.VISIBLE, activity.buttonSkip.getVisibility());
            assertEquals("SKIP", ReflectionHelpers.getField(activity, "skipPill").toString());
        }
    }

    @Test public void aSecondSkipGetsItsOwnUndoDeadlineWithoutAnOldTimerHidingIt() {
        try (ActivityController<Screen> controller = screen()) {
            Screen activity = controller.get(); prepare(activity, Prefs.SKIP_MODE_AUTO, 12_000);
            activity.mPrefs.skipUndo = Prefs.SKIP_UNDO_ALL;
            ReflectionHelpers.setField(activity, "skipUndoFromMs", 12_000L);
            ReflectionHelpers.setField(activity, "skipUndoToMs", 20_000L);
            ReflectionHelpers.callInstanceMethod(activity, "showSkipNotification",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
            ReflectionHelpers.callInstanceMethod(activity, "showSkipNotification",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
            assertEquals(View.VISIBLE, activity.buttonSkip.getVisibility());
            assertEquals("UNDO", ReflectionHelpers.getField(activity, "skipPill").toString());
        }
    }
}
