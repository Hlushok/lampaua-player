package com.brouken.player;

import static android.content.pm.PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.TimeBar;

import com.brouken.player.dtpv.DoubleTapPlayerView;
import com.brouken.player.dtpv.youtube.YouTubeOverlay;
import com.brouken.player.update.UpdateInfo;
import com.brouken.player.update.UpdateUi;
import com.brouken.player.update.Updater;
import com.brouken.player.skip.SkipController;
import com.brouken.player.skip.SkipPolicy;
import com.brouken.player.skip.SkipSegment;
import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;
import com.brouken.player.together.RoomAction;
import com.brouken.player.together.SessionCodec;
import com.brouken.player.together.TogetherManager;
import com.bumptech.glide.Glide;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerActivity extends Activity {

    private PlayerListener playerListener;
    private BroadcastReceiver mReceiver;
    private BroadcastReceiver audioOutputReceiver;
    private boolean audioOutputReceiverPrimed;
    private AudioManager mAudioManager;
    private MediaSession mediaSession;
    private DefaultTrackSelector trackSelector;
    public static LoudnessEnhancer loudnessEnhancer;
    public static BoostAudioProcessor boostProcessor;
    private static TrackingAudioSink audioSink;

    public CustomPlayerView playerView;
    public static ExoPlayer player;
    private YouTubeOverlay youTubeOverlay;

    private Object mPictureInPictureParamsBuilder;

    public Prefs mPrefs;
    public BrightnessControl mBrightnessControl;
    public static boolean haveMedia;
    private boolean videoLoading;
    public static boolean controllerVisible;
    public static boolean controllerVisibleFully;
    public static Snackbar snackbar;
    private ExoPlaybackException errorToShow;
    public static int boostLevel = 0;
    public static boolean systemVolume = true;
    public static float playerVolume = 100f;
    public static int maxVolumeBoost = 0;
    public static boolean volumeGesturesEnabled = true;
    public static boolean brightnessGesturesEnabled = true;
    private boolean isScaling = false;
    private boolean isScaleStarting = false;
    private float scaleFactor = 1.0f;

    private static final int REQUEST_CHOOSER_VIDEO = 1;
    private static final int REQUEST_CHOOSER_SUBTITLE = 2;
    private static final int REQUEST_CHOOSER_SCOPE_DIR = 10;
    private static final int REQUEST_CHOOSER_VIDEO_MEDIASTORE = 20;
    private static final int REQUEST_CHOOSER_SUBTITLE_MEDIASTORE = 21;
    private static final int REQUEST_SETTINGS = 100;
    public static final int CONTROLLER_TIMEOUT = 3500;
    private static final String ACTION_MEDIA_CONTROL = "media_control";
    private static final String EXTRA_CONTROL_TYPE = "control_type";
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int CONTROL_TYPE_PLAY = 1;
    private static final int CONTROL_TYPE_PAUSE = 2;
    private static final long VIDEO_LOAD_TIMEOUT_MS = 30_000L;
    private static final long STALL_CHECK_INTERVAL_MS = 1_500L;
    private static final long STALL_TIMEOUT_MS = 10_000L;
    private static final long STABLE_PLAYBACK_MS = 15_000L;
    private static final long SWIPE_UNLOCK_TIMEOUT_MS = 3_000L;
    private static final long FRAME_RATE_SWITCH_TIMEOUT_MS = 1_500L;
    private static final long SUBTITLE_MISS_TTL_MS = 30 * 60 * 1000L;
    private static final Map<String, Long> subtitleSearchMisses = new ConcurrentHashMap<>();

    private CoordinatorLayout coordinatorLayout;
    private TextView titleView;
    private ImageButton buttonOpen;
    private ImageButton buttonPlaylist;
    private ImageButton buttonQuality;
    private ImageButton buttonPiP;
    private ImageButton buttonAspectRatio;
    private ImageButton buttonLock;
    private ImageButton buttonRotation;
    private ImageButton buttonTools;
    private ImageButton buttonTogether;
    private ImageButton buttonUpdate;
    private ImageButton buttonAppSettings;
    private UpdateInfo pendingUpdate;
    private SwipeToUnlockView swipeToUnlock;
    private ImageButton exoSettings;
    private ImageButton exoPlayPause;
    private ProgressBar loadingProgressBar;
    private TextView loadingRateView;
    private PlayerControlView controlView;
    private CustomDefaultTimeBar timeBar;

    private boolean restoreOrientationLock;
    private boolean restorePlayState;
    private boolean restorePlayStateAllowed;
    private boolean play;
    private float subtitlesScale;
    private boolean isScrubbing;
    private boolean scrubbingNoticeable;
    private long scrubbingStart;
    public boolean frameRendered;
    private boolean alive;
    public static boolean focusPlay = false;
    private Uri nextUri;
    private static boolean isTvBox;
    public static boolean locked = false;
    private Thread nextUriThread;
    public Thread frameRateSwitchThread;

    public static boolean restoreControllerTimeout = false;
    public static boolean shortControllerTimeout = false;

    final Rational rationalLimitWide = new Rational(239, 100);
    final Rational rationalLimitTall = new Rational(100, 239);

    static final String API_POSITION = "position";
    static final String API_DURATION = "duration";
    static final String API_RETURN_RESULT = "return_result";
    static final String API_SUBS = "subs";
    static final String API_SUBS_ENABLE = "subs.enable";
    static final String API_SUBS_NAME = "subs.name";
    static final String API_TITLE = "title";
    static final String API_END_BY = "end_by";
    static final String API_HEADERS = "headers";
    boolean apiAccess;
    boolean apiAccessPartial;
    String apiTitle;
    List<MediaItem.SubtitleConfiguration> apiSubs = new ArrayList<>();
    boolean intentReturnResult;
    boolean playbackFinished;
    final HashMap<String, String> apiHeaders = new HashMap<>();
    private LampaPlaylist lampaPlaylist;
    private String subtitleSearchStarted;
    private Thread subtitleSearchThread;
    private volatile int subtitleSearchGeneration;
    private boolean switchingPlaylistItem;
    private boolean inPip;
    private boolean playlistCurrentRecorded;
    private String playlistPlaybackKey;
    private boolean playlistPlaybackEverReady;
    private boolean lampaIptv;
    private TextView roomPill;
    private TextView roomMessage;
    private TogetherManager together;
    private boolean applyingRoomMedia;
    private boolean awaitingRoomMedia;
    private boolean alternateStreamTypeTried;
    private boolean decoderQualityFallbackTried;
    private boolean decoderCompatibilityMode;
    private boolean decoderCompatibilityTried;
    private String decoderCompatibilityUri;
    private boolean forceHevcForDolbyVision;
    private boolean pendingStuckRecovery;
    private String stuckRecoveryAttemptedUri;
    private int selectedVideoQualityMode = VideoQualityChoice.MODE_AUTO;
    private TrackGroup selectedVideoTrackGroup;
    private int selectedVideoTrackIndex = -1;
    private CustomDefaultTrackNameProvider trackNameProvider;
    private Dv7Converter dv7Converter;
    private final Map<String, List<TrackMetadata>> containerTracks = new ConcurrentHashMap<>();
    private final Map<String, Long> contentLengths = new ConcurrentHashMap<>();
    private final Map<String, String> resolvedTrackNames = new HashMap<>();
    private int av1DroppedFrames;
    private int totalDroppedFrames;
    private long bandwidthBitrate;
    private long sampledTransferBitrate;
    private long transferSampleBytes;
    private long transferSampleAt;
    private String videoDecoderName;
    private String audioDecoderName;
    private String forcedStreamMimeType;
    private volatile String resolverControlUri;
    private volatile String detectedManifestUri;
    private volatile String detectedManifestType;
    private String playbackRecoveryKey;
    private int sourceRecoveryAttempts;
    private int compatibilityRecoveryAttempts;
    private int liveRecoveryAttempts;
    private long lastLiveRecoveryAt;
    private long playbackWaitStartedAt;
    private long lastPositionAdvanceAt;
    private long lastObservedPosition = C.TIME_UNSET;
    private long stablePlaybackStartedAt;
    private long stablePlaybackStartPosition = C.TIME_UNSET;
    private boolean playbackEverReady;
    private long loadWatchdogBytes;
    private final Runnable loadTimeoutRunnable = this::reportLoadWatchdog;
    private final Runnable swipeHider = this::hideSwipeToUnlock;
    private final Runnable frameRateGiveUpRunnable = this::frameRateSettled;
    private final Runnable stallWatchdogRunnable = new Runnable() {
        @Override public void run() {
            if (player == null || !player.isPlaying()) return;
            long now = SystemClock.elapsedRealtime();
            long position = player.getCurrentPosition();
            if (lastObservedPosition == C.TIME_UNSET || position > lastObservedPosition + 250) {
                lastObservedPosition = position;
                lastPositionAdvanceAt = now;
            } else if (now - lastPositionAdvanceAt >= STALL_TIMEOUT_MS) {
                PlaybackRecoveryPolicy.FailureKind kind = player.isCurrentMediaItemLive()
                        ? PlaybackRecoveryPolicy.FailureKind.LIVE_STALL
                        : (playbackEverReady && position - stablePlaybackStartPosition >= 2_000
                        ? PlaybackRecoveryPolicy.FailureKind.STALL_MIDSTREAM
                        : PlaybackRecoveryPolicy.FailureKind.STALL_AT_START);
                if (!recoverPlayback(kind)) stopPlaybackAfterRecoveryFailure(kind, null);
                return;
            }
            playerView.postDelayed(this, STALL_CHECK_INTERVAL_MS);
        }
    };
    private final Runnable stablePlaybackRunnable = () -> {
        if (player == null || !player.isPlaying() || player.getPlaybackState() != Player.STATE_READY) return;
        long progress = player.getCurrentPosition() - stablePlaybackStartPosition;
        if (SystemClock.elapsedRealtime() - stablePlaybackStartedAt >= STABLE_PLAYBACK_MS
                && progress >= 5_000) {
            sourceRecoveryAttempts = 0;
            liveRecoveryAttempts = LiveRecoveryPolicy.effectiveAttempts(liveRecoveryAttempts,
                    SystemClock.elapsedRealtime(), lastLiveRecoveryAt);
        }
    };
    private final AudioRecoveryState audioRecoveryState = new AudioRecoveryState();
    private boolean audioRestartInFlight;
    private int audioRestartRetries;
    private final Runnable audioRestartRunnable = this::restartPassthroughAudio;
    private final TvSeekController tvSeekController = new TvSeekController();
    private final BackExitGuard backExitGuard = new BackExitGuard(2_000L);
    private final SleepTimerController sleepTimer = new SleepTimerController(SystemClock::elapsedRealtime);
    private final Runnable sleepTimerRunnable = new Runnable() {
        @Override public void run() {
            SleepTimerController.Tick tick = sleepTimer.tick(false);
            if (tick.fire) {
                fireSleepTimer();
                return;
            }
            if (player != null) player.setVolume(basePlayerVolume() * tick.volumeFactor);
            if (sleepTimer.isArmed()) {
                playerView.postDelayed(this, tick.remainingMs <= 30_000 ? 250 : 1000);
            }
        }
    };
    private final ResolverResponseDataSource.Listener resolverResponseListener =
            new ResolverResponseDataSource.Listener() {
                @Override
                public void onResolverControlResponse(Uri requestedUri) {
                    resolverControlUri = requestedUri == null ? null : requestedUri.toString();
                }

                @Override
                public void onManifestTypeDetected(Uri requestedUri, String mimeType) {
                    detectedManifestUri = requestedUri == null ? null : requestedUri.toString();
                    detectedManifestType = mimeType;
                }
            };
    private final TrackNameParsingDataSource.Listener trackNameListener =
            new TrackNameParsingDataSource.Listener() {
                @Override public void onMetadataParsed(Uri originalUri,
                                                       List<TrackMetadata> tracks) {
                    if (originalUri == null || tracks == null || tracks.isEmpty()) return;
                    containerTracks.put(originalUri.toString(),
                            Collections.unmodifiableList(new ArrayList<>(tracks)));
                    runOnUiThread(() -> onContainerMetadata(originalUri));
                }

                @Override public boolean isMetadataParsed(Uri originalUri) {
                    return originalUri != null
                            && containerTracks.containsKey(originalUri.toString());
                }

                @Override public void onContentLength(Uri originalUri, long length) {
                    if (originalUri != null && length > 0) {
                        contentLengths.put(originalUri.toString(), length);
                    }
                }

                @Override public void onMediaTypeResolved(Uri requestedUri, String mimeType) {
                    detectedManifestUri = requestedUri == null ? null : requestedUri.toString();
                    detectedManifestType = mimeType;
                }

                @Override public void onResolverNotReady(Uri requestedUri) {
                    resolverControlUri = requestedUri == null ? null : requestedUri.toString();
                }
            };
    private final Handler lampaUiHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat lampaClockFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private LinearLayout lampaTopPanel;
    private ImageView lampaTopThumbnail;
    private TextView lampaEpisodeBadge;
    private TextView lampaTopTitle;
    private TextView lampaTopDetails;
    private TextView lampaClock;
    private TextView lampaFinishTime;
    private LinearLayout lampaSkipPanel;
    private TextView lampaSkipButton;
    private ProgressBar lampaSkipProgress;
    private final SkipController skipController = new SkipController();
    private SkipController.Model skipModel;
    private String focusedSkipKey;
    private boolean skipPlaylistAdvance;
    private int skipUndoPlaylistIndex = -1;
    private long pendingPlaylistRestorePosition = C.TIME_UNSET;
    private int skipKeyUpToConsume;
    private final Runnable primaryTvFocusRunnable = this::requestPrimaryTvFocus;
    private View exoPrevious;
    private View exoNext;
    private final Runnable lampaUiTicker = new Runnable() {
        @Override public void run() {
            updateLampaRuntimeUi();
            lampaUiHandler.postDelayed(this, 1000);
        }
    };
    private final AnalyticsListener lampaPerformanceListener = new AnalyticsListener() {
        @Override
        public void onVideoDecoderInitialized(EventTime eventTime, String decoderName,
                                              long initializedTimestampMs, long initializationDurationMs) {
            videoDecoderName = decoderName;
        }

        @Override
        public void onAudioDecoderInitialized(EventTime eventTime, String decoderName,
                                              long initializedTimestampMs, long initializationDurationMs) {
            audioDecoderName = decoderName;
        }

        @Override
        public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs,
                                        long totalBytesLoaded, long bitrateEstimate) {
            bandwidthBitrate = Math.max(0, bitrateEstimate);
        }

        @Override
        public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
            totalDroppedFrames += Math.max(0, droppedFrames);
            if (player == null || decoderQualityFallbackTried || droppedFrames <= 0) return;
            Format format = player.getVideoFormat();
            if (format == null || format.height < 2000
                    || !MimeTypes.VIDEO_AV1.equals(format.sampleMimeType)) return;
            av1DroppedFrames += droppedFrames;
            if (av1DroppedFrames >= 24) fallbackFromSlowAv1(format.height);
        }
    };

    DisplayManager displayManager;
    DisplayManager.DisplayListener displayListener;
    SubtitleFinder subtitleFinder;

    Runnable barsHider = () -> {
        if (playerView != null && !controllerVisible) {
            Utils.toggleSystemUi(PlayerActivity.this, playerView, false);
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Rotate ASAP, before super/inflating to avoid glitches with activity launch animation
        mPrefs = new Prefs(this);
        systemVolume = mPrefs.systemVolume;
        playerVolume = mPrefs.playerVolume;
        maxVolumeBoost = mPrefs.volumeBoost;
        volumeGesturesEnabled = mPrefs.volumeGesturesEnabled;
        brightnessGesturesEnabled = mPrefs.brightnessGesturesEnabled;
        boostLevel = 0;
        Utils.setOrientation(this, mPrefs.orientation);

        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT == 28 && Build.MANUFACTURER.equalsIgnoreCase("xiaomi") &&
                (Build.DEVICE.equalsIgnoreCase("oneday") || Build.DEVICE.equalsIgnoreCase("once"))) {
            setContentView(R.layout.activity_player_textureview);
        } else {
            setContentView(R.layout.activity_player);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            Window window = getWindow();
            if (window != null) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController windowInsetsController = window.getInsetsController();
                if (windowInsetsController != null) {
                    // On Android 12 BEHAVIOR_DEFAULT allows system gestures without visible system bars
                    windowInsetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
                }
            }
        }

        isTvBox = Utils.isTvBox(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }

        if (isTvBox) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        final Intent launchIntent = getIntent();
        final String action = launchIntent.getAction();
        final String type = launchIntent.getType();
        final Room.Invite launchRoomInvite = roomInviteFromIntent(launchIntent);
        awaitingRoomMedia = launchRoomInvite != null;

        if ("com.lampaua.player.action.SHORTCUT_VIDEOS".equals(action)) {
            openFile(Utils.getMoviesFolderUri());
        } else if (launchRoomInvite != null) {
            // The room sends its media session after the relay connection is established.
        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String text = launchIntent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                final Uri parsedUri = Uri.parse(text);
                if (parsedUri.isAbsolute()) {
                    mPrefs.updateMedia(this, parsedUri, null);
                    focusPlay = true;
                }
            }
        } else if (launchIntent.getData() != null) {
            resetApiAccess();
            final Uri uri = launchIntent.getData();
            if (SubtitleUtils.isSubtitle(uri, type)) {
                handleSubtitles(uri);
            } else {
                Bundle bundle = launchIntent.getExtras();
                if (bundle != null) {
                    apiAccess = bundle.containsKey(API_POSITION) || bundle.containsKey(API_RETURN_RESULT)
                            || bundle.containsKey(API_SUBS) || bundle.containsKey(API_SUBS_ENABLE)
                            || bundle.containsKey(API_HEADERS);
                    if (apiAccess) {
                        mPrefs.setPersistent(false);
                    } else if (bundle.containsKey(API_TITLE)) {
                        apiAccessPartial = true;
                    }
                    apiTitle = bundle.getString(API_TITLE);
                    readApiHeaders(bundle);
                }

                mPrefs.updateMedia(this, uri, type);

                readApiSubtitles(bundle);

                if (apiSubs.isEmpty()) {
                    searchSubtitles();
                }

                if (bundle != null) {
                    intentReturnResult = bundle.getBoolean(API_RETURN_RESULT);

                    if (bundle.containsKey(API_POSITION)) {
                        mPrefs.updatePosition((long) bundle.getInt(API_POSITION));
                    }
                }
            }
            focusPlay = true;
        }

        if (launchRoomInvite == null) {
            readLampaPlaylist(launchIntent);
        }

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playerView = findViewById(R.id.video_view);
        exoPlayPause = findViewById(R.id.exo_play_pause);
        loadingProgressBar = findViewById(R.id.loading);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loadingProgressBar.setDefaultFocusHighlightEnabled(false);
        }
        loadingRateView = new TextView(this);
        loadingRateView.setTextColor(Color.WHITE);
        loadingRateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        loadingRateView.setGravity(Gravity.CENTER);
        loadingRateView.setVisibility(View.GONE);
        FrameLayout.LayoutParams rateParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        rateParams.topMargin = Utils.dpToPx(58);
        playerView.addView(loadingRateView, rateParams);

        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowFastForwardButton(false);
        playerView.setShowRewindButton(false);

        playerView.setRepeatToggleModes(Player.REPEAT_MODE_ONE);

        playerView.setControllerHideOnTouch(false);
        playerView.setControllerAutoShow(true);

        ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(false);

        timeBar = playerView.findViewById(R.id.exo_progress);
        timeBar.setFocusable(true);
        timeBar.setFocusableInTouchMode(true);
        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                if (player == null) {
                    return;
                }
                restorePlayState = player.isPlaying();
                if (restorePlayState) {
                    player.pause();
                }
                scrubbingNoticeable = false;
                isScrubbing = true;
                frameRendered = true;
                playerView.setControllerShowTimeoutMs(-1);
                scrubbingStart = player.getCurrentPosition();
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                reportScrubbing(position);
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                reportScrubbing(position);
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                playerView.setCustomErrorMessage(null);
                isScrubbing = false;
                if (restorePlayState) {
                    restorePlayState = false;
                    playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    if (player != null) {
                        player.setPlayWhenReady(true);
                    }
                }
            }
        });

        buttonOpen = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonOpen.setImageResource(R.drawable.ic_folder_open_24dp);
        buttonOpen.setId(View.generateViewId());
        buttonOpen.setContentDescription(getString(R.string.button_open));

        buttonOpen.setOnClickListener(view -> openFile(mPrefs.mediaUri));

        buttonOpen.setOnLongClickListener(view -> {
            if (!isTvBox && mPrefs.askScope) {
                askForScope(true, false);
            } else {
                loadSubtitleFile(mPrefs.mediaUri);
            }
            return true;
        });

        buttonPlaylist = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonPlaylist.setImageResource(R.drawable.ic_playlist_play_24dp);
        buttonPlaylist.setId(View.generateViewId());
        buttonPlaylist.setContentDescription(getString(R.string.button_playlist));
        buttonPlaylist.setVisibility(lampaPlaylist != null && lampaPlaylist.size() > 1 ? View.VISIBLE : View.GONE);
        buttonPlaylist.setOnClickListener(view -> showLampaPlaylist());

        buttonQuality = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonQuality.setImageResource(R.drawable.ic_high_quality_24dp);
        buttonQuality.setContentDescription(getString(R.string.button_quality));
        buttonQuality.setOnClickListener(view -> showQualityDialog());

        buttonAppSettings = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonAppSettings.setImageResource(R.drawable.ic_settings_24dp);
        buttonAppSettings.setContentDescription(getString(R.string.button_app_settings));
        buttonAppSettings.setOnClickListener(view -> openAppSettings());

        buttonUpdate = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonUpdate.setImageResource(R.drawable.ic_update_24dp);
        buttonUpdate.setContentDescription(getString(R.string.button_update));
        buttonUpdate.setVisibility(View.GONE);
        buttonUpdate.setOnClickListener(view -> showPendingUpdate(true));

        buttonTools = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonTools.setImageResource(R.drawable.ic_more_vert_24dp);
        buttonTools.setContentDescription(getString(R.string.player_tools));
        buttonTools.setOnClickListener(view -> showPlayerTools());

        buttonTogether = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonTogether.setImageResource(R.drawable.ic_together_24dp);
        buttonTogether.setContentDescription(getString(R.string.together_title));
        buttonTogether.setVisibility(View.GONE);
        buttonTogether.setOnClickListener(view -> showTogetherMenu());

        if (Utils.isPiPSupported(this)) {
            // TODO: Android 12 improvements:
            // https://developer.android.com/about/versions/12/features/pip-improvements
            mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
            boolean success = updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, R.string.exo_controls_play_description, CONTROL_TYPE_PLAY, REQUEST_PLAY);

            if (success) {
                buttonPiP = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
                buttonPiP.setContentDescription(getString(R.string.button_pip));
                buttonPiP.setImageResource(R.drawable.ic_picture_in_picture_alt_24dp);

                buttonPiP.setOnClickListener(view -> enterPiP());
            }
        }

        buttonAspectRatio = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonAspectRatio.setId(Integer.MAX_VALUE - 100);
        buttonAspectRatio.setContentDescription(getString(R.string.button_crop));
        updatebuttonAspectRatioIcon();
        buttonAspectRatio.setOnClickListener(view -> {
            applyVideoScaleMode(VideoScaleMode.nextQuickMode(currentVideoScaleMode()));
            resetHideCallbacks();
        });
        buttonAspectRatio.setOnLongClickListener(v -> {
            showVideoScaleModePicker();
            return true;
        });

        buttonLock = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonLock.setImageResource(R.drawable.ic_lock_24dp);
        buttonLock.setContentDescription(getString(R.string.button_lock));
        buttonLock.setOnClickListener(view -> playerView.toggleLock());
        buttonRotation = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonRotation.setContentDescription(getString(R.string.button_rotate));
        updateButtonRotation();
        buttonRotation.setOnClickListener(view -> {
            mPrefs.orientation = Utils.getNextOrientation(mPrefs.orientation);
            Utils.setOrientation(PlayerActivity.this, mPrefs.orientation);
            updateButtonRotation();
            Utils.showText(playerView, getString(mPrefs.orientation.description), 2500);
            resetHideCallbacks();
        });

        final int titleViewPaddingHorizontal = Utils.dpToPx(14);
        final int titleViewPaddingVertical = getResources().getDimensionPixelOffset(R.dimen.exo_styled_bottom_bar_time_padding);
        FrameLayout centerView = playerView.findViewById(R.id.exo_controls_background);
        titleView = new TextView(this);
        titleView.setBackgroundResource(R.color.ui_controls_background);
        titleView.setTextColor(Color.WHITE);
        titleView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setPadding(titleViewPaddingHorizontal, titleViewPaddingVertical, titleViewPaddingHorizontal, titleViewPaddingVertical);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setVisibility(View.GONE);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        centerView.addView(titleView);
        setupLampaOverlay(centerView);
        setupTogetherOverlay();

        if (!isTvBox) {
            swipeToUnlock = new SwipeToUnlockView(this);
            swipeToUnlock.setVisibility(View.GONE);
            swipeToUnlock.setOnUnlockListener(() -> {
                locked = false;
                onLockChanged();
            });
            swipeToUnlock.setOnStartTouchingListener(() ->
                    playerView.removeCallbacks(swipeHider));
            swipeToUnlock.setOnStopTouchingListener(this::rescheduleSwipeHide);
            CoordinatorLayout.LayoutParams swipeParams = new CoordinatorLayout.LayoutParams(
                    Utils.dpToPx(310), ViewGroup.LayoutParams.WRAP_CONTENT);
            swipeParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            swipeParams.bottomMargin = Utils.dpToPx(70);
            coordinatorLayout.addView(swipeToUnlock, swipeParams);
        }

        exoPrevious = playerView.findViewById(R.id.lampaua_prev);
        exoNext = playerView.findViewById(R.id.lampaua_next);
        if (exoPrevious != null) {
            exoPrevious.setOnClickListener(view -> playRelativeEpisode(-1));
        }
        if (exoNext != null) {
            exoNext.setOnClickListener(view -> playRelativeEpisode(1));
        }
        updateEpisodeControls();

        titleView.setOnLongClickListener(view -> {
            // Prevent FileUriExposedException
            if (mPrefs.mediaUri != null && ContentResolver.SCHEME_FILE.equals(mPrefs.mediaUri.getScheme())) {
                return false;
            }

            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, mPrefs.mediaUri);
            if (mPrefs.mediaType == null)
                shareIntent.setType("video/*");
            else
                shareIntent.setType(mPrefs.mediaType);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Start without intent chooser to allow any target to be set as default
            startActivity(shareIntent);

            return true;
        });

        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        controlView = playerView.findViewById(R.id.exo_controller);
        controlView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (windowInsets != null) {
                if (Build.VERSION.SDK_INT >= 31) {
                    boolean visibleBars = windowInsets.isVisible(WindowInsets.Type.statusBars());
                    if (visibleBars && !controllerVisible) {
                        playerView.postDelayed(barsHider, 2500);
                    } else {
                        playerView.removeCallbacks(barsHider);
                    }
                }

                int insetLeft = windowInsets.getSystemWindowInsetLeft();
                int insetRight = windowInsets.getSystemWindowInsetRight();

                int paddingLeft = 0;
                int marginLeft = insetLeft;

                int paddingRight = 0;
                int marginRight = insetRight;

                if (Build.VERSION.SDK_INT >= 28 && windowInsets.getDisplayCutout() != null) {
                    if (windowInsets.getDisplayCutout().getSafeInsetLeft() == insetLeft) {
                        paddingLeft = insetLeft;
                        marginLeft = 0;
                    }
                    if (windowInsets.getDisplayCutout().getSafeInsetRight() == insetRight) {
                        paddingRight = insetRight;
                        marginRight = 0;
                    }
                }

                int bottomBarPaddingBottom = 0;
                int progressBarMarginBottom = 0;

                if (Build.VERSION.SDK_INT >= 35) {
                    final int left = windowInsets.getInsets(WindowInsets.Type.navigationBars()).left;
                    final int right = windowInsets.getInsets(WindowInsets.Type.navigationBars()).right;

                    final View exoTop = findViewById(R.id.exo_top);
                    exoTop.getLayoutParams().height = windowInsets.getSystemWindowInsetTop();
                    Utils.setViewMargins(exoTop, left, 0, right, 0);

                    final FrameLayout exoBottomBar = findViewById(R.id.exo_bottom_bar);
                    ViewGroup.LayoutParams params = exoBottomBar.getLayoutParams();
                    params.height = getResources().getDimensionPixelSize(R.dimen.exo_styled_bottom_bar_height) + windowInsets.getSystemWindowInsetBottom();
                    exoBottomBar.setLayoutParams(params);

                    findViewById(R.id.exo_left).getLayoutParams().width = left;
                    findViewById(R.id.exo_right).getLayoutParams().width = right;

                    bottomBarPaddingBottom = windowInsets.getSystemWindowInsetBottom();
                    progressBarMarginBottom = windowInsets.getSystemWindowInsetBottom();
                } else {
                    view.setPadding(0, windowInsets.getSystemWindowInsetTop(),0, windowInsets.getSystemWindowInsetBottom());
                }

                Utils.setViewParams(titleView, paddingLeft + titleViewPaddingHorizontal, titleViewPaddingVertical, paddingRight + titleViewPaddingHorizontal, titleViewPaddingVertical,
                        marginLeft, windowInsets.getSystemWindowInsetTop(), marginRight, 0);

                Utils.setViewParams(findViewById(R.id.exo_bottom_bar), paddingLeft, 0, paddingRight, bottomBarPaddingBottom,
                        marginLeft, 0, marginRight, 0);

                Utils.setViewParams(findViewById(R.id.exo_progress), windowInsets.getSystemWindowInsetLeft(), 0, windowInsets.getSystemWindowInsetRight(), 0,
                        0, 0, 0, getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom) + progressBarMarginBottom);

                Utils.setViewMargins(findViewById(R.id.exo_error_message), 0, windowInsets.getSystemWindowInsetTop() / 2, 0, getResources().getDimensionPixelSize(R.dimen.exo_error_message_margin_bottom) + windowInsets.getSystemWindowInsetBottom() / 2);

                windowInsets.consumeSystemWindowInsets();
            }
            return windowInsets;
        });
        timeBar.setAdMarkerColor(Color.argb(0x00, 0xFF, 0xFF, 0xFF));
        timeBar.setPlayedAdMarkerColor(Color.argb(0x98, 0xFF, 0xFF, 0xFF));
        timeBar.setPlayedColor(Color.rgb(211, 165, 24));
        timeBar.setScrubberColor(Color.rgb(231, 190, 47));
        timeBar.setBufferedColor(Color.rgb(42, 78, 121));

        try {
            trackNameProvider = new CustomDefaultTrackNameProvider(getResources());
            final Field field = PlayerControlView.class.getDeclaredField("trackNameProvider");
            field.setAccessible(true);
            field.set(controlView, trackNameProvider);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        findViewById(R.id.delete).setOnClickListener(view -> askDeleteMedia());

        findViewById(R.id.next).setOnClickListener(view -> {
            if (!isTvBox && mPrefs.askScope) {
                askForScope(false, true);
            } else {
                skipToNext();
            }
        });

        exoPlayPause.setOnClickListener(view -> dispatchPlayPause());

        // Prevent double tap actions in controller
        findViewById(R.id.exo_bottom_bar).setOnTouchListener((v, event) -> true);
        //titleView.setOnTouchListener((v, event) -> true);

        playerListener = new PlayerListener();

        mBrightnessControl = new BrightnessControl(this);
        if (mPrefs.brightness >= 0) {
            mBrightnessControl.currentBrightnessLevel = mPrefs.brightness;
            mBrightnessControl.setScreenBrightness(mBrightnessControl.levelToBrightness(mBrightnessControl.currentBrightnessLevel));
        }
        playerView.setBrightnessControl(mBrightnessControl);

        final LinearLayout exoBasicControls = playerView.findViewById(R.id.exo_basic_controls);
        final ImageButton exoSubtitle = exoBasicControls.findViewById(R.id.exo_subtitle);
        exoBasicControls.removeView(exoSubtitle);

        exoSettings = exoBasicControls.findViewById(R.id.exo_settings);
        exoBasicControls.removeView(exoSettings);
        exoSettings.setImageResource(R.drawable.ic_tune_24dp);
        exoSettings.setContentDescription(getString(R.string.button_playback_options));
        final ImageButton exoRepeat = exoBasicControls.findViewById(R.id.exo_repeat_toggle);
        exoBasicControls.removeView(exoRepeat);
        //exoBasicControls.setVisibility(View.GONE);

        exoSettings.setOnLongClickListener(view -> {
            openAppSettings();
            return true;
        });

        exoSubtitle.setOnLongClickListener(v -> {
            openAppSettings("languageSubtitle");
            return true;
        });

        updateButtons(false);

        final HorizontalScrollView horizontalScrollView = (HorizontalScrollView) getLayoutInflater().inflate(R.layout.controls, null);
        final LinearLayout controls = horizontalScrollView.findViewById(R.id.controls);

        controls.addView(buttonOpen);
        controls.addView(buttonPlaylist);
        controls.addView(buttonQuality);
        controls.addView(exoSubtitle);
        controls.addView(buttonAspectRatio);
        if (!isTvBox) controls.addView(buttonLock);
        if (Utils.isPiPSupported(this) && buttonPiP != null) {
            controls.addView(buttonPiP);
        }
        if (mPrefs.repeatToggle) {
            controls.addView(exoRepeat);
        }
        if (!isTvBox) {
            controls.addView(buttonRotation);
        }
        controls.addView(exoSettings);
        controls.addView(buttonTogether);
        controls.addView(buttonTools);
        controls.addView(buttonUpdate);
        controls.addView(buttonAppSettings);
        styleTvBottomControls(controls);

        exoBasicControls.addView(horizontalScrollView);

        if (Build.VERSION.SDK_INT > 23) {
            horizontalScrollView.setOnScrollChangeListener((view, i, i1, i2, i3) -> resetHideCallbacks());
        }

        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                controllerVisible = visibility == View.VISIBLE;
                controllerVisibleFully = playerView.isControllerFullyVisible();
                if (lampaTopPanel != null) {
                    lampaTopPanel.setVisibility(controllerVisible ? View.VISIBLE : View.GONE);
                }
                updateRoomBadge();

                if (PlayerActivity.restoreControllerTimeout) {
                    restoreControllerTimeout = false;
                    if (player == null || !player.isPlaying()) {
                        playerView.setControllerShowTimeoutMs(-1);
                    } else {
                        playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    }
                }

                // https://developer.android.com/training/system-ui/immersive
                Utils.toggleSystemUi(PlayerActivity.this, playerView, visibility == View.VISIBLE);
                if (visibility == View.VISIBLE) {
                    requestPrimaryTvFocus();
                }

                if (controllerVisible && playerView.isControllerFullyVisible()) {
                    if (mPrefs.firstRun) {
                        TapTargetView.showFor(PlayerActivity.this,
                                TapTarget.forView(buttonOpen, getString(R.string.onboarding_open_title), getString(R.string.onboarding_open_description))
                                        .outerCircleColor(R.color.green)
                                        .targetCircleColor(R.color.white)
                                        .titleTextSize(22)
                                        .titleTextColor(R.color.white)
                                        .descriptionTextSize(14)
                                        .cancelable(true),
                                new TapTargetView.Listener() {
                                    @Override
                                    public void onTargetClick(TapTargetView view) {
                                        super.onTargetClick(view);
                                        buttonOpen.performClick();
                                    }
                                });
                        // TODO: Explain gestures?
                        //  "Use vertical and horizontal gestures to change brightness, volume and seek in video"
                        mPrefs.markFirstRun();
                    }
                    if (errorToShow != null) {
                        showError(errorToShow);
                        errorToShow = null;
                    }
                }
            }
        });

        youTubeOverlay = findViewById(R.id.youtube_overlay);
        youTubeOverlay.performListener(new YouTubeOverlay.PerformListener() {
            @Override
            public void onAnimationStart() {
                youTubeOverlay.setAlpha(1.0f);
                youTubeOverlay.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd() {
                youTubeOverlay.animate()
                        .alpha(0.0f)
                        .setDuration(300)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                youTubeOverlay.setVisibility(View.GONE);
                                youTubeOverlay.setAlpha(1.0f);
                            }
                        });
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (useMediaStore()) {
                Utils.scanMediaStorage(this);
            }
        }
        pendingUpdate = Updater.pending(this);
        updatePendingButton();
        checkForUpdates(false);
        if (launchRoomInvite != null) {
            joinRoom(launchRoomInvite.code, launchRoomInvite.password);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        alive = true;
        updateSubtitleStyle(this);
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
            Utils.toggleSystemUi(this, playerView, true);
        }
        if (!awaitingRoomMedia) {
            initializePlayer();
        }
        if (together != null) {
            together.resume();
        }
        updateRoomBadge();
        registerAudioOutputReceiver();
        updateButtonRotation();
        lampaUiHandler.removeCallbacks(lampaUiTicker);
        lampaUiHandler.post(lampaUiTicker);
        if (sleepTimer.isArmed() && !sleepTimer.isAtMediaEnd()) {
            playerView.removeCallbacks(sleepTimerRunnable);
            playerView.post(sleepTimerRunnable);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        restorePlayStateAllowed = true;
        audioRecoveryState.onResume();
        if (player != null && player.isPlaying() && audioRecoveryState.shouldRebuildSink()) {
            requestPassthroughAudioRestart();
        }
        updateLampaMenuOpacity();
        postPrimaryTvFocus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) postPrimaryTvFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        audioRecoveryState.onPause();
        savePlayer();
    }

    @Override
    public void onStop() {
        super.onStop();
        alive = false;
        if (together != null) {
            together.suspend();
        }
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
        }
        playerView.setCustomErrorMessage(null);
        playerView.removeCallbacks(sleepTimerRunnable);
        lampaUiHandler.removeCallbacks(lampaUiTicker);
        unregisterAudioOutputReceiver();
        releasePlayer(false);
    }

    @Override
    protected void onDestroy() {
        hideSwipeToUnlock();
        if (together != null) {
            together.leave();
        }
        super.onDestroy();
    }

    private void registerAudioOutputReceiver() {
        if (audioOutputReceiver != null) return;
        audioOutputReceiverPrimed = false;
        audioOutputReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!AudioManager.ACTION_HDMI_AUDIO_PLUG.equals(intent.getAction())) return;
                if (!audioOutputReceiverPrimed) {
                    audioOutputReceiverPrimed = true;
                    return;
                }
                audioRecoveryState.onAudioOutputChanged();
                if (player != null && player.isPlaying()) requestPassthroughAudioRestart();
            }
        };
        ContextCompat.registerReceiver(this, audioOutputReceiver,
                new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterAudioOutputReceiver() {
        if (audioOutputReceiver == null) return;
        unregisterReceiver(audioOutputReceiver);
        audioOutputReceiver = null;
        audioOutputReceiverPrimed = false;
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (locked) {
            if (!backExitGuard.shouldExit(SystemClock.elapsedRealtime())) {
                showSwipeToUnlock();
                Utils.showText(playerView, getString(R.string.press_back_again), 2_000);
                return;
            }
        } else if (isTvBox && haveMedia) {
            if (tvSeekController.isArmed()) {
                tvSeekController.reset();
                backExitGuard.reset();
                if (playerView != null) {
                    playerView.removeCallbacks(playerView.textClearRunnable);
                    playerView.textClearRunnable.run();
                }
                return;
            }
            if (controllerVisible) {
                backExitGuard.reset();
                playerView.hideController();
                return;
            }
            if (!backExitGuard.shouldExit(SystemClock.elapsedRealtime())) {
                Utils.showText(playerView, getString(R.string.press_back_again), 2_000);
                return;
            }
        }
        restorePlayStateAllowed = false;
        super.onBackPressed();
    }

    @Override
    public void finish() {
        if (together != null) {
            together.leave();
        }
        if (intentReturnResult) {
            Intent intent = new Intent("com.mxtech.intent.result.VIEW");
            intent.putExtra(API_END_BY, playbackFinished ? "playback_completion" : "user");
            if (!playbackFinished) {
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration != C.TIME_UNSET) {
                        intent.putExtra(API_DURATION, (int) player.getDuration());
                    }
                    if (player.isCurrentMediaItemSeekable()) {
                        if (mPrefs.persistentMode) {
                            intent.putExtra(API_POSITION, (int) mPrefs.nonPersitentPosition);
                        } else {
                            intent.putExtra(API_POSITION, (int) player.getCurrentPosition());
                        }
                    }
                }
            }
            if (lampaPlaylist != null && !lampaPlaylist.isEmpty()) {
                recordCurrentPlaylistItem(playbackFinished);
                LampaPlaylist.Item current = lampaPlaylist.getCurrent();
                intent.putExtra(LampaPlaylist.EXTRA_PLAYLIST_INDEX, lampaPlaylist.getCurrentIndex());
                if (current != null && current.url != null) {
                    intent.putExtra(LampaPlaylist.EXTRA_CURRENT_URL, current.url);
                    // Official LAMPA identifies the active playlist entry by
                    // the result Intent data URI.
                    intent.setData(Uri.parse(current.url));
                }
                intent.putExtra(LampaPlaylist.EXTRA_PLAYBACK_RESULTS, lampaPlaylist.getPlaybackResultsJson());
            }
            setResult(Activity.RESULT_OK, intent);
        }

        super.finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        backExitGuard.reset();
        if (intent == null) {
            return;
        }
        if (handleRoomIntent(intent)) {
            return;
        }
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            applyViewIntent(intent, true);
        } else if (Intent.ACTION_SEND.equals(action)
                && "text/plain".equals(intent.getType())) {
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                final Uri parsedUri = Uri.parse(text.trim());
                if (parsedUri.isAbsolute()) {
                    final Intent view = new Intent(Intent.ACTION_VIEW).setData(parsedUri);
                    applyViewIntent(view, true);
                }
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isSkipActionEnabled()
                && (keyCode == KeyEvent.KEYCODE_BUTTON_START
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            activateSkipModel();
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (player == null)
                    break;
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    player.pause();
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    player.play();
                } else if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                Utils.adjustVolume(this, mAudioManager, playerView, keyCode == KeyEvent.KEYCODE_VOLUME_UP, event.getRepeatCount() == 0, true);
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (player == null)
                    break;
                if (!controllerVisibleFully) {
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!controllerVisibleFully || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                    return previewTvSeek(TvSeekController.BACKWARD, event);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!controllerVisibleFully || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    return previewTvSeek(TvSeekController.FORWARD, event);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (isTvBox && !controllerVisibleFully) {
                    playerView.showController();
                    playerView.post(timeBar::requestFocus);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                // Let Activity/OnBackInvokedDispatcher route Back through onBackPressed().
                break;
            case KeyEvent.KEYCODE_UNKNOWN:
                return super.onKeyDown(keyCode, event);
            default:
                if (!controllerVisibleFully) {
                    playerView.showController();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                playerView.postDelayed(playerView.textClearRunnable, CustomPlayerView.MESSAGE_TIMEOUT_KEY);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (tvSeekController.isArmed()) {
                    commitTvSeek();
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean previewTvSeek(int direction, KeyEvent event) {
        if (player == null || !player.isCurrentMediaItemSeekable()) return false;
        playerView.removeCallbacks(playerView.textClearRunnable);
        long eventTime = event == null ? SystemClock.uptimeMillis() : event.getEventTime();
        if (event != null && event.getRepeatCount() > 0) {
            tvSeekController.hold(direction, eventTime);
        } else {
            tvSeekController.press(direction, eventTime);
        }
        long current = player.getCurrentPosition();
        long target = tvSeekController.previewTarget(current, player.getDuration());
        playerView.setCustomErrorMessage(Utils.formatMilisSign(target - current)
                + "\n" + Utils.formatMilis(target));
        return true;
    }

    private void commitTvSeek() {
        if (player == null || !tvSeekController.isArmed()) return;
        long current = player.getCurrentPosition();
        long target = tvSeekController.consumeTarget(current, player.getDuration());
        player.setSeekParameters(target < current
                ? SeekParameters.PREVIOUS_SYNC : SeekParameters.NEXT_SYNC);
        player.seekTo(target);
        playerView.postDelayed(playerView.textClearRunnable, 1000);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int lampaKeyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && isSkipActionEnabled()
                && (lampaKeyCode == KeyEvent.KEYCODE_BUTTON_START
                || lampaKeyCode == KeyEvent.KEYCODE_BUTTON_A
                || lampaKeyCode == KeyEvent.KEYCODE_BUTTON_SELECT
                || lampaKeyCode == KeyEvent.KEYCODE_ENTER
                || lampaKeyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || lampaKeyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            activateSkipModel();
            skipKeyUpToConsume = lampaKeyCode;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && skipKeyUpToConsume == lampaKeyCode) {
            skipKeyUpToConsume = 0;
            return true;
        }
        if (isScaling) {
            final int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        scale(true);
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        scale(false);
                        break;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        break;
                    default:
                        if (isScaleStarting) {
                            isScaleStarting = false;
                        } else {
                            scaleEnd();
                        }
                }
            }
            return true;
        }

        if (isTvBox && controllerVisibleFully && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
            playerView.hideController();
            return true;
        }

        if (isTvBox && !controllerVisibleFully
                && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                onKeyDown(event.getKeyCode(), event);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                onKeyUp(event.getKeyCode(), event);
            }
            return true;
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (0 != (event.getSource() & InputDevice.SOURCE_CLASS_POINTER)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL:
                    final float value = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    Utils.adjustVolume(this, mAudioManager, playerView, value > 0.0f, Math.abs(value) > 1.0f, true);
                    return true;
            }
        } else if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {
            // TODO: This somehow works, but it would use better filtering
            float value = event.getAxisValue(MotionEvent.AXIS_RZ);
            for (int i = 0; i < event.getHistorySize(); i++) {
                float historical = event.getHistoricalAxisValue(MotionEvent.AXIS_RZ, i);
                if (Math.abs(historical) > value) {
                    value = historical;
                }
            }
            if (Math.abs(value) == 1.0f) {
                Utils.adjustVolume(this, mAudioManager, playerView, value < 0, true, true);
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        inPip = isInPictureInPictureMode;

        if (isInPictureInPictureMode) {
            // On Android TV it is required to hide controller in this PIP change callback
            playerView.hideController();
            hideSwipeToUnlock();
            setSubtitleTextSizePiP();
            playerView.setScale(1.f);
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction()) || player == null) {
                        return;
                    }

                    switch (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        case CONTROL_TYPE_PLAY:
                            player.play();
                            break;
                        case CONTROL_TYPE_PAUSE:
                            player.pause();
                            break;
                    }
                }
            };
            ContextCompat.registerReceiver(this, mReceiver, new IntentFilter(ACTION_MEDIA_CONTROL), ContextCompat.RECEIVER_EXPORTED);
        } else {
            setSubtitleTextSize();
            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            }
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }
            playerView.setControllerAutoShow(true);
            if (player != null) {
                if (player.isPlaying())
                    Utils.toggleSystemUi(this, playerView, false);
                else if (!locked)
                    playerView.showController();
            }
            if (locked) showSwipeToUnlock();
        }
        updateRoomBadge();
    }

    private void applyViewIntent(final Intent intent, final boolean initialize) {
        final Uri uri = intent == null ? null : intent.getData();
        if (uri == null) {
            return;
        }
        final String type = intent.getType();
        if (SubtitleUtils.isSubtitle(uri, type)) {
            handleSubtitles(uri);
            focusPlay = true;
            if (initialize) {
                initializePlayer();
            }
            return;
        }
        if (!applyingRoomMedia && together != null && together.isActive()) {
            together.leave();
        }
        cancelSubtitleSearch();
        awaitingRoomMedia = false;
        setIntent(intent);
        lampaPlaylist = null;
        if (buttonPlaylist != null) {
            buttonPlaylist.setVisibility(View.GONE);
        }
        resetApiAccess();
        final Bundle bundle = intent.getExtras();
        if (bundle != null) {
            apiAccess = bundle.containsKey(API_POSITION) || bundle.containsKey(API_RETURN_RESULT)
                    || bundle.containsKey(API_SUBS) || bundle.containsKey(API_SUBS_ENABLE)
                    || bundle.containsKey(API_HEADERS);
            if (apiAccess) {
                mPrefs.setPersistent(false);
            } else if (bundle.containsKey(API_TITLE)) {
                apiAccessPartial = true;
            }
            apiTitle = bundle.getString(API_TITLE);
            intentReturnResult = bundle.getBoolean(API_RETURN_RESULT);
            readApiHeaders(bundle);
            readApiSubtitles(bundle);
        }
        mPrefs.updateMedia(this, uri, type);
        if (bundle != null && bundle.containsKey(API_POSITION)) {
            mPrefs.updatePosition((long) bundle.getInt(API_POSITION));
        }
        if (apiSubs.isEmpty()) {
            searchSubtitles();
        }
        readLampaPlaylist(intent);
        focusPlay = true;
        updateRoomBadge();
        if (initialize) {
            initializePlayer();
        }
    }

    void resetApiAccess() {
        apiAccess = false;
        apiAccessPartial = false;
        apiTitle = null;
        apiSubs.clear();
        apiHeaders.clear();
        intentReturnResult = false;
        mPrefs.setPersistent(true);
    }

    /**
     * Reads the Lampa/LampaUA external-player header contract. The value is an
     * alternating String array: name, value, name, value. Invalid entries are
     * ignored instead of failing playback.
     */
    void readApiHeaders(Bundle bundle) {
        apiHeaders.clear();
        String[] headers = bundle.getStringArray(API_HEADERS);
        if (headers == null) {
            ArrayList<String> headerList = bundle.getStringArrayList(API_HEADERS);
            if (headerList != null) {
                headers = headerList.toArray(new String[0]);
            }
        }
        if (headers == null) {
            return;
        }
        for (int i = 0; i + 1 < headers.length; i += 2) {
            String name = headers[i];
            String value = headers[i + 1];
            if (name == null || value == null) {
                continue;
            }
            name = name.trim();
            value = value.trim();
            if (name.isEmpty() || value.isEmpty() || "content-length".equalsIgnoreCase(name)) {
                continue;
            }
            apiHeaders.put(name, value);
        }
    }

    void readApiSubtitles(Bundle bundle) {
        apiSubs.clear();
        if (bundle == null) {
            return;
        }
        Uri defaultSub = null;
        Parcelable[] subsEnable = bundle.getParcelableArray(API_SUBS_ENABLE);
        if (subsEnable != null && subsEnable.length > 0 && subsEnable[0] instanceof Uri) {
            defaultSub = (Uri) subsEnable[0];
        }

        Parcelable[] subs = bundle.getParcelableArray(API_SUBS);
        String[] subsName = bundle.getStringArray(API_SUBS_NAME);
        if (subs == null) {
            return;
        }
        for (int i = 0; i < subs.length; i++) {
            if (!(subs[i] instanceof Uri)) {
                continue;
            }
            Uri sub = (Uri) subs[i];
            String name = subsName != null && subsName.length > i ? subsName[i] : null;
            apiSubs.add(SubtitleUtils.buildSubtitle(this, sub, name, sub.equals(defaultSub)));
        }
    }

    private void readLampaPlaylist(Intent intent) {
        if (intent == null) return;
        lampaIptv = intent.getBooleanExtra("lampaua.is_iptv", false);
        String raw = intent.getStringExtra(LampaPlaylist.EXTRA_PLAYLIST_JSON);
        if (raw == null) raw = intent.getStringExtra("playlist_json");
        if (raw == null || raw.trim().isEmpty()) {
            raw = buildOfficialLampaPlaylist(intent);
        }
        if (raw == null || raw.trim().isEmpty()) {
            String imdbId = intent.getStringExtra("lampaua.imdb_id");
            if (imdbId == null) imdbId = intent.getStringExtra("imdb_id");
            String cardId = intent.getStringExtra("id");
            int tmdbId = intent.getIntExtra("lampaua.tmdb_id",
                    intent.getIntExtra("tmdb_id", -1));
            if (((imdbId != null && imdbId.startsWith("tt")) || tmdbId > 0
                    || (cardId != null && !cardId.trim().isEmpty())
                    || intent.hasExtra("quality_levels") || intent.hasExtra("segments")
                    || intent.hasExtra("season") || intent.hasExtra("episode"))
                    && intent.getData() != null) {
                JSONObject item = new JSONObject();
                JSONObject root = new JSONObject();
                JSONArray items = new JSONArray();
                try {
                    item.put("url", intent.getData().toString());
                    item.put("title", intent.getStringExtra("title"));
                    if (item.isNull("title")) item.put("title", intent.getStringExtra("filename"));
                    String thumbnail = intent.getStringExtra("thumbnail");
                    if (thumbnail != null && !thumbnail.trim().isEmpty()) {
                        item.put("thumbnail", thumbnail);
                    }
                    if (cardId != null && !cardId.trim().isEmpty()) item.put("id", cardId);
                    if (imdbId != null && imdbId.startsWith("tt")) item.put("imdb_id", imdbId);
                    if (tmdbId > 0) item.put("tmdb_id", tmdbId);
                    String mediaType = intent.getStringExtra("lampaua.media_type");
                    if (mediaType == null) mediaType = intent.getStringExtra("media_type");
                    if (mediaType != null) item.put("media_type", mediaType);
                    int season = intent.getIntExtra("lampaua.season",
                            intent.getIntExtra("season", -1));
                    int episode = intent.getIntExtra("lampaua.episode",
                            intent.getIntExtra("episode", -1));
                    if (season > 0) item.put("season", season);
                    if (episode > 0) item.put("episode", episode);
                    String segmentJson = intent.getStringExtra("segments");
                    if (segmentJson != null && segmentJson.trim().startsWith("{")) {
                        item.put("segments", new JSONObject(segmentJson));
                    }
                    JSONArray subtitles = officialSubtitles(intent);
                    if (subtitles.length() > 0) item.put("subtitles", subtitles);
                    if (intent.hasExtra(API_POSITION)) {
                        item.put("position_ms", Math.max(0,
                                intent.getIntExtra(API_POSITION, 0)));
                    }
                    putOfficialQuality(item, intent, "quality_levels", "quality_urls");
                    items.put(item);
                    root.put("current_index", 0);
                    root.put("auto_next", false);
                    root.put("items", items);
                    raw = root.toString();
                } catch (JSONException ignored) { }
            }
        }
        if (raw == null || raw.trim().isEmpty()) return;

        try {
            int index = intent.getIntExtra(LampaPlaylist.EXTRA_PLAYLIST_INDEX,
                    intent.getIntExtra("playlist_index", 0));
            boolean autoNext = intent.getBooleanExtra(LampaPlaylist.EXTRA_AUTO_NEXT,
                    intent.getBooleanExtra("auto_next", true));
            lampaPlaylist = LampaPlaylist.fromJson(this, raw, index, autoNext);
            alternateStreamTypeTried = false;
            decoderQualityFallbackTried = false;
            resetDecoderCompatibilityMode();
            resetResolverResponseState();
            forcedStreamMimeType = null;
            if (!lampaPlaylist.isEmpty()) {
                LampaPlaylist.Item current = lampaPlaylist.getCurrent();
                if (current != null && !current.isResolved() && intent.getData() != null) {
                    current.url = intent.getData().toString();
                }
                if (current != null && current.isResolved()) {
                    applyPlaylistItem(current, true);
                }
                apiAccess = true;
                mPrefs.setPersistent(false);
            }
            if (buttonPlaylist != null) {
                buttonPlaylist.setVisibility(lampaPlaylist.size() > 1 ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            lampaPlaylist = null;
            Toast.makeText(this, R.string.playlist_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Converts the public Just+ contract introduced by LAMPA 1.12.6 into the
     * richer internal playlist format. The private lampaua.playlist_json extra
     * is still preferred, so existing LampaUA installations remain compatible.
     */
    private String buildOfficialLampaPlaylist(Intent intent) {
        Parcelable[] parcelables = intent.getParcelableArrayExtra("video_list");
        String[] stringUrls = intent.getStringArrayExtra("video_list");
        int count = parcelables != null ? parcelables.length
                : (stringUrls != null ? stringUrls.length : 0);
        if (count <= 0) return null;

        ArrayList<String> names = stringValues(intent, "video_list.name");
        ArrayList<String> filenames = stringValues(intent, "video_list.filename");
        ArrayList<String> thumbnails = stringValues(intent, "video_list.thumbnail");
        ArrayList<String> segments = stringValues(intent, "video_list.segments");
        ArrayList<String> seasons = stringValues(intent, "video_list.season");
        ArrayList<String> episodes = stringValues(intent, "video_list.episode");
        ArrayList<String> imdbIds = stringValues(intent, "video_list.imdb_id");
        ArrayList<String> tmdbIds = stringValues(intent, "video_list.tmdb_id");
        ArrayList<String> ids = stringValues(intent, "video_list.id");
        ArrayList<Bundle> subtitleBundles = bundleValues(intent, "video_list.subtitles");
        JSONArray items = new JSONArray();
        String currentUrl = intent.getData() == null ? null : intent.getData().toString();
        int currentIndex = 0;

        try {
            for (int i = 0; i < count; i++) {
                String url = null;
                if (parcelables != null && parcelables[i] instanceof Uri) {
                    url = parcelables[i].toString();
                } else if (stringUrls != null) {
                    url = stringUrls[i];
                }
                if (url == null || url.trim().isEmpty()) continue;
                JSONObject item = new JSONObject();
                item.put("url", url);
                putIndexedText(item, "title", names, i);
                if (!item.has("title")) putIndexedText(item, "title", filenames, i);
                putIndexedText(item, "thumbnail", thumbnails, i);
                putIndexedText(item, "imdb_id", imdbIds, i);
                putIndexedPositiveInt(item, "tmdb_id", tmdbIds, i);
                putIndexedText(item, "id", ids, i);
                putIndexedPositiveInt(item, "season", seasons, i);
                putIndexedPositiveInt(item, "episode", episodes, i);

                String segmentJson = indexedText(segments, i);
                if (segmentJson != null && segmentJson.trim().startsWith("{")) {
                    item.put("segments", new JSONObject(segmentJson));
                }
                Bundle subBundle = subtitleBundles != null && i < subtitleBundles.size()
                        ? subtitleBundles.get(i) : null;
                JSONArray subtitles = officialSubtitles(subBundle);
                if (subtitles.length() > 0) item.put("subtitles", subtitles);
                putOfficialQuality(item, intent,
                        "video_list.quality_levels." + i,
                        "video_list.quality_urls." + i);
                if (itemMatchesUrl(item, currentUrl)) {
                    currentIndex = items.length();
                    item.put("url", currentUrl);
                }
                items.put(item);
            }
            if (items.length() == 0) return null;
            JSONObject root = new JSONObject();
            root.put("items", items);
            root.put("current_index", currentIndex);
            root.put("auto_next", true);
            return root.toString();
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static JSONArray officialSubtitles(Bundle bundle) throws JSONException {
        JSONArray result = new JSONArray();
        if (bundle == null) return result;
        Parcelable[] uris = bundle.getParcelableArray("uris");
        String[] names = bundle.getStringArray("names");
        ArrayList<String> nameList = bundle.getStringArrayList("names");
        if (uris == null) return result;
        for (int i = 0; i < uris.length; i++) {
            if (!(uris[i] instanceof Uri)) continue;
            JSONObject subtitle = new JSONObject();
            subtitle.put("url", uris[i].toString());
            if (names != null && i < names.length && names[i] != null) {
                subtitle.put("label", names[i]);
            } else if (nameList != null && i < nameList.size() && nameList.get(i) != null) {
                subtitle.put("label", nameList.get(i));
            }
            result.put(subtitle);
        }
        return result;
    }

    private static JSONArray officialSubtitles(Intent intent) throws JSONException {
        JSONArray result = new JSONArray();
        Parcelable[] uris = intent.getParcelableArrayExtra("subs");
        if (uris == null) uris = intent.getParcelableArrayExtra(API_SUBS);
        String[] names = intent.getStringArrayExtra("subs.name");
        if (names == null) names = intent.getStringArrayExtra(API_SUBS_NAME);
        if (uris == null) return result;
        for (int i = 0; i < uris.length; i++) {
            if (!(uris[i] instanceof Uri)) continue;
            JSONObject subtitle = new JSONObject();
            subtitle.put("url", uris[i].toString());
            if (names != null && i < names.length && names[i] != null) {
                subtitle.put("label", names[i]);
            }
            result.put(subtitle);
        }
        return result;
    }

    private static void putOfficialQuality(JSONObject item, Intent intent,
                                           String levelsKey, String urlsKey) throws JSONException {
        ArrayList<String> levelValues = stringValues(intent, levelsKey);
        if (levelValues == null || levelValues.isEmpty()) return;
        Parcelable[] parcelableUrls = intent.getParcelableArrayExtra(urlsKey);
        String[] stringUrls = intent.getStringArrayExtra(urlsKey);
        ArrayList<Uri> parcelableUrlList = intent.getParcelableArrayListExtra(urlsKey);
        ArrayList<String> stringUrlList = stringValues(intent, urlsKey);
        JSONObject quality = new JSONObject();
        for (int index = 0; index < levelValues.size(); index++) {
            String label = levelValues.get(index);
            String url = null;
            if (parcelableUrls != null && index < parcelableUrls.length
                    && parcelableUrls[index] instanceof Uri) {
                url = parcelableUrls[index].toString();
            } else if (stringUrls != null && index < stringUrls.length) {
                url = stringUrls[index];
            } else if (parcelableUrlList != null && index < parcelableUrlList.size()
                    && parcelableUrlList.get(index) != null) {
                url = parcelableUrlList.get(index).toString();
            } else if (stringUrlList != null && index < stringUrlList.size()) {
                url = stringUrlList.get(index);
            }
            if (label != null && !label.trim().isEmpty()
                    && url != null && !url.trim().isEmpty()) {
                quality.put(label, url);
            }
        }
        if (quality.length() > 0) item.put("quality", quality);
    }

    private static boolean itemMatchesUrl(JSONObject item, String currentUrl) {
        if (item == null || currentUrl == null || currentUrl.trim().isEmpty()) return false;
        if (currentUrl.equals(item.optString("url", null))) return true;
        JSONObject quality = item.optJSONObject("quality");
        if (quality == null) return false;
        Iterator<String> keys = quality.keys();
        while (keys.hasNext()) {
            if (currentUrl.equals(quality.optString(keys.next(), null))) return true;
        }
        return false;
    }

    private static ArrayList<String> stringValues(Intent intent, String key) {
        ArrayList<String> list = intent.getStringArrayListExtra(key);
        if (list != null) return list;
        String[] array = intent.getStringArrayExtra(key);
        if (array == null) return null;
        return new ArrayList<>(Arrays.asList(array));
    }

    private static ArrayList<Bundle> bundleValues(Intent intent, String key) {
        ArrayList<Bundle> list = intent.getParcelableArrayListExtra(key);
        if (list != null) return list;
        Parcelable[] array = intent.getParcelableArrayExtra(key);
        if (array == null) return null;
        ArrayList<Bundle> result = new ArrayList<>();
        for (Parcelable value : array) result.add(value instanceof Bundle ? (Bundle) value : null);
        return result;
    }

    private static void putIndexedText(JSONObject target, String key,
                                       ArrayList<String> values, int index) throws JSONException {
        String value = indexedText(values, index);
        if (value != null) target.put(key, value);
    }

    private static String indexedText(ArrayList<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) return null;
        String value = values.get(index);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static void putIndexedPositiveInt(JSONObject target, String key,
                                              ArrayList<String> values, int index) throws JSONException {
        String value = indexedText(values, index);
        if (value == null) return;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) target.put(key, parsed);
        } catch (NumberFormatException ignored) { }
    }

    private void applyPlaylistItem(LampaPlaylist.Item item, boolean preserveMissingExtras) {
        if (item == null || !item.isResolved()) return;
        cancelSubtitleSearch();
        if (timeBar != null) timeBar.setSkipSegments(0, null, null);
        apiAccess = true;
        apiAccessPartial = false;
        mPrefs.setPersistent(false);
        if (item.title != null || !preserveMissingExtras) apiTitle = item.title;
        if (!item.headers.isEmpty() || !preserveMissingExtras) {
            apiHeaders.clear();
            apiHeaders.putAll(item.headers);
        }
        if (!item.subtitles.isEmpty() || !preserveMissingExtras) {
            apiSubs.clear();
            for (int i = 0; i < item.subtitles.size(); i++) {
                LampaPlaylist.Subtitle subtitle = item.subtitles.get(i);
                Uri uri = Uri.parse(subtitle.url);
                String label = subtitle.label == null ? subtitle.language : subtitle.label;
                apiSubs.add(SubtitleUtils.buildSubtitle(this, uri, label, i == 0));
            }
        }
        mPrefs.updateMedia(this, Uri.parse(item.url), "video/*");
        String resumeKey = item.resumeKey();
        if (!resumeKey.equals(playlistPlaybackKey)) {
            playlistPlaybackKey = resumeKey;
            playlistPlaybackEverReady = false;
        }
        mPrefs.selectPositionKey(resumeKey, item.positionMs);
        item.positionMs = mPrefs.getPosition();
        updateLampaTopPanel();
        updateEpisodeControls();
    }

    private GradientDrawable lampaBackground(int color, int strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(Utils.dpToPx((int) radiusDp));
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(Utils.dpToPx(1), strokeColor);
        }
        return drawable;
    }

    private int lampaMenuAlpha() {
        String value = android.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString("menuOpacity", "88");
        int percent = 88;
        try {
            percent = Integer.parseInt(value);
        } catch (NumberFormatException ignored) { }
        percent = Math.max(70, Math.min(100, percent));
        return Math.round(255f * percent / 100f);
    }

    private int lampaMenuColor(int red, int green, int blue) {
        return Color.argb(lampaMenuAlpha(), red, green, blue);
    }

    private void updateLampaMenuOpacity() {
        final int gold = Color.rgb(211, 165, 24);
        if (lampaTopPanel != null) {
            lampaTopPanel.setBackground(lampaBackground(lampaMenuColor(4, 18, 40), gold, 10));
        }
        if (lampaSkipPanel != null) {
            lampaSkipPanel.setBackground(lampaBackground(lampaMenuColor(4, 18, 40), gold, 8));
        }
    }

    private void setupLampaOverlay(FrameLayout controllerBackground) {
        final int navy = Color.rgb(5, 17, 36);
        final int gold = Color.rgb(211, 165, 24);
        final int muted = Color.rgb(174, 185, 202);

        lampaTopPanel = new LinearLayout(this);
        lampaTopPanel.setOrientation(LinearLayout.HORIZONTAL);
        lampaTopPanel.setGravity(Gravity.CENTER_VERTICAL);
        lampaTopPanel.setPadding(Utils.dpToPx(10), Utils.dpToPx(8), Utils.dpToPx(12), Utils.dpToPx(8));
        lampaTopPanel.setBackground(lampaBackground(lampaMenuColor(4, 18, 40), gold, 10));

        FrameLayout preview = new FrameLayout(this);
        preview.setPadding(Utils.dpToPx(2), Utils.dpToPx(2), Utils.dpToPx(2), Utils.dpToPx(2));
        preview.setBackground(lampaBackground(Color.rgb(10, 39, 76),
                Color.rgb(62, 91, 126), 7));
        lampaTopThumbnail = new ImageView(this);
        lampaTopThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        lampaTopThumbnail.setBackgroundColor(Color.rgb(10, 32, 64));
        preview.addView(lampaTopThumbnail, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        lampaEpisodeBadge = new TextView(this);
        lampaEpisodeBadge.setTextColor(Color.WHITE);
        lampaEpisodeBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lampaEpisodeBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        lampaEpisodeBadge.setGravity(Gravity.CENTER);
        lampaEpisodeBadge.setBackground(lampaBackground(Color.argb(235, 7, 25, 52), gold, 5));
        preview.addView(lampaEpisodeBadge, new FrameLayout.LayoutParams(
                Utils.dpToPx(32), Utils.dpToPx(29), Gravity.START | Gravity.TOP));
        lampaTopPanel.addView(preview, new LinearLayout.LayoutParams(Utils.dpToPx(136), Utils.dpToPx(76)));

        View titleAccent = new View(this);
        titleAccent.setBackgroundColor(gold);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(
                Utils.dpToPx(2), Utils.dpToPx(48));
        accentParams.setMarginStart(Utils.dpToPx(12));
        accentParams.setMarginEnd(Utils.dpToPx(12));
        lampaTopPanel.addView(titleAccent, accentParams);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER_VERTICAL);
        lampaTopTitle = new TextView(this);
        lampaTopTitle.setTextColor(Color.WHITE);
        lampaTopTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        lampaTopTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        lampaTopTitle.setMaxLines(1);
        lampaTopTitle.setEllipsize(TextUtils.TruncateAt.END);
        lampaTopDetails = new TextView(this);
        lampaTopDetails.setTextColor(muted);
        lampaTopDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lampaTopDetails.setMaxLines(2);
        textBlock.addView(lampaTopTitle);
        textBlock.addView(lampaTopDetails);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lampaTopPanel.addView(textBlock, textParams);

        LinearLayout timeBlock = new LinearLayout(this);
        timeBlock.setOrientation(LinearLayout.VERTICAL);
        timeBlock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        timeBlock.setPadding(Utils.dpToPx(12), Utils.dpToPx(6),
                Utils.dpToPx(12), Utils.dpToPx(6));
        timeBlock.setBackground(lampaBackground(Color.argb(105, 10, 39, 76),
                Color.rgb(35, 58, 84), 7));
        lampaClock = new TextView(this);
        lampaClock.setTextColor(Color.WHITE);
        lampaClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        lampaClock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        lampaClock.setGravity(Gravity.END);
        lampaFinishTime = new TextView(this);
        lampaFinishTime.setTextColor(gold);
        lampaFinishTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lampaFinishTime.setGravity(Gravity.END);
        timeBlock.addView(lampaClock);
        timeBlock.addView(lampaFinishTime);
        lampaTopPanel.addView(timeBlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(94), Gravity.TOP);
        topParams.setMargins(Utils.dpToPx(16), Utils.dpToPx(8),
                Utils.dpToPx(16), 0);
        controllerBackground.addView(lampaTopPanel, topParams);

        lampaSkipPanel = new LinearLayout(this);
        lampaSkipPanel.setOrientation(LinearLayout.VERTICAL);
        lampaSkipPanel.setPadding(Utils.dpToPx(4), Utils.dpToPx(4), Utils.dpToPx(4), Utils.dpToPx(4));
        lampaSkipPanel.setBackground(lampaBackground(lampaMenuColor(4, 18, 40), gold, 8));
        lampaSkipPanel.setVisibility(View.GONE);
        lampaSkipButton = new TextView(this);
        lampaSkipButton.setTextColor(Color.WHITE);
        lampaSkipButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lampaSkipButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        lampaSkipButton.setGravity(Gravity.CENTER);
        lampaSkipButton.setPadding(Utils.dpToPx(20), Utils.dpToPx(10), Utils.dpToPx(20), Utils.dpToPx(8));
        lampaSkipButton.setFocusable(true);
        lampaSkipButton.setClickable(true);
        lampaSkipButton.setOnClickListener(view -> activateSkipModel());
        lampaSkipProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        lampaSkipProgress.setMax(1000);
        lampaSkipProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(gold));
        lampaSkipProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(23, 48, 78)));
        lampaSkipPanel.addView(lampaSkipButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lampaSkipPanel.addView(lampaSkipProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(4)));
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        skipParams.setMarginEnd(Utils.dpToPx(24));
        // Keep the action close to the timeline, but above the bottom controls.
        skipParams.bottomMargin = Utils.dpToPx(86);
        playerView.addView(lampaSkipPanel, skipParams);
        updateLampaTopPanel();
    }

    private void openAppSettings() {
        openAppSettings(null);
    }

    private void openAppSettings(String scrollToKey) {
        Intent intent = new Intent(this, SettingsActivity.class);
        if (scrollToKey != null) {
            intent.putExtra(SettingsActivity.EXTRA_SCROLL_TO, scrollToKey);
        }
        ArrayList<String> languages = new ArrayList<>();
        if (player != null) {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_AUDIO
                        && group.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int index = 0; index < group.length; index++) {
                    String language = AudioLanguagePriority.normalize(
                            group.getTrackFormat(index).language);
                    if (language != null && !languages.contains(language)) languages.add(language);
                }
            }
        }
        if (!languages.isEmpty()) {
            intent.putExtra(SettingsActivity.EXTRA_MEDIA_LANGUAGES,
                    languages.toArray(new String[0]));
        }
        startActivityForResult(intent, REQUEST_SETTINGS);
    }

    private void checkForUpdates(boolean manual) {
        Updater.find(this, manual, info -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (info == null) {
                if (manual) Toast.makeText(this, R.string.update_none, Toast.LENGTH_SHORT).show();
                pendingUpdate = Updater.pending(this);
                updatePendingButton();
                return;
            }
            pendingUpdate = info;
            updatePendingButton();
            showPendingUpdate(manual);
        }));
    }

    private void showPendingUpdate(boolean manual) {
        if (pendingUpdate == null) {
            if (manual) checkForUpdates(true);
            return;
        }
        UpdateInfo shown = pendingUpdate;
        UpdateUi.showAvailableDialog(this, shown, () -> {
            Updater.skip(this, shown);
            pendingUpdate = null;
            updatePendingButton();
        }, haveMedia);
    }

    private void updatePendingButton() {
        if (buttonUpdate != null) {
            buttonUpdate.setVisibility(pendingUpdate == null ? View.GONE : View.VISIBLE);
        }
    }

    private void showPlayerTools() {
        String timerSummary = sleepTimer.isAtMediaEnd()
                ? getString(R.string.sleep_timer_end_of_item)
                : sleepTimer.remainingMs() > 0 ? Utils.formatMilis(sleepTimer.remainingMs()) : null;
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.sleep_timer_title)
                + (timerSummary == null ? "" : "  \u00B7  " + timerSummary));
        actions.add(this::showSleepTimerMenu);
        items.add(getString(R.string.playback_statistics_title));
        actions.add(this::showPlaybackStatistics);
        if (togetherAvailable()) {
            final String summary = togetherSummary();
            items.add(getString(R.string.together_title)
                    + (summary == null ? "" : "  \u00B7  " + summary));
            actions.add(this::showTogetherMenu);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.player_tools)
                .setItems(items.toArray(new String[0]), (selected, which) -> {
                    selected.dismiss();
                    actions.get(which).run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, false));
        dialog.show();
    }

    private void setupTogetherOverlay() {
        final int gold = Color.rgb(240, 183, 38);
        roomPill = new TextView(this);
        roomPill.setTextColor(Color.WHITE);
        roomPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        roomPill.setGravity(Gravity.CENTER_VERTICAL);
        roomPill.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_together_24dp, 0, 0, 0);
        roomPill.setCompoundDrawablePadding(Utils.dpToPx(7));
        roomPill.setPadding(Utils.dpToPx(12), Utils.dpToPx(6),
                Utils.dpToPx(12), Utils.dpToPx(6));
        roomPill.setBackground(lampaBackground(Color.argb(225, 4, 18, 40), gold, 9));
        roomPill.setVisibility(View.GONE);
        final FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.BOTTOM);
        pillParams.setMargins(Utils.dpToPx(22), 0, 0, Utils.dpToPx(88));
        playerView.addView(roomPill, pillParams);

        roomMessage = new TextView(this);
        roomMessage.setTextColor(Color.WHITE);
        roomMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        roomMessage.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        roomMessage.setGravity(Gravity.CENTER);
        roomMessage.setPadding(Utils.dpToPx(16), Utils.dpToPx(8),
                Utils.dpToPx(16), Utils.dpToPx(8));
        roomMessage.setBackground(lampaBackground(Color.argb(225, 4, 18, 40), gold, 9));
        roomMessage.setVisibility(View.GONE);
        final FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        messageParams.bottomMargin = Utils.dpToPx(132);
        playerView.addView(roomMessage, messageParams);
    }

    private Uri currentPlayingUri() {
        if (player != null) {
            final MediaItem item = player.getCurrentMediaItem();
            if (item != null && item.localConfiguration != null) {
                return item.localConfiguration.uri;
            }
        }
        return mPrefs == null ? null : mPrefs.mediaUri;
    }

    private boolean togetherAvailable() {
        if (together != null && together.isActive()) {
            return true;
        }
        return Utils.isSupportedNetworkUri(currentPlayingUri());
    }

    private String togetherSummary() {
        if (together == null || !together.isActive()) {
            return null;
        }
        final String name = together.roomName();
        final String defaultName = getString(R.string.together_room_default_name, together.code());
        return TextUtils.isEmpty(name) || defaultName.equals(name)
                ? getString(R.string.together_badge, together.code(), together.peers())
                : getString(R.string.together_badge_named,
                        name, together.code(), together.peers());
    }

    private void showTogetherMenu() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (together != null && together.isActive()) {
            labels.add(getString(R.string.together_share));
            actions.add(this::shareInvite);
            labels.add(getString(R.string.together_leave));
            actions.add(this::leaveRoom);
        } else {
            if (Utils.isSupportedNetworkUri(currentPlayingUri())) {
                labels.add(getString(R.string.together_create));
                actions.add(this::createRoom);
            }
            labels.add(getString(R.string.together_find));
            actions.add(this::findRooms);
            labels.add(getString(R.string.together_enter_code));
            actions.add(this::askRoomCode);
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.together_title)
                .setItems(labels.toArray(new String[0]), (selected, which) -> {
                    selected.dismiss();
                    actions.get(which).run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, false));
        dialog.show();
    }

    private void findRooms() {
        Relay.setBase(mPrefs.togetherRelay);
        Toast.makeText(this, R.string.together_searching, Toast.LENGTH_SHORT).show();
        TogetherManager.discover(rooms -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (rooms.isEmpty()) {
                showSnack(getString(R.string.together_none_found), null);
                return;
            }
            final String[] labels = new String[rooms.size()];
            for (int i = 0; i < rooms.size(); i++) {
                final JSONObject room = rooms.get(i);
                final String title = room.optString("title", "").isEmpty()
                        ? room.optString("name", room.optString("id"))
                        : room.optString("title");
                labels[i] = title + "\n" + getString(R.string.together_room_summary,
                        room.optString("owner", ""), room.optInt("members", 1));
            }
            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.together_find)
                    .setItems(labels, (selected, which) -> {
                        final JSONObject room = rooms.get(which);
                        final String code = room.optString("id");
                        if (room.optInt("pwd") == 1) {
                            askRoomPassword(code);
                        } else {
                            joinRoom(code, "");
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, false));
            dialog.show();
        });
    }

    private void askRoomPassword(final String code) {
        final EditText password = roomPasswordField();
        password.setText(mPrefs.togetherPassword);
        password.setSelection(password.length());
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(code)
                .setView(password)
                .setPositiveButton(android.R.string.ok,
                        (selected, which) -> joinRoom(code, password.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, true));
        dialog.show();
    }

    private EditText roomPasswordField() {
        final EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint(R.string.together_password_hint);
        password.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return password;
    }

    private void createRoom() {
        final JSONObject session = sessionDescription();
        if (session == null) {
            return;
        }
        final String code = Room.newCode();
        final JSONObject card = roomCard();
        final String suggested = card == null || card.optString("title", "").isEmpty()
                ? getString(R.string.together_room_default_name, code)
                : card.optString("title");

        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.together_room_name_hint);
        name.setText(suggested);
        name.setSelection(name.length());
        final EditText password = roomPasswordField();
        password.setText(mPrefs.togetherPassword);
        final CheckBox listed = new CheckBox(this);
        listed.setText(R.string.together_public);
        listed.setChecked(mPrefs.togetherPublic);
        final TextView warning = new TextView(this);
        warning.setText(R.string.together_public_needs_password);
        warning.setTextColor(Color.rgb(240, 183, 38));
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        warning.setVisibility(View.GONE);

        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int padding = Utils.dpToPx(18);
        fields.setPadding(padding, 0, padding, 0);
        fields.addView(name);
        fields.addView(password);
        fields.addView(listed);
        fields.addView(warning);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.together_create_title, code))
                .setView(fields)
                .setPositiveButton(android.R.string.ok, (selected, which) -> {
                    mPrefs.updateTogetherPublic(listed.isChecked());
                    openRoom(code, name.getText().toString().trim(),
                            password.getText().toString(), session);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            styleUaAlertDialog(dialog, true);
            final Runnable validate = () -> {
                final boolean invalid = listed.isChecked() && password.length() == 0;
                warning.setVisibility(invalid ? View.VISIBLE : View.GONE);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!invalid);
            };
            listed.setOnCheckedChangeListener((button, checked) -> validate.run());
            password.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start,
                                                        int count, int after) { }
                @Override public void onTextChanged(CharSequence value, int start,
                                                     int before, int count) { }
                @Override public void afterTextChanged(Editable value) {
                    validate.run();
                }
            });
            validate.run();
        });
        dialog.show();
    }

    private void openRoom(final String code, final String name, final String password,
                          final JSONObject session) {
        ensureTogether();
        together.create(code, password, mPrefs.togetherPublic,
                name.isEmpty() ? getString(R.string.together_room_default_name, code) : name,
                session, roomNick());
        copyToClipboard(together.invite());
        if (isTvBox) {
            showInviteQr(together.invite());
        } else {
            showSnack(getString(R.string.together_created, together.code()), null);
        }
    }

    private void askRoomCode() {
        final EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setHint("ABC234");
        code.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText password = roomPasswordField();
        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int padding = Utils.dpToPx(18);
        fields.setPadding(padding, 0, padding, 0);
        fields.addView(code);
        fields.addView(password);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.together_join)
                .setView(fields)
                .setPositiveButton(android.R.string.ok, (selected, which) -> {
                    final String entered = code.getText().toString().trim()
                            .toUpperCase(Locale.US);
                    if (Room.isCode(entered)) {
                        joinRoom(entered, password.getText().toString());
                    } else {
                        showSnack(getString(R.string.together_code_invalid), null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, true));
        dialog.show();
    }

    private void joinRoom(final String code, final String password) {
        ensureTogether();
        together.join(code, password, roomNick());
    }

    private String roomNick() {
        return TextUtils.isEmpty(mPrefs.togetherNick) ? Build.MODEL : mPrefs.togetherNick;
    }

    private void leaveRoom() {
        if (together != null) {
            together.leave();
        }
    }

    private Room.Invite roomInviteFromIntent(final Intent intent) {
        if (intent == null) {
            return null;
        }
        Room.Invite invite = Room.inviteFrom(intent.getData());
        if (invite != null) {
            return invite;
        }
        if (!Intent.ACTION_SEND.equals(intent.getAction())
                || !"text/plain".equals(intent.getType())) {
            return null;
        }
        final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        try {
            invite = Room.inviteFrom(Uri.parse(text.trim()));
        } catch (RuntimeException ignored) {
            // A plain code or encoded invite is handled below.
        }
        return invite != null ? invite : Room.inviteFrom(text.trim());
    }

    private boolean handleRoomIntent(final Intent intent) {
        final Room.Invite invite = roomInviteFromIntent(intent);
        if (invite == null) {
            return false;
        }
        awaitingRoomMedia = player == null || !haveMedia;
        joinRoom(invite.code, invite.password);
        return true;
    }

    private JSONObject sessionDescription() {
        final Uri uri = currentPlayingUri();
        if (!Utils.isSupportedNetworkUri(uri)) {
            return null;
        }
        try {
            final JSONObject session = new JSONObject().put("uri", uri.toString());
            if (!TextUtils.isEmpty(mPrefs.mediaType)) {
                session.put("type", mPrefs.mediaType);
            }
            final Intent current = getIntent();
            if (current != null && current.getExtras() != null) {
                final Bundle extras = new Bundle(current.getExtras());
                extras.remove(API_RETURN_RESULT);
                extras.remove(API_END_BY);
                extras.remove(API_DURATION);
                extras.remove(LampaPlaylist.EXTRA_PLAYBACK_RESULTS);
                if (extras.containsKey(API_POSITION) && player != null
                        && player.isCurrentMediaItemSeekable()) {
                    extras.putInt(API_POSITION,
                            (int) Math.max(0, player.getCurrentPosition()));
                }
                if (lampaPlaylist != null && !lampaPlaylist.isEmpty()) {
                    extras.putInt(LampaPlaylist.EXTRA_PLAYLIST_INDEX,
                            lampaPlaylist.getCurrentIndex());
                    extras.putString(LampaPlaylist.EXTRA_CURRENT_URL, uri.toString());
                }
                session.put("extras", SessionCodec.toJson(extras));
            }
            final JSONObject card = roomCard();
            if (card != null) {
                session.put("title", card.optString("title", ""));
                session.put("poster", card.optString("poster", ""));
            }
            return session;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject roomCard() {
        final Uri uri = currentPlayingUri();
        if (!Utils.isSupportedNetworkUri(uri)) {
            return null;
        }
        final LampaPlaylist.Item item = lampaPlaylist == null ? null : lampaPlaylist.getCurrent();
        String title = item == null ? apiTitle : item.displayTitle(lampaPlaylist.getCurrentIndex());
        if (TextUtils.isEmpty(title)) {
            title = Utils.getFileName(this, uri);
        }
        String poster = item == null ? null : item.thumbnail;
        if (TextUtils.isEmpty(poster) && getIntent() != null) {
            poster = getIntent().getStringExtra("thumbnail");
        }
        if (!TextUtils.isEmpty(poster)) {
            try {
                if (!Utils.isSupportedNetworkUri(Uri.parse(poster))) {
                    poster = "";
                }
            } catch (RuntimeException ignored) {
                poster = "";
            }
        }
        final int tmdb = item == null ? getIntent().getIntExtra("tmdb_id", -1) : item.tmdbId;
        final String mediaType = item == null
                ? getIntent().getStringExtra("media_type") : item.mediaType;
        final boolean series = item != null && item.season > 0
                || mediaType != null && !"movie".equalsIgnoreCase(mediaType);
        try {
            return new JSONObject()
                    .put("url", uri.toString())
                    .put("title", title == null ? "" : title)
                    .put("poster", poster == null ? "" : poster)
                    .put("tmdb", Math.max(0, tmdb))
                    .put("source", "tmdb")
                    .put("type", series ? "tv" : "movie");
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openSession(final JSONObject session) {
        final String uriText = session == null ? null : session.optString("uri", null);
        if (TextUtils.isEmpty(uriText)) {
            return;
        }
        final Uri uri = Uri.parse(uriText);
        if (!Utils.isSupportedNetworkUri(uri)) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW).setData(uri);
        final String type = session.optString("type", null);
        if (!TextUtils.isEmpty(type)) {
            intent.setType(type);
        }
        final JSONObject encodedExtras = session.optJSONObject("extras");
        if (encodedExtras != null) {
            final Bundle extras = SessionCodec.toBundle(encodedExtras);
            extras.remove(API_RETURN_RESULT);
            extras.remove(LampaPlaylist.EXTRA_PLAYBACK_RESULTS);
            intent.putExtras(extras);
        } else {
            final String title = session.optString("title", null);
            final String poster = session.optString("poster", null);
            if (!TextUtils.isEmpty(title)) {
                intent.putExtra(API_TITLE, title);
            }
            if (!TextUtils.isEmpty(poster)) {
                intent.putExtra("thumbnail", poster);
            }
        }
        applyingRoomMedia = true;
        awaitingRoomMedia = false;
        try {
            applyViewIntent(intent, false);
            final long roomPosition = together == null ? -1L : together.roomPositionMs();
            if (roomPosition >= 0L) {
                mPrefs.updatePosition(roomPosition);
            }
            if (alive) {
                initializePlayer();
            }
        } finally {
            applyingRoomMedia = false;
        }
    }

    private void syncRoomPlaylistStep(final boolean automatic) {
        if (applyingRoomMedia || together == null || !together.isActive()) {
            return;
        }
        final JSONObject session = sessionDescription();
        if (session == null) {
            together.leave();
        } else if (together.isOwner()) {
            together.changeMedia(session);
        } else if (automatic) {
            together.mediaStepped(session);
        } else {
            together.leave();
        }
    }

    private void shareInvite() {
        final String invite = together == null ? null : together.invite();
        if (invite == null) {
            return;
        }
        copyToClipboard(invite);
        final Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, invite);
        if (isTvBox || getPackageManager().queryIntentActivities(share, 0).isEmpty()) {
            showInviteQr(invite);
            return;
        }
        try {
            startActivity(Intent.createChooser(share, getString(R.string.together_share)));
        } catch (RuntimeException ignored) {
            showInviteQr(invite);
        }
    }

    private void showInviteQr(final String invite) {
        copyToClipboard(invite);
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final int size = Math.max(Utils.dpToPx(220), Math.min(Utils.dpToPx(620),
                Math.round(Math.min(metrics.widthPixels, metrics.heightPixels) * 0.58f)));
        final Bitmap qr = createQrBitmap(invite, size);
        if (qr == null) {
            showSnack(getString(R.string.together_created,
                    together == null ? "" : together.code()), null);
            return;
        }
        final ImageView image = new ImageView(this);
        image.setImageBitmap(qr);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.WHITE);
        final int padding = Utils.dpToPx(14);
        image.setPadding(padding, padding, padding, padding);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.together_qr_title,
                        together == null ? "" : together.code()))
                .setMessage(R.string.together_qr_hint)
                .setView(image)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, true));
        dialog.show();
    }

    private Bitmap createQrBitmap(final String value, final int size) {
        try {
            final BitMatrix matrix = new QRCodeWriter().encode(
                    value, BarcodeFormat.QR_CODE, size, size);
            final int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                final int offset = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void copyToClipboard(final String value) {
        final android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null && value != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", value));
        }
    }

    private void ensureTogether() {
        Relay.setBase(mPrefs.togetherRelay);
        Room.setInvitePage(mPrefs.togetherInvitePage);
        if (together == null) {
            together = new TogetherManager(togetherHost());
        }
    }

    private void updateRoomBadge() {
        final boolean active = together != null && together.isActive();
        if (buttonTogether != null) {
            buttonTogether.setVisibility(togetherAvailable() ? View.VISIBLE : View.GONE);
        }
        if (roomPill != null) {
            final boolean visible = active && controllerVisible && !inPip && !locked;
            if (visible) {
                roomPill.setText(together.connected()
                        ? getString(R.string.together_badge, together.code(), together.peers())
                        : getString(together.everConnected()
                                ? R.string.together_offline : R.string.together_connecting,
                                together.code()));
                roomPill.setTextColor(together.connected()
                        ? Color.WHITE : Color.rgb(240, 183, 38));
            }
            roomPill.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (roomMessage != null) {
            final String waiting = active ? together.waitingFor() : null;
            final boolean visible = active && !inPip && !locked
                    && (together.holding() || waiting != null);
            if (visible) {
                roomMessage.setText(together.holding()
                        ? getString(R.string.together_hold)
                        : getString(R.string.together_waiting,
                                TextUtils.isEmpty(waiting)
                                        ? getString(R.string.together_act_somebody) : waiting));
            }
            roomMessage.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void announceRoomAction(final String nick, final RoomAction action) {
        if (playerView == null || inPip || locked) {
            return;
        }
        final int message;
        switch (action) {
            case PAUSED:
                message = R.string.together_act_paused;
                break;
            case RESUMED:
                message = R.string.together_act_resumed;
                break;
            case LEFT:
                message = R.string.together_act_left;
                break;
            default:
                message = R.string.together_act_seeked;
                break;
        }
        Utils.showText(playerView, getString(message,
                TextUtils.isEmpty(nick) ? getString(R.string.together_act_somebody) : nick),
                3_500L);
    }

    private TogetherManager.Host togetherHost() {
        return new TogetherManager.Host() {
            @Override public boolean ready() {
                return player != null && haveMedia;
            }

            @Override public boolean scrubbing() {
                return isScrubbing;
            }

            @Override public long positionMs() {
                return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
            }

            @Override public boolean playWhenReady() {
                return player != null && player.getPlayWhenReady()
                        && player.getPlaybackState() != Player.STATE_ENDED;
            }

            @Override public float speed() {
                return player == null ? 1f : player.getPlaybackParameters().speed;
            }

            @Override public boolean buffering() {
                return player != null && player.getPlaybackState() == Player.STATE_BUFFERING;
            }

            @Override public boolean ended() {
                return player != null && player.getPlaybackState() == Player.STATE_ENDED;
            }

            @Override public long durationMs() {
                if (player == null || player.getDuration() == C.TIME_UNSET) {
                    return 0L;
                }
                return Math.max(0L, player.getDuration());
            }

            @Override public String playingUri() {
                final Uri uri = currentPlayingUri();
                return uri == null ? null : uri.toString();
            }

            @Override public long bufferedAheadMs() {
                return player == null ? 0L : Math.max(0L, player.getTotalBufferedDuration());
            }

            @Override public void applyPlay(final boolean play) {
                if (player != null) {
                    player.setPlayWhenReady(play);
                }
            }

            @Override public void applySeek(final long positionMs) {
                if (player != null) {
                    player.setSeekParameters(SeekParameters.EXACT);
                    player.seekTo(positionMs);
                }
            }

            @Override public void applySpeed(final float speed) {
                if (player != null) {
                    player.setPlaybackSpeed(speed);
                }
            }

            @Override public void onRoomChanged() {
                updateRoomBadge();
            }

            @Override public void onRoomAction(final String nick, final RoomAction action) {
                announceRoomAction(nick, action);
            }

            @Override public JSONObject sessionDescription() {
                return PlayerActivity.this.sessionDescription();
            }

            @Override public JSONObject roomCard() {
                return PlayerActivity.this.roomCard();
            }

            @Override public void openSession(final JSONObject session) {
                PlayerActivity.this.openSession(session);
            }

            @Override public void onJoinFailed() {
                final boolean resumeRememberedMedia = awaitingRoomMedia;
                awaitingRoomMedia = false;
                showSnack(getString(R.string.together_no_room), null);
                if (resumeRememberedMedia && alive && player == null) {
                    initializePlayer();
                }
            }

            @Override public void onHoldLifted() {
                if (playerView != null && !inPip && !locked) {
                    Utils.showText(playerView, getString(R.string.together_go), 3_500L);
                }
            }
        };
    }

    private void showSleepTimerMenu() {
        final int[] minutes = {0, 15, 30, 45, 60, 90, -1, -2};
        String[] labels = {
                getString(R.string.sleep_timer_off),
                getString(R.string.sleep_timer_minutes, 15),
                getString(R.string.sleep_timer_minutes, 30),
                getString(R.string.sleep_timer_minutes, 45),
                getString(R.string.sleep_timer_hours, 1),
                getString(R.string.sleep_timer_hours_minutes, 1, 30),
                getString(R.string.sleep_timer_end_of_item),
                getString(R.string.sleep_timer_custom)
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.sleep_timer_title)
                .setItems(labels, (selected, which) -> {
                    int value = minutes[which];
                    selected.dismiss();
                    if (value == -2) showCustomSleepTimer();
                    else if (value == -1) armSleepAtMediaEnd();
                    else armSleepAfterMinutes(value);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, false));
        dialog.show();
    }

    private void showCustomSleepTimer() {
        AlertDialog dialog = DurationPanel.create(this, this::armSleepAfterMinutes);
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, true));
        dialog.show();
    }

    private void armSleepAfterMinutes(int minutes) {
        playerView.removeCallbacks(sleepTimerRunnable);
        if (minutes <= 0) {
            sleepTimer.cancel();
            if (player != null) player.setVolume(basePlayerVolume());
            Utils.showText(playerView, getString(R.string.sleep_timer_cancelled));
            return;
        }
        sleepTimer.armAfter(minutes * 60_000L);
        playerView.post(sleepTimerRunnable);
        Utils.showText(playerView, getString(R.string.sleep_timer_set,
                getString(R.string.sleep_timer_minutes, minutes)));
    }

    private void armSleepAtMediaEnd() {
        playerView.removeCallbacks(sleepTimerRunnable);
        sleepTimer.armAtMediaEnd();
        Utils.showText(playerView, getString(R.string.sleep_timer_set,
                getString(R.string.sleep_timer_end_of_item)));
    }

    private void fireSleepTimer() {
        playerView.removeCallbacks(sleepTimerRunnable);
        sleepTimer.cancel();
        if (player != null) {
            player.pause();
            player.setVolume(basePlayerVolume());
        }
        Utils.showText(playerView, getString(R.string.sleep_timer_finished), 3500);
    }

    private float basePlayerVolume() {
        return systemVolume ? 1f : Math.max(0f, Math.min(1f, playerVolume / 100f));
    }

    private void showPlaybackStatistics() {
        if (player == null) return;
        Format video = player.getVideoFormat();
        Format audio = player.getAudioFormat();
        PlaybackStatistics.Snapshot snapshot = new PlaybackStatistics.Snapshot(
                mediaContainerLabel(),
                video == null ? 0 : video.width,
                video == null ? 0 : video.height,
                video == null ? null : shortCodec(video.sampleMimeType),
                videoFrameRate(),
                videoBitrate(),
                player.getTotalBufferedDuration(),
                videoDecoderName,
                audio == null ? audioDecoderName : shortCodec(audio.sampleMimeType)
                        + (audio.channelCount > 0 ? " \u00B7 " + audio.channelCount + " ch" : "")
                        + (audioDecoderName == null ? "" : " \u00B7 " + audioDecoderName),
                currentTransferBitrate(),
                totalDroppedFrames);
        PlaybackStatistics.Labels labels = new PlaybackStatistics.Labels(
                getString(R.string.playback_stats_container),
                getString(R.string.playback_stats_video),
                getString(R.string.playback_stats_fps),
                getString(R.string.playback_stats_bitrate),
                getString(R.string.playback_stats_buffer),
                getString(R.string.playback_stats_network),
                getString(R.string.playback_stats_decoder),
                getString(R.string.playback_stats_audio),
                getString(R.string.playback_stats_dropped_frames));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.playback_statistics_title)
                .setMessage(snapshot.render(labels))
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> styleUaAlertDialog(dialog, true));
        dialog.show();
    }

    private float videoFrameRate() {
        Format format = player == null ? null : player.getVideoFormat();
        return format != null && format.frameRate > 0f
                ? format.frameRate : containerFrameRate();
    }

    private float containerFrameRate() {
        for (TrackMetadata metadata : currentContainerTracks()) {
            if (metadata.type == TrackMetadata.Type.VIDEO && metadata.frameRate > 0f) {
                return metadata.frameRate;
            }
        }
        return 0f;
    }

    private long videoBitrate() {
        Format format = player == null ? null : player.getVideoFormat();
        if (format != null && format.bitrate > 0) return format.bitrate;
        String key = currentMediaKey();
        Long length = key == null ? null : contentLengths.get(key);
        long duration = player == null ? C.TIME_UNSET : player.getDuration();
        return length == null || duration == C.TIME_UNSET ? 0L
                : PlaybackStatistics.averageBitrate(length, duration);
    }

    private String mediaContainerLabel() {
        Uri uri = mPrefs == null ? null : mPrefs.mediaUri;
        String value = uri == null ? "" : uri.toString().toLowerCase(Locale.US);
        if (lampaIptv) return "LIVE";
        if (value.contains(".m3u8")) return "HLS";
        if (value.contains(".mpd") || value.contains("/ytdl/manifest?")) return "DASH";
        if (value.contains(".mkv")) return "MKV";
        if (value.contains(".avi")) return "AVI";
        if (value.contains(".ts")) return "TS";
        if (value.contains(".mp4")) return "MP4";
        return "VIDEO";
    }

    private void updateLampaTopPanel() {
        if (lampaTopPanel == null) return;
        LampaPlaylist.Item item = lampaPlaylist == null ? null : lampaPlaylist.getCurrent();
        int index = lampaPlaylist == null ? 0 : lampaPlaylist.getCurrentIndex();
        String title = item != null ? item.displayTitle(index) : apiTitle;
        lampaTopTitle.setText(title == null || title.trim().isEmpty() ? "UA Player" : title);
        boolean isEpisode = item != null && (item.episode > 0 || item.season > 0
                || (lampaPlaylist != null && lampaPlaylist.size() > 1
                && !"movie".equalsIgnoreCase(item.mediaType)));
        lampaEpisodeBadge.setVisibility(isEpisode ? View.VISIBLE : View.GONE);
        if (isEpisode) {
            lampaEpisodeBadge.setText(String.valueOf(item.episode > 0
                    ? item.episode : index + 1));
        }
        if (item != null && item.thumbnail != null && !item.thumbnail.trim().isEmpty()) {
            lampaTopThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(item.thumbnail).centerCrop().into(lampaTopThumbnail);
        } else {
            Glide.with(this).clear(lampaTopThumbnail);
            lampaTopThumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            lampaTopThumbnail.setImageResource(R.drawable.ua_player_icon);
        }
        updateLampaTrackDetails();
    }

    private void updateLampaTrackDetails() {
        if (lampaTopDetails == null) return;
        Format video = player == null ? null : player.getVideoFormat();
        Format audio = player == null ? null : player.getAudioFormat();
        if (player != null) {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                for (int i = 0; i < group.length; i++) {
                    if (!group.isTrackSelected(i)) continue;
                    // Adaptive HLS groups can report several tracks as selected.
                    // getVideoFormat()/getAudioFormat() above are the formats that
                    // are actually being rendered right now; only fall back to the
                    // group metadata before the first sample reaches the decoder.
                    if (video == null && group.getType() == C.TRACK_TYPE_VIDEO) {
                        video = group.getTrackFormat(i);
                    }
                    if (audio == null && group.getType() == C.TRACK_TYPE_AUDIO) {
                        audio = group.getTrackFormat(i);
                    }
                }
            }
        }
        ArrayList<String> parts = new ArrayList<>();
        Uri media = mPrefs == null ? null : mPrefs.mediaUri;
        if (media == null && video == null && audio == null && !lampaIptv) {
            lampaTopDetails.setText("");
            return;
        }
        String path = media == null ? "" : String.valueOf(media.getLastPathSegment()).toLowerCase(Locale.US);
        String mediaUrl = media == null ? "" : media.toString().toLowerCase(Locale.US);
        if (lampaIptv) parts.add("LIVE");
        else if (path.contains(".m3u8")) parts.add("HLS");
        else if (path.contains(".mpd") || mediaUrl.contains("/ytdl/manifest?")) parts.add("DASH");
        else if (path.contains(".ts")) parts.add("TS");
        else if (path.contains(".mkv")) parts.add("MKV");
        else if (path.contains(".avi")) parts.add("AVI");
        else parts.add("VIDEO");
        if (video != null) {
            parts.addAll(MediaFormatLabel.videoParts(video));
        }
        if (audio != null) {
            String codec = shortCodec(audio.sampleMimeType);
            if (codec != null) parts.add("[" + codec + "]");
        }
        lampaTopDetails.setText(TextUtils.join(" · ", parts));
    }

    private String currentMediaKey() {
        MediaItem item = player == null ? null : player.getCurrentMediaItem();
        if (item != null && item.localConfiguration != null
                && item.localConfiguration.uri != null) {
            return item.localConfiguration.uri.toString();
        }
        return mPrefs == null || mPrefs.mediaUri == null ? null : mPrefs.mediaUri.toString();
    }

    private List<TrackMetadata> currentContainerTracks() {
        String key = currentMediaKey();
        List<TrackMetadata> tracks = key == null ? null : containerTracks.get(key);
        return tracks == null ? Collections.emptyList() : tracks;
    }

    private void onContainerMetadata(Uri originalUri) {
        if (originalUri == null || !originalUri.toString().equals(currentMediaKey())) return;
        resolveTrackNames();
        updateLampaTrackDetails();
    }

    private void resolveTrackNames() {
        resolvedTrackNames.clear();
        if (player == null || currentContainerTracks().isEmpty()) {
            if (trackNameProvider != null) trackNameProvider.setTrackNames(resolvedTrackNames);
            return;
        }
        resolveTrackNames(C.TRACK_TYPE_AUDIO, TrackMetadata.Type.AUDIO);
        resolveTrackNames(C.TRACK_TYPE_TEXT, TrackMetadata.Type.SUBTITLE);
        if (trackNameProvider != null) trackNameProvider.setTrackNames(resolvedTrackNames);
    }

    private void resolveTrackNames(int mediaTrackType, TrackMetadata.Type metadataType) {
        ArrayList<TrackMetadata> candidates = new ArrayList<>();
        for (TrackMetadata metadata : currentContainerTracks()) {
            if (metadata.type == metadataType && metadata.name != null
                    && !metadata.name.trim().isEmpty()) candidates.add(metadata);
        }
        Collections.sort(candidates, (left, right) -> Integer.compare(left.trackId, right.trackId));
        int ordinal = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != mediaTrackType) continue;
            for (int index = 0; index < group.length; index++, ordinal++) {
                Format format = group.getTrackFormat(index);
                TrackMetadata match = null;
                Integer id = parseTrackId(format.id);
                if (id != null) {
                    for (TrackMetadata candidate : candidates) {
                        if (candidate.trackId == id) { match = candidate; break; }
                    }
                }
                if (match == null && ordinal < candidates.size()) match = candidates.get(ordinal);
                if (match != null && format.id != null) resolvedTrackNames.put(format.id, match.name);
            }
        }
    }

    private static Integer parseTrackId(String id) {
        if (id == null) return null;
        try { return Integer.parseInt(id); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String shortCodec(String mimeType) {
        if (mimeType == null) return null;
        if (mimeType.contains("avc")) return "H.264";
        if (mimeType.contains("hevc")) return "H.265";
        if (mimeType.contains("av01")) return "AV1";
        if (mimeType.contains("vp9")) return "VP9";
        if (mimeType.contains("eac3")) return "E-AC3";
        if (mimeType.contains("ac3")) return "AC3";
        if (mimeType.contains("aac") || mimeType.contains("mp4a")) return "AAC";
        return mimeType.substring(mimeType.lastIndexOf('/') + 1).toUpperCase(Locale.US);
    }

    private void updateLampaRuntimeUi() {
        // HLS may switch rendition without rebuilding the Tracks object.
        // Refresh the badge so it follows the format currently rendered.
        updateLampaTrackDetails();
        if (lampaClock != null) {
            lampaClock.setText(lampaClockFormatter.format(new Date()));
            String finish = "";
            if (player != null && player.getDuration() != C.TIME_UNSET && player.getDuration() > 0) {
                long remaining = Math.max(0, player.getDuration() - player.getCurrentPosition());
                float speed = player.getPlaybackParameters().speed;
                if (speed > 0) remaining = (long) (remaining / speed);
                finish = getString(R.string.playback_finishes_at,
                        lampaClockFormatter.format(new Date(System.currentTimeMillis() + remaining)));
            }
            lampaFinishTime.setText(finish);
        }
        updateLampaSkipUi();
        updateTransferRateUi();
    }

    private void updateTransferRateUi() {
        if (loadingRateView == null) return;
        boolean loading = loadingProgressBar != null
                && loadingProgressBar.getVisibility() == View.VISIBLE;
        long now = SystemClock.elapsedRealtime();
        long bytes = TrackNameParsingDataSource.bytesRead.get();
        if (!loading) {
            transferSampleAt = now;
            transferSampleBytes = bytes;
            loadingRateView.setVisibility(View.GONE);
            return;
        }
        long elapsed = now - transferSampleAt;
        if (transferSampleAt == 0 || elapsed >= 500) {
            if (transferSampleAt > 0 && bytes >= transferSampleBytes && elapsed > 0) {
                sampledTransferBitrate = (bytes - transferSampleBytes) * 8000L / elapsed;
            }
            transferSampleAt = now;
            transferSampleBytes = bytes;
        }
        long bitrate = currentTransferBitrate();
        if (bitrate <= 0) {
            loadingRateView.setVisibility(View.GONE);
            return;
        }
        String value = bitrate >= 1_000_000
                ? String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000f)
                : String.format(Locale.US, "%.0f Kbps", bitrate / 1_000f);
        loadingRateView.setText(value);
        loadingRateView.setVisibility(View.VISIBLE);
    }

    private long currentTransferBitrate() {
        return sampledTransferBitrate > 0 ? sampledTransferBitrate : bandwidthBitrate;
    }

    private void updateLampaSkipUi() {
        if (lampaSkipPanel == null || player == null || lampaPlaylist == null) return;
        if (!mPrefs.skipEnabled) {
            skipModel = null;
            focusedSkipKey = null;
            lampaSkipPanel.setVisibility(View.GONE);
            return;
        }
        LampaPlaylist.Item item = lampaPlaylist.getCurrent();
        long position = Math.max(0, player.getCurrentPosition());
        long duration = player.getDuration();
        if (item == null || duration == C.TIME_UNSET || duration <= 0) {
            skipModel = null;
            lampaSkipPanel.setVisibility(View.GONE);
            return;
        }

        List<SkipSegment> segments = validatedSkipSegments(item, duration);
        SkipPolicy.Mode mode = skipModeForPosition(segments, position, duration);
        skipModel = skipController.update(segments, position, duration,
                lampaPlaylist.hasNext(), mode, SystemClock.elapsedRealtime());
        if (skipModel.action != SkipController.Action.NONE) {
            applySkipAction(skipModel);
            lampaSkipPanel.setVisibility(View.GONE);
            return;
        }
        if (skipModel.state == SkipController.State.HIDDEN) {
            focusedSkipKey = null;
            lampaSkipPanel.setVisibility(View.GONE);
            return;
        }

        switch (skipModel.state) {
            case COUNTDOWN:
                lampaSkipButton.setText(getString(R.string.skip_available_in, skipModel.seconds));
                break;
            case AUTO_PENDING:
                lampaSkipButton.setText(getString(R.string.skip_cancel_countdown, skipModel.seconds));
                break;
            case UNDO_AVAILABLE:
                lampaSkipButton.setText(R.string.skip_undo);
                break;
            case AVAILABLE:
                lampaSkipButton.setText(segmentButtonText(skipModel.segment, duration));
                break;
            default:
                break;
        }
        boolean enabled = skipModel.enabled();
        lampaSkipButton.setEnabled(enabled);
        lampaSkipButton.setClickable(enabled);
        lampaSkipButton.setAlpha(enabled ? 1f : 0.72f);
        lampaSkipProgress.setProgress(skipModel.progress);
        lampaSkipPanel.setVisibility(View.VISIBLE);
        String focusKey = skipModel.segment == null ? skipModel.state.name()
                : skipModel.state.name() + ":" + skipModel.segment.key();
        if (enabled && !focusKey.equals(focusedSkipKey)) {
            focusedSkipKey = focusKey;
            playerView.showController();
            lampaSkipButton.post(() -> lampaSkipButton.requestFocus());
        }
    }

    private void updateLampaSegmentMarkers() {
        if (timeBar == null || player == null || lampaPlaylist == null) {
            if (timeBar != null) timeBar.setSkipSegments(0, null, null);
            return;
        }
        if (!mPrefs.skipEnabled) {
            timeBar.setSkipSegments(0, null, null);
            return;
        }
        long duration = player.getDuration();
        LampaPlaylist.Item item = lampaPlaylist.getCurrent();
        if (duration == C.TIME_UNSET || duration <= 0 || item == null || item.segments.isEmpty()) {
            timeBar.setSkipSegments(0, null, null);
            return;
        }

        List<SkipSegment> valid = validatedSkipSegments(item, duration);
        long[] starts = new long[valid.size()];
        long[] ends = new long[valid.size()];
        for (int index = 0; index < valid.size(); index++) {
            starts[index] = valid.get(index).startMs;
            ends[index] = valid.get(index).endMs;
        }
        timeBar.setSkipSegments(duration, starts, ends);
    }

    private List<SkipSegment> validatedSkipSegments(LampaPlaylist.Item item, long duration) {
        ArrayList<SkipSegment> segments = new ArrayList<>();
        if (item != null) {
            for (LampaPlaylist.Segment segment : item.segments) {
                segments.add(new SkipSegment(segment.startMs, segment.endMs,
                        skipKind(segment), segment.source));
            }
        }
        return SkipPolicy.validate(segments, duration);
    }

    private SkipSegment.Kind skipKind(LampaPlaylist.Segment segment) {
        if (segment == null) return SkipSegment.Kind.UNKNOWN;
        if ("ad".equalsIgnoreCase(segment.type) || "ad".equalsIgnoreCase(segment.kind)) {
            return SkipSegment.Kind.AD;
        }
        String kind = segment.kind == null ? "" : segment.kind.trim().toLowerCase(Locale.US);
        switch (kind) {
            case "intro": return SkipSegment.Kind.INTRO;
            case "recap": return SkipSegment.Kind.RECAP;
            case "outro": return SkipSegment.Kind.OUTRO;
            case "credits": return SkipSegment.Kind.CREDITS;
            case "preview": return SkipSegment.Kind.PREVIEW;
            default: return SkipSegment.Kind.UNKNOWN;
        }
    }

    private SkipPolicy.Mode skipModeForPosition(List<SkipSegment> segments, long position,
                                                long duration) {
        SkipSegment relevant = null;
        for (SkipSegment segment : segments) {
            if (segment.contains(position)
                    || (segment.startMs > position && segment.startMs - position <= 5000)) {
                relevant = segment;
                break;
            }
        }
        if (relevant != null && relevant.kind == SkipSegment.Kind.AD) {
            return SkipPolicy.Mode.AUTO;
        }
        String preference = relevant != null && isCreditsSegment(relevant, duration)
                ? mPrefs.skipModeCredits : mPrefs.skipMode;
        return parseSkipMode(preference);
    }

    private SkipPolicy.Mode parseSkipMode(String value) {
        if (Prefs.SKIP_MODE_AUTO.equals(value)) return SkipPolicy.Mode.AUTO;
        if (Prefs.SKIP_MODE_BRIEF.equals(value)) return SkipPolicy.Mode.BRIEF_BUTTON;
        return SkipPolicy.Mode.FULL_BUTTON;
    }

    private String segmentButtonText(SkipSegment segment, long duration) {
        if (reachesMediaEnd(segment, duration)
                && lampaPlaylist != null && lampaPlaylist.hasNext()) {
            return getString(R.string.next_episode);
        }
        return getString(R.string.skip_action);
    }

    private boolean isCreditsSegment(SkipSegment segment, long duration) {
        if (segment == null) return false;
        if (segment.kind == SkipSegment.Kind.OUTRO
                || segment.kind == SkipSegment.Kind.CREDITS) return true;
        return duration != C.TIME_UNSET && duration > 0
                && segment.endMs >= Math.round(duration * 0.75d);
    }

    private boolean reachesMediaEnd(SkipSegment segment, long duration) {
        return segment != null && duration != C.TIME_UNSET && duration > 0
                && segment.endMs >= duration - 1500;
    }

    private boolean isSkipActionEnabled() {
        return lampaSkipPanel != null && lampaSkipPanel.getVisibility() == View.VISIBLE
                && skipModel != null && skipModel.enabled();
    }

    private void postPrimaryTvFocus() {
        if (!isTvBox || playerView == null) return;
        playerView.removeCallbacks(primaryTvFocusRunnable);
        playerView.post(primaryTvFocusRunnable);
    }

    private void styleTvBottomControls(ViewGroup controls) {
        if (!isTvBox || controls == null) return;
        controls.setClipChildren(false);
        controls.setClipToPadding(false);
        for (int i = 0; i < controls.getChildCount(); i++) {
            View child = controls.getChildAt(i);
            if (!(child instanceof ImageButton)) continue;
            child.setBackgroundResource(R.drawable.ua_tv_control_background);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                child.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                        this, R.animator.ua_tv_control_focus));
            }
        }
    }

    private void requestPrimaryTvFocus() {
        if (playerView == null || exoPlayPause == null) return;
        TvFocusPolicy.Target target = TvFocusPolicy.choose(isTvBox,
                playerView.isControllerFullyVisible(), isSkipActionEnabled(),
                exoPlayPause.isShown());
        if (target == TvFocusPolicy.Target.SKIP && lampaSkipButton != null) {
            lampaSkipButton.requestFocus();
        } else if (target == TvFocusPolicy.Target.PLAY_PAUSE) {
            exoPlayPause.requestFocus();
        } else if (isTvBox && playerView.isControllerFullyVisible()
                && loadingProgressBar != null && loadingProgressBar.isShown()) {
            parkFocusOnLoadingRing();
        }
    }

    private void activateSkipModel() {
        if (!isSkipActionEnabled() || player == null) return;
        SkipController.Model action = skipController.activate(skipModel,
                Math.max(0, player.getCurrentPosition()), player.getDuration(),
                lampaPlaylist != null && lampaPlaylist.hasNext(),
                SystemClock.elapsedRealtime());
        skipModel = action;
        if (action.action != SkipController.Action.NONE) {
            applySkipAction(action);
        } else {
            focusedSkipKey = null;
            lampaSkipPanel.setVisibility(View.GONE);
        }
    }

    private void applySkipAction(SkipController.Model action) {
        if (action == null || player == null) return;
        switch (action.action) {
            case SEEK_TO_END:
                player.setSeekParameters(SeekParameters.EXACT);
                player.seekTo(action.targetMs);
                break;
            case PLAY_NEXT:
                if (lampaPlaylist != null && lampaPlaylist.hasNext()) {
                    skipUndoPlaylistIndex = lampaPlaylist.getCurrentIndex();
                    skipPlaylistAdvance = true;
                    playRelativeEpisode(1, true);
                }
                break;
            case RESTORE_POSITION:
                if (lampaPlaylist != null && skipUndoPlaylistIndex >= 0
                        && skipUndoPlaylistIndex < lampaPlaylist.size()
                        && skipUndoPlaylistIndex != lampaPlaylist.getCurrentIndex()) {
                    pendingPlaylistRestorePosition = action.targetMs;
                    int target = skipUndoPlaylistIndex;
                    skipUndoPlaylistIndex = -1;
                    playPlaylistIndex(target, false);
                } else {
                    player.setSeekParameters(SeekParameters.EXACT);
                    player.seekTo(action.targetMs);
                    skipUndoPlaylistIndex = -1;
                }
                break;
            default:
                break;
        }
        focusedSkipKey = null;
        if (lampaSkipPanel != null) lampaSkipPanel.setVisibility(View.GONE);
    }

    private void playRelativeEpisode(int offset) {
        playRelativeEpisode(offset, false);
    }

    private void playRelativeEpisode(int offset, boolean automatic) {
        if (lampaPlaylist == null) return;
        int target = lampaPlaylist.getCurrentIndex() + offset;
        if (target >= 0 && target < lampaPlaylist.size()) {
            playPlaylistIndex(target, automatic);
        }
    }

    private void updateEpisodeControls() {
        if (exoPrevious == null || exoNext == null || lampaPlaylist == null) return;
        int index = lampaPlaylist.getCurrentIndex();
        exoPrevious.setVisibility(index > 0 ? View.VISIBLE : View.INVISIBLE);
        exoNext.setVisibility(index + 1 < lampaPlaylist.size() ? View.VISIBLE : View.INVISIBLE);
        exoPrevious.setEnabled(index > 0);
        exoNext.setEnabled(index + 1 < lampaPlaylist.size());
        exoPrevious.setAlpha(index > 0 ? 1f : 0f);
        exoNext.setAlpha(index + 1 < lampaPlaylist.size() ? 1f : 0f);
        if (buttonPlaylist != null) {
            buttonPlaylist.setVisibility(lampaPlaylist.size() > 1 ? View.VISIBLE : View.GONE);
        }
    }

    private void showLampaPlaylist() {
        if (lampaPlaylist == null || lampaPlaylist.size() < 2) return;

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(36, 0, 0, 0));

        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Utils.dpToPx(8), 0, Utils.dpToPx(8), Utils.dpToPx(8));
        panel.setBackground(lampaBackground(lampaMenuColor(4, 18, 40),
                Color.rgb(240, 183, 38), 10));

        final TextView heading = new TextView(this);
        heading.setText(R.string.playlist_title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(Utils.dpToPx(18), 0, Utils.dpToPx(18), 0);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(58)));

        View headingDivider = new View(this);
        headingDivider.setBackgroundColor(Color.rgb(35, 58, 84));
        panel.addView(headingDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(1)));

        final ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Utils.dpToPx(4));
        list.setSelector(lampaBackground(Color.argb(56, 10, 39, 76),
                Color.rgb(240, 183, 38), 7));
        list.setDrawSelectorOnTop(true);
        list.setItemsCanFocus(false);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setPadding(Utils.dpToPx(8), 0, Utils.dpToPx(8), Utils.dpToPx(8));
        list.setClipToPadding(false);
        list.setAdapter(new EpisodeAdapter());
        list.setOnItemClickListener((parent, view, which, id) -> {
            dialog.dismiss();
            if (which != lampaPlaylist.getCurrentIndex()) {
                playPlaylistIndex(which, false);
            }
        });
        installTvListNavigation(list);
        panel.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.max(Utils.dpToPx(330), Math.min(
                (int) (screenWidth * (isTvBox ? 0.46f : 0.52f)), Utils.dpToPx(620)));
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int topInset = isTvBox ? Utils.dpToPx(8) : Utils.dpToPx(28);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, Math.max(Utils.dpToPx(280), screenHeight - topInset),
                Gravity.END | Gravity.BOTTOM);
        overlay.addView(panel, panelParams);
        overlay.setOnClickListener(view -> dialog.dismiss());
        panel.setOnClickListener(view -> { });

        dialog.setContentView(overlay);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setDimAmount(0f);
        }
        dialog.setOnShowListener(ignored -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            list.setSelection(lampaPlaylist.getCurrentIndex());
            list.setItemChecked(lampaPlaylist.getCurrentIndex(), true);
            list.requestFocus();
        });
        dialog.show();
    }

    private void showQualityDialog() {
        if (player == null) {
            Toast.makeText(this, R.string.quality_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        final ArrayList<VideoQualityChoice> choices = new ArrayList<>();
        choices.add(VideoQualityChoice.auto(getString(R.string.quality_auto)));
        choices.add(VideoQualityChoice.maximum(getString(R.string.quality_maximum)));

        final HashMap<Integer, VideoQualityChoice> renditions = new HashMap<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSupported(index)) continue;
                Format format = group.getTrackFormat(index);
                int longSide = Math.max(format.width, format.height);
                if (longSide <= 0) continue;
                int bitrateValue = format.averageBitrate > 0
                        ? format.averageBitrate : format.peakBitrate;
                VideoQualityChoice previous = renditions.get(longSide);
                if (previous == null || bitrateValue > previous.bitrate) {
                    String details = MediaFormatLabel.videoDetails(format);
                    renditions.put(longSide, VideoQualityChoice.track(
                            MediaFormatLabel.qualityLabel(format), details, "",
                            group.getMediaTrackGroup(), index, bitrateValue));
                }
            }
        }
        ArrayList<Integer> longSides = new ArrayList<>(renditions.keySet());
        Collections.sort(longSides, Collections.reverseOrder());
        for (Integer longSide : longSides) choices.add(renditions.get(longSide));

        LampaPlaylist.Item item = lampaPlaylist == null ? null : lampaPlaylist.getCurrent();
        if (item != null && !item.quality.isEmpty()) {
            ArrayList<String> labels = new ArrayList<>(item.quality.keySet());
            Collections.sort(labels,
                    (left, right) -> Integer.compare(qualityNumber(right), qualityNumber(left)));
            for (String label : labels) {
                String url = item.quality.get(label);
                if (url != null && !url.trim().isEmpty()) {
                    choices.add(VideoQualityChoice.source(label, getString(R.string.quality_source), url));
                }
            }
        }

        if (choices.size() <= 2) {
            Toast.makeText(this, R.string.quality_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(44, 0, 0, 0));

        final int gold = Color.rgb(240, 183, 38);
        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Utils.dpToPx(18), Utils.dpToPx(8), Utils.dpToPx(18), Utils.dpToPx(14));
        panel.setBackground(lampaBackground(lampaMenuColor(4, 18, 40), gold, 10));

        final TextView heading = new TextView(this);
        heading.setText(R.string.quality_title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(Utils.dpToPx(12), 0, Utils.dpToPx(12), 0);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(66)));

        final View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(35, 58, 84));
        panel.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(1)));

        final ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Utils.dpToPx(4));
        list.setSelector(lampaBackground(Color.argb(56, 10, 39, 76), gold, 7));
        list.setDrawSelectorOnTop(true);
        list.setItemsCanFocus(false);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setPadding(0, Utils.dpToPx(6), 0, 0);
        list.setClipToPadding(false);
        list.setAdapter(new QualityAdapter(choices));
        list.setOnItemClickListener((parent, view, which, id) -> {
            applyVideoQuality(choices.get(which));
            dialog.dismiss();
        });
        installTvListNavigation(list);
        int visibleRows = Math.min(choices.size(), 6);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int desiredListHeight = Utils.dpToPx(visibleRows * 70 + 10);
        int maxListHeight = Math.max(Utils.dpToPx(150),
                screenHeight - Utils.dpToPx(110));
        panel.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.min(desiredListHeight, maxListHeight)));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min((int) (screenWidth * (isTvBox ? 0.72f : 0.78f)),
                Utils.dpToPx(920));
        panelWidth = Math.max(panelWidth, Math.min(Utils.dpToPx(360), screenWidth));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(panel, panelParams);
        overlay.setOnClickListener(view -> dialog.dismiss());
        panel.setOnClickListener(view -> { });

        dialog.setContentView(overlay);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0f);
        }
        dialog.setOnShowListener(ignored -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
            int selected = selectedQualityIndex(choices);
            list.setSelection(selected);
            list.setItemChecked(selected, true);
            list.post(list::requestFocus);
        });
        dialog.show();
    }

    private void installTvListNavigation(ListView list) {
        list.setFocusable(true);
        list.setFocusableInTouchMode(true);
        list.setOnKeyListener((view, keyCode, event) -> {
            boolean navigationKey = keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (!navigationKey) return false;
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;

            int count = list.getAdapter() == null ? 0 : list.getAdapter().getCount();
            if (count == 0) return true;
            int selected = list.getSelectedItemPosition();
            if (selected == ListView.INVALID_POSITION) selected = 0;

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int target = keyCode == KeyEvent.KEYCODE_DPAD_UP
                        ? Math.max(0, selected - 1)
                        : Math.min(count - 1, selected + 1);
                list.setSelection(target);
                list.setItemChecked(target, true);
                list.smoothScrollToPosition(target);
                return true;
            }

            int childIndex = selected - list.getFirstVisiblePosition();
            View child = list.getChildAt(childIndex);
            if (child != null) {
                list.performItemClick(child, selected, list.getAdapter().getItemId(selected));
            }
            return true;
        });
    }

    private void applyVideoQuality(VideoQualityChoice choice) {
        if (player == null || choice == null) return;
        selectedVideoQualityMode = choice.mode;
        selectedVideoTrackGroup = choice.group;
        selectedVideoTrackIndex = choice.trackIndex;
        if (choice.sourceUrl != null && lampaPlaylist != null) {
            LampaPlaylist.Item item = lampaPlaylist.getCurrent();
            if (item == null || choice.sourceUrl.equals(item.url)) return;
            item.positionMs = Math.max(0, player.getCurrentPosition());
            boolean resume = player.getPlayWhenReady();
            item.url = choice.sourceUrl;
            decoderQualityFallbackTried = false;
            alternateStreamTypeTried = false;
            resetDecoderCompatibilityMode();
            resetResolverResponseState();
            forcedStreamMimeType = null;
            applyPlaylistItem(item, false);
            restorePlayState = resume;
            initializePlayer();
            return;
        }

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setForceHighestSupportedBitrate(choice.mode == VideoQualityChoice.MODE_MAXIMUM);
        if (choice.mode == VideoQualityChoice.MODE_TRACK && choice.group != null) {
            builder.setOverrideForType(new TrackSelectionOverride(
                    choice.group, Collections.singletonList(choice.trackIndex)));
        }
        player.setTrackSelectionParameters(builder.build());
        updateLampaTrackDetails();
    }

    private void fallbackFromSlowAv1(int currentHeight) {
        if (tryLowerQualityRecovery(currentHeight)) {
            Utils.showText(playerView, getString(R.string.decoder_quality_fallback), 3500);
        }
    }

    private boolean tryLowerQualityRecovery(int currentHeight) {
        if (player == null || decoderQualityFallbackTried) return false;

        LampaPlaylist.Item item = lampaPlaylist == null ? null : lampaPlaylist.getCurrent();
        if (item != null) {
            item.positionMs = Math.max(0, player.getCurrentPosition());
            String lowerUrl = lampaPlaylist.useLowerQuality(item);
            if (lowerUrl != null) {
                boolean resume = player.getPlayWhenReady();
                decoderQualityFallbackTried = true;
                alternateStreamTypeTried = false;
                forcedStreamMimeType = null;
                applyPlaylistItem(item, false);
                restorePlayState = resume;
                initializePlayer();
                return true;
            }
        }

        VideoQualityChoice bestLower = null;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSupported(index)) continue;
                Format format = group.getTrackFormat(index);
                if (format.height <= 0 || format.height >= currentHeight) continue;
                if (bestLower == null || format.height > qualityNumber(bestLower.label)
                        || (format.height == qualityNumber(bestLower.label)
                        && format.bitrate > bestLower.bitrate)) {
                    bestLower = VideoQualityChoice.track(
                            format.height + "p", group.getMediaTrackGroup(), index, format.bitrate);
                }
            }
        }
        if (bestLower != null) {
            decoderQualityFallbackTried = true;
            applyVideoQuality(bestLower);
            return true;
        }
        return false;
    }

    private static int qualityNumber(String label) {
        if (label == null) return 0;
        String normalized = label.toLowerCase(Locale.US);
        if (normalized.contains("4k") || normalized.contains("uhd")) return 2160;
        String digits = label.replaceAll("[^0-9]", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class VideoQualityChoice {
        static final int MODE_AUTO = 0;
        static final int MODE_MAXIMUM = 1;
        static final int MODE_TRACK = 2;
        static final int MODE_SOURCE = 3;
        final String label;
        final String details;
        final String bitrateText;
        final int mode;
        final TrackGroup group;
        final int trackIndex;
        final int bitrate;
        final String sourceUrl;

        private VideoQualityChoice(String label, String details, String bitrateText,
                                   int mode, TrackGroup group, int trackIndex,
                                   int bitrate, String sourceUrl) {
            this.label = label;
            this.details = details;
            this.bitrateText = bitrateText;
            this.mode = mode;
            this.group = group;
            this.trackIndex = trackIndex;
            this.bitrate = bitrate;
            this.sourceUrl = sourceUrl;
        }

        static VideoQualityChoice auto(String label) {
            return new VideoQualityChoice(label, "", "", MODE_AUTO, null, -1, -1, null);
        }

        static VideoQualityChoice maximum(String label) {
            return new VideoQualityChoice(label, "", "", MODE_MAXIMUM, null, -1, -1, null);
        }

        static VideoQualityChoice track(String label, TrackGroup group, int index, int bitrate) {
            return new VideoQualityChoice(label, "", "", MODE_TRACK, group, index, bitrate, null);
        }

        static VideoQualityChoice track(String label, String details, String bitrateText,
                                        TrackGroup group, int index, int bitrate) {
            return new VideoQualityChoice(label, details, bitrateText,
                    MODE_TRACK, group, index, bitrate, null);
        }

        static VideoQualityChoice source(String label, String details, String url) {
            return new VideoQualityChoice(label, details, "", MODE_SOURCE, null, -1, -1, url);
        }
    }

    private int selectedQualityIndex(List<VideoQualityChoice> choices) {
        LampaPlaylist.Item current = lampaPlaylist == null ? null : lampaPlaylist.getCurrent();
        for (int index = 0; index < choices.size(); index++) {
            VideoQualityChoice choice = choices.get(index);
            if (choice.mode == VideoQualityChoice.MODE_SOURCE && current != null
                    && choice.sourceUrl != null && choice.sourceUrl.equals(current.url)) return index;
            if (choice.mode == VideoQualityChoice.MODE_TRACK
                    && selectedVideoQualityMode == VideoQualityChoice.MODE_TRACK
                    && choice.group == selectedVideoTrackGroup
                    && choice.trackIndex == selectedVideoTrackIndex) return index;
            if (choice.mode == selectedVideoQualityMode
                    && (choice.mode == VideoQualityChoice.MODE_AUTO
                    || choice.mode == VideoQualityChoice.MODE_MAXIMUM)) return index;
        }
        return 0;
    }

    private final class QualityAdapter extends BaseAdapter {
        private final List<VideoQualityChoice> choices;

        QualityAdapter(List<VideoQualityChoice> choices) {
            this.choices = choices;
        }

        @Override public int getCount() { return choices.size(); }
        @Override public VideoQualityChoice getItem(int position) { return choices.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            QualityRow holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(PlayerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Utils.dpToPx(12), Utils.dpToPx(6), Utils.dpToPx(14), Utils.dpToPx(6));
                row.setMinimumHeight(Utils.dpToPx(66));
                row.setFocusable(false);
                row.setClickable(false);

                TextView badge = new TextView(PlayerActivity.this);
                badge.setGravity(Gravity.CENTER);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                row.addView(badge, new LinearLayout.LayoutParams(
                        Utils.dpToPx(112), Utils.dpToPx(44)));

                LinearLayout textBlock = new LinearLayout(PlayerActivity.this);
                textBlock.setOrientation(LinearLayout.VERTICAL);
                textBlock.setGravity(Gravity.CENTER_VERTICAL);
                TextView title = new TextView(PlayerActivity.this);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                title.setTextColor(Color.WHITE);
                title.setSingleLine(true);
                TextView details = new TextView(PlayerActivity.this);
                details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                details.setTextColor(Color.rgb(145, 178, 219));
                details.setSingleLine(true);
                textBlock.addView(title);
                textBlock.addView(details);
                LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                blockParams.setMarginStart(Utils.dpToPx(14));
                row.addView(textBlock, blockParams);

                TextView bitrate = new TextView(PlayerActivity.this);
                bitrate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                bitrate.setTextColor(Color.rgb(145, 178, 219));
                bitrate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                bitrate.setSingleLine(true);
                row.addView(bitrate, new LinearLayout.LayoutParams(
                        Utils.dpToPx(128), ViewGroup.LayoutParams.MATCH_PARENT));

                TextView check = new TextView(PlayerActivity.this);
                check.setText("✓");
                check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                check.setTextColor(Color.rgb(240, 183, 38));
                check.setGravity(Gravity.CENTER);
                row.addView(check, new LinearLayout.LayoutParams(
                        Utils.dpToPx(42), ViewGroup.LayoutParams.MATCH_PARENT));

                holder = new QualityRow(badge, title, details, bitrate, check);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (QualityRow) convertView.getTag();
            }

            VideoQualityChoice choice = getItem(position);
            boolean special = choice.mode == VideoQualityChoice.MODE_AUTO
                    || choice.mode == VideoQualityChoice.MODE_MAXIMUM;
            holder.badge.setText(choice.mode == VideoQualityChoice.MODE_AUTO ? "A"
                    : choice.mode == VideoQualityChoice.MODE_MAXIMUM ? "★" : choice.label);
            holder.title.setText(special ? choice.label : choice.details);
            holder.details.setText(choice.mode == VideoQualityChoice.MODE_AUTO
                    ? getString(R.string.quality_auto_description)
                    : choice.mode == VideoQualityChoice.MODE_MAXIMUM
                    ? getString(R.string.quality_maximum_badge) : "");
            holder.details.setTextColor(choice.mode == VideoQualityChoice.MODE_MAXIMUM
                    ? Color.rgb(240, 183, 38) : Color.rgb(145, 178, 219));
            holder.bitrate.setText(choice.bitrateText);
            holder.check.setVisibility(position == selectedQualityIndex(choices)
                    ? View.VISIBLE : View.INVISIBLE);
            styleQualityBadge(holder.badge, false);
            styleQualityRow(convertView, holder, false);
            return convertView;
        }
    }

    private void styleQualityRow(View row, QualityRow holder, boolean focused) {
        int gold = Color.rgb(240, 183, 38);
        row.setBackground(lampaBackground(focused
                ? Color.rgb(10, 39, 76) : Color.argb(80, 4, 18, 40),
                focused ? gold : Color.rgb(35, 58, 84), 7));
        styleQualityBadge(holder.badge, focused);
    }

    private void styleQualityBadge(TextView badge, boolean focused) {
        badge.setTextColor(focused ? Color.rgb(255, 204, 74) : Color.rgb(145, 178, 219));
        badge.setBackground(lampaBackground(Color.argb(70, 4, 18, 40),
                focused ? Color.rgb(240, 183, 38) : Color.rgb(62, 91, 126), 6));
    }

    private static final class QualityRow {
        final TextView badge;
        final TextView title;
        final TextView details;
        final TextView bitrate;
        final TextView check;

        QualityRow(TextView badge, TextView title, TextView details,
                   TextView bitrate, TextView check) {
            this.badge = badge;
            this.title = title;
            this.details = details;
            this.bitrate = bitrate;
            this.check = check;
        }
    }

    private final class EpisodeAdapter extends BaseAdapter {
        @Override public int getCount() { return lampaPlaylist == null ? 0 : lampaPlaylist.size(); }
        @Override public LampaPlaylist.Item getItem(int position) { return lampaPlaylist.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            EpisodeRow holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(PlayerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Utils.dpToPx(10), Utils.dpToPx(8), Utils.dpToPx(14), Utils.dpToPx(8));
                row.setMinimumHeight(Utils.dpToPx(94));
                row.setFocusable(false);
                row.setClickable(false);

                FrameLayout preview = new FrameLayout(PlayerActivity.this);
                ImageView thumbnail = new ImageView(PlayerActivity.this);
                thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbnail.setBackgroundColor(Color.rgb(35, 35, 35));
                preview.addView(thumbnail, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                TextView number = new TextView(PlayerActivity.this);
                number.setTextColor(Color.WHITE);
                number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                number.setGravity(Gravity.CENTER);
                number.setBackgroundColor(Color.argb(190, 0, 0, 0));
                FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(
                        Utils.dpToPx(30), Utils.dpToPx(28), Gravity.START | Gravity.TOP);
                preview.addView(number, numberParams);

                LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                        Utils.dpToPx(154), Utils.dpToPx(86));
                row.addView(preview, previewParams);

                TextView title = new TextView(PlayerActivity.this);
                title.setTextColor(Color.WHITE);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                title.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                titleParams.setMarginStart(Utils.dpToPx(16));
                row.addView(title, titleParams);

                holder = new EpisodeRow(thumbnail, number, title);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (EpisodeRow) convertView.getTag();
            }

            LampaPlaylist.Item item = getItem(position);
            holder.position = position;
            holder.number.setText(String.valueOf(position + 1));
            holder.title.setText(item.displayTitle(position));
            holder.title.setTypeface(Typeface.DEFAULT,
                    position == lampaPlaylist.getCurrentIndex() ? Typeface.BOLD : Typeface.NORMAL);
            holder.number.setTextColor(position == lampaPlaylist.getCurrentIndex()
                    ? Color.rgb(255, 204, 74) : Color.WHITE);
            styleEpisodeRow(convertView, holder, false);

            Glide.with(PlayerActivity.this)
                    .load(item.thumbnail)
                    .placeholder(new ColorDrawable(Color.rgb(35, 35, 35)))
                    .error(new ColorDrawable(Color.rgb(35, 35, 35)))
                    .centerCrop()
                    .into(holder.thumbnail);
            return convertView;
        }
    }

    private static final class EpisodeRow {
        final ImageView thumbnail;
        final TextView number;
        final TextView title;
        int position;

        EpisodeRow(ImageView thumbnail, TextView number, TextView title) {
            this.thumbnail = thumbnail;
            this.number = number;
            this.title = title;
        }
    }

    private void styleEpisodeRow(View row, EpisodeRow holder, boolean focused) {
        boolean current = lampaPlaylist != null
                && holder.position == lampaPlaylist.getCurrentIndex();
        int fill = focused ? Color.rgb(10, 39, 76)
                : current ? Color.rgb(19, 54, 94) : Color.argb(55, 4, 18, 40);
        int stroke = focused ? Color.rgb(240, 183, 38) : Color.rgb(35, 58, 84);
        row.setBackground(lampaBackground(fill, stroke, 7));
    }

    private void playPlaylistIndex(int index, boolean outgoingEnded) {
        if (lampaPlaylist == null || switchingPlaylistItem || lampaPlaylist.get(index) == null) return;
        if (!skipPlaylistAdvance) {
            skipController.reset();
            skipModel = null;
            focusedSkipKey = null;
            skipUndoPlaylistIndex = -1;
        }
        switchingPlaylistItem = true;
        recordCurrentPlaylistItem(outgoingEnded);
        updateLoading(true);
        lampaPlaylist.resolve(index, new LampaPlaylist.ResolveCallback() {
            @Override
            public void onResolved(LampaPlaylist.Item item) {
                if (isFinishing() || isDestroyed()) return;
                lampaPlaylist.setCurrentIndex(index);
                playlistCurrentRecorded = false;
                alternateStreamTypeTried = false;
                decoderQualityFallbackTried = false;
                resetDecoderCompatibilityMode();
                resetResolverResponseState();
                forcedStreamMimeType = null;
                applyPlaylistItem(item, false);
                if (pendingPlaylistRestorePosition != C.TIME_UNSET) {
                    item.positionMs = Math.max(0, pendingPlaylistRestorePosition);
                    mPrefs.updatePosition(item.positionMs);
                    pendingPlaylistRestorePosition = C.TIME_UNSET;
                }
                playbackFinished = false;
                restorePlayState = true;
                focusPlay = true;
                skipPlaylistAdvance = false;
                switchingPlaylistItem = false;
                initializePlayer();
                syncRoomPlaylistStep(outgoingEnded);
                lampaPlaylist.preResolveNext();
            }

            @Override
            public void onError(String message) {
                skipPlaylistAdvance = false;
                pendingPlaylistRestorePosition = C.TIME_UNSET;
                switchingPlaylistItem = false;
                playlistCurrentRecorded = false;
                updateLoading(false);
                Toast.makeText(PlayerActivity.this,
                        getString(R.string.playlist_resolve_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void recordCurrentPlaylistItem(boolean ended) {
        if (lampaPlaylist == null || lampaPlaylist.isEmpty() || playlistCurrentRecorded) return;
        long position = 0;
        long duration = 0;
        if (player != null) {
            position = Math.max(0, player.getCurrentPosition());
            long playerDuration = player.getDuration();
            if (playerDuration != C.TIME_UNSET) duration = Math.max(0, playerDuration);
        }
        LampaPlaylist.Item item = lampaPlaylist.getCurrent();
        boolean completed = ended && playlistPlaybackEverReady;
        item.positionMs = completed ? 0 : position;
        mPrefs.selectPositionKey(item.resumeKey(), item.positionMs);
        mPrefs.updatePosition(item.positionMs);
        lampaPlaylist.recordResult(item, position, duration, completed);
        playlistCurrentRecorded = true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (restoreOrientationLock) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                restoreOrientationLock = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (resultCode == RESULT_OK && alive) {
            releasePlayer();
        }

        if (requestCode == REQUEST_CHOOSER_VIDEO || requestCode == REQUEST_CHOOSER_VIDEO_MEDIASTORE) {
            if (resultCode == RESULT_OK) {
                resetApiAccess();
                restorePlayState = false;

                final Uri uri = data.getData();

                if (requestCode == REQUEST_CHOOSER_VIDEO) {
                    boolean uriAlreadyTaken = false;

                    // https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
                    final ContentResolver contentResolver = getContentResolver();
                    for (UriPermission persistedUri : contentResolver.getPersistedUriPermissions()) {
                        if (persistedUri.getUri().equals(mPrefs.scopeUri)) {
                            continue;
                        } else if (persistedUri.getUri().equals(uri)) {
                            uriAlreadyTaken = true;
                        } else {
                            try {
                                contentResolver.releasePersistableUriPermission(persistedUri.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (!uriAlreadyTaken && uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                    }
                }

                mPrefs.setPersistent(true);
                mPrefs.updateMedia(this, uri, data.getType());

                if (requestCode == REQUEST_CHOOSER_VIDEO) {
                    searchSubtitles();
                }
            }
        } else if (requestCode == REQUEST_CHOOSER_SUBTITLE || requestCode == REQUEST_CHOOSER_SUBTITLE_MEDIASTORE) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();

                if (requestCode == REQUEST_CHOOSER_SUBTITLE) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }

                handleSubtitles(uri);
            }
        } else if (requestCode == REQUEST_CHOOSER_SCOPE_DIR) {
            if (resultCode == RESULT_OK) {
                final Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    mPrefs.updateScope(uri);
                    mPrefs.markScopeAsked();
                    searchSubtitles();
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == REQUEST_SETTINGS) {
            mPrefs.loadUserPreferences();
            systemVolume = mPrefs.systemVolume;
            playerVolume = mPrefs.playerVolume;
            maxVolumeBoost = mPrefs.volumeBoost;
            volumeGesturesEnabled = mPrefs.volumeGesturesEnabled;
            brightnessGesturesEnabled = mPrefs.brightnessGesturesEnabled;
            if (boostLevel * 10 > maxVolumeBoost) {
                boostLevel = (int) Math.ceil(maxVolumeBoost / 10f);
            }
            Utils.applyPlayerVolume();
            Utils.applyBoost();
            applyPreferredTextLanguages();
            updateSubtitleStyle(this);
            updateLampaSegmentMarkers();
            updateLampaSkipUi();
            if (mPrefs.skipEnabled && mPrefs.skipFetchOnline && player != null
                    && lampaPlaylist != null && player.getDuration() > 0) {
                LampaPlaylist.Item item = lampaPlaylist.getCurrent();
                lampaPlaylist.fetchRemoteSegments(item, player.getDuration(), loaded -> {
                    if (lampaPlaylist != null && loaded == lampaPlaylist.getCurrent()) {
                        updateLampaSegmentMarkers();
                        updateLampaSkipUi();
                    }
                });
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        // Init here because onStart won't follow when app was only paused when file chooser was shown
        // (for example pop-up file chooser on tablets)
        if (resultCode == RESULT_OK && alive) {
            initializePlayer();
        }
    }

    private void handleSubtitles(Uri uri) {
        // Convert subtitles to UTF-8 if necessary
        SubtitleUtils.clearCache(this);
        uri = Utils.convertToUTF(this, uri);
        mPrefs.updateSubtitle(uri);
    }

    /** Adds one external subtitle without discarding launcher-provided tracks or the play position. */
    boolean addSubtitleTrack(Uri subtitleUri) {
        if (player == null || subtitleUri == null) return false;
        MediaItem current = player.getCurrentMediaItem();
        if (current == null || containsSubtitle(current, subtitleUri)) return false;

        MediaItem updated = withSubtitle(current,
                SubtitleUtils.buildSubtitle(this, subtitleUri, null, true));
        long position = player.getCurrentPosition();
        boolean resume = player.getPlayWhenReady();
        player.setMediaItems(Collections.singletonList(updated), 0, position);
        player.prepare();
        player.setPlayWhenReady(resume);
        return true;
    }

    private MediaItem.SubtitleConfiguration rememberedSubtitle() {
        if (mPrefs.subtitleUri == null || !Utils.fileExists(this, mPrefs.subtitleUri)) {
            return null;
        }
        return SubtitleUtils.buildSubtitle(this, mPrefs.subtitleUri, null, true);
    }

    private static MediaItem withSubtitle(MediaItem item,
                                           MediaItem.SubtitleConfiguration subtitle) {
        List<MediaItem.SubtitleConfiguration> subtitles = new ArrayList<>();
        if (item.localConfiguration != null) {
            subtitles.addAll(item.localConfiguration.subtitleConfigurations);
        }
        if (!containsSubtitle(subtitles, subtitle.uri)) subtitles.add(subtitle);
        return item.buildUpon().setSubtitleConfigurations(subtitles).build();
    }

    private static boolean containsSubtitle(MediaItem item, Uri uri) {
        return item.localConfiguration != null
                && containsSubtitle(item.localConfiguration.subtitleConfigurations, uri);
    }

    private static boolean containsSubtitle(
            List<MediaItem.SubtitleConfiguration> subtitles, Uri uri) {
        for (MediaItem.SubtitleConfiguration subtitle : subtitles) {
            if (subtitle.uri.equals(uri)) return true;
        }
        return false;
    }

    private void cancelSubtitleSearch() {
        subtitleSearchGeneration++;
        subtitleSearchStarted = null;
        if (subtitleSearchThread != null) {
            subtitleSearchThread.interrupt();
            subtitleSearchThread = null;
        }
    }

    private void maybeSearchSubtitlesOnline(Tracks tracks) {
        if (player == null || !mPrefs.subtitleSearch || tracks.getGroups().isEmpty()
                || player.getPlaybackState() == Player.STATE_IDLE) {
            return;
        }
        List<String> preferred = AudioLanguagePriority.parse(mPrefs.languageSubtitle);
        if (preferred.isEmpty()) return;

        Set<String> present = new HashSet<>();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int index = 0; index < group.length; index++) {
                Format format = group.getTrackFormat(index);
                if (isPhantomClosedCaption(format)) continue;
                String language = AudioLanguagePriority.normalize(format.language);
                if (language != null) present.add(language);
            }
        }
        List<String> wanted = SubtitleLanguagePolicy.missing(
                preferred, present, mPrefs.subtitleSearchStrict);
        if (wanted.isEmpty()) return;

        MediaId id = currentMediaId();
        if (id.isEmpty() || enabledSubtitleSources().isEmpty()) return;
        String key = id.key() + "|" + wanted + "|" + enabledSubtitleSources();
        if (key.equals(subtitleSearchStarted)) return;
        Long missedAt = subtitleSearchMisses.get(key);
        if (missedAt != null && System.currentTimeMillis() - missedAt < SUBTITLE_MISS_TTL_MS) {
            return;
        }

        String cachePrefix = "subs." + id.key().replaceAll("[^A-Za-z0-9]", "-");
        for (String language : wanted) {
            File cached = new File(getCacheDir(), cachePrefix + "." + language + ".srt");
            if (cached.isFile() && cached.length() > 0) {
                cached.setLastModified(System.currentTimeMillis());
                cancelSubtitleSearch();
                subtitleSearchStarted = key;
                attachSearchedSubtitle(subtitleSearchGeneration, id,
                        Uri.fromFile(cached), language);
                return;
            }
        }

        cancelSubtitleSearch();
        subtitleSearchStarted = key;
        int generation = subtitleSearchGeneration;
        Thread worker = new Thread(() -> {
            AtomicBoolean answered = new AtomicBoolean();
            SubtitleSearch.Result found = null;
            try {
                found = SubtitleSearch.find(id, wanted, mPrefs, result -> {
                    if (generation != subtitleSearchGeneration) return false;
                    List<Uri> urls = new ArrayList<>(result.urls.size());
                    for (String url : result.urls) urls.add(Uri.parse(url));
                    Uri file = new SubtitleFetcher(this, urls,
                            cachePrefix + "." + result.language + ".srt").fetchNow();
                    if (file == null) return false;
                    runOnUiThread(() -> attachSearchedSubtitle(
                            generation, id, file, result.language));
                    return true;
                }, answered);
            } catch (Throwable error) {
                Utils.log("subtitles: search failed " + error);
            }
            if (found == null && answered.get() && !Thread.currentThread().isInterrupted()) {
                subtitleSearchMisses.put(key, System.currentTimeMillis());
            }
        }, "SubtitleSearch");
        worker.setDaemon(true);
        subtitleSearchThread = worker;
        worker.start();
    }

    private void attachSearchedSubtitle(int generation, MediaId id, Uri file, String language) {
        if (generation != subtitleSearchGeneration || player == null
                || !currentMediaId().sameAs(id)) {
            return;
        }
        mPrefs.updateSubtitle(file);
        if (addSubtitleTrack(file)) {
            Toast.makeText(this, getString(R.string.subtitle_search_found,
                    displaySubtitleLanguage(language)), Toast.LENGTH_SHORT).show();
        }
    }

    private String enabledSubtitleSources() {
        return (mPrefs.subtitleSourceRest ? "1" : "")
                + (mPrefs.subtitleSourceStremio ? "2" : "")
                + (mPrefs.subtitleSourceShegu ? "3" : "")
                + (mPrefs.subtitleSourceOpenSubtitles ? "4" : "");
    }

    private static String displaySubtitleLanguage(String language) {
        List<String> codes = OpenSubtitles.toIso639_1(Collections.singletonList(language));
        if (codes.isEmpty()) return language;
        return Locale.forLanguageTag(codes.get(0)).getDisplayLanguage();
    }

    private MediaId currentMediaId() {
        LampaPlaylist.Item item = lampaPlaylist == null ? null : lampaPlaylist.getCurrent();
        if (item != null) {
            return new MediaId(item.imdbId,
                    item.tmdbId > 0 ? String.valueOf(item.tmdbId) : null,
                    item.season, item.episode);
        }
        Bundle extras = getIntent() == null ? null : getIntent().getExtras();
        return new MediaId(firstExtra(extras, "lampaua.imdb_id", "imdb_id"),
                firstExtra(extras, "lampaua.tmdb_id", "tmdb_id"),
                positiveExtra(extras, "lampaua.season", "season"),
                positiveExtra(extras, "lampaua.episode", "episode"));
    }

    private static String firstExtra(Bundle extras, String... keys) {
        if (extras == null) return null;
        for (String key : keys) {
            Object value = extras.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static int positiveExtra(Bundle extras, String... keys) {
        String value = firstExtra(extras, keys);
        if (value == null) return -1;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isPhantomClosedCaption(Format format) {
        return MimeTypes.APPLICATION_CEA608.equals(format.sampleMimeType)
                && format.accessibilityChannel == Format.NO_VALUE;
    }

    private static final class TrackingAudioSink extends ForwardingAudioSink {
        private final Set<String> blockedMimes;
        private volatile boolean passthrough;

        TrackingAudioSink(AudioSink sink, Set<String> initiallyBlocked) {
            super(sink);
            blockedMimes = new CopyOnWriteArraySet<>(initiallyBlocked);
        }

        @Override
        public void configure(AudioSink.AudioSinkConfig config) throws AudioSink.ConfigurationException {
            passthrough = !MimeTypes.AUDIO_RAW.equals(config.format.sampleMimeType);
            super.configure(config);
        }

        boolean isPassthrough() {
            return passthrough;
        }

        void block(String mime) {
            if (mime != null && !mime.isEmpty()) blockedMimes.add(mime);
        }

        @Override
        public int getFormatSupport(Format format) {
            return blockedMimes.contains(format.sampleMimeType)
                    ? AudioSink.SINK_FORMAT_UNSUPPORTED : super.getFormatSupport(format);
        }

        @Override
        public boolean supportsFormat(Format format) {
            return !blockedMimes.contains(format.sampleMimeType) && super.supportsFormat(format);
        }
    }

    private void applyPreferredTextLanguages() {
        if (trackSelector == null) return;
        List<String> languages = AudioLanguagePriority.parse(mPrefs.languageSubtitle);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                // Subtitles only start automatically when they match the user's ordered list.
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setPreferredTextLanguages(languages.toArray(new String[0])));
    }

    public void initializePlayer() {
        cancelFrameRateSwitchWait();
        play = false;
        boolean isNetworkUri = Utils.isSupportedNetworkUri(mPrefs.mediaUri);
        haveMedia = mPrefs.mediaUri != null;
        av1DroppedFrames = 0;
        totalDroppedFrames = 0;
        bandwidthBitrate = 0;
        sampledTransferBitrate = 0;
        transferSampleBytes = TrackNameParsingDataSource.bytesRead.get();
        transferSampleAt = SystemClock.elapsedRealtime();
        videoDecoderName = null;
        audioDecoderName = null;
        resolvedTrackNames.clear();
        if (trackNameProvider != null) trackNameProvider.setTrackNames(resolvedTrackNames);

        String mediaUri = mPrefs.mediaUri == null ? null : mPrefs.mediaUri.toString();
        ensurePlaybackRecoveryKey(mediaUri);
        if (decoderCompatibilityUri != null && !decoderCompatibilityUri.equals(mediaUri)) {
            resetDecoderCompatibilityMode();
        }

        if (pendingStuckRecovery) {
            pendingStuckRecovery = false;
        } else if (!decoderCompatibilityMode) {
            forceHevcForDolbyVision = false;
            stuckRecoveryAttemptedUri = null;
        }

        if (player != null) {
            player.removeListener(playerListener);
            player.clearMediaItems();
            player.release();
            player = null;
        }

        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setAllowMultipleAdaptiveSelections(true));
        final boolean optimize4k = isCurrent4kCandidate();
        if (mPrefs.tunneling && !decoderCompatibilityMode) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true)
            );
        }
        List<String> preferredAudioLanguages = AudioLanguagePriority.parse(mPrefs.languageAudio);
        if (!preferredAudioLanguages.isEmpty()) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredAudioLanguages(preferredAudioLanguages.toArray(new String[0])));
        }
        applyPreferredTextLanguages();
        // Keep the same parser factory when Dv7Converter replaces MatroskaExtractor.
        final SubtitleParser.Factory subtitleParserFactory = new DefaultSubtitleParserFactory();
        // https://github.com/google/ExoPlayer/issues/8571
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setSubtitleParserFactory(subtitleParserFactory)
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);
        int decoderPriority = mPrefs.decoderPriority;
        if (lampaPlaylist != null
                && decoderPriority == DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON) {
            // Android often exposes a slow software AV1 MediaCodec ahead of the
            // bundled dav1d renderer. LampaUA streams should prefer our bundled
            // decoder, which is considerably smoother on non-AV1 chipsets.
            decoderPriority = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        }
        if (decoderCompatibilityMode) {
            decoderPriority = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        }
        DefaultRenderersFactory baseRenderersFactory = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                               boolean enableAudioTrackPlaybackParams) {
                boostProcessor = new BoostAudioProcessor();
                AudioSink sink = new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new AudioProcessor[]{boostProcessor})
                        .build();
                audioSink = new TrackingAudioSink(
                        sink, audioRecoveryState.blockedPassthroughMimeTypes());
                return audioSink;
            }
        };
        @SuppressLint("WrongConstant") DefaultRenderersFactory renderersFactory = baseRenderersFactory
                .setExtensionRendererMode(decoderPriority)
                .setEnableDecoderFallback(true)
                .setMapDV7ToHevc(mPrefs.mapDV7ToHevc || decoderCompatibilityMode);
        if (forceHevcForDolbyVision) {
            renderersFactory.setMediaCodecSelector((mimeType, secure, tunneling) ->
                    MediaCodecSelector.DEFAULT.getDecoderInfos(
                            MimeTypes.VIDEO_DOLBY_VISION.equals(mimeType)
                                    ? MimeTypes.VIDEO_H265 : mimeType,
                            secure, tunneling));
        }

        final boolean convertDv7 = !mPrefs.mapDV7ToHevc && !forceHevcForDolbyVision;
        dv7Converter = convertDv7
                ? new Dv7Converter(extractorsFactory, subtitleParserFactory)
                : null;
        final androidx.media3.extractor.ExtractorsFactory activeExtractorsFactory =
                convertDv7 ? dv7Converter : extractorsFactory;

        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(this, renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(
                        this, activeExtractorsFactory));

        if (optimize4k) {
            playerBuilder.setLoadControl(new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 90_000, 5_000, 8_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build());
        }

        if (haveMedia && isNetworkUri) {
            if (mPrefs.mediaUri.getScheme().toLowerCase().startsWith("http")) {
                HashMap<String, String> headers = new HashMap<>(apiHeaders);
                String userInfo = mPrefs.mediaUri.getUserInfo();
                if (userInfo != null && userInfo.length() > 0 && userInfo.contains(":")) {
                    headers.put("Authorization", "Basic " + Base64.encodeToString(userInfo.getBytes(), Base64.NO_WRAP));
                }
                DefaultHttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(30000);
                if (!headers.isEmpty()) defaultHttpDataSourceFactory.setDefaultRequestProperties(headers);
                DataSource.Factory inspectedDataSource = new ResolverResponseDataSource.Factory(
                        defaultHttpDataSourceFactory, resolverResponseListener);
                DataSource.Factory metadataDataSource = new TrackNameParsingDataSource.Factory(
                        inspectedDataSource, trackNameListener);
                playerBuilder.setMediaSourceFactory(new DefaultMediaSourceFactory(
                        metadataDataSource, activeExtractorsFactory));
            }
        }

        player = playerBuilder.build();
        audioRecoveryState.clearRebuildRequest();
        audioRestartInFlight = false;
        audioRestartRetries = 0;

        if (!mPrefs.allowSystemFrameRate) {
            // Prevent Surface.setFrameRate() votes on pause/seek. Some TV and HDMI devices
            // visibly resynchronise even when Android reports that the switch is seamless.
            player.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF);
        }
        player.addAnalyticsListener(lampaPerformanceListener);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        player.setAudioAttributes(audioAttributes, true);
        player.setVolume(systemVolume ? 1f : Math.max(0f, Math.min(1f, playerVolume / 100f)));
        Utils.applyBoost();

        if (mPrefs.skipSilence) {
            player.setSkipSilenceEnabled(true);
        }

        youTubeOverlay.player(player);
        playerView.setPlayer(player);

        if (mediaSession != null) {
            mediaSession.release();
        }

        if (player.canAdvertiseSession()) {
            try {
                mediaSession = new MediaSession.Builder(this, player).build();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        playerView.setControllerShowTimeoutMs(-1);

        locked = false;
        hideSwipeToUnlock();

        if (haveMedia) {
            if (isNetworkUri) {
                timeBar.setBufferedColor(DefaultTimeBar.DEFAULT_BUFFERED_COLOR);
            } else {
                // https://github.com/google/ExoPlayer/issues/5765
                timeBar.setBufferedColor(0x33FFFFFF);
            }

            currentVideoScaleMode().apply(playerView);

            if (mPrefs.aspectRatio == 0f
                    && mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            } else {
                playerView.setScale(1.f);
            }
            updatebuttonAspectRatioIcon();

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(mPrefs.mediaUri);
            String streamMimeType = getStreamMimeType(mPrefs.mediaUri, mPrefs.mediaType);
            if (streamMimeType != null) mediaItemBuilder.setMimeType(streamMimeType);
            String title;
            if (apiTitle != null) {
                title = apiTitle;
            } else {
                title = Utils.getFileName(PlayerActivity.this, mPrefs.mediaUri);
            }
            if (title != null) {
                final MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                        .setTitle(title)
                        .setDisplayTitle(title)
                        .build();
                mediaItemBuilder.setMediaMetadata(mediaMetadata);
            }
            List<MediaItem.SubtitleConfiguration> startingSubtitles = new ArrayList<>();
            if (apiAccess) startingSubtitles.addAll(apiSubs);
            MediaItem.SubtitleConfiguration remembered = rememberedSubtitle();
            if (remembered != null && !containsSubtitle(startingSubtitles, remembered.uri)) {
                startingSubtitles.add(remembered);
            }
            if (!startingSubtitles.isEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(startingSubtitles);
            }
            player.setMediaItem(mediaItemBuilder.build(), mPrefs.getPosition());

            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(player.getAudioSessionId());
            } catch (Exception e) {
                e.printStackTrace();
            }

            notifyAudioSessionUpdate(true);

            videoLoading = true;

            updateLoading(true);

            if (mPrefs.getPosition() == 0L || apiAccess || apiAccessPartial) {
                play = true;
            }

            if (apiTitle != null) {
                titleView.setText(apiTitle);
            } else {
                titleView.setText(Utils.getFileName(this, mPrefs.mediaUri));
            }
            titleView.setVisibility(lampaPlaylist == null ? View.VISIBLE : View.GONE);
            updateLampaTopPanel();

            updateButtons(true);

            ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(true);

            if (!apiAccess) {
                if (nextUriThread != null) {
                    nextUriThread.interrupt();
                }
                nextUri = null;
                nextUriThread = new Thread(() -> {
                    Uri uri = findNext();
                    if (!Thread.currentThread().isInterrupted()) {
                        nextUri = uri;
                    }
                });
                nextUriThread.start();
            }

            player.setHandleAudioBecomingNoisy(!isTvBox);
//            mediaSession.setActive(true);
        } else {
            playerView.showController();
        }

        player.addListener(playerListener);
        player.prepare();

        if (restorePlayState) {
            restorePlayState = false;
            playerView.showController();
            playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
            player.setPlayWhenReady(true);
        }
    }

    private void savePlayer() {
        mPrefs.updatePlayerVolume(Math.round(playerVolume));
        if (player != null) {
            mPrefs.updateBrightness(mBrightnessControl.currentBrightnessLevel);
            mPrefs.updateOrientation();

            if (haveMedia) {
                // Prevent overwriting temporarily inaccessible media position
                if (player.isCurrentMediaItemSeekable()) {
                    long position = player.getPlaybackState() == Player.STATE_ENDED
                            && playlistPlaybackEverReady ? 0 : player.getCurrentPosition();
                    mPrefs.updatePosition(position);
                    if (lampaPlaylist != null && lampaPlaylist.getCurrent() != null) {
                        lampaPlaylist.getCurrent().positionMs = Math.max(0, position);
                    }
                }
                mPrefs.updateMeta(getSelectedTrack(C.TRACK_TYPE_AUDIO),
                        getSelectedTrack(C.TRACK_TYPE_TEXT),
                        playerView.getResizeMode(),
                        playerView.getVideoSurfaceView().getScaleX(),
                        together != null && together.isActive()
                                ? together.userSpeed()
                                : player.getPlaybackParameters().speed);
            }
        }
    }

    public void releasePlayer() {
        releasePlayer(true);
    }

    public void releasePlayer(boolean save) {
        cancelFrameRateSwitchWait();
        cancelSubtitleSearch();
        play = false;
        cancelPlaybackWatchdogs();
        hideSwipeToUnlock();
        if (playerView != null) {
            playerView.removeCallbacks(audioRestartRunnable);
        }
        audioRestartInFlight = false;
        audioRestartRetries = 0;
        if (save) {
            savePlayer();
        }

        if (player != null) {
            notifyAudioSessionUpdate(false);

//            mediaSession.setActive(false);
            if (mediaSession != null) {
                mediaSession.release();
            }

            if (player.isPlaying() && restorePlayStateAllowed) {
                restorePlayState = true;
            }
            player.removeListener(playerListener);
            player.clearMediaItems();
            player.release();
            player = null;
            audioSink = null;
            boostProcessor = null;
        }
        titleView.setVisibility(View.GONE);
        updateButtons(false);
    }

    public static boolean canBoostCurrentOutput() {
        return maxVolumeBoost > 0 && boostProcessor != null
                && (audioSink == null || !audioSink.isPassthrough());
    }

    private void restartPassthroughAudio() {
        if (!alive || player == null || audioSink == null || !audioSink.isPassthrough()
                || mPrefs.tunneling
                || !player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)) {
            return;
        }
        if (audioRestartInFlight || !player.isPlaying()) {
            if (audioRestartRetries < 5) {
                audioRestartRetries++;
                playerView.postDelayed(audioRestartRunnable, 100);
            }
            return;
        }
        if (!audioRecoveryState.consumeRebuildRequest()) return;
        audioRestartInFlight = true;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
    }

    private void requestPassthroughAudioRestart() {
        audioRestartRetries = 0;
        playerView.removeCallbacks(audioRestartRunnable);
        playerView.post(audioRestartRunnable);
    }

    private boolean recoverFromAudioTrackFailure(PlaybackException error) {
        if (error == null || player == null || audioSink == null) return false;
        if (error.errorCode != PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                && error.errorCode != PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED) {
            return false;
        }
        String mime = null;
        for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof AudioSink.InitializationException) {
                Format format = ((AudioSink.InitializationException) cause).format;
                mime = format == null ? null : format.sampleMimeType;
                break;
            }
        }
        if (mime == null && player.getAudioFormat() != null) {
            mime = player.getAudioFormat().sampleMimeType;
        }
        if (mime == null || audioRecoveryState.blockedPassthroughMimeTypes().contains(mime)) {
            return false;
        }
        audioRecoveryState.onWriteFailure(mime);
        audioSink.block(mime);
        restorePlayState = player.getPlayWhenReady();
        savePlayer();
        playerView.post(() -> {
            releasePlayer(false);
            initializePlayer();
        });
        return true;
    }

    private class PlayerListener implements Player.Listener {
        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                Utils.applyBoost();
            } catch (Exception e) {
                e.printStackTrace();
            }
            notifyAudioSessionUpdate(true);
        }

        @Override
        public void onRenderedFirstFrame() {
            frameRendered = true;
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                            Player.PositionInfo newPosition, int reason) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK
                    && oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                audioRecoveryState.onSeek();
            }
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (!playWhenReady) {
                playerView.removeCallbacks(audioRestartRunnable);
                audioRecoveryState.onPause();
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            playerView.setKeepScreenOn(isPlaying);

            if (Utils.isPiPSupported(PlayerActivity.this)) {
                if (isPlaying) {
                    updatePictureInPictureActions(R.drawable.ic_pause_24dp, R.string.exo_controls_pause_description, CONTROL_TYPE_PAUSE, REQUEST_PAUSE);
                } else {
                    updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, R.string.exo_controls_play_description, CONTROL_TYPE_PLAY, REQUEST_PLAY);
                }
            }

            if (!isScrubbing) {
                if (isPlaying) {
                    if (shortControllerTimeout) {
                        playerView.setControllerShowTimeoutMs(CONTROLLER_TIMEOUT / 3);
                        shortControllerTimeout = false;
                        restoreControllerTimeout = true;
                    } else {
                        playerView.setControllerShowTimeoutMs(CONTROLLER_TIMEOUT);
                    }
                } else {
                    playerView.setControllerShowTimeoutMs(-1);
                }
            }

            if (!isPlaying) {
                PlayerActivity.locked = false;
                hideSwipeToUnlock();
                playerView.removeCallbacks(stallWatchdogRunnable);
            } else {
                audioRecoveryState.onResume();
                if (audioRecoveryState.shouldRebuildSink()) {
                    requestPassthroughAudioRestart();
                }
                lastObservedPosition = player.getCurrentPosition();
                lastPositionAdvanceAt = SystemClock.elapsedRealtime();
                playerView.removeCallbacks(stallWatchdogRunnable);
                playerView.postDelayed(stallWatchdogRunnable, STALL_CHECK_INTERVAL_MS);
            }
        }

        @SuppressLint("SourceLockedOrientationActivity")
        @Override
        public void onPlaybackStateChanged(int state) {
            boolean isNearEnd = false;
            final long duration = player.getDuration();
            if (duration != C.TIME_UNSET) {
                final long position = player.getCurrentPosition();
                if (position + 4000 >= duration) {
                    isNearEnd = true;
                }
            }
            setEndControlsVisible(haveMedia && (state == Player.STATE_ENDED || isNearEnd));

            if (state == Player.STATE_READY) {
                resetResolverResponseState();
                frameRendered = true;
                cancelLoadWatchdog();
                playbackWaitStartedAt = 0L;
                playbackEverReady = true;
                stablePlaybackStartedAt = SystemClock.elapsedRealtime();
                stablePlaybackStartPosition = player.getCurrentPosition();
                lastObservedPosition = stablePlaybackStartPosition;
                lastPositionAdvanceAt = stablePlaybackStartedAt;
                playerView.removeCallbacks(stablePlaybackRunnable);
                playerView.postDelayed(stablePlaybackRunnable, STABLE_PLAYBACK_MS);
                if (lampaPlaylist != null) playlistPlaybackEverReady = true;
                updateLampaTopPanel();
                updateLampaSegmentMarkers();

                if (mPrefs.skipEnabled && lampaPlaylist != null
                        && duration != C.TIME_UNSET && duration > 0) {
                    LampaPlaylist.Item segmentItem = lampaPlaylist.getCurrent();
                    if (mPrefs.skipFetchOnline || (segmentItem != null
                            && !segmentItem.segments.isEmpty())) {
                        lampaPlaylist.fetchRemoteSegments(segmentItem, duration, loaded -> {
                            if (lampaPlaylist != null && loaded == lampaPlaylist.getCurrent()) {
                                updateLampaSegmentMarkers();
                                updateLampaSkipUi();
                            }
                        });
                    }
                }

                if (lampaPlaylist != null) {
                    lampaPlaylist.preResolveNext();
                }

                if (videoLoading) {
                    videoLoading = false;

                    if (mPrefs.orientation == Utils.Orientation.UNSPECIFIED) {
                        mPrefs.orientation = Utils.getNextOrientation(mPrefs.orientation);
                        Utils.setOrientation(PlayerActivity.this, mPrefs.orientation);
                    }

                    final Format format = player.getVideoFormat();

                    if (format != null) {
                        if (!isTvBox && mPrefs.orientation == Utils.Orientation.VIDEO) {
                            if (Utils.isPortrait(format)) {
                                PlayerActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                            } else {
                                PlayerActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                            }
                            updateButtonRotation();
                        }

                        updateSubtitleViewMargin(format);
                    }

                    if (duration != C.TIME_UNSET && duration > TimeUnit.MINUTES.toMillis(20)) {
                        timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
                    } else {
                        timeBar.setKeyCountIncrement(20);
                    }

                    boolean switched = false;
                    if (mPrefs.frameRateMatching) {
                        if (play) {
                            if (displayManager == null) {
                                displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                            }
                            if (displayListener == null) {
                                displayListener = new DisplayManager.DisplayListener() {
                                    @Override
                                    public void onDisplayAdded(int displayId) {

                                    }

                                    @Override
                                    public void onDisplayRemoved(int displayId) {

                                    }

                                    @Override
                                    public void onDisplayChanged(int displayId) {
                                        frameRateSettled();
                                    }
                                };
                            }
                            displayManager.registerDisplayListener(displayListener, null);
                        }
                        float rate = videoFrameRate();
                        switched = rate > 0f
                                ? Utils.handleFrameRate(PlayerActivity.this, rate)
                                : Utils.switchFrameRate(PlayerActivity.this, mPrefs.mediaUri);
                    }
                    if (switched) {
                        playerView.removeCallbacks(frameRateGiveUpRunnable);
                        playerView.postDelayed(frameRateGiveUpRunnable,
                                FRAME_RATE_SWITCH_TIMEOUT_MS);
                    } else if (!mPrefs.frameRateMatching) {
                        frameRateSettled();
                    }

                    updateLoading(false);

                    if (mPrefs.speed <= 0.99f || mPrefs.speed >= 1.01f) {
                        player.setPlaybackSpeed(mPrefs.speed);
                    }
                    if (!apiAccess) {
                        setSelectedTracks(mPrefs.subtitleTrackId, mPrefs.audioTrackId);
                    }
                }
            } else if (state == Player.STATE_BUFFERING) {
                if (playbackWaitStartedAt == 0L) playbackWaitStartedAt = SystemClock.elapsedRealtime();
                armLoadWatchdog();
            } else if (state == Player.STATE_ENDED) {
                cancelPlaybackWatchdogs();
                locked = false;
                hideSwipeToUnlock();
                if (sleepTimer.isAtMediaEnd()) {
                    fireSleepTimer();
                    return;
                }
                if (lampaPlaylist != null && playlistPlaybackEverReady
                        && lampaPlaylist.hasNext() && lampaPlaylist.isAutoNext()) {
                    playPlaylistIndex(lampaPlaylist.getCurrentIndex() + 1, true);
                    return;
                }
                playbackFinished = true;
                recordCurrentPlaylistItem(true);
                if (apiAccess) {
                    finish();
                }
            }
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (audioRestartInFlight) {
                audioRestartInFlight = false;
                if (player != null) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build());
                }
                return;
            }
            resolveTrackNames();
            updateLampaTrackDetails();
            maybeSearchSubtitlesOnline(tracks);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            cancelPlaybackWatchdogs();
            updateLoading(false);
            if (recoverFromAudioTrackFailure(error)) return;
            if (error instanceof ExoPlaybackException) {
                final ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
                if (exoPlaybackException.type == ExoPlaybackException.TYPE_SOURCE) {
                    if (recoverResolverControlResponse()) return;
                    String detectedManifest = consumeDetectedManifestType();
                    if (detectedManifest != null && !detectedManifest.equals(forcedStreamMimeType)) {
                        alternateStreamTypeTried = true;
                        forcedStreamMimeType = detectedManifest;
                        restorePlayState = true;
                        initializePlayer();
                        return;
                    }
                    if (lampaPlaylist != null && !alternateStreamTypeTried
                            && mPrefs.mediaUri != null
                            && Utils.isSupportedNetworkUri(mPrefs.mediaUri)
                            && getStreamMimeType(mPrefs.mediaUri, mPrefs.mediaType) == null) {
                        alternateStreamTypeTried = true;
                        forcedStreamMimeType = MimeTypes.APPLICATION_M3U8;
                        restorePlayState = true;
                        initializePlayer();
                        return;
                    }
                    if (lampaIptv && !alternateStreamTypeTried && mPrefs.mediaUri != null) {
                        alternateStreamTypeTried = true;
                        String currentMime = getStreamMimeType(mPrefs.mediaUri, mPrefs.mediaType);
                        forcedStreamMimeType = "application/x-mpegURL".equals(currentMime)
                                ? "" : "application/x-mpegURL";
                        restorePlayState = true;
                        Utils.showText(playerView, getString(R.string.live_stream_fallback));
                        initializePlayer();
                        return;
                    }
                    PlaybackRecoveryPolicy.FailureKind kind = Utils.isSupportedNetworkUri(mPrefs.mediaUri)
                            ? PlaybackRecoveryPolicy.FailureKind.NETWORK_READ
                            : PlaybackRecoveryPolicy.FailureKind.TRUNCATED_LOCAL_FILE;
                    if (recoverPlayback(kind)) return;
                    stopPlaybackAfterRecoveryFailure(kind, error.getLocalizedMessage());
                    return;
                }
                if (error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) {
                    if (recoverFromStuckPlayback()) return;
                    PlaybackRecoveryPolicy.FailureKind kind = player != null
                            && player.isCurrentMediaItemLive()
                            ? PlaybackRecoveryPolicy.FailureKind.LIVE_STALL
                            : (playbackEverReady
                            ? PlaybackRecoveryPolicy.FailureKind.STALL_MIDSTREAM
                            : PlaybackRecoveryPolicy.FailureKind.STALL_AT_START);
                    if (recoverPlayback(kind)) return;
                    stopPlaybackAfterRecoveryFailure(kind, error.getLocalizedMessage());
                    return;
                }
                if (exoPlaybackException.type == ExoPlaybackException.TYPE_RENDERER) {
                    if (recoverPlayback(PlaybackRecoveryPolicy.FailureKind.DECODER)) return;
                }
                if (controllerVisible && controllerVisibleFully) {
                    showError(exoPlaybackException);
                } else {
                    errorToShow = exoPlaybackException;
                }
            }
        }
    }

    private boolean recoverFromStuckPlayback() {
        if (player == null) return false;
        MediaItem item = player.getCurrentMediaItem();
        Format format = player.getVideoFormat();
        if (item == null || item.localConfiguration == null || format == null
                || !MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) return false;
        String uri = item.localConfiguration.uri.toString();
        if (uri.equals(stuckRecoveryAttemptedUri)) return false;
        stuckRecoveryAttemptedUri = uri;
        forceHevcForDolbyVision = true;
        pendingStuckRecovery = true;
        restorePlayState = true;
        savePlayer();
        playerView.post(() -> {
            releasePlayer(false);
            initializePlayer();
        });
        return true;
    }

    private boolean recoverResolverControlResponse() {
        if (resolverControlUri == null || player == null) return false;
        MediaItem mediaItem = player.getCurrentMediaItem();
        String currentUri = mediaItem == null || mediaItem.localConfiguration == null
                ? null : mediaItem.localConfiguration.uri.toString();
        if (!resolverControlUri.equals(currentUri)) return false;

        resolverControlUri = null;
        if (!recoverPlayback(PlaybackRecoveryPolicy.FailureKind.RESOLVER_NOT_READY)) {
            showSnack(getString(R.string.resolver_not_ready), null);
            releasePlayer(false);
        }
        return true;
    }

    private String consumeDetectedManifestType() {
        if (detectedManifestUri == null || detectedManifestType == null || player == null) return null;
        MediaItem mediaItem = player.getCurrentMediaItem();
        String currentUri = mediaItem == null || mediaItem.localConfiguration == null
                ? null : mediaItem.localConfiguration.uri.toString();
        if (!detectedManifestUri.equals(currentUri)) return null;
        String result = detectedManifestType;
        detectedManifestUri = null;
        detectedManifestType = null;
        return result;
    }

    private void resetResolverResponseState() {
        resolverControlUri = null;
        detectedManifestUri = null;
        detectedManifestType = null;
    }

    private boolean recoverDecoderCompatibilityMode() {
        if (player == null || decoderCompatibilityTried) return false;
        MediaItem mediaItem = player.getCurrentMediaItem();
        if (mediaItem == null || mediaItem.localConfiguration == null) return false;

        decoderCompatibilityTried = true;
        compatibilityRecoveryAttempts++;
        decoderCompatibilityMode = true;
        decoderCompatibilityUri = mediaItem.localConfiguration.uri.toString();
        Format format = player.getVideoFormat();
        if (format != null && MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) {
            forceHevcForDolbyVision = true;
            pendingStuckRecovery = true;
        }
        if (lampaPlaylist != null && lampaPlaylist.getCurrent() != null) {
            lampaPlaylist.getCurrent().positionMs = Math.max(0, player.getCurrentPosition());
        }
        restorePlayState = player.getPlayWhenReady();
        savePlayer();
        Utils.showText(playerView, getString(R.string.decoder_compatibility_retry), 3500);
        playerView.post(() -> {
            releasePlayer(false);
            initializePlayer();
        });
        return true;
    }

    private void resetDecoderCompatibilityMode() {
        decoderCompatibilityMode = false;
        decoderCompatibilityTried = false;
        decoderCompatibilityUri = null;
    }

    private void ensurePlaybackRecoveryKey(String mediaUri) {
        String nextKey = mediaUri;
        if (lampaPlaylist != null && lampaPlaylist.getCurrent() != null) {
            nextKey = lampaPlaylist.getCurrent().resumeKey();
        }
        if (Objects.equals(playbackRecoveryKey, nextKey)) return;
        cancelPlaybackWatchdogs();
        playbackRecoveryKey = nextKey;
        sourceRecoveryAttempts = 0;
        compatibilityRecoveryAttempts = 0;
        liveRecoveryAttempts = 0;
        lastLiveRecoveryAt = 0L;
        playbackWaitStartedAt = 0L;
        lastPositionAdvanceAt = 0L;
        lastObservedPosition = C.TIME_UNSET;
        stablePlaybackStartedAt = 0L;
        stablePlaybackStartPosition = C.TIME_UNSET;
        playbackEverReady = false;
    }

    private void cancelPlaybackWatchdogs() {
        if (playerView == null) return;
        cancelLoadWatchdog();
        playerView.removeCallbacks(stallWatchdogRunnable);
        playerView.removeCallbacks(stablePlaybackRunnable);
    }

    private void armLoadWatchdog() {
        cancelLoadWatchdog();
        loadWatchdogBytes = TrackNameParsingDataSource.bytesRead.get();
        playerView.postDelayed(loadTimeoutRunnable, VIDEO_LOAD_TIMEOUT_MS);
    }

    private void cancelLoadWatchdog() {
        if (playerView != null) playerView.removeCallbacks(loadTimeoutRunnable);
    }

    private void reportLoadWatchdog() {
        LoadWatchdogPolicy.SourceKind sourceKind = currentLoadSourceKind();
        LoadWatchdogPolicy.Action action = LoadWatchdogPolicy.evaluate(
                player != null && player.getPlaybackState() == Player.STATE_BUFFERING && haveMedia,
                playbackEverReady, loadWatchdogBytes,
                TrackNameParsingDataSource.bytesRead.get(), sourceKind);
        if (action == LoadWatchdogPolicy.Action.IGNORE) return;
        if (action == LoadWatchdogPolicy.Action.REARM) {
            armLoadWatchdog();
            return;
        }

        cancelLoadWatchdog();
        player.stop();
        updateLoading(false);
        int message = sourceKind == LoadWatchdogPolicy.SourceKind.LOCAL
                ? R.string.error_local_media_corrupt : R.string.error_playback_stalled;
        showSnack(getString(message), null);
        if (lampaPlaylist != null) {
            updateEpisodeControls();
            updateLampaTopPanel();
            playerView.showController();
        }
    }

    private LoadWatchdogPolicy.SourceKind currentLoadSourceKind() {
        if (player != null && player.isCurrentMediaItemLive()) {
            return LoadWatchdogPolicy.SourceKind.LIVE;
        }
        return Utils.isSupportedNetworkUri(mPrefs.mediaUri)
                ? LoadWatchdogPolicy.SourceKind.NETWORK : LoadWatchdogPolicy.SourceKind.LOCAL;
    }

    private boolean recoverPlayback(PlaybackRecoveryPolicy.FailureKind kind) {
        if (player == null || kind == null) return false;
        if (kind == PlaybackRecoveryPolicy.FailureKind.LIVE_STALL) {
            return rejoinLiveWindow();
        }

        boolean lowerAvailable = hasLowerQualityCandidate();
        PlaybackRecoveryPolicy.Action action = PlaybackRecoveryPolicy.decide(
                kind, playbackEverReady, sourceRecoveryAttempts,
                compatibilityRecoveryAttempts, lowerAvailable);
        switch (action) {
            case RETRY_SOURCE:
                sourceRecoveryAttempts++;
                savePlayer();
                restorePlayState = player.getPlayWhenReady();
                updateLoading(true);
                Utils.showText(playerView, getString(
                        kind == PlaybackRecoveryPolicy.FailureKind.RESOLVER_NOT_READY
                                ? R.string.resolver_preparing_retry
                                : R.string.playback_recovery_retry), 2500);
                String retryKey = playbackRecoveryKey;
                long delay = Math.min(3_000L, 600L * Math.max(1,
                        sourceRecoveryAttempts));
                playerView.postDelayed(() -> {
                    if (isFinishing() || isDestroyed() || switchingPlaylistItem
                            || !Objects.equals(retryKey, playbackRecoveryKey)) return;
                    releasePlayer(false);
                    initializePlayer();
                }, delay);
                return true;
            case RETRY_COMPATIBILITY:
                if (recoverDecoderCompatibilityMode()) return true;
                if (lowerAvailable && tryLowerQualityRecovery(currentVideoHeight())) {
                    Utils.showText(playerView, getString(R.string.decoder_quality_fallback), 3500);
                    return true;
                }
                return false;
            case LOWER_QUALITY:
                if (tryLowerQualityRecovery(currentVideoHeight())) {
                    Utils.showText(playerView, getString(R.string.decoder_quality_fallback), 3500);
                    return true;
                }
                return false;
            case FAIL:
            default:
                return false;
        }
    }

    private boolean rejoinLiveWindow() {
        if (player == null || !player.isCurrentMediaItemLive()) return false;
        long now = SystemClock.elapsedRealtime();
        liveRecoveryAttempts = LiveRecoveryPolicy.effectiveAttempts(
                liveRecoveryAttempts, now, lastLiveRecoveryAt);
        if (!LiveRecoveryPolicy.canRejoin(liveRecoveryAttempts, now, lastLiveRecoveryAt)) {
            return false;
        }

        boolean resume = player.getPlayWhenReady();
        liveRecoveryAttempts++;
        lastLiveRecoveryAt = now;
        updateLoading(true);
        Utils.showText(playerView, getString(R.string.playback_recovery_retry), 2500);
        player.seekToDefaultPosition();
        player.prepare();
        player.setPlayWhenReady(resume);
        armLoadWatchdog();
        return true;
    }

    private int currentVideoHeight() {
        Format format = player == null ? null : player.getVideoFormat();
        return format == null || format.height <= 0 ? Integer.MAX_VALUE : format.height;
    }

    private boolean hasLowerQualityCandidate() {
        if (player == null || decoderQualityFallbackTried) return false;
        LampaPlaylist.Item item = lampaPlaylist == null ? null : lampaPlaylist.getCurrent();
        if (item != null && lampaPlaylist.hasLowerQuality(item)) return true;
        int currentHeight = currentVideoHeight();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int index = 0; index < group.length; index++) {
                Format format = group.getTrackFormat(index);
                if (group.isTrackSupported(index) && format.height > 0 && format.height < currentHeight) {
                    return true;
                }
            }
        }
        return false;
    }

    private void stopPlaybackAfterRecoveryFailure(PlaybackRecoveryPolicy.FailureKind kind,
                                                  String detail) {
        cancelPlaybackWatchdogs();
        int message = kind == PlaybackRecoveryPolicy.FailureKind.TRUNCATED_LOCAL_FILE
                ? R.string.error_local_media_corrupt : R.string.error_playback_stalled;
        showSnack(getString(message), detail);
        releasePlayer(false);
    }

    private String getStreamMimeType(Uri uri, String suppliedType) {
        if (forcedStreamMimeType != null) {
            return forcedStreamMimeType.isEmpty() ? null : forcedStreamMimeType;
        }
        if (uri != null) {
            String value = Uri.decode(uri.toString()).toLowerCase(Locale.ROOT);
            if (value.contains(".m3u8")) return "application/x-mpegURL";
            if (value.contains(".mpd") || value.contains("/ytdl/manifest?")) {
                return "application/dash+xml";
            }
            if (value.contains(".ism/manifest") || value.endsWith(".ism")) return "application/vnd.ms-sstr+xml";
        }
        return suppliedType != null && suppliedType.endsWith("/*") ? null : suppliedType;
    }

    private boolean isCurrent4kCandidate() {
        if (lampaPlaylist != null) {
            LampaPlaylist.Item item = lampaPlaylist.getCurrent();
            if (item != null) {
                for (String label : item.quality.keySet()) {
                    String candidate = item.quality.get(label);
                    if (candidate == null || !candidate.equals(item.url)) continue;
                    String normalized = label.toLowerCase(Locale.US);
                    String digits = normalized.replaceAll("[^0-9]", "");
                    if (normalized.contains("4k") || normalized.contains("uhd")) return true;
                    try {
                        if (!digits.isEmpty() && Integer.parseInt(digits) >= 2000) return true;
                    } catch (NumberFormatException ignored) { }
                }
                String source = item.url == null ? "" : item.url.toLowerCase(Locale.US);
                if (source.contains("2160") || source.contains("4k") || source.contains("uhd")) return true;
            }
        }
        Uri uri = mPrefs == null ? null : mPrefs.mediaUri;
        String source = uri == null ? "" : uri.toString().toLowerCase(Locale.US);
        return source.contains("2160") || source.contains("4k") || source.contains("uhd");
    }

    private void enableRotation() {
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 0) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
                restoreOrientationLock = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean useMediaStore() {
        final int targetSdkVersion = getApplicationContext().getApplicationInfo().targetSdkVersion;
        return (isTvBox && Build.VERSION.SDK_INT >= 30 && targetSdkVersion >= 30 && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("mediastore");
    }

    private void openFile(Uri pickerInitialUri) {
        if (useMediaStore()) {
            Intent intent = new Intent(this, MediaStoreChooserActivity.class);
            startActivityForResult(intent, REQUEST_CHOOSER_VIDEO_MEDIASTORE);
        } else if ((isTvBox && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("legacy")) {
            Utils.alternativeChooser(this, pickerInitialUri, true);
        } else {
            enableRotation();

            if (pickerInitialUri == null || Utils.isSupportedNetworkUri(pickerInitialUri)) {
                pickerInitialUri = Utils.getMoviesFolderUri();
            }

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, Utils.supportedMimeTypesVideo);

            if (Build.VERSION.SDK_INT < 30) {
                final ComponentName systemComponentName = Utils.getSystemComponent(this, intent);
                if (systemComponentName != null) {
                    intent.setComponent(systemComponentName);
                }
            }

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_VIDEO);
        }
    }

    private void loadSubtitleFile(Uri pickerInitialUri) {
        Toast.makeText(PlayerActivity.this, R.string.open_subtitles, Toast.LENGTH_SHORT).show();
        final int targetSdkVersion = getApplicationContext().getApplicationInfo().targetSdkVersion;
        if ((isTvBox && Build.VERSION.SDK_INT >= 30 && targetSdkVersion >= 30 && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("mediastore")) {
            Intent intent = new Intent(this, MediaStoreChooserActivity.class);
            intent.putExtra(MediaStoreChooserActivity.SUBTITLES, true);
            startActivityForResult(intent, REQUEST_CHOOSER_SUBTITLE_MEDIASTORE);
        } else if ((isTvBox && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("legacy")) {
            Utils.alternativeChooser(this, pickerInitialUri, false);
        } else {
            enableRotation();

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            final String[] supportedMimeTypes = {
                    MimeTypes.APPLICATION_SUBRIP,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.APPLICATION_TTML,
                    "text/*",
                    "application/octet-stream"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes);

            if (Build.VERSION.SDK_INT < 30) {
                final ComponentName systemComponentName = Utils.getSystemComponent(this, intent);
                if (systemComponentName != null) {
                    intent.setComponent(systemComponentName);
                }
            }

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_SUBTITLE);
        }
    }

    private void requestDirectoryAccess() {
        enableRotation();
        final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT_TREE, Utils.getMoviesFolderUri());
        safelyStartActivityForResult(intent, REQUEST_CHOOSER_SCOPE_DIR);
    }

    private Intent createBaseFileIntent(final String action, final Uri initialUri) {
        final Intent intent = new Intent(action);

        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        if (Build.VERSION.SDK_INT >= 26 && initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        return intent;
    }

    void safelyStartActivityForResult(final Intent intent, final int code) {
        if (intent.resolveActivity(getPackageManager()) == null)
            showSnack(getText(R.string.error_files_missing).toString(), intent.toString());
        else
            startActivityForResult(intent, code);
    }

    private TrackGroup getTrackGroupFromFormatId(int trackType, String id) {
        if ((id == null && trackType == C.TRACK_TYPE_AUDIO ) || player == null) {
            return null;
        }
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == trackType) {
                final TrackGroup trackGroup = group.getMediaTrackGroup();
                final Format format = trackGroup.getFormat(0);
                if (Objects.equals(id, format.id)) {
                    return trackGroup;
                }
            }
        }
        return null;
    }

    public void setSelectedTracks(final String subtitleId, final String audioId) {
        if ("#none".equals(subtitleId)) {
            if (trackSelector == null) {
                return;
            }
            trackSelector.setParameters(trackSelector.buildUponParameters().setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED));
        }

        TrackGroup subtitleGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_TEXT, subtitleId);
        TrackGroup audioGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_AUDIO, audioId);

        TrackSelectionParameters.Builder overridesBuilder = new TrackSelectionParameters.Builder(this);
        TrackSelectionOverride trackSelectionOverride = null;
        final List<Integer> tracks = new ArrayList<>(); tracks.add(0);
        if (subtitleGroup != null) {
            trackSelectionOverride = new TrackSelectionOverride(subtitleGroup, tracks);
            overridesBuilder.addOverride(trackSelectionOverride);
        }
        if (audioGroup != null) {
            trackSelectionOverride = new TrackSelectionOverride(audioGroup, tracks);
            overridesBuilder.addOverride(trackSelectionOverride);
        }

        if (player != null) {
            TrackSelectionParameters.Builder trackSelectionParametersBuilder = player.getTrackSelectionParameters().buildUpon();
            if (trackSelectionOverride != null) {
                trackSelectionParametersBuilder.setOverrideForType(trackSelectionOverride);
            }
            player.setTrackSelectionParameters(trackSelectionParametersBuilder.build());
        }
    }

    private boolean hasOverrideType(final int trackType) {
        TrackSelectionParameters trackSelectionParameters = player.getTrackSelectionParameters();
        for (TrackSelectionOverride override : trackSelectionParameters.overrides.values()) {
            if (override.getType() == trackType)
                return true;
        }
        return false;
    }

    public String getSelectedTrack(final int trackType) {
        if (player == null) {
            return null;
        }
        Tracks tracks = player.getCurrentTracks();

        // Disabled (e.g. selected subtitle "None" - different than default)
        if (!tracks.isTypeSelected(trackType)) {
            return "#none";
        }

        // Audio track set to "Auto"
        if (trackType == C.TRACK_TYPE_AUDIO) {
            if (!hasOverrideType(C.TRACK_TYPE_AUDIO)) {
                return null;
            }
        }

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.isSelected() && group.getType() == trackType) {
                Format format = group.getMediaTrackGroup().getFormat(0);
                return format.id;
            }
        }

        return null;
    }

    void setSubtitleTextSize() {
        setSubtitleTextSize(getResources().getConfiguration().orientation);
    }

    void setSubtitleTextSize(final int orientation) {
        // Tweak text size as fraction size doesn't work well in portrait
        final SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView != null) {
            final float size;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                size = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitlesScale;
            } else {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                float ratio = ((float)metrics.heightPixels / (float)metrics.widthPixels);
                if (ratio < 1)
                    ratio = 1 / ratio;
                size = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitlesScale / ratio;
            }

            subtitleView.setFractionalTextSize(size);
        }
    }

    void updateSubtitleViewMargin() {
        if (player == null) {
            return;
        }

        updateSubtitleViewMargin(player.getVideoFormat());
    }

    // Set margins to fix PGS aspect as subtitle view is outside of content frame
    void updateSubtitleViewMargin(Format format) {
        if (format == null) {
            return;
        }

        final Rational aspectVideo = Utils.getRational(format);
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final Rational aspectDisplay = new Rational(metrics.widthPixels, metrics.heightPixels);

        int marginHorizontal = 0;
        int marginVertical = 0;

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (aspectDisplay.floatValue() > aspectVideo.floatValue()) {
                // Left & right bars
                int videoWidth = metrics.heightPixels / aspectVideo.getDenominator() * aspectVideo.getNumerator();
                marginHorizontal = (metrics.widthPixels - videoWidth) / 2;
            }
        }

        Utils.setViewParams(playerView.getSubtitleView(), 0, 0, 0, 0,
                marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    void setSubtitleTextSizePiP() {
        final SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView != null)
            subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 2);
    }

    @TargetApi(26)
    boolean updatePictureInPictureActions(final int iconId, final int resTitle, final int controlType, final int requestCode) {
        try {
            final ArrayList<RemoteAction> actions = new ArrayList<>();
            final PendingIntent intent = PendingIntent.getBroadcast(PlayerActivity.this, requestCode,
                    new Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, controlType), PendingIntent.FLAG_IMMUTABLE);
            final Icon icon = Icon.createWithResource(PlayerActivity.this, iconId);
            final String title = getString(resTitle);
            actions.add(new RemoteAction(icon, title, title, intent));
            ((PictureInPictureParams.Builder) mPictureInPictureParamsBuilder).setActions(actions);
            setPictureInPictureParams(((PictureInPictureParams.Builder) mPictureInPictureParamsBuilder).build());
            return true;
        } catch (IllegalStateException e) {
            // On Samsung devices with Talkback active:
            // Caused by: java.lang.IllegalStateException: setPictureInPictureParams: Device doesn't support picture-in-picture mode.
            e.printStackTrace();
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean isInPip() {
        if (!Utils.isPiPSupported(this))
            return false;
        return isInPictureInPictureMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isInPip()) {
            setSubtitleTextSize(newConfig.orientation);
        }
        updateSubtitleViewMargin();

        updateButtonRotation();
    }

    void showError(ExoPlaybackException error) {
        final String errorGeneral = error.getLocalizedMessage();
        String errorDetailed;

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                errorDetailed = error.getSourceException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_RENDERER:
                errorDetailed = error.getRendererException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                errorDetailed = error.getUnexpectedException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_REMOTE:
            default:
                errorDetailed = errorGeneral;
                break;
        }

        showSnack(errorGeneral, errorDetailed);
    }

    void showSnack(final String textPrimary, final String textSecondary) {
        if (isTvBox) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setMessage(textPrimary)
                    .setPositiveButton(android.R.string.ok, null);
            if (textSecondary != null && !textSecondary.trim().isEmpty()) {
                builder.setNeutralButton(R.string.error_details,
                        (dialog, which) -> showErrorDetails(textSecondary));
            }
            AlertDialog dialog = builder.create();
            dialog.show();
            styleUaAlertDialog(dialog, true);
            return;
        }
        snackbar = Snackbar.make(coordinatorLayout, textPrimary, Snackbar.LENGTH_LONG);
        snackbar.setTextColor(Color.WHITE);
        snackbar.setActionTextColor(Color.rgb(240, 183, 38));
        snackbar.getView().setBackgroundResource(R.drawable.ua_dialog_background);
        if (textSecondary != null) {
            snackbar.setAction(R.string.error_details, v -> showErrorDetails(textSecondary));
        }
        snackbar.setAnchorView(R.id.exo_bottom_bar);
        snackbar.show();
    }

    private void showErrorDetails(String text) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        styleUaAlertDialog(dialog, true);
    }

    void reportScrubbing(long position) {
        final long diff = position - scrubbingStart;
        if (Math.abs(diff) > 1000) {
            scrubbingNoticeable = true;
        }
        if (scrubbingNoticeable) {
            playerView.clearIcon();
            playerView.setCustomErrorMessage(Utils.formatMilisSign(diff));
        }
        if (frameRendered) {
            frameRendered = false;
            if (player != null) {
                player.seekTo(position);
            }
        }
    }

    void updateSubtitleStyle(final Context context) {
        final SubtitleView subtitleView = playerView.getSubtitleView();
        final boolean isTablet = Utils.isTablet(context);
        subtitlesScale = SubtitleUtils.normalizeFontScale(
                mPrefs.subtitleScale, isTvBox || isTablet);
        if (subtitleView != null) {
            final CaptionStyleCompat captionStyle = new CaptionStyleCompat(
                    mPrefs.subtitleTextColor,
                    mPrefs.subtitleBackgroundColor,
                    Color.TRANSPARENT,
                    mPrefs.subtitleEdgeType,
                    mPrefs.subtitleTextColor == Color.BLACK ? Color.WHITE : Color.BLACK,
                    Typeface.create(Typeface.DEFAULT,
                            mPrefs.subtitleStyleBold ? Typeface.BOLD : Typeface.NORMAL));
            subtitleView.setStyle(captionStyle);
            subtitleView.setApplyEmbeddedStyles(mPrefs.subtitleStyleEmbedded);
            subtitleView.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * 2f / 3f);
        }
        setSubtitleTextSize();
    }

    void searchSubtitles() {
        if (mPrefs.mediaUri == null)
            return;

        if (Utils.isSupportedNetworkUri(mPrefs.mediaUri) && Utils.isProgressiveContainerUri(mPrefs.mediaUri)) {
            SubtitleUtils.clearCache(this);
            if (SubtitleFinder.isUriCompatible(mPrefs.mediaUri)) {
                subtitleFinder = new SubtitleFinder(PlayerActivity.this, mPrefs.mediaUri);
                subtitleFinder.start();
            }
            return;
        }

        if (mPrefs.scopeUri != null || isTvBox) {
            DocumentFile video = null;
            File videoRaw = null;
            final String scheme = mPrefs.mediaUri.getScheme();

            if (mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost()) ||
                        "org.courville.nova.provider".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile subtitle = null;
                if (mPrefs.scopeUri != null) {
                    subtitle = SubtitleUtils.findSubtitle(video);
                } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    subtitle = SubtitleUtils.findSubtitle(video, dir);
                }

                if (subtitle != null) {
                    handleSubtitles(subtitle.getUri());
                }
            }
        }
    }

    Uri findNext() {
        // TODO: Unify with searchSubtitles()
        if (mPrefs.scopeUri != null || isTvBox) {
            DocumentFile video = null;
            File videoRaw = null;

            if (!isTvBox && mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (isTvBox) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile next;
                if (!isTvBox) {
                    next = SubtitleUtils.findNext(video);
                } else {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    next = SubtitleUtils.findNext(video, dir);
                }
                if (next != null) {
                    return next.getUri();
                }
            }
        }
        return null;
    }

    void askForScope(boolean loadSubtitlesOnCancel, boolean skipToNextOnCancel) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage(String.format(getString(R.string.request_scope), getString(R.string.app_name)));
        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> requestDirectoryAccess()
        );
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            mPrefs.markScopeAsked();
            if (loadSubtitlesOnCancel) {
                loadSubtitleFile(mPrefs.mediaUri);
            }
            if (skipToNextOnCancel) {
                nextUri = findNext();
                if (nextUri != null) {
                    skipToNext();
                }
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
        styleUaAlertDialog(dialog, true);
    }

    void resetHideCallbacks() {
        if (haveMedia && player != null && player.isPlaying()) {
            // Keep controller UI visible - alternative to resetHideCallbacks()
            playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
        }
    }

    private void updateLoading(final boolean enableLoading) {
        if (enableLoading) {
            boolean playPauseHadFocus = exoPlayPause.hasFocus();
            exoPlayPause.setVisibility(View.INVISIBLE);
            loadingProgressBar.setVisibility(View.VISIBLE);
            if (isTvBox && (playPauseHadFocus || focusPlay)) parkFocusOnLoadingRing();
            updateTransferRateUi();
        } else {
            boolean loadingHadFocus = loadingProgressBar.hasFocus();
            loadingProgressBar.setFocusable(false);
            loadingProgressBar.setVisibility(View.GONE);
            if (loadingRateView != null) loadingRateView.setVisibility(View.GONE);
            exoPlayPause.setVisibility(View.VISIBLE);
            boolean shouldFocusPlay = focusPlay || loadingHadFocus;
            focusPlay = false;
            if (shouldFocusPlay) {
                exoPlayPause.requestFocus();
            } else if (isTvBox && getCurrentFocus() == null) {
                postPrimaryTvFocus();
            }
        }
    }

    private void parkFocusOnLoadingRing() {
        loadingProgressBar.setFocusable(true);
        loadingProgressBar.requestFocus();
    }

    void frameRateSettled() {
        cancelFrameRateSwitchWait();
        playIfCan();
    }

    private void cancelFrameRateSwitchWait() {
        if (playerView != null) playerView.removeCallbacks(frameRateGiveUpRunnable);
        if (displayManager != null && displayListener != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (IllegalArgumentException ignored) { }
        }
        if (frameRateSwitchThread != null) {
            frameRateSwitchThread.interrupt();
            frameRateSwitchThread = null;
        }
    }

    private void playIfCan() {
        if (!play) return;
        play = false;
        if (player != null) player.play();
        if (playerView != null) playerView.hideController();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onUserLeaveHint() {
        if (mPrefs!= null && mPrefs.autoPiP && player != null && player.isPlaying() && Utils.isPiPSupported(this))
            enterPiP();
        else
            super.onUserLeaveHint();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPiP() {
        final AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (AppOpsManager.MODE_ALLOWED != appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName())) {
            final Intent intent = new Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.fromParts("package", getPackageName(), null));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
            return;
        }

        if (player == null) {
            return;
        }

        playerView.setControllerAutoShow(false);
        playerView.hideController();

        final Format format = player.getVideoFormat();

        if (format != null) {
            // https://github.com/google/ExoPlayer/issues/8611
            // TODO: Test/disable on Android 11+
            final View videoSurfaceView = playerView.getVideoSurfaceView();
            if (videoSurfaceView instanceof SurfaceView) {
                ((SurfaceView)videoSurfaceView).getHolder().setFixedSize(format.width, format.height);
            }

            Rational rational = Utils.getRational(format);
            if (Build.VERSION.SDK_INT >= 33 &&
                    getPackageManager().hasSystemFeature(FEATURE_EXPANDED_PICTURE_IN_PICTURE) &&
                    (rational.floatValue() > rationalLimitWide.floatValue() || rational.floatValue() < rationalLimitTall.floatValue())) {
                ((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).setExpandedAspectRatio(rational);
            }
            if (rational.floatValue() > rationalLimitWide.floatValue())
                rational = rationalLimitWide;
            else if (rational.floatValue() < rationalLimitTall.floatValue())
                rational = rationalLimitTall;

            ((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).setAspectRatio(rational);
        }
        enterPictureInPictureMode(((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).build());
    }

    void setEndControlsVisible(boolean visible) {
        final int deleteVisible = (visible && haveMedia && Utils.isDeletable(this, mPrefs.mediaUri)) ? View.VISIBLE : View.INVISIBLE;
        final int nextVisible = (visible && haveMedia && (nextUri != null || (mPrefs.askScope && !isTvBox))) ? View.VISIBLE : View.INVISIBLE;
        findViewById(R.id.delete).setVisibility(deleteVisible);
        findViewById(R.id.next).setVisibility(nextVisible);
    }

    void askDeleteMedia() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage(getString(R.string.delete_query));
        builder.setPositiveButton(R.string.delete_confirmation, (dialogInterface, i) -> {
            releasePlayer();
            deleteMedia();
            if (nextUri == null) {
                haveMedia = false;
                setEndControlsVisible(false);
                playerView.setControllerShowTimeoutMs(-1);
            } else {
                skipToNext();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {});
        final AlertDialog dialog = builder.create();
        dialog.show();
        styleUaAlertDialog(dialog, false);
    }

    private void styleUaAlertDialog(AlertDialog dialog, boolean focusPositive) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.ua_dialog_background);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int width = Math.min((int) (screenWidth * (isTvBox ? 0.62f : 0.76f)),
                    Utils.dpToPx(760));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(Color.WHITE);
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            message.setLineSpacing(0f, 1.12f);
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        styleUaDialogButton(positive);
        styleUaDialogButton(negative);
        styleUaDialogButton(neutral);
        Button preferred = focusPositive ? positive : negative;
        if (preferred != null) preferred.requestFocus();
    }

    private void styleUaDialogButton(Button button) {
        if (button == null) return;
        button.setTextColor(Color.rgb(240, 183, 38));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.ua_dialog_button_background);
        button.setPadding(Utils.dpToPx(16), 0, Utils.dpToPx(16), 0);
    }

    void deleteMedia() {
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(mPrefs.mediaUri.getScheme())) {
                DocumentsContract.deleteDocument(getContentResolver(), mPrefs.mediaUri);
            } else if (ContentResolver.SCHEME_FILE.equals(mPrefs.mediaUri.getScheme())) {
                final File file = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                if (file.canWrite()) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dispatchPlayPause() {
        if (player == null)
            return;

        @Player.State int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player.getPlayWhenReady()) {
            shortControllerTimeout = true;
            androidx.media3.common.util.Util.handlePlayButtonAction(player);
        } else {
            androidx.media3.common.util.Util.handlePauseButtonAction(player);
        }
    }

    void skipToNext() {
        if (nextUri != null) {
            releasePlayer();
            mPrefs.updateMedia(this, nextUri, null);
            searchSubtitles();
            initializePlayer();
        }
    }

    void notifyAudioSessionUpdate(final boolean active) {
        final Intent intent = new Intent(active ? AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                : AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        if (active) {
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE);
        }
        try {
            sendBroadcast(intent);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    void updateButtons(final boolean enable) {
        if (buttonPiP != null) {
            Utils.setButtonEnabled(this, buttonPiP, enable);
        }
        Utils.setButtonEnabled(this, buttonAspectRatio, enable);
        if (buttonLock != null) Utils.setButtonEnabled(this, buttonLock, enable);
        if (isTvBox) {
            Utils.setButtonEnabled(this, exoSettings, true);
        } else {
            Utils.setButtonEnabled(this, exoSettings, enable);
        }
    }

    private void scaleStart() {
        isScaling = true;
        if (playerView.getResizeMode() != AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        }
        scaleFactor = playerView.getVideoSurfaceView().getScaleX();
        playerView.removeCallbacks(playerView.textClearRunnable);
        playerView.clearIcon();
        playerView.setCustomErrorMessage((int)(scaleFactor * 100) + "%");
        playerView.hideController();
        isScaleStarting = true;
    }

    private void scale(boolean up) {
        if (up) {
            scaleFactor += 0.01;
        } else {
            scaleFactor -= 0.01;
        }
        scaleFactor = Utils.normalizeScaleFactor(scaleFactor, playerView.getScaleFit());
        playerView.setScale(scaleFactor);
        playerView.setCustomErrorMessage((int)(scaleFactor * 100) + "%");
    }

    private void scaleEnd() {
        isScaling = false;
        playerView.postDelayed(playerView.textClearRunnable, 200);
        if (player != null && !player.isPlaying()) {
            playerView.showController();
        }
        if (Math.abs(playerView.getScaleFit() - scaleFactor) < 0.01 / 2) {
            playerView.setScale(1.f);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
        updatebuttonAspectRatioIcon();
    }

    private VideoScaleMode currentVideoScaleMode() {
        return VideoScaleMode.from(mPrefs.resizeMode, mPrefs.aspectRatio);
    }

    private void applyVideoScaleMode(VideoScaleMode mode) {
        if (mode == null) return;
        mode.apply(playerView);
        mPrefs.resizeMode = mode.resizeMode;
        mPrefs.scale = 1f;
        mPrefs.updateAspectRatio(mode.ratio);
        Utils.showText(playerView, videoScaleLabel(mode));
        updatebuttonAspectRatioIcon();
    }

    private String videoScaleLabel(VideoScaleMode mode) {
        switch (mode) {
            case FIT: return getString(R.string.video_resize_fit);
            case CROP: return getString(R.string.video_resize_crop);
            case FILL: return getString(R.string.video_resize_fill);
            case RATIO_16_9: return "16:9";
            case RATIO_4_3: return "4:3";
            case RATIO_16_10: return "16:10";
            case RATIO_2_1: return "2:1";
            case RATIO_2_35_1: return "2.35:1";
            case RATIO_2_39_1: return "2.39:1";
            case RATIO_5_4: return "5:4";
            default: return mode.name();
        }
    }

    private void showVideoScaleModePicker() {
        VideoScaleMode[] modes = VideoScaleMode.values();
        String[] labels = new String[modes.length];
        int checked = 0;
        VideoScaleMode current = currentVideoScaleMode();
        for (int i = 0; i < modes.length; i++) {
            labels[i] = videoScaleLabel(modes[i]);
            if (modes[i] == current) checked = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.video_scale_title)
                .setSingleChoiceItems(labels, checked, (selected, which) -> {
                    applyVideoScaleMode(modes[which]);
                    selected.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            styleUaAlertDialog(dialog, false);
            if (dialog.getListView() != null) {
                dialog.getListView().setSelector(lampaBackground(
                        Color.rgb(10, 39, 76), Color.rgb(240, 183, 38), 7));
                dialog.getListView().requestFocus();
            }
        });
        dialog.show();
    }

    void showSwipeToUnlock() {
        if (swipeToUnlock == null || !locked || inPip) return;
        swipeToUnlock.setVisibility(View.VISIBLE);
        rescheduleSwipeHide();
    }

    private void rescheduleSwipeHide() {
        if (playerView == null) return;
        playerView.removeCallbacks(swipeHider);
        playerView.postDelayed(swipeHider, SWIPE_UNLOCK_TIMEOUT_MS);
    }

    void hideSwipeToUnlock() {
        if (playerView != null) playerView.removeCallbacks(swipeHider);
        if (swipeToUnlock != null) swipeToUnlock.setVisibility(View.GONE);
    }

    void onLockChanged() {
        if (locked) {
            playerView.hideController();
            showSwipeToUnlock();
        } else {
            hideSwipeToUnlock();
            playerView.showController();
        }
        updateRoomBadge();
    }

    private void updatebuttonAspectRatioIcon() {
        if (currentVideoScaleMode() == VideoScaleMode.CROP) {
            buttonAspectRatio.setImageResource(R.drawable.ic_fit_screen_24dp);
        } else {
            buttonAspectRatio.setImageResource(R.drawable.ic_aspect_ratio_24dp);
        }
    }

    private void updateButtonRotation() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean auto = false;
        try {
            auto = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (mPrefs.orientation == Utils.Orientation.VIDEO) {
            if (auto) {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_rotation_24dp);
            } else if (portrait) {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_portrait_24dp);
            } else {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_landscape_24dp);
            }
        } else {
            if (auto) {
                buttonRotation.setImageResource(R.drawable.ic_screen_rotation_24dp);
            } else if (portrait) {
                buttonRotation.setImageResource(R.drawable.ic_screen_portrait_24dp);
            } else {
                buttonRotation.setImageResource(R.drawable.ic_screen_landscape_24dp);
            }
        }
    }
}
