package com.brouken.player;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.view.accessibility.CaptioningManager;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;

import com.brouken.player.together.AliasGenerator;
import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;

class Prefs {
    // Previously used
    // private static final String PREF_KEY_AUDIO_TRACK = "audioTrack";
    // private static final String PREF_KEY_AUDIO_TRACK_FFMPEG = "audioTrackFfmpeg";
    // private static final String PREF_KEY_SUBTITLE_TRACK = "subtitleTrack";

    private static final String PREF_KEY_MEDIA_URI = "mediaUri";
    private static final String PREF_KEY_MEDIA_TYPE = "mediaType";
    private static final String PREF_KEY_BRIGHTNESS = "brightness";
    private static final String PREF_KEY_FIRST_RUN = "firstRun";
    private static final String PREF_KEY_SUBTITLE_URI = "subtitleUri";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_URI = "subtitleSecondaryUri";

    private static final String PREF_KEY_AUDIO_TRACK_ID = "audioTrackId";
    private static final String PREF_KEY_SUBTITLE_TRACK_ID = "subtitleTrackId";
    private static final String PREF_KEY_RESIZE_MODE = "resizeMode";
    private static final String PREF_KEY_ASPECT_RATIO = "aspectRatio";
    private static final String PREF_KEY_ORIENTATION = "orientation";
    private static final String PREF_KEY_SCALE = "scale";
    private static final String PREF_KEY_SCOPE_URI = "scopeUri";
    private static final String PREF_KEY_ASK_SCOPE = "askScope";
    private static final String PREF_KEY_AUTO_PIP = "autoPiP";
    private static final String PREF_KEY_TUNNELING = "tunneling";
    private static final String PREF_KEY_SKIP_SILENCE = "skipSilence";
    private static final String PREF_KEY_FRAMERATE_MATCHING = "frameRateMatching";
    private static final String PREF_KEY_ALLOW_SYSTEM_FRAMERATE = "allowSystemFrameRate";
    private static final String PREF_KEY_REPEAT_TOGGLE = "repeatToggle";
    private static final String PREF_KEY_SPEED = "speed";
    private static final String PREF_KEY_HOLD_SPEED = "holdSpeed";
    private static final String PREF_KEY_TIME_REMAINING = "timeRemaining";
    private static final String PREF_KEY_SHOW_STATS = "showStats";
    private static final String PREF_KEY_FILE_ACCESS = "fileAccess";
    private static final String PREF_KEY_DECODER_PRIORITY = "decoderPriority";
    private static final String PREF_KEY_MAP_DV7 = "mapDV7ToHevc";
    private static final String PREF_KEY_LANGUAGE_AUDIO = "languageAudio";
    private static final String PREF_KEY_LANGUAGE_SUBTITLE = "languageSubtitle";
    private static final String PREF_KEY_LANGUAGE_SUBTITLE_SECONDARY =
            "languageSubtitleSecondary";
    private static final String PREF_KEY_LANGUAGE_SUBTITLE_TRANSLATE =
            "languageSubtitleTranslate";
    private static final String PREF_KEY_SUBTITLE_SEARCH_MODE = "subtitleSearchMode";
    private static final String PREF_KEY_SUBTITLE_SEARCH = "subtitleSearch";
    private static final String PREF_KEY_SUBTITLE_SEARCH_STRICT = "subtitleSearchStrict";
    private static final String PREF_KEY_SUBTITLE_TRANSLATE_ON = "subtitleTranslateOn";
    private static final String PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS =
            "subtitleTranslateBackends";
    private static final String PREF_KEY_SUBTITLE_AUTO_TRANSLATE_UKRAINIAN =
            "subtitleAutoTranslateUkrainian";
    private static final String PREF_KEY_SUBTITLE_STYLE_EMBEDDED = "subtitleStyleEmbedded";
    private static final String PREF_KEY_SUBTITLE_STYLE_BOLD = "subtitleStyleBold";
    private static final String PREF_KEY_SUBTITLE_SCALE = "subtitleScale";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_MODE = "subtitleSecondaryMode";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_SCALE = "subtitleSecondaryScale";
    private static final String PREF_KEY_SUBTITLE_TEXT_COLOR = "subtitleTextColor";
    private static final String PREF_KEY_SUBTITLE_BACKGROUND = "subtitleBackground";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_TEXT_COLOR =
            "subtitleSecondaryTextColor";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_BACKGROUND =
            "subtitleSecondaryBackground";
    private static final String PREF_KEY_SUBTITLE_EDGE = "subtitleEdge";
    private static final String PREF_KEY_SKIP_ENABLED = "skipEnabled";
    private static final String PREF_KEY_SKIP_MODE = "skipMode";
    private static final String PREF_KEY_SKIP_MODE_CREDITS = "skipModeCredits";
    private static final String PREF_KEY_SKIP_FETCH = "skipFetchOnline";
    private static final String PREF_KEY_SYSTEM_VOLUME = "systemVolume";
    private static final String PREF_KEY_PLAYER_VOLUME = "playerVolume";
    private static final String PREF_KEY_VOLUME_BOOST = "volumeBoost";
    private static final String PREF_KEY_VOLUME_GESTURES = "volumeGesturesEnabled";
    private static final String PREF_KEY_BRIGHTNESS_GESTURES = "brightnessGesturesEnabled";
    private static final String PREF_KEY_SHOW_BUTTON_OPEN = "showButtonOpen";
    private static final String PREF_KEY_SHOW_BUTTON_PLAYLIST = "showButtonPlaylist";
    private static final String PREF_KEY_SHOW_BUTTON_QUALITY = "showButtonQuality";
    private static final String PREF_KEY_SHOW_BUTTON_SUBTITLES = "showButtonSubtitles";
    private static final String PREF_KEY_SHOW_BUTTON_ASPECT_RATIO = "showButtonAspectRatio";
    private static final String PREF_KEY_SHOW_BUTTON_ROTATION = "showButtonRotation";
    private static final String PREF_KEY_SHOW_BUTTON_LOCK = "showButtonLock";
    private static final String PREF_KEY_SHOW_BUTTON_PIP = "showButtonPiP";
    private static final String PREF_KEY_SHOW_BUTTON_PLAYBACK_OPTIONS =
            "showButtonPlaybackOptions";
    private static final String PREF_KEY_SHOW_BUTTON_TOGETHER = "showButtonTogether";
    private static final String PREF_KEY_SHOW_BUTTON_APP_SETTINGS = "showButtonAppSettings";
    private static final String PREF_KEY_TOGETHER_NICK = "togetherNick";
    private static final String PREF_KEY_TOGETHER_PASSWORD = "togetherPassword";
    private static final String PREF_KEY_TOGETHER_PUBLIC = "togetherPublic";
    private static final String PREF_KEY_TOGETHER_RELAY = "togetherRelay";
    private static final String PREF_KEY_TOGETHER_INVITE_PAGE = "togetherInvitePage";

