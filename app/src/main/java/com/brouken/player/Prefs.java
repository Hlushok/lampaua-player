package com.brouken.player;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.ui.AspectRatioFrameLayout;

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

    private static final String PREF_KEY_AUDIO_TRACK_ID = "audioTrackId";
    private static final String PREF_KEY_SUBTITLE_TRACK_ID = "subtitleTrackId";
    private static final String PREF_KEY_RESIZE_MODE = "resizeMode";
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
    private static final String PREF_KEY_FILE_ACCESS = "fileAccess";
    private static final String PREF_KEY_DECODER_PRIORITY = "decoderPriority";
    private static final String PREF_KEY_MAP_DV7 = "mapDV7ToHevc";
    private static final String PREF_KEY_LANGUAGE_AUDIO = "languageAudio";
    private static final String PREF_KEY_SUBTITLE_STYLE_EMBEDDED = "subtitleStyleEmbedded";
    private static final String PREF_KEY_SUBTITLE_STYLE_BOLD = "subtitleStyleBold";
    private static final String PREF_KEY_SKIP_ENABLED = "skipEnabled";
    private static final String PREF_KEY_SKIP_MODE = "skipMode";
    private static final String PREF_KEY_SKIP_MODE_CREDITS = "skipModeCredits";
    private static final String PREF_KEY_SKIP_FETCH = "skipFetchOnline";
    private static final String PREF_KEY_SYSTEM_VOLUME = "systemVolume";
    private static final String PREF_KEY_PLAYER_VOLUME = "playerVolume";
    private static final String PREF_KEY_VOLUME_BOOST = "volumeBoost";
    private static final String PREF_KEY_VOLUME_GESTURES = "volumeGesturesEnabled";
    private static final String PREF_KEY_BRIGHTNESS_GESTURES = "brightnessGesturesEnabled";

    public static final String SKIP_MODE_BUTTON = "button";
    public static final String SKIP_MODE_AUTO = "auto";

    public static final String TRACK_DEFAULT = "default";
    public static final String TRACK_DEVICE = "device";

    final Context mContext;
    final SharedPreferences mSharedPreferences;

    public Uri mediaUri;
    public Uri subtitleUri;
    public Uri scopeUri;
    public String mediaType;
    public int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    public Utils.Orientation orientation = Utils.Orientation.UNSPECIFIED;
    public float scale = 1.f;
    public float speed = 1.f;

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
    public String languageAudio = TRACK_DEVICE;
    public boolean subtitleStyleEmbedded = true;
    public boolean subtitleStyleBold = false;
    public boolean skipEnabled = true;
    public String skipMode = SKIP_MODE_BUTTON;
    public String skipModeCredits = SKIP_MODE_BUTTON;
    public boolean skipFetchOnline = true;
    public boolean systemVolume = true;
    public int playerVolume = 100;
    public int volumeBoost = 0;
    public boolean volumeGesturesEnabled = true;
    public boolean brightnessGesturesEnabled = true;

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
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK_ID))
            audioTrackId = mSharedPreferences.getString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_TRACK_ID))
            subtitleTrackId = mSharedPreferences.getString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
        if (mSharedPreferences.contains(PREF_KEY_RESIZE_MODE))
            resizeMode = mSharedPreferences.getInt(PREF_KEY_RESIZE_MODE, resizeMode);
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
        tunneling = mSharedPreferences.getBoolean(PREF_KEY_TUNNELING, tunneling);
        skipSilence = mSharedPreferences.getBoolean(PREF_KEY_SKIP_SILENCE, skipSilence);
        frameRateMatching = mSharedPreferences.getBoolean(PREF_KEY_FRAMERATE_MATCHING, frameRateMatching);
        allowSystemFrameRate = mSharedPreferences.getBoolean(PREF_KEY_ALLOW_SYSTEM_FRAMERATE, !Utils.isTvBox(mContext));
        repeatToggle = mSharedPreferences.getBoolean(PREF_KEY_REPEAT_TOGGLE, repeatToggle);
        fileAccess = mSharedPreferences.getString(PREF_KEY_FILE_ACCESS, fileAccess);
        decoderPriority = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_DECODER_PRIORITY, String.valueOf(decoderPriority)));
        mapDV7ToHevc = mSharedPreferences.getBoolean(PREF_KEY_MAP_DV7, mapDV7ToHevc);
        languageAudio = mSharedPreferences.getString(PREF_KEY_LANGUAGE_AUDIO, languageAudio);
        subtitleStyleEmbedded = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_EMBEDDED, subtitleStyleEmbedded);
        subtitleStyleBold = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_BOLD, subtitleStyleBold);
        skipEnabled = mSharedPreferences.getBoolean(PREF_KEY_SKIP_ENABLED, skipEnabled);
        skipMode = mSharedPreferences.getString(PREF_KEY_SKIP_MODE, skipMode);
        skipModeCredits = mSharedPreferences.getString(PREF_KEY_SKIP_MODE_CREDITS, skipModeCredits);
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
    }

    public void updateMedia(final Context context, final Uri uri, final String type) {
        mediaUri = uri;
        positionKey = uri == null ? null : uri.toString();
        mediaType = type;
        updateSubtitle(null);
        updateMeta(null, null, AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.f, 1.f);

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
