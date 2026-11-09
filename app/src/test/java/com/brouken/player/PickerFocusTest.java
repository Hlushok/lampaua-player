package com.brouken.player;

import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.metadata.MetadataRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "uk-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class PickerFocusTest {
    public static class FocusActivity extends PlayerActivity {
        @Override public void initializePlayer() { /* Exercise UI without network or codecs. */ }
    }

    @Before public void reset() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().putBoolean("autoUpdate", false).commit();
    }

    private ActivityController<FocusActivity> screen() {
        return Robolectric.buildActivity(FocusActivity.class,
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/video.mkv"))).setup().windowFocusChanged(true);
    }

    private View focus(FocusActivity activity, String field) {
        PlayerActivity.isTvBox = true;
        activity.ui = UiMetrics.of(activity, true);
        View view = ReflectionHelpers.getField(activity, field);
        view.setVisibility(View.VISIBLE); view.setEnabled(true);
        activity.playerView.showController();
        assertTrue(view.requestFocus());
        return view;
    }

    private Dialog panel(FocusActivity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(new TextView(activity));
        ReflectionHelpers.callInstanceMethod(activity, "showPickerDialog", ClassParameter.from(Dialog.class, dialog));
        return dialog;
    }

    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS); }

    @Test public void closingAPickerRestoresItsOpeningControl() {
        try (ActivityController<FocusActivity> controller = screen()) {
            FocusActivity activity = controller.get();
            View opener = focus(activity, "buttonQuality");
            Dialog dialog = panel(activity);
            dialog.dismiss(); idle();
            assertTrue(activity.playerView.isControllerFullyVisible());
            assertTrue(opener.hasFocus());
        }
    }

    @Test public void movingBetweenPanelsDoesNotRestoreControlsUntilTheLastOneCloses() {
        try (ActivityController<FocusActivity> controller = screen()) {
            FocusActivity activity = controller.get();
            View opener = focus(activity, "buttonAudio");
            Dialog first = panel(activity);
            first.dismiss();
            Dialog second = panel(activity);
            idle();
            assertTrue(second.isShowing());
            assertFalse(activity.playerView.isControllerFullyVisible());
            second.dismiss(); idle();
            assertTrue(opener.hasFocus());
        }
    }

    @Test public void backgroundDismissalDoesNotRaiseTheControls() {
        try (ActivityController<FocusActivity> controller = screen()) {
            FocusActivity activity = controller.get();
            focus(activity, "buttonQuality");
            Dialog dialog = panel(activity);
            controller.pause().stop();
            dialog.dismiss(); idle();
            assertFalse(activity.playerView.isControllerFullyVisible());
        }
    }

    @Test public void aNewLaunchInvalidatesAnOldPickersDeferredReturn() {
        try (ActivityController<FocusActivity> controller = screen()) {
            FocusActivity activity = controller.get();
            focus(activity, "buttonQuality");
            panel(activity).dismiss();
            Intent next = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/next.mkv"));
            ReflectionHelpers.callInstanceMethod(activity, "onNewIntent", ClassParameter.from(Intent.class, next));
            idle();
            assertNull(ReflectionHelpers.getField(activity, "pickerReturnFocus"));
            assertEquals(next.getData(), activity.mPrefs.mediaUri);
            assertFalse(activity.playerView.isControllerFullyVisible());
        }
    }

    @Test public void backFromSoundReturnsToMoreOnTheSoundRow() {
        try (ActivityController<FocusActivity> controller = screen()) {
            FocusActivity activity = controller.get();
            View opener = focus(activity, "buttonMore");
            ExoPlayer engine = new ExoPlayer.Builder(activity).setRenderersFactory(
                    (handler, video, audio, text, metadata) -> new Renderer[]{new MetadataRenderer(metadata, handler.getLooper())}).build();
            PlayerActivity.player = engine;
            try {
                ReflectionHelpers.callInstanceMethod(activity, "showMoreMenu"); idle();
                Dialog more = Dialogs.openMenu();
                View row = row(more, activity.getString(R.string.sound_row));
                assertNotNull(row); row.performClick(); idle();
                Dialog sound = ReflectionHelpers.getField(activity, "soundDialog");
                assertTrue(sound.isShowing());
                sound.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)); idle();
                Dialog returned = Dialogs.openMenu();
                assertTrue(returned.isShowing());
                assertTrue(row(returned, activity.getString(R.string.sound_row)).hasFocus());
                returned.dismiss(); idle();
                assertTrue(opener.hasFocus());
            } finally { PlayerActivity.player = null; engine.release(); }
        }
    }

    private View row(Dialog dialog, String title) {
        ArrayList<View> found = new ArrayList<>();
        dialog.getWindow().getDecorView().findViewsWithText(found, title, View.FIND_VIEWS_WITH_TEXT);
        for (View text : found) {
            View candidate = text;
            while (!candidate.isClickable() && candidate.getParent() instanceof View) candidate = (View) candidate.getParent();
            if (candidate.isClickable()) return candidate;
        }
        return null;
    }
}
