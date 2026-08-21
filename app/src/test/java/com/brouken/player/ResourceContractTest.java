package com.brouken.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class ResourceContractTest {

    private static Path projectPath(String relativePath) {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) {
            path = Paths.get("app").resolve(relativePath);
        }
        return path;
    }

    private static String readProjectFile(String relativePath) throws Exception {
        Path path = projectPath(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int pngInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder text = new StringBuilder(digest.length * 2);
        for (byte value : digest) text.append(String.format("%02x", value & 0xff));
        return text.toString();
    }

    @Test
    public void skipLabelsStayUkrainian() throws Exception {
        String xml = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(xml.contains("name=\"skip_action\">Пропустити</string>"));
        assertTrue(xml.contains("name=\"skip_available_in\">Пропуск через %1$d</string>"));
        assertFalse(xml.contains("SideSheetBehavior"));
    }

    @Test
    public void tvBannerUsesUaPalette() throws Exception {
        byte[] image = Files.readAllBytes(projectPath(
                "src/main/res/mipmap-xhdpi/banner.png"));

        assertTrue(image.length > 8_000);
        assertEquals(320, pngInt(image, 16));
        assertEquals(180, pngInt(image, 20));
        assertEquals("67cff9977c881c0fd1a31060455c5841531f2188ae78cecdd628c90d0e3c4d15",
                sha256(image));
    }

    @Test
    public void bottomControlsHaveStrongTvFocusResources() throws Exception {
        String background = readProjectFile(
                "src/main/res/drawable/ua_tv_control_background.xml");
        String animator = readProjectFile(
                "src/main/res/animator/ua_tv_control_focus.xml");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String controls = readProjectFile("src/main/res/layout/controls.xml");

        assertTrue(background.contains("state_focused=\"true\""));
        assertTrue(background.contains("android:width=\"3dp\""));
        assertTrue(background.contains("@color/ua_gold"));
        assertTrue(animator.contains("android:valueTo=\"1.1\""));
        assertTrue(activity.contains("styleTvBottomControls(controls)"));
        assertTrue(controls.contains("android:clipChildren=\"false\""));
    }

    @Test
    public void updaterDownloadsInsidePlayerAndUsesFileProvider() throws Exception {
        String updater = readProjectFile(
                "src/main/java/com/brouken/player/update/Updater.java");
        String updateUi = readProjectFile(
                "src/main/java/com/brouken/player/update/UpdateUi.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(updater.contains("CLIENT.newCall(request).execute()"));
        assertTrue(updater.contains("context.getCacheDir()"));
        assertTrue(updater.contains("FileProvider.getUriForFile"));
        assertTrue(updater.contains("FLAG_GRANT_READ_URI_PERMISSION"));
        assertTrue(updateUi.contains("Updater.downloadApk"));
        assertTrue(ukrainian.contains(">Завантажити й установити</string>"));
        assertFalse(updater.contains("Intent.createChooser"));
        assertFalse(updateUi.contains("startActivity(new Intent(Intent.ACTION_VIEW"));
    }

    @Test
    public void tvBackHintStaysLocalized() throws Exception {
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(ukrainian.contains(
                "name=\"press_back_again\">Натисніть «Назад» ще раз для виходу</string>"));
    }

    @Test
    public void lockHintAutoHidesAfterThreeSeconds() throws Exception {
        String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String swipe = readProjectFile("src/main/java/com/brouken/player/SwipeToUnlockView.java");

        assertTrue(activity.contains("SWIPE_UNLOCK_TIMEOUT_MS = 3_000L"));
        assertTrue(activity.contains("postDelayed(swipeHider, SWIPE_UNLOCK_TIMEOUT_MS)"));
        assertTrue(swipe.contains("setOnStartTouchingListener"));
        assertTrue(swipe.contains("setOnStopTouchingListener"));
    }

    @Test
    public void upstreamTvInteractionFixesStayIntegrated() throws Exception {
        String customView = readProjectFile(
                "src/main/java/com/brouken/player/CustomPlayerView.java");
        String timeBar = readProjectFile(
                "src/main/java/com/brouken/player/CustomDefaultTimeBar.java");
        String settings = readProjectFile(
                "src/main/java/com/brouken/player/SettingsActivity.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String manifest = readProjectFile("src/main/AndroidManifest.xml");

        assertTrue(customView.contains("seekGesture(final long position)"));
        assertTrue(customView.contains("player.seekTo(seekStart + seekChange)"));
        assertTrue(timeBar.contains("drawBand(Canvas canvas, int left, int right)"));
        assertTrue(settings.contains("calculateExtraLayoutSpace"));
        assertTrue(activity.contains("parkFocusOnLoadingRing"));
        assertTrue(manifest.contains("android:autoRemoveFromRecents=\"true\""));
        assertTrue(manifest.contains("android:launchMode=\"singleTask\""));
        assertTrue(activity.contains("com.mxtech.intent.result.VIEW"));
    }

    @Test
    public void frameRateSwitchCannotHoldPlaybackForever() throws Exception {
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String utils = readProjectFile("src/main/java/com/brouken/player/Utils.java");

        assertTrue(activity.contains("FRAME_RATE_SWITCH_TIMEOUT_MS = 1_500L"));
        assertTrue(activity.contains(
                "postDelayed(frameRateGiveUpRunnable,"));
        assertTrue(activity.contains("videoFrameRate()"));
        assertTrue(utils.contains("FrameRatePolicy.bestRate"));
    }

    @Test
    public void watchTogetherUiKeepsPrivacyAndLocalQrContracts() throws Exception {
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");
        String build = readProjectFile("build.gradle");
        String manifest = readProjectFile("src/main/AndroidManifest.xml");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(ukrainian.contains(
                "name=\"together_title\">Дивитися разом</string>"));
        assertTrue(ukrainian.contains("name=\"together_create\""));
        assertTrue(ukrainian.contains("name=\"together_join\""));
        assertTrue(ukrainian.contains("name=\"together_share\""));
        assertTrue(ukrainian.contains("name=\"together_leave\""));
        assertTrue(ukrainian.contains("name=\"together_public_needs_password\""));
        assertTrue(ukrainian.contains("name=\"pref_together_relay_summary\""));
        assertTrue(ukrainian.contains("name=\"together_qr_hint\""));
        assertTrue(build.contains("com.google.zxing:core:3.5.3"));
        assertTrue(manifest.contains("android.intent.action.SEND"));
        assertTrue(activity.contains("QRCodeWriter"));
        assertTrue(activity.contains("extras.remove(API_RETURN_RESULT)"));
        assertTrue(activity.contains("extras.remove(LampaPlaylist.EXTRA_PLAYBACK_RESULTS)"));
        assertTrue(activity.contains("SessionCodec.toJson(extras)"));
        assertTrue(activity.contains("SessionCodec.toBundle(encodedExtras)"));
        assertTrue(activity.contains("together.suspend()"));
        assertTrue(activity.contains("together.resume()"));
        assertTrue(activity.contains("syncRoomPlaylistStep(outgoingEnded)"));
        assertFalse(build.contains("io.sentry"));
        assertFalse(activity.contains("termbin.com"));
        assertFalse(activity.contains("api.qrserver.com"));
    }

    @Test
    public void subtitleLanguageAndAppearanceStayInsideThePlayer() throws Exception {
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String settings = readProjectFile(
                "src/main/java/com/brouken/player/SettingsActivity.java");

        assertTrue(preferences.contains("app:key=\"languageSubtitle\""));
        assertTrue(preferences.contains("app:key=\"subtitleAppearance\""));
        assertTrue(preferences.contains("app:key=\"subtitleScale\""));
        assertTrue(preferences.contains("app:key=\"subtitleTextColor\""));
        assertTrue(preferences.contains("app:key=\"subtitleBackground\""));
        assertTrue(preferences.contains("app:key=\"subtitleEdge\""));
        assertFalse(preferences.contains("android.settings.CAPTIONING_SETTINGS"));
        assertTrue(activity.contains("setPreferredTextLanguages"));
        assertTrue(activity.contains("openAppSettings(\"languageSubtitle\")"));
        assertTrue(activity.contains("mPrefs.subtitleScale"));
        assertTrue(prefs.contains("getLanguageSubtitle"));
        assertTrue(settings.contains("EXTRA_SCROLL_TO"));
        assertTrue(settings.contains("openAtPreference"));
    }

    @Test
    public void onlineSubtitleSearchKeepsSourceAndPrivacyContracts() throws Exception {
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String search = readProjectFile("src/main/java/com/brouken/player/SubtitleSearch.java");
        String openSubtitles = readProjectFile(
                "src/main/java/com/brouken/player/OpenSubtitles.java");
        String fetcher = readProjectFile(
                "src/main/java/com/brouken/player/SubtitleFetcher.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(preferences.contains("app:key=\"subtitleSources\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceRest\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceStremio\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceShegu\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceOpenSubtitles\""));
        assertTrue(prefs.contains("public boolean subtitleSearch = false"));
        assertTrue(search.contains("rest.opensubtitles.org"));
        assertTrue(search.contains("opensubtitles-v3.strem.io"));
        assertTrue(search.contains("subtitles.shegu.st"));
        assertFalse(search.contains("SegmentFinder"));
        assertTrue(openSubtitles.contains("header(\"Api-Key\", KEY)"));
        assertTrue(openSubtitles.contains("UA-Player/"));
        assertFalse(openSubtitles.contains("JustPlayer"));
        assertTrue(fetcher.contains("Uri fetchNow()"));
        assertTrue(activity.contains("maybeSearchSubtitlesOnline(tracks)"));
        assertTrue(activity.contains("addSubtitleTrack(Uri subtitleUri)"));
    }

    @Test
    public void containerMetadataIsBoundedAndScopedToTheCurrentUri() throws Exception {
        String source = readProjectFile(
                "src/main/java/com/brouken/player/TrackNameParsingDataSource.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(source.contains("onMetadataParsed(Uri originalUri"));
        assertTrue(source.contains("isMetadataParsed(Uri originalUri)"));
        assertTrue(source.contains("onContentLength(Uri originalUri, long length)"));
        assertFalse(source.contains("PipedInputStream"));
        assertTrue(activity.contains("currentContainerTracks()"));
        assertTrue(activity.contains("containerFrameRate()"));
        assertTrue(activity.contains("contentLengths"));
        assertTrue(activity.contains("PlaybackStatistics.averageBitrate"));
        assertTrue(activity.contains("Utils.handleFrameRate(PlayerActivity.this, rate)"));
    }

    @Test
    public void dolbyVisionProfile7ConversionStaysFailOpen() throws Exception {
        String build = readProjectFile("build.gradle");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(build.contains("com.suyashbelekar:exoplayerhdrutils:0.3.0"));
        assertTrue(build.contains(
                "exclude group: \"androidx.media3\", module: \"media3-exoplayer\""));
        assertTrue(build.contains(
                "exclude group: \"androidx.core\", module: \"core-ktx\""));
        assertTrue(activity.contains(
                "!mPrefs.mapDV7ToHevc && !forceHevcForDolbyVision"));
        assertTrue(activity.contains("new Dv7Converter"));
        assertFalse(build.contains("io.sentry"));
    }

    @Test
    public void remainingTimeAndHeldSpeedStayBoundedAndRoomSafe() throws Exception {
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String customView = readProjectFile(
                "src/main/java/com/brouken/player/CustomPlayerView.java");
        String settings = readProjectFile(
                "src/main/java/com/brouken/player/SettingsActivity.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(preferences.contains("app:key=\"timeRemaining\""));
        assertTrue(preferences.contains("app:key=\"holdSpeed\""));
        assertTrue(prefs.contains("public boolean timeRemaining = false"));
        assertTrue(prefs.contains("public boolean holdSpeed = true"));
        assertTrue(activity.contains("setProgressUpdateListener"));
        assertTrue(activity.contains("mPrefs.timeRemaining"));
        assertTrue(activity.contains("isSpeedBoosting()"));
        assertTrue(activity.contains("isSeekGesture()"));
        assertTrue(customView.contains("REWIND_TICK_MS = 100"));
        assertTrue(customView.contains("HoldSpeedPolicy.evaluate"));
        assertTrue(customView.contains("SeekParameters.DEFAULT"));
        assertTrue(settings.contains("findPreference(\"holdSpeed\")"));
        assertTrue(ukrainian.contains("name=\"pref_time_remaining\""));
        assertTrue(ukrainian.contains("name=\"pref_hold_speed\""));
    }

    @Test
    public void playbackReportsStayLocalSanitizedAndShareOnlyOnDemand() throws Exception {
        String manifest = readProjectFile("src/main/AndroidManifest.xml");
        String reportActivity = readProjectFile(
                "src/main/java/com/brouken/player/PlaybackReportActivity.java");
        String diagnostic = readProjectFile(
                "src/main/java/com/brouken/player/DiagnosticReport.java");
        String player = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String readme = readProjectFile("../README.md");

        assertTrue(manifest.contains("android:name=\".PlaybackReportActivity\""));
        assertTrue(manifest.contains("android:excludeFromRecents=\"true\""));
        assertTrue(manifest.contains("android:exported=\"false\""));
        assertTrue(reportActivity.contains("static void show(Context context"));
        assertTrue(reportActivity.contains("ClipboardManager"));
        assertTrue(reportActivity.contains("Intent.ACTION_SEND"));
        assertTrue(reportActivity.contains("R.id.report_close"));
        assertTrue(diagnostic.contains("sanitizeNetworkUri"));
        assertTrue(diagnostic.contains("sanitizeText"));
        assertFalse(reportActivity.contains("HttpURLConnection"));
        assertFalse(reportActivity.contains("Socket"));
        assertFalse(reportActivity.contains("termbin.com"));
        assertFalse(reportActivity.contains("qrserver.com"));
        assertFalse(reportActivity.contains("Sentry"));
        assertTrue(player.contains("PlaybackReportActivity.show"));
        assertTrue(player.contains("fadeAuxiliaryChrome"));
        assertTrue(player.contains("fadeAuxiliaryChrome(statsView"));
        assertTrue(player.contains("fadeAuxiliaryChrome(roomPill"));
        assertTrue(preferences.contains("app:key=\"showStats\""));
        assertTrue(readme.contains("Oleksandr Zhyzhchenko"));
        assertTrue(readme.contains("just-plus-player/just-plus-player"));
    }

    @Test
    public void legacyAndroidTrustsTheCurrentLetsEncryptRoot() throws Exception {
        String config = readProjectFile("src/main/res/xml/network_security_config.xml");
        Path certificatePath = projectPath("src/main/res/raw/isrg_root_x1.pem");

        assertTrue(config.contains("<certificates src=\"@raw/isrg_root_x1\" />"));
        assertTrue(Files.isRegularFile(certificatePath));
        Certificate certificate;
        try (java.io.InputStream input = Files.newInputStream(certificatePath)) {
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        assertEquals("96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6",
                sha256(certificate.getEncoded()));
    }

    @Test
    public void transientNetworkReadsKeepTheCurrentPlayerInstance() throws Exception {
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(activity.contains("case REPREPARE_SOURCE:"));
        assertTrue(activity.contains("sourceRetryRunnable"));
        assertTrue(activity.contains("HttpDataSource.InvalidResponseCodeException"));
        assertTrue(activity.contains("player.prepare();"));
    }

    @Test
    public void subtitleOffsetAndHotAttachStayIntegrated() throws Exception {
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");

        assertTrue(activity.contains("buildTextRenderers(Context context, TextOutput output"));
        assertTrue(activity.contains("new SubtitleOffset(output, outputLooper"));
        assertTrue(activity.contains("paintSubtitle(subtitleUri)"));
        assertTrue(activity.contains("SubtitleTimeline.load"));
        assertTrue(activity.contains("subtitleOffset.setTimeline"));
        assertTrue(activity.contains("R.drawable.ic_subtitle_offset_24dp"));
        assertTrue(activity.contains("showSubtitleDialog()"));
        assertTrue(activity.contains("attachSubtitleTrack(subtitleUri)"));
        assertFalse(activity.contains("button_skip_offset"));
        assertFalse(activity.contains("skipOffsetSec"));
    }

    @Test
    public void onlineSubtitleTranslationStaysUkrainianAndPrivate() throws Exception {
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String transport = readProjectFile(
                "src/main/java/com/brouken/player/GoogleSubtitleTranslationTransport.java");
        String translator = readProjectFile(
                "src/main/java/com/brouken/player/UkrainianSubtitleTranslator.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(preferences.contains("app:key=\"subtitleAutoTranslateUkrainian\""));
        assertTrue(preferences.contains("app:defaultValue=\"true\""));
        assertTrue(prefs.contains("public boolean subtitleSearch = false"));
        assertTrue(prefs.contains("public boolean subtitleAutoTranslateUkrainian = true"));
        assertTrue(activity.contains("UkrainianSubtitlePolicy.directLanguages"));
        assertTrue(activity.contains("UkrainianSubtitlePolicy.fallbackLanguages"));
        assertTrue(activity.contains("auto-ukr"));
        assertTrue(activity.contains("if (!directAnswered.get())"));
        assertTrue(transport.contains(
                "https://translate.googleapis.com/translate_a/single"));
        assertTrue(transport.contains("new FormBody.Builder()"));
        assertTrue(transport.contains(".add(\"tl\", UkrainianSubtitlePolicy.targetIso2())"));
        assertFalse(transport.contains("MediaId"));
        assertFalse(transport.contains("apiTitle"));
        assertFalse(transport.contains("imdb"));
        assertFalse(transport.contains("tmdb"));
        assertFalse(transport.contains("HttpLoggingInterceptor"));
        assertTrue(translator.contains("target.getName() + \".tmp\""));
        assertTrue(translator.contains("temporary.renameTo(target)"));
        assertTrue(ukrainian.contains("name=\"subtitle_translate_progress\""));
        assertTrue(ukrainian.contains("name=\"subtitle_translate_success\""));
        assertTrue(ukrainian.contains("name=\"subtitle_translate_failed\""));

        String search = readProjectFile(
                "src/main/java/com/brouken/player/SubtitleSearch.java");
        String openSubtitles = readProjectFile(
                "src/main/java/com/brouken/player/OpenSubtitles.java");
        assertTrue(search.contains(
                "if (response.isSuccessful() && body != null) answered.set(true)"));
        assertTrue(openSubtitles.contains(
                "if (response.isSuccessful() && body != null && answered != null)"));
    }
}
