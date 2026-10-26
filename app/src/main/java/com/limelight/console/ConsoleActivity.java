package com.limelight.console;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.BatteryState;
import android.hardware.input.InputManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.util.LruCache;
import android.view.Gravity;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.BuildConfig;
import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.utils.UiHelper;
import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.audio.UsbMicrophoneService;
import com.limelight.binding.usb.UsbForwardingController;
import com.limelight.binding.usb.UsbForwardingStatus;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.diagnostics.MoonWakerDiagnostics;
import com.limelight.gateway.GatewayConnection;
import com.limelight.gateway.ChildSessionClient;
import com.limelight.gateway.ControlOperationClient;
import com.limelight.gateway.GatewayTransport;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.binding.input.GyroAim;
import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.stream.BackgroundStreamPreferences;
import com.limelight.stream.BackgroundStreamService;
import com.limelight.session.HostPowerStateStore;
import com.limelight.session.HostStreamRecord;
import com.limelight.session.SessionEvent;
import com.limelight.session.SessionModel;
import com.limelight.session.SessionResumeManager;
import com.limelight.session.SuspendedSessionStore;
import com.limelight.session.WatchNextTile;
import com.limelight.stream.HostSessionTermination;
import com.limelight.stream.StreamingAutopilot;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.QuickLaunchManager;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.ui.ConsoleStreamLoadingView;
import com.limelight.ui.LoadingArtworkPolicy;
import com.limelight.ui.ArtworkImageView;
import com.limelight.ui.ControllerGlyphDrawable;
import com.limelight.ui.ControllerGlyphs;
import com.limelight.console.transition.LaunchTransitionController;
import com.limelight.console.transition.LaunchTransitionSpec;
import com.limelight.console.transition.LaunchTransitionType;
import com.limelight.console.transition.PresentationBarrier;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Deque;
import java.util.Date;
import java.util.UUID;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.lang.ref.WeakReference;
import java.security.cert.X509Certificate;

/** TV-first dashboard adapted from Wake & Play and backed by Moonlight's internal APIs. */
public class ConsoleActivity extends Activity implements InputManager.InputDeviceListener {
    private static final long ACTIVE_GAME_OBSERVATION_TTL_MS = 15_000L;
    private static final int WARM_UP_NONE = 0;
    private static final int WARM_UP_WAKING = 1;
    private static final int WARM_UP_PREPARING = 2;
    private static final int WARM_UP_READY = 3;
    private static final int WARM_UP_ERROR = 4;
    private static final String STATE_WARM_UP_RELAY_DISPATCHED =
            "warmUpRelayDispatched";
    public static final String EXTRA_RETAINED_STREAM_HOME =
            "com.limelight.console.RETAINED_STREAM_HOME";
    public static final String EXTRA_RETAINED_STREAM_SESSION_ID =
            "com.limelight.console.RETAINED_STREAM_SESSION_ID";
    public static final String EXTRA_RETAINED_STREAM_HOST_ID =
            "com.limelight.console.RETAINED_STREAM_HOST_ID";
    public static final String EXTRA_RETAINED_STREAM_PROFILE_ID =
            "com.limelight.console.RETAINED_STREAM_PROFILE_ID";
    public static final String EXTRA_RETAINED_STREAM_APP_ID =
            "com.limelight.console.RETAINED_STREAM_APP_ID";
    public static final String EXTRA_RETAINED_STREAM_PLAYNITE_GAME_ID =
            "com.limelight.console.RETAINED_STREAM_PLAYNITE_GAME_ID";
    public static final String EXTRA_SUPPRESS_AUTO_WARM_UP =
            "com.limelight.console.SUPPRESS_AUTO_WARM_UP";
    /** A Watch Next tile's stream: resumed only after its profile gate (PIN included). */
    static final String EXTRA_WATCH_NEXT_SESSION_ID =
            "com.limelight.console.WATCH_NEXT_SESSION_ID";
    /** The session model's preparing operation of the warm-up a stream Home opened over. */
    public static final String EXTRA_PREPARING_OPERATION =
            "com.limelight.console.PREPARING_OPERATION";
    public static final String EXTRA_WARM_UP_PENDING_GAME_ID =
            "com.limelight.console.WARM_UP_PENDING_GAME_ID";
    public static final String EXTRA_WARM_UP_PENDING_GAME_NAME =
            "com.limelight.console.WARM_UP_PENDING_GAME_NAME";
    public static final String EXTRA_WARM_UP_PENDING_ARTWORK_ID =
            "com.limelight.console.WARM_UP_PENDING_ARTWORK_ID";
    public static final String EXTRA_WARM_UP_PENDING_QUICK_LAUNCH =
            "com.limelight.console.WARM_UP_PENDING_QUICK_LAUNCH";
    public static final String EXTRA_WARM_UP_PENDING_REQUIRES_CONNECTOR =
            "com.limelight.console.WARM_UP_PENDING_REQUIRES_CONNECTOR";
    public static final String EXTRA_WARM_UP_PENDING_START_BEFORE_STREAM =
            "com.limelight.console.WARM_UP_PENDING_START_BEFORE_STREAM";
    // The console UI is a product feature and must be identical in debug and release builds.
    private static final boolean CONSOLE_UI_V2 = true;
    static final String PREFS = "console_dashboard";
    private static final String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";
    /** Preference files whose per-host entries name the host UUID in their key or value. */
    private static final String[] HOST_KEYED_PREFERENCES = {PREFS,
            HIDDEN_APPS_PREF_FILENAME, AppPreferences.APP_PREFERENCES_FILE,
            DiscordOverlayController.PREFS};
    private static final int REQUEST_BLUETOOTH_CONNECT = 2201;
    private static final int REQUEST_ACCESSORY_SETTINGS = 2202;
    private static final int REQUEST_COMMUNITY_DICTATION = 2202;
    private static final long CONTROLLER_REFRESH_MS = 30_000L;
    private static final long DUPLICATE_NAVIGATION_WINDOW_MS = 70L;
    private static final long PLAYNITE_REFRESH_MS = 60_000L;
    private static final long PLAYNITE_INSTALL_REFRESH_MS = 3_000L;
    private static final long PREVIOUS_SESSION_CLOSE_TIMEOUT_MS = 20_000L;
    private static final long LIBRARY_ENTER_TRANSITION_MS = 280L;
    private static final long LIBRARY_EXIT_TRANSITION_MS = 200L;
    private static final long LIBRARY_SHARED_ARTWORK_MS = 260L;
    private static final long HOST_WAKING_TIMEOUT_MS = 45_000L;
    /** Grid rows warmed from the carousel: three visible plus the grid's prefetch rows. */
    private static final int EXPANDED_PREVIEW_ROWS = 3
            + ExpandedLibraryGrid.Rows.PREFETCH_ROWS;
    private static final int PLAYNITE_ARTWORK_PREFETCH_WORKERS = 2;
    private static final long ARTWORK_FOCUS_SETTLE_MS = 420L;
    private static final long CAROUSEL_MARQUEE_SETTLE_MS = 320L;
    private static final long ARTWORK_CROSSFADE_MS = 560L;
    private static final int CAROUSEL_CARD_WIDTH_DP = 66;
    private static final int CAROUSEL_CARD_HEIGHT_DP = 88;
    private static final int CAROUSEL_FOCUSED_CARD_WIDTH_DP = 92;
    private static final int CAROUSEL_FOCUSED_CARD_HEIGHT_DP = 123;
    private static final int CAROUSEL_CARD_GAP_DP = 10;
    private static final int CAROUSEL_PREVIOUS_CARD_COUNT = 2;
    private static final long PLAYNITE_SELECTION_SAVE_DELAY_MS = 350L;
    private static final long LIBRARY_UPDATE_NAVIGATION_IDLE_MS = 180L;
    private static final long WINDOWS_PROFILE_SWITCH_TIMEOUT_MS = 2 * 60_000L;
    private static final String PREF_SCREEN_SAVER_SECONDS = "screen_saver_seconds";
    private static final String PREF_AUTOPILOT_LAST_RESULT = "streaming_autopilot_last_result";
    /** The last update notice shown on Home, so each one appears once. */
    private static final String PREF_ANNOUNCED_UPDATE = "announced_update";
    private static final String PREF_AUTOPILOT_LAST_RESULT_AT =
            "streaming_autopilot_last_result_at";
    private static final String PREF_LAST_CONFIRMED_HOST_STATE = "last_confirmed_host_state.";
    private static final int SCREEN_SAVER_NEVER = 0;
    private static final int SCREEN_SAVER_DEFAULT_SECONDS = 5 * 60;
    private static final long SCREEN_SAVER_SLIDE_MS = 30_000L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = lifecycleExecutor(3);
    private final ExecutorService playniteExecutor = lifecycleExecutor(1);
    private final ExecutorService playniteArtworkExecutor =
            lifecycleExecutor(PLAYNITE_ARTWORK_PREFETCH_WORKERS);
    /**
     * Local disk work only (app-list parses, artwork lookups, bounds checks and decodes): it
     * never queues behind {@link #executor}'s network calls, so a launch's cover waits on disk
     * alone.
     */
    private final ExecutorService diskExecutor = lifecycleExecutor(1);
    /**
     * Avatar images: a picker reads up to the whole catalog (96 images on the owner's host),
     * which on {@link #executor} queued every other Gateway call (a child save, Settings'
     * reads) behind it.
     */
    private final ExecutorService avatarExecutor = lifecycleExecutor(2);
    /**
     * Cards' covers of a page (Disk space lists up to 50): their artwork fetches never queue
     * the page's own Gateway calls (sizes, the drives) behind them on {@link #executor}.
     */
    private final ExecutorService coverExecutor = lifecycleExecutor(1);

    /** Work submitted after onDestroy shut the pool down belongs to a dead Activity: drop it. */
    private static ExecutorService lifecycleExecutor(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
    }
    private final AtomicInteger hostPreparationGeneration = new AtomicInteger();
    private final AtomicInteger artworkGeneration = new AtomicInteger();
    private final AtomicInteger discordStatusGeneration = new AtomicInteger();
    private final AtomicInteger playniteGeneration = new AtomicInteger();
    private final AtomicInteger appListRenderGeneration = new AtomicInteger();
    /**
     * Main thread: app-list results below this generation are dropped (older than one applied,
     * or requested before a profile change). A newer request in flight does not drop a result:
     * host polls request one often, and under the intro every result would wait out the next.
     */
    private int appListRenderFloor;
    private final AtomicInteger playniteArtworkGeneration = new AtomicInteger();
    private final AtomicInteger profileGeneration = new AtomicInteger();
    private final AtomicInteger profileGateGeneration = new AtomicInteger();
    private final AtomicInteger hostProfileRefreshGeneration = new AtomicInteger();
    private final AtomicInteger childManagementGeneration = new AtomicInteger();
    /** The host and profile whose child capability is being read, or "". */
    private String childCapabilityLoading = "";
    private final Map<String, Boolean> childCapabilityByProfile =
            new ConcurrentHashMap<>();
    /** Each host's Host Plus state from its capabilities; a host not read yet counts as on. */
    private final Map<String, Boolean> hostPlusByHost = new ConcurrentHashMap<>();
    /** Whether the host can save screenshots and clips (#51); unknown counts as yes. */
    private final Map<String, Boolean> capturesByHost = new ConcurrentHashMap<>();
    private ConsoleCaptureGallery captureGallery;
    /** The last power status each host's Gateway reported; the Gateway owns the facts. */
    private final Map<String, HostGatewayClient.PowerStatus> hostPowerStatus =
            new ConcurrentHashMap<>();
    /** Hosts whose pending restart the user chose to play through, until a status read finds
     *  no pending restart. */
    private final Set<String> pendingRestartAcknowledged = ConcurrentHashMap.newKeySet();
    /** Hosts whose sign-in after a restart this client sent runs now: one at a time each. */
    private final Set<String> restartSignInInFlight = ConcurrentHashMap.newKeySet();
    private final ChildSessionClient childSessionClient =
            new ChildSessionClient(new GatewayTransport());
    private final Map<String, ChildLaunchBinding> childLaunchBindings =
            new ConcurrentHashMap<>();
    private final Set<String> childEligibilityProfileInFlight =
            ConcurrentHashMap.newKeySet();
    private final Map<String, Long> childEligibilityObservedAt =
            new ConcurrentHashMap<>();
    private final Object childIdentityBindingLock = new Object();
    private IdentityBindingAttempt childIdentityBindingAttempt;
    private int childIdentityBindingLifecycleToken;
    private int childEligibilityLaunchToken;
    private final AtomicInteger childBindingSequence = new AtomicInteger();
    private volatile String childLaunchFailureReason = "";
    private ChildLaunchBinding childStreamHandoff;
    private volatile String childLaunchHostId = "";
    private volatile String childLaunchProfileId = "";
    private volatile boolean childPairingRepairAvailable;
    private final Runnable childEligibilityBoundary = this::runChildEligibilityBoundary;

    private enum IdentityBindingStart {
        STARTED,
        IN_FLIGHT,
        ATTEMPTED,
        CONTEXT_UNAVAILABLE
    }

    private static final class IdentityBindingAttempt {
        final String contextKey;
        final int lifecycleToken;
        boolean inFlight = true;
        boolean completed;

        IdentityBindingAttempt(String contextKey, int lifecycleToken) {
            this.contextKey = contextKey;
            this.lifecycleToken = lifecycleToken;
        }
    }

    private void runChildEligibilityBoundary() {
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null || !active) return;
        refreshChildEligibilityCardPresentation(host);
        refreshChildEligibilityProfile(host);
    }
    private final Map<String, Integer> hostProfileRefreshGenerations =
            new ConcurrentHashMap<>();
    private final Set<String> hostProfileRefreshInFlight = ConcurrentHashMap.newKeySet();
    private volatile WindowsProfileSwitchAttempt windowsProfileSwitchAttempt;
    private final Map<String, ComputerDetails> hosts = new LinkedHashMap<>();
    private final Set<String> newlyDiscoveredHosts = new LinkedHashSet<>();

    private SharedPreferences preferences;
    private FeatureShowcaseLauncher featureShowcase;
    private TesterWelcomeScreen testerWelcome;
    private HostPairingFlow hostPairingFlow;
    /** Built with the entry shell, whose page it is; null on a stream Home until then. */
    private FirstRunWizard firstRunWizard;
    /** The host whose parent panel opens once the wizard has entered it. */
    private String pendingParentPanelHost;
    /** The selected host's last read /capabilities; null until read, so host cards stay hidden. */
    private HostGatewayClient.Capabilities showcaseCapabilities;
    /** The selected host's /updates answer; null until it gave one. */
    private ReleaseUpdates releaseUpdates;
    private AppUpdater appUpdater;
    private SharedPreferences microphoneStatePreferences;
    private final Runnable usbForwardingListener = () -> {
        refreshUsbForwardingIndicator();
        // Only the device list follows the devices; a consent question stays as asked.
        if (usbDeviceListShowing()) {
            usbConsentDevice = null;
            showUsbForwardingPanel();
        }
    };
    /** The device whose sharing waits for the user's consent in the USB panel, if any. */
    private String usbConsentDevice;
    private final SharedPreferences.OnSharedPreferenceChangeListener microphoneStateListener =
            (preferences, key) -> {
                if (UsbMicrophoneService.STATE_KEY.equals(key)) {
                    mainHandler.post(this::refreshUsbMicrophoneIndicator);
                }
            };
    private ConsoleLibraryViewStateStore libraryViewStateStore;
    private ConsoleLibraryTransitionCoordinator libraryTransitionCoordinator;
    private DiskAssetLoader assetLoader;
    private ConsoleAudioEngine consoleAudioEngine;
    private ConsoleUiFeedback consoleFeedback;
    private InputManager inputManager;
    private DiscordPanelController discordPanelController;
    private DiscordSocialPanelController discordSocialPanelController;
    private HostGatewayClient hostGatewayClient;
    private GameOperationsController gameOperationsController;
    private HostLaunchPreflight hostLaunchPreflight;
    private SessionOrchestrator sessionOrchestrator;
    private HostGatewayStore hostGatewayStore;
    private PlayniteLibraryRepository playniteLibraryRepository;
    private PlayniteArtworkCache playniteArtworkCache;
    private ProfileAvatars profileAvatars;
    /** The top bar's avatar, redrawn when its Gateway image arrives. */
    private ProfileToolbarDrawable profileToolbarDrawable;
    /** The child editor's avatar choices: the parent's host catalog, read with the children. */
    private List<HostGatewayClient.Avatar> childAvatarCatalog = Collections.emptyList();
    /** Settings › Children, for {@link ConsoleSettingsScreen#refresh(Category)} on arrival. */
    private ConsoleSettingsScreen.Category childrenCategory;
    /**
     * Settings › Children's list for {@link #childrenListKey} (host and parent): null until read
     * in this Settings opening; {@link #childrenListFailed} when the read failed.
     */
    private HostGatewayClient.ChildProfiles childrenList;
    private String childrenListKey = "";
    private boolean childrenListFailed;
    private boolean childrenListLoading;
    private QuickLaunchManager quickLaunchManager;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private ComputerManagerService.ApplistPoller appListPoller;
    private boolean serviceBound;
    private boolean polling;
    private boolean active;
    /** The orchestrator request of the warm-up this console asked for; its status follows it. */
    private long warmUpRequest;
    /** A stream Home over this client's preparing warm-up: that warm-up's identity. */
    private long homePreparingOperation;
    /** The host of the warm-up this console tracks; empty once it relinquished it. */
    private String warmUpClientHostId = "";
    private int warmUpStatus;
    private PlayIntent pendingWarmUpRelay;
    private boolean warmUpRelayDispatched;
    private boolean warmUpRelaySubmitted;
    private boolean warmUpHomeFrameAccepted;
    private boolean inputListenerRegistered;
    private boolean reducedMotion;
    private boolean uiSoundsEnabled;
    private boolean ambientSoundsEnabled;
    private int hostMusicVolume;
    private int menuMusicVolume;
    private int effectsVolume;
    private int backgroundStreamRetentionMinutes;
    private int screenSaverSeconds;
    private boolean showCarouselGameDescription;
    private boolean refreshHostsOnResume;
    private boolean refreshSessionOnResume;
    private boolean initialHostsLoaded;
    private boolean showHiddenApps;
    private boolean hostSelectionVisible = true;
    private boolean initialHostSelectionResolved;
    /** Restored by a recreation (e.g. a language change): re-entered without host selection. */
    private String restoredEnteredHost = "";
    /** Set by a language choice; the recreated console reopens Settings › Appearance. */
    private boolean reopenAppearance;
    private String pendingHostPreparation;
    private String autoLoginHostUuid;
    private String profileGateHostUuid;
    private final ConsolePinEntry pinEntry = new ConsolePinEntry();
    private String pinProfileId;
    private boolean pinFocusApps;
    private boolean pinPrepareHost;
    private boolean pinCompleteHostEntry;
    private boolean pinSubmitting;
    private long pinCooldownUntil;
    private boolean pinSecureFlagAdded;
    private volatile OfflinePinAttempt offlinePinAttempt;
    private boolean hasLastControllerInput;
    /**
     * A warm-up opens its stream screen, a main-thread stall of about two seconds (Game and
     * the stream Home are built), only once the user stopped moving for this long.
     */
    private static final long WARM_UP_OPEN_QUIET_MS = 1_200L;
    private long lastKeyDownAt;
    private final Runnable openPreparedHostWhenQuiet = () -> {
        if (sessionOrchestrator != null) sessionOrchestrator.openPreparedHost();
    };
    private boolean lastControllerPlayStation;
    private boolean lastInputWasController;

    static final class WindowsProfileSwitchAttempt {
        final String hostId;
        final String profileId;
        final String requestId;
        final GatewayConnection connection;
        volatile String attemptId = "";
        volatile boolean cancelled;

        WindowsProfileSwitchAttempt(String hostId, String profileId, String requestId,
                                    GatewayConnection connection) {
            this.hostId = hostId;
            this.profileId = profileId;
            this.requestId = requestId;
            this.connection = connection;
        }

        boolean matches(String hostId, String profileId, String requestId) {
            return !cancelled && this.hostId.equals(hostId)
                    && this.profileId.equals(profileId) && this.requestId.equals(requestId);
        }
    }

    private static final class ChildManagementLease {
        final String hostId;
        final String parentProfileId;
        final GatewayConnection connection;
        final String authorizationId;
        final long expiresAtElapsedMs;

        ChildManagementLease(String hostId, String parentProfileId,
                             GatewayConnection connection, String authorizationId,
                             int expiresInSeconds) {
            this.hostId = hostId;
            this.parentProfileId = parentProfileId;
            this.connection = connection;
            this.authorizationId = authorizationId;
            this.expiresAtElapsedMs = SystemClock.elapsedRealtime()
                    + Math.max(1, expiresInSeconds) * 1_000L;
        }

        boolean matches(String expectedHostId, String expectedParentProfileId,
                        GatewayConnection expectedConnection) {
            return SystemClock.elapsedRealtime() < expiresAtElapsedMs
                    && hostId.equals(expectedHostId)
                    && parentProfileId.equals(expectedParentProfileId)
                    && connection != null && expectedConnection != null
                    && connection.endpoint().equals(expectedConnection.endpoint())
                    && connection.profileId().equals(expectedConnection.profileId())
                    && connection.token().equals(expectedConnection.token())
                    && connection.certificateSha256().equals(
                    expectedConnection.certificateSha256());
        }
    }

    /** A single child session binding carried from Gateway start to Game launch. */
    private static final class ChildLaunchBinding {
        final String hostId;
        final String actorProfileId;
        /** The raw Playnite identifier used by the Android transition/request. */
        final String requestedGameId;
        final String sessionId;
        final String requestId;
        final int generation;
        final int launchSequence;
        volatile GatewayConnection connection;
        volatile ChildSessionClient.State state;
        /** The canonical identifier echoed by Gateway (usually playnite:<guid>). */
        volatile String gameId;
        volatile String executionProfileId = "";
        volatile long deadlineElapsedMs;
        volatile long policyRevision;
        volatile long controlGeneration;
        volatile boolean started;
        volatile boolean handedToPlayer;
        volatile boolean endRequested;
        volatile boolean ended;

        ChildLaunchBinding(String hostId, String actorProfileId, String gameId,
                           String sessionId, String requestId, int generation,
                           int launchSequence) {
            this.hostId = hostId;
            this.actorProfileId = actorProfileId;
            this.requestedGameId = gameId;
            this.gameId = gameId;
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.generation = generation;
            this.launchSequence = launchSequence;
        }

        String key() {
            return hostId + "\n" + actorProfileId + "\n" + requestedGameId;
        }

        boolean matches(PlayIntent intent) {
            return intent != null && hostId.equals(intent.hostId)
                    && actorProfileId.equals(intent.profileId)
                    && requestedGameId.equals(intent.playniteGameId);
        }
    }

    /** A policy denial carries the host's structured reason through preflight. */
    private static final class ChildLaunchFailure extends IOException {
        final String reason;
        final boolean cancelled;

        ChildLaunchFailure(String reason) {
            this(reason, false);
        }

        ChildLaunchFailure(String reason, boolean cancelled) {
            super(reason == null || reason.trim().isEmpty() ? "child_not_playable" : reason.trim());
            this.reason = getMessage();
            this.cancelled = cancelled;
        }
    }

    private static final class ChildEligibilityProjection {
        static final ChildEligibilityProjection ADULT =
                new ChildEligibilityProjection(false, false, "");

        final boolean child;
        final boolean blocked;
        final String reason;

        ChildEligibilityProjection(boolean child, boolean blocked, String reason) {
            this.child = child;
            this.blocked = blocked;
            this.reason = reason == null ? "" : reason;
        }
    }

    private static final class ChildMutationRetry {
        final String hostId;
        final String parentProfileId;
        final String operation;
        final String childProfileId;
        final String gameId;
        final String signature;
        final String requestId;

        ChildMutationRetry(String hostId, String parentProfileId, String operation,
                           String childProfileId, String gameId, String signature,
                           String requestId) {
            this.hostId = hostId;
            this.parentProfileId = parentProfileId;
            this.operation = operation;
            this.childProfileId = childProfileId;
            this.gameId = gameId;
            this.signature = signature;
            this.requestId = requestId;
        }

        boolean matches(String expectedHostId, String expectedParentProfileId,
                        String expectedOperation, String expectedChildProfileId,
                        String expectedGameId, String expectedSignature) {
            return hostId.equals(expectedHostId)
                    && parentProfileId.equals(expectedParentProfileId)
                    && operation.equals(expectedOperation)
                    && childProfileId.equals(expectedChildProfileId)
                    && gameId.equals(expectedGameId)
                    && signature.equals(expectedSignature);
        }
    }

    private static final class ChildSharingOriginContext {
        int token;
        ChildManagementLease lease;

        ChildSharingOriginContext(int token, ChildManagementLease lease) {
            this.token = token;
            this.lease = lease;
        }
    }

    private static final class OfflinePinAttempt {
        final String hostId;
        final String profileId;
        final int gateToken;
        final GatewayConnection pairing;
        final ComputerDetails host;
        final String initialAddress;
        volatile boolean cancelled;

        OfflinePinAttempt(String hostId, String profileId, int gateToken,
                          GatewayConnection pairing, ComputerDetails host) {
            this.hostId = hostId;
            this.profileId = profileId;
            this.gateToken = gateToken;
            this.pairing = pairing;
            this.host = host;
            this.initialAddress = host == null || host.activeAddress == null
                    ? null : host.activeAddress.address;
        }
    }

    private FrameLayout root;
    private DiscordDmToastView discordDmToastView;
    private DiscordDmNotificationCoordinator discordDmNotifications;
    private DiscordDmNotificationCoordinator.HostToken discordDmHostToken;
    private DiscordDmShortcutHandler discordDmShortcut;
    private FrameLayout screenSaverLayer;
    private ImageView screenSaverArtwork;
    private ImageView screenSaverArtworkNext;
    private TextView screenSaverCaption;
    private boolean screenSaverVisible;
    private int screenSaverGeneration;
    private int screenSaverLoadGeneration;
    private int screenSaverDismissKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private long screenSaverMotionBlockUntil;
    private List<PlayniteDashboardItem> screenSaverItems = Collections.emptyList();
    private String screenSaverHostUuid = "";
    private HostProfileKey screenSaverProfileKey;
    private int screenSaverItemIndex;
    private final Runnable screenSaverTimeout = this::showScreenSaver;
    private final Runnable screenSaverSlide = this::showNextScreenSaverArtwork;
    private FrameLayout homeLayer;
    private ConsoleEntryShell entryShell;
    private ConsoleHostSelectView hostSelectView;
    private final ConsoleHostEntryAttempt hostEntryAttempt = new ConsoleHostEntryAttempt(
            profileGateGeneration, uuid -> {
                if (hostSelectView != null) hostSelectView.setPending(uuid);
            });
    private boolean contentPanelOpen;
    private ConsoleProfileSelectView profileSelectView;
    private ConsolePinEntryView pinEntryView;
    /** The queued Play whose launch cover is up, waiting for a close; null otherwise. */
    private PlayIntent queuedCoverIntent;
    private ConsoleIntroView introView; // Null over a retained stream: no entry intro there.
    private final Set<String> loadingPosterTags = new HashSet<>(); // Prefetch not yet done.
    private String settledBackdropTag = ""; // Last card whose backdrop request ended.
    private final ConsoleHostStateController hostStateController =
            new ConsoleHostStateController(SystemClock::uptimeMillis,
                    host -> this.sessionModel.streamEnds().lastEndedAt(host));
    private LinearLayout homeContent;
    private ConsoleTopBar topBar;
    private FrameLayout loadingLayer;
    private ConsoleStreamLoadingView streamLoadingView;
    private long streamLoadingEpoch;
    private FrameLayout modalLayer;
    private LinearLayout sidePanel;
    private LockableScrollView sidePanelScroll;
    private ConsolePanel consolePanel;
    private GameModeStep gameModeStep;
    private ConsoleMenuBackdrop menuBackdrop;
    private ConsoleSettingsScreen settingsScreen;
    private ConsoleStreamSettings streamSettings;
    private ConsoleSystemAudioSettings systemAudioSettings;
    private ConsoleDiscordSettings discordSettings;
    private FrameLayout communityPanelHost;
    private android.app.Dialog sideDialog;
    private final CommunityDictationSession communityDictationSession = new CommunityDictationSession();
    private final Deque<PanelSnapshot> panelHistory = new ArrayDeque<>();
    private ConsoleUserMenu userMenu;
    private String currentPanelKey;
    private View sidePanelBusyBanner;
    private boolean sidePanelTransient;
    private ChildManagementLease childManagementLease;
    private ChildMutationRetry childMutationRetry;
    private ImageView artworkBackdrop;
    private ImageView artworkBackdropNext;
    private ImageView artworkHero;
    private View artworkScrim;
    private ValueAnimator artworkScrimAnimator;
    private float artworkScrimLuminance = .42f;
    private Runnable pendingArtworkCommit;
    private File pendingArtworkFile;
    private Drawable pendingArtworkPreview;
    private String pendingArtworkKey;
    private String loadingArtworkKey;
    private String displayedArtworkKey;
    private TextView controllersLabel;
    private TextView appsLabel;
    private TextView playniteLibraryStatus;
    private LinearLayout debugLibraryActions;
    private View debugLibrarySpacer;
    private FrameLayout carouselStage;
    private LinearLayout selectedGameTitleRow;
    private LinearLayout selectedGameMetadata;
    private ImageView selectedGameSource;
    private TextView selectedGameTitle;
    private TextView selectedGameFacts;
    private LinearLayout selectedGameFactsRow;
    private TextView selectedGameLastPlayedPill;
    /** Mockup 23c: the parent's rating chip after the playtime pill. */
    private TextView selectedGameRatingPill;
    private TextView selectedGamePlaytimePill;
    /** The selected game's full status: the first pill of the carousel's facts row. */
    private LinearLayout selectedGameStatusPill;
    private ImageView selectedGameStatusIcon;
    private TextView selectedGameStatusText;
    private ConsoleTileProgressBar selectedGameStatusBar;
    /** The selected game's full status: the first, coloured line of the library's facts. */
    private TextView expandedGameStatus;
    private TextView selectedGameDescription;
    private LinearLayout expandedLibrary;
    private LibraryFilterBar libraryFilterBar;
    private LibraryFiltersPanel libraryFiltersPanel;
    private final ChildLibraryFilter childLibraryFilter = new ChildLibraryFilter();
    /** The child filter the grid's cards were last bound with (see renderExpandedLibrary). */
    private ChildLibraryFilter.Selection renderedChildSelection;
    private TextView expandedChildState;
    private LibrarySearchKeyboard librarySearch;
    private ExpandedLibraryGrid expandedGrid;
    private TextView expandedGameTitle;
    private TextView expandedGameFacts;
    private TextView expandedGameDescription;
    private TextView expandedPageIndicator;
    private TextView expandedCacheStatus;
    private LinearLayout expandedNavigationLegend;
    private ScrollView expandedDescriptionScroll;
    private int expandedDescriptionScrollGeneration;
    private boolean expandedLibraryMode;
    private boolean libraryTransitionRunning;
    private long lastDirectionalNavigationAt;
    private TextView quickResumeButton;
    private LinearLayout controllerRow;
    private LinearLayout appRow;
    private HorizontalScrollView controllerScroll;
    private HorizontalScrollView appScroll;
    private ScrollView appVerticalScroll;
    private boolean portraitLayout;
    private String selectedHostUuid;
    private boolean pendingInitialGameFocus;
    private View lastContentFocus;
    private Object lastContentFocusTag;
    private ControllerInfo pendingController;
    private final NavigationInputDeduplicator navigationInputDeduplicator =
            new NavigationInputDeduplicator(DUPLICATE_NAVIGATION_WINDOW_MS);
    private BluetoothAction pendingBluetoothAction;
    private int glassAccent = 0xFF73D7FF;
    private String renderedAppsSignature;
    private String renderedControllersSignature;
    private String renderedControllerDevicesSignature;
    private Future<?> playniteRequest;
    private Future<?> streamingAutopilotTask;
    private final AtomicInteger streamingAutopilotGeneration = new AtomicInteger();
    private String playniteArtworkPrefetchSignature;
    private final List<Future<?>> playniteArtworkPrefetchTasks =
            Collections.synchronizedList(new ArrayList<>());
    private static final LruCache<String, Bitmap> playniteBitmapCache =
            new LruCache<String, Bitmap>(32 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap == null ? 0 : bitmap.getAllocationByteCount();
                }
            };
    private static final ConcurrentHashMap<String, Object> playniteBitmapDecodeLocks =
            new ConcurrentHashMap<>();
    /** Artwork tag to bitmap-cache key, filled off the main thread so binds never stat(). */
    private static final ConcurrentHashMap<String, ResolvedArtwork> resolvedPlayniteArtwork =
            new ConcurrentHashMap<>();
    private static volatile InitialLibraryPresentation initialLibraryPresentation;
    static final String EXTRA_HARD_TERMINATE_HOST_ID =
            "com.limelight.console.HARD_TERMINATE_HOST_ID";
    private static final String STATE_SUPPRESS_AUTO_WARM_UP = "suppressAutoWarmUp";
    /** The host the user had entered (past its profile gate) when this console was recreated. */
    private static final String STATE_ENTERED_HOST = "enteredHost";
    /** A language change recreated this console from Settings › Appearance. */
    private static final String STATE_REOPEN_APPEARANCE = "reopenAppearance";
    /** The only owner of the return Intent's requests; the Intent is never read again. */
    private final ConsoleReturnRequests returnRequests = new ConsoleReturnRequests();

    /**
     * The stream error screen offers the host panel's hard close: the console it returns to
     * opens its confirmation on that host's own screen. Otherwise the action stays in the host
     * panel.
     */
    public static void offerHardTerminate(Activity from, String hostId) {
        Intent console = new Intent(from, ConsoleActivity.class);
        console.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        console.putExtra(EXTRA_SUPPRESS_AUTO_WARM_UP, true);
        console.putExtra(EXTRA_HARD_TERMINATE_HOST_ID, hostId == null ? "" : hostId);
        from.startActivity(console);
    }

    /** The Watch Next tile's launch: a fresh console over the task, never the stream itself. */
    public static Intent watchNextIntent(Context context, String streamSessionId) {
        return new Intent(context, ConsoleActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_WATCH_NEXT_SESSION_ID, streamSessionId);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        returnRequests.delivered(intent.getBooleanExtra(EXTRA_SUPPRESS_AUTO_WARM_UP, false),
                intent.getStringExtra(EXTRA_HARD_TERMINATE_HOST_ID));
    }

    private static final class InitialLibraryPresentation {
        final HostProfileKey profileKey;
        final List<PlayniteLibraryGame> games;
        final List<NvApp> sunshineApps;
        final List<PlayniteDashboardItem> allItems;
        final List<PlayniteDashboardItem> unfilteredItems;
        final List<PlayniteDashboardItem> carouselItems;
        /** The session signature the carousel order was built for; never the session itself. */
        final String sessionSignature;

        InitialLibraryPresentation(HostProfileKey profileKey,
                                   List<PlayniteLibraryGame> games,
                                   List<NvApp> sunshineApps,
                                   List<PlayniteDashboardItem> allItems,
                                   List<PlayniteDashboardItem> unfilteredItems,
                                   List<PlayniteDashboardItem> carouselItems,
                                   String sessionSignature) {
            this.profileKey = profileKey;
            this.games = games;
            this.sunshineApps = sunshineApps;
            this.allItems = allItems;
            this.unfilteredItems = unfilteredItems;
            this.carouselItems = carouselItems;
            this.sessionSignature = sessionSignature;
        }
    }
    private String expandedSearchQuery = "";
    private final PlayniteLibraryQuery.Cache expandedLibraryQuery =
            new PlayniteLibraryQuery.Cache();
    private Runnable pendingPlayniteSelectionSave;
    private String pendingPlayniteSelectionHostUuid;
    private String pendingPlayniteSelectionGameId;
    private boolean pendingPlayniteSelectionExpanded;
    private volatile List<PlayniteLibraryGame> currentPlayniteGames = Collections.emptyList();
    private volatile HostProfileKey currentPlayniteProfileKey;
    private List<PlayniteDashboardItem> renderedPlayniteItems = Collections.emptyList();
    private List<PlayniteDashboardItem> allPlayniteItems = Collections.emptyList();
    private List<PlayniteDashboardItem> unfilteredPlayniteItems = Collections.emptyList();
    private String resumePlayniteGameId = "";
    private String suspendedPlayniteGameId = "";
    private PlayniteSessionPresentation.Projection playniteSessionProjection;
    private String renderedCarouselSessionSignature = "";
    private String renderedExpandedSessionSignature = "";
    private int cardBindBatchDepth;
    private String renderedSessionPresentationKey = "";
    /** {@link SessionSnapshot.Display#key} of the status line the top bar shows. */
    private String renderedSessionDisplayKey = "";
    /** The "Check again" action of the unconfirmed-end panel while it is offered; else null. */
    private View terminationCheckAction;
    private String renderedSunshineAppsKey = "";
    private String gridChildPolicyKey = "";
    private final Map<String, SessionSnapshot> cardBindBatchMemo = new LinkedHashMap<>();
    private final ConsoleSessionSubscriber sessionSubscriber =
            new ConsoleSessionSubscriber(channels -> {
                queueConsoleUpdates(channels);
                // The snapshot that ends a close starts (or drops) the Play queued behind it.
                if (this.sessionOrchestrator != null) this.sessionOrchestrator.sessionChanged();
            });
    private SessionModel sessionModel;
    private final Map<String, Long> sessionProjectionRequestedAt = new LinkedHashMap<>();
    private final Map<String, Integer> sessionProjectionRequestGenerations =
            new LinkedHashMap<>();
    private int sessionProjectionRequestGeneration;
    /** Host/profile entries whose Gateway stream record was read since the last onPause. */
    private final Set<String> hostStreamReconciled = new HashSet<>();
    private final Set<String> sessionProjectionRefreshInFlight =
            Collections.synchronizedSet(new HashSet<>());
    private String lastCarouselGameId = "";
    /** The badge the selected game's status shows (see showSelectedGameStatus). */
    private ConsoleTileBadge shownSelectedGameBadge;
    /** The saved home focus a warm-up's Home inherits; null until its first frame reads it. */
    private String inheritedHomeFocusTag;
    /** What the library status line shows (see updatePlayniteLibraryStatus). */
    private String shownLibraryStatus = "";
    private String libraryTransitionGameId = "";
    private boolean pendingExpandedLibraryRestore;
    private final List<LibraryTransitionGhost> libraryTransitionGhosts = new ArrayList<>();
    private final Set<String> completedPlayniteInstallAnimations = new HashSet<>();
    private volatile List<NvApp> currentSunshineApps = Collections.emptyList();
    /** Each host's parsed app list: loaded on workers, read from the memo on the main thread. */
    private final ConsoleAppLists appLists = new ConsoleAppLists(new ConsoleAppLists.Source() {
        @Override public String cachedRaw(String hostUuid) throws IOException {
            return CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(
                    getCacheDir(), "applist", hostUuid));
        }

        @Override public List<NvApp> parse(String raw)
                throws IOException, XmlPullParserException {
            return NvHTTP.getAppListByReader(new StringReader(raw));
        }
    });
    private volatile ResolvedTargets resolvedTargets;
    private boolean playniteLibraryRefreshing;
    private boolean playniteInitialLoadPending;
    private String initialLocalAppsHostId = "";
    private String initialLocalLibraryHostId = "";
    private final Set<String> initialCarouselArtworkLoads = new HashSet<>();
    private int initialCarouselArtworkGeneration;
    private boolean suppressInitialCarouselMotion;
    /**
     * The carousel row's resize animation. Its animators start only with a drawn frame, and
     * until then the row skips its layouts: a Home holding its first frame installs it after.
     */
    private LayoutTransition carouselTransition;
    /** Tag prefix of a carousel position whose card a later frame builds. */
    private static final String PENDING_CARD_TAG = "playnite.pending:";
    private static final long CAROUSEL_CARD_BUILD_GAP_MS = 100L;
    private final Runnable carouselCardBuildStep = this::buildNextPendingCarouselCard;
    private boolean deferInitialPlayniteRefresh;
    private Future<?> libraryReadAhead;
    private boolean deferInitialHostProfileRefresh;
    private PlayniteLibraryRepository.ErrorKind playniteLibraryError;
    private String currentPlayniteHostUuid;
    private int pendingConsoleUpdateChannels;
    private boolean consoleUpdatePosted;
    private final ViewTreeObserver.OnGlobalFocusChangeListener consoleFocusSoundListener =
            (oldFocus, newFocus) -> {
                if (newFocus != null) newFocus.setSoundEffectsEnabled(false);
                // The engine ticks only when a directional key pressed here caused this move.
                if (newFocus != null && oldFocus != null && consoleAudioEngine != null) {
                    consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.NAVIGATE);
                }
            };

    private final Runnable playniteRefreshCycle = new Runnable() {
        @Override public void run() {
            if (!active || loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE) return;
            refreshChildBindingStates();
            ComputerDetails host = hosts.get(selectedHostUuid);
            if (host != null) {
                refreshChildEligibilityProfile(host);
                requestPlayniteRefresh(host, false);
            }
        }
    };

    private final ComputerManagerListener computerListener = (details, polled) -> {
        ComputerDetails copy = new ComputerDetails(details);
        mainHandler.post(() -> {
            // An offline poll while the streamer settles after a stream is no observation.
            boolean fresh = polled && !hostStateController.settling(copy);
            ComputerDetails previous = hosts.get(copy.uuid);
            if (!fresh && previous != null) {
                hostStateController.remember(previous.uuid, previous.state);
            }
            if (fresh) reportHostPoll(copy);
            hostStateController.observe(copy, fresh);
            if (fresh) rememberConfirmedHostState(copy);
            if (previous == null && initialHostsLoaded) newlyDiscoveredHosts.add(copy.uuid);
            hosts.put(copy.uuid, copy);
            warmAppList(copy);
            if (fresh && active && copy.uuid.equals(selectedHostUuid)) {
                refreshSessionProjection(copy, null);
                reconcileHostStreamRecord(copy);
            }
            queueConsoleUpdates(ConsoleUpdateChannels.diff(
                    previous, copy, selectedHostUuid));
            if (fresh && hostEntryAttempt.pollAnswered(copy.uuid) && hostSelectionVisible) {
                activateHostFromSelection(copy.uuid);
            }
        });
    };

    /**
     * A fresh poll of {@code host} is a session-model event: the model decides what it ends
     * (a power command, a reconnect marker the host's 0 retired, a stream it ended); this
     * console applies only the effects of the outcome.
     */
    private void reportHostPoll(ComputerDetails host) {
        String profileId = selectedProfileId(host.uuid);
        sessionModel.dispatch(new SessionEvent.HostPolled(host.uuid, profileId,
                executionProfileId(host.uuid, profileId),
                host.state == ComputerDetails.State.ONLINE,
                host.state == ComputerDetails.State.OFFLINE, host.runningGameId,
                System.currentTimeMillis()), outcome -> {
            String lost = outcome.lostStreamSessionId;
            if (!lost.isEmpty()) {
                BackgroundStreamService.dismiss(this, lost);
                if (isStreamHome() && lost.equals(
                        launchedExtra(EXTRA_RETAINED_STREAM_SESSION_ID))
                        && host.uuid.equalsIgnoreCase(
                        launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID))) finish();
            }
            if (!outcome.endedStreamSessionId.isEmpty()) {
                BackgroundStreamService.dismiss(this, outcome.endedStreamSessionId);
            }
            if (outcome.restarted != null) signInAfterRestart(host, outcome.restarted);
            if (!outcome.ended || isDestroyed()) return;
            invalidateSessionProjectionRequest(host.uuid);
            if (host.uuid.equalsIgnoreCase(selectedHostUuid)) {
                refreshSelectedApplications();
                refreshSelectedPlayniteLibrary();
            }
        });
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            ComputerManagerService.ComputerManagerBinder binder =
                    (ComputerManagerService.ComputerManagerBinder) service;
            executor.execute(() -> {
                binder.waitForReady();
                mainHandler.post(() -> {
                    managerBinder = binder;
                    dispatchPendingHostPreparation();
                    dispatchPendingWarmUpRelay();
                    if (active) startPolling();
                });
            });
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            managerBinder = null;
            polling = false;
            updateHostSelector();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        sessionModel = SessionModel.of(this);
        returnRequests.created(state != null,
                state != null && state.getBoolean(STATE_SUPPRESS_AUTO_WARM_UP, false),
                getIntent().getBooleanExtra(EXTRA_SUPPRESS_AUTO_WARM_UP, false),
                getIntent().getStringExtra(EXTRA_HARD_TERMINATE_HOST_ID),
                getIntent().getStringExtra(EXTRA_WATCH_NEXT_SESSION_ID));
        if (isStreamHome()) setTheme(useTransparentWarmUpStartingWindow()
                ? R.style.ConsoleStreamHomeWarmUpTheme
                : R.style.ConsoleStreamHomeTheme);
        UiHelper.setLocale(this);
        super.onCreate(state);
        // A Watch Next tile resumes only after its profile gate (enterHostAfterProfileGate).
        if (!isStreamHome() && returnRequests.watchNextSessionId().isEmpty()
                && sessionModel.canResumeInstantly()) {
            resumeRetainedStream();
            return;
        }
        homePreparingOperation = getIntent().getLongExtra(EXTRA_PREPARING_OPERATION, 0L);
        warmUpClientHostId = homePreparingOperation > 0L
                ? launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID) : "";
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        microphoneStatePreferences = getSharedPreferences(
                UsbMicrophoneService.STATE_PREFS, MODE_PRIVATE);
        libraryViewStateStore = new ConsoleLibraryViewStateStore(preferences);
        reducedMotion = startupReducedMotion();
        libraryTransitionCoordinator = new ConsoleLibraryTransitionCoordinator(
                mainHandler, reducedMotion);
        uiSoundsEnabled = preferences.getBoolean("ui_sounds", true);
        ambientSoundsEnabled = preferences.getBoolean("ambient_sounds", true);
        hostMusicVolume = preferences.getInt("host_music_volume", 40);
        menuMusicVolume = preferences.getInt("menu_music_volume", 30);
        effectsVolume = preferences.getInt("effects_volume", 40);
        backgroundStreamRetentionMinutes = BackgroundStreamPreferences.readMinutes(this);
        screenSaverSeconds = preferences.getInt(PREF_SCREEN_SAVER_SECONDS,
                SCREEN_SAVER_DEFAULT_SECONDS);
        showCarouselGameDescription = preferences.getBoolean(
                "show_carousel_game_description", true);
        selectedHostUuid = preferences.getString("selected_host", null);
        if (state != null) {
            restoredEnteredHost = state.getString(STATE_ENTERED_HOST, "");
            reopenAppearance = state.getBoolean(STATE_REOPEN_APPEARANCE, false);
        }
        if (isStreamHome() && !launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID).isEmpty()) {
            selectedHostUuid = launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID);
            hostSelectionVisible = false;
        }
        autoLoginHostUuid = preferences.getString("auto_login_host", "");
        assetLoader = new DiskAssetLoader(this);
        consoleAudioEngine = new ConsoleAudioEngine(this);
        consoleAudioEngine.setEffectsEnabled(uiSoundsEnabled);
        consoleAudioEngine.setAmbientEnabled(ambientSoundsEnabled);
        consoleAudioEngine.setHostSelectionVolume(hostMusicVolume / 100f);
        consoleAudioEngine.setMenuVolume(menuMusicVolume / 100f);
        consoleAudioEngine.setEffectsVolume(effectsVolume / 100f);
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        hostGatewayClient = new HostGatewayClient();
        captureGallery = new ConsoleCaptureGallery(this, hostGatewayClient, executor);
        gameOperationsController = new GameOperationsController(
                hostGatewayClient, executor, mainHandler::post);
        hostGatewayStore = new HostGatewayStore(this);
        hostPairingFlow = new HostPairingFlow(this, executor, mainHandler, hostGatewayClient,
                hostGatewayStore, () -> managerBinder, this::saveHostProfiles);
        playniteLibraryRepository = new PlayniteLibraryRepository(hostGatewayClient,
                new PlayniteLibraryCache(this));
        playniteArtworkCache = new PlayniteArtworkCache(this);
        profileAvatars = new ProfileAvatars(this, hostGatewayClient, avatarExecutor, mainHandler);
        profileAvatars.setOnLoaded(() -> {
            if (profileToolbarDrawable != null) profileToolbarDrawable.invalidateSelf();
        });
        quickLaunchManager = QuickLaunchManager.getInstance(this);
        shortcutHelper = new ShortcutHelper(this);
        getWindow().setFormat(isStreamHome()
                ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE);
        root = buildUi();
        setContentView(root);
        PreferenceConfiguration shortcutPreferences = PreferenceConfiguration.readPreferences(this);
        discordDmToastView.setReducedMotion(reducedMotion);
        discordDmToastView.setShortcutLabel(getString(R.string.discord_dm_toast_shortcut,
                getString(DiscordDmShortcutHandler.triggerLabelResource(
                        shortcutPreferences.overlayTriggerButton))));
        discordDmNotifications = DiscordDmNotificationCoordinator.getInstance();
        discordDmNotifications.initialize(this);
        discordDmHostToken = discordDmNotifications.registerHost(
                new DiscordDmNotificationCoordinator.Host() {
                    @Override public void showDiscordDmToast(
                            DiscordDmNotificationCoordinator.ToastModel model, boolean announce) {
                        if (!discordAllowedForSelectedProfile()) {
                            discordDmToastView.hideDiscordDmToastImmediately();
                            return;
                        }
                        discordDmToastView.showDiscordDmToast(model, announce);
                        setDiscordNotificationPending(true);
                    }

                    @Override public void hideDiscordDmToast() {
                        discordDmToastView.hideDiscordDmToast();
                    }

                    @Override public void hideDiscordDmToastImmediately() {
                        discordDmToastView.hideDiscordDmToastImmediately();
                    }
                });
        discordDmShortcut = new DiscordDmShortcutHandler(
                shortcutPreferences.overlayTriggerButton,
                shortcutPreferences.overlayHoldDurationMs,
                new DiscordDmShortcutHandler.Scheduler() {
                    @Override public void postDelayed(Runnable task, long delayMs) {
                        mainHandler.postDelayed(task, delayMs);
                    }

                    @Override public void remove(Runnable task) {
                        mainHandler.removeCallbacks(task);
                    }
                },
                () -> discordAllowedForSelectedProfile()
                        && discordDmNotifications.hasQuickAction(discordDmHostToken),
                this::openDiscordDmShortcut);
        consoleFeedback = new ConsoleUiFeedback(this, root, consoleAudioEngine, reducedMotion);
        hostLaunchPreflight = createHostLaunchPreflight();
        sessionOrchestrator = createSessionOrchestrator();
        warmUpRelayDispatched = state != null
                && state.getBoolean(STATE_WARM_UP_RELAY_DISPATCHED, false);
        armPendingWarmUpRelay();
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(consoleFocusSoundListener);
        discordPanelController = new DiscordPanelController(this, mainHandler, executor);
        discordSocialPanelController = new DiscordSocialPanelController(this, mainHandler,
                new DiscordSocialPanelController.Ui() {
                    @Override public TextView action(String label) {
                        return consolePanel.action(label);
                    }

                    @Override public TextView label(String label) {
                        return consolePanel.note(label);
                    }

                    @Override public void show(String eyebrow, String title, String details,
                                               View... actions) {
                        showSidePanel(eyebrow, title, details, actions);
                    }

                    @Override public void showCommunity(View shell) {
                        showCommunityPanel(shell);
                    }

                    @Override public ConsoleMenuBackdrop menuBackdrop() { return menuBackdrop; }
                    @Override public String dmShortcutTrigger() { return discordDmShortcut.trigger(); }

                    @Override public void toast(String message) {
                        ConsoleUiFeedback.makeText(ConsoleActivity.this, message,
                                Toast.LENGTH_LONG).show();
                    }

                    @Override public void backPanel() {
                        handlePanelBack();
                    }

                    @Override public void requestCommunityDictation(long recipientId,
                                                                     long directMessageGeneration) {
                        startCommunityDictation(recipientId, directMessageGeneration);
                    }

                    @Override public void dismissCommunityForAuthorization() {
                        hideSidePanelImmediately();
                    }

                    @Override public void openDiscordSettings() {
                        hideSidePanelImmediately();
                        showSettings(SETTINGS_DISCORD, ConsoleActivity.this::showDiscordPanel);
                    }

                    @Override public void voiceChanged(HostGatewayClient.DiscordVoice voice) {
                        applyDiscordVoiceIndicator(voice);
                    }

                    @Override public void visiblePeerChanged(long peerId) {
                        if (discordDmNotifications != null) {
                            discordDmNotifications.setVisiblePeer(discordDmHostToken, peerId);
                        }
                    }
                }, new DiscordSocialPanelController.CommunitySource() {
                    @Override public GatewayConnection connection() {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        return host == null ? null : hostGatewayStore.loadForHost(host.uuid,
                                host.activeAddress == null ? null : host.activeAddress.address,
                                selectedProfileId(host.uuid));
                    }

                    @Override public void requestHome(DiscordPanelController.CommunityHomeCallback callback) {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        if (host == null) {
                            callback.onUnavailable(R.string.discord_community_host_unavailable);
                            return;
                        }
                        String address = host.activeAddress == null ? null : host.activeAddress.address;
                        discordPanelController.loadCommunityHome(host.uuid, address, callback);
                    }

                    @Override public void requestGuildChannels(HostGatewayClient.DiscordGuild guild,
                            DiscordPanelController.CommunityChannelsCallback callback) {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        if (host == null) {
                            callback.onUnavailable();
                            return;
                        }
                        String address = host.activeAddress == null ? null : host.activeAddress.address;
                        discordPanelController.loadCommunityGuildChannels(host.uuid, address,
                                guild, callback);
                    }

                    @Override public void joinChannel(HostGatewayClient.DiscordChannel channel,
                            DiscordPanelController.CommunityActionCallback callback) {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        if (host == null) {
                            callback.onError(getString(R.string.discord_join_not_confirmed));
                            return;
                        }
                        String address = host.activeAddress == null ? null : host.activeAddress.address;
                        discordPanelController.joinCommunityChannel(host.uuid, address,
                                channel, callback);
                    }

                    @Override public void requestVoice(
                            DiscordPanelController.CommunityVoiceCallback callback) {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        if (host == null) { callback.onError(getString(R.string.discord_join_not_confirmed)); return; }
                        String address = host.activeAddress == null ? null : host.activeAddress.address;
                        discordPanelController.loadCommunityVoice(host.uuid, address, callback);
                    }

                    @Override public void voiceAction(DiscordPanelController.CommunityVoiceAction action,
                                                      DiscordPanelController.CommunityVoiceCallback callback) {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        if (host == null) { callback.onError(getString(R.string.discord_join_not_confirmed)); return; }
                        String address = host.activeAddress == null ? null : host.activeAddress.address;
                        discordPanelController.performCommunityVoiceAction(host.uuid, address,
                                action, callback);
                    }
                });
        // The wizard opens here, before the host list loads: a run the console is recreated
        // in (a language change) is back on its step in the first frame, with nothing between.
        FirstRunWizard wizard = isStreamHome() ? null : firstRunWizard();
        if (wizard != null && wizard.wantsToStart(hostGatewayStore.hasPairedHost())) {
            showHostSelection(null);
            wizard.autoStart(hostGatewayStore.hasPairedHost());
        } else if (!isStreamHome()) {
            // A tester whose wizard is done (an update) sees the welcome at once.
            mainHandler.post(() -> testerWelcomeThen(null));
        }
        hideSystemUi();
        loadKnownHosts();
        serviceBound = bindService(new Intent(this, ComputerManagerService.class),
                serviceConnection, Service.BIND_AUTO_CREATE);
    }

    @Override protected void onStart() { super.onStart(); sessionSubscriber.start(sessionModel); }

    @Override
    protected void onResume() {
        childStreamHandoff = null;
        super.onResume();
        if (settingsScreen != null && settingsScreen.isShowing()) settingsScreen.refresh();
        microphoneStatePreferences.registerOnSharedPreferenceChangeListener(
                microphoneStateListener);
        UsbForwardingController.get(this).addListener(usbForwardingListener);
        if (discordAllowedForSelectedProfile() && discordDmNotifications != null) {
            discordDmNotifications.activateHost(discordDmHostToken);
            discordDmNotifications.setWindowFocused(
                    discordDmHostToken, getWindow().getDecorView().hasWindowFocus());
        }
        active = true;
        if (initialHostsLoaded) resolveInitialHostSelection();
        String hardTerminateOffer = returnRequests.takeHardTerminateOffer();
        if (!hardTerminateOffer.isEmpty()) {
            // ponytail: also dropped when this console was recreated and its host binder is
            // not connected yet; the action stays in the host panel.
            mainHandler.post(() -> {
                // Never over a host picker, a profile gate or its PIN, nor on another host.
                if (hardTerminateOffer.equalsIgnoreCase(selectedHostUuid)
                        && !hostSelectionVisible && profileGateHostUuid == null) {
                    confirmHardTerminateSession(currentHost(hardTerminateOffer));
                }
            });
        }
        refreshChildBindingStates();
        refreshDiscordAccess();
        // The retained stream Home always belongs to the host backing the live stream.
        // A stale "return to hosts" request from an earlier suspend flow must not move it
        // away from that host's carousel.
        if (!isStreamHome()) {
            sessionModel.dispatch(new SessionEvent.HostSelectionConsumed(), returnToHosts -> {
                if (returnToHosts.isEmpty() || isFinishing()) return;
                ProfileAuthorization.revoke();
                showHostSelection(returnToHosts);
            });
        }
        if (consoleAudioEngine != null) {
            consoleAudioEngine.setMenuVisible(loadingLayer == null
                    || loadingLayer.getVisibility() != View.VISIBLE);
            consoleAudioEngine.setHostSelectionVisible(hostSelectionVisible);
            consoleAudioEngine.resume();
        }
        if (refreshHostsOnResume) {
            refreshHostsOnResume = false;
            loadKnownHosts();
        }
        if (inputManager != null && !inputListenerRegistered) {
            inputManager.registerInputDeviceListener(this, mainHandler);
            inputListenerRegistered = true;
        }
        startPolling();
        ComputerDetails selected = hosts.get(selectedHostUuid);
        if (selected != null && !hostSelectionVisible && profileGateHostUuid == null) {
            if (appListPoller == null) startAppListPoller(selected);
            if (!renderedPlayniteItems.isEmpty()) {
                schedulePlayniteArtworkPrefetch(selected, renderedPlayniteItems);
            }
            loadPlayniteForHost(selected);
            // Back on screen (for example from a stream): the last known state stays until the
            // host's session is read now, past the poll cadence; nothing polls while hidden.
            refreshSessionProjection(selected, accepted -> { });
            reconcileHostStreamRecord(selected);
        }
        if (refreshSessionOnResume) {
            refreshSessionOnResume = false;
            refreshSessionState(selectedHostUuid);
        }
        refreshControllers();
        refreshDiscordIndicator();
        refreshUsbMicrophoneIndicator();
        refreshUsbForwardingIndicator();
        mainHandler.postDelayed(controllerRefresh, CONTROLLER_REFRESH_MS);
        if (loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE) showHome();
        if (homeLayer != null) {
            homeLayer.setVisibility(hostSelectionVisible ? View.GONE : View.VISIBLE);
        }
        if (entryShell != null) {
            // The profile screen is a page of the shell.
            entryShell.setVisibility(hostSelectionVisible || profileGateHostUuid != null
                    || firstRunWizard != null && firstRunWizard.isShowing()
                    ? View.VISIBLE : View.GONE);
        }
        if (firstRunWizard != null) firstRunWizard.refresh();
        // After the stale-cover cleanup above: a queued Play taken over from the stream Home
        // draws its cover now and keeps it.
        if (sessionOrchestrator != null && sessionOrchestrator.queued() != null) {
            renderQueue();
            sessionOrchestrator.sessionChanged();
        }
        hideSystemUi();
        resetScreenSaverTimer();
        updateScreenSaverWakePolicy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                lastKeyDownAt = SystemClock.uptimeMillis();
            }
            InputDevice inputDevice = event.getDevice();
            if (inputDevice != null && isGamepad(inputDevice)) {
                hasLastControllerInput = true;
                lastInputWasController = true;
                lastControllerPlayStation = ControllerGlyphs.isPlayStation(inputDevice);
                if (pinEntryView != null && pinProfileId != null) {
                    pinEntryView.setPlayStationButtons(lastControllerPlayStation);
                    pinEntryView.setControllerInputMode(true);
                }
            } else if (inputDevice != null) {
                lastInputWasController = false;
                if (pinEntryView != null && pinProfileId != null) {
                    pinEntryView.setControllerInputMode(false);
                }
            }
            if (screenSaverVisible) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    screenSaverDismissKeyCode = event.getKeyCode();
                    hideScreenSaver();
                }
                return true;
            }
            if (screenSaverDismissKeyCode == event.getKeyCode()) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    screenSaverDismissKeyCode = KeyEvent.KEYCODE_UNKNOWN;
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) resetScreenSaverTimer();
            if (introView != null && introView.handleKey(event)) return true;
            // Back on a stepper being edited only stops editing; the panel stays open.
            if (ConsolePanel.isBackKey(event.getKeyCode())
                    && ConsolePanel.isEditing(getCurrentFocus())) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    ConsolePanel.endEditing(getCurrentFocus());
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && isDirectionalNavigationKey(event.getKeyCode())) {
                lastDirectionalNavigationAt = event.getEventTime();
            }
        }
        if (pinProfileId != null && event != null) {
            int sources = event.getSource();
            InputDevice device = event.getDevice();
            if (device != null) sources |= device.getSources();
            boolean blocked = pinSubmitting
                    || SystemClock.elapsedRealtime() < pinCooldownUntil;
            ConsolePinEntry.Action action = blocked
                    ? pinEntry.handleBlocked(event.getAction(), event.getKeyCode(), sources)
                    : pinEntry.handle(event.getAction(), event.getKeyCode(), sources);
            if (action != ConsolePinEntry.Action.IGNORED) {
                if (action == ConsolePinEntry.Action.CHANGED) renderPinEntry("");
                else if (action == ConsolePinEntry.Action.SUBMIT) submitPin();
                else if (action == ConsolePinEntry.Action.BACK) leavePin();
                return true;
            }
        }
        if (pinProfileId == null && event != null && firstRunWizard != null
                && firstRunWizard.isShowing()) {
            return firstRunWizard.handleKey(event) || super.dispatchKeyEvent(event);
        }
        if (profileGateHostUuid != null && pinProfileId == null && event != null
                && profileSelectView != null && profileSelectView.handleKey(event)) return true;
        if (profileGateHostUuid != null && event != null
                && event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B)) {
            closeProfileGate();
            return true;
        }
        if (discordDmShortcut != null && discordDmShortcut.handle(event)) return true;
        if (isGuideKey(event)) return true;
        if (librarySearch != null && librarySearch.isOpen() && event != null
                && librarySearch.handleKey(event)) return true;
        if (libraryTransitionRunning && event != null
                && (isDirectionalNavigationKey(event.getKeyCode())
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A)) {
            return true;
        }
        handleConsoleAudioKey(event);
        if (hostSelectionVisible && event != null && hostSelectView != null
                && hostSelectView.handleKey(event)) {
            return true;
        }
        if (!hostSelectionVisible && event != null
                && event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_MENU
                || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_START)) {
            PlayniteDashboardItem item = focusedPlayniteItem();
            ComputerDetails host = hosts.get(selectedHostUuid);
            if (item != null && host != null) {
                showPlayniteGameActions(host, item);
                return true;
            }
        }
        if (!hostSelectionVisible && event != null && sessionOrchestrator != null
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B)) {
            // Back on the queued game's tile cancels its queued Play, and only that.
            PlayniteDashboardItem item = focusedPlayniteItem();
            if (item != null && isQueuedPlayniteItem(selectedHostUuid, item)) {
                if (event.getAction() == KeyEvent.ACTION_UP) sessionOrchestrator.cancelQueued();
                return true;
            }
        }
        if (expandedLibraryMode && event != null
                && event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B)) {
            exitExpandedLibrary();
            return true;
        }
        if (expandedLibraryMode && event != null
                && isExpandedLibraryShortcutKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_UP) return true;
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                if (navigationInputDeduplicator.shouldSuppress(
                        event.getKeyCode(), event.getEventTime())) return true;
                return handleExpandedLibraryShortcut(event.getKeyCode());
            }
        }
        if (CONSOLE_UI_V2 && event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0
                && isDirectionalNavigationKey(event.getKeyCode())) {
            if (navigationInputDeduplicator.shouldSuppress(
                    event.getKeyCode(), event.getEventTime())) {
                android.util.Log.d("MoonWakerInput",
                        "Suppressed duplicate navigation key " + event.getKeyCode()
                                + " from device " + event.getDeviceId());
                return true;
            }
        }
        if (handleCarouselNavigation(event)) return true;
        if (expandedLibraryMode && expandedGrid != null && expandedGrid.handleKey(event)) {
            return true;
        }
        if (handleManualFocusNavigation(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && screenSaverVisible) {
            hideScreenSaver();
            return true;
        }
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            resetScreenSaverTimer();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isMeaningfulMotion(event)) {
            if (screenSaverVisible) {
                screenSaverMotionBlockUntil = SystemClock.uptimeMillis() + 350L;
                hideScreenSaver();
                return true;
            }
            if (SystemClock.uptimeMillis() < screenSaverMotionBlockUntil) return true;
            resetScreenSaverTimer();
        }
        return super.dispatchGenericMotionEvent(event);
    }

    /**
     * Moves focus without delegating directional input to ViewRootImpl. Some Android TV
     * builds play their own navigation sound there even when sound effects are disabled
     * on the focused view, which otherwise doubles MoonWaker's console cue.
     */
    private boolean handleManualFocusNavigation(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN
                || !isDirectionalNavigationKey(event.getKeyCode())) return false;
        View focused = getCurrentFocus();
        if (focused == null) return false;
        int direction;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                direction = View.FOCUS_LEFT;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                direction = View.FOCUS_RIGHT;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                direction = View.FOCUS_UP;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                direction = View.FOCUS_DOWN;
                break;
            default:
                return false;
        }
        View target = focused.focusSearch(direction);
        if (target != null && target != focused) target.requestFocus(direction);
        return true;
    }

    /**
     * Guide/PS arrives as BUTTON_MODE; left unhandled, Generic.kcm's "fallback HOME" sends
     * the TV to its launcher. The console consumes it until the Guide quick menu exists.
     */
    private static boolean isGuideKey(KeyEvent event) {
        return event != null && event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE;
    }

    private boolean handleCarouselNavigation(KeyEvent event) {
        if (expandedLibraryMode || hostSelectionVisible || appRow == null || event == null
                || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int keyCode = event.getKeyCode();
        if (getCurrentFocus() == null && isDirectionalNavigationKey(keyCode)
                && appRow.isShown()) {
            // A card that held focus went away under fast navigation: the key resumes at the
            // carousel's selection, never at the window's first focusable (the host pill).
            View target = carouselFocusTarget();
            if (target != null) target.requestFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusCarouselBelowTopBar();
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        View focused = getCurrentFocus();
        if (focused == null || focused.getParent() != appRow || !focused.isFocusable()) {
            return false;
        }
        int current = appRow.indexOfChild(focused);
        if (current < 0) return false;
        int step = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
        int target = current + step;
        while (target >= 0 && target < appRow.getChildCount()) {
            View candidate = appRow.getChildAt(target);
            // A position whose card a later frame would build gets it now.
            if (pendingCarouselGameId(candidate) != null) {
                candidate = buildPendingCarouselCard(candidate);
            }
            if (candidate != null && candidate.getVisibility() == View.VISIBLE
                    && candidate.isFocusable()) {
                candidate.requestFocus();
                return true;
            }
            target += step;
        }
        return true;
    }

    /**
     * Down from the top bar returns to the carousel's selection while it is in view, else lands
     * on the visible card nearest below the focused item.
     */
    private boolean focusCarouselBelowTopBar() {
        View focused = getCurrentFocus();
        if (focused == null || topBar == null || appScroll == null
                || !isDescendant(topBar, focused) || appScroll.getWidth() == 0) return false;
        List<View> cards = new ArrayList<>();
        int selected = -1;
        for (int index = 0; index < appRow.getChildCount(); index++) {
            View child = appRow.getChildAt(index);
            if (child.getVisibility() == View.VISIBLE && (child.isFocusable()
                    || pendingCarouselGameId(child) != null)) {
                if (!lastCarouselGameId.isEmpty() && (("playnite:" + lastCarouselGameId)
                        .equals(child.getTag())
                        || lastCarouselGameId.equals(pendingCarouselGameId(child)))) {
                    selected = cards.size();
                }
                cards.add(child);
            }
        }
        int[] location = new int[2];
        int[] lefts = new int[cards.size()];
        int[] rights = new int[cards.size()];
        for (int index = 0; index < cards.size(); index++) {
            cards.get(index).getLocationInWindow(location);
            lefts[index] = location[0];
            rights[index] = location[0] + cards.get(index).getWidth();
        }
        focused.getLocationInWindow(location);
        // A wide item (the host pill) counts from its leading card's width: at the carousel's
        // start that is the first card, never the one under the middle of the pill.
        int originCenter = location[0]
                + Math.min(focused.getWidth(), dp(CAROUSEL_CARD_WIDTH_DP)) / 2;
        appScroll.getLocationInWindow(location);
        int nearest = topBarDownTarget(selected, lefts, rights, location[0],
                location[0] + appScroll.getWidth(), originCenter);
        if (nearest < 0) return false;
        View target = cards.get(nearest);
        if (pendingCarouselGameId(target) != null) target = buildPendingCarouselCard(target);
        if (target == null || !target.isFocusable()) return false;
        target.requestFocus();
        return true;
    }

    /**
     * The card Down from the top bar lands on: {@code selected} (the carousel's selection, -1
     * when none) while its centre lies in the viewport, else {@link #nearestVisibleCard}.
     */
    static int topBarDownTarget(int selected, int[] lefts, int[] rights, int viewportLeft,
                                int viewportRight, int originCenter) {
        if (selected >= 0 && selected < lefts.length) {
            long center = ((long) lefts[selected] + rights[selected]) / 2;
            if (center >= viewportLeft && center < viewportRight) return selected;
        }
        return nearestVisibleCard(lefts, rights, viewportLeft, viewportRight, originCenter);
    }

    /**
     * The card whose centre is nearest {@code originCenter} among those whose centre lies in
     * the viewport [{@code viewportLeft}, {@code viewportRight}); the left one on a tie.
     * -1 when no card is visible.
     */
    static int nearestVisibleCard(int[] lefts, int[] rights, int viewportLeft,
                                  int viewportRight, int originCenter) {
        int nearest = -1;
        long nearestDistance = Long.MAX_VALUE;
        for (int index = 0; index < lefts.length; index++) {
            long center = ((long) lefts[index] + rights[index]) / 2;
            if (center < viewportLeft || center >= viewportRight) continue;
            long distance = Math.abs(center - originCenter);
            if (distance < nearestDistance) {
                nearest = index;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void handleConsoleAudioKey(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN
                || consoleAudioEngine == null) return;
        int keyCode = event.getKeyCode();
        if (isDirectionalNavigationKey(keyCode)) {
            consoleAudioEngine.noteDirectionalInput(event.getDownTime());
            return;
        }
        if (event.getRepeatCount() != 0) return;
        if (ConsoleHostSelectView.isConfirmKey(keyCode)) {
            consoleAudioEngine.playSystemConfirm();
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            consoleAudioEngine.playSystemBack();
        } else if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            consoleAudioEngine.playSystemConfirm();
        }
    }

    private PlayniteDashboardItem focusedPlayniteItem() {
        View focused = getCurrentFocus();
        Object tag = focused != null ? focused.getTag() : null;
        if (!(tag instanceof String) || !((String) tag).startsWith("playnite:")) return null;
        String stableId = ((String) tag).substring("playnite:".length());
        int index = PlayniteLibraryQuery.indexOf(allPlayniteItems, stableId);
        if (index >= 0) return allPlayniteItems.get(index);
        index = PlayniteLibraryQuery.indexOf(unfilteredPlayniteItems, stableId);
        return index >= 0 ? unfilteredPlayniteItems.get(index) : null;
    }

    private boolean handleExpandedLibraryShortcut(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
            case KeyEvent.KEYCODE_BUTTON_L2:
                showLibraryFilters();
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                ComputerDetails host = hosts.get(selectedHostUuid);
                if (host == null) return true;
                clearLibraryFilters(host);
                ConsoleUiFeedback.makeText(this, R.string.playnite_filters_reset,
                        Toast.LENGTH_SHORT).show();
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                openLibrarySearch();
                return true;
            default:
                return false;
        }
    }

    /** Filters, platforms, sort and search back to their defaults; the grid restarts at 0. */
    private void clearLibraryFilters(ComputerDetails host) {
        clearNarrowingLibraryFilters(host);
        hostGatewayStore.setPlayniteLibrarySort(host.uuid, PlayniteLibrarySort.RECENT);
        childLibraryFilter.select(childLibraryKey(host.uuid), null, null);
        renderFilteredPlayniteLibraryFromStart(host);
    }

    /** State, platforms and search back to "everything"; the sort stays. */
    private void clearNarrowingLibraryFilters(ComputerDetails host) {
        hostGatewayStore.setPlayniteLibraryFilter(host.uuid, PlayniteLibraryFilter.ALL);
        hostGatewayStore.clearPlayniteLibrarySources(host.uuid);
        expandedSearchQuery = "";
        libraryViewStateStore.saveSearch(host.uuid, "");
    }

    /**
     * Shows {@code child}'s view of the library (mockup 23g), or the whole library for null. The
     * grid is exactly what the child sees, so the parent's own narrowing filters are cleared.
     */
    private void selectChildLibraryFilter(ComputerDetails host, ChildLibraryFilter.Child child,
                                          ChildLibraryFilter.Group group) {
        if (child != null) clearNarrowingLibraryFilters(host);
        childLibraryFilter.select(childLibraryKey(host.uuid), child, group);
    }

    private void showLibraryFilters() {
        if (expandedLibraryMode && selectedHostUuid != null) {
            libraryFiltersPanel.open(selectedHostUuid);
        }
    }

    /**
     * Opens the full library with the on-screen keyboard over the grid, filtering it live by
     * title (UX_MENU_PL.md 3.1, 3.5). The single search entry: Y, "Wyszukaj" and the top bar.
     */
    public void openLibrarySearch() {
        if (!expandedLibraryMode) enterExpandedLibrary();
        if (!expandedLibraryMode || librarySearch.isOpen()) return;
        expandedNavigationLegend.setVisibility(View.GONE);
        librarySearch.open(expandedSearchQuery);
    }

    private void closeLibrarySearch() {
        if (selectedHostUuid != null) {
            libraryViewStateStore.saveSearch(selectedHostUuid, expandedSearchQuery);
        }
        // Focus moves to the first result before the keyboard holding it is hidden.
        expandedGrid.focusPosition(0);
        librarySearch.close();
        expandedNavigationLegend.setVisibility(View.VISIBLE);
    }

    private void searchLibrary(String query) {
        expandedSearchQuery = query.trim();
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) return;
        renderExpandedLibrary(host);
        expandedGrid.showPosition(0);
        // No card has focus while typing: the details follow the first result instead.
        List<PlayniteDashboardItem> results = expandedLibraryItems(host);
        if (results.isEmpty()) {
            expandedGameTitle.setText("");
            expandedGameFacts.setText("");
            expandedGameDescription.setText("");
        } else {
            showGameMetadataHeader(results.get(0));
            showGameMetadataDescription(results.get(0));
        }
    }

    protected boolean useTransparentWarmUpStartingWindow() {
        return false;
    }

    /** True for the Home placed over a retained stream ({@link StreamHomeActivity}). */
    protected boolean isStreamHome() {
        return false;
    }

    /** A stream this console asked the model to close has ended; it owns what is shown next. */
    protected void onRequestedStreamClosed() { }

    /** The retained stream this Home was launched for: its launch request, not live state. */
    private String launchedExtra(String name) {
        return normalizeId(getIntent().getStringExtra(name));
    }

    private String launchedProfileId() {
        return GatewayConnection.normalizeProfileId(
                getIntent().getStringExtra(EXTRA_RETAINED_STREAM_PROFILE_ID));
    }

    private int launchedAppId() {
        return getIntent().getIntExtra(EXTRA_RETAINED_STREAM_APP_ID,
                StreamConfiguration.INVALID_APP_ID);
    }

    protected final boolean hasResolvedInitialHostSelection() {
        return initialHostSelectionResolved;
    }

    protected boolean requiresPreparedInitialCarouselFrame() {
        return false;
    }

    protected final boolean prepareInitialCarouselFrame() {
        if (!initialLocalPresentationReady(selectedHostUuid,
                initialLocalAppsHostId, initialLocalLibraryHostId)) return false;
        if (!initialCarouselArtworkLoads.isEmpty()) return false;
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) return false;
        if (pendingExpandedLibraryRestore && CONSOLE_UI_V2
                && !unfilteredPlayniteItems.isEmpty()) {
            pendingExpandedLibraryRestore = false;
            enterExpandedLibrary(true);
            return false;
        }
        if (expandedLibraryMode) {
            focusExpandedLibrary();
        } else {
            // A warm-up's Home replaces the console that opened it: that console's focused
            // element and carousel scroll position, both saved when it paused.
            // Read once: every held frame asks again, and a later read (the warm-up handed
            // over, the card still a placeholder) must not move focus to the first card.
            if (inheritedHomeFocusTag == null) {
                inheritedHomeFocusTag = tracksWarmUp()
                        ? preferences.getString("home_focus." + host.uuid, "") : "";
            }
            String inheritedGameId = carouselGameId(inheritedHomeFocusTag);
            View inherited = inheritedHomeFocusTag.isEmpty() ? null
                    : inheritedGameId != null ? carouselCard(inheritedGameId)
                    : homeContent.findViewWithTag(inheritedHomeFocusTag);
            View target = inherited != null ? inherited : firstFocusableChild(appRow);
            if (target != null && !target.isFocused()) {
                int scroll = inherited == null ? 0
                        : preferences.getInt("app_scroll." + host.uuid, 0);
                // Scrolled first: the scroll view then only moves to keep the card in view.
                if (portraitLayout) appVerticalScroll.scrollTo(0, scroll);
                else appScroll.scrollTo(scroll, 0);
                target.requestFocus();
            }
            // The focused card's backdrop, title and description belong to the same frame.
            if (target != null && !carouselSelectionSettled(target)) return false;
        }
        return true;
    }

    /**
     * Whether a focused carousel card shows its selection at rest: its backdrop request ended
     * (art shown, or none to show). Its title and description steps were queued before it.
     */
    private boolean carouselSelectionSettled(View card) {
        if (card.getParent() != appRow || !card.isFocused()) return true;
        for (PlayniteDashboardItem item : renderedPlayniteItems) {
            if (!("playnite:" + item.stableId()).equals(card.getTag())) continue;
            if (!playniteCardArtworkSpec(item.game).available()) return true;
            return settledBackdropTag.equals(playniteArtworkTag(item))
                    && pendingArtworkKey == null && loadingArtworkKey == null;
        }
        return true;
    }

    protected final void completeInitialCarouselFrame() {
        suppressInitialCarouselMotion = false;
        if (carouselTransition != null) appRow.setLayoutTransition(carouselTransition);
        resetInitialCarouselArtworkWarmup();
        libraryTransitionCoordinator.setReducedMotion(reducedMotion);
        if (deferInitialHostProfileRefresh) {
            deferInitialHostProfileRefresh = false;
            ComputerDetails selected = currentHost(selectedHostUuid);
            if (selected != null) refreshHostProfiles(selected);
        }
        if (deferInitialPlayniteRefresh) {
            deferInitialPlayniteRefresh = false;
            requestPlayniteRefresh(currentHost(selectedHostUuid), false);
        }
    }

    static boolean initialLocalPresentationReady(String selectedHostId,
                                                 String appsHostId,
                                                 String libraryHostId) {
        return selectedHostId != null && !selectedHostId.isEmpty()
                && selectedHostId.equals(appsHostId)
                && selectedHostId.equals(libraryHostId);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Whether the shown Home ever gets the keys (TV 2026-10-21: no window had focus).
        MoonWakerDiagnostics.record("INFO", "android.console", "activity.window_focus",
                "status", hasFocus ? "focused" : "unfocused");
        if (discordDmNotifications != null) {
            discordDmNotifications.setWindowFocused(discordDmHostToken, hasFocus);
        }
    }

    private static boolean isExpandedLibraryShortcutKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR
                || keyCode == KeyEvent.KEYCODE_BUTTON_L2
                || keyCode == KeyEvent.KEYCODE_BUTTON_X
                || keyCode == KeyEvent.KEYCODE_BUTTON_Y;
    }

    @Override
    protected void onPause() {
        // Each return to the screen is a new host entry: its stream record is read again.
        hostStreamReconciled.clear();
        childEligibilityLaunchToken++;
        synchronized (childIdentityBindingLock) {
            childIdentityBindingLifecycleToken++;
            childIdentityBindingAttempt = null;
        }
        mainHandler.removeCallbacks(childEligibilityBoundary);
        pendingHostPreparation = null;
        cancelOfflinePinAttempt();
        hostEntryAttempt.end();
        if (firstRunWizard != null) firstRunWizard.pause();
        if (introView != null) introView.end();
        if (pinProfileId != null) {
            profileGateGeneration.incrementAndGet();
            pinEntry.clear();
            pinSubmitting = false;
            pinCooldownUntil = 0L;
            renderPinEntry("");
        }
        cancelWindowsProfileSwitch(false);
        microphoneStatePreferences.unregisterOnSharedPreferenceChangeListener(
                microphoneStateListener);
        UsbForwardingController.get(this).removeListener(usbForwardingListener);
        flushPendingPlayniteSelection();
        if (discordDmShortcut != null) discordDmShortcut.cancel();
        if (discordDmNotifications != null) {
            discordDmNotifications.deactivateHost(discordDmHostToken);
        }
        setDiscordNotificationPending(false);
        if (discordSocialPanelController != null) discordSocialPanelController.onActivityPaused();
        active = false;
        screenSaverDismissKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        screenSaverMotionBlockUntil = 0L;
        hideScreenSaver();
        mainHandler.removeCallbacks(screenSaverTimeout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // A queued Play this screen started and not yet decided goes to the screen shown next.
        if (sessionOrchestrator != null) sessionOrchestrator.screenLeft();
        cancelCurrentPreparation(childStreamHandoff);
        hostPreparationGeneration.incrementAndGet();
        artworkGeneration.incrementAndGet();
        mainHandler.removeCallbacks(controllerRefresh);
        mainHandler.removeCallbacks(playniteRefreshCycle);
        cancelPlayniteRequest();
        cancelPlayniteArtworkPrefetch();
        stopExpandedDescriptionAutoScroll();
        if (libraryTransitionCoordinator != null) libraryTransitionCoordinator.cancel();
        libraryTransitionRunning = false;
        if (streamLoadingView != null) streamLoadingView.stop();
        saveScrollPositions();
        stopAppListPoller();
        if (polling && managerBinder != null) {
            managerBinder.stopPolling();
            polling = false;
        }
        if (inputManager != null && inputListenerRegistered) {
            inputManager.unregisterInputDeviceListener(this);
            inputListenerRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onStop() {
        sessionSubscriber.stop(sessionModel);
        if (gameModeStep != null) gameModeStep.dismissNotice();
        // The music follows what is seen: a see-through window over this one (a hidden
        // warm-up's stream) pauses it but leaves the ambience playing for the next console.
        if (consoleAudioEngine != null) consoleAudioEngine.pause();
        // A launch cover handed to the stream it opened is that launch's only: hidden now, it
        // never shows again when this window becomes visible (TV 2026-10-15: an earlier
        // game's cover under the translucent stream Home as the stream closed).
        if (loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE) showHome();
        super.onStop();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            playniteBitmapCache.evictAll();
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            playniteBitmapCache.trimToSize(16 * 1024 * 1024);
        }
    }

    private boolean startupReducedMotion() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memory);
        return ConsoleReducedMotion.resolve(preferences.contains(ConsoleReducedMotion.KEY)
                        ? preferences.getBoolean(ConsoleReducedMotion.KEY, false) : null,
                activityManager.isLowRamDevice(), memory.totalMem, memory.availMem);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_WARM_UP_RELAY_DISPATCHED,
                warmUpRelayDispatched);
        outState.putBoolean(STATE_SUPPRESS_AUTO_WARM_UP,
                returnRequests.suppressesAutoWarmUp());
        boolean entered = !hostSelectionVisible && profileGateHostUuid == null
                && ProfileAuthorization.allows(selectedHostUuid);
        outState.putString(STATE_ENTERED_HOST, entered ? selectedHostUuid : "");
        outState.putBoolean(STATE_REOPEN_APPEARANCE, entered && reopenAppearance
                && settingsScreen != null && settingsScreen.isShowing());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        MoonWakerDiagnostics.record("INFO", "android.console", "activity.destroyed");
        active = false;
        pendingHostPreparation = null;
        cancelOfflinePinAttempt();
        pinEntry.clear();
        clearPinEntry();
        cancelStreamingAutopilot(false);
        if (root != null && root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalFocusChangeListener(
                    consoleFocusSoundListener);
        }
        if (consoleAudioEngine != null) consoleAudioEngine.release();
        if (consoleFeedback != null) consoleFeedback.release();
        if (artworkScrimAnimator != null) artworkScrimAnimator.cancel();
        if (discordPanelController != null) discordPanelController.destroy();
        if (discordSocialPanelController != null) discordSocialPanelController.closePanel();
        if (discordDmNotifications != null) {
            discordDmNotifications.unregisterHost(discordDmHostToken);
            discordDmHostToken = null;
        }
        if (sideDialog != null) sideDialog.dismiss();
        if (settingsScreen != null) settingsScreen.dismiss();
        if (serviceBound) unbindService(serviceConnection);
        cancelPlayniteRequest();
        cancelPlayniteArtworkPrefetch();
        if (appRow != null) appRow.removeCallbacks(carouselCardBuildStep);
        if (sessionOrchestrator != null) sessionOrchestrator.close();
        if (gameOperationsController != null) gameOperationsController.close();
        playniteExecutor.shutdown();
        playniteArtworkExecutor.shutdownNow();
        executor.shutdownNow();
        diskExecutor.shutdownNow();
        avatarExecutor.shutdownNow();
        coverExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (pinProfileId != null) {
            leavePin();
        } else if (profileGateHostUuid != null) {
            closeProfileGate();
        } else if (sideDialog != null && sideDialog.isShowing()) {
            handlePanelBack();
        } else if (loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE) {
            cancelCurrentPreparation();
            hostPreparationGeneration.incrementAndGet();
            showHome();
        } else if (expandedLibraryMode) {
            exitExpandedLibrary();
        } else if (hostSelectionVisible) {
            requestExit();
        } else {
            leaveHost();
        }
    }

    private void requestExit() {
        if (sessionModel.hasRetainedSession() || sessionModel.records().pending != null) {
            showRetainedStreamExitConfirmation();
        } else {
            showExitConfirmation();
        }
    }

    private void loadKnownHosts() {
        executor.execute(() -> {
            ComputerDatabaseManager database = new ComputerDatabaseManager(this);
            List<ComputerDetails> known = database.getAllComputers();
            database.close();
            mainHandler.post(() -> {
                for (ComputerDetails host : known) {
                    hostStateController.remember(host.uuid,
                            cachedConfirmedHostState(host.uuid));
                    hostStateController.observe(host, false);
                    hosts.put(host.uuid, host);
                    warmAppList(host);
                }
                initialHostsLoaded = true;
                setCardBindBatch(true);
                try {
                    renderHosts();
                } finally {
                    setCardBindBatch(false);
                }
                resolveInitialHostSelection();
            });
        });
    }

    private ComputerDetails.State cachedConfirmedHostState(String hostUuid) {
        String value = preferences.getString(PREF_LAST_CONFIRMED_HOST_STATE + hostUuid, "");
        try {
            return ComputerDetails.State.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ComputerDetails.State.UNKNOWN;
        }
    }

    private void rememberConfirmedHostState(ComputerDetails host) {
        if (host == null || host.uuid == null || host.state == ComputerDetails.State.UNKNOWN) return;
        preferences.edit().putString(PREF_LAST_CONFIRMED_HOST_STATE + host.uuid,
                host.state.name()).apply();
    }

    private void startPolling() {
        if (!active || polling || managerBinder == null) return;
        polling = true;
        managerBinder.startPolling(computerListener);
        ComputerDetails selected = hosts.get(selectedHostUuid);
        if (selected != null) startAppListPoller(selected);
    }

    private FrameLayout buildUi() {
        portraitLayout = getResources().getDisplayMetrics().heightPixels
                > getResources().getDisplayMetrics().widthPixels;
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundResource(R.color.console_background);
        homeLayer = new FrameLayout(this);
        homeLayer.addView(new ConsoleBackdrop(this), match());

        artworkBackdrop = new ImageView(this);
        artworkBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkBackdrop.setAlpha(0f);
        homeLayer.addView(artworkBackdrop, match());

        artworkBackdropNext = new ImageView(this);
        artworkBackdropNext.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkBackdropNext.setAlpha(0f);
        homeLayer.addView(artworkBackdropNext, match());

        artworkHero = new ImageView(this);
        artworkHero.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artworkHero.setAlpha(0f);
        artworkHero.setPadding(dp(30), dp(56), dp(30), dp(56));
        if (CONSOLE_UI_V2) artworkHero.setVisibility(View.GONE);
        FrameLayout.LayoutParams hero = new FrameLayout.LayoutParams(dp(500),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL);
        hero.rightMargin = dp(16);
        homeLayer.addView(artworkHero, hero);

        artworkScrim = new View(this);
        artworkScrim.setBackground(artworkScrimDrawable(artworkScrimLuminance));
        artworkScrim.setAlpha(0f);
        homeLayer.addView(artworkScrim, match());

        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(dp(54), dp(14), dp(54), dp(27));
        homeContent.setClipChildren(false);
        homeContent.setClipToPadding(false);
        homeLayer.addView(homeContent, match());

        topBar = new ConsoleTopBar(this, () -> reducedMotion || suppressInitialCarouselMotion,
                () -> {
                    ComputerDetails host = hosts.get(selectedHostUuid);
                    if (host != null) showHostSelectionOptions(host);
                }, this::showDiscordPanel, this::showUsbMicrophonePanel,
                this::openUsbForwardingPanel, this::openLibrarySearch,
                () -> showUserMenu(hosts.get(selectedHostUuid)), () -> showSettings(0));
        homeContent.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ConsoleTopBar.HEIGHT_DP)));

        if (CONSOLE_UI_V2) {
            controllersLabel = sectionLabel("");
            controllerScroll = horizontalScroll();
            controllerScroll.setFillViewport(true);
            controllerRow = horizontalRow();
            controllerRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            controllerScroll.addView(controllerRow, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        quickResumeButton = text(getString(R.string.console_quick_resume),
                12, 0xFFE8F6FF, true);
        quickResumeButton.setId(View.generateViewId());
        quickResumeButton.setTag("session.resume");
        quickResumeButton.setFocusable(true);
        quickResumeButton.setClickable(true);
        quickResumeButton.setGravity(Gravity.CENTER);
        quickResumeButton.setMinHeight(dp(42));
        quickResumeButton.setPadding(dp(13), dp(5), dp(13), dp(5));
        quickResumeButton.setVisibility(View.GONE);
        quickResumeButton.setOnClickListener(view -> resumeSelectedSession());
        quickResumeButton.setOnFocusChangeListener((view, focused) ->
                styleCompactButton(quickResumeButton, focused));
        styleCompactButton(quickResumeButton, false);

        if (!CONSOLE_UI_V2) {
            controllersLabel = sectionLabel(getString(R.string.console_controllers_none));
            LinearLayout.LayoutParams section = wrapLinear();
            section.topMargin = dp(13);
            homeContent.addView(controllersLabel, section);
            controllerScroll = horizontalScroll();
            controllerRow = horizontalRow();
            controllerScroll.addView(controllerRow, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            homeContent.addView(controllerScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        }

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setOrientation(LinearLayout.HORIZONTAL);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        appsLabel = sectionLabel(getString(R.string.console_apps));
        libraryHeader.addView(appsLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        shownLibraryStatus = "";
        playniteLibraryStatus = text("", 11, 0xFF8790A8, false);
        playniteLibraryStatus.setPadding(dp(8), 0, dp(8), 0);
        libraryHeader.addView(playniteLibraryStatus, wrapLinear());
        if (!CONSOLE_UI_V2) {
            LinearLayout.LayoutParams libraryHeaderParams = sectionWithTop(2);
            libraryHeaderParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            homeContent.addView(libraryHeader, libraryHeaderParams);
        }
        appRow = horizontalRow();
        if (CONSOLE_UI_V2 && !portraitLayout) {
            appRow.setGravity(Gravity.TOP);
            carouselTransition = new LayoutTransition();
            carouselTransition.disableTransitionType(LayoutTransition.APPEARING);
            carouselTransition.disableTransitionType(LayoutTransition.DISAPPEARING);
            carouselTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
            carouselTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            carouselTransition.enableTransitionType(LayoutTransition.CHANGING);
            carouselTransition.setDuration(LayoutTransition.CHANGING,
                    getResources().getInteger(R.integer.console_motion_focus_ms));
            if (!requiresPreparedInitialCarouselFrame()) {
                appRow.setLayoutTransition(carouselTransition);
            }
        }
        carouselStage = CONSOLE_UI_V2 && !portraitLayout
                ? new FrameLayout(this) : null;
        if (carouselStage != null) {
            carouselStage.setClipChildren(false);
            carouselStage.setClipToPadding(false);
        }
        if (portraitLayout) {
            appRow.setOrientation(LinearLayout.VERTICAL);
            appVerticalScroll = new ScrollView(this);
            appVerticalScroll.setVerticalScrollBarEnabled(false);
            appVerticalScroll.addView(appRow, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            homeContent.addView(appVerticalScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(CONSOLE_UI_V2 ? 180 : 235)));
        } else {
            appScroll = horizontalScroll();
            if (carouselStage != null) {
                appScroll.setClipChildren(true);
                appScroll.setClipToPadding(true);
            }
            appScroll.addView(appRow, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (carouselStage != null) {
                appScroll.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                      oldLeft, oldTop, oldRight, oldBottom) -> {
                    view.setClipBounds(new Rect(0, 0,
                            Math.max(0, right - left), Math.max(0, bottom - top)));
                    int endPadding = Math.max(0, right - left - dp(92));
                    if (appRow.getPaddingRight() != endPadding) {
                        appRow.setPadding(0, 0, endPadding, 0);
                    }
                });
                appScroll.setOnScrollChangeListener((view, scrollX, scrollY,
                                                     oldScrollX, oldScrollY) -> {
                    View focus = getCurrentFocus();
                    if (focus != null && focus.getParent() == appRow) {
                        positionCarouselMetadata(focus);
                    }
                });
                FrameLayout.LayoutParams appScrollParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(149), Gravity.TOP);
                appScrollParams.topMargin = dp(8);
                carouselStage.addView(appScroll, appScrollParams);
                homeContent.addView(carouselStage, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));
            } else {
                homeContent.addView(appScroll, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(235)));
            }
        }
        appRow.addView(text(getString(R.string.console_choose_host), 15, 0xFFBDC4D8, false),
                new LinearLayout.LayoutParams(dp(500), ViewGroup.LayoutParams.MATCH_PARENT));
        if (CONSOLE_UI_V2) {
            debugLibraryActions = new LinearLayout(this);
            debugLibraryActions.setOrientation(LinearLayout.HORIZONTAL);
            debugLibraryActions.setGravity(Gravity.CENTER_VERTICAL);
            debugLibraryActions.setClipChildren(false);
            debugLibraryActions.setClipToPadding(false);
            LinearLayout.LayoutParams libraryActionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            libraryActionParams.rightMargin = dp(8);
            debugLibraryActions.addView(quickResumeButton, libraryActionParams);
            debugLibraryActions.addView(new View(this), new LinearLayout.LayoutParams(
                    0, 1, 1f));
            libraryHeader.removeView(playniteLibraryStatus);
            playniteLibraryStatus.setTextSize(11);
            playniteLibraryStatus.setMaxLines(2);
            playniteLibraryStatus.setMinHeight(dp(18));
            playniteLibraryStatus.setShadowLayer(dp(2), 0, dp(1), 0xE0000000);
            playniteLibraryStatus.setEllipsize(TextUtils.TruncateAt.END);
            playniteLibraryStatus.setGravity(Gravity.CENTER_VERTICAL);
            if (carouselStage != null) {
                // A fixed band under the cards, inside the carousel's own height: a notice
                // coming or going moves neither the cards nor the selected game's details.
                FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(32),
                        Gravity.BOTTOM | Gravity.START);
                carouselStage.addView(playniteLibraryStatus, statusParams);
            } else {
                // Portrait: its slot under the list is kept while no notice shows.
                homeContent.addView(playniteLibraryStatus, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
            }
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            actionParams.topMargin = dp(5);
            homeContent.addView(debugLibraryActions, actionParams);
            debugLibraryActions.setVisibility(View.GONE);

            selectedGameMetadata = new LinearLayout(this);
            selectedGameMetadata.setOrientation(LinearLayout.VERTICAL);
            selectedGameMetadata.setPadding(dp(portraitLayout ? 8 : 62),
                    dp(8), dp(12), dp(8));
            selectedGameMetadata.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x0004070B, 0x6004070B, 0x6004070B,
                            0x3404070B, 0x0004070B}));
            selectedGameTitleRow = horizontalRow();
            selectedGameTitleRow.setGravity(Gravity.CENTER_VERTICAL);
            selectedGameSource = new ImageView(this);
            selectedGameSource.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            selectedGameTitleRow.addView(selectedGameSource,
                    new LinearLayout.LayoutParams(dp(22), dp(22)));
            selectedGameTitle = text("", 19, Color.WHITE, true);
            selectedGameTitle.setSingleLine(true);
            selectedGameTitle.setEllipsize(TextUtils.TruncateAt.END);
            selectedGameTitle.setShadowLayer(dp(2), 0, dp(1), 0xE0000000);
            LinearLayout.LayoutParams selectedTitleParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            selectedTitleParams.leftMargin = dp(CAROUSEL_CARD_GAP_DP);
            selectedGameTitleRow.addView(selectedGameTitle, selectedTitleParams);
            selectedGameTitleRow.setVisibility(View.INVISIBLE);
            selectedGameFacts = text("", 11, 0xFFB8C9DC, false);
            selectedGameFacts.setVisibility(View.GONE);
            selectedGameFactsRow = horizontalRow();
            selectedGameFactsRow.setGravity(Gravity.CENTER_VERTICAL);
            shownSelectedGameBadge = null;
            selectedGameStatusPill = horizontalRow();
            selectedGameStatusPill.setGravity(Gravity.CENTER_VERTICAL);
            selectedGameStatusPill.setPadding(dp(8), 0, dp(8), 0);
            selectedGameStatusPill.setVisibility(View.GONE);
            selectedGameStatusIcon = new ImageView(this);
            selectedGameStatusPill.addView(selectedGameStatusIcon,
                    new LinearLayout.LayoutParams(dp(13), dp(13)));
            selectedGameStatusText = text("", 11, 0xFFF0F5F8, true);
            selectedGameStatusText.setSingleLine(true);
            selectedGameStatusText.setFontFeatureSettings("tnum");
            LinearLayout.LayoutParams statusTextParams = wrapLinear();
            statusTextParams.leftMargin = dp(5);
            selectedGameStatusPill.addView(selectedGameStatusText, statusTextParams);
            selectedGameStatusBar = new ConsoleTileProgressBar(this);
            LinearLayout.LayoutParams statusBarParams =
                    new LinearLayout.LayoutParams(dp(40), dp(4));
            statusBarParams.leftMargin = dp(5);
            selectedGameStatusPill.addView(selectedGameStatusBar, statusBarParams);
            LinearLayout.LayoutParams statusPillParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
            statusPillParams.rightMargin = dp(12);
            selectedGameFactsRow.addView(selectedGameStatusPill, statusPillParams);
            selectedGameLastPlayedPill = metadataPill();
            selectedGamePlaytimePill = metadataPill();
            selectedGamePlaytimePill.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_console_clock, 0, 0, 0);
            selectedGamePlaytimePill.setCompoundDrawablePadding(dp(5));
            // The row measures its pills left to right and squeezes the last: the rating, whose
            // badge must stay whole ("ESRB Everyone 10+"), comes before the pills that may
            // shorten with an ellipsis.
            selectedGameRatingPill = metadataPill();
            LinearLayout.LayoutParams ratingPillParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
            ratingPillParams.rightMargin = dp(12);
            selectedGameFactsRow.addView(selectedGameRatingPill, ratingPillParams);
            selectedGameLastPlayedPill.setEllipsize(TextUtils.TruncateAt.END);
            selectedGamePlaytimePill.setEllipsize(TextUtils.TruncateAt.END);
            selectedGameFactsRow.addView(selectedGameLastPlayedPill,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)));
            LinearLayout.LayoutParams playtimePillParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
            playtimePillParams.leftMargin = dp(12);
            selectedGameFactsRow.addView(selectedGamePlaytimePill, playtimePillParams);
            selectedGameDescription = text("", 14, 0xFFD2D9E2, false);
            selectedGameDescription.setShadowLayer(dp(2), 0, dp(1), 0xE0000000);
            selectedGameDescription.setLineSpacing(0, 1.25f);
            selectedGameDescription.setMaxLines(7);
            selectedGameDescription.setEllipsize(TextUtils.TruncateAt.END);
            selectedGameDescription.setVisibility(
                    showCarouselGameDescription ? View.VISIBLE : View.GONE);
            selectedGameMetadata.addView(selectedGameFactsRow, matchLinearWidth());
            selectedGameMetadata.addView(selectedGameFacts, matchLinearWidth());
            LinearLayout.LayoutParams descriptionParams = matchLinearWidth();
            descriptionParams.topMargin = dp(8);
            selectedGameMetadata.addView(selectedGameDescription, descriptionParams);
            selectedGameMetadata.setVisibility(View.INVISIBLE);
            if (carouselStage != null) {
                FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                        dp(430), dp(28),
                        Gravity.TOP | Gravity.START);
                titleParams.topMargin = dp(103);
                // Placed by translation (positionCarouselMetadata): moving it lays nothing out.
                selectedGameTitleRow.setTranslationX(dp(112));
                carouselStage.addView(selectedGameTitleRow, titleParams);
            } else {
                selectedGameMetadata.addView(selectedGameTitleRow, 0, matchLinearWidth());
            }
            LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                    portraitLayout ? ViewGroup.LayoutParams.MATCH_PARENT : dp(484),
                    dp(showCarouselGameDescription ? 196 : 36));
            if (!portraitLayout) metadataParams.leftMargin = -dp(54);
            metadataParams.topMargin = dp(4);
            homeContent.addView(selectedGameMetadata, metadataParams);

            debugLibrarySpacer = new View(this);
            homeContent.addView(debugLibrarySpacer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            LinearLayout.LayoutParams controllerStrip = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
            homeContent.addView(controllerScroll, controllerStrip);

            buildExpandedLibrary();
            homeContent.addView(expandedLibrary, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        wireHomeFocusNavigation();

        container.addView(homeLayer, match());
        discordDmToastView = new DiscordDmToastView(this);
        FrameLayout.LayoutParams toastParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        toastParams.setMargins(dp(18), dp(18), dp(18), 0);
        container.addView(discordDmToastView, toastParams);
        if (isStreamHome()) deferSecondaryLayersAfterFirstLayout(container);
        else buildSecondaryLayers(container);
        return container;
    }

    private void deferSecondaryLayersAfterFirstLayout(FrameLayout container) {
        ViewTreeObserver.OnGlobalLayoutListener listener =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() {
                        if (container.getViewTreeObserver().isAlive()) {
                            container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            buildSecondaryLayers(container);
                            homeLayer.setVisibility(View.VISIBLE);
                            entryShell.setVisibility(View.GONE);
                        });
                    }
                };
        container.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    private void buildSecondaryLayers(FrameLayout container) {
        buildHostSelectionLayer(container);
        profileSelectView = new ConsoleProfileSelectView(this, entryShell);
        pinEntryView = new ConsolePinEntryView(this, entryShell, profileSelectView);
        if (!isStreamHome()) container.addView(introView = new ConsoleIntroView(this, entryShell,
                profileSelectView, pinEntryView, homeLayer, appRow, () -> reducedMotion,
                () -> initialLocalPresentationReady(selectedHostUuid, initialLocalAppsHostId,
                initialLocalLibraryHostId), loadingPosterTags, tag -> pendingArtworkKey == null
                && loadingArtworkKey == null
                && (displayedArtworkKey != null || settledBackdropTag.equals(tag))), match());
        if (introView != null) {
            // A host prepared under the intro opens its stream screen once the intro is over;
            // posted, so that an ending by onPause finds its preparation cancelled.
            introView.whenStarted(() -> {
                if (consoleAudioEngine != null) consoleAudioEngine.playIntro();
            });
            introView.whenEnded(() -> mainHandler.post(() -> {
                if (sessionOrchestrator != null) sessionOrchestrator.openPreparedHost();
            }));
            introView.carouselCards(() -> firstPendingCarouselCard() != null, () -> {
                View pending = firstPendingCarouselCard();
                if (pending == null) return;
                buildPendingCarouselCard(pending);
                requestPendingInitialGameFocus(currentHost(selectedHostUuid));
            });
        }
        buildSidePanel();
        buildLoadingLayer(container);
        buildScreenSaverLayer(container);
        discordDmToastView.bringToFront();
    }

    private void buildScreenSaverLayer(FrameLayout container) {
        screenSaverLayer = new FrameLayout(this);
        screenSaverLayer.setVisibility(View.GONE);
        screenSaverLayer.setBackgroundColor(Color.BLACK);
        screenSaverLayer.setFocusable(true);
        screenSaverLayer.setClickable(true);

        screenSaverArtwork = screenSaverImage();
        screenSaverArtworkNext = screenSaverImage();
        screenSaverLayer.addView(screenSaverArtwork, match());
        screenSaverLayer.addView(screenSaverArtworkNext, match());

        View captionScrim = new View(this);
        captionScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xC0000000}));
        FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190), Gravity.BOTTOM);
        screenSaverLayer.addView(captionScrim, scrimParams);

        screenSaverCaption = text("", 13, Color.WHITE, false);
        screenSaverCaption.setGravity(Gravity.END);
        screenSaverCaption.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        captionParams.rightMargin = dp(42);
        captionParams.bottomMargin = dp(32);
        screenSaverLayer.addView(screenSaverCaption, captionParams);

        container.addView(screenSaverLayer, match());
    }

    private ImageView screenSaverImage() {
        ImageView image = new ArtworkImageView(this);
        image.setAlpha(0f);
        return image;
    }

    private void buildHostSelectionLayer(FrameLayout container) {
        entryShell = new ConsoleEntryShell(this);
        entryShell.setReducedMotion(reducedMotion);
        hostSelectView = new ConsoleHostSelectView(this, entryShell, HOST_WAKING_TIMEOUT_MS,
                new ConsoleHostSelectView.Listener() {
                    @Override public void onHostSelected(String uuid) {
                        activateHostFromSelection(uuid);
                    }

                    @Override public void onHostOptions(String uuid) {
                        ComputerDetails host = hosts.get(uuid);
                        if (host != null) showHostSelectionOptions(host);
                    }

                    @Override public void onAddHost() {
                        FirstRunWizard wizard = firstRunWizard();
                        if (wizard != null) wizard.open(FirstRunPlan.Entry.ADD_HOST, null);
                    }
                });
        hostSelectView.setReducedMotion(reducedMotion);
        homeLayer.setVisibility(View.GONE);
        container.addView(entryShell, match());
        entryShell.setOnVisibilityChange(this::updateContentLayerAccess);
        updateContentLayerAccess();
        renderHostSelection();
    }

    private void resolveInitialHostSelection() {
        if (!active || initialHostSelectionResolved) return;
        initialHostSelectionResolved = true;
        if (isStreamHome()) {
            ComputerDetails retainedHost = hosts.get(
                    launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID));
            if (retainedHost != null) {
                selectHost(retainedHost, true);
                return;
            }
        }
        // The wizard opened in onCreate: the hosts it reads are here now.
        if (firstRunWizard != null && firstRunWizard.isShowing()) {
            restoredEnteredHost = "";
            reopenAppearance = false;
            firstRunWizard.refresh();
            return;
        }
        // A recreation (a language change) keeps the user on the host and profile they had
        // entered: the grant is process-wide, so the gate was already passed.
        ComputerDetails entered = hosts.get(restoredEnteredHost);
        boolean reopenSettings = reopenAppearance;
        restoredEnteredHost = "";
        reopenAppearance = false;
        if (entered != null && ProfileAuthorization.allows(entered.uuid)) {
            selectHost(entered, true);
            if (reopenSettings) mainHandler.post(() -> {
                if (!isFinishing() && !hostSelectionVisible) showSettings(SETTINGS_APPEARANCE);
            });
            return;
        }
        PlayIntent waiting = sessionOrchestrator == null ? null : sessionOrchestrator.queued();
        ComputerDetails queuedHost = waiting == null ? null : currentHost(waiting.hostId);
        if (queuedHost != null && ProfileAuthorization.allows(queuedHost.uuid)
                && waiting.profileId.equals(selectedProfileId(queuedHost.uuid))) {
            selectHost(queuedHost, true);
            return;
        }
        ComputerDetails tileHost = returnRequests.watchNextSessionId().isEmpty() ? null
                : hosts.get(sessionModel.snapshot().hostId);
        if (tileHost != null) {
            // The tile's stream: its host's normal profile gate, without a warm-up.
            resolveProfileGate(tileHost, false, false);
            return;
        }
        ComputerDetails automatic = hosts.get(autoLoginHostUuid);
        if (automatic != null) {
            resolveProfileGate(automatic, false, true);
        } else {
            showHostSelection(selectedHostUuid);
        }
    }

    /** Leaving the host or its profile drops a Play queued behind a close. */
    private void cancelQueuedPlay() {
        if (sessionOrchestrator != null) sessionOrchestrator.cancelQueued();
        endQueuedCover();
    }

    /**
     * The queued Play's launch cover, from the press on: the next game's title and artwork,
     * waiting for the closing game; Back on it cancels the queued Play. Its start continues
     * on this same cover (Effects.showLoading).
     */
    private void showQueuedCover(PlayIntent waiting) {
        launchCoverGeneration++;
        ComputerDetails host = currentHost(waiting.hostId);
        String artwork = coverArtworkPath(waiting.hostId, waiting.profileId,
                waiting.loadingArtworkGameId, "launch");
        if (streamLoadingView == null || loadingLayer.getVisibility() != View.VISIBLE) {
            showLoading(host == null ? waiting.hostId : host.name, waiting.appName, artwork);
        } else {
            streamLoadingView.setTitle(appDisplayName(waiting.appName));
            streamLoadingView.setSplashArtwork(artwork);
        }
        queuedCoverIntent = waiting;
        SessionSnapshot closing = projectSession(host, waiting.profileId, true);
        String closingName = closing == null ? null : libraryGameName(closing.playniteGameId);
        streamLoadingView.setStep(1, closingName == null
                ? getString(R.string.console_queued_waiting_session)
                : getString(R.string.console_queued_waiting_close, closingName));
    }

    /** The queued Play ended without starting: its cover gives way to the Home. */
    private void endQueuedCover() {
        if (queuedCoverIntent == null) return;
        queuedCoverIntent = null;
        if (streamLoadingView != null) streamLoadingView.stopAndHide();
        showHome();
    }

    /** The tiles of the selected host render the queued Play (or its absence) again. */
    private void renderQueue() {
        ComputerDetails host = currentHost(selectedHostUuid);
        if (host != null) renderLibraryForSessionChange(host);
    }

    private void showHostSelection(String focusUuid) {
        pendingHostPreparation = null;
        cancelQueuedPlay();
        cancelOfflinePinAttempt();
        profileGateGeneration.incrementAndGet();
        clearPinEntry();
        profileGateHostUuid = null;
        hostSelectionVisible = true;
        if (consoleAudioEngine != null) consoleAudioEngine.setHostSelectionVisible(true);
        if (hostSelectView != null) hostSelectView.setFocusTarget(focusUuid);
        hostEntryAttempt.end();
        stopAppListPoller();
        cancelPlayniteArtworkPrefetch();
        if (loadingLayer != null) loadingLayer.setVisibility(View.GONE);
        if (discordDmNotifications != null) {
            discordDmNotifications.setPresentationBlocked(discordDmHostToken, false);
        }
        if (homeLayer != null) homeLayer.setVisibility(View.GONE);
        if (hostSelectView != null) hostSelectView.show();
        renderHostSelection();
        refreshVisibleHostProfiles();
        requestHostSelectionFocus();
    }

    private void renderHostSelection() {
        if (hostSelectView == null) return;
        List<ComputerDetails> sorted = new ArrayList<>(hosts.values());
        sorted.removeIf(host -> newlyDiscoveredHosts.contains(host.uuid));
        sorted.sort(Comparator.comparing(host -> host.name, String.CASE_INSENSITIVE_ORDER));
        List<ConsoleHostSelectView.Tile> tiles = new ArrayList<>();
        for (ComputerDetails host : sorted) tiles.add(hostSelectionTile(host));
        if (profileSelectView != null) profileSelectView.updateHost(tiles);
        if (hostSelectView.render(tiles, polling) && hostSelectionVisible) {
            requestHostSelectionFocus();
        }
    }

    private ConsoleHostSelectView.Tile hostSelectionTile(ComputerDetails host) {
        SessionSnapshot snapshot = renderedSessionSnapshot(host);
        return new ConsoleHostSelectView.Tile(host.uuid, host.name,
                consoleHostState(host, snapshot), consoleProfile(host).state,
                hostStatus(host, snapshot), hostStateController.wakingSince(host.uuid));
    }

    private void updateHostSelectionTile(ComputerDetails host) {
        if (host != null && hostSelectView != null && hostSelectView.shows(host.uuid)) {
            hostSelectView.update(hostSelectionTile(host));
        }
    }

    private void requestHostSelectionFocus() {
        if (firstRunWizard != null && firstRunWizard.isShowing()) return;
        if (hostSelectionVisible && hostSelectView != null) {
            hostSelectView.requestTargetFocus(selectedHostUuid);
        }
    }

    private void activateHostFromSelection(String uuid) {
        ComputerDetails host = hosts.get(uuid);
        if (host == null) return;
        // OK frames the tile at once; a host not polled yet decides here when its poll answers.
        if (hostStateController.state(host) == ConsoleHostPresentation.State.CHECKING) {
            hostEntryAttempt.awaitPoll(uuid);
        } else if (!ConsoleActionCatalog.isRememberedPaired(host)) {
            hostEntryAttempt.cancel();
            pairHost(host);
        } else {
            hostEntryAttempt.press(uuid);
            resolveProfileGate(host, true, true);
        }
    }

    private void resolveProfileGate(ComputerDetails host, boolean focusApps,
                                    boolean prepareHost) {
        int token = host == null ? -1 : hostEntryAttempt.start(host.uuid);
        if (token < 0) return; // No host, or OK joins the host's running profile fetch.
        profileGateHostUuid = host.uuid;
        stopAppListPoller();
        cancelPlayniteRequest();
        mainHandler.removeCallbacks(playniteRefreshCycle);
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid, address);
        if (connection == null) {
            resolveProfileGate(host, focusApps, prepareHost, token);
            return;
        }
        executor.execute(() -> {
            HostGatewayClient.IntegrationProfiles profiles = null;
            try {
                profiles = hostGatewayClient.getIntegrationProfiles(connection,
                        HostGatewayClient.READ_TIMEOUT_MS);
            } catch (IOException | RuntimeException unavailable) {
                // The last authorized profile cache remains usable while Gateway is unavailable.
            }
            HostGatewayClient.IntegrationProfiles result = profiles;
            mainHandler.post(() -> {
                // A superseded fetch leaves the tile's pressed state to the newer attempt.
                if (!hostEntryAttempt.finish(token)) return;
                ComputerDetails current = currentHost(host.uuid);
                if (current == null) return; // The host was removed; finish ended the frame.
                boolean childWasInvalidated = result != null
                        && selectedChildWasInvalidated(host.uuid, result);
                if (result != null) saveHostProfiles(host.uuid, result);
                if (childWasInvalidated) {
                    hostGatewayStore.setAutomaticIntegrationProfileId(host.uuid, "");
                    showProfileGate(current, focusApps, prepareHost);
                    return;
                }
                resolveProfileGate(current, focusApps, prepareHost, token);
            });
        });
    }

    private void resolveProfileGate(ComputerDetails host, boolean focusApps,
                                    boolean prepareHost, int token) {
        if (!hostEntryAttempt.finish(token)) return;
        HostGatewayStore.ProfileGate gate = hostGatewayStore.profileGate(host.uuid);
        if (gate.clearAutomatic) hostGatewayStore.setAutomaticIntegrationProfileId(host.uuid, "");
        if (gate.showGate) {
            showProfileGate(host, focusApps, prepareHost);
            return;
        }
        if (gate.profile != null) {
            if (gate.profile.pinRequired) {
                showPinEntry(host, gate.profile, focusApps, prepareHost, true);
                return;
            }
            if (introView != null) introView.start(gate.profile, host.uuid,
                    ConsoleIntroView.Entry.AUTOMATIC);
            changeSelectedProfile(host, gate.profile.id);
        }
        enterHostAfterProfileGate(host, focusApps, prepareHost);
    }

    private void showProfileGate(ComputerDetails host, boolean focusApps,
                                 boolean prepareHost) {
        cancelOfflinePinAttempt();
        clearPinEntry();
        HostGatewayStore.ProfileSelection selection =
                hostGatewayStore.profileSelection(host.uuid);
        profileGateHostUuid = host.uuid;
        hostSelectionVisible = false;
        hostSelectView.setFocusTarget(host.uuid);
        if (homeLayer != null) homeLayer.setVisibility(View.GONE);
        List<ControllerInfo> controllers = loadControllers();
        profileSelectView.setControllers(!controllers.isEmpty(),
                usesPlayStationButtons(controllers));
        profileAvatars.load(hostGatewayStore.loadForHost(host.uuid, host.activeAddress == null
                ? null : host.activeAddress.address), host.uuid, ProfileAvatars.ids(selection.profiles));
        // The profile row cross-fades in above the shell, whose background stays.
        profileSelectView.show(hostSelectionTile(host), selection.profiles,
                hostGatewayStore.automaticIntegrationProfileId(host.uuid), reducedMotion,
                new ConsoleProfileSelectView.Listener() {
                    @Override public void onProfileSelected(String profileId) {
                        if (!host.uuid.equals(profileGateHostUuid)) return;
                        HostGatewayClient.IntegrationProfile profile =
                                hostGatewayStore.profileSelection(host.uuid).find(profileId);
                        if (profile == null) return;
                        if (profile.pinRequired) {
                            showPinEntry(host, profile, focusApps, prepareHost, true);
                        } else {
                            if (introView != null) introView.start(profile, host.uuid,
                                    ConsoleIntroView.Entry.PROFILE);
                            changeSelectedProfile(host, profileId);
                            enterHostAfterProfileGate(
                                    currentHost(host.uuid), focusApps, prepareHost);
                        }
                    }

                    @Override public void onProfileFocused(String profileId) {
                        if (host.uuid.equals(profileGateHostUuid)) {
                            readLibraryAhead(host, profileId);
                        }
                    }

                    @Override public void onAutomaticProfileToggled(String profileId) {
                        if (!host.uuid.equals(profileGateHostUuid)) return;
                        HostGatewayClient.IntegrationProfile profile =
                                hostGatewayStore.profileSelection(host.uuid).find(profileId);
                        if (profile == null || profile.pinRequired) return;
                        String automatic = hostGatewayStore.automaticIntegrationProfileId(
                                host.uuid);
                        hostGatewayStore.setAutomaticIntegrationProfileId(host.uuid,
                                profileId.equals(automatic) ? "" : profileId);
                        showProfileGate(host, focusApps, prepareHost);
                    }

                    @Override public void onAddProfile(String profileId) {
                        if (!host.uuid.equals(profileGateHostUuid)) return;
                        HostGatewayClient.IntegrationProfile actor =
                                hostGatewayStore.profileSelection(host.uuid).find(profileId);
                        if (actor == null) return;
                        startAdultProfileCreation(host, actor, created -> {
                            ComputerDetails current = currentHost(host.uuid);
                            if (current == null) return;
                            // A new profile is fetched before the gate shows it.
                            if (created != null) resolveProfileGate(current, focusApps, prepareHost);
                            else showProfileGate(current, focusApps, prepareHost);
                        });
                    }

                    @Override public void onChangeHost() {
                        closeProfileGate();
                    }
                });
    }

    private static String childProfileMetadataKey(String hostId, String profileId) {
        return (hostId == null ? "" : hostId) + "\n"
                + (profileId == null ? "" : profileId);
    }

    private void rememberChildCapability(String hostId,
                                         HostGatewayClient.IntegrationProfiles profiles,
                                         HostGatewayClient.Capabilities capabilities) {
        if (hostId == null || profiles == null) return;
        for (HostGatewayClient.IntegrationProfile profile : profiles.profiles) {
            if (profile == null) continue;
            String key = childProfileMetadataKey(hostId, profile.id);
            if (capabilities == null) childCapabilityByProfile.remove(key);
            else childCapabilityByProfile.put(key, capabilities.childProfiles);
        }
    }

    private boolean childManagementProfileAllowed(ComputerDetails host,
                                                   HostGatewayClient.IntegrationProfile profile) {
        if (host == null || profile == null || !profile.useProfile) return false;
        return profile.manageChildren;
    }

    private boolean childCapabilityAllowed(ComputerDetails host,
                                           HostGatewayClient.IntegrationProfile profile) {
        return host != null && profile != null
                && Boolean.TRUE.equals(childCapabilityByProfile.get(
                childProfileMetadataKey(host.uuid, profile.id)));
    }

    private boolean hasOwnChildren(ComputerDetails host,
                                    HostGatewayClient.IntegrationProfile profile) {
        if (host == null || profile == null) return false;
        return profile.ownChildrenCount > 0;
    }

    private HostGatewayClient.IntegrationProfile integrationProfile(
            String hostId, String profileId) {
        if (hostId == null || profileId == null || hostGatewayStore == null) return null;
        HostGatewayClient.IntegrationProfiles profiles = hostGatewayStore.profiles(hostId);
        return profiles == null ? null : profiles.find(profileId);
    }

    private ChildEligibilityProjection childEligibilityProjection(
            String hostId, String profileId) {
        HostGatewayClient.IntegrationProfile profile = integrationProfile(hostId, profileId);
        if (profile == null || !profile.isChild()) return ChildEligibilityProjection.ADULT;
        Long observedAt = childEligibilityObservedAt.get(hostId + "\n" + profileId);
        long now = System.currentTimeMillis();
        long server = parseChildPolicyTime(profile.serverTime);
        if (server > 0L && observedAt != null) {
            now = server + Math.max(0L, SystemClock.elapsedRealtime() - observedAt);
        }
        String reason = childEligibilityBlockReason(profile, now, 0L);
        return new ChildEligibilityProjection(true, !reason.isEmpty(), reason);
    }

    private static boolean isAllowedChildEligibilityReason(String reason) {
        String value = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() || "none".equals(value);
    }

    static String childEligibilityBlockReason(HostGatewayClient.IntegrationProfile profile,
                                              long nowMs, long observedAtMs) {
        if (profile == null || !profile.isChild()) return "";
        if (!profile.enabled || !profile.useProfile) return "child_profile_unavailable";
        String reason = profile.reason == null ? "" : profile.reason.trim();
        if (!reason.isEmpty() && !isAllowedChildEligibilityReason(reason)) return reason;
        long server = parseChildPolicyTime(profile.serverTime);
        long effectiveServer = nowMs;
        if (server > 0L && observedAtMs > 0L && nowMs >= observedAtMs) {
            effectiveServer = server + (nowMs - observedAtMs);
        }
        long windowEnd = parseChildPolicyTime(profile.windowEnd);
        if (windowEnd > 0L && effectiveServer >= windowEnd) return "outside_schedule";
        boolean hasPolicyProjection = profile.policyRevision > 0L
                || profile.usageRevision > 0L
                || profile.playableNowSeconds > 0L
                || profile.remainingDailySeconds > 0L
                || !profile.nextAllowedAt.isEmpty()
                || !profile.windowEnd.isEmpty();
        if (hasPolicyProjection && profile.playableNowSeconds <= 0L) {
            if (!reason.isEmpty() && !"none".equalsIgnoreCase(reason)) return reason;
            return !profile.nextAllowedAt.isEmpty()
                    ? "outside_schedule" : "daily_limit_reached";
        }
        return "";
    }

    public static boolean childEligibilityCanLaunch(boolean ok, String state, String reason,
                                             long playableNowSeconds) {
        return ok && playableNowSeconds > 0L
                && "ready".equalsIgnoreCase(state)
                && isAllowedChildEligibilityReason(reason);
    }

    static boolean childSessionCanContinue(boolean ok, String state, String reason,
                                           long playableNowSeconds) {
        String normalizedState = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        boolean launchState = "running".equals(normalizedState)
                || "launch_pending".equals(normalizedState);
        return ok && launchState && playableNowSeconds > 0L
                && isAllowedChildEligibilityReason(reason);
    }

    private static final Pattern CHILD_POLICY_COLON_OFFSET =
            Pattern.compile(".*[+-]\\d{2}:\\d{2}$");
    /** Guarded by itself: a SimpleDateFormat is not thread-safe. */
    private static final SimpleDateFormat[] CHILD_POLICY_TIME_FORMATS = {
            childPolicyTimeFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
            childPolicyTimeFormat("yyyy-MM-dd'T'HH:mm:ssZ"),
            childPolicyTimeFormat("yyyy-MM-dd HH:mm:ssZ")};

    private static SimpleDateFormat childPolicyTimeFormat(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setLenient(false);
        return format;
    }

    static long parseChildPolicyTime(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return -1L;
        if (text.endsWith("Z")) text = text.substring(0, text.length() - 1) + "+0000";
        else if (CHILD_POLICY_COLON_OFFSET.matcher(text).matches()) {
            text = text.substring(0, text.length() - 3) + text.substring(text.length() - 2);
        }
        int fractionStart = text.indexOf('.');
        if (fractionStart >= 0) {
            int fractionEnd = fractionStart + 1;
            while (fractionEnd < text.length()
                    && Character.isDigit(text.charAt(fractionEnd))) fractionEnd++;
            String fraction = text.substring(fractionStart + 1, fractionEnd);
            if (fraction.length() > 3) fraction = fraction.substring(0, 3);
            while (fraction.length() < 3) fraction += "0";
            text = text.substring(0, fractionStart + 1) + fraction
                    + text.substring(fractionEnd);
        }
        synchronized (CHILD_POLICY_TIME_FORMATS) {
            for (SimpleDateFormat format : CHILD_POLICY_TIME_FORMATS) {
                try {
                    ParsePosition position = new ParsePosition(0);
                    Date parsed = format.parse(text, position);
                    if (parsed != null && position.getIndex() == text.length()) {
                        return parsed.getTime();
                    }
                } catch (RuntimeException ignored) {
                    // Try the next server timestamp shape.
                }
            }
        }
        return -1L;
    }

    private void scheduleChildEligibilityBoundary(ComputerDetails host) {
        mainHandler.removeCallbacks(childEligibilityBoundary);
        if (host == null || !host.uuid.equals(selectedHostUuid)) return;
        HostGatewayClient.IntegrationProfile profile = integrationProfile(
                host.uuid, selectedProfileId(host.uuid));
        if (profile == null || !profile.isChild()) return;
        long server = parseChildPolicyTime(profile.serverTime);
        long boundary = Long.MAX_VALUE;
        long nextAllowed = parseChildPolicyTime(profile.nextAllowedAt);
        long windowEnd = parseChildPolicyTime(profile.windowEnd);
        if (nextAllowed > 0L) boundary = Math.min(boundary, nextAllowed);
        if (windowEnd > 0L) boundary = Math.min(boundary, windowEnd);
        if (boundary == Long.MAX_VALUE) return;
        long delay = server > 0L ? boundary - server + 1_000L
                : boundary - System.currentTimeMillis() + 1_000L;
        if (delay <= 0L) delay = 5_000L;
        mainHandler.postDelayed(childEligibilityBoundary,
                Math.min(delay, 24L * 60L * 60L * 1_000L));
    }

    private void refreshChildEligibilityProfile(ComputerDetails host) {
        if (!active || host == null || !host.uuid.equals(selectedHostUuid)) return;
        String profileId = selectedProfileId(host.uuid);
        HostGatewayClient.IntegrationProfile profile = integrationProfile(host.uuid, profileId);
        if (profile == null || !profile.isChild()) {
            scheduleChildEligibilityBoundary(host);
            return;
        }
        GatewayConnection connection = childConnection(host, profileId);
        if (connection == null) return;
        String key = "profile:" + host.uuid + "\n" + profileId;
        if (!childEligibilityProfileInFlight.add(key)) return;
        String expectedHostId = host.uuid;
        String expectedProfileId = profileId;
        int expectedProfileGeneration = profileGeneration.get();
        executor.execute(() -> {
            HostGatewayClient.IntegrationProfiles profiles = null;
            try {
                profiles = hostGatewayClient.getIntegrationProfiles(connection);
            } catch (IOException | RuntimeException ignored) {
                // Keep the last policy projection; a click performs a fresh gate.
            }
            HostGatewayClient.IntegrationProfiles result = profiles;
            mainHandler.post(() -> {
                childEligibilityProfileInFlight.remove(key);
                if (result == null || !active || !expectedHostId.equals(selectedHostUuid)
                        || !expectedProfileId.equals(selectedProfileId(expectedHostId))
                        || expectedProfileGeneration != profileGeneration.get()) return;
                ComputerDetails current = currentHost(expectedHostId);
                if (current == null) return;
                GatewayConnection currentConnection = childConnection(current, expectedProfileId);
                if (!sameGatewayProfile(connection, currentConnection)
                        || !connection.token().equals(currentConnection.token())) return;
                childEligibilityObservedAt.put(expectedHostId + "\n" + expectedProfileId,
                        SystemClock.elapsedRealtime());
                saveHostProfiles(expectedHostId, result);
                HostGatewayClient.IntegrationProfile refreshedProfile =
                        result.find(expectedProfileId);
                if (refreshedProfile != null && refreshedProfile.isChild()
                        && "pairing_required".equalsIgnoreCase(refreshedProfile.reason)) {
                    tryBindExistingVibepolloIdentity(expectedHostId, expectedProfileId, false);
                } else {
                    clearIdentityBindingAttempt(expectedHostId, expectedProfileId,
                            expectedProfileGeneration, connection);
                }
                scheduleChildEligibilityBoundary(current);
                if (!currentPlayniteGames.isEmpty()) {
                    renderPlayniteLibrary(current, currentSunshineApps);
                }
            });
        });
    }

    private boolean selectedProfileIsChild(String hostId) {
        HostGatewayClient.IntegrationProfile profile = integrationProfile(
                hostId, selectedProfileId(hostId));
        return profile != null && profile.isChild();
    }

    private boolean discordAllowedForSelectedProfile() {
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null || hostGatewayStore == null) return true;
        HostGatewayStore.ProfileSelection selection = hostGatewayStore.profileSelection(host.uuid);
        return selection == null || selection.selected == null || !selection.selected.isChild();
    }

    private boolean microphoneAllowedForSelectedProfile() {
        return discordAllowedForSelectedProfile();
    }

    private boolean discordPanelVisible() {
        return "discord.community".equals(currentPanelKey)
                || (currentPanelKey != null
                && currentPanelKey.startsWith(getString(R.string.discord_panel_title)));
    }

    private void refreshDiscordAccess() {
        if (discordAllowedForSelectedProfile()) {
            if (active && discordDmNotifications != null) {
                discordDmNotifications.activateHost(discordDmHostToken);
                discordDmNotifications.setWindowFocused(discordDmHostToken,
                        getWindow().getDecorView().hasWindowFocus());
            }
            if (active && discordSocialPanelController != null) {
                discordSocialPanelController.onActivityResumed();
            }
            return;
        }
        if (discordDmShortcut != null) discordDmShortcut.cancel();
        if (discordDmNotifications != null) {
            discordDmNotifications.deactivateHost(discordDmHostToken);
        }
        setDiscordNotificationPending(false);
        if (discordSocialPanelController != null) discordSocialPanelController.closePanel();
        if (discordPanelVisible()) hideSidePanelImmediately();
    }

    private String executionProfileId(String hostId, String actorProfileId) {
        HostGatewayClient.IntegrationProfile actor = integrationProfile(hostId, actorProfileId);
        return actor != null && actor.isChild()
                ? GatewayConnection.normalizeProfileId(actor.executionProfileId)
                : GatewayConnection.normalizeProfileId(actorProfileId);
    }

    private PlayniteTransitionGateway.ActorContext gatewayActorContext(
            String hostId, String actorProfileId) {
        HostGatewayClient.IntegrationProfile actor =
                integrationProfile(hostId, actorProfileId);
        return actor != null && actor.isChild()
                ? PlayniteTransitionGateway.ActorContext.child(
                actorProfileId, actor.executionProfileId)
                : PlayniteTransitionGateway.ActorContext.parent(actorProfileId);
    }

    private boolean isChildIntent(PlayIntent intent) {
        return intent != null && intent.kind == PlayIntent.Kind.PLAYNITE_GAME
                && intent.profileId.equals(selectedProfileId(intent.hostId))
                && integrationProfile(intent.hostId, intent.profileId) != null
                && integrationProfile(intent.hostId, intent.profileId).isChild();
    }

    private boolean isCurrentChildIntentContext(PlayIntent intent) {
        return active && isChildIntent(intent)
                && intent.hostId.equals(selectedHostUuid)
                && currentHost(intent.hostId) != null;
    }

    private static String childEligibilityErrorReason(IOException error) {
        if (error instanceof GatewayTransport.GatewayException) {
            GatewayTransport.GatewayException gateway =
                    (GatewayTransport.GatewayException) error;
            String reason = gateway.reason();
            if (reason != null && !reason.trim().isEmpty()) return reason.trim();
        }
        String message = error == null || error.getMessage() == null
                ? "" : error.getMessage().trim();
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("pairing_required")) return "pairing_required";
        if (normalized.contains("session_in_use")) return "session_in_use";
        if (normalized.contains("cleanup_required")) return "cleanup_required";
        if (normalized.contains("parent_unavailable")) return "parent_unavailable";
        return "policy_unavailable";
    }

    private IdentityBindingStart tryBindExistingVibepolloIdentity(String hostId, String profileId,
                                                                  boolean allowRetry) {
        ComputerDetails host = currentHost(hostId);
        if (host == null) return IdentityBindingStart.CONTEXT_UNAVAILABLE;
        GatewayConnection connection = childConnection(host, profileId);
        if (connection == null) return IdentityBindingStart.CONTEXT_UNAVAILABLE;
        final int expectedProfileGeneration = profileGeneration.get();
        final int expectedLifecycleToken;
        final String attemptKey = identityBindingContextKey(hostId, profileId,
                expectedProfileGeneration, connection);
        final IdentityBindingAttempt attempt;
        synchronized (childIdentityBindingLock) {
            expectedLifecycleToken = childIdentityBindingLifecycleToken;
            if (childIdentityBindingAttempt != null
                    && childIdentityBindingAttempt.contextKey.equals(attemptKey)
                    && childIdentityBindingAttempt.inFlight) {
                return IdentityBindingStart.IN_FLIGHT;
            }
            if (childIdentityBindingAttempt != null
                    && childIdentityBindingAttempt.contextKey.equals(attemptKey)
                    && (childIdentityBindingAttempt.completed || !allowRetry)) {
                return IdentityBindingStart.ATTEMPTED;
            }
            attempt = new IdentityBindingAttempt(attemptKey, expectedLifecycleToken);
            childIdentityBindingAttempt = attempt;
        }
        final String expectedHostId = hostId;
        final String expectedProfileId = profileId;
        executor.execute(() -> {
            String failureReason = "";
            boolean bound = false;
            boolean stale = false;
            try {
                HostGatewayClient.VibepolloIdentityChallenge challenge =
                        hostGatewayClient.requestVibepolloIdentityChallenge(connection);
                if (!isCurrentIdentityBindingContext(expectedHostId, expectedProfileId,
                        expectedProfileGeneration, expectedLifecycleToken, connection)) {
                    stale = true;
                } else {
                    AndroidCryptoProvider crypto = new AndroidCryptoProvider(this);
                    hostGatewayClient.bindExistingVibepolloIdentity(connection,
                            challenge.id, crypto.getClientCertificateSha256(),
                            crypto.signIdentityChallenge(challenge.challenge));
                    bound = true;
                }
            } catch (GatewayTransport.GatewayException error) {
                failureReason = identityBindingFailureReason(error);
            } catch (HostGatewayClient.GatewayException error) {
                failureReason = identityBindingFailureReason(error);
            } catch (IOException | RuntimeException error) {
                failureReason = "identity_proof_invalid";
            }
            final boolean resultBound = bound;
            final String resultReason = failureReason;
            final boolean resultStale = stale;
            mainHandler.post(() -> {
                synchronized (childIdentityBindingLock) {
                    if (childIdentityBindingAttempt == attempt) {
                        attempt.inFlight = false;
                        attempt.completed = resultBound;
                    }
                }
                if (resultStale || !isCurrentIdentityBindingContext(expectedHostId,
                        expectedProfileId, expectedProfileGeneration,
                        expectedLifecycleToken, connection)) return;
                if (resultBound) {
                    refreshChildEligibilityProfile(currentHost(expectedHostId));
                    return;
                }
                if ("identity_binding_unsupported".equals(resultReason)) {
                    ConsoleUiFeedback.makeText(this,
                            R.string.console_child_launch_pairing_upgrade,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ConsoleUiFeedback.makeText(this,
                        R.string.console_child_launch_pairing_retry,
                        Toast.LENGTH_LONG).show();
            });
        });
        return IdentityBindingStart.STARTED;
    }

    private static String identityBindingContextKey(String hostId, String profileId,
                                                    int profileGeneration,
                                                    GatewayConnection connection) {
        return hostId + "\n" + profileId + "\n" + profileGeneration + "\n"
                + connection.endpoint() + "\n" + connection.profileId() + "\n"
                + connection.certificateSha256() + "\n" + connection.token();
    }

    private void clearIdentityBindingAttempt(String hostId, String profileId,
                                             int expectedProfileGeneration,
                                             GatewayConnection connection) {
        String key = identityBindingContextKey(hostId, profileId,
                expectedProfileGeneration, connection);
        synchronized (childIdentityBindingLock) {
            if (childIdentityBindingAttempt != null
                    && childIdentityBindingAttempt.contextKey.equals(key)) {
                childIdentityBindingAttempt = null;
            }
        }
    }

    private boolean isCurrentIdentityBindingContext(String hostId, String profileId,
                                                    int expectedProfileGeneration,
                                                    int expectedLifecycleToken,
                                                    GatewayConnection expectedConnection) {
        if (!active || hostId == null || !hostId.equals(selectedHostUuid)
                || profileId == null || !profileId.equals(selectedProfileId(hostId))
                || expectedProfileGeneration != profileGeneration.get()) return false;
        synchronized (childIdentityBindingLock) {
            if (expectedLifecycleToken != childIdentityBindingLifecycleToken) return false;
        }
        ComputerDetails host = currentHost(hostId);
        if (host == null) return false;
        GatewayConnection current = childConnection(host, profileId);
        return sameGatewayProfile(expectedConnection, current)
                && current != null && expectedConnection.token().equals(current.token());
    }

    private static String identityBindingFailureReason(
            GatewayTransport.GatewayException error) {
        String reason = error == null ? "" : error.reason();
        return identityBindingFailureReason(
                reason == null || reason.trim().isEmpty() ? error == null
                        ? "" : error.getMessage() : reason,
                error != null && error.statusCode() == 404);
    }

    private static String identityBindingFailureReason(HostGatewayClient.GatewayException error) {
        return identityBindingFailureReason(error == null ? "" : error.getMessage(),
                error != null && error.statusCode == 404);
    }

    static String identityBindingFailureReason(String message, boolean notFound) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("identity_binding_unsupported")
                || normalized.contains("child_action_not_allowed")) {
            return "identity_binding_unsupported";
        }
        if (normalized.contains("identity_challenge_expired")) {
            return "identity_challenge_expired";
        }
        if (normalized.contains("identity_ambiguous")) return "identity_ambiguous";
        if (normalized.contains("identity_proof_invalid")) return "identity_proof_invalid";
        return notFound ? "identity_binding_unsupported" : "identity_proof_invalid";
    }

    private void playProviderGame(PlayIntent intent) {
        if (warnPendingRestart(intent.hostId, () -> sessionOrchestrator.play(intent))) return;
        sessionOrchestrator.play(intent);
    }

    /** Shows the host's pending restart before Play; true when the dialog took the Play. */
    private boolean warnPendingRestart(String hostId, Runnable play) {
        HostGatewayClient.PowerStatus status = hostPowerStatus.get(hostId);
        if (status == null || !status.rebootRequired
                || pendingRestartAcknowledged.contains(hostId)) return false;
        ComputerDetails host = currentHost(hostId);
        if (host == null) return false;
        List<View> actions = new ArrayList<>();
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        cancel.setOnClickListener(view -> handlePanelBack());
        actions.add(cancel);
        if (!selectedProfileIsChild(hostId)) {
            TextView restart = consolePanel.action(getString(R.string.console_restart));
            restart.setOnClickListener(view -> confirmHostPower(host, false));
            actions.add(restart);
        }
        TextView playAnyway = consolePanel.action(getString(R.string.console_play_anyway));
        playAnyway.setOnClickListener(view -> {
            pendingRestartAcknowledged.add(hostId);
            hideSidePanel();
            play.run();
        });
        actions.add(playAnyway);
        showSidePanel(getString(R.string.console_host_power),
                getString(R.string.console_restart_pending_title, host.name),
                getString(R.string.console_restart_pending_details),
                actions.toArray(new View[0]));
        return true;
    }

    private ChildLaunchBinding childBinding(PlayIntent intent) {
        if (intent == null) return null;
        return childLaunchBindings.get(intent.hostId + "\n"
                + intent.profileId + "\n" + intent.playniteGameId);
    }

    private ChildLaunchBinding startChildSession(PlayIntent intent,
                                                  BooleanSupplier cancelled) throws IOException {
        if (!isChildIntent(intent)) return null;
        HostGatewayClient.IntegrationProfile profile = integrationProfile(
                intent.hostId, intent.profileId);
        if (profile == null || !profile.enabled || !profile.useProfile) {
            throw new ChildLaunchFailure("child_profile_unavailable");
        }
        for (ChildLaunchBinding pending : childLaunchBindings.values()) {
            if (pending.hostId.equals(intent.hostId)
                    && (!pending.actorProfileId.equals(intent.profileId)
                    || !childGameIdsMatch(pending.gameId, intent.playniteGameId))) {
                pending.ended = true;
                childLaunchBindings.remove(pending.key(), pending);
            }
        }
        String key = intent.hostId + "\n" + intent.profileId + "\n" + intent.playniteGameId;
        ChildLaunchBinding binding = childLaunchBindings.get(key);
        if (binding != null && binding.started) {
            ChildSessionClient.State state = childSessionClient.status(binding.connection, binding.sessionId);
            if (state.cleanupRequired) {
                binding.ended = true;
                childLaunchBindings.remove(key, binding);
                binding = null;
            } else if (childStateRemovesBinding(false, state.phase, state.state)) {
                binding.ended = true;
                childLaunchBindings.remove(key, binding);
                binding = null;
            }
        }
        if (binding == null) {
            ChildLaunchBinding candidate = new ChildLaunchBinding(
                    intent.hostId, intent.profileId, intent.playniteGameId,
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    profileGeneration.get(), childBindingSequence.incrementAndGet());
            ChildLaunchBinding existing = childLaunchBindings.putIfAbsent(key, candidate);
            binding = existing == null ? candidate : existing;
        }
        synchronized (binding) {
            if (binding.ended) throw new IOException("child_session_cancelled");
            if (binding.started) {
                ChildSessionClient.State resumed = childSessionClient.heartbeat(
                        binding.connection, binding.sessionId);
                validateChildState(intent, profile, resumed, binding.sessionId);
                if (!childSessionCanContinue(resumed.ok, resumed.state,
                        resumed.reason, resumed.playableNowSeconds)) {
                    throw new ChildLaunchFailure(resumed.reason);
                }
                binding.state = resumed;
                binding.deadlineElapsedMs = SystemClock.elapsedRealtime()
                        + Math.max(0L, resumed.playableNowSeconds) * 1_000L;
                binding.policyRevision = resumed.policyRevision;
                return binding;
            }
            ComputerDetails host = currentHost(intent.hostId);
            if (host == null) throw new IOException("host_unavailable");
            String address = host.activeAddress == null ? null : host.activeAddress.address;
            GatewayConnection connection = hostGatewayStore.loadForHost(
                    host.uuid, address, intent.profileId);
            if (connection == null) throw new IOException("gateway_unavailable");
            binding.connection = connection;
            ChildSessionClient.State eligibility = childSessionClient.eligibility(
                    connection, intent.playniteGameId);
            binding.state = eligibility;
            if (eligibility == null || !childEligibilityCanLaunch(eligibility.ok,
                    eligibility.state, eligibility.reason, eligibility.playableNowSeconds)) {
                String reason = eligibility == null || eligibility.reason == null
                        || eligibility.reason.isEmpty()
                        ? "child_not_playable" : eligibility.reason;
                throw new ChildLaunchFailure(reason);
            }
            if (cancelled.getAsBoolean()) {
                throw new ChildLaunchFailure("cancelled", true);
            }
            ChildSessionClient.State started = childSessionClient.start(
                    connection, intent.playniteGameId, binding.sessionId, binding.requestId);
            binding.state = started;
            if (!started.ok) {
                String reason = started.reason == null || started.reason.isEmpty()
                        ? "child_not_playable" : started.reason;
                throw new ChildLaunchFailure(reason);
            }
            validateChildState(intent, profile, started, binding.sessionId);
            if (started == null || !childSessionCanContinue(started.ok, started.state,
                    started.reason, started.playableNowSeconds)) {
                String reason = started == null || started.reason == null
                        || started.reason.isEmpty()
                        ? "child_not_playable" : started.reason;
                throw new ChildLaunchFailure(reason);
            }
            binding.gameId = canonicalChildGameId(intent.playniteGameId, started.gameId);
            binding.executionProfileId = started.executionProfileId.isEmpty()
                    ? profile.executionProfileId : started.executionProfileId;
            binding.policyRevision = started.policyRevision;
            binding.controlGeneration = started.controlGeneration;
            long durationMs = Math.min(Long.MAX_VALUE,
                    Math.max(0L, started.playableNowSeconds) * 1_000L);
            binding.deadlineElapsedMs = Math.min(Long.MAX_VALUE,
                    SystemClock.elapsedRealtime() + durationMs);
            binding.started = true;
            if (cancelled.getAsBoolean()) {
                endChildBinding(binding, "cancelled");
                throw new ChildLaunchFailure("cancelled", true);
            }
            return binding;
        }
    }

    private void validateChildState(PlayIntent intent,
                                    HostGatewayClient.IntegrationProfile profile,
                                    ChildSessionClient.State state,
                                    String expectedSessionId) throws IOException {
        if (state == null || !intent.profileId.equals(state.actorProfileId)
                || !expectedSessionId.equals(state.sessionId)) {
            throw new IOException("child_session_binding_mismatch");
        }
        String expectedExecution = profile.executionProfileId.isEmpty()
                ? profile.parentProfileId : profile.executionProfileId;
        if (!expectedExecution.isEmpty() && !state.executionProfileId.isEmpty()
                && !expectedExecution.equals(state.executionProfileId)) {
            throw new IOException("child_execution_profile_mismatch");
        }
        if (!state.gameId.isEmpty()
                && !childGameIdsMatch(intent.playniteGameId, state.gameId)) {
            throw new IOException("child_game_binding_mismatch");
        }
    }

    static String canonicalChildGameId(String requestedGameId, String echoedGameId) {
        String echoed = echoedGameId == null ? "" : echoedGameId.trim();
        return ChildGameAccess.canonicalGameId(echoed.isEmpty() ? requestedGameId : echoed);
    }

    static boolean childGameIdsMatch(String requestedGameId, String echoedGameId) {
        return ChildGameAccess.canonicalGameId(requestedGameId)
                .equalsIgnoreCase(ChildGameAccess.canonicalGameId(echoedGameId));
    }

    private void endChildBinding(ChildLaunchBinding binding, String reason) {
        if (binding == null || !binding.started || binding.ended) return;
        synchronized (binding) {
            if (binding.ended || !binding.started || binding.endRequested) return;
            binding.endRequested = true;
        }
        GatewayConnection connection = binding.connection;
        if (connection == null) {
            return;
        }
        executor.execute(() -> {
            try {
                ChildSessionClient.State ended = childSessionClient.end(
                        connection, binding.sessionId, reason);
                mainHandler.post(() -> consumeChildBindingState(binding, ended));
            } catch (IOException | RuntimeException ignored) {
                binding.endRequested = false;
            }
        });
    }

    private ChildLaunchBinding activeChildBinding(String hostId, String profileId) {
        for (ChildLaunchBinding binding : childLaunchBindings.values()) {
            if (binding.started && !binding.ended && binding.hostId.equals(hostId)
                    && binding.actorProfileId.equals(profileId)) {
                return binding;
            }
        }
        return null;
    }

    static boolean childStateRemovesBinding(boolean cleanupRequired,
                                            String phase, String state) {
        if (cleanupRequired) return false;
        return "ended".equalsIgnoreCase(phase)
                || "ended".equalsIgnoreCase(state)
                || "stopped".equalsIgnoreCase(phase)
                || "stopped".equalsIgnoreCase(state)
                || "already_stopped".equalsIgnoreCase(state);
    }

    private static boolean childEndConfirmed(ChildSessionClient.State state) {
        return state != null && childStateRemovesBinding(state.cleanupRequired,
                state.phase, state.state);
    }

    private void cancelChildLaunches(String hostId, String profileId) {
        cancelChildLaunches(hostId, profileId, null);
    }

    private void cancelChildLaunches(String hostId, String profileId,
                                     ChildLaunchBinding preservedBinding) {
        for (ChildLaunchBinding binding : new ArrayList<>(childLaunchBindings.values())) {
            if (binding != preservedBinding && !binding.handedToPlayer
                    && (hostId == null || hostId.equals(binding.hostId))
                    && (profileId == null || profileId.equals(binding.actorProfileId))) {
                endChildBinding(binding, "cancelled");
            }
        }
    }

    private void refreshChildBindingStates() {
        for (ChildLaunchBinding binding : new ArrayList<>(childLaunchBindings.values())) {
            if (!binding.started || binding.ended || binding.connection == null) continue;
            executor.execute(() -> {
                try {
                    ChildSessionClient.State state = childSessionClient.status(
                            binding.connection, binding.sessionId);
                    mainHandler.post(() -> consumeChildBindingState(binding, state));
                } catch (IOException | RuntimeException ignored) { }
            });
        }
    }

    private void consumeChildBindingState(ChildLaunchBinding binding,
                                          ChildSessionClient.State state) {
        if (binding == null || state == null
                || childLaunchBindings.get(binding.key()) != binding
                || binding.ended || !binding.started) {
            return;
        }
        if (!state.sessionId.isEmpty() && !binding.sessionId.equals(state.sessionId)) return;
        if (!state.actorProfileId.isEmpty()
                && !binding.actorProfileId.equals(state.actorProfileId)) return;
        if (!state.gameId.isEmpty()
                && !childGameIdsMatch(binding.requestedGameId, state.gameId)) return;
        binding.state = state;
        boolean removesBinding = childStateRemovesBinding(state.cleanupRequired,
                state.phase, state.state);
        if (!state.cleanupRequired && !removesBinding) return;
        if (state.cleanupRequired) binding.endRequested = true;
        if (removesBinding) {
            binding.ended = true;
            childLaunchBindings.remove(binding.key(), binding);
        }
    }

    private void clearChildLibraryForPolicyChange(String hostId, String profileId) {
        if (hostGatewayStore == null || !selectedProfileId(hostId).equals(profileId)) return;
        playniteGeneration.incrementAndGet();
        cancelPlayniteRequest();
        cancelPlayniteArtworkPrefetch();
        artworkGeneration.incrementAndGet();
        currentPlayniteGames = Collections.emptyList();
        currentPlayniteProfileKey = null;
        currentPlayniteHostUuid = null;
        renderedPlayniteItems = Collections.emptyList();
        allPlayniteItems = Collections.emptyList();
        unfilteredPlayniteItems = Collections.emptyList();
        initialLibraryPresentation = null;
        clearArtwork();
    }

    private void saveHostProfiles(String hostId,
                                  HostGatewayClient.IntegrationProfiles profiles) {
        if (hostGatewayStore == null || hostId == null || profiles == null) return;
        HostGatewayClient.IntegrationProfiles previous = hostGatewayStore.profiles(hostId);
        hostGatewayStore.saveProfiles(hostId, profiles);
        String selected = selectedProfileId(hostId);
        HostGatewayClient.IntegrationProfile before = previous == null
                ? null : previous.find(selected);
        HostGatewayClient.IntegrationProfile after = profiles.find(selected);
        if (before != null && before.isChild()
                && (after == null || !after.isChild() || !after.enabled || !after.useProfile
                || before.policyRevision != after.policyRevision
                || !before.parentProfileId.equals(after.parentProfileId))) {
            clearChildLibraryForPolicyChange(hostId, selected);
        }
    }

    private boolean selectedChildWasInvalidated(
            String hostId, HostGatewayClient.IntegrationProfiles profiles) {
        if (hostGatewayStore == null || hostId == null || profiles == null) return false;
        String selected = selectedProfileId(hostId);
        HostGatewayClient.IntegrationProfile before = hostGatewayStore.profiles(hostId)
                .find(selected);
        HostGatewayClient.IntegrationProfile after = profiles.find(selected);
        return before != null && before.isChild()
                && (after == null || !after.isChild() || !after.enabled || !after.useProfile);
    }

    /**
     * Reads a profile's library into memory and resolves its targets while its profile is
     * focused or its PIN typed: the entry's own read then finds it there. Nothing is rendered
     * or handed to the UI here. A read not yet started for another profile is dropped.
     */
    private void readLibraryAhead(ComputerDetails host, String profileId) {
        HostProfileKey key = new HostProfileKey(host.uuid, profileId);
        Locale sortLocale = getResources().getConfiguration().getLocales().get(0);
        if (libraryReadAhead != null) libraryReadAhead.cancel(false);
        libraryReadAhead = playniteExecutor.submit(() -> readCachedLibrary(host, key, sortLocale));
    }

    private void showPinEntry(ComputerDetails host,
                              HostGatewayClient.IntegrationProfile profile,
                              boolean focusApps, boolean prepareHost,
                              boolean completeHostEntry) {
        cancelOfflinePinAttempt();
        profileGateGeneration.incrementAndGet();
        clearPinEntry();
        profileGateHostUuid = host.uuid;
        pinProfileId = profile.id;
        pinFocusApps = focusApps;
        pinPrepareHost = prepareHost;
        pinCompleteHostEntry = completeHostEntry;
        readLibraryAhead(host, profile.id);
        showPinPage(R.string.console_pin_title, host.name, profile);
        // The entry intro this PIN leads to is built while it is typed.
        if (introView != null && completeHostEntry) introView.prepare(profile);
    }

    /**
     * The PIN page of the entry shell for {@code profile}: the profile row stays beneath as its
     * blurred backdrop, or, opened from Home, the shell's own background. The window is secure
     * until the page is gone.
     */
    private void showPinPage(int title, String context,
                             HostGatewayClient.IntegrationProfile profile) {
        if ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            pinSecureFlagAdded = true;
        }
        boolean playStationButtons = hasLastControllerInput
                ? lastControllerPlayStation : ControllerGlyphs.hasPlayStationController();
        pinEntryView.show(title, context, profile, playStationButtons,
                lastInputWasController && hasLastControllerInput, reducedMotion,
                this::onPinDigit);
        renderPinEntry("");
    }

    private void onPinDigit(int digit) {
        if (pinProfileId == null || pinSubmitting
                || SystemClock.elapsedRealtime() < pinCooldownUntil) return;
        ConsolePinEntry.Action action = digit == ConsolePinEntryView.DELETE
                ? pinEntry.deleteLast() : pinEntry.appendDigit(digit);
        renderPinEntry("");
        if (action == ConsolePinEntry.Action.SUBMIT) submitPin();
    }

    private void submitPin() {
        if (adultPin != null) submitAdultPin();
        else if (profileCreation != null) submitProfileCreationPin();
        else if (parentPin != null) submitParentPin();
        else submitProfilePin();
    }

    /** Back on the PIN page: the parent's PIN is cancelled, a profile's returns to the gate. */
    private void leavePin() {
        if (profileCreation != null) {
            ProfileCreationRequest request = profileCreation;
            clearPinPage();
            finishProfileCreation(request, null);
            return;
        }
        if (adultPin != null) {
            AdultPinRequest request = adultPin;
            clearPinEntry();
            leaveAdultPin(request);
            return;
        }
        if (parentPin != null) {
            ParentPinRequest request = parentPin;
            closeParentPin();
            if (request.onCancel != null) request.onCancel.run();
            return;
        }
        ComputerDetails host = currentHost(profileGateHostUuid);
        if (host != null) showProfileGate(host, pinFocusApps, pinPrepareHost);
        else closeProfileGate();
    }

    private void submitProfilePin() {
        if (pinProfileId == null || pinSubmitting || pinEntry.length() != 4) return;
        long now = SystemClock.elapsedRealtime();
        if (now < pinCooldownUntil) {
            renderPinCooldown();
            return;
        }
        String hostId = profileGateHostUuid;
        String profileId = pinProfileId;
        int token = profileGateGeneration.get();
        String pin = pinEntry.takeAndClear();
        pinSubmitting = true;
        renderPinEntry("");
        ComputerDetails host = currentHost(hostId);
        String address = host == null || host.activeAddress == null
                ? null : host.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                hostId, address, profileId);
        OfflinePinAttempt attempt = new OfflinePinAttempt(
                hostId, profileId, token, connection, host);
        offlinePinAttempt = attempt;
        renderPinEntry(getString(R.string.console_pin_checking));
        executor.execute(() -> {
            OfflineProfilePinFlow.Result result = OfflineProfilePinFlow.run(
                    hostId, connection, pin,
                    OfflineProfilePinFlow.cacheOf(hostGatewayStore.offlineProfilePinStore()),
                    new OfflineProfilePinFlow.Gateway() {
                        @Override public void verifyProfilePin(
                                GatewayConnection current, String enteredPin)
                                throws IOException {
                            mainHandler.post(() -> renderOfflinePinMessage(attempt,
                                    R.string.console_pin_checking));
                            hostGatewayClient.verifyProfilePin(current, enteredPin);
                        }

                        @Override public HostGatewayClient.IntegrationProfiles
                        getIntegrationProfiles(GatewayConnection current) throws IOException {
                            mainHandler.post(() -> renderOfflinePinMessage(attempt,
                                    R.string.console_pin_offline_waiting));
                            return hostGatewayClient.getIntegrationProfiles(current);
                        }
                    },
                    () -> {
                        if (!isOfflinePinAttemptCurrent(attempt)
                                || currentOfflinePinConnection(attempt) == null
                                || attempt.host == null) {
                            throw new IOException("Host unavailable");
                        }
                        mainHandler.post(() -> renderOfflinePinMessage(attempt,
                                R.string.console_pin_offline_waking));
                        WakeOnLanSender.sendWolPacket(attempt.host);
                    },
                    () -> currentOfflinePinConnection(attempt),
                    SystemClock::elapsedRealtime,
                    millis -> {
                        try {
                            Thread.sleep(millis);
                            return true;
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    },
                    () -> !isOfflinePinAttemptCurrent(attempt));
            mainHandler.post(() -> finishProfilePinFlow(attempt, result));
        });
    }

    private boolean isOfflinePinAttemptCurrent(OfflinePinAttempt attempt) {
        return attempt != null && offlinePinAttempt == attempt && !attempt.cancelled
                && attempt.gateToken == profileGateGeneration.get();
    }

    private GatewayConnection currentOfflinePinConnection(OfflinePinAttempt attempt) {
        if (attempt == null) return null;
        ComputerDetails current = managerBinder == null
                ? null : managerBinder.getComputer(attempt.hostId);
        if (managerBinder != null && current == null) return null;
        String address = current == null || current.activeAddress == null
                ? attempt.initialAddress : current.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                attempt.hostId, address, attempt.profileId);
        return OfflineProfilePinFlow.samePairing(attempt.pairing, connection)
                ? connection : null;
    }

    private void cancelOfflinePinAttempt() {
        OfflinePinAttempt attempt = offlinePinAttempt;
        if (attempt != null) attempt.cancelled = true;
        offlinePinAttempt = null;
    }

    private void renderOfflinePinMessage(OfflinePinAttempt attempt, int message) {
        if (!isOfflinePinAttemptCurrent(attempt)) return;
        renderPinEntry(getString(message));
    }

    private void finishProfilePinFlow(OfflinePinAttempt attempt,
                                      OfflineProfilePinFlow.Result result) {
        if (!isOfflinePinAttemptCurrent(attempt)
                || !ConsolePinEntry.matchesResult(attempt.gateToken, attempt.hostId,
                attempt.profileId, profileGateGeneration.get(), profileGateHostUuid,
                pinProfileId)) return;
        if (currentOfflinePinConnection(attempt) == null) {
            attempt.cancelled = true;
            offlinePinAttempt = null;
            pinSubmitting = false;
            pinEntry.clear();
            renderPinEntry(getString(R.string.console_pin_failed));
            return;
        }
        offlinePinAttempt = null;
        pinSubmitting = false;
        pinEntry.clear();
        if (result == null) {
            renderPinEntry(getString(R.string.console_pin_failed));
            return;
        }
        if (result.profiles != null) {
            saveHostProfiles(attempt.hostId, result.profiles);
        }
        switch (result.status) {
            case VERIFIED:
                finishProfilePinVerification(attempt.hostId, attempt.profileId,
                        attempt.gateToken, null, null);
                return;
            case INVALID:
            case COOLDOWN:
                finishOfflinePinFailure(result.retryAfterSeconds,
                        result.status == OfflineProfilePinFlow.Status.INVALID);
                return;
            case MISSING:
                renderPinEntry(getString(R.string.console_pin_offline_connect_once));
                return;
            case PROFILE_DENIED:
                ComputerDetails current = currentHost(attempt.hostId);
                if (current != null) {
                    showProfileGate(current, pinFocusApps, pinPrepareHost);
                }
                return;
            case CANCELLED:
                renderPinEntry("");
                return;
            case TIMEOUT:
                renderPinEntry(getString(R.string.console_pin_offline_unavailable));
                return;
            case UNAVAILABLE:
            default:
                renderPinEntry(getString(R.string.console_pin_failed));
        }
    }

    private void finishOfflinePinFailure(int retryAfterSeconds, boolean invalid) {
        int seconds = Math.max(1, retryAfterSeconds);
        pinCooldownUntil = SystemClock.elapsedRealtime() + seconds * 1_000L;
        if (invalid) pinEntryView.showInvalid();
        renderPinCooldown();
    }

    private void finishProfilePinVerification(String hostId, String profileId, int token,
                                              HostGatewayClient.GatewayException gatewayError,
                                              IOException transportError) {
        if (!ConsolePinEntry.matchesResult(token, hostId, profileId,
                profileGateGeneration.get(), profileGateHostUuid, pinProfileId)) return;
        pinSubmitting = false;
        pinEntry.clear();
        if (gatewayError == null && transportError == null) {
            ComputerDetails host = currentHost(hostId);
            HostGatewayClient.IntegrationProfile profile =
                    hostGatewayStore.profileSelection(hostId).find(profileId);
            if (host == null || profile == null || !profile.pinRequired) return;
            if (pinCompleteHostEntry) {
                if (introView != null) introView.start(profile, hostId, ConsoleIntroView.Entry.PIN);
                changeSelectedProfile(host, profileId);
                enterHostAfterProfileGate(host, pinFocusApps, pinPrepareHost);
            } else {
                profileGateGeneration.incrementAndGet();
                clearPinEntry();
                profileGateHostUuid = null;
                applySelectedProfile(host, profileId);
            }
            return;
        }
        String reason = gatewayError == null ? "" : gatewayError.getMessage();
        int seconds = ConsolePinEntry.retryDelaySeconds(reason,
                gatewayError == null ? 0 : gatewayError.retryAfterSeconds);
        if (seconds > 0) {
            pinCooldownUntil = SystemClock.elapsedRealtime() + seconds * 1_000L;
            if ("invalid_pin".equals(reason)) pinEntryView.showInvalid();
            renderPinCooldown();
        } else {
            renderPinEntry(getString(R.string.console_pin_failed));
        }
    }

    private void renderPinCooldown() {
        long remaining = Math.max(0L, pinCooldownUntil - SystemClock.elapsedRealtime());
        if (remaining == 0L) {
            pinCooldownUntil = 0L;
            renderPinEntry("");
            return;
        }
        int seconds = (int) ((remaining + 999L) / 1_000L);
        renderPinEntry(getString(R.string.console_pin_cooldown, seconds));
        int token = profileGateGeneration.get();
        String profileId = pinProfileId;
        mainHandler.postDelayed(() -> {
            if (token == profileGateGeneration.get()
                    && profileId != null && profileId.equals(pinProfileId)) {
                renderPinCooldown();
            }
        }, Math.min(1_000L, remaining));
    }

    private void renderPinEntry(String message) {
        if (pinEntryView != null && pinProfileId != null) {
            pinEntryView.render(pinEntry.length(), message, pinSubmitting,
                    pinCooldownUntil - SystemClock.elapsedRealtime());
        }
    }

    private void clearPinEntry() {
        pinEntry.clear();
        adultPin = null;
        profileCreation = null;
        pinProfileId = null;
        pinSubmitting = false;
        pinCooldownUntil = 0L;
        pinCompleteHostEntry = false;
        if (introView != null) introView.discard();
        // FLAG_SECURE stays until the PIN page has faded out and a starting entry intro has
        // covered its last frame on screen, unless a PIN screen reopened.
        if (pinEntryView != null) pinEntryView.hide(() -> ConsoleIntroView.shown(introView, () -> {
            if (pinProfileId != null || !pinSecureFlagAdded) return;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            pinSecureFlagAdded = false;
        }));
    }

    private void enterHostAfterProfileGate(ComputerDetails host, boolean focusApps,
                                           boolean prepareHost) {
        if (host == null) return;
        cancelOfflinePinAttempt();
        profileGateGeneration.incrementAndGet();
        clearPinEntry();
        profileGateHostUuid = null;
        if (profileSelectView != null) profileSelectView.setVisibility(View.GONE);
        newlyDiscoveredHosts.remove(host.uuid);
        String tileSessionId = returnRequests.takeWatchNextSessionId();
        WatchNextTile.Entry tileEntry = tileSessionId.isEmpty() ? null
                : WatchNextTile.entry(sessionModel.snapshot(),
                sessionModel.canResumeInstantly(), tileSessionId, host.uuid,
                selectedProfileId(host.uuid));
        if (tileEntry == WatchNextTile.Entry.RESUME) {
            ProfileAuthorization.grant(host.uuid);
            resumeRetainedStream();
            return;
        }
        selectHost(host, focusApps);
        if (tileEntry == WatchNextTile.Entry.ENDED) {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.INFO,
                    getString(R.string.watch_next_session_ended));
        }
        if (prepareHost) prepareSelectedHost(host);
        // The host is entered: a Play queued behind a close may start now.
        if (sessionOrchestrator != null) sessionOrchestrator.sessionChanged();
        if (host.uuid.equals(pendingParentPanelHost)) {
            pendingParentPanelHost = null;
            mainHandler.post(() -> openChildrenSettings(host));
        }
    }

    private void closeProfileGate() {
        String hostId = profileGateHostUuid;
        ProfileAuthorization.revoke();
        clearPinEntry();
        showHostSelection(hostId);
    }

    private void prepareSelectedHost(ComputerDetails host) {
        if (!active) return;
        if (returnRequests.takeSuppressAutoWarmUp()) return;
        // The host never warms up a child's neutral stream (child_session_unmanaged): the
        // refused attempt only swapped the console twice (TV 2026-10-19).
        if (selectedProfileIsChild(host.uuid)) return;
        if (sessionOrchestrator != null
                && ConsoleActionCatalog.isRememberedPaired(host)
                && HostAutoWarmUpPreferences.isEnabled(preferences, host.uuid)) {
            if (managerBinder == null) {
                pendingHostPreparation = host.uuid;
                return;
            }
            pendingHostPreparation = null;
            long attempt = sessionOrchestrator.prepareHost(host.uuid,
                    selectedProfileId(host.uuid));
            warmUpClientHostId = normalizeId(host.uuid);
            warmUpRequest = attempt;
            if (attempt > 0L) {
                warmUpStatus = WARM_UP_PREPARING;
            } else {
                warmUpStatus = WARM_UP_ERROR;
            }
            renderHosts();
        }
    }

    private void dispatchPendingHostPreparation() {
        String hostId = pendingHostPreparation;
        pendingHostPreparation = null;
        if (active && hostId != null && !hostSelectionVisible && hostId.equals(selectedHostUuid)) {
            prepareSelectedHost(currentHost(hostId));
        }
    }

    private void armPendingWarmUpRelay() {
        if (!isStreamHome() || warmUpRelayDispatched || homePreparingOperation <= 0L) return;
        String gameId = normalizeId(getIntent().getStringExtra(
                EXTRA_WARM_UP_PENDING_GAME_ID));
        String gameName = getIntent().getStringExtra(EXTRA_WARM_UP_PENDING_GAME_NAME);
        if (gameId.isEmpty() || gameName == null || gameName.trim().isEmpty()) return;
        String artworkId = getIntent().getStringExtra(EXTRA_WARM_UP_PENDING_ARTWORK_ID);
        String quickLaunchId = getIntent().getStringExtra(EXTRA_WARM_UP_PENDING_QUICK_LAUNCH);
        pendingWarmUpRelay = PlayIntent.playniteGame(
                launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID), launchedProfileId(),
                launchedAppId(), gameName, false, gameId,
                artworkId, quickLaunchId,
                getIntent().getBooleanExtra(EXTRA_WARM_UP_PENDING_REQUIRES_CONNECTOR, true),
                getIntent().getBooleanExtra(
                        EXTRA_WARM_UP_PENDING_START_BEFORE_STREAM, false));
    }

    protected final void acceptPreparingHomeFrame() {
        warmUpHomeFrameAccepted = true;
        if (pendingWarmUpRelay == null) return;
        ComputerDetails host = currentHost(pendingWarmUpRelay.hostId);
        showLoading(host == null ? pendingWarmUpRelay.hostId : host.name,
                pendingWarmUpRelay.appName,
                coverArtworkPath(pendingWarmUpRelay.hostId,
                        pendingWarmUpRelay.profileId,
                        pendingWarmUpRelay.loadingArtworkGameId, "launch"));
        dispatchPendingWarmUpRelay();
    }

    private void dispatchPendingWarmUpRelay() {
        if (!warmUpHomeFrameAccepted || managerBinder == null || sessionOrchestrator == null
                || pendingWarmUpRelay == null || warmUpRelayDispatched) return;
        if (isPendingWarmUpRelayOwnedOrCompleted(pendingWarmUpRelay.playniteGameId)) {
            clearPendingWarmUpRelay();
            return;
        }
        if (warmUpRelaySubmitted) return;
        SessionModel.Snapshot preparing =
                sessionModel.snapshot();
        boolean exact = preparing.state == SessionModel.State.PREPARING
                && homePreparingOperation > 0L
                && preparing.preparingOperation == homePreparingOperation
                && preparing.streamSessionId.equals(
                launchedExtra(EXTRA_RETAINED_STREAM_SESSION_ID))
                && preparing.hostId.equalsIgnoreCase(launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID))
                && preparing.appId == launchedAppId();
        if (!exact) {
            clearPendingWarmUpRelay();
            warmUpStatus = WARM_UP_ERROR;
            renderHosts();
            updateHostSelector();
            showHome();
            return;
        }
        PlayIntent relay = pendingWarmUpRelay;
        warmUpRelaySubmitted = true;
        // The Play this relays already passed the pending-restart warning when it was pressed.
        sessionOrchestrator.play(relay);
    }

    private boolean isPendingWarmUpRelayOwnedOrCompleted(String gameId) {
        if (sessionModel.isPreparingSwitchOwned(
                launchedExtra(EXTRA_RETAINED_STREAM_SESSION_ID),
                launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID), launchedAppId(),
                homePreparingOperation)) {
            return true;
        }
        SessionModel.Snapshot retained =
                sessionModel.snapshot();
        return (retained.state == SessionModel.State.PREPARING
                || retained.state == SessionModel.State.HOME_LIVE
                || retained.state == SessionModel.State.PARKED_LIVE)
                && retained.streamSessionId.equals(launchedExtra(EXTRA_RETAINED_STREAM_SESSION_ID))
                && retained.hostId.equalsIgnoreCase(launchedExtra(EXTRA_RETAINED_STREAM_HOST_ID))
                && retained.appId == launchedAppId()
                && retained.playniteGameId.equalsIgnoreCase(normalizeId(gameId));
    }

    private void clearPendingWarmUpRelay() {
        pendingWarmUpRelay = null;
        warmUpRelayDispatched = true;
        warmUpRelaySubmitted = false;
        getIntent().removeExtra(EXTRA_WARM_UP_PENDING_GAME_ID);
        getIntent().removeExtra(EXTRA_WARM_UP_PENDING_GAME_NAME);
        getIntent().removeExtra(EXTRA_WARM_UP_PENDING_ARTWORK_ID);
        getIntent().removeExtra(EXTRA_WARM_UP_PENDING_QUICK_LAUNCH);
    }

    private void buildExpandedLibrary() {
        expandedLibrary = new LinearLayout(this);
        expandedLibrary.setOrientation(LinearLayout.VERTICAL);
        expandedLibrary.setGravity(Gravity.TOP);
        expandedLibrary.setVisibility(View.GONE);
        expandedLibrary.setClipChildren(false);
        expandedLibrary.setClipToPadding(false);

        libraryFilterBar = new LibraryFilterBar(this, this::showLibraryFilters,
                this::openLibrarySearch, this::stopExpandedDescriptionAutoScroll);
        expandedLibrary.addView(libraryFilterBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        // A baseline-aligned row measures its weighted children with an UNSPECIFIED height,
        // which makes the recycling grid lay out (create and bind) every card in the library.
        body.setBaselineAligned(false);
        body.setGravity(Gravity.TOP);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        expandedLibrary.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(4), dp(16), dp(18), dp(12));
        expandedGameTitle = text("", 18, Color.WHITE, true);
        expandedGameTitle.setMaxLines(2);
        expandedGameTitle.setEllipsize(TextUtils.TruncateAt.END);
        expandedGameFacts = text("", 12, 0xFFB8C9DC, false);
        expandedGameFacts.setMaxLines(3);
        expandedGameDescription = text("", 14, 0xFFE0E5EA, false);
        expandedGameDescription.setLineSpacing(0, 1.25f);
        expandedGameDescription.setPadding(0, 0, 0, dp(20));
        shownSelectedGameBadge = null;
        expandedGameStatus = text("", 12, 0xFFF0F5F8, true);
        expandedGameStatus.setSingleLine(true);
        expandedGameStatus.setEllipsize(TextUtils.TruncateAt.END);
        expandedGameStatus.setCompoundDrawablePadding(dp(5));
        expandedGameStatus.setVisibility(View.GONE);
        details.addView(expandedGameTitle, matchLinearWidth());
        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.VERTICAL);
        facts.addView(expandedGameStatus, matchLinearWidth());
        facts.addView(expandedGameFacts, matchLinearWidth());
        expandedChildState = text("", 12, 0xFFE0E5EA, false);
        expandedChildState.setLineSpacing(0, 1.2f);
        expandedChildState.setVisibility(View.GONE);
        LinearLayout.LayoutParams childStateParams = matchLinearWidth();
        childStateParams.topMargin = dp(8);
        facts.addView(expandedChildState, childStateParams);
        LinearLayout.LayoutParams expandedFactsParams = matchLinearWidth();
        expandedFactsParams.topMargin = dp(12);
        details.addView(facts, expandedFactsParams);
        View divider = new View(this);
        divider.setBackgroundColor(0x6073D7FF);
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(12);
        dividerParams.bottomMargin = dp(12);
        details.addView(divider, dividerParams);
        expandedDescriptionScroll = new ScrollView(this);
        expandedDescriptionScroll.setFocusable(false);
        expandedDescriptionScroll.setClickable(false);
        expandedDescriptionScroll.setVerticalScrollBarEnabled(false);
        expandedDescriptionScroll.setFadingEdgeLength(dp(20));
        expandedDescriptionScroll.setVerticalFadingEdgeEnabled(true);
        expandedDescriptionScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        expandedDescriptionScroll.setPadding(0, dp(5), dp(7), dp(5));
        expandedDescriptionScroll.addView(expandedGameDescription,
                new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        details.addView(expandedDescriptionScroll,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        expandedPageIndicator = text("", 9, 0xFF8F9AAF, false);
        expandedPageIndicator.setGravity(Gravity.CENTER_HORIZONTAL);
        expandedPageIndicator.setFocusable(false);
        LinearLayout.LayoutParams pageIndicatorParams = matchLinearWidth();
        pageIndicatorParams.topMargin = dp(7);
        details.addView(expandedPageIndicator, pageIndicatorParams);

        shownLibraryStatus = "";
        expandedCacheStatus = text("", 8, 0xFF9EADBD, false);
        expandedCacheStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        expandedCacheStatus.setFocusable(false);
        expandedCacheStatus.setMaxLines(2);
        LinearLayout.LayoutParams cacheStatusParams = matchLinearWidth();
        cacheStatusParams.topMargin = dp(3);
        details.addView(expandedCacheStatus, cacheStatusParams);

        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                dp(205), ViewGroup.LayoutParams.MATCH_PARENT);
        detailsParams.rightMargin = dp(16);
        body.addView(details, detailsParams);

        LinearLayout expandedGridFrame = new LinearLayout(this);
        expandedGridFrame.setOrientation(LinearLayout.VERTICAL);
        expandedGridFrame.setClipChildren(true);
        expandedGridFrame.setClipToPadding(true);

        expandedGrid = createExpandedGrid();
        expandedGridFrame.addView(expandedGrid, new LinearLayout.LayoutParams(
                expandedGrid.preferredWidth(), 0, 1f));
        librarySearch = new LibrarySearchKeyboard(this, new LibrarySearchKeyboard.Listener() {
            @Override public void onQueryChanged(String query) { searchLibrary(query); }

            @Override public void onDone() { closeLibrarySearch(); }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                expandedGrid.preferredWidth(), ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, dp(6), 0, dp(12));
        expandedGridFrame.addView(librarySearch, searchParams);

        expandedNavigationLegend = new LinearLayout(this);
        expandedNavigationLegend.setOrientation(LinearLayout.HORIZONTAL);
        expandedNavigationLegend.setGravity(Gravity.CENTER_VERTICAL);
        expandedNavigationLegend.setPadding(dp(10), dp(6), dp(10), dp(6));
        expandedNavigationLegend.setFocusable(false);
        expandedNavigationLegend.setClickable(false);
        GradientDrawable legendBackground = gradient(0xE61A242B, 0xF20C1116, 14);
        legendBackground.setStroke(dp(1), 0x4D6E8291);
        expandedNavigationLegend.setBackground(legendBackground);
        rebuildExpandedNavigationLegend(Collections.emptyList());
        LinearLayout.LayoutParams legendParams = wrapLinear();
        legendParams.gravity = Gravity.END;
        legendParams.setMargins(dp(8), dp(8), dp(14), dp(12));
        expandedGridFrame.addView(expandedNavigationLegend, legendParams);

        body.addView(expandedGridFrame, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // The filter row spans exactly the covers: Filtry starts at the first column's left
        // edge and Wyszukaj ends at the last column's right edge.
        LinearLayout.LayoutParams barParams =
                (LinearLayout.LayoutParams) libraryFilterBar.getLayoutParams();
        barParams.width = expandedGrid.preferredWidth() - 2 * expandedGrid.coverInset();
        barParams.leftMargin = detailsParams.width + detailsParams.rightMargin
                + expandedGrid.coverInset();
    }

    private void rebuildExpandedNavigationLegend(List<ControllerInfo> controllers) {
        if (expandedNavigationLegend == null) return;
        boolean playStation = usesPlayStationButtons(controllers);
        expandedNavigationLegend.removeAllViews();
        addNavigationLegendItem(ControllerGlyphs.text(
                        playStation, ControllerGlyphs.Button.RIGHT_STICK),
                getString(R.string.playnite_legend_filters));
        addNavigationLegendItem(ControllerGlyphs.text(
                        playStation, ControllerGlyphs.Button.WEST),
                getString(R.string.playnite_legend_reset));
        addNavigationLegendItem(ControllerGlyphs.text(
                        playStation, ControllerGlyphs.Button.NORTH),
                getString(R.string.playnite_legend_search));
        addNavigationLegendItem(ControllerGlyphs.text(
                        playStation, ControllerGlyphs.Button.CANCEL),
                getString(R.string.playnite_legend_back));
    }

    private boolean usesPlayStationButtons(List<ControllerInfo> controllers) {
        for (ControllerInfo controller : controllers) {
            String name = controller.name.toLowerCase(Locale.ROOT);
            if (name.contains("dualsense") || name.contains("dualshock")
                    || name.contains("playstation") || name.contains("sony interactive")
                    || name.contains("wireless controller")) {
                return true;
            }
        }
        return false;
    }

    private void addNavigationLegendItem(String glyph, String label) {
        addLegendSeparator(expandedNavigationLegend, 20, 10);
        TextView button = controllerGlyph(glyph, 25);
        expandedNavigationLegend.addView(button, new LinearLayout.LayoutParams(dp(25), dp(25)));
        addLegendDescription(expandedNavigationLegend, label, 10, 6);
    }

    private void addLegendSeparator(LinearLayout container, int height, int margin) {
        if (container.getChildCount() > 0) {
            View separator = new View(this);
            separator.setBackgroundColor(0x336E8291);
            LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                    dp(1), dp(height));
            separatorParams.setMargins(dp(margin), 0, dp(margin), 0);
            container.addView(separator, separatorParams);
        }
    }

    private TextView controllerGlyph(String glyph, int size) {
        TextView button = text(glyph, size - 3, Color.WHITE, false);
        button.setTypeface(ControllerGlyphs.typeface(this));
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private void addLegendDescription(LinearLayout container, String label,
                                      int textSize, int leftMargin) {
        TextView description = text(label, textSize, 0xFFD7E4EA, true);
        LinearLayout.LayoutParams descriptionParams = wrapLinear();
        descriptionParams.leftMargin = dp(leftMargin);
        container.addView(description, descriptionParams);
    }

    private void buildLoadingLayer(FrameLayout container) {
        loadingLayer = new FrameLayout(this);
        loadingLayer.setVisibility(View.GONE);
        container.addView(loadingLayer, match());
    }

    private void buildSidePanel() {
        modalLayer = new FrameLayout(this);
        modalLayer.setFocusable(false);
        modalLayer.setClickable(true);
        modalLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        modalLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        menuBackdrop = new ConsoleMenuBackdrop(this, isStreamHome());
        menuBackdrop.setOnClickListener(v -> hideSidePanel());
        modalLayer.addView(menuBackdrop, match());
        sidePanelScroll = new LockableScrollView(this);
        sidePanelScroll.setFillViewport(true);
        sidePanelScroll.setVerticalScrollBarEnabled(false);
        // Rows below the fold fade out instead of being cut by the panel edge.
        sidePanelScroll.setVerticalFadingEdgeEnabled(true);
        sidePanelScroll.setFadingEdgeLength(dp(32));
        sidePanel = new LinearLayout(this);
        sidePanel.setOrientation(LinearLayout.VERTICAL);
        sidePanelScroll.addView(sidePanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        consolePanel = new ConsolePanel(this, sidePanelScroll, menuBackdrop, reducedMotion);
        libraryFiltersPanel = new LibraryFiltersPanel(this, consolePanel, hostGatewayStore,
                childLibraryFilter, libraryFiltersHost());
        settingsScreen = new ConsoleSettingsScreen(this, reducedMotion, isStreamHome());
        gameModeStep = new GameModeStep(this, preferences, hostGatewayClient, executor,
                mainHandler);
        userMenu = new ConsoleUserMenu(this, consolePanel, hostGatewayClient, executor,
                mainHandler, new ConsoleUserMenu.Presenter() {
                    @Override public void show(String eyebrow, String title, String details,
                                               View... rows) {
                        showSidePanel(eyebrow, title, details, rows);
                    }

                    @Override public boolean showing(String eyebrow, String title) {
                        return sideDialog != null && sideDialog.isShowing()
                                && (eyebrow + "\n" + title).equals(currentPanelKey);
                    }
                }, () -> openSettingsFromUserMenu(SETTINGS_DISCORD),
                () -> openSettingsFromUserMenu(SETTINGS_PROFILE));
        streamSettings = new ConsoleStreamSettings(this, settingsScreen, this::reportConnection);
        systemAudioSettings = new ConsoleSystemAudioSettings(this, settingsScreen,
                hostGatewayClient, executor, mainHandler);
        discordSettings = new ConsoleDiscordSettings(this, settingsScreen, hostGatewayClient,
                hostGatewayStore, executor, mainHandler, () -> {
                    if (discordSocialPanelController != null) {
                        discordSocialPanelController.accountUnlinked();
                    }
                });
        modalLayer.addView(sidePanelScroll, new FrameLayout.LayoutParams(dp(ConsolePanel.WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        // Community is a full-screen, bounded composition with its own feed/detail scrollers.
        // Keeping it out of the generic ScrollView prevents the modal root from stealing focus
        // and prevents the fixed header/footer from being measured as scrollable content.
        communityPanelHost = new FrameLayout(this);
        communityPanelHost.setVisibility(View.GONE);
        communityPanelHost.setFocusable(false);
        modalLayer.addView(communityPanelHost, match());
        sideDialog = new android.app.Dialog(this);
        sideDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sideDialog.setContentView(modalLayer);
        sideDialog.setCanceledOnTouchOutside(false);
        sideDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (discordDmShortcut != null && discordDmShortcut.handle(event)) return true;
            if ("discord.community".equals(currentPanelKey)
                    && discordSocialPanelController != null
                    && discordSocialPanelController.handleCommunityBack(event)) {
                return true;
            }
            if ("discord.community".equals(currentPanelKey)
                    && discordSocialPanelController != null
                    && discordSocialPanelController.handleCommunityKey(event)) {
                return true;
            }
            if ((keyCode == KeyEvent.KEYCODE_BACK || ("discord.community".equals(currentPanelKey)
                    && keyCode == KeyEvent.KEYCODE_BUTTON_B)) && event.getAction() == KeyEvent.ACTION_UP) {
                handlePanelBack();
                return true;
            }
            return isGuideKey(event);
        });
        Window window = sideDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    /** Every host's tile and the selected host's bar; under an entry intro, after its end. */
    private void renderHosts() {
        ConsoleIntroView.afterIntro(introView, "hosts", () -> {
            if (selectedHostUuid != null && !hosts.containsKey(selectedHostUuid)) {
                selectedHostUuid = null;
            }
            renderHostSelection();
            updateHostSelector();
            refreshDiscordIndicator();
            wireHomeFocusNavigation();
            ComputerDetails selected = hosts.get(selectedHostUuid);
            if (!hostSelectionVisible && profileGateHostUuid == null && selected != null
                    && appRow != null && appRow.getChildCount() <= 1) {
                selectHost(selected, false);
            }
        });
    }

    /**
     * The channels coalesce until their flush; under an entry intro the flush, with every
     * channel queued meanwhile, waits for the intro's end.
     */
    private void queueConsoleUpdates(int channels) {
        if (channels == ConsoleUpdateChannels.NONE) return;
        pendingConsoleUpdateChannels |= channels;
        if (consoleUpdatePosted) return;
        consoleUpdatePosted = true;
        mainHandler.post(() -> ConsoleIntroView.afterIntro(introView, "console.updates",
                this::flushConsoleUpdates));
    }

    private void flushConsoleUpdates() {
        consoleUpdatePosted = false;
        int channels = pendingConsoleUpdateChannels;
        pendingConsoleUpdateChannels = ConsoleUpdateChannels.NONE;
        ComputerDetails selected = hosts.get(selectedHostUuid);

        if (ConsoleUpdateChannels.has(channels, ConsoleUpdateChannels.HOST_SELECTION)) {
            renderHostSelection();
        }
        if (ConsoleUpdateChannels.has(channels, ConsoleUpdateChannels.SELECTED_HOST)) {
            updateHostSelector();
        }
        if (ConsoleUpdateChannels.has(channels, ConsoleUpdateChannels.INTEGRATIONS)) {
            if (selected != null) updateProfileSelector(selected);
            refreshDiscordIndicator();
        }
        if (selected != null && ConsoleUpdateChannels.has(
                channels, ConsoleUpdateChannels.APPLICATIONS)) {
            renderAppsAsync(selected);
        } else if (selected != null && ConsoleUpdateChannels.has(
                channels, ConsoleUpdateChannels.SESSION)
                && !currentPlayniteGames.isEmpty()) {
            renderLibraryForSessionChange(selected);
        }
        if (ConsoleUpdateChannels.has(channels, ConsoleUpdateChannels.HOST_SELECTION)) {
            wireHomeFocusNavigation();
        }
        if (ConsoleUpdateChannels.has(channels, ConsoleUpdateChannels.SESSION)) {
            offerTerminationCheck(selected);
        }
    }

    /**
     * The TV stopped its stream and the host did not confirm the session ended (a fact of the
     * session projection): the Home says so once with "Check again", and drops the offer when
     * the host answers.
     */
    private void offerTerminationCheck(ComputerDetails host) {
        boolean unconfirmed = host != null
                && resolveSessionSnapshot(host, selectedProfileId(host.uuid))
                .terminationUnconfirmed;
        if (!unconfirmed) {
            if (terminationCheckAction != null && terminationCheckAction.isAttachedToWindow()) {
                hideSidePanel();
            }
            terminationCheckAction = null;
            return;
        }
        if (terminationCheckAction == null) showTerminationCheck(host);
    }

    private void showTerminationCheck(ComputerDetails host) {
        TextView check = consolePanel.action(getString(R.string.console_check_again));
        terminationCheckAction = check;
        check.setOnClickListener(view -> {
            showSidePanelBusy(host.name,
                    getString(R.string.console_status_termination_unconfirmed),
                    getString(R.string.console_checking_session));
            // A forced read of the host: its answer resolves the fact, silence keeps it.
            refreshSessionProjection(host, accepted -> {
                if (isDestroyed()) return;
                ComputerDetails current = currentHost(host.uuid);
                if (current != null && resolveSessionSnapshot(current,
                        selectedProfileId(current.uuid)).terminationUnconfirmed) {
                    showTerminationCheck(current);
                } else {
                    hideSidePanel();
                    terminationCheckAction = null;
                }
                queueSessionPresentationUpdate();
            });
        });
        showSidePanel(host.name, getString(R.string.console_status_termination_unconfirmed),
                "", check);
    }

    private void updateHostSelector() {
        setCardBindBatch(true);
        try {
            updateHostSelectorInBatch();
        } finally {
            setCardBindBatch(false);
        }
    }

    private void updateHostSelectorInBatch() {
        if (topBar == null) return;
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) topBar.showNoHost(getString(R.string.console_no_hosts));
        else updateHostStatus();
        updateProfileSelector(host);
        updateQuickResumeButton(host);
    }

    private String selectedProfileId(String hostId) {
        return hostGatewayStore == null ? GatewayConnection.DEFAULT_PROFILE_ID
                : hostGatewayStore.selectedIntegrationProfileId(hostId);
    }

    private HostProfileKey selectedProfileKey(String hostId) {
        return new HostProfileKey(hostId, selectedProfileId(hostId));
    }

    private boolean currentPlayniteProjectionMatches(HostProfileKey expected) {
        return HostProfileKey.isCurrentProjection(expected, currentPlayniteProfileKey,
                expected == null ? null : selectedProfileKey(expected.hostId));
    }

    private void updateProfileSelector(ComputerDetails host) {
        if (topBar == null) return;
        HostGatewayStore.ProfileSelection selection = host == null ? null
                : hostGatewayStore.profileSelection(host.uuid);
        if (selection == null || selection.selected == null) {
            profileToolbarDrawable = null;
            topBar.showProfile(null, "", "");
            return;
        }
        if (!selection.showSelector && !selection.selected.pinRequired) {
            hostGatewayStore.setSelectedIntegrationProfileId(host.uuid,
                    selection.selected.id);
        }
        HostGatewayClient.IntegrationProfile selected = selection.selected;
        String name = profileDisplayName(selected);
        HostGatewayClient.IntegrationProfile active = activeAuthorizedProfile(selection);
        boolean matches = profileWindowsMatch(selected, active);
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress == null ? null : host.activeAddress.address);
        boolean gatewayAvailable = host.state == ComputerDetails.State.ONLINE
                && connection != null;
        boolean switchable = !matches && gatewayAvailable
                && canSwitchWindowsProfile(selected, active);
        profileAvatars.load(connection, host.uuid, Collections.singletonList(selected.avatarId));
        profileToolbarDrawable = new ProfileToolbarDrawable(selected.id, selected.avatarId, name,
                switchable);
        topBar.showProfile(profileToolbarDrawable,
                name, getString(R.string.console_profile_selector_description, name));
        refreshDiscordAccess();
    }

    private boolean profileWindowsMatch(HostGatewayClient.IntegrationProfile selected,
                                        HostGatewayClient.IntegrationProfile active) {
        if (selected == null) return false;
        String expected = selected.isChild() ? selected.executionProfileId : selected.id;
        if (active != null && "active".equals(active.sessionState)
                && expected != null && !expected.isEmpty() && expected.equals(active.id)) {
            return true;
        }
        // A child may be the only authorized row; its parent is then absent from
        // /profiles. The child session projection still carries the execution match.
        return selected.isChild() && ("active".equals(selected.sessionState)
                || "running".equals(selected.sessionState));
    }

    private boolean canSwitchWindowsProfile(HostGatewayClient.IntegrationProfile selected,
                                            HostGatewayClient.IntegrationProfile active) {
        return selected != null && selected.remoteSignIn
                && (active == null || !profileWindowsMatch(selected, active));
    }

    /**
     * #47: the user menu under the avatar. Its accounts and layout belong to
     * {@link ConsoleUserMenu}; the session actions stay with their owners here.
     */
    private void showUserMenu(ComputerDetails host) {
        if (host == null) return;
        HostGatewayStore.ProfileSelection selection =
                hostGatewayStore.profileSelection(host.uuid);
        if (selection == null || selection.selected == null) return;
        HostGatewayClient.IntegrationProfile selected = selection.selected;
        HostGatewayClient.IntegrationProfile active = activeAuthorizedProfile(selection);
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        List<ConsoleUserMenu.Action> actions = new ArrayList<>();
        if (selection.showSelector) {
            actions.add(new ConsoleUserMenu.Action(getString(R.string.user_menu_change_profile),
                    true, () -> changeProfile(host)));
        }
        boolean matches = profileWindowsMatch(selected, active);
        if (active != null && !selected.isChild() && !matches) {
            actions.add(new ConsoleUserMenu.Action(getString(
                    R.string.console_use_profile_in_moonwaker, profileDisplayName(active)), true,
                    () -> {
                        selectProfile(host, active.id);
                        hideSidePanel();
                    }));
        }
        if (canSwitchWindowsProfile(selected, active)) {
            boolean enabled = host.state == ComputerDetails.State.ONLINE
                    && hostGatewayStore.loadForHost(host.uuid, address) != null
                    && windowsProfileSwitchAttempt == null;
            actions.add(new ConsoleUserMenu.Action(getString(
                    R.string.console_switch_windows_profile, profileDisplayName(selected)),
                    enabled, () -> confirmWindowsProfileSwitch(host, selected)));
        }
        GatewayConnection adult = selected.isChild() ? null
                : hostGatewayStore.loadForHost(host.uuid, address, selected.id);
        if (adult != null) {
            actions.add(new ConsoleUserMenu.Action(getString(R.string.console_add_profile), true,
                    () -> startAdultProfileCreation(host, selected,
                            created -> showUserMenu(host))));
        }
        String state = matches ? getString(R.string.user_menu_signed_in, host.name)
                : active != null ? getString(R.string.user_menu_other_signed_in,
                profileDisplayName(active), host.name)
                : getString(R.string.user_menu_signed_out, host.name);
        userMenu.show(new ConsoleUserMenu.Subject(host.uuid, host.name,
                profileDisplayName(selected), selected.isChild(), state,
                selected.remoteSignIn, selected.remoteSignInState, adult), actions);
    }

    /** "Change profile": the one profile gate, with its PIN, from a fresh authorization. */
    private void changeProfile(ComputerDetails host) {
        hideSidePanelImmediately();
        ProfileAuthorization.revoke();
        showProfileGate(host, true, false);
    }

    private void openSettingsFromUserMenu(int category) {
        hideSidePanel();
        if (!settingsScreen.isShowing()) showSettings(category);
    }

    /** Mockup 21: the selected profile picks its own avatar from the host's catalog. */
    private void showAvatarPicker(ComputerDetails host,
                                  HostGatewayClient.IntegrationProfile profile) {
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress == null ? null : host.activeAddress.address);
        if (connection == null) return;
        String name = profileDisplayName(profile);
        showSidePanelBusy(getString(R.string.console_profile_eyebrow),
                getString(R.string.console_avatar_change), getString(R.string.console_loading));
        executor.execute(() -> {
            List<HostGatewayClient.Avatar> catalog;
            try {
                catalog = profileAvatars.catalog(connection, host.uuid);
            } catch (IOException | RuntimeException unavailable) {
                catalog = null;
            }
            List<HostGatewayClient.Avatar> avatars = catalog;
            mainHandler.post(() -> {
                if (isDestroyed() || !profile.id.equals(selectedProfileId(host.uuid))) return;
                if (avatars == null) {
                    TextView retry = consolePanel.action(getString(R.string.console_avatar_retry));
                    retry.setOnClickListener(view -> showAvatarPicker(host, profile));
                    showSidePanel(getString(R.string.console_profile_eyebrow),
                            getString(R.string.console_avatar_change),
                            getString(R.string.console_avatar_unavailable), retry);
                    return;
                }
                profileAvatars.load(connection, host.uuid, ProfileAvatars.avatarIds(avatars));
                View grid = ConsoleAvatarGrid.create(this, profile.id, name, avatars,
                        profile.avatarId, id -> saveProfileAvatar(host, profile, id));
                showSidePanel(getString(R.string.console_profile_eyebrow),
                        getString(R.string.console_avatar_change),
                        getString(avatars.isEmpty() ? R.string.console_avatar_empty
                                : R.string.console_avatar_details, name), grid);
                View chosen = grid.findViewWithTag("avatar.choice:" + profile.avatarId);
                if (chosen != null) grid.post(chosen::requestFocus);
            });
        });
    }

    private void saveProfileAvatar(ComputerDetails host,
                                   HostGatewayClient.IntegrationProfile profile, String avatarId) {
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress == null ? null : host.activeAddress.address);
        if (connection == null || !profile.id.equals(connection.profileId())) return;
        executor.execute(() -> {
            boolean saved;
            try {
                hostGatewayClient.setProfileAvatar(connection, avatarId);
                saved = true;
            } catch (IOException | RuntimeException failed) {
                saved = false;
            }
            boolean result = saved;
            mainHandler.post(() -> {
                if (isDestroyed()) return;
                if (result) {
                    hideSidePanel();
                    refreshHostProfiles(host);
                } else {
                    showAvatarSaveFailure(host, profile);
                }
            });
        });
    }

    private void showAvatarSaveFailure(ComputerDetails host,
                                       HostGatewayClient.IntegrationProfile profile) {
        TextView retry = consolePanel.action(getString(R.string.console_avatar_retry));
        retry.setOnClickListener(view -> showAvatarPicker(host, profile));
        showSidePanel(getString(R.string.console_profile_eyebrow),
                getString(R.string.console_avatar_change),
                getString(R.string.console_avatar_save_failed), retry);
    }

    private void selectProfile(ComputerDetails host, String profileId) {
        HostGatewayClient.IntegrationProfile profile =
                hostGatewayStore.profileSelection(host == null ? null : host.uuid).find(profileId);
        if (profile == null) return;
        if (profile.pinRequired) {
            ProfileAuthorization.revoke();
            showPinEntry(host, profile, true, false, false);
            return;
        }
        applySelectedProfile(host, profileId);
    }

    private String profileDisplayName(HostGatewayClient.IntegrationProfile profile) {
        if (profile == null) return getString(R.string.gateway_profile_title);
        String name = profile.name == null ? "" : profile.name.trim();
        if (!name.isEmpty()) return name;
        return profile.isChild() ? getString(R.string.console_child_profile_title)
                : getString(R.string.gateway_profile_title);
    }

    private void applySelectedProfile(ComputerDetails host, String profileId) {
        ProfileAuthorization.grant(host.uuid);
        if (!changeSelectedProfile(host, profileId)) {
            // An app-list change that arrived under the PIN gate was not rendered.
            renderAppsAsync(host);
            return;
        }
        currentSunshineApps = visibleApps(host.uuid, appLists.apps(host.uuid));
        initialLocalAppsHostId = host.uuid;
        updateHostSelector();
        refreshHostProfiles(host);
        refreshDiscordIndicator();
        refreshUsbMicrophoneIndicator();
        loadPlayniteForHost(host);
        refreshSessionProjection(host, null);
        reconcileHostStreamRecord(host);
    }

    private boolean changeSelectedProfile(ComputerDetails host, String profileId) {
        if (host == null) return false;
        String selected = GatewayConnection.normalizeProfileId(profileId);
        if (selected.equals(selectedProfileId(host.uuid))) return false;
        String previousProfileId = selectedProfileId(host.uuid);
        cancelChildLaunches(host.uuid, previousProfileId);
        HostGatewayClient.IntegrationProfile nextProfile = integrationProfile(
                host.uuid, selected);
        if (nextProfile != null && nextProfile.isChild()) {
            hostGatewayStore.setAutomaticIntegrationProfileId(host.uuid, "");
        }
        childEligibilityLaunchToken++;
        mainHandler.removeCallbacks(childEligibilityBoundary);
        childEligibilityObservedAt.remove(host.uuid + "\n" + previousProfileId);
        childEligibilityObservedAt.remove(host.uuid + "\n" + selected);
        invalidateChildManagement();
        cancelWindowsProfileSwitch(false);
        cancelQueuedPlay();
        if (sessionOrchestrator != null) sessionOrchestrator.cancel();
        hostGatewayStore.setSelectedIntegrationProfileId(host.uuid, selected);
        if (host.uuid.equalsIgnoreCase(selectedHostUuid)) refreshDiscordAccess();
        profileGeneration.incrementAndGet();
        cancelPlayniteRequest();
        cancelPlayniteArtworkPrefetch();
        appListRenderFloor = appListRenderGeneration.incrementAndGet();
        resetInitialCarouselArtworkWarmup();
        gameOperationsController.close();
        gameOperationsController = new GameOperationsController(
                hostGatewayClient, executor, mainHandler::post);
        currentPlayniteGames = Collections.emptyList();
        currentPlayniteProfileKey = null;
        currentSunshineApps = Collections.emptyList();
        currentPlayniteHostUuid = null;
        renderedPlayniteItems = Collections.emptyList();
        allPlayniteItems = Collections.emptyList();
        unfilteredPlayniteItems = Collections.emptyList();
        playniteSessionProjection = null;
        initialLibraryPresentation = null;
        clearArtwork();
        libraryTransitionCoordinator.cancel();
        selectedGameMetadata.setVisibility(View.INVISIBLE);
        selectedGameDescription.setText("");
        selectedGameFactsRow.setVisibility(View.GONE);
        if (selectedGameTitleRow != null) selectedGameTitleRow.setVisibility(View.INVISIBLE);
        renderedAppsSignature = null;
        return true;
    }

    private void refreshHostProfiles(ComputerDetails host) {
        if (host == null) return;
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid, address);
        if (connection == null) return;
        int token = profileGeneration.incrementAndGet();
        String hostId = host.uuid;
        executor.execute(() -> {
            HostGatewayClient.IntegrationProfiles profiles;
            HostGatewayClient.Capabilities capabilities = null;
            try {
                profiles = hostGatewayClient.getIntegrationProfiles(connection);
            } catch (IOException | RuntimeException unavailable) {
                return;
            }
            try {
                capabilities = hostGatewayClient.getCapabilities(connection);
            } catch (IOException | RuntimeException unavailable) {
                // The profile data remains usable; child controls stay hidden until capability is known.
            }
            refreshHostPowerStatus(hostId, connection);
            HostGatewayClient.Capabilities capabilityResult = capabilities;
            mainHandler.post(() -> ConsoleIntroView.afterEntrance(introView, () -> {
                if (token != profileGeneration.get() || !hostId.equals(selectedHostUuid)) return;
                String previous = selectedProfileId(hostId);
                HostGatewayClient.IntegrationProfiles cachedBefore =
                        hostGatewayStore.profiles(hostId);
                HostGatewayClient.IntegrationProfile previousProfile =
                        cachedBefore == null ? null : cachedBefore.find(previous);
                HostGatewayClient.IntegrationProfile refreshedProfile = profiles.find(previous);
                boolean childWasInvalidated = previousProfile != null
                        && previousProfile.isChild()
                        && (refreshedProfile == null || !refreshedProfile.isChild()
                        || !refreshedProfile.enabled || !refreshedProfile.useProfile);
                rememberChildCapability(hostId, profiles, capabilityResult);
                if (capabilityResult != null) {
                    hostPlusByHost.put(hostId, capabilityResult.hostPlus);
                    capturesByHost.put(hostId, capabilityResult.captures);
                }
                showcaseCapabilities = capabilityResult;
                saveHostProfiles(hostId, profiles);
                if (refreshedProfile != null && refreshedProfile.isChild()) {
                    childEligibilityObservedAt.put(hostId + "\n" + previous,
                            SystemClock.elapsedRealtime());
                }
                if (childWasInvalidated) {
                    hostGatewayStore.setAutomaticIntegrationProfileId(hostId, "");
                    ComputerDetails current = currentHost(hostId);
                    ProfileAuthorization.revoke();
                    if (current != null && !isDestroyed()) showProfileGate(current, true, false);
                    return;
                }
                if (isDestroyed()) return; // Profile data and grants above outlive the UI.
                HostGatewayStore.ProfileSelection selection =
                        hostGatewayStore.profileSelection(hostId);
                if (selection.selected != null
                        && !previous.equals(selection.selected.id)) {
                    selectProfile(currentHost(hostId), selection.selected.id);
                } else {
                    String selectedProfileId = selection.selected == null
                            ? previous : selection.selected.id;
                    HostGatewayClient.IntegrationProfile selectedProfile =
                            profiles.find(selectedProfileId);
                    if (selectedProfile != null && selectedProfile.isChild()
                            && "pairing_required".equalsIgnoreCase(selectedProfile.reason)) {
                        tryBindExistingVibepolloIdentity(hostId, selectedProfileId, false);
                    }
                    updateProfileSelector(currentHost(hostId));
                    updateHostStatus();
                    updateHostSelectionTile(currentHost(hostId));
                    if (selectedProfile == null || !selectedProfile.isChild()) announceNewFeatures();
                }
            }));
        });
        // A separate read, so the profiles never wait for it.
        executor.execute(() -> {
            ReleaseUpdates updates;
            try {
                updates = hostGatewayClient.getUpdates(connection,
                        BuildConfig.RELEASE_SIGNING_KEY, getPackageName());
            } catch (IOException | RuntimeException unavailable) {
                return; // No update notices for this host until the next refresh.
            }
            mainHandler.post(() -> ConsoleIntroView.afterEntrance(introView, () -> {
                if (token != profileGeneration.get() || !hostId.equals(selectedHostUuid)) return;
                releaseUpdates = updates;
                if (!isDestroyed()) announceUpdates();
            }));
        });
    }

    private void refreshVisibleHostProfiles() {
        int generation = hostProfileRefreshGeneration.incrementAndGet();
        for (ComputerDetails host : hosts.values()) {
            if (!newlyDiscoveredHosts.contains(host.uuid)) {
                hostProfileRefreshGenerations.put(host.uuid, generation);
                refreshVisibleHostProfile(host, generation);
            }
        }
    }

    private void refreshVisibleHostProfile(ComputerDetails host, int generation) {
        if (host == null || host.state != ComputerDetails.State.ONLINE
                || host.pairState != PairingManager.PairState.PAIRED) return;
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid, address);
        if (connection == null || !hostProfileRefreshInFlight.add(host.uuid)) return;
        String hostId = host.uuid;
        executor.execute(() -> {
            HostGatewayClient.IntegrationProfiles profiles = null;
            try {
                profiles = hostGatewayClient.getIntegrationProfiles(connection);
            } catch (IOException | RuntimeException unavailable) {
                // Keep the last cached presentation when this read-only refresh is unavailable.
            }
            HostGatewayClient.IntegrationProfiles result = profiles;
            mainHandler.post(() -> {
                if (isDestroyed()) return;
                hostProfileRefreshInFlight.remove(hostId);
                Integer latest = hostProfileRefreshGenerations.get(hostId);
                if (latest == null || latest != generation || !hostSelectionVisible) {
                    if (latest != null && hostSelectionVisible) {
                        refreshVisibleHostProfile(hosts.get(hostId), latest);
                    }
                    return;
                }
                ComputerDetails current = hosts.get(hostId);
                if (result != null && current != null) {
                    saveHostProfiles(hostId, result);
                    updateHostSelectionTile(current);
                }
            });
        });
    }

    private void updateHostStatus() {
        if (topBar == null) return;
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) return;
        // Read before the text: a minute that passes in between at worst renders once more.
        renderedSessionDisplayKey = renderedSessionSnapshot(host).display.key();
        String status = compactHostStatus(host);
        topBar.showHost(host.name, status, ConsoleTopBar.statusDotColor(
                consoleHostState(host, renderedSessionSnapshot(host))),
                getString(R.string.console_host_status_description, host.name, status));
        // The library line reads the same host facts (a requested sleep).
        updatePlayniteLibraryStatus(host);
    }

    private String compactHostStatus(ComputerDetails host) {
        SessionSnapshot snapshot = renderedSessionSnapshot(host);
        if (snapshot.isSuspended()) {
            SuspendedSessionStore.Session suspended =
                    suspendedSession(host.uuid, selectedProfileId(host.uuid));
            String title = suspended == null || suspended.title.isEmpty()
                    ? getString(R.string.console_game) : suspended.title;
            if (host.state != ComputerDetails.State.ONLINE) {
                return getString(R.string.console_status_suspended_short, title);
            }
            return !snapshot.suspensionSleepObserved
                    && !hostStateController.isWaking(host.uuid)
                    ? getString(R.string.console_status_suspending_short)
                    : getString(R.string.console_status_suspended_short, title);
        }
        return hostStatus(host, snapshot);
    }

    private void updateQuickResumeButton(ComputerDetails host) {
        if (quickResumeButton == null) return;
        SessionSnapshot snapshot = renderedSessionSnapshot(host);
        boolean activeSession = host != null && ConsoleActionCatalog.isOnline(host)
                && ConsoleActionCatalog.isPaired(host) && snapshot.hasActiveSession();
        boolean visible = activeSession && !CONSOLE_UI_V2;
        if (CONSOLE_UI_V2 && host != null) {
            boolean ready = ConsoleActionCatalog.isOnline(host)
                    && ConsoleActionCatalog.isPaired(host);
            if (ready || PlayniteIdentityResolutionPolicy.decide(ConsoleActionCatalog.isOnline(
                    host), ConsoleActionCatalog.isPaired(host), snapshot.state)
                    != PlayniteIdentityResolutionPolicy.Action.PRESERVE) {
                refreshSessionProjection(host, null);
            }
        }
        boolean restoreFocus = quickResumeButton.hasFocus() && !visible;
        quickResumeButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        quickResumeButton.setEnabled(visible);
        if (visible) {
            String appName = appDisplayName(findAppName(host, snapshot.hostGameAppId));
            quickResumeButton.setText(appName == null
                    ? getString(R.string.console_quick_resume)
                    : getString(R.string.console_quick_resume_app, appName));
            quickResumeButton.setContentDescription(getString(
                    R.string.console_quick_resume_description,
                    appName == null ? getString(R.string.console_status_active_session) : appName));
        } else if (restoreFocus) {
            View target = topBar.hostStatus();
            target.post(target::requestFocus);
        }
        updateLibraryActionVisibility();
        wireHomeFocusNavigation();
    }

    private void refreshSessionProjection(ComputerDetails host,
                                           Consumer<Boolean> completion) {
        if (host == null) return;
        refreshSessionProjection(host, selectedProfileKey(host.uuid), completion);
    }

    private void refreshSessionProjection(ComputerDetails host, HostProfileKey requestKey,
                                           Consumer<Boolean> completion) {
        if (host == null) return;
        String stateKey = requestKey.cacheKey();
        if (!ConsoleActionCatalog.isOnline(host) || !ConsoleActionCatalog.isPaired(host)) {
            invalidateSessionProjectionRequest(host.uuid);
            if (completion != null) completion.accept(false);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (completion != null) invalidateSessionProjectionRequest(host.uuid);
        if (completion == null && !sessionProjectionRefreshDue(now,
                sessionProjectionRequestedAt.getOrDefault(stateKey, 0L))) return;
        if (!sessionProjectionRefreshInFlight.add(stateKey)) {
            // Still in flight only for a profile that is not selected, whose result is never
            // accepted (sameRenderScope); every exit answers a waiting completion.
            if (completion != null) completion.accept(false);
            return;
        }
        sessionProjectionRequestedAt.put(stateKey, now);
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForPolling(host.uuid, address, requestKey.profileId);
        if (connection == null) {
            sessionProjectionRefreshInFlight.remove(stateKey);
            if (completion != null) completion.accept(false);
            return;
        }
        int requestGeneration = ++sessionProjectionRequestGeneration;
        sessionProjectionRequestGenerations.put(stateKey, requestGeneration);
        String expectedHostId = host.uuid;
        executor.execute(() -> {
            SessionSnapshot resolved = null;
            try {
                PlayniteTransitionGateway gateway = new PlayniteTransitionGateway(sessionModel,
                        expectedHostId, connection,
                        gatewayActorContext(expectedHostId, requestKey.profileId),
                        hostGatewayClient, SystemClock::uptimeMillis);
                resolved = gateway.snapshot().sessionSnapshot;
            } catch (IOException | RuntimeException ignored) {
                // The projection owner preserves safe local transport and bounded liveness.
            }
            SessionSnapshot projected = resolved;
            mainHandler.post(() -> {
                if (isDestroyed()) return;
                Integer currentGeneration = sessionProjectionRequestGenerations.get(stateKey);
                if (currentGeneration != null && currentGeneration == requestGeneration) {
                    sessionProjectionRefreshInFlight.remove(stateKey);
                }
                ComputerDetails latestHost = currentHost(expectedHostId);
                GatewayConnection latestConnection = latestHost == null ? null
                        : hostGatewayStore.loadForHost(expectedHostId,
                        latestHost.activeAddress == null ? null : latestHost.activeAddress.address,
                        requestKey.profileId);
                boolean sameRenderScope = latestHost != null
                        && expectedHostId.equals(latestHost.uuid)
                        && requestKey.equals(selectedProfileKey(expectedHostId))
                        && sameGatewayProfile(connection, latestConnection);
                // Renders only when what the library shows changed (presentation key).
                if (sameRenderScope && active && expectedHostId.equals(selectedHostUuid)
                        && projected != null) {
                    renderLibraryForSessionChange(currentHost(expectedHostId));
                }
                if (completion != null) completion.accept(projected != null && sameRenderScope);
            });
        });
    }

    /**
     * Session polls change bookkeeping fields (revisions, ordinals) every few seconds. The full
     * library render runs only when something the library screen shows changed; under an entry
     * intro, after its end.
     */
    private void renderLibraryForSessionChange(ComputerDetails host) {
        if (host == null) return;
        ConsoleIntroView.afterIntro(introView, "library.session", () -> {
            SessionSnapshot snapshot = renderedSessionSnapshot(host);
            if (!sessionPresentationKey(host, snapshot).equals(renderedSessionPresentationKey)) {
                renderPlayniteLibrary(host, currentSunshineApps);
            } else if (!snapshot.display.key().equals(renderedSessionDisplayKey)) {
                // Only the status line's minute changed (at most once a minute): no render.
                updateHostStatus();
                updateHostSelectionTile(host);
            }
        });
    }

    /** Everything the library screen shows from a session snapshot. */
    private String sessionPresentationKey(ComputerDetails host, SessionSnapshot snapshot) {
        return PlayniteSessionPresentation.project(snapshot, unfilteredPlayniteItems,
                preferences.getString("selected_playnite." + host.uuid, "")).signature()
                + '|' + snapshot.state + '|' + snapshot.observationState
                + '|' + snapshot.playniteGameId + '|' + snapshot.processEvidence
                + '|' + snapshot.processId + '|' + snapshot.processToken
                // Not projection identity, but the app cards' running badge reads it.
                + '|' + snapshot.streamerAppId
                + '|' + consoleHostState(host, snapshot)
                + '|' + hostStatus(host, snapshot, false)
                + '|' + queuedPlayniteGameId(host.uuid);
    }

    /** The game whose Play waits on its tile for a close to be confirmed; "" when none. */
    private String queuedPlayniteGameId(String hostUuid) {
        PlayIntent waiting = sessionOrchestrator == null ? null : sessionOrchestrator.queued();
        return waiting != null && waiting.kind == PlayIntent.Kind.PLAYNITE_GAME
                && waiting.hostId.equalsIgnoreCase(hostUuid)
                ? SessionSnapshot.normalize(waiting.playniteGameId) : "";
    }

    private boolean isQueuedApp(String hostUuid, int appId) {
        PlayIntent waiting = sessionOrchestrator == null ? null : sessionOrchestrator.queued();
        return waiting != null && waiting.kind == PlayIntent.Kind.SUNSHINE_APP
                && waiting.hostId.equalsIgnoreCase(hostUuid) && waiting.sunshineAppId == appId;
    }

    private boolean isQueuedPlayniteItem(String hostUuid, PlayniteDashboardItem item) {
        String gameId = queuedPlayniteGameId(hostUuid);
        return !gameId.isEmpty() && gameId.equals(SessionSnapshot.normalize(item.stableId()));
    }

    /** What the game tiles render from the session: its projection and the queued Play. */
    private String playniteTileSessionSignature(
            PlayniteSessionPresentation.Projection projection, ComputerDetails host) {
        return projection.signature() + '|' + queuedPlayniteGameId(host.uuid);
    }

    private void invalidateSessionProjectionRequest(String hostId) {
        String key = selectedProfileKey(hostId).cacheKey();
        sessionProjectionRequestGenerations.put(key,
                ++sessionProjectionRequestGeneration);
        sessionProjectionRefreshInFlight.remove(key);
    }

    static boolean sessionProjectionRefreshDue(long now, long requestedAt) {
        return requestedAt == 0L || now < requestedAt || now - requestedAt >= 5_000L;
    }

    static boolean sameGatewayProfile(GatewayConnection first, GatewayConnection second) {
        return first != null && second != null
                && first.profileId().equals(second.profileId())
                && first.endpoint().equals(second.endpoint())
                && first.certificateSha256().equals(second.certificateSha256());
    }

    private void resumeSelectedSession() {
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) {
            updateHostSelector();
            return;
        }
        resumeSession(host);
    }

    private SessionSnapshot resolveSessionSnapshot(ComputerDetails host) {
        return projectSession(host, host == null ? null : selectedProfileId(host.uuid), false);
    }

    /** Render-only projection of the delivered snapshot; publishes nothing to the owner. */
    private SessionSnapshot renderedSessionSnapshot(ComputerDetails host) {
        String profileId = host == null ? null : selectedProfileId(host.uuid);
        if (cardBindBatchDepth == 0 || host == null) return projectSession(host, profileId, true);
        return cardBindBatchMemo.computeIfAbsent(host.uuid + "\n" + profileId,
                key -> projectSession(host, profileId, true));
    }

    private SessionSnapshot resolveSessionSnapshot(ComputerDetails host, String profileId) {
        return projectSession(host, profileId, false);
    }

    private SessionSnapshot projectSession(ComputerDetails host, String profileId, boolean render) {
        SessionModel.Snapshot retained = render
                ? sessionSubscriber.snapshot() : sessionModel.snapshot();
        if (host == null || retained == null) { // Nothing is delivered before the first onStart.
            return SessionSnapshot.pending("", GatewayConnection.DEFAULT_PROFILE_ID,
                    GatewayConnection.DEFAULT_PROFILE_ID);
        }
        HostProfileKey profileKey = new HostProfileKey(host.uuid, profileId);
        String executionProfileId = executionProfileId(host.uuid, profileKey.profileId);
        SessionModel.Records records = retained.records;
        SuspendedSessionStore.Session suspended = records.suspendedForExecution(
                host.uuid, executionProfileId);
        HostPowerStateStore.State sleep = records.hostPower(host.uuid);
        SessionResumeManager.PendingSession pending = records.pending;
        int sessionAppId = retained.hostId.equalsIgnoreCase(host.uuid)
                && retained.executionProfileId.equals(executionProfileId)
                && retained.appId != 0 ? retained.appId
                : pending != null && pending.hostUuid.equalsIgnoreCase(host.uuid)
                && pending.executionProfileId.equals(executionProfileId)
                ? pending.appId : 0;
        boolean neutralStreamTarget = pending != null
                && pending.neutralStreamTarget
                && pending.hostUuid.equalsIgnoreCase(host.uuid)
                && pending.executionProfileId.equals(executionProfileId)
                && pending.appId == sessionAppId;
        if (!neutralStreamTarget && sessionAppId != 0) {
            neutralStreamTarget = PlayniteTargetResolver.isNeutralStream(
                    PlayniteTargetResolver.findById(appLists.apps(host.uuid), sessionAppId));
        }
        SessionStateResolver.LocalFacts local = new SessionStateResolver.LocalFacts(suspended,
                records.recentlyEndedForExecution(host.uuid, executionProfileId), pending,
                sleep != null, sleep != null && sleep.downObservedAt > 0L,
                neutralStreamTarget);
        return SessionStateResolver.render(retained, host.uuid, profileKey.profileId,
                executionProfileId, local, SystemClock.uptimeMillis());
    }

    private SuspendedSessionStore.Session suspendedSession(
            String hostId, String actorProfileId) {
        return sessionModel.records().suspendedForExecution(
                hostId, executionProfileId(hostId, actorProfileId));
    }
    private String hostStatus(ComputerDetails host, SessionSnapshot snapshot) {
        return hostStatus(host, snapshot, true);
    }

    /**
     * {@code timed}: with the running time of a live session or a running game. The library's
     * presentation key reads the status without it, so a passing minute renders no library.
     */
    private String hostStatus(ComputerDetails host, SessionSnapshot snapshot, boolean timed) {
        // A power command this client sent is known at once and names the whole host, unless
        // a live session or a game being closed still has its own status to show.
        if (showsHostSleep(snapshot)) return getString(powerStatus(host, snapshot));
        int warmUp = sessionSubscriber.warmUpStatus(host.uuid, snapshot.neutralStreamTarget);
        if (warmUp != 0) return getString(warmUp);
        if (snapshot.terminationUnconfirmed) {
            return getString(R.string.console_status_termination_unconfirmed);
        }
        String closing = closingStatus(snapshot);
        if (closing != null) return closing;
        if (snapshot.isSuspended()) {
            SuspendedSessionStore.Session suspended =
                    suspendedSession(host.uuid, selectedProfileId(host.uuid));
            String title = suspended == null || suspended.title.isEmpty()
                    ? getString(R.string.console_game) : suspended.title;
            if (host.state != ComputerDetails.State.ONLINE) {
                return getString(R.string.console_status_sleeping_suspended_game, title);
            }
            if (!snapshot.suspensionSleepObserved
                    && !hostStateController.isWaking(host.uuid)) {
                return getString(R.string.console_status_suspending_game, title);
            }
            return getString(R.string.console_status_suspended_game, title);
        }
        ConsoleHostPresentation.State state = consoleHostState(host, snapshot);
        if (state == ConsoleHostPresentation.State.ACTIVE_SESSION) {
            // Between observations the session's game is the one last seen running.
            String gameId = snapshot.playniteGameId.isEmpty()
                    ? snapshot.display.runningGameId : snapshot.playniteGameId;
            String gameName = libraryGameName(gameId);
            String appName = gameName != null ? gameName
                    : appDisplayName(findAppName(host, snapshot.hostGameAppId));
            String status = appName == null ? getString(R.string.console_status_active_session)
                    : getString(R.string.console_status_active_app, appName);
            return timed ? statusWithMinutes(status, ConsoleHostPresentation.sessionMinutes(
                    snapshot, gameName == null ? "" : gameId)) : status;
        }
        boolean localWarmUp = host.uuid.equalsIgnoreCase(warmUpClientHostId);
        boolean preparingHost = sessionOrchestrator != null
                && sessionOrchestrator.hasPreparationForHost(host.uuid);
        if (localWarmUp && preparingHost) {
            if (warmUpStatus == WARM_UP_WAKING) {
                return getString(R.string.console_warm_up_waking);
            }
            if (warmUpStatus == WARM_UP_PREPARING) {
                return getString(R.string.console_warm_up_preparing);
            }
        }
        if (localWarmUp
                && warmUpStatus == WARM_UP_PREPARING
                && sessionSubscriber.reconnectRequired(host.uuid)
                && !preparingHost) {
            return getString(R.string.console_warm_up_error);
        }
        if (localWarmUp && warmUpStatus == WARM_UP_ERROR) {
            return getString(R.string.console_warm_up_error);
        }
        if (ConsoleHostPresentation.holdsPausedSession(state, snapshot)) {
            String name = pausedSessionName(host, snapshot);
            return name == null ? getString(R.string.console_status_paused_stream)
                    : getString(R.string.console_status_suspended_game, name);
        }
        if (ConsoleHostPresentation.showsRunningGame(state, snapshot)) {
            // Named from this profile's library only: a game it does not list stays unnamed.
            String name = libraryGameName(snapshot.display.runningGameId);
            if (name != null) {
                String status = getString(R.string.console_status_game_running, name);
                return timed ? statusWithMinutes(status, snapshot.display.gameMinutes) : status;
            }
        }
        if (state == ConsoleHostPresentation.State.PROFILE_ATTENTION) {
            ConsoleHostPresentation.Profile profile = consoleProfile(host);
            if (!profile.needsAttention()) return getString(R.string.first_run_todo_status);
            // Windows runs another session than the selected profile's (after a shut down, a
            // sign-in on the PC): named here; the profile panel offers the switch back.
            switch (profile.state) {
                case OTHER_AUTHORIZED_ACTIVE:
                    return getString(R.string.console_status_other_profile_active,
                            profile.activeName);
                case OTHER_ACTIVE:
                    return getString(R.string.console_status_other_user_active);
                case SIGN_IN_REQUIRED:
                    return getString(R.string.console_status_windows_sign_in_required);
                default:
                    break;
            }
        }
        return getString(ConsoleHostPresentation.status(state));
    }

    /** The selected profile's library name of {@code gameId}, or null; reads memory only. */
    private String libraryGameName(String gameId) {
        if (gameId.isEmpty()) return null;
        for (PlayniteDashboardItem item : unfilteredPlayniteItems) {
            if (gameId.equals(SessionSnapshot.normalize(item.stableId()))) return item.game.name;
        }
        return null;
    }

    /** "Stream - Game - 12 min"; a duration under a minute or unknown (-1) adds nothing. */
    private String statusWithMinutes(String status, long minutes) {
        return minutes < 1L ? status : getString(R.string.console_status_with_duration, status,
                PlaynitePlaytimeFormatter.minutes(minutes, new PlaynitePlaytimeFormatter.Labels(
                        getString(R.string.playnite_never_played),
                        getString(R.string.playnite_hours_short),
                        getString(R.string.playnite_minutes_short))));
    }

    /**
     * The game a saved session carries, named from its record: stable between host polls,
     * unlike the observed game id. Null when the record names no known game. Runs on every
     * status render, so it reads only what is already in memory.
     */
    private String pausedSessionName(ComputerDetails host, SessionSnapshot snapshot) {
        SessionResumeManager.PendingSession pending = sessionModel.records().pending;
        String gameId = pending != null && pending.hostUuid.equalsIgnoreCase(host.uuid)
                && pending.executionProfileId.equals(snapshot.executionProfileId)
                ? pending.playniteGameId : "";
        if (!gameId.isEmpty()) {
            for (PlayniteDashboardItem item : unfilteredPlayniteItems) {
                if (gameId.equals(SessionSnapshot.normalize(item.stableId()))) {
                    return item.game.name;
                }
            }
        }
        return null;
    }

    /** What a local close or game stop in flight shows, or null when none runs. */
    private String closingStatus(SessionSnapshot snapshot) {
        if (snapshot.endingGame) return getString(R.string.playnite_ending_game);
        return snapshot.state == SessionSnapshot.State.TERMINATING
                ? getString(R.string.console_status_closing_session) : null;
    }

    private static boolean showsHostSleep(SessionSnapshot snapshot) {
        return snapshot.hostSleepRequested && !snapshot.hasActiveSession()
                && !snapshot.endingGame;
    }

    private static boolean hostAsleep(ComputerDetails host, SessionSnapshot snapshot) {
        return snapshot.hostSleepObserved || host.state != ComputerDetails.State.ONLINE;
    }

    /**
     * The status of the power command this client sent. An observed one holds until a fresh
     * poll finds the host online again; a state remembered from before the command does not
     * make it "going down" again. A restarted host that is back signs its actor in.
     */
    private int powerStatus(ComputerDetails host, SessionSnapshot snapshot) {
        HostPowerStateStore.State power = sessionModel.records().hostPower(host.uuid);
        HostPowerStateStore.Kind kind = power == null ? HostPowerStateStore.Kind.SLEEP
                : power.kind;
        boolean down = hostAsleep(host, snapshot);
        switch (kind) {
            case RESTART:
                if (!down) return R.string.console_status_restarting;
                return host.state == ComputerDetails.State.ONLINE
                        ? R.string.console_status_signing_in : R.string.console_status_booting;
            case SHUTDOWN:
                return down && host.state != ComputerDetails.State.ONLINE
                        ? R.string.console_status_shut_down
                        : R.string.console_status_shutting_down;
            default:
                return down ? R.string.console_status_asleep
                        : R.string.console_status_suspending_short;
        }
    }

    private ConsoleHostPresentation.State consoleHostState(
            ComputerDetails host, SessionSnapshot snapshot) {
        // Same order as hostStatus: a power command this client sent comes first.
        if (showsHostSleep(snapshot)) {
            HostPowerStateStore.State power = sessionModel.records().hostPower(host.uuid);
            boolean down = hostAsleep(host, snapshot);
            if (power == null || power.kind == HostPowerStateStore.Kind.SLEEP) {
                return down ? ConsoleHostPresentation.State.ASLEEP
                        : ConsoleHostPresentation.State.CONNECTING;
            }
            // A restart is a passage until its actor is signed in; a shut down host is off.
            return power.kind == HostPowerStateStore.Kind.SHUTDOWN && down
                    && host.state != ComputerDetails.State.ONLINE
                    ? ConsoleHostPresentation.State.OFFLINE
                    : ConsoleHostPresentation.State.CONNECTING;
        }
        if (snapshot.state == SessionSnapshot.State.TERMINATING) {
            return ConsoleHostPresentation.State.CONNECTING;
        }
        if (snapshot.hasActiveSession()) {
            return ConsoleHostPresentation.State.ACTIVE_SESSION;
        }
        if (snapshot.isSuspended()) {
            if (host.state != ComputerDetails.State.ONLINE) {
                return ConsoleHostPresentation.State.ASLEEP;
            }
            return !snapshot.suspensionSleepObserved
                    ? ConsoleHostPresentation.State.CONNECTING
                    : ConsoleHostPresentation.State.WAKING;
        }
        ConsoleHostPresentation.State state = hostStateController.state(host);
        if (state == ConsoleHostPresentation.State.ACTIVE_SESSION) {
            state = ConsoleHostPresentation.State.ONLINE;
        }
        state = ConsoleHostPresentation.withProfile(state, consoleProfile(host));
        // Wizard steps left "To finish" show as the same "!" as a profile needing attention.
        return state == ConsoleHostPresentation.State.ONLINE && firstRunWizard != null
                && firstRunWizard.hasTodo(host.uuid)
                ? ConsoleHostPresentation.State.PROFILE_ATTENTION : state;
    }

    private ConsoleHostPresentation.Profile consoleProfile(ComputerDetails host) {
        return ConsoleHostPresentation.profile(host == null || hostGatewayStore == null
                ? null : hostGatewayStore.profileSelection(host.uuid));
    }

    private String findAppName(ComputerDetails host, int appId) {
        for (NvApp app : appLists.apps(host.uuid)) {
            if (app.getAppId() == appId) return app.getAppName();
        }
        return null;
    }

    private String appDisplayName(String appName) {
        return PlayniteTargetResolver.appDisplayName(appName,
                getString(R.string.console_launch_desktop));
    }

    private String activeSessionName(ComputerDetails host, SessionSnapshot snapshot) {
        for (PlayniteDashboardItem item : unfilteredPlayniteItems) {
            if (snapshot.playniteGameId.equals(
                    SessionSnapshot.normalize(item.stableId()))) return item.game.name;
        }
        return appDisplayName(findAppName(host, snapshot.hostGameAppId));
    }

    private void addHost() {
        refreshHostsOnResume = true;
        Intent intent = new Intent(this, AddComputerManually.class);
        intent.putExtra(AddComputerManually.EXTRA_CONSOLE_APPEARANCE, true);
        startActivity(intent);
    }

    /** Reads whether the host manages child profiles; Settings shows the action once known. */
    private void loadChildCapability(ComputerDetails host,
                                     HostGatewayClient.IntegrationProfile profile) {
        if (host == null || profile == null) return;
        String key = childProfileMetadataKey(host.uuid, profile.id);
        if (key.equals(childCapabilityLoading)) return;
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid, address);
        if (connection == null) return;
        String hostId = host.uuid;
        String profileId = profile.id;
        int token = childManagementGeneration.incrementAndGet();
        childCapabilityLoading = key;
        executor.execute(() -> {
            boolean available = false;
            try {
                available = hostGatewayClient.getCapabilities(connection).childProfiles;
            } catch (IOException | RuntimeException unavailable) {
                // Keep the management action hidden until a capability read succeeds.
            }
            boolean result = available;
            mainHandler.post(() -> {
                if (key.equals(childCapabilityLoading)) childCapabilityLoading = "";
                if (token != childManagementGeneration.get()
                        || !hostId.equals(selectedHostUuid)
                        || !profileId.equals(selectedProfileId(hostId))) return;
                childCapabilityByProfile.put(key, result);
                if (settingsScreen.isShowing()) settingsScreen.refresh();
            });
        });
    }

    private void showHostSelectionOptions(ComputerDetails host) {
        // Decision read: which rows exist depends on the session, so no cached render read.
        SessionSnapshot session = resolveSessionSnapshot(host);
        boolean terminating = session.state == SessionSnapshot.State.TERMINATING;
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        ConsoleHostPanel.Facts facts = new ConsoleHostPanel.Facts();
        facts.reachable = host.state == ComputerDetails.State.ONLINE;
        facts.online = facts.reachable && !terminating;
        facts.paired = host.pairState == PairingManager.PairState.PAIRED;
        facts.gatewayAvailable = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid)) != null;
        facts.canWake = !terminating && ConsoleHostPresentation.canWake(host)
                && !hostStateController.isWaking(host.uuid);
        facts.streamRunning = session.streamerAppId > 0;
        facts.inHost = !hostSelectionVisible;
        facts.sessionActive = facts.online && facts.paired && session.hostHoldsSession();
        facts.resumeAvailable = session.isResumeAvailable() && session.hostGameAppId != 0;
        facts.child = selectedProfileIsChild(host.uuid);
        FirstRunWizard wizard = firstRunWizard();
        facts.setupPending = wizard != null && wizard.hasTodo(host.uuid);
        HostGatewayClient.IntegrationProfile profile =
                hostGatewayStore.profileSelection(host.uuid).selected;
        String state = facts.sessionActive || showsHostSleep(session)
                ? hostStatus(host, session)
                : getString(terminating ? R.string.console_status_closing_session
                : facts.reachable ? R.string.console_status_online
                : R.string.console_status_offline);
        showSidePanel(getString(R.string.console_host_eyebrow), host.name, profile == null ? state
                        : state + " \u00B7 " + getString(R.string.console_host_panel_profile,
                        profileDisplayName(profile)),
                ConsoleHostPanel.menu(facts).rows(this, consolePanel, a -> a.icon, action -> {
                    switch (action) {
                        case FINISH_SETUP:
                            hideSidePanel();
                            wizard.openAt(FirstRunPlan.Entry.SETTINGS, host.uuid,
                                    wizard.todo(host.uuid).get(0));
                            break;
                        case DISMISS_SETUP:
                            hideSidePanel();
                            wizard.dismissTodo(host.uuid);
                            break;
                        case RESUME: hideSidePanel(); resumeSession(host); break;
                        case DESKTOP: hideSidePanel(); launchDesktopSession(host); break;
                        case WAKE: hideSidePanel(); wakeHost(host); break;
                        case SLEEP: confirmSleepHost(host); break;
                        case RESTART: confirmHostPower(host, false); break;
                        case SHUTDOWN: confirmHostPower(host, true); break;
                        case WINDOWS_UPDATE: showWindowsUpdate(host); break;
                        case CLOSE_SESSION: confirmTerminateSession(host); break;
                        case TEST_CONNECTION: hideSidePanel(); ServerHelper.doNetworkTest(this); break;
                        case REPORT_PROBLEM:
                            hideSidePanel();
                            ProblemReportDialog.show(this, hostGatewayStore.loadForHost(
                                    host.uuid, address, selectedProfileId(host.uuid)));
                            break;
                        case CHANGE_HOST: hideSidePanel(); leaveHost(); break;
                        case HARD_TERMINATE: confirmHardTerminateSession(host); break;
                        case UNPAIR: confirmUnpairHost(host); break;
                        case REMOVE: confirmRemoveHost(host); break;
                    }
                }));
    }

    static HostGatewayClient.IntegrationProfile activeAuthorizedProfile(
            HostGatewayStore.ProfileSelection selection) {
        if (selection == null) return null;
        for (HostGatewayClient.IntegrationProfile profile : selection.profiles) {
            if ("active".equals(profile.sessionState)) return profile;
        }
        return null;
    }

    private interface ChildAuthorizationCallback {
        void onAuthorized(ChildManagementLease lease, int token);
    }

    /** The parent's PIN the PIN page asks for to manage children (mockup 20a). */
    private static final class ParentPinRequest {
        final ComputerDetails host;
        final String parentProfileId;
        final GatewayConnection connection;
        final int token;
        final ChildAuthorizationCallback callback;
        final Runnable onCancel;

        ParentPinRequest(ComputerDetails host, String parentProfileId,
                         GatewayConnection connection, int token,
                         ChildAuthorizationCallback callback, Runnable onCancel) {
            this.host = host;
            this.parentProfileId = parentProfileId;
            this.connection = connection;
            this.token = token;
            this.callback = callback;
            this.onCancel = onCancel;
        }
    }

    private ParentPinRequest parentPin;

    /** Setting, changing or removing the selected adult profile's own app PIN on the PIN page. */
    private static final class AdultPinRequest {
        final ComputerDetails host;
        final HostGatewayClient.IntegrationProfile profile;
        final GatewayConnection connection;
        final AdultPinChange change;
        /** Where the PIN page leaves to (the wizard); null: the profile panel. */
        final Runnable onDone;

        AdultPinRequest(ComputerDetails host, HostGatewayClient.IntegrationProfile profile,
                        GatewayConnection connection, AdultPinChange change, Runnable onDone) {
            this.host = host;
            this.profile = profile;
            this.connection = connection;
            this.change = change;
            this.onDone = onDone;
        }
    }

    private AdultPinRequest adultPin;

    /** The PIN page belongs to the Home window, below Settings; every way out returns here. */
    private TextView adultPinAction(ConsolePanel panel, ComputerDetails host,
                                    HostGatewayClient.IntegrationProfile profile,
                                    GatewayConnection connection, AdultPinChange.Mode mode,
                                    int label, int settingsCategory) {
        TextView action = panel.navigation(getString(label));
        action.setOnClickListener(view -> {
            settingsScreen.dismiss();
            startAdultPinChange(host, profile, connection, mode,
                    () -> showSettings(settingsCategory));
        });
        return action;
    }

    /** The adult PIN page; {@code onDone} runs on every way out of it, or null. */
    private void startAdultPinChange(ComputerDetails host,
                                     HostGatewayClient.IntegrationProfile profile,
                                     GatewayConnection connection, AdultPinChange.Mode mode,
                                     Runnable onDone) {
        hideSidePanelImmediately();
        pinEntry.clear();
        pinSubmitting = false;
        pinCooldownUntil = 0L;
        adultPin = new AdultPinRequest(host, profile, connection, new AdultPinChange(mode),
                onDone);
        pinProfileId = profile.id;
        showAdultPinPage("");
    }

    /** Where an adult PIN request ends: its caller's next screen, or the profile panel. */
    private void leaveAdultPin(AdultPinRequest request) {
        if (request.onDone != null) request.onDone.run();
        else showUserMenu(request.host);
    }

    private void showAdultPinPage(String message) {
        AdultPinRequest request = adultPin;
        AdultPinChange.Step step = request.change.step();
        int title = step == AdultPinChange.Step.CURRENT ? R.string.console_adult_pin_current_title
                : step == AdultPinChange.Step.NEW ? R.string.console_adult_pin_new_title
                : R.string.console_adult_pin_confirm_title;
        showPinPage(title, getString(R.string.console_adult_pin_context), request.profile);
        if (!message.isEmpty()) renderPinEntry(message);
    }

    private void submitAdultPin() {
        AdultPinRequest request = adultPin;
        if (request == null || pinSubmitting || pinEntry.length() != 4) return;
        if (SystemClock.elapsedRealtime() < pinCooldownUntil) {
            renderPinCooldown();
            return;
        }
        AdultPinChange.Next next = request.change.accept(pinEntry.takeAndClear());
        if (next != AdultPinChange.Next.SUBMIT) {
            showAdultPinPage(next == AdultPinChange.Next.MISMATCH
                    ? getString(R.string.console_adult_pin_mismatch) : "");
            return;
        }
        String currentPin = request.change.currentPin();
        String newPin = request.change.newPin();
        String requestId = UUID.randomUUID().toString();
        pinSubmitting = true;
        renderPinEntry(getString(R.string.console_adult_pin_saving));
        executor.execute(() -> {
            Boolean required = null;
            IOException failure = null;
            try {
                required = hostGatewayClient.changeProfilePin(
                        request.connection, currentPin, newPin, requestId);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            Boolean pinRequired = required;
            IOException error = failure;
            mainHandler.post(() -> finishAdultPin(request, newPin, pinRequired, error));
        });
    }

    /** A wrong current PIN shakes and locks like any PIN; the steps then start over. */
    private void finishAdultPin(AdultPinRequest request, String newPin, Boolean pinRequired,
                                IOException error) {
        if (adultPin != request) return;
        pinSubmitting = false;
        ComputerDetails host = request.host;
        if (error == null && pinRequired != null) {
            // The device-local offline check follows the host's PIN.
            OfflineProfilePinStore offline = hostGatewayStore.offlineProfilePinStore();
            if (pinRequired) offline.rememberVerified(host.uuid, request.connection, newPin);
            else offline.forget(host.uuid, request.profile.id);
            clearPinEntry();
            ConsoleUiFeedback.makeText(this, pinRequired ? R.string.console_adult_pin_saved
                    : R.string.console_adult_pin_removed, Toast.LENGTH_LONG).show();
            refreshHostProfiles(host);
            if (request.onDone != null) request.onDone.run();
            return;
        }
        HostGatewayClient.GatewayException gatewayError =
                error instanceof HostGatewayClient.GatewayException
                        ? (HostGatewayClient.GatewayException) error : null;
        String reason = gatewayError == null ? "" : gatewayError.getMessage();
        if ("first_pin_grant_required".equals(reason)) {
            clearPinEntry();
            ConsoleUiFeedback.makeText(this, R.string.console_adult_pin_first_unavailable,
                    Toast.LENGTH_LONG).show();
            leaveAdultPin(request);
            return;
        }
        int seconds = ConsolePinEntry.retryDelaySeconds(reason,
                gatewayError == null ? 0 : gatewayError.retryAfterSeconds);
        request.change.restart();
        showAdultPinPage(getString("pin_changed".equals(reason)
                ? R.string.console_adult_pin_changed_on_host
                : seconds > 0 ? R.string.console_pin_invalid : R.string.console_adult_pin_failed));
        if (seconds > 0) {
            pinCooldownUntil = SystemClock.elapsedRealtime() + seconds * 1_000L;
            if ("invalid_pin".equals(reason)) pinEntryView.showInvalid();
            renderPinCooldown();
        }
    }

    /** "Add profile" in progress: its PIN pages, account list and create request. */
    private static final class ProfileCreationRequest {
        final ComputerDetails host;
        final HostGatewayClient.IntegrationProfile actor;
        final GatewayConnection connection;
        final AdultProfileCreation creation;
        final Consumer<String> onFinished;

        ProfileCreationRequest(ComputerDetails host, HostGatewayClient.IntegrationProfile actor,
                               GatewayConnection connection, AdultProfileCreation creation,
                               Consumer<String> onFinished) {
            this.host = host;
            this.actor = actor;
            this.connection = connection;
            this.creation = creation;
            this.onFinished = onFinished;
        }
    }

    private ProfileCreationRequest profileCreation;

    /**
     * "Add profile": binds one of the host's local Windows accounts to a new adult profile,
     * optionally with its first PIN. {@code actor} is the adult that authorizes it (its PIN
     * when it has one); null while this TV has no adult profile, when the host accepts only
     * the pairing grant of the last few minutes. {@code onFinished} gets the new profile's ID,
     * or null when cancelled or refused. The first-run wizard's "Who's playing?" uses it too.
     */
    void startAdultProfileCreation(ComputerDetails host,
                                   HostGatewayClient.IntegrationProfile actor,
                                   Consumer<String> onFinished) {
        if (host == null || (actor != null && actor.isChild())) return;
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress == null ? null : host.activeAddress.address,
                actor == null ? null : actor.id);
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.console_add_profile_failed,
                    Toast.LENGTH_LONG).show();
            onFinished.accept(null);
            return;
        }
        ProfileCreationRequest request = new ProfileCreationRequest(host, actor, connection,
                new AdultProfileCreation(actor != null && actor.pinRequired), onFinished);
        adultPin = null;
        profileCreation = request;
        if (request.creation.step() == AdultProfileCreation.Step.ACTOR_PIN) {
            showProfileCreationPinPage("");
        } else {
            loadProfileCreationAccounts(request);
        }
    }

    private void showProfileCreationPinPage(String message) {
        ProfileCreationRequest request = profileCreation;
        hideSidePanelImmediately();
        pinSubmitting = false;
        if (request.creation.step() == AdultProfileCreation.Step.ACTOR_PIN) {
            pinProfileId = request.actor.id;
            showPinPage(R.string.console_adult_pin_current_title,
                    getString(R.string.console_add_profile), request.actor);
        } else {
            String account = request.creation.accountName();
            pinProfileId = "new-profile";
            showPinPage(request.creation.newPinChange().step() == AdultPinChange.Step.NEW
                            ? R.string.console_adult_pin_new_title
                            : R.string.console_adult_pin_confirm_title,
                    getString(R.string.console_adult_pin_context),
                    new HostGatewayClient.IntegrationProfile(pinProfileId, account,
                            false, false, false, false));
        }
        if (!message.isEmpty()) renderPinEntry(message);
    }

    private void submitProfileCreationPin() {
        ProfileCreationRequest request = profileCreation;
        if (request == null || pinSubmitting || pinEntry.length() != 4) return;
        if (SystemClock.elapsedRealtime() < pinCooldownUntil) {
            renderPinCooldown();
            return;
        }
        String pin = pinEntry.takeAndClear();
        if (request.creation.step() == AdultProfileCreation.Step.ACTOR_PIN) {
            request.creation.acceptActorPin(pin);
            clearPinPage();
            loadProfileCreationAccounts(request);
            return;
        }
        AdultPinChange.Next next = request.creation.newPinChange().accept(pin);
        if (next == AdultPinChange.Next.SUBMIT) {
            clearPinPage();
            submitProfileCreation(request);
        } else {
            showProfileCreationPinPage(next == AdultPinChange.Next.MISMATCH
                    ? getString(R.string.console_adult_pin_mismatch) : "");
        }
    }

    /** Leaves the PIN page but keeps the creation, which continues on the side panel. */
    private void clearPinPage() {
        ProfileCreationRequest request = profileCreation;
        clearPinEntry();
        profileCreation = request;
    }

    private void loadProfileCreationAccounts(ProfileCreationRequest request) {
        showSidePanelBusy(getString(R.string.console_profile_eyebrow),
                getString(R.string.console_add_profile), getString(R.string.console_loading));
        String actorPin = request.creation.actorPin();
        executor.execute(() -> {
            List<String> accounts = null;
            IOException failure = null;
            try {
                accounts = hostGatewayClient.adultProfileAccounts(request.connection, actorPin);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            List<String> result = accounts;
            IOException error = failure;
            mainHandler.post(() -> {
                if (profileCreation != request) return;
                if (error != null) refuseProfileCreation(request, error);
                else showProfileCreationAccounts(request, result);
            });
        });
    }

    private void showProfileCreationAccounts(ProfileCreationRequest request,
                                             List<String> accounts) {
        List<View> actions = new ArrayList<>();
        for (String account : accounts) {
            TextView choice = consolePanel.action(account);
            choice.setOnClickListener(view -> chooseProfileCreationPin(request, account));
            actions.add(choice);
        }
        if (accounts.isEmpty()) {
            actions.add(consolePanel.note(getString(R.string.console_add_profile_no_accounts)));
        }
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        cancel.setOnClickListener(view -> finishProfileCreation(request, null));
        actions.add(cancel);
        showSidePanel(getString(R.string.console_profile_eyebrow),
                getString(R.string.console_add_profile),
                getString(R.string.console_add_profile_details), actions.toArray(new View[0]));
    }

    private void chooseProfileCreationPin(ProfileCreationRequest request, String account) {
        TextView withPin = consolePanel.action(getString(R.string.console_adult_pin_set));
        withPin.setOnClickListener(view -> {
            request.creation.chooseAccount(account, true, UUID.randomUUID().toString());
            pinEntry.clear();
            pinCooldownUntil = 0L;
            showProfileCreationPinPage("");
        });
        TextView withoutPin = consolePanel.action(getString(R.string.console_add_profile_no_pin));
        withoutPin.setOnClickListener(view -> {
            request.creation.chooseAccount(account, false, UUID.randomUUID().toString());
            submitProfileCreation(request);
        });
        showSidePanel(getString(R.string.console_profile_eyebrow), account,
                getString(R.string.console_add_profile_pin_details), withPin, withoutPin);
    }

    private void submitProfileCreation(ProfileCreationRequest request) {
        AdultProfileCreation creation = request.creation;
        String account = creation.accountName();
        String actorPin = creation.actorPin();
        String newPin = creation.newPin();
        String requestId = creation.requestId();
        showSidePanelBusy(getString(R.string.console_profile_eyebrow), account,
                getString(R.string.console_add_profile_creating));
        executor.execute(() -> {
            String created = null;
            IOException failure = null;
            try {
                created = hostGatewayClient.createAdultProfile(request.connection, account,
                        actorPin, newPin, requestId);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            String profileId = created;
            IOException error = failure;
            mainHandler.post(() -> {
                if (profileCreation != request) return;
                if (error != null) {
                    refuseProfileCreation(request, error);
                    return;
                }
                if (newPin != null) {
                    hostGatewayStore.offlineProfilePinStore().rememberVerified(request.host.uuid,
                            request.connection.forProfile(profileId), newPin);
                }
                ConsoleUiFeedback.makeText(this, getString(R.string.console_add_profile_created,
                        account), Toast.LENGTH_LONG).show();
                refreshHostProfiles(request.host);
                finishProfileCreation(request, profileId);
            });
        });
    }

    private void refuseProfileCreation(ProfileCreationRequest request, IOException error) {
        HostGatewayClient.GatewayException gatewayError =
                error instanceof HostGatewayClient.GatewayException
                        ? (HostGatewayClient.GatewayException) error : null;
        String reason = gatewayError == null ? "" : gatewayError.getMessage();
        switch (request.creation.refused(reason)) {
            case ACTOR_PIN:
                int seconds = ConsolePinEntry.retryDelaySeconds(reason,
                        gatewayError == null ? 0 : gatewayError.retryAfterSeconds);
                pinEntry.clear();
                showProfileCreationPinPage(getString(R.string.console_pin_invalid));
                if (seconds > 0) {
                    pinCooldownUntil = SystemClock.elapsedRealtime() + seconds * 1_000L;
                    if ("invalid_pin".equals(reason)) pinEntryView.showInvalid();
                    renderPinCooldown();
                }
                return;
            case ACCOUNT:
                ConsoleUiFeedback.makeText(this, R.string.console_add_profile_account_taken,
                        Toast.LENGTH_LONG).show();
                loadProfileCreationAccounts(request);
                return;
            case NOT_AUTHORIZED:
                ConsoleUiFeedback.makeText(this, R.string.console_add_profile_not_authorized,
                        Toast.LENGTH_LONG).show();
                break;
            default:
                ConsoleUiFeedback.makeText(this, R.string.console_add_profile_failed,
                        Toast.LENGTH_LONG).show();
                break;
        }
        finishProfileCreation(request, null);
    }

    private void finishProfileCreation(ProfileCreationRequest request, String profileId) {
        if (profileCreation != request) return;
        profileCreation = null;
        hideSidePanelImmediately();
        request.onFinished.accept(profileId);
    }

    private void invalidateChildManagementPresentation() {
        childManagementGeneration.incrementAndGet();
        childMutationRetry = null;
    }

    private void prepareChildSharingForDeparture() {
        if (sidePanel == null) return;
        for (int index = 0; index < sidePanel.getChildCount(); index++) {
            View child = sidePanel.getChildAt(index);
            if (child instanceof ConsoleChildGameSharingPanel) {
                ((ConsoleChildGameSharingPanel) child).prepareForDismiss();
                return;
            }
        }
    }

    private void invalidateChildManagement() {
        invalidateChildManagementPresentation();
        childManagementLease = null;
    }

    static boolean isChildManagementAuthorizationFailure(IOException error) {
        if (!(error instanceof HostGatewayClient.GatewayException)) return false;
        HostGatewayClient.GatewayException gatewayError =
                (HostGatewayClient.GatewayException) error;
        String reason = gatewayError.getMessage() == null ? ""
                : gatewayError.getMessage().trim().toLowerCase(Locale.ROOT);
        if ("authorization_required".equals(reason)
                || "authorization_expired".equals(reason)
                || "management_authorization_expired".equals(reason)
                || reason.contains("fresh child management session")) return true;
        return (gatewayError.statusCode == 401 || gatewayError.statusCode == 403)
                && (reason.contains("child management") || reason.contains("not authorized"));
    }

    /** Drops {@code rejected} if it is still the current lease and the host refused it. */
    private void clearRejectedChildManagementLease(ChildManagementLease rejected,
                                                   IOException error) {
        if (childManagementLease == rejected && isChildManagementAuthorizationFailure(error)) {
            childManagementLease = null;
        }
    }

    private GatewayConnection childConnection(ComputerDetails host, String parentProfileId) {
        if (host == null || parentProfileId == null || parentProfileId.isEmpty()) return null;
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        return hostGatewayStore.loadForHost(host.uuid, address, parentProfileId);
    }

    private boolean isCurrentChildContext(String hostId, String parentProfileId, int token) {
        return token == childManagementGeneration.get()
                && hostId != null && hostId.equals(selectedHostUuid)
                && parentProfileId != null
                && parentProfileId.equals(selectedProfileId(hostId))
                && currentHost(hostId) != null;
    }

    /** Keep an already-enqueued sharing save alive while its panel is dismissed. */
    private boolean isCurrentChildMutationContext(String hostId, String parentProfileId) {
        return hostId != null && hostId.equals(selectedHostUuid)
                && parentProfileId != null
                && parentProfileId.equals(selectedProfileId(hostId))
                && currentHost(hostId) != null;
    }

    private boolean isAttachedChildSharingPanel(ConsoleChildGameSharingPanel panel) {
        return panel != null && sidePanel != null && panel.getParent() == sidePanel;
    }

    private ChildManagementLease currentChildLease(ComputerDetails host,
                                                   String parentProfileId) {
        GatewayConnection connection = childConnection(host, parentProfileId);
        if (connection == null || childManagementLease == null
                || !childManagementLease.matches(host.uuid, parentProfileId, connection)) {
            childManagementLease = null;
            return null;
        }
        return childManagementLease;
    }

    private void authorizeChildManagementFor(ComputerDetails host,
                                             ChildAuthorizationCallback callback,
                                             Runnable onFailure) {
        if (host == null) {
            if (onFailure != null) onFailure.run();
            return;
        }
        authorizeChildManagementFor(host, childManagementGeneration.incrementAndGet(),
                callback, onFailure);
    }

    private void reauthorizeChildManagementFor(ComputerDetails host, int token,
                                               ChildAuthorizationCallback callback,
                                               Runnable onFailure) {
        authorizeChildManagementFor(host, token, callback, onFailure);
    }

    /**
     * Calls back with the current lease, or asks for the parent's PIN on the PIN page first;
     * {@code onFailure} runs when management is unavailable or the PIN is cancelled.
     */
    private void authorizeChildManagementFor(ComputerDetails host, int token,
                                             ChildAuthorizationCallback callback,
                                             Runnable onFailure) {
        if (host == null) {
            if (onFailure != null) onFailure.run();
            return;
        }
        HostGatewayStore.ProfileSelection selection =
                hostGatewayStore.profileSelection(host.uuid);
        HostGatewayClient.IntegrationProfile profile = selection.selected;
        if (!childManagementProfileAllowed(host, profile)
                || !childCapabilityAllowed(host, profile)) {
            if (onFailure != null) onFailure.run();
            return;
        }
        String parentProfileId = selectedProfileId(host.uuid);
        GatewayConnection connection = childConnection(host, parentProfileId);
        if (connection == null) {
            ConsoleUiFeedback.makeText(this,
                    R.string.console_child_management_authorize_failed,
                    Toast.LENGTH_LONG).show();
            if (onFailure != null) onFailure.run();
            return;
        }
        if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
        ChildManagementLease existingLease = currentChildLease(host, parentProfileId);
        if (existingLease != null) {
            callback.onAuthorized(existingLease, token);
            return;
        }
        // The PIN page lives in this window, under the side panel's and Settings' windows;
        // closing the panel retires the token, so the request carries the one current after it.
        // Callers that started in Settings › Children open it again (reloadChildrenSettings).
        settingsScreen.dismiss();
        hideSidePanelImmediately();
        pinEntry.clear();
        pinSubmitting = false;
        pinCooldownUntil = 0L;
        profileCreation = null;
        parentPin = new ParentPinRequest(host, parentProfileId, connection,
                childManagementGeneration.get(), callback, onFailure);
        pinProfileId = parentProfileId;
        showPinPage(R.string.console_child_management_pin_title,
                getString(R.string.console_child_management_title), profile);
    }

    private void submitParentPin() {
        ParentPinRequest request = parentPin;
        if (request == null || pinSubmitting || pinEntry.length() != 4) return;
        if (SystemClock.elapsedRealtime() < pinCooldownUntil) {
            renderPinCooldown();
            return;
        }
        String pin = pinEntry.takeAndClear();
        pinSubmitting = true;
        renderPinEntry(getString(R.string.console_child_management_authorizing));
        executor.execute(() -> {
            HostGatewayClient.ChildManagementAuthorization authorization = null;
            IOException failure = null;
            try {
                authorization = hostGatewayClient.authorizeChildManagement(
                        request.connection, request.parentProfileId, pin);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            HostGatewayClient.ChildManagementAuthorization result = authorization;
            IOException error = failure;
            mainHandler.post(() -> finishParentPin(request, result, error));
        });
    }

    /** A wrong PIN shakes and locks like a profile's; other failures stay on the page. */
    private void finishParentPin(ParentPinRequest request,
                                 HostGatewayClient.ChildManagementAuthorization result,
                                 IOException error) {
        if (parentPin != request) return;
        pinSubmitting = false;
        ComputerDetails host = request.host;
        if (!isCurrentChildContext(host.uuid, request.parentProfileId, request.token)) {
            closeParentPin();
            return;
        }
        if (error == null && result != null
                && request.parentProfileId.equals(result.parentProfileId)
                && !result.authorizationId.isEmpty()) {
            closeParentPin();
            childManagementLease = new ChildManagementLease(host.uuid, request.parentProfileId,
                    request.connection, result.authorizationId, result.expiresInSeconds);
            request.callback.onAuthorized(childManagementLease, request.token);
            return;
        }
        HostGatewayClient.GatewayException gatewayError =
                error instanceof HostGatewayClient.GatewayException
                        ? (HostGatewayClient.GatewayException) error : null;
        String reason = gatewayError == null ? "" : gatewayError.getMessage();
        int seconds = ConsolePinEntry.retryDelaySeconds(reason,
                gatewayError == null ? 0 : gatewayError.retryAfterSeconds);
        if (seconds > 0) {
            pinCooldownUntil = SystemClock.elapsedRealtime() + seconds * 1_000L;
            if ("invalid_pin".equals(reason)) pinEntryView.showInvalid();
            renderPinCooldown();
        } else {
            renderPinEntry(getString(R.string.console_child_management_authorize_failed));
        }
    }

    private void closeParentPin() {
        parentPin = null;
        clearPinEntry();
    }

    /**
     * The one load of a parent's children: lists them with {@code lease} off the main thread;
     * back on it the library's "For child" filter keeps them unless a later load began (a shown
     * filter follows) and {@code loaded} gets them, or a rejected lease is dropped and
     * {@code failed} gets this load's filter generation.
     */
    private void fetchChildProfiles(ComputerDetails host, String parentProfileId,
                                    ChildManagementLease lease,
                                    Consumer<HostGatewayClient.ChildProfiles> loaded,
                                    java.util.function.IntConsumer failed) {
        String key = ChildLibraryFilter.key(host.uuid, parentProfileId);
        int generation = childLibraryFilter.begin(key);
        executor.execute(() -> {
            HostGatewayClient.ChildProfiles profiles = null;
            IOException failure = null;
            try {
                profiles = hostGatewayClient.listChildProfiles(lease.connection,
                        parentProfileId, lease.authorizationId);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            HostGatewayClient.ChildProfiles result = profiles;
            IOException error = failure;
            mainHandler.post(() -> {
                if (result == null) {
                    if (isCurrentChildMutationContext(host.uuid, parentProfileId)) {
                        clearRejectedChildManagementLease(lease, error);
                    }
                    failed.accept(generation);
                    return;
                }
                boolean filtered = childLibraryFilter.selection(key) != null;
                if (childLibraryFilter.remember(key, generation,
                        ChildLibraryFilter.of(result, parentProfileId)) && filtered) {
                    renderPlayniteLibrary(host, currentSunshineApps);
                }
                loaded.accept(result);
            });
        });
    }

    /**
     * "For child" in the library filters before child management has run: the children load
     * behind the parent's PIN, as there, and the filters panel opens again with them.
     */
    private void loadChildFilterChildren(ComputerDetails host) {
        authorizeChildManagementFor(host, (lease, token) -> {
            String parentProfileId = selectedProfileId(host.uuid);
            showSidePanelBusy(getString(R.string.console_settings_library),
                    getString(R.string.child_library_filter),
                    getString(R.string.console_child_management_loading));
            fetchChildProfiles(host, parentProfileId, lease, result -> {
                if (isCurrentChildContext(host.uuid, parentProfileId, token)) showLibraryFilters();
            }, generation -> {
                if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
                ConsoleUiFeedback.makeText(this,
                        R.string.console_child_management_authorize_failed,
                        Toast.LENGTH_LONG).show();
                showLibraryFilters();
            });
        }, this::showLibraryFilters);
    }

    /** An editor count (mockup 23a): the parent's library as {@code child} sees it. */
    private void showChildLibrary(ComputerDetails host, ChildLibraryFilter.Child child,
                                  ChildLibraryFilter.Group group) {
        hideSidePanelImmediately();
        selectChildLibraryFilter(host, child, group);
        if (expandedLibraryMode) {
            renderFilteredPlayniteLibraryFromStart(host);
        } else {
            renderPlayniteLibrary(host, currentSunshineApps);
            enterExpandedLibrary();
        }
    }

    private ConsoleChildProfileEditor.Draft childDraft(
            HostGatewayClient.ChildProfile child, int revision) {
        List<ConsoleChildProfileEditor.Day> days = child.weekdays;
        if (days == null || days.size() != 7) {
            days = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                days.add(ConsoleChildProfileEditor.Day.disabled());
            }
        }
        return new ConsoleChildProfileEditor.Draft(child.id, child.name,
                child.avatarId, child.enabled, days, child.ageLimit, child.allowedGameIds,
                child.blockedGameIds, revision, false, child.gameMode, child.gameModeWindows,
                child.gameModeMute);
    }

    /**
     * Mockups 20c-20e as a nested screen of Settings › Children; Back to the list saves a changed
     * draft. {@code unsaved}: the draft is one whose save failed, so leaving saves it even
     * unchanged. The editor's actions take the token current when they run: side panels shown
     * and closed over Settings meanwhile (a save's progress) retire older ones.
     */
    private void showChildEditor(ComputerDetails host, ConsoleChildProfileEditor.Draft draft,
                                 boolean unsaved, int token) {
        if (!isCurrentChildContext(host.uuid, selectedProfileId(host.uuid), token)) return;
        hideSidePanelImmediately();
        if (!settingsScreen.isShowing()) showSettings(SETTINGS_CHILDREN);
        new ConsoleChildProfileEditor(this, settingsScreen, new ConsoleChildProfileEditor.Host() {
            @Override public void editName(String current, Consumer<String> onName) {
                consoleFeedback.showInput(getString(R.string.console_child_profile_name), null,
                        current, getString(R.string.console_child_profile_name_missing),
                        false, 80,
                        getString(android.R.string.cancel), null, null,
                        getString(android.R.string.ok), value -> {
                            String name = value.trim();
                            if (name.isEmpty()) return getString(R.string.console_input_required);
                            onName.accept(name);
                            return null;
                        }, null);
            }

            @Override public View avatarPicker(String profileId, String name, String current,
                                               Consumer<String> onPick) {
                // Each grid asks for its images: the memory keeps only part of a catalog, and a
                // grid bound to an evicted image would otherwise wait for it forever.
                profileAvatars.load(childConnection(host, selectedProfileId(host.uuid)),
                        host.uuid, ProfileAvatars.avatarIds(childAvatarCatalog));
                return ConsoleAvatarGrid.create(ConsoleActivity.this, profileId, name,
                        childAvatarCatalog, current, onPick);
            }

            @Override public void delete(ConsoleChildProfileEditor.Draft current) {
                confirmDeleteChild(host, current);
            }

            @Override public String hostId() {
                return host.uuid;
            }

            @Override public List<PlayniteLibraryGame> parentLibrary() {
                // The parent manages children from their own profile: its library is the one
                // shown, once loaded, counted as the "For child" grid counts it.
                HostProfileKey parent = new HostProfileKey(host.uuid, selectedProfileId(host.uuid));
                return parent.equals(currentPlayniteProfileKey)
                        ? ChildLibraryFilter.listed(currentPlayniteGames,
                        locallyHiddenPlayniteGames(host.uuid)) : null;
            }

            @Override public void showGames(ConsoleChildProfileEditor.Draft current,
                                            boolean changed, ChildLibraryFilter.Group group) {
                settingsScreen.dismiss();
                ChildLibraryFilter.Child child = new ChildLibraryFilter.Child(current.profileId,
                        current.name, current.ageLimit, current.allowedGameIds,
                        current.blockedGameIds);
                Runnable show = () -> showChildLibrary(host, child, group);
                // A changed draft is saved first, as Back would.
                if (changed) {
                    saveChildDraft(host, childManagementGeneration.get(), current, show);
                } else {
                    show.run();
                }
            }

            @Override public void closed(ConsoleChildProfileEditor.Draft current,
                                         boolean changed) {
                // A child is only created once it has a name; Back already shows the list.
                if (changed && !current.name.isEmpty()) {
                    saveChildDraft(host, childManagementGeneration.get(), current, null);
                }
            }
        }).open(draft, unsaved);
    }

    private String childRequestId(String hostId, String parentProfileId, String operation,
                                  String childId, String gameId, String signature) {
        if (childMutationRetry == null
                || !childMutationRetry.matches(hostId, parentProfileId, operation,
                childId, gameId, signature)) {
            childMutationRetry = new ChildMutationRetry(hostId, parentProfileId, operation,
                    childId, gameId, signature, UUID.randomUUID().toString());
        }
        return childMutationRetry.requestId;
    }

    /** {@code onSaved} runs after a successful save; null: the list of children reloads. */
    private void saveChildDraft(ComputerDetails host, int token,
                                ConsoleChildProfileEditor.Draft draft, Runnable onSaved) {
        String parentProfileId = selectedProfileId(host.uuid);
        if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
        ChildManagementLease lease = currentChildLease(host, parentProfileId);
        if (lease == null) {
            reauthorizeChildManagementFor(host, token,
                    (fresh, freshToken) -> saveChildDraft(host, freshToken, draft, onSaved),
                    () -> showChildSaveFailure(host, childManagementGeneration.get(), draft,
                            onSaved));
            return;
        }
        String operation = draft.profileId.isEmpty() ? "create" : "update";
        String childId = draft.profileId;
        String requestId = childRequestId(host.uuid, parentProfileId, operation, childId,
                "", draft.signature());
        showSidePanelBusy(getString(R.string.console_child_management_title), draft.name,
                getString(R.string.console_child_profile_saving));
        executor.execute(() -> {
            HostGatewayClient.ChildMutationResult result = null;
            IOException failure = null;
            try {
                result = "create".equals(operation)
                        ? hostGatewayClient.createChildProfile(lease.connection,
                        parentProfileId, lease.authorizationId, requestId,
                        draft.expectedRevision, draft)
                        : hostGatewayClient.updateChildProfile(lease.connection,
                        parentProfileId, lease.authorizationId, childId, requestId,
                        draft.expectedRevision, draft);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            HostGatewayClient.ChildMutationResult response = result;
            IOException error = failure;
            mainHandler.post(() -> {
                if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
                if (error != null || response == null
                        || !parentProfileId.equals(response.parentProfileId)
                        || response.child == null
                        || !parentProfileId.equals(response.child.parentProfileId)) {
                    clearRejectedChildManagementLease(lease, error);
                    boolean conflict = error instanceof HostGatewayClient.GatewayException
                            && ((HostGatewayClient.GatewayException) error).statusCode == 409;
                    if (conflict) {
                        // Changed elsewhere: the list shows the host's version to edit again.
                        ConsoleUiFeedback.makeText(this,
                                R.string.console_child_profile_save_conflict,
                                Toast.LENGTH_LONG).show();
                        reloadChildrenSettings();
                    } else {
                        showChildSaveFailure(host, token, draft, onSaved);
                    }
                    return;
                }
                childMutationRetry = null;
                ConsoleUiFeedback.makeText(this,
                        R.string.console_child_management_saved,
                        Toast.LENGTH_SHORT).show();
                refreshChildProfileMetadata(host, token, onSaved != null ? onSaved
                        : this::reloadChildrenSettings);
            });
        });
    }

    /**
     * A save that failed keeps its draft: retry (still followed by {@code onSaved}), edit it
     * further or drop it.
     */
    private void showChildSaveFailure(ComputerDetails host, int token,
                                      ConsoleChildProfileEditor.Draft draft, Runnable onSaved) {
        if (!isCurrentChildContext(host.uuid, selectedProfileId(host.uuid), token)) return;
        TextView retry = consolePanel.action(getString(R.string.console_child_profile_save_retry));
        retry.setOnClickListener(view -> saveChildDraft(host, token, draft, onSaved));
        TextView edit = consolePanel.action(getString(R.string.console_child_profile_save_edit));
        edit.setOnClickListener(view -> showChildEditor(host, draft, true, token));
        TextView discard = consolePanel.destructive(consolePanel.action(
                getString(R.string.console_child_profile_save_discard)));
        discard.setOnClickListener(view -> reloadChildrenSettings());
        showSidePanel(getString(R.string.console_child_management_title), draft.name,
                getString(R.string.console_child_profile_save_failed), retry, edit, discard);
    }

    private void confirmDeleteChild(ComputerDetails host, ConsoleChildProfileEditor.Draft child) {
        String name = child.name.isEmpty()
                ? getString(R.string.console_child_profile_title) : child.name;
        consoleFeedback.showConfirm(getString(R.string.console_child_management_delete),
                getString(R.string.console_child_management_delete_confirm, name),
                getString(R.string.console_cancel),
                getString(R.string.console_child_management_delete), true, () -> {
                    // Out of the editor to the list without saving its draft.
                    settingsScreen.pop();
                    deleteChild(host, child.profileId, child.expectedRevision,
                            childManagementGeneration.get());
                });
    }

    private void deleteChild(ComputerDetails host, String childId, int expectedRevision,
                             int token) {
        String parentProfileId = selectedProfileId(host.uuid);
        if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
        ChildManagementLease lease = currentChildLease(host, parentProfileId);
        String signature = "delete|" + childId + "|" + expectedRevision;
        if (lease == null) {
            reauthorizeChildManagementFor(host, token,
                    (fresh, freshToken) -> deleteChild(host, childId, expectedRevision,
                            freshToken),
                    this::reloadChildrenSettings);
            return;
        }
        String requestId = childRequestId(host.uuid, parentProfileId, "delete", childId,
                "", signature);
        showSidePanelBusy(getString(R.string.console_child_management_title), host.name,
                getString(R.string.console_child_management_loading));
        executor.execute(() -> {
            IOException failure = null;
            HostGatewayClient.ChildMutationResult result = null;
            try {
                result = hostGatewayClient.deleteChildProfile(lease.connection,
                        parentProfileId, lease.authorizationId, childId, requestId,
                        expectedRevision);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            IOException error = failure;
            HostGatewayClient.ChildMutationResult response = result;
            mainHandler.post(() -> {
                if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
                if (error != null || response == null
                        || !parentProfileId.equals(response.parentProfileId)) {
                    clearRejectedChildManagementLease(lease, error);
                    ConsoleUiFeedback.makeText(this,
                            R.string.console_child_profile_save_failed,
                            Toast.LENGTH_LONG).show();
                    reloadChildrenSettings();
                    return;
                }
                childMutationRetry = null;
                ConsoleUiFeedback.makeText(this,
                        R.string.console_child_management_deleted,
                        Toast.LENGTH_SHORT).show();
                refreshChildProfileMetadata(host, token, this::reloadChildrenSettings);
            });
        });
    }

    private void refreshChildProfileMetadata(ComputerDetails host, int token, Runnable done) {
        if (host == null) return;
        String parentProfileId = selectedProfileId(host.uuid);
        GatewayConnection connection = childConnection(host, parentProfileId);
        if (connection == null) {
            if (done != null) done.run();
            return;
        }
        executor.execute(() -> {
            HostGatewayClient.IntegrationProfiles profiles = null;
            try {
                profiles = hostGatewayClient.getIntegrationProfiles(connection);
            } catch (IOException | RuntimeException ignored) {
                // The child list remains authoritative when this metadata refresh is unavailable.
            }
            HostGatewayClient.IntegrationProfiles result = profiles;
            mainHandler.post(() -> {
                if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
                if (result != null) {
                    saveHostProfiles(host.uuid, result);
                }
                if (done != null) done.run();
            });
        });
    }

    private void beginChildSharing(ComputerDetails host, PlayniteDashboardItem item) {
        if (host == null || item == null) return;
        String gameId = item.game.playniteGameId;
        if (!HostGatewayClient.isPlayniteId(gameId)) return;
        childMutationRetry = null;
        authorizeChildManagementFor(host,
                (lease, token) -> loadChildSharing(host, item, lease, token),
                () -> showChildSharingFailure(host, item, childManagementGeneration.get()));
    }

    private void loadChildSharing(ComputerDetails host, PlayniteDashboardItem item,
                                  ChildManagementLease lease, int token) {
        String parentProfileId = selectedProfileId(host.uuid);
        if (lease == null || !isCurrentChildContext(host.uuid, parentProfileId, token)) return;
        showSidePanelBusy(getString(R.string.console_child_management_eyebrow),
                getString(R.string.console_child_sharing_title, item.game.name),
                getString(R.string.console_child_management_loading));
        executor.execute(() -> {
            HostGatewayClient.ChildSharing sharing = null;
            IOException failure = null;
            try {
                sharing = hostGatewayClient.getChildSharing(lease.connection,
                        parentProfileId, lease.authorizationId, item.game.playniteGameId);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            HostGatewayClient.ChildSharing result = sharing;
            IOException error = failure;
            mainHandler.post(() -> {
                if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
                if (error != null || result == null) {
                    clearRejectedChildManagementLease(lease, error);
                    showChildSharingFailure(host, item, token);
                    return;
                }
                renderChildSharing(host, item, result, token);
            });
        });
    }

    private void showChildSharingFailure(ComputerDetails host, PlayniteDashboardItem item,
                                         int token) {
        TextView retry = consolePanel.action(getString(R.string.console_child_management_refresh));
        retry.setOnClickListener(view -> {
            ChildManagementLease lease = currentChildLease(host, selectedProfileId(host.uuid));
            if (lease != null) loadChildSharing(host, item, lease, token);
            else reauthorizeChildManagementFor(host, token,
                    (fresh, freshToken) -> loadChildSharing(host, item, fresh, freshToken), null);
        });
        TextView close = consolePanel.action(getString(R.string.console_cancel));
        close.setOnClickListener(view -> handlePanelBack());
        showSidePanel(getString(R.string.console_child_management_eyebrow),
                getString(R.string.console_child_sharing_title, item.game.name),
                getString(R.string.console_child_management_authorize_failed), retry, close);
    }

    private void renderChildSharing(ComputerDetails host, PlayniteDashboardItem item,
                                    HostGatewayClient.ChildSharing sharing, int token) {
        String parentProfileId = selectedProfileId(host.uuid);
        if (!isCurrentChildContext(host.uuid, parentProfileId, token)) return;
        ConsoleChildGameSharingPanel panel = new ConsoleChildGameSharingPanel(this, consolePanel);
        List<ConsoleChildGameSharingPanel.ChildOption> options = new ArrayList<>();
        for (HostGatewayClient.ChildShare child : sharing.children) {
            if (child == null || child.id.isEmpty()) continue;
            options.add(new ConsoleChildGameSharingPanel.ChildOption(child.id, child.name,
                    child.ageLimit, child.state));
        }
        ChildSharingOriginContext origin = new ChildSharingOriginContext(
                token, currentChildLease(host, parentProfileId));
        panel.show(sharing.ageRating, sharing.minAge, options, sharing.revision,
                (draft, completion) -> saveChildSharing(host, item, panel,
                        parentProfileId, origin, draft, completion));
        panel.setRefreshCallback(() -> {
            ChildManagementLease lease = currentChildLease(host, parentProfileId);
            if (lease != null) {
                origin.lease = lease;
                loadChildSharing(host, item, lease, origin.token);
            } else reauthorizeChildManagementFor(host, origin.token,
                    (fresh, freshToken) -> {
                        origin.token = freshToken;
                        origin.lease = fresh;
                        loadChildSharing(host, item, fresh, freshToken);
                    },
                    null);
        });
        showSidePanel(getString(R.string.console_child_management_eyebrow),
                getString(R.string.console_child_sharing_title, item.game.name), "");
        sidePanel.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wireModalFocusTrap(true);
    }

    private void saveChildSharing(ComputerDetails host, PlayniteDashboardItem item,
                                  ConsoleChildGameSharingPanel panel,
                                  String parentProfileId, ChildSharingOriginContext origin,
                                  ConsoleChildGameSharingPanel.Draft draft,
                                  ConsoleChildGameSharingPanel.Completion completion) {
        String gameId = item.game.playniteGameId;
        StringBuilder signature = new StringBuilder("sharing|").append(gameId).append('|')
                .append(draft.expectedRevision).append('|').append(draft.expectedMinAge);
        for (String id : draft.selectedChildIds) signature.append('|').append(id);
        boolean dismissedSave = panel != null && panel.isDismissRequested();
        int token = origin == null ? -1 : origin.token;
        boolean currentContext = isCurrentChildContext(host.uuid, parentProfileId, token);
        boolean originContinuation = dismissedSave && origin != null && origin.lease != null;
        if (!currentContext && !originContinuation) {
            if (panel != null) panel.discardPendingSave();
            completion.onFailed(getString(
                    R.string.console_child_management_authorize_failed), false);
            return;
        }
        ChildManagementLease lease = dismissedSave
                ? origin.lease : currentChildLease(host, parentProfileId);
        if (!dismissedSave && lease != null && origin != null) origin.lease = lease;
        if (lease == null) {
            if (dismissedSave) {
                completion.onFailed(getString(
                        R.string.console_child_management_authorize_failed), false);
                return;
            }
            reauthorizeChildManagementFor(host, token,
                    (fresh, freshToken) -> {
                        origin.token = freshToken;
                        origin.lease = fresh;
                        saveChildSharing(host, item, panel, parentProfileId, origin,
                                draft, completion);
                    },
                    () -> completion.onFailed(
                            getString(R.string.console_child_management_authorize_failed),
                            false));
            return;
        }
        String requestId = panel.requestIdFor(signature.toString());
        executor.execute(() -> {
            HostGatewayClient.ChildMutationResult result = null;
            IOException failure = null;
            try {
                result = hostGatewayClient.setChildSharing(lease.connection, parentProfileId,
                        lease.authorizationId, gameId, draft.selectedChildIds,
                        draft.expectedRevision, draft.expectedMinAge, requestId);
            } catch (IOException | RuntimeException error) {
                failure = error instanceof IOException
                        ? (IOException) error : new IOException(error);
            }
            HostGatewayClient.ChildMutationResult response = result;
            IOException error = failure;
            mainHandler.post(() -> {
                boolean current = isCurrentChildMutationContext(host.uuid, parentProfileId);
                boolean dismissedOrigin = panel != null && panel.isDismissRequested();
                if (!current && !dismissedOrigin) {
                    if (panel != null) panel.discardPendingSave();
                    completion.onFailed(getString(
                            R.string.console_child_management_authorize_failed), false);
                    return;
                }
                if (error != null || response == null
                        || !parentProfileId.equals(response.parentProfileId)) {
                    if (current) clearRejectedChildManagementLease(lease, error);
                    boolean conflict = error instanceof HostGatewayClient.GatewayException
                            && ((HostGatewayClient.GatewayException) error).statusCode == 409;
                    completion.onFailed(getString(conflict
                                    ? R.string.console_child_sharing_save_conflict
                                    : R.string.console_child_sharing_save_failed), conflict);
                    if (conflict && isAttachedChildSharingPanel(panel)) {
                        wireModalFocusTrap(false);
                    }
                    return;
                }
                completion.onSaved(response.revision);
                refreshChildFilterAfterSharing(host, parentProfileId, lease);
            });
        });
    }

    /**
     * A sharing save changed the children's lists on the host: the library's "For child" filter
     * reloads them with the same authorization (nothing to do before it knows them). A failed
     * reload drops them, so the filter never shows lists the host no longer has; a shown filter
     * turns off with a notice.
     */
    private void refreshChildFilterAfterSharing(ComputerDetails host, String parentProfileId,
                                                ChildManagementLease lease) {
        String key = ChildLibraryFilter.key(host.uuid, parentProfileId);
        if (childLibraryFilter.children(key) == null) return;
        fetchChildProfiles(host, parentProfileId, lease, result -> { }, generation -> {
            boolean filtered = childLibraryFilter.selection(key) != null;
            if (!childLibraryFilter.forget(key, generation) || !filtered) return;
            ConsoleUiFeedback.makeText(this, R.string.child_library_filter_dropped,
                    Toast.LENGTH_LONG).show();
            renderPlayniteLibrary(host, currentSunshineApps);
        });
    }

    private void confirmWindowsProfileSwitch(ComputerDetails host,
                                             HostGatewayClient.IntegrationProfile profile) {
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView switchProfile = consolePanel.action(getString(
                R.string.console_switch_windows_profile_confirm, profile.name));
        consolePanel.destructive(switchProfile);
        cancel.setOnClickListener(view -> handlePanelBack());
        switchProfile.setOnClickListener(view -> {
            hideSidePanel();
            requestWindowsProfileSwitch(host, profile);
        });
        showSidePanel(getString(R.string.console_profile_eyebrow),
                getString(R.string.console_switch_windows_profile_title, profile.name),
                getString(R.string.console_switch_windows_profile_details),
                cancel, switchProfile);
    }

    private void requestWindowsProfileSwitch(ComputerDetails host,
                                             HostGatewayClient.IntegrationProfile profile) {
        if (host == null || profile == null || !profile.id.equals(selectedProfileId(host.uuid))) {
            return;
        }
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, profile.id);
        if (connection == null) return;
        cancelWindowsProfileSwitch(false);
        WindowsProfileSwitchAttempt attempt = new WindowsProfileSwitchAttempt(
                host.uuid, profile.id, "android-switch-" + UUID.randomUUID(), connection);
        windowsProfileSwitchAttempt = attempt;

        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        cancel.setOnClickListener(view -> {
            hideSidePanel();
            cancelWindowsProfileSwitch(true);
        });
        showSidePanel(getString(R.string.console_profile_eyebrow),
                getString(R.string.console_switch_windows_profile_progress, profile.name),
                getString(R.string.console_switch_windows_profile_progress_details), cancel);
        executor.execute(() -> runWindowsProfileSwitch(attempt));
    }

    private void runWindowsProfileSwitch(WindowsProfileSwitchAttempt attempt) {
        HostGatewayClient.WindowsSession session;
        try {
            session = hostGatewayClient.switchWindowsSession(
                    attempt.connection, attempt.requestId);
        } catch (IOException | RuntimeException unavailable) {
            finishWindowsProfileSwitch(attempt,
                    new HostGatewayClient.WindowsSession("failed", "gateway_unavailable", "", 1_000));
            return;
        }
        attempt.attemptId = session.attemptId;
        if (!isCurrentWindowsProfileSwitch(attempt)) {
            cancelWindowsProfileSwitchAttempt(attempt);
            return;
        }
        long deadline = SystemClock.elapsedRealtime() + WINDOWS_PROFILE_SWITCH_TIMEOUT_MS;
        while (canPollWindowsProfileSwitch(session)
                && isCurrentWindowsProfileSwitch(attempt)
                && SystemClock.elapsedRealtime() < deadline) {
            try {
                Thread.sleep(Math.max(250L, Math.min(3_000L, session.retryAfterMs)));
                if (!isCurrentWindowsProfileSwitch(attempt)) break;
                session = hostGatewayClient.getWindowsSessionStatus(
                        attempt.connection, attempt.requestId, attempt.attemptId);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException | RuntimeException unavailable) {
                // Retry the same bounded, request-correlated status poll.
            }
        }
        if (!isCurrentWindowsProfileSwitch(attempt)) {
            cancelWindowsProfileSwitchAttempt(attempt);
            return;
        }
        if (canPollWindowsProfileSwitch(session)) {
            cancelWindowsProfileSwitchAttempt(attempt);
            session = new HostGatewayClient.WindowsSession(
                    "failed", "switch_timeout", attempt.attemptId, 1_000);
        }
        finishWindowsProfileSwitch(attempt, session);
    }

    static boolean canPollWindowsProfileSwitch(HostGatewayClient.WindowsSession session) {
        if (session == null || session.attemptId.isEmpty()) return false;
        switch (session.state) {
            case "pending":
            case "credential_available":
            case "credential_issued":
            case "credential_acquired":
            case "credential_submitted":
            case "sign_in_requested":
            case "session_starting":
                return true;
            default:
                return false;
        }
    }

    static boolean windowsProfileSwitchNeedsAttention(
            HostGatewayClient.WindowsSession session) {
        return session != null && ("attention_required".equals(session.state)
                || "credential_missing".equals(session.reason));
    }

    private boolean isCurrentWindowsProfileSwitch(WindowsProfileSwitchAttempt attempt) {
        return windowsProfileSwitchAttempt == attempt && attempt.matches(
                attempt.hostId, selectedProfileId(attempt.hostId), attempt.requestId);
    }

    private void finishWindowsProfileSwitch(WindowsProfileSwitchAttempt attempt,
                                            HostGatewayClient.WindowsSession session) {
        mainHandler.post(() -> {
            if (!isCurrentWindowsProfileSwitch(attempt)) return;
            windowsProfileSwitchAttempt = null;
            refreshWindowsProfileStatus(attempt.hostId);
            hideSidePanel();
            int message = "ready".equals(session.state)
                    ? R.string.console_switch_windows_profile_success
                    : windowsProfileSwitchNeedsAttention(session)
                    ? R.string.console_switch_windows_profile_attention
                    : "cancelled".equals(session.state)
                    ? R.string.console_switch_windows_profile_cancelled
                    : R.string.console_switch_windows_profile_failed;
            ConsoleUiFeedback.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void cancelWindowsProfileSwitch(boolean userVisible) {
        WindowsProfileSwitchAttempt attempt = windowsProfileSwitchAttempt;
        if (attempt == null) return;
        windowsProfileSwitchAttempt = null;
        attempt.cancelled = true;
        if (!attempt.attemptId.isEmpty()) {
            executor.execute(() -> cancelWindowsProfileSwitchAttempt(attempt));
        }
        refreshWindowsProfileStatus(attempt.hostId);
        if (userVisible) {
            ConsoleUiFeedback.makeText(this,
                    R.string.console_switch_windows_profile_cancelled,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void cancelWindowsProfileSwitchAttempt(WindowsProfileSwitchAttempt attempt) {
        if (attempt.attemptId.isEmpty()) return;
        try {
            hostGatewayClient.cancelWindowsSession(attempt.connection,
                    attempt.requestId, attempt.attemptId);
        } catch (IOException | RuntimeException ignored) {
            // Best effort: the Login Broker expires the exact bound attempt.
        }
    }

    private void refreshWindowsProfileStatus(String hostId) {
        ComputerDetails host = hosts.get(hostId);
        if (host == null) return;
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection connection = hostGatewayStore.loadForHost(hostId, address);
        if (connection == null) return;
        executor.execute(() -> {
            HostGatewayClient.IntegrationProfiles profiles;
            try {
                profiles = hostGatewayClient.getIntegrationProfiles(connection);
            } catch (IOException | RuntimeException unavailable) {
                return;
            }
            mainHandler.post(() -> {
                ComputerDetails current = hosts.get(hostId);
                if (current == null) return;
                    saveHostProfiles(hostId, profiles);
                updateHostSelectionTile(current);
                if (hostId.equals(selectedHostUuid)) {
                    updateProfileSelector(current);
                    updateHostStatus();
                }
            });
        });
    }

    private void refreshDashboard() {
        if (managerBinder == null) {
            ConsoleUiFeedback.makeText(this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
            return;
        }
        refreshHostStates();
        refreshSelectedApplications();
        refreshSelectedPlayniteLibrary();
        ConsoleUiFeedback.makeText(this, R.string.console_refresh_started, Toast.LENGTH_SHORT).show();
    }

    private void refreshHostStates() {
        for (ComputerDetails host : hosts.values()) invalidateHostStateAsync(host.uuid);
    }

    private void refreshSelectedApplications() {
        if (appListPoller != null) appListPoller.pollNow();
    }

    private void refreshSelectedPlayniteLibrary() {
        ComputerDetails selected = hosts.get(selectedHostUuid);
        if (selected != null) requestPlayniteRefresh(selected, true);
    }

    private void refreshSessionState(String hostUuid) {
        if (managerBinder == null || hostUuid == null || hostUuid.isEmpty()) return;
        boolean refreshSelected = hostUuid.equalsIgnoreCase(selectedHostUuid);
        executor.execute(() -> {
            ComputerManagerService.ComputerManagerBinder binder = managerBinder;
            if (binder == null) return;
            binder.invalidateStateForComputer(hostUuid);
            if (refreshSelected) {
                mainHandler.post(() -> {
                    if (!active || !hostUuid.equalsIgnoreCase(selectedHostUuid)) return;
                    refreshSelectedApplications();
                    refreshSelectedPlayniteLibrary();
                });
            }
        });
    }

    private void invalidateHostStateAsync(String hostUuid) {
        if (hostUuid == null || hostUuid.isEmpty()) return;
        executor.execute(() -> {
            ComputerManagerService.ComputerManagerBinder binder = managerBinder;
            if (binder != null) binder.invalidateStateForComputer(hostUuid);
        });
    }

    private void toggleHiddenApps() {
        showHiddenApps = !showHiddenApps;
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host != null) renderAppsAsync(host, true);
        ConsoleUiFeedback.makeText(this, showHiddenApps ? R.string.console_hidden_shown
                : R.string.console_hidden_filtered, Toast.LENGTH_SHORT).show();
    }

    private void leaveHost() {
        ProfileAuthorization.revoke();
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host != null && hostGatewayStore.profileSelection(host.uuid).showSelector) {
            showProfileGate(host, true, false);
        } else {
            showHostSelection(selectedHostUuid);
        }
    }

    private void refreshDiscordIndicator() {
        if (topBar == null) return;
        int request = discordStatusGeneration.incrementAndGet();
        ComputerDetails host = hosts.get(selectedHostUuid);
        String address = host == null || host.activeAddress == null ? null
                : host.activeAddress.address;
        GatewayConnection connection = host == null ? null
                : hostGatewayStore.loadForPolling(host.uuid, address, null);
        boolean configured = connection != null
                && hostGatewayStore.isDiscordEnabled(host.uuid, connection.profileId());
        boolean visible = ConsoleTopBar.discordVisible(
                discordAllowedForSelectedProfile(), configured);
        topBar.showDiscord(visible);
        if (!visible) return;
        applyDiscordIndicator(0xFF73D7FF, getString(R.string.console_discord_connecting));
        executor.execute(() -> {
            int color = 0xFFFF6B6B;
            String state;
            try {
                HostGatewayClient.DiscordStatus status =
                        hostGatewayClient.getDiscordStatus(connection);
                if (!status.bridgeOnline) {
                    state = getString(R.string.console_discord_disconnected);
                    color = DiscordPanelController.discordIndicatorColor(
                            false, status.authenticated, status.rpcConnected, false);
                } else if (!status.authenticated) {
                    state = getString(R.string.console_discord_auth_required);
                    color = DiscordPanelController.discordIndicatorColor(
                            true, false, status.rpcConnected, false);
                } else if (DiscordPanelController.discordNeedsReconnect(status)) {
                    state = getString(R.string.console_discord_reconnecting);
                    color = DiscordPanelController.discordIndicatorColor(
                            true, true, false, false);
                } else {
                    HostGatewayClient.DiscordVoice voice =
                            hostGatewayClient.getDiscordVoice(connection, true);
                    state = voice.connected
                            ? getString(R.string.console_discord_voice_active, voice.channelName)
                            : getString(R.string.console_discord_connected);
                    color = DiscordPanelController.discordIndicatorColor(
                            true, true, true, voice.connected);
                }
            } catch (IOException | RuntimeException error) {
                state = getString(R.string.console_discord_error);
            }
            int finalColor = color;
            String finalState = state;
            mainHandler.post(() -> {
                if (request == discordStatusGeneration.get()) {
                    applyDiscordIndicator(finalColor, finalState);
                }
            });
        });
    }

    private void applyDiscordIndicator(int color, String state) {
        topBar.setDiscordStatus(color, getString(R.string.console_discord_description, state));
    }

    private void applyDiscordVoiceIndicator(HostGatewayClient.DiscordVoice voice) {
        if (topBar == null || voice == null || !discordAllowedForSelectedProfile()) return;
        discordStatusGeneration.incrementAndGet();
        String state = voice.connected
                ? getString(R.string.console_discord_voice_active, voice.channelName)
                : getString(R.string.console_discord_connected);
        applyDiscordIndicator(DiscordPanelController.discordIndicatorColor(
                true, true, true, voice.connected), state);
    }

    private void refreshUsbMicrophoneIndicator() {
        if (topBar == null) return;
        boolean allowed = microphoneAllowedForSelectedProfile();
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(UsbMicrophoneService.PREF_ENABLED, false);
        if (!allowed && enabled) {
            UsbMicrophoneService.disableWithState(this, UsbMicrophoneService.STATE_OFF);
        }
        String state = UsbMicrophoneService.state(this);
        topBar.showMicrophone(ConsoleTopBar.microphoneVisible(allowed, enabled),
                microphoneStateColor(state), getString(R.string.console_usb_microphone_description,
                        getString(microphoneStateTextResource(state))));
    }

    static int microphoneStateTextResource(String state) {
        if (UsbMicrophoneService.STATE_ACTIVE.equals(state)) {
            return R.string.console_usb_microphone_active;
        }
        if (UsbMicrophoneService.STATE_CONNECTING.equals(state)) {
            return R.string.console_usb_microphone_connecting;
        }
        if (UsbMicrophoneService.STATE_GATEWAY_RECONNECTING.equals(state)) {
            return R.string.console_usb_microphone_reconnecting;
        }
        if (UsbMicrophoneService.STATE_WAITING_USB.equals(state)) {
            return R.string.console_usb_microphone_waiting_usb;
        }
        if (UsbMicrophoneService.STATE_USB_MULTIPLE.equals(state)) {
            return R.string.console_usb_microphone_multiple;
        }
        if (UsbMicrophoneService.STATE_USB_UNAVAILABLE.equals(state)) {
            return R.string.console_usb_microphone_usb_unavailable;
        }
        if (UsbMicrophoneService.STATE_GATEWAY_REQUIRED.equals(state)) {
            return R.string.console_usb_microphone_gateway_required;
        }
        if (UsbMicrophoneService.STATE_HOST_PLUS_REQUIRED.equals(state)) {
            return R.string.host_plus_required;
        }
        if (UsbMicrophoneService.STATE_PERMISSION_REQUIRED.equals(state)) {
            return R.string.console_usb_microphone_permission_required;
        }
        if (UsbMicrophoneService.STATE_UNSUPPORTED.equals(state)) {
            return R.string.console_usb_microphone_unsupported;
        }
        if (UsbMicrophoneService.STATE_WORKER_MISSING.equals(state)) {
            return R.string.console_usb_microphone_worker_missing;
        }
        if (UsbMicrophoneService.STATE_HOST_UNAVAILABLE.equals(state)) {
            return R.string.console_usb_microphone_host_unavailable;
        }
        if (UsbMicrophoneService.STATE_STEAM_ENDPOINT_UNAVAILABLE.equals(state)) {
            return R.string.console_usb_microphone_steam_endpoint;
        }
        return R.string.console_usb_microphone_off;
    }

    private void refreshUsbForwardingIndicator() {
        if (topBar == null) return;
        UsbForwardingController usb = UsbForwardingController.get(this);
        List<UsbForwardingController.Entry> entries = usb.entries();
        String state = UsbForwardingController.summaryState(entries);
        topBar.showUsb(usb.available() && !entries.isEmpty(), usbStateColor(state),
                getString(R.string.usb_forwarding_description,
                        getString(UsbForwardingStatus.text(state, ""))));
    }

    private void openUsbForwardingPanel() {
        usbConsentDevice = null;
        showUsbForwardingPanel();
    }

    private boolean usbDeviceListShowing() {
        return sideDialog != null && sideDialog.isShowing()
                && (getString(R.string.usb_forwarding_title) + "\n"
                + getString(R.string.usb_forwarding_panel_title)).equals(currentPanelKey);
    }

    /** Share or Cancel answers the question: the list replaces it instead of stacking on it. */
    private void answerUsbConsent() {
        usbConsentDevice = null;
        // The list under the question is current again; Back from it leaves the panel.
        if (!panelHistory.isEmpty()) panelHistory.pop();
        sidePanelTransient = true;
        showUsbForwardingPanel();
    }

    /**
     * The TV's USB devices: choosing one shares it with the selected host after the user agrees
     * (it is then shared again whenever it is plugged in) or stops it. Live while open.
     */
    private void showUsbForwardingPanel() {
        UsbForwardingController usb = UsbForwardingController.get(this);
        List<UsbForwardingController.Entry> entries = usb.entries();
        UsbForwardingController.Entry consent = null;
        for (UsbForwardingController.Entry entry : entries) {
            if (entry.deviceName.equals(usbConsentDevice)) consent = entry;
        }
        String eyebrow = getString(R.string.usb_forwarding_title);
        if (consent != null) {
            String deviceName = consent.deviceName;
            TextView share = consolePanel.icon(
                    consolePanel.action(getString(R.string.usb_forwarding_share)),
                    R.drawable.ic_console_usb);
            share.setOnClickListener(view -> {
                usb.share(deviceName);
                answerUsbConsent();
            });
            TextView cancel = consolePanel.action(getString(R.string.usb_forwarding_cancel));
            cancel.setOnClickListener(view -> answerUsbConsent());
            showSidePanel(eyebrow, getString(R.string.usb_forwarding_consent_title, consent.label),
                    getString(R.string.usb_forwarding_consent_message), share, cancel);
            return;
        }
        usbConsentDevice = null;
        List<View> rows = new ArrayList<>();
        for (UsbForwardingController.Entry entry : entries) {
            TextView row = consolePanel.icon(consolePanel.action(consolePanel.withDetail(
                    entry.label, getString(UsbForwardingStatus.text(entry.state, entry.reason)))),
                    R.drawable.ic_console_usb);
            // Focus stays on the device while its status line changes.
            row.setTag("usb:" + entry.deviceName);
            boolean running = UsbForwardingStatus.isRunning(entry.state);
            row.setOnClickListener(view -> {
                if (running) {
                    usb.stop(entry.deviceName);
                } else {
                    usbConsentDevice = entry.deviceName;
                    showUsbForwardingPanel();
                }
            });
            rows.add(row);
        }
        showSidePanel(eyebrow, getString(R.string.usb_forwarding_panel_title),
                getString(entries.isEmpty() ? R.string.usb_forwarding_empty
                        : R.string.usb_forwarding_panel_details), rows.toArray(new View[0]));
    }

    private static int usbStateColor(String state) {
        if (UsbForwardingController.STATE_ACTIVE.equals(state)) return 0xFF69F0AE;
        if (UsbForwardingController.STATE_OFF.equals(state)) return 0xFF697083;
        if (UsbForwardingController.STATE_REFUSED.equals(state)) return 0xFFFFB74D;
        return 0xFF73D7FF;
    }

    private static int microphoneStateColor(String state) {
        if (UsbMicrophoneService.STATE_ACTIVE.equals(state)) return 0xFF69F0AE;
        if (UsbMicrophoneService.STATE_CONNECTING.equals(state)
                || UsbMicrophoneService.STATE_GATEWAY_RECONNECTING.equals(state)) {
            return 0xFF73D7FF;
        }
        if (UsbMicrophoneService.STATE_OFF.equals(state)) return 0xFF697083;
        return 0xFFFFB74D;
    }

    static boolean shouldShowMicrophoneRetry(String state) {
        return UsbMicrophoneService.STATE_GATEWAY_RECONNECTING.equals(state)
                || UsbMicrophoneService.STATE_HOST_UNAVAILABLE.equals(state)
                || UsbMicrophoneService.STATE_WORKER_MISSING.equals(state)
                || UsbMicrophoneService.STATE_STEAM_ENDPOINT_UNAVAILABLE.equals(state)
                || UsbMicrophoneService.STATE_USB_UNAVAILABLE.equals(state);
    }

    private void showUsbMicrophonePanel() {
        if (!microphoneAllowedForSelectedProfile()) return;
        String state = UsbMicrophoneService.state(this);
        String status = getString(microphoneStateTextResource(state));
        List<View> actions = new ArrayList<>();
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(UsbMicrophoneService.PREF_ENABLED, false);
        if (enabled) {
            TextView stop = consolePanel.action(getString(R.string.console_usb_microphone_stop));
            stop.setOnClickListener(view -> {
                UsbMicrophoneService.disableWithState(
                        this, UsbMicrophoneService.STATE_OFF);
                showUsbMicrophonePanel();
            });
            actions.add(stop);
            if (shouldShowMicrophoneRetry(state)) {
                TextView retry = consolePanel.action(getString(R.string.console_usb_microphone_retry));
                retry.setOnClickListener(view -> {
                    UsbMicrophoneService.retryFromVisibleActivity(this);
                    hideSidePanel();
                });
                actions.add(retry);
            }
        }
        TextView settings = consolePanel.action(getString(R.string.console_usb_microphone_settings));
        settings.setOnClickListener(view -> showSettings(SETTINGS_CONTROLLERS));
        actions.add(settings);
        showSidePanel(getString(R.string.console_action_usb_microphone),
                getString(R.string.console_usb_microphone_panel_title),
                status + "\n\n" + getString(R.string.console_usb_microphone_panel_details),
                actions.toArray(new View[0]));
    }

    private void setDiscordNotificationPending(boolean pending) {
        if (topBar != null) topBar.setDiscordUnread(pending);
    }

    /** A warm-up this console asked for, or the one its stream Home opened over. */
    private boolean tracksWarmUp() {
        return !warmUpClientHostId.isEmpty() && (warmUpRequest > 0L || homePreparingOperation > 0L);
    }

    private void relinquishWarmUp() {
        warmUpRequest = 0L;
        warmUpClientHostId = "";
        warmUpStatus = WARM_UP_NONE;
    }

    private void setWarmUpStatus(String hostId, long request, int status) {
        mainHandler.post(() -> {
            if (request <= 0L || request != warmUpRequest
                    || !normalizeId(hostId).equals(warmUpClientHostId)) return;
            warmUpStatus = status;
            renderHosts();
        });
    }

    private void cancelCurrentPreparation() {
        cancelCurrentPreparation(null);
    }

    private void cancelCurrentPreparation(ChildLaunchBinding preservedBinding) {
        if (preservedBinding == null) childEligibilityLaunchToken++;
        cancelChildLaunches(selectedHostUuid,
                selectedHostUuid == null ? null : selectedProfileId(selectedHostUuid),
                preservedBinding);
        boolean cancelledPreparation = sessionOrchestrator != null
                && sessionOrchestrator.cancelPreparation(warmUpClientHostId);
        if (cancelledPreparation) {
            relinquishWarmUp();
            renderHosts();
        } else if (sessionOrchestrator != null) {
            sessionOrchestrator.cancel();
        }
    }


    private void resumeSession(ComputerDetails host) {
        if (host == null) return;
        SessionSnapshot snapshot = resolveSessionSnapshot(currentHost(host.uuid));
        if (!snapshot.isResumeAvailable() || snapshot.hostGameAppId == 0) return;
        // A suspended session names no game while the host sleeps or holds it frozen: this
        // client's record of the game it suspended is what the user resumes.
        SuspendedSessionStore.Session suspended = snapshot.isSuspended()
                ? suspendedSession(host.uuid, snapshot.profileId) : null;
        String gameId = !snapshot.playniteGameId.isEmpty() || suspended == null
                ? snapshot.playniteGameId : SessionSnapshot.normalize(suspended.playniteGameId);
        if (selectedProfileIsChild(host.uuid)
                && (gameId.isEmpty()
                || providerGameMetadata(host.uuid, snapshot.profileId, gameId) == null)) return;
        NvApp running = null;
        for (NvApp app : appLists.apps(host.uuid)) {
            if (app.getAppId() == snapshot.hostGameAppId) {
                running = app;
                break;
            }
        }
        if (running == null) {
            String name = findAppName(host, snapshot.hostGameAppId);
            running = new NvApp(name == null ? "Moonlight" : name,
                    snapshot.hostGameAppId, false);
        }
        String loadingArtworkGameId = gameId.isEmpty()
                ? uniquePlayniteGameIdForRunningApp(host, running) : gameId;
        PlayIntent intent = gameId.isEmpty()
                ? PlayIntent.sunshineApp(host.uuid, snapshot.profileId,
                running.getAppId(), running.getAppName(),
                running.isHdrSupported(), "", loadingArtworkGameId)
                : providerGameIntent(host.uuid, snapshot.profileId,
                running.getAppId(), running.getAppName(),
                running.isHdrSupported(), gameId, loadingArtworkGameId, "");
        playProviderGame(intent);
    }
    private String uniquePlayniteGameIdForRunningApp(ComputerDetails host, NvApp app) {
        if (host == null || !host.uuid.equals(selectedHostUuid)) return "";
        String synchronizedId = PlayniteTargetResolver.playniteGameId(app);
        if (!synchronizedId.isEmpty()) return synchronizedId;
        int appId = app == null ? 0 : app.getAppId();
        String match = "";
        for (PlayniteDashboardItem item : allPlayniteItems) {
            if (item.sunshineAppId == null || item.sunshineAppId != appId) continue;
            if (!match.isEmpty()) return "";
            match = item.game.playniteGameId;
        }
        return HostGatewayClient.isPlayniteId(match) ? match : "";
    }

    private String uniquePlayniteGameIdForRunningApp(ComputerDetails host, int appId) {
        return uniquePlayniteGameIdForRunningApp(host,
                PlayniteTargetResolver.findById(appLists.apps(host.uuid), appId));
    }

    /**
     * "Pair" on an unpaired tile. A host whose Gateway this TV already pairs needs streaming
     * only; a new one is paired in the wizard's pairing step (code, progress, repair screens).
     */
    private void pairHost(ComputerDetails host) {
        if (host.state != ComputerDetails.State.ONLINE || host.activeAddress == null) {
            ConsoleUiFeedback.makeText(this, R.string.pair_pc_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, host.activeAddress.address);
        if (connection != null) {
            pairStream(host, connection, false);
            return;
        }
        FirstRunWizard wizard = firstRunWizard();
        if (wizard != null) wizard.open(FirstRunPlan.Entry.PAIR_HOST, host.uuid);
    }

    private void beginChildPairingRepair() {
        String hostId = childLaunchHostId;
        String profileId = childLaunchProfileId;
        IdentityBindingStart binding = tryBindExistingVibepolloIdentity(hostId, profileId, true);
        if (binding == IdentityBindingStart.STARTED
                || binding == IdentityBindingStart.IN_FLIGHT) {
            if (streamLoadingView != null) streamLoadingView.stopAndHide();
            cancelCurrentPreparation();
            hostPreparationGeneration.incrementAndGet();
            showHome();
            return;
        }
        ConsoleUiFeedback.makeText(this,
                binding == IdentityBindingStart.CONTEXT_UNAVAILABLE
                        ? R.string.console_child_launch_pairing_required
                        : R.string.console_child_launch_pairing_retry,
                Toast.LENGTH_LONG).show();
    }

    private void beginChildPairingRepair(String hostId, String profileId) {
        ComputerDetails host = currentHost(hostId);
        if (host == null || !isChildPairingRepairContextCurrent(hostId, profileId,
                profileGateGeneration.get())) {
            ConsoleUiFeedback.makeText(this,
                    R.string.console_child_launch_pairing_context_changed,
                    Toast.LENGTH_LONG).show();
            showHome();
            return;
        }
        GatewayConnection connection = childConnection(host, profileId);
        if (connection == null) {
            ConsoleUiFeedback.makeText(this,
                    R.string.console_child_launch_pairing_unavailable,
                    Toast.LENGTH_LONG).show();
            showHome();
            return;
        }
        final int expectedGeneration = profileGateGeneration.get();
        if (streamLoadingView != null) streamLoadingView.stopAndHide();
        cancelCurrentPreparation();
        hostPreparationGeneration.incrementAndGet();
        showHome();
        consoleFeedback.showConfirm(
                getString(R.string.console_pair_host_renew_title),
                getString(R.string.console_pair_host_renew_details, host.name),
                getString(android.R.string.cancel),
                getString(R.string.console_pair_host_renew_action),
                false,
                () -> {
                    if (!isChildPairingRepairContextCurrent(hostId, profileId,
                            expectedGeneration)) {
                        ConsoleUiFeedback.makeText(this,
                                R.string.console_child_launch_pairing_context_changed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    pairStream(host, connection, true);
                });
    }

    private boolean isChildPairingRepairContextCurrent(String hostId, String profileId,
                                                       int expectedGeneration) {
        return hostId != null && hostId.equals(selectedHostUuid)
                && profileId != null && profileId.equals(selectedProfileId(hostId))
                && expectedGeneration == profileGateGeneration.get()
                && currentHost(hostId) != null;
    }

    /**
     * Streaming through the Gateway for a host whose Gateway is paired. {@code renew}
     * replaces the existing pairing (child repair) and is dropped once the host, profile or
     * profile gate it started for is no longer the current one.
     */
    private void pairStream(ComputerDetails host, GatewayConnection connection, boolean renew) {
        final String profileId = connection.profileId();
        final String selectedHostAtStart = selectedHostUuid;
        final String selectedProfileAtStart = selectedProfileId(host.uuid);
        final String profileGateHostAtStart = profileGateHostUuid;
        final int profileGateGenerationAtStart = profileGateGeneration.get();
        BooleanSupplier current = () -> isPairingContextCurrent(host.uuid, profileId,
                selectedHostAtStart, selectedProfileAtStart, profileGateHostAtStart,
                profileGateGenerationAtStart);
        ConsoleUiFeedback.makeText(this, R.string.console_pair_host_stream,
                Toast.LENGTH_SHORT).show();
        hostPairingFlow.pairStream(host, connection, null, renew, current,
                new HostPairingFlow.Listener() {
                    @Override public void onStage(String stage) { }

                    @Override public void onPaired(ComputerDetails paired) {
                        pairingSucceeded(paired, R.string.console_pair_host_success);
                    }

                    @Override public void onFailed(ComputerDetails failed,
                                                   HostPairingFlow.Failure failure) {
                        showPairingFailure(failed, failure, renew ? connection : null, current);
                    }
                });
    }

    private void pairingSucceeded(ComputerDetails host, int message) {
        if (consoleAudioEngine != null) {
            consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.SUCCESS);
        }
        ConsoleUiFeedback.makeText(this, message, Toast.LENGTH_SHORT).show();
        invalidateHostStateAsync(host.uuid);
        resolveProfileGate(host, true, false);
    }

    private void showPairingFailure(ComputerDetails host, HostPairingFlow.Failure failure,
                                    GatewayConnection repairConnection,
                                    BooleanSupplier current) {
        if (consoleAudioEngine != null) {
            consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.ERROR);
        }
        consoleFeedback.showConfirm(getString(R.string.console_pair_host_failed_title),
                getString(R.string.console_pair_host_failed_details, failure.detail),
                getString(android.R.string.cancel),
                getString(repairConnection == null
                        ? R.string.console_pair_stream_manually
                        : R.string.console_pair_host_renew_action), false,
                () -> {
                    if (repairConnection == null) {
                        pairManually(host);
                    } else if (current.getAsBoolean()) {
                        pairStream(host, repairConnection, true);
                    }
                });
    }

    private void pairManually(ComputerDetails host) {
        if (sideDialog != null && sideDialog.isShowing()) hideSidePanel();
        hostPairingFlow.pairManually(host, new HostPairingFlow.Listener() {
            @Override public void onStage(String stage) {
                ConsoleUiFeedback.makeText(ConsoleActivity.this, stage,
                        Toast.LENGTH_SHORT).show();
            }

            @Override public void onManualPin(String pin) {
                consoleFeedback.showMessage(getString(R.string.pair_pairing_title),
                        getString(R.string.pair_pairing_msg) + " " + pin + "\n\n"
                                + getString(R.string.pair_pairing_help),
                        getString(R.string.console_close), null);
            }

            @Override public void onPaired(ComputerDetails paired) {
                consoleFeedback.dismissModal();
                pairingSucceeded(paired, R.string.console_pair_success);
            }

            @Override public void onFailed(ComputerDetails failed,
                                           HostPairingFlow.Failure failure) {
                consoleFeedback.dismissModal();
                if (consoleAudioEngine != null) {
                    consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.ERROR);
                }
                ConsoleUiFeedback.makeText(ConsoleActivity.this, failure.detail,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Unpairing also forgets the host here (Gateway pairing, offline PINs, cache): confirm. */
    private void confirmUnpairHost(ComputerDetails host) {
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView unpair = consolePanel.action(getString(R.string.console_unpair_host));
        consolePanel.destructive(unpair);
        cancel.setOnClickListener(view -> handlePanelBack());
        unpair.setOnClickListener(view -> unpairHost(host));
        showSidePanel(getString(R.string.console_host_eyebrow),
                getString(R.string.console_unpair_host_title, host.name),
                getString(R.string.console_unpair_host_details), cancel, unpair);
    }

    private void unpairHost(ComputerDetails host) {
        if (host.state != ComputerDetails.State.ONLINE || host.activeAddress == null) {
            ConsoleUiFeedback.makeText(this, R.string.error_pc_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            ConsoleUiFeedback.makeText(this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
            return;
        }
        hideSidePanel();
        ConsoleUiFeedback.makeText(this, R.string.unpairing, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            String message;
            boolean unpaired = false;
            try {
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(host),
                        host.httpsPort, managerBinder.getUniqueId(), host.serverCert,
                        PlatformBinding.getCryptoProvider(this));
                if (http.getPairState() != PairingManager.PairState.PAIRED) {
                    message = getString(R.string.unpair_error);
                } else {
                    http.unpair();
                    unpaired = http.getPairState() == PairingManager.PairState.NOT_PAIRED;
                    message = getString(unpaired ? R.string.unpair_success : R.string.unpair_fail);
                }
            } catch (UnknownHostException error) {
                message = getString(R.string.error_unknown_host);
            } catch (FileNotFoundException error) {
                message = getString(R.string.error_404);
            } catch (IOException | XmlPullParserException error) {
                message = error.getMessage();
            }
            String result = message;
            boolean forget = unpaired;
            mainHandler.post(() -> {
                // The host no longer knows this device: forget it here the same way as "Usuń".
                if (forget) forgetHost(host);
                ConsoleUiFeedback.makeText(this,
                        result == null || result.isEmpty() ? getString(R.string.unpair_fail) : result,
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    /** Off the main thread: remembers what the host's Gateway reports, or null. */
    private HostGatewayClient.PowerStatus refreshHostPowerStatus(String hostId,
                                                                 GatewayConnection connection) {
        try {
            HostGatewayClient.PowerStatus status = hostGatewayClient.getPowerStatus(connection);
            hostPowerStatus.put(hostId, status);
            if (!status.rebootRequired) {
                pendingRestartAcknowledged.remove(hostId);
            }
            return status;
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    /** "Zgłoś problem" from Settings: the selected host's Gateway, null without one. */
    private GatewayConnection reportConnection() {
        ComputerDetails host = currentHost(selectedHostUuid);
        if (host == null) return null;
        return hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress != null ? host.activeAddress.address : null,
                selectedProfileId(host.uuid));
    }

    private GatewayConnection selectedGatewayConnection(ComputerDetails host) {
        String activeAddress = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, activeAddress, selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.console_gateway_pair_required,
                    Toast.LENGTH_LONG).show();
        }
        return connection;
    }

    private void confirmHostPower(ComputerDetails host, boolean shutdown) {
        GatewayConnection connection = selectedGatewayConnection(host);
        if (connection == null) return;
        SessionSnapshot session = resolveSessionSnapshot(host);
        boolean playing = session.hasActiveSession() || session.streamerAppId > 0;
        executor.execute(() -> {
            HostGatewayClient.PowerStatus status = refreshHostPowerStatus(host.uuid, connection);
            mainHandler.post(() -> {
                if (isDestroyed()) return;
                List<String> details = new ArrayList<>();
                details.add(getString(shutdown ? R.string.console_shutdown_details
                        : R.string.console_restart_details));
                if (playing) details.add(getString(R.string.console_power_session_warning));
                if (!shutdown && status != null && status.rebootRequired) {
                    details.add(getString(R.string.console_restart_updates_pending));
                }
                // Unknown counts as unverified: the warning is the safe side.
                if (shutdown && (status == null || !status.wakeFromShutdownVerified)) {
                    details.add(getString(R.string.console_shutdown_wake_warning));
                }
                TextView cancel = consolePanel.action(getString(R.string.console_cancel));
                TextView confirm = consolePanel.action(getString(shutdown
                        ? R.string.console_shutdown : R.string.console_restart));
                consolePanel.destructive(confirm);
                cancel.setOnClickListener(view -> handlePanelBack());
                confirm.setOnClickListener(view -> {
                    hideSidePanel();
                    requestHostPower(host, connection, shutdown);
                });
                showSidePanel(getString(R.string.console_host_power),
                        getString(shutdown ? R.string.console_shutdown_title
                                : R.string.console_restart_title, host.name),
                        TextUtils.join("\n\n", details), cancel, confirm);
            });
        });
    }

    private void requestHostPower(ComputerDetails host, GatewayConnection connection,
                                  boolean shutdown) {
        // Remembered like a sleep: the host's silence that follows is this command, and a
        // restarted host signs its actor in again (signInAfterRestart).
        long requestedAt = System.currentTimeMillis();
        sessionModel.post(new SessionEvent.HostPowerRequested(host.uuid, shutdown
                ? HostPowerStateStore.Kind.SHUTDOWN : HostPowerStateStore.Kind.RESTART,
                selectedProfileId(host.uuid), requestedAt));
        renderHostSelection();
        if (host.uuid.equals(selectedHostUuid)) updateHostSelector();
        ConsoleUiFeedback.makeText(this, getString(shutdown ? R.string.console_shutdown_request
                : R.string.console_restart_request, host.name), Toast.LENGTH_SHORT).show();
        // Its own thread like sleep: leaving MoonWaker does not interrupt the request.
        ComputerDetails target = new ComputerDetails(host);
        HostGatewayClient client = hostGatewayClient;
        SessionModel model = sessionModel;
        Handler main = mainHandler;
        WeakReference<ConsoleActivity> console = new WeakReference<>(this);
        new Thread(() -> {
            boolean accepted;
            try {
                accepted = client.powerHost(connection, shutdown).optBoolean("accepted", false);
            } catch (IOException | RuntimeException error) {
                accepted = false;
            }
            if (accepted) hostPowerStatus.remove(target.uuid);
            long acceptedAt = System.currentTimeMillis();
            HostReadiness.powerAnswered(model, target.uuid, requestedAt, null, accepted,
                    acceptedAt);
            boolean sent = accepted;
            main.post(() -> {
                ConsoleActivity shown = console.get();
                if (shown == null || shown.isDestroyed()) return;
                shown.renderHostSelection();
                if (target.uuid.equals(shown.selectedHostUuid)) shown.updateHostSelector();
                ConsoleUiFeedback.makeText(shown, shown.getString(sent
                        ? R.string.console_power_accepted : R.string.console_power_failed,
                        target.name), Toast.LENGTH_LONG).show();
            });
            if (accepted) {
                HostReadiness.settlePower(model, target.uuid, acceptedAt, null,
                        HostReadiness.awaitDown(() -> target, () -> false));
            }
        }, "MoonWaker-HostPower").start();
    }

    /**
     * The host this client restarted is back: the actor that restarted it is signed in to
     * Windows again through the Gateway's session ensure, which applies that actor's own
     * entitlements and stored credentials. A Gateway not answering yet is asked again on a
     * later poll; any answer settles the record (a refusal is told once).
     */
    private void signInAfterRestart(ComputerDetails host, HostPowerStateStore.State restart) {
        if (!restartSignInInFlight.add(host.uuid)) return;
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, address, restart.profileId);
        if (connection == null) {
            restartSignInInFlight.remove(host.uuid);
            sessionModel.post(new SessionEvent.HostPowerCleared(host.uuid, restart.requestedAt));
            return;
        }
        PlayniteTransitionGateway gateway = new PlayniteTransitionGateway(sessionModel,
                host.uuid, connection, gatewayActorContext(host.uuid, restart.profileId),
                hostGatewayClient, SystemClock::uptimeMillis);
        SessionModel model = sessionModel;
        Handler main = mainHandler;
        WeakReference<ConsoleActivity> console = new WeakReference<>(this);
        String hostId = host.uuid;
        String name = host.name;
        renderHostSelection();
        // Its own thread: the attempt waits up to the broker's bound, off the console's work.
        new Thread(() -> {
            Boolean signedIn;
            try {
                signedIn = gateway.unlockHostSession(() -> false);
            } catch (IOException | RuntimeException unavailable) {
                signedIn = null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                signedIn = null;
            }
            restartSignInInFlight.remove(hostId);
            if (signedIn == null) return;
            model.post(new SessionEvent.HostPowerCleared(hostId, restart.requestedAt));
            boolean ready = signedIn;
            main.post(() -> {
                ConsoleActivity shown = console.get();
                if (shown == null || shown.isDestroyed()) return;
                shown.renderHostSelection();
                if (hostId.equals(shown.selectedHostUuid)) shown.updateHostSelector();
                if (!ready) {
                    ConsoleUiFeedback.makeText(shown, shown.getString(
                            R.string.console_restart_sign_in_failed, name),
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "MoonWaker-RestartSignIn").start();
    }

    private void showWindowsUpdate(ComputerDetails host) {
        GatewayConnection connection = selectedGatewayConnection(host);
        if (connection == null) return;
        executor.execute(() -> {
            HostGatewayClient.PowerStatus status = refreshHostPowerStatus(host.uuid, connection);
            mainHandler.post(() -> {
                if (isDestroyed()) return;
                TextView back = consolePanel.action(getString(R.string.console_cancel));
                back.setOnClickListener(view -> handlePanelBack());
                TextView scan = consolePanel.action(
                        getString(R.string.console_windows_update_scan));
                scan.setOnClickListener(view -> {
                    hideSidePanel();
                    scanWindowsUpdate(host, connection);
                });
                showSidePanel(getString(R.string.console_host_eyebrow),
                        getString(R.string.console_windows_update),
                        TextUtils.join("\n\n", windowsUpdateDetails(status)), back, scan);
            });
        });
    }

    /** The scan result first, then the pending restart and the note. */
    private List<String> windowsUpdateDetails(HostGatewayClient.PowerStatus status) {
        List<String> details = new ArrayList<>();
        if (status == null) {
            details.add(getString(R.string.console_windows_update_unavailable));
            return details;
        }
        switch (status.scanState) {
            case "running":
                details.add(getString(R.string.console_windows_update_scanning));
                break;
            case "done":
                details.add(status.availableUpdates == 0
                        ? getString(R.string.console_windows_update_none)
                        : getString(R.string.console_windows_update_available,
                        status.availableUpdates, status.downloadedUpdates));
                break;
            case "failed":
                details.add(getString(R.string.console_windows_update_failed));
                break;
            default:
                details.add(getString(R.string.console_windows_update_not_checked));
        }
        if (status.rebootRequired) {
            details.add(getString(R.string.console_restart_updates_pending));
        }
        details.add(getString(R.string.console_windows_update_note));
        return details;
    }

    /** The scan runs on the host; this thread only follows it and reports the result. */
    private void scanWindowsUpdate(ComputerDetails host, GatewayConnection connection) {
        ConsoleUiFeedback.makeText(this, R.string.console_windows_update_scanning,
                Toast.LENGTH_SHORT).show();
        HostGatewayClient client = hostGatewayClient;
        Handler main = mainHandler;
        WeakReference<ConsoleActivity> console = new WeakReference<>(this);
        String hostId = host.uuid;
        new Thread(() -> {
            HostGatewayClient.PowerStatus status;
            try {
                client.scanWindowsUpdate(connection);
                // A Windows Update search can take minutes; the Gateway bounds it at ten.
                long deadline = SystemClock.elapsedRealtime() + 11 * 60_000L;
                do {
                    Thread.sleep(3_000L);
                    status = client.getPowerStatus(connection);
                    hostPowerStatus.put(hostId, status);
                } while ("running".equals(status.scanState)
                        && SystemClock.elapsedRealtime() < deadline);
            } catch (IOException | RuntimeException error) {
                status = null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            HostGatewayClient.PowerStatus result = status;
            main.post(() -> {
                ConsoleActivity shown = console.get();
                if (shown == null || shown.isDestroyed()) return;
                ConsoleUiFeedback.makeText(shown, shown.windowsUpdateDetails(result).get(0),
                        Toast.LENGTH_LONG).show();
            });
        }, "MoonWaker-WindowsUpdateScan").start();
    }

    private void confirmSleepHost(ComputerDetails host) {
        String activeAddress = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, activeAddress, selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.console_gateway_pair_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView sleep = consolePanel.action(getString(R.string.console_sleep));
        consolePanel.destructive(sleep);
        cancel.setOnClickListener(view -> handlePanelBack());
        sleep.setOnClickListener(view -> {
            hideSidePanel();
            requestHostSleep(host, connection);
        });
        showSidePanel(getString(R.string.console_host_power),
                getString(R.string.console_sleep_title, host.name),
                getString(R.string.console_sleep_details),
                cancel, sleep);
    }

    private void requestHostSleep(ComputerDetails host,
                                  GatewayConnection connection) {
        final SuspendedSessionStore.Session suspendedRequest;
        final long sleepRequestedAt;
        SessionSnapshot session = resolveSessionSnapshot(host);
        // Recoverable over refusal: with the streamer unknown the session this client knows of
        // (its retained, suspended or reconnect app) is saved anyway; a stale resume record is
        // recoverable, a lost one is not. With none known there is no app to resume.
        int runningAppId = session.streamerAppId == SessionSnapshot.STREAMER_APP_UNKNOWN
                ? session.hostGameAppId : session.streamerAppId;
        if (runningAppId > 0) {
            String gameId = uniquePlayniteGameIdForRunningApp(host, runningAppId);
            if (gameId.isEmpty()) {
                gameId = preferences.getString("selected_playnite." + host.uuid, "");
            }
            String title = findAppName(host, runningAppId);
            suspendedRequest = new SuspendedSessionStore.Session(
                    UUID.randomUUID().toString(), host.uuid, selectedProfileId(host.uuid),
                    runningAppId, gameId,
                    title == null ? getString(R.string.console_game) : title,
                    "", System.currentTimeMillis(),
                    executionProfileId(host.uuid, selectedProfileId(host.uuid)));
            sleepRequestedAt = 0L;
        } else {
            suspendedRequest = null;
            sleepRequestedAt = System.currentTimeMillis();
            sessionModel.post(new SessionEvent.HostPowerRequested(host.uuid,
                    HostPowerStateStore.Kind.SLEEP, selectedProfileId(host.uuid),
                    sleepRequestedAt));
        }
        renderHostSelection();
        if (host.uuid.equals(selectedHostUuid)) updateHostSelector();
        ConsoleUiFeedback.makeText(this, getString(R.string.console_sleep_request, host.name),
                Toast.LENGTH_SHORT).show();
        // Its own thread on app-scoped state only, not this Activity's executor: leaving
        // MoonWaker neither interrupts the request nor leaves the host "going to sleep"; the
        // host's real state settles the record.
        ComputerDetails target = new ComputerDetails(host);
        HostGatewayClient client = hostGatewayClient;
        SessionModel model = sessionModel;
        Handler main = mainHandler;
        WeakReference<ConsoleActivity> console = new WeakReference<>(this);
        new Thread(() -> {
            boolean accepted;
            try {
                accepted = client.sleepHost(connection).optBoolean("accepted", false);
            } catch (IOException | RuntimeException error) {
                accepted = false;
            }
            long acceptedAt = System.currentTimeMillis();
            HostReadiness.powerAnswered(model, target.uuid, sleepRequestedAt, suspendedRequest,
                    accepted, acceptedAt);
            boolean sent = accepted;
            main.post(() -> {
                ConsoleActivity shown = console.get();
                if (shown == null || shown.isDestroyed()) return;
                shown.renderHostSelection();
                if (!sent || target.uuid.equals(shown.selectedHostUuid)) {
                    shown.updateHostSelector();
                }
                ConsoleUiFeedback.makeText(shown, shown.getString(sent
                        ? R.string.console_sleep_accepted : R.string.console_sleep_failed,
                        target.name), Toast.LENGTH_LONG).show();
            });
            if (accepted) {
                HostReadiness.settlePower(model, target.uuid, acceptedAt, suspendedRequest,
                        HostReadiness.awaitDown(() -> target, () -> false));
            }
        }, "MoonWaker-HostSleep").start();
    }

    private void wakeHost(ComputerDetails host) {
        if (host.state == ComputerDetails.State.ONLINE) {
            ConsoleUiFeedback.makeText(this, R.string.wol_pc_online, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ConsoleHostPresentation.canWake(host)) {
            ConsoleUiFeedback.makeText(this, R.string.wol_no_mac, Toast.LENGTH_LONG).show();
            return;
        }
        if (hostStateController.isWaking(host.uuid)) {
            ConsoleUiFeedback.makeText(this, R.string.console_status_waking, Toast.LENGTH_SHORT).show();
            return;
        }
        sessionModel.post(new SessionEvent.HostPowerCleared(host.uuid));
        long wakeToken = hostStateController.beginWaking(host.uuid);
        if (wakeToken < 0L) return;
        renderHostSelection();
        if (host.uuid.equals(selectedHostUuid)) updateHostSelector();
        executor.execute(() -> {
            int message;
            try {
                WakeOnLanSender.sendWolPacket(host);
                message = R.string.wol_waking_msg;
            } catch (IOException error) {
                message = R.string.wol_fail;
            }
            int toastMessage = message;
            mainHandler.post(() -> {
                ConsoleUiFeedback.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
                if (toastMessage == R.string.wol_fail) {
                    finishHostWaking(host.uuid, wakeToken);
                } else {
                    invalidateHostStateAsync(host.uuid);
                    mainHandler.postDelayed(() -> finishHostWaking(host.uuid, wakeToken),
                            HOST_WAKING_TIMEOUT_MS);
                }
            });
        });
    }

    private void resumeSuspendedSession(ComputerDetails host) {
        if (host == null) return;
        String selectedActor = selectedProfileId(host.uuid);
        SuspendedSessionStore.Session suspended = suspendedSession(host.uuid, selectedActor);
        if (suspended == null) {
            requestPlayniteRefresh(host, false);
            return;
        }
        if (selectedProfileIsChild(host.uuid)
                && (suspended.playniteGameId.isEmpty()
                || providerGameMetadata(host.uuid, selectedProfileId(host.uuid),
                suspended.playniteGameId) == null)) return;
        suspendedPlayniteGameId = suspended.playniteGameId;
        resumePlayniteGameId = suspended.playniteGameId;
        NvApp target = new NvApp(suspended.title.isEmpty()
                ? getString(R.string.console_game) : suspended.title,
                suspended.sunshineAppId, false);
        PlayIntent intent = suspended.playniteGameId.isEmpty()
                ? PlayIntent.sunshineApp(host.uuid, selectedActor,
                target.getAppId(), target.getAppName(),
                false, "", uniquePlayniteGameIdForRunningApp(host, target))
                : providerGameIntent(host.uuid, selectedActor,
                target.getAppId(), target.getAppName(),
                false, suspended.playniteGameId, suspended.playniteGameId, "");
        playProviderGame(intent);
    }
    private void confirmTerminateSession(ComputerDetails host) {
        confirmTerminateSession(host, "");
    }

    private void confirmTerminateSession(ComputerDetails host, String providerGameId) {
        SessionSnapshot session = renderedSessionSnapshot(host);
        if (host == null || host.state != ComputerDetails.State.ONLINE
                || (!session.hostHoldsSession() && session.streamerAppId == 0)
                || managerBinder == null) return;
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView terminate = consolePanel.action(getString(R.string.console_close_session));
        consolePanel.destructive(terminate);
        cancel.setOnClickListener(view -> handlePanelBack());
        // The close stops exactly the game this dialog names.
        String gameId = !normalizeId(providerGameId).isEmpty() ? normalizeId(providerGameId)
                : HostGatewayClient.isPlayniteId(session.playniteGameId)
                ? session.playniteGameId : "";
        terminate.setOnClickListener(view -> requestTerminateSession(host, gameId));
        String name = activeSessionName(host, session);
        showSidePanel(getString(R.string.console_status_active_session),
                getString(R.string.console_terminate_session_title), name == null
                        ? getString(R.string.console_terminate_session_details)
                        : getString(R.string.console_terminate_session_details_game, name),
                cancel, terminate);
    }

    private void requestTerminateSession(ComputerDetails host, String expectedProviderGameId) {
        HostProfileKey profileKey = selectedProfileKey(host.uuid);
        SuspendedSessionStore.Session suspended =
                suspendedSession(host.uuid, profileKey.profileId);
        String expectedSuspendId = suspended == null ? "" : suspended.suspendId;
        final String providerGameId = normalizeId(expectedProviderGameId);
        // The host status shows the close ("Closing session…"); no progress panel.
        hideSidePanelImmediately();
        SessionModel.Snapshot retained = sessionModel.snapshot();
        if (retained.hostId.equalsIgnoreCase(host.uuid)
                && retained.executionProfileId.equals(
                executionProfileId(host.uuid, profileKey.profileId))
                && !retained.streamSessionId.isEmpty()) {
            sessionModel.dispatch(new SessionEvent.TerminateRequested(retained.streamSessionId,
                    providerGameId, terminationOutcome(outcome -> {
                        finishTerminateSession(host, profileKey, expectedSuspendId, outcome);
                        onRequestedStreamClosed();
                    })), result -> {
                if (result == SessionModel.TerminationResult.NO_CONTROLLER) {
                    terminateSessionByControlPlane(host, profileKey, expectedSuspendId,
                            providerGameId);
                }
            });
            return;
        }
        terminateSessionByControlPlane(host, profileKey, expectedSuspendId, providerGameId);
    }

    /** A retained close's result; a game left behind a closed stream is its own outcome. */
    private static SessionModel.TerminationCallback terminationOutcome(
            Consumer<HostSessionTermination.Outcome> finished) {
        return new SessionModel.TerminationCallback() {
            @Override public void complete(boolean success) {
                finished.accept(HostSessionTermination.Outcome.of(success));
            }

            @Override public void gameLeftOnHost() {
                finished.accept(HostSessionTermination.Outcome.CLOSED_GAME_UNVERIFIED);
            }
        };
    }

    private void terminateSessionByControlPlane(ComputerDetails host, HostProfileKey profileKey,
                                                String expectedSuspendId, String providerGameId) {
        String executionProfile = executionProfileId(host.uuid, profileKey.profileId);
        sessionModel.dispatch(new SessionEvent.LocalCloseBegun(host.uuid, profileKey.profileId,
                executionProfile, false), closeToken -> {
            queueSessionPresentationUpdate();
            executor.execute(() -> {
                HostSessionTermination.Outcome outcome = null;
                try {
                    outcome = terminateKnownControlPlane(host, profileKey.profileId, providerGameId);
                } finally {
                    // Also on an unexpected failure: the host must not stay "closing".
                    sessionModel.post(new SessionEvent.LocalCloseFinished(host.uuid,
                            profileKey.profileId, executionProfile, closeToken,
                            outcome != null && outcome.streamClosed()));
                    HostSessionTermination.Outcome finished = outcome;
                    mainHandler.post(() -> {
                        if (finished != null) {
                            finishTerminateSession(host, profileKey, expectedSuspendId,
                                    finished);
                        }
                        queueSessionPresentationUpdate();
                    });
                }
            });
        });
    }

    /** A local close changed what the host status and game tiles show. */
    private void queueSessionPresentationUpdate() {
        queueConsoleUpdates(ConsoleUpdateChannels.SELECTED_HOST
                | ConsoleUpdateChannels.SESSION);
    }

    private void finishTerminateSession(ComputerDetails host, HostProfileKey profileKey,
                                        String expectedSuspendId,
                                        HostSessionTermination.Outcome outcome) {
        // The user's close: a closed stream ends the session even when a game was left.
        if (outcome.streamClosed()) {
            if (!expectedSuspendId.isEmpty()) {
                sessionModel.post(SessionEvent.SuspendedEnded.forExecution(host.uuid,
                        executionProfileId(host.uuid, profileKey.profileId),
                        expectedSuspendId));
            }
            if (profileKey.equals(selectedProfileKey(host.uuid))) {
                invalidateSessionProjectionRequest(host.uuid);
            }
        }
        refreshSessionState(host.uuid);
        // The host confirms the closed session free now, not at the next poll cadence.
        refreshSessionProjection(host, profileKey, accepted -> { });
        // The session was already gone: the refreshed state says so, no notice.
        if (outcome == HostSessionTermination.Outcome.NOTHING_TO_CLOSE) return;
        ConsoleUiFeedback.makeText(this,
                outcome == HostSessionTermination.Outcome.CLOSED_GAME_UNVERIFIED
                        ? R.string.console_terminate_session_game_left
                        : outcome.closed()
                        ? R.string.console_terminate_session_success
                        : outcome == HostSessionTermination.Outcome.GAME_UNVERIFIED
                        ? R.string.console_terminate_session_game_unverified
                        : outcome == HostSessionTermination.Outcome.OTHER_STREAM
                        ? R.string.console_terminate_session_other_stream
                        : R.string.console_terminate_session_failed, Toast.LENGTH_LONG).show();
    }

    private boolean isPairingContextCurrent(String hostUuid, String expectedProfileId,
                                            String selectedHostAtStart,
                                            String selectedProfileAtStart,
                                            String profileGateHostAtStart,
                                            int profileGateGenerationAtStart) {
        return Objects.equals(hostUuid, selectedHostUuid)
                && Objects.equals(expectedProfileId, selectedProfileId(hostUuid))
                && Objects.equals(selectedHostAtStart, selectedHostUuid)
                && Objects.equals(selectedProfileAtStart, selectedProfileId(hostUuid))
                && Objects.equals(profileGateHostAtStart, profileGateHostUuid)
                && profileGateGeneration.get() == profileGateGenerationAtStart;
    }

    private void confirmHardTerminateSession(ComputerDetails host) {
        if (host == null || host.state != ComputerDetails.State.ONLINE
                || host.pairState != PairingManager.PairState.PAIRED
                || managerBinder == null) return;
        HostProfileKey profileKey = selectedProfileKey(host.uuid);
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        if (hostGatewayStore.loadForHost(host.uuid, address, profileKey.profileId) == null) return;
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView terminate = consolePanel.action(getString(R.string.console_hard_terminate_session));
        consolePanel.destructive(terminate);
        cancel.setOnClickListener(view -> handlePanelBack());
        terminate.setOnClickListener(view -> {
            hideSidePanel();
            requestHardTerminateSession(host, profileKey);
        });
        showSidePanel(getString(R.string.console_host_eyebrow),
                getString(R.string.console_hard_terminate_session_title),
                getString(R.string.console_hard_terminate_session_details),
                cancel, terminate);
    }

    private void requestHardTerminateSession(ComputerDetails host, HostProfileKey profileKey) {
        if (host == null || profileKey == null
                || !profileKey.equals(selectedProfileKey(host.uuid))) return;
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection gateway = hostGatewayStore.loadForHost(host.uuid, address,
                profileKey.profileId);
        if (gateway == null || managerBinder == null) return;
        if (sessionOrchestrator != null) sessionOrchestrator.cancel();
        cancelOwnedWarmUp(host.uuid, false);
        ChildLaunchBinding childBinding = selectedProfileIsChild(host.uuid)
                ? activeChildBinding(host.uuid, profileKey.profileId) : null;
        String childSessionId = childBinding == null ? "" : childBinding.sessionId;
        ConsoleUiFeedback.makeText(this, R.string.console_hard_terminate_session_request,
                Toast.LENGTH_SHORT).show();
        // This is the recovery escape hatch: discard local ownership immediately and
        // let the host invalidate any durable operation independently.
        clearHardResetSessionState(host, profileKey);
        executor.execute(() -> {
            boolean success = hardResetHost(gateway, childSessionId);
            mainHandler.post(() -> finishHardTerminate(host, profileKey, success));
        });
    }

    private boolean hardResetHost(GatewayConnection gateway, String childSessionId) {
        try {
            hostGatewayClient.hardResetSession(gateway, childSessionId);
            return true;
        } catch (IOException error) {
            LimeLog.warning("Hard host reset failed: " + error.getMessage());
            return false;
        }
    }

    private void finishHardTerminate(ComputerDetails host, HostProfileKey profileKey,
                                     boolean success) {
        if (success) clearHardResetSessionState(host, profileKey);
        ConsoleUiFeedback.makeText(this, getString(success
                        ? R.string.console_hard_terminate_session_success
                        : R.string.console_hard_terminate_session_failed),
                Toast.LENGTH_LONG).show();
    }

    /**
     * The orchestrator's automatic recovery of an uncertain session: the same host hard reset
     * as the explicit one, its local state cleared after it. Runs on the worker.
     */
    private SessionOrchestrator.CloseResult hardResetForRecovery(PlayIntent intent,
                                                                 BooleanSupplier cancelled)
            throws SessionFailure.Raised {
        ComputerDetails host = currentHost(intent.hostId);
        String address = host == null || host.activeAddress == null
                ? null : host.activeAddress.address;
        GatewayConnection gateway = host == null ? null
                : hostGatewayStore.loadForHost(host.uuid, address, intent.profileId);
        if (gateway == null) throw SessionFailure.CLOSE_HOST_UNAVAILABLE.raise();
        if (cancelled.getAsBoolean()) return SessionOrchestrator.CloseResult.CANCELLED;
        ChildLaunchBinding child = activeChildBinding(host.uuid, intent.profileId);
        if (!hardResetHost(gateway, child == null ? "" : child.sessionId)) {
            throw SessionFailure.RECOVERY_HARD_RESET_FAILED.raise();
        }
        // Before the orchestrator's next pass, which is posted after this.
        mainHandler.post(() -> clearHardResetSessionState(host, intent.profileKey));
        return SessionOrchestrator.CloseResult.CLOSED;
    }

    private void clearHardResetSessionState(ComputerDetails host, HostProfileKey profileKey) {
        if (host == null || profileKey == null
                || !profileKey.hostId.equalsIgnoreCase(host.uuid)
                || !profileKey.equals(selectedProfileKey(host.uuid))) return;
        SuspendedSessionStore.Session suspended =
                suspendedSession(host.uuid, profileKey.profileId);
        if (suspended != null) {
            sessionModel.post(SessionEvent.SuspendedEnded.forProfile(
                    host.uuid, suspended.profileId, suspended.suspendId));
        }
        sessionModel.post(new SessionEvent.HostPowerCleared(host.uuid));
        Set<String> streamSessionIds = new LinkedHashSet<>();
        SessionModel.Snapshot retained =
                sessionModel.snapshot();
        if (host.uuid.equalsIgnoreCase(retained.hostId)
                && !retained.streamSessionId.isEmpty()) {
            streamSessionIds.add(retained.streamSessionId);
        }
        // Resets the host's session if the model holds one, and forgets its last poll.
        sessionModel.post(new SessionEvent.HardReset(host.uuid));
        SessionResumeManager.PendingSession pending =
                sessionModel.records().pending;
        if (pending != null && host.uuid.equalsIgnoreCase(pending.hostUuid)) {
            streamSessionIds.add(pending.streamSessionId);
        }
        for (String streamSessionId : streamSessionIds) {
            sessionModel.post(new SessionEvent.ReconnectCleared(streamSessionId));
            BackgroundStreamService.dismiss(this, streamSessionId);
        }
        if (profileKey.equals(selectedProfileKey(host.uuid))) {
            invalidateSessionProjectionRequest(host.uuid);
        }
        if (host.uuid.equalsIgnoreCase(selectedHostUuid)) {
            resumePlayniteGameId = "";
            suspendedPlayniteGameId = "";
        }
        refreshSessionState(host.uuid);
    }

    private HostSessionTermination.Outcome terminateKnownControlPlane(ComputerDetails host,
            String profileId, String providerGameId) {
        return knownControlPlaneTermination(host, profileId, providerGameId).get();
    }

    /**
     * Resolves this console's facts of the close now; the returned close (blocking, off the main
     * thread) needs only app-scoped state, so it may run after this Activity is gone.
     */
    private Supplier<HostSessionTermination.Outcome> knownControlPlaneTermination(
            ComputerDetails host, String profileId, String providerGameId) {
        HostSessionTermination.Outcome failed = HostSessionTermination.Outcome.FAILED;
        if (host == null || managerBinder == null) return () -> failed;
        String profile = GatewayConnection.normalizeProfileId(profileId);
        String executionProfile = executionProfileId(host.uuid, profile);
        HostGatewayClient.IntegrationProfile actor = integrationProfile(host.uuid, profile);
        PlayniteTransitionGateway.ActorContext actorContext = actor != null && actor.isChild()
                ? PlayniteTransitionGateway.ActorContext.child(
                profile, actor.executionProfileId)
                : PlayniteTransitionGateway.ActorContext.parent(profile);
        ComputerDetails.AddressTuple address;
        byte[] certificate;
        try {
            address = ServerHelper.getCurrentAddressFromComputer(host);
            certificate = host.serverCert == null ? null : host.serverCert.getEncoded();
        } catch (Exception error) {
            return () -> failed;
        }
        String hostId = host.uuid;
        int httpsPort = host.httpsPort;
        String uniqueId = managerBinder.getUniqueId();
        String gameId = normalizeId(providerGameId);
        Context app = getApplicationContext();
        SessionModel model = sessionModel;
        return () -> {
            SessionModel.Snapshot retained = model.snapshot();
            SessionResumeManager.PendingSession pending = model.records().pending;
            String sessionId = "";
            int appId = 0;
            long generation = 0L;
            if (hostId.equalsIgnoreCase(retained.hostId)
                    && executionProfile.equals(retained.executionProfileId)
                    && retained.controlGeneration > 0L) {
                sessionId = retained.streamSessionId;
                appId = retained.appId;
                generation = retained.controlGeneration;
            } else if (pending != null && hostId.equalsIgnoreCase(pending.hostUuid)
                    && executionProfile.equals(pending.executionProfileId)
                    && pending.controlGeneration > 0L) {
                sessionId = pending.streamSessionId;
                appId = pending.appId;
                generation = pending.controlGeneration;
            }
            if (sessionId.isEmpty() || appId <= 0 || generation <= 0L) {
                try {
                    GatewayConnection gateway = new HostGatewayStore(app).loadForHost(
                            hostId, address.address, profile);
                    if (gateway == null) return failed;
                    ControlOperationClient.Permit owned;
                    try {
                        owned = new ControlOperationClient(new GatewayTransport())
                                .streamRecord(gateway);
                    } catch (GatewayTransport.GatewayException refused) {
                        // Another TV's or profile's stream: reported, never closed.
                        if (ControlOperationClient.isForeignStream(refused)) {
                            return HostSessionTermination.Outcome.OTHER_STREAM;
                        }
                        throw refused;
                    }
                    // No stream record at all: the session is only its game, which a fresh
                    // host observation must verify before it is stopped.
                    if (owned == null) {
                        return HostSessionTermination.stopStreamlessGame(app, hostId,
                                address.address, actorContext, gameId);
                    }
                    sessionId = owned.streamSessionId;
                    appId = owned.sunshineAppId;
                    generation = owned.generation;
                } catch (IOException error) {
                    LimeLog.warning("Host ownership recovery failed: " + error.getMessage());
                    return failed;
                }
            }
            HostSessionTermination.Outcome stopped = HostSessionTermination.run(app, hostId,
                    address.address, address.port, httpsPort, uniqueId, certificate, sessionId,
                    appId, gameId, generation, actorContext);
            // The stream records end with the stream; callers decide on any game left behind.
            if (stopped.streamClosed()) {
                model.post(new SessionEvent.Cleared(sessionId));
                model.post(new SessionEvent.ReconnectCleared(sessionId));
                BackgroundStreamService.dismiss(app, sessionId);
            }
            return stopped;
        };
    }

    /**
     * Once per host entry (until the next onPause), reads the Gateway's stream record of this
     * TV and the selected profile's execution profile and reports it to the session model with
     * the local reconnect record it was read beside ({@link HostStreamRecord}): the model makes
     * a stream the host kept for this actor a reconnect record (it reads as paused and Play
     * reconnects it), or ends a record of a stream the host no longer holds.
     */
    private void reconcileHostStreamRecord(ComputerDetails host) {
        if (host == null || managerBinder == null || hostSelectionVisible
                || profileGateHostUuid != null || host.activeAddress == null
                || !ConsoleActionCatalog.isOnline(host) || !ConsoleActionCatalog.isPaired(host)) {
            return;
        }
        HostProfileKey key = selectedProfileKey(host.uuid);
        // Cheap read of the model's published state: it would answer KEEP, so no Gateway read.
        if (HostStreamRecord.sessionOwnsRecords(host.uuid, sessionModel.snapshot())) return;
        String local = ownReconnectRecord(host.uuid, key);
        // ponytail: one reconnect record slot; another host's record stays and nothing is
        // adopted beside it.
        if (local == null || !hostStreamReconciled.add(key.cacheKey())) return;
        GatewayConnection gateway = hostGatewayStore.loadForHost(
                host.uuid, host.activeAddress.address, key.profileId);
        if (gateway == null) return;
        String hostId = host.uuid;
        executor.execute(() -> {
            ControlOperationClient.Permit owned;
            try {
                owned = new ControlOperationClient(new GatewayTransport()).ownedStream(gateway);
            } catch (IOException | RuntimeException unknown) {
                return;
            }
            // Adoption material needs the host's app list: its cache is read here, off the UI
            // thread and off the model thread.
            List<NvApp> apps = owned != null && local.isEmpty() ? loadAppList(host)
                    : Collections.emptyList();
            mainHandler.post(() -> reportHostStreamRecord(hostId, key, local, owned, apps));
        });
    }

    /** The reconnect record's stream of this host and profile, "" for none, null for another's. */
    private String ownReconnectRecord(String hostId, HostProfileKey key) {
        SessionResumeManager.PendingSession pending = sessionModel.records().pending;
        if (pending == null) return "";
        return hostId.equalsIgnoreCase(pending.hostUuid) && executionProfileId(
                hostId, key.profileId).equals(pending.executionProfileId)
                ? pending.streamSessionId : null;
    }

    private void reportHostStreamRecord(String hostId, HostProfileKey key, String local,
                                        ControlOperationClient.Permit owned, List<NvApp> apps) {
        ComputerDetails host = currentHost(hostId);
        if (host == null || isDestroyed() || !key.equals(selectedProfileKey(hostId))) return;
        Intent adoption = owned != null && local.isEmpty()
                ? adoptedStreamRecord(host, key, owned, apps) : null;
        sessionModel.dispatch(new SessionEvent.HostStreamRecordObserved(hostId, key.profileId,
                executionProfileId(hostId, key.profileId), local, owned, adoption), action -> {
            // A stream that ended while it was watched tells the user itself (Game).
            if (action == HostStreamRecord.Action.RETIRE) {
                BackgroundStreamService.dismiss(this, local);
            }
        });
    }
    /**
     * Reconnect material for the host's stream {@code owned}: what the stream screen records
     * once connected, with the host's generation, so Play reconnects it like any saved session.
     */
    private Intent adoptedStreamRecord(ComputerDetails host, HostProfileKey key,
                                       ControlOperationClient.Permit owned, List<NvApp> apps) {
        NvApp app = PlayniteTargetResolver.findById(apps, owned.sunshineAppId);
        if (app == null || host.activeAddress == null || managerBinder == null) return null;
        LaunchTransitionSpec transition = LaunchTransitionSpec.create(host.uuid, key.profileId,
                owned.providerGameId.isEmpty() ? LaunchTransitionType.GENERIC
                        : LaunchTransitionType.GAME_CONNECTION,
                app.getAppId(), owned.providerGameId, System.currentTimeMillis());
        HostGatewayClient.IntegrationProfile actor = integrationProfile(host.uuid, key.profileId);
        return ServerHelper.createStartIntent(this, app, host, managerBinder, null)
                .putExtra(Game.EXTRA_CONSOLE_LOADING, true)
                .putExtra(Game.EXTRA_CONSOLE_REDUCED_MOTION, reducedMotion)
                .putExtra(Game.EXTRA_TRANSITION_ID, transition.id)
                .putExtra(Game.EXTRA_TRANSITION_TYPE, transition.type.name())
                .putExtra(Game.EXTRA_TRANSITION_HOST_ID, transition.hostId)
                .putExtra(Game.EXTRA_TRANSITION_PLAYNITE_GAME_ID, transition.playniteGameId)
                .putExtra(Game.EXTRA_TRANSITION_CREATED_AT, transition.createdAtMillis)
                .putExtra(Game.EXTRA_PROFILE_ID, key.profileId)
                .putExtra(Game.EXTRA_EXECUTION_PROFILE_ID,
                        executionProfileId(host.uuid, key.profileId))
                .putExtra(Game.EXTRA_CHILD_PROFILE, actor != null && actor.isChild())
                .putExtra(Game.EXTRA_CAPTURES, captures(host.uuid))
                .putExtra(Game.EXTRA_CONTROL_GENERATION, owned.generation)
                .putExtra(Game.EXTRA_STREAM_TARGET_NAME, app.getAppName())
                .putExtra(Game.EXTRA_NEUTRAL_STREAM_TARGET,
                        PlayniteTargetResolver.isNeutralStream(app));
    }

    private long knownControlGeneration(String hostId, String profileId) {
        String profile = GatewayConnection.normalizeProfileId(profileId);
        String executionProfile = executionProfileId(hostId, profile);
        SessionModel.Snapshot retained =
                sessionModel.snapshot();
        if (normalizeId(hostId).equalsIgnoreCase(retained.hostId)
                && executionProfile.equals(retained.executionProfileId)) {
            return retained.controlGeneration;
        }
        SessionResumeManager.PendingSession pending =
                sessionModel.records().pending;
        return pending != null && normalizeId(hostId).equalsIgnoreCase(pending.hostUuid)
                && executionProfile.equals(pending.executionProfileId)
                ? pending.controlGeneration : 0L;
    }

    private String knownProviderGameId(ComputerDetails host) {
        String gameId = host == null ? "" : resolveSessionSnapshot(host).playniteGameId;
        return HostGatewayClient.isPlayniteId(gameId) ? gameId : "";
    }

    private void stopActiveProviderGame(ComputerDetails host, String knownGameId,
                                        boolean requireProviderVerification,
                                        String profileId)
            throws IOException {
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection gateway = hostGatewayStore.loadForHost(host.uuid, address, profileId);
        String expectedGameId = normalizeId(knownGameId);
        if (gateway == null) {
            if (requireProviderVerification || !expectedGameId.isEmpty()) {
                throw new IOException("Gateway unavailable for provider stop");
            }
            return;
        }
        PlayniteTransitionGateway.Snapshot current;
        try {
            current = new PlayniteTransitionGateway(sessionModel, host.uuid, gateway,
                    gatewayActorContext(host.uuid, profileId), hostGatewayClient,
                    SystemClock::uptimeMillis).snapshot();
        } catch (IOException unavailable) {
            if (!requireProviderVerification && expectedGameId.isEmpty()) return;
            throw unavailable;
        }
        String gameId = PlayniteIdentityResolutionPolicy.verifiedStopTarget(
                expectedGameId, current.gameState, current.gameId);
        if (gameId == null) {
            if (!requireProviderVerification && expectedGameId.isEmpty()) return;
            throw new IOException("Provider game state is uncertain or does not match");
        }
        if (!gameId.isEmpty()) {
            if (!current.hasVerifiedProcessFor(gameId)) {
                throw new IOException("Provider process identity is uncertain");
            }
            hostGatewayClient.stopGame(gateway, gameId, current.processToken);
        }
    }

    private void finishHostWaking(String hostUuid, long token) {
        if (!hostStateController.finishWaking(hostUuid, token)) return;
        renderHostSelection();
        if (hostUuid.equals(selectedHostUuid)) updateHostSelector();
    }

    private void confirmRemoveHost(ComputerDetails host) {
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView remove = consolePanel.action(getString(R.string.console_remove));
        consolePanel.destructive(remove);
        cancel.setOnClickListener(view -> handlePanelBack());
        remove.setOnClickListener(view -> {
            hideSidePanel();
            if (forgetHost(host)) {
                ConsoleUiFeedback.makeText(this, getString(R.string.console_host_removed,
                        host.name), Toast.LENGTH_LONG).show();
            }
        });
        showSidePanel(getString(R.string.console_host_eyebrow),
                getString(R.string.console_remove_host_title, host.name),
                getString(R.string.console_remove_host_details), cancel, remove);
    }

    /**
     * The one local cleanup when this device forgets a host ("Usuń", and "Odparuj hosta" after
     * the host confirms). Never contacts the host: the session is ended locally first, then
     * every record of the host goes, and the console returns to host selection.
     */
    private boolean forgetHost(ComputerDetails host) {
        if (managerBinder == null) {
            ConsoleUiFeedback.makeText(this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
            return false;
        }
        String uuid = host.uuid;
        boolean selected = uuid.equals(selectedHostUuid);
        cancelQueuedPlay();
        if (selected) {
            ProfileAuthorization.revoke();
            cancelWindowsProfileSwitch(false);
            cancelStreamingAutopilot(false);
            cancelPlayniteRequest();
            cancelPlayniteArtworkPrefetch();
            stopAppListPoller();
            invalidateChildManagement();
            selectedHostUuid = null;
        }
        cancelOwnedWarmUp(uuid, false);
        SessionModel.Snapshot retained = sessionModel.snapshot();
        if (uuid.equalsIgnoreCase(retained.hostId) && !retained.streamSessionId.isEmpty()) {
            BackgroundStreamService.dismiss(this, retained.streamSessionId);
        }
        SessionResumeManager.PendingSession pending = sessionModel.records().pending;
        if (pending != null && uuid.equalsIgnoreCase(pending.hostUuid)) {
            BackgroundStreamService.dismiss(this, pending.streamSessionId);
        }
        sessionModel.post(new SessionEvent.HostForgotten(uuid));

        managerBinder.removeComputer(host);
        assetLoader.deleteAssetsForComputer(uuid);
        new ShortcutHelper(this).disableComputerShortcut(host, getString(R.string.scut_deleted_pc));
        for (QuickLaunchManager.QuickLaunchItem item : quickLaunchManager.getAllQuickLaunchItems()) {
            if (uuid.equals(item.computerUuid)) quickLaunchManager.removeQuickLaunchItem(item.key);
        }
        hostGatewayStore.remove(uuid);
        if (firstRunWizard != null) firstRunWizard.forgetHost(uuid);
        playniteLibraryRepository.forgetHost(uuid);
        playniteArtworkCache.forgetHost(uuid);
        profileAvatars.forgetHost(uuid);
        flushPendingPlayniteSelection();
        for (String file : HOST_KEYED_PREFERENCES) {
            LocalHostRecords.sweep(getSharedPreferences(file, MODE_PRIVATE), uuid);
        }
        String id = uuid.toLowerCase(Locale.ROOT);
        for (Collection<String> keys : Arrays.<Collection<String>>asList(
                cardBindBatchMemo.keySet(),
                sessionProjectionRequestedAt.keySet(),
                sessionProjectionRequestGenerations.keySet(),
                childCapabilityByProfile.keySet(), childLaunchBindings.keySet(),
                childEligibilityObservedAt.keySet(), hostProfileRefreshGenerations.keySet())) {
            keys.removeIf(key -> key.toLowerCase(Locale.ROOT).contains(id));
        }
        hosts.remove(uuid);
        newlyDiscoveredHosts.remove(uuid);
        if (uuid.equals(autoLoginHostUuid)) autoLoginHostUuid = "";

        if (selected || !hostSelectionVisible) showHostSelection(null);
        else renderHostSelection();
        return true;
    }

    private void selectHost(ComputerDetails host, boolean focusApps) {
        pendingHostPreparation = null;
        boolean enteringHost = hostSelectionVisible;
        boolean changed = !host.uuid.equals(selectedHostUuid);
        if (changed) {
            childEligibilityLaunchToken++;
            mainHandler.removeCallbacks(childEligibilityBoundary);
            invalidateChildManagement();
            cancelWindowsProfileSwitch(false);
            cancelStreamingAutopilot(false);
            cancelPlayniteArtworkPrefetch();
            cancelOwnedWarmUp(selectedHostUuid, true);
        }
        hostSelectionVisible = false;
        if (consoleAudioEngine != null) {
            consoleAudioEngine.setHostSelectionVisible(false);
        }
        if (enteringHost || focusApps) pendingInitialGameFocus = true;
        if (hostSelectView != null) hostSelectView.setFocusTarget(host.uuid);
        if (entryShell != null) entryShell.setVisibility(View.GONE);
        if (homeLayer != null) homeLayer.setVisibility(View.VISIBLE);
        selectedHostUuid = host.uuid;
        ProfileAuthorization.grant(host.uuid);
        refreshDiscordAccess();
        initialLocalAppsHostId = "";
        initialLocalLibraryHostId = "";
        if (requiresPreparedInitialCarouselFrame()) {
            resetInitialCarouselArtworkWarmup();
            suppressInitialCarouselMotion = true;
            libraryTransitionCoordinator.setReducedMotion(true);
        }
        if (changed) currentSunshineApps = Collections.emptyList();
        restoreHostLibraryState(host.uuid);
        boolean restoredInitialPresentation = requiresPreparedInitialCarouselFrame()
                && restoreInitialLocalPresentationData(host);
        preferences.edit().putString("selected_host", host.uuid).apply();
        newlyDiscoveredHosts.remove(host.uuid);
        clearArtwork();
        appsLabel.setText(getString(R.string.console_apps_host,
                host.name.toUpperCase(Locale.ROOT)));
        updateHostSelector();
        if (requiresPreparedInitialCarouselFrame()) deferInitialHostProfileRefresh = true;
        else refreshHostProfiles(host);
        ConsoleIntroView.afterEntrance(introView, this::refreshDiscordIndicator);
        startAppListPoller(host);
        if (restoredInitialPresentation) settleInitialLocalPresentation(host);
        renderAppsAsync(host, focusApps);
        if (!restoredInitialPresentation && (changed || currentPlayniteGames.isEmpty())) {
            loadPlayniteForHost(host);
        } else initialLocalLibraryHostId = host.uuid; // This host's library is in memory.
    }

    private void cancelOwnedWarmUp(String hostId, boolean relinquishForHostChange) {
        String normalizedHost = normalizeId(hostId);
        if (sessionOrchestrator != null) {
            sessionOrchestrator.cancelPreparation(normalizedHost);
        }
        SessionModel.Snapshot preparing =
                sessionModel.snapshot();
        if (ownsPreparing(homePreparingOperation, normalizedHost, launchedProfileId(),
                preparing)) {
            sessionModel.dispatch(new SessionEvent.CancelPreparingTransport(preparing),
                    cancelled -> {
                // Ordered after the owner's reconnect marker: the model clears exactly it.
                if (cancelled && relinquishForHostChange) {
                    sessionModel.post(new SessionEvent.ReconnectRelinquished(preparing));
                }
            });
        }
        if (warmUpClientHostId.equals(normalizedHost)) relinquishWarmUp();
    }

    /**
     * Only the exact preparing operation this stream Home opened over (with its host and
     * profile) is this console's warm-up; another PREPARING on the host is someone else's.
     */
    static boolean ownsPreparing(long operation, String hostId, String profileId,
                                 SessionModel.Snapshot preparing) {
        return operation > 0L && preparing.state == SessionModel.State.PREPARING
                && preparing.preparingOperation == operation
                && preparing.hostId.equalsIgnoreCase(hostId)
                && preparing.profileId.equals(profileId);
    }

    private void startAppListPoller(ComputerDetails host) {
        stopAppListPoller();
        if (managerBinder == null || !active) return;
        ComputerDetails current = managerBinder.getComputer(host.uuid);
        if (current == null) return;
        appListPoller = managerBinder.createAppListPoller(current);
        appListPoller.start();
    }

    private void stopAppListPoller() {
        if (appListPoller != null) {
            appListPoller.stop();
            appListPoller = null;
        }
    }

    private void renderAppsAsync(ComputerDetails host) {
        renderAppsAsync(host, false);
    }

    private void renderAppsAsync(ComputerDetails host, boolean focusApps) {
        String uuid = host.uuid;
        HostProfileKey requestKey = selectedProfileKey(uuid);
        int generation = appListRenderGeneration.incrementAndGet();
        executor.execute(() -> {
            List<NvApp> apps = visibleApps(uuid, loadAppList(host));
            List<PlayniteLibraryGame> games = currentPlayniteGames;
            // A library read but not yet published (entry from the profile row) is resolved
            // here too, so the render this list leads to does not resolve it.
            if (games.isEmpty()) {
                PlayniteLibraryCache.Entry cached = playniteLibraryRepository.cached(requestKey);
                if (cached != null) games = cached.games;
            }
            prepareResolvedTargets(host, games, apps);
            StringBuilder appsSignature = new StringBuilder();
            for (NvApp app : apps) {
                appsSignature.append(app.getAppId()).append(':').append(app.getAppName())
                        .append(';');
            }
            mainHandler.post(() -> ConsoleIntroView.afterEntrance(introView,
                    () -> runLibraryUpdateWhenNavigationIdle(() -> {
                if (generation < appListRenderFloor) return;
                appListRenderFloor = generation;
                // Under the profile gate nothing renders: entering the host renders afresh.
                if (profileGateHostUuid != null) return;
                if (!uuid.equals(selectedHostUuid)) return;
                if (!requestKey.equals(selectedProfileKey(uuid))) return;
                ComputerDetails latestHost = currentHost(uuid);
                if (latestHost == null) return;
                currentSunshineApps = apps;
                initialLocalAppsHostId = uuid;
                if (requiresPreparedInitialCarouselFrame()) {
                    settleInitialLocalPresentation(latestHost);
                    return;
                }
                boolean playniteAvailable = currentPlayniteProjectionMatches(requestKey)
                        && !currentPlayniteGames.isEmpty() ||
                        hostGatewayStore.loadForHost(latestHost.uuid,
                                latestHost.activeAddress != null ? latestHost.activeAddress.address : null) != null;
                if (playniteAvailable) {
                    // The same app list for the same host state renders the same library.
                    String appsKey = requestKey.cacheKey() + '|'
                            + ConsoleActionCatalog.isOnline(latestHost) + '|' + appsSignature;
                    if (focusApps || !appsKey.equals(renderedSunshineAppsKey)) {
                        renderedSunshineAppsKey = appsKey;
                        // An empty library still loading has nothing to render: its loading
                        // cards stay until the cache or the host answers, and that renders
                        // with this app list.
                        if (playniteInitialLoadPending && currentPlayniteGames.isEmpty()) return;
                        renderPlayniteLibrary(latestHost, apps);
                    }
                } else {
                    renderApps(latestHost, apps);
                }
                if (!playniteAvailable) requestPendingInitialGameFocus(latestHost);
            })));
        });
    }

    private void runLibraryUpdateWhenNavigationIdle(Runnable update) {
        long delay = LIBRARY_UPDATE_NAVIGATION_IDLE_MS
                - (SystemClock.uptimeMillis() - lastDirectionalNavigationAt);
        if (delay > 0L) mainHandler.postDelayed(
                () -> runLibraryUpdateWhenNavigationIdle(update), delay);
        else update.run();
    }

    /**
     * Worker thread: the host's app list, current with its latest observed raw list. A change
     * recomputes the app names and session decisions that read the memo meanwhile (and the
     * selected host's status).
     */
    private List<NvApp> loadAppList(ComputerDetails host) {
        List<NvApp> before = appLists.apps(host.uuid);
        List<NvApp> apps = appLists.load(host.uuid);
        if (apps != before) mainHandler.post(() -> queueConsoleUpdates(
                ConsoleUpdateChannels.HOST_SELECTION | ConsoleUpdateChannels.SESSION
                        | (host.uuid.equals(selectedHostUuid)
                        ? ConsoleUpdateChannels.SELECTED_HOST : ConsoleUpdateChannels.NONE)));
        return apps;
    }

    /** Main thread: records the host's raw list; one the memo has not loaded is loaded. */
    private void warmAppList(ComputerDetails host) {
        if (appLists.observe(host.uuid, host.rawAppList)) {
            diskExecutor.execute(() -> loadAppList(host));
        }
    }

    /** {@code apps} as the library shows them: without the host's hidden apps unless shown. */
    private List<NvApp> visibleApps(String hostUuid, List<NvApp> apps) {
        if (showHiddenApps) return apps;
        return ConsoleAppLists.withoutHidden(apps, getSharedPreferences(
                HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .getStringSet(hostUuid, Collections.emptySet()));
    }

    private boolean restoreInitialLocalPresentationData(ComputerDetails host) {
        InitialLibraryPresentation cached = initialLibraryPresentation;
        if (cached == null || host == null
                || !selectedProfileKey(host.uuid).equals(cached.profileKey)) return false;
        currentPlayniteHostUuid = host.uuid;
        currentPlayniteProfileKey = cached.profileKey;
        currentPlayniteGames = cached.games;
        currentSunshineApps = cached.sunshineApps;
        playniteLibraryError = null;
        playniteInitialLoadPending = false;
        initialLocalAppsHostId = host.uuid;
        initialLocalLibraryHostId = host.uuid;
        deferInitialPlayniteRefresh = true;
        return true;
    }

    private void loadPlayniteForHost(ComputerDetails host) {
        if (host == null || profileGateHostUuid != null) return;
        HostProfileKey requestKey = selectedProfileKey(host.uuid);
        if (requestKey.equals(currentPlayniteProfileKey)
                && host.uuid.equals(currentPlayniteHostUuid)
                && !currentPlayniteGames.isEmpty()) {
            requestPlayniteRefresh(host, false);
            return;
        }
        cancelPlayniteRequest();
        int token = playniteGeneration.incrementAndGet();
        currentPlayniteHostUuid = host.uuid;
        currentPlayniteProfileKey = requestKey;
        currentPlayniteGames = Collections.emptyList();
        renderedPlayniteItems = Collections.emptyList();
        allPlayniteItems = Collections.emptyList();
        unfilteredPlayniteItems = Collections.emptyList();
        playniteLibraryError = null;
        playniteInitialLoadPending = true;
        // Nothing of this library is rendered: the next app list result renders it.
        renderedSunshineAppsKey = "";
        if (!requiresPreparedInitialCarouselFrame()) {
            // The row empties now; under an entry intro its loading cards wait for the
            // entrance, and are not built at all once the load has ended or brought games.
            if (appRow != null) appRow.removeAllViews();
            ConsoleIntroView.afterEntrance(introView, () -> {
                if (playniteInitialLoadPending && currentPlayniteGames.isEmpty()) {
                    showCarouselLoadingGhosts();
                }
            });
        }
        updatePlayniteLibraryStatus(host);
        Locale sortLocale = getResources().getConfiguration().getLocales().get(0);
        playniteRequest = playniteExecutor.submit(() -> {
            PlayniteLibraryCache.Entry cached = readCachedLibrary(host, requestKey, sortLocale);
            mainHandler.post(() -> {
                if (token != playniteGeneration.get() ||
                        !host.uuid.equals(selectedHostUuid)
                        || !requestKey.equals(selectedProfileKey(host.uuid))) return;
                if (cached != null) {
                    currentPlayniteProfileKey = requestKey;
                    currentPlayniteGames = cached.games;
                }
                initialLocalLibraryHostId = host.uuid;
                ComputerDetails latestHost = currentHost(host.uuid);
                if (requiresPreparedInitialCarouselFrame()) {
                    settleInitialLocalPresentation(latestHost);
                    deferInitialPlayniteRefresh = true;
                } else if (cached != null && host.uuid.equals(initialLocalAppsHostId)) {
                    // Without this host's app list yet, its result renders the library: once.
                    renderLibraryAfterIntroEntrance(host.uuid);
                }
                if (!deferInitialPlayniteRefresh) {
                    requestPlayniteRefresh(latestHost, false);
                }
            });
        });
    }

    /** Worker thread: a profile's cached library, its launch targets resolved for the render. */
    private PlayniteLibraryCache.Entry readCachedLibrary(ComputerDetails host, HostProfileKey key,
                                                         Locale sortLocale) {
        PlayniteLibraryCache.Entry cached = playniteLibraryRepository.cached(key);
        // The app list is read here: the main thread may not have published its own yet.
        if (cached != null) {
            prepareResolvedTargets(host, cached.games, visibleApps(host.uuid, loadAppList(host)));
            PlayniteLibraryOrdering.prepareNames(cached.games, sortLocale);
        }
        return cached;
    }

    /**
     * Renders the library as it then is; under a starting entry intro, as one of its steps.
     * The state the render reads is set by the caller at once.
     */
    private void renderLibraryAfterIntroEntrance(String hostUuid) {
        ConsoleIntroView.afterEntrance(introView, () -> renderLibraryOf(hostUuid));
    }

    /** Renders a library home already shows again; under an entry intro, after its end. */
    private void renderLibraryAfterIntro(String hostUuid) {
        ConsoleIntroView.afterIntro(introView, "library", () -> renderLibraryOf(hostUuid));
    }

    private void renderLibraryOf(String hostUuid) {
        ComputerDetails host = currentHost(hostUuid);
        if (host != null) renderPlayniteLibrary(host, currentSunshineApps);
    }

    private void settleInitialLocalPresentation(ComputerDetails host) {
        if (!requiresPreparedInitialCarouselFrame() || host == null
                || !initialLocalPresentationReady(host.uuid,
                initialLocalAppsHostId, initialLocalLibraryHostId)) return;
        if (currentPlayniteGames.isEmpty()) {
            renderApps(host, currentSunshineApps);
            requestPendingInitialGameFocus(host);
        } else if (!restoreInitialLibraryPresentation(host)) {
            renderPlayniteLibrary(host, currentSunshineApps);
        }
    }

    /**
     * Reuses the last rendered library for the first frame of a returning Home. The session is
     * projected from the current model, never from the render before the stream; when that
     * changes the carousel order, the caller renders the library in full instead.
     */
    private boolean restoreInitialLibraryPresentation(ComputerDetails host) {
        InitialLibraryPresentation cached = initialLibraryPresentation;
        if (cached == null || !selectedProfileKey(host.uuid).equals(cached.profileKey)
                || cached.games != currentPlayniteGames) return false;
        SessionSnapshot session = renderedSessionSnapshot(host);
        PlayniteSessionPresentation.Projection projection = PlayniteSessionPresentation.project(
                session, cached.unfilteredItems,
                preferences.getString("selected_playnite." + host.uuid, ""));
        if (!playniteTileSessionSignature(projection, host)
                .equals(cached.sessionSignature)) return false;
        scheduleChildEligibilityBoundary(host);
        allPlayniteItems = cached.allItems;
        unfilteredPlayniteItems = cached.unfilteredItems;
        playniteSessionProjection = projection;
        resumePlayniteGameId = projection.resumeGameId;
        suspendedPlayniteGameId = projection.suspendedGameId;
        renderedSessionPresentationKey = sessionPresentationKey(host, session);
        applyPlayniteDiff(host, currentSunshineApps, cached.carouselItems,
                "", "", false);
        renderedCarouselSessionSignature = cached.sessionSignature;
        syncFullLibraryCard(cached.allItems, cached.carouselItems,
                "playnite:__library__".equals(
                        getCurrentFocus() == null ? null : getCurrentFocus().getTag()));
        refreshChildEligibilityCardPresentation(host);
        renderedAppsSignature = null;
        appsLabel.setText(getString(R.string.playnite_library,
                host.name.toUpperCase(Locale.ROOT)));
        updateHostSelector();
        updatePlayniteLibraryStatus(host);
        requestPendingInitialGameFocus(host);
        return true;
    }

    private void requestPlayniteRefresh(ComputerDetails host, boolean manual) {
        if (!active || profileGateHostUuid != null || host == null
                || !host.uuid.equals(selectedHostUuid)) return;
        HostProfileKey requestKey = selectedProfileKey(host.uuid);
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, address, requestKey.profileId);
        mainHandler.removeCallbacks(playniteRefreshCycle);
        if (connection == null) {
            playniteInitialLoadPending = false;
            playniteLibraryRefreshing = false;
            playniteLibraryError = PlayniteLibraryRepository.ErrorKind.AUTHENTICATION;
            updatePlayniteLibraryStatus(host);
            scheduleNextPlayniteRefresh();
            return;
        }
        cancelPlayniteRequest();
        int token = playniteGeneration.incrementAndGet();
        playniteLibraryRefreshing = true;
        playniteLibraryError = null;
        updatePlayniteLibraryStatus(host);
        Locale sortLocale = getResources().getConfiguration().getLocales().get(0);
        playniteRequest = playniteExecutor.submit(() -> {
            PlayniteLibraryRepository.Result result = playniteLibraryRepository.refresh(
                    requestKey, connection, () -> token != playniteGeneration.get() ||
                            Thread.currentThread().isInterrupted(), manual);
            if (result.entry != null && !result.entry.games.equals(currentPlayniteGames)) {
                prepareResolvedTargets(host, result.entry.games, currentSunshineApps);
                PlayniteLibraryOrdering.prepareNames(result.entry.games, sortLocale);
            }
            mainHandler.post(() -> {
                if (token != playniteGeneration.get() || !active ||
                        !host.uuid.equals(selectedHostUuid)
                        || !requestKey.equals(selectedProfileKey(host.uuid))) return;
                playniteLibraryRefreshing = false;
                boolean initialLoad = playniteInitialLoadPending;
                playniteInitialLoadPending = false;
                ComputerDetails latestHost = currentHost(host.uuid);
                if (latestHost == null) return;
                if (result.entry != null) {
                    boolean libraryChanged = !result.entry.games.equals(currentPlayniteGames);
                    // Games home shows, or renders as an intro step: a change redraws them.
                    boolean refresh = !currentPlayniteGames.isEmpty();
                    currentPlayniteProfileKey = requestKey;
                    // An unchanged library keeps its list, and with it the resolved targets.
                    if (libraryChanged) currentPlayniteGames = result.entry.games;
                    playniteLibraryError = null;
                    reconcilePlayniteInstallations(latestHost, currentPlayniteGames);
                    if (libraryChanged && refresh) renderLibraryAfterIntro(host.uuid);
                    else if (libraryChanged) renderLibraryAfterIntroEntrance(host.uuid);
                    else updatePlayniteLibraryStatus(latestHost);
                } else {
                    playniteLibraryError = result.error;
                    updatePlayniteLibraryStatus(latestHost);
                }
                // An initial load that ends without games (no cache and an error, or an
                // empty library): its loading cards give way to the empty row and message.
                if (initialLoad && currentPlayniteGames.isEmpty()) {
                    renderLibraryAfterIntroEntrance(host.uuid);
                }
                mainHandler.removeCallbacks(playniteRefreshCycle);
                scheduleNextPlayniteRefresh();
            });
        });
    }

    private void scheduleNextPlayniteRefresh() {
        mainHandler.removeCallbacks(playniteRefreshCycle);
        mainHandler.postDelayed(playniteRefreshCycle,
                hasActivePlayniteInstallation()
                        ? PLAYNITE_INSTALL_REFRESH_MS : PLAYNITE_REFRESH_MS);
    }

    private boolean hasActivePlayniteInstallation() {
        return gameOperationsController.hasActiveOperation(
                selectedHostUuid, currentPlayniteGames);
    }

    private boolean hasPlayniteOperationAwaitingConfirmation() {
        return gameOperationsController.hasOperationAwaitingConfirmation(
                selectedHostUuid, currentPlayniteGames);
    }

    private void reconcilePlayniteInstallations(ComputerDetails host,
                                                List<PlayniteLibraryGame> current) {
        if (host == null) return;
        Set<String> completed = new HashSet<>();
        for (GameOperationsController.Observation observation
                : gameOperationsController.reconcile(host.uuid, current)) {
            if (observation.type == GameOperationsController.ObservationType
                    .INSTALL_CONFIRMED_BY_SNAPSHOT) {
                completePlayniteInstallation(host, observation.game, observation.gameId,
                        observation.gameName);
                completed.add(observation.gameId);
            } else if (observation.type == GameOperationsController.ObservationType
                    .INSTALL_NO_LONGER_ACTIVE_AFTER_OBSERVED_ACTIVITY) {
                preferences.edit()
                        .remove(playniteInstallNotificationKey(host.uuid, observation.gameId))
                        .remove(playniteInstallPendingKey(host.uuid, observation.gameId)).apply();
                if (consoleAudioEngine != null) {
                    consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.ERROR);
                }
                ConsoleUiFeedback.makeText(this, getString(R.string.playnite_install_cancelled,
                        observation.gameName), Toast.LENGTH_LONG).show();
            } else if (observation.type == GameOperationsController.ObservationType
                    .UNINSTALL_CONFIRMED_BY_SNAPSHOT) {
                ConsoleUiFeedback.makeText(this, getString(
                        R.string.playnite_uninstall_complete, observation.gameName),
                        Toast.LENGTH_LONG).show();
                updateDiskSpaceAfterUninstall(host, observation.gameId);
                preferences.edit().remove(playniteCarouselInstallActivityKey(
                        host.uuid, observation.gameId)).apply();
            }
        }
        for (PlayniteLibraryGame game : current) {
            String gameId = game.playniteGameId;
            String pendingKey = playniteInstallPendingKey(host.uuid, gameId);
            if (!preferences.contains(pendingKey)) continue;
            if (game.installed && !completed.contains(gameId)) {
                completePlayniteInstallation(host, game, gameId, game.name);
            } else if (!game.installing && !game.installRequiresAttention
                    && game.operationState.trim().isEmpty()) {
                preferences.edit().remove(playniteInstallNotificationKey(host.uuid, gameId))
                        .remove(pendingKey).apply();
            }
        }
    }

    private void completePlayniteInstallation(ComputerDetails host, PlayniteLibraryGame game,
                                               String gameId, String gameName) {
        boolean alreadyShownInStream = preferences.getLong(
                playniteInstallNotificationKey(host.uuid, gameId), 0L) > 0L;
        if (!alreadyShownInStream) {
            if (consoleAudioEngine != null) {
                consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.SUCCESS);
            }
            ConsoleUiFeedback.makeText(this, getString(R.string.playnite_install_complete,
                    gameName), Toast.LENGTH_LONG).show();
        }
        preferences.edit()
                .putLong(playniteCarouselInstallActivityKey(
                        host.uuid, gameId), System.currentTimeMillis())
                .remove(playniteInstallNotificationKey(host.uuid, gameId))
                .remove(playniteInstallPendingKey(host.uuid, gameId)).apply();
        completedPlayniteInstallAnimations.add(playniteInstallKey(host.uuid, gameId));
    }

    private static String playniteInstallNotificationKey(String hostUuid, String gameId) {
        return "playnite_install_notified." + hostUuid + ":" + gameId;
    }

    private static String playniteInstallPendingKey(String hostUuid, String gameId) {
        return "playnite_install_pending." + hostUuid + ":" + gameId;
    }

    private void cancelPlayniteRequest() {
        if (playniteRequest != null) {
            playniteRequest.cancel(false);
            playniteRequest = null;
        }
    }

    private void updatePlayniteLibraryStatus(ComputerDetails host) {
        if (playniteLibraryStatus == null) return;
        String address = host != null && host.activeAddress != null
                ? host.activeAddress.address : null;
        boolean gatewayConfigured = host != null
                && hostGatewayStore.loadForHost(host.uuid, address) != null;
        SessionSnapshot session = host == null ? null : renderedSessionSnapshot(host);
        ConsoleLibraryStatus.State libraryStatus = ConsoleLibraryStatus.resolve(
                playniteLibraryRefreshing, !currentPlayniteGames.isEmpty(),
                playniteLibraryError, gatewayConfigured,
                session != null && knowsHostSleep(host, session));
        int textId;
        switch (libraryStatus) {
            case REFRESHING:
                textId = R.string.playnite_refreshing_library;
                break;
            case GATEWAY_NOT_CONFIGURED:
                textId = R.string.playnite_gateway_not_configured;
                break;
            case AUTHENTICATION_ERROR:
                textId = R.string.playnite_authentication_error;
                break;
            case TIMEOUT:
                textId = R.string.playnite_timeout;
                break;
            case INVALID_RESPONSE:
                textId = R.string.playnite_invalid_response;
                break;
            case SERVER_ERROR:
                textId = R.string.playnite_server_error;
                break;
            case GATEWAY_UNAVAILABLE:
                textId = R.string.playnite_gateway_unavailable;
                break;
            case HOST_ASLEEP:
            case CURRENT:
            default:
                textId = 0;
                break;
        }
        boolean showStatus = !playniteInitialLoadPending
                && ConsoleLibraryStatus.visible(libraryStatus);
        // The sleep in the host status line's own words.
        CharSequence status = !showStatus ? ""
                : libraryStatus == ConsoleLibraryStatus.State.HOST_ASLEEP
                ? host.name + " · " + hostStatus(host, session, false) : getText(textId);
        updateLibraryActionVisibility();
        int color = ConsoleLibraryStatus.isError(libraryStatus) ? 0xFFFFB74D : 0xFFC8D2DC;
        // Every refresh and host poll renders this line: views change only with what it says.
        String shown = showStatus + "|" + color + "|" + status;
        if (shown.equals(shownLibraryStatus)) return;
        shownLibraryStatus = shown;
        playniteLibraryStatus.setText(status);
        playniteLibraryStatus.setVisibility(showStatus ? View.VISIBLE : View.INVISIBLE);
        playniteLibraryStatus.setTextColor(color);
        if (expandedCacheStatus != null) {
            expandedCacheStatus.setText(status);
            expandedCacheStatus.setTextColor(color);
            expandedCacheStatus.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * This client put the host to sleep (with or without a game suspended) and no fresh poll
     * has found it awake: its silence is the sleep, not a failure.
     */
    private boolean knowsHostSleep(ComputerDetails host, SessionSnapshot snapshot) {
        if (showsHostSleep(snapshot)) return true;
        return snapshot.isSuspended() && (host.state != ComputerDetails.State.ONLINE
                || !snapshot.suspensionSleepObserved);
    }

    private void updateLibraryActionVisibility() {
        if (debugLibraryActions == null) return;
        boolean resumeVisible = quickResumeButton != null
                && quickResumeButton.getVisibility() == View.VISIBLE;
        debugLibraryActions.setVisibility(resumeVisible ? View.VISIBLE : View.GONE);
    }

    private void renderExpandedLibraryFromStart(ComputerDetails host) {
        renderExpandedLibrary(host);
        if (expandedLibraryMode && expandedGrid != null) expandedGrid.focusPosition(0);
    }

    private void renderFilteredPlayniteLibraryFromStart(ComputerDetails host) {
        renderPlayniteLibrary(host, currentSunshineApps);
        if (expandedLibraryMode && expandedGrid != null) expandedGrid.focusPosition(0);
    }

    private void renderPlayniteLibrary(ComputerDetails host, List<NvApp> apps) {
        setCardBindBatch(true);
        try {
            renderPlayniteLibraryInBatch(host, apps);
        } finally {
            setCardBindBatch(false);
        }
    }

    private void renderPlayniteLibraryInBatch(ComputerDetails host, List<NvApp> apps) {
        if (host == null || !host.uuid.equals(selectedHostUuid)) return;
        HostProfileKey requestedKey = selectedProfileKey(host.uuid);
        if (!currentPlayniteProjectionMatches(requestedKey)) {
            currentPlayniteGames = Collections.emptyList();
            currentPlayniteProfileKey = null;
            currentPlayniteHostUuid = null;
            renderedPlayniteItems = Collections.emptyList();
            allPlayniteItems = Collections.emptyList();
            unfilteredPlayniteItems = Collections.emptyList();
            renderApps(host, apps);
            return;
        }
        scheduleChildEligibilityBoundary(host);
        String previousResumeGameId = resumePlayniteGameId;
        String previousSuspendedGameId = suspendedPlayniteGameId;
        String previousSessionSignature = renderedCarouselSessionSignature;
        Set<String> locallyHidden = locallyHiddenPlayniteGames(host.uuid);
        List<PlayniteLibraryGame> visibleLibraryGames = new ArrayList<>();
        for (PlayniteLibraryGame game : currentPlayniteGames) {
            if (showHiddenApps || (!game.hidden
                    && !locallyHidden.contains(game.playniteGameId))) {
                visibleLibraryGames.add(game);
            }
        }
        ChildLibraryFilter.Selection childFilter = childLibraryFilter.selection(
                childLibraryKey(host.uuid));
        // "For child" shows what the child's TV lists, even with hidden games shown here.
        List<PlayniteLibraryGame> sourceFiltered = PlayniteLibrarySources.filter(
                childFilter == null ? visibleLibraryGames : childFilter.filter(
                        ChildLibraryFilter.listed(currentPlayniteGames, locallyHidden)),
                hostGatewayStore.playniteLibrarySources(host.uuid));
        // A host always lists MoonWaker Stream: an empty list is "not fetched yet" (just paired).
        boolean appListAuthoritative = ConsoleActionCatalog.isOnline(host) && !apps.isEmpty();
        Map<String, PlayniteDashboardItem> resolvedById = new LinkedHashMap<>();
        Map<String, Long> localActivity = new LinkedHashMap<>();
        List<PlayniteDashboardItem> unfilteredItems = new ArrayList<>();
        ResolvedTargets targets = resolvedTargets;
        if (targets == null || !targets.matches(host.uuid, apps, appListAuthoritative)) {
            targets = new ResolvedTargets(host.uuid, apps, appListAuthoritative);
            // An empty library resolves nothing: what a worker prepared for the next render stays.
            if (!visibleLibraryGames.isEmpty()) resolvedTargets = targets;
        }
        for (PlayniteLibraryGame game : visibleLibraryGames) {
            PlayniteDashboardItem item = resolvePlayniteTarget(targets, game);
            unfilteredItems.add(item);
            resolvedById.put(item.stableId(), item);
            localActivity.put(item.stableId(), playActivityEpoch(host.uuid, item));
        }
        SessionSnapshot sessionSnapshot = renderedSessionSnapshot(host);
        playniteSessionProjection = PlayniteSessionPresentation.project(
                sessionSnapshot, unfilteredItems, preferences.getString(
                        "selected_playnite." + host.uuid, ""));
        resumePlayniteGameId = playniteSessionProjection.resumeGameId;
        suspendedPlayniteGameId = playniteSessionProjection.suspendedGameId;
        renderedSessionPresentationKey = sessionPresentationKey(host, sessionSnapshot);
        Set<String> pinned = PinnedGames.ids(preferences, host.uuid, selectedProfileId(host.uuid));
        PlayniteLibraryFilter libraryFilter = hostGatewayStore.playniteLibraryFilter(host.uuid);
        if (libraryFilter == PlayniteLibraryFilter.PINNED
                && !PinnedGames.pinsEnabled(preferences)) {
            libraryFilter = PlayniteLibraryFilter.ALL;
        }
        List<PlayniteLibraryGame> ordered = PlayniteLibraryOrdering.order(
                sourceFiltered, libraryFilter,
                getResources().getConfiguration().getLocales().get(0), localActivity, pinned);
        // The game operations are read once for the whole render.
        Set<String> installing = new HashSet<>();
        for (PlayniteLibraryGame game : visibleLibraryGames) {
            if (isPlayniteInstalling(host.uuid, game)) installing.add(game.playniteGameId);
        }
        ordered = promoteInstallingGames(installing, ordered, sourceFiltered);
        List<PlayniteDashboardItem> items = new ArrayList<>();
        for (PlayniteLibraryGame game : ordered) {
            PlayniteDashboardItem item = resolvedById.get(game.playniteGameId);
            if (item != null) items.add(item);
        }
        if (CONSOLE_UI_V2) {
            items = putInstallingItemsFirst(installing, items);
            items = putSelectedSessionFirst(items);
        } else {
            resumePlayniteGameId = "";
            suspendedPlayniteGameId = "";
        }
        allPlayniteItems = Collections.unmodifiableList(new ArrayList<>(items));
        unfilteredPlayniteItems = Collections.unmodifiableList(
                new ArrayList<>(unfilteredItems));
        List<PlayniteDashboardItem> dashboardItems = carouselPlayniteItems(
                host, unfilteredItems, maxCarouselGameCount(), installing);
        String sessionSignature = playniteTileSessionSignature(playniteSessionProjection, host);
        boolean libraryTileFocused = getCurrentFocus() != null
                && "playnite:__library__".equals(getCurrentFocus().getTag());
        applyPlayniteDiff(host, apps, dashboardItems, previousResumeGameId,
                previousSuspendedGameId,
                !previousSessionSignature.equals(sessionSignature));
        renderedCarouselSessionSignature = sessionSignature;
        syncFullLibraryCard(allPlayniteItems, dashboardItems, libraryTileFocused);
        refreshChildEligibilityCardPresentation(host);
        if (expandedLibraryMode) renderExpandedLibrary(host);
        else {
            List<PlayniteDashboardItem> gridPreview = expandedLibraryItems(host);
            int previewCount = Math.min(gridPreview.size(),
                    expandedGridColumns() * EXPANDED_PREVIEW_ROWS);
            List<PlayniteDashboardItem> warmItems = mergeArtworkWarmup(
                    dashboardItems, gridPreview.subList(0, previewCount));
            schedulePlayniteArtworkPrefetch(host, warmItems,
                    stableIds(dashboardItems));
        }
        renderedAppsSignature = null;
        appsLabel.setText(getString(R.string.playnite_library,
                host.name.toUpperCase(Locale.ROOT)));
        updateHostSelector();
        updatePlayniteLibraryStatus(host);
        initialLibraryPresentation = new InitialLibraryPresentation(
                requestedKey, currentPlayniteGames, currentSunshineApps, allPlayniteItems,
                unfilteredPlayniteItems, renderedPlayniteItems,
                renderedCarouselSessionSignature);
        if (pendingExpandedLibraryRestore && CONSOLE_UI_V2
                && !unfilteredItems.isEmpty()) {
            if (!requiresPreparedInitialCarouselFrame()) {
                pendingExpandedLibraryRestore = false;
                appRow.post(() -> enterExpandedLibrary(true));
            }
        } else {
            requestPendingInitialGameFocus(host);
        }
    }

    private void requestPendingInitialGameFocus(ComputerDetails host) {
        if (!pendingInitialGameFocus || hostSelectionVisible || host == null || appRow == null
                || !host.uuid.equals(selectedHostUuid)) return;
        // The first game's card is still to be built: its build asks again.
        if (appRow.getChildCount() > 0 && pendingCarouselGameId(appRow.getChildAt(0)) != null) {
            return;
        }
        View target = firstFocusableChild(appRow);
        if (target == null) return;
        pendingInitialGameFocus = false;
        View resolved = target;
        resolved.post(resolved::requestFocus);
    }

    private void refreshChildEligibilityCardPresentation(ComputerDetails host) {
        if (host == null || !host.uuid.equals(selectedHostUuid)) return;
        setCardBindBatch(true);
        try {
            refreshChildEligibilityCards(appRow, host);
        } finally {
            setCardBindBatch(false);
        }
        ChildEligibilityProjection policy = childEligibilityProjection(
                host.uuid, selectedProfileId(host.uuid));
        String policyKey = host.uuid + '|' + policy.blocked + '|' + policy.reason;
        if (expandedGrid != null && !policyKey.equals(gridChildPolicyKey)) {
            gridChildPolicyKey = policyKey;
            expandedGrid.rebindAll(0);
        }
    }

    private void refreshChildEligibilityCards(ViewGroup parent, ComputerDetails host) {
        if (parent == null) return;
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            Object tag = child.getTag();
            if (tag instanceof String && ((String) tag).startsWith("playnite:")) {
                String stableId = ((String) tag).substring("playnite:".length());
                if (!"__library__".equals(stableId)) {
                    PlayniteDashboardItem item = findPlayniteDashboardItem(stableId);
                    if (item != null) {
                        bindPlayniteCard(child, host, item, currentSunshineApps, 0);
                    }
                }
            }
            if (child instanceof ViewGroup) {
                refreshChildEligibilityCards((ViewGroup) child, host);
            }
        }
    }

    private PlayniteDashboardItem findPlayniteDashboardItem(String stableId) {
        for (PlayniteDashboardItem item : allPlayniteItems) {
            if (item != null && item.stableId().equals(stableId)) return item;
        }
        for (PlayniteDashboardItem item : unfilteredPlayniteItems) {
            if (item != null && item.stableId().equals(stableId)) return item;
        }
        return null;
    }

    private int maxCarouselGameCount() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        int available = Math.max(1, (int) widthDp - 108);
        int pitch = CAROUSEL_CARD_WIDTH_DP + CAROUSEL_CARD_GAP_DP;
        int focusedGrowth = CAROUSEL_FOCUSED_CARD_WIDTH_DP - CAROUSEL_CARD_WIDTH_DP;
        int slots = Math.max(2, (available - focusedGrowth) / pitch);
        return Math.max(1, slots + 3);
    }

    private List<PlayniteDashboardItem> carouselPlayniteItems(ComputerDetails host,
            List<PlayniteDashboardItem> items, int limit, Set<String> installingIds) {
        if (host == null || items.isEmpty() || limit <= 0) return Collections.emptyList();
        for (PlayniteDashboardItem item : items) {
            if (!installingIds.contains(item.game.playniteGameId)) continue;
            String activityKey = playniteCarouselInstallActivityKey(host.uuid, item.stableId());
            if (preferences.getLong(activityKey, 0L) == 0L) {
                preferences.edit().putLong(activityKey, System.currentTimeMillis()).apply();
            }
        }
        return PlayniteLibraryOrdering.carousel(items, limit, selectedSessionGameId(),
                installingIds, PinnedGames.ids(preferences, host.uuid,
                        selectedProfileId(host.uuid)), item -> carouselActivityEpoch(host.uuid, item),
                getResources().getConfiguration().getLocales().get(0));
    }

    private long carouselActivityEpoch(String hostUuid, PlayniteDashboardItem item) {
        long playActivity = playActivityEpoch(hostUuid, item);
        long installActivity = preferences.getLong(
                playniteCarouselInstallActivityKey(hostUuid, item.stableId()), 0L);
        return Math.max(playActivity, installActivity);
    }

    /**
     * Launch targets resolved against one Sunshine app list. A game's result is reused while
     * the app list and the host's authority over it are unchanged. The main thread owns a
     * published instance; background threads only publish fresh ones.
     */
    private static final class ResolvedTargets {
        final String hostUuid;
        final List<NvApp> apps;
        final boolean authoritative;
        /** Read by a worker preparing the next instance while the main thread fills this one. */
        final Map<PlayniteLibraryGame, PlayniteDashboardItem> items =
                Collections.synchronizedMap(new IdentityHashMap<>());

        ResolvedTargets(String hostUuid, List<NvApp> apps, boolean authoritative) {
            this.hostUuid = hostUuid;
            this.apps = apps;
            this.authoritative = authoritative;
        }

        boolean matches(String hostUuid, List<NvApp> apps, boolean authoritative) {
            return this.hostUuid.equals(hostUuid) && this.authoritative == authoritative
                    && (this.apps == apps || sameApps(this.apps, apps));
        }

        /** Two reads of one app list resolve alike: these are the fields a target reads. */
        private static boolean sameApps(List<NvApp> first, List<NvApp> second) {
            if (first.size() != second.size()) return false;
            for (int index = 0; index < first.size(); index++) {
                NvApp one = first.get(index);
                NvApp other = second.get(index);
                if (one.getAppId() != other.getAppId()
                        || !Objects.equals(one.getAppName(), other.getAppName())
                        || !Objects.equals(one.getAppUuid(), other.getAppUuid())
                        || !Objects.equals(one.getArtVersion(), other.getArtVersion())) {
                    return false;
                }
            }
            return true;
        }
    }

    private PlayniteDashboardItem resolvePlayniteTarget(ResolvedTargets targets,
                                                        PlayniteLibraryGame game) {
        PlayniteDashboardItem item = targets.items.get(game);
        if (item != null) return item;
        item = PlayniteTargetResolver.resolve(game, targets.apps, targets.authoritative);
        targets.items.put(game, item);
        return item;
    }

    /** Background thread: resolves the library before the main-thread render needs it. */
    private void prepareResolvedTargets(ComputerDetails host, List<PlayniteLibraryGame> games,
                                        List<NvApp> apps) {
        if (host == null || games.isEmpty()) return;
        ResolvedTargets targets = new ResolvedTargets(host.uuid, apps,
                ConsoleActionCatalog.isOnline(host));
        // What is already resolved against the same app list (on the PIN screen, say) is kept.
        ResolvedTargets known = resolvedTargets;
        if (known != null && known.matches(host.uuid, apps, targets.authoritative)) {
            for (PlayniteLibraryGame game : games) {
                PlayniteDashboardItem item = known.items.get(game);
                if (item != null) targets.items.put(game, item);
            }
        }
        for (PlayniteLibraryGame game : games) resolvePlayniteTarget(targets, game);
        resolvedTargets = targets;
    }

    private long playActivityEpoch(String hostUuid, PlayniteDashboardItem item) {
        long playActivity = item.game.activityEpoch;
        long localLaunchActivity = preferences.getLong(
                PlayHistory.gameKey(hostUuid, item.stableId()), 0L);
        return Math.max(playActivity, localLaunchActivity);
    }

    private static String playniteCarouselInstallActivityKey(String hostUuid, String gameId) {
        return "playnite_carousel_install_activity." + hostUuid + ':' + gameId;
    }

    private void addFullLibraryCard() {
        View existing = directChildWithTag(appRow, "playnite:__library__");
        // Kept across renders, and never detached (that would drop its focus to the host
        // pill): any card after it moves in front of it.
        if (existing != null) {
            int index;
            while ((index = appRow.indexOfChild(existing)) < appRow.getChildCount() - 1) {
                View after = appRow.getChildAt(index + 1);
                appRow.removeViewAt(index + 1);
                appRow.addView(after, index);
            }
            return;
        }
        LinearLayout card = cardBase(dp(CAROUSEL_CARD_WIDTH_DP),
                dp(CAROUSEL_CARD_HEIGHT_DP));
        card.setTag("playnite:__library__");
        card.setForeground(carouselCardForeground());
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(18), dp(14), dp(18));
        card.setContentDescription(getString(R.string.playnite_open_full_library));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_console_library_grid);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.setOnFocusChangeListener((view, focused) -> {
            styleCard(card, focused);
            if (focused) {
                libraryTransitionCoordinator.beginTransition("__library__");
                positionCarouselMetadata(card);
                showLibraryMetadataPlaceholder();
            } else {
                hideCarouselMetadataOutsideCarousel();
            }
        });
        card.setOnClickListener(view -> enterExpandedLibrary());
        appRow.addView(card, playniteCardSpacing());
        wireHomeFocusNavigation();
    }

    static boolean hasGamesOutsideCarousel(List<PlayniteDashboardItem> libraryItems,
                                            List<PlayniteDashboardItem> carouselItems) {
        if (libraryItems == null || libraryItems.isEmpty()) return false;
        Set<String> carouselIds = new HashSet<>();
        if (carouselItems != null) {
            for (PlayniteDashboardItem item : carouselItems) {
                if (item != null) carouselIds.add(item.stableId());
            }
        }
        for (PlayniteDashboardItem item : libraryItems) {
            if (item != null && !carouselIds.contains(item.stableId())) return true;
        }
        return false;
    }

    private void syncFullLibraryCard(List<PlayniteDashboardItem> libraryItems,
                                     List<PlayniteDashboardItem> carouselItems,
                                     boolean restoreFocus) {
        boolean shouldShow = CONSOLE_UI_V2 && !portraitLayout
                && hasGamesOutsideCarousel(libraryItems, carouselItems);
        if (shouldShow) {
            addFullLibraryCard();
            return;
        }
        View existing = directChildWithTag(appRow, "playnite:__library__");
        if (existing != null) appRow.removeView(existing);
        if (restoreFocus) {
            View target = carouselFocusTarget();
            if (target != null) target.post(target::requestFocus);
        }
        if (existing != null) wireHomeFocusNavigation();
    }

    private void enterExpandedLibrary() {
        enterExpandedLibrary(false);
    }

    private void enterExpandedLibrary(boolean restoreSavedGridPosition) {
        if (!CONSOLE_UI_V2 || expandedLibrary == null || unfilteredPlayniteItems.isEmpty()
                || libraryTransitionRunning) return;
        expandedLibraryMode = true;
        pendingInitialGameFocus = false;
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host != null) expandedLibraryItems(host);
        libraryTransitionGameId = restoreSavedGridPosition && host != null
                ? libraryViewStateStore.load(host.uuid, "").expandedGameId
                : lastCarouselGameId;
        if (libraryTransitionGameId.isEmpty() && host != null) {
            libraryTransitionGameId = preferences.getString(
                    "selected_playnite." + host.uuid, "");
        }
        if (host != null) {
            libraryViewStateStore.saveExpandedMode(host.uuid, true);
        }
        if (host != null) {
            renderExpandedLibrary(host);
            expandedGrid.showPosition(Math.max(0,
                    expandedLibraryQuery.indexOf(libraryTransitionGameId)));
        }
        if (reducedMotion || suppressInitialCarouselMotion) {
            setNormalLibraryVisibility(View.GONE);
            expandedLibrary.setAlpha(1f);
            expandedLibrary.setTranslationX(0f);
            expandedLibrary.setVisibility(View.VISIBLE);
            focusExpandedLibrary();
            return;
        }
        libraryTransitionRunning = true;
        libraryTransitionCoordinator.beginTransition(libraryTransitionGameId);
        expandedLibrary.setAlpha(0f);
        expandedLibrary.setTranslationX(dp(18));
        expandedLibrary.setVisibility(View.VISIBLE);
        expandedLibrary.animate().alpha(1f).translationX(0f)
                .setDuration(LIBRARY_ENTER_TRANSITION_MS)
                .withEndAction(() -> {
                    setNormalLibraryVisibility(View.GONE);
                    resetNormalLibraryTransforms();
                    libraryTransitionRunning = false;
                    focusExpandedLibrary();
                }).start();
    }

    private void exitExpandedLibrary() {
        if (!expandedLibraryMode || libraryTransitionRunning) return;
        View gridFocus = getCurrentFocus();
        if (gridFocus != null && expandedGrid != null
                && isDescendant(expandedGrid, gridFocus)
                && gridFocus.getTag() instanceof String
                && ((String) gridFocus.getTag()).startsWith("playnite:")) {
            String focusedGameId = ((String) gridFocus.getTag())
                    .substring("playnite:".length());
            if (!focusedGameId.isEmpty()) {
                libraryTransitionGameId = focusedGameId;
                lastCarouselGameId = focusedGameId;
                if (selectedHostUuid != null) {
                    libraryViewStateStore.saveCarouselGame(
                            selectedHostUuid, focusedGameId);
                }
            }
        }
        expandedLibraryMode = false;
        librarySearch.close();
        expandedNavigationLegend.setVisibility(View.VISIBLE);
        if (selectedHostUuid != null) {
            libraryViewStateStore.saveExpandedMode(selectedHostUuid, false);
        }
        stopExpandedDescriptionAutoScroll();
        if (reducedMotion) {
            expandedLibrary.setVisibility(View.GONE);
            releaseExpandedGrid();
            revealNormalLibraryAfterTransition();
            return;
        }
        libraryTransitionRunning = true;
        setNormalLibraryVisibility(View.VISIBLE);
        prepareCarouselReturnPosition();
        libraryTransitionCoordinator.beginTransition(libraryTransitionGameId);
        resetNormalLibraryTransforms();
        expandedLibrary.animate().alpha(0f).translationX(dp(28))
                .setDuration(LIBRARY_EXIT_TRANSITION_MS)
                .withEndAction(() -> {
                    expandedLibrary.setVisibility(View.GONE);
                    expandedLibrary.setAlpha(1f);
                    expandedLibrary.setTranslationX(0f);
                    expandedLibrary.setScaleX(1f);
                    expandedLibrary.setScaleY(1f);
                    releaseExpandedGrid();
                    libraryTransitionRunning = false;
                    revealNormalLibraryAfterTransition();
                }).start();
    }

    private void setNormalLibraryVisibility(int visibility) {
        topBar.setVisibility(visibility);
        View carousel = carouselStage != null ? carouselStage
                : appScroll != null ? appScroll : appVerticalScroll;
        if (carousel != null) carousel.setVisibility(visibility);
        if (visibility == View.VISIBLE) updateLibraryActionVisibility();
        else debugLibraryActions.setVisibility(visibility);
        selectedGameMetadata.setVisibility(visibility);
        debugLibrarySpacer.setVisibility(visibility);
        controllerScroll.setVisibility(visibility);
    }

    private List<View> normalLibraryViews() {
        List<View> views = new ArrayList<>();
        views.add(topBar);
        View carousel = carouselStage != null ? carouselStage
                : appScroll != null ? appScroll : appVerticalScroll;
        if (carousel != null) views.add(carousel);
        views.add(debugLibraryActions);
        views.add(selectedGameMetadata);
        views.add(debugLibrarySpacer);
        views.add(controllerScroll);
        return views;
    }

    private void resetNormalLibraryTransforms() {
        for (View view : normalLibraryViews()) {
            view.animate().cancel();
            view.animate().setStartDelay(0L);
            view.setAlpha(1f);
            view.setTranslationX(0f);
        }
    }

    private void revealNormalLibraryAfterTransition() {
        setNormalLibraryVisibility(View.VISIBLE);
        resetNormalLibraryTransforms();
        View target = libraryTransitionGameId.isEmpty() ? null
                : carouselCard(libraryTransitionGameId);
        if (target == null) target = directChildWithTag(appRow, "playnite:__library__");
        if (target == null) target = firstFocusableChild(appRow);
        if (target != null) target.post(target::requestFocus);
        wireHomeFocusNavigation();
    }

    private void prepareCarouselReturnPosition() {
        if (appScroll == null || appRow == null || libraryTransitionGameId.isEmpty()) return;
        View target = carouselCard(libraryTransitionGameId);
        if (target == null) return;
        int viewport = appScroll.getWidth();
        int desired = Math.max(0, target.getLeft() - Math.max(0,
                (viewport - target.getWidth()) / 2));
        appScroll.scrollTo(desired, 0);
    }

    private void createCarouselToGridGhosts() {
        removeLibraryTransitionGhost();
        if (reducedMotion || appRow == null) return;
        for (int index = 0; index < appRow.getChildCount(); index++) {
            View card = appRow.getChildAt(index);
            String gameId = playniteCardGameId(card);
            if (gameId == null) continue;
            addLibraryTransitionGhost(gameId, card, null, null, true);
        }
    }

    private void createGridToCarouselGhosts() {
        removeLibraryTransitionGhost();
        if (reducedMotion || appRow == null || expandedGrid == null) return;
        for (int index = 0; index < appRow.getChildCount(); index++) {
            View destination = appRow.getChildAt(index);
            String gameId = playniteCardGameId(destination);
            if (gameId == null) continue;
            View source = directChildWithTag(expandedGrid, "playnite:" + gameId);
            if (source != null && source.isLaidOut()) {
                addLibraryTransitionGhost(gameId, source, null, null, true);
                continue;
            }
            View poster = findPlaynitePoster(destination);
            if (poster == null || !poster.isLaidOut()) continue;
            int[] rootLocation = new int[2];
            int[] destinationLocation = new int[2];
            root.getLocationInWindow(rootLocation);
            poster.getLocationInWindow(destinationLocation);
            boolean above = expandedGameIndex(gameId) < expandedGameIndex(
                    libraryTransitionGameId);
            float startX = destinationLocation[0] - rootLocation[0];
            float startY = above ? -poster.getHeight() - dp(12)
                    : root.getHeight() + dp(12);
            addLibraryTransitionGhost(gameId, destination, startX, startY, false);
        }
    }

    private void addLibraryTransitionGhost(String gameId, View card,
                                           Float startX, Float startY,
                                           boolean hideSource) {
        if (card == null || !card.isLaidOut()) return;
        View posterView = card instanceof ViewGroup
                ? findTaggedChild((ViewGroup) card, "playnite.poster") : null;
        if (!(posterView instanceof ImageView)) return;
        Drawable drawable = ((ImageView) posterView).getDrawable();
        if (drawable == null) return;
        int[] rootLocation = new int[2];
        int[] cardLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        posterView.getLocationInWindow(cardLocation);
        ImageView ghost = new ImageView(this);
        ghost.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ghost.setImageDrawable(cloneDrawable(drawable));
        ghost.setBackground(gradient(0xFF172128, 0xFF0D151A, 12));
        ghost.setClipToOutline(true);
        ghost.setElevation(dp(20));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                posterView.getWidth(), posterView.getHeight());
        params.leftMargin = Math.round(startX != null
                ? startX : cardLocation[0] - rootLocation[0]);
        params.topMargin = Math.round(startY != null
                ? startY : cardLocation[1] - rootLocation[1]);
        root.addView(ghost, params);
        LibraryTransitionGhost transitionGhost = new LibraryTransitionGhost(
                gameId, ghost, hideSource ? posterView : null);
        libraryTransitionGhosts.add(transitionGhost);
        if (hideSource) posterView.setAlpha(0f);
    }

    private void animateLibraryTransitionGhosts(int token, ViewGroup targetContainer,
                                                boolean enteringGrid) {
        if (!libraryTransitionCoordinator.isCurrent(token, libraryTransitionGameId)) {
            removeLibraryTransitionGhost();
            return;
        }
        List<LibraryTransitionGhost> ghosts = new ArrayList<>(libraryTransitionGhosts);
        for (int index = 0; index < ghosts.size(); index++) {
            LibraryTransitionGhost transitionGhost = ghosts.get(index);
            View target = directChildWithTag(targetContainer,
                    "playnite:" + transitionGhost.gameId);
            View targetPoster = findPlaynitePoster(target);
            if (targetPoster != null && targetPoster.isLaidOut()) {
                animateLibraryTransitionGhostToTarget(
                        transitionGhost, targetPoster, index);
            } else if (enteringGrid) {
                animateLibraryTransitionGhostToEdge(transitionGhost, index);
            } else {
                removeLibraryTransitionGhost(transitionGhost);
            }
        }
    }

    private void animateLibraryTransitionGhostToTarget(
            LibraryTransitionGhost transitionGhost, View targetPoster, int order) {
        ImageView ghost = transitionGhost.view;
        transitionGhost.target = targetPoster;
        targetPoster.setAlpha(0f);
        int[] rootLocation = new int[2];
        int[] targetLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        targetPoster.getLocationInWindow(targetLocation);
        float x = targetLocation[0] - rootLocation[0];
        float y = targetLocation[1] - rootLocation[1];
        float scaleX = targetPoster.getWidth() / (float) Math.max(1, ghost.getWidth());
        float scaleY = targetPoster.getHeight() / (float) Math.max(1, ghost.getHeight());
        ghost.animate().x(x).y(y).scaleX(scaleX).scaleY(scaleY).alpha(.18f)
                .setStartDelay(Math.min(70L, order * 9L))
                .setDuration(LIBRARY_SHARED_ARTWORK_MS)
                .withEndAction(() -> removeLibraryTransitionGhost(transitionGhost)).start();
    }

    private void animateLibraryTransitionGhostToEdge(
            LibraryTransitionGhost transitionGhost, int order) {
        boolean above = expandedGameIndex(transitionGhost.gameId)
                < expandedGameIndex(libraryTransitionGameId);
        float y = above ? -transitionGhost.view.getHeight() - dp(12)
                : root.getHeight() + dp(12);
        transitionGhost.view.animate().y(y).alpha(0f).scaleX(.82f).scaleY(.82f)
                .setStartDelay(Math.min(70L, order * 9L))
                .setDuration(LIBRARY_SHARED_ARTWORK_MS)
                .withEndAction(() -> removeLibraryTransitionGhost(transitionGhost)).start();
    }

    private int expandedGameIndex(String gameId) {
        ComputerDetails host = currentHost(selectedHostUuid);
        if (host == null || gameId == null) return Integer.MAX_VALUE;
        expandedLibraryItems(host);
        return expandedLibraryQuery.indexOf(gameId);
    }

    private String playniteCardGameId(View card) {
        Object tag = card != null ? card.getTag() : null;
        if (!(tag instanceof String) || !((String) tag).startsWith("playnite:")) return null;
        String gameId = ((String) tag).substring("playnite:".length());
        return HostGatewayClient.isPlayniteId(gameId) ? gameId : null;
    }

    private View findPlaynitePoster(View card) {
        if (!(card instanceof ViewGroup)) return null;
        return findTaggedChild((ViewGroup) card, "playnite.poster");
    }

    private void removeLibraryTransitionGhost() {
        for (LibraryTransitionGhost ghost :
                new ArrayList<>(libraryTransitionGhosts)) {
            removeLibraryTransitionGhost(ghost);
        }
    }

    private void removeLibraryTransitionGhost(LibraryTransitionGhost ghost) {
        if (ghost == null || !libraryTransitionGhosts.remove(ghost)) return;
        if (ghost.source != null) ghost.source.setAlpha(1f);
        if (ghost.target != null) ghost.target.setAlpha(1f);
        ghost.view.animate().cancel();
        ViewParent parent = ghost.view.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(ghost.view);
    }

    private static final class LibraryTransitionGhost {
        final String gameId;
        final ImageView view;
        final View source;
        View target;

        LibraryTransitionGhost(String gameId, ImageView view, View source) {
            this.gameId = gameId;
            this.view = view;
            this.source = source;
        }
    }

    private void focusExpandedLibrary() {
        // Search opened from Home keeps focus on its keyboard through the entrance.
        if (expandedGrid == null || librarySearch.isOpen()) return;
        expandedGrid.focusPosition(Math.max(0,
                expandedLibraryQuery.indexOf(libraryTransitionGameId)));
    }

    private void renderExpandedLibrary(ComputerDetails host) {
        if (!expandedLibraryMode || expandedGrid == null || host == null) return;
        List<PlayniteDashboardItem> expandedItems = expandedLibraryItems(host);
        expandedGrid.setItems(expandedItems);
        String sessionSignature = playniteSessionProjection == null
                ? "" : playniteTileSessionSignature(playniteSessionProjection, host);
        if (!renderedExpandedSessionSignature.equals(sessionSignature)) {
            renderedExpandedSessionSignature = sessionSignature;
            expandedGrid.rebindAll(PlayniteLibraryDiff.TEXT | PlayniteLibraryDiff.LAUNCH);
        }
        updateExpandedPositionIndicator(expandedGrid.focusedPosition(), expandedItems.size());
        ChildLibraryFilter.Selection childFilter = childLibraryFilter.selection(
                childLibraryKey(host.uuid));
        if (childFilter != renderedChildSelection) {
            // The cards' rating marks and the focused game's state line follow the filter.
            renderedChildSelection = childFilter;
            expandedGrid.rebindAll(0);
            int focused = expandedGrid.focusedPosition();
            if (childFilter == null) expandedChildState.setVisibility(View.GONE);
            else if (focused >= 0 && focused < expandedItems.size()) {
                bindChildFilterInfo(expandedItems.get(focused).game);
            }
        }
        libraryFilterBar.show(LibraryFiltersPanel.summary(hostGatewayStore, host.uuid,
                librarySources(), childFilter), expandedSearchQuery);
    }

    private String childLibraryKey(String hostId) {
        return ChildLibraryFilter.key(hostId, selectedProfileId(hostId));
    }

    private Map<String, String> librarySources() {
        return PlayniteLibrarySources.available(currentPlayniteGames,
                getResources().getConfiguration().getLocales().get(0));
    }

    private LibraryFiltersPanel.Host libraryFiltersHost() {
        return new LibraryFiltersPanel.Host() {
            @Override public void showPanel(String eyebrow, String title, String details,
                                            View... rows) {
                showSidePanel(eyebrow, title, details, rows);
            }

            @Override public void panelBack() { handlePanelBack(); }

            @Override public void filtersChanged(boolean sortOnly) {
                ComputerDetails host = hosts.get(selectedHostUuid);
                if (host == null) return;
                if (sortOnly) renderExpandedLibraryFromStart(host);
                else renderFilteredPlayniteLibraryFromStart(host);
            }

            @Override public void clearFilters() {
                ComputerDetails host = hosts.get(selectedHostUuid);
                if (host != null) clearLibraryFilters(host);
            }

            @Override public Map<String, String> sources() { return librarySources(); }

            @Override public boolean pinsEnabled() {
                return PinnedGames.pinsEnabled(preferences);
            }

            @Override public int shownCount() {
                ComputerDetails host = hosts.get(selectedHostUuid);
                return host == null ? 0 : expandedLibraryItems(host).size();
            }

            @Override public int totalCount() { return unfilteredPlayniteItems.size(); }

            @Override public String childFilterKey() {
                ComputerDetails host = hosts.get(selectedHostUuid);
                HostGatewayClient.IntegrationProfile profile = host == null ? null
                        : hostGatewayStore.profileSelection(host.uuid).selected;
                if (!childManagementProfileAllowed(host, profile)
                        || !childCapabilityAllowed(host, profile)
                        || !hasOwnChildren(host, profile)) return null;
                return childLibraryKey(host.uuid);
            }

            @Override public void loadChildren() {
                ComputerDetails host = hosts.get(selectedHostUuid);
                if (host != null) loadChildFilterChildren(host);
            }

            @Override public void selectChild(ChildLibraryFilter.Child child) {
                ComputerDetails host = hosts.get(selectedHostUuid);
                if (host == null) return;
                selectChildLibraryFilter(host, child, ChildLibraryFilter.Group.VISIBLE);
                renderFilteredPlayniteLibraryFromStart(host);
            }
        };
    }

    private ExpandedLibraryGrid createExpandedGrid() {
        return new ExpandedLibraryGrid(this, expandedGridColumns(),
                new ExpandedLibraryGrid.Callbacks() {
                    @Override public View createCard() {
                        return playniteCard(true);
                    }

                    @Override public void bindCard(View card, PlayniteDashboardItem item,
                                                   int payload) {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        if (host == null) return;
                        bindPlayniteCard(card, host, item, currentSunshineApps, payload);
                        ImageView poster = playniteCardViews(card).poster;
                        if ((payload & PlayniteLibraryDiff.ARTWORK) != 0
                                && !(poster.getDrawable() instanceof BitmapDrawable)) {
                            loadPlaynitePoster(host, item, poster, false);
                        }
                    }

                    @Override public void releaseCard(View card) {
                        PlayniteCardViews views = playniteCardViews(card);
                        views.poster.setTag(R.id.playnite_artwork_key, "released");
                        views.poster.setImageDrawable(null);
                        views.backdrop.setImageDrawable(null);
                    }

                    @Override public void bindBatch(boolean active) {
                        setCardBindBatch(active);
                    }

                    @Override public int changePayload(PlayniteDashboardItem previous,
                                                       PlayniteDashboardItem next) {
                        boolean live = isPlayniteInstalling(selectedHostUuid, next);
                        return previous.equals(next) && !live
                                ? -1 : playnitePayload(previous, next);
                    }

                    @Override public void onItemFocused(int position, int count) {
                        updateExpandedPositionIndicator(position, count);
                    }

                    @Override public void onLeave() {
                        libraryFilterBar.focusFilters();
                    }

                    @Override public void prefetchArtwork(List<PlayniteDashboardItem> items) {
                        ComputerDetails host = hosts.get(selectedHostUuid);
                        List<PlayniteDashboardItem> carouselItems =
                                new ArrayList<>(renderedPlayniteItems);
                        schedulePlayniteArtworkPrefetch(host,
                                mergeArtworkWarmup(items, carouselItems),
                                stableIds(carouselItems));
                    }

                    @Override public boolean reducedMotion() {
                        return reducedMotion;
                    }
                });
    }

    private List<PlayniteDashboardItem> mergeArtworkWarmup(
            List<PlayniteDashboardItem> primary, List<PlayniteDashboardItem> secondary) {
        LinkedHashMap<String, PlayniteDashboardItem> merged = new LinkedHashMap<>();
        for (PlayniteDashboardItem item : primary) merged.put(item.stableId(), item);
        for (PlayniteDashboardItem item : secondary) merged.putIfAbsent(item.stableId(), item);
        return new ArrayList<>(merged.values());
    }

    private Set<String> stableIds(List<PlayniteDashboardItem> items) {
        Set<String> result = new HashSet<>();
        for (PlayniteDashboardItem item : items) result.add(item.stableId());
        return result;
    }

    private List<PlayniteDashboardItem> expandedLibraryItems(ComputerDetails host) {
        return expandedLibraryQuery.apply(allPlayniteItems, expandedSearchQuery,
                hostGatewayStore.playniteLibrarySort(host.uuid), Locale.getDefault());
    }

    private void updateExpandedPositionIndicator(int focusedItem, int itemCount) {
        if (expandedPageIndicator == null) return;
        if (itemCount <= 0) expandedPageIndicator.setText("");
        else expandedPageIndicator.setText(String.format(Locale.getDefault(), "%d / %d",
                Math.max(1, Math.min(itemCount, focusedItem + 1)), itemCount));
    }

    private void showCarouselLoadingGhosts() {
        if (appRow == null) return;
        appRow.removeAllViews();
        int count = Math.max(4, Math.min(8, maxCarouselGameCount()));
        for (int index = 0; index < count; index++) {
            FrameLayout ghost = carouselGhost("playnite.loading." + index,
                    index == 0 ? .42f : .28f);
            if (index == 0) {
                ProgressBar spinner = new ProgressBar(this);
                ghost.addView(spinner, new FrameLayout.LayoutParams(
                        dp(20), dp(20), Gravity.CENTER));
            }
            appRow.addView(ghost, playniteCardSpacing());
        }
        renderedPlayniteItems = Collections.emptyList();
        wireHomeFocusNavigation();
    }

    private FrameLayout carouselGhost(String tag, float alpha) {
        FrameLayout ghost = new FrameLayout(this);
        ghost.setTag(tag);
        ghost.setFocusable(false);
        ghost.setClickable(false);
        ghost.setAlpha(alpha);
        ghost.setBackground(gradient(0xFF28343D, 0xFF151D23, 12));
        return ghost;
    }

    /** The game a carousel placeholder stands for, or null for any other view. */
    private static String pendingCarouselGameId(View view) {
        Object tag = view.getTag();
        return tag instanceof String && ((String) tag).startsWith(PENDING_CARD_TAG)
                ? ((String) tag).substring(PENDING_CARD_TAG.length()) : null;
    }

    /**
     * Frame step after a render that left placeholders: turns the first one into its card.
     * It reads the row and the rendered list as they are now, so a newer render is never
     * followed by a card of an older list. While the row animates a layout change it waits:
     * the row lays nothing out until that ends.
     */
    private View firstPendingCarouselCard() {
        for (int index = 0; appRow != null && index < appRow.getChildCount(); index++) {
            View child = appRow.getChildAt(index);
            if (pendingCarouselGameId(child) != null) return child;
        }
        return null;
    }

    private void buildNextPendingCarouselCard() {
        if (appRow == null) return;
        appRow.removeCallbacks(carouselCardBuildStep);
        // Under an entry intro's entrance only its own steps build cards; the rest wait here.
        if (introView != null && introView.entering()) {
            appRow.postDelayed(carouselCardBuildStep, CAROUSEL_CARD_BUILD_GAP_MS);
            return;
        }
        LayoutTransition transition = appRow.getLayoutTransition();
        boolean rowAnimating = transition != null && transition.isChangingLayout();
        for (int index = 0; index < appRow.getChildCount(); index++) {
            View child = appRow.getChildAt(index);
            if (pendingCarouselGameId(child) == null) continue;
            if (rowAnimating) {
                appRow.postOnAnimation(carouselCardBuildStep);
            } else if (buildPendingCarouselCard(child) != null) {
                // A card costs a few frames: smooth frames between cards read better than a run.
                requestPendingInitialGameFocus(currentHost(selectedHostUuid));
                // Under an intro that is leaving, one a frame: they are all due on screen.
                if (introView != null && introView.running()) {
                    appRow.postOnAnimation(carouselCardBuildStep);
                } else {
                    appRow.postDelayed(carouselCardBuildStep, CAROUSEL_CARD_BUILD_GAP_MS);
                }
            }
            return;
        }
    }

    /**
     * Step of the entry intro: the first placeholder's card among the leading positions. A
     * render under the intro's entrance leaves those to these steps, one per frame; a step
     * that finds them built (a second render queued its own) does nothing.
     */
    private void buildLeadingCarouselCard() {
        if (appRow == null) return;
        int leading = Math.min(PlayniteLibraryDiff.IMMEDIATE_CARDS, appRow.getChildCount());
        for (int index = 0; index < leading; index++) {
            View child = appRow.getChildAt(index);
            if (pendingCarouselGameId(child) == null) continue;
            buildPendingCarouselCard(child);
            requestPendingInitialGameFocus(currentHost(selectedHostUuid));
            return;
        }
    }

    /**
     * Replaces a carousel placeholder with its card, bound to the current state. The card
     * takes the placeholder's bounds at once: nothing in the row moves, and the card can be
     * focused even while the row holds its layout back for a running animation.
     */
    private View buildPendingCarouselCard(View placeholder) {
        String gameId = pendingCarouselGameId(placeholder);
        ComputerDetails host = currentHost(selectedHostUuid);
        PlayniteDashboardItem item = null;
        for (PlayniteDashboardItem rendered : renderedPlayniteItems) {
            if (rendered.stableId().equals(gameId)) item = rendered;
        }
        if (host == null || item == null) return null;
        View card = playniteCard(false);
        setCardBindBatch(true);
        try {
            bindPlayniteCard(card, host, item, currentSunshineApps, PlayniteLibraryDiff.TEXT
                    | PlayniteLibraryDiff.ARTWORK | PlayniteLibraryDiff.LAUNCH);
        } finally {
            setCardBindBatch(false);
        }
        int index = appRow.indexOfChild(placeholder);
        appRow.removeViewAt(index);
        appRow.addView(card, index, playniteCardSpacing());
        if (placeholder.isLaidOut()) {
            card.measure(View.MeasureSpec.makeMeasureSpec(placeholder.getWidth(),
                    View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(
                    placeholder.getHeight(), View.MeasureSpec.EXACTLY));
            card.layout(placeholder.getLeft(), placeholder.getTop(),
                    placeholder.getRight(), placeholder.getBottom());
        }
        wireHomeFocusNavigation();
        return card;
    }

    /** This game's carousel card, built now when its position still shows a placeholder. */
    private View carouselCard(String gameId) {
        View card = directChildWithTag(appRow, "playnite:" + gameId);
        if (card != null) return card;
        View placeholder = directChildWithTag(appRow, PENDING_CARD_TAG + gameId);
        return placeholder == null ? null : buildPendingCarouselCard(placeholder);
    }

    private void releaseExpandedGrid() {
        if (expandedGrid != null) expandedGrid.release();
    }

    private int expandedGridColumns() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        int available = Math.max(252, (int) widthDp - 108 - 221);
        return Math.max(3, available / 100);
    }

    /** The session's game the library and carousel lead with: resumable, else suspended. */
    private String selectedSessionGameId() {
        return !resumePlayniteGameId.isEmpty() ? resumePlayniteGameId : suspendedPlayniteGameId;
    }

    private List<PlayniteDashboardItem> putSelectedSessionFirst(
            List<PlayniteDashboardItem> items) {
        String selectedGameId = selectedSessionGameId();
        if (selectedGameId.isEmpty() || items.isEmpty()) return items;
        for (PlayniteDashboardItem item : items) {
            if (!selectedGameId.equalsIgnoreCase(item.stableId())) continue;
            if (items.get(0) == item) return items;
            List<PlayniteDashboardItem> reordered = new ArrayList<>(items);
            reordered.remove(item);
            reordered.add(0, item);
            return reordered;
        }
        return items;
    }

    private List<PlayniteLibraryGame> promoteInstallingGames(
            Set<String> installingIds, List<PlayniteLibraryGame> ordered,
            List<PlayniteLibraryGame> sourceFiltered) {
        if (installingIds.isEmpty()) return ordered;
        List<PlayniteLibraryGame> result = new ArrayList<>();
        Set<String> included = new HashSet<>();
        for (PlayniteLibraryGame game : sourceFiltered) {
            if (installingIds.contains(game.playniteGameId)) {
                result.add(game);
                included.add(game.playniteGameId);
            }
        }
        for (PlayniteLibraryGame game : ordered) {
            if (included.add(game.playniteGameId)) result.add(game);
        }
        return result;
    }

    private List<PlayniteDashboardItem> putInstallingItemsFirst(
            Set<String> installingIds, List<PlayniteDashboardItem> items) {
        if (installingIds.isEmpty()) return items;
        List<PlayniteDashboardItem> installing = new ArrayList<>();
        List<PlayniteDashboardItem> remaining = new ArrayList<>();
        for (PlayniteDashboardItem item : items) {
            if (installingIds.contains(item.game.playniteGameId)) installing.add(item);
            else remaining.add(item);
        }
        if (installing.isEmpty()) return items;
        installing.addAll(remaining);
        return installing;
    }

    private void applyPlayniteDiff(ComputerDetails host, List<NvApp> apps,
                                   List<PlayniteDashboardItem> requestedItems,
                                   String previousResumeGameId,
                                   String previousSuspendedGameId,
                                   boolean sessionPresentationChanged) {
        View currentFocus = getCurrentFocus();
        boolean carouselFocused = currentFocus != null && appRow != null
                && isDescendant(appRow, currentFocus);
        Object focusTag = carouselFocused ? currentFocus.getTag() : null;
        String focusedId = focusTag instanceof String &&
                ((String) focusTag).startsWith("playnite:")
                ? ((String) focusTag).substring("playnite:".length()) : null;
        int previousIndex = carouselFocused
                ? Math.max(0, appRow.indexOfChild(currentFocus)) : 0;

        List<PlayniteDashboardItem> items = requestedItems;
        Map<String, View> existing = new LinkedHashMap<>();
        Map<String, View> placeholders = new LinkedHashMap<>();
        boolean containsNonPlaynite = false;
        for (int index = 0; index < appRow.getChildCount(); index++) {
            View child = appRow.getChildAt(index);
            Object tag = child.getTag();
            String pendingId = pendingCarouselGameId(child);
            if (("playnite:" + PlayniteLibraryDiff.LIBRARY_TILE_ID).equals(tag)) {
                // Not a game: syncFullLibraryCard keeps it, focused or not, after the games.
                continue;
            } else if (tag instanceof String && ((String) tag).startsWith("playnite:")) {
                existing.put(((String) tag).substring("playnite:".length()), child);
            } else if (pendingId != null) {
                placeholders.put(pendingId, child);
            } else {
                containsNonPlaynite = true;
            }
        }
        if (containsNonPlaynite) {
            appRow.removeAllViews();
            existing.clear();
            placeholders.clear();
        }
        Map<String, PlayniteDashboardItem> previous = new LinkedHashMap<>();
        for (PlayniteDashboardItem item : renderedPlayniteItems) previous.put(item.stableId(), item);
        Set<String> wanted = new HashSet<>();
        for (PlayniteDashboardItem item : items) wanted.add(item.stableId());
        for (Map.Entry<String, View> entry : existing.entrySet()) {
            if (!wanted.contains(entry.getKey())) appRow.removeView(entry.getValue());
        }
        for (Map.Entry<String, View> entry : placeholders.entrySet()) {
            if (!wanted.contains(entry.getKey())) appRow.removeView(entry.getValue());
        }
        if (items.isEmpty()) {
            View libraryTile = directChildWithTag(appRow,
                    "playnite:" + PlayniteLibraryDiff.LIBRARY_TILE_ID);
            if (libraryTile != null) appRow.removeView(libraryTile);
            if (playniteInitialLoadPending || playniteLibraryRefreshing) {
                showCarouselLoadingGhosts();
                return;
            }
            if (appRow.getChildCount() == 0) {
                TextView empty = text(getString(hostGatewayStore.playniteLibraryFilter(host.uuid)
                                == PlayniteLibraryFilter.INSTALLED ? R.string.playnite_no_installed_games : R.string.playnite_library_empty),
                        15, 0xFFFFB74D, false);
                empty.setTag("playnite.empty");
                appRow.addView(empty, new LinearLayout.LayoutParams(
                        dp(700), ViewGroup.LayoutParams.MATCH_PARENT));
            }
            renderedPlayniteItems = Collections.emptyList();
            wireHomeFocusNavigation();
            return;
        }
        int missingCards = 0;
        for (PlayniteDashboardItem item : items) {
            if (!existing.containsKey(item.stableId())) missingCards++;
        }
        // Under an entry intro's entrance no card is built here: the intro runs their steps.
        boolean introSteps = introView != null && introView.entering();
        // Under the rest of a running intro neither: its frames get one card each.
        boolean introRunning = introView != null && introView.running();
        boolean placeholdersLeft = false;
        for (int index = 0; index < items.size(); index++) {
            PlayniteDashboardItem item = items.get(index);
            View card = existing.get(item.stableId());
            View placeholder = placeholders.get(item.stableId());
            PlayniteDashboardItem old = previous.get(item.stableId());
            boolean resumeStateChanged = item.stableId().equals(previousResumeGameId)
                    != item.stableId().equals(resumePlayniteGameId);
            boolean suspendedStateChanged = item.stableId().equals(previousSuspendedGameId)
                    != item.stableId().equals(suspendedPlayniteGameId);
            if (card == null && !suppressInitialCarouselMotion && (introRunning
                    || !PlayniteLibraryDiff.buildsInRender(index, missingCards))) {
                // A later frame builds this card; until then its position shows a placeholder.
                card = placeholder != null ? placeholder
                        : carouselGhost(PENDING_CARD_TAG + item.stableId(), .28f);
                placeholdersLeft = true;
            } else if (card == null) {
                if (placeholder != null) appRow.removeView(placeholder);
                card = playniteCard(false);
                bindPlayniteCard(card, host, item, apps, PlayniteLibraryDiff.TEXT
                        | PlayniteLibraryDiff.ARTWORK | PlayniteLibraryDiff.LAUNCH);
            } else if (!item.equals(old)
                    || resumeStateChanged
                    || suspendedStateChanged
                    || sessionPresentationChanged
                    || isPlayniteInstalling(host.uuid, item)) {
                int payload = !item.equals(old) ? playnitePayload(old, item)
                        : PlayniteLibraryDiff.TEXT;
                if (resumeStateChanged || suspendedStateChanged
                        || sessionPresentationChanged) {
                    payload |= PlayniteLibraryDiff.TEXT | PlayniteLibraryDiff.LAUNCH;
                }
                bindPlayniteCard(card, host, item, apps,
                        payload);
            }
            int currentIndex = appRow.indexOfChild(card);
            if (currentIndex < 0) {
                appRow.addView(card, Math.min(index, appRow.getChildCount()),
                        playniteCardSpacing());
            } else if (currentIndex != index) {
                moveCarouselCard(card, currentIndex, index);
            }
            if (suppressInitialCarouselMotion) {
                ImageView poster = (ImageView) findTaggedChild(
                        (ViewGroup) card, "playnite.poster");
                restoreCachedPlaynitePoster(host, item, poster);
            }
        }
        renderedPlayniteItems = Collections.unmodifiableList(new ArrayList<>(items));
        appRow.removeCallbacks(carouselCardBuildStep);
        if (placeholdersLeft) {
            // The leading cards one per frame, all before the intro leaves; then the rest.
            for (int card = 0; introSteps && card < PlayniteLibraryDiff.IMMEDIATE_CARDS; card++) {
                ConsoleIntroView.afterEntrance(introView, this::buildLeadingCarouselCard);
            }
            appRow.postOnAnimation(carouselCardBuildStep);
        }
        String selection = PlayniteLibraryDiff.selectionAfter(focusedId, previousIndex, items);
        if (focusedId != null && selection != null) {
            View target = carouselCard(selection);
            // Only focus this render took away comes back: a key pressed before the post runs
            // has moved on, and pulling focus back would bounce it.
            if (target != null) target.post(() -> {
                View now = getCurrentFocus();
                if (now == null || !isDescendant(appRow, now)) target.requestFocus();
            });
        }
        wireHomeFocusNavigation();
    }

    /**
     * Puts a carousel card at {@code index}. A focused card stays attached (detaching it
     * would drop focus): the cards ahead of it move behind it, and the row follows it.
     */
    private void moveCarouselCard(View card, int currentIndex, int index) {
        if (!card.hasFocus()) {
            appRow.removeViewAt(currentIndex);
            appRow.addView(card, Math.min(index, appRow.getChildCount()),
                    playniteCardSpacing());
            return;
        }
        for (int moved = 0; moved < currentIndex - index; moved++) {
            View ahead = appRow.getChildAt(index);
            appRow.removeViewAt(index);
            appRow.addView(ahead, appRow.indexOfChild(card) + 1 + moved);
        }
        if (portraitLayout) smoothCenterOn(appVerticalScroll, card);
        else smoothCenterOn(appScroll, card);
    }

    private int playnitePayload(PlayniteDashboardItem old, PlayniteDashboardItem item) {
        if (old == null) return PlayniteLibraryDiff.TEXT | PlayniteLibraryDiff.ARTWORK |
                PlayniteLibraryDiff.LAUNCH;
        int payload = 0;
        if (!old.game.name.equals(item.game.name) || old.game.installed != item.game.installed ||
                old.game.installing != item.game.installing ||
                !old.game.updateState.equals(item.game.updateState) ||
                old.game.updateProgress != item.game.updateProgress ||
                old.game.installRequiresAttention != item.game.installRequiresAttention ||
                old.game.operationProgress != item.game.operationProgress ||
                old.game.uninstalling != item.game.uninstalling ||
                !old.game.operationState.equals(item.game.operationState) ||
                !old.game.installAttentionReason.equals(item.game.installAttentionReason) ||
                !old.game.installWindowTitle.equals(item.game.installWindowTitle) ||
                !old.game.installLauncher.equals(item.game.installLauncher) ||
                old.game.playtimeSeconds != item.game.playtimeSeconds ||
                old.game.playCount != item.game.playCount ||
                !old.game.description.equals(item.game.description) ||
                !old.game.source.equals(item.game.source) ||
                !old.game.libraryKey.equals(item.game.libraryKey) ||
                !old.game.libraryName.equals(item.game.libraryName)) {
            payload |= PlayniteLibraryDiff.TEXT;
        }
        if (!old.game.coverKey.equals(item.game.coverKey) ||
                !old.game.backgroundKey.equals(item.game.backgroundKey) ||
                !old.game.heroKey.equals(item.game.heroKey)) {
            payload |= PlayniteLibraryDiff.ARTWORK;
        }
        if (!Objects.equals(old.sunshineAppId, item.sunshineAppId) ||
                old.mappingState != item.mappingState) payload |= PlayniteLibraryDiff.LAUNCH;
        return payload;
    }

    /** Builds an unbound card; callers bind it with bindPlayniteCard. */
    private View playniteCard(boolean expandedCard) {
        boolean debugCarousel = CONSOLE_UI_V2 && !portraitLayout;
        expandedCard = debugCarousel && expandedCard;
        LinearLayout card = cardBase(
                dp(portraitLayout ? 220 : expandedCard ? CAROUSEL_FOCUSED_CARD_WIDTH_DP
                        : debugCarousel ? CAROUSEL_CARD_WIDTH_DP : 205),
                dp(portraitLayout ? 225 : expandedCard ? CAROUSEL_FOCUSED_CARD_HEIGHT_DP
                        : debugCarousel ? CAROUSEL_CARD_HEIGHT_DP : 190));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(0, 0, 0, 0);
        card.setClipToOutline(true);
        if (debugCarousel) card.setForeground(carouselCardForeground());
        FrameLayout artworkFrame = new FrameLayout(this);
        artworkFrame.setBackground(gradient(0xFF26333D, 0xFF172128, 12));
        ImageView backdrop = new ImageView(this);
        backdrop.setTag("playnite.poster.backdrop");
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(.42f);
        backdrop.setVisibility(View.GONE);
        artworkFrame.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView poster = new ImageView(this);
        poster.setTag("playnite.poster");
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        poster.setImageResource(R.drawable.ic_computer);
        poster.setPadding(dp(expandedCard ? 20 : debugCarousel ? 24 : 72),
                dp(expandedCard ? 29 : debugCarousel ? 36 : 54),
                dp(expandedCard ? 20 : debugCarousel ? 24 : 72),
                dp(expandedCard ? 29 : debugCarousel ? 36 : 54));
        artworkFrame.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (debugCarousel && !expandedCard) {
            View shine = new View(this);
            shine.setTag("playnite.shine");
            shine.setAlpha(0f);
            shine.setRotation(14f);
            shine.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x00FFFFFF, 0x5CFFFFFF, 0x00FFFFFF}));
            shine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams shineParams = new FrameLayout.LayoutParams(
                    dp(24), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
            shineParams.leftMargin = -dp(32);
            artworkFrame.addView(shine, shineParams);
        }
        ImageView source = new ImageView(this);
        source.setTag("playnite.source");
        source.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        source.setPadding(dp(2), dp(2), dp(2), dp(2));
        source.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.setTag(R.id.playnite_tile_layout, !debugCarousel ? TileLayout.PORTRAIT
                : expandedCard ? TileLayout.LIBRARY : TileLayout.CAROUSEL);
        View copyScrim = new View(this);
        copyScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xE6000000}));
        copyScrim.setVisibility(debugCarousel && expandedCard ? View.VISIBLE : View.GONE);
        artworkFrame.addView(copyScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38), Gravity.BOTTOM));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.HORIZONTAL);
        copy.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(CAROUSEL_CARD_GAP_DP), dp(2),
                dp(CAROUSEL_CARD_GAP_DP), dp(5));
        copy.addView(source, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView name = text("", debugCarousel ? 12 : 19, Color.WHITE, true);
        name.setTag("playnite.name");
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setShadowLayer(dp(2), 0, dp(1), 0xF0000000);
        name.addOnLayoutChangeListener(CardTitleFit.ON_LAYOUT);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(CAROUSEL_CARD_GAP_DP);
        copy.addView(name, nameParams);
        TextView playtime = text("", debugCarousel ? 8 : 10, 0xFFB8C9DC, false);
        playtime.setTag("playnite.playtime");
        playtime.setVisibility(View.GONE);
        copy.addView(playtime, matchLinearWidth());
        if (debugCarousel) {
            copy.setVisibility(expandedCard ? View.VISIBLE : View.GONE);
            artworkFrame.addView(copy, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34), Gravity.BOTTOM));
            card.addView(artworkFrame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            card.addView(artworkFrame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(portraitLayout ? 140 : 115)));
            card.addView(copy, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        // Top-right status badge (mockups/tile-badges-v2.html); the bottom stays the provider's.
        TextView badge = text("", 11, Color.WHITE, true);
        badge.setTag("playnite.badge");
        badge.setSingleLine(true);
        badge.setFontFeatureSettings("tnum");
        badge.setVisibility(View.GONE);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        artworkFrame.addView(badge, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(20), Gravity.TOP | Gravity.END));
        return card;
    }

    /** How a card lays out its badge and bar: a carousel card is compact until focused. */
    private enum TileLayout { CAROUSEL, LIBRARY, PORTRAIT }

    /** The work bar exists only on cards that have shown work in progress. */
    private ConsoleTileProgressBar tileProgressBar(PlayniteCardViews views, boolean needed) {
        if (views.progress != null || !needed) return views.progress;
        ConsoleTileProgressBar progress = new ConsoleTileProgressBar(this);
        progress.setVisibility(View.GONE);
        ViewGroup artworkFrame = (ViewGroup) views.poster.getParent();
        // Under the badge, over the artwork and the library's title scrim.
        artworkFrame.addView(progress, artworkFrame.indexOfChild(views.badge),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3),
                        Gravity.BOTTOM));
        views.progress = progress;
        return progress;
    }

    /**
     * Shows the card's badge and bar at the size its layout and focus give: a resting carousel
     * card 16 dp / 10 dp icon / 9 sp, 4 dp in; a focused or library card 20 / 12 / 11 sp, 6 dp
     * in. The bar is inset from the sides, and sits on the library card's title strip.
     */
    private void renderTileBadge(PlayniteCardViews views, boolean focused) {
        ConsoleTileBadge model = views.badgeModel;
        boolean compact = views.layout == TileLayout.CAROUSEL && !focused;
        // Focus moves rebind two cards per step while scrolling: touch the views (each setter
        // can request a layout) only when what they show actually changed.
        String key = model.kind.name() + '|' + model.percent() + '|' + model.progress + '|'
                + model.fillColor() + '|' + compact + '|' + views.layout + '|' + reducedMotion
                + '|' + views.childMarks.ratingShown();
        if (key.equals(views.badgeKey)) return;
        views.badgeKey = key;
        TextView badge = views.badge;
        if (!model.visible()) {
            badge.setVisibility(View.GONE);
        } else {
            int height = dp(compact ? 16 : 20);
            int icon = dp(compact ? 10 : 12);
            String percent = model.percent();
            badge.setVisibility(View.VISIBLE);
            if (!percent.contentEquals(badge.getText())) badge.setText(percent);
            badge.setTextSize(compact ? 9 : 11);
            badge.setTextColor(model.contentColor());
            badge.setMaxWidth(dp(compact ? 58 : 80));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) badge.getLayoutParams();
            params.height = height;
            params.width = percent.isEmpty() ? height : ViewGroup.LayoutParams.WRAP_CONTENT;
            params.topMargin = dp(compact ? 4 : 6);
            params.rightMargin = params.topMargin;
            // The child filter's rating takes the corner; the status goes under it.
            if (views.childMarks.ratingShown()) params.topMargin += dp(26);
            badge.setLayoutParams(params);
            badge.setMinWidth(height);
            if (percent.isEmpty()) {
                // An icon-only badge is a circle with its icon centred.
                badge.setPadding((height - icon) / 2, 0, 0, 0);
                badge.setCompoundDrawablePadding(0);
            } else {
                int side = dp(compact ? 4 : 5);
                badge.setPadding(side, 0, side, 0);
                badge.setCompoundDrawablePadding(dp(compact ? 2 : 3));
            }
            android.graphics.drawable.Drawable glyph = getDrawable(model.kind.icon).mutate();
            glyph.setBounds(0, 0, icon, icon);
            badge.setCompoundDrawables(glyph, null, null, null);
            badge.setCompoundDrawableTintList(
                    android.content.res.ColorStateList.valueOf(model.contentColor()));
            GradientDrawable background = badge.getBackground() instanceof GradientDrawable
                    ? (GradientDrawable) badge.getBackground() : new GradientDrawable();
            background.setColor(model.fillColor());
            background.setStroke(dp(1), model.kind.color);
            background.setCornerRadius(height / 2f);
            badge.setBackground(background);
        }
        ConsoleTileProgressBar progress = tileProgressBar(views, model.kind.bar);
        if (progress == null) return;
        progress.setVisibility(model.kind.bar ? View.VISIBLE : View.GONE);
        if (!model.kind.bar) return;
        progress.setProgress(model.progress, reducedMotion);
        boolean library = views.layout == TileLayout.LIBRARY;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) progress.getLayoutParams();
        params.height = dp(library || compact ? 3 : 4);
        params.leftMargin = dp(library ? 10 : compact ? 6 : 8);
        params.rightMargin = params.leftMargin;
        params.bottomMargin = dp(library ? 36 : compact ? 5 : 6);
        progress.setLayoutParams(params);
    }

    private LinearLayout.LayoutParams matchLinearWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams playniteCardSpacing() {
        LinearLayout.LayoutParams params = portraitLayout
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(225))
                : new LinearLayout.LayoutParams(
                        dp(CONSOLE_UI_V2 ? CAROUSEL_CARD_WIDTH_DP : 205),
                        dp(CONSOLE_UI_V2 ? CAROUSEL_CARD_HEIGHT_DP : 190));
        if (portraitLayout) params.bottomMargin = dp(14);
        else params.rightMargin = dp(CONSOLE_UI_V2 ? CAROUSEL_CARD_GAP_DP : 14);
        return params;
    }

    private int playniteSourceIcon(String source) {
        switch (PlayniteLibrarySources.key(source)) {
            case "steam":
                return R.drawable.ic_source_steam;
            case "epic":
            case "epic games":
                return R.drawable.ic_source_epic_games;
            case PlayniteLibrarySources.PLAYNITE_KEY:
                return R.drawable.ic_source_playnite;
            default:
                return 0;
        }
    }

    private PlayniteSessionPresentation.State playniteSessionState(
            PlayniteDashboardItem item) {
        return playniteSessionProjection == null
                ? PlayniteSessionPresentation.State.READY
                : playniteSessionProjection.stateFor(item.stableId());
    }

    private boolean isFreshExactRunningManagedGame(ComputerDetails host,
                                                    PlayniteDashboardItem item) {
        if (host == null) return false;
        SessionSnapshot snapshot = resolveSessionSnapshot(host);
        // A stop already in flight leaves nothing to end: every "End game" entry reads this.
        return !snapshot.endingGame && isExactRunningManagedGame(host, item, snapshot);
    }

    private boolean isExactRunningManagedGame(ComputerDetails host, PlayniteDashboardItem item,
                                              SessionSnapshot snapshot) {
        return item != null && host.uuid.equalsIgnoreCase(selectedHostUuid)
                && "VERIFIED".equals(snapshot.processEvidence)
                && snapshot.processId > 0
                && !snapshot.processToken.isEmpty()
                && snapshot.playniteGameId.equals(
                SessionSnapshot.normalize(item.stableId()));
    }

    /**
     * Brackets one synchronous UI pass (a grid layout or scroll, a library render, the host
     * selector). No other message runs inside it, so the session snapshot, which reads several
     * stores and parses stored JSON, is resolved once per host and profile for the whole pass.
     */
    private void setCardBindBatch(boolean active) {
        if (active) {
            cardBindBatchDepth++;
        } else if (--cardBindBatchDepth == 0) {
            cardBindBatchMemo.clear();
        }
    }

    private void bindPlayniteCard(View card, ComputerDetails host,
                                  PlayniteDashboardItem item, List<NvApp> apps, int payload) {
        PlayniteCardViews views = playniteCardViews(card);
        if (views.item == null || !views.item.stableId().equals(item.stableId())) {
            card.setTag("playnite:" + item.stableId());
        }
        TextView name = views.name;
        TextView playtime = views.playtime;
        ImageView source = views.source;
        ImageView poster = views.poster;
        ImageView backdrop = views.backdrop;
        PlayniteSessionPresentation.State sessionState = playniteSessionState(item);
        // A session needing recovery is still this game's session: it shows Resume, never the
        // ready checkmark (which belongs to the full library only).
        boolean resumeSession = sessionState == PlayniteSessionPresentation.State.RESUME_ACTIVE
                || sessionState == PlayniteSessionPresentation.State.RECOVERY_REQUIRED;
        boolean suspendedSession = sessionState
                == PlayniteSessionPresentation.State.RESUME_SUSPENDED
                || sessionState == PlayniteSessionPresentation.State.FROZEN;
        stylePlayniteSessionCard(card, card.hasFocus(), resumeSession || suspendedSession);
        GameOperationsController.Presentation operation =
                gameOperationsController.presentation(host.uuid, item.game);
        boolean installing = operation.installActive || operation.uninstallActive;
        ChildEligibilityProjection childPolicy = childEligibilityProjection(
                host.uuid, selectedProfileId(host.uuid));
        boolean childGameBlocked = childPolicy.blocked;
        card.setAlpha(childAwarePlayniteCardAlpha(host.uuid, item, operation,
                resumeSession || suspendedSession, card.hasFocus(), childPolicy.blocked));
        if (!item.game.installed && !installing) {
            android.graphics.ColorMatrixColorFilter muted = uninstalledArtworkFilter;
            poster.setColorFilter(muted);
            if (backdrop != null) backdrop.setColorFilter(muted);
        } else {
            poster.clearColorFilter();
            if (backdrop != null) backdrop.clearColorFilter();
        }
        if (childGameBlocked) {
            android.graphics.ColorMatrixColorFilter muted = blockedArtworkFilter;
            poster.setColorFilter(muted);
            if (backdrop != null) backdrop.setColorFilter(muted);
        }
        views.childMarks.bind((ViewGroup) poster.getParent(), item.game,
                views.layout == TileLayout.LIBRARY
                        ? childLibraryFilter.selection(childLibraryKey(host.uuid)) : null);
        views.badgeModel = tileBadge(host.uuid, item, sessionState, operation, childGameBlocked);
        renderTileBadge(views, card.hasFocus());
        // The selected game's panel follows its live status; selecting it binds the panel.
        if (card.hasFocus()) showSelectedGameStatus(views.badgeModel);
        String playtimeText = (payload & PlayniteLibraryDiff.TEXT) != 0
                ? formatPlayniteTime(item.game.playtimeSeconds) : null;
        if ((payload & PlayniteLibraryDiff.TEXT) != 0) {
            if (!item.game.name.contentEquals(name.getText())) {
                name.setText(item.game.name);
                CardTitleFit.apply(name);
            }
            playtime.setText(playtimeText);
            String sourceKey = item.game.libraryKey.isEmpty()
                    ? item.game.source : item.game.libraryKey;
            int sourceIcon = playniteSourceIcon(sourceKey);
            if (sourceIcon != views.sourceIcon || source.getDrawable() == null) {
                views.sourceIcon = sourceIcon;
                source.setImageResource(sourceIcon);
            }
            source.setVisibility(sourceIcon == 0 ? View.GONE : View.VISIBLE);
            if (source.getBackground() == null) {
                GradientDrawable sourceBackground = gradient(0xD9182027, 0xE00C1116, 4);
                sourceBackground.setStroke(dp(1), 0xB3FFFFFF);
                source.setBackground(sourceBackground);
            }
        }
        if ((payload & PlayniteLibraryDiff.ARTWORK) != 0) resetPlaynitePoster(poster, item);
        if (!completedPlayniteInstallAnimations.isEmpty()
                && completedPlayniteInstallAnimations.remove(
                        playniteInstallKey(host.uuid, item))) {
            card.animate().cancel();
            float restingAlpha = childAwarePlayniteCardAlpha(host.uuid, item, operation,
                    resumeSession || suspendedSession, card.hasFocus(), childPolicy.blocked);
            if (reducedMotion) {
                card.setAlpha(restingAlpha);
            } else {
                card.setAlpha(.55f);
                card.setTranslationY(dp(5));
                card.animate().alpha(restingAlpha).translationY(0f)
                        .setDuration(420L).start();
            }
        }
        views.item = item;
        views.hostUuid = host.uuid;
        views.sessionCard = resumeSession || suspendedSession;
    }

    /**
     * The spoken card description, built when accessibility asks for it rather than on
     * every bind (it formats several strings and is read only with a screen reader).
     */
    private CharSequence playniteCardDescription(View card) {
        PlayniteCardViews views = playniteCardViews(card);
        PlayniteDashboardItem item = views.item;
        ComputerDetails host = item == null ? null : currentHost(views.hostUuid);
        if (host == null) return null;
        // The full state, also for a child-blocked game, whose reason follows below.
        String stateText = tileBadgeText(tileBadge(host.uuid, item, playniteSessionState(item),
                gameOperationsController.presentation(host.uuid, item.game), false));
        String sourceName = PlayniteLibrarySources.label(item.game.libraryName.isEmpty()
                ? item.game.source : item.game.libraryName);
        String cardDescription = getString(R.string.playnite_card_description,
                item.game.name, formatPlayniteTime(item.game.playtimeSeconds), stateText)
                + ". " + getString(R.string.playnite_source, sourceName);
        ChildEligibilityProjection childPolicy = childEligibilityProjection(
                host.uuid, selectedProfileId(host.uuid));
        if (childPolicy.blocked) {
            cardDescription += ". " + childLaunchFailureMessage(childPolicy.reason);
        }
        return cardDescription;
    }

    private final class PlayniteCardAccessibility extends View.AccessibilityDelegate {
        @Override public void onInitializeAccessibilityNodeInfo(
                View card, android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(card, info);
            CharSequence description = playniteCardDescription(card);
            if (description != null) info.setContentDescription(description);
        }

        @Override public void onInitializeAccessibilityEvent(
                View card, android.view.accessibility.AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(card, event);
            CharSequence description = playniteCardDescription(card);
            if (description != null) event.setContentDescription(description);
        }
    }

    /** A card's child views and what it was last bound to, kept on the card itself. */
    private static final class PlayniteCardViews {
        final TextView name;
        final TextView playtime;
        final TextView badge;
        final ImageView source;
        final ImageView poster;
        final ImageView backdrop;
        final TileLayout layout;
        ConsoleTileProgressBar progress;
        /** What the badge and bar last rendered (see renderTileBadge). */
        String badgeKey = "";
        ConsoleTileBadge badgeModel;
        final ConsoleChildFilterMarks childMarks = new ConsoleChildFilterMarks();
        PlayniteDashboardItem item;
        String hostUuid;
        boolean sessionCard;
        int sourceIcon;

        PlayniteCardViews(TextView name, TextView playtime, TextView badge, ImageView source,
                          ImageView poster, ImageView backdrop, TileLayout layout) {
            this.name = name;
            this.playtime = playtime;
            this.badge = badge;
            this.source = source;
            this.poster = poster;
            this.backdrop = backdrop;
            this.layout = layout;
        }
    }

    private PlayniteCardViews playniteCardViews(View card) {
        Object cached = card.getTag(R.id.playnite_card_views);
        if (cached instanceof PlayniteCardViews) return (PlayniteCardViews) cached;
        ViewGroup group = (ViewGroup) card;
        PlayniteCardViews views = new PlayniteCardViews(
                (TextView) findTaggedChild(group, "playnite.name"),
                (TextView) findTaggedChild(group, "playnite.playtime"),
                (TextView) findTaggedChild(group, "playnite.badge"),
                (ImageView) findTaggedChild(group, "playnite.source"),
                (ImageView) findTaggedChild(group, "playnite.poster"),
                (ImageView) findTaggedChild(group, "playnite.poster.backdrop"),
                (TileLayout) card.getTag(R.id.playnite_tile_layout));
        card.setTag(R.id.playnite_card_views, views);
        // One fixed line (boring layout, end ellipsis) so title changes stay cheap to lay out.
        views.name.setSingleLine(true);
        views.name.setEllipsize(TextUtils.TruncateAt.END);
        card.setOnClickListener(playniteCardListener);
        card.setOnLongClickListener(playniteCardListener);
        card.setOnKeyListener(playniteCardListener);
        card.setOnFocusChangeListener(playniteCardListener);
        card.setAccessibilityDelegate(playniteCardAccessibility);
        return views;
    }

    /** One listener for every Playnite card; it reads the bound game from the card. */
    private final class PlayniteCardListener implements View.OnClickListener,
            View.OnLongClickListener, View.OnKeyListener, View.OnFocusChangeListener {
        @Override public void onClick(View card) {
            PlayniteCardViews views = playniteCardViews(card);
            if (views.item != null) activatePlayniteItem(views.hostUuid, views.item);
        }

        @Override public boolean onLongClick(View card) {
            PlayniteCardViews views = playniteCardViews(card);
            if (views.item == null) return false;
            showPlayniteGameActions(currentHost(views.hostUuid), views.item);
            return true;
        }

        @Override public boolean onKey(View card, int keyCode, KeyEvent event) {
            PlayniteCardViews views = playniteCardViews(card);
            if (views.item != null && event.getAction() == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_MENU
                    || keyCode == KeyEvent.KEYCODE_BUTTON_START)) {
                showPlayniteGameActions(currentHost(views.hostUuid), views.item);
                return true;
            }
            return false;
        }

        @Override public void onFocusChange(View card, boolean focused) {
            PlayniteCardViews views = playniteCardViews(card);
            PlayniteDashboardItem item = views.item;
            if (item == null) return;
            String hostUuid = views.hostUuid;
            boolean sessionCard = views.sessionCard;
            stylePlayniteSessionCard(card, focused, sessionCard);
            GameOperationsController.Presentation focusedOperation =
                    gameOperationsController.presentation(hostUuid, item.game);
            card.setAlpha(childAwarePlayniteCardAlpha(hostUuid, item, focusedOperation,
                    sessionCard, focused, childEligibilityProjection(
                            hostUuid, selectedProfileId(hostUuid)).blocked));
            renderTileBadge(views, focused);
            updateCarouselMarquee(views.name, focused);
            if (focused) {
                if (!expandedLibraryMode && card.getParent() == appRow) {
                    // Hidden until the new selection's title fades in; the same selection
                    // focused again (a render re-requests it) keeps its title shown.
                    if (selectedGameTitleRow != null
                            && !libraryTransitionCoordinator.presents(item.stableId())) {
                        selectedGameTitleRow.setVisibility(View.INVISIBLE);
                    }
                    positionCarouselMetadata(card);
                }
                ImageView poster = views.poster;
                libraryTransitionCoordinator.beginSelection(item.stableId(),
                        () -> {
                            prepareGameMetadataTransition();
                            ComputerDetails host = currentHost(hostUuid);
                            if (host != null) loadPlaynitePoster(host, item, poster, true);
                        },
                        () -> showGameMetadataHeader(item),
                        () -> showGameMetadataDescription(item));
                if (!expandedLibraryMode && card.getParent() == appRow) {
                    lastCarouselGameId = item.stableId();
                    schedulePlayniteSelectionSave(hostUuid, lastCarouselGameId, false);
                } else if (expandedLibraryMode && card.getParent() == expandedGrid) {
                    schedulePlayniteSelectionSave(hostUuid, item.stableId(), true);
                }
                if (expandedLibraryMode && card.getParent() == expandedGrid) {
                    // ExpandedLibraryGrid scrolls its focused card into view.
                } else if (portraitLayout) {
                    smoothCenterOn(appVerticalScroll, card);
                } else {
                    smoothCenterOn(appScroll, card);
                    mainHandler.postDelayed(() -> {
                        if (card.hasFocus()) positionCarouselMetadata(card);
                    }, 280L);
                }
            } else {
                hideCarouselMetadataOutsideCarousel();
            }
        }
    }

    private final PlayniteCardListener playniteCardListener = new PlayniteCardListener();
    private final PlayniteCardAccessibility playniteCardAccessibility =
            new PlayniteCardAccessibility();

    private final android.graphics.ColorMatrixColorFilter uninstalledArtworkFilter =
            saturationFilter(.25f);
    private final android.graphics.ColorMatrixColorFilter blockedArtworkFilter =
            saturationFilter(0f);

    private static android.graphics.ColorMatrixColorFilter saturationFilter(float saturation) {
        android.graphics.ColorMatrix colors = new android.graphics.ColorMatrix();
        colors.setSaturation(saturation);
        return new android.graphics.ColorMatrixColorFilter(colors);
    }

    private float playniteCardAlpha(PlayniteDashboardItem item,
                                    GameOperationsController.Presentation operation,
                                    boolean resumeSession, boolean focused) {
        if (operation.attentionRequired) return .94f;
        if (operation.installActive || operation.uninstallActive || !item.game.installed) {
            return .88f;
        }
        return focused || resumeSession ? 1f : .93f;
    }

    private float childAwarePlayniteCardAlpha(String hostUuid, PlayniteDashboardItem item,
                                              GameOperationsController.Presentation operation,
                                              boolean resumeSession, boolean focused,
                                              boolean childBlocked) {
        float alpha = playniteCardAlpha(item, operation, resumeSession, focused);
        // A closing session never greys the games: a Play while it closes waits in the queue.
        return childBlocked ? Math.min(alpha, .46f) : alpha;
    }

    private void schedulePlayniteSelectionSave(String hostUuid, String gameId,
                                                boolean expanded) {
        if (hostUuid == null || hostUuid.isEmpty() || gameId == null || gameId.isEmpty()) return;
        if (pendingPlayniteSelectionSave != null
                && (!hostUuid.equals(pendingPlayniteSelectionHostUuid)
                || expanded != pendingPlayniteSelectionExpanded)) {
            flushPendingPlayniteSelection();
        } else if (pendingPlayniteSelectionSave != null) {
            mainHandler.removeCallbacks(pendingPlayniteSelectionSave);
        }
        pendingPlayniteSelectionHostUuid = hostUuid;
        pendingPlayniteSelectionGameId = gameId;
        pendingPlayniteSelectionExpanded = expanded;
        pendingPlayniteSelectionSave = this::flushPendingPlayniteSelection;
        mainHandler.postDelayed(pendingPlayniteSelectionSave,
                PLAYNITE_SELECTION_SAVE_DELAY_MS);
    }

    private void flushPendingPlayniteSelection() {
        if (pendingPlayniteSelectionSave == null) return;
        mainHandler.removeCallbacks(pendingPlayniteSelectionSave);
        pendingPlayniteSelectionSave = null;
        String hostUuid = pendingPlayniteSelectionHostUuid;
        String gameId = pendingPlayniteSelectionGameId;
        boolean expanded = pendingPlayniteSelectionExpanded;
        pendingPlayniteSelectionHostUuid = null;
        pendingPlayniteSelectionGameId = null;
        if (expanded) libraryViewStateStore.saveExpandedGame(hostUuid, gameId);
        else libraryViewStateStore.saveCarouselGame(hostUuid, gameId);
        String preferenceKey = "selected_playnite." + hostUuid;
        if (!gameId.equals(preferences.getString(preferenceKey, ""))) {
            preferences.edit().putString(preferenceKey, gameId).apply();
        }
    }

    private void stylePlayniteSessionCard(View card, boolean focused, boolean resumeSession) {
        styleCard(card, focused);
        if (!resumeSession) return;
        GradientDrawable background = gradient(
                focused ? 0xF0203C43 : 0xE0173037,
                focused ? 0xF011252B : 0xE00D2026, 12);
        background.setStroke(dp(1), focused ? 0xFFEAFBFF : 0xFF54D4DE);
        card.setBackground(background);
        card.setElevation(dp(focused ? 14 : 8));
        boolean homeCarousel = card.getParent() == appRow && !expandedLibraryMode;
        card.setPivotX(card.getWidth() > 0 ? card.getWidth() / 2f : dp(42));
        float scale = homeCarousel ? 1f : focused ? 1.04f : 1.02f;
        if (reducedMotion || !card.isLaidOut()) {
            card.setScaleX(scale);
            card.setScaleY(scale);
        } else {
            card.animate().cancel();
            card.animate().scaleX(scale).scaleY(scale).translationY(0f)
                    .setDuration(getResources().getInteger(
                            R.integer.console_motion_focus_ms)).start();
        }
    }

    private void showGameMetadataHeader(PlayniteDashboardItem item) {
        if (item == null) return;
        if (selectedGameTitleRow != null) {
            selectedGameTitleRow.setVisibility(View.VISIBLE);
        }
        if (selectedGameSource != null) {
            String sourceKey = item.game.libraryKey.isEmpty()
                    ? item.game.source : item.game.libraryKey;
            String sourceLabel = PlayniteLibrarySources.label(item.game.libraryName.isEmpty()
                    ? item.game.source : item.game.libraryName);
            int sourceIcon = playniteSourceIcon(sourceKey);
            selectedGameSource.setImageResource(sourceIcon);
            selectedGameSource.setContentDescription(getString(
                    R.string.playnite_source, sourceLabel));
            selectedGameSource.setVisibility(sourceIcon == 0 ? View.GONE : View.VISIBLE);
        }
        String played = lastPlayedText(selectedHostUuid, item);
        String playtime = formatPlayniteTime(item.game.playtimeSeconds);
        String facts = getString(R.string.playnite_selected_game_facts, played, playtime);
        if (selectedGameFactsRow != null) {
            selectedGameFacts.setVisibility(View.GONE);
            selectedGameFactsRow.setVisibility(View.VISIBLE);
            selectedGameLastPlayedPill.setText(getString(
                    R.string.playnite_last_played_pill, played));
            selectedGamePlaytimePill.setText(getString(
                    R.string.playnite_playtime_pill, playtime));
            selectedGamePlaytimePill.setContentDescription(
                    getString(R.string.playnite_playtime) + ": " + playtime);
            GameAgeRating rating = item.game.ageRating;
            selectedGameRatingPill.setVisibility(rating == null
                    || selectedProfileIsChild(selectedHostUuid) ? View.GONE : View.VISIBLE);
            if (rating != null) {
                selectedGameRatingPill.setText(GameAgeRating.chip(rating));
                selectedGameRatingPill.setContentDescription(rating.systemText());
                ((GradientDrawable) selectedGameRatingPill.getBackground()).setStroke(dp(1),
                        GameAgeRating.color(rating));
            }
        }
        ComputerDetails host = currentHost(selectedHostUuid);
        if (host != null) {
            showSelectedGameStatus(tileBadge(host.uuid, item, playniteSessionState(item),
                    gameOperationsController.presentation(host.uuid, item.game),
                    childEligibilityProjection(host.uuid, selectedProfileId(host.uuid)).blocked));
        }
        bindChildFilterInfo(item.game);
        bindGameMetadataHeader(selectedGameMetadata, selectedGameTitle,
                selectedGameFacts, item.game.name, facts);
        bindGameMetadataHeader(expandedLibrary, expandedGameTitle,
                expandedGameFacts, item.game.name, facts);
    }

    /**
     * Mockup 23g: under the focused game's facts while the "For child" filter is on, its rating
     * and what it is for that child, in the sharing panel's words.
     */
    private void bindChildFilterInfo(PlayniteLibraryGame game) {
        if (expandedChildState == null) return;
        ChildLibraryFilter.Selection filter = selectedHostUuid == null ? null
                : childLibraryFilter.selection(childLibraryKey(selectedHostUuid));
        expandedChildState.setVisibility(filter == null ? View.GONE : View.VISIBLE);
        if (filter == null) return;
        expandedChildState.setText(ChildGameAccess.filterInfo(this, game.ageRating,
                filter.child.state(game), filter.child.ageLimit));
    }

    /**
     * The selected game's full status beside it, in its badge's colour and icon: a pill with a
     * mini bar for work in progress in the carousel, a line in the library. Ready says nothing.
     */
    private void showSelectedGameStatus(ConsoleTileBadge badge) {
        // Every rebind of the focused card (host polls, session refreshes) lands here: the
        // views change only when the status does.
        if (badge.equals(shownSelectedGameBadge)) return;
        shownSelectedGameBadge = badge;
        boolean visible = badge.visible();
        String text = visible ? tileBadgeText(badge) : "";
        int textColor = badge.panelTextColor();
        if (selectedGameStatusPill != null) {
            selectedGameStatusPill.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                selectedGameStatusIcon.setImageResource(badge.kind.icon);
                selectedGameStatusIcon.setImageTintList(
                        android.content.res.ColorStateList.valueOf(textColor));
                selectedGameStatusText.setText(text);
                selectedGameStatusText.setTextColor(textColor);
                selectedGameStatusBar.setVisibility(badge.kind.bar ? View.VISIBLE : View.GONE);
                selectedGameStatusBar.setProgress(badge.progress, reducedMotion);
                GradientDrawable background = gradient(0xD91A2026, 0xE00E1318, 10);
                background.setStroke(dp(1), badge.kind.color);
                selectedGameStatusPill.setBackground(background);
            }
        }
        if (expandedGameStatus != null) {
            expandedGameStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                android.graphics.drawable.Drawable icon = getDrawable(badge.kind.icon).mutate();
                icon.setBounds(0, 0, dp(13), dp(13));
                expandedGameStatus.setCompoundDrawables(icon, null, null, null);
                expandedGameStatus.setCompoundDrawableTintList(
                        android.content.res.ColorStateList.valueOf(textColor));
                expandedGameStatus.setText(text);
                expandedGameStatus.setTextColor(textColor);
            }
        }
    }

    private void prepareGameMetadataTransition() {
        if (instantMetadata()) return;
        prepareMetadataViewsForTransition(selectedGameTitle, selectedGameFactsRow,
                selectedGameDescription);
        prepareMetadataViewsForTransition(expandedGameTitle, expandedGameFacts,
                expandedGameDescription);
    }

    private void prepareMetadataViewsForTransition(View... views) {
        for (View view : views) {
            if (view == null) continue;
            view.animate().cancel();
            view.animate().alpha(0f).translationY(dp(3))
                    .setStartDelay(0L).setDuration(80L).start();
        }
    }

    /** Without motion, or on a Home's first frame, which shows the console's at rest. */
    private boolean instantMetadata() {
        return reducedMotion || suppressInitialCarouselMotion;
    }

    private void showGameMetadataDescription(PlayniteDashboardItem item) {
        if (item == null) return;
        String description = item.game.description.isEmpty()
                ? getString(R.string.playnite_description_unavailable)
                : item.game.description;
        bindGameMetadataDescription(selectedGameDescription, description);
        bindGameMetadataDescription(expandedGameDescription, description);
    }

    private void bindGameMetadataHeader(View panel, TextView title, TextView facts,
                                        String name, String factsText) {
        if (panel == null || title == null || facts == null) return;
        title.setText(name);
        facts.setText(factsText);
        View factsView = panel == selectedGameMetadata
                && selectedGameFactsRow != null
                && selectedGameFactsRow.getVisibility() == View.VISIBLE
                ? selectedGameFactsRow : facts;
        if (panel == selectedGameMetadata && !expandedLibraryMode) {
            panel.setVisibility(View.VISIBLE);
            if (selectedGameTitleRow != null) {
                selectedGameTitleRow.setVisibility(View.VISIBLE);
            }
        }
        title.animate().cancel();
        factsView.animate().cancel();
        if (!instantMetadata()) {
            title.setAlpha(0f);
            title.setTranslationY(dp(4));
            factsView.setAlpha(0f);
            factsView.setTranslationY(dp(3));
        }
        title.animate().alpha(1f).translationY(0f)
                .setDuration(instantMetadata() ? 0L : 150L).start();
        factsView.animate().alpha(1f).translationY(0f)
                .setDuration(instantMetadata() ? 0L : 170L).start();
    }

    private void bindGameMetadataDescription(TextView target, String value) {
        if (target == null) return;
        target.setText(value);
        target.animate().cancel();
        if (!instantMetadata()) {
            target.setAlpha(0f);
            target.setTranslationY(dp(3));
        }
        target.animate().alpha(1f).translationY(0f)
                .setDuration(instantMetadata() ? 0L : 180L).start();
        if (target == expandedGameDescription && expandedDescriptionScroll != null) {
            expandedDescriptionScroll.scrollTo(0, 0);
            startExpandedDescriptionAutoScroll();
        }
    }

    private void bindGameMetadata(View panel, TextView title, TextView facts,
                                  TextView description, String name,
                                  String factsText, String descriptionText) {
        if (panel == null || title == null || facts == null || description == null) return;
        title.setText(name);
        facts.setText(factsText);
        description.setText(descriptionText);
        if (description == expandedGameDescription && expandedDescriptionScroll != null) {
            expandedDescriptionScroll.scrollTo(0, 0);
            startExpandedDescriptionAutoScroll();
        }
        if (panel == selectedGameMetadata && !expandedLibraryMode) {
            panel.setVisibility(View.VISIBLE);
            if (selectedGameTitleRow != null) {
                selectedGameTitleRow.setVisibility(View.VISIBLE);
            }
        }
    }

    private void startExpandedDescriptionAutoScroll() {
        int generation = ++expandedDescriptionScrollGeneration;
        if (!expandedLibraryMode || expandedDescriptionScroll == null) return;
        expandedDescriptionScroll.scrollTo(0, 0);
        mainHandler.postDelayed(() -> advanceExpandedDescriptionAutoScroll(generation),
                1_500L);
    }

    private void advanceExpandedDescriptionAutoScroll(int generation) {
        if (generation != expandedDescriptionScrollGeneration
                || !expandedLibraryMode || expandedDescriptionScroll == null) return;
        View focused = getCurrentFocus();
        if (focused == null || expandedGrid == null
                || !isDescendant(expandedGrid, focused)) return;
        if (expandedDescriptionScroll.canScrollVertically(1)) {
            expandedDescriptionScroll.scrollBy(0, Math.max(1, dp(1)));
            mainHandler.postDelayed(
                    () -> advanceExpandedDescriptionAutoScroll(generation), 48L);
            return;
        }
        if (expandedDescriptionScroll.getScrollY() > 0) {
            mainHandler.postDelayed(
                    () -> {
                        if (generation != expandedDescriptionScrollGeneration
                                || !expandedLibraryMode
                                || expandedDescriptionScroll == null) return;
                        View currentFocus = getCurrentFocus();
                        if (currentFocus == null || expandedGrid == null
                                || !isDescendant(expandedGrid, currentFocus)) return;
                        expandedDescriptionScroll.smoothScrollTo(0, 0);
                        mainHandler.postDelayed(
                                () -> advanceExpandedDescriptionAutoScroll(generation),
                                2_000L);
                    },
                    2_500L);
        }
    }

    private void stopExpandedDescriptionAutoScroll() {
        expandedDescriptionScrollGeneration++;
    }

    private void showLibraryMetadataPlaceholder() {
        if (selectedGameSource != null) selectedGameSource.setVisibility(View.GONE);
        if (selectedGameFactsRow != null) selectedGameFactsRow.setVisibility(View.GONE);
        if (selectedGameFacts != null) selectedGameFacts.setVisibility(View.GONE);
        if (expandedGameStatus != null) expandedGameStatus.setVisibility(View.GONE);
        shownSelectedGameBadge = null;
        bindGameMetadata(selectedGameMetadata, selectedGameTitle,
                selectedGameFacts, selectedGameDescription,
                getString(R.string.playnite_full_library), "", "");
    }

    private void positionCarouselMetadata(View card) {
        if (selectedGameTitleRow == null || card == null
                || !(selectedGameTitleRow.getParent() instanceof FrameLayout)) return;
        FrameLayout stage = (FrameLayout) selectedGameTitleRow.getParent();
        stage.post(() -> {
            if (card.getParent() != appRow || !card.hasFocus()
                    || stage.getWidth() == 0) return;
            int[] cardLocation = new int[2];
            int[] stageLocation = new int[2];
            card.getLocationInWindow(cardLocation);
            stage.getLocationInWindow(stageLocation);
            int left = cardLocation[0] - stageLocation[0]
                    + dp(CAROUSEL_FOCUSED_CARD_WIDTH_DP)
                    + dp(CAROUSEL_CARD_GAP_DP);
            int width = Math.min(dp(430), Math.max(dp(240),
                    stage.getWidth() - left - dp(12)));
            // Focus moves and scroll frames only translate the row; its width (fixed at 430 dp
            // unless the stage's right edge is near) relays it out only when that changes.
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) selectedGameTitleRow.getLayoutParams();
            if (params.width != width) {
                params.width = width;
                selectedGameTitleRow.setLayoutParams(params);
            }
            selectedGameTitleRow.setTranslationX(Math.max(dp(104),
                    Math.min(left, stage.getWidth() - width - dp(12))));
        });
    }

    private void hideCarouselMetadataOutsideCarousel() {
        if (selectedGameTitleRow == null) return;
        selectedGameTitleRow.post(() -> {
            View focus = getCurrentFocus();
            if (focus == null || appRow == null || !isDescendant(appRow, focus)) {
                selectedGameTitleRow.setVisibility(View.INVISIBLE);
                if (focus == null || expandedGrid == null
                        || !isDescendant(expandedGrid, focus)) {
                    libraryTransitionCoordinator.beginTransition("");
                }
            }
        });
    }

    /** "3 days ago" from the Playnite and local play record, or its date when that option is off. */
    private String lastPlayedText(String hostUuid, PlayniteDashboardItem item) {
        long epoch = playActivityEpoch(hostUuid, item);
        if (epoch > 0L && PinnedGames.relativePlayed(preferences)) {
            return RelativeTime.format(getResources(), System.currentTimeMillis() - epoch);
        }
        return formatLastActivity(item.game.lastActivity);
    }

    private String formatLastActivity(String value) {
        long epoch = PlayniteLibraryOrdering.activityEpoch(value);
        if (epoch == Long.MIN_VALUE) return getString(R.string.playnite_never_played);
        return DateFormat.getDateInstance(DateFormat.MEDIUM,
                getResources().getConfiguration().getLocales().get(0))
                .format(new Date(epoch));
    }

    private View findTaggedChild(ViewGroup parent, String tag) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (tag.equals(child.getTag())) return child;
            if (child instanceof ViewGroup) {
                View result = findTaggedChild((ViewGroup) child, tag);
                if (result != null) return result;
            }
        }
        return null;
    }

    private String formatPlayniteTime(long seconds) {
        return PlaynitePlaytimeFormatter.format(seconds, new PlaynitePlaytimeFormatter.Labels(
                getString(R.string.playnite_never_played),
                getString(R.string.playnite_hours_short),
                getString(R.string.playnite_minutes_short)));
    }

    /** The tile badge of a game, from the session, the queue, its operation and its state. */
    private ConsoleTileBadge tileBadge(String hostUuid, PlayniteDashboardItem item,
                                       PlayniteSessionPresentation.State sessionState,
                                       GameOperationsController.Presentation operation,
                                       boolean childBlocked) {
        return ConsoleTileBadge.of(sessionState, isQueuedPlayniteItem(hostUuid, item),
                operation, item.game,
                item.mappingState == PlayniteDashboardItem.MappingState.MAPPED, childBlocked);
    }

    /** A badge's full words, as the side panel and the spoken card description say them. */
    private String tileBadgeText(ConsoleTileBadge badge) {
        if (badge.kind.label == 0) return "";
        String percent = badge.percent();
        String label = getString(badge.kind.label);
        return percent.isEmpty() ? label : label + " " + percent;
    }

    private String playniteInstallKey(String hostUuid, PlayniteDashboardItem item) {
        return playniteInstallKey(hostUuid, item.game.playniteGameId);
    }

    private String playniteInstallKey(String hostUuid, String gameId) {
        return hostUuid + ":" + gameId;
    }

    private boolean isPlayniteInstalling(String hostUuid, PlayniteDashboardItem item) {
        return gameOperationsController.isInstalling(hostUuid, item.game);
    }

    private boolean isPlayniteInstalling(String hostUuid, PlayniteLibraryGame game) {
        return gameOperationsController.isInstalling(hostUuid, game);
    }

    private boolean isPlayniteUninstalling(String hostUuid, PlayniteDashboardItem item) {
        return gameOperationsController.isUninstalling(hostUuid, item.game);
    }

    private void resetPlaynitePoster(ImageView poster, PlayniteDashboardItem item) {
        String artworkTag = playniteArtworkTag(item);
        ResolvedArtwork known = resolvedPlayniteArtwork.get(artworkTag);
        Bitmap inMemory = known == null ? null : playniteBitmapCache.get(known.bitmapKey);
        if (inMemory != null) {
            // Artwork already decoded: show it now instead of the placeholder.
            poster.setTag(R.id.playnite_artwork_key, artworkTag);
            applyPlayniteBitmap(poster, artworkTag, known.kind, inMemory);
            return;
        }
        ImageView backdrop = playnitePosterBackdrop(poster);
        if (backdrop != null) {
            backdrop.setImageDrawable(null);
            backdrop.setVisibility(View.GONE);
        }
        poster.setImageResource(R.drawable.ic_computer);
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        poster.setPadding(dp(CONSOLE_UI_V2 ? 24 : 72),
                dp(CONSOLE_UI_V2 ? 36 : 54),
                dp(CONSOLE_UI_V2 ? 24 : 72),
                dp(CONSOLE_UI_V2 ? 36 : 54));
        poster.setTag(R.id.playnite_artwork_key, playniteArtworkTag(item));
    }

    /**
     * Shows a card poster. The main thread only consults memory (the resolved key and the
     * bitmap LRU); file lookups, cache keys and decoding run on the executor.
     */
    private void loadPlaynitePoster(ComputerDetails host, PlayniteDashboardItem item,
                                    ImageView poster, boolean allowNetwork) {
        ComputerDetails latestHost = currentHost(host.uuid);
        HostProfileKey profileKey = currentPlayniteProfileKey;
        if (latestHost == null || !currentPlayniteProjectionMatches(profileKey)
                || !profileKey.hostId.equalsIgnoreCase(latestHost.uuid)) return;
        PlayniteArtworkSpec spec = playniteCardArtworkSpec(item.game);
        if (!spec.available()) return;
        String expectedTag = playniteArtworkTag(item);
        ResolvedArtwork known = resolvedPlayniteArtwork.get(expectedTag);
        Bitmap inMemory = known == null ? null : playniteBitmapCache.get(known.bitmapKey);
        if (inMemory != null && (spec.kind.equals(known.kind) || !allowNetwork)) {
            applyPlayniteBitmap(poster, expectedTag, known.kind, inMemory);
            if (cardHasFocus(poster)) showPlayniteBackdrop(latestHost, item, poster, allowNetwork);
            return;
        }
        String address = latestHost.activeAddress != null
                ? latestHost.activeAddress.address : null;
        GatewayConnection connection = allowNetwork
                ? hostGatewayStore.loadForHost(latestHost.uuid, address, profileKey.profileId)
                : null;
        executor.execute(() -> {
            ArtworkResult cached = cachedPlayniteArtwork(latestHost.uuid, item, spec);
            if (cached != null && (spec.kind.equals(cached.kind) || !allowNetwork)) {
                deliverPlaynitePoster(profileKey, latestHost, item, poster, expectedTag,
                        cached, allowNetwork);
                return;
            }
            if (connection == null) return;
            try {
                deliverPlaynitePoster(profileKey, latestHost, item, poster, expectedTag,
                        fetchPlayniteArtwork(connection, latestHost.uuid, item, spec), true);
            } catch (IOException ignored) { }
        });
    }

    /** Executor thread: this card's backdrop request ended, art or not (the intro waits). */
    private void settleBackdrop(String expectedTag) {
        mainHandler.post(() -> settledBackdropTag = expectedTag);
    }

    /** Executor thread: decodes into the LRU, then hands the main thread the bitmap. */
    private void deliverPlaynitePoster(HostProfileKey profileKey, ComputerDetails host,
                                       PlayniteDashboardItem item, ImageView poster,
                                       String expectedTag, ArtworkResult result,
                                       boolean allowNetwork) {
        Bitmap bitmap = decodePlayniteArtwork(expectedTag, result);
        if (bitmap == null) return;
        mainHandler.post(() -> {
            if (!currentPlayniteProjectionMatches(profileKey)
                    || !expectedTag.equals(poster.getTag(R.id.playnite_artwork_key))) return;
            updateCardInPlace(() -> applyPlayniteBitmap(poster, expectedTag, result.kind, bitmap));
            if (cardHasFocus(poster)) showPlayniteBackdrop(host, item, poster, allowNetwork);
        });
    }

    /** Artwork arriving after a bind changes only the inside of a fixed-size grid card. */
    private void updateCardInPlace(Runnable update) {
        if (expandedLibraryMode && expandedGrid != null) expandedGrid.updateCardsInPlace(update);
        else update.run();
    }

    /** Executor thread: decodes into the LRU and remembers its key for main-thread lookups. */
    private Bitmap decodePlayniteArtwork(String artworkTag, ArtworkResult result) {
        Bitmap bitmap = cachePlayniteBitmap(result.file);
        if (bitmap != null) {
            resolvedPlayniteArtwork.put(artworkTag,
                    new ResolvedArtwork(playniteBitmapCacheKey(result.file), result.kind));
        }
        return bitmap;
    }

    private boolean cardHasFocus(ImageView poster) {
        return poster.hasFocus() || cardParentHasFocus(poster);
    }

    private static final class ResolvedArtwork {
        final String bitmapKey;
        final String kind;

        ResolvedArtwork(String bitmapKey, String kind) {
            this.bitmapKey = bitmapKey;
            this.kind = kind;
        }
    }

    private void schedulePlayniteArtworkPrefetch(ComputerDetails host,
                                                  List<PlayniteDashboardItem> items) {
        schedulePlayniteArtworkPrefetch(host, items, stableIds(items));
    }

    private void schedulePlayniteArtworkPrefetch(ComputerDetails host,
                                                  List<PlayniteDashboardItem> items,
                                                  Set<String> backdropIds) {
        HostProfileKey profileKey = currentPlayniteProfileKey;
        if (!active || host == null || items.isEmpty()
                || !currentPlayniteProjectionMatches(profileKey)
                || !profileKey.hostId.equalsIgnoreCase(host.uuid)) return;
        StringBuilder signature = new StringBuilder(
                profileKey.cacheKey());
        for (PlayniteDashboardItem item : items) {
            signature.append('|').append(item.stableId()).append(':')
                    .append(playniteCardArtworkSpec(item.game).cacheIdentity());
            if (CONSOLE_UI_V2 && backdropIds.contains(item.stableId())) {
                signature.append(':')
                        .append(PlayniteArtworkSpec.forBackdrop(
                                item.game).cacheIdentity());
            }
        }
        String nextSignature = signature.toString();
        if (nextSignature.equals(playniteArtworkPrefetchSignature)) return;
        cancelPlayniteArtworkPrefetch();
        playniteArtworkPrefetchSignature = nextSignature;
        ComputerDetails latestHost = currentHost(host.uuid);
        if (latestHost == null) return;
        String address = latestHost.activeAddress != null
                ? latestHost.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(latestHost.uuid, address,
                        profileKey.profileId);
        if (connection == null) return;
        int token = playniteArtworkGeneration.incrementAndGet();
        List<PlayniteDashboardItem> snapshot = new ArrayList<>(items);
        for (PlayniteDashboardItem item : snapshot) loadingPosterTags.add(playniteArtworkTag(item));
        int workers = Math.min(PLAYNITE_ARTWORK_PREFETCH_WORKERS, snapshot.size());
        for (int worker = 0; worker < workers; worker++) {
            final int offset = worker;
            Future<?> task = playniteArtworkExecutor.submit(() -> {
                for (int index = offset; index < snapshot.size(); index += workers) {
                    if (Thread.currentThread().isInterrupted()
                            || token != playniteArtworkGeneration.get()
                            || !currentPlayniteProjectionMatches(profileKey)) return;
                    String tag = playniteArtworkTag(snapshot.get(index));
                    prefetchPlayniteArtwork(connection, latestHost, snapshot.get(index),
                            backdropIds.contains(snapshot.get(index).stableId()), token);
                    mainHandler.post(() -> { // After its poster's post; failed ones settle too.
                        if (token == playniteArtworkGeneration.get()) loadingPosterTags.remove(tag);
                    });
                }
            });
            playniteArtworkPrefetchTasks.add(task);
        }
    }

    private void prefetchPlayniteArtwork(GatewayConnection connection,
                                         ComputerDetails host,
                                         PlayniteDashboardItem item,
                                         boolean includeBackdrops, int token) {
        HostProfileKey profileKey = new HostProfileKey(host.uuid, connection.profileId());
        if (!currentPlayniteProjectionMatches(profileKey)) return;
        PlayniteArtworkSpec spec = playniteCardArtworkSpec(item.game);
        if (!spec.available()) return;
        try {
            ArtworkResult result = cachedPlayniteArtwork(host.uuid, item, spec);
            if (result == null || !spec.kind.equals(result.kind)) {
                try {
                    result = fetchPlayniteArtwork(connection, host.uuid, item, spec);
                } catch (IOException primaryFailure) {
                    if (result == null) throw primaryFailure;
                }
            }
            String artworkTag = playniteArtworkTag(item);
            Bitmap bitmap = decodePlayniteArtwork(artworkTag, result);
            ArtworkResult ready = result;
            if (bitmap != null) {
                mainHandler.post(() -> applyPrefetchedPlayniteArtwork(
                        token, profileKey, item, ready, artworkTag, bitmap));
            }
            if (!CONSOLE_UI_V2 || !includeBackdrops) return;
            PlayniteArtworkSpec backdropSpec = PlayniteArtworkSpec.forBackdrop(item.game);
            if (backdropSpec.available()
                    && !backdropSpec.cacheIdentity().equals(spec.cacheIdentity())) {
                ArtworkResult backdrop = cachedPlayniteArtwork(host.uuid, item, backdropSpec);
                if (backdrop == null || !backdropSpec.kind.equals(backdrop.kind)) {
                    try {
                        backdrop = fetchPlayniteArtwork(
                                connection, host.uuid, item, backdropSpec);
                    } catch (IOException primaryFailure) {
                        if (backdrop == null) throw primaryFailure;
                    }
                }
                File readyBackdrop = backdrop.file;
                String backdropKey = playniteBitmapCacheKey(readyBackdrop);
                mainHandler.post(() -> {
                    View card = directChildWithTag(appRow, "playnite:" + item.stableId());
                    if (token == playniteArtworkGeneration.get()
                            && currentPlayniteProjectionMatches(profileKey)
                            && card != null && card.hasFocus()) {
                        showArtwork(readyBackdrop, backdropKey, null);
                    }
                });
            }
        } catch (IOException ignored) { }
    }

    private void cancelPlayniteArtworkPrefetch() {
        playniteArtworkGeneration.incrementAndGet();
        loadingPosterTags.clear();
        synchronized (playniteArtworkPrefetchTasks) {
            for (Future<?> task : playniteArtworkPrefetchTasks) task.cancel(true);
            playniteArtworkPrefetchTasks.clear();
        }
        playniteArtworkPrefetchSignature = null;
    }

    private ArtworkResult cachedPlayniteArtwork(String hostUuid, PlayniteDashboardItem item,
                                                 PlayniteArtworkSpec spec) {
        HostProfileKey key = currentPlayniteProfileKey;
        if (!currentPlayniteProjectionMatches(key)) return null;
        return cachedPlayniteArtwork(key, hostUuid, item, spec);
    }

    private ArtworkResult cachedPlayniteArtwork(HostProfileKey key, String hostUuid,
                                                 PlayniteDashboardItem item,
                                                 PlayniteArtworkSpec spec) {
        if (key == null || !key.hostId.equalsIgnoreCase(hostUuid)
                || !key.equals(selectedProfileKey(hostUuid))) return null;
        File primary = playniteArtworkCache.get(key, item.stableId(),
                spec.kind, spec.version);
        if (primary != null && primary.isFile() && primary.length() > 0) {
            return new ArtworkResult(primary, spec.kind);
        }
        if (!spec.hasFallback()) return null;
        File fallback = playniteArtworkCache.get(key, item.stableId(),
                spec.fallbackKind, spec.fallbackVersion);
        return fallback != null && fallback.isFile() && fallback.length() > 0
                ? new ArtworkResult(fallback, spec.fallbackKind) : null;
    }

    /**
     * The path of a cover's artwork ({@link CoverArtwork}), or null for the standard background;
     * {@code cover}: launch, switch or warm_up. Every lookup records its outcome once.
     */
    private String coverArtworkPath(String hostUuid, String profileId, String gameId,
                                    String cover) {
        if (hostUuid == null || hostUuid.trim().isEmpty()
                || gameId == null || gameId.trim().isEmpty()) return null;
        return coverArtworkPath(CoverArtwork.find(playniteArtworkCache,
                new HostProfileKey(hostUuid, profileId),
                providerGameMetadata(hostUuid, profileId, gameId)), gameId, cover);
    }

    /** Any thread: {@code found}'s path when it can be the splash (its bounds are read). */
    private static String coverArtworkPath(CoverArtwork found, String gameId, String cover) {
        String outcome = found.outcome.name().toLowerCase(Locale.ROOT);
        String path = null;
        if (found.file != null) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(found.file.getAbsolutePath(), bounds);
            if (bounds.outWidth > bounds.outHeight
                    && LoadingArtworkPolicy.canUseAsSplash(bounds.outWidth, bounds.outHeight)) {
                path = found.file.getAbsolutePath();
            } else {
                outcome = "rejected";
            }
        }
        MoonWakerDiagnostics.record(path == null ? "WARN" : "INFO", "android.console",
                "cover.artwork", "outcome", outcome, "kind", cover, "game_id", gameId.trim());
        return path;
    }

    /**
     * A Play's cover artwork, decided once on the UI thread within {@link
     * CoverArtwork#FETCH_WAIT_MS} of this call: the cached background once the disk executor
     * has looked it up; one the library names but this device has not cached yet once it is
     * fetched; else, or at the bound, the standard cover (null). Never decided once the cover
     * is no longer this Play's ({@link #launchCoverGeneration}).
     */
    private void decideLaunchArtwork(PlayIntent intent, Consumer<String> decided) {
        int generation = launchCoverGeneration;
        String gameId = intent.loadingArtworkGameId;
        boolean named = intent.hostId != null && !intent.hostId.trim().isEmpty()
                && gameId != null && !gameId.trim().isEmpty();
        PlayniteLibraryGame game = providerGameMetadata(intent.hostId, intent.profileId, gameId);
        HostProfileKey key = new HostProfileKey(intent.hostId, intent.profileId);
        ComputerDetails host = currentHost(intent.hostId);
        GatewayConnection connection = host == null ? null
                : hostGatewayStore.loadForHost(host.uuid, host.activeAddress == null
                        ? null : host.activeAddress.address, intent.profileId);
        Consumer<String> answer = CoverArtwork.within(mainHandler::postDelayed,
                () -> generation == launchCoverGeneration, decided);
        diskExecutor.execute(() -> {
            CoverArtwork found = CoverArtwork.find(playniteArtworkCache, key, game);
            String cached = named ? coverArtworkPath(found, gameId, "launch") : null;
            if (cached != null || connection == null
                    || found.outcome != CoverArtwork.Outcome.CACHE_MISS) {
                mainHandler.post(() -> answer.accept(cached));
                return;
            }
            PlayniteArtworkSpec spec = PlayniteArtworkSpec.forBackdrop(game);
            playniteArtworkExecutor.execute(() -> {
                String fetched = null;
                try {
                    playniteArtworkCache.fetch(hostGatewayClient, connection, key,
                            game.playniteGameId, spec.kind, spec.version);
                    fetched = named ? coverArtworkPath(CoverArtwork.find(playniteArtworkCache,
                            key, game), gameId, "launch") : null;
                } catch (IOException failed) {
                    // The standard cover at once, not at the bound.
                }
                String result = fetched;
                mainHandler.post(() -> answer.accept(result));
            });
        });
    }

    /**
     * The warm-up transition's game as the loaded library lists it, only when that library is
     * the transition's own host and profile; its title and artwork both come from this item.
     * Otherwise null and the warm-up stays neutral.
     */
    static PlayniteDashboardItem transitionLibraryItem(HostProfileKey loaded,
                                                       List<PlayniteDashboardItem> items,
                                                       LaunchTransitionSpec transition) {
        String gameId = SessionSnapshot.normalize(transition.playniteGameId);
        if (loaded == null || gameId.isEmpty()
                || !loaded.equals(new HostProfileKey(transition.hostId, transition.profileId))) {
            return null;
        }
        for (PlayniteDashboardItem item : items) {
            if (gameId.equals(SessionSnapshot.normalize(item.stableId()))) return item;
        }
        return null;
    }

    private ArtworkResult fetchPlayniteArtwork(GatewayConnection connection,
                                                String hostUuid,
                                                 PlayniteDashboardItem item,
                                                 PlayniteArtworkSpec spec) throws IOException {
        HostProfileKey key = currentPlayniteProfileKey;
        if (!currentPlayniteProjectionMatches(key)) {
            throw new IOException("stale_library_projection");
        }
        return fetchPlayniteArtwork(connection, key, hostUuid, item, spec);
    }

    private ArtworkResult fetchPlayniteArtwork(GatewayConnection connection,
                                                HostProfileKey key,
                                                String hostUuid,
                                                PlayniteDashboardItem item,
                                                PlayniteArtworkSpec spec) throws IOException {
        if (key == null || !key.hostId.equalsIgnoreCase(hostUuid)
                || !key.equals(selectedProfileKey(hostUuid))
                || connection == null || !key.profileId.equals(connection.profileId())) {
            throw new IOException("stale_library_projection");
        }
        try {
            return new ArtworkResult(playniteArtworkCache.fetch(hostGatewayClient, connection,
                    hostUuid, item.stableId(), spec.kind, spec.version), spec.kind);
        } catch (IOException primaryFailure) {
            if (!spec.hasFallback()) throw primaryFailure;
            if (!key.equals(selectedProfileKey(hostUuid))) {
                throw new IOException("stale_library_projection", primaryFailure);
            }
            return new ArtworkResult(playniteArtworkCache.fetch(hostGatewayClient, connection,
                    hostUuid, item.stableId(), spec.fallbackKind, spec.fallbackVersion),
                    spec.fallbackKind);
        }
    }

    private void applyPrefetchedPlayniteArtwork(int token, HostProfileKey profileKey,
                                                 PlayniteDashboardItem item,
                                                 ArtworkResult result, String artworkTag,
                                                 Bitmap bitmap) {
        String hostUuid = profileKey.hostId;
        if (token != playniteArtworkGeneration.get() || !hostUuid.equals(selectedHostUuid)
                || !currentPlayniteProjectionMatches(profileKey)) return;
        View card = directChildWithTag(appRow, "playnite:" + item.stableId());
        if (card == null && expandedGrid != null) {
            card = directChildWithTag(expandedGrid, "playnite:" + item.stableId());
        }
        if (!(card instanceof ViewGroup)) return;
        ImageView poster = (ImageView) findTaggedChild((ViewGroup) card, "playnite.poster");
        if (poster == null || !artworkTag.equals(poster.getTag(R.id.playnite_artwork_key))) {
            return;
        }
        updateCardInPlace(() -> applyPlayniteBitmap(poster, artworkTag, result.kind, bitmap));
        if (card.hasFocus()) showPlayniteBackdrop(currentHost(hostUuid), item, poster, true);
    }

    private View directChildWithTag(ViewGroup parent, Object tag) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (Objects.equals(tag, child.getTag())) return child;
        }
        return null;
    }

    private String playniteArtworkTag(PlayniteDashboardItem item) {
        HostProfileKey key = currentPlayniteProfileKey;
        String profile = currentPlayniteProjectionMatches(key) ? key.profileId : "stale";
        return profile + ":" + item.stableId() + ":"
                + playniteCardArtworkSpec(
                item.game).cacheIdentity();
    }

    private PlayniteArtworkSpec playniteCardArtworkSpec(PlayniteLibraryGame game) {
        return CONSOLE_UI_V2
                ? PlayniteArtworkSpec.forCard(game)
                : PlayniteArtworkSpec.forGame(game);
    }

    private void showPlayniteBackdrop(ComputerDetails host, PlayniteDashboardItem item,
                                      ImageView poster, boolean allowNetwork) {
        HostProfileKey profileKey = currentPlayniteProfileKey;
        if (host == null || !currentPlayniteProjectionMatches(profileKey)
                || !profileKey.hostId.equalsIgnoreCase(host.uuid)) return;
        PlayniteArtworkSpec spec = PlayniteArtworkSpec.forBackdrop(item.game);
        String expectedTag = playniteArtworkTag(item);
        if (!spec.available()) {
            settledBackdropTag = expectedTag;
            return;
        }
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = allowNetwork
                ? hostGatewayStore.loadForHost(host.uuid, address, profileKey.profileId)
                : null;
        executor.execute(() -> {
            ArtworkResult cached = cachedPlayniteArtwork(host.uuid, item, spec);
            if (cached != null) postCardBackdrop(profileKey, poster, expectedTag, cached.file);
            if (connection != null && (cached == null || !spec.kind.equals(cached.kind))) {
                try {
                    postCardBackdrop(profileKey, poster, expectedTag,
                            fetchPlayniteArtwork(connection, host.uuid, item, spec).file);
                } catch (IOException ignored) { }
            }
            settleBackdrop(expectedTag); // After the art's own post, which the intro then sees.
        });
    }

    /** Executor thread: keys the backdrop file, then shows it if its card still has focus. */
    private void postCardBackdrop(HostProfileKey profileKey, ImageView poster,
                                  String expectedTag, File file) {
        String key = playniteBitmapCacheKey(file);
        mainHandler.post(() -> {
            if (currentPlayniteProjectionMatches(profileKey)
                    && expectedTag.equals(poster.getTag(R.id.playnite_artwork_key))
                    && cardHasFocus(poster)) {
                showArtwork(file, key, poster.getDrawable());
            }
        });
    }

    private boolean cardParentHasFocus(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (((View) parent).hasFocus()) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private Bitmap cachePlayniteBitmap(File file) {
        if (file == null || !file.isFile()) return null;
        String key = playniteBitmapCacheKey(file);
        Bitmap cached = playniteBitmapCache.get(key);
        if (cached != null) return cached;
        Object decodeLock = playniteBitmapDecodeLocks.computeIfAbsent(key,
                ignored -> new Object());
        try {
            synchronized (decodeLock) {
                cached = playniteBitmapCache.get(key);
                if (cached != null) return cached;
                boolean landscape = file.getName().contains("_background_");
                Bitmap decoded = decodeSampledBitmap(file,
                        landscape ? 512 : 256, landscape ? 288 : 344);
                if (decoded != null) {
                    float scale;
                    if (landscape || portraitLayout) {
                        int maxWidth = landscape ? 512 : 256;
                        int maxHeight = landscape ? 288 : 344;
                        scale = Math.min(1f, Math.min(maxWidth / (float) decoded.getWidth(),
                                maxHeight / (float) decoded.getHeight()));
                    } else {
                        // TV posters fill a 92x123 dp card at most: keep just enough pixels
                        // to cover it (no upscaling, so artwork never looks low-resolution).
                        scale = Math.min(1f, Math.max(
                                dp(CAROUSEL_FOCUSED_CARD_WIDTH_DP) / (float) decoded.getWidth(),
                                dp(CAROUSEL_FOCUSED_CARD_HEIGHT_DP)
                                        / (float) decoded.getHeight()));
                    }
                    if (scale < .999f) {
                        Bitmap scaled = Bitmap.createScaledBitmap(decoded,
                                Math.max(1, Math.round(decoded.getWidth() * scale)),
                                Math.max(1, Math.round(decoded.getHeight() * scale)), true);
                        if (scaled != decoded) decoded.recycle();
                        decoded = scaled;
                    }
                    CardBackdropBlur.prepare(decoded);
                    playniteBitmapCache.put(key, decoded);
                }
                return decoded;
            }
        } finally {
            playniteBitmapDecodeLocks.remove(key, decodeLock);
        }
    }

    /**
     * A Playnite artwork file's bitmap key: its path, which names its profile, game, kind and
     * version digest ({@link PlayniteArtworkCache}); a file is published once under it, never
     * rewritten, so the key needs no stat.
     */
    private static String playniteBitmapCacheKey(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private void applyPlayniteBitmap(ImageView poster, String expectedTag,
                                     String kind, Bitmap bitmap) {
        Object expected = poster.getTag(R.id.playnite_artwork_key);
        if (bitmap == null || !expectedTag.equals(expected)) return;
        Drawable shown = poster.getDrawable();
        if (shown instanceof BitmapDrawable && ((BitmapDrawable) shown).getBitmap() == bitmap) {
            return;
        }
        poster.setPadding(0, 0, 0, 0);
        boolean landscape = "background".equals(kind);
        int targetWidth = poster.getWidth() > 0 ? poster.getWidth() : dp(92);
        int targetHeight = poster.getHeight() > 0 ? poster.getHeight() : dp(123);
        boolean modestTileCrop = CONSOLE_UI_V2
                && LoadingArtworkPolicy.canFillWithModestCrop(
                bitmap.getWidth(), bitmap.getHeight(), targetWidth, targetHeight);
        boolean lowResolution = bitmap.getWidth() < targetWidth
                || bitmap.getHeight() < targetHeight;
        poster.setScaleType(lowResolution ? ImageView.ScaleType.CENTER_INSIDE
                : modestTileCrop || landscape && !CONSOLE_UI_V2
                ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        BitmapDrawable drawable = filteredBitmapDrawable(bitmap);
        ImageView backdrop = playnitePosterBackdrop(poster);
        if (backdrop != null) {
            if (lowResolution || modestTileCrop || landscape && !CONSOLE_UI_V2) {
                backdrop.setImageDrawable(null);
                backdrop.setVisibility(View.GONE);
            } else {
                backdrop.setImageDrawable(filteredBitmapDrawable(
                        CardBackdropBlur.get(bitmap)));
                backdrop.setVisibility(View.VISIBLE);
            }
        }
        poster.setImageDrawable(drawable);
    }

    private BitmapDrawable filteredBitmapDrawable(Bitmap bitmap) {
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setFilterBitmap(true);
        drawable.setDither(true);
        drawable.setAntiAlias(true);
        return drawable;
    }

    private ImageView playnitePosterBackdrop(ImageView poster) {
        ViewParent parent = poster.getParent();
        if (!(parent instanceof ViewGroup)) return null;
        View value = findTaggedChild((ViewGroup) parent, "playnite.poster.backdrop");
        return value instanceof ImageView ? (ImageView) value : null;
    }

    private static Bitmap decodeSampledBitmap(File file, int requestedWidth,
                                              int requestedHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= requestedWidth &&
                bounds.outHeight / (sample * 2) >= requestedHeight) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inDither = true;
        options.inScaled = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static final class ArtworkResult {
        final File file;
        final String kind;

        ArtworkResult(File file, String kind) {
            this.file = file;
            this.kind = kind;
        }
    }

    private void showPlayniteGameActions(ComputerDetails host,
                                         PlayniteDashboardItem item) {
        if (host == null || item == null) return;
        SessionSnapshot session = resolveSessionSnapshot(host);
        String closing = closingStatus(session);
        if (closing != null) {
            showSidePanelBusy(getString(R.string.console_apps_eyebrow), item.game.name, closing);
            return;
        }
        GameOperationsController.Presentation operation =
                gameOperationsController.presentation(host.uuid, item.game);
        HostGatewayClient.IntegrationProfile selectedProfile =
                hostGatewayStore.profileSelection(host.uuid).selected;
        ConsoleGameCard.Facts facts = new ConsoleGameCard.Facts();
        facts.installed = item.game.installed;
        facts.canLaunch = item.game.canLaunch && !operation.launchBlocked;
        facts.canInstall = item.game.canInstall;
        facts.canUninstall = item.game.canUninstall;
        facts.installing = operation.installActive;
        facts.canUpdate = item.game.canInstall && item.game.updateRequired()
                && !item.game.updateActive() && !operation.installActive
                && !operation.uninstallActive;
        facts.installNeedsAttention = operation.attentionRequired;
        facts.plusRequired = hostPlus(host.uuid) ? null : getString(R.string.host_plus_required);
        facts.attentionOnPc = "secure_desktop".equals(operation.attentionReason);
        facts.gameRunning = isFreshExactRunningManagedGame(host, item);
        facts.sessionActive = session.state == SessionSnapshot.State.ACTIVE
                && SessionSnapshot.normalize(item.stableId()).equals(session.playniteGameId);
        facts.frozen = !session.frozenGameId().isEmpty()
                && session.frozenGameId().equals(SessionSnapshot.normalize(item.stableId()));
        facts.childSharing = HostGatewayClient.isPlayniteId(item.game.playniteGameId)
                && childManagementProfileAllowed(host, selectedProfile)
                && childCapabilityAllowed(host, selectedProfile)
                && hasOwnChildren(host, selectedProfile);
        // Only children child management already loaded (behind the parent PIN) are summed up.
        facts.childSharingSummary = facts.childSharing ? ChildLibraryFilter.sharingSummary(this,
                childLibraryFilter.children(childLibraryKey(host.uuid)), item.game) : null;
        facts.autopilotAvailable = ConsoleActionCatalog.isOnline(host)
                && ConsoleActionCatalog.isPaired(host);
        facts.hidden = locallyHiddenPlayniteGames(host.uuid).contains(item.game.playniteGameId);
        facts.pinsEnabled = PinnedGames.pinsEnabled(preferences);
        facts.pinned = PinnedGames.ids(preferences, host.uuid, selectedProfileId(host.uuid))
                .contains(item.game.playniteGameId);
        String panelDetails = formatPlayniteTime(item.game.playtimeSeconds) + " \u00B7 "
                + getString(R.string.playnite_last_played_pill,
                lastPlayedText(host.uuid, item))
                + (item.game.source.isEmpty() ? "" : " \u00B7 " + item.game.source);
        if (operation.attentionRequired) {
            String prompt = !operation.attentionWindowTitle.isEmpty()
                    ? operation.attentionWindowTitle : operation.attentionLauncher;
            panelDetails = getString(facts.attentionOnPc
                            ? R.string.playnite_install_attention_details_pc
                            : R.string.playnite_install_attention_details,
                    prompt.isEmpty() ? item.game.name : prompt) + "\n" + panelDetails;
        }
        CharSequence details = panelDetails;
        if (!selectedProfileIsChild(host.uuid)) {
            // Mockup 23d: the parent sees the rating under the facts (a child's library has none).
            details = new android.text.SpannableStringBuilder(panelDetails).append('\n')
                    .append(GameAgeRating.summary(this, item.game.ageRating));
        }
        String streamSettingsKey = AppPreferences.playniteGameKey(host.uuid, item.stableId());
        showSidePanel(getString(R.string.playnite_game_options), item.game.name, details,
                ConsoleGameCard.menu(facts).rows(this, consolePanel, a -> a.icon, action -> {
                    switch (action) {
                        case LAUNCH:
                        case RESUME:
                        case RESUME_FROZEN:
                            // A frozen game's Play thaws it (SessionOrchestrator.startThaws).
                            hideSidePanel();
                            activatePlayniteItem(host.uuid, item);
                            break;
                        case CLOSE_FROZEN: confirmCloseFrozenGame(host, item); break;
                        case CONTINUE_INSTALL:
                            hideSidePanel();
                            continuePlayniteInstallation(host, item);
                            break;
                        case INSTALL:
                            hideSidePanel();
                            startPlayniteInstallation(host, item);
                            break;
                        case UPDATE:
                            hideSidePanel();
                            startPlayniteUpdate(host, item);
                            break;
                        case END_GAME: confirmEndPlayniteGame(host, item); break;
                        case CLOSE_GAME_AND_STREAM:
                            confirmTerminateSession(host, item.stableId());
                            break;
                        case STREAM_SETTINGS:
                            hideSidePanel();
                            new ConsoleAppStreamSettings(this, settingsScreen)
                                    .withGameMode(host.uuid, selectedProfileId(host.uuid),
                                            selectedProfileIsChild(host.uuid))
                                    .open(getString(R.string.console_game_card),
                                            streamSettingsKey, item.game.name, true,
                                            item.sunshineAppId == null ? null
                                                    : host.uuid + ":" + item.sunshineAppId);
                            break;
                        case OPTIMIZE_STREAMING:
                            NvApp target = PlayniteTargetResolver.findById(
                                    currentSunshineApps, item.sunshineAppId);
                            requestStreamingAutopilot(host, streamSettingsKey,
                                    item.sunshineAppId == null ? null
                                            : host.uuid + ":" + item.sunshineAppId,
                                    item.game.name, item.game.genres, PlayIntent.providerGame(host.uuid,
                                            selectedProfileId(host.uuid),
                                            item.sunshineAppId == null ? 0 : item.sunshineAppId,
                                            item.game.name,
                                            target != null && target.isHdrSupported(),
                                            item.game, streamSettingsKey));
                            break;
                        case SHARE_WITH_CHILD: beginChildSharing(host, item); break;
                        case PIN:
                        case UNPIN:
                            hideSidePanel();
                            PinnedGames.set(preferences, host.uuid, selectedProfileId(host.uuid),
                                    item.game.playniteGameId, action == ConsoleGameCard.Action.PIN);
                            ComputerDetails pinHost = currentHost(host.uuid);
                            if (pinHost != null) renderPlayniteLibrary(pinHost, currentSunshineApps);
                            break;
                        case HIDE:
                        case SHOW:
                            hideSidePanel();
                            setPlayniteGameHidden(host.uuid, item.game.playniteGameId,
                                    action == ConsoleGameCard.Action.HIDE);
                            ComputerDetails current = currentHost(host.uuid);
                            if (current != null) renderPlayniteLibrary(current, currentSunshineApps);
                            break;
                        case DETAILS: showPlayniteGameDetails(item); break;
                        case UNINSTALL: confirmPlayniteUninstall(host, item); break;
                        case INSTALLING: break;
                    }
                }));
    }

    private void confirmEndPlayniteGame(ComputerDetails host,
                                        PlayniteDashboardItem item) {
        HostProfileKey confirmedKey = selectedProfileKey(host.uuid);
        if (!isFreshExactRunningManagedGame(host, item)) return;
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView endGame = consolePanel.action(getString(R.string.overlay_menu_end_game_confirm));
        consolePanel.destructive(endGame);
        cancel.setOnClickListener(view -> handlePanelBack());
        endGame.setOnClickListener(view -> {
            hideSidePanelImmediately();
            requestEndPlayniteGame(host, item, confirmedKey);
        });
        showSidePanel(getString(R.string.playnite_game_options), item.game.name,
                getString(R.string.playnite_end_game_confirmation), cancel, endGame);
    }

    private void confirmCloseFrozenGame(ComputerDetails host, PlayniteDashboardItem item) {
        HostProfileKey confirmedKey = selectedProfileKey(host.uuid);
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView close = consolePanel.action(getString(R.string.console_frozen_close));
        consolePanel.destructive(close);
        cancel.setOnClickListener(view -> handlePanelBack());
        close.setOnClickListener(view -> {
            hideSidePanelImmediately();
            closeFrozenGame(host, item, confirmedKey);
        });
        showSidePanel(getString(R.string.playnite_game_options), item.game.name,
                getString(R.string.console_frozen_close_confirmation), cancel, close);
    }

    /**
     * Thaws and closes the frozen game through the host (it needs no stream). Every answer
     * ends on the library with the host read again: the tile then shows what is true.
     */
    private void closeFrozenGame(ComputerDetails host, PlayniteDashboardItem item,
                                 HostProfileKey confirmedKey) {
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection gateway = hostGatewayStore.loadForHost(host.uuid, address,
                confirmedKey.profileId);
        String gameId = item.game.playniteGameId;
        executor.execute(() -> {
            int message;
            try {
                if (gateway == null) throw new IOException("gateway_unavailable");
                HostGatewayClient.QuickResumeAnswer answer =
                        hostGatewayClient.closeFrozenGame(gateway, gameId);
                message = answer.accepted || "game_not_frozen".equals(answer.reason)
                        ? R.string.console_frozen_closed
                        : "game_stop_unconfirmed".equals(answer.reason)
                        ? R.string.console_frozen_close_unconfirmed
                        : R.string.console_frozen_close_failed;
            } catch (IOException | RuntimeException failed) {
                message = R.string.console_frozen_close_failed;
            }
            int shown = message;
            mainHandler.post(() -> {
                ConsoleUiFeedback.makeText(this, shown, Toast.LENGTH_LONG).show();
                if (!confirmedKey.equals(selectedProfileKey(host.uuid))
                        || !host.uuid.equalsIgnoreCase(selectedHostUuid)) return;
                invalidateSessionProjectionRequest(host.uuid);
                refreshSessionProjection(host, ignored -> { });
            });
        });
    }

    private void requestEndPlayniteGame(ComputerDetails host,
                                        PlayniteDashboardItem item,
                                        HostProfileKey confirmedKey) {
        String expectedGameId = SessionSnapshot.normalize(item.stableId());
        if (host == null || confirmedKey == null
                || !confirmedKey.equals(selectedProfileKey(host.uuid))
                || !host.uuid.equalsIgnoreCase(selectedHostUuid)
                || expectedGameId.isEmpty() || !isFreshExactRunningManagedGame(host, item)) {
            ConsoleUiFeedback.makeText(this, R.string.overlay_menu_end_game_failed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        // The host status shows the stop ("Ending game…") and greys the games; a live stream
        // stays open to return to. No progress panel.
        String executionProfile = executionProfileId(host.uuid, confirmedKey.profileId);
        sessionModel.dispatch(new SessionEvent.LocalCloseBegun(host.uuid,
                confirmedKey.profileId, executionProfile, true), closeToken -> {
            queueSessionPresentationUpdate();
            executor.execute(() -> endPlayniteGame(host, confirmedKey, expectedGameId,
                    executionProfile, closeToken));
        });
    }

    private void endPlayniteGame(ComputerDetails host, HostProfileKey confirmedKey,
                                 String expectedGameId,
                                 String executionProfile, long closeToken) {
        boolean success = false;
        try {
            if (!confirmedKey.equals(selectedProfileKey(host.uuid)))
                throw new IOException("game_stop_profile_changed");
            stopActiveProviderGame(host, expectedGameId, true, confirmedKey.profileId);
            success = true;
        } catch (IOException | RuntimeException ignored) {
        } finally {
            sessionModel.post(new SessionEvent.LocalCloseFinished(host.uuid,
                    confirmedKey.profileId, executionProfile, closeToken, success));
        }
        boolean stopped = success;
        mainHandler.post(() -> {
            queueSessionPresentationUpdate();
            if (!confirmedKey.equals(selectedProfileKey(host.uuid))
                    || !host.uuid.equalsIgnoreCase(selectedHostUuid)) return;
            if (stopped) {
                invalidateSessionProjectionRequest(host.uuid);
                renderPlayniteLibrary(currentHost(host.uuid), currentSunshineApps);
                refreshSessionState(host.uuid);
            }
            refreshSessionProjection(host, ignored -> { });
            ConsoleUiFeedback.makeText(this, stopped ? R.string.playnite_end_game_success
                    : R.string.overlay_menu_end_game_failed, Toast.LENGTH_LONG).show();
        });
    }

    private void restoreCachedPlaynitePoster(ComputerDetails host,
                                             PlayniteDashboardItem item,
                                             ImageView poster) {
        if (host == null || poster == null) return;
        PlayniteArtworkSpec spec = playniteCardArtworkSpec(item.game);
        String expectedTag = playniteArtworkTag(item);
        // Memory only here; the cache files are looked up and decoded on the disk executor.
        ResolvedArtwork known = resolvedPlayniteArtwork.get(expectedTag);
        // Any kind memory holds (the card's fallback too) shows at once, as without network.
        Bitmap bitmap = known == null ? null : playniteBitmapCache.get(known.bitmapKey);
        if (bitmap != null) {
            applyPlayniteBitmap(poster, expectedTag, known.kind, bitmap);
        } else if (suppressInitialCarouselMotion
                && initialCarouselArtworkLoads.add(expectedTag)) {
            int generation = initialCarouselArtworkGeneration;
            diskExecutor.execute(() -> {
                ArtworkResult cached = cachedPlayniteArtwork(host.uuid, item, spec);
                Bitmap decoded = cached == null ? null
                        : decodePlayniteArtwork(expectedTag, cached);
                mainHandler.post(() -> {
                    if (generation != initialCarouselArtworkGeneration
                            || !initialCarouselArtworkLoads.remove(expectedTag)) return;
                    if (decoded != null) {
                        applyPlayniteBitmap(poster, expectedTag, cached.kind, decoded);
                    }
                    if (appRow != null) appRow.postInvalidateOnAnimation();
                });
            });
        }
    }

    private void resetInitialCarouselArtworkWarmup() {
        initialCarouselArtworkGeneration++;
        initialCarouselArtworkLoads.clear();
    }

    private void showPlayniteGameDetails(PlayniteDashboardItem item) {
        String source = item.game.source.isEmpty()
                ? getString(R.string.playnite_metadata_unknown) : item.game.source;
        String genres = item.game.genres.isEmpty()
                ? getString(R.string.playnite_metadata_unknown) : item.game.genres;
        String description = item.game.description.isEmpty()
                ? getString(R.string.playnite_description_unavailable) : item.game.description;
        String body = getString(R.string.playnite_game_details_body,
                item.game.installed ? getString(R.string.playnite_installed)
                        : getString(R.string.playnite_uninstalled),
                formatLastActivity(item.game.lastActivity),
                formatPlayniteTime(item.game.playtimeSeconds), source, genres, description);
        TextView back = consolePanel.action(getString(R.string.console_back_close));
        back.setTag("panel.back");
        back.setOnClickListener(view -> handlePanelBack());
        showScrollableDetailsSidePanel(getString(R.string.playnite_game_details), item.game.name,
                body, back);
        animateDetailsPanelIn();
    }

    private void animateDetailsPanelIn() {
        if (sidePanelScroll == null) return;
        if (reducedMotion) {
            sidePanelScroll.animate().cancel();
            sidePanelScroll.setAlpha(1f);
            sidePanelScroll.setScaleX(1f);
            sidePanelScroll.setScaleY(1f);
            sidePanelScroll.setTranslationX(0f);
            resetDetailsPanelChildren();
            return;
        }
        sidePanelScroll.post(() -> {
            if (sidePanelScroll == null || sideDialog == null || !sideDialog.isShowing()) return;
            sidePanelScroll.animate().cancel();
            float pivotY = sidePanelScroll.getHeight() / 2f;
            if (lastContentFocus != null && lastContentFocus.isAttachedToWindow()) {
                int[] source = new int[2];
                int[] panel = new int[2];
                lastContentFocus.getLocationOnScreen(source);
                sidePanelScroll.getLocationOnScreen(panel);
                pivotY = Math.max(0f, Math.min(sidePanelScroll.getHeight(),
                        source[1] + lastContentFocus.getHeight() / 2f - panel[1]));
            }
            sidePanelScroll.setPivotX(0f);
            sidePanelScroll.setPivotY(pivotY);
            sidePanelScroll.setAlpha(.28f);
            sidePanelScroll.setScaleX(.91f);
            sidePanelScroll.setScaleY(.91f);
            sidePanelScroll.setTranslationX(dp(42));
            prepareDetailsPanelChildren();
            sidePanelScroll.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .translationX(0f).setDuration(260L).start();
            animateDetailsPanelChildrenIn();
        });
    }

    private void prepareDetailsPanelChildren() {
        if (sidePanel == null) return;
        for (int index = 0; index < sidePanel.getChildCount(); index++) {
            View child = sidePanel.getChildAt(index);
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(dp(7));
        }
    }

    private void animateDetailsPanelChildrenIn() {
        if (sidePanel == null) return;
        for (int index = 0; index < sidePanel.getChildCount(); index++) {
            View child = sidePanel.getChildAt(index);
            child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(65L + index * 32L)
                    .setDuration(170L).start();
        }
    }

    private void resetDetailsPanelChildren() {
        if (sidePanel == null) return;
        for (int index = 0; index < sidePanel.getChildCount(); index++) {
            View child = sidePanel.getChildAt(index);
            child.animate().cancel();
            child.setAlpha(1f);
            child.setTranslationY(0f);
        }
    }

    private Set<String> locallyHiddenPlayniteGames(String hostUuid) {
        if (hostUuid == null) return Collections.emptySet();
        return new HashSet<>(preferences.getStringSet(
                "playnite_hidden." + hostUuid, Collections.emptySet()));
    }

    private void setPlayniteGameHidden(String hostUuid, String gameId, boolean hidden) {
        Set<String> values = locallyHiddenPlayniteGames(hostUuid);
        if (hidden) values.add(gameId);
        else values.remove(gameId);
        preferences.edit().putStringSet("playnite_hidden." + hostUuid, values).apply();
    }

    private void activatePlayniteItem(String hostUuid, PlayniteDashboardItem item) {
        ComputerDetails host = currentHost(hostUuid);
        if (host == null) {
            ConsoleUiFeedback.makeText(this, R.string.error_pc_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        // The tile already shows the child's block (hours, limit): a popup, never a cover.
        ChildEligibilityProjection childPolicy = childEligibilityProjection(
                hostUuid, selectedProfileId(hostUuid));
        if (childPolicy.blocked) {
            ConsoleUiFeedback.makeText(this, childLaunchFailureMessage(childPolicy.reason),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        GameOperationsController.Presentation operation =
                gameOperationsController.presentation(hostUuid, item.game);
        if (operation.attentionRequired) {
            continuePlayniteInstallation(host, item);
            return;
        }
        if (operation.launchBlocked) {
            ConsoleUiFeedback.makeText(this, getString(R.string.playnite_install_in_progress,
                    item.game.name), Toast.LENGTH_SHORT).show();
            return;
        }
        List<NvApp> apps = currentSunshineApps;
        if (!item.game.installed) {
            if (!item.game.canInstall) {
                ConsoleUiFeedback.makeText(this, R.string.playnite_launch_unavailable,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            startPlayniteInstallation(host, item);
            return;
        }
        if (!item.game.canLaunch) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_launch_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        playPlayniteGame(host, PlayniteTargetResolver.resolveNeutralStream(apps), item.game,
                playniteSessionState(item) == PlayniteSessionPresentation.State.READY);
    }

    /** Host Plus features the host would refuse are shown off with this note, never tried. */
    private boolean hostPlus(String hostId) {
        return !Boolean.FALSE.equals(hostPlusByHost.get(hostId));
    }

    /** Whether the host can save screenshots and clips; Settings and the stream both ask. */
    private boolean captures(String hostId) {
        return !Boolean.FALSE.equals(capturesByHost.get(hostId));
    }

    private boolean refuseWithoutHostPlus(String hostId) {
        if (hostPlus(hostId)) return false;
        ConsoleUiFeedback.makeText(this, R.string.host_plus_required, Toast.LENGTH_LONG).show();
        return true;
    }

    private void startPlayniteUpdate(ComputerDetails host, PlayniteDashboardItem item) {
        if (host == null || item == null || !item.game.canInstall) return;
        if (refuseWithoutHostPlus(host.uuid)) return;
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        gameOperationsController.requestUpdate(host.uuid, item.game, connection, success -> {
            if (success) {
                if (active && host.uuid.equals(selectedHostUuid)) {
                    requestPlayniteRefresh(currentHost(host.uuid), false);
                }
            } else {
                if (consoleAudioEngine != null) {
                    consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.ERROR);
                }
                ConsoleUiFeedback.makeText(this, getString(R.string.playnite_update_failed,
                        item.game.name), Toast.LENGTH_LONG).show();
            }
        });
        ConsoleUiFeedback.makeText(this, getString(R.string.playnite_update_starting,
                item.game.name), Toast.LENGTH_LONG).show();
    }

    private void startPlayniteInstallation(ComputerDetails host,
                                           PlayniteDashboardItem item) {
        if (host == null || item == null || !item.game.canInstall) return;
        if (refuseWithoutHostPlus(host.uuid)) return;
        if (hasPlayniteOperationAwaitingConfirmation()) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_operation_waiting,
                    Toast.LENGTH_LONG).show();
            return;
        }
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, address,
                        selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        String launcher = InstallLocations.launcher(item.game);
        if (launcher == null) {
            beginPlayniteInstallation(host, item, connection, null, null);
            return;
        }
        showSidePanelBusy(getString(R.string.install_location_title), item.game.name,
                getString(R.string.install_location_loading));
        loadInstallTargets(connection, launcher, targets -> {
            if (sideDialog == null || !sideDialog.isShowing()) return;
            showInstallLocationPicker(host, item, connection, launcher, targets);
        });
    }

    /** {@code onLoaded} gets null when the host could not list locations. */
    private void loadInstallTargets(GatewayConnection connection, String launcher,
                                    Consumer<List<InstallLocations.Target>> onLoaded) {
        executor.execute(() -> {
            List<InstallLocations.Target> targets;
            try {
                targets = InstallLocations.parse(
                        hostGatewayClient.installTargets(connection), launcher);
            } catch (IOException | RuntimeException error) {
                targets = null;
            }
            List<InstallLocations.Target> result = targets;
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()) onLoaded.accept(result);
            });
        });
    }

    private String installLocationLabel(InstallLocations.Target target) {
        return getString(R.string.install_location_row, target.path,
                android.text.format.Formatter.formatShortFileSize(this, target.freeBytes));
    }

    /** Mockups 23a/23b: the game's cover and size, then one row per drive with its bar. */
    private void showInstallLocationPicker(ComputerDetails host, PlayniteDashboardItem item,
                                           GatewayConnection connection, String launcher,
                                           List<InstallLocations.Target> targets) {
        List<InstallLocations.Target> listed =
                targets == null ? Collections.emptyList() : targets;
        ConsoleDiskSpaceViews views = new ConsoleDiskSpaceViews(this);
        List<ConsoleDiskSpaceViews.TargetRow> rows = new ArrayList<>();
        List<View> actions = new ArrayList<>();
        InstallLocations.Target hostTarget = null;
        for (InstallLocations.Target target : listed) {
            if (target.hostDefault) hostTarget = target;
        }
        rows.add(views.targetRow(consolePanel,
                getString(R.string.install_location_host_default),
                hostTarget == null ? null : hostTarget.path, hostTarget));
        rows.get(0).view.setOnClickListener(view -> {
            hideSidePanel();
            beginPlayniteInstallation(host, item, connection, launcher, "");
        });
        for (InstallLocations.Target target : listed) {
            ConsoleDiskSpaceViews.TargetRow row =
                    views.targetRow(consolePanel, target.path, null, target);
            row.view.setOnClickListener(view -> {
                hideSidePanel();
                beginPlayniteInstallation(host, item, connection, launcher, target.path);
            });
            rows.add(row);
        }
        for (ConsoleDiskSpaceViews.TargetRow row : rows) actions.add(row.view);
        ConsoleDiskSpaceViews.GameHeader header = views.gameHeader(item.game.libraryName);
        loadCover(host, item, header.view, header::setCover);
        // Legendary may download the manifest first: the picker does not wait for the size.
        executor.execute(() -> {
            long size;
            try {
                size = hostGatewayClient.installSize(connection, item.game.playniteGameId);
            } catch (IOException | RuntimeException unknown) {
                return; // the picker works without it, only the space warning is missing
            }
            mainHandler.post(() -> {
                if (!header.view.isAttachedToWindow()) return; // another panel by now
                header.setGameSize(size);
                for (ConsoleDiskSpaceViews.TargetRow row : rows) row.setGameSize(size);
            });
        });
        List<View> shown = new ArrayList<>();
        shown.add(header.view);
        shown.addAll(actions);
        showSidePanel(getString(R.string.install_location_title), item.game.name,
                targets == null ? getString(R.string.install_location_unavailable)
                        : getString(R.string.install_location_details, item.game.name),
                shown.toArray(new View[0]));
        View focus = actions.get(InstallLocations.preselected(listed,
                InstallLocations.defaultPath(preferences, host.uuid, launcher),
                InstallLocations.lastPath(preferences, host.uuid, launcher)));
        focus.post(focus::requestFocus);
    }

    /**
     * A game's card cover, called on the UI thread: the artwork cache, else fetched from the
     * host like a library tile's (a game far down the library was never fetched). Skipped
     * once {@code owner} left the window (Back, another page) before its turn.
     */
    private void loadCover(ComputerDetails host, PlayniteDashboardItem item, View owner,
                           Consumer<Bitmap> shown) {
        if (host == null || item == null) return;
        PlayniteArtworkSpec spec = playniteCardArtworkSpec(item.game);
        if (!spec.available()) return;
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid));
        AtomicBoolean left = new AtomicBoolean();
        owner.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) { }

            @Override
            public void onViewDetachedFromWindow(View view) {
                left.set(true);
            }
        });
        coverExecutor.execute(() -> {
            if (left.get()) return;
            ArtworkResult cover = cachedPlayniteArtwork(host.uuid, item, spec);
            if (cover == null && connection != null) {
                try {
                    cover = fetchPlayniteArtwork(connection, host.uuid, item, spec);
                } catch (IOException ignored) { }
            }
            Bitmap bitmap = cover == null ? null : cachePlayniteBitmap(cover.file);
            if (bitmap != null) mainHandler.post(() -> shown.accept(bitmap));
        });
    }

    /** {@code installTarget} "" or null: the host default; {@code launcher} null: no choice. */
    private void beginPlayniteInstallation(ComputerDetails host, PlayniteDashboardItem item,
                                           GatewayConnection connection, String launcher,
                                           String installTarget) {
        if (launcher != null) {
            InstallLocations.setLastPath(preferences, host.uuid, launcher, installTarget);
        }
        preferences.edit().putLong(playniteCarouselInstallActivityKey(
                        host.uuid, item.game.playniteGameId), System.currentTimeMillis())
                .remove(playniteInstallNotificationKey(
                host.uuid, item.game.playniteGameId))
                .putString(playniteInstallPendingKey(host.uuid,
                        item.game.playniteGameId), item.game.name).apply();
        gameOperationsController.requestInstall(host.uuid, item.game, connection, installTarget,
                success -> {
            if (success) {
                    if (!active || !host.uuid.equals(selectedHostUuid)) return;
                    requestPlayniteRefresh(currentHost(host.uuid), false);
            } else {
                    preferences.edit().remove(playniteInstallPendingKey(
                            host.uuid, item.game.playniteGameId)).apply();
                    ComputerDetails current = currentHost(host.uuid);
                    if (current != null && host.uuid.equals(selectedHostUuid)) {
                        renderedPlayniteItems = Collections.emptyList();
                        renderPlayniteLibrary(current, currentSunshineApps);
                    }
                    if (consoleAudioEngine != null) {
                        consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.ERROR);
                    }
                    ConsoleUiFeedback.makeText(this, getString(R.string.playnite_install_failed,
                            item.game.name), Toast.LENGTH_LONG).show();
            }
        });
        renderPlayniteLibrary(host, currentSunshineApps);
        ConsoleUiFeedback.makeText(this, getString(R.string.playnite_install_starting,
                item.game.name), Toast.LENGTH_LONG).show();
    }

    private void confirmPlayniteUninstall(ComputerDetails host,
                                          PlayniteDashboardItem item) {
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView uninstall = consolePanel.action(getString(R.string.playnite_uninstall));
        consolePanel.destructive(uninstall);
        cancel.setOnClickListener(view -> handlePanelBack());
        uninstall.setOnClickListener(view -> {
            hideSidePanel();
            uninstallPlayniteGame(host, item);
        });
        showSidePanel(getString(R.string.playnite_game_options), item.game.name,
                getString(R.string.playnite_uninstall_confirmation, item.game.name),
                cancel, uninstall);
    }

    private void uninstallPlayniteGame(ComputerDetails host,
                                       PlayniteDashboardItem item) {
        uninstallPlayniteGame(host, item, null, null);
    }

    /**
     * {@code onSent}: the request went to the host (an early refusal here sends nothing);
     * {@code onRefused}: the host did not take it (the failure toast shows too).
     */
    private void uninstallPlayniteGame(ComputerDetails host, PlayniteDashboardItem item,
                                       Runnable onSent, Runnable onRefused) {
        if (host == null || item == null || !item.game.canUninstall) return;
        if (hasPlayniteOperationAwaitingConfirmation()) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_operation_waiting,
                    Toast.LENGTH_LONG).show();
            return;
        }
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, address,
                        selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        gameOperationsController.requestUninstall(host.uuid, item.game, connection, success -> {
            if (success) {
                requestPlayniteRefresh(currentHost(host.uuid), false);
            } else {
                renderPlayniteLibrary(currentHost(host.uuid), currentSunshineApps);
                if (onRefused != null) onRefused.run();
                ConsoleUiFeedback.makeText(this,
                        getString(R.string.playnite_uninstall_failed, item.game.name),
                        Toast.LENGTH_LONG).show();
            }
        });
        if (onSent != null) onSent.run();
        renderPlayniteLibrary(currentHost(host.uuid), currentSunshineApps);
        ConsoleUiFeedback.makeText(this, getString(R.string.playnite_uninstall_starting,
                item.game.name), Toast.LENGTH_LONG).show();
    }

    private void continuePlayniteInstallation(ComputerDetails host,
                                              PlayniteDashboardItem item) {
        if (host == null || item == null) return;
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection =
                hostGatewayStore.loadForHost(host.uuid, address,
                        selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        NvApp streamTarget = PlayniteTargetResolver.resolveInstallationStream(
                currentSunshineApps);
        if (streamTarget == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_no_fullscreen_target,
                    Toast.LENGTH_LONG).show();
            return;
        }
        GameOperationsController.Presentation operation =
                gameOperationsController.presentation(host.uuid, item.game);
        if (gameOperationsController.continuation(operation)
                == GameOperationsController.Continuation.OPEN_INSTALLATION_DESKTOP) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_install_confirm_on_pc_details,
                    Toast.LENGTH_LONG).show();
            openInstallationDesktop(host, item, streamTarget);
            return;
        }
        gameOperationsController.requestInstallationFocus(
                connection, item.game.playniteGameId, success -> {
            if (success) {
                    ConsoleUiFeedback.makeText(this, getString(
                            R.string.playnite_install_opening_confirmation,
                            item.game.name), Toast.LENGTH_LONG).show();
                    beginInstallationSupportStream(
                            currentHost(host.uuid), streamTarget, item.game.playniteGameId);
            } else {
                    ConsoleUiFeedback.makeText(this,
                            R.string.playnite_install_window_unavailable,
                            Toast.LENGTH_LONG).show();
                    openInstallationDesktop(host, item, streamTarget);
            }
        });
    }

    private void openInstallationDesktop(ComputerDetails host,
                                         PlayniteDashboardItem item,
                                         NvApp streamTarget) {
        beginInstallationSupportStream(
                currentHost(host.uuid), streamTarget, item.game.playniteGameId);
    }

    private void launchDesktopSession(ComputerDetails host) {
        if (host == null) return;
        String hostUuid = host.uuid;
        List<NvApp> selectedApps = hostUuid.equalsIgnoreCase(selectedHostUuid)
                ? currentSunshineApps : Collections.emptyList();
        executor.execute(() -> {
            List<NvApp> apps = selectedApps.isEmpty() ? loadAppList(host) : selectedApps;
            NvApp desktop = PlayniteTargetResolver.findPlayableExactName(apps, "Desktop");
            mainHandler.post(() -> {
                ComputerDetails current = currentHost(hostUuid);
                if (current == null || desktop == null
                        || !ConsoleActionCatalog.isRememberedPaired(current)) {
                    ConsoleUiFeedback.makeText(this,
                            R.string.console_launch_desktop_unavailable,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                playSunshineApp(current, desktop, "");
            });
        });
    }

    private void renderApps(ComputerDetails host, List<NvApp> apps) {
        if (selectedProfileIsChild(host == null ? null : host.uuid)) {
            apps = Collections.emptyList();
        }
        int runningAppId = renderedSessionSnapshot(host).streamerAppId;
        // One preferences handle per render: the signature and every card read it in memory.
        SharedPreferences appSettings = getSharedPreferences(
                AppPreferences.APP_PREFERENCES_FILE, MODE_PRIVATE);
        String signature = appRenderSignature(host, apps, runningAppId, appSettings);
        Object firstTag = appRow.getChildCount() > 0 ? appRow.getChildAt(0).getTag() : null;
        if (signature.equals(renderedAppsSignature) && firstTag instanceof String &&
                ((String) firstTag).startsWith("app:")) return;
        renderedAppsSignature = signature;
        renderedPlayniteItems = Collections.emptyList();
        allPlayniteItems = Collections.emptyList();
        unfilteredPlayniteItems = Collections.emptyList();
        if (expandedLibraryMode) exitExpandedLibrary();
        int restored = preferences.getInt("app_scroll." + host.uuid, 0);
        Object focusedTag = getCurrentFocus() != null ? getCurrentFocus().getTag() : null;
        int previousFocusedIndex = getCurrentFocus() != null
                ? appRow.indexOfChild(getCurrentFocus()) : -1;
        boolean appHadFocus = focusedTag instanceof String
                && ((String) focusedTag).startsWith("app:" + host.uuid + ":");
        if (!(focusedTag instanceof String) || !((String) focusedTag).startsWith("app:" + host.uuid + ":")) {
            focusedTag = preferences.getString("selected_app." + host.uuid, null);
        }
        appRow.removeAllViews();
        appsLabel.setText(getString(R.string.console_apps_host, host.name.toUpperCase(Locale.ROOT)));
        if (apps.isEmpty()) {
            appRow.addView(text(showHiddenApps ? getString(R.string.console_apps_empty)
                            : getString(R.string.console_apps_empty_or_hidden),
                    15, 0xFFFFB74D, false), new LinearLayout.LayoutParams(
                    dp(700), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            for (NvApp app : apps) {
                appRow.addView(appCard(host, app, runningAppId, appSettings),
                        cardSpacing());
            }
        }
        if (portraitLayout) {
            appVerticalScroll.post(() -> appVerticalScroll.scrollTo(0, restored));
        } else {
            appScroll.post(() -> appScroll.scrollTo(restored, 0));
        }
        List<String> availableTags = new ArrayList<>();
        for (int index = 0; index < appRow.getChildCount(); index++) {
            Object tag = appRow.getChildAt(index).getTag();
            if (tag instanceof String) availableTags.add((String) tag);
        }
        String selection = ConsoleDashboardState.restoreSelection(
                focusedTag instanceof String ? (String) focusedTag : null, availableTags);
        if (focusedTag instanceof String && !availableTags.contains(focusedTag)
                && previousFocusedIndex >= 0 && !availableTags.isEmpty()) {
            selection = availableTags.get(Math.min(previousFocusedIndex, availableTags.size() - 1));
        }
        if (selection != null && (appHadFocus || getCurrentFocus() == null)) {
            restoreTaggedFocus(appRow, selection);
        }
        wireHomeFocusNavigation();
    }

    private View appCard(ComputerDetails host, NvApp app, int runningAppId,
                         SharedPreferences appSettings) {
        boolean debugCarousel = CONSOLE_UI_V2 && !portraitLayout;
        LinearLayout card = cardBase(dp(portraitLayout ? 220 : debugCarousel ? 84 : 205),
                dp(portraitLayout ? 225 : debugCarousel ? 150 : 225));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(0, 0, 0, 0);
        card.setClipToOutline(true);
        card.setTag("app:" + host.uuid + ":" + app.getAppId());
        styleCard(card, false);
        ImageView poster = new ImageView(this);
        poster.setScaleType(debugCarousel
                ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        poster.setImageResource(R.drawable.ic_computer);
        poster.setPadding(dp(debugCarousel ? 24 : 72), dp(debugCarousel ? 36 : 54),
                dp(debugCarousel ? 24 : 72), dp(debugCarousel ? 36 : 54));
        poster.setBackground(gradient(0xFF26333D, 0xFF172128, 12));
        card.addView(poster, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(portraitLayout ? 140 : debugCarousel ? 110 : 142)));
        File artwork = assetLoader.getFile(host.uuid, app.getAppId());
        loadPoster(host, app, artwork, poster);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.TOP);
        copy.setPadding(dp(debugCarousel ? 7 : 14), dp(debugCarousel ? 4 : 12),
                dp(debugCarousel ? 7 : 14), dp(debugCarousel ? 3 : 10));
        TextView name = text(appDisplayName(app.getAppName()), debugCarousel ? 9 : 15,
                Color.WHITE, true);
        name.setMaxLines(debugCarousel ? 1 : 2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        updateCarouselMarquee(name, false);
        copy.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        long playedAt = preferences.getLong(
                PlayHistory.appKey(host.uuid, app.getAppId()), 0L);
        boolean running = runningAppId == app.getAppId();
        boolean hostReady = ConsoleActionCatalog.isOnline(host)
                && ConsoleActionCatalog.isPaired(host);
        boolean customSettings = !AppPreferences.getAppSettings(appSettings,
                host.uuid + ":" + app.getAppId()).useGlobalSettings;
        String metadata = !ConsoleActionCatalog.isOnline(host)
                ? getString(R.string.console_app_host_offline)
                : !ConsoleActionCatalog.isPaired(host)
                ? getString(R.string.console_app_host_unpaired)
                : running ? getString(R.string.console_app_running)
                : isQueuedApp(host.uuid, app.getAppId()) ? getString(R.string.console_queued)
                : playedAt > 0 ? getString(R.string.console_last_played,
                RelativeTime.format(getResources(), System.currentTimeMillis() - playedAt))
                : getString(R.string.console_app_ready);
        if (customSettings) metadata += getString(R.string.console_app_custom_settings_suffix);
        TextView metadataView = text(metadata, debugCarousel ? 7 : 10, 0xFF929BAD, false);
        metadataView.setMaxLines(2);
        metadataView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metadataParams = wrapLinear();
        metadataParams.topMargin = dp(debugCarousel ? 1 : 5);
        copy.addView(metadataView, metadataParams);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        card.addView(copy, copyParams);
        card.setOnClickListener(v -> {
            if (ConsoleActionCatalog.isRememberedPaired(host)) playSunshineApp(host, app, "");
            else showAppActions(host, app, poster);
        });
        card.setOnLongClickListener(view -> {
            showAppActions(host, app, poster);
            return true;
        });
        card.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_MENU
                    || keyCode == KeyEvent.KEYCODE_BUTTON_X)) {
                showAppActions(host, app, poster);
                return true;
            }
            return false;
        });
        card.setContentDescription(getString(R.string.console_app_description,
                appDisplayName(app.getAppName()), metadata, hostReady
                        ? running ? getString(R.string.console_resume) : getString(R.string.console_play)
                        : getString(R.string.console_app_options_action)));
        card.setOnFocusChangeListener((v, focused) -> {
            styleCard(card, focused);
            updateCarouselMarquee(name, focused);
            if (focused) {
                preferences.edit().putString("selected_app." + host.uuid,
                        String.valueOf(card.getTag())).apply();
                if (portraitLayout) smoothCenterOn(appVerticalScroll, card);
                else smoothCenterOn(appScroll, card);
                showSunshineArtwork(card, artwork, poster.getDrawable());
            }
        });
        return card;
    }

    private void updateCarouselMarquee(TextView title, boolean focused) {
        if (!CONSOLE_UI_V2 || title == null) return;
        Object pending = title.getTag(R.id.carousel_marquee_start);
        if (pending instanceof Runnable) title.removeCallbacks((Runnable) pending);
        // Grid titles keep their ellipsis: changing a title's line/ellipsize mode requests a
        // layout, and a layout request from any card re-lays out the whole recycling grid.
        if (expandedGrid != null && isDescendant(expandedGrid, title)) return;
        // setSingleLine re-applies its transformation and relays out even when already single
        // line (the setters below skip an unchanged value): a title at rest is left alone.
        if (!title.isSingleLine()) title.setSingleLine(true);
        title.setHorizontallyScrolling(false);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMarqueeRepeatLimit(0);
        title.setSelected(false);
        title.setTag(R.id.carousel_marquee_start, null);
        if (!focused) return;
        Runnable start = () -> {
            title.setTag(R.id.carousel_marquee_start, null);
            if (!title.isAttachedToWindow() || !cardParentHasFocus(title)) return;
            title.setHorizontallyScrolling(true);
            title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            title.setMarqueeRepeatLimit(1);
            title.setSelected(true);
        };
        title.setTag(R.id.carousel_marquee_start, start);
        title.postDelayed(start, reducedMotion ? 0L : CAROUSEL_MARQUEE_SETTLE_MS);
    }

    private String appRenderSignature(ComputerDetails host, List<NvApp> apps,
                                      int runningAppId, SharedPreferences appSettings) {
        StringBuilder value = new StringBuilder(host.uuid).append('|')
                .append(host.state).append('|').append(host.pairState).append('|')
                .append(runningAppId).append('|').append(showHiddenApps);
        for (NvApp app : apps) {
            String appKey = host.uuid + ":" + app.getAppId();
            value.append(';').append(app.getAppId()).append(':').append(app.getAppName())
                    .append(':').append(preferences.getLong(PlayHistory.appKey(
                            host.uuid, app.getAppId()), 0L))
                    .append(':').append(AppPreferences.getAppSettings(appSettings, appKey)
                            .useGlobalSettings)
                    .append(':').append(isQueuedApp(host.uuid, app.getAppId()));
        }
        return value.toString();
    }

    private void playSunshineApp(ComputerDetails host, NvApp app, String quickLaunchId) {
        if (host == null || app == null) return;
        // A poor history's advice waits in the stream's menu (Streaming Autopilot), not here.
        PlayIntent intent = PlayIntent.sunshineApp(host.uuid,
                selectedProfileId(host.uuid), app.getAppId(),
                app.getAppName(), app.isHdrSupported(), quickLaunchId,
                uniquePlayniteGameIdForRunningApp(host, app));
        if (warnPendingRestart(host.uuid, () -> sessionOrchestrator.play(intent))) return;
        sessionOrchestrator.play(intent);
    }

    /**
     * {@code stream} is the neutral stream this list knows, or null: preflight resolves it.
     * {@code freshStart}: no session of this game to resume, so game mode may close programs
     * on the PC first.
     */
    private void playPlayniteGame(ComputerDetails host, NvApp stream,
                                  PlayniteLibraryGame game, boolean freshStart) {
        if (host == null || game == null) return;
        String streamSettingsKey = AppPreferences.playniteGameKey(host.uuid, game.playniteGameId);
        String profileId = selectedProfileId(host.uuid);
        PlayIntent intent = PlayIntent.providerGame(host.uuid, profileId,
                stream == null ? 0 : stream.getAppId(), game.name,
                stream != null && stream.isHdrSupported(), game, streamSettingsKey);
        Runnable play = () -> sessionOrchestrator.play(intent);
        HostProfileKey artworkKey = new HostProfileKey(host.uuid, profileId);
        Runnable start = !freshStart || !hostPlus(host.uuid) ? play
                : () -> gameModeStep.run(host.uuid, profileId,
                selectedProfileIsChild(host.uuid), hostGatewayStore.loadForHost(host.uuid,
                        host.activeAddress == null ? null : host.activeAddress.address,
                        profileId), game.playniteGameId, game.name, streamSettingsKey,
                cachedArtwork(artworkKey, game, PlayniteArtworkSpec.forCard(game)),
                cachedArtwork(artworkKey, game, PlayniteArtworkSpec.forBackdrop(game)),
                () -> host.uuid.equals(selectedHostUuid)
                        && profileId.equals(selectedProfileId(host.uuid)), play);
        if (warnPendingRestart(host.uuid, start)) return;
        start.run();
    }

    /** The game's artwork already in the cache, or null (game mode's window draws none). */
    private java.io.File cachedArtwork(HostProfileKey key, PlayniteLibraryGame game,
                                       PlayniteArtworkSpec spec) {
        return spec.available() ? playniteArtworkCache.get(key, game.playniteGameId, spec.kind,
                spec.version) : null;
    }

    private PlayIntent providerGameIntent(String hostUuid, String profileId,
                                          int appId, String appName,
                                          boolean hdrSupported, String gameId,
                                          String loadingArtworkGameId,
                                          String streamSettingsKey) {
        PlayniteLibraryGame game = providerGameMetadata(hostUuid, profileId, gameId);
        if (game == null) {
            return PlayIntent.playniteGame(hostUuid, profileId,
                    appId, appName, hdrSupported,
                    gameId, loadingArtworkGameId, streamSettingsKey);
        }
        // The game's library name, never the stream target it runs through.
        return PlayIntent.playniteGame(hostUuid, profileId,
                appId, game.name, hdrSupported,
                game.playniteGameId, loadingArtworkGameId, streamSettingsKey,
                game.requiresConnector, game.startBeforeStream);
    }

    private PlayniteLibraryGame providerGameMetadata(String hostUuid, String profileId,
                                                      String gameId) {
        HostProfileKey key = new HostProfileKey(hostUuid, profileId);
        if (normalizeId(hostUuid).equalsIgnoreCase(normalizeId(currentPlayniteHostUuid))
                && currentPlayniteProjectionMatches(key)) {
            PlayniteLibraryGame current = findProviderGame(currentPlayniteGames, gameId);
            if (current != null) return current;
        }
        if (selectedProfileIsChild(hostUuid)) return null;
        PlayniteLibraryCache.Entry cached = playniteLibraryRepository == null
                ? null : playniteLibraryRepository.cached(key);
        return cached == null ? null : findProviderGame(cached.games, gameId);
    }

    static PlayniteLibraryGame findProviderGame(List<PlayniteLibraryGame> games,
                                                 String gameId) {
        String expected = normalizeId(gameId);
        if (games == null || expected.isEmpty()) return null;
        for (PlayniteLibraryGame game : games) {
            if (game != null && game.playniteGameId.equalsIgnoreCase(expected)) return game;
        }
        return null;
    }

    /**
     * The host as this console shows it. Only the poll listener and loadKnownHosts write it,
     * through hostStateController, which keeps a settling or invalidated (UNKNOWN) poll from
     * reading as Offline. The poller's own object is raw: readiness waits read that directly.
     */
    private ComputerDetails currentHost(String hostUuid) {
        return hostUuid == null ? null : hosts.get(hostUuid);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private void showAppActions(ComputerDetails host, NvApp app, ImageView poster) {
        if (host == null || app == null || selectedProfileIsChild(host.uuid)) return;
        SessionSnapshot session = renderedSessionSnapshot(host);
        String closing = closingStatus(session);
        if (closing != null) {
            showSidePanelBusy(getString(R.string.console_apps_eyebrow),
                    appDisplayName(app.getAppName()), closing);
            return;
        }
        boolean online = ConsoleActionCatalog.isOnline(host);
        boolean paired = ConsoleActionCatalog.isPaired(host);
        boolean thisAppRunning = session.streamerAppId == app.getAppId();
        boolean anotherAppRunning = session.streamerAppId > 0 && !thisAppRunning;
        Set<String> hidden = new HashSet<>(getSharedPreferences(
                HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .getStringSet(host.uuid, Collections.emptySet()));
        boolean isHidden = hidden.contains(String.valueOf(app.getAppId()));
        boolean hasArtwork = poster.getDrawable() instanceof BitmapDrawable;
        List<ConsoleAction> resolved = new ArrayList<>();
        addAppAction(resolved, ConsoleActionCatalog.AppCapability.LAUNCH,
                getString(R.string.console_play), online, paired, thisAppRunning,
                anotherAppRunning, isHidden, hasArtwork,
                false, () -> { hideSidePanel(); playSunshineApp(host, app, ""); });
        addAppAction(resolved, ConsoleActionCatalog.AppCapability.RESUME,
                getString(R.string.applist_menu_resume), online, paired, thisAppRunning,
                anotherAppRunning, isHidden, hasArtwork,
                false, () -> { hideSidePanel(); playSunshineApp(host, app, ""); });
        addAppAction(resolved, ConsoleActionCatalog.AppCapability.QUIT_AND_LAUNCH,
                getString(R.string.applist_menu_quit_and_start), online, paired, thisAppRunning,
                anotherAppRunning, isHidden, hasArtwork,
                false, () -> { hideSidePanel(); playSunshineApp(host, app, ""); });
        addAppAction(resolved, ConsoleActionCatalog.AppCapability.QUIT,
                getString(R.string.console_close_session), online, paired, thisAppRunning,
                anotherAppRunning, isHidden, hasArtwork,
                true, () -> confirmTerminateSession(host));
        addAppAction(resolved, ConsoleActionCatalog.AppCapability.SETTINGS,
                getString(R.string.console_app_stream_settings), online, paired, thisAppRunning,
                anotherAppRunning, isHidden, hasArtwork,
                false, () -> {
                    hideSidePanel();
                    new ConsoleAppStreamSettings(this, settingsScreen).open(
                            getString(R.string.console_apps_eyebrow),
                            host.uuid + ":" + app.getAppId(),
                            appDisplayName(app.getAppName()), false, null);
                });
        if (online && paired) {
            resolved.add(ConsoleAction.enabled("app.streaming_autopilot",
                    getString(R.string.console_streaming_autopilot_game), 0,
                    ConsoleAction.Context.APPLICATION, false,
                    () -> requestStreamingAutopilot(host,
                            host.uuid + ":" + app.getAppId(), null, app.getAppName(), "",
                            PlayIntent.sunshineApp(host.uuid,
                                    selectedProfileId(host.uuid), app.getAppId(),
                                    app.getAppName(), app.isHdrSupported(), ""))));
        }
        Runnable changeVisibility = () -> {
            if (isHidden) hidden.remove(String.valueOf(app.getAppId()));
            else hidden.add(String.valueOf(app.getAppId()));
            getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                    .edit().putStringSet(host.uuid, hidden).apply();
            showHiddenApps = isHidden || showHiddenApps;
            hideSidePanel();
            renderAppsAsync(host, true);
        };
        addAppAction(resolved, isHidden ? ConsoleActionCatalog.AppCapability.SHOW
                        : ConsoleActionCatalog.AppCapability.HIDE,
                getString(isHidden ? R.string.console_show_app : R.string.applist_menu_hide_app),
                online, paired, thisAppRunning, anotherAppRunning, isHidden,
                hasArtwork, false, changeVisibility);
        addAppAction(resolved, ConsoleActionCatalog.AppCapability.SHORTCUT,
                getString(R.string.applist_menu_scut), online, paired, thisAppRunning,
                anotherAppRunning, isHidden, hasArtwork,
                false, () -> {
                    Bitmap bitmap = ((BitmapDrawable) poster.getDrawable()).getBitmap();
                    if (!shortcutHelper.createPinnedGameShortcut(host, app, bitmap)) {
                        ConsoleUiFeedback.makeText(this, R.string.unable_to_pin_shortcut, Toast.LENGTH_LONG).show();
                    }
                });
        addAppAction(resolved, ConsoleActionCatalog.AppCapability.DETAILS,
                getString(R.string.applist_menu_details), online, paired, thisAppRunning,
                anotherAppRunning, isHidden, hasArtwork,
                false, () -> consoleFeedback.showMessage(
                        getString(R.string.title_details), app.toString(),
                        getString(R.string.console_close), null));
        List<View> actions = new ArrayList<>();
        for (ConsoleAction action : resolved) actions.add(actionView(action));
        showSidePanel(getString(R.string.console_apps_eyebrow),
                appDisplayName(app.getAppName()),
                getString(R.string.console_app_actions_details), actions.toArray(new View[0]));
    }

    private void addAppAction(List<ConsoleAction> actions,
                              ConsoleActionCatalog.AppCapability capability,
                              CharSequence label, boolean online, boolean paired,
                              boolean thisAppRunning, boolean anotherAppRunning,
                              boolean hidden, boolean hasArtwork,
                              boolean destructive, Runnable handler) {
        if (!ConsoleActionCatalog.appActionVisible(capability, online, paired,
                thisAppRunning, anotherAppRunning, hidden, hasArtwork)) return;
        actions.add(ConsoleAction.enabled("app." + capability.name().toLowerCase(Locale.ROOT),
                label, 0, ConsoleAction.Context.APPLICATION, destructive, handler));
    }

    private void loadPoster(ComputerDetails host, NvApp app, File file, ImageView view) {
        executor.execute(() -> {
            if (!file.exists() && managerBinder != null && host.activeAddress != null) {
                CachedAppAssetLoader.LoaderTuple tuple =
                        new CachedAppAssetLoader.LoaderTuple(host, app);
                try (InputStream input = new NetworkAssetLoader(this,
                        managerBinder.getUniqueId()).getBitmapStream(tuple)) {
                    if (input != null) assetLoader.populateCacheWithStream(tuple, input);
                } catch (IOException | RuntimeException ignored) {}
            }
            if (!file.exists()) return;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            mainHandler.post(() -> {
                if (bitmap != null && view.isAttachedToWindow()) {
                    view.setPadding(0, 0, 0, 0);
                    view.setScaleType(CONSOLE_UI_V2
                            ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
                    view.clearColorFilter();
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    private void updateArtworkReadability(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
        int columns = 12;
        int rows = 8;
        float total = 0f;
        int samples = 0;
        for (int row = 0; row < rows; row++) {
            int y = Math.min(bitmap.getHeight() - 1,
                    Math.round((row + .5f) * bitmap.getHeight() / rows));
            for (int column = 0; column < columns; column++) {
                int x = Math.min(bitmap.getWidth() - 1,
                        Math.round((column + .5f) * bitmap.getWidth() / columns));
                int color = bitmap.getPixel(x, y);
                total += ConsoleArtworkReadability.perceivedLuminance(
                        Color.red(color), Color.green(color), Color.blue(color));
                samples++;
            }
        }
        animateArtworkScrimTo(samples == 0 ? .42f : total / samples);
    }

    private void animateArtworkScrimTo(float luminance) {
        float target = Math.max(0f, Math.min(1f, luminance));
        if (artworkScrim == null) {
            artworkScrimLuminance = target;
            return;
        }
        if (artworkScrimAnimator != null) artworkScrimAnimator.cancel();
        if (instantArtwork() || Math.abs(target - artworkScrimLuminance) < .015f) {
            artworkScrimLuminance = target;
            artworkScrim.setBackground(artworkScrimDrawable(target));
            return;
        }
        artworkScrimAnimator = ValueAnimator.ofFloat(artworkScrimLuminance, target);
        artworkScrimAnimator.setDuration(240L);
        artworkScrimAnimator.addUpdateListener(animation -> {
            artworkScrimLuminance = (Float) animation.getAnimatedValue();
            artworkScrim.setBackground(artworkScrimDrawable(artworkScrimLuminance));
        });
        artworkScrimAnimator.start();
    }

    private GradientDrawable artworkScrimDrawable(float luminance) {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                ConsoleArtworkReadability.gradient(luminance, CONSOLE_UI_V2));
    }

    /**
     * A Sunshine app's box art behind its focused {@code card}: the file (rewritable, so keyed
     * by its size and time) is checked on the disk executor.
     */
    private void showSunshineArtwork(View card, File file, Drawable preview) {
        if (file == null) return;
        diskExecutor.execute(() -> {
            if (!file.exists()) return;
            String key = file.getAbsolutePath() + ':' + file.length() + ':' + file.lastModified();
            mainHandler.post(() -> {
                if (card.hasFocus()) showArtwork(file, key, preview);
            });
        });
    }

    /** {@code key} names the file's content, computed off the main thread. */
    private void showArtwork(File file, String key, Drawable preview) {
        if (key.equals(pendingArtworkKey) || key.equals(loadingArtworkKey)
                || key.equals(displayedArtworkKey)) return;
        if (pendingArtworkCommit != null) mainHandler.removeCallbacks(pendingArtworkCommit);
        pendingArtworkFile = file;
        pendingArtworkPreview = preview;
        pendingArtworkKey = key;
        pendingArtworkCommit = () -> {
            File settledFile = pendingArtworkFile;
            Drawable settledPreview = pendingArtworkPreview;
            String settledKey = pendingArtworkKey;
            pendingArtworkCommit = null;
            pendingArtworkFile = null;
            pendingArtworkPreview = null;
            pendingArtworkKey = null;
            if (settledFile != null && settledKey != null) {
                showArtworkSettled(settledFile, settledPreview, settledKey);
            }
        };
        mainHandler.postDelayed(pendingArtworkCommit,
                instantArtwork() ? 0L : ARTWORK_FOCUS_SETTLE_MS);
    }

    /** Without motion, or under the entry intro, whose exit then reveals the art at rest. */
    private boolean instantArtwork() {
        return reducedMotion || suppressInitialCarouselMotion
                || introView != null && introView.holding();
    }

    private void showArtworkSettled(File file, Drawable preview, String artworkKey) {
        int token = artworkGeneration.incrementAndGet();
        boolean immediate = instantArtwork();
        loadingArtworkKey = artworkKey;
        if (preview != null && !CONSOLE_UI_V2) {
            Drawable next = cloneDrawable(preview);
            Drawable current = artworkHero.getDrawable();
            artworkHero.animate().cancel();
            if (current != null && !immediate) {
                TransitionDrawable transition = new TransitionDrawable(
                        new Drawable[]{current, next});
                transition.setCrossFadeEnabled(true);
                artworkHero.setImageDrawable(transition);
                transition.startTransition(180);
            } else {
                artworkHero.setImageDrawable(next);
            }
            artworkHero.animate().alpha(.72f)
                    .setDuration(immediate ? 0 : 180).start();
            artworkScrim.animate().cancel();
            artworkScrim.animate().alpha(1f).setDuration(immediate ? 0 : 180).start();
        }
        executor.execute(() -> {
            if (token != artworkGeneration.get()) return;
            String bitmapKey = "backdrop:" + artworkKey;
            Bitmap bitmap = playniteBitmapCache.get(bitmapKey);
            if (bitmap == null) {
                bitmap = decodeArtwork(file, CONSOLE_UI_V2 ? 1920 : 1200);
                if (bitmap != null) playniteBitmapCache.put(bitmapKey, bitmap);
            }
            if (token != artworkGeneration.get()) {
                return;
            }
            Bitmap readyBitmap = bitmap;
            int accent = sampleAccent(readyBitmap);
            mainHandler.post(() -> {
                if (token != artworkGeneration.get() || readyBitmap == null) {
                    if (artworkKey.equals(loadingArtworkKey)) loadingArtworkKey = null;
                    return;
                }
                loadingArtworkKey = null;
                displayedArtworkKey = artworkKey;
                glassAccent = accent;
                updateArtworkReadability(readyBitmap);
                ImageView outgoingBackdrop = artworkBackdrop;
                ImageView incomingBackdrop = artworkBackdropNext;
                outgoingBackdrop.animate().cancel();
                incomingBackdrop.animate().cancel();
                incomingBackdrop.setImageBitmap(readyBitmap);
                incomingBackdrop.setAlpha(immediate
                        ? (CONSOLE_UI_V2 ? .72f : .16f) : 0f);
                if (!CONSOLE_UI_V2) {
                    // The preview and final hero have identical geometry. Replacing the
                    // preview avoids a soft double-image while retaining the tile-to-tile crossfade.
                    artworkHero.setImageBitmap(readyBitmap);
                    artworkHero.animate().alpha(.72f)
                            .setDuration(immediate ? 0 : 220).start();
                }
                float targetAlpha = CONSOLE_UI_V2 ? .72f : .16f;
                if (immediate) {
                    outgoingBackdrop.setAlpha(0f);
                    finishBackdropSwap(token, outgoingBackdrop, incomingBackdrop);
                } else {
                    incomingBackdrop.animate().alpha(targetAlpha)
                            .setDuration(ARTWORK_CROSSFADE_MS).start();
                    outgoingBackdrop.animate().alpha(0f)
                            .setDuration(ARTWORK_CROSSFADE_MS)
                            .withEndAction(() -> finishBackdropSwap(
                                    token, outgoingBackdrop, incomingBackdrop))
                            .start();
                }
                artworkScrim.animate().alpha(1f).setDuration(immediate ? 0 : 220).start();
                View focused = getCurrentFocus();
                if (focused != null && focused.getTag() instanceof String
                        && ((String) focused.getTag()).startsWith("app:")) {
                    styleCard(focused, true);
                }
            });
        });
    }

    private void finishBackdropSwap(int token, ImageView outgoing, ImageView incoming) {
        if (token != artworkGeneration.get()) return;
        artworkBackdrop = incoming;
        artworkBackdropNext = outgoing;
        artworkBackdropNext.animate().cancel();
        artworkBackdropNext.setAlpha(0f);
        artworkBackdropNext.setImageDrawable(null);
    }

    private void clearArtwork() {
        if (pendingArtworkCommit != null) {
            mainHandler.removeCallbacks(pendingArtworkCommit);
            pendingArtworkCommit = null;
        }
        pendingArtworkFile = null;
        pendingArtworkPreview = null;
        pendingArtworkKey = null;
        loadingArtworkKey = null;
        displayedArtworkKey = null;
        settledBackdropTag = "";
        int token = artworkGeneration.incrementAndGet();
        glassAccent = 0xFF73D7FF;
        animateArtworkScrimTo(.42f);
        if (artworkBackdrop != null) {
            artworkBackdrop.animate().cancel();
            artworkBackdrop.animate().alpha(0f).setDuration(reducedMotion ? 0 : 260).start();
        }
        if (artworkBackdropNext != null) {
            artworkBackdropNext.animate().cancel();
            artworkBackdropNext.animate().alpha(0f)
                    .setDuration(reducedMotion ? 0 : 260).start();
        }
        if (artworkHero != null) {
            artworkHero.animate().cancel();
            artworkHero.animate().alpha(0f).setDuration(reducedMotion ? 0 : 260)
                    .withEndAction(() -> {
                        if (token != artworkGeneration.get()) return;
                        artworkBackdrop.setImageDrawable(null);
                        artworkBackdropNext.setImageDrawable(null);
                        artworkHero.setImageDrawable(null);
                    }).start();
        }
        if (artworkScrim != null) {
            artworkScrim.animate().cancel();
            artworkScrim.animate().alpha(0f).setDuration(reducedMotion ? 0 : 260).start();
        }
    }

    private Bitmap decodeArtwork(File file, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = LoadingArtworkPolicy.sampleSize(
                bounds.outWidth, bounds.outHeight, maxDimension);
        // Full-screen hero/screen-saver art is opaque: half the native memory of ARGB_8888.
        // The decoder keeps ARGB_8888 on its own for a source that has an alpha channel.
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private Drawable cloneDrawable(Drawable drawable) {
        Drawable.ConstantState state = drawable != null ? drawable.getConstantState() : null;
        return state != null ? state.newDrawable(getResources()).mutate() : drawable;
    }

    private int sampleAccent(Bitmap bitmap) {
        if (bitmap == null) return 0xFF715BA8;
        long red = 0, green = 0, blue = 0, count = 0;
        int stepX = Math.max(1, bitmap.getWidth() / 12);
        int stepY = Math.max(1, bitmap.getHeight() / 12);
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int color = bitmap.getPixel(x, y);
                int brightness = Color.red(color) + Color.green(color) + Color.blue(color);
                if (brightness < 90 || brightness > 690) continue;
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++;
            }
        }
        return count == 0 ? 0xFF715BA8 : Color.rgb((int) (red / count),
                (int) (green / count), (int) (blue / count));
    }

    private HostLaunchPreflight createHostLaunchPreflight() {
        HostLaunchPreflight.Network network = (hostId, action, cancelled) -> {
            if (action == HostLaunchPreflight.Action.SWITCH_RETAINED) {
                SessionModel.Snapshot retained =
                        sessionModel.snapshot();
                return (retained.state == SessionModel.State.HOME_LIVE
                        || retained.state == SessionModel.State.PREPARING)
                        && retained.hostId.equalsIgnoreCase(hostId)
                        && sessionModel.canSwitchGame(retained);
            }
            ComputerDetails target = currentHost(hostId);
            if (target == null) return false;
            if (target.state == ComputerDetails.State.OFFLINE) {
                HostReadiness.requestWake(target);
            }
            return !cancelled.getAsBoolean();
        };
        HostLaunchPreflight.Gateway gateway = new HostLaunchPreflight.Gateway() {
            @Override public HostLaunchPreflight.Profile profile(String hostId, String profileId)
                    throws IOException {
                GatewayConnection connection = connection(hostId, profileId);
                if (connection == null) return null;
                if (!hostGatewayClient.getCapabilities(connection).controlOperations) {
                    throw new IOException("Gateway control operations unavailable");
                }
                HostGatewayClient.IntegrationProfiles profiles = hostGatewayClient
                        .getIntegrationProfiles(connection);
                saveHostProfiles(hostId, profiles);
                HostGatewayClient.IntegrationProfile profile = profiles.find(profileId);
                if (profile == null) {
                    return new HostLaunchPreflight.Profile(false, false, false);
                }
                return new HostLaunchPreflight.Profile(profile.id,
                        profile.executionProfileId, profile.name, profile.useProfile,
                        profile.playniteBridgeOnline,
                        profile.playniteConnectorConnected);
            }

            @Override public HostLaunchPreflight.Session ensureSession(
                    String hostId, String profileId, String requestId) throws IOException {
                GatewayConnection connection = connection(hostId, profileId);
                if (connection == null) throw new IOException("Gateway unavailable");
                return session(hostGatewayClient.ensureWindowsSession(connection, requestId));
            }

            @Override public HostLaunchPreflight.Session sessionStatus(
                    String hostId, String profileId, String requestId, String attemptId)
                    throws IOException {
                GatewayConnection connection = connection(hostId, profileId);
                if (connection == null) throw new IOException("Gateway unavailable");
                return session(hostGatewayClient.getWindowsSessionStatus(
                        connection, requestId, attemptId));
            }

            @Override public void cancelSession(String hostId, String profileId,
                                                String requestId, String attemptId)
                    throws IOException {
                GatewayConnection connection = connection(hostId, profileId);
                if (connection != null) {
                    hostGatewayClient.cancelWindowsSession(
                            connection, requestId, attemptId);
                }
            }

            private GatewayConnection connection(String hostId, String profileId) {
                ComputerDetails host = currentHost(hostId);
                String address = host != null && host.activeAddress != null
                        ? host.activeAddress.address : null;
                return hostGatewayStore.loadForHost(hostId, address, profileId);
            }

            private HostLaunchPreflight.Session session(
                    HostGatewayClient.WindowsSession value) {
                return HostLaunchPreflight.Session.from(value);
            }
        };
        HostLaunchPreflight.Sunshine sunshine = new HostLaunchPreflight.Sunshine() {
            @Override public NvApp verifiedRetainedTarget(HostLaunchPreflight.Request request) {
                SessionModel.Snapshot retained =
                        sessionModel.snapshot();
                if (request.action != HostLaunchPreflight.Action.SWITCH_RETAINED
                        || !request.requiresPlaynite()
                        || !retained.hostId.equalsIgnoreCase(request.hostId)
                        || !retained.executionProfileId.equals(executionProfileId(request.hostId, request.profileId))
                        || retained.appId != request.appId
                        || !sessionModel.canSwitchGame(retained)) return null;
                ComputerDetails host = currentHost(request.hostId);
                if (host == null || !host.uuid.equalsIgnoreCase(retained.hostId)) return null;
                NvApp target = PlayniteTargetResolver.findById(loadAppList(host), retained.appId);
                return PlayniteTargetResolver.isNeutralStream(target)
                        && sessionModel.canSwitchGame(retained) ? target : null;
            }

            @Override public List<NvApp> refreshApps(String hostId) throws IOException {
                ComputerDetails host = currentHost(hostId);
                if (host == null || managerBinder == null) {
                    throw new IOException("Sunshine host unavailable");
                }
                NvHTTP connection = new NvHTTP(
                        ServerHelper.getCurrentAddressFromComputer(host), host.httpsPort,
                        managerBinder.getUniqueId(), host.serverCert,
                        PlatformBinding.getCryptoProvider(ConsoleActivity.this));
                try {
                    return connection.getAppList();
                } catch (XmlPullParserException invalidResponse) {
                    throw new IOException("Invalid Sunshine app list", invalidResponse);
                }
            }
        };
        HostLaunchPreflight.Waiter waiter = (millis, cancelled) -> {
            if (cancelled.getAsBoolean()) return false;
            try {
                Thread.sleep(millis);
                return !cancelled.getAsBoolean();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
        return new HostLaunchPreflight(network, gateway, sunshine,
                SystemClock::uptimeMillis, waiter);
    }

    private String preflightStageMessage(HostLaunchPreflight.Stage stage,
                                         PlayIntent intent) {
        switch (stage) {
            case NETWORK_READY:
                return getString(R.string.console_checking_host);
            case GATEWAY_READY:
                return getString(R.string.transition_waiting_gateway);
            case PROFILE_AUTHORIZED:
                return getString(R.string.preflight_authorizing_profile);
            case INTERACTIVE_SESSION_READY:
                return getString(R.string.preflight_starting_windows_session);
            case PROFILE_READY:
                return getString(R.string.preflight_starting_profile_services);
            case PLAYNITE_READY:
                return getString(R.string.transition_checking_game_launch_service);
            default:
                // TARGET_READY: the stream target exists; nothing has started a game yet.
                return getString(R.string.transition_preparing_session);
        }
    }

    private String preflightFailureMessage(HostLaunchPreflight.Failure failure) {
        switch (failure.reason) {
            case NETWORK_UNAVAILABLE:
                return getString(R.string.console_host_timeout);
            case GATEWAY_UNAVAILABLE:
                return getString(R.string.transition_gateway_unavailable);
            case SELECTED_PROFILE_UNAVAILABLE:
                return getString(R.string.preflight_profile_unavailable);
            case REMOTE_SIGN_IN_NOT_GRANTED:
                return getString(R.string.preflight_remote_sign_in_not_granted,
                        failure.profileName);
            case MANUAL_SIGN_IN_REQUIRED:
                return getString(R.string.preflight_manual_sign_in_required,
                        failure.profileName);
            case CREDENTIAL_ACTION_REQUIRED:
                return getString(R.string.preflight_credential_action_required,
                        failure.profileName);
            case OTHER_PROFILE_ACTIVE:
                return getString(R.string.preflight_other_profile_active);
            case LOGIN_BROKER_UNAVAILABLE:
                return getString(R.string.preflight_login_broker_unavailable);
            case HOST_SESSION_STATE_UNREADABLE:
                return getString(R.string.preflight_host_session_state_unreadable);
            case WINDOWS_SIGN_IN_EXPIRED:
                return getString(R.string.preflight_windows_sign_in_expired,
                        failure.profileName);
            case WINDOWS_SIGN_IN_CANCELLED:
                return getString(R.string.preflight_windows_sign_in_cancelled,
                        failure.profileName);
            case WINDOWS_SIGN_IN_TIMEOUT:
                return getString(R.string.preflight_windows_sign_in_timeout,
                        failure.profileName);
            case WINDOWS_SIGN_IN_FAILED:
            case WINDOWS_IDENTITY_MISMATCH:
                return getString(R.string.preflight_windows_sign_in_failed,
                        failure.profileName);
            case PLAYNITE_BRIDGE_OFFLINE:
                return getString(R.string.preflight_playnite_offline);
            case PLAYNITE_CONNECTOR_DISCONNECTED:
                return getString(R.string.preflight_playnite_connector_disconnected);
            case NEUTRAL_STREAM_UNAVAILABLE:
                return getString(R.string.preflight_neutral_stream_unavailable);
            default:
                return getString(R.string.playnite_launch_unavailable);
        }
    }

    private String childLaunchFailureMessage(String reason) {
        int message = childRefusalMessage(reason);
        return getString(message != 0 ? message : R.string.console_child_launch_unavailable);
    }

    /** The message of a host child-policy refusal; 0 for a reason that is not one. */
    static int childRefusalMessage(String reason) {
        String normalized = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "outside_schedule":
            case "outside_allowed_hours":
                return R.string.console_child_launch_outside_schedule;
            case "daily_limit_reached":
            case "daily_limit_exhausted":
                return R.string.console_child_launch_daily_limit;
            case "game_not_shared":
            case "not_shared":
                return R.string.console_child_launch_not_shared;
            case "pairing_required":
                return R.string.console_child_launch_pairing_required;
            case "parent_unavailable":
                return R.string.console_child_launch_parent_unavailable;
            case "policy_unavailable":
            case "policy_unknown":
                return R.string.console_child_launch_policy_unavailable;
            case "session_in_use":
                return R.string.console_child_launch_session_in_use;
            case "cleanup_required":
                return R.string.console_child_launch_cleanup_required;
            case "gateway_unavailable":
                return R.string.console_child_launch_gateway_unavailable;
            case "child_profile_unavailable":
                return R.string.console_child_launch_profile_unavailable;
            case "child_not_playable":
                return R.string.console_child_launch_unavailable;
            default:
                return 0;
        }
    }

    /**
     * Reads the host's observation now; whether the model accepted this read's own
     * observation. The projection records what the read observed (or that it was unavailable).
     */
    private boolean refreshObservationNow(ComputerDetails host, String profileId) {
        String address = host.activeAddress == null ? null : host.activeAddress.address;
        GatewayConnection gateway = hostGatewayStore.loadForHost(
                host.uuid, address, profileId);
        if (gateway == null) return false;
        try {
            return new PlayniteTransitionGateway(sessionModel, host.uuid, gateway,
                    gatewayActorContext(host.uuid, profileId), hostGatewayClient,
                    SystemClock::uptimeMillis).snapshot().observationAccepted;
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    private ComputerDetails refreshWarmUpHost(ComputerDetails host) throws IOException {
        if (host == null || managerBinder == null) throw new IOException("Host unavailable");
        NvHTTP connection = new NvHTTP(
                ServerHelper.getCurrentAddressFromComputer(host), host.httpsPort,
                managerBinder.getUniqueId(), host.serverCert,
                PlatformBinding.getCryptoProvider(this));
        try {
            return HostReadiness.mergeFreshDetails(
                    host, connection.getComputerDetails(true));
        } catch (XmlPullParserException invalid) {
            throw new IOException("Invalid Sunshine host response", invalid);
        }
    }

    private SessionOrchestrator createSessionOrchestrator() {
        return new SessionOrchestrator(new SessionOrchestrator.Effects() {
            @Override public boolean drivesQueue() {
                // A queued Play starts once this screen shows its host (never under the entry
                // gate or its PIN: enterHostAfterProfileGate starts it). A stream Home whose
                // stream is gone is being replaced by the console.
                return active && !isFinishing() && initialHostSelectionResolved
                        && !hostSelectionVisible && profileGateHostUuid == null
                        && !(isStreamHome()
                        && !sessionModel.snapshot().streamSessionId.equals(
                        launchedExtra(EXTRA_RETAINED_STREAM_SESSION_ID)));
            }

            @Override public boolean isAvailable() {
                return managerBinder != null;
            }

            @Override public boolean isPaired(String hostId) {
                return ConsoleActionCatalog.isRememberedPaired(currentHost(hostId));
            }

            @Override public boolean isHostOnline(String hostId) {
                return ConsoleActionCatalog.isOnline(currentHost(hostId));
            }

            @Override public SessionFailure awaitHost(
                    PlayIntent intent, BooleanSupplier cancelled) {
                ComputerDetails host = currentHost(intent.hostId);
                if (host == null) return SessionFailure.HOST_WAKE_TIMEOUT;
                String waking = getString(R.string.console_status_waking);
                // A host without a wake address is still awaited: it can be switched on by hand.
                // A host this client put to sleep is woken while its polls still call it online.
                Supplier<ComputerDetails> current = () -> managerBinder != null
                        ? managerBinder.getComputer(intent.hostId) : null;
                Consumer<String> status = message -> mainHandler.post(() -> {
                    if (!cancelled.getAsBoolean() && streamLoadingView != null) {
                        streamLoadingView.setStep(1, message);
                    }
                });
                HostPowerStateStore.State power = sessionModel.records().hostPower(host.uuid);
                if (power != null && power.downObservedAt == 0L) {
                    // A host still going down answers its port and would end the wake at once:
                    // let it stop answering first. One that never does is taken as awake.
                    status.accept(getString(R.string.console_status_suspending_short));
                    HostReadiness.awaitDown(current, cancelled);
                }
                // A host this client shut down or restarted starts cold: it gets longer.
                boolean coldStart = power != null
                        && power.kind != HostPowerStateStore.Kind.SLEEP;
                long timeout = coldStart ? HostReadiness.COLD_START_TIMEOUT_MS
                        : HostReadiness.WAKE_TIMEOUT_MS;
                ComputerDetails ready = HostReadiness.await(current,
                        ConsoleHostPresentation.hasWakeAddress(host) ? host : null, cancelled,
                        status, waking, waking, timeout);
                if (ready == null) return SessionFailure.HOST_WAKE_TIMEOUT;
                sessionModel.post(new SessionEvent.HostPowerCleared(host.uuid));
                // Not paired on the fresh state: the table's next pass refuses it.
                if (!ConsoleActionCatalog.isPaired(ready)) return null;
                status.accept(getString(R.string.transition_waiting_gateway));
                // After a cold start the Gateway comes up later than the streaming port.
                return hostLaunchPreflight.awaitGateway(HostLaunchPreflight.Request.from(
                        intent, HostLaunchPreflight.Action.LAUNCH), cancelled)
                        ? null : SessionFailure.HOST_GATEWAY_TIMEOUT;
            }

            @Override public SessionSnapshot resolve(HostProfileKey profileKey) {
                String hostId = profileKey.hostId;
                ComputerDetails host = currentHost(hostId);
                if (ConsoleActionCatalog.isOnline(host)
                        && ConsoleActionCatalog.isPaired(host)
                        && profileKey.equals(selectedProfileKey(hostId))) {
                    refreshSessionProjection(host, profileKey, null);
                }
                return resolveSessionSnapshot(host, profileKey.profileId);
            }

            @Override public SessionSnapshot rendered(HostProfileKey profileKey) {
                return projectSession(currentHost(profileKey.hostId), profileKey.profileId, true);
            }

            @Override public void postDelayed(Runnable action, long delayMs) {
                mainHandler.postDelayed(action, delayMs);
            }

            @Override public void queueChanged() {
                PlayIntent waiting = sessionOrchestrator == null
                        ? null : sessionOrchestrator.queued();
                // The queued Play waits on its own launch cover; an emptied queue leaves the
                // cover to whatever follows (its start continues on it, a cancel ends it).
                if (waiting != null) showQueuedCover(waiting);
                renderQueue();
            }

            @Override public void queueDropped(PlayIntent intent,
                                               SessionOrchestrator.QueueDrop reason) {
                endQueuedCover();
                queueChanged();
                ConsoleUiFeedback.makeText(ConsoleActivity.this, getString(
                        reason == SessionOrchestrator.QueueDrop.CLOSE_FAILED
                                ? R.string.console_queue_dropped_close_failed
                                : R.string.console_queue_dropped_unconfirmed,
                        intent.appName), Toast.LENGTH_LONG).show();
            }

            @Override public void returnToRetainedStream() {
                ConsoleActivity.this.returnToRetainedStream();
            }

            @Override public void attachPresentationForSwitch(
                    long orchestrationId, PlayIntent intent, NvApp target,
                    SessionOrchestrator.SwitchStop stop) {
                ConsoleActivity.this.attachPresentationForSwitch(
                        orchestrationId, intent, target, stop);
            }

            @Override public void reconnectSavedSession(PlayIntent intent) {
                reconnectSavedSessionForActor(intent);
            }
            @Override public boolean warmUpCarriesGame() { SessionModel.Snapshot s =
                    sessionModel.snapshot(); return warmUpCarriesPendingGame(
                    tracksWarmUp(), warmUpClientHostId, s.records.pending, s); }
            @Override public void reject(SessionOrchestrator.Rejection reason) {
                boolean initializing = reason == SessionOrchestrator.Rejection.INITIALIZING;
                String title = getString(initializing
                        ? R.string.console_initializing : R.string.scut_not_paired);
                String details = getString(initializing
                        ? R.string.console_reject_initializing_details
                        : R.string.console_reject_unpaired_details);
                // Under a raised loading layer (after Retry) a toast would leave its loader up.
                if (streamLoadingView != null && streamLoadingView.canShowError()) {
                    streamLoadingView.showError(title, details, false, false, initializing);
                    return;
                }
                ConsoleUiFeedback.makeText(ConsoleActivity.this, title + " " + details,
                        Toast.LENGTH_LONG).show();
            }

            @Override public void confirmFreezeOrReplacement(Runnable replace,
                                                             Runnable freeze) {
                consoleFeedback.showChoice(getString(R.string.console_replace_session_title),
                        getString(R.string.console_replace_freeze_message),
                        getString(android.R.string.cancel),
                        getString(R.string.console_replace_session), replace,
                        getString(R.string.console_replace_freeze_current), freeze);
            }

            @Override public boolean freezesOnSwitch(String hostId) {
                return hostPlus(hostId) && QuickResumePreferences.freezesOnSwitch(preferences, hostId);
            }

            @Override public SessionOrchestrator.Retained retained(PlayIntent intent) {
                ComputerDetails host = currentHost(intent.hostId);
                SessionModel.Snapshot stream = sessionModel.snapshot();
                String execution = executionProfileId(intent);
                NvApp streamTarget = PlayniteTargetResolver.findById(
                        stream.state == SessionModel.State.PREPARING && host != null
                                ? appLists.apps(host.uuid) : currentSunshineApps,
                        stream.appId);
                return new SessionOrchestrator.Retained(stream, execution,
                        host == null ? null : resolveSessionSnapshot(host, intent.profileId),
                        PlayniteTargetResolver.isNeutralStream(streamTarget),
                        sessionModel.canSwitchOrigin(intent.hostId, execution, stream.appId),
                        sessionModel.hasAttachedPresentation());
            }

            @Override public SessionModel.Snapshot stream() {
                return sessionModel.snapshot();
            }

            @Override public String executionProfileId(PlayIntent intent) {
                return ConsoleActivity.this.executionProfileId(intent.hostId, intent.profileId);
            }


            @Override public void showLoading(PlayIntent intent,
                                              LaunchTransitionType type,
                                              Runnable opaqueFrameReady) {
                if (intent.kind == PlayIntent.Kind.PLAYNITE_GAME
                        && warmUpStatus == WARM_UP_ERROR
                        && warmUpClientHostId.equals(normalizeId(intent.hostId))
                        && !sessionOrchestrator.hasPreparationForHost(intent.hostId)) {
                    relinquishWarmUp();
                }
                ComputerDetails host = currentHost(intent.hostId);
                String hostName = host == null ? intent.hostId : host.name;
                if (intent == queuedCoverIntent && streamLoadingView != null
                        && loadingLayer.getVisibility() == View.VISIBLE) {
                    // The queued Play started: it continues on the cover it waited on.
                    queuedCoverIntent = null;
                    streamLoadingView.setStep(1, getString(R.string.console_preparing_host,
                            hostName));
                    afterLoadingCover(opaqueFrameReady);
                } else {
                    queuedCoverIntent = null;
                    ConsoleActivity.this.showLoading(hostName, intent.appName, null);
                    // The cover is up at once on its plain ground; the launch waits until its
                    // artwork is decided, so the cover never swaps it mid-transition.
                    ConsoleStreamLoadingView cover = streamLoadingView;
                    cover.awaitSplashArtwork();
                    decideLaunchArtwork(intent, artwork -> {
                        cover.setSplashArtwork(artwork);
                        afterLoadingCover(opaqueFrameReady);
                    });
                }
            }

            @Override public void refreshSession(
                    PlayIntent intent, BooleanSupplier cancelled,
                    Consumer<Boolean> completion) {
                if (streamLoadingView != null) {
                    streamLoadingView.setStep(1,
                            getString(R.string.console_session_uncertain_title));
                }
                ComputerDetails host = currentHost(intent.hostId);
                if (host == null) {
                    completion.accept(false);
                    return;
                }
                refreshSessionProjection(host, intent.profileKey, accepted -> {
                    if (!cancelled.getAsBoolean()) completion.accept(accepted);
                });
            }

            @Override public void awaitPrepared(BooleanSupplier cancelled, Runnable settled) {
                SessionModel.Listener waiter = new SessionModel.Listener() {
                    @Override public void onSnapshot(SessionModel.Snapshot snapshot) {
                        boolean stop = cancelled.getAsBoolean();
                        if (!stop && snapshot.state == SessionModel.State.PREPARING) return;
                        sessionModel.unsubscribe(this);
                        if (!stop) settled.run();
                    }
                };
                waiter.onSnapshot(sessionModel.subscribe(waiter));
            }

            @Override public HostLaunchPreflight.Result preflight(
                    PlayIntent intent, HostLaunchPreflight.Action action,
                    long orchestrationId,
                    BooleanSupplier cancelled) {
                long timelineEpoch = streamLoadingEpoch;
                if (isChildIntent(intent)) {
                    childLaunchFailureReason = "";
                    childPairingRepairAvailable = false;
                    childLaunchHostId = normalizeId(intent.hostId);
                    childLaunchProfileId = normalizeId(intent.profileId);
                }
                LimeLog.info("Launch timeline epoch=" + timelineEpoch
                        + " host=" + intent.hostId
                        + " game=" + intent.transitionGameId()
                        + " +" + Math.max(0L, SystemClock.elapsedRealtime() - timelineEpoch)
                        + "ms preflight-start action=" + action);
                HostLaunchPreflight.Result result = hostLaunchPreflight.run(
                        HostLaunchPreflight.Request.from(
                                intent, action, orchestrationId), cancelled,
                        stage -> mainHandler.post(() -> {
                            if (!cancelled.getAsBoolean() && streamLoadingView != null) {
                                streamLoadingView.setStep(1,
                                        preflightStageMessage(stage, intent));
                            }
                        }));
                LimeLog.info("Launch timeline epoch=" + timelineEpoch
                        + " host=" + intent.hostId
                        + " game=" + intent.transitionGameId()
                        + " +" + Math.max(0L, SystemClock.elapsedRealtime() - timelineEpoch)
                        + "ms preflight-" + result.status.name().toLowerCase(Locale.ROOT));
                return result;
            }

            @Override public SessionOrchestrator.CloseResult switchRetained(
                    PlayIntent intent, NvApp target, SessionOrchestrator.SwitchStop stop,
                    BooleanSupplier cancelled)
                    throws SessionFailure.Raised {
                return switchRetainedProviderGame(intent, target, stop, cancelled);
            }

            @Override public SessionOrchestrator.CloseResult closeSession(
                    PlayIntent intent, SessionModel.Snapshot retained,
                    BooleanSupplier cancelled)
                    throws SessionFailure.Raised {
                ComputerDetails host = currentHost(intent.hostId);
                if (host == null) throw SessionFailure.CLOSE_HOST_UNAVAILABLE.raise();
                SessionModel.DirectTermination reconnectTermination = null;
                if (retained != null) {
                    Boolean terminated = terminateLiveRetainedForReplacement(
                            retained, cancelled);
                    if (terminated != null) {
                        LimeLog.info("Destructive close path=retained-live session="
                                + retained.streamSessionId + " success=" + terminated);
                        if (terminated) return SessionOrchestrator.CloseResult.CLOSED;
                        if (cancelled.getAsBoolean()) {
                            return SessionOrchestrator.CloseResult.CANCELLED;
                        }
                        throw SessionFailure.CLOSE_RETAINED_NOT_CLOSED.raise();
                    }
                    SessionModel.Snapshot current =
                            sessionModel.snapshot();
                    if (current.state == SessionModel.State.RECONNECT_REQUIRED
                            && current.streamSessionId.equals(retained.streamSessionId)) {
                        reconnectTermination = sessionModel.await(
                                new SessionEvent.DirectTerminationBegun(retained));
                    }
                    LimeLog.info("Destructive close path=reconnect-fallback session="
                            + retained.streamSessionId + " marked="
                            + (reconnectTermination != null));
                    if (reconnectTermination == null) {
                        throw SessionFailure.CLOSE_RETAINED_CHANGED.raise();
                    }
                } else {
                    LimeLog.info("Destructive close path=direct-nonretained host="
                            + intent.hostId);
                }
                HostSessionTermination.Outcome outcome = HostSessionTermination.Outcome.FAILED;
                try {
                    if (!cancelled.getAsBoolean()) {
                        mainHandler.post(() -> {
                            if (!cancelled.getAsBoolean() && streamLoadingView != null) {
                                streamLoadingView.setStep(1,
                                        getString(R.string.transition_closing_session));
                            }
                        });
                        outcome = terminateKnownControlPlane(host, intent.profileId,
                                knownProviderGameId(host));
                    }
                } finally {
                    if (reconnectTermination != null) {
                        // Awaited: the resolve that follows must see the model past TERMINATING.
                        sessionModel.await(SessionEvent.DirectTerminationCompleted.completed(
                                reconnectTermination, outcome.streamClosed(),
                                outcome == HostSessionTermination.Outcome
                                        .CLOSED_GAME_UNVERIFIED));
                    }
                }
                if (outcome.streamClosed()) {
                    // The host session is gone, and with it whatever was suspended in it.
                    SuspendedSessionStore.Session suspended =
                            suspendedSession(host.uuid, intent.profileId);
                    if (suspended != null && suspended.resumedAt == 0L) {
                        sessionModel.post(SessionEvent.SuspendedEnded.forExecution(host.uuid,
                                suspended.executionProfileId, suspended.suspendId));
                    }
                    // A game left on the host must not let the replacement launch.
                    if (!outcome.closed()) throw SessionFailure.CLOSE_GAME_LEFT.raise();
                    return SessionOrchestrator.CloseResult.CLOSED;
                }
                if (cancelled.getAsBoolean()) {
                    return SessionOrchestrator.CloseResult.CANCELLED;
                }
                if (outcome == HostSessionTermination.Outcome.OTHER_STREAM) {
                    throw SessionFailure.CLOSE_OTHER_STREAM.raise();
                }
                throw (reconnectTermination != null
                        ? SessionFailure.CLOSE_RECONNECT_FALLBACK_NOT_CLOSED
                        : SessionFailure.CLOSE_DIRECT_NOT_CLOSED).raise();
            }

            @Override public SessionOrchestrator.CloseResult hardResetSession(
                    PlayIntent intent, BooleanSupplier cancelled) throws SessionFailure.Raised {
                return hardResetForRecovery(intent, cancelled);
            }

            @Override public void sessionRecovering(PlayIntent intent) {
                ConsoleUiFeedback.makeText(ConsoleActivity.this,
                        R.string.console_session_recovering_notice, Toast.LENGTH_LONG).show();
            }

            @Override public void launch(PlayIntent intent, NvApp app,
                                         LaunchTransitionType type, String sourceSuspendId,
                                         boolean ownsFreshSunshineSession) {
                if (!isChildIntent(intent)) {
                    launchStream(intent, app, type, sourceSuspendId, ownsFreshSunshineSession);
                    return;
                }
                int launchToken = childEligibilityLaunchToken;
                BooleanSupplier cancelled = () -> launchToken != childEligibilityLaunchToken
                        || !isCurrentChildIntentContext(intent);
                executor.execute(() -> {
                    try {
                        startChildSession(intent, cancelled);
                        mainHandler.post(() -> {
                            if (cancelled.getAsBoolean()) {
                                endChildBinding(childBinding(intent), "cancelled");
                                return;
                            }
                            launchStream(intent, app, type, sourceSuspendId, ownsFreshSunshineSession);
                        });
                    } catch (IOException | RuntimeException error) {
                        ChildLaunchBinding binding = childBinding(intent);
                        if (binding != null && binding.started) endChildBinding(binding, "start_failed");
                        else if (binding != null) childLaunchBindings.remove(binding.key(), binding);
                        mainHandler.post(() -> {
                            if (cancelled.getAsBoolean()) return;
                            childLaunchFailureReason = error instanceof ChildLaunchFailure
                                    ? ((ChildLaunchFailure) error).reason
                                    : error instanceof IOException
                                    ? childEligibilityErrorReason((IOException) error) : "policy_unavailable";
                            LimeLog.warning("Child session start failed reason=" + childLaunchFailureReason);
                            preflightFailed(new HostLaunchPreflight.Failure(
                                    HostLaunchPreflight.Stage.TARGET_READY,
                                    HostLaunchPreflight.FailureReason.TARGET_UNAVAILABLE));
                        });
                    }
                });
            }

            private void launchStream(PlayIntent intent, NvApp app,
                                      LaunchTransitionType type, String sourceSuspendId,
                                      boolean ownsFreshSunshineSession) {
                ComputerDetails host = currentHost(intent.hostId);
                if (host == null) {
                    preflightFailed(new HostLaunchPreflight.Failure(
                            HostLaunchPreflight.Stage.NETWORK_READY,
                            HostLaunchPreflight.FailureReason.NETWORK_UNAVAILABLE));
                    return;
                }
                LaunchTransitionType transitionType = intent.transitionType(type);
                LaunchTransitionSpec transition = LaunchTransitionSpec.create(
                        intent.hostId, intent.profileId, transitionType, app.getAppId(),
                        intent.transitionGameId(), System.currentTimeMillis(),
                        intent.startBeforeStream);
                // The stream keeps the artwork its cover decided, even one fetched since.
                launchPreparedStream(host, app,
                        intent.quickLaunchId.isEmpty() ? null : intent.quickLaunchId,
                        transition, streamLoadingView == null
                                ? null : streamLoadingView.splashArtworkPath(),
                        sourceSuspendId, intent.playniteGameId,
                        ownsFreshSunshineSession
                                && transitionType == LaunchTransitionType.GAME
                                && PlayniteTargetResolver.isNeutralStream(app), intent);
            }

            @Override public SessionOrchestrator.PreparedWarmUp prepareHost(
                    PlayIntent intent, long request, BooleanSupplier cancelled) {
                ComputerDetails host = currentHost(intent.hostId);
                if (host == null) {
                    if (!cancelled.getAsBoolean()) {
                        setWarmUpStatus(intent.hostId, request, WARM_UP_ERROR);
                    }
                    return null;
                }
                setWarmUpStatus(intent.hostId, request,
                        host.state == ComputerDetails.State.OFFLINE
                                ? WARM_UP_WAKING : WARM_UP_PREPARING);
                HostLaunchPreflight.Result result = hostLaunchPreflight.run(
                        HostLaunchPreflight.Request.from(intent,
                                HostLaunchPreflight.Action.WARM_UP, request),
                        cancelled, ignored -> { });
                if (result.status != HostLaunchPreflight.Status.READY
                        || result.target == null || cancelled.getAsBoolean()) {
                    if (!cancelled.getAsBoolean()) {
                        setWarmUpStatus(intent.hostId, request, WARM_UP_ERROR);
                    }
                    return null;
                }
                ComputerDetails ready;
                try {
                    ready = refreshWarmUpHost(host);
                } catch (IOException unavailable) {
                    if (!cancelled.getAsBoolean()) {
                        setWarmUpStatus(intent.hostId, request, WARM_UP_ERROR);
                    }
                    return null;
                }
                // A host that answered the warm-up is not asleep.
                sessionModel.post(new SessionEvent.HostPowerCleared(host.uuid));
                // Listed here, on the worker, the codecs no longer stall the stream screen's
                // creation over the carousel (TV 2026-10-19: most of Game.onCreate's 0.7 s).
                MediaCodecHelper.preloadCodecList();
                if (!ConsoleActionCatalog.isPaired(ready)) {
                    setWarmUpStatus(intent.hostId, request, WARM_UP_ERROR);
                    return null;
                }
                // The streamer's app is the observation's fact: observe before deciding on it.
                boolean observed = refreshObservationNow(ready, intent.profileId);
                SessionSnapshot readyProjection = SessionOrchestrator.preLaunchProjection(
                        resolveSessionSnapshot(ready, intent.profileId), observed);
                return new SessionOrchestrator.PreparedWarmUp(
                        result.target, ready, intent.profileKey, readyProjection);
            }

            @Override public void showWarmUpStatus(PlayIntent intent, long request,
                                                   SessionOrchestrator.WarmUpStart status) {
                setWarmUpStatus(intent.hostId, request,
                        status == SessionOrchestrator.WarmUpStart.PREPARING ? WARM_UP_PREPARING
                                : status == SessionOrchestrator.WarmUpStart.LIVE ? WARM_UP_READY
                                : WARM_UP_ERROR);
            }

            @Override public boolean mayOpenPreparedHost(boolean playRequested) {
                // Starting the stream screen blocks the main thread: never under the intro,
                if (introView != null && introView.running()) return false;
                if (playRequested) return true;
                // nor while the user moves through the carousel (TV 2026-10-19: ~2 s of
                // dropped frames); it opens once they pause.
                long quiet = SystemClock.uptimeMillis() - lastKeyDownAt;
                if (quiet >= WARM_UP_OPEN_QUIET_MS) return true;
                mainHandler.removeCallbacks(openPreparedHostWhenQuiet);
                mainHandler.postDelayed(openPreparedHostWhenQuiet, WARM_UP_OPEN_QUIET_MS - quiet);
                return false;
            }

            @Override public SessionResumeManager.PendingSession pendingResume() {
                return sessionModel.records().pending;
            }

            @Override public void launchPreparedHost(
                    SessionOrchestrator.PreparedWarmUp prepared,
                    SessionOrchestrator.WarmUpLaunch launch,
                    SessionResumeManager.PendingSession record,
                    boolean ownsFreshSunshineSession, PlayIntent pendingGame, long request) {
                if (prepared.host == null || request != warmUpRequest
                        || !prepared.host.uuid.equalsIgnoreCase(warmUpClientHostId)) return;
                launchPreparedWarmUp(prepared, launch, record, ownsFreshSunshineSession,
                        pendingGame, request);
            }

            @Override public void preparationFailed() {
                setWarmUpStatus(warmUpClientHostId, warmUpRequest, WARM_UP_ERROR);
                if (streamLoadingView != null
                        && loadingLayer.getVisibility() == View.VISIBLE) {
                    streamLoadingView.showError(
                            getString(R.string.console_warm_up_error),
                            getString(R.string.console_warm_up_fallback_details));
                }
            }


            @Override public void preflightFailed(HostLaunchPreflight.Failure failure) {
                if (streamLoadingView != null) {
                    String childReason = childLaunchFailureReason;
                    boolean pairingRequired = "pairing_required".equals(
                            childReason == null ? "" : childReason.trim().toLowerCase(Locale.ROOT));
                    childPairingRepairAvailable = pairingRequired;
                    childLaunchFailureReason = "";
                    if (pairingRequired) {
                        streamLoadingView.setRetryLabel(
                                R.string.console_child_launch_pairing_action);
                    }
                    streamLoadingView.showError(
                            getString(failure.stage == HostLaunchPreflight.Stage.NETWORK_READY
                                    ? R.string.console_host_not_ready
                                    : R.string.playnite_launch_unavailable),
                            childReason.isEmpty()
                                    ? preflightFailureMessage(failure)
                                    : childLaunchFailureMessage(childReason));
                }
            }

            @Override public void sessionFailed(PlayIntent intent, SessionFailure reason) {
                String childReason = childLaunchFailureReason;
                childLaunchFailureReason = "";
                if (reason == SessionFailure.SWITCH_FAILED && isChildIntent(intent)
                        && !childReason.isEmpty()) {
                    // The switch kept the stream and its game; only the child's refusal shows.
                    if (streamLoadingView == null) {
                        ComputerDetails host = currentHost(intent.hostId);
                        ConsoleActivity.this.showLoading(
                                host == null ? intent.hostId : host.name, intent.appName);
                    }
                    streamLoadingView.showError(getString(R.string.playnite_launch_unavailable),
                            childLaunchFailureMessage(childReason), reason.code);
                    return;
                }
                showSessionFailure(reason, intent.hostId, intent.appName);
            }
        }, executor, mainHandler::post, PlayQueue.of(this));
    }

    /** A refusal is never silent: one raised before any loading layer raises its own. */
    private void showSessionFailure(SessionFailure reason, String hostId, String appName) {
        if (streamLoadingView == null) {
            ComputerDetails host = currentHost(hostId);
            showLoading(host == null ? hostId : host.name, appName);
        }
        streamLoadingView.showError(
                getString(reason.title), getString(reason.details), reason.code);
    }

    /** For failures outside the orchestrator: records, then shows the failure screen. */
    private void sessionFailed(SessionFailure reason, long orchestrationId,
                               String hostId, String profileId, String appName,
                               String detail) {
        reason.record(orchestrationId, hostId, profileId,
                resolveSessionSnapshot(currentHost(hostId), profileId), detail, "");
        showSessionFailure(reason, hostId, appName);
    }

    private Boolean terminateLiveRetainedForReplacement(
            SessionModel.Snapshot retained,
            BooleanSupplier cancelled) throws SessionFailure.Raised {
        AtomicReference<HostSessionTermination.Outcome> result = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        // Runs on the orchestration worker: waiting for the model never blocks the main thread.
        SessionModel.TerminationResult started = sessionModel.await(
                new SessionEvent.TerminateRequested(retained.streamSessionId, "",
                        terminationOutcome(outcome -> {
                            result.set(outcome);
                            completed.countDown();
                        })));
        if (started == SessionModel.TerminationResult.NO_CONTROLLER) {
            return null;
        }
        if (started != SessionModel.TerminationResult.STARTED
                && started != SessionModel.TerminationResult.IN_PROGRESS) {
            throw SessionFailure.CLOSE_RETAINED_TERMINATION_UNAVAILABLE.raise();
        }
        long deadline = SystemClock.uptimeMillis() + 60_000L;
        try {
            while (result.get() == null && !cancelled.getAsBoolean()
                    && SystemClock.uptimeMillis() < deadline) {
                completed.await(100L, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw SessionFailure.CLOSE_RETAINED_INTERRUPTED.raise();
        }
        if (cancelled.getAsBoolean()) return false;
        if (result.get() == null) throw SessionFailure.CLOSE_RETAINED_TIMEOUT.raise();
        // A game left on the host must not let the replacement launch.
        if (result.get() == HostSessionTermination.Outcome.CLOSED_GAME_UNVERIFIED) {
            throw SessionFailure.CLOSE_GAME_LEFT.raise();
        }
        return result.get().closed();
    }

    private void beginInstallationSupportStream(ComputerDetails host, NvApp app,
                                                String playniteGameId) {
        if (host == null || app == null) return;
        LaunchTransitionType transitionType = LaunchTransitionType.GENERIC;
        String loadingArtworkGameId = playniteGameId;
        if (handleRetainedStreamLaunch(host, app, playniteGameId)) return;
        if (managerBinder == null) {
            ConsoleUiFeedback.makeText(this, R.string.console_initializing, Toast.LENGTH_SHORT).show();
            return;
        }
        String transitionProfileId = selectedProfileId(host.uuid);
        int token = hostPreparationGeneration.incrementAndGet();
        LaunchTransitionSpec transition = LaunchTransitionSpec.create(
                host.uuid, transitionProfileId, transitionType,
                app.getAppId(), playniteGameId,
                System.currentTimeMillis());
        SuspendedSessionStore.Session suspendedLaunch =
                suspendedSession(host.uuid, transitionProfileId);
        boolean restoringSuspendedSession = suspendedLaunch != null
                && suspendedLaunch.resumedAt == 0L
                && suspendedLaunch.sunshineAppId == app.getAppId();
        String loadingArtworkPath = coverArtworkPath(
                host.uuid, selectedProfileId(host.uuid), loadingArtworkGameId, "launch");
        showLoading(host.name, app.getAppName(), loadingArtworkPath);
        Runnable startAfterOverlayFrame = () -> executor.execute(() -> {
            ComputerDetails ready = HostReadiness.await(
                    () -> managerBinder != null ? managerBinder.getComputer(host.uuid) : null,
                    host,
                    () -> token != hostPreparationGeneration.get() || !active,
                    message -> setLoadingStatus(token, message),
                    getString(R.string.console_wol_status, host.name),
                    getString(R.string.console_waiting_stream_ports));
            if (ready != null && restoringSuspendedSession) {
                PlayniteTransitionGateway gateway = PlayniteTransitionGateway.connect(
                        this, host.uuid, ready.activeAddress != null
                                ? ready.activeAddress.address : null,
                        gatewayActorContext(host.uuid, transitionProfileId));
                if (gateway != null) {
                    try {
                        gateway.focusGame();
                        Thread.sleep(350L);
                    } catch (IOException ignored) {
                        // Stream resume remains available when foreground restore fails.
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (ready != null && SessionOrchestrator.anotherAppRuns(
                    SessionOrchestrator.preLaunchProjection(
                            resolveSessionSnapshot(ready, transitionProfileId),
                            refreshObservationNow(ready, transitionProfileId)), app.getAppId())
                    && !terminateKnownControlPlane(ready, transitionProfileId,
                    knownProviderGameId(ready)).closed()) {
                mainHandler.post(() -> {
                    if (token != hostPreparationGeneration.get() || !active) return;
                    sessionFailed(SessionFailure.SUPPORT_PREVIOUS_NOT_CLOSED, 0L,
                            host.uuid, transitionProfileId, app.getAppName(), "");
                });
                return;
            }
            mainHandler.post(() -> {
                if (token != hostPreparationGeneration.get() || !active) return;
                if (ready == null) {
                    sessionFailed(SessionFailure.SUPPORT_HOST_NOT_READY, 0L,
                            host.uuid, transitionProfileId, app.getAppName(), "");
                    return;
                }
                launchPreparedStream(ready, app, "", transition, loadingArtworkPath,
                        restoringSuspendedSession ? suspendedLaunch.suspendId : "",
                        restoringSuspendedSession ? suspendedLaunch.playniteGameId : "", false);
            });
        });
        afterLoadingCover(startAfterOverlayFrame);
    }

    /** Every loading cover's wait: its view has one, so one fence per view. */
    private static final String LOADING_COVER_FENCE = "console-loading";

    /**
     * Runs {@code effect} once the loading cover's frame settled, also when it never draws
     * (UNAVAILABLE); a Cancel on the cover (CANCELLED) leaves the effect to the cancel path.
     */
    private void afterLoadingCover(Runnable effect) {
        if (streamLoadingView == null) {
            effect.run();
            return;
        }
        streamLoadingView.frames().await(LOADING_COVER_FENCE,
                PresentationBarrier.COVER_DEADLINE_MS,
                LaunchTransitionController.runUnlessCancelled(effect));
    }

    private void launchPreparedStream(ComputerDetails host, NvApp app,
                                      String quickLaunchKey,
                                      LaunchTransitionSpec transition,
                                      String loadingArtworkPath,
                                      String sourceSuspendId,
                                      String sourceSuspendPlayniteGameId,
                                      boolean ownsFreshSunshineSession) {
        launchPreparedStream(host, app, quickLaunchKey, transition, loadingArtworkPath,
                sourceSuspendId, sourceSuspendPlayniteGameId,
                ownsFreshSunshineSession, false, null, true, null);
    }

    private void launchPreparedStream(ComputerDetails host, NvApp app,
                                      String quickLaunchKey,
                                      LaunchTransitionSpec transition,
                                      String loadingArtworkPath,
                                      String sourceSuspendId,
                                      String sourceSuspendPlayniteGameId,
                                      boolean ownsFreshSunshineSession,
                                      PlayIntent playIntent) {
        launchPreparedStream(host, app, quickLaunchKey, transition, loadingArtworkPath,
                sourceSuspendId, sourceSuspendPlayniteGameId,
                ownsFreshSunshineSession, false, null, true, playIntent);
    }

    private void launchPreparedWarmUp(SessionOrchestrator.PreparedWarmUp prepared,
                                      SessionOrchestrator.WarmUpLaunch launch,
                                      SessionResumeManager.PendingSession record,
                                      boolean ownsFreshSunshineSession,
                                      PlayIntent pendingGame, long request) {
        // The game the host runs is read when the warm-up launches, from the one projection.
        LaunchTransitionSpec transition = SessionOrchestrator.warmUpTransition(prepared,
                resolveSessionSnapshot(prepared.host, prepared.profileKey.profileId),
                System.currentTimeMillis());
        if (launch == SessionOrchestrator.WarmUpLaunch.RECONNECT) {
            Intent resume = SessionResumeManager.buildResumeIntent(this, record);
            if (resume == null) {
                setWarmUpStatus(prepared.host.uuid, request, WARM_UP_ERROR);
                return;
            }
            applyWarmUpIntent(resume, transition, pendingGame);
            refreshSessionOnResume = true;
            startActivity(resume);
            overridePendingTransition(0, 0);
            return;
        }
        SuspendedSessionStore.Session suspended =
                suspendedSession(prepared.host.uuid, prepared.profileKey.profileId);
        String sourceSuspendId = suspended != null && suspended.resumedAt == 0L
                && suspended.sunshineAppId == prepared.target.getAppId()
                ? suspended.suspendId : "";
        launchPreparedStream(prepared.host, prepared.target, null, transition, null,
                sourceSuspendId, sourceSuspendId.isEmpty() ? "" : suspended.playniteGameId,
                ownsFreshSunshineSession, true, pendingGame, false, null);
    }

    private void applyWarmUpIntent(Intent intent, LaunchTransitionSpec transition,
                                   PlayIntent pendingGame) {
        // A saved record's presentation belongs to its game; the warm-up's game is the host's.
        boolean recordGame = !transition.playniteGameId.isEmpty()
                && transition.playniteGameId.equalsIgnoreCase(normalizeId(intent.getStringExtra(
                Game.EXTRA_TRANSITION_PLAYNITE_GAME_ID)));
        intent.putExtra(Game.EXTRA_AUTO_WARM_UP, true);
        intent.putExtra(Game.EXTRA_NEUTRAL_STREAM_TARGET, true);
        intent.putExtra(Game.EXTRA_STREAM_TARGET_NAME,
                PlayniteTargetResolver.MOONWAKER_STREAM_NAME);
        intent.putExtra(Game.EXTRA_TRANSITION_ID, transition.id);
        intent.putExtra(Game.EXTRA_PROFILE_ID, transition.profileId);
        intent.putExtra(Game.EXTRA_TRANSITION_TYPE, transition.type.name());
        intent.putExtra(Game.EXTRA_TRANSITION_HOST_ID, transition.hostId);
        intent.putExtra(Game.EXTRA_TRANSITION_PLAYNITE_GAME_ID,
                transition.playniteGameId);
        intent.putExtra(Game.EXTRA_TRANSITION_CREATED_AT, transition.createdAtMillis);
        intent.putExtra(Game.EXTRA_TRANSITION_START_BEFORE_STREAM,
                transition.startProviderBeforeStream);
        if (!recordGame) {
            // The host's current game as this profile's library lists it; otherwise neutral.
            HostProfileKey loaded = currentPlayniteProfileKey;
            PlayniteDashboardItem hostGame = transitionLibraryItem(
                    loaded, unfilteredPlayniteItems, transition);
            String artwork = hostGame == null ? null : coverArtworkPath(loaded.hostId,
                    loaded.profileId, hostGame.stableId(), "warm_up");
            intent.putExtra(Game.EXTRA_APP_NAME, hostGame != null ? hostGame.game.name
                    : PlayniteTargetResolver.MOONWAKER_STREAM_NAME);
            intent.removeExtra(Game.EXTRA_QUICK_LAUNCH_APP_KEY);
            if (artwork != null) intent.putExtra(Game.EXTRA_CONSOLE_LOADING_ARTWORK, artwork);
            else intent.removeExtra(Game.EXTRA_CONSOLE_LOADING_ARTWORK);
        }
        if (pendingGame != null) {
            intent.putExtra(EXTRA_WARM_UP_PENDING_GAME_ID, pendingGame.playniteGameId);
            intent.putExtra(EXTRA_WARM_UP_PENDING_GAME_NAME, pendingGame.appName);
            intent.putExtra(EXTRA_WARM_UP_PENDING_ARTWORK_ID,
                    pendingGame.loadingArtworkGameId);
            intent.putExtra(EXTRA_WARM_UP_PENDING_QUICK_LAUNCH,
                    pendingGame.quickLaunchId);
            intent.putExtra(EXTRA_WARM_UP_PENDING_REQUIRES_CONNECTOR,
                    pendingGame.requiresConnector);
            intent.putExtra(EXTRA_WARM_UP_PENDING_START_BEFORE_STREAM,
                    pendingGame.startBeforeStream);
        }
    }

    private void launchPreparedStream(ComputerDetails host, NvApp app,
                                      String quickLaunchKey,
                                      LaunchTransitionSpec transition,
                                      String loadingArtworkPath,
                                      String sourceSuspendId,
                                      String sourceSuspendPlayniteGameId,
                                      boolean ownsFreshSunshineSession,
                                      boolean autoWarmUp,
                                      PlayIntent pendingWarmUpGame,
                                      boolean recordHistory,
                                      PlayIntent playIntent) {
        // A game is not played by launching its stream: the stream screen records it once
        // the host confirmed the game ready.
        if (recordHistory && transition.type != LaunchTransitionType.GAME
                && transition.type != LaunchTransitionType.GAME_CONNECTION) {
            PlayHistory.recordApp(preferences, host.uuid, app.getAppId(),
                    System.currentTimeMillis());
        }
        String openingStatus = getString(R.string.console_opening_stream);
        if (streamLoadingView != null) streamLoadingView.setStep(2, openingStatus);
        Bundle presentation = new Bundle();
        presentation.putBoolean(Game.EXTRA_CONSOLE_LOADING, true);
        presentation.putString(Game.EXTRA_CONSOLE_LOADING_MESSAGE,
                streamLoadingView == null ? null : streamLoadingView.getCurrentMessage());
        // A hidden warm-up no one asked for starts now; its game's Play (a switch) brings its own.
        presentation.putLong(Game.EXTRA_CONSOLE_LOADING_EPOCH,
                autoWarmUp && pendingWarmUpGame == null
                        ? SystemClock.elapsedRealtime() : streamLoadingEpoch);
        presentation.putInt(Game.EXTRA_CONSOLE_LOADING_STEP, 2);
        presentation.putString(Game.EXTRA_CONSOLE_LOADING_STATUS, openingStatus);
        presentation.putBoolean(Game.EXTRA_CONSOLE_REDUCED_MOTION, reducedMotion);
        presentation.putString(Game.EXTRA_CONSOLE_LOADING_ARTWORK, loadingArtworkPath);
        presentation.putString(Game.EXTRA_TRANSITION_ID, transition.id);
        presentation.putString(Game.EXTRA_PROFILE_ID, transition.profileId);
        presentation.putString(Game.EXTRA_EXECUTION_PROFILE_ID,
                executionProfileId(transition.hostId, transition.profileId));
        HostGatewayClient.IntegrationProfile actor = integrationProfile(transition.hostId, transition.profileId);
        presentation.putBoolean(Game.EXTRA_CHILD_PROFILE, actor != null && actor.isChild());
        presentation.putBoolean(Game.EXTRA_CAPTURES, captures(transition.hostId));
        presentation.putString(Game.EXTRA_TRANSITION_TYPE, transition.type.name());
        presentation.putString(Game.EXTRA_TRANSITION_HOST_ID, transition.hostId);
        presentation.putString(Game.EXTRA_TRANSITION_PLAYNITE_GAME_ID,
                transition.playniteGameId);
        long controlGeneration = knownControlGeneration(
                transition.hostId, transition.profileId);
        if (controlGeneration > 0L) {
            presentation.putLong(Game.EXTRA_CONTROL_GENERATION, controlGeneration);
            presentation.putString(Game.EXTRA_CONTROL_INTENT, "replacement");
        }
        presentation.putLong(Game.EXTRA_TRANSITION_CREATED_AT, transition.createdAtMillis);
        presentation.putBoolean(Game.EXTRA_TRANSITION_START_BEFORE_STREAM,
                transition.startProviderBeforeStream);
        ChildLaunchBinding childBinding = childBinding(playIntent);
        if (childBinding != null && childBinding.started && !childBinding.ended
                && childBinding.matches(playIntent)) {
            presentation.putString(Game.EXTRA_CHILD_SESSION_ID, childBinding.sessionId);
            presentation.putString(Game.EXTRA_CHILD_EXECUTION_PROFILE_ID,
                    childBinding.executionProfileId);
            presentation.putString(Game.EXTRA_CHILD_GAME_ID, childBinding.gameId);
            presentation.putLong(Game.EXTRA_CHILD_DEADLINE_ELAPSED_MS,
                    childBinding.deadlineElapsedMs);
            presentation.putLong(Game.EXTRA_CHILD_POLICY_REVISION,
                    childBinding.policyRevision);
        }
        presentation.putString(Game.EXTRA_STREAM_TARGET_NAME, app.getAppName());
        // The cover names the game it launches, never the stream target that carries it.
        String gameTitle = playIntent == null ? ""
                : PlayniteTargetResolver.gameCoverTitle(playIntent.appName, app.getAppName());
        if (!gameTitle.isEmpty()) presentation.putString(Game.EXTRA_APP_NAME, gameTitle);
        presentation.putBoolean(Game.EXTRA_NEUTRAL_STREAM_TARGET,
                PlayniteTargetResolver.isNeutralStream(app));
        presentation.putBoolean(Game.EXTRA_FRESH_SUNSHINE_SESSION_OWNER,
                ownsFreshSunshineSession);
        if (playIntent != null && playIntent.isCalibration()) {
            presentation.putString(Game.EXTRA_AUTOPILOT_CALIBRATION_APP_KEY,
                    playIntent.calibrationAppKey);
            presentation.putString(Game.EXTRA_AUTOPILOT_VARIANT,
                    playIntent.calibrationVariant.name());
            presentation.putInt(Game.EXTRA_RUNTIME_WIDTH, playIntent.runtimeWidth);
            presentation.putInt(Game.EXTRA_RUNTIME_HEIGHT, playIntent.runtimeHeight);
            presentation.putInt(Game.EXTRA_RUNTIME_FPS, playIntent.runtimeFps);
            presentation.putInt(Game.EXTRA_RUNTIME_BITRATE_KBPS,
                    playIntent.runtimeBitrateKbps);
        }
        if (autoWarmUp) {
            presentation.putBoolean(Game.EXTRA_AUTO_WARM_UP, true);
            // A warm-up into the host's running game carries that game's cover for the Play
            // that takes it over, as a resumed warm-up does (applyWarmUpIntent).
            HostProfileKey loaded = currentPlayniteProfileKey;
            PlayniteDashboardItem hostGame = transitionLibraryItem(
                    loaded, unfilteredPlayniteItems, transition);
            if (hostGame != null) {
                presentation.putString(Game.EXTRA_APP_NAME, hostGame.game.name);
                presentation.putString(Game.EXTRA_CONSOLE_LOADING_ARTWORK, coverArtworkPath(
                        loaded.hostId, loaded.profileId, hostGame.stableId(), "warm_up"));
            }
        }
        if (pendingWarmUpGame != null) {
            presentation.putString(EXTRA_WARM_UP_PENDING_GAME_ID,
                    pendingWarmUpGame.playniteGameId);
            presentation.putString(EXTRA_WARM_UP_PENDING_GAME_NAME,
                    pendingWarmUpGame.appName);
            presentation.putString(EXTRA_WARM_UP_PENDING_ARTWORK_ID,
                    pendingWarmUpGame.loadingArtworkGameId);
            presentation.putString(EXTRA_WARM_UP_PENDING_QUICK_LAUNCH,
                    pendingWarmUpGame.quickLaunchId);
        }
        if (sourceSuspendId != null && !sourceSuspendId.isEmpty()) {
            presentation.putString(Game.EXTRA_SOURCE_SUSPEND_ID, sourceSuspendId);
            presentation.putString(Game.EXTRA_SOURCE_SUSPEND_PLAYNITE_GAME_ID,
                    sourceSuspendPlayniteGameId);
        }
        refreshSessionOnResume = true;
        ServerHelper.doStart(this, app, host, managerBinder, quickLaunchKey, presentation);
        // Navigation must not cancel a game already handed to the player.
        childStreamHandoff = presentation.containsKey(Game.EXTRA_CHILD_SESSION_ID)
                ? childBinding : null;
        if (childStreamHandoff != null) childStreamHandoff.handedToPlayer = true;
        overridePendingTransition(0, 0);
    }

    private boolean handleRetainedStreamLaunch(ComputerDetails host, NvApp app,
                                                String playniteGameId) {
        if (!isStreamHome()) return false;
        if (host != null && sessionSubscriber.holdsTarget(host.uuid,
                app == null ? StreamConfiguration.INVALID_APP_ID : app.getAppId(),
                playniteGameId)) {
            returnToRetainedStream();
        } else {
            ConsoleUiFeedback.makeText(this, R.string.console_stream_home_switch_blocked,
                    Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /** Starts the attaching Game with the selected target; it submits the switch itself. */
    private void attachPresentationForSwitch(
            long orchestrationId, PlayIntent intent, NvApp target,
            SessionOrchestrator.SwitchStop stop) {
        if (isFinishing()) {
            MoonWakerDiagnostics.record("WARN", "android.console",
                    "console.retained_switch.presentation",
                    "orchestration_id", orchestrationId, "status", "ACTIVITY_FINISHING",
                    "host_id", intent.hostId, "profile_id", intent.profileId,
                    "app_id", target.getAppId(), "game_id", intent.playniteGameId);
            return;
        }
        SessionResumeManager.PendingSession pending =
                sessionModel.records().pending;
        Intent attach = SessionResumeManager.buildResumeIntent(this, pending);
        if (attach == null) {
            MoonWakerDiagnostics.record("WARN", "android.console",
                    "console.retained_switch.presentation",
                    "orchestration_id", orchestrationId, "status", "RESUME_INTENT_UNAVAILABLE",
                    "host_id", intent.hostId, "profile_id", intent.profileId,
                    "app_id", target.getAppId(), "game_id", intent.playniteGameId);
            sessionFailed(SessionFailure.ATTACH_RESUME_INTENT_MISSING, orchestrationId,
                    intent.hostId, intent.profileId, intent.appName, "");
            return;
        }
        attach.putExtra(Game.EXTRA_PENDING_SWITCH_HOST_ID, intent.hostId)
                .putExtra(Game.EXTRA_PENDING_SWITCH_EXECUTION_PROFILE_ID,
                        executionProfileId(intent.hostId, intent.profileId))
                .putExtra(Game.EXTRA_PENDING_SWITCH_TARGET_PROFILE_ID, intent.profileId)
                .putExtra(Game.EXTRA_PENDING_SWITCH_TARGET_CHILD, isChildIntent(intent))
                .putExtra(Game.EXTRA_PENDING_SWITCH_APP_ID, target.getAppId())
                .putExtra(Game.EXTRA_PENDING_SWITCH_GAME_ID, intent.playniteGameId)
                .putExtra(Game.EXTRA_PENDING_SWITCH_GAME_NAME, intent.appName)
                .putExtra(Game.EXTRA_PENDING_SWITCH_STREAM_TARGET, target.getAppName())
                .putExtra(Game.EXTRA_PENDING_SWITCH_ARTWORK, coverArtworkPath(intent.hostId,
                        intent.profileId, intent.loadingArtworkGameId, "switch"))
                .putExtra(Game.EXTRA_PENDING_SWITCH_ORCHESTRATION_ID, orchestrationId)
                .putExtra(Game.EXTRA_PENDING_SWITCH_STOP, stop.name())
                // The switch's cover counts from this Home's Play, not from the attach.
                .putExtra(Game.EXTRA_CONSOLE_LOADING_EPOCH, streamLoadingEpoch);
        try {
            startActivity(attach);
        } catch (RuntimeException error) {
            MoonWakerDiagnostics.record("WARN", "android.console",
                    "console.retained_switch.presentation",
                    "orchestration_id", orchestrationId, "status", "START_ACTIVITY_FAILED",
                    "host_id", intent.hostId, "profile_id", intent.profileId,
                    "app_id", target.getAppId(), "game_id", intent.playniteGameId);
            sessionFailed(SessionFailure.ATTACH_START_ACTIVITY_FAILED, orchestrationId,
                    intent.hostId, intent.profileId, intent.appName, "");
            return;
        }
        MoonWakerDiagnostics.record("INFO", "android.console",
                "console.retained_switch.presentation",
                "orchestration_id", orchestrationId, "status", "START_ACTIVITY_DISPATCHED",
                "stage", "activity_start", "host_id", intent.hostId,
                "profile_id", intent.profileId, "app_id", target.getAppId(),
                "game_id", intent.playniteGameId);
        if (isStreamHome()) finish();
        // The stream's own switch cover takes over: no cross-fade between the two covers.
        overridePendingTransition(0, 0);
    }

    /** Shows the live stream: its presentation, or a new one when the transport outlived it. */
    private void resumeRetainedStream() {
        if (!sessionModel.hasAttachedPresentation()) {
            // The transport survived its Activity: reattach a presentation to it.
            Intent attach = SessionResumeManager.buildResumeIntent(this,
                    sessionModel.records().pending);
            if (attach != null) startActivity(attach);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void returnToRetainedStream() {
        if (isFinishing()) return;
        if (!sessionModel.canResumeInstantly()
                || !sessionModel.hasAttachedPresentation()) {
            SessionResumeManager.PendingSession pending =
                    sessionModel.records().pending;
            Intent resume = SessionResumeManager.buildResumeIntent(this, pending);
            if (resume != null) startActivity(resume);
        }
        boolean coverHandOff = loadingLayer.getVisibility() == View.VISIBLE;
        if (isStreamHome()) finish();
        // A launch or switch cover hands over to the stream's cover, already drawn under this
        // window: fading this one out over it blinked the cover (TV 2026-10-16).
        overridePendingTransition(0, coverHandOff ? 0 : android.R.anim.fade_out);
    }

    private void reconnectSavedSessionForActor(PlayIntent intent) {
        if (intent == null) return;
        SessionResumeManager.PendingSession pending =
                sessionModel.records().pending;
        String targetExecution = executionProfileId(intent.hostId, intent.profileId);
        String savedRefusal = pending == null ? "no_saved_session"
                : intent.hostId.equalsIgnoreCase(pending.hostUuid)
                && targetExecution.equals(pending.executionProfileId) ? null : "saved_session_mismatch";
        boolean actorHandoff = savedRefusal == null && !intent.profileId.equals(pending.profileId);
        if (!actorHandoff) {
            String refusal = savedRefusal != null ? savedRefusal
                    : startResumeActivity(intent, pending, null, false, 0L);
            if (refusal != null) sessionFailed(SessionFailure.RECONNECT_SAVED_SESSION_UNAVAILABLE,
                    0L, intent.hostId, intent.profileId, intent.appName, refusal);
            return;
        }
        BooleanSupplier cancelled = () -> !active
                || !intent.profileKey.equals(selectedProfileKey(intent.hostId));
        executor.execute(() -> {
            boolean timeAttachmentChanged = false;
            ChildLaunchBinding binding = null;
            long hostHandoffGeneration = 0L;
            try {
                if (!pending.childSessionId.isEmpty() && !isChildIntent(intent)) {
                    timeAttachmentChanged = true;
                    hostHandoffGeneration = releasePersistedChildTimeAttachment(
                            pending, intent.profileId).controlGeneration;
                }
                if (isChildIntent(intent)) {
                    binding = startChildSession(intent, cancelled);
                    timeAttachmentChanged |= binding != null && binding.started;
                }
                if (cancelled.getAsBoolean()) throw new IOException("actor_handoff_cancelled");
                ChildLaunchBinding accepted = binding;
                long acceptedHandoffGeneration = hostHandoffGeneration;
                mainHandler.post(() -> {
                    boolean handoffCancelled = cancelled.getAsBoolean();
                    String refusal = handoffCancelled ? "actor_handoff_cancelled"
                            : startResumeActivity(intent, pending, accepted, true, acceptedHandoffGeneration);
                    if (refusal == null) return;
                    failClosedActorHandoff(intent, pending, accepted);
                    if (!handoffCancelled) sessionFailed(SessionFailure.RECONNECT_ACTOR_HANDOFF_FAILED,
                            0L, intent.hostId, intent.profileId, intent.appName, refusal);
                });
            } catch (IOException | RuntimeException error) {
                LimeLog.warning("Saved session actor handoff failed: " + error.getMessage());
                if (timeAttachmentChanged) failClosedActorHandoff(intent, pending, binding);
                mainHandler.post(() -> { if (!cancelled.getAsBoolean()) sessionFailed(
                        SessionFailure.RECONNECT_ACTOR_HANDOFF_FAILED, 0L, intent.hostId,
                        intent.profileId, intent.appName, error.getMessage()); });
            }
        });
    }

    private ChildSessionClient.State releasePersistedChildTimeAttachment(
            SessionResumeManager.PendingSession pending, String nextActorProfileId)
            throws IOException {
        ComputerDetails host = currentHost(pending.hostUuid);
        String address = host == null || host.activeAddress == null
                ? null : host.activeAddress.address;
        GatewayConnection previous = hostGatewayStore.loadForHost(
                pending.hostUuid, address, pending.profileId);
        if (previous == null) throw new IOException("previous_child_gateway_unavailable");
        ChildSessionClient.State ended = new ChildSessionClient(new GatewayTransport()).end(
                previous, pending.childSessionId, "actor_replaced",
                nextActorProfileId);
        if (ended == null || !pending.childSessionId.equals(ended.sessionId)
                || !ChildSessionLifecycle.terminal(ended) || ended.cleanupRequired) {
            throw new IOException("previous_child_time_release_unconfirmed");
        }
        return ended;
    }

    private String startResumeActivity(PlayIntent intent,
                                       SessionResumeManager.PendingSession pending,
                                       ChildLaunchBinding binding,
                                       boolean actorHandoff,
                                       long hostHandoffGeneration) {
        Intent resume = SessionResumeManager.buildResumeIntent(this, pending);
        if (resume == null) return "resume_intent_unavailable";
        resume.putExtra(Game.EXTRA_PROFILE_ID, intent.profileId);
        resume.putExtra(Game.EXTRA_EXECUTION_PROFILE_ID,
                executionProfileId(intent.hostId, intent.profileId));
        resume.putExtra(Game.EXTRA_CHILD_PROFILE, isChildIntent(intent));
        resume.putExtra(Game.EXTRA_CAPTURES, captures(intent.hostId));
        if (actorHandoff) {
            resume.removeExtra(Game.EXTRA_CHILD_SESSION_ID);
            resume.removeExtra(Game.EXTRA_CHILD_EXECUTION_PROFILE_ID);
            resume.removeExtra(Game.EXTRA_CHILD_GAME_ID);
            resume.removeExtra(Game.EXTRA_CHILD_DEADLINE_ELAPSED_MS);
            resume.removeExtra(Game.EXTRA_CHILD_POLICY_REVISION);
            resume.putExtra(Game.EXTRA_CONTROL_INTENT, "reauthorize");
            if (hostHandoffGeneration > 0L) {
                resume.putExtra(Game.EXTRA_CONTROL_GENERATION,
                        hostHandoffGeneration);
            }
            if (binding != null && binding.started && !binding.ended) {
                if (binding.controlGeneration > 0L) {
                    resume.putExtra(Game.EXTRA_CONTROL_GENERATION,
                            binding.controlGeneration);
                }
                resume.putExtra(Game.EXTRA_CHILD_SESSION_ID, binding.sessionId);
                resume.putExtra(Game.EXTRA_CHILD_EXECUTION_PROFILE_ID,
                        binding.executionProfileId);
                resume.putExtra(Game.EXTRA_CHILD_GAME_ID, binding.gameId);
                resume.putExtra(Game.EXTRA_CHILD_DEADLINE_ELAPSED_MS,
                        binding.deadlineElapsedMs);
                resume.putExtra(Game.EXTRA_CHILD_POLICY_REVISION, binding.policyRevision);
            }
        }
        SessionModel.Snapshot retained =
                sessionModel.snapshot();
        if (pending.neutralStreamTarget
                && retained.state == SessionModel.State.RECONNECT_REQUIRED
                && (!retained.hostId.equalsIgnoreCase(pending.hostUuid)
                || !retained.executionProfileId.equals(pending.executionProfileId)
                || retained.appId != pending.appId
                || !retained.streamSessionId.equals(pending.streamSessionId))) return "retained_mismatch";
        if (warmUpCarriesPendingGame(tracksWarmUp(), warmUpClientHostId, pending, retained))
            applyWarmUpIntent(resume, LaunchTransitionSpec.create(
                pending.hostUuid, intent.profileId, LaunchTransitionType.GENERIC,
                pending.appId, "", System.currentTimeMillis()), intent);
        if (binding != null) binding.handedToPlayer = true;
        refreshSessionOnResume = true;
        startActivity(resume);
        overridePendingTransition(0, 0);
        return null;
    }

    /**
     * The live warm-up attempt carries the intent's game into this saved neutral stream: only
     * into a marker that names no game. Play of another game over a marker still naming a game
     * is a replacement, not a reconnect.
     */
    static boolean warmUpCarriesPendingGame(boolean tracksWarmUp, String warmUpHostId,
                                            SessionResumeManager.PendingSession pending,
                                            SessionModel.Snapshot retained) {
        return tracksWarmUp && pending != null && pending.neutralStreamTarget
                && warmUpHostId.equalsIgnoreCase(pending.hostUuid)
                && retained.state == SessionModel.State.RECONNECT_REQUIRED
                && retained.playniteGameId.isEmpty();
    }

    private void failClosedActorHandoff(PlayIntent intent,
                                        SessionResumeManager.PendingSession pending,
                                        ChildLaunchBinding newBinding) {
        executor.execute(() -> {
            boolean released = releaseNewChildTimeAttachmentAndWait(newBinding);
            ComputerDetails host = currentHost(intent.hostId);
            boolean stopped = terminateKnownControlPlane(
                    host, intent.profileId, pending.playniteGameId).closed();
            LimeLog.warning("Saved session actor handoff compensation child_released="
                    + released + " stopped=" + stopped);
            mainHandler.post(() -> refreshSessionState(intent.hostId));
        });
    }

    private boolean releaseNewChildTimeAttachmentAndWait(ChildLaunchBinding binding) {
        if (binding == null || !binding.started || binding.ended) return true;
        synchronized (binding) {
            if (binding.ended) return true;
            binding.endRequested = true;
        }
        try {
            ChildSessionClient client = new ChildSessionClient(new GatewayTransport());
            ChildSessionClient.State state = client.end(
                    binding.connection, binding.sessionId, "cancelled");
            long deadline = SystemClock.elapsedRealtime() + 30_000L;
            while (!childEndConfirmed(state)
                    && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(100L);
                state = client.status(binding.connection, binding.sessionId);
            }
            if (!childEndConfirmed(state)) {
                binding.endRequested = false;
                return false;
            }
            binding.state = state;
            binding.ended = true;
            childLaunchBindings.remove(binding.key(), binding);
            return true;
        } catch (IOException | RuntimeException error) {
            binding.endRequested = false;
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            binding.endRequested = false;
            return false;
        }
    }

    private SessionOrchestrator.CloseResult switchRetainedProviderGame(
            PlayIntent intent, NvApp target, SessionOrchestrator.SwitchStop stop,
            BooleanSupplier cancelled)
            throws SessionFailure.Raised {
        AtomicReference<SessionModel.SwitchOutcome> outcome =
                new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>("");
        CountDownLatch completed = new CountDownLatch(1);
        SessionModel.SwitchResult started = sessionModel.await(new SessionEvent.SwitchRequested(
                intent.hostId, executionProfileId(intent.hostId, intent.profileId),
                intent.profileId, isChildIntent(intent), target.getAppId(),
                intent.playniteGameId, intent.appName, target.getAppName(),
                coverArtworkPath(intent.hostId, intent.profileId,
                        intent.loadingArtworkGameId, "switch"), streamLoadingEpoch, 0L,
                stop, cancelled, (result, reason) -> {
                    outcome.set(result);
                    error.set(reason == null ? "" : reason);
                    completed.countDown();
                }));
        if (started != SessionModel.SwitchResult.STARTED) {
            throw SessionFailure.SWITCH_NOT_ELIGIBLE.raise();
        }
        long deadline = SystemClock.uptimeMillis() + 60_000L;
        try {
            while (outcome.get() == null && SystemClock.uptimeMillis() < deadline) {
                completed.await(100L, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw SessionFailure.SWITCH_INTERRUPTED.raise();
        }
        SessionModel.SwitchOutcome result = outcome.get();
        // REUSED only says the stream carries the request from here, not that a game started.
        if (result == SessionModel.SwitchOutcome.REUSED) {
            return SessionOrchestrator.CloseResult.REUSED;
        }
        if (result == SessionModel.SwitchOutcome.CANCELLED) {
            return SessionOrchestrator.CloseResult.CANCELLED;
        }
        if (result == null) throw SessionFailure.SWITCH_TIMEOUT.raise();
        // A child's host refusal (time, schedule, sharing) shows the child's own message.
        if (isChildIntent(intent) && childRefusalMessage(error.get()) != 0) {
            childLaunchFailureReason = error.get();
        }
        throw SessionFailure.switchFailed(error.get());
    }

    /**
     * Whose the loading cover is: bumped when a cover is shown, handed to another (queued)
     * Play, or taken down. A Play's late launch artwork lands only while it is unchanged.
     */
    private int launchCoverGeneration;

    private void showLoading(String hostName, String appName) {
        showLoading(hostName, appName, null);
    }

    /** {@code loadingArtworkPath}: the game's full backdrop; null shows the standard one. */
    private void showLoading(String hostName, String appName, String loadingArtworkPath) {
        childPairingRepairAvailable = false;
        if (discordDmNotifications != null) {
            discordDmNotifications.setPresentationBlocked(discordDmHostToken, true);
        }
        if (consoleAudioEngine != null) consoleAudioEngine.setMenuVisible(false);
        lastContentFocus = getCurrentFocus();
        lastContentFocusTag = lastContentFocus == null ? null : lastContentFocus.getTag();
        homeLayer.setVisibility(View.GONE);
        loadingLayer.setVisibility(View.VISIBLE);
        loadingLayer.bringToFront();
        if (streamLoadingView != null) {
            streamLoadingView.stop();
            loadingLayer.removeView(streamLoadingView);
        }
        launchCoverGeneration++;
        // The user's action that began this transition: every cover of it counts from here.
        streamLoadingEpoch = SystemClock.elapsedRealtime();
        streamLoadingView = new ConsoleStreamLoadingView(this,
                appDisplayName(appName), null, reducedMotion, streamLoadingEpoch);
        streamLoadingView.setSplashArtwork(loadingArtworkPath);
        loadingLayer.addView(streamLoadingView, match());
        streamLoadingView.setActions(new ConsoleStreamLoadingView.Actions() {
            @Override public void onCancel() {
                childPairingRepairAvailable = false;
                if (streamLoadingView != null) {
                    streamLoadingView.frames().cancel(LOADING_COVER_FENCE);
                }
                // Back on a queued Play's cover cancels that Play (the queue is emptied).
                if (queuedCoverIntent != null) {
                    queuedCoverIntent = null;
                    if (sessionOrchestrator != null) sessionOrchestrator.cancelQueued();
                }
                cancelCurrentPreparation();
                hostPreparationGeneration.incrementAndGet();
                // A failure dismissed after this Home's stream was closed leaves nothing under it.
                if (isStreamHome() && !sessionModel.snapshot().streamSessionId.equals(
                        launchedExtra(EXTRA_RETAINED_STREAM_SESSION_ID))) onRequestedStreamClosed();
                else showHome();
            }

            @Override public void onRetry() {
                if (childPairingRepairAvailable) {
                    childPairingRepairAvailable = false;
                    beginChildPairingRepair();
                    return;
                }
                if (sessionOrchestrator != null) sessionOrchestrator.retry();
            }

            @Override public void onShowStreamAnyway() {
                // Dashboard preparation never exposes a stream. This action becomes
                // available only inside Game after a readiness timeout.
            }
        });
        streamLoadingView.setStep(1, getString(R.string.console_preparing_host, hostName));
        streamLoadingView.bringToFront();
    }

    private void setLoadingStatus(int token, String message) {
        mainHandler.post(() -> {
            if (token == hostPreparationGeneration.get()
                    && loadingLayer.getVisibility() == View.VISIBLE) {
                if (streamLoadingView != null) streamLoadingView.setStep(1, message);
            }
        });
    }

    private void showHome() {
        launchCoverGeneration++;
        childPairingRepairAvailable = false;
        queuedCoverIntent = null;
        if (discordDmNotifications != null) {
            discordDmNotifications.setPresentationBlocked(discordDmHostToken, false);
        }
        if (consoleAudioEngine != null) consoleAudioEngine.setMenuVisible(true);
        if (streamLoadingView != null) {
            streamLoadingView.stopAndHide();
            loadingLayer.removeView(streamLoadingView);
            streamLoadingView = null;
        }
        loadingLayer.setVisibility(View.GONE);
        homeLayer.setVisibility(View.VISIBLE);
        View restore = lastContentFocus;
        if (restore != null && restore.isAttachedToWindow() && restore.isFocusable()) {
            restore.requestFocus();
        }
    }

    private void refreshControllers() {
        executor.execute(() -> {
            List<ControllerInfo> controllers = loadControllers();
            mainHandler.post(() -> renderControllers(controllers));
        });
    }

    private final Runnable controllerRefresh = new Runnable() {
        @Override public void run() {
            if (!active) return;
            refreshControllers();
            refreshDiscordIndicator();
            mainHandler.postDelayed(this, CONTROLLER_REFRESH_MS);
        }
    };

    private List<ControllerInfo> loadControllers() {
        List<ControllerInfo> controllers = new ArrayList<>();
        Set<String> descriptors = new HashSet<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || !isGamepad(device)) continue;
            String name = device.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith("virtual-")) continue;
            if (device.getDescriptor() != null && !descriptors.add(device.getDescriptor())) continue;
            int percentage = -1;
            int batteryStatus = BatteryState.STATUS_UNKNOWN;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BatteryState battery = device.getBatteryState();
                if (battery.isPresent()) {
                    if (!Float.isNaN(battery.getCapacity())) {
                        percentage = Math.max(0, Math.min(100,
                                Math.round(battery.getCapacity() * 100f)));
                    }
                    batteryStatus = battery.getStatus();
                }
            }
            controllers.add(new ControllerInfo(id, name, percentage, batteryStatus));
        }
        controllers.sort(Comparator.comparing(controller -> controller.name,
                String.CASE_INSENSITIVE_ORDER));
        return controllers;
    }

    private void renderControllers(List<ControllerInfo> controllers) {
        StringBuilder signature = new StringBuilder();
        StringBuilder devicesSignature = new StringBuilder();
        for (ControllerInfo controller : controllers) {
            signature.append(controller.deviceId).append(':').append(controller.name)
                    .append(':').append(controller.percentage).append(':')
                    .append(controller.batteryStatus).append(';');
            devicesSignature.append(controller.deviceId).append(':')
                    .append(controller.name).append(';');
        }
        boolean devicesChanged = !devicesSignature.toString().equals(
                renderedControllerDevicesSignature);
        if (devicesChanged) {
            rebuildExpandedNavigationLegend(controllers);
            if (hostSelectView != null) {
                hostSelectView.setControllers(!controllers.isEmpty(),
                        usesPlayStationButtons(controllers));
            }
        }
        if (profileSelectView != null) {
            profileSelectView.setControllers(!controllers.isEmpty(),
                    usesPlayStationButtons(controllers));
        }
        if (signature.toString().equals(renderedControllersSignature)) return;
        renderedControllersSignature = signature.toString();
        renderedControllerDevicesSignature = devicesSignature.toString();
        int scroll = controllerScroll.getScrollX();
        Object focusedTag = getCurrentFocus() != null ? getCurrentFocus().getTag() : null;
        controllerRow.removeAllViews();
        if (CONSOLE_UI_V2) {
            controllerScroll.setVisibility(
                    controllers.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
        controllersLabel.setText(controllers.isEmpty()
                ? getString(R.string.console_controllers_none)
                : getResources().getQuantityString(R.plurals.console_controller_count,
                        controllers.size(), controllers.size()));
        int player = 1;
        for (ControllerInfo controller : controllers) {
            controllerRow.addView(controllerCard(player++, controller),
                    CONSOLE_UI_V2 ? controllerPillSpacing() : cardSpacing());
        }
        if (CONSOLE_UI_V2 && devicesChanged && !controllers.isEmpty()) {
            controllerRow.setAlpha(0f);
            controllerRow.setTranslationX(-dp(24));
            controllerRow.animate().alpha(1f).translationX(0f)
                    .setDuration(reducedMotion ? 0 : 240).start();
        }
        controllerScroll.post(() -> controllerScroll.scrollTo(scroll != 0 ? scroll
                : preferences.getInt("controller_scroll", 0), 0));
        restoreTaggedFocus(controllerRow, focusedTag);
        wireHomeFocusNavigation();
    }

    private View controllerCard(int player, ControllerInfo controller) {
        if (CONSOLE_UI_V2) return controllerPill(player, controller);
        LinearLayout card = cardBase(dp(220), dp(50));
        card.setTag("controller:" + controller.deviceId);
        card.setFocusable(true);
        card.setClickable(true);
        int batteryColor = ControllerGlyphDrawable.batteryColor(
                controller.percentage, controller.isCharging());
        ImageView batteryIcon = new ImageView(this);
        batteryIcon.setImageDrawable(new ControllerBatteryDrawable(
                controller.percentage, controller.isCharging(), batteryColor));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.rightMargin = dp(10);
        card.addView(batteryIcon, iconParams);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(getString(R.string.console_controller_player_name,
                        player, compactControllerName(controller.name)),
                14, Color.WHITE, true);
        name.setSingleLine(true);
        copy.addView(name, wrapLinear());
        String battery;
        if (controller.percentage < 0) {
            battery = getString(R.string.console_controller_battery_unknown);
        } else if (controller.isFull()) {
            battery = getString(R.string.console_controller_battery_full, controller.percentage);
        } else if (controller.isCharging()) {
            battery = getString(R.string.console_controller_battery_charging, controller.percentage);
        } else {
            battery = getString(R.string.console_controller_battery_level, controller.percentage);
        }
        batteryIcon.setContentDescription(battery);
        TextView level = text(battery, 11, batteryColor, true);
        copy.addView(level, wrapLinear());
        card.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setContentDescription(getString(R.string.console_controller_description,
                name.getText(), battery));
        card.setOnClickListener(view -> showControllerMenu(player, controller));
        card.setOnFocusChangeListener((view, focused) -> styleCard(card, focused));
        return card;
    }

    private View controllerPill(int player, ControllerInfo controller) {
        LinearLayout pill = cardBase(dp(62), dp(26));
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(7), dp(2), dp(8), dp(2));
        pill.setTag("controller:" + controller.deviceId);
        int batteryColor = ControllerGlyphDrawable.batteryColor(
                controller.percentage, controller.isCharging());
        ImageView controllerIcon = new ImageView(this);
        controllerIcon.setImageDrawable(new ControllerGlyphDrawable(controller.name));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(20));
        iconParams.rightMargin = dp(5);
        pill.addView(controllerIcon, iconParams);

        String battery;
        if (controller.percentage < 0) {
            battery = getString(R.string.console_controller_battery_unknown);
        } else if (controller.isFull()) {
            battery = getString(R.string.console_controller_battery_full, controller.percentage);
        } else if (controller.isCharging()) {
            battery = getString(R.string.console_controller_battery_charging,
                    controller.percentage);
        } else {
            battery = getString(R.string.console_controller_battery_level,
                    controller.percentage);
        }
        TextView level = text(controller.percentage < 0
                        ? "?" : controller.percentage + "%",
                11, batteryColor, true);
        pill.addView(level, wrapLinear());
        controllerIcon.setContentDescription(compactControllerName(controller.name));
        pill.setContentDescription(getString(R.string.console_controller_description,
                getString(R.string.console_controller_player_name,
                        player, compactControllerName(controller.name)), battery));
        pill.setOnClickListener(view -> showControllerMenu(player, controller));
        pill.setOnFocusChangeListener((view, focused) ->
                styleControllerPill(pill, focused));
        styleControllerPill(pill, false);
        return pill;
    }

    private void showControllerMenu(int player, ControllerInfo controller) {
        List<View> actions = new ArrayList<>();
        if (ControllerActions.canIdentify(controller.deviceId)) {
            TextView identify = consolePanel.action(getString(R.string.console_controller_identify));
            identify.setOnClickListener(view -> ControllerActions.identify(
                    controller.deviceId, mainHandler, (success, message) -> mainHandler.post(() ->
                            ConsoleUiFeedback.makeText(this, message, Toast.LENGTH_LONG).show())));
            actions.add(identify);
        }
        TextView powerOff = consolePanel.action(getString(R.string.console_controller_power_off));
        powerOff.setOnClickListener(view ->
                confirmBluetoothAction(controller, BluetoothAction.POWER_OFF));
        actions.add(powerOff);
        TextView unpair = consolePanel.action(getString(R.string.console_controller_unpair));
        consolePanel.destructive(unpair);
        unpair.setOnClickListener(view ->
                confirmBluetoothAction(controller, BluetoothAction.UNPAIR));
        actions.add(unpair);
        showSidePanel(getString(R.string.console_controller_menu_eyebrow),
                getString(R.string.console_controller_menu_title, player,
                        compactControllerName(controller.name)),
                getString(R.string.console_controller_menu_details),
                actions.toArray(new View[0]));
    }

    private void confirmBluetoothAction(ControllerInfo controller, BluetoothAction action) {
        boolean unpair = action == BluetoothAction.UNPAIR;
        consoleFeedback.showConfirm(getString(unpair
                        ? R.string.console_controller_unpair_confirm_title
                        : R.string.console_controller_power_off_confirm_title),
                getString(unpair
                        ? R.string.console_controller_unpair_confirm_details
                        : R.string.console_controller_power_off_confirm_details),
                getString(android.R.string.cancel),
                getString(unpair ? R.string.console_controller_unpair
                        : R.string.console_controller_power_off), unpair,
                () -> runBluetoothAction(controller, action));
    }

    private void runBluetoothAction(ControllerInfo controller, BluetoothAction action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            pendingController = controller;
            pendingBluetoothAction = action;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
            return;
        }
        executeBluetoothAction(controller, action);
    }

    private void executeBluetoothAction(ControllerInfo controller, BluetoothAction action) {
        executor.execute(() -> {
            ControllerActions.ResultCallback callback = (success, message) -> mainHandler.post(() -> {
                if (success) {
                    consoleFeedback.notify(ConsoleUiFeedback.Kind.SUCCESS, message);
                    refreshControllers();
                } else {
                    consoleFeedback.showConfirm(
                            getString(R.string.console_operation_unavailable), message,
                            getString(R.string.console_close),
                            getString(R.string.console_bluetooth_settings), false,
                            this::openAccessorySettings);
                }
            });
            if (action == BluetoothAction.POWER_OFF) {
                ControllerActions.disconnect(this, controller.deviceId, callback);
            } else {
                ControllerActions.unpair(controller.deviceId, callback);
            }
        });
    }

    // Android TV's accessory pairing screen refuses callers without BLUETOOTH_CONNECT.
    private void openAccessorySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_ACCESSORY_SETTINGS);
            return;
        }
        if (!ControllerActions.openAccessorySettings(this)) {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.ERROR,
                    getString(R.string.first_run_bluetooth_missing));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_ACCESSORY_SETTINGS) {
            if (granted) {
                openAccessorySettings();
            } else {
                consoleFeedback.notify(ConsoleUiFeedback.Kind.ERROR,
                        getString(R.string.console_bluetooth_permission_required));
            }
            return;
        }
        if (requestCode != REQUEST_BLUETOOTH_CONNECT) return;
        ControllerInfo controller = pendingController;
        BluetoothAction action = pendingBluetoothAction;
        pendingController = null;
        pendingBluetoothAction = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && controller != null && action != null) {
            executeBluetoothAction(controller, action);
        } else {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.ERROR,
                    getString(R.string.console_bluetooth_permission_required));
        }
    }

    /** {@code genres}: the game's Playnite genres ("" without), for a gyro proposal. */
    private void requestStreamingAutopilot(ComputerDetails requestedHost, String appKey,
                                           String inheritedAppKey, String targetName,
                                           String genres, PlayIntent calibrationIntent) {
        ComputerDetails host = requestedHost == null ? null : currentHost(requestedHost.uuid);
        if (!ConsoleActionCatalog.isOnline(host) || !ConsoleActionCatalog.isPaired(host)
                || host.activeAddress == null || managerBinder == null) {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.ERROR,
                    getString(R.string.console_autopilot_unavailable));
            return;
        }
        if (calibrationIntent != null && hasStreamingAutopilotCalibrationSession(host)) {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.ERROR,
                    getString(R.string.console_autopilot_calibration_session_active));
            return;
        }
        GatewayConnection gateway = hostGatewayStore.loadForHost(
                host.uuid, host.activeAddress.address);
        if (gateway != null && resolveSessionSnapshot(host).hasActiveSession()) {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.INFO,
                    getString(R.string.console_autopilot_active_stream_fallback));
            beginStreamingAutopilot(host, appKey, inheritedAppKey, targetName,
                    genres, calibrationIntent, null);
            return;
        }
        if (gateway != null && isAutopilotNetworkMetered()) {
            TextView fallback = consolePanel.action(
                    getString(R.string.console_autopilot_skip_network_test));
            fallback.setOnClickListener(view -> beginStreamingAutopilot(
                    host, appKey, inheritedAppKey, targetName, genres, calibrationIntent, null));
            TextView measure = consolePanel.action(getString(R.string.console_autopilot_measure));
            measure.setOnClickListener(view -> beginStreamingAutopilot(
                    host, appKey, inheritedAppKey, targetName, genres, calibrationIntent,
                    gateway));
            showSidePanel(getString(R.string.console_options_section_streaming),
                    getString(R.string.console_autopilot_metered_title),
                    getString(R.string.console_autopilot_metered_details),
                    fallback, measure);
            return;
        }
        beginStreamingAutopilot(host, appKey, inheritedAppKey, targetName, genres,
                calibrationIntent, gateway);
    }

    private boolean hasStreamingAutopilotCalibrationSession(ComputerDetails host) {
        return resolveSessionSnapshot(host).state != SessionSnapshot.State.NONE
                || sessionModel.state()
                != SessionModel.State.NONE;
    }

    private boolean isAutopilotNetworkMetered() {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return connectivity != null && connectivity.isActiveNetworkMetered();
    }

    private void beginStreamingAutopilot(ComputerDetails requestedHost, String appKey,
                                         String inheritedAppKey, String targetName,
                                         String genres, PlayIntent calibrationIntent,
                                         GatewayConnection gateway) {
        ComputerDetails current = currentHost(requestedHost.uuid);
        if (current == null || current.activeAddress == null || managerBinder == null
                || selectedHostUuid == null
                || !current.uuid.equalsIgnoreCase(selectedHostUuid)) {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.ERROR,
                    getString(R.string.console_autopilot_unavailable));
            return;
        }

        cancelStreamingAutopilot(false);
        int generation = streamingAutopilotGeneration.incrementAndGet();
        String hostUuid = current.uuid;
        ComputerDetails host = new ComputerDetails(current);
        String uniqueId = managerBinder.getUniqueId();
        Display display = getWindowManager().getDefaultDisplay();
        String glRenderer = GlPreferences.readPreferences(this).glRenderer;

        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        cancel.setOnClickListener(view -> {
            cancelStreamingAutopilot(true);
            hideSidePanel();
        });
        showSidePanel(getString(R.string.console_options_section_streaming),
                getString(R.string.console_autopilot_analyzing_title),
                getString(R.string.console_autopilot_analyzing_details), consolePanel.busy(),
                cancel);

        streamingAutopilotTask = executor.submit(() -> {
            try {
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(host),
                        host.httpsPort, uniqueId, host.serverCert,
                        PlatformBinding.getCryptoProvider(this));
                String serverInfo = http.getServerInfo(true);
                StreamingAutopilotController.Analysis analysis =
                        StreamingAutopilotController.analyze(this, display, glRenderer,
                                http, serverInfo, gateway);
                if (Thread.currentThread().isInterrupted()) return;
                mainHandler.post(() -> {
                    if (generation != streamingAutopilotGeneration.get()) return;
                    streamingAutopilotTask = null;
                    if (!isStreamingAutopilotRequestCurrent(generation, hostUuid)) return;
                    showStreamingAutopilotPreview(generation, hostUuid, appKey,
                            inheritedAppKey, targetName, genres, calibrationIntent, analysis);
                });
            } catch (IOException | XmlPullParserException | RuntimeException error) {
                if (Thread.currentThread().isInterrupted()) return;
                mainHandler.post(() -> {
                    if (generation != streamingAutopilotGeneration.get()) return;
                    streamingAutopilotTask = null;
                    if (!isStreamingAutopilotRequestCurrent(generation, hostUuid)) return;
                    recordGlobalAutopilotResult(appKey, calibrationIntent,
                            getString(R.string.console_autopilot_error_title));
                    showStreamingAutopilotError(error);
                });
            }
        });
    }

    private boolean isStreamingAutopilotRequestCurrent(int generation, String hostUuid) {
        return generation == streamingAutopilotGeneration.get() && active
                && !isFinishing() && !isDestroyed() && selectedHostUuid != null
                && hostUuid.equalsIgnoreCase(selectedHostUuid);
    }

    private void showStreamingAutopilotPreview(int generation, String hostUuid,
                                               String appKey, String inheritedAppKey,
                                               String targetName, String genres,
                                               PlayIntent calibrationIntent,
                                               StreamingAutopilotController.Analysis analysis) {
        if (!analysis.recommendation.isPresent()) {
            recordGlobalAutopilotResult(appKey, calibrationIntent,
                    getString(R.string.console_autopilot_no_recommendation_title));
            TextView close = consolePanel.action(getString(R.string.console_close));
            close.setOnClickListener(view -> hideSidePanel());
            showSidePanel(getString(R.string.console_options_section_streaming),
                    getString(R.string.console_autopilot_no_recommendation_title),
                    getString(R.string.console_autopilot_no_recommendation_details,
                            streamingAutopilotNetworkSummary(analysis)), close);
            return;
        }
        // The stream menu offers this analysis' variants on this host later (mockup O3).
        StreamingAutopilotController.rememberBudget(this, hostUuid, analysis);

        PreferenceConfiguration current;
        if (appKey == null) {
            current = PreferenceConfiguration.readPreferences(this);
        } else if (inheritedAppKey != null) {
            current = AppPreferences.getEffectivePreferences(this, inheritedAppKey, appKey);
        } else {
            current = AppPreferences.getEffectivePreferences(this, appKey);
        }
        recordGlobalAutopilotResult(appKey, calibrationIntent, getString(
                R.string.console_autopilot_result_recommended,
                ConsoleAutopilotPanel.settings(this, analysis.variants.get(0))));
        String link = analysis.networkStatus == StreamingAutopilotController.NetworkStatus.MEASURED
                ? getString(R.string.console_autopilot_fact_link_value,
                Math.round(analysis.goodputKbps.getAsLong() / 1000f),
                Math.round(analysis.safeBitrateKbps.getAsInt() / 1000f)) : null;
        String pad = FeatureShowcaseLauncher.gyroPadName();
        ConsoleAutopilotPanel.GyroOffer gyro = calibrationIntent != null && pad != null
                && offersAutopilotGyro(appKey, genres)
                ? new ConsoleAutopilotPanel.GyroOffer(genres, pad, AppPreferences.globalGyro(
                PreferenceManager.getDefaultSharedPreferences(this))) : null;
        hideSidePanelImmediately();
        new ConsoleAutopilotPanel(this, settingsScreen).open(
                getString(calibrationIntent == null ? R.string.console_settings_title
                        : R.string.console_game_card), targetName, calibrationIntent != null,
                analysis, ConsoleAutopilotPanel.settings(this, current.width, current.height,
                        current.fps, current.bitrate), link, gyro,
                new ConsoleAutopilotPanel.Actions() {
                    @Override public void save(StreamingAutopilot.Variant variant,
                                               GyroAim.Settings gyroSetting) {
                        applyStreamingAutopilotVariant(generation, hostUuid, appKey, variant,
                                gyroSetting);
                    }

                    @Override public void playTest(StreamingAutopilot.Variant variant,
                                                   GyroAim.Settings gyroSetting) {
                        playStreamingAutopilotTest(generation, hostUuid, appKey, gyroSetting,
                                calibrationIntent.withCalibration(appKey, variant.kind,
                                        variant.width, variant.height, variant.fps,
                                        variant.bitrateKbps));
                    }
                });
    }

    /** Step 1's X, step 3's "Save without a test", or the global variant: kept, no test. */
    private void applyStreamingAutopilotVariant(int generation, String hostUuid, String appKey,
                                                StreamingAutopilot.Variant variant,
                                                GyroAim.Settings gyro) {
        if (!isStreamingAutopilotRequestCurrent(generation, hostUuid)) {
            showStreamingAutopilotError(
                    new IllegalStateException(getString(R.string.console_autopilot_stale)));
            return;
        }
        if (appKey == null) {
            StreamingAutopilotController.applyGlobal(this, variant);
            recordGlobalAutopilotResult(null, null, getString(
                    R.string.console_autopilot_result_applied,
                    ConsoleAutopilotPanel.settings(this, variant)));
        } else {
            StreamingAutopilotController.applyForApp(this, appKey, variant);
            if (gyro != null) AppPreferences.saveGyro(this, appKey, gyro);
        }
        consoleFeedback.notify(ConsoleUiFeedback.Kind.SUCCESS,
                getString(R.string.console_autopilot_applied));
    }

    /**
     * A shooter or action game, a pad with gyro, and no gyro aiming yet (the game's own, or
     * everyone's): before its test, Autopilot proposes aiming with the pad.
     */
    private boolean offersAutopilotGyro(String appKey, String genres) {
        return StreamingAutopilotController.proposesGyro(genres)
                && AppPreferences.getAppSettings(this, appKey).gyro == null
                && !AppPreferences.globalGyro(PreferenceManager
                        .getDefaultSharedPreferences(this)).aim;
    }

    /** "Play and test": the gyro choice is kept for the game, then the test starts if it may. */
    private void playStreamingAutopilotTest(int generation, String hostUuid, String appKey,
                                            GyroAim.Settings gyro, PlayIntent play) {
        ComputerDetails host = currentHost(hostUuid);
        if (!isStreamingAutopilotRequestCurrent(generation, hostUuid)) {
            showStreamingAutopilotError(
                    new IllegalStateException(getString(R.string.console_autopilot_stale)));
            return;
        }
        if (host == null || hasStreamingAutopilotCalibrationSession(host)) {
            showStreamingAutopilotError(new IllegalStateException(
                    getString(R.string.console_autopilot_calibration_session_active)));
            return;
        }
        if (gyro != null) AppPreferences.saveGyro(this, appKey, gyro);
        playProviderGame(play);
    }

    private String streamingAutopilotNetworkSummary(
            StreamingAutopilotController.Analysis analysis) {
        switch (analysis.networkStatus) {
            case MEASURED:
                return getString(R.string.console_autopilot_network_measured,
                        analysis.goodputKbps.getAsLong() / 1000d,
                        analysis.safeBitrateKbps.getAsInt() / 1000d);
            case FAILED:
                return getString(R.string.console_autopilot_network_failed);
            case TOO_SLOW:
                return getString(R.string.console_autopilot_network_too_slow,
                        analysis.goodputKbps.getAsLong() / 1000d);
            case UNAVAILABLE:
            default:
                return getString(R.string.console_autopilot_network_unavailable);
        }
    }

    private void showStreamingAutopilotError(Throwable error) {
        String detail = error == null || TextUtils.isEmpty(error.getMessage())
                ? getString(R.string.console_autopilot_error_unknown) : error.getMessage();
        TextView close = consolePanel.action(getString(R.string.console_close));
        close.setOnClickListener(view -> hideSidePanel());
        showSidePanel(getString(R.string.console_options_section_streaming),
                getString(R.string.console_autopilot_error_title),
                getString(R.string.console_autopilot_error_details, detail), close);
    }

    private void cancelStreamingAutopilot(boolean notify) {
        streamingAutopilotGeneration.incrementAndGet();
        Future<?> task = streamingAutopilotTask;
        streamingAutopilotTask = null;
        if (task != null) task.cancel(true);
        if (notify && consoleFeedback != null && !isFinishing() && !isDestroyed()) {
            consoleFeedback.notify(ConsoleUiFeedback.Kind.INFO,
                    getString(R.string.console_autopilot_cancelled));
        }
    }

    /** Indexes of the {@link #showSettings} categories that other surfaces open directly. */
    private static final int SETTINGS_CONTROLLERS = 2;
    private static final int SETTINGS_PROFILE = 5;
    private static final int SETTINGS_CHILDREN = 6;
    private static final int SETTINGS_DISCORD = 7;
    private static final int SETTINGS_APPEARANCE = 8;

    private void showSettings(int category) {
        showSettings(category, null);
    }

    /** {@code onClose} runs once Back has closed Settings (e.g. to return to Community). */
    private void showSettings(int category, Runnable onClose) {
        systemAudioSettings.reset();
        discordSettings.reset();
        // Children are listed again on each opening, so another TV's change shows.
        childrenList = null;
        childrenListFailed = false;
        childrenCategory = new ConsoleSettingsScreen.Category(
                getString(R.string.console_settings_children), this::childrenSettings);
        String title = getString(R.string.console_settings_title);
        settingsScreen.show("", title, title, Arrays.asList(
                new ConsoleSettingsScreen.Category(getString(R.string.console_stream_title),
                        getString(R.string.console_stream_video_details),
                        this::streamQualitySettings),
                new ConsoleSettingsScreen.Category(
                        getString(R.string.console_settings_section_sound), this::soundSettings),
                new ConsoleSettingsScreen.Category(getString(R.string.console_settings_controllers),
                        streamSettings::controllerRows),
                new ConsoleSettingsScreen.Category(getString(R.string.console_settings_sessions),
                        this::sessionSettings),
                new ConsoleSettingsScreen.Category(getString(R.string.console_settings_library),
                        this::librarySettings),
                new ConsoleSettingsScreen.Category(getString(R.string.console_settings_profile),
                        this::profileSettings),
                childrenCategory,
                new ConsoleSettingsScreen.Category(getString(R.string.console_settings_discord),
                        this::discordSettings),
                new ConsoleSettingsScreen.Category(
                        getString(R.string.console_settings_appearance), this::appearanceSettings),
                new ConsoleSettingsScreen.Category(getString(R.string.console_settings_about),
                        this::aboutSettings)), category, onClose);
    }

    private View[] discordSettings(ConsolePanel panel) {
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null || !discordAllowedForSelectedProfile()) {
            return discordSettings.accountRows(panel, null);
        }
        String profileId = selectedProfileId(host.uuid);
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress == null ? null : host.activeAddress.address, profileId);
        List<View> rows = new ArrayList<>(Arrays.asList(discordSettings.accountRows(panel, connection)));
        rows.add(hostSection(panel, host));
        rows.addAll(Arrays.asList(discordSettings.hostRows(panel, host.uuid, profileId, connection)));
        return rows.toArray(new View[0]);
    }

    private View[] streamQualitySettings(ConsolePanel panel) {
        TextView autopilot = panel.navigation(getString(R.string.console_streaming_autopilot_global));
        autopilot.setOnClickListener(v -> showStreamingAutopilotPanel());
        panel.described(autopilot, getString(R.string.console_settings_desc_autopilot));
        List<View> rows = new ArrayList<>();
        rows.add(autopilot);
        rows.addAll(Arrays.asList(streamSettings.qualityRows(panel)));
        return rows.toArray(new View[0]);
    }

    private View[] soundSettings(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        rows.add(panel.section(getString(R.string.console_stream_title)));
        rows.addAll(Arrays.asList(streamSettings.audioRows(panel)));
        rows.addAll(Arrays.asList(
                panel.section(getString(R.string.console_stream_category_interface)),
                settingsToggle(panel, R.string.console_ui_sounds_label,
                        R.string.console_settings_desc_ui_sounds, () -> uiSoundsEnabled,
                        () -> setUiSounds(!uiSoundsEnabled)),
                settingsToggle(panel, R.string.console_ambient_sounds_label,
                        R.string.console_settings_desc_ambient_sounds,
                        () -> ambientSoundsEnabled, () -> {
                    ambientSoundsEnabled = !ambientSoundsEnabled;
                    preferences.edit().putBoolean("ambient_sounds", ambientSoundsEnabled).apply();
                    if (consoleAudioEngine != null) {
                        consoleAudioEngine.setAmbientEnabled(ambientSoundsEnabled);
                    }
                }),
                panel.described(panel.stepper(getString(R.string.console_host_music_volume_label),
                        hostMusicVolume, value -> {
                            hostMusicVolume = value;
                            preferences.edit().putInt("host_music_volume", value).apply();
                            if (consoleAudioEngine != null) {
                                consoleAudioEngine.setHostSelectionVolume(value / 100f);
                            }
                        }), getString(R.string.console_settings_desc_host_music_volume)),
                panel.described(panel.stepper(getString(R.string.console_menu_music_volume_label),
                        menuMusicVolume, value -> {
                            menuMusicVolume = value;
                            preferences.edit().putInt("menu_music_volume", value).apply();
                            if (consoleAudioEngine != null) consoleAudioEngine.setMenuVolume(value / 100f);
                        }), getString(R.string.console_settings_desc_menu_music_volume)),
                panel.described(panel.stepper(getString(R.string.console_effects_volume_label),
                        effectsVolume, value -> {
                            effectsVolume = value;
                            preferences.edit().putInt("effects_volume", value).apply();
                            if (consoleAudioEngine != null) {
                                consoleAudioEngine.setEffectsVolume(value / 100f);
                            }
                        }), getString(R.string.console_settings_desc_effects_volume))));
        // The host's Windows audio; a child profile has no access to it.
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null || selectedProfileIsChild(host.uuid)) return rows.toArray(new View[0]);
        rows.add(hostSection(panel, host));
        String profileId = selectedProfileId(host.uuid);
        GatewayConnection connection = hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress == null ? null : host.activeAddress.address, profileId);
        if (connection == null) {
            rows.add(panel.note(getString(R.string.playnite_gateway_not_configured)));
        } else {
            rows.addAll(Arrays.asList(systemAudioSettings.rows(panel,
                    host.uuid + "/" + profileId, connection)));
        }
        return rows.toArray(new View[0]);
    }

    private View[] sessionSettings(ConsolePanel panel) {
        LinearLayout backgroundStream = panel.value(
                getString(R.string.console_background_stream_retention),
                backgroundStreamLabel(backgroundStreamRetentionMinutes));
        backgroundStream.setOnClickListener(v -> chooseBackgroundStreamRetention());
        panel.described(backgroundStream,
                getString(R.string.console_background_stream_retention_details));
        List<View> rows = new ArrayList<>();
        rows.add(backgroundStream);
        rows.addAll(Arrays.asList(streamSettings.sessionRows(panel)));
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) return rows.toArray(new View[0]);
        LinearLayout warmUp = settingsToggle(panel, R.string.console_auto_stream_warm_up, 0,
                () -> HostAutoWarmUpPreferences.isEnabled(preferences, host.uuid), () -> {
                    boolean enabled = !HostAutoWarmUpPreferences.isEnabled(preferences, host.uuid);
                    HostAutoWarmUpPreferences.setEnabled(preferences, host.uuid, enabled);
                    if (!enabled) cancelOwnedWarmUp(host.uuid, false);
                });
        LinearLayout freezeOnSwitch = settingsToggle(panel, R.string.console_freeze_on_switch, 0,
                () -> QuickResumePreferences.freezesOnSwitch(preferences, host.uuid),
                () -> QuickResumePreferences.setFreezesOnSwitch(preferences, host.uuid,
                        !QuickResumePreferences.freezesOnSwitch(preferences, host.uuid)));
        if (!hostPlus(host.uuid)) panel.disabled(freezeOnSwitch, getString(R.string.host_plus_required));
        rows.addAll(Arrays.asList(hostSection(panel, host), warmUp,
                panel.note(getString(R.string.console_auto_stream_warm_up_description)),
                freezeOnSwitch, panel.note(getString(R.string.console_freeze_on_switch_description))));
        return rows.toArray(new View[0]);
    }

    private void setUiSounds(boolean enabled) {
        uiSoundsEnabled = enabled;
        preferences.edit().putBoolean("ui_sounds", uiSoundsEnabled).apply();
        if (consoleAudioEngine != null) consoleAudioEngine.setEffectsEnabled(uiSoundsEnabled);
    }

    private View[] librarySettings(ConsolePanel panel) {
        TextView refresh = panel.action(getString(R.string.console_refresh_library));
        refresh.setOnClickListener(v -> refreshDashboard());
        panel.described(refresh, getString(R.string.console_settings_desc_refresh_library));
        LinearLayout pins = settingsToggle(panel, R.string.console_pinned_games_label,
                R.string.console_pinned_games_description,
                () -> PinnedGames.pinsEnabled(preferences), () -> {
                    PinnedGames.setPinsEnabled(preferences,
                            !PinnedGames.pinsEnabled(preferences));
                    refreshPlayniteLibraryAfterOptionChange();
                });
        LinearLayout relative = settingsToggle(panel,
                R.string.console_last_played_relative_label, 0,
                () -> PinnedGames.relativePlayed(preferences), () -> {
                    PinnedGames.setRelativePlayed(preferences,
                            !PinnedGames.relativePlayed(preferences));
                    refreshPlayniteLibraryAfterOptionChange();
                });
        LinearLayout description = settingsToggle(panel,
                R.string.console_carousel_game_description_label,
                R.string.console_settings_desc_carousel_description,
                () -> showCarouselGameDescription, () -> {
                    showCarouselGameDescription = !showCarouselGameDescription;
                    preferences.edit().putBoolean("show_carousel_game_description",
                            showCarouselGameDescription).apply();
                    applyCarouselDescriptionVisibility();
                });
        List<View> rows = new ArrayList<>(Arrays.asList(refresh,
                settingsToggle(panel, R.string.console_hidden_apps_label,
                        R.string.console_settings_desc_hidden_apps, () -> showHiddenApps,
                        this::toggleHiddenApps), pins, relative, description));
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) return rows.toArray(new View[0]);
        rows.addAll(Arrays.asList(hostSection(panel, host),
                defaultInstallLocationRow(panel, host, InstallLocations.STEAM,
                        R.string.install_location_steam),
                defaultInstallLocationRow(panel, host, InstallLocations.EPIC,
                        R.string.install_location_epic),
                panel.note(getString(R.string.install_location_settings_note))));
        if (!selectedProfileIsChild(host.uuid)) {
            TextView diskSpace = panel.navigation(getString(R.string.disk_space_title));
            diskSpace.setOnClickListener(v -> showDiskSpace(host));
            rows.add(diskSpace);
        }
        if (captures(host.uuid)) {
            // Mockup 25f: children too; the host shows each profile only its own.
            TextView captures = panel.navigation(getString(R.string.console_captures_title));
            if (hostPlus(host.uuid)) {
                captures.setOnClickListener(v -> showCaptures(host));
            } else {
                panel.disabled(captures, getString(R.string.host_plus_required));
            }
            rows.add(captures);
        }
        return rows.toArray(new View[0]);
    }

    private void refreshPlayniteLibraryAfterOptionChange() {
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host != null) renderPlayniteLibrary(host, currentSunshineApps);
    }

    private View[] profileSettings(ConsolePanel panel) {
        LinearLayout autoLogin = panel.value(getString(R.string.console_auto_login_host),
                autoLoginHostLabel());
        autoLogin.setOnClickListener(v -> chooseAutoLoginHost());
        panel.described(autoLogin, getString(R.string.console_auto_login_details));
        ComputerDetails host = hosts.get(selectedHostUuid);
        if (host == null) return new View[]{autoLogin};
        String profileId = selectedProfileId(host.uuid);
        HostGatewayClient.IntegrationProfile profile = integrationProfile(host.uuid, profileId);
        List<View> rows = new ArrayList<>(Arrays.asList(autoLogin, hostSection(panel, host)));
        LinearLayout windowsProfile = panel.value(getString(R.string.console_profile_eyebrow),
                profileDisplayName(profile));
        if (hostGatewayStore.profileSelection(host.uuid).showSelector) {
            windowsProfile.setOnClickListener(v -> {
                settingsScreen.dismiss();
                changeProfile(host);
            });
        }
        rows.add(windowsProfile);
        if (profile == null) return rows.toArray(new View[0]);
        TextView avatar = panel.navigation(getString(R.string.console_avatar_change));
        avatar.setOnClickListener(v -> showAvatarPicker(host, profile));
        rows.add(avatar);
        GatewayConnection pinConnection = profile.isChild() ? null
                : hostGatewayStore.loadForHost(host.uuid,
                host.activeAddress == null ? null : host.activeAddress.address, profile.id);
        if (pinConnection != null) {
            if (profile.pinRequired) {
                rows.add(adultPinAction(panel, host, profile, pinConnection,
                        AdultPinChange.Mode.CHANGE, R.string.console_adult_pin_change,
                        SETTINGS_PROFILE));
                rows.add(adultPinAction(panel, host, profile, pinConnection,
                        AdultPinChange.Mode.REMOVE, R.string.console_adult_pin_remove,
                        SETTINGS_PROFILE));
            } else {
                rows.add(adultPinAction(panel, host, profile, pinConnection,
                        AdultPinChange.Mode.SET, R.string.console_adult_pin_set,
                        SETTINGS_PROFILE));
            }
        }
        // One automatic profile per host, as on the profile tile; a PIN or a child excludes it.
        LinearLayout automatic = settingsToggle(panel, R.string.console_profile_gate_options, 0,
                () -> profile.id.equals(hostGatewayStore.automaticIntegrationProfileId(host.uuid)),
                () -> hostGatewayStore.setAutomaticIntegrationProfileId(host.uuid,
                        profile.id.equals(hostGatewayStore.automaticIntegrationProfileId(
                                host.uuid)) ? "" : profile.id));
        if (profile.pinRequired || profile.isChild()) {
            panel.disabled(automatic, getString(R.string.console_settings_profile_automatic_reason));
        } else {
            panel.described(automatic,
                    getString(R.string.console_settings_profile_automatic_details));
        }
        rows.add(automatic);
        LinearLayout tile = settingsToggle(panel, R.string.watch_next_tile_label, 0,
                () -> WatchNextTile.consented(preferences, host.uuid, profileId),
                () -> WatchNextTile.setConsent(preferences, host.uuid, profileId,
                        !WatchNextTile.consented(preferences, host.uuid, profileId)));
        // A child's tile is the parent's decision, made in the child's editor.
        if (profile.isChild()) {
            panel.disabled(tile, getString(R.string.watch_next_tile_child_note));
        } else {
            panel.described(tile, getString(R.string.watch_next_tile_description,
                    profileDisplayName(profile)));
        }
        rows.add(tile);
        // Mockup 24e: game mode and its two options; a child's are the parent's.
        rows.add(panel.section(getString(R.string.console_game_mode_label)));
        int[][] gameModeRows = {
                {R.string.console_game_mode_label, R.string.console_game_mode_description},
                {R.string.console_game_mode_windows_label,
                        R.string.console_game_mode_windows_description},
                {R.string.console_game_mode_mute_label,
                        R.string.console_game_mode_mute_description}};
        if (profile.isChild()) {
            // The parent's values live on the host, set in the child's editor.
            rows.add(panel.note(getString(R.string.console_game_mode_child_note)));
        } else {
            for (GameModeStep.Option option : GameModeStep.Option.values()) {
                int[] texts = gameModeRows[option.ordinal()];
                LinearLayout row = settingsToggle(panel, texts[0], 0,
                        () -> GameModeStep.profileEnabled(preferences, host.uuid, profileId,
                                option),
                        () -> GameModeStep.setProfileEnabled(preferences, host.uuid, profileId,
                                option, !GameModeStep.profileEnabled(preferences, host.uuid,
                                        profileId, option)));
                if (hostPlus(host.uuid)) panel.described(row, getString(texts[1]));
                else panel.disabled(row, getString(R.string.host_plus_required));
                rows.add(row);
            }
        }
        if (captures(host.uuid)) {
            // Mockup 25m: the last 30 s on the PC, per profile on this TV, on by default.
            rows.add(panel.section(getString(R.string.console_captures_title)));
            LinearLayout buffer = settingsToggle(panel, R.string.console_captures_buffer_label, 0,
                    () -> StreamCaptures.bufferEnabled(preferences, host.uuid, profileId),
                    () -> StreamCaptures.setBufferEnabled(preferences, host.uuid, profileId,
                            !StreamCaptures.bufferEnabled(preferences, host.uuid, profileId)));
            if (hostPlus(host.uuid)) {
                panel.described(buffer, getString(R.string.console_captures_buffer_description));
            } else {
                panel.disabled(buffer, getString(R.string.host_plus_required));
            }
            rows.add(buffer);
            if (!profile.isChild()) {
                // Host-wide: where every profile's captures go, an adult's choice.
                TextView drive = panel.navigation(getString(R.string.console_captures_drive_label));
                if (hostPlus(host.uuid)) {
                    panel.described(drive,
                            getString(R.string.console_captures_drive_description));
                    drive.setOnClickListener(v -> chooseCaptureDrive(host));
                } else {
                    panel.disabled(drive, getString(R.string.host_plus_required));
                }
                rows.add(drive);
            }
        }
        return rows.toArray(new View[0]);
    }

    /**
     * Settings › Children (mockup 20b): the selected parent's children behind the parent's PIN,
     * each opening its editor as a nested screen. Without a PIN the parent sets one here first.
     */
    private View[] childrenSettings(ConsolePanel panel) {
        ComputerDetails host = hosts.get(selectedHostUuid);
        HostGatewayClient.IntegrationProfile parent = host == null ? null
                : integrationProfile(host.uuid, selectedProfileId(host.uuid));
        if (host == null || parent == null) {
            return new View[]{panel.note(getString(
                    R.string.console_child_management_authorize_failed))};
        }
        if (!childManagementProfileAllowed(host, parent)) {
            return new View[]{panel.note(getString(
                    R.string.console_settings_children_unavailable))};
        }
        if (!childCapabilityByProfile.containsKey(
                childProfileMetadataKey(host.uuid, parent.id))) {
            loadChildCapability(host, parent);
            return new View[]{panel.note(getString(R.string.console_child_management_loading))};
        }
        GatewayConnection connection = childConnection(host, parent.id);
        if (!childCapabilityAllowed(host, parent) || connection == null) {
            return new View[]{panel.note(getString(
                    R.string.console_child_management_authorize_failed))};
        }
        List<View> rows = new ArrayList<>();
        rows.add(hostSection(panel, host));
        if (!parent.pinRequired) {
            rows.add(panel.note(getString(R.string.console_child_management_pin_first)));
            rows.add(adultPinAction(panel, host, parent, connection, AdultPinChange.Mode.SET,
                    R.string.console_adult_pin_set, SETTINGS_CHILDREN));
            return rows.toArray(new View[0]);
        }
        ChildManagementLease lease = currentChildLease(host, parent.id);
        if (lease == null) {
            TextView unlock = panel.navigation(getString(
                    R.string.console_settings_children_unlock));
            panel.described(unlock, getString(R.string.console_settings_children_locked));
            unlock.setOnClickListener(v -> openChildrenSettings(host));
            rows.add(unlock);
            return rows.toArray(new View[0]);
        }
        String key = ChildLibraryFilter.key(host.uuid, parent.id);
        if (!key.equals(childrenListKey)) {
            childrenListKey = key;
            childrenList = null;
            childrenListFailed = false;
        }
        TextView refresh = panel.action(getString(R.string.console_child_management_refresh));
        refresh.setOnClickListener(v -> reloadChildrenSettings());
        if (childrenListFailed) {
            rows.add(panel.note(getString(R.string.console_settings_children_load_failed)));
            rows.add(refresh);
            return rows.toArray(new View[0]);
        }
        if (childrenList == null) {
            loadChildrenSettings(host, lease);
            rows.add(panel.note(getString(R.string.console_child_management_loading)));
            return rows.toArray(new View[0]);
        }
        // Calendar weeks start on Sunday (1); the host's start on Monday (0).
        int today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
        for (HostGatewayClient.ChildProfile child : childrenList.children) {
            if (child == null || !parent.id.equals(child.parentProfileId)) continue;
            ConsoleChildProfileEditor.Draft draft = childDraft(child, childrenList.revision);
            // Today's plan on a second line, so the name keeps the row's whole width.
            TextView row = panel.navigation(panel.withDetail(draft.name.isEmpty()
                            ? getString(R.string.console_child_profile_title) : draft.name,
                    ConsoleChildProfileEditor.todaySummary(this, draft, today)));
            row.setOnClickListener(v -> showChildEditor(host, draft, false,
                    childManagementGeneration.get()));
            rows.add(row);
        }
        if (rows.size() == 1) {
            rows.add(panel.note(getString(R.string.console_child_management_empty)));
        }
        TextView add = panel.icon(panel.action(
                getString(R.string.console_child_management_add)), R.drawable.ic_console_add);
        int revision = childrenList.revision;
        add.setOnClickListener(v -> showChildEditor(host,
                ConsoleChildProfileEditor.Draft.newChild(revision), false,
                childManagementGeneration.get()));
        rows.add(add);
        rows.add(refresh);
        return rows.toArray(new View[0]);
    }

    /**
     * Settings › Children behind the parent's PIN. The PIN page belongs to the Home window
     * (secure flag, entry shell, PIN keys), which cannot draw over the Settings window: Settings
     * steps aside for it and opens again on Children, whether the PIN was given or cancelled.
     */
    private void openChildrenSettings(ComputerDetails host) {
        HostGatewayClient.IntegrationProfile parent =
                hostGatewayStore.profileSelection(host.uuid).selected;
        if (parent == null || !parent.pinRequired
                || currentChildLease(host, parent.id) != null) {
            showSettings(SETTINGS_CHILDREN);
            return;
        }
        childMutationRetry = null;
        authorizeChildManagementFor(host, (lease, token) -> showSettings(SETTINGS_CHILDREN),
                () -> showSettings(SETTINGS_CHILDREN));
    }

    /** Reads the children and the avatar catalog for Settings › Children, then shows them. */
    private void loadChildrenSettings(ComputerDetails host, ChildManagementLease lease) {
        if (childrenListLoading) return;
        childrenListLoading = true;
        String parentProfileId = lease.parentProfileId;
        String key = ChildLibraryFilter.key(host.uuid, parentProfileId);
        fetchChildProfiles(host, parentProfileId, lease, result -> executor.execute(() -> {
            List<HostGatewayClient.Avatar> avatars;
            try {
                avatars = profileAvatars.catalog(lease.connection, host.uuid);
            } catch (IOException | RuntimeException unavailable) {
                // The editor still offers the generated art.
                avatars = Collections.emptyList();
            }
            List<HostGatewayClient.Avatar> avatarCatalog = avatars;
            mainHandler.post(() -> {
                childrenListLoading = false;
                if (!key.equals(childrenListKey)) return;
                childAvatarCatalog = avatarCatalog;
                childrenList = result;
                settingsScreen.refresh(childrenCategory);
            });
        }), generation -> {
            childrenListLoading = false;
            if (!key.equals(childrenListKey)) return;
            childrenListFailed = true;
            settingsScreen.refresh(childrenCategory);
        });
    }

    /**
     * The children changed (a save, a delete, a conflict): Settings › Children lists them
     * again, Settings opening on it when the PIN page or the library had closed it.
     */
    private void reloadChildrenSettings() {
        childrenList = null;
        childrenListFailed = false;
        // Closing the side panel re-renders the category Settings shows (and so reads the list).
        hideSidePanelImmediately();
        if (!settingsScreen.isShowing()) showSettings(SETTINGS_CHILDREN);
    }

    /** Heads the rows that apply only to the selected host (and its profile). */
    private View hostSection(ConsolePanel panel, ComputerDetails host) {
        return panel.section(getString(R.string.console_settings_for_host, host.name));
    }

    private View[] appearanceSettings(ConsolePanel panel) {
        LinearLayout screenSaver = panel.value(getString(R.string.console_screen_saver_timeout),
                screenSaverLabel(screenSaverSeconds));
        screenSaver.setOnClickListener(v -> chooseScreenSaverTimeout());
        panel.described(screenSaver, getString(R.string.console_screen_saver_timeout_details));
        String[] languageNames = getResources().getStringArray(R.array.language_names);
        String[] languageValues = getResources().getStringArray(R.array.language_values);
        LinearLayout language = panel.value(getString(R.string.console_stream_language),
                ConsoleStreamSettings.label(languageNames, languageValues,
                        UiHelper.currentLanguage(this)));
        language.setOnClickListener(v -> settingsScreen.showChoices(
                getString(R.string.console_stream_language), "", languageNames,
                Arrays.asList(languageValues).indexOf(UiHelper.currentLanguage(this)),
                index -> {
                    if (languageValues[index].equals(UiHelper.currentLanguage(this))) return;
                    reopenAppearance = true;
                    UiHelper.applyLanguage(this, languageValues[index]);
                }));
        panel.described(language, getString(R.string.console_settings_desc_language));
        return new View[]{
                language,
                settingsToggle(panel, R.string.console_reduced_motion_label,
                        R.string.console_settings_desc_reduced_motion, () -> reducedMotion, () -> {
                    reducedMotion = !reducedMotion;
                    preferences.edit().putBoolean(ConsoleReducedMotion.KEY, reducedMotion).apply();
                    if (consoleFeedback != null) consoleFeedback.setReducedMotion(reducedMotion);
                    if (discordDmToastView != null) discordDmToastView.setReducedMotion(reducedMotion);
                    consolePanel.setReducedMotion(reducedMotion);
                    settingsScreen.setReducedMotion(reducedMotion);
                    entryShell.setReducedMotion(reducedMotion);
                    hostSelectView.setReducedMotion(reducedMotion);
                    if (firstRunWizard != null) firstRunWizard.setReducedMotion(reducedMotion);
                    if (libraryTransitionCoordinator != null) {
                        libraryTransitionCoordinator.setReducedMotion(reducedMotion);
                    }
                }),
                screenSaver};
    }

    private LinearLayout defaultInstallLocationRow(ConsolePanel panel, ComputerDetails host,
                                                   String launcher, int label) {
        String path = InstallLocations.defaultPath(preferences, host.uuid, launcher);
        LinearLayout row = panel.value(getString(label), path.isEmpty()
                ? getString(R.string.install_location_host_default) : path);
        row.setOnClickListener(v -> chooseDefaultInstallLocation(host, launcher, label));
        return row;
    }

    private void showCaptures(ComputerDetails host) {
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        captureGallery.show(settingsScreen, connection,
                getString(R.string.console_settings_library));
    }

    /** The shown Settings › Disk space page, updated in place; null when another is shown. */
    private DiskSpacePage diskSpacePage;

    private static final class DiskSpacePage {
        final String hostUuid;
        final LinearLayout strip;
        final Map<String, ConsoleDiskSpaceViews.GameRow> rows = new HashMap<>();
        /** The host's latest answer: the drives a confirmation computes "then free" from. */
        DiskSpace space;

        DiskSpacePage(String hostUuid, LinearLayout strip, DiskSpace space) {
            this.hostUuid = hostUuid;
            this.strip = strip;
            this.space = space;
        }

        boolean shows(String hostId) {
            return hostUuid.equals(hostId) && strip.isAttachedToWindow();
        }
    }

    /**
     * Settings › Library › Disk space (mockup 23c) for the selected host, adults only (the
     * host refuses a child). Opens at once with a busy bar; the host's answer fills it. No
     * network on the UI thread; covers from the artwork cache, else fetched like a library
     * tile's on their own thread while the page is shown.
     */
    private void showDiskSpace(ComputerDetails host) {
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        ConsolePanel panel = settingsScreen.panel();
        diskSpacePage = null;
        View loading = panel.busy();
        settingsScreen.showPage(getString(R.string.disk_space_title), "", new View[]{
                panel.section(getString(R.string.disk_space_drives)), loading}, 0);
        readDiskSpace(connection, space -> {
            // Its own loading page still shown: not after Back or another page opened.
            if (settingsScreen.isShowing() && loading.isAttachedToWindow()) {
                renderDiskSpace(host, space);
            }
        });
    }

    /** {@code read} gets null when the host could not answer. */
    private void readDiskSpace(GatewayConnection connection, Consumer<DiskSpace> read) {
        executor.execute(() -> {
            DiskSpace space;
            try {
                space = DiskSpace.parse(hostGatewayClient.storage(connection));
            } catch (IOException | RuntimeException error) {
                space = null;
            }
            DiskSpace result = space;
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()) read.accept(result);
            });
        });
    }

    private void renderDiskSpace(ComputerDetails host, DiskSpace space) {
        ConsolePanel panel = settingsScreen.panel();
        String title = getString(R.string.disk_space_title);
        if (space == null) {
            TextView retry = panel.action(getString(R.string.disk_space_retry));
            retry.setOnClickListener(v -> showDiskSpace(host));
            settingsScreen.showPage(title, getString(R.string.disk_space_unavailable),
                    new View[]{retry}, 0);
            return;
        }
        ConsoleDiskSpaceViews views = new ConsoleDiskSpaceViews(this);
        DiskSpacePage page = new DiskSpacePage(host.uuid, views.driveStrip(space.drives), space);
        List<View> rows = new ArrayList<>();
        rows.add(panel.section(getString(R.string.disk_space_drives)));
        rows.add(page.strip);
        rows.add(panel.section(getString(R.string.disk_space_candidates)));
        long now = System.currentTimeMillis();
        for (DiskSpace.Candidate candidate : space.candidates) {
            PlayniteDashboardItem game = diskSpaceGame(candidate.gameId);
            long played = PlayniteLibraryOrdering.activityEpoch(candidate.lastPlayed);
            List<String> words = new ArrayList<>();
            words.add(played == Long.MIN_VALUE ? getString(R.string.playnite_never_played)
                    : getString(R.string.disk_space_played,
                    RelativeTime.format(getResources(), now - played)));
            if (game != null && !game.game.libraryName.isEmpty()) {
                words.add(game.game.libraryName);
            }
            if (!candidate.drive.isEmpty()) {
                words.add(getString(R.string.disk_space_on_drive,
                        DiskSpace.driveLabel(candidate.drive)));
            }
            ConsoleDiskSpaceViews.GameRow[] holder = new ConsoleDiskSpaceViews.GameRow[1];
            ConsoleDiskSpaceViews.GameRow row = views.gameRow(panel, candidate,
                    TextUtils.join("  ·  ", words), v -> {
                        if (game == null || !game.game.canUninstall) {
                            ConsoleUiFeedback.makeText(this,
                                    R.string.disk_space_uninstall_unavailable,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        confirmDiskSpaceUninstall(host, page.space, candidate, game, holder[0]);
                    });
            holder[0] = row;
            // The host journal's running uninstall (it may hang on the PC): shown, not blocking.
            if (!candidate.operation.isEmpty()) {
                row.settle(getString("attention_required".equals(candidate.operation)
                        ? R.string.disk_space_uninstall_attention
                        : R.string.playnite_uninstalling));
            }
            loadCover(host, game, row.view, row::setCover);
            page.rows.put(DiskSpace.rowKey(candidate.gameId), row);
            rows.add(row.view);
        }
        if (space.candidates.isEmpty()) {
            rows.add(panel.note(getString(R.string.disk_space_no_candidates)));
            TextView refresh = panel.action(getString(R.string.disk_space_retry));
            refresh.setOnClickListener(v -> showDiskSpace(host));
            rows.add(refresh);
        }
        int focus = 0;
        while (focus < rows.size() - 1 && !rows.get(focus).isFocusable()) focus++;
        settingsScreen.showPage(title, getString(R.string.disk_space_details,
                        android.text.format.Formatter.formatShortFileSize(
                                this, space.minSizeBytes), space.idleDays),
                rows.toArray(new View[0]), focus);
        diskSpacePage = page;
    }

    /**
     * The library's game of a candidate, whatever the library view filters out (hidden, other
     * source, pinned only): the page lists what the host says is installed.
     */
    private PlayniteDashboardItem diskSpaceGame(String gameId) {
        String key = DiskSpace.rowKey(gameId);
        for (PlayniteLibraryGame game : currentPlayniteGames) {
            if (DiskSpace.rowKey(game.playniteGameId).equals(key)) {
                return new PlayniteDashboardItem(game, null,
                        PlayniteDashboardItem.MappingState.NOT_INSTALLED);
            }
        }
        return null;
    }

    /** Mockup 23d: the cover, the space freed on its drive and that drive afterwards. */
    private void confirmDiskSpaceUninstall(ComputerDetails host, DiskSpace space,
                                           DiskSpace.Candidate candidate,
                                           PlayniteDashboardItem item,
                                           ConsoleDiskSpaceViews.GameRow row) {
        ConsoleDiskSpaceViews views = new ConsoleDiskSpaceViews(this);
        String freed = android.text.format.Formatter.formatShortFileSize(
                this, candidate.sizeBytes);
        DiskSpace.Drive drive = space.driveOf(candidate);
        SpannableStringBuilder message = new SpannableStringBuilder(drive == null
                ? getString(R.string.disk_space_confirm_frees_unknown_drive, freed)
                : getString(R.string.disk_space_confirm_frees, freed,
                DiskSpace.driveLabel(drive.path)));
        message.setSpan(new ForegroundColorSpan(ConsoleTileBadge.BLUE), 0, message.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        View extra = null;
        if (drive != null) {
            DiskSpace.Drive after = DiskSpace.after(drive, candidate.sizeBytes);
            message.append("\n").append(getString(R.string.disk_space_confirm_after,
                    android.text.format.Formatter.formatShortFileSize(this, after.freeBytes),
                    android.text.format.Formatter.formatShortFileSize(this, after.totalBytes)));
            extra = views.confirmExtra(after, getString(R.string.disk_space_confirm_launcher));
        } else {
            message.append("\n").append(getString(R.string.disk_space_confirm_launcher));
        }
        ConsoleConfirmDialog.showDestructive(this,
                getString(R.string.disk_space_confirm_title, item.game.name), message,
                getString(R.string.console_cancel), getString(R.string.playnite_uninstall),
                () -> uninstallPlayniteGame(host, item,
                        () -> row.settle(getString(R.string.playnite_uninstalling)),
                        row::restore), views.confirmCover(row.cover()), extra);
    }

    /**
     * An uninstall finished: its row leaves the page and the drive strip is read again,
     * nothing else on the page changes (the last row gone, the page is the empty one).
     */
    private void updateDiskSpaceAfterUninstall(ComputerDetails host, String gameId) {
        DiskSpacePage page = diskSpacePage;
        if (host == null || page == null || !page.shows(host.uuid)) return;
        ConsoleDiskSpaceViews.GameRow row = page.rows.remove(DiskSpace.rowKey(gameId));
        if (row != null) settingsScreen.removeRow(row.view);
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid));
        if (connection == null) return;
        readDiskSpace(connection, space -> {
            if (page != diskSpacePage || !page.shows(host.uuid)) return;
            if (page.rows.isEmpty()) {
                renderDiskSpace(host, space);
            } else if (space != null) {
                page.space = space;
                new ConsoleDiskSpaceViews(this).setDrives(page.strip, space.drives);
            }
        });
    }

    private void chooseDefaultInstallLocation(ComputerDetails host, String launcher, int label) {
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        loadInstallTargets(connection, launcher, targets -> {
            if (!settingsScreen.isShowing()) return;
            List<InstallLocations.Target> listed =
                    targets == null ? Collections.emptyList() : targets;
            String current = InstallLocations.defaultPath(preferences, host.uuid, launcher);
            CharSequence[] labels = new CharSequence[listed.size() + 1];
            labels[0] = getString(R.string.install_location_host_default);
            for (int index = 0; index < listed.size(); index++) {
                labels[index + 1] = installLocationLabel(listed.get(index));
            }
            settingsScreen.showChoices(getString(label), targets == null
                            ? getString(R.string.install_location_unavailable)
                            : getString(R.string.install_location_settings_note), labels,
                    InstallLocations.indexOf(listed, current) + 1,
                    index -> InstallLocations.setDefaultPath(preferences, host.uuid, launcher,
                            index == 0 ? "" : listed.get(index - 1).path));
        });
    }

    /**
     * The host-wide drive for screenshots and clips: the Videos folder or one of the host's
     * fixed drives. The host checks it can write there before it keeps the choice; captures
     * saved before stay where they are (the gallery lists the chosen drive's).
     */
    private void chooseCaptureDrive(ComputerDetails host) {
        String address = host.activeAddress != null ? host.activeAddress.address : null;
        GatewayConnection connection = hostGatewayStore.loadForHost(
                host.uuid, address, selectedProfileId(host.uuid));
        if (connection == null) {
            ConsoleUiFeedback.makeText(this, R.string.playnite_gateway_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        executor.execute(() -> {
            JSONObject answer;
            try {
                answer = hostGatewayClient.captureDrive(connection);
            } catch (IOException | RuntimeException error) {
                answer = null;
            }
            JSONObject result = answer;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || !settingsScreen.isShowing()) return;
                if (result == null) {
                    ConsoleUiFeedback.makeText(this, R.string.disk_space_unavailable,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                List<DiskSpace.Drive> drives = DiskSpace.parse(result).drives;
                String current = result.optString("drive", "");
                CharSequence[] labels = new CharSequence[drives.size() + 1];
                labels[0] = getString(R.string.console_captures_drive_videos);
                int selected = 0;
                for (int index = 0; index < drives.size(); index++) {
                    DiskSpace.Drive drive = drives.get(index);
                    labels[index + 1] = getString(R.string.install_location_row, drive.path,
                            android.text.format.Formatter.formatShortFileSize(
                                    this, drive.freeBytes));
                    if (drive.path.equalsIgnoreCase(current)) selected = index + 1;
                }
                settingsScreen.showChoices(getString(R.string.console_captures_drive_label),
                        getString(R.string.console_captures_drive_description), labels, selected,
                        index -> setCaptureDrive(connection,
                                index == 0 ? "" : drives.get(index - 1).path));
            });
        });
    }

    private void setCaptureDrive(GatewayConnection connection, String drive) {
        executor.execute(() -> {
            String reason;
            try {
                hostGatewayClient.setCaptureDrive(connection, drive);
                reason = "";
            } catch (IOException | RuntimeException error) {
                reason = StreamCaptures.reason(error);
            }
            String answer = reason;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                ConsoleUiFeedback.makeText(this, answer.isEmpty()
                        ? R.string.console_captures_drive_saved
                        : "capture_drive_unavailable".equals(answer)
                        ? R.string.console_captures_drive_unwritable
                        : R.string.console_captures_drive_failed, Toast.LENGTH_LONG).show();
            });
        });
    }

    private View[] aboutSettings(ConsolePanel panel) {
        TextView help = panel.navigation(getString(R.string.console_options_help));
        help.setOnClickListener(v -> showHelpPanel());
        TextView features = panel.navigation(getString(R.string.console_options_features));
        features.setOnClickListener(v -> featureShowcase().open(showcaseCapabilities, "", null));
        List<View> rows = new ArrayList<>(Arrays.asList(help, features));
        FirstRunWizard wizard = firstRunWizard();
        if (wizard != null) {
            // Re-running changes nothing by itself: every step opens on the current state.
            TextView setup = panel.navigation(getString(R.string.first_run_settings_entry));
            panel.described(setup, getString(R.string.first_run_settings_entry_detail));
            setup.setOnClickListener(v -> openFirstRunWizard(
                    FirstRunPlan.first(FirstRunPlan.Entry.SETTINGS)));
            rows.add(setup);
            for (FirstRunPlan.Step item : wizard.todo(selectedHostUuid)) {
                TextView todo = panel.navigation(getString(R.string.first_run_todo_row,
                        wizard.stepName(item)));
                todo.setOnClickListener(v -> openFirstRunWizard(item));
                rows.add(todo);
            }
        }
        TextView tester = TesterWelcomeScreen.settingsRow(panel, this, BuildConfig.TESTING,
                () -> testerWelcome().show(controllerInput(), null));
        if (tester != null) rows.add(tester);
        if (releaseUpdates != null) {
            rows.addAll(AppUpdater.settingsRows(this, panel, releaseUpdates,
                    BuildConfig.VERSION_CODE, this::startAppUpdate));
        }
        rows.add(panel.note(TesterWelcomeScreen.versionNote(this, BuildConfig.TESTING)));
        rows.add(panel.section(getString(R.string.console_settings_section_diagnostics)));
        rows.addAll(Arrays.asList(streamSettings.diagnosticsRows(panel)));
        return rows.toArray(new View[0]);
    }

    /** Once per finding over an idle Home: TV and host disagree, or a newer app is out. */
    private void announceUpdates() {
        ReleaseUpdates updates = releaseUpdates;
        if (updates == null || !active || isStreamHome() || libraryTransitionRunning
                || loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE
                || sideDialog != null && sideDialog.isShowing()
                || settingsScreen != null && settingsScreen.isShowing()
                || featureShowcase().isShowing()
                || testerWelcome != null && testerWelcome.isShowing()) return;
        AppUpdater.Notice notice = AppUpdater.notice(this, updates, BuildConfig.VERSION_CODE,
                BuildConfig.VERSION_NAME);
        if (notice == null
                || notice.finding.equals(preferences.getString(PREF_ANNOUNCED_UPDATE, ""))) return;
        preferences.edit().putString(PREF_ANNOUNCED_UPDATE, notice.finding).apply();
        TextView close = consolePanel.action(getString(R.string.console_close));
        close.setOnClickListener(view -> handlePanelBack());
        if (notice.apk == null) {
            showSidePanel(getString(R.string.app_update_eyebrow), notice.title, notice.details,
                    close);
            return;
        }
        TextView update = consolePanel.action(getString(R.string.app_update_install,
                notice.apk.versionName));
        update.setOnClickListener(view -> startAppUpdate(notice.apk));
        showSidePanel(getString(R.string.app_update_eyebrow), notice.title, notice.details,
                update, close);
    }

    /** Android's own dialog confirms; the first time MoonWaker needs "install apps" access. */
    private void startAppUpdate(ReleaseUpdates.Apk apk) {
        if (appUpdater == null) appUpdater = new AppUpdater(this, executor, mainHandler);
        if (!appUpdater.canInstall()) {
            TextView open = consolePanel.action(getString(R.string.app_update_permission_open));
            open.setOnClickListener(view -> {
                if (!appUpdater.openInstallPermission(this)) {
                    ConsoleUiFeedback.makeText(this, R.string.app_update_permission_manual,
                            Toast.LENGTH_LONG).show();
                }
            });
            showSidePanel(getString(R.string.app_update_eyebrow),
                    getString(R.string.app_update_permission_title),
                    getString(R.string.app_update_permission_details), open);
            return;
        }
        String eyebrow = getString(R.string.app_update_eyebrow);
        String downloading = getString(R.string.app_update_downloading, apk.versionName);
        showSidePanel(eyebrow, downloading, getString(R.string.app_update_downloading_details),
                consolePanel.note(getString(R.string.console_loading)));
        sidePanelTransient = true;
        appUpdater.install(apk, message -> {
            if (isDestroyed()) return;
            boolean waiting = sideDialog != null && sideDialog.isShowing()
                    && (eyebrow + "\n" + downloading).equals(currentPanelKey);
            if (message == 0) {
                // Android's own dialog takes over from here.
                if (waiting) hideSidePanel();
                return;
            }
            TextView close = consolePanel.action(getString(R.string.console_close));
            close.setOnClickListener(view -> handlePanelBack());
            showSidePanel(eyebrow, getString(R.string.app_update_failed_title),
                    getString(message), close);
        });
    }

    private void openFirstRunWizard(FirstRunPlan.Step step) {
        FirstRunWizard wizard = firstRunWizard();
        if (wizard == null) return;
        settingsScreen.dismiss();
        wizard.openAt(FirstRunPlan.Entry.SETTINGS, selectedHostUuid, step);
    }

    /** A preference toggle; {@code description} 0 leaves the row without a caption. */
    private LinearLayout settingsToggle(ConsolePanel panel, int label, int description,
                                        BooleanSupplier value, Runnable flip) {
        LinearLayout row = panel.toggle(getString(label), value.getAsBoolean());
        if (description != 0) panel.described(row, getString(description));
        row.setOnClickListener(v -> {
            flip.run();
            panel.setChecked(row, value.getAsBoolean());
        });
        return row;
    }

    private void showStreamingAutopilotPanel() {
        ComputerDetails selectedHost = hosts.get(selectedHostUuid);
        TextView run = consolePanel.action(getString(R.string.console_autopilot_run),
                ConsoleActionCatalog.isOnline(selectedHost)
                        && ConsoleActionCatalog.isPaired(selectedHost));
        run.setOnClickListener(v -> requestStreamingAutopilot(
                currentHost(selectedHostUuid), null, null,
                getString(R.string.console_autopilot_global_target), "", null));
        String result = preferences.getString(PREF_AUTOPILOT_LAST_RESULT, "");
        long at = preferences.getLong(PREF_AUTOPILOT_LAST_RESULT_AT, 0L);
        String state = result.isEmpty() || at <= 0L
                ? getString(R.string.console_autopilot_never_run)
                : getString(R.string.console_autopilot_last_result, result,
                DateUtils.getRelativeTimeSpanString(Math.min(at, System.currentTimeMillis()),
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE));
        showSidePanel(getString(R.string.console_options_section_streaming),
                getString(R.string.console_streaming_autopilot_global),
                getString(R.string.console_autopilot_panel_details, state), run);
    }

    /** Only the global target has a "last result"; per-game runs leave it untouched. */
    private void recordGlobalAutopilotResult(String appKey, PlayIntent calibrationIntent,
                                             String result) {
        if (appKey != null || calibrationIntent != null) return;
        preferences.edit().putString(PREF_AUTOPILOT_LAST_RESULT, result)
                .putLong(PREF_AUTOPILOT_LAST_RESULT_AT, System.currentTimeMillis()).apply();
    }

    /** The first-run wizard, built on first use once the entry shell exists. */
    private FirstRunWizard firstRunWizard() {
        if (firstRunWizard == null && entryShell != null && consoleFeedback != null) {
            firstRunWizard = new FirstRunWizard(this, new FirstRunWizard.Host() {
                @Override public Collection<ComputerDetails> hosts() {
                    return new ArrayList<>(hosts.values());
                }

                @Override public ComputerDetails host(String uuid) {
                    return currentHost(uuid);
                }

                @Override public void addHostByAddress() {
                    addHost();
                }

                @Override public void wake(ComputerDetails host) {
                    wakeHost(host);
                }

                @Override public void sleep(ComputerDetails host, GatewayConnection connection) {
                    requestHostSleep(host, connection);
                }

                @Override public void createAdultProfile(ComputerDetails host,
                        HostGatewayClient.IntegrationProfile actor, Consumer<String> done) {
                    startAdultProfileCreation(host, actor, done);
                }

                @Override public void setAdultPin(ComputerDetails host,
                        HostGatewayClient.IntegrationProfile profile,
                        GatewayConnection connection, Runnable done) {
                    startAdultPinChange(host, profile, connection, AdultPinChange.Mode.SET,
                            done);
                }

                @Override public void saveProfiles(String hostId,
                        HostGatewayClient.IntegrationProfiles profiles) {
                    saveHostProfiles(hostId, profiles);
                }

                @Override public void pairController() {
                    openAccessorySettings();
                }

                @Override public void paired(String hostId) {
                    newlyDiscoveredHosts.remove(hostId);
                    if (consoleAudioEngine != null) {
                        consoleAudioEngine.play(ConsoleAudioSynthesis.Cue.SUCCESS);
                    }
                    invalidateHostStateAsync(hostId);
                    renderHostSelection();
                }

                @Override public void todoChanged() {
                    renderHostSelection();
                }

                @Override public String uniqueId() {
                    return managerBinder == null ? "" : managerBinder.getUniqueId();
                }

                @Override public String autoLoginHost() {
                    return autoLoginHostUuid == null ? "" : autoLoginHostUuid;
                }

                @Override public void setAutoLoginHost(String hostId) {
                    ConsoleActivity.this.setAutoLoginHost(hostId);
                }

                @Override public boolean menuSounds() {
                    return uiSoundsEnabled;
                }

                @Override public void setMenuSounds(boolean on) {
                    setUiSounds(on);
                }

                @Override public boolean playStationButtons() {
                    return hasLastControllerInput ? lastControllerPlayStation
                            : ControllerGlyphs.hasPlayStationController();
                }

                @Override public boolean controllerPresent() {
                    return !loadControllers().isEmpty();
                }

                @Override public boolean controllerInput() {
                    return ConsoleActivity.this.controllerInput();
                }

                @Override public void showFeatures(HostGatewayClient.Capabilities capabilities,
                                                   String eyebrow, Runnable onNext) {
                    featureShowcase().open(capabilities, eyebrow, onNext);
                }

                @Override public void finished(String hostId, String profileId,
                                               boolean parentPanel) {
                    testerWelcomeThen(() -> onFirstRunFinished(hostId, profileId, parentPanel));
                }
            }, preferences, hostGatewayStore, hostGatewayClient, hostPairingFlow,
                    consoleFeedback, executor, mainHandler, entryShell, reducedMotion);
        }
        return firstRunWizard;
    }

    /**
     * The wizard closed. With a host it enters it as the chosen profile through the profile
     * gate's own steps (its PIN when it has one), then opens the parent panel when asked;
     * without one the host screen stays (it has "Add host").
     */
    private void onFirstRunFinished(String hostId, String profileId, boolean parentPanel) {
        ComputerDetails host = currentHost(hostId);
        if (host == null) {
            if (hostSelectionVisible || selectedHostUuid == null) showHostSelection(null);
            return;
        }
        newlyDiscoveredHosts.remove(host.uuid);
        if (host.uuid.equals(selectedHostUuid) && !hostSelectionVisible
                && profileGateHostUuid == null && ProfileAuthorization.allows(host.uuid)
                && Objects.equals(profileId, selectedProfileId(host.uuid))) {
            if (parentPanel) openChildrenSettings(host);
            return;
        }
        pendingParentPanelHost = parentPanel ? host.uuid : null;
        HostGatewayClient.IntegrationProfile profile = profileId == null ? null
                : hostGatewayStore.profileSelection(host.uuid).find(profileId);
        if (profile == null) {
            resolveProfileGate(host, true, true);
        } else if (profile.pinRequired) {
            showPinEntry(host, profile, true, true, true);
        } else {
            changeSelectedProfile(host, profile.id);
            enterHostAfterProfileGate(host, true, true);
        }
    }


    private FeatureShowcaseLauncher featureShowcase() {
        if (featureShowcase == null) {
            featureShowcase = new FeatureShowcaseLauncher(this, preferences, reducedMotion);
        }
        return featureShowcase;
    }

    /** Once after an update: only the cards not seen yet, and only over an idle Home. */
    private void announceNewFeatures() {
        if (!active || isStreamHome() || libraryTransitionRunning
                || loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE
                || sideDialog != null && sideDialog.isShowing()
                || settingsScreen != null && settingsScreen.isShowing()
                || featureShowcase().isShowing()
                || testerWelcome != null && testerWelcome.isShowing()) return;
        featureShowcase().announceIfDue(showcaseCapabilities);
    }

    private TesterWelcomeScreen testerWelcome() {
        if (testerWelcome == null) {
            testerWelcome = new TesterWelcomeScreen(this, preferences, reducedMotion);
        }
        return testerWelcome;
    }

    /**
     * A tester build's first start: the tester welcome once, before the Home and before any
     * "What's new" (which waits for a later Home refresh), then {@code then} (may be null).
     */
    private void testerWelcomeThen(Runnable then) {
        if (isFinishing() || !TesterWelcomeScreen.isDue(BuildConfig.TESTING, preferences)) {
            if (then != null) then.run();
            return;
        }
        testerWelcome().show(controllerInput(), then);
    }

    /** The last input came from a controller (before any: one is connected). */
    private boolean controllerInput() {
        return hasLastControllerInput ? lastInputWasController : !loadControllers().isEmpty();
    }

    private void showHelpPanel() {
        TextView close = consolePanel.action(getString(R.string.console_close));
        close.setOnClickListener(view -> handlePanelBack());
        showScrollableDetailsSidePanel(getString(R.string.console_title),
                getString(R.string.console_help_title),
                TextUtils.replace(getText(R.string.console_help_body), new String[]{"%1$s"},
                        new CharSequence[]{BuildConfig.VERSION_NAME}), close);
    }

    private void resetScreenSaverTimer() {
        mainHandler.removeCallbacks(screenSaverTimeout);
        screenSaverLoadGeneration++;
        if (!active || screenSaverSeconds == SCREEN_SAVER_NEVER || screenSaverVisible) return;
        mainHandler.postDelayed(screenSaverTimeout, screenSaverSeconds * 1_000L);
    }

    private void updateScreenSaverWakePolicy() {
        if (active && screenSaverSeconds != SCREEN_SAVER_NEVER) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showScreenSaver() {
        if (!active || screenSaverSeconds == SCREEN_SAVER_NEVER
                || loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE
                || sideDialog != null && sideDialog.isShowing()
                || settingsScreen != null && settingsScreen.isShowing()
                || libraryTransitionRunning) {
            resetScreenSaverTimer();
            return;
        }
        String hostUuid = selectedHostUuid;
        if ((hostUuid == null || hostUuid.isEmpty()) && !hosts.isEmpty()) {
            hostUuid = hosts.keySet().iterator().next();
        }
        if (hostUuid == null || hostUuid.isEmpty()) {
            resetScreenSaverTimer();
            return;
        }
        if (selectedProfileIsChild(hostUuid)) {
            resetScreenSaverTimer();
            return;
        }
        HostProfileKey profileKey = selectedProfileKey(hostUuid);
        List<PlayniteDashboardItem> eligible = currentPlayniteProjectionMatches(profileKey)
                ? screenSaverItems(allPlayniteItems) : Collections.emptyList();
        if (eligible.isEmpty() && hostSelectionVisible) {
            String cachedHostUuid = hostUuid;
            HostProfileKey cachedProfileKey = profileKey;
            int loadGeneration = screenSaverLoadGeneration;
            executor.execute(() -> {
                PlayniteLibraryCache.Entry cached = playniteLibraryRepository.cached(
                        cachedProfileKey);
                List<PlayniteDashboardItem> cachedItems = new ArrayList<>();
                if (cached != null) {
                    for (PlayniteLibraryGame game : cached.games) {
                        cachedItems.add(new PlayniteDashboardItem(game, null,
                                PlayniteDashboardItem.MappingState.NOT_INSTALLED));
                    }
                }
                mainHandler.post(() -> {
                    if (loadGeneration == screenSaverLoadGeneration
                            && cachedProfileKey.equals(selectedProfileKey(cachedHostUuid))) {
                        startScreenSaver(cachedProfileKey, screenSaverItems(cachedItems));
                    }
                });
            });
            return;
        }
        startScreenSaver(profileKey, eligible);
    }

    private List<PlayniteDashboardItem> screenSaverItems(
            List<PlayniteDashboardItem> source) {
        List<PlayniteDashboardItem> eligible = new ArrayList<>();
        for (PlayniteDashboardItem item : source) {
            if (item != null && item.game != null
                    && (!item.game.heroKey.isEmpty() || !item.game.backgroundKey.isEmpty())) {
                eligible.add(item);
            }
        }
        return eligible;
    }

    private void startScreenSaver(HostProfileKey profileKey,
                                  List<PlayniteDashboardItem> eligible) {
        if (!active || screenSaverVisible || profileKey == null
                || !profileKey.equals(selectedProfileKey(profileKey.hostId))) return;
        if (eligible.isEmpty()) {
            resetScreenSaverTimer();
            return;
        }
        Collections.shuffle(eligible);
        screenSaverHostUuid = profileKey.hostId;
        screenSaverProfileKey = profileKey;
        screenSaverItems = eligible;
        screenSaverItemIndex = 0;
        screenSaverGeneration++;
        showNextScreenSaverArtwork();
    }

    private void showNextScreenSaverArtwork() {
        mainHandler.removeCallbacks(screenSaverSlide);
        if (!active || screenSaverItems.isEmpty()) return;
        int generation = screenSaverGeneration;
        PlayniteDashboardItem item = screenSaverItems.get(
                screenSaverItemIndex++ % screenSaverItems.size());
        String hostUuid = screenSaverHostUuid;
        HostProfileKey profileKey = screenSaverProfileKey;
        if (profileKey == null || !profileKey.equals(selectedProfileKey(hostUuid))) return;
        ComputerDetails host = hosts.get(hostUuid);
        executor.execute(() -> {
            PlayniteArtworkSpec spec = PlayniteArtworkSpec.forScreenSaver(item.game);
            ArtworkResult result = cachedPlayniteArtwork(profileKey, hostUuid, item, spec);
            if (result == null && host != null) {
                String address = host.activeAddress != null ? host.activeAddress.address : null;
                GatewayConnection connection =
                        hostGatewayStore.loadForHost(hostUuid, address, profileKey.profileId);
                if (connection != null) {
                    try {
                        result = fetchPlayniteArtwork(
                                connection, profileKey, hostUuid, item, spec);
                    } catch (IOException ignored) { }
                }
            }
            Bitmap bitmap = result == null ? null : decodeArtwork(result.file, 1920);
            if (bitmap != null && !LoadingArtworkPolicy.canUseAsSplash(
                    bitmap.getWidth(), bitmap.getHeight())) {
                bitmap = null;
            }
            Bitmap selectedBitmap = bitmap;
            mainHandler.post(() -> applyScreenSaverArtwork(
                    generation, profileKey, item.game.name, selectedBitmap));
        });
    }

    private void applyScreenSaverArtwork(int generation, HostProfileKey profileKey,
                                         String title, Bitmap bitmap) {
        if (!active || generation != screenSaverGeneration
                || !profileKey.equals(screenSaverProfileKey)
                || !profileKey.equals(selectedProfileKey(profileKey.hostId))) return;
        if (bitmap == null) {
            if (screenSaverItemIndex < screenSaverItems.size()) {
                showNextScreenSaverArtwork();
            } else {
                resetScreenSaverTimer();
            }
            return;
        }
        screenSaverVisible = true;
        if (discordDmNotifications != null) {
            discordDmNotifications.setScreenSaverActive(discordDmHostToken, true);
        }
        screenSaverLayer.setVisibility(View.VISIBLE);
        screenSaverLayer.bringToFront();
        screenSaverCaption.setText(getString(R.string.console_screen_saver_caption, title));
        ImageView incoming = screenSaverArtworkNext;
        ImageView outgoing = screenSaverArtwork;
        incoming.animate().cancel();
        outgoing.animate().cancel();
        incoming.setImageBitmap(bitmap);
        incoming.setAlpha(reducedMotion ? 1f : 0f);
        if (reducedMotion) {
            outgoing.setImageDrawable(null);
        } else {
            incoming.animate().alpha(1f).setDuration(850L).start();
            outgoing.animate().alpha(0f).setDuration(850L).withEndAction(
                    () -> outgoing.setImageDrawable(null)).start();
        }
        screenSaverArtwork = incoming;
        screenSaverArtworkNext = outgoing;
        mainHandler.postDelayed(screenSaverSlide, SCREEN_SAVER_SLIDE_MS);
    }

    private void hideScreenSaver() {
        mainHandler.removeCallbacks(screenSaverSlide);
        screenSaverGeneration++;
        screenSaverItems = Collections.emptyList();
        screenSaverProfileKey = null;
        if (screenSaverLayer != null) screenSaverLayer.setVisibility(View.GONE);
        if (screenSaverArtwork != null) screenSaverArtwork.setImageDrawable(null);
        if (screenSaverArtworkNext != null) screenSaverArtworkNext.setImageDrawable(null);
        screenSaverVisible = false;
        if (discordDmNotifications != null) {
            discordDmNotifications.setScreenSaverActive(discordDmHostToken, false);
        }
        resetScreenSaverTimer();
    }

    private static boolean isMeaningfulMotion(MotionEvent event) {
        if (event == null) return false;
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0) {
            return Math.abs(event.getAxisValue(MotionEvent.AXIS_X)) > .25f
                    || Math.abs(event.getAxisValue(MotionEvent.AXIS_Y)) > .25f
                    || Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_X)) > .25f
                    || Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_Y)) > .25f
                    || Math.abs(event.getAxisValue(MotionEvent.AXIS_Z)) > .25f
                    || Math.abs(event.getAxisValue(MotionEvent.AXIS_RZ)) > .25f;
        }
        return (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0;
    }

    private String screenSaverLabel(int seconds) {
        if (seconds == SCREEN_SAVER_NEVER) return getString(R.string.console_screen_saver_never);
        return seconds < 60 ? getString(R.string.console_screen_saver_seconds, seconds)
                : getString(R.string.console_screen_saver_minutes, seconds / 60);
    }

    private void chooseScreenSaverTimeout() {
        int[] values = {SCREEN_SAVER_NEVER, 30, 5 * 60, 10 * 60, 30 * 60, 60 * 60};
        CharSequence[] labels = new CharSequence[values.length];
        int current = -1;
        for (int index = 0; index < values.length; index++) {
            labels[index] = screenSaverLabel(values[index]);
            if (values[index] == screenSaverSeconds) current = index;
        }
        settingsScreen.showChoices(getString(R.string.console_screen_saver_timeout_title),
                getString(R.string.console_screen_saver_timeout_details), labels, current,
                index -> setScreenSaverSeconds(values[index]));
    }

    private void setScreenSaverSeconds(int seconds) {
        screenSaverSeconds = seconds;
        preferences.edit().putInt(PREF_SCREEN_SAVER_SECONDS, seconds).apply();
        updateScreenSaverWakePolicy();
        resetScreenSaverTimer();
    }

    private String autoLoginHostLabel() {
        ComputerDetails host = autoLoginHostUuid == null || autoLoginHostUuid.isEmpty()
                ? null : hosts.get(autoLoginHostUuid);
        return host == null ? getString(R.string.console_auto_login_none) : host.name;
    }

    private String backgroundStreamLabel(int minutes) {
        if (minutes == BackgroundStreamPreferences.NEVER) {
            return getString(R.string.console_background_stream_never);
        }
        return minutes <= 0 ? getString(R.string.console_background_stream_off)
                : getString(R.string.console_background_stream_minutes, minutes);
    }

    private void chooseBackgroundStreamRetention() {
        int[] values = {0, 5, 10, 30, 60, BackgroundStreamPreferences.NEVER};
        CharSequence[] labels = new CharSequence[values.length];
        int current = -1;
        for (int index = 0; index < values.length; index++) {
            labels[index] = backgroundStreamLabel(values[index]);
            if (values[index] == backgroundStreamRetentionMinutes) current = index;
        }
        settingsScreen.showChoices(getString(R.string.console_background_stream_retention_title),
                getString(R.string.console_background_stream_retention_details), labels, current,
                index -> {
                    int minutes = values[index];
                    backgroundStreamRetentionMinutes = minutes;
                    BackgroundStreamPreferences.writeMinutes(this, minutes);
                });
    }

    private void chooseAutoLoginHost() {
        List<ComputerDetails> sorted = new ArrayList<>(hosts.values());
        sorted.sort(Comparator.comparing(host -> host.name, String.CASE_INSENSITIVE_ORDER));
        CharSequence[] labels = new CharSequence[sorted.size() + 1];
        labels[0] = getString(R.string.console_auto_login_none);
        int current = 0;
        for (int index = 0; index < sorted.size(); index++) {
            labels[index + 1] = sorted.get(index).name;
            if (sorted.get(index).uuid.equals(autoLoginHostUuid)) current = index + 1;
        }
        settingsScreen.showChoices(getString(R.string.console_auto_login_title),
                getString(R.string.console_auto_login_details), labels, current,
                index -> setAutoLoginHost(index == 0 ? "" : sorted.get(index - 1).uuid));
    }

    private void setAutoLoginHost(String hostUuid) {
        autoLoginHostUuid = hostUuid;
        SharedPreferences.Editor editor = preferences.edit();
        if (autoLoginHostUuid.isEmpty()) editor.remove("auto_login_host");
        else editor.putString("auto_login_host", autoLoginHostUuid);
        editor.apply();
    }

    private void applyCarouselDescriptionVisibility() {
        if (selectedGameDescription != null) {
            selectedGameDescription.setVisibility(
                    showCarouselGameDescription ? View.VISIBLE : View.GONE);
        }
        if (selectedGameMetadata != null) {
            ViewGroup.LayoutParams params = selectedGameMetadata.getLayoutParams();
            if (params != null) {
                params.height = dp(showCarouselGameDescription ? 196 : 36);
                selectedGameMetadata.setLayoutParams(params);
            }
        }
    }

    private void showExitConfirmation() {
        TextView cancel = consolePanel.action(getString(R.string.console_cancel));
        TextView exit = consolePanel.action(getString(R.string.console_exit));
        consolePanel.destructive(exit);
        cancel.setOnClickListener(view -> handlePanelBack());
        exit.setOnClickListener(view -> {
            hideSidePanel();
            finishAndRemoveTask();
        });
        showSidePanel(getString(R.string.console_title), getString(R.string.console_close_title),
                getString(R.string.console_close_details), cancel, exit);
    }

    private void showDiscordPanel() {
        if (!discordAllowedForSelectedProfile()) return;
        setDiscordNotificationPending(false);
        discordSocialPanelController.showHub();
    }

    private void openDiscordDmShortcut() {
        if (!discordAllowedForSelectedProfile()) {
            refreshDiscordAccess();
            return;
        }
        setDiscordNotificationPending(false);
        long peerId = discordDmNotifications.consumeQuickAction(discordDmHostToken);
        if (sideDialog != null && sideDialog.isShowing()) hideSidePanelImmediately();
        if (peerId > 0L) discordSocialPanelController.showDirectMessage(peerId);
        else discordSocialPanelController.showHub();
    }

    private void startCommunityDictation(long recipientId, long directMessageGeneration) {
        if (discordSocialPanelController == null
                || !communityDictationSession.begin(recipientId, directMessageGeneration)) return;
        if (!discordSocialPanelController.collapseCommunityKeyboardForDictation(recipientId,
                directMessageGeneration)) {
            communityDictationSession.finish();
            return;
        }
        Intent recognition = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (getPackageManager().resolveActivity(recognition, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            communityDictationSession.finish();
            discordSocialPanelController.restoreCommunityKeyboardAfterDictation(recipientId,
                    directMessageGeneration);
            ConsoleUiFeedback.makeText(this, R.string.discord_dm_dictation_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivityForResult(recognition, REQUEST_COMMUNITY_DICTATION);
        } catch (ActivityNotFoundException | SecurityException error) {
            communityDictationSession.finish();
            discordSocialPanelController.restoreCommunityKeyboardAfterDictation(recipientId,
                    directMessageGeneration);
            ConsoleUiFeedback.makeText(this, R.string.discord_dm_dictation_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_COMMUNITY_DICTATION || !communityDictationSession.isActive()) return;
        long recipientId = communityDictationSession.recipientId();
        long directMessageGeneration = communityDictationSession.directMessageGeneration();
        communityDictationSession.finish();
        if (discordSocialPanelController == null) return;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String transcript = results == null || results.isEmpty() ? "" : results.get(0);
            if (!TextUtils.isEmpty(transcript)) {
                discordSocialPanelController.insertCommunityDictationResult(recipientId,
                        directMessageGeneration, transcript);
            }
        }
        discordSocialPanelController.restoreCommunityKeyboardAfterDictation(recipientId,
                directMessageGeneration);
    }

    private void showSidePanel(String eyebrow, String title, CharSequence details,
                               View... actions) {
        showSidePanelInternal(eyebrow, title, details, false, actions);
    }

    private void showCommunityPanel(View shell) {
        if (communityPanelHost == null || !discordAllowedForSelectedProfile()) return;
        boolean alreadyShowing = sideDialog != null && sideDialog.isShowing();
        boolean sameCommunity = alreadyShowing && "discord.community".equals(currentPanelKey)
                && communityPanelHost.getChildCount() == 1
                && communityPanelHost.getChildAt(0) == shell;
        if (!alreadyShowing) {
            lastContentFocus = getCurrentFocus();
            lastContentFocusTag = lastContentFocus != null ? lastContentFocus.getTag() : null;
            panelHistory.clear();
            contentPanelOpen = true;
            updateContentLayerAccess();
        }
        if (!sameCommunity) {
            communityPanelHost.removeAllViews();
            ViewParent parent = shell.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(shell);
            communityPanelHost.addView(shell, match());
        }
        currentPanelKey = "discord.community";
        sidePanelScroll.animate().cancel();
        sidePanelScroll.setVisibility(View.GONE);
        communityPanelHost.setVisibility(View.VISIBLE);
        if (!alreadyShowing) {
            captureMenuBackdrop();
            sideDialog.show();
            Window window = sideDialog.getWindow();
            if (window != null) window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        communityPanelHost.setAlpha(1f);
        communityPanelHost.setTranslationX(0f);
        if (!sameCommunity) {
            shell.post(() -> {
                if ("discord.community".equals(currentPanelKey)) {
                    discordSocialPanelController.focusCommunityEntry();
                }
            });
        }
    }

    private void showRetainedStreamExitConfirmation() {
        TextView leave = consolePanel.action(getString(R.string.console_leave_keep_stream));
        TextView returnToGame = consolePanel.action(getString(R.string.console_return_to_game));
        TextView close = consolePanel.action(getString(R.string.console_close_game_and_moonwaker));
        consolePanel.destructive(close);
        leave.setOnClickListener(view -> {
            hideSidePanelImmediately();
            leaveMoonWakerKeepingStream();
        });
        returnToGame.setOnClickListener(view -> {
            hideSidePanel();
            returnToRetainedStream();
        });
        close.setOnClickListener(view -> {
            hideSidePanel();
            terminateRetainedSessionAndExit();
        });
        showSidePanel(getString(R.string.console_title),
                getString(R.string.console_leave_with_stream_title),
                getString(R.string.console_leave_with_stream_details),
                leave, returnToGame, close);
    }

    private void leaveMoonWakerKeepingStream() {
        sessionModel.post(new SessionEvent.ParkRequested(currentRetainedStreamSessionId(),
                sessionModel.snapshot().presentationOperation));
        moveTaskToBack(true);
    }

    /**
     * MoonWaker leaves at once; the close runs on owners that outlive this Activity (the model and
     * stream runtime, or its own thread) and nothing here is finished before it completes.
     */
    private void terminateRetainedSessionAndExit() {
        final String expectedStreamSessionId = currentRetainedStreamSessionId();
        if (expectedStreamSessionId.isEmpty()) {
            exitMoonWaker();
            return;
        }
        // From here on app-scoped state only: the close may complete after this Activity is gone.
        Context app = getApplicationContext();
        SessionModel model = sessionModel;
        Handler main = mainHandler;
        WeakReference<ConsoleActivity> console = new WeakReference<>(this);
        long exit = MoonWakerExit.request();
        SessionModel.TerminationCallback complete = terminationOutcome(outcome -> main.post(() -> {
            if (!mayCompleteTermination(model, expectedStreamSessionId)) return;
            // Still away: this close ends MoonWaker. Back meanwhile: it no longer does.
            boolean away = MoonWakerExit.take(exit);
            if (!outcome.closed()) {
                // The stream closed, but the game may still run, or the close failed: say so,
                // in the console when the user is back in it.
                ConsoleActivity shown = console.get();
                ConsoleUiFeedback.makeText(away || shown == null || shown.isDestroyed()
                                ? app : shown,
                        outcome == HostSessionTermination.Outcome.CLOSED_GAME_UNVERIFIED
                                ? R.string.console_terminate_session_game_left
                                : R.string.console_terminate_session_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            model.post(new SessionEvent.Cleared(expectedStreamSessionId));
            model.post(new SessionEvent.ReconnectCleared(expectedStreamSessionId));
            BackgroundStreamService.dismiss(app, expectedStreamSessionId);
            if (away) MoonWakerExit.finishTasks(app);
        }));
        sessionModel.dispatch(new SessionEvent.TerminateRequested(expectedStreamSessionId,
                "", complete), result -> {
            if (result == SessionModel.TerminationResult.NO_CONTROLLER) {
                closeRecordedSessionAndExit(expectedStreamSessionId, complete);
            }
        });
        // Backgrounded, not finished: finishing would end the presentation the close may run in.
        moveTaskToBack(true);
    }

    private void closeRecordedSessionAndExit(String expectedStreamSessionId,
                                             SessionModel.TerminationCallback complete) {
        SessionResumeManager.PendingSession record = sessionModel.records().pending;
        if (record != null && !record.matches(expectedStreamSessionId)) record = null;
        RetainedCloseTarget target = RetainedCloseTarget.of(
                sessionModel.snapshot(),
                record == null ? null : record.hostUuid,
                record == null ? null : record.profileId,
                record == null ? null : record.playniteGameId);
        ComputerDetails host = target == null ? null : hosts.get(target.hostId);
        String providerGameId = target == null ? "" : target.providerGameId;
        if (host == null || managerBinder == null) {
            complete.complete(providerGameId.isEmpty());
            return;
        }
        Supplier<HostSessionTermination.Outcome> close =
                knownControlPlaneTermination(host, target.profileId, providerGameId);
        SessionModel model = sessionModel;
        Handler main = mainHandler;
        WeakReference<ConsoleActivity> console = new WeakReference<>(this);
        String hostId = host.uuid;
        // Its own thread on app-scoped state, not this Activity's executor: onDestroy neither
        // interrupts the request nor leaves it without what it needs.
        new Thread(() -> {
            if (!mayCompleteTermination(model, expectedStreamSessionId)) return;
            boolean success = close.get().closed();
            main.post(() -> {
                if (success) model.post(new SessionEvent.Cleared(expectedStreamSessionId));
                ConsoleActivity shown = console.get();
                if (shown != null && !shown.isDestroyed()) shown.refreshSessionState(hostId);
                complete.complete(success);
            });
        }, "MoonWaker-CloseAndExit").start();
    }

    private void exitMoonWaker() {
        finishAffinity();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    private String currentRetainedStreamSessionId() {
        SessionModel.Snapshot retained =
                sessionModel.snapshot();
        if (!retained.streamSessionId.isEmpty()) return retained.streamSessionId;
        SessionResumeManager.PendingSession pending =
                sessionModel.records().pending;
        return pending == null ? "" : pending.streamSessionId;
    }

    private static boolean mayCompleteTermination(SessionModel model,
                                                  String expectedStreamSessionId) {
        SessionModel.Snapshot retained = model.snapshot();
        if (!retained.streamSessionId.isEmpty()
                && !expectedStreamSessionId.equals(retained.streamSessionId)) return false;
        SessionResumeManager.PendingSession pending = model.records().pending;
        return pending == null || pending.matches(expectedStreamSessionId);
    }
    private void showScrollableDetailsSidePanel(String eyebrow, String title,
                                                CharSequence details, View... actions) {
        showSidePanelInternal(eyebrow, title, details, true, actions);
    }

    private void showSidePanelInternal(String eyebrow, String title, CharSequence details,
                                       boolean scrollableDetails, View... actions) {
        if (communityPanelHost != null && communityPanelHost.getVisibility() == View.VISIBLE) {
            communityPanelHost.removeAllViews();
            communityPanelHost.setVisibility(View.GONE);
            sidePanelScroll.setVisibility(View.VISIBLE);
        }
        if (sidePanelBusyBanner != null) {
            ViewParent parent = sidePanelBusyBanner.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(sidePanelBusyBanner);
            sidePanelBusyBanner = null;
        }
        applySidePanelFrame();
        String nextKey = eyebrow + "\n" + title;
        boolean alreadyShowing = sideDialog != null && sideDialog.isShowing();
        boolean samePanel = alreadyShowing && nextKey.equals(currentPanelKey);
        if (alreadyShowing && !samePanel) prepareChildSharingForDeparture();
        // The panel is the dialog's window: its focus, not the Activity's, is the focused row.
        View previousPanelFocus = samePanel ? sideDialog.getCurrentFocus() : null;
        if (previousPanelFocus != null && !isDescendant(sidePanel, previousPanelFocus)) {
            previousPanelFocus = null;
        }
        Object previousPanelFocusTag = previousPanelFocus != null
                ? previousPanelFocus.getTag() : null;
        int previousPanelFocusIndex = -1;
        if (previousPanelFocus != null) {
            List<View> previousFocusable = new ArrayList<>();
            collectFocusable(sidePanel, previousFocusable);
            previousPanelFocusIndex = previousFocusable.indexOf(previousPanelFocus);
        }
        if (!alreadyShowing) {
            captureMenuBackdrop();
            lastContentFocus = getCurrentFocus();
            lastContentFocusTag = lastContentFocus != null ? lastContentFocus.getTag() : null;
            panelHistory.clear();
            contentPanelOpen = true;
            updateContentLayerAccess();
        } else if (!sidePanelTransient && currentPanelKey != null
                && !currentPanelKey.equals(nextKey) && !"discord.community".equals(currentPanelKey)) {
            // Community lives in communityPanelHost; sidePanel holds no rows of it to restore.
            PanelSnapshot snapshot = capturePanelSnapshot();
            if (snapshot != null) panelHistory.push(snapshot);
        }
        sidePanelTransient = false;
        consolePanel.render(sidePanel, eyebrow, title, details, scrollableDetails, actions);
        currentPanelKey = nextKey;
        if (!alreadyShowing) {
            sideDialog.show();
            Window window = sideDialog.getWindow();
            if (window != null) window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        if (!alreadyShowing) {
            sidePanelScroll.setTranslationX(reducedMotion ? 0 : dp(ConsolePanel.WIDTH_DP));
            if (!reducedMotion) {
                sidePanelScroll.animate().translationX(0).setDuration(180).start();
            }
        } else {
            sidePanelScroll.animate().cancel();
            sidePanelScroll.setAlpha(1f);
            sidePanelScroll.setScaleX(1f);
            sidePanelScroll.setScaleY(1f);
            sidePanelScroll.setTranslationX(0);
        }
        wireModalFocusTrap(false);
        View preserved = previousPanelFocusTag != null
                ? sidePanel.findViewWithTag(previousPanelFocusTag) : null;
        if ((preserved == null || !preserved.isFocusable() || !preserved.isEnabled())
                && previousPanelFocusIndex >= 0) {
            List<View> currentFocusable = new ArrayList<>();
            collectFocusable(sidePanel, currentFocusable);
            if (!currentFocusable.isEmpty()) {
                preserved = currentFocusable.get(Math.min(
                        previousPanelFocusIndex, currentFocusable.size() - 1));
            }
        }
        if (preserved != null && preserved.isFocusable() && preserved.isEnabled()) {
            preserved.post(preserved::requestFocus);
        } else {
            requestFirstModalFocus();
        }
        if (!samePanel) {
            sidePanelScroll.post(() -> sidePanelScroll.scrollTo(0, 0));
        }
    }

    private void applySidePanelFrame() {
        if (sidePanelScroll == null || sidePanel == null) return;
        FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) sidePanelScroll.getLayoutParams();
        int margin = dp(ConsolePanel.MARGIN_DP);
        frame.width = dp(ConsolePanel.WIDTH_DP);
        frame.height = ViewGroup.LayoutParams.MATCH_PARENT;
        frame.gravity = Gravity.END;
        frame.setMargins(margin, margin, margin, margin);
        consolePanel.floatOnGlass(sidePanel);
        ViewGroup.LayoutParams content = sidePanel.getLayoutParams();
        content.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        sidePanel.setLayoutParams(content);
    }

    /** The side panel blurs whatever is directly below it: settings when open, else Home. */
    private void captureMenuBackdrop() {
        menuBackdrop.capture(settingsScreen.isShowing() ? settingsScreen.window() : getWindow(),
                reducedMotion);
    }

    private void showSidePanelBusy(String eyebrow, String title, String details) {
        boolean alreadyShowing = sideDialog != null && sideDialog.isShowing();
        if (!alreadyShowing) {
            showSidePanel(eyebrow, title, details,
                    consolePanel.note(getString(R.string.console_loading)));
            sidePanelTransient = true;
            return;
        }
        if (sidePanelBusyBanner != null) {
            ViewParent parent = sidePanelBusyBanner.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(sidePanelBusyBanner);
        }
        TextView banner = consolePanel.note("\u21BB  " + details);
        banner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        sidePanel.addView(banner, Math.min(3, sidePanel.getChildCount()), params);
        sidePanelBusyBanner = banner;
    }

    private void hideSidePanel() {
        if (reducedMotion || "discord.community".equals(currentPanelKey)) completeSidePanelDismissal();
        else sidePanelScroll.animate().translationX(dp(ConsolePanel.WIDTH_DP)).setDuration(150).withEndAction(this::completeSidePanelDismissal).start();
    }

    private void hideSidePanelImmediately() {
        sidePanelScroll.animate().cancel();
        completeSidePanelDismissal();
    }

    private void completeSidePanelDismissal() {
        prepareChildSharingForDeparture();
        invalidateChildManagementPresentation();
        if (discordSocialPanelController != null) discordSocialPanelController.closePanel();
        if (discordPanelController != null) discordPanelController.closePanel();
        if (sideDialog != null) sideDialog.dismiss();
        menuBackdrop.release();
        if (communityPanelHost != null) {
            communityPanelHost.removeAllViews();
            communityPanelHost.setVisibility(View.GONE);
        }
        sidePanelScroll.setVisibility(View.VISIBLE);
        sidePanelScroll.setTranslationX(0);
        sidePanelScroll.setAlpha(1f);
        sidePanelScroll.setScaleX(1f);
        sidePanelScroll.setScaleY(1f);
        applySidePanelFrame();
        panelHistory.clear();
        currentPanelKey = null;
        sidePanelBusyBanner = null;
        sidePanelTransient = false;
        contentPanelOpen = false;
        updateContentLayerAccess();
        restoreContentFocus();
        if (settingsScreen != null && settingsScreen.isShowing()) settingsScreen.refresh();
    }

    /**
     * Only the top content layer takes focus and accessibility: an open panel covers both
     * layers, and the entry shell covers the home layer beneath it.
     */
    private void updateContentLayerAccess() {
        boolean entry = entryShell != null && entryShell.getVisibility() == View.VISIBLE;
        setLayerAccess(homeLayer, !contentPanelOpen && !entry);
        if (entryShell != null) setLayerAccess(entryShell, !contentPanelOpen);
    }

    private static void setLayerAccess(ViewGroup layer, boolean exposed) {
        layer.setImportantForAccessibility(exposed ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        layer.setDescendantFocusability(exposed ? ViewGroup.FOCUS_AFTER_DESCENDANTS
                : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private PanelSnapshot capturePanelSnapshot() {
        List<View> focusable = new ArrayList<>();
        collectFocusable(sidePanel, focusable);
        if (focusable.isEmpty()) return null;
        View focused = getCurrentFocus();
        if (focused != null && !isDescendant(sidePanel, focused)) focused = null;
        List<View> children = new ArrayList<>();
        for (int index = 0; index < sidePanel.getChildCount(); index++) {
            children.add(sidePanel.getChildAt(index));
        }
        return new PanelSnapshot(currentPanelKey, children, focused,
                focused != null ? focused.getTag() : null, sidePanelScroll.getScrollY());
    }

    private void handlePanelBack() {
        if (streamingAutopilotTask != null) cancelStreamingAutopilot(false);
        if (discordSocialPanelController != null && discordSocialPanelController.prepareForPanelBack()) return;
        if (!panelHistory.isEmpty()) {
            prepareChildSharingForDeparture();
            PanelSnapshot snapshot = panelHistory.pop();
            sidePanel.removeAllViews();
            for (View child : snapshot.children) {
                sidePanel.addView(child);
                if (child instanceof ConsoleChildGameSharingPanel) {
                    ((ConsoleChildGameSharingPanel) child).resumeAfterRestore();
                }
            }
            consolePanel.updateLegend(sidePanel);
            currentPanelKey = snapshot.key;
            if (!reducedMotion) {
                sidePanelScroll.animate().cancel();
                sidePanelScroll.setAlpha(.55f);
                sidePanelScroll.setTranslationX(dp(18));
                sidePanelScroll.animate().alpha(1f).translationX(0f).setDuration(170L).start();
            }
            wireModalFocusTrap(false);
            View restore = snapshot.focusedTag != null
                    ? sidePanel.findViewWithTag(snapshot.focusedTag) : snapshot.focused;
            if (restore != null && restore.isFocusable() && restore.isEnabled()) {
                restore.requestFocus();
                mainHandler.postDelayed(() -> {
                    if (sideDialog != null && sideDialog.isShowing()
                            && restore.isAttachedToWindow()) restore.requestFocus();
                }, 32);
            } else {
                requestFirstModalFocus();
            }
            mainHandler.postDelayed(() -> {
                if (sideDialog != null && sideDialog.isShowing()) {
                    sidePanelScroll.scrollTo(0, snapshot.scrollY);
                }
            }, 48);
        } else {
            hideSidePanel();
        }
    }

    private void wireModalFocusTrap(boolean requestFirst) {
        List<View> focusable = new ArrayList<>();
        collectFocusable(sidePanel, focusable);
        if (focusable.isEmpty()) {
            sidePanelScroll.setFocusable(true);
            sidePanelScroll.post(sidePanelScroll::requestFocus);
            return;
        }
        sidePanelScroll.setFocusable(false);
        for (int index = 0; index < focusable.size(); index++) {
            View item = focusable.get(index);
            View up = focusable.get(Math.max(0, index - 1));
            View down = focusable.get(Math.min(focusable.size() - 1, index + 1));
            if (!isSpatialDiscordTile(item)) {
                if (item.getNextFocusUpId() == View.NO_ID) item.setNextFocusUpId(up.getId());
                if (item.getNextFocusDownId() == View.NO_ID) item.setNextFocusDownId(down.getId());
            }
        }
        if (requestFirst) focusable.get(0).post(focusable.get(0)::requestFocus);
    }

    private void requestFirstModalFocus() {
        List<View> focusable = new ArrayList<>();
        collectFocusable(sidePanel, focusable);
        if (!focusable.isEmpty()) focusable.get(0).post(focusable.get(0)::requestFocus);
    }

    private boolean isSpatialDiscordTile(View view) {
        Object tag = view.getTag();
        return tag instanceof String && ((String) tag).startsWith("discord.");
    }

    private boolean isDescendant(ViewGroup ancestor, View view) {
        View current = view;
        while (current != null) {
            if (current == ancestor) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void collectFocusable(View view, List<View> output) {
        if (view.getVisibility() != View.VISIBLE || !view.isEnabled()) return;
        if (view.isFocusable()) output.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectFocusable(group.getChildAt(index), output);
            }
        }
    }

    private void restoreContentFocus() {
        // A grid card may be removed or recycled by now, so the grid restores by position.
        if (expandedLibraryMode && expandedGrid != null && lastContentFocusTag instanceof String
                && ((String) lastContentFocusTag).startsWith("playnite:")) {
            expandedGrid.restoreFocus();
            return;
        }
        View target = lastContentFocus != null && lastContentFocus.isAttachedToWindow()
                && lastContentFocus.isShown() && lastContentFocus.isFocusable()
                ? lastContentFocus : null;
        if (target == null && lastContentFocusTag != null) {
            View tagged = (hostSelectionVisible && entryShell != null
                    ? entryShell : homeLayer).findViewWithTag(lastContentFocusTag);
            if (tagged != null && tagged.isShown() && tagged.isFocusable()) target = tagged;
        }
        // A card re-rendered meanwhile (the host went to sleep): the carousel's selection.
        if (target == null && !hostSelectionVisible) target = carouselFocusTarget();
        if (target == null && !hostSelectionVisible) target = topBar.hostStatus();
        if (target != null) target.post(target::requestFocus);
        else if (hostSelectionVisible) requestHostSelectionFocus();
    }

    private TextView actionView(ConsoleAction resolved) {
        TextView action = consolePanel.action(resolved.unavailableReason == null
                ? resolved.label : consolePanel.withDetail(resolved.label.toString(),
                resolved.unavailableReason.toString()), resolved.enabled);
        action.setTag(resolved.id);
        if (resolved.destructive) consolePanel.destructive(action);
        if (resolved.enabled) action.setOnClickListener(view -> resolved.handler.run());
        if (resolved.unavailableReason != null) {
            action.setContentDescription(resolved.label + ". " + resolved.unavailableReason);
        }
        return action;
    }

    private TextView compactButton(String label) {
        TextView button = text(label, 14, 0xFFF0E9FF, true);
        button.setId(View.generateViewId());
        button.setFocusable(true);
        button.setClickable(true);
        button.setMinHeight(dp(46));
        button.setPadding(dp(16), dp(7), dp(16), dp(7));
        button.setGravity(Gravity.CENTER);
        button.setOnFocusChangeListener((view, focused) -> styleCompactButton(button, focused));
        styleCompactButton(button, false);
        return button;
    }

    private TextView metadataPill() {
        TextView pill = text("", 11, 0xFFF0F5F8, true);
        pill.setSingleLine(true);
        pill.setGravity(Gravity.CENTER);
        pill.setMinHeight(dp(24));
        pill.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = gradient(0xD91A2026, 0xE00E1318, 10);
        background.setStroke(dp(1), 0x667C8B96);
        pill.setBackground(background);
        return pill;
    }

    private LinearLayout cardBase(int width, int height) {
        LinearLayout card = new LinearLayout(this);
        card.setId(View.generateViewId());
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setFocusable(true);
        card.setClickable(true);
        card.setSoundEffectsEnabled(false);
        card.setMinimumWidth(width);
        card.setMinimumHeight(height);
        styleCard(card, false);
        return card;
    }

    private void styleCard(View card, boolean focused) {
        Object tag = card.getTag();
        boolean carouselCard = CONSOLE_UI_V2 && tag instanceof String
                && (((String) tag).startsWith("app:")
                || ((String) tag).startsWith("playnite:"));
        if (carouselCard) {
            if (!(card.getBackground() instanceof StateListDrawable)) {
                card.setBackground(carouselCardBackground());
            }
            card.setElevation(dp(focused ? 12 : 2));
            boolean homeCarousel = card.getParent() == appRow && !expandedLibraryMode;
            boolean playniteHomeCarousel = homeCarousel
                    && ((String) tag).startsWith("playnite:");
            if (playniteHomeCarousel) resizeHomeCarouselCard(card, focused);
            card.setPivotX(card.getWidth() > 0 ? card.getWidth() / 2f : dp(42));
            card.setPivotY(homeCarousel ? 0f
                    : card.getHeight() > 0 ? card.getHeight() / 2f : dp(75));
            float scale = focused ? playniteHomeCarousel ? 1f
                    : homeCarousel ? 1.10f : 1.035f : 1f;
            float lift = focused && !homeCarousel ? -dp(2) : 0f;
            card.animate().cancel();
            boolean atRest = card.getScaleX() == scale && card.getScaleY() == scale
                    && card.getTranslationY() == lift;
            if (reducedMotion || !card.isLaidOut() || atRest) {
                card.setScaleX(scale);
                card.setScaleY(scale);
                card.setTranslationY(lift);
            } else {
                card.animate().scaleX(scale).scaleY(scale).translationY(lift)
                        .setDuration(getResources().getInteger(
                                R.integer.console_motion_focus_ms)).start();
            }
            updateCarouselShine(card, focused && playniteHomeCarousel);
            return;
        }
        int top = focused ? 0xFF202A32 : 0xE01B2026;
        int bottom = focused ? 0xFF151C22 : 0xF012161B;
        GradientDrawable background = gradient(top, bottom, 14);
        background.setStroke(dp(focused ? 2 : 1), focused ? 0xFF8DDCFF : 0x425F6D76);
        card.setBackground(background);
        card.setElevation(dp(focused ? 7 : 2));
        animateScale(card, focused ? 1.04f : 1f);
    }

    private void resizeHomeCarouselCard(View card, boolean focused) {
        ViewGroup.LayoutParams params = card.getLayoutParams();
        if (params == null) return;
        int width = dp(focused
                ? CAROUSEL_FOCUSED_CARD_WIDTH_DP : CAROUSEL_CARD_WIDTH_DP);
        int height = dp(focused
                ? CAROUSEL_FOCUSED_CARD_HEIGHT_DP : CAROUSEL_CARD_HEIGHT_DP);
        if (params.width == width && params.height == height) return;
        params.width = width;
        params.height = height;
        card.setLayoutParams(params);
    }

    private void updateCarouselShine(View card, boolean focused) {
        if (!(card instanceof ViewGroup)) return;
        View shine = findTaggedChild((ViewGroup) card, "playnite.shine");
        if (shine == null) return;
        Object scheduled = card.getTag(R.id.carousel_shine_runnable);
        if (scheduled instanceof Runnable) shine.removeCallbacks((Runnable) scheduled);
        shine.animate().cancel();
        shine.setAlpha(0f);
        card.setTag(R.id.carousel_shine_runnable, null);
        if (!focused || reducedMotion) return;
        Runnable animation = new Runnable() {
            @Override public void run() {
                if (!card.hasFocus() || card.getParent() != appRow
                        || card.getTag(R.id.carousel_shine_runnable) != this) return;
                shine.setAlpha(0f);
                shine.setTranslationX(-dp(32));
                shine.animate().alpha(.36f)
                        .translationX(card.getWidth() + dp(32))
                        .setDuration(850L)
                        .withEndAction(() -> shine.animate().alpha(0f)
                                .setDuration(180L)
                                .withEndAction(() -> {
                                    if (card.hasFocus()
                                            && card.getTag(R.id.carousel_shine_runnable) == this) {
                                        shine.postDelayed(this, 7_000L);
                                    }
                                }).start())
                        .start();
            }
        };
        card.setTag(R.id.carousel_shine_runnable, animation);
        shine.postDelayed(animation, 2_200L);
    }

    private StateListDrawable carouselCardBackground() {
        GradientDrawable focused = gradient(0xF019222B, 0xF00C1116, 12);
        focused.setStroke(dp(1), 0xFFEAF8FF);
        GradientDrawable normal = gradient(0xC012171D, 0xD00A0E12, 12);
        normal.setStroke(dp(1), 0x305F6D76);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        background.addState(new int[0], normal);
        return background;
    }

    private StateListDrawable carouselCardForeground() {
        GradientDrawable focused = gradient(0x00000000, 0x00000000, 12);
        focused.setStroke(dp(1), 0xFFF5FAFF);
        GradientDrawable normal = gradient(0x00000000, 0x00000000, 12);
        StateListDrawable foreground = new StateListDrawable();
        foreground.addState(new int[]{android.R.attr.state_focused}, focused);
        foreground.addState(new int[0], normal);
        return foreground;
    }

    private void styleControllerPill(View pill, boolean focused) {
        GradientDrawable background = gradient(
                focused ? 0xF02A3A46 : 0xB51A222A,
                focused ? 0xF018242D : 0xC010151A, 18);
        background.setStroke(dp(focused ? 2 : 1),
                focused ? 0xFFBDEBFF : 0x465F7482);
        pill.setBackground(background);
        pill.setElevation(dp(focused ? 5 : 1));
        animateScale(pill, focused ? 1.04f : 1f);
    }

    private void styleCompactButton(View button, boolean focused) {
        int top = focused ? 0xFF24343F : 0x26242B32;
        int bottom = focused ? 0xFF18242C : 0x4813181D;
        GradientDrawable background = gradient(top, bottom, 10);
        background.setStroke(dp(1), focused
                ? getResources().getColor(R.color.console_accent) : 0x384C5962);
        button.setBackground(background);
        button.setElevation(dp(focused ? 4 : 0));
    }

    private void animateScale(View view, float scale) {
        view.animate().cancel();
        if (reducedMotion || suppressInitialCarouselMotion || !view.isLaidOut()) {
            view.setScaleX(scale); view.setScaleY(scale);
        } else {
            view.animate().scaleX(scale).scaleY(scale).setDuration(
                    getResources().getInteger(R.integer.console_motion_focus_ms)).start();
        }
    }

    private void saveScrollPositions() {
        SharedPreferences.Editor editor = preferences.edit()
                .putInt("controller_scroll", controllerScroll != null ? controllerScroll.getScrollX() : 0);
        if (selectedHostUuid != null) {
            int appPosition = portraitLayout && appVerticalScroll != null
                    ? appVerticalScroll.getScrollY()
                    : appScroll != null ? appScroll.getScrollX() : 0;
            editor.putInt("app_scroll." + selectedHostUuid, appPosition);
            // The focused home element, which a warm-up's Home focuses in this console's place.
            View focus = getCurrentFocus();
            Object focusTag = focus != null && homeContent != null
                    && isDescendant(homeContent, focus) ? focus.getTag() : null;
            editor.putString("home_focus." + selectedHostUuid,
                    focusTag instanceof String ? (String) focusTag : "");
            libraryViewStateStore.saveExpandedMode(selectedHostUuid, expandedLibraryMode);
        }
        editor.apply();
    }

    private void restoreHostLibraryState(String hostUuid) {
        if (hostUuid == null) return;
        ConsoleLibraryViewStateStore.State state = libraryViewStateStore.load(
                hostUuid, preferences.getString("selected_playnite." + hostUuid, ""));
        lastCarouselGameId = state.carouselGameId;
        expandedSearchQuery = state.searchQuery;
        pendingExpandedLibraryRestore = CONSOLE_UI_V2 && state.expandedMode;
    }

    private void wireHomeFocusNavigation() {
        if (topBar == null) return;
        View resume = quickResumeButton != null
                && quickResumeButton.getVisibility() == View.VISIBLE
                && quickResumeButton.isEnabled() ? quickResumeButton : null;
        wireDebugHomeFocusNavigation(resume, firstFocusableChild(controllerRow),
                firstFocusableChild(appRow));
    }
    /** The id a carousel card's tag names (a game or the full-library tile); null otherwise. */
    static String carouselGameId(Object tag) {
        return tag instanceof String && ((String) tag).startsWith("playnite:")
                && ((String) tag).length() > "playnite:".length()
                ? ((String) tag).substring("playnite:".length()) : null;
    }

    /** The carousel's selection to return to: its last focused card, else its first. */
    private View carouselFocusTarget() {
        View selected = lastCarouselGameId.isEmpty() ? null : carouselCard(lastCarouselGameId);
        return selected != null && selected.isFocusable() && selected.getVisibility()
                == View.VISIBLE ? selected : firstFocusableChild(appRow);
    }

    private View firstFocusableChild(ViewGroup row) {
        if (row == null) return null;
        for (int index = 0; index < row.getChildCount(); index++) {
            View child = row.getChildAt(index);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) return child;
        }
        return null;
    }

    private void restoreTaggedFocus(ViewGroup row, Object tag) {
        if (tag == null || row == null) return;
        for (int index = 0; index < row.getChildCount(); index++) {
            View child = row.getChildAt(index);
            if (tag.equals(child.getTag())) {
                child.post(child::requestFocus);
                return;
            }
        }
    }

    private void smoothCenterOn(HorizontalScrollView scroll, View tile) {
        if (scroll == null || tile == null) return;
        if (suppressInitialCarouselMotion) return;
        tile.post(() -> {
            boolean homeCarousel = CONSOLE_UI_V2 && scroll == appScroll
                    && tile.getParent() == appRow;
            int previousCards = dp(CAROUSEL_PREVIOUS_CARD_COUNT
                    * (CAROUSEL_CARD_WIDTH_DP + CAROUSEL_CARD_GAP_DP));
            int target = Math.max(0, tile.getLeft() - (homeCarousel ? previousCards
                    : (scroll.getWidth() - tile.getWidth()) / 2));
            if (reducedMotion) scroll.scrollTo(target, 0);
            else scroll.smoothScrollTo(target, 0);
        });
    }

    private void smoothCenterOn(ScrollView scroll, View tile) {
        if (scroll == null || tile == null) return;
        if (suppressInitialCarouselMotion) return;
        tile.post(() -> {
            int target = Math.max(0, tile.getTop() - (scroll.getHeight() - tile.getHeight()) / 2);
            if (reducedMotion) scroll.scrollTo(0, target);
            else scroll.smoothScrollTo(0, target);
        });
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override public void onInputDeviceAdded(int deviceId) { refreshControllers(); }
    @Override public void onInputDeviceRemoved(int deviceId) { refreshControllers(); }
    @Override public void onInputDeviceChanged(int deviceId) { refreshControllers(); }

    private HorizontalScrollView horizontalScroll() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFocusable(false);
        return scroll;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private TextView sectionLabel(String value) { return text(value, 12, 0xFF9CA6C5, true); }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setSoundEffectsEnabled(false);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams cardSpacing() {
        LinearLayout.LayoutParams params = portraitLayout
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                : wrapLinear();
        if (portraitLayout) params.bottomMargin = dp(14);
        else params.rightMargin = dp(CONSOLE_UI_V2 ? 10 : 14);
        return params;
    }

    private LinearLayout.LayoutParams controllerPillSpacing() {
        LinearLayout.LayoutParams params = wrapLinear();
        params.rightMargin = dp(16);
        return params;
    }

    private LinearLayout.LayoutParams sectionWithTop(int top) {
        LinearLayout.LayoutParams params = wrapLinear();
        params.topMargin = dp(top);
        return params;
    }

    private LinearLayout.LayoutParams wrapLinear() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private GradientDrawable gradient(int top, int bottom, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int blendColor(int from, int to, float amount) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    private static boolean isGamepad(InputDevice device) {
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    static boolean isDirectionalNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    static String compactControllerName(String name) {
        if (name == null) return "Controller";
        return name.toLowerCase(Locale.ROOT).contains("dualsense") ? "DualSense" : name;
    }

    private static final class ControllerInfo {
        final int deviceId;
        final String name;
        final int percentage;
        final int batteryStatus;

        ControllerInfo(int deviceId, String name, int percentage, int batteryStatus) {
            this.deviceId = deviceId;
            this.name = name != null ? name : "Controller";
            this.percentage = percentage;
            this.batteryStatus = batteryStatus;
        }

        boolean isCharging() { return batteryStatus == BatteryState.STATUS_CHARGING; }
        boolean isFull() { return batteryStatus == BatteryState.STATUS_FULL; }
    }

    private static final class ControllerBatteryDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int percentage;
        private final boolean charging;
        private final int color;

        ControllerBatteryDrawable(int percentage, boolean charging, int color) {
            this.percentage = percentage;
            this.charging = charging;
            this.color = color;
        }

        @Override public void draw(Canvas canvas) {
            float scale = Math.min(getBounds().width(), getBounds().height()) / 24f;
            float x = getBounds().left + (getBounds().width() - 24f * scale) / 2f;
            float y = getBounds().top + (getBounds().height() - 24f * scale) / 2f;
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(scale, scale);
            paint.setColor(color);
            paint.setStrokeWidth(1.7f);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(new RectF(5f, 4f, 19f, 22f), 2f, 2f, paint);
            canvas.drawLine(10f, 2.5f, 14f, 2.5f, paint);
            if (percentage >= 0) {
                float fill = Math.max(0f, Math.min(1f, percentage / 100f));
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(7f, 20f - 14f * fill, 17f, 20f),
                        1f, 1f, paint);
            }
            if (charging) {
                Path bolt = new Path();
                bolt.moveTo(14f, 6f);
                bolt.lineTo(9.5f, 13f);
                bolt.lineTo(13f, 13f);
                bolt.lineTo(10.5f, 19f);
                bolt.lineTo(16f, 11f);
                bolt.lineTo(12.5f, 11f);
                bolt.close();
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(bolt, paint);
            }
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 24; }
        @Override public int getIntrinsicHeight() { return 24; }
    }

    private static final class PanelSnapshot {
        final String key;
        final List<View> children;
        final View focused;
        final Object focusedTag;
        final int scrollY;

        PanelSnapshot(String key, List<View> children, View focused, Object focusedTag,
                      int scrollY) {
            this.key = key;
            this.children = children;
            this.focused = focused;
            this.focusedTag = focusedTag;
            this.scrollY = scrollY;
        }
    }

    static final class ProfileToolbarDrawable extends Drawable {
        private final HostAvatarDrawable avatar;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** Its Gateway image, drawn in place of the generated art once loaded. */
        private final String avatarId;
        private final String initials;
        /** Another Windows profile is active on the PC and this one can take over. */
        private final boolean switchable;

        /** The avatar fills its bounds: the bar gives it the canvas of the other icons. */
        ProfileToolbarDrawable(String profileId, String avatarId, String name,
                               boolean switchable) {
            avatar = new HostAvatarDrawable(profileId);
            this.avatarId = avatarId;
            initials = profileInitials(name);
            this.switchable = switchable;
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float size = Math.min(bounds.width(), bounds.height());
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            float radius = size / 2f;
            Rect avatarBounds = new Rect(Math.round(cx - radius), Math.round(cy - radius),
                    Math.round(cx + radius), Math.round(cy + radius));
            Bitmap image = ProfileAvatars.cached(avatarId);
            if (image != null) {
                Drawable art = new ConsoleProfileSelectView.RoundArt(image);
                art.setBounds(avatarBounds);
                art.draw(canvas);
            } else {
                avatar.setBounds(avatarBounds);
                avatar.draw(canvas);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                paint.setTextSize(size * .36f);
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(initials, cx, baseline, paint);
            }

            if (!switchable) return;
            // Cut into the lower right edge, as the badges of the profile screen.
            float badge = size * .15f;
            float edge = radius * .7071f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF0B0D12);
            canvas.drawCircle(cx + edge, cy + edge, badge + size * .05f, paint);
            paint.setColor(0xFFFFB74D);
            canvas.drawCircle(cx + edge, cy + edge, badge, paint);
        }

        @Override public void setAlpha(int alpha) {
            avatar.setAlpha(alpha);
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            avatar.setColorFilter(filter);
            paint.setColorFilter(null);
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        private static String profileInitials(String value) {
            String name = value == null ? "" : value.trim();
            if (name.isEmpty()) return "MW";
            String[] words = name.split("\\s+");
            if (words.length > 1) {
                return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1))
                        .toUpperCase(Locale.ROOT);
            }
            return words[0].substring(0, Math.min(2, words[0].length()))
                    .toUpperCase(Locale.ROOT);
        }
    }

    private void wireDebugHomeFocusNavigation(View resume, View controller, View app) {
        topBar.wireFocus(app != null ? app : controller);
        if (resume != null) {
            if (app != null) resume.setNextFocusUpId(app.getId());
            resume.setNextFocusRightId(resume.getId());
            if (controller != null) resume.setNextFocusDownId(controller.getId());
        }
        if (controller != null) {
            View aboveController = resume != null ? resume : app;
            if (aboveController != null) controller.setNextFocusUpId(aboveController.getId());
        }
        View belowApps = resume != null ? resume : controller;
        if (appRow != null) {
            List<View> focusableApps = new ArrayList<>();
            for (int index = 0; index < appRow.getChildCount(); index++) {
                View child = appRow.getChildAt(index);
                if (!child.isFocusable()) continue;
                focusableApps.add(child);
                child.setNextFocusUpId(topBar.hostStatus().getId());
                if (belowApps != null) child.setNextFocusDownId(belowApps.getId());
            }
            for (int index = 0; index < focusableApps.size(); index++) {
                View child = focusableApps.get(index);
                child.setNextFocusRightId(index + 1 < focusableApps.size()
                        ? focusableApps.get(index + 1).getId() : child.getId());
            }
        }
    }

    private enum BluetoothAction { POWER_OFF, UNPAIR }
}