    public static final String SKIP_MODE_BRIEF = "brief";
    public static final String SKIP_MODE_FULL = "full";
    public static final String SKIP_MODE_BUTTON = "button";
    public static final String SKIP_MODE_AUTO = "auto";
    /**
     * Offer nothing: the session's mute for segments. Only ever a choice made in the player's skip
     * panel — deliberately not in the settings list, where switching skipping off is a switch for the
     * whole feature rather than one film's worth of it.
     */
    public static final String SKIP_MODE_OFF = "off";

    public static final String TRACK_DEFAULT = "default";
    public static final String TRACK_DEVICE = "device";
    public static final String SEARCH_OFF = "off";
    public static final String SEARCH_FIRST = "first";
    public static final String SEARCH_NONE = "none";
    public static final String SECONDARY_OFF = "off";
    public static final String SECONDARY_ALWAYS = "always";
    public static final String SECONDARY_DEMAND = "demand";
    public static final String SECONDARY_PAUSE = SECONDARY_DEMAND;

    final Context mContext;
    final SharedPreferences mSharedPreferences;

    public Uri mediaUri;
    // A launcher start keeps the remembered URI for the next file picker, but must not replay it.
    // This is session-only and is cleared by every real media selection.
    public boolean suppressResume;
    public Uri subtitleUri;
    public Uri subtitleSecondaryUri;
    public Uri scopeUri;
    public String mediaType;
    public int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    public float aspectRatio = 0f;
    public Utils.Orientation orientation = Utils.Orientation.UNSPECIFIED;
    public float scale = 1.f;
    public float speed = 1.f;
    public boolean holdSpeed = true;
    public boolean timeRemaining = false;
    public boolean showStats = false;

    public String subtitleTrackId;
    public String audioTrackId;

