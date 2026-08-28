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

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, Math.max(0, from + start.length()));
        if (from < 0 || to < 0 || to <= from) return "";
        return source.substring(from, to);
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
        String preview = readProjectFile(
                "src/main/java/com/brouken/player/SubtitlePreviewPreference.java");
        String previewLayout = readProjectFile(
                "src/main/res/layout/preference_subtitle_preview.xml");

        assertTrue(preferences.contains("app:key=\"languageSubtitle\""));
        assertTrue(preferences.contains("app:key=\"subtitleAppearance\""));
        assertTrue(preferences.contains("app:key=\"subtitleScale\""));
        assertTrue(preferences.contains("app:key=\"subtitleTextColor\""));
        assertTrue(preferences.contains("app:key=\"subtitleBackground\""));
        assertTrue(preferences.contains("app:key=\"subtitleEdge\""));
        assertTrue(preferences.contains("com.brouken.player.SubtitlePreviewPreference"));
        assertTrue(preferences.contains("app:key=\"subtitleStylePreview\""));
        assertFalse(preferences.contains("android.settings.CAPTIONING_SETTINGS"));
        assertTrue(activity.contains("setPreferredTextLanguages"));
        assertTrue(activity.contains("openAppSettings(\"languageSubtitle\")"));
        assertTrue(activity.contains("mPrefs.subtitleScale"));
        assertTrue(prefs.contains("getLanguageSubtitle"));
        assertTrue(settings.contains("EXTRA_SCROLL_TO"));
        assertTrue(settings.contains("openAtPreference"));
        assertTrue(settings.contains("refreshSubtitlePreview"));
        assertTrue(preview.contains("CaptionStyleCompat"));
        assertTrue(preview.contains("setApplyEmbeddedStyles"));
        assertTrue(previewLayout.contains("@+id/subtitle_preview"));
    }

    @Test
    public void onlineSubtitleSearchUsesDonorScreensAndDebugOnlyDiagnostics() throws Exception {
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String settings = readProjectFile(
                "src/main/java/com/brouken/player/SettingsActivity.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");
        String search = readProjectFile("src/main/java/com/brouken/player/SubtitleSearch.java");
        String openSubtitles = readProjectFile(
                "src/main/java/com/brouken/player/OpenSubtitles.java");
        String fetcher = readProjectFile(
                "src/main/java/com/brouken/player/SubtitleFetcher.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");

        int primary = preferences.indexOf("app:key=\"languageSubtitle\"");
        int secondary = preferences.indexOf("app:key=\"subtitleSecondaryScreen\"");
        int online = preferences.indexOf("app:key=\"subtitleSearchScreen\"");
        int appearance = preferences.indexOf("app:key=\"subtitleAppearance\"");
        assertTrue(primary >= 0 && primary < secondary);
        assertTrue(secondary < online);
        assertTrue(online < appearance);
        assertTrue(preferences.contains("app:key=\"subtitleTranslateOn\""));
        assertFalse(preferences.contains("app:key=\"languageSubtitleTranslate\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceRest\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceStremio\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceShegu\""));
        assertTrue(preferences.contains("app:key=\"subtitleSourceOpenSubtitles\""));
        assertTrue(settings.contains("sources.setVisible(BuildConfig.DEBUG)"));
        assertTrue(settings.contains("translationBackendsVisible("));
        assertTrue(ukrainian.contains(
                "name=\"pref_subtitle_translate\">Автопереклад українською"));
        assertTrue(prefs.contains("public boolean subtitleSearch = false"));
        assertTrue(prefs.contains("subtitleSourceOpenSubtitles = true"));
        assertTrue(prefs.contains("subtitleSourceShegu = true"));
        assertTrue(prefs.contains("subtitleSourceStremio = true"));
        assertTrue(prefs.contains("subtitleSourceRest = true"));
        assertTrue(search.contains("rest.opensubtitles.org"));
        assertTrue(search.contains("opensubtitles-v3.strem.io"));
        assertTrue(search.contains("subtitles.shegu.st"));
        assertTrue(search.contains("prefs.subtitleSource"));
        assertTrue(search.contains("SegmentFinder.tmdbExternalImdb"));
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
    public void playerControlsFollowDonorPlacementWithoutUserPinning() throws Exception {
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String offset = readProjectFile("src/main/java/com/brouken/player/OffsetPanel.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertFalse(preferences.contains("app:key=\"playerButtons\""));
        assertFalse(preferences.contains("app:key=\"showButtonOpen\""));
        assertFalse(preferences.contains("app:key=\"showButtonPlaylist\""));
        assertFalse(preferences.contains("app:key=\"showButtonQuality\""));
        assertFalse(preferences.contains("app:key=\"showButtonSubtitles\""));
        assertFalse(preferences.contains("app:key=\"showButtonAspectRatio\""));
        assertFalse(preferences.contains("app:key=\"showButtonTogether\""));
        assertFalse(preferences.contains("app:key=\"showButtonAppSettings\""));
        assertFalse(prefs.contains("public boolean showButtonOpen"));
        assertFalse(prefs.contains("public boolean showButtonPlaylist"));
        assertFalse(prefs.contains("public boolean showButtonQuality"));
        assertFalse(prefs.contains("public boolean showButtonSubtitles"));
        assertFalse(prefs.contains("public boolean showButtonPlaybackOptions"));
        assertFalse(prefs.contains("public boolean showButtonAppSettings"));
        assertFalse(activity.contains("PlayerButtonPlacement.resolve"));
        assertTrue(activity.contains("controls.addView(buttonQuality)"));
        assertTrue(activity.contains("controls.addView(buttonAudio)"));
        assertTrue(activity.contains("controls.addView(exoSubtitle)"));
        assertTrue(activity.contains("controls.addView(buttonPlaylist)"));
        assertTrue(activity.contains("controls.addView(buttonMore)"));
        assertTrue(activity.contains("showMoreMenu()"));
        assertTrue(activity.contains(
                "new MenuItem(getString(R.string.subtitle_offset_title)"));
        assertTrue(offset.contains("subtitle_offset_earlier"));
        assertTrue(offset.contains("subtitle_offset_later"));
        assertTrue(ukrainian.contains("name=\"subtitle_offset_earlier\""));
        assertTrue(ukrainian.contains("name=\"subtitle_offset_later\""));
    }

    @Test
    public void remainingDonorPanelsStayReachableAndRotationSafe() throws Exception {
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String launcher = readProjectFile(
                "src/main/res/layout/view_ua_empty_state.xml");
        String launcherLandscape = readProjectFile(
                "src/main/res/layout-land/view_ua_empty_state.xml");
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String more = section(activity, "private void showMoreMenu()",
                "private void showPlaybackTools()");
        String subtitles = section(activity, "private void showSubtitleDialog()",
                "private void clearSubtitleTimeline()");
        String secondary = section(activity, "private void showSecondarySubtitleDialog()",
                "private void showManualSubtitleSearch(boolean secondary)");
        String configuration = section(activity,
                "public void onConfigurationChanged(@NonNull Configuration newConfig)",
                "void showError(");

        assertTrue(more.contains("this::showSpeedDialog"));
        assertTrue(more.contains("this::askForLink"));
        assertFalse(more.contains("button_playback_options"));
        assertTrue(more.contains("if (player != null) {\n"
                + "            items.add(new MenuItem(getString(R.string.sleep_timer_title)"));
        assertTrue(activity.contains("showPickerDialog(subtitleOffsetDialog)"));
        assertTrue(activity.contains("showPickerDialog(skipSessionDialog)"));
        assertTrue(activity.contains("showPickerDialog(sleepTimerDialog)"));
        assertTrue(configuration.contains("dismissOpenPickers();"));
        assertTrue(subtitles.contains(
                "MenuItem.caption(getString(R.string.subtitle_main_title))"));
        assertTrue(subtitles.contains(
                "textEnabled && !painting && group.isTrackSelected(index)"));
        assertFalse(subtitles.contains("R.string.subtitle_offset_title"));
        assertTrue(secondary.contains("format.sampleMimeType == null"));
        assertTrue(launcher.contains("@+id/ua_empty_state_link"));
        assertTrue(launcherLandscape.contains("@+id/ua_empty_state_link"));

        int translate = preferences.indexOf("app:key=\"subtitleTranslateOn\"");
        int backends = preferences.indexOf("app:key=\"subtitleTranslateBackends\"");
        int sourceLanguage = preferences.indexOf("app:key=\"subtitleSearchLanguage\"");
        assertTrue(translate >= 0 && translate < backends && backends < sourceLanguage);
    }

    @Test
    public void manualLaunchUsesBrandedEmptyStateWithoutPreparingAnEmptyPlayer()
            throws Exception {
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String playerLayout = readProjectFile("src/main/res/layout/activity_player.xml");
        String textureLayout = readProjectFile(
                "src/main/res/layout/activity_player_textureview.xml");
        String emptyLayout = readProjectFile("src/main/res/layout/view_ua_empty_state.xml");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(activity.contains("LaunchIntentPolicy.shouldSuppressResume"));
        assertTrue(activity.contains(
                "haveMedia = mPrefs.mediaUri != null && !mPrefs.suppressResume"));
        assertTrue(activity.contains("showEmptyState();"));
        assertTrue(activity.contains("if (!haveMedia)"));
        assertTrue(prefs.contains("public boolean suppressResume"));
        assertTrue(prefs.contains("suppressResume = false"));
        assertTrue(playerLayout.contains("@layout/view_ua_empty_state"));
        assertTrue(textureLayout.contains("@layout/view_ua_empty_state"));
        assertTrue(emptyLayout.contains("@+id/ua_empty_state"));
        assertTrue(emptyLayout.contains("@+id/ua_empty_state_open"));
        assertTrue(emptyLayout.contains("@+id/ua_empty_state_together"));
        assertTrue(emptyLayout.contains("@+id/ua_empty_state_settings"));
        assertTrue(ukrainian.contains("name=\"empty_state_subtitle\""));
        assertTrue(ukrainian.contains("name=\"empty_state_open\""));
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
        String offset = readProjectFile(
                "src/main/java/com/brouken/player/OffsetPanel.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(activity.contains("buildTextRenderers(Context context, TextOutput output"));
        assertTrue(activity.contains("new SubtitleOffset("));
        assertTrue(activity.contains("secondaryTextTrack.forSecondary"));
        assertTrue(activity.contains("secondarySubtitleOffset"));
        assertTrue(activity.contains("paintSubtitle(subtitleUri)"));
        assertTrue(activity.contains("SubtitleTimeline.load"));
        assertTrue(activity.contains("subtitleOffset.setTimeline"));
        assertTrue(activity.contains("R.drawable.ic_subtitle_offset_24dp"));
        assertTrue(activity.contains("showSubtitleDialog()"));
        assertTrue(activity.contains("attachSubtitleTrack(subtitleUri)"));
        assertTrue(activity.contains("showSkipSessionDialog()"));
        assertTrue(activity.contains("skipOffsetSec"));
        assertTrue(activity.contains("SkipSessionPolicy.shiftAndValidate"));
        assertTrue(offset.contains("static final class Choice"));
        assertTrue(ukrainian.contains("name=\"skip_session_title\""));
    }

    @Test
    public void onlineSubtitleTranslationKeepsUkrainianDefaultAndPrivateContent() throws Exception {
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String translator = readProjectFile(
                "src/main/java/com/brouken/player/SubtitleTranslate.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(preferences.contains("app:key=\"subtitleTranslateOn\""));
        assertFalse(preferences.contains("app:key=\"languageSubtitleTranslate\""));
        assertTrue(preferences.contains("app:defaultValue=\"true\""));
        assertTrue(prefs.contains("public boolean subtitleSearch = false"));
        assertFalse(prefs.contains("public String languageSubtitleTranslate"));
        assertFalse(prefs.contains("setLanguageSubtitleTranslate"));
        assertTrue(prefs.contains("remove(PREF_KEY_LANGUAGE_SUBTITLE_TRANSLATE)"));
        assertTrue(prefs.contains("return UkrainianSubtitlePolicy.SEARCH_LANGUAGE"));
        assertTrue(prefs.contains("public boolean subtitleTranslate = true"));
        assertFalse(activity.contains("mPrefs.languageSubtitleTranslate"));
        assertTrue(activity.contains("UkrainianSubtitlePolicy.directLanguages"));
        assertTrue(activity.contains("UkrainianSubtitlePolicy.fallbackLanguages"));
        assertTrue(activity.contains("UkrainianSubtitlePolicy.translatedCacheName"));
        assertTrue(translator.contains(
                "https://translate.googleapis.com/translate_a/single"));
        assertTrue(translator.contains("new FormBody.Builder()"));
        assertTrue(translator.contains(".add(\"q\", joined)"));
        assertFalse(translator.contains("MediaId"));
        assertFalse(translator.contains("apiTitle"));
        assertFalse(translator.contains("imdb"));
        assertFalse(translator.contains("tmdb"));
        assertFalse(translator.contains("HttpLoggingInterceptor"));
        assertTrue(translator.contains("target.delete()"));
        assertTrue(ukrainian.contains("name=\"subtitle_translate_progress\""));
        assertTrue(ukrainian.contains("name=\"subtitle_translate_success\""));
        assertTrue(ukrainian.contains("name=\"subtitle_translate_failed\""));

        String settings = readProjectFile(
                "src/main/java/com/brouken/player/SettingsActivity.java");
        assertFalse(settings.contains("TranslationLanguageDialog.show"));
        assertTrue(settings.contains("UaListPreferenceDialogFragment"));

        String search = readProjectFile(
                "src/main/java/com/brouken/player/SubtitleSearch.java");
        String openSubtitles = readProjectFile(
                "src/main/java/com/brouken/player/OpenSubtitles.java");
        assertTrue(search.contains("answered.set(true);"));
        assertTrue(openSubtitles.contains("if (answered != null) answered.set(true)"));
    }

    @Test
    public void tvBackAndPausedScreenGuardRemainWired() throws Exception {
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String prefs = readProjectFile("src/main/java/com/brouken/player/Prefs.java");
        String settings = readProjectFile(
                "src/main/java/com/brouken/player/SettingsActivity.java");
        String preferences = readProjectFile("src/main/res/xml/root_preferences.xml");
        String surface = readProjectFile("src/main/res/layout/activity_player.xml");
        String texture = readProjectFile(
                "src/main/res/layout/activity_player_textureview.xml");

        assertTrue(prefs.contains("public boolean tvSingleBack = false"));
        assertTrue(prefs.contains("public boolean keepAwakeOnPause = true"));
        assertTrue(preferences.contains("app:key=\"tvSingleBack\""));
        assertTrue(preferences.contains("app:key=\"keepAwakeOnPause\""));
        assertFalse(preferences.contains("app:key=\"skipSilence\""));
        assertFalse(prefs.contains("skipSilence"));
        assertTrue(settings.contains("preferenceSingleBack.setVisible(tvBox)"));
        assertTrue(settings.contains("preferenceKeepAwake.setVisible(tvBox)"));
        assertTrue(activity.contains("!mPrefs.tvSingleBack"));
        assertTrue(activity.contains("resetPausedScreenGuard()"));
        assertTrue(activity.contains("KEEP_AWAKE_MAX_MS"));
        assertTrue(activity.contains("dimPausedScreen"));
        assertTrue(activity.contains("schedulePausedControllerHide()"));
        assertTrue(activity.contains("hidePausedControllerRunnable"));
        assertTrue(surface.contains("android:id=\"@+id/dim_overlay\""));
        assertTrue(texture.contains("android:id=\"@+id/dim_overlay\""));
    }

    @Test
    public void duplicateScreensHandOffOneCompleteSession() throws Exception {
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String playlist = readProjectFile(
                "src/main/java/com/brouken/player/LampaPlaylist.java");
        String manifest = readProjectFile("src/main/AndroidManifest.xml");

        assertTrue(manifest.contains("android:launchMode=\"singleTask\""));
        assertTrue(activity.contains("private static PlayerActivity live"));
        assertTrue(activity.contains("live.saveApiSession(inheritedState)"));
        assertTrue(activity.contains("live.buildHandoffIntent()"));
        assertTrue(activity.contains("live.handOver()"));
        assertTrue(activity.contains("if (handedOver) return;"));
        assertTrue(activity.contains("if (!handedOver) releasePlayer(false)"));
        assertTrue(activity.contains("restoreApiSession(savedInstanceState"));
        assertTrue(playlist.contains("String toSessionJson()"));
        assertTrue(playlist.contains("playback_results"));
        assertTrue(playlist.contains("_session_segments"));
    }
}
