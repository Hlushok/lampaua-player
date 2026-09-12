package com.brouken.player;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResourceContractTest {

    private static Path resolve(String relativePath) {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) {
            path = Paths.get("app").resolve(relativePath);
        }
        return path;
    }

    private static String read(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolve(relativePath)), StandardCharsets.UTF_8);
    }

    @Test
    public void playerIdentityAndUpdaterRemainUaOwned() throws Exception {
        final String build = read("build.gradle");
        final String strings = read("src/main/res/values/strings.xml");
        final String updater = read("src/main/java/com/brouken/player/update/Updater.java");

        assertTrue(build.contains("applicationId \"com.lampaua.player\""));
        assertTrue(build.contains("versionName \"2.0.3\""));
        assertTrue(strings.contains("name=\"app_name\"") && strings.contains(">UA Player</string>"));
        assertTrue(updater.contains("Hlushok/lampaua-player/releases"));
        assertTrue(updater.contains("ua-player-update.apk"));
    }

    @Test
    public void subtitleTrackIdentitySurvivesPlayerRebuilds() throws Exception {
        final String subtitles = read("src/main/java/com/brouken/player/SubtitleUtils.java");
        final String player = read("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(subtitles.contains(".setId(uri.toString())"));
        assertTrue(player.contains("startingSubs.addAll(apiSubs)"));
        assertTrue(player.contains("itemBuilder.setSubtitleConfigurations(itemSubs)"));
        assertTrue(player.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)"));
    }

    @Test
    public void subtitleSettingsStayCompleteAndTranslateOnlyToUkrainian() throws Exception {
        final String preferences = read("src/main/res/xml/root_preferences.xml");
        final String translation = read("src/main/java/com/brouken/player/SubtitleTranslate.java");
        final String prefs = read("src/main/java/com/brouken/player/Prefs.java");

        assertTrue(preferences.contains("app:key=\"languageSubtitle\""));
        assertTrue(preferences.contains("app:key=\"subtitleSecondaryScreen\""));
        assertTrue(preferences.contains("app:key=\"subtitleSearchScreen\""));
        assertTrue(preferences.contains("app:key=\"subtitleAppearance\""));
        assertTrue(preferences.contains("app:key=\"subtitleTranslateOn\""));
        assertFalse(preferences.contains("app:key=\"languageSubtitleTranslate\""));
        assertTrue(translation.contains("TARGET_LANGUAGE = \"ukr\""));
        assertTrue(prefs.contains("remove(PREF_KEY_LANGUAGE_SUBTITLE_TRANSLATE)"));
    }

    @Test
    public void panelsDismissOutsideAndTvHeaderHasOnlyUsableControls() throws Exception {
        final String player = read("src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(player.contains("playlistDialog.setCanceledOnTouchOutside(true)"));
        assertTrue(player.contains("qualityDialog.setCanceledOnTouchOutside(true)"));
        assertTrue(player.contains("menuDialog.setCanceledOnTouchOutside(true)"));
        assertTrue(player.contains("final LinearLayout displayParent = headerButtons"));
        assertTrue(player.contains("if (!isTvBox) {\n            displayParent.addView(buttonRotation)"));
        assertTrue(player.contains("endsAtView.setVisibility(View.VISIBLE)"));
    }

    @Test
    public void launcherLogoHasNoSquareUnderlay() throws Exception {
        final String adaptiveIcon = read("src/main/res/mipmap-anydpi-v26/ic_launcher.xml");
        final String playerLayout = read("src/main/res/layout/activity_player.xml");

        assertTrue(adaptiveIcon.contains("@android:color/transparent"));
        assertTrue(adaptiveIcon.contains("@drawable/ua_player_launcher_icon"));
        assertTrue(playerLayout.contains("android:src=\"@drawable/ua_player_icon\""));
        assertTrue(Files.isRegularFile(resolve(
                "src/main/res/drawable-nodpi/ua_player_launcher_icon.png")));
    }

    @Test
    public void telemetryIsDisabledInUaBuilds() throws Exception {
        final String build = read("build.gradle");
        final String app = read("src/main/java/com/brouken/player/App.java");

        assertTrue(build.contains("ENABLE_CRASH_REPORTING\", \"false\""));
        assertTrue(build.contains("SENTRY_DSN\", '\"\"'"));
        assertTrue(app.contains("if (!BuildConfig.ENABLE_CRASH_REPORTING"));
    }
}