    public int brightness = -1;
    public boolean firstRun = true;
    public boolean askScope = true;
    public boolean autoPiP = false;

    public boolean tunneling = false;
    public boolean skipSilence = false;
    public boolean frameRateMatching = false;
    public boolean allowSystemFrameRate = true;
    public boolean repeatToggle = false;
    public String fileAccess = "auto";
    public int decoderPriority = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
    public boolean mapDV7ToHevc = false;
    public String languageAudio = "";
    public String languageSubtitle = "";
    public String languageSubtitleSecondary = "";
    public String languageSubtitleTranslate = "ukr";
    // Opt-in because searches disclose the title identifiers to third-party services.
    public boolean subtitleSearch = false;
    public boolean subtitleSearchStrict = false;
    public boolean subtitleAutoTranslateUkrainian = true;
    public boolean subtitleTranslate = true;
    public String subtitleTranslateBackends = SubtitleTranslate.DEFAULT_BACKENDS;
    public boolean subtitleSourceOpenSubtitles = true;
    public boolean subtitleSourceStremio = true;
    public boolean subtitleSourceShegu = true;
    public boolean subtitleSourceRest = true;
    public boolean subtitleStyleEmbedded = true;
    public boolean subtitleStyleBold = false;
    public float subtitleScale = 1.0f;
    public String subtitleSecondaryMode = SECONDARY_ALWAYS;
    public float subtitleSecondaryScale = 1.0f;
    public int subtitleTextColor = Color.WHITE;
    public int subtitleBackgroundColor = Color.TRANSPARENT;
    public int subtitleSecondaryTextColor = 0xFFCCCCCC;
    public int subtitleSecondaryBackgroundColor = 0x80000000;
    public int subtitleEdgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE;
    public boolean skipEnabled = true;
    public String skipMode = SKIP_MODE_FULL;
    public String skipModeCredits = SKIP_MODE_FULL;
    public boolean skipFetchOnline = true;
    public boolean systemVolume = true;
    public int playerVolume = 100;
    public int volumeBoost = 0;
    public boolean volumeGesturesEnabled = true;
    public boolean brightnessGesturesEnabled = true;
    public boolean showButtonOpen = false;
    public boolean showButtonPlaylist = true;
    public boolean showButtonQuality = true;
    public boolean showButtonSubtitles = true;
    public boolean showButtonAspectRatio = false;
    public boolean showButtonRotation = false;
    public boolean showButtonLock = false;
    public boolean showButtonPiP = false;
    public boolean showButtonPlaybackOptions = true;
    public boolean showButtonTogether = false;
    public boolean showButtonAppSettings = false;
    public String togetherNick = "";
    public String togetherPassword = "";
    public boolean togetherPublic = false;
    public String togetherRelay = "";
    public String togetherInvitePage = "";

    private LinkedHashMap positions;
    private final LinkedHashMap<String, Long> sessionPositions = new LinkedHashMap<>();
    private String positionKey;

    public boolean persistentMode = true;
    public long nonPersitentPosition = -1L;

    public Prefs(Context context) {
        mContext = context;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        loadSavedPreferences();
        loadPositions();
    }

