package com.brouken.player;

import android.app.Application;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.provider.Settings;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.ScrollView;

import androidx.preference.PreferenceManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "uk-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UaHomeActivityTest {
    private static final int[] ACTIONS = {R.id.ua_home_open, R.id.ua_home_link,
            R.id.ua_home_room, R.id.ua_home_settings};

    @Before public void prepare() {
        final Application app = RuntimeEnvironment.getApplication();
        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear()
                .putBoolean("autoUpdate", false).commit();
        final ShadowPackageManager pm = Shadows.shadowOf(app.getPackageManager());
        pm.setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true);
        pm.setSystemFeature(PackageManager.FEATURE_TELEVISION, false);
        pm.setSystemFeature(PackageManager.FEATURE_LEANBACK, false);
        final Intent documents = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("video/*");
        final ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.android.documentsui";
        info.activityInfo.name = "com.android.documentsui.FilesActivity";
        pm.addResolveInfoForIntent(documents, info);
    }

    @Test public void portraitHasFourReachableActions() throws Exception {
        verifyLayout(360, 800, false, "phone-portrait");
    }

    @Test public void landscapeScrollsInsteadOfCroppingActions() throws Exception {
        verifyLayout(800, 360, false, "phone-landscape");
    }

    @Test public void televisionKeepsRemoteFocusOnTheUaPage() throws Exception {
        verifyLayout(960, 540, true, "tv");
    }

    private void verifyLayout(final int width, final int height, final boolean tv,
                              final String name) throws Exception {
        RuntimeEnvironment.setQualifiers("uk-w" + width + "dp-h" + height + "dp-"
                + (width > height ? "land" : "port") + "-mdpi");
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, tv);
        try (ActivityController<UaHomeActivity> controller = Robolectric
                .buildActivity(UaHomeActivity.class).setup()) {
            final UaHomeActivity activity = controller.get();
            assertEquals(tv, Utils.isTvBox(activity));
            final View root = activity.findViewById(R.id.ua_home_root);
            if (!tv) {
                ViewCompat.dispatchApplyWindowInsets(root, new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24)).build());
            }
            measure(root, width, height);
            // The width limit is applied after the root is laid out.
            measure(root, width, height);
            if (tv) assertTrue(activity.findViewById(R.id.ua_home_open).hasFocus());
            for (final int id : ACTIONS) {
                final View action = activity.findViewById(id);
                assertTrue(action.isClickable());
                assertTrue(action.isFocusable());
                assertTrue(action.getHeight() >= (tv ? 60 : 48));
                final Rect box = new Rect();
                assertTrue("Action hidden: " + id, action.getGlobalVisibleRect(box)
                        || ((ScrollView) activity.findViewById(R.id.ua_home_scroll))
                        .getChildAt(0).getHeight() > activity.findViewById(R.id.ua_home_scroll).getHeight());
                assertEquals("Action cropped at normal text size: " + id, action.getHeight(), box.height());
                assertTrue(action.getWidth() <= width - root.getPaddingLeft() - root.getPaddingRight());
            }
            final ScrollView scroll = activity.findViewById(R.id.ua_home_scroll);
            scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            final Rect last = new Rect();
            assertTrue(activity.findViewById(R.id.ua_home_settings).getGlobalVisibleRect(last));
            assertTrue(last.bottom <= height - root.getPaddingBottom());
            scroll.scrollTo(0, 0);
            render(root, width, height, name);
        }
    }

    @Test public void aboutIdentifiesUaProjectAndHasNoLogoPlate() {
        try (ActivityController<UaHomeActivity> controller = Robolectric
                .buildActivity(UaHomeActivity.class).setup()) {
            final UaHomeActivity activity = controller.get();
            assertEquals("https://github.com/Hlushok/lampaua-player",
                    activity.getString(R.string.about_github_url));
            assertEquals("Віталій Глушок (@Hlushok)", activity.getString(R.string.about_developer));
            final ViewGroup identity = (ViewGroup) LayoutInflater.from(activity)
                    .inflate(R.layout.preference_about_identity, null);
            final ViewGroup mark = (ViewGroup) identity.getChildAt(0);
            assertNull(mark.getBackground());
            assertEquals(1, mark.getChildCount());
        }
    }

    @Test public void openVideoLeadsToFilesAndSettingsStayOurSettings() {
        try (ActivityController<UaHomeActivity> controller = Robolectric
                .buildActivity(UaHomeActivity.class).setup()) {
            final UaHomeActivity activity = controller.get();
            activity.findViewById(R.id.ua_home_open).performClick();
            final Intent files = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(BrowserActivity.class.getName(), files.getComponent().getClassName());
            assertTrue(files.getBooleanExtra(BrowserActivity.EXTRA_OPEN_FILES, false));
            activity.findViewById(R.id.ua_home_settings).performClick();
            assertEquals(SettingsActivity.class.getName(), Shadows.shadowOf(activity)
                    .getNextStartedActivity().getComponent().getClassName());
        }
    }

    @Test @LooperMode(LooperMode.Mode.PAUSED)
    public void attentionPulseKeepsThreeCyclesAndCleansUpOnLeaving() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        ReflectionHelpers.callStaticMethod(ValueAnimator.class, "setDurationScale",
                ReflectionHelpers.ClassParameter.from(float.class, 1f));
        try (ActivityController<UaHomeActivity> controller = Robolectric
                .buildActivity(UaHomeActivity.class).create().start().resume()) {
            final UaHomeActivity activity = controller.get();
            final ObjectAnimator pulse = ReflectionHelpers.getField(activity, "pulse");
            assertNotNull(pulse);
            assertEquals(1400L, pulse.getDuration());
            assertEquals(5, pulse.getRepeatCount());
            assertEquals(ValueAnimator.REVERSE, pulse.getRepeatMode());
            pulse.setCurrentPlayTime(1400);
            final View button = activity.findViewById(R.id.ua_home_open);
            assertEquals(1.04f, button.getScaleX(), 0.001f);
            assertEquals(View.LAYER_TYPE_HARDWARE, button.getLayerType());
            controller.pause();
            assertNull(ReflectionHelpers.getField(activity, "pulse"));
            assertEquals(1f, button.getScaleX(), 0f);
            assertEquals(View.LAYER_TYPE_NONE, button.getLayerType());
            controller.resume();
            assertNull(ReflectionHelpers.getField(activity, "pulse"));
        }
    }

    @Test public void reducedMotionLeavesTheButtonStatic() {
        try (ActivityController<UaHomeActivity> controller = Robolectric
                .buildActivity(UaHomeActivity.class).setup()) {
            assertNull(ReflectionHelpers.getField(controller.get(), "pulse"));
        }
    }

    @Test public void allFilesAccessIsDeclaredInTheMergedAppManifest() throws Exception {
        final Application app = RuntimeEnvironment.getApplication();
        final String[] permissions = app.getPackageManager().getPackageInfo(app.getPackageName(),
                PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertTrue(java.util.Arrays.asList(permissions)
                .contains("android.permission.MANAGE_EXTERNAL_STORAGE"));
    }

    private static void measure(final View root, final int width, final int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static void render(final View root, final int width, final int height,
                               final String name) throws Exception {
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        final Path folder = Paths.get("build", "reports", "ua-home");
        Files.createDirectories(folder);
        try (OutputStream output = Files.newOutputStream(folder.resolve(name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
    }
}