    private void loadSavedPreferences() {
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_URI))
            mediaUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_MEDIA_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_TYPE))
            mediaType = mSharedPreferences.getString(PREF_KEY_MEDIA_TYPE, null);
        brightness = mSharedPreferences.getInt(PREF_KEY_BRIGHTNESS, brightness);
        firstRun = mSharedPreferences.getBoolean(PREF_KEY_FIRST_RUN, firstRun);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_URI))
            subtitleUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SUBTITLE_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_SECONDARY_URI))
            subtitleSecondaryUri = Uri.parse(
                    mSharedPreferences.getString(PREF_KEY_SUBTITLE_SECONDARY_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK_ID))
            audioTrackId = mSharedPreferences.getString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_TRACK_ID))
            subtitleTrackId = mSharedPreferences.getString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
        if (mSharedPreferences.contains(PREF_KEY_RESIZE_MODE))
            resizeMode = mSharedPreferences.getInt(PREF_KEY_RESIZE_MODE, resizeMode);
        aspectRatio = mSharedPreferences.getFloat(PREF_KEY_ASPECT_RATIO, aspectRatio);
        orientation = Utils.Orientation.values()[mSharedPreferences.getInt(PREF_KEY_ORIENTATION, orientation.value)];
        scale = mSharedPreferences.getFloat(PREF_KEY_SCALE, scale);
        if (mSharedPreferences.contains(PREF_KEY_SCOPE_URI))
            scopeUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SCOPE_URI, null));
        askScope = mSharedPreferences.getBoolean(PREF_KEY_ASK_SCOPE, askScope);
        speed = mSharedPreferences.getFloat(PREF_KEY_SPEED, speed);
        loadUserPreferences();
    }

    public void loadUserPreferences() {
        autoPiP = mSharedPreferences.getBoolean(PREF_KEY_AUTO_PIP, autoPiP);
        holdSpeed = mSharedPreferences.getBoolean(PREF_KEY_HOLD_SPEED, holdSpeed);
        timeRemaining = mSharedPreferences.getBoolean(
                PREF_KEY_TIME_REMAINING, timeRemaining);
        showStats = mSharedPreferences.getBoolean(PREF_KEY_SHOW_STATS, showStats);
        tunneling = mSharedPreferences.getBoolean(PREF_KEY_TUNNELING, tunneling);
        skipSilence = mSharedPreferences.getBoolean(PREF_KEY_SKIP_SILENCE, skipSilence);
        frameRateMatching = mSharedPreferences.getBoolean(PREF_KEY_FRAMERATE_MATCHING, frameRateMatching);
        allowSystemFrameRate = mSharedPreferences.getBoolean(PREF_KEY_ALLOW_SYSTEM_FRAMERATE, !Utils.isTvBox(mContext));
        repeatToggle = mSharedPreferences.getBoolean(PREF_KEY_REPEAT_TOGGLE, repeatToggle);
        fileAccess = mSharedPreferences.getString(PREF_KEY_FILE_ACCESS, fileAccess);
        decoderPriority = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_DECODER_PRIORITY, String.valueOf(decoderPriority)));
        mapDV7ToHevc = mSharedPreferences.getBoolean(PREF_KEY_MAP_DV7, mapDV7ToHevc);
        languageAudio = getLanguageAudio(mContext);
        languageSubtitle = getLanguageSubtitle(mContext);
        languageSubtitleSecondary = getLanguageSubtitleSecondary(mContext);
        languageSubtitleTranslate = getLanguageSubtitleTranslate(mContext);
        String subtitleSearchMode = getSubtitleSearchMode(mContext);
        subtitleSearch = !SEARCH_OFF.equals(subtitleSearchMode);
        subtitleSearchStrict = SEARCH_NONE.equals(subtitleSearchMode);
        subtitleAutoTranslateUkrainian = mSharedPreferences.getBoolean(
                PREF_KEY_SUBTITLE_AUTO_TRANSLATE_UKRAINIAN,
                subtitleAutoTranslateUkrainian);
        subtitleTranslate = getSubtitleTranslate(mContext);
        subtitleTranslateBackends = getSubtitleTranslateBackends(mContext);
        subtitleStyleEmbedded = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_EMBEDDED, subtitleStyleEmbedded);
        subtitleStyleBold = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_BOLD, subtitleStyleBold);
        subtitleScale = readFloat(PREF_KEY_SUBTITLE_SCALE, subtitleScale, 0.25f, 2.0f);
        subtitleSecondaryMode = mSharedPreferences.getString(
                PREF_KEY_SUBTITLE_SECONDARY_MODE, subtitleSecondaryMode);
        subtitleSecondaryScale = readFloat(PREF_KEY_SUBTITLE_SECONDARY_SCALE,
                subtitleSecondaryScale, 0.25f, 2.0f);
        subtitleTextColor = readColor(PREF_KEY_SUBTITLE_TEXT_COLOR, subtitleTextColor);
        subtitleBackgroundColor = readColor(PREF_KEY_SUBTITLE_BACKGROUND,
                subtitleBackgroundColor);
        subtitleSecondaryTextColor = readColor(PREF_KEY_SUBTITLE_SECONDARY_TEXT_COLOR,
                subtitleSecondaryTextColor);
        subtitleSecondaryBackgroundColor = readColor(PREF_KEY_SUBTITLE_SECONDARY_BACKGROUND,
                subtitleSecondaryBackgroundColor);
        subtitleEdgeType = readInt(PREF_KEY_SUBTITLE_EDGE, subtitleEdgeType, 0, 2);
        skipEnabled = mSharedPreferences.getBoolean(PREF_KEY_SKIP_ENABLED, skipEnabled);
        skipMode = mSharedPreferences.getString(PREF_KEY_SKIP_MODE, skipMode);
        skipModeCredits = mSharedPreferences.getString(PREF_KEY_SKIP_MODE_CREDITS, skipModeCredits);
        if (SKIP_MODE_BUTTON.equals(skipMode)) skipMode = SKIP_MODE_FULL;
        if (SKIP_MODE_BUTTON.equals(skipModeCredits)) skipModeCredits = SKIP_MODE_FULL;
        skipFetchOnline = mSharedPreferences.getBoolean(PREF_KEY_SKIP_FETCH, skipFetchOnline);
        systemVolume = mSharedPreferences.getBoolean(PREF_KEY_SYSTEM_VOLUME, systemVolume);
        playerVolume = Math.max(0, Math.min(100,
                mSharedPreferences.getInt(PREF_KEY_PLAYER_VOLUME, playerVolume)));
        try {
            volumeBoost = Math.max(0, Math.min(100, Integer.parseInt(
                    mSharedPreferences.getString(PREF_KEY_VOLUME_BOOST, String.valueOf(volumeBoost)))));
        } catch (NumberFormatException ignored) {
            volumeBoost = 0;
        }
        volumeGesturesEnabled = mSharedPreferences.getBoolean(
                PREF_KEY_VOLUME_GESTURES, volumeGesturesEnabled);
        brightnessGesturesEnabled = mSharedPreferences.getBoolean(
                PREF_KEY_BRIGHTNESS_GESTURES, brightnessGesturesEnabled);
        showButtonOpen = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_OPEN, showButtonOpen);
        showButtonPlaylist = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_PLAYLIST, showButtonPlaylist);
        showButtonQuality = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_QUALITY, showButtonQuality);
        showButtonSubtitles = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_SUBTITLES, showButtonSubtitles);
        showButtonAspectRatio = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_ASPECT_RATIO, showButtonAspectRatio);
        showButtonRotation = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_ROTATION, showButtonRotation);
        showButtonLock = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_LOCK, showButtonLock);
        showButtonPiP = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_PIP, showButtonPiP);
        showButtonPlaybackOptions = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_PLAYBACK_OPTIONS, showButtonPlaybackOptions);
        showButtonTogether = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_TOGETHER, showButtonTogether);
        showButtonAppSettings = mSharedPreferences.getBoolean(
                PREF_KEY_SHOW_BUTTON_APP_SETTINGS, showButtonAppSettings);
        togetherPassword = mSharedPreferences.getString(
                PREF_KEY_TOGETHER_PASSWORD, togetherPassword);
        togetherPublic = mSharedPreferences.getBoolean(
                PREF_KEY_TOGETHER_PUBLIC, togetherPublic);
        togetherRelay = mSharedPreferences.getString(
                PREF_KEY_TOGETHER_RELAY, togetherRelay);
        togetherInvitePage = mSharedPreferences.getString(
                PREF_KEY_TOGETHER_INVITE_PAGE, togetherInvitePage);
        togetherNick = mSharedPreferences.getString(PREF_KEY_TOGETHER_NICK, "");
        if (togetherNick == null || togetherNick.trim().isEmpty()) {
            togetherNick = AliasGenerator.random();
            mSharedPreferences.edit().putString(PREF_KEY_TOGETHER_NICK, togetherNick).apply();
        }
        if (togetherPublic && (togetherPassword == null || togetherPassword.isEmpty())) {
            updateTogetherPublic(false);
        }
        Relay.setBase(togetherRelay);
        Room.setInvitePage(togetherInvitePage);
    }

    public void setLanguageAudio(String languages) {
        languageAudio = AudioLanguagePriority.serialize(AudioLanguagePriority.parse(languages));
        setLanguageAudio(mContext, languageAudio);
    }

    public static String getLanguageAudio(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String stored = preferences.getString(PREF_KEY_LANGUAGE_AUDIO, null);
        if (stored != null && !TRACK_DEFAULT.equals(stored) && !TRACK_DEVICE.equals(stored)) {
            String normalized = AudioLanguagePriority.serialize(AudioLanguagePriority.parse(stored));
            if (!normalized.equals(stored)) {
                preferences.edit().putString(PREF_KEY_LANGUAGE_AUDIO, normalized).apply();
            }
            return normalized;
        }
        String migrated = TRACK_DEFAULT.equals(stored)
                ? "" : AudioLanguagePriority.serialize(java.util.Arrays.asList(Utils.getDeviceLanguages()));
        preferences.edit().putString(PREF_KEY_LANGUAGE_AUDIO, migrated).apply();
        return migrated;
    }

    public static void setLanguageAudio(Context context, String languages) {
        String normalized = AudioLanguagePriority.serialize(AudioLanguagePriority.parse(languages));
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LANGUAGE_AUDIO, normalized).apply();
    }

    public static String getLanguageSubtitle(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String stored = preferences.getString(PREF_KEY_LANGUAGE_SUBTITLE, null);
        if (stored != null) {
            String normalized = AudioLanguagePriority.serialize(
                    AudioLanguagePriority.parse(stored));
            if (!normalized.equals(stored)) {
                preferences.edit().putString(PREF_KEY_LANGUAGE_SUBTITLE, normalized).apply();
            }
            return normalized;
        }

        CaptioningManager manager =
                (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
        String migrated = "";
        if (manager != null && manager.getLocale() != null) {
            String language = AudioLanguagePriority.normalize(manager.getLocale().toLanguageTag());
            if (language != null) migrated = language;
        }
        preferences.edit().putString(PREF_KEY_LANGUAGE_SUBTITLE, migrated).apply();
        return migrated;
    }

    public static void setLanguageSubtitle(Context context, String languages) {
        String normalized = AudioLanguagePriority.serialize(AudioLanguagePriority.parse(languages));
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LANGUAGE_SUBTITLE, normalized).apply();
    }

    public static String getLanguageSubtitleSecondary(Context context) {
        return AudioLanguagePriority.serialize(AudioLanguagePriority.parse(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_KEY_LANGUAGE_SUBTITLE_SECONDARY, "")));
    }

    public static void setLanguageSubtitleSecondary(Context context, String languages) {
        String normalized = AudioLanguagePriority.serialize(AudioLanguagePriority.parse(languages));
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LANGUAGE_SUBTITLE_SECONDARY, normalized).apply();
    }

    public static String getLanguageSubtitleTranslate(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String stored = preferences.getString(PREF_KEY_LANGUAGE_SUBTITLE_TRANSLATE, null);
        String target = LanguagePriorityModel.targetOrUkrainian(stored);
        if (!target.equals(stored)) {
            preferences.edit().putString(PREF_KEY_LANGUAGE_SUBTITLE_TRANSLATE, target).apply();
        }
        return target;
    }

    public static void setLanguageSubtitleTranslate(Context context, String language) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LANGUAGE_SUBTITLE_TRANSLATE,
                        LanguagePriorityModel.targetOrUkrainian(language)).apply();
    }

    public static String getSubtitleSearchMode(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String stored = preferences.getString(PREF_KEY_SUBTITLE_SEARCH_MODE, null);
        if (SEARCH_OFF.equals(stored) || SEARCH_FIRST.equals(stored)
                || SEARCH_NONE.equals(stored)) return stored;
        String migrated = !preferences.getBoolean(PREF_KEY_SUBTITLE_SEARCH, false)
                ? SEARCH_OFF
                : preferences.getBoolean(PREF_KEY_SUBTITLE_SEARCH_STRICT, false)
                ? SEARCH_NONE : SEARCH_FIRST;
        preferences.edit().putString(PREF_KEY_SUBTITLE_SEARCH_MODE, migrated).apply();
        return migrated;
    }

    public static void setSubtitleSearchMode(Context context, String mode) {
        String value = SEARCH_NONE.equals(mode) || SEARCH_FIRST.equals(mode)
                ? mode : SEARCH_OFF;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_SUBTITLE_SEARCH_MODE, value)
                .putBoolean(PREF_KEY_SUBTITLE_SEARCH, !SEARCH_OFF.equals(value))
                .putBoolean(PREF_KEY_SUBTITLE_SEARCH_STRICT, SEARCH_NONE.equals(value))
                .apply();
    }

    public static boolean getSubtitleTranslate(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.contains(PREF_KEY_SUBTITLE_TRANSLATE_ON)) {
            return preferences.getBoolean(PREF_KEY_SUBTITLE_TRANSLATE_ON, true);
        }
        boolean migrated = preferences.getBoolean(PREF_KEY_SUBTITLE_AUTO_TRANSLATE_UKRAINIAN, true);
        preferences.edit().putBoolean(PREF_KEY_SUBTITLE_TRANSLATE_ON, migrated).apply();
        return migrated;
    }

    public static String getSubtitleTranslateBackends(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String normalized = SubtitleTranslate.normalize(preferences.getString(
                PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS, SubtitleTranslate.DEFAULT_BACKENDS));
        preferences.edit().putString(PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS, normalized).apply();
        return normalized;
    }

    public static boolean getSubtitleSearch(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_KEY_SUBTITLE_SEARCH, false);
    }

    public static boolean getSubtitleSearchStrict(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_KEY_SUBTITLE_SEARCH_STRICT, false);
    }

    public static void setSubtitleSearch(Context context, boolean enabled, boolean strict) {
        setSubtitleSearchMode(context, enabled ? (strict ? SEARCH_NONE : SEARCH_FIRST) : SEARCH_OFF);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_KEY_SUBTITLE_SEARCH, enabled)
                .putBoolean(PREF_KEY_SUBTITLE_SEARCH_STRICT, strict)
                .apply();
    }

    private float readFloat(String key, float fallback, float min, float max) {
        try {
            float value = Float.parseFloat(mSharedPreferences.getString(key,
                    String.valueOf(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private int readColor(String key, int fallback) {
        try {
            return Color.parseColor(mSharedPreferences.getString(key, colorString(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private int readInt(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(mSharedPreferences.getString(key,
                    String.valueOf(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String colorString(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }

    public void updateMedia(final Context context, final Uri uri, final String type) {
        mediaUri = uri;
        suppressResume = false;
        positionKey = uri == null ? null : uri.toString();
        mediaType = type;
        updateSubtitle(null);
        updateSecondarySubtitle(null);
        updateMeta(null, null, AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.f, 1.f);
        updateAspectRatio(0f);

        if (mediaType != null && mediaType.endsWith("/*")) {
            mediaType = null;
        }

        if (mediaType == null) {
            if (ContentResolver.SCHEME_CONTENT.equals(mediaUri.getScheme())) {
                mediaType = context.getContentResolver().getType(mediaUri);
            }
        }

        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (mediaUri == null)
                sharedPreferencesEditor.remove(PREF_KEY_MEDIA_URI);
            else
                sharedPreferencesEditor.putString(PREF_KEY_MEDIA_URI, mediaUri.toString());
            if (mediaType == null)
                sharedPreferencesEditor.remove(PREF_KEY_MEDIA_TYPE);
            else
                sharedPreferencesEditor.putString(PREF_KEY_MEDIA_TYPE, mediaType);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateSubtitle(final Uri uri) {
        subtitleUri = uri;
        subtitleTrackId = null;
        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (uri == null)
                sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_URI);
            else
                sharedPreferencesEditor.putString(PREF_KEY_SUBTITLE_URI, uri.toString());
            sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateSecondarySubtitle(final Uri uri) {
        subtitleSecondaryUri = uri;
        if (!persistentMode) return;
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        if (uri == null) editor.remove(PREF_KEY_SUBTITLE_SECONDARY_URI);
        else editor.putString(PREF_KEY_SUBTITLE_SECONDARY_URI, uri.toString());
        editor.apply();
    }

    public void updatePosition(final long position) {
        String key = effectivePositionKey();
        if (key == null)
            return;

        while (positions.size() > 100)
            positions.remove(positions.keySet().toArray()[0]);

        if (persistentMode) {
            positions.put(key, position);
            savePositions();
        } else {
            nonPersitentPosition = position;
            sessionPositions.put(key, position);
        }
    }

    public void updateBrightness(final int brightness) {
        if (brightness >= -1) {
            this.brightness = brightness;
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            sharedPreferencesEditor.putInt(PREF_KEY_BRIGHTNESS, brightness);
            sharedPreferencesEditor.apply();
        }
    }

    public void updatePlayerVolume(final int volume) {
        playerVolume = Math.max(0, Math.min(100, volume));
        mSharedPreferences.edit().putInt(PREF_KEY_PLAYER_VOLUME, playerVolume).apply();
    }

    public void updateTogetherPublic(final boolean value) {
        togetherPublic = value;
        mSharedPreferences.edit().putBoolean(PREF_KEY_TOGETHER_PUBLIC, value).apply();
    }

    public void markFirstRun() {
        this.firstRun = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_FIRST_RUN, false);
        sharedPreferencesEditor.apply();
    }

    public void markScopeAsked() {
        this.askScope = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_ASK_SCOPE, false);
        sharedPreferencesEditor.apply();
    }

    private void savePositions() {
        try {
            FileOutputStream fos = mContext.openFileOutput("positions", Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(positions);
            os.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        try {
            FileInputStream fis = mContext.openFileInput("positions");
            ObjectInputStream is = new ObjectInputStream(fis);
            positions = (LinkedHashMap) is.readObject();
            is.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
            positions = new LinkedHashMap(10);
        }
    }

    public long getPosition() {
        String key = effectivePositionKey();
        if (key == null) return 0L;
        if (!persistentMode) {
            Long sessionPosition = sessionPositions.get(key);
            return sessionPosition == null ? Math.max(0L, nonPersitentPosition) : sessionPosition;
        }

        Object val = positions.get(key);
        if (val != null)
            return (long) val;

        // Return position for uri from limited scope (loaded after using Next action)
        final String searchId = documentIdentity(mediaUri);
        if (mediaUri != null && key.equals(mediaUri.toString()) && searchId != null) {
            final Object[] keys = positions.keySet().toArray();
            for (int i = keys.length; i > 0; i--) {
                final String storedKey = (String) keys[i - 1];
                if (searchId.equals(documentIdentity(Uri.parse(storedKey)))) {
                    return (long) positions.get(storedKey);
                }
            }
        }

        return 0L;
    }

    public void selectPositionKey(final String key, final long initialPosition) {
        positionKey = key == null || key.trim().isEmpty()
                ? (mediaUri == null ? null : mediaUri.toString()) : key;
        if (positionKey == null) return;
        if (persistentMode) {
            if (!positions.containsKey(positionKey)) {
                positions.put(positionKey, Math.max(0L, initialPosition));
                savePositions();
            }
        } else if (!sessionPositions.containsKey(positionKey)) {
            sessionPositions.put(positionKey, Math.max(0L, initialPosition));
        }
        nonPersitentPosition = getPosition();
    }

    private String effectivePositionKey() {
        if (positionKey != null && !positionKey.isEmpty()) return positionKey;
        return mediaUri == null ? null : mediaUri.toString();
    }

    private static String documentIdentity(final Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return null;
        try {
            return uri.getAuthority() + '/' + DocumentsContract.getDocumentId(uri);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void updateOrientation() {
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(PREF_KEY_ORIENTATION, orientation.value);
        sharedPreferencesEditor.apply();
    }

    public void updateMeta(final String audioTrackId, final String subtitleTrackId, final int resizeMode, final float scale, final float speed) {
        this.audioTrackId = audioTrackId;
        this.subtitleTrackId = subtitleTrackId;
        this.resizeMode = resizeMode;
        this.scale = scale;
        this.speed = speed;
        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (audioTrackId == null)
                sharedPreferencesEditor.remove(PREF_KEY_AUDIO_TRACK_ID);
            else
                sharedPreferencesEditor.putString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
            if (subtitleTrackId == null)
                sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID);
            else
                sharedPreferencesEditor.putString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
            sharedPreferencesEditor.putInt(PREF_KEY_RESIZE_MODE, resizeMode);
            sharedPreferencesEditor.putFloat(PREF_KEY_SCALE, scale);
            sharedPreferencesEditor.putFloat(PREF_KEY_SPEED, speed);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateAspectRatio(final float ratio) {
        aspectRatio = Math.max(0f, ratio);
        if (persistentMode) {
            mSharedPreferences.edit().putFloat(PREF_KEY_ASPECT_RATIO, aspectRatio).apply();
        }
    }

    public void updateScope(final Uri uri) {
        scopeUri = uri;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (uri == null)
            sharedPreferencesEditor.remove(PREF_KEY_SCOPE_URI);
        else
            sharedPreferencesEditor.putString(PREF_KEY_SCOPE_URI, uri.toString());
        sharedPreferencesEditor.apply();
    }

    public void setPersistent(boolean persistentMode) {
        if (this.persistentMode != persistentMode && persistentMode) {
            sessionPositions.clear();
            nonPersitentPosition = -1L;
        }
        this.persistentMode = persistentMode;
    }
}
