package com.limelight.ui.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.BuildConfig;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler.ControllerBatteryInfo;
import com.limelight.binding.input.GyroAim;
import com.limelight.console.EmbeddedTvKeyboardView;
import com.limelight.discord.DiscordSocialClient;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.stream.LiveStreamSettings;
import com.limelight.console.DiscordCommunityPresentation;
import com.limelight.ui.ControllerGlyphDrawable;
import com.limelight.ui.ControllerGlyphs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class OverlayMenuView extends LinearLayout {
    private static final int BUTTON_SPACING_DP = 8;
    /** The side panel on the Menu tab; the Discord tab widens it to the Community width. */
    private static final int PANEL_WIDTH_DP = 350;
    private static final int PANEL_PADDING_DP = 20;
    private static final int PANEL_MARGIN_DP = 12;
    private static final int PANEL_SURFACE = 0xE60C0E16;
    private static final int ROW_REST_FILL = 0x00000000;
    private static final int PRIMARY_REST_FILL = 0x1FFFFFFF;
    private static final int DESTRUCTIVE_TEXT = 0xFFFF6B6B;
    private static final int WARNING_TEXT = 0xFFFFB74D;
    private static final int ACCENT = 0xFF73D7FF;
    private static final int GOOD = 0xFF69F0AE;
    private static final float TRIGGER_THRESHOLD = 0.6f;
    private static final int COMMUNITY_QUICK_WIDTH_DP = 520;
    private static final int COMMUNITY_CHAT_WIDTH_DP = 600;
    private static final int COMMUNITY_LIST_SCROLL_DP = 84;
    private static final int COMMUNITY_HISTORY_SCROLL_DP = 96;
    private static final int COMMUNITY_RAIL_ICON_COUNT = 3;
    private static final float ANALOG_STICK_THRESHOLD = 0.5f;
    // A held direction leaves the menu below this, so stick jitter at the edge is not a press.
    private static final float ANALOG_STICK_RELEASE = 0.35f;
    // Held D-pad (keys or HAT) and stick: one step at once, then a steady fast repeat.
    private static final long NAV_REPEAT_DELAY_MS = 250;
    private static final long NAV_REPEAT_INTERVAL_MS = 60;
    private static final int NAV_KEY = 0, NAV_MOTION = 1;
    private static final int FADING_EDGE_DP = 36;
    private static final long ANALOG_NAV_THROTTLE_MS = 200;

    public interface MenuActionListener {
        void onHome();
        void onEndGame();
        /** "Freeze and play something else" (Quick Resume). */
        void onFreezeGame();
        void onQuitSession();
        void onSuspendSession();
        void onToggleStats();
        void onToggleMouseEmulation();
        void onShowKeyboard();
        void onSendGuideButton();
        /** Also keeps the settings for the game running now (Game.streamSettingsKey). */
        void onApplyStreamSettings(LiveStreamSettings settings);
        /** The poor-link card: reconnect with {@code settings} for this session only. */
        void onEmergencyStream(LiveStreamSettings settings);
        /** The emergency card: reconnect with the settings the emergency stream returns to. */
        void onLeaveEmergencyStream();
        /** More › Streaming Autopilot: the test of the current settings starts, or stops. */
        void onAutopilotTest();
        /** The history's proposal: kept for the game, the stream reconnects with it. */
        void onAutopilotApplyAdvice();
        /** More › Streaming Autopilot › gyro: a short try of gyro aiming starts. */
        void onAutopilotGyroTrial();
        /** More › Streaming Autopilot › another variant: its index in the page's list. */
        void onAutopilotVariant(int index);
        /** A {@link Prompt}'s option, or {@link #PROMPT_DISMISSED}; the menu has closed. */
        void onPromptChoice(int option);
        void onCustomCommand(CustomCommand command);
        void onDiscordMute();
        void onDiscordLeave();
        void onDiscordRejoin();
        void onDiscordDockToggle();
        default void onDiscordCommunityOpenFriendChat(String friendId) { }
        default void onDiscordCommunityLoadGuild(String guildId) { }
        default void onDiscordCommunityJoinChannel(String channelId) { }
        default void onDiscordCommunityChatDraftChanged(String friendId, String draft) { }
        default void onDiscordCommunitySendChat(String friendId, String draft) { }
        default void onDiscordCommunityOpenMessageInDiscord(String messageId) { }
        default void onDiscordCommunityBackToFriends() { }
        default void onDiscordCommunityBackToChannels() { }
        default void onDiscordCommunityOpened() { }
        default void onDiscordCommunitySectionChanged(CommunitySection section) { }
        default void onDiscordCommunityAuthorizeDirectMessages() { }
        void onInstallationConfirmed();
        /** The USB page opened: the listener answers with {@link #setUsbDevices}. */
        void onUsbDevices();
        /** Shares a device (after the user agreed in the menu) or stops it. */
        void onUsbDeviceChosen(String deviceName, boolean share);
        void onMenuClosed();
        /** A quick gyro change; the menu already shows {@code next}. */
        void onGyroAimSettings(GyroAim.Settings next);
        /** "Calibrate gyro": the menu closes so the pad's samples reach the gyro again. */
        void onCalibrateGyro();
        /** More › Audio haptics changed its mode or strength; the menu already shows them. */
        void onAudioHaptics(String mode, int strengthPercent);
        /** More › "Zaznacz moment": marks the time a later problem report covers. */
        void onMarkMoment();
        /** More › "Zgłoś problem": the menu closes and the report card opens. */
        void onReportProblem();
        /** Zrzuty › "Zrzut ekranu" or ({@code clip}) "Zapisz ostatnie 30 s"; the menu closes. */
        void onCapture(boolean clip);
    }

    /** The captures group's rows for screenshots and clips (#51, mockups 25a-25c). */
    public enum Captures {
        /** No MoonWaker host behind this stream. */
        HIDDEN,
        AVAILABLE,
        /** The profile turned "Record the last 30 s" off: the clip row is off with its note. */
        BUFFER_OFF,
        /** The host refused for want of Host Plus: one row, off, with that note. */
        HOST_PLUS_REQUIRED
    }

    /** What More › Streaming Autopilot offers this stream. */
    public enum AutopilotTest { AVAILABLE, RUNNING, UNAVAILABLE }

    /** One of the last analysis' variants, as More › Streaming Autopilot lists it (O3). */
    public static final class AutopilotVariant {
        public final String name;
        public final String settings;
        public final String description;
        public final int icon;
        public final boolean recommended;
        public final boolean current;

        public AutopilotVariant(String name, String settings, String description, int icon,
                                boolean recommended, boolean current) {
            this.name = name;
            this.settings = settings;
            this.description = description;
            this.icon = icon;
            this.recommended = recommended;
            this.current = current;
        }
    }

    /** A prompt closed without a choice: Back, or the menu closing. */
    public static final int PROMPT_DISMISSED = -1;

    public enum Tone { GOOD, WARNING, INFO }

    /**
     * A page the menu shows in place of its list (mockups O6–O8: a test's result, how the gyro
     * try went): its own header, a verdict card in its tone, optional metric tiles and the
     * "was → proposal" line, an optional question, then the options. The listener gets the
     * chosen option's id. Its options wait a moment before they act.
     */
    public static final class Prompt {
        public final String overline;
        public final String title;
        public final String subtitle;
        public final Tone tone;
        public final String cardTitle;
        public final String cardText;
        public final List<Tile> tiles;
        /** The settings tested and the proposal; both null without a change. */
        public final String changeFrom;
        public final String changeTo;
        /** Asked above the options; null without. */
        public final String question;
        public final List<PromptOption> options;
        /** What Back does, for the hint bar. */
        public final String back;

        public Prompt(String overline, String title, String subtitle, Tone tone,
                      String cardTitle, String cardText, List<Tile> tiles, String changeFrom,
                      String changeTo, String question, List<PromptOption> options,
                      String back) {
            this.overline = overline;
            this.title = title;
            this.subtitle = subtitle;
            this.tone = tone;
            this.cardTitle = cardTitle;
            this.cardText = cardText;
            this.tiles = tiles;
            this.changeFrom = changeFrom;
            this.changeTo = changeTo;
            this.question = question;
            this.options = options;
            this.back = back;
        }
    }

    /** One measured value of a result; {@code bad} outlines it as over its limit. */
    public static final class Tile {
        public final String label;
        public final String value;
        public final String unit;
        public final boolean bad;

        public Tile(String label, String value, String unit, boolean bad) {
            this.label = label;
            this.value = value;
            this.unit = unit;
            this.bad = bad;
        }
    }

    public static final class PromptOption {
        public final int icon;
        public final String label;
        /** What choosing it does, under the label; null without. */
        public final String detail;
        public final int id;

        public PromptOption(int icon, String label, String detail, int id) {
            this.icon = icon;
            this.label = label;
            this.detail = detail;
            this.id = id;
        }
    }

    /** Immutable data projection for the stream overlay; network and SDK ownership stay outside. */
    public static final class CommunityModel {
        public final long revision;
        public final CommunityStatus gatewayStatus;
        public final String gatewayMessage;
        public final CommunityStatus socialStatus;
        public final String socialMessage;
        public final VoiceSummary voice;
        public final List<CommunityFriend> friends;
        public final List<CommunityChannel> favorites;
        public final List<CommunityChannel> recent;
        public final List<CommunityGuild> guilds;
        public final List<CommunityChannel> guildChannels;
        public final String selectedGuildId;
        public final boolean directMessagesAvailable;
        public final ChatModel chat;

        public CommunityModel(long revision, CommunityStatus gatewayStatus, String gatewayMessage,
                              CommunityStatus socialStatus, String socialMessage, VoiceSummary voice,
                              List<CommunityFriend> friends, List<CommunityChannel> favorites,
                              List<CommunityChannel> recent, List<CommunityGuild> guilds,
                              List<CommunityChannel> guildChannels, String selectedGuildId,
                              boolean directMessagesAvailable, ChatModel chat) {
            this.revision = revision;
            this.gatewayStatus = gatewayStatus == null ? CommunityStatus.UNAVAILABLE : gatewayStatus;
            this.gatewayMessage = clean(gatewayMessage);
            this.socialStatus = socialStatus == null ? CommunityStatus.UNAVAILABLE : socialStatus;
            this.socialMessage = clean(socialMessage);
            this.voice = voice;
            this.friends = immutable(friends);
            this.favorites = immutable(favorites);
            this.recent = immutable(recent);
            this.guilds = immutable(guilds);
            this.guildChannels = immutable(guildChannels);
            this.selectedGuildId = clean(selectedGuildId);
            this.directMessagesAvailable = directMessagesAvailable;
            this.chat = chat;
        }
    }

    public enum CommunityStatus { LOADING, READY, UNAVAILABLE, ERROR }
    public enum CommunitySection { TOGETHER, FRIENDS, CHANNELS }

    public static final class VoiceSummary {
        public final boolean connected, muted, deafened;
        public final String guildName, channelName;
        public final List<VoiceParticipant> participants;
        public VoiceSummary(boolean connected, String guildName, String channelName, boolean muted,
                            boolean deafened, List<VoiceParticipant> participants) {
            this.connected = connected; this.guildName = clean(guildName); this.channelName = clean(channelName);
            this.muted = muted; this.deafened = deafened; this.participants = immutable(participants);
        }
    }
    public static final class VoiceParticipant {
        public final String id, name, avatarUrl;
        public final int volume;
        public final boolean muted, speaking, self;
        public VoiceParticipant(String id, String name, int volume, boolean muted,
                                boolean speaking, boolean self) {
            this(id, name, volume, muted, speaking, self, "");
        }
        public VoiceParticipant(String id, String name, int volume, boolean muted,
                                boolean speaking, boolean self, String avatarUrl) {
            this.id = clean(id); this.name = clean(name); this.volume = Math.max(0, Math.min(200, volume));
            this.muted = muted; this.speaking = speaking; this.self = self;
            this.avatarUrl = clean(avatarUrl);
        }
    }
    public static final class CommunityFriend {
        public final String id, name, activity, avatarUrl;
        public final FriendPresence presence;
        public final boolean unread;
        public CommunityFriend(String id, String name, String activity, String avatarUrl,
                               FriendPresence presence) {
            this(id, name, activity, avatarUrl, presence, false);
        }
        public CommunityFriend(String id, String name, String activity, String avatarUrl,
                               FriendPresence presence, boolean unread) {
            this.id = clean(id); this.name = clean(name); this.activity = clean(activity);
            this.avatarUrl = clean(avatarUrl); this.presence = presence == null ? FriendPresence.OFFLINE : presence;
            this.unread = unread;
        }
    }
    public enum FriendPresence { PLAYING, ONLINE, OFFLINE }
    public static final class CommunityGuild {
        public final String id, name;
        public CommunityGuild(String id, String name) { this.id = clean(id); this.name = clean(name); }
    }
    public static final class CommunityChannel {
        public final String id, guildId, guildName, name;
        public final int people;
        public final boolean favorite;
        public CommunityChannel(String id, String guildId, String guildName, String name,
                                int people, boolean favorite) {
            this.id = clean(id); this.guildId = clean(guildId); this.guildName = clean(guildName);
            this.name = clean(name); this.people = people; this.favorite = favorite;
        }
    }
    public static final class ChatModel {
        public final String recipientId, recipientName, recipientAvatarUrl, draft, error, retryMessage;
        public final List<ChatMessage> messages;
        public final boolean loadingHistory, sending, retryable;
        public ChatModel(String recipientId, String recipientName, String recipientAvatarUrl,
                         String draft, List<ChatMessage> messages, boolean loadingHistory,
                         boolean sending, String error, boolean retryable, String retryMessage) {
            this.recipientId = clean(recipientId); this.recipientName = clean(recipientName);
            this.recipientAvatarUrl = clean(recipientAvatarUrl); this.draft = clean(draft);
            this.messages = immutable(messages); this.loadingHistory = loadingHistory; this.sending = sending;
            this.error = clean(error); this.retryable = retryable; this.retryMessage = clean(retryMessage);
        }
    }
    public static final class ChatMessage {
        public final String id, content, additionalContentType, additionalContentTitle;
        public final int additionalContentCount;
        public final boolean self, disclosure;
        public ChatMessage(String id, String content, String additionalContentType,
                           String additionalContentTitle, int additionalContentCount,
                           boolean self, boolean disclosure) {
            this.id = clean(id); this.content = clean(content); this.additionalContentType = clean(additionalContentType);
            this.additionalContentTitle = clean(additionalContentTitle);
            this.additionalContentCount = Math.max(0, additionalContentCount);
            this.self = self; this.disclosure = disclosure;
        }
    }

    private static String clean(String value) { return value == null ? "" : value; }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.<T>emptyList() : values));
    }

    private LinearLayout panel;
    private TextView panelOverline;
    private TextView panelTitle;
    private TextView panelSubtitle;
    /** The pads, bottom-right of the screen like the console carousel's controller row. */
    private LinearLayout padDock;
    private TextView childRemainingChip;
    private LinearLayout tabRow;
    private TextView menuTab;
    private TextView discordTab;
    private ScrollView menuScroll;
    /** Under the gyro or variant list while open: what the focused option does. */
    private TextView gyroDescription;
    private LinearLayout menuList;
    private LinearLayout discordContainer;
    private LinearLayout discordRail;
    private LinearLayout discordQuickMain;
    private LinearLayout discordQuickHeader;
    private LinearLayout discordQuickFooter;
    private ScrollView discordContentScroll;
    private LinearLayout discordContentContainer;
    private LinearLayout discordVoiceContentContainer;
    private LinearLayout discordSocialContentContainer;
    private LinearLayout discordActionsContainer;
    private LinearLayout discordChatContainer;
    private ScrollView discordChatHistoryScroll;
    private LinearLayout discordChatHistory;
    private TextView discordChatStatus;
    private EditText discordChatComposer;
    private EmbeddedTvKeyboardView discordChatKeyboard;

    private final List<OverlayMenuButton> menuButtons = new ArrayList<>();
    private final List<Integer> menuActions = new ArrayList<>();
    /** Rewrites each value row of the open list from the current settings. */
    private final List<Runnable> menuRefreshers = new ArrayList<>();
    private List<OverlayMenuButton> discordButtons;
    private List<Integer> discordActions;

    private enum Region { MENU, DISCORD }
    enum OverlayMode { MENU, COMMUNITY }
    private enum CommunityFocus { RAIL, LIST, ACTION, CHAT_COMPOSER, CHAT_KEYBOARD }
    enum CommunitySubmode { ROOT, GUILD_CHANNELS, FRIEND_CHAT }
    private enum CommunityRowKind { INFO, FRIEND, GUILD, CHANNEL }
    static final int COMMUNITY_REGION_RAIL = 0;
    static final int COMMUNITY_REGION_CONTENT = 1;
    static final int COMMUNITY_REGION_ACTION = 2;
    private OverlayMode overlayMode = OverlayMode.MENU;
    private CommunityFocus communityFocus = CommunityFocus.RAIL;
    private Region activeRegion = Region.MENU;
    private int menuIndex = 0;
    /** Menu index of "Back to game"; it and Home share the first row. -1 in the shortcut list. */
    private int menuPairStart = -1;
    private boolean moreOpen;
    private boolean usbOpen;
    /** The device whose sharing waits for the user's consent on the USB page, if any. */
    private String usbConsent;
    private List<UsbRow> usbRows = new ArrayList<>();
    private boolean gyroOpen;
    private boolean streamOpen;
    private boolean autopilotOpen;
    /** The Autopilot page's list of variants is open (mockup O3). */
    private boolean autopilotVariantsOpen;
    private List<AutopilotVariant> autopilotVariants = Collections.emptyList();
    private AutopilotTest autopilotTest = AutopilotTest.UNAVAILABLE;
    /** The history's proposal for the game, as the card says it; null without one. */
    private String autopilotAdvice;
    /** The question the menu shows instead of its list; null otherwise. */
    private Prompt prompt;
    private long promptActionsAtMs;
    /** The Autopilot page offers a gyro try (a pad with gyro, no test running). */
    private boolean autopilotGyroTrial;
    private boolean leftTriggerDown;
    private boolean rightTriggerDown;
    private String sessionTitle = "";
    private String sessionHost = "";
    private int discordIndex = 0;

    private MenuActionListener actionListener;
    private CustomCommandsManager commandsManager;

    private static final int ACTION_HOME = 0;
    private static final int ACTION_QUIT = 1;
    private static final int ACTION_TOGGLE_STATS = 2;
    private static final int ACTION_CLOSE = 3;
    private static final int ACTION_SHOW_KEYBOARD = 4;
    private static final int ACTION_TOGGLE_MOUSE_EMULATION = 5;
    private static final int ACTION_SEND_GUIDE = 6;
    private static final int ACTION_STREAM_SETTINGS = 8;
    private static final int ACTION_DISCORD_MUTE = 10;
    private static final int ACTION_DISCORD_LEAVE = 11;
    private static final int ACTION_DISCORD_REJOIN = 13;
    private static final int ACTION_DISCORD_DOCK = 14;
    private static final int ACTION_INSTALLATION_CONFIRMED = 15;
    private static final int ACTION_SUSPEND_SESSION = 16;
    private static final int ACTION_END_GAME = 17;
    private static final int ACTION_FREEZE_GAME = 44;
    private static final int ACTION_AUDIO_HAPTICS = 45;
    static final int ACTION_AUDIO_HAPTICS_STRENGTH = 46;
    private static final int ACTION_MORE = 18;
    private static final int ACTION_GYRO = 19;
    // The gyro list; isGyroOption relies on AIM..GEARING being one range.
    static final int ACTION_GYRO_AIM = 20;
    static final int ACTION_GYRO_OUTPUT = 21;
    static final int ACTION_GYRO_ACTIVATION = 22;
    static final int ACTION_GYRO_BUTTON = 23;
    static final int ACTION_GYRO_BUTTON_ONLY = 24;
    static final int ACTION_GYRO_SENS_X = 25;
    static final int ACTION_GYRO_SENS_Y = 26;
    static final int ACTION_GYRO_INVERT_X = 27;
    static final int ACTION_GYRO_INVERT_Y = 28;
    static final int ACTION_GYRO_AXIS = 29;
    static final int ACTION_GYRO_DEADZONE = 30;
    static final int ACTION_GYRO_CURVE = 31;
    static final int ACTION_GYRO_ACCELERATION = 32;
    static final int ACTION_GYRO_STICK_PAUSE = 33;
    /** Stick turn speed or mouse counts per 360 degrees, whichever the output uses. */
    static final int ACTION_GYRO_GEARING = 34;
    private static final int ACTION_GYRO_CALIBRATE = 40;
    // The stream list; isStreamOption relies on BITRATE..HDR being one range.
    private static final int ACTION_STREAM_BITRATE = 35;
    private static final int ACTION_STREAM_RESOLUTION = 36;
    private static final int ACTION_STREAM_FPS = 37;
    private static final int ACTION_STREAM_HDR = 38;
    private static final int ACTION_STREAM_APPLY = 39;
    private static final int ACTION_USB_DEVICES = 41;
    private static final int ACTION_USB_SHARE = 42;
    private static final int ACTION_USB_CANCEL = 43;
    private static final int ACTION_MARK_MOMENT = 47;
    private static final int ACTION_REPORT_PROBLEM = 48;
    private static final int ACTION_EMERGENCY_STREAM = 49;
    private static final int ACTION_LEAVE_EMERGENCY_STREAM = 50;
    private static final int ACTION_AUTOPILOT = 51;
    private static final int ACTION_AUTOPILOT_TEST = 52;
    private static final int ACTION_AUTOPILOT_ADVICE = 53;
    private static final int ACTION_AUTOPILOT_GYRO = 54;
    private static final int ACTION_AUTOPILOT_VARIANTS = 55;
    private static final int ACTION_SCREENSHOT = 56;
    private static final int ACTION_SAVE_CLIP = 57;
    /** One per variant row of the Autopilot page. */
    private static final int ACTION_AUTOPILOT_VARIANT_BASE = 6000;
    /** One per prompt option, above the USB rows' range. */
    private static final int ACTION_PROMPT_BASE = 5000;
    /** A prompt's options wait this long: a press meant for the game must not choose one. */
    static final long RESULT_ACTION_DELAY_MS = 2_000L;
    /** One per USB device row; above the custom commands' range. */
    private static final int ACTION_USB_BASE = 1000;
    private static final int ACTION_CUSTOM_BASE = 100;
    private static final int BITRATE_STEP_KBPS = 5000;
    private static final int BITRATE_MIN_KBPS = 1000;
    private static final int BITRATE_MAX_KBPS = 150000;

    /** The menu direction being held, by keys or by HAT/stick motion; repeats while held. */
    private int heldNavKey = KeyEvent.KEYCODE_UNKNOWN;
    private int heldNavSource;
    private int motionNavKey = KeyEvent.KEYCODE_UNKNOWN;
    private final Runnable navRepeat = new Runnable() {
        @Override
        public void run() {
            if (heldNavKey == KeyEvent.KEYCODE_UNKNOWN || getVisibility() != VISIBLE
                    || overlayMode != OverlayMode.MENU) return;
            moveMenu(heldNavKey);
            postDelayed(this, NAV_REPEAT_INTERVAL_MS);
        }
    };
    private final KeyLatch ownedKeys = new KeyLatch();
    private boolean flipFaceButtons = false;
    private List<ControllerBatteryInfo> controllerBatteryInfo = new ArrayList<>();
    private LiveStreamSettings currentStream;
    private LiveStreamSettings pendingStream;
    private LiveStreamSettings.Options streamOptions;
    private boolean discordFeatureEnabled = true;
    private boolean suspendSessionAvailable = true;
    private boolean discordConfigured;
    private boolean discordLoading;
    private String discordError;
    private String discordMuteShortcut = "x";
    private String discordLeaveShortcut = "y";
    private final GuideShortcutLatch discordShortcutChord = new GuideShortcutLatch();
    private DiscordGatewayClient.VoiceState discordVoice;
    private OverlayMenuButton discordMuteButton;
    private OverlayMenuButton discordLeaveButton;
    private OverlayMenuButton discordRejoinButton;
    private OverlayMenuButton discordDockButton;
    private boolean discordCanRejoin;
    private String discordRejoinChannel = "";
    private boolean discordDocked;
    private boolean discordSocialAvailable;
    private long discordSocialRevision = Long.MIN_VALUE;
    private long renderedDiscordSocialRevision = Long.MIN_VALUE;
    private int renderedDiscordSocialSection = -1;
    private String renderedDiscordActionSignature = "";
    private boolean discordSocialConnected;
    private String discordSocialDisplayName = "";
    private String discordSocialAvatarUrl = "";
    private List<DiscordSocialClient.Friend> discordSocialFriends = new ArrayList<>();
    private boolean discordSocialExpanded;
    private OverlayMenuButton discordSocialFriendsButton;
    private List<ImageButton> discordRailIcons = new ArrayList<>();
    private int discordQuickSection;
    private int communityMotionDirection = KeyEvent.KEYCODE_UNKNOWN;
    private long lastCommunityDirectionalKeyAt;
    private long lastCommunityScrollTime;
    private CommunityModel communityModel;
    private CommunitySubmode communitySubmode = CommunitySubmode.ROOT;
    private CommunitySection communitySection = CommunitySection.TOGETHER;
    private final List<CommunityRow> communityRows = new ArrayList<>();
    private int communityListIndex = -1;
    private String selectedTogetherRowId = "";
    private String selectedFriendRowId = "";
    private String selectedChannelsRootRowId = "";
    private String selectedGuildChannelRowId = "";
    private String selectedGuildChannelGuildId = "";
    private String selectedFriendId = "";
    private String selectedGuildId = "";
    private String selectedChannelId = "";
    private String pendingGuildId = "";
    private String pendingChatFriendId = "";
    private long communityRenderGeneration;
    private long communityInteractionGeneration;
    private long renderedCommunityModelRevision = Long.MIN_VALUE;
    private CommunitySection renderedCommunitySection;
    private CommunitySubmode renderedCommunitySubmode;
    private String renderedCommunityHeaderSignature = "";
    private String renderedChatRecipientId = "";
    private String renderedChatLastOutgoingMessageId = "";
    private String renderedChatStructureSignature = "";
    private String renderedChatDraft = "";
    private String renderedChatStatusSignature = "";
    private boolean communityCancelKeyDown;
    private boolean updatingChatComposer;
    private boolean installationConfirmationAvailable;
    /** What the poor-link card offers; null without that card. */
    private LiveStreamSettings emergencyStream;
    private int emergencyObservedKbps;
    /** The settings this emergency stream returns to; null when it is none. */
    private LiveStreamSettings emergencyReturn;
    private boolean endGameAvailable;
    private Captures captures = Captures.HIDDEN;
    /** The stream's gyro settings; null hides the gyro row (no pad gyro, or sensors off). */
    private GyroAim.Settings gyroSettings;
    private List<GyroAim.Button> gyroButtons = new ArrayList<>();
    private String childRemainingTime;
    private boolean playStationButtons;
    private String audioHapticsMode = "off";
    private int audioHapticsStrength = PreferenceConfiguration.DEFAULT_AUDIO_HAPTICS_STRENGTH;

    public OverlayMenuView(Context context) {
        super(context);
        init(context);
    }

    public OverlayMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OverlayMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.START);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundDrawable(null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP),
                dp(16));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(PANEL_SURFACE);
        panelBackground.setCornerRadius(dp(18));
        panelBackground.setStroke(dp(1), 0x1FFFFFFF);
        panel.setBackground(panelBackground);
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                dp(PANEL_WIDTH_DP), LinearLayout.LayoutParams.MATCH_PARENT);
        panelParams.setMargins(dp(PANEL_MARGIN_DP), dp(PANEL_MARGIN_DP), 0, dp(PANEL_MARGIN_DP));
        addView(panel, panelParams);
        padDock = new LinearLayout(context);
        padDock.setOrientation(LinearLayout.HORIZONTAL);
        padDock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dockParams.gravity = Gravity.BOTTOM;
        dockParams.setMargins(dp(24), 0, dp(48), dp(27));
        addView(padDock, dockParams);

        panelOverline = panelText(context, 10, OverlayMenuButton.TEXT_SECONDARY, true);
        panelOverline.setLetterSpacing(0.16f);
        panelOverline.setAllCaps(true);
        panel.addView(panelOverline);
        panelTitle = panelText(context, 18, OverlayMenuButton.TEXT, true);
        panelTitle.setMaxLines(2);
        panelTitle.setLineSpacing(0f, 1.1f);
        panelTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panel.addView(panelTitle, topMargin(dp(7)));
        LinearLayout subtitleRow = new LinearLayout(context);
        subtitleRow.setOrientation(LinearLayout.HORIZONTAL);
        subtitleRow.setGravity(Gravity.CENTER_VERTICAL);
        panelSubtitle = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
        panelSubtitle.setSingleLine(true);
        panelSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitleRow.addView(panelSubtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        childRemainingChip = panelText(context, 10, WARNING_TEXT, true);
        childRemainingChip.setSingleLine(true);
        childRemainingChip.setPadding(dp(6), dp(1), dp(7), dp(1));
        childRemainingChip.setBackground(pill(0x1AFFB74D, 0x55FFB74D));
        android.graphics.drawable.Drawable clock = context.getDrawable(R.drawable.ic_console_clock).mutate();
        clock.setTint(WARNING_TEXT);
        clock.setBounds(0, 0, dp(11), dp(11));
        childRemainingChip.setCompoundDrawablesRelative(clock, null, null, null);
        childRemainingChip.setCompoundDrawablePadding(dp(3));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipParams.leftMargin = dp(6);
        subtitleRow.addView(childRemainingChip, chipParams);
        panel.addView(subtitleRow, topMargin(dp(7)));

        tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.addView(tabGlyph(context, ControllerGlyphs.Button.LEFT_BUMPER));
        menuTab = tabLabel(context, context.getString(R.string.overlay_menu_tab_menu));
        tabRow.addView(menuTab);
        discordTab = tabLabel(context, context.getString(R.string.overlay_menu_tab_discord));
        tabRow.addView(discordTab);
        tabRow.addView(tabGlyph(context, ControllerGlyphs.Button.RIGHT_BUMPER));
        panel.addView(tabRow, topMargin(dp(24)));

        FrameLayout body = new FrameLayout(context);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyParams.topMargin = dp(22);
        panel.addView(body, bodyParams);
        menuScroll = new ScrollView(context);
        menuScroll.setVerticalScrollBarEnabled(false);
        fadeOverflow(menuScroll);
        menuScroll.setFocusable(false);
        menuScroll.setFillViewport(false);
        menuList = new LinearLayout(context);
        menuList.setOrientation(LinearLayout.VERTICAL);
        menuScroll.addView(menuList, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(menuScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gyroDescription = panelText(context, 12, OverlayMenuButton.TEXT_SECONDARY, false);
        gyroDescription.setVisibility(GONE);
        panel.addView(gyroDescription, topMargin(dp(8)));

        discordContainer = new LinearLayout(context);
        discordContainer.setOrientation(LinearLayout.HORIZONTAL);
        discordContainer.setVisibility(GONE);
        body.addView(discordContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        discordRail = new LinearLayout(context);
        discordRail.setOrientation(LinearLayout.VERTICAL);
        discordRail.setGravity(Gravity.CENTER_HORIZONTAL);
        discordRail.setPadding(dp(10), dp(16), dp(10), dp(12));
        discordRail.setBackgroundColor(0x0FFFFFFF);
        View dot = new View(context);
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(0xFF5BE28D);
        dot.setBackground(dotBackground);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.bottomMargin = dp(12);
        discordRail.addView(dot, dotParams);
        addDiscordRailIcon(context, R.drawable.ic_console_discord, R.string.discord_community_together, 0);
        addDiscordRailIcon(context, R.drawable.ic_overlay_community_friends, R.string.discord_community_friends, 1);
        addDiscordRailIcon(context, R.drawable.ic_overlay_community_servers, R.string.discord_community_servers, 2);
        discordContainer.addView(discordRail, new LinearLayout.LayoutParams(dp(68),
                LinearLayout.LayoutParams.MATCH_PARENT));

        discordQuickMain = new LinearLayout(context);
        discordQuickMain.setOrientation(LinearLayout.VERTICAL);
        discordQuickMain.setPadding(dp(18), dp(16), dp(18), dp(14));
        discordContainer.addView(discordQuickMain, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        discordQuickHeader = new LinearLayout(context);
        discordQuickHeader.setGravity(Gravity.CENTER_VERTICAL);
        discordQuickMain.addView(discordQuickHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)));
        discordContentScroll = new ScrollView(context);
        discordContentScroll.setFillViewport(true);
        discordContentScroll.setVerticalScrollBarEnabled(false);
        fadeOverflow(discordContentScroll);
        // Content rows own selection. A ScrollView must never become one giant focus target.
        discordContentScroll.setFocusable(false);
        discordContentScroll.setFocusableInTouchMode(false);
        discordContentScroll.setOnFocusChangeListener((ignored, focused) -> {
            if (focused && overlayMode == OverlayMode.COMMUNITY) {
                communityFocus = CommunityFocus.LIST;
                activeRegion = Region.DISCORD;
            }
        });
        discordQuickMain.addView(discordContentScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f));
        discordContentContainer = new LinearLayout(context);
        discordContentContainer.setOrientation(LinearLayout.VERTICAL);
        discordContentScroll.addView(discordContentContainer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        discordVoiceContentContainer = new LinearLayout(context);
        discordVoiceContentContainer.setOrientation(LinearLayout.VERTICAL);
        discordContentContainer.addView(discordVoiceContentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        discordSocialContentContainer = new LinearLayout(context);
        discordSocialContentContainer.setOrientation(LinearLayout.VERTICAL);
        discordContentContainer.addView(discordSocialContentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        discordChatContainer = new LinearLayout(context);
        discordChatContainer.setOrientation(LinearLayout.VERTICAL);
        discordChatContainer.setVisibility(GONE);
        discordChatHistoryScroll = new ScrollView(context);
        discordChatHistoryScroll.setFillViewport(true);
        discordChatHistoryScroll.setVerticalScrollBarEnabled(false);
        fadeOverflow(discordChatHistoryScroll);
        discordChatHistoryScroll.setFocusable(false);
        discordChatHistory = new LinearLayout(context);
        discordChatHistory.setOrientation(LinearLayout.VERTICAL);
        discordChatHistoryScroll.addView(discordChatHistory, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        discordChatContainer.addView(discordChatHistoryScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        discordQuickMain.addView(discordChatContainer, 2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        discordActionsContainer = new LinearLayout(context);
        discordActionsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams discordActionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        discordActionsParams.topMargin = dp(8);
        discordQuickMain.addView(discordActionsContainer, discordActionsParams);
        discordQuickFooter = new LinearLayout(context);
        discordQuickFooter.setOrientation(LinearLayout.HORIZONTAL);
        discordQuickFooter.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        discordQuickMain.addView(discordQuickFooter, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));
        renderDiscordCommunityFooter();
        discordButtons = new ArrayList<>();
        discordActions = new ArrayList<>();

        activeRegion = Region.MENU;
        discordIndex = 0;

        commandsManager = new CustomCommandsManager(context);

        setVisibility(GONE);
    }

    /** One USB device of the TV as the menu shows it. */
    public static final class UsbRow {
        final String deviceName;
        final String label;
        final String status;
        final boolean running;

        public UsbRow(String deviceName, String label, String status, boolean running) {
            this.deviceName = deviceName;
            this.label = label;
            this.status = status;
            this.running = running;
        }
    }

    /** The TV's USB devices for the USB page; it re-renders in place while open. */
    public void setUsbDevices(List<UsbRow> rows) {
        usbRows = new ArrayList<>(rows);
        if (!usbOpen) return;
        int index = menuIndex;
        renderMenuList();
        post(() -> setMenuIndex(Math.max(0, Math.min(index, menuActions.size() - 1))));
    }

    public void buildMenu() {
        overlayMode = OverlayMode.MENU;
        moreOpen = false;
        usbOpen = false;
        usbConsent = null;
        gyroOpen = false;
        streamOpen = false;
        autopilotOpen = false;
        autopilotVariantsOpen = false;
        playStationButtons = ControllerGlyphs.hasPlayStationController();
        renderDiscordCommunityFooter();
        clearFocus();

        discordButtons.clear();
        discordActions.clear();
        discordMuteButton = null;
        discordLeaveButton = null;
        discordRejoinButton = null;
        discordDockButton = null;
        discordSocialFriendsButton = null;
        renderedDiscordActionSignature = "";
        ensureDiscordActions();

        renderPanelHeader();
        renderMenuList();
        renderDiscordCard();
        applyOverlayMode();
    }

    /** The game and host this stream shows in the panel header. */
    public void setSessionInfo(String title, String host) {
        sessionTitle = title == null ? "" : title;
        sessionHost = host == null ? "" : host;
        if (getVisibility() == VISIBLE) renderPanelHeader();
    }

    private void renderPanelHeader() {
        if (prompt != null) {
            panelOverline.setText(prompt.overline);
            panelTitle.setText(prompt.title);
            panelTitle.setVisibility(VISIBLE);
            panelSubtitle.setText(prompt.subtitle);
        } else {
            panelOverline.setText(installationConfirmationAvailable
                    ? R.string.overlay_menu_installing : R.string.overlay_menu_in_game);
            panelTitle.setText(sessionTitle);
            panelTitle.setVisibility(sessionTitle.isEmpty() ? GONE : VISIBLE);
            panelSubtitle.setText(installationConfirmationAvailable
                    ? getContext().getString(R.string.overlay_menu_install_subtitle)
                    : sessionHost);
        }
        childRemainingChip.setText(childRemainingTime);
        childRemainingChip.setVisibility(childRemainingTime == null ? GONE : VISIBLE);
        renderPads();
    }

    /**
     * One pill per pad, as the console draws them: controller glyph and battery ink. A prompt
     * shows its button hints there instead (mockups O6–O8).
     */
    private void renderPads() {
        padDock.removeAllViews();
        padDock.setGravity((prompt != null ? Gravity.CENTER_HORIZONTAL : Gravity.END)
                | Gravity.CENTER_VERTICAL);
        if (prompt != null) {
            padDock.addView(promptHints());
            return;
        }
        for (ControllerBatteryInfo info : controllerBatteryInfo) {
            LinearLayout pad = new LinearLayout(getContext());
            pad.setOrientation(LinearLayout.HORIZONTAL);
            pad.setGravity(Gravity.CENTER_VERTICAL);
            pad.setPadding(dp(7), dp(2), dp(8), dp(2));
            pad.setMinimumHeight(dp(26));
            GradientDrawable background = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB51A222A, 0xC010151A});
            background.setCornerRadius(dp(18));
            background.setStroke(dp(1), 0x465F7482);
            pad.setBackground(background);
            ImageView glyph = new ImageView(getContext());
            glyph.setImageDrawable(new ControllerGlyphDrawable(info.controllerName));
            LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(30), dp(20));
            glyphParams.rightMargin = dp(5);
            pad.addView(glyph, glyphParams);
            TextView level = panelText(getContext(), 11,
                    ControllerGlyphDrawable.batteryColor(info.percentage, info.isCharging()), true);
            level.setText(info.percentage < 0 ? "?" : info.percentage + "%");
            pad.addView(level);
            pad.setContentDescription(info.controllerName + " " + level.getText());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.leftMargin = dp(6);
            padDock.addView(pad, params);
        }
    }

    private void renderMenuList() {
        menuList.removeAllViews();
        menuButtons.clear();
        menuActions.clear();
        menuRefreshers.clear();
        menuPairStart = -1;
        gyroDescription.setVisibility(GONE);
        if (prompt != null) {
            renderPrompt();
            return;
        }
        if (autopilotOpen && autopilotVariantsOpen) {
            renderAutopilotVariants();
            return;
        }
        if (autopilotOpen) {
            renderAutopilotList();
            return;
        }
        if (usbOpen) {
            renderUsbList();
            return;
        }
        if (gyroOpen && gyroSettings != null) {
            renderGyroList();
            return;
        }
        if (streamOpen && streamSettingsAvailable()) {
            renderStreamList();
            return;
        }
        if (moreOpen) {
            renderMoreList();
            return;
        }
        if (installationConfirmationAvailable) addInstallationCard();
        if (emergencyStream != null || emergencyReturn != null) addEmergencyCard();
        if (autopilotAdvice != null) addAdviceCard();

        LinearLayout pair = new LinearLayout(getContext());
        pair.setOrientation(LinearLayout.HORIZONTAL);
        menuList.addView(pair, wrapWidthParams());
        menuPairStart = menuButtons.size();
        addMenuButton(pair, R.drawable.ic_overlay_play,
                getContext().getString(installationConfirmationAvailable
                        ? R.string.overlay_menu_resume_installer : R.string.overlay_menu_resume),
                ACTION_CLOSE, PRIMARY_REST_FILL, OverlayMenuButton.TEXT);
        addMenuButton(pair, R.drawable.ic_overlay_monitor,
                getContext().getString(R.string.overlay_menu_home), ACTION_HOME,
                PRIMARY_REST_FILL, OverlayMenuButton.TEXT);
        for (int index = menuPairStart; index < menuButtons.size(); index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (index == menuPairStart) params.rightMargin = dp(BUTTON_SPACING_DP);
            menuButtons.get(index).setLayoutParams(params);
            menuButtons.get(index).setMinimumHeight(dp(30));
        }

        addMenuSection(R.string.overlay_section_controls);
        // The installer is driven with the mouse, so that tool leads while installing.
        if (installationConfirmationAvailable) addMouseRow();
        addShortcutRow(R.drawable.ic_overlay_keyboard_toggle, R.string.overlay_menu_keyboard,
                ACTION_SHOW_KEYBOARD, ControllerGlyphs.Button.NORTH);
        if (!installationConfirmationAvailable) addMouseRow();
        if (gyroSettings != null) addGyroRow();
        addMenuButton(menuList, R.drawable.ic_overlay_usb,
                getContext().getString(R.string.usb_forwarding_menu), ACTION_USB_DEVICES,
                ROW_REST_FILL, OverlayMenuButton.TEXT);
        addMenuButton(menuList, R.drawable.ic_overlay_settings,
                getContext().getString(R.string.overlay_menu_more), ACTION_MORE, ROW_REST_FILL,
                OverlayMenuButton.TEXT).setValue(ControllerGlyphs.chord(getContext(),
                "%1$s  \u203A", playStationButtons, ControllerGlyphs.Button.RIGHT_TRIGGER));

        // Captures are their own group, apart from what ends or pauses the session.
        if (captures != Captures.HIDDEN) {
            addMenuSection(R.string.overlay_section_captures);
            addCaptureRows();
        }

        addMenuSection(R.string.overlay_section_session);
        if (endGameAvailable) {
            // The game the user may end is the game it may freeze (a verified provider game).
            addMenuButton(menuList, R.drawable.ic_overlay_sleep,
                    getContext().getString(R.string.overlay_menu_freeze_game),
                    ACTION_FREEZE_GAME, ROW_REST_FILL, OverlayMenuButton.TEXT);
            addMenuButton(menuList, R.drawable.ic_overlay_close,
                    getContext().getString(R.string.overlay_menu_end_game), ACTION_END_GAME,
                    ROW_REST_FILL, OverlayMenuButton.TEXT);
        }
        if (suspendSessionAvailable) {
            addMenuButton(menuList, R.drawable.ic_overlay_restore,
                    getContext().getString(R.string.overlay_menu_suspend_session),
                    ACTION_SUSPEND_SESSION, ROW_REST_FILL, OverlayMenuButton.TEXT);
        }
        addMenuButton(menuList, R.drawable.ic_overlay_power,
                getContext().getString(R.string.console_close_session), ACTION_QUIT,
                ROW_REST_FILL, DESTRUCTIVE_TEXT);
    }

    private void addCaptureRows() {
        Context context = getContext();
        switch (captures) {
            case HOST_PLUS_REQUIRED:
                addDisabledRow(R.drawable.ic_overlay_screenshot,
                        context.getString(R.string.overlay_menu_captures),
                        context.getString(R.string.host_plus_required));
                break;
            case BUFFER_OFF:
            case AVAILABLE:
                addMenuButton(menuList, R.drawable.ic_overlay_screenshot,
                        context.getString(R.string.overlay_menu_screenshot), ACTION_SCREENSHOT,
                        ROW_REST_FILL, OverlayMenuButton.TEXT);
                if (captures == Captures.AVAILABLE) {
                    addMenuButton(menuList, R.drawable.ic_overlay_record,
                            context.getString(R.string.overlay_menu_save_clip), ACTION_SAVE_CLIP,
                            ROW_REST_FILL, OverlayMenuButton.TEXT);
                } else {
                    addDisabledRow(R.drawable.ic_overlay_record,
                            context.getString(R.string.overlay_menu_save_clip),
                            context.getString(R.string.overlay_menu_clip_buffer_off));
                }
                break;
            default:
        }
    }

    /** A row shown off with its reason: no action, never focused (D-pad steps over it). */
    private void addDisabledRow(int iconResId, String label, String reason) {
        OverlayMenuButton row = OverlayMenuButton.create(getContext(), iconResId, label);
        row.panelRow(ROW_REST_FILL, OverlayMenuButton.TEXT);
        row.setDetail(reason);
        row.disabledRow();
        menuList.addView(row, wrapWidthParams());
    }

    /** The Playnite install-confirmation stream: what to do on the desktop, then one button. */
    private void addInstallationCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable background = pill(0x1F73D7FF, 0x8073D7FF);
        background.setCornerRadius(dp(14));
        card.setBackground(background);
        TextView steps = panelText(getContext(), 11, OverlayMenuButton.TEXT_SECONDARY, false);
        steps.setText(R.string.overlay_menu_install_steps);
        card.addView(steps);
        addMenuButton(card, R.drawable.ic_overlay_play,
                getContext().getString(R.string.playnite_install_confirmation_done),
                ACTION_INSTALLATION_CONFIRMED, PRIMARY_REST_FILL, OverlayMenuButton.TEXT)
                .setLayoutParams(topMargin(dp(6)));
        TextView footnote = panelText(getContext(), 10, OverlayMenuButton.TEXT_SECONDARY, false);
        footnote.setText(R.string.overlay_menu_install_footnote);
        card.addView(footnote, topMargin(dp(4)));
        menuList.addView(card, bottomMargin(dp(6)));
    }

    /**
     * The link card: on a poor link the emergency mode it offers (what the link carries, the
     * change); on an emergency stream the way back. Its button leads the menu.
     */
    private void addEmergencyCard() {
        Context context = getContext();
        if (emergencyStream != null) {
            String change = s(R.string.overlay_poor_link_change,
                    streamText(context, currentStream, true),
                    streamText(context, emergencyStream, true));
            addCard(WARNING_TEXT, s(R.string.overlay_poor_link_title),
                    emergencyObservedKbps <= 0 ? change
                            : s(R.string.overlay_poor_link_carried,
                                    megabits(emergencyObservedKbps),
                                    megabits(currentStream.bitrateKbps)) + '\n' + change,
                    R.drawable.ic_overlay_perf, s(R.string.overlay_poor_link_start),
                    ACTION_EMERGENCY_STREAM, R.string.overlay_poor_link_footnote);
        } else {
            addCard(ACCENT, s(R.string.overlay_emergency_title,
                            streamText(context, currentStream, true)),
                    s(R.string.overlay_emergency_normal,
                            streamText(context, emergencyReturn, true)),
                    R.drawable.ic_overlay_restore, s(R.string.overlay_emergency_leave),
                    ACTION_LEAVE_EMERGENCY_STREAM, R.string.overlay_emergency_footnote);
        }
    }

    /** The history's proposal for this game, atop the menu and the Autopilot page. */
    private void addAdviceCard() {
        addCard(ACCENT, s(R.string.overlay_autopilot_advice_title), autopilotAdvice,
                R.drawable.ic_overlay_restore, s(R.string.overlay_autopilot_advice_apply),
                ACTION_AUTOPILOT_ADVICE, R.string.overlay_emergency_footnote);
    }

    /** A card in {@code ink}: title, details, one button and a footnote under it. */
    private void addCard(int ink, String title, String details, int icon, String button,
                         int action, int footnote) {
        Context context = getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable background = pill(ink & 0x1FFFFFFF, ink & 0x80FFFFFF);
        background.setCornerRadius(dp(14));
        card.setBackground(background);
        TextView titleView = panelText(context, 13, ink, true);
        titleView.setText(title);
        card.addView(titleView);
        TextView detailsView = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
        detailsView.setText(details);
        card.addView(detailsView, topMargin(dp(2)));
        addMenuButton(card, icon, button, action, PRIMARY_REST_FILL, OverlayMenuButton.TEXT)
                .setLayoutParams(topMargin(dp(6)));
        TextView footnoteView = panelText(context, 10, OverlayMenuButton.TEXT_SECONDARY, false);
        footnoteView.setText(footnote);
        card.addView(footnoteView, topMargin(dp(4)));
        menuList.addView(card, bottomMargin(dp(6)));
    }

    /**
     * More › Streaming Autopilot (mockup O2): the history's proposal, a test of the settings
     * running now, a test of another variant and the gyro try, each with what it costs.
     */
    private void renderAutopilotList() {
        Context context = getContext();
        TextView title = panelText(context, 15, OverlayMenuButton.TEXT, true);
        title.setText("\u2039 " + s(R.string.overlay_autopilot));
        menuList.addView(title, bottomMargin(dp(5)));
        if (autopilotTest == AutopilotTest.UNAVAILABLE) {
            TextView hint = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
            hint.setText(R.string.overlay_autopilot_unavailable);
            menuList.addView(hint, bottomMargin(dp(6)));
        }
        if (autopilotAdvice != null) addAdviceCard();
        boolean running = autopilotTest == AutopilotTest.RUNNING;
        if (autopilotTest != AutopilotTest.UNAVAILABLE) {
            addMenuButton(menuList, running ? R.drawable.ic_overlay_close
                            : R.drawable.ic_overlay_perf,
                    s(running ? R.string.overlay_autopilot_test_stop
                            : R.string.overlay_autopilot_test_current), ACTION_AUTOPILOT_TEST,
                    ROW_REST_FILL, OverlayMenuButton.TEXT)
                    .setDetail(s(running ? R.string.overlay_autopilot_test_stop_detail
                            : R.string.overlay_autopilot_test_current_detail));
        }
        if (autopilotTest == AutopilotTest.AVAILABLE && !autopilotVariants.isEmpty()) {
            addMenuButton(menuList, R.drawable.ic_overlay_list,
                    s(R.string.overlay_autopilot_variants), ACTION_AUTOPILOT_VARIANTS,
                    ROW_REST_FILL, OverlayMenuButton.TEXT)
                    .setDetail(getResources().getQuantityString(
                            R.plurals.overlay_autopilot_variants_detail,
                            autopilotVariants.size(), autopilotVariants.size()));
        }
        if (autopilotGyroTrial && !running) {
            addMenuButton(menuList, R.drawable.ic_overlay_gyro,
                    s(R.string.overlay_autopilot_gyro_trial), ACTION_AUTOPILOT_GYRO,
                    ROW_REST_FILL, OverlayMenuButton.TEXT)
                    .setDetail(s(R.string.overlay_autopilot_gyro_trial_detail));
        }
        if (currentStream != null) {
            TextView now = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
            now.setText(s(R.string.overlay_autopilot_current, streamSummary(currentStream)));
            menuList.addView(now, topMargin(dp(8)));
        }
    }

    /**
     * The last analysis' variants (mockup O3): choosing one reconnects with it and tests it;
     * the focused one's trade is described under the list.
     */
    private void renderAutopilotVariants() {
        Context context = getContext();
        TextView title = panelText(context, 15, OverlayMenuButton.TEXT, true);
        title.setText("\u2039 " + s(R.string.overlay_autopilot_variants_title));
        menuList.addView(title, bottomMargin(dp(5)));
        TextView hint = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
        hint.setText(R.string.overlay_autopilot_variants_hint);
        menuList.addView(hint, bottomMargin(dp(6)));
        gyroDescription.setText("");
        gyroDescription.setVisibility(VISIBLE);
        for (int index = 0; index < autopilotVariants.size(); index++) {
            AutopilotVariant variant = autopilotVariants.get(index);
            SpannableStringBuilder label = new SpannableStringBuilder(variant.name);
            if (variant.recommended) badge(label, s(R.string.overlay_autopilot_badge_recommended));
            if (variant.current) badge(label, s(R.string.overlay_autopilot_badge_current));
            OverlayMenuButton row = addMenuButton(menuList, variant.icon, "",
                    ACTION_AUTOPILOT_VARIANT_BASE + index, ROW_REST_FILL, OverlayMenuButton.TEXT);
            row.setLabel(label);
            row.setDetail(variant.settings);
            describeOnFocus(row, () -> variant.description);
        }
    }

    /** A small tracked tag after a row label (REKOMENDACJA, OBECNY). */
    private static void badge(SpannableStringBuilder label, String text) {
        label.append("   ");
        int start = label.length();
        label.append(text.toUpperCase(java.util.Locale.ROOT));
        label.setSpan(new RelativeSizeSpan(0.7f), start, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new StyleSpan(Typeface.BOLD), start, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(ACCENT), start, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * The prompt in place of the menu (mockups O6–O8): the verdict card in its tone, the metric
     * tiles three to a row (one over its limit outlined red), "was → proposal", the question,
     * then one row per option with what it does under it.
     */
    private void renderPrompt() {
        Context context = getContext();
        int ink = prompt.tone == Tone.GOOD ? GOOD : prompt.tone == Tone.WARNING ? WARNING_TEXT
                : ACCENT;
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(9), dp(11), dp(9));
        GradientDrawable cardBackground = pill(ink & 0x1AFFFFFF, ink & 0x66FFFFFF);
        cardBackground.setCornerRadius(dp(10));
        card.setBackground(cardBackground);
        TextView cardTitle = panelText(context, 13, ink, true);
        cardTitle.setText(prompt.cardTitle);
        Drawable icon = context.getDrawable(prompt.tone == Tone.GOOD ? R.drawable.ic_overlay_check
                : prompt.tone == Tone.WARNING ? R.drawable.ic_overlay_warning
                : R.drawable.ic_overlay_gyro).mutate();
        icon.setTint(ink);
        icon.setBounds(0, 0, dp(16), dp(16));
        cardTitle.setCompoundDrawablesRelative(icon, null, null, null);
        cardTitle.setCompoundDrawablePadding(dp(6));
        card.addView(cardTitle);
        if (prompt.cardText != null && !prompt.cardText.isEmpty()) {
            TextView cardText = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
            cardText.setText(prompt.cardText);
            cardText.setLineSpacing(0f, 1.15f);
            card.addView(cardText, topMargin(dp(3)));
        }
        menuList.addView(card, bottomMargin(dp(7)));

        for (int start = 0; start < prompt.tiles.size(); start += 3) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int index = start; index < start + 3; index++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (index > start) params.leftMargin = dp(5);
                row.addView(index < prompt.tiles.size() ? tile(prompt.tiles.get(index))
                        : new View(context), params);
            }
            menuList.addView(row, bottomMargin(dp(5)));
        }

        if (prompt.changeFrom != null && prompt.changeTo != null) {
            SpannableStringBuilder change = new SpannableStringBuilder();
            change.append(prompt.changeFrom, new StrikethroughSpan(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            change.setSpan(new ForegroundColorSpan(OverlayMenuButton.TEXT_SECONDARY), 0,
                    change.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            change.append("  →  ", new ForegroundColorSpan(ACCENT),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            change.append(prompt.changeTo, new StyleSpan(Typeface.BOLD),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView changeView = panelText(context, 12, OverlayMenuButton.TEXT, false);
            changeView.setText(change);
            changeView.setPadding(dp(2), dp(2), 0, 0);
            menuList.addView(changeView, bottomMargin(dp(7)));
        }
        if (prompt.question != null) {
            TextView question = panelText(context, 14, OverlayMenuButton.TEXT, true);
            question.setText(prompt.question);
            question.setPadding(dp(2), dp(3), 0, 0);
            menuList.addView(question, bottomMargin(dp(7)));
        }
        for (int index = 0; index < prompt.options.size(); index++) {
            PromptOption option = prompt.options.get(index);
            addMenuButton(menuList, option.icon, option.label, ACTION_PROMPT_BASE + index,
                    ROW_REST_FILL, OverlayMenuButton.TEXT).setDetail(option.detail);
        }
    }

    /** A result's metric: status dot and label, then the value and its unit or limit. */
    private View tile(Tile tile) {
        Context context = getContext();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable background = pill(0x0FFFFFFF, tile.bad ? 0x8CFF6B6B : 0);
        background.setCornerRadius(dp(8));
        box.setBackground(background);
        TextView label = panelText(context, 9, OverlayMenuButton.TEXT_SECONDARY, false);
        label.setText(tile.label);
        label.setSingleLine(true);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(tile.bad ? DESTRUCTIVE_TEXT : GOOD);
        dot.setBounds(0, 0, dp(6), dp(6));
        label.setCompoundDrawablesRelative(dot, null, null, null);
        label.setCompoundDrawablePadding(dp(4));
        box.addView(label);
        SpannableStringBuilder value = new SpannableStringBuilder(tile.value);
        if (tile.unit != null && !tile.unit.isEmpty()) {
            int start = value.length();
            value.append(' ').append(tile.unit);
            value.setSpan(new RelativeSizeSpan(0.6f), start, value.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(new ForegroundColorSpan(OverlayMenuButton.TEXT_SECONDARY), start,
                    value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView number = panelText(context, 15, OverlayMenuButton.TEXT, true);
        number.setText(value);
        box.addView(number, topMargin(dp(2)));
        return box;
    }

    /** "▲▼ Select   A OK   B {back}" in the pad's glyphs: a pill beside the panel. */
    private View promptHints() {
        Context context = getContext();
        boolean playStation = ControllerGlyphs.hasPlayStationController();
        SpannableStringBuilder text = new SpannableStringBuilder();
        ControllerGlyphs.append(context, text, playStation, ControllerGlyphs.Button.DPAD_UP);
        ControllerGlyphs.append(context, text, playStation, ControllerGlyphs.Button.DPAD_DOWN);
        text.append("  ").append(s(R.string.overlay_hint_select)).append("      ");
        ControllerGlyphs.append(context, text, playStation, ControllerGlyphs.Button.CONFIRM);
        text.append("  ").append(s(R.string.overlay_hint_ok)).append("      ");
        ControllerGlyphs.append(context, text, playStation, ControllerGlyphs.Button.CANCEL);
        text.append("  ").append(prompt.back);
        TextView hints = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
        hints.setText(text);
        hints.setSingleLine(true);
        hints.setPadding(dp(14), dp(6), dp(14), dp(6));
        hints.setBackground(pill(0xC70A0D14, 0x1FFFFFFF));
        return hints;
    }

    private void openAutopilotVariants() {
        autopilotVariantsOpen = true;
        renderMenuList();
        post(() -> setMenuIndex(0));
    }

    private void closeAutopilotVariants() {
        autopilotVariantsOpen = false;
        renderMenuList();
        post(() -> setMenuIndex(menuActions.indexOf(ACTION_AUTOPILOT_VARIANTS)));
    }

    private void openAutopilot() {
        autopilotOpen = true;
        renderMenuList();
        post(() -> setMenuIndex(0));
    }

    private void closeAutopilot() {
        autopilotOpen = false;
        autopilotVariantsOpen = false;
        renderMenuList();
        post(() -> setMenuIndex(menuActions.indexOf(ACTION_AUTOPILOT)));
    }

    private void finishPrompt(int option) {
        prompt = null;
        closeMenu();
        if (actionListener != null) actionListener.onPromptChoice(option);
    }

    private static int megabits(int kbps) {
        return Math.max(1, Math.round(kbps / 1000f));
    }

    /**
     * The less frequent tools behind "More" (R2): Guide, stats, stream quality, audio haptics
     * and the custom key commands. Back returns to the menu.
     */
    private void renderMoreList() {
        TextView title = panelText(getContext(), 15, OverlayMenuButton.TEXT, true);
        title.setText("\u2039 " + getContext().getString(R.string.overlay_menu_more));
        menuList.addView(title, bottomMargin(dp(5)));
        addShortcutRow(R.drawable.ic_overlay_guide, R.string.overlay_menu_guide,
                ACTION_SEND_GUIDE, ControllerGlyphs.Button.MENU);
        addShortcutRow(R.drawable.ic_overlay_perf, R.string.overlay_menu_toggle_stats,
                ACTION_TOGGLE_STATS, ControllerGlyphs.Button.LEFT_TRIGGER);
        if (streamSettingsAvailable()) {
            addMenuButton(menuList, R.drawable.ic_overlay_perf,
                    getContext().getString(R.string.overlay_menu_quality),
                    ACTION_STREAM_SETTINGS, ROW_REST_FILL, OverlayMenuButton.TEXT)
                    .setValue(streamSummary(currentStream));
        }
        addMenuButton(menuList, R.drawable.ic_overlay_perf, s(R.string.overlay_autopilot),
                ACTION_AUTOPILOT, ROW_REST_FILL, OverlayMenuButton.TEXT)
                .setValue(autopilotAdvice == null ? "\u203A"
                        : s(R.string.overlay_autopilot_proposal) + "  \u203A");
        // Short names: Settings' long ones would leave the row no room for its title.
        String[] hapticsNames = getResources().getStringArray(R.array.audio_haptics_short_names);
        String[] hapticsValues = getResources().getStringArray(R.array.audio_haptics_values);
        addValueOption(R.drawable.ic_overlay_haptics, R.string.console_stream_audio_haptics,
                ACTION_AUDIO_HAPTICS, () -> {
            int index = Arrays.asList(hapticsValues).indexOf(audioHapticsMode);
            return hapticsNames[Math.max(0, index)];
        });
        addValueOption(R.drawable.ic_overlay_haptics, R.string.console_stream_audio_haptics_strength,
                ACTION_AUDIO_HAPTICS_STRENGTH, () -> audioHapticsStrength + "%");
        // Reports go to the owner's Discord webhook: tester builds (-Ptesting) only.
        if (BuildConfig.TESTING) {
            addMenuButton(menuList, R.drawable.ic_overlay_star,
                    getContext().getString(R.string.problem_report_mark), ACTION_MARK_MOMENT,
                    ROW_REST_FILL, OverlayMenuButton.TEXT);
            addMenuButton(menuList, R.drawable.ic_overlay_record,
                    getContext().getString(R.string.problem_report_title), ACTION_REPORT_PROBLEM,
                    ROW_REST_FILL, OverlayMenuButton.TEXT);
        }
        List<CustomCommand> commands = commandsManager.getCommands();
        if (commands.isEmpty()) return;
        addMenuSection(R.string.overlay_menu_shortcuts);
        for (int index = 0; index < commands.size(); index++) {
            CustomCommand command = commands.get(index);
            addMenuButton(menuList, command.getIconResId(), command.getName(),
                    ACTION_CUSTOM_BASE + index, ROW_REST_FILL, OverlayMenuButton.TEXT)
                    .setValue(command.getKeyCombination().toDisplayString());
        }
    }

    /**
     * The TV's USB devices as their own list inside the panel: Select shares a device (after
     * the consent shown in place) or stops it; Back returns to the menu.
     */
    private void renderUsbList() {
        Context context = getContext();
        addMenuSection(R.string.usb_forwarding_title);
        TextView hint = panelText(context, 11, OverlayMenuButton.TEXT_SECONDARY, false);
        UsbRow consent = null;
        for (UsbRow row : usbRows) if (row.deviceName.equals(usbConsent)) consent = row;
        if (consent != null) {
            hint.setText(context.getString(R.string.usb_forwarding_consent_title, consent.label)
                    + "\n" + context.getString(R.string.usb_forwarding_consent_message));
            menuList.addView(hint, bottomMargin(dp(6)));
            addMenuButton(menuList, R.drawable.ic_overlay_usb,
                    context.getString(R.string.usb_forwarding_share), ACTION_USB_SHARE,
                    ROW_REST_FILL, OverlayMenuButton.TEXT);
            addMenuButton(menuList, R.drawable.ic_overlay_close,
                    context.getString(R.string.usb_forwarding_cancel), ACTION_USB_CANCEL,
                    ROW_REST_FILL, OverlayMenuButton.TEXT);
            return;
        }
        usbConsent = null;
        hint.setText(usbRows.isEmpty() ? R.string.usb_forwarding_empty
                : R.string.usb_forwarding_panel_details);
        menuList.addView(hint, bottomMargin(dp(6)));
        for (int index = 0; index < usbRows.size(); index++) {
            UsbRow row = usbRows.get(index);
            addMenuButton(menuList, R.drawable.ic_overlay_usb, row.label, ACTION_USB_BASE + index,
                    ROW_REST_FILL, OverlayMenuButton.TEXT).setValue(row.status);
        }
    }

    private void openUsb() {
        usbOpen = true;
        usbConsent = null;
        if (actionListener != null) actionListener.onUsbDevices();
        renderMenuList();
        post(() -> setMenuIndex(0));
    }

    private void closeUsb() {
        usbOpen = false;
        usbConsent = null;
        renderMenuList();
        post(() -> setMenuIndex(menuActions.indexOf(ACTION_USB_DEVICES)));
    }

    private void showUsbList(String consentDevice) {
        usbConsent = consentDevice;
        renderMenuList();
        post(() -> setMenuIndex(0));
    }

    private void openMore() {
        moreOpen = true;
        renderMenuList();
        post(() -> setMenuIndex(0));
    }

    private void closeMore() {
        moreOpen = false;
        renderMenuList();
        post(() -> setMenuIndex(menuActions.indexOf(ACTION_MORE)));
    }

    /** The "Gyro" row of Controls: the current mode, Select opens the gyro list. */
    private void addGyroRow() {
        Context context = getContext();
        addMenuButton(menuList, R.drawable.ic_overlay_gamepad,
                context.getString(R.string.overlay_section_gyro), ACTION_GYRO, ROW_REST_FILL,
                OverlayMenuButton.TEXT).setValue(!gyroSettings.aim
                ? context.getString(R.string.console_stream_off)
                : context.getString(gyroSettings.output == GyroAim.Output.MOUSE
                        ? R.string.console_gyro_output_mouse : R.string.console_gyro_output_stick));
    }

    /**
     * Every gyro option as its own list inside the panel, set while playing and saved for the
     * game; Back returns to the menu. Left/Right (and Select) change the focused value.
     */
    private void renderGyroList() {
        addMenuSection(R.string.overlay_section_gyro);
        TextView hint = panelText(getContext(), 11, OverlayMenuButton.TEXT_SECONDARY, false);
        hint.setText(R.string.overlay_gyro_hint);
        menuList.addView(hint, bottomMargin(dp(6)));
        gyroDescription.setText("");
        gyroDescription.setVisibility(VISIBLE);
        for (int action : gyroOptions(gyroSettings)) {
            describeOnFocus(addValueOption(gyroLabel(action, gyroSettings), action,
                    () -> gyroValue(action, gyroSettings)),
                    () -> s(gyroDescription(action, gyroSettings)));
        }
        describeOnFocus(addMenuButton(menuList, 0, s(R.string.overlay_gyro_calibrate),
                ACTION_GYRO_CALIBRATE, ROW_REST_FILL, OverlayMenuButton.TEXT),
                () -> s(R.string.overlay_gyro_desc_calibrate));
    }

    /** The focused row's explanation goes to {@link #gyroDescription}; its own focus handling stays. */
    private void describeOnFocus(OverlayMenuButton button, Supplier<String> description) {
        OnFocusChangeListener base = button.getOnFocusChangeListener();
        button.setOnFocusChangeListener((v, hasFocus) -> {
            base.onFocusChange(v, hasFocus);
            if (hasFocus) gyroDescription.setText(description.get());
        });
    }

    /** The gyro rows {@code g} shows; a change that keeps them only rewrites the values. */
    static List<Integer> gyroOptions(GyroAim.Settings g) {
        List<Integer> options = new ArrayList<>();
        options.add(ACTION_GYRO_AIM);
        if (!g.aim) return options;
        options.add(ACTION_GYRO_OUTPUT);
        options.add(ACTION_GYRO_ACTIVATION);
        if (g.activation != GyroAim.Activation.ALWAYS) options.add(ACTION_GYRO_BUTTON);
        if (g.activation == GyroAim.Activation.HOLD_OFF
                || g.activation == GyroAim.Activation.TOGGLE) {
            options.add(ACTION_GYRO_BUTTON_ONLY);
        }
        Collections.addAll(options, ACTION_GYRO_STICK_PAUSE, ACTION_GYRO_GEARING, ACTION_GYRO_SENS_X,
                ACTION_GYRO_SENS_Y, ACTION_GYRO_ACCELERATION, ACTION_GYRO_INVERT_X,
                ACTION_GYRO_INVERT_Y, ACTION_GYRO_AXIS);
        if (g.output == GyroAim.Output.STICK) {
            options.add(ACTION_GYRO_DEADZONE);
            options.add(ACTION_GYRO_CURVE);
        }
        return options;
    }

    private static int gyroLabel(int action, GyroAim.Settings g) {
        switch (action) {
            case ACTION_GYRO_GEARING: return g.output == GyroAim.Output.STICK
                    ? R.string.console_gyro_stick_speed : R.string.console_gyro_mouse_360;
            case ACTION_GYRO_AIM: return R.string.overlay_gyro_aim;
            case ACTION_GYRO_OUTPUT: return R.string.console_gyro_output;
            case ACTION_GYRO_ACTIVATION: return R.string.console_gyro_activation;
            case ACTION_GYRO_BUTTON: return R.string.console_gyro_button;
            case ACTION_GYRO_BUTTON_ONLY: return R.string.console_gyro_button_only;
            case ACTION_GYRO_STICK_PAUSE: return R.string.console_gyro_stick_pause;
            case ACTION_GYRO_SENS_X: return R.string.console_gyro_sens_x;
            case ACTION_GYRO_SENS_Y: return R.string.console_gyro_sens_y;
            case ACTION_GYRO_ACCELERATION: return R.string.console_gyro_acceleration;
            case ACTION_GYRO_INVERT_X: return R.string.console_gyro_invert_x;
            case ACTION_GYRO_INVERT_Y: return R.string.console_gyro_invert_y;
            case ACTION_GYRO_AXIS: return R.string.console_gyro_axis;
            case ACTION_GYRO_DEADZONE: return R.string.console_gyro_deadzone;
            default: return R.string.console_gyro_curve;
        }
    }

    private static int gyroDescription(int action, GyroAim.Settings g) {
        switch (action) {
            case ACTION_GYRO_AIM: return R.string.overlay_gyro_desc_aim;
            case ACTION_GYRO_OUTPUT: return R.string.console_gyro_desc_output;
            case ACTION_GYRO_ACTIVATION: return R.string.console_gyro_desc_activation;
            case ACTION_GYRO_BUTTON: return R.string.console_gyro_desc_button;
            case ACTION_GYRO_BUTTON_ONLY: return R.string.console_gyro_desc_button_only;
            case ACTION_GYRO_STICK_PAUSE: return R.string.console_gyro_desc_stick_pause;
            case ACTION_GYRO_GEARING: return g.output == GyroAim.Output.STICK
                    ? R.string.console_gyro_desc_stick_speed : R.string.console_gyro_desc_mouse_360;
            case ACTION_GYRO_SENS_X: return R.string.console_gyro_desc_sens_x;
            case ACTION_GYRO_SENS_Y: return R.string.console_gyro_desc_sens_y;
            case ACTION_GYRO_ACCELERATION: return R.string.console_gyro_desc_acceleration;
            case ACTION_GYRO_INVERT_X:
            case ACTION_GYRO_INVERT_Y: return R.string.console_gyro_desc_invert;
            case ACTION_GYRO_AXIS: return R.string.console_gyro_desc_axis;
            case ACTION_GYRO_DEADZONE: return R.string.console_gyro_desc_deadzone;
            default: return R.string.console_gyro_desc_curve;
        }
    }

    private String gyroValue(int action, GyroAim.Settings g) {
        switch (action) {
            case ACTION_GYRO_AIM: return onOff(g.aim);
            case ACTION_GYRO_OUTPUT: return s(g.output == GyroAim.Output.MOUSE
                    ? R.string.console_gyro_output_mouse : R.string.console_gyro_output_stick);
            case ACTION_GYRO_ACTIVATION: return s(GYRO_ACTIVATION_LABELS[g.activation.ordinal()]);
            case ACTION_GYRO_BUTTON: return buttonLabel(g.button);
            case ACTION_GYRO_BUTTON_ONLY: return onOff(g.buttonGyroOnly);
            case ACTION_GYRO_STICK_PAUSE: return onOff(g.stickPausesGyro);
            case ACTION_GYRO_SENS_X: return times(g.sensX);
            case ACTION_GYRO_SENS_Y: return times(g.sensY);
            case ACTION_GYRO_ACCELERATION:
                return s(GYRO_ACCELERATION_LABELS[g.acceleration.ordinal()]);
            case ACTION_GYRO_INVERT_X: return onOff(g.invertX);
            case ACTION_GYRO_INVERT_Y: return onOff(g.invertY);
            case ACTION_GYRO_AXIS: return s(GYRO_AXIS_LABELS[g.axis.ordinal()]);
            case ACTION_GYRO_DEADZONE: return Math.round(g.stickDeadzone * 100) + "%";
            case ACTION_GYRO_GEARING: return g.output == GyroAim.Output.STICK
                    ? s(R.string.console_gyro_deg_s, g.stickTurnSpeed)
                    : String.valueOf(g.mouseCountsPer360);
            default: return String.format(java.util.Locale.ROOT, "%.1f", g.stickCurve);
        }
    }

    /** Values a held Left/Right steps through; toggles and cycles take one step per press. */
    static boolean repeatsHorizontally(int action) {
        return action == ACTION_GYRO_SENS_X || action == ACTION_GYRO_SENS_Y
                || action == ACTION_GYRO_DEADZONE || action == ACTION_GYRO_CURVE
                || action == ACTION_GYRO_GEARING
                || action == ACTION_STREAM_BITRATE || action == ACTION_AUDIO_HAPTICS_STRENGTH;
    }

    private static final int[] GYRO_ACTIVATION_LABELS = {R.string.console_gyro_activation_always,
            R.string.console_gyro_activation_hold_on, R.string.console_gyro_activation_hold_off,
            R.string.console_gyro_activation_toggle};
    private static final int[] GYRO_AXIS_LABELS = {R.string.console_gyro_axis_player,
            R.string.console_gyro_axis_yaw, R.string.console_gyro_axis_roll};
    private static final int[] GYRO_ACCELERATION_LABELS = {
            R.string.console_gyro_acceleration_none, R.string.console_gyro_acceleration_low,
            R.string.console_gyro_acceleration_standard, R.string.console_gyro_acceleration_high};

    /** A "◄ value ►" row; {@link #refreshMenuValues} rewrites its value in place. */
    private OverlayMenuButton addValueOption(int label, int action, Supplier<String> value) {
        return addValueOption(0, label, action, value);
    }

    private OverlayMenuButton addValueOption(int icon, int label, int action,
                                             Supplier<String> value) {
        OverlayMenuButton button = addMenuButton(menuList, icon, s(label), action, ROW_REST_FILL,
                OverlayMenuButton.TEXT);
        Runnable refresh = () -> button.setValue("◄  " + value.get() + "  ►");
        refresh.run();
        menuRefreshers.add(refresh);
        return button;
    }

    /** A value change that keeps the rows: no rebuild, focus stays where it is. */
    private void refreshMenuValues() {
        for (Runnable refresh : menuRefreshers) refresh.run();
    }

    private String onOff(boolean on) {
        return s(on ? R.string.console_stream_on : R.string.console_stream_off);
    }

    private String buttonLabel(GyroAim.Button button) {
        return button == GyroAim.Button.TOUCHPAD ? s(R.string.console_gyro_button_touchpad)
                : button.name();
    }

    private static String times(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f×", value);
    }

    private String s(int resource) {
        return getContext().getString(resource);
    }

    private void openGyro() {
        gyroOpen = true;
        renderMenuList();
        post(() -> setMenuIndex(0));
    }

    private void closeGyro() {
        gyroOpen = false;
        renderMenuList();
        post(() -> setMenuIndex(menuActions.indexOf(ACTION_GYRO)));
    }

    private static boolean isGyroOption(int action) {
        return action >= ACTION_GYRO_AIM && action <= ACTION_GYRO_GEARING;
    }

    /** {@code step} is +1 (Select, Right) or -1 (Left); the list shows {@code next} at once. */
    private void changeGyro(int action, int step) {
        GyroAim.Settings previous = gyroSettings;
        GyroAim.Settings next = nextGyro(previous, action, step, gyroButtons);
        if (next == previous) return;
        gyroSettings = next;
        if (gyroOptions(next).equals(gyroOptions(previous))) {
            refreshMenuValues();
        } else {
            int focused = menuActions.get(menuIndex);
            renderMenuList();
            int index = menuActions.indexOf(focused);
            setMenuIndex(index >= 0 ? index : Math.min(menuIndex, menuButtons.size() - 1));
        }
        if (actionListener != null) actionListener.onGyroAimSettings(next);
    }

    static GyroAim.Settings nextGyro(GyroAim.Settings s, int action, int step,
                                     List<GyroAim.Button> buttons) {
        GyroAim.Builder b = s.toBuilder();
        switch (action) {
            case ACTION_GYRO_AIM: b.aim(!s.aim); break;
            case ACTION_GYRO_OUTPUT:
                b.output(s.output == GyroAim.Output.MOUSE
                        ? GyroAim.Output.STICK : GyroAim.Output.MOUSE);
                break;
            case ACTION_GYRO_ACTIVATION:
                b.activation(cycle(GyroAim.Activation.values(), s.activation, step));
                break;
            case ACTION_GYRO_BUTTON: {
                if (buttons.isEmpty()) return s;
                int index = Math.max(0, buttons.indexOf(s.button));
                b.button(buttons.get(Math.floorMod(index + step, buttons.size())));
                break;
            }
            case ACTION_GYRO_BUTTON_ONLY: b.buttonGyroOnly(!s.buttonGyroOnly); break;
            case ACTION_GYRO_STICK_PAUSE: b.stickPausesGyro(!s.stickPausesGyro); break;
            case ACTION_GYRO_SENS_X: b.sensX(sensitivityStep(s.sensX, step)); break;
            case ACTION_GYRO_SENS_Y: b.sensY(sensitivityStep(s.sensY, step)); break;
            case ACTION_GYRO_ACCELERATION:
                b.acceleration(cycle(GyroAim.Acceleration.values(), s.acceleration, step));
                break;
            case ACTION_GYRO_INVERT_X: b.invertX(!s.invertX); break;
            case ACTION_GYRO_INVERT_Y: b.invertY(!s.invertY); break;
            case ACTION_GYRO_AXIS: b.axis(cycle(GyroAim.Axis.values(), s.axis, step)); break;
            case ACTION_GYRO_DEADZONE: {
                int percent = Math.max(0, Math.min(40, Math.round(s.stickDeadzone * 100) + step));
                b.stickDeadzone(percent / 100f);
                break;
            }
            case ACTION_GYRO_CURVE:
                b.stickCurve(Math.max(10, Math.min(30, Math.round(s.stickCurve * 10) + step)) / 10f);
                break;
            case ACTION_GYRO_GEARING:
                if (s.output == GyroAim.Output.STICK) {
                    b.stickTurnSpeed(s.stickTurnSpeed + step * GyroAim.STICK_TURN_STEP);
                } else {
                    b.mouseCountsPer360(s.mouseCountsPer360 + step * GyroAim.MOUSE_360_STEP);
                }
                break;
            default: return s;
        }
        return b.build();
    }

    /** {@code step} is +1 (Select, Right) or -1 (Left): off, game, music, round again. */
    private void changeAudioHaptics(int step) {
        audioHapticsMode = nextAudioHaptics(
                getResources().getStringArray(R.array.audio_haptics_values), audioHapticsMode, step);
        refreshMenuValues();
        if (actionListener != null) {
            actionListener.onAudioHaptics(audioHapticsMode, audioHapticsStrength);
        }
    }

    /** {@code step} is +1 (Select, Right) or -1 (Left), within Settings' strength range. */
    private void changeAudioHapticsStrength(int step) {
        int next = nextAudioHapticsStrength(audioHapticsStrength, step);
        if (next == audioHapticsStrength) return;
        audioHapticsStrength = next;
        refreshMenuValues();
        if (actionListener != null) {
            actionListener.onAudioHaptics(audioHapticsMode, audioHapticsStrength);
        }
    }

    static int nextAudioHapticsStrength(int current, int step) {
        return Math.max(PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_MIN,
                Math.min(PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_MAX,
                        current + step * PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_STEP));
    }

    static String nextAudioHaptics(String[] values, String current, int step) {
        int index = Math.max(0, Arrays.asList(values).indexOf(current));
        return values[Math.floorMod(index + step, values.length)];
    }

    private static <T extends Enum<T>> T cycle(T[] values, T current, int step) {
        return values[Math.floorMod(current.ordinal() + step, values.length)];
    }

    /** 0.1–5.0 in steps of 0.1, as in the settings screen. */
    static float sensitivityStep(float value, int step) {
        return Math.max(1, Math.min(50, Math.round(value * 10) + step)) / 10f;
    }

    /** The stream's settings are known; its bitrate can always change (the only place it does). */
    private boolean streamSettingsAvailable() {
        return currentStream != null;
    }

    private String streamSummary(LiveStreamSettings settings) {
        return streamText(getContext(), settings, true);
    }

    /** A stream's settings as the overlay and the stream's notices name them ("1440p · 60 FPS"). */
    public static String streamText(Context context, LiveStreamSettings settings,
                                    boolean withBitrate) {
        String text = streamText(context, settings.width, settings.height, settings.fps,
                withBitrate ? settings.bitrateKbps : 0);
        return settings.hdr ? text + " · HDR" : text;
    }

    /** "1440p · 60 FPS · 40 Mb/s"; {@code bitrateKbps} 0 leaves the bitrate out. */
    public static String streamText(Context context, int width, int height, int fps,
                                    int bitrateKbps) {
        String text = LiveStreamSettings.resolutionName(width, height) + " · "
                + context.getString(R.string.overlay_stream_fps, fps);
        return bitrateKbps <= 0 ? text : text + " · "
                + context.getString(R.string.overlay_bitrate_current, megabits(bitrateKbps));
    }

    private String bitrateText(int bitrateKbps) {
        return s(R.string.overlay_bitrate_current, megabits(bitrateKbps));
    }

    /**
     * The stream's settings as their own list inside the panel: Left/Right (and Select) change
     * the focused value, Apply reconnects with them; Back returns to the menu.
     */
    private void renderStreamList() {
        addMenuSection(R.string.overlay_menu_quality);
        TextView hint = panelText(getContext(), 11, OverlayMenuButton.TEXT_SECONDARY, false);
        hint.setText(R.string.overlay_stream_hint);
        menuList.addView(hint, bottomMargin(dp(6)));
        addValueOption(R.string.console_stream_bitrate, ACTION_STREAM_BITRATE,
                () -> bitrateText(pendingStream.bitrateKbps));
        if (streamOptions.offersModes()) {
            addValueOption(R.string.overlay_stream_resolution_label, ACTION_STREAM_RESOLUTION,
                    () -> s(R.string.overlay_stream_resolution, pendingStream.width,
                            pendingStream.height));
            addValueOption(R.string.overlay_stream_fps_label, ACTION_STREAM_FPS,
                    () -> s(R.string.overlay_stream_fps, pendingStream.fps));
        }
        if (streamOptions.hdrAvailable) {
            addValueOption(R.string.overlay_stream_hdr_label, ACTION_STREAM_HDR,
                    () -> onOff(pendingStream.hdr));
        }
        OverlayMenuButton apply = addMenuButton(menuList, 0, "", ACTION_STREAM_APPLY,
                ROW_REST_FILL, OverlayMenuButton.TEXT);
        Runnable applyLabel = () -> apply.setLabel(s(pendingStream.equals(currentStream)
                ? R.string.overlay_stream_unchanged : R.string.overlay_stream_apply));
        applyLabel.run();
        menuRefreshers.add(applyLabel);
    }

    private String s(int resource, Object... args) {
        return getContext().getString(resource, args);
    }

    private void openStream() {
        streamOpen = true;
        renderMenuList();
        post(() -> setMenuIndex(0));
    }

    private void closeStream() {
        streamOpen = false;
        renderMenuList();
        post(() -> setMenuIndex(menuActions.indexOf(ACTION_STREAM_SETTINGS)));
    }

    private static boolean isStreamOption(int action) {
        return action >= ACTION_STREAM_BITRATE && action <= ACTION_STREAM_HDR;
    }

    /** {@code step} is +1 (Select, Right) or -1 (Left); the list shows the choice at once. */
    private void changeStream(int action, int step) {
        LiveStreamSettings p = pendingStream;
        switch (action) {
            case ACTION_STREAM_BITRATE:
                pendingStream = p.withBitrate(
                        clampBitrate(p.bitrateKbps + step * BITRATE_STEP_KBPS));
                break;
            case ACTION_STREAM_RESOLUTION:
                pendingStream = p.nextResolution(streamOptions, step);
                break;
            case ACTION_STREAM_FPS:
                pendingStream = p.nextFps(streamOptions, step);
                break;
            default:
                pendingStream = p.toggleHdr(streamOptions);
                break;
        }
        refreshMenuValues();
    }

    private void addMouseRow() {
        addShortcutRow(R.drawable.ic_overlay_mouse, R.string.overlay_menu_mouse_emulation,
                ACTION_TOGGLE_MOUSE_EMULATION, ControllerGlyphs.Button.WEST);
    }

    private void addShortcutRow(int iconResId, int labelRes, int action,
                                ControllerGlyphs.Button shortcut) {
        addMenuButton(menuList, iconResId, getContext().getString(labelRes), action,
                ROW_REST_FILL, OverlayMenuButton.TEXT).setShortcut(
                ControllerGlyphs.typeface(getContext()),
                ControllerGlyphs.text(playStationButtons, shortcut));
    }

    private void addMenuSection(int labelRes) {
        TextView label = panelText(getContext(), 10, OverlayMenuButton.TEXT_SECONDARY, true);
        label.setText(labelRes);
        label.setAllCaps(true);
        label.setLetterSpacing(0.16f);
        View previous = menuList.getChildCount() == 0 ? null
                : menuList.getChildAt(menuList.getChildCount() - 1);
        // Plain rows keep ~7 dp of air under their text; the filled pair row does not.
        int top = previous == null ? 0 : previous instanceof OverlayMenuButton
                || previous instanceof TextView ? 18 : 25;
        LinearLayout.LayoutParams params = topMargin(dp(top));
        params.bottomMargin = dp(5);
        menuList.addView(label, params);
    }

    private OverlayMenuButton addMenuButton(LinearLayout parent, int iconResId, String label,
                                            int action, int restFill, int restText) {
        OverlayMenuButton button = OverlayMenuButton.create(getContext(), iconResId, label);
        button.panelRow(restFill, restText);
        parent.addView(button, wrapWidthParams());
        menuButtons.add(button);
        menuActions.add(action);
        final int index = menuButtons.size() - 1;
        button.setOnClickListener(v -> selectAndActivate(Region.MENU, index));
        button.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                activeRegion = Region.MENU;
                menuIndex = index;
            }
            button.setSelected(hasFocus);
        });
        return button;
    }

    /** "More above/below": the list fades out at an edge only while it can scroll that way. */
    private void fadeOverflow(ScrollView scroll) {
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(FADING_EDGE_DP));
    }

    private TextView panelText(Context context, int sizeSp, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(color);
        if (bold) text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
        return text;
    }

    private TextView tabGlyph(Context context, ControllerGlyphs.Button button) {
        TextView glyph = panelText(context, 14, OverlayMenuButton.TEXT_SECONDARY, false);
        glyph.setTypeface(ControllerGlyphs.typeface(context));
        glyph.setText(ControllerGlyphs.text(ControllerGlyphs.hasPlayStationController(), button));
        glyph.setPadding(dp(4), 0, dp(4), 0);
        return glyph;
    }

    private TextView tabLabel(Context context, String text) {
        TextView label = panelText(context, 12, OverlayMenuButton.TEXT_SECONDARY, false);
        label.setText(text);
        label.setPadding(dp(12), dp(3), dp(12), dp(3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(4);
        label.setLayoutParams(params);
        return label;
    }

    private void styleTabs() {
        boolean community = overlayMode == OverlayMode.COMMUNITY;
        styleTab(menuTab, !community);
        styleTab(discordTab, community);
        tabRow.setVisibility(canShowDiscordCard() && prompt == null ? VISIBLE : GONE);
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setTextColor(selected ? OverlayMenuButton.INK : OverlayMenuButton.TEXT_SECONDARY);
        tab.setBackground(selected ? pill(Color.WHITE, 0) : null);
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(18));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private static LinearLayout.LayoutParams topMargin(int px) {
        LinearLayout.LayoutParams params = wrapWidthParams();
        params.topMargin = px;
        return params;
    }

    private static LinearLayout.LayoutParams bottomMargin(int px) {
        LinearLayout.LayoutParams params = wrapWidthParams();
        params.bottomMargin = px;
        return params;
    }

    private static LinearLayout.LayoutParams rightMargin(int px) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = px;
        return params;
    }

    private static LinearLayout.LayoutParams wrapWidthParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void buildDiscordActions(int spacing) {
        discordActionsContainer.removeAllViews();
        discordButtons.clear();
        discordActions.clear();
        discordMuteButton = null;
        discordLeaveButton = null;
        discordRejoinButton = null;
        discordDockButton = null;
        discordSocialFriendsButton = null;
        if (!communityVoiceActionsVisible(discordQuickSection, discordConfigured)) return;

        DiscordActionVoiceState actionVoice = discordActionVoiceState();
        boolean connected = actionVoice.connected;
        if (connected) {
            LinearLayout firstRow = new LinearLayout(getContext());
            firstRow.setOrientation(LinearLayout.HORIZONTAL);
            discordActionsContainer.addView(firstRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            discordMuteButton = addDiscordButton(firstRow, R.drawable.ic_overlay_microphone,
                    discordMuteLabel(actionVoice.muted), ACTION_DISCORD_MUTE, spacing);
            setDiscordShortcut(discordMuteButton, discordMuteShortcut);
            discordLeaveButton = addDiscordButton(firstRow, R.drawable.ic_overlay_close,
                    discordLeaveLabel(), ACTION_DISCORD_LEAVE, 0);
            setDiscordShortcut(discordLeaveButton, discordLeaveShortcut);
            LinearLayout secondRow = new LinearLayout(getContext());
            secondRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams secondRowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            secondRowParams.topMargin = spacing;
            discordActionsContainer.addView(secondRow, secondRowParams);
            discordDockButton = addDiscordButton(secondRow, R.drawable.ic_overlay_restore,
                    discordDockLabel(), ACTION_DISCORD_DOCK, spacing);
        } else {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            discordActionsContainer.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            if (communityRejoinVisible(connected, discordCanRejoin)) {
                discordRejoinButton = addDiscordButton(row, R.drawable.ic_overlay_play,
                        discordRejoinLabel(), ACTION_DISCORD_REJOIN, spacing);
            }
            discordDockButton = addDiscordButton(row, R.drawable.ic_overlay_restore,
                    discordDockLabel(), ACTION_DISCORD_DOCK, 0);
        }
    }

    private void ensureDiscordActions() {
        DiscordActionVoiceState actionVoice = discordActionVoiceState();
        String signature = discordQuickSection + ":" + discordConfigured + ":"
                + actionVoice.connected + ":" + actionVoice.muted + ":"
                + actionVoice.deafened + ":" + discordCanRejoin;
        if (signature.equals(renderedDiscordActionSignature)) return;
        renderedDiscordActionSignature = signature;
        int previous = discordIndex;
        long interactionGeneration = communityInteractionGeneration;
        buildDiscordActions(dp(BUTTON_SPACING_DP));
        if (communityFocus == CommunityFocus.ACTION && !discordButtons.isEmpty()) {
            post(() -> {
                if (overlayMode == OverlayMode.COMMUNITY
                        && interactionGeneration == communityInteractionGeneration
                        && communityFocus == CommunityFocus.ACTION && !discordButtons.isEmpty()) {
                    setDiscordIndex(Math.min(previous, discordButtons.size() - 1));
                }
            });
        } else if (discordButtons.isEmpty()) {
            communityFocus = CommunityFocus.RAIL;
        }
    }

    private OverlayMenuButton addDiscordButton(LinearLayout row, int iconResId,
                                                String label, int action, int rightMarginPx) {
        OverlayMenuButton button = OverlayMenuButton.create(getContext(), iconResId, label);
        button.fitLabel();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = rightMarginPx;
        row.addView(button, params);

        discordButtons.add(button);
        discordActions.add(action);
        final int index = discordButtons.size() - 1;
        button.setOnClickListener(v -> {
            if (overlayMode == OverlayMode.COMMUNITY) communityInteractionGeneration++;
            selectAndActivate(Region.DISCORD, index);
        });
        button.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                activeRegion = Region.DISCORD;
                discordIndex = index;
                if (overlayMode == OverlayMode.COMMUNITY) communityFocus = CommunityFocus.ACTION;
                button.setSelected(true);
            } else {
                button.setSelected(false);
            }
        });
        return button;
    }

    private void addDiscordRailIcon(Context context, int iconResource, int descriptionResource, int section) {
        ImageButton icon = new ImageButton(context);
        icon.setImageResource(iconResource);
        icon.setContentDescription(context.getString(descriptionResource));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setFocusable(true);
        icon.setClickable(true);
        icon.setOnClickListener(ignored -> {
            if (overlayMode == OverlayMode.COMMUNITY) communityInteractionGeneration++;
            selectDiscordQuickSection(section);
        });
        icon.setOnFocusChangeListener((ignored, focused) -> {
            if (focused) {
                // Rail navigation changes the section immediately; Center is only an optional
                // confirmation, never a prerequisite for a visible content update.
                selectDiscordQuickSection(section);
                styleDiscordRailIcon(icon, true, true);
                communityFocus = CommunityFocus.RAIL;
                activeRegion = Region.DISCORD;
            }
            else styleDiscordRailIcon(icon, section == discordQuickSection, false);
        });
        styleDiscordRailIcon(icon, section == discordQuickSection, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(46), dp(46));
        params.topMargin = dp(8);
        discordRail.addView(icon, params);
        discordRailIcons.add(icon);
    }

    private void selectDiscordQuickSection(int section) {
        if (section < 0 || section >= discordRailIcons.size()) return;
        if (discordQuickSection == section) {
            for (int index = 0; index < discordRailIcons.size(); index++) {
                styleDiscordRailIcon(discordRailIcons.get(index), index == section,
                        discordRailIcons.get(index).hasFocus());
            }
            return;
        }
        discordQuickSection = section;
        CommunitySection nextSection = section == 1 ? CommunitySection.FRIENDS
                : section == 2 ? CommunitySection.CHANNELS : CommunitySection.TOGETHER;
        boolean sectionChanged = communitySection != nextSection;
        communitySection = nextSection;
        if (communitySubmode != CommunitySubmode.ROOT) communitySubmode = CommunitySubmode.ROOT;
        for (int index = 0; index < discordRailIcons.size(); index++) {
            styleDiscordRailIcon(discordRailIcons.get(index), index == section,
                    discordRailIcons.get(index).hasFocus());
        }
        renderedDiscordSocialRevision = Long.MIN_VALUE;
        renderedDiscordSocialSection = -1;
        renderDiscordCard();
        applyOverlayMode();
        if (discordContentScroll != null) discordContentScroll.scrollTo(0, 0);
        if (sectionChanged && actionListener != null) {
            actionListener.onDiscordCommunitySectionChanged(communitySection);
        }
    }

    /** Rail icons take the menu buttons' focus (white fill, black ink); the section stays lit. */
    private void styleDiscordRailIcon(ImageButton icon, boolean selected, boolean focused) {
        icon.setColorFilter(focused ? OverlayMenuButton.INK
                : selected ? OverlayMenuButton.TEXT : OverlayMenuButton.TEXT_SECONDARY);
        if (focused) {
            icon.setBackground(OverlayMenuButton.surface(getContext(), true,
                    OverlayMenuButton.CORNER_RADIUS_DP));
            return;
        }
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(OverlayMenuButton.CORNER_RADIUS_DP));
        background.setColor(selected ? 0x26FFFFFF : Color.TRANSPARENT);
        icon.setBackground(background);
    }

    private void openCommunity() {
        if (!canShowDiscordCard()) return;
        if (!communityOpenNotifies(overlayMode)) return;
        invalidateCommunityProjectionCache();
        overlayMode = OverlayMode.COMMUNITY;
        discordQuickSection = 0;
        communitySection = CommunitySection.TOGETHER;
        communitySubmode = CommunitySubmode.ROOT;
        communityMotionDirection = KeyEvent.KEYCODE_UNKNOWN;
        communityFocus = CommunityFocus.RAIL;
        activeRegion = Region.DISCORD;
        discordIndex = 0;
        applyOverlayMode();
        renderDiscordCard();
        if (actionListener != null) actionListener.onDiscordCommunityOpened();
        post(() -> {
            if (!discordRailIcons.isEmpty()) discordRailIcons.get(discordQuickSection).requestFocus();
        });
    }

    /** Opens an exact known friend after the controller has secured DM ownership. */
    public boolean openDiscordFriendChat(String friendId) {
        if (friendId == null || friendId.isEmpty()) return false;
        if (overlayMode == OverlayMode.MENU) openCommunity();
        if (overlayMode != OverlayMode.COMMUNITY || communityModel == null
                || !communityModel.directMessagesAvailable || actionListener == null) return false;
        boolean known = false;
        for (CommunityFriend friend : communityModel.friends) {
            if (friendId.equals(friend.id)) {
                known = true;
                break;
            }
        }
        if (!known) return false;
        communityInteractionGeneration++;
        communitySection = CommunitySection.FRIENDS;
        discordQuickSection = 1;
        selectedFriendId = friendId;
        pendingChatFriendId = friendId;
        communitySubmode = CommunitySubmode.FRIEND_CHAT;
        communityFocus = CommunityFocus.CHAT_COMPOSER;
        renderDiscordCard();
        actionListener.onDiscordCommunityOpenFriendChat(friendId);
        return true;
    }

    private void returnToMenu() {
        overlayMode = OverlayMode.MENU;
        communityMotionDirection = KeyEvent.KEYCODE_UNKNOWN;
        communityFocus = CommunityFocus.RAIL;
        communitySubmode = CommunitySubmode.ROOT;
        invalidateCommunityProjectionCache();
        applyOverlayMode();
        post(() -> setMenuIndex(menuIndex));
    }

    private void applyOverlayMode() {
        boolean community = overlayMode == OverlayMode.COMMUNITY;
        menuScroll.setVisibility(community ? GONE : VISIBLE);
        gyroDescription.setVisibility(!community && (gyroOpen && gyroSettings != null
                || autopilotOpen && autopilotVariantsOpen) ? VISIBLE : GONE);
        discordContainer.setVisibility(community && canShowDiscordCard() ? VISIBLE : GONE);
        styleTabs();
        if (community) {
            if (communitySubmode == CommunitySubmode.FRIEND_CHAT) applyCommunityChatPanelSize();
            else restoreCommunityPanelSize();
        } else {
            setPanelWidth(dp(PANEL_WIDTH_DP));
        }
    }

    static OverlayMode backMode(OverlayMode mode) {
        return mode == OverlayMode.COMMUNITY ? OverlayMode.MENU : OverlayMode.MENU;
    }

    static boolean communityVisible(OverlayMode mode, boolean available) {
        return mode == OverlayMode.COMMUNITY && available;
    }

    static boolean communityOpenNotifies(OverlayMode mode) {
        return mode != OverlayMode.COMMUNITY;
    }

    static int nextCommunityRailIndex(int current, int direction, int count) {
        if (count <= 0) return current;
        return Math.max(0, Math.min(count - 1, current + direction));
    }

    static boolean communityVoiceActionsVisible(int section, boolean configured) {
        return section == 0 && configured;
    }

    static boolean communityRejoinVisible(boolean connected, boolean canRejoin) {
        return !connected && canRejoin;
    }

    static int communityActionTarget(int current, int keyCode, int count) {
        if (count <= 0) return -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return current % 2 == 1 ? current - 1 : -1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return current % 2 == 0 && current + 1 < count ? current + 1 : current;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return current < 2 ? current : current - 2;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return Math.min(count - 1, current + 2);
        return current;
    }

    static int communityRegionTarget(int currentRegion, int keyCode, boolean hasActions) {
        if (currentRegion == COMMUNITY_REGION_RAIL && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return COMMUNITY_REGION_CONTENT;
        }
        if (currentRegion == COMMUNITY_REGION_ACTION && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return COMMUNITY_REGION_CONTENT;
        }
        if (currentRegion == COMMUNITY_REGION_CONTENT) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return COMMUNITY_REGION_RAIL;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && hasActions) return COMMUNITY_REGION_ACTION;
        }
        return currentRegion;
    }

    static int communityRailRightRegion(boolean hasRows, boolean hasActions) {
        if (hasRows) return COMMUNITY_REGION_CONTENT;
        return hasActions ? COMMUNITY_REGION_ACTION : COMMUNITY_REGION_RAIL;
    }

    static boolean communityBackNotifiesChannels(CommunitySubmode submode) {
        return submode == CommunitySubmode.GUILD_CHANNELS;
    }

    static boolean shouldExitCommunityChatForPause(OverlayMode mode, CommunitySubmode submode) {
        return mode == OverlayMode.COMMUNITY && submode == CommunitySubmode.FRIEND_CHAT;
    }

    static int communityContentScrollTarget(int currentScroll, int maxScroll, int delta) {
        return Math.max(0, Math.min(Math.max(0, maxScroll), currentScroll + delta));
    }

    static int communityActionMaxRows() { return 2; }
    static int communityQuickWidthDp() { return COMMUNITY_QUICK_WIDTH_DP; }
    static int communityChatWidthDp() { return COMMUNITY_CHAT_WIDTH_DP; }
    static int communityRailIconCount() { return COMMUNITY_RAIL_ICON_COUNT; }
    static boolean communityContentUsesSingleScrollOwner() { return false; }
    static boolean communityContentUsesFocusableRows() { return true; }
    static int communityPanelDimension(int requested, int available) {
        return available > 0 ? Math.min(requested, available) : requested;
    }
    static int communityStableIndex(List<String> ids, String selectedId) {
        if (ids == null || ids.isEmpty()) return -1;
        int index = selectedId == null ? -1 : ids.indexOf(selectedId);
        return index >= 0 ? index : 0;
    }

    static boolean shouldAutoScrollCommunityChat(boolean sameRecipient, boolean nearBottom,
                                                  boolean newOutgoingMessage) {
        return !sameRecipient || nearBottom || newOutgoingMessage;
    }

    static boolean isCommunityNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_B
                || keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1;
    }

    static boolean communityRestoreStillCurrent(long scheduledGeneration, long currentGeneration,
                                                long scheduledRevision, long currentRevision,
                                                CommunitySection scheduledSection, CommunitySection currentSection,
                                                CommunitySubmode scheduledSubmode, CommunitySubmode currentSubmode,
                                                long scheduledInteraction, long currentInteraction) {
        return scheduledGeneration == currentGeneration && scheduledRevision == currentRevision
                && scheduledSection == currentSection && scheduledSubmode == currentSubmode
                && scheduledInteraction == currentInteraction;
    }

    static boolean shouldRenderCommunityProjection(long renderedRevision, long revision,
                                                   CommunitySection renderedSection,
                                                   CommunitySection section,
                                                   CommunitySubmode renderedSubmode,
                                                   CommunitySubmode submode) {
        return renderedRevision != revision || renderedSection != section || renderedSubmode != submode;
    }

    static int communityActionLeftRegion(int actionIndex, boolean hasRows) {
        if (actionIndex % 2 == 1) return COMMUNITY_REGION_ACTION;
        return hasRows ? COMMUNITY_REGION_CONTENT : COMMUNITY_REGION_RAIL;
    }

    static boolean communityCancelDownHasEffect(boolean alreadyDown, int repeatCount) {
        return !alreadyDown && repeatCount == 0;
    }

    static boolean communityListDownEntersActions(int listIndex, int rowCount, boolean hasActions) {
        return hasActions && rowCount > 0 && listIndex >= rowCount - 1;
    }

    static boolean communityActionUpEntersList(int actionIndex, boolean hasRows) {
        return hasRows && actionIndex >= 0 && actionIndex < 2;
    }

    static boolean shouldRouteCommunityChatKeyboard(OverlayMode mode, CommunitySubmode submode,
                                                    boolean keyboardVisible) {
        return mode == OverlayMode.COMMUNITY && submode == CommunitySubmode.FRIEND_CHAT
                && keyboardVisible;
    }

    static boolean shouldForwardCommunityChatKeyAction(int action) {
        return action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP;
    }

    static ControllerGlyphs.Button communityFooterButton(boolean confirmAction, boolean flipFaceButtons) {
        if (confirmAction) {
            return flipFaceButtons ? ControllerGlyphs.Button.CANCEL : ControllerGlyphs.Button.CONFIRM;
        }
        return flipFaceButtons ? ControllerGlyphs.Button.CONFIRM : ControllerGlyphs.Button.CANCEL;
    }

    static String communityChatStructureSignature(ChatModel chat) {
        if (chat == null) return "null";
        StringBuilder value = new StringBuilder();
        appendCommunitySignature(value, chat.recipientId);
        appendCommunitySignature(value, chat.recipientName);
        appendCommunitySignature(value, chat.recipientAvatarUrl);
        appendCommunitySignature(value, chat.loadingHistory ? "1" : "0");
        appendCommunitySignature(value, Integer.toString(chat.messages.size()));
        for (ChatMessage message : chat.messages) {
            appendCommunitySignature(value, message.id);
            appendCommunitySignature(value, message.content);
            appendCommunitySignature(value, message.additionalContentType);
            appendCommunitySignature(value, message.additionalContentTitle);
            appendCommunitySignature(value, Integer.toString(message.additionalContentCount));
            appendCommunitySignature(value, message.self ? "1" : "0");
            appendCommunitySignature(value, message.disclosure ? "1" : "0");
        }
        return value.toString();
    }

    static boolean shouldRebuildCommunityChat(String renderedSignature, ChatModel chat) {
        return renderedSignature == null
                || !renderedSignature.equals(communityChatStructureSignature(chat));
    }

    static String communityChatStatusSignature(ChatModel chat) {
        if (chat == null) return "null";
        return (chat.sending ? "1" : "0") + ":" + (chat.retryable ? "1" : "0") + ":"
                + chat.error.length() + ":" + chat.error + chat.retryMessage.length() + ":"
                + chat.retryMessage;
    }

    private static void appendCommunitySignature(StringBuilder target, String value) {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append(':').append(safe);
    }

    public void setMenuActionListener(MenuActionListener listener) {
        this.actionListener = listener;
    }

    public void setSuspendSessionAvailable(boolean available) {
        suspendSessionAvailable = available;
    }

    /** The child's time left, shown in the menu row; null when the stream has no child limit. */
    public void setChildRemainingTime(String text) {
        childRemainingTime = text;
        if (text != null) childRemainingChip.setText(text);
    }

    public void setEndGameAvailable(boolean available) {
        endGameAvailable = available;
    }

    /** Set before {@link #show}. */
    public void setCaptures(Captures captures) {
        this.captures = captures;
    }

    /** Set before {@link #show}; null hides the gyro row. {@code buttons} the pad can spare. */
    public void setGyroAimSettings(GyroAim.Settings settings, List<GyroAim.Button> buttons) {
        gyroSettings = settings;
        gyroButtons = new ArrayList<>(buttons);
    }

    /** The stream's audio haptics mode (an {@code audio_haptics_values} entry) and strength. */
    public void setAudioHaptics(String mode, int strengthPercent) {
        audioHapticsMode = mode;
        audioHapticsStrength = strengthPercent;
        refreshMenuValues();
    }

    public void setFlipFaceButtons(boolean flip) {
        this.flipFaceButtons = flip;
        renderDiscordCommunityFooter();
    }

    public void setInstallationConfirmationAvailable(boolean available) {
        installationConfirmationAvailable = available;
    }

    /**
     * The link card for the next {@link #show()}: {@code emergency} is what a poor link offers
     * (null: nothing), {@code returnTo} what this emergency stream returns to (null: none).
     */
    public void setEmergencyStream(LiveStreamSettings emergency, int observedKbps,
                                   LiveStreamSettings returnTo) {
        emergencyStream = emergency;
        emergencyObservedKbps = observedKbps;
        emergencyReturn = returnTo;
    }

    /**
     * The Autopilot page for the next {@link #show()}: {@code advice} null without one,
     * {@code gyroTrial} when it may offer a try of gyro aiming, {@code variants} the last
     * analysis' (empty without one).
     */
    public void setAutopilot(AutopilotTest test, String advice, boolean gyroTrial,
                             List<AutopilotVariant> variants) {
        autopilotTest = test;
        autopilotAdvice = advice;
        autopilotGyroTrial = gyroTrial;
        autopilotVariants = variants;
    }

    /** The next {@link #show()} shows {@code next} in place of the menu. */
    public void setPrompt(Prompt next) {
        prompt = next;
        promptActionsAtMs = SystemClock.uptimeMillis() + RESULT_ACTION_DELAY_MS;
    }

    public void setControllerBatteryInfo(List<ControllerBatteryInfo> info) {
        controllerBatteryInfo = info != null ? new ArrayList<>(info) : new ArrayList<>();
        if (getVisibility() == VISIBLE) renderPads();
    }

    /**
     * The stream's settings now and what this TV and host can switch to; any unapplied choice
     * is dropped. The rows follow on the next {@link #show()}.
     */
    public void setLiveStreamSettings(LiveStreamSettings current,
                                      LiveStreamSettings.Options options) {
        currentStream = current;
        pendingStream = current;
        streamOptions = options;
    }

    /** Hides the entire Discord surface for stream profiles that cannot use it. */
    public void setDiscordFeatureEnabled(boolean enabled) {
        discordFeatureEnabled = enabled;
        if (!enabled && overlayMode == OverlayMode.COMMUNITY) {
            returnToMenu();
        }
        renderDiscordCard();
    }

    public void setDiscordRejoinTarget(boolean available, String channelName) {
        discordCanRejoin = available;
        discordRejoinChannel = channelName == null ? "" : channelName;
        updateDiscordActionButtons();
    }

    public void setDiscordDocked(boolean docked) {
        discordDocked = docked;
        updateDiscordActionButtons();
    }

    public void setDiscordConfigured(boolean configured) {
        discordConfigured = configured;
        if (!configured) {
            discordVoice = null;
            discordError = null;
            discordLoading = false;
        }
        renderDiscordCard();
    }

    public void setDiscordSocialAvailable(boolean available) {
        if (discordSocialAvailable == available) return;
        discordSocialAvailable = available;
        renderedDiscordSocialRevision = Long.MIN_VALUE;
        renderedDiscordSocialSection = -1;
        renderDiscordCard();
    }

    /** The process-wide client owns this snapshot; the overlay only projects a new revision. */
    public void setDiscordSocialState(DiscordSocialClient.Snapshot snapshot) {
        if (snapshot == null || snapshot.revision == discordSocialRevision) return;
        discordSocialRevision = snapshot.revision;
        discordSocialConnected = snapshot.connected;
        discordSocialDisplayName = snapshot.displayName;
        discordSocialAvatarUrl = snapshot.avatarUrl;
        discordSocialFriends = snapshot.friendDetails;
        renderDiscordCard();
        updateDiscordActionButtons();
    }

    /** Projects controller-owned Community state without doing network or Discord SDK work. */
    public void setDiscordCommunityModel(CommunityModel model) {
        if (model == null) {
            cancelCommunityChatKeyboardHold();
            communityModel = null;
            invalidateCommunityProjectionCache();
            renderDiscordCard();
            return;
        }
        if (communityModel != null && model.revision == communityModel.revision) return;
        if (communitySubmode == CommunitySubmode.FRIEND_CHAT
                && !communityChatRecipientId(communityModel).equals(communityChatRecipientId(model))) {
            cancelCommunityChatKeyboardHold();
        }
        communityModel = model;
        if (!model.selectedGuildId.isEmpty()) selectedGuildId = model.selectedGuildId;
        if (!pendingGuildId.isEmpty() && pendingGuildId.equals(model.selectedGuildId)) pendingGuildId = "";
        renderDiscordCard();
    }

    /** Lifecycle-only chat exit. Controller cleanup already ran, so no user callback fires. */
    public void exitDiscordCommunityChatForPause() {
        if (!shouldExitCommunityChatForPause(overlayMode, communitySubmode)) return;
        cancelCommunityChatKeyboardHold();
        pendingChatFriendId = "";
        communitySubmode = CommunitySubmode.ROOT;
        communitySection = CommunitySection.FRIENDS;
        discordQuickSection = 1;
        communityFocus = CommunityFocus.RAIL;
        restoreCommunityPanelSize();
        renderDiscordCard();
        post(() -> {
            if (!communityRows.isEmpty()) focusCommunityList();
            else focusCommunityRail();
        });
    }

    public void setDiscordShortcuts(String muteShortcut, String leaveShortcut) {
        discordMuteShortcut = normalizeDiscordShortcut(muteShortcut);
        discordLeaveShortcut = normalizeDiscordShortcut(leaveShortcut);
        if (!"none".equals(discordMuteShortcut) &&
                discordMuteShortcut.equals(discordLeaveShortcut)) {
            discordLeaveShortcut = "none";
        }
        updateDiscordActionButtons();
    }

    public void setDiscordState(DiscordGatewayClient.VoiceState voice, String error,
                                boolean loading) {
        if (voice != null) discordVoice = voice;
        discordError = error;
        discordLoading = loading;
        renderDiscordCard();
        updateDiscordActionButtons();
    }

    private void renderDiscordCard() {
        if (discordContainer == null || discordContentContainer == null) return;
        discordContainer.setVisibility(communityVisible(overlayMode, canShowDiscordCard())
                ? VISIBLE : GONE);
        styleTabs();
        if (!canShowDiscordCard()) return;

        renderDiscordQuickHeaderIfChanged();
        ensureDiscordActions();
        if (communityModel != null) {
            if (shouldRenderCommunityProjection(renderedCommunityModelRevision,
                    communityModel.revision, renderedCommunitySection, communitySection,
                    renderedCommunitySubmode, communitySubmode)) {
                renderCommunityModel();
                renderedCommunityModelRevision = communityModel.revision;
                renderedCommunitySection = communitySection;
                renderedCommunitySubmode = communitySubmode;
            }
        }
        else {
            invalidateCommunityProjectionCache();
            renderDiscordVoiceCard();
            renderDiscordSocialCard();
        }
    }

    private void renderDiscordQuickHeaderIfChanged() {
        String signature = discordQuickSection + ":" + discordSocialConnected + ":"
                + discordSocialDisplayName + ":" + discordSocialAvatarUrl;
        if (signature.equals(renderedCommunityHeaderSignature)) return;
        renderedCommunityHeaderSignature = signature;
        renderDiscordQuickHeader();
    }

    private void renderDiscordCommunityFooter() {
        if (discordQuickFooter == null) return;
        discordQuickFooter.removeAllViews();
        addDiscordCommunityFooterItem(true, R.string.discord_community_open, dp(14));
        addDiscordCommunityFooterItem(false, R.string.discord_community_back, 0);
    }

    private void addDiscordCommunityFooterItem(boolean confirmAction, int labelResource,
                                               int rightMargin) {
        TextView glyph = new TextView(getContext());
        glyph.setText(ControllerGlyphs.text(playStationButtons,
                communityFooterButton(confirmAction, flipFaceButtons)));
        glyph.setTextColor(OverlayMenuButton.TEXT);
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        glyph.setGravity(Gravity.CENTER);
        glyph.setTypeface(ControllerGlyphs.typeface(getContext()));
        discordQuickFooter.addView(glyph, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView label = new TextView(getContext());
        label.setText(labelResource);
        label.setTextColor(OverlayMenuButton.TEXT);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(24));
        labelParams.leftMargin = dp(4);
        labelParams.rightMargin = rightMargin;
        discordQuickFooter.addView(label, labelParams);
    }

    private void invalidateCommunityProjectionCache() {
        renderedCommunityModelRevision = Long.MIN_VALUE;
        renderedCommunitySection = null;
        renderedCommunitySubmode = null;
        renderedCommunityHeaderSignature = "";
        renderedChatStructureSignature = "";
        renderedChatDraft = "";
        renderedChatStatusSignature = "";
    }

    private void renderDiscordQuickHeader() {
        discordQuickHeader.removeAllViews();
        TextView title = new TextView(getContext());
        title.setText(getContext().getString(discordQuickSection == 1
                ? R.string.discord_community_friends : discordQuickSection == 2
                ? R.string.discord_community_servers : R.string.discord_community_together));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(Color.WHITE);
        discordQuickHeader.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (discordSocialConnected) {
            LinearLayout account = new LinearLayout(getContext());
            account.setGravity(Gravity.CENTER_VERTICAL);
            account.addView(DiscordCommunityPresentation.avatar(getContext(),
                    discordSocialDisplayName.isEmpty() ? "Discord" : discordSocialDisplayName,
                    discordSocialAvatarUrl, 34), new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView name = new TextView(getContext());
            name.setText(discordSocialDisplayName);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            name.setTextColor(0xFFB8BDCC);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.leftMargin = dp(7);
            account.addView(name, nameParams);
            discordQuickHeader.addView(account);
        }
    }

    private void renderCommunityModel() {
        CommunityFocus focusBeforeRender = communityFocus;
        String rowBeforeRender = selectedCommunityRowId();
        int actionBeforeRender = discordIndex;
        long interactionBeforeRender = communityInteractionGeneration;
        long revision = communityModel.revision;
        CommunitySection section = communitySection;
        CommunitySubmode submode = communitySubmode;
        if (communitySubmode == CommunitySubmode.FRIEND_CHAT) {
            if (!shouldRebuildCommunityChat(renderedChatStructureSignature, communityModel.chat)
                    && discordChatContainer.getVisibility() == VISIBLE) {
                ensureCommunityChatComposer(communityModel.chat);
                bindCommunityChatStatus(communityModel.chat);
                return;
            }
            long generation = ++communityRenderGeneration;
            renderCommunityChat(generation);
            postCommunityFocusRestore(generation, revision, section, submode,
                    interactionBeforeRender, focusBeforeRender, rowBeforeRender, actionBeforeRender);
            return;
        }
        long generation = ++communityRenderGeneration;
        restoreCommunityPanelSize();
        discordRail.setVisibility(VISIBLE);
        discordQuickHeader.setVisibility(VISIBLE);
        discordContentScroll.setVisibility(VISIBLE);
        discordActionsContainer.setVisibility(VISIBLE);
        discordQuickFooter.setVisibility(VISIBLE);
        discordChatContainer.setVisibility(GONE);
        discordVoiceContentContainer.removeAllViews();
        discordSocialContentContainer.removeAllViews();
        communityRows.clear();
        communityListIndex = -1;
        if (communitySection == CommunitySection.TOGETHER) renderCommunityTogether();
        else if (communitySection == CommunitySection.FRIENDS) renderCommunityFriends();
        else renderCommunityChannels();
        restoreCommunityRowSelection();
        postCommunityFocusRestore(generation, revision, section, submode,
                interactionBeforeRender, focusBeforeRender, rowBeforeRender, actionBeforeRender);
    }

    private void postCommunityFocusRestore(long generation, long revision, CommunitySection section,
                                           CommunitySubmode submode, long interactionGeneration,
                                           CommunityFocus focus, String rowId, int actionIndex) {
        post(() -> {
            if (overlayMode != OverlayMode.COMMUNITY || getVisibility() != VISIBLE
                    || communityModel == null || !communityRestoreStillCurrent(generation,
                    communityRenderGeneration, revision, communityModel.revision, section,
                    communitySection, submode, communitySubmode, interactionGeneration,
                    communityInteractionGeneration)) return;
            if (focus == CommunityFocus.LIST && submode != CommunitySubmode.FRIEND_CHAT) {
                int index = communityRowIndex(rowId);
                if (index < 0 && !communityRows.isEmpty()) index = 0;
                if (index >= 0) setCommunityListIndex(index);
                else if (!discordButtons.isEmpty()) {
                    communityFocus = CommunityFocus.ACTION;
                    setDiscordIndex(0);
                } else focusCommunityRail();
            } else if (focus == CommunityFocus.ACTION && !discordButtons.isEmpty()
                    && submode != CommunitySubmode.FRIEND_CHAT) {
                communityFocus = CommunityFocus.ACTION;
                setDiscordIndex(Math.max(0, Math.min(actionIndex, discordButtons.size() - 1)));
            } else if (focus == CommunityFocus.CHAT_COMPOSER
                    || focus == CommunityFocus.CHAT_KEYBOARD) {
                if (discordChatComposer != null) discordChatComposer.requestFocus();
            } else {
                focusCommunityRail();
            }
        });
    }

    private boolean isCurrentCommunityRender(long generation, long revision,
                                             CommunitySection section, CommunitySubmode submode) {
        return overlayMode == OverlayMode.COMMUNITY && getVisibility() == VISIBLE
                && communityModel != null && communityModel.revision == revision
                && communityRenderGeneration == generation && communitySection == section
                && communitySubmode == submode;
    }

    private void renderCommunityTogether() {
        VoiceSummary voice = communityModel.voice;
        if (voice == null || !voice.connected) {
            addCommunityStateLine(communityModel.gatewayStatus, communityModel.gatewayMessage,
                    getContext().getString(R.string.overlay_discord_disconnected));
            return;
        }
        String location = voice.guildName.isEmpty() ? voice.channelName
                : voice.guildName + "  ·  " + voice.channelName;
        discordVoiceContentContainer.addView(discordLine(location, 16, Color.WHITE, true));
        String state = voice.deafened ? getContext().getString(R.string.overlay_discord_deafened)
                : voice.muted ? getContext().getString(R.string.overlay_discord_muted)
                : getContext().getString(R.string.overlay_community_active);
        discordVoiceContentContainer.addView(discordLine(state, 12, 0xFF69F0AE, false));
        if (voice.participants.isEmpty()) {
            discordVoiceContentContainer.addView(discordLine(getContext().getString(
                    R.string.overlay_discord_empty), 13, 0xFF9FA3B2, false));
            return;
        }
        for (VoiceParticipant participant : voice.participants) {
            String title = participant.self ? getContext().getString(R.string.overlay_discord_self,
                    participant.name) : participant.name;
            String details = participant.speaking ? getContext().getString(
                    R.string.overlay_discord_participant_speaking, "")
                    : participant.muted ? getContext().getString(R.string.overlay_discord_muted)
                    : participant.volume != 100 ? participant.volume + "%" : "";
            addCommunityRow("voice:" + participant.id, CommunityRowKind.INFO, title, details, participant);
        }
    }

    private void renderCommunityFriends() {
        if (communityModel.socialStatus != CommunityStatus.READY) {
            addCommunityStateLine(communityModel.socialStatus, communityModel.socialMessage,
                    getContext().getString(R.string.overlay_discord_social_connect_in_menu));
            return;
        }
        addCommunityFriends(FriendPresence.PLAYING, R.string.overlay_discord_social_playing);
        addCommunityFriends(FriendPresence.ONLINE, R.string.overlay_discord_social_online);
        addCommunityFriends(FriendPresence.OFFLINE, R.string.discord_community_offline);
        if (communityRows.isEmpty()) {
            discordVoiceContentContainer.addView(discordLine(getContext().getString(
                    R.string.overlay_discord_social_no_friends), 13, 0xFF9FA3B2, false));
        }
    }

    private void addCommunityFriends(FriendPresence presence, int heading) {
        boolean added = false;
        for (CommunityFriend friend : communityModel.friends) {
            if (friend.presence != presence) continue;
            if (!added) {
                discordVoiceContentContainer.addView(discordLine(getContext().getString(heading),
                        12, 0xFF9FA3B2, true));
                added = true;
            }
            String details = communityFriendDetails(friend);
            addCommunityRow("friend:" + friend.id, CommunityRowKind.FRIEND, friend.name, details, friend);
        }
    }

    private void renderCommunityChannels() {
        if (communitySubmode == CommunitySubmode.GUILD_CHANNELS) {
            renderCommunityGuildChannels();
            return;
        }
        if (communityModel.gatewayStatus != CommunityStatus.READY) {
            addCommunityStateLine(communityModel.gatewayStatus, communityModel.gatewayMessage,
                    getContext().getString(R.string.discord_community_host_unavailable));
            return;
        }
        addCommunityChannels(communityModel.favorites, R.string.discord_community_active,
                "favorite-channel:");
        addCommunityChannels(communityModel.recent, R.string.discord_community_recent,
                "recent-channel:");
        if (!communityModel.guilds.isEmpty()) {
            discordVoiceContentContainer.addView(discordLine(getContext().getString(
                    R.string.discord_community_servers), 12, 0xFF9FA3B2, true));
            for (CommunityGuild guild : communityModel.guilds) {
                addCommunityRow("guild:" + guild.id, CommunityRowKind.GUILD, guild.name, "", guild);
            }
        }
        if (communityRows.isEmpty()) discordVoiceContentContainer.addView(discordLine(getContext().getString(
                R.string.discord_no_channels), 13, 0xFF9FA3B2, false));
    }

    private void renderCommunityGuildChannels() {
        CommunityGuild guild = findGuild(selectedGuildId);
        String title = guild == null ? getContext().getString(R.string.discord_community_servers) : guild.name;
        discordVoiceContentContainer.addView(discordLine(title, 16, Color.WHITE, true));
        if (!pendingGuildId.isEmpty()) {
            discordVoiceContentContainer.addView(discordLine(getContext().getString(
                    R.string.overlay_discord_loading), 13, 0xFFC5C8D3, false));
            return;
        }
        if (communityModel.gatewayStatus == CommunityStatus.ERROR) {
            addCommunityStateLine(communityModel.gatewayStatus, communityModel.gatewayMessage,
                    getContext().getString(R.string.discord_no_channels));
            return;
        }
        if (communityModel.guildChannels.isEmpty()) {
            discordVoiceContentContainer.addView(discordLine(getContext().getString(
                    R.string.discord_no_channels), 13, 0xFF9FA3B2, false));
            return;
        }
        for (CommunityChannel channel : communityModel.guildChannels) {
            String details = channel.people >= 0 ? channel.people + "" : "";
            addCommunityRow("guild-channel:" + channel.id, CommunityRowKind.CHANNEL,
                    "#  " + channel.name, details, channel);
        }
    }

    private void addCommunityChannels(List<CommunityChannel> channels, int heading, String stablePrefix) {
        if (channels.isEmpty()) return;
        discordVoiceContentContainer.addView(discordLine(getContext().getString(heading),
                12, 0xFF9FA3B2, true));
        for (CommunityChannel channel : channels) {
            String details = channel.guildName + (channel.people >= 0 ? "  ·  " + channel.people : "");
            addCommunityRow(stablePrefix + channel.id, CommunityRowKind.CHANNEL,
                    "#  " + channel.name, details, channel);
        }
    }

    private void addCommunityStateLine(CommunityStatus status, String message, String fallback) {
        String value = message.isEmpty() ? fallback : message;
        int color = status == CommunityStatus.ERROR ? 0xFFFFB4AB
                : status == CommunityStatus.READY ? 0xFF69F0AE : 0xFFC5C8D3;
        discordVoiceContentContainer.addView(discordLine(value, 14, color, false));
    }

    private void addCommunityRow(String id, CommunityRowKind kind, String title, String details, Object source) {
        LinearLayout row = new LinearLayout(getContext());
        CommunityFriend friend = source instanceof CommunityFriend ? (CommunityFriend) source : null;
        VoiceParticipant participant = source instanceof VoiceParticipant ? (VoiceParticipant) source : null;
        boolean person = friend != null || participant != null;
        row.setOrientation(person ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        if (person) row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(7));
        row.setMinimumHeight(dp(48));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        String description = details.isEmpty() ? title : title + ". " + details;
        if (friend != null && friend.unread) description += ". "
                + getContext().getString(R.string.overlay_discord_unread);
        row.setContentDescription(description);
        if (person) {
            String avatarTitle = friend != null ? friend.name : participant.name;
            String avatarUrl = friend != null ? friend.avatarUrl : participant.avatarUrl;
            FrameLayout avatar = DiscordCommunityPresentation.avatar(getContext(), avatarTitle, avatarUrl, 34);
            if (friend != null && communityFriendShowsPresenceDot(friend.presence)) {
                addCommunityPresenceDot(avatar);
            }
            if (friend != null && friend.presence == FriendPresence.OFFLINE) {
                avatar.setAlpha(0.58f);
            }
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(34), dp(34));
            avatarParams.rightMargin = dp(10);
            row.addView(avatar, avatarParams);
        }
        LinearLayout copy = person ? new LinearLayout(getContext()) : row;
        if (person) copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(friend != null && friend.unread ? communityUnreadTitle(title) : title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(OverlayMenuButton.textColors(OverlayMenuButton.TEXT));
        name.setSingleLine(true);
        name.setDuplicateParentStateEnabled(true);
        titleRow.setDuplicateParentStateEnabled(true);
        if (person) copy.setDuplicateParentStateEnabled(true);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(titleRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (!details.isEmpty()) {
            TextView meta = new TextView(getContext());
            meta.setText(details);
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            meta.setTextColor(OverlayMenuButton.textColors(
                    friend != null && friend.presence == FriendPresence.OFFLINE
                    ? 0xFF777C89 : OverlayMenuButton.TEXT_SECONDARY));
            meta.setSingleLine(true);
            meta.setDuplicateParentStateEnabled(true);
            copy.addView(meta, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (person) row.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        CommunityRow item = new CommunityRow(id, kind, source, row);
        int index = communityRows.size();
        communityRows.add(item);
        row.setOnFocusChangeListener((ignored, focused) -> {
            styleCommunityRow(row, focused);
            if (focused) {
                communityFocus = CommunityFocus.LIST;
                communityListIndex = index;
                rememberCommunitySelection(item);
            }
        });
        row.setOnClickListener(ignored -> {
            communityInteractionGeneration++;
            openCommunityRow(item);
        });
        styleCommunityRow(row, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(4);
        discordVoiceContentContainer.addView(row, params);
    }

    private void addCommunityPresenceDot(FrameLayout avatar) {
        View dot = new View(getContext());
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xFF61E594);
        background.setStroke(dp(1), 0xFF10131C);
        dot.setBackground(background);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(8), dp(8),
                Gravity.RIGHT | Gravity.BOTTOM);
        avatar.addView(dot, params);
    }

    private CharSequence communityUnreadTitle(String title) {
        SpannableStringBuilder value = new SpannableStringBuilder(title).append("  ●");
        value.setSpan(new ForegroundColorSpan(0xFF5BCBFF), title.length() + 2, value.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    static boolean communityFriendShowsPresenceDot(FriendPresence presence) {
        return presence == FriendPresence.ONLINE || presence == FriendPresence.PLAYING;
    }

    static String communityFriendDetails(CommunityFriend friend) {
        return friend == null ? "" : friend.activity;
    }

    /** Rows rest on the panel and take the menu buttons' white focus with black ink. */
    private void styleCommunityRow(View row, boolean focused) {
        if (focused) {
            row.setBackground(OverlayMenuButton.surface(getContext(), true,
                    OverlayMenuButton.CORNER_RADIUS_DP));
            return;
        }
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(OverlayMenuButton.CORNER_RADIUS_DP));
        background.setColor(0x14FFFFFF);
        row.setBackground(background);
    }

    private void rememberCommunitySelection(CommunityRow item) {
        if (communitySection == CommunitySection.TOGETHER) selectedTogetherRowId = item.id;
        else if (communitySection == CommunitySection.FRIENDS) selectedFriendRowId = item.id;
        else if (communitySubmode == CommunitySubmode.GUILD_CHANNELS) {
            selectedGuildChannelRowId = item.id;
            selectedGuildChannelGuildId = selectedGuildId;
        } else selectedChannelsRootRowId = item.id;
        if (item.kind == CommunityRowKind.FRIEND) selectedFriendId = idPart(item.id);
        else if (item.kind == CommunityRowKind.GUILD) selectedGuildId = idPart(item.id);
        else if (item.kind == CommunityRowKind.CHANNEL) selectedChannelId = idPart(item.id);
    }

    private void openCommunityRow(CommunityRow row) {
        rememberCommunitySelection(row);
        if (actionListener == null) return;
        if (row.kind == CommunityRowKind.FRIEND) {
            if (!communityModel.directMessagesAvailable) {
                actionListener.onDiscordCommunityAuthorizeDirectMessages();
                return;
            }
            pendingChatFriendId = selectedFriendId;
            communitySubmode = CommunitySubmode.FRIEND_CHAT;
            communityFocus = CommunityFocus.CHAT_COMPOSER;
            renderDiscordCard();
            actionListener.onDiscordCommunityOpenFriendChat(selectedFriendId);
        } else if (row.kind == CommunityRowKind.GUILD) {
            pendingGuildId = selectedGuildId;
            communitySubmode = CommunitySubmode.GUILD_CHANNELS;
            renderDiscordCard();
            actionListener.onDiscordCommunityLoadGuild(selectedGuildId);
        } else if (row.kind == CommunityRowKind.CHANNEL && !selectedChannelId.isEmpty()) {
            actionListener.onDiscordCommunityJoinChannel(selectedChannelId);
        }
    }

    private void restoreCommunityRowSelection() {
        int index = communityRowIndex(selectedCommunityRowId());
        if (index >= 0) {
            communityListIndex = index;
            return;
        }
        if (!communityRows.isEmpty()) communityListIndex = 0;
    }

    private String selectedCommunityRowId() {
        if (communitySection == CommunitySection.TOGETHER) return selectedTogetherRowId;
        if (communitySection == CommunitySection.FRIENDS) return selectedFriendRowId;
        if (communitySubmode == CommunitySubmode.GUILD_CHANNELS) {
            return selectedGuildId.equals(selectedGuildChannelGuildId) ? selectedGuildChannelRowId : "";
        }
        return selectedChannelsRootRowId;
    }

    private int communityRowIndex(String id) {
        if (id == null || id.isEmpty()) return -1;
        for (int index = 0; index < communityRows.size(); index++) {
            if (id.equals(communityRows.get(index).id)) return index;
        }
        return -1;
    }

    private CommunityGuild findGuild(String id) {
        for (CommunityGuild guild : communityModel.guilds) if (guild.id.equals(id)) return guild;
        return null;
    }

    private static String idPart(String stableId) {
        int colon = stableId.indexOf(':');
        return colon < 0 ? stableId : stableId.substring(colon + 1);
    }

    private static final class CommunityRow {
        final String id;
        final CommunityRowKind kind;
        final Object source;
        final View view;
        CommunityRow(String id, CommunityRowKind kind, Object source, View view) {
            this.id = id; this.kind = kind; this.source = source; this.view = view;
        }
    }

    private void renderCommunityChat(long generation) {
        ChatModel chat = communityModel.chat;
        renderedChatStructureSignature = communityChatStructureSignature(chat);
        String recipientId = chat != null && !chat.recipientId.isEmpty()
                ? chat.recipientId : pendingChatFriendId;
        boolean sameRecipient = recipientId.equals(renderedChatRecipientId);
        int previousScroll = discordChatHistoryScroll == null ? 0 : discordChatHistoryScroll.getScrollY();
        View previousHistory = discordChatHistoryScroll == null ? null : discordChatHistoryScroll.getChildAt(0);
        int previousMaxScroll = previousHistory == null ? 0
                : Math.max(0, previousHistory.getHeight() - discordChatHistoryScroll.getHeight());
        boolean nearBottom = previousMaxScroll - previousScroll <= dp(48);
        String lastOutgoingMessageId = lastOutgoingMessageId(chat);
        boolean newOutgoingMessage = sameRecipient && !lastOutgoingMessageId.isEmpty()
                && !lastOutgoingMessageId.equals(renderedChatLastOutgoingMessageId);
        boolean autoScroll = shouldAutoScrollCommunityChat(sameRecipient, nearBottom, newOutgoingMessage);
        applyCommunityChatPanelSize();
        discordRail.setVisibility(GONE);
        discordQuickHeader.setVisibility(GONE);
        discordContentScroll.setVisibility(GONE);
        discordActionsContainer.setVisibility(GONE);
        discordQuickFooter.setVisibility(GONE);
        discordChatContainer.setVisibility(VISIBLE);
        discordChatContainer.removeAllViews();
        String recipient = chat != null && !chat.recipientName.isEmpty() ? chat.recipientName
                : selectedFriendName(pendingChatFriendId);
        TextView title = new TextView(getContext());
        title.setText(recipient.isEmpty() ? getContext().getString(R.string.discord_community_friends) : recipient);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        discordChatContainer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        discordChatHistoryScroll = new ScrollView(getContext());
        discordChatHistoryScroll.setFillViewport(true);
        discordChatHistoryScroll.setVerticalScrollBarEnabled(false);
        fadeOverflow(discordChatHistoryScroll);
        discordChatHistoryScroll.setFocusable(false);
        discordChatHistory = new LinearLayout(getContext());
        discordChatHistory.setOrientation(LinearLayout.VERTICAL);
        discordChatHistory.setMinimumHeight(dp(140));
        discordChatHistory.setPadding(0, dp(4), 0, dp(4));
        discordChatHistoryScroll.addView(discordChatHistory, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        renderCommunityChatHistory(chat);
        discordChatContainer.addView(discordChatHistoryScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        ensureCommunityChatComposer(chat);
        ensureCommunityChatStatus();
        bindCommunityChatStatus(chat);
        discordChatContainer.addView(discordChatStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        discordChatContainer.addView(discordChatComposer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        discordChatKeyboard.setCompactMode(true);
        discordChatContainer.addView(discordChatKeyboard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        renderedChatRecipientId = recipientId;
        renderedChatLastOutgoingMessageId = lastOutgoingMessageId;
        ScrollView renderedHistory = discordChatHistoryScroll;
        renderedHistory.post(() -> {
            if (overlayMode != OverlayMode.COMMUNITY
                    || communitySubmode != CommunitySubmode.FRIEND_CHAT
                    || communityRenderGeneration != generation
                    || renderedHistory != discordChatHistoryScroll) return;
            View history = renderedHistory.getChildAt(0);
            int maxScroll = history == null ? 0
                    : Math.max(0, history.getHeight() - renderedHistory.getHeight());
            if (autoScroll) renderedHistory.scrollTo(0, maxScroll);
            else renderedHistory.scrollTo(0, Math.min(previousScroll, maxScroll));
        });
    }

    private static String lastOutgoingMessageId(ChatModel chat) {
        if (chat == null) return "";
        for (int index = chat.messages.size() - 1; index >= 0; index--) {
            ChatMessage message = chat.messages.get(index);
            if (message.self && !message.disclosure) return message.id;
        }
        return "";
    }

    private void ensureCommunityChatStatus() {
        if (discordChatStatus != null) return;
        discordChatStatus = discordLine("", 11, 0xFFC5C8D3, false);
    }

    private void bindCommunityChatStatus(ChatModel chat) {
        ensureCommunityChatStatus();
        String signature = communityChatStatusSignature(chat);
        if (signature.equals(renderedChatStatusSignature)) return;
        renderedChatStatusSignature = signature;
        if (chat != null && chat.sending) {
            discordChatStatus.setText(R.string.discord_dm_sending);
            discordChatStatus.setTextColor(0xFFC5C8D3);
            discordChatStatus.setVisibility(VISIBLE);
            return;
        }
        if (chat != null && (!chat.error.isEmpty()
                || (chat.retryable && !chat.retryMessage.isEmpty()))) {
            discordChatStatus.setText(!chat.retryMessage.isEmpty() ? chat.retryMessage : chat.error);
            discordChatStatus.setTextColor(0xFFFFB4AB);
            discordChatStatus.setVisibility(VISIBLE);
            return;
        }
        discordChatStatus.setText("");
        discordChatStatus.setVisibility(GONE);
    }

    private String selectedFriendName(String id) {
        for (CommunityFriend friend : communityModel.friends) if (friend.id.equals(id)) return friend.name;
        return "";
    }

    private static String communityChatRecipientId(CommunityModel model) {
        return model == null || model.chat == null ? "" : model.chat.recipientId;
    }

    private void cancelCommunityChatKeyboardHold() {
        communityMotionDirection = KeyEvent.KEYCODE_UNKNOWN;
        if (discordChatKeyboard != null) discordChatKeyboard.cancelKeyboardHold();
    }

    private void renderCommunityChatHistory(ChatModel chat) {
        if (chat == null || chat.loadingHistory) {
            discordChatHistory.addView(discordLine(getContext().getString(R.string.overlay_discord_loading),
                    13, 0xFFC5C8D3, false));
            return;
        }
        if (chat.messages.isEmpty()) {
            discordChatHistory.addView(discordLine(getContext().getString(R.string.overlay_discord_chat_empty),
                    12, 0xFF9FA3B2, false));
            return;
        }
        for (ChatMessage message : chat.messages) {
            if (message.disclosure) {
                TextView notice = discordLine(getContext().getString(R.string.discord_dm_disclosure),
                        11, 0xFF9FA3B2, false);
                notice.setGravity(Gravity.CENTER);
                discordChatHistory.addView(notice);
                continue;
            }
            if (!message.content.isEmpty()) addCommunityChatBubble(message.content, message.self, false, message.id);
            if (!message.additionalContentType.isEmpty() || message.additionalContentCount > 0) {
                addCommunityChatBubble(additionalContentLabel(message), message.self, true, message.id);
            } else if (message.content.isEmpty()) {
                addCommunityChatBubble(getContext().getString(R.string.discord_dm_unsupported_content),
                        message.self, true, message.id);
            }
        }
    }

    private void addCommunityChatBubble(String value, boolean self, boolean media, String messageId) {
        TextView bubble = new TextView(getContext());
        bubble.setText(value);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, media ? 11 : 13);
        bubble.setTextColor(media ? OverlayMenuButton.TEXT_SECONDARY : OverlayMenuButton.TEXT);
        bubble.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(OverlayMenuButton.CORNER_RADIUS_DP));
        background.setColor(self ? 0x40FFFFFF : 0x1AFFFFFF);
        bubble.setBackground(background);
        if (media && !messageId.isEmpty()) {
            bubble.setFocusable(true);
            bubble.setClickable(true);
            bubble.setOnClickListener(ignored -> {
                if (actionListener != null) actionListener.onDiscordCommunityOpenMessageInDiscord(messageId);
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = self ? Gravity.RIGHT : Gravity.LEFT;
        params.topMargin = dp(3);
        discordChatHistory.addView(bubble, params);
    }

    private String additionalContentLabel(ChatMessage message) {
        int count = Math.max(1, message.additionalContentCount);
        int stringId;
        switch (message.additionalContentType) {
            case "Attachment": stringId = R.string.discord_dm_media_attachment; break;
            case "Poll": stringId = R.string.discord_dm_media_poll; break;
            case "VoiceMessage": stringId = R.string.discord_dm_media_voice_message; break;
            case "Thread": stringId = R.string.discord_dm_media_thread; break;
            case "Embed": stringId = R.string.discord_dm_media_embed; break;
            case "Sticker": stringId = R.string.discord_dm_media_sticker; break;
            default: stringId = R.string.discord_dm_media_other; break;
        }
        String label = getContext().getString(stringId, count);
        return message.additionalContentTitle.isEmpty() ? label : getContext().getString(
                R.string.discord_dm_media_titled, label, message.additionalContentTitle);
    }

    private void ensureCommunityChatComposer(ChatModel chat) {
        if (discordChatComposer == null) {
            discordChatComposer = new EditText(getContext());
            discordChatComposer.setTextColor(Color.WHITE);
            discordChatComposer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            discordChatComposer.setHintTextColor(0xFFA2A9BB);
            discordChatComposer.setHint(R.string.discord_dm_placeholder);
            discordChatComposer.setSingleLine(true);
            GradientDrawable composerBackground = new GradientDrawable();
            composerBackground.setCornerRadius(dp(OverlayMenuButton.CORNER_RADIUS_DP));
            composerBackground.setColor(0x1AFFFFFF);
            discordChatComposer.setBackground(composerBackground);
            discordChatComposer.setPadding(dp(12), 0, dp(12), 0);
            discordChatComposer.setShowSoftInputOnFocus(false);
            discordChatComposer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
            discordChatComposer.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                    if (!updatingChatComposer && actionListener != null && !pendingChatFriendId.isEmpty()) {
                        actionListener.onDiscordCommunityChatDraftChanged(pendingChatFriendId, value.toString());
                    }
                }
                @Override public void afterTextChanged(Editable value) { }
            });
        }
        String draft = chat == null ? "" : chat.draft;
        if (!draft.equals(renderedChatDraft)) {
            if (!draft.equals(discordChatComposer.getText().toString())) {
                updatingChatComposer = true;
                discordChatComposer.setText(draft);
                discordChatComposer.setSelection(draft.length());
                updatingChatComposer = false;
            }
            renderedChatDraft = draft;
        }
        if (discordChatKeyboard == null) {
            discordChatKeyboard = new EmbeddedTvKeyboardView(getContext(), new EmbeddedTvKeyboardView.Callback() {
                @Override public void onText(String value) { insertCommunityChatText(value); }
                @Override public void onBackspace() { deleteCommunityChatText(); }
                @Override public void onMoveCursor(int direction) { moveCommunityChatCursor(direction); }
                @Override public void onMicrophone() { }
                @Override public void onSend() { sendCommunityChat(); }
                @Override public void onOpenVisibleAdditionalContent() { openVisibleCommunityChatContent(); }
            }, getContext().getString(R.string.discord_dm_keyboard_microphone),
                    getContext().getString(R.string.discord_dm_keyboard_space),
                    getContext().getString(R.string.discord_dm_send),
                    getContext().getString(R.string.discord_dm_keyboard_shift_legend),
                    getContext().getString(R.string.discord_dm_keyboard_backspace_legend),
                    getContext().getString(R.string.discord_dm_keyboard_cursor_legend),
                    getContext().getString(R.string.discord_dm_keyboard_send_legend),
                    getContext().getString(R.string.discord_dm_keyboard_history_scroll_legend),
                    getContext().getString(R.string.discord_dm_keyboard_media_open_legend),
                    true);
        }
    }

    private void insertCommunityChatText(String value) {
        if (discordChatComposer == null) return;
        Editable editable = discordChatComposer.getText();
        int start = Math.max(0, discordChatComposer.getSelectionStart());
        int end = Math.max(start, discordChatComposer.getSelectionEnd());
        editable.replace(start, end, value == null ? "" : value);
        discordChatComposer.setSelection(Math.min(editable.length(), start + (value == null ? 0 : value.length())));
    }

    private void deleteCommunityChatText() {
        if (discordChatComposer == null) return;
        Editable editable = discordChatComposer.getText();
        int start = Math.max(0, discordChatComposer.getSelectionStart());
        int end = Math.max(start, discordChatComposer.getSelectionEnd());
        if (start != end) editable.delete(start, end);
        else if (start > 0) {
            int previous = Character.offsetByCodePoints(editable, start, -1);
            editable.delete(previous, start);
        }
    }

    private void moveCommunityChatCursor(int direction) {
        if (discordChatComposer == null) return;
        int current = Math.max(0, discordChatComposer.getSelectionStart());
        Editable editable = discordChatComposer.getText();
        int next = direction < 0 && current > 0 ? Character.offsetByCodePoints(editable, current, -1)
                : direction > 0 && current < editable.length() ? Character.offsetByCodePoints(editable, current, 1)
                : current;
        discordChatComposer.setSelection(next);
    }

    private void sendCommunityChat() {
        if (discordChatComposer == null || actionListener == null || pendingChatFriendId.isEmpty()) return;
        String draft = discordChatComposer.getText().toString();
        if (!draft.trim().isEmpty()) actionListener.onDiscordCommunitySendChat(pendingChatFriendId, draft);
    }

    private void openVisibleCommunityChatContent() {
        if (discordChatHistory == null || actionListener == null) return;
        int childCount = discordChatHistory.getChildCount();
        int[] tops = new int[childCount];
        int[] bottoms = new int[childCount];
        View[] candidates = new View[childCount];
        int candidateCount = 0;
        for (int index = 0; index < childCount; index++) {
            View child = discordChatHistory.getChildAt(index);
            if (!child.isFocusable() || !child.isClickable()) continue;
            android.graphics.Rect bounds = new android.graphics.Rect();
            child.getDrawingRect(bounds);
            discordChatHistoryScroll.offsetDescendantRectToMyCoords(child, bounds);
            tops[candidateCount] = bounds.top;
            bottoms[candidateCount] = bounds.bottom;
            candidates[candidateCount] = child;
            candidateCount++;
        }
        int selected = nearestVisibleCommunityMediaIndex(discordChatHistoryScroll.getScrollY(),
                discordChatHistoryScroll.getHeight(), tops, bottoms, candidateCount);
        if (selected >= 0) candidates[selected].performClick();
    }

    /** R3 must never open a stale CTA outside the currently visible chat viewport. */
    static int nearestVisibleCommunityMediaIndex(int scrollTop, int viewportHeight,
                                                  int[] tops, int[] bottoms, int count) {
        if (tops == null || bottoms == null || viewportHeight <= 0) return -1;
        int limit = Math.min(Math.min(tops.length, bottoms.length), Math.max(0, count));
        int scrollBottom = scrollTop + viewportHeight;
        int preferredCenter = scrollTop + Math.round(viewportHeight * .65f);
        int selected = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < limit; index++) {
            if (bottoms[index] <= scrollTop || tops[index] >= scrollBottom) continue;
            int center = tops[index] + (bottoms[index] - tops[index]) / 2;
            int distance = Math.abs(center - preferredCenter);
            if (distance < bestDistance) {
                selected = index;
                bestDistance = distance;
            }
        }
        return selected;
    }

    private void applyCommunityChatPanelSize() { applyCommunityPanelSize(COMMUNITY_CHAT_WIDTH_DP); }
    private void restoreCommunityPanelSize() { applyCommunityPanelSize(COMMUNITY_QUICK_WIDTH_DP); }

    private void applyCommunityPanelSize(int widthDp) {
        if (overlayMode != OverlayMode.COMMUNITY) return;
        View root = getRootView();
        int availableWidth = root == null ? 0 : root.getWidth() - dp(2 * PANEL_MARGIN_DP);
        setPanelWidth(communityPanelDimension(dp(widthDp + 2 * PANEL_PADDING_DP), availableWidth));
        if (root == null || root.getWidth() <= 0) post(() -> applyCommunityPanelSize(widthDp));
    }

    private void setPanelWidth(int width) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) panel.getLayoutParams();
        if (params.width == width) return;
        params.width = width;
        panel.setLayoutParams(params);
    }

    private void renderDiscordVoiceCard() {
        discordVoiceContentContainer.removeAllViews();
        if (discordQuickSection == 1) return;
        if (!discordConfigured) return;

        if (discordLoading && discordVoice == null) {
            discordVoiceContentContainer.addView(discordLine(
                    getContext().getString(R.string.overlay_discord_loading),
                    14, 0xFFC5C8D3, false));
            return;
        }
        if (discordError != null && !discordError.isEmpty()) {
            discordVoiceContentContainer.addView(discordLine(discordError, 13, 0xFFFFB4AB, false));
            return;
        }
        if (discordVoice == null || !discordVoice.connected) {
            discordVoiceContentContainer.addView(discordLine(
                    getContext().getString(R.string.overlay_discord_disconnected),
                    14, 0xFFC5C8D3, false));
            return;
        }

        String channel = discordVoice.channelName == null || discordVoice.channelName.isEmpty() ?
                "Voice" : discordVoice.channelName;
        discordVoiceContentContainer.addView(discordLine(getContext().getString(
                R.string.overlay_discord_channel, channel, discordVoice.participants.size()),
                15, Color.WHITE, true));

        int shown = 0;
        for (DiscordGatewayClient.Participant participant : discordVoice.participants) {
            if (shown >= 6) break;
            String name = participant.self ? getContext().getString(
                    R.string.overlay_discord_self, participant.name) : participant.name;
            if (participant.speaking) {
                name = getContext().getString(R.string.overlay_discord_participant_speaking, name);
            } else {
                name = getContext().getString(R.string.overlay_discord_participant, name);
            }
            if (participant.muted) {
                name += " · " + getContext().getString(R.string.overlay_discord_muted);
            } else if (!participant.self && participant.volume != 100) {
                name += " · " + participant.volume + "%";
            }
            discordVoiceContentContainer.addView(discordLine(name, 14,
                    participant.speaking ? 0xFF69F0AE : 0xFFE6E1E9, false));
            shown++;
        }
        if (discordVoice.participants.size() <= 1) {
            discordVoiceContentContainer.addView(discordLine(
                    getContext().getString(R.string.overlay_discord_empty),
                    12, 0xFF9FA3B2, false));
        } else if (discordVoice.participants.size() > shown) {
            discordVoiceContentContainer.addView(discordLine(
                    "+" + (discordVoice.participants.size() - shown),
                    12, 0xFF9FA3B2, false));
        }
    }

    private void renderDiscordSocialCard() {
        if (!discordSocialAvailable) {
            discordSocialContentContainer.removeAllViews();
            renderedDiscordSocialRevision = Long.MIN_VALUE;
            renderedDiscordSocialSection = -1;
            return;
        }
        if (renderedDiscordSocialRevision == discordSocialRevision
                && renderedDiscordSocialSection == discordQuickSection) return;
        discordSocialContentContainer.removeAllViews();
        renderedDiscordSocialRevision = discordSocialRevision;
        renderedDiscordSocialSection = discordQuickSection;
        if (discordQuickSection == 2) {
            discordSocialContentContainer.addView(discordLine(getContext().getString(
                    R.string.discord_community_host_unavailable), 13, 0xFF9FA3B2, false));
            return;
        }
        discordSocialContentContainer.addView(discordLine(getContext().getString(
                R.string.overlay_community_active), 13, 0xFF69F0AE, true));
        if (!discordSocialConnected) {
            discordSocialContentContainer.addView(discordLine(getContext().getString(
                    R.string.overlay_discord_social_connect_in_menu), 14, 0xFFC5C8D3, false));
            return;
        }
        discordSocialContentContainer.addView(discordLine(getContext().getString(
                R.string.overlay_discord_social_connected,
                discordSocialDisplayName.isEmpty() ? "Discord" : discordSocialDisplayName),
                14, Color.WHITE, false));
        if (!discordSocialExpanded && discordQuickSection != 1) return;
        int playing = 0;
        int online = 0;
        for (DiscordSocialClient.Friend friend : discordSocialFriends) {
            if (friend.group == DiscordSocialClient.Friend.Group.PLAYING) playing++;
            else if (friend.group == DiscordSocialClient.Friend.Group.ONLINE) online++;
        }
        if (playing == 0 && online == 0) {
            discordSocialContentContainer.addView(discordLine(getContext().getString(
                    R.string.overlay_discord_social_no_friends), 13, 0xFF9FA3B2, false));
            return;
        }
        int shown = addDiscordSocialFriends(DiscordSocialClient.Friend.Group.PLAYING,
                R.string.overlay_discord_social_playing, 3);
        addDiscordSocialFriends(DiscordSocialClient.Friend.Group.ONLINE,
                R.string.overlay_discord_social_online, 3 - shown);
    }

    private int addDiscordSocialFriends(DiscordSocialClient.Friend.Group group, int title, int max) {
        boolean headingAdded = false;
        int shown = 0;
        for (DiscordSocialClient.Friend friend : discordSocialFriends) {
            if (friend.group != group || shown >= max) continue;
            if (!headingAdded) {
                discordSocialContentContainer.addView(discordLine(getContext().getString(title),
                        12, 0xFF9FA3B2, true));
                headingAdded = true;
            }
            discordSocialContentContainer.addView(discordSocialFriendRow(friend));
            shown++;
        }
        return shown;
    }

    private TextView discordLine(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setSingleLine(true);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(4);
        view.setLayoutParams(params);
        return view;
    }

    private View discordSocialFriendRow(DiscordSocialClient.Friend friend) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setContentDescription(friend.activityName.isEmpty() ? friend.displayName
                : friend.displayName + ". " + friend.activityName);
        FrameLayout avatar = DiscordCommunityPresentation.avatar(getContext(), friend.displayName,
                friend.avatarUrl, 40);
        if (friend.group != DiscordSocialClient.Friend.Group.OFFLINE) {
            View dot = new View(getContext());
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0xFF61E594);
            dot.setBackground(background);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(9), dp(9),
                    Gravity.RIGHT | Gravity.BOTTOM);
            dotParams.rightMargin = dp(1);
            dotParams.bottomMargin = dp(1);
            avatar.addView(dot, dotParams);
        }
        row.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = discordLine(friend.displayName, 14, 0xFFE6E1E9, false);
        title.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        text.addView(title);
        String status = friend.activityName;
        if (status.isEmpty()) {
            status = friend.group == DiscordSocialClient.Friend.Group.PLAYING
                    ? getContext().getString(R.string.overlay_discord_social_playing)
                    : getContext().getString(R.string.overlay_discord_social_online);
        }
        TextView meta = discordLine(status, 12, 0xFF9FA3B2, false);
        meta.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        text.addView(meta);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(9);
        textParams.bottomMargin = dp(4);
        row.addView(text, textParams);
        return row;
    }

    private void updateDiscordActionButtons() {
        DiscordActionVoiceState actionVoice = discordActionVoiceState();
        boolean connected = actionVoice.connected;
        if (discordMuteButton != null) {
            discordMuteButton.setLabel(discordMuteLabel(actionVoice.muted));
            setDiscordShortcut(discordMuteButton, discordMuteShortcut);
            discordMuteButton.setAlpha(connected ? 1f : 0.45f);
        }
        if (discordLeaveButton != null) {
            discordLeaveButton.setLabel(discordLeaveLabel());
            setDiscordShortcut(discordLeaveButton, discordLeaveShortcut);
            discordLeaveButton.setAlpha(connected ? 1f : 0.45f);
        }
        if (discordRejoinButton != null) {
            discordRejoinButton.setLabel(discordRejoinLabel());
            discordRejoinButton.setAlpha(!connected && discordCanRejoin ? 1f : 0.45f);
        }
        if (discordDockButton != null) {
            discordDockButton.setLabel(discordDockLabel());
            discordDockButton.setAlpha(connected || discordDocked ? 1f : 0.6f);
        }
        if (discordSocialFriendsButton != null) {
            discordSocialFriendsButton.setLabel(discordSocialFriendsLabel());
            discordSocialFriendsButton.setAlpha(discordSocialConnected ? 1f : 0.45f);
        }
    }

    private String discordSocialFriendsLabel() {
        return getContext().getString(socialActionLabel(discordSocialConnected, discordSocialExpanded));
    }

    static boolean shouldShowDiscordCard(boolean hostVoiceConfigured, boolean socialAvailable) {
        return hostVoiceConfigured || socialAvailable;
    }

    private boolean canShowDiscordCard() {
        return discordFeatureEnabled && shouldShowDiscordCard(discordConfigured,
                discordSocialAvailable);
    }

    static int socialActionLabel(boolean socialConnected, boolean socialExpanded) {
        if (!socialConnected) return R.string.overlay_discord_social_connect_in_menu;
        return socialExpanded ? R.string.overlay_discord_social_hide_friends
                : R.string.overlay_discord_social_friends;
    }

    private String discordMuteLabel(boolean muted) {
        return getContext().getString(muted
                ? R.string.overlay_discord_unmute : R.string.overlay_discord_mute);
    }

    private DiscordActionVoiceState discordActionVoiceState() {
        return discordActionVoiceState(overlayMode, communityModel,
                discordVoice != null && discordVoice.connected,
                discordVoice != null && discordVoice.muted,
                discordVoice != null && discordVoice.deafened);
    }

    static DiscordActionVoiceState discordActionVoiceState(OverlayMode mode, CommunityModel model,
                                                            boolean legacyConnected,
                                                            boolean legacyMuted,
                                                            boolean legacyDeafened) {
        if (mode == OverlayMode.COMMUNITY && model != null) {
            VoiceSummary voice = model.voice;
            return voice == null
                    ? new DiscordActionVoiceState(false, false, false)
                    : new DiscordActionVoiceState(voice.connected, voice.muted, voice.deafened);
        }
        return new DiscordActionVoiceState(legacyConnected, legacyMuted, legacyDeafened);
    }

    static final class DiscordActionVoiceState {
        final boolean connected;
        final boolean muted;
        final boolean deafened;

        DiscordActionVoiceState(boolean connected, boolean muted, boolean deafened) {
            this.connected = connected;
            this.muted = muted;
            this.deafened = deafened;
        }
    }

    private String discordLeaveLabel() {
        return getContext().getString(R.string.overlay_discord_leave);
    }

    private String discordRejoinLabel() {
        if (discordRejoinChannel.isEmpty()) {
            return getContext().getString(R.string.overlay_discord_rejoin);
        }
        return getContext().getString(R.string.overlay_discord_rejoin_channel,
                discordRejoinChannel);
    }

    private String discordDockLabel() {
        return getContext().getString(discordDocked
                ? R.string.overlay_discord_undock : R.string.overlay_discord_dock);
    }

    /** Guide chords show their button as the same controller glyph the menu buttons use. */
    private void setDiscordShortcut(OverlayMenuButton button, String shortcut) {
        ControllerGlyphs.Button glyph = discordShortcutGlyph(shortcut);
        if (glyph == null) {
            button.clearShortcut();
            return;
        }
        button.setGuideChordShortcut(ControllerGlyphs.chord(getContext(),
                getContext().getString(R.string.discord_shortcut_guide), playStationButtons,
                glyph));
    }

    private static String normalizeDiscordShortcut(String value) {
        if ("x".equals(value) || "y".equals(value) || "l1".equals(value) ||
                "r1".equals(value) || "l3".equals(value) || "r3".equals(value)) {
            return value;
        }
        return "none";
    }

    public static ControllerGlyphs.Button discordShortcutGlyph(String value) {
        switch (normalizeDiscordShortcut(value)) {
            case "x": return ControllerGlyphs.Button.WEST;
            case "y": return ControllerGlyphs.Button.NORTH;
            case "l1": return ControllerGlyphs.Button.LEFT_BUMPER;
            case "r1": return ControllerGlyphs.Button.RIGHT_BUMPER;
            case "l3": return ControllerGlyphs.Button.LEFT_STICK;
            case "r3": return ControllerGlyphs.Button.RIGHT_STICK;
            default: return null;
        }
    }

    private static int discordShortcutKeyCode(String value) {
        switch (normalizeDiscordShortcut(value)) {
            case "x": return KeyEvent.KEYCODE_BUTTON_X;
            case "y": return KeyEvent.KEYCODE_BUTTON_Y;
            case "l1": return KeyEvent.KEYCODE_BUTTON_L1;
            case "r1": return KeyEvent.KEYCODE_BUTTON_R1;
            case "l3": return KeyEvent.KEYCODE_BUTTON_THUMBL;
            case "r3": return KeyEvent.KEYCODE_BUTTON_THUMBR;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    static final class GuideShortcutLatch {
        static final int NONE = 0;
        static final int CONSUMED = 1;
        static final int MUTE = 2;
        static final int LEAVE = 3;
        private boolean guideDown;
        private boolean triggered;

        int handle(int action, int rawKeyCode, int repeatCount,
                   int muteKeyCode, int leaveKeyCode) {
            if (rawKeyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                    guideDown = true;
                    triggered = false;
                } else if (action == KeyEvent.ACTION_UP) {
                    reset();
                }
                return CONSUMED;
            }
            if (!guideDown || rawKeyCode == KeyEvent.KEYCODE_UNKNOWN
                    || (rawKeyCode != muteKeyCode && rawKeyCode != leaveKeyCode)) {
                return NONE;
            }
            if (action == KeyEvent.ACTION_DOWN && repeatCount == 0 && !triggered) {
                triggered = true;
                return rawKeyCode == muteKeyCode ? MUTE : LEAVE;
            }
            return CONSUMED;
        }

        void reset() {
            guideDown = false;
            triggered = false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    private int clampBitrate(int bitrateKbps) {
        return Math.max(BITRATE_MIN_KBPS, Math.min(BITRATE_MAX_KBPS, bitrateKbps));
    }


    /**
     * Every key the overlay sees comes through here, whether the framework sends it to the
     * focused overlay or Game routes it while another view holds focus. A press whose down the
     * overlay handled stays the overlay's until its up, even after the overlay closes.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        long key = KeyLatch.sequence(event.getDeviceId(), event.getKeyCode());
        if (event.getAction() == KeyEvent.ACTION_UP || event.isCanceled()) {
            ownedKeys.release(key);
            releaseNav(event.getKeyCode(), NAV_KEY);
        }
        boolean handled = dispatchOverlayKey(event);
        if (handled && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            ownedKeys.retain(key);
        }
        return handled;
    }

    /** Game, while the overlay is hidden: the rest (repeats, up) of a press the overlay began. */
    public boolean consumeOwnedKey(KeyEvent event) {
        return ownedKeys.consume(KeyLatch.sequence(event.getDeviceId(), event.getKeyCode()),
                event.getAction() == KeyEvent.ACTION_UP || event.isCanceled());
    }

    static final class KeyLatch {
        private final Set<Long> sequences = new HashSet<>();

        static long sequence(int deviceId, int keyCode) {
            return ((long) deviceId << 32) ^ (keyCode & 0xffffffffL);
        }

        void retain(long sequence) {
            sequences.add(sequence);
        }

        void release(long sequence) {
            sequences.remove(sequence);
        }

        boolean consume(long sequence, boolean terminalUp) {
            if (!sequences.contains(sequence)) return false;
            if (terminalUp) sequences.remove(sequence);
            return true;
        }
    }

    private boolean dispatchOverlayKey(KeyEvent event) {
        DiscordActionVoiceState shortcutVoice = discordActionVoiceState();
        int chordAction = discordShortcutChord.handle(event.getAction(), event.getKeyCode(),
                event.getRepeatCount(), shortcutVoice.connected
                        ? discordShortcutKeyCode(discordMuteShortcut) : KeyEvent.KEYCODE_UNKNOWN,
                shortcutVoice.connected
                        ? discordShortcutKeyCode(discordLeaveShortcut) : KeyEvent.KEYCODE_UNKNOWN);
        if (chordAction != GuideShortcutLatch.NONE) {
            if (chordAction == GuideShortcutLatch.MUTE) activateDiscordMute();
            else if (chordAction == GuideShortcutLatch.LEAVE) activateDiscordLeave();
            return true;
        }
        int keyCode = flipFaceButtons ? handleFlipFaceButtons(event.getKeyCode()) : event.getKeyCode();
        boolean chatKeyboardVisible = discordChatKeyboard != null
                && discordChatKeyboard.getVisibility() == VISIBLE
                && discordChatContainer != null && discordChatContainer.getVisibility() == VISIBLE;
        if (shouldRouteCommunityChatKeyboard(overlayMode, communitySubmode, chatKeyboardVisible)
                && shouldForwardCommunityChatKeyAction(event.getAction())) {
            KeyEvent translated = keyCode == event.getKeyCode() ? event
                    : new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), keyCode,
                    event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                    event.getFlags(), event.getSource());
            if ((keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK)
                    && discordChatKeyboard.handleAccentCancel(translated)) return true;
            if (discordChatKeyboard.handleNavigationKey(translated)) {
                communityFocus = CommunityFocus.CHAT_KEYBOARD;
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean consumed = communityCancelKeyDown;
                communityCancelKeyDown = false;
                return consumed || isGamepadEvent(event) || keyCode == KeyEvent.KEYCODE_BACK;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (communityCancelDownHasEffect(communityCancelKeyDown, event.getRepeatCount())) {
                    communityCancelKeyDown = true;
                    if (keyCode != KeyEvent.KEYCODE_BACK || !isFullGamepadEvent(event)) back();
                }
                return true;
            }
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (overlayMode == OverlayMode.COMMUNITY) {
                if (isCommunityNavigationKey(keyCode) || isGamepadEvent(event)) {
                    communityInteractionGeneration++;
                }
                if (communitySubmode == CommunitySubmode.FRIEND_CHAT) {
                    return dispatchCommunityChatKey(keyCode, event);
                }
                syncCommunityFocusFromFocusedView();
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    lastCommunityDirectionalKeyAt = event.getEventTime();
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
                    returnToMenu();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) return true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (communityFocus == CommunityFocus.ACTION) {
                        int targetRegion = communityActionLeftRegion(discordIndex,
                                !communityRows.isEmpty());
                        if (targetRegion == COMMUNITY_REGION_ACTION) {
                            setDiscordIndex(discordIndex - 1);
                            return true;
                        }
                        focusCommunityListOrRail();
                        return true;
                    }
                    if (communityFocus == CommunityFocus.LIST) {
                        focusCommunityRail();
                        return true;
                    }
                    focusCommunityRail();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (communityFocus == CommunityFocus.ACTION) {
                        int next = communityActionTarget(discordIndex, keyCode,
                                discordButtons.size());
                        if (next >= 0) setDiscordIndex(next);
                    } else if (communityFocus == CommunityFocus.LIST
                            && communityRegionTarget(COMMUNITY_REGION_CONTENT, keyCode,
                            !discordButtons.isEmpty()) == COMMUNITY_REGION_ACTION) {
                        communityFocus = CommunityFocus.ACTION;
                        setDiscordIndex(0);
                    } else if (communityFocus == CommunityFocus.RAIL
                            && communityRailRightRegion(!communityRows.isEmpty(),
                            !discordButtons.isEmpty()) == COMMUNITY_REGION_CONTENT) {
                        focusCommunityList();
                    } else if (communityFocus == CommunityFocus.RAIL
                            && communityRailRightRegion(!communityRows.isEmpty(),
                            !discordButtons.isEmpty()) == COMMUNITY_REGION_ACTION) {
                        communityFocus = CommunityFocus.ACTION;
                        setDiscordIndex(0);
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (communityFocus == CommunityFocus.RAIL) {
                        int direction = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                        int next = nextCommunityRailIndex(discordQuickSection, direction,
                                discordRailIcons.size());
                        if (next != discordQuickSection) selectDiscordQuickSection(next);
                        if (!discordRailIcons.isEmpty()) discordRailIcons.get(next).requestFocus();
                    } else if (communityFocus == CommunityFocus.LIST) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                                && communityListDownEntersActions(communityListIndex,
                                communityRows.size(), !discordButtons.isEmpty())) {
                            communityFocus = CommunityFocus.ACTION;
                            setDiscordIndex(0);
                        } else moveCommunityList(keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                    } else if (!discordButtons.isEmpty()) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                                && communityActionUpEntersList(discordIndex, !communityRows.isEmpty())) {
                            communityFocus = CommunityFocus.LIST;
                            setCommunityListIndex(communityRows.size() - 1);
                        } else setDiscordIndex(communityActionTarget(discordIndex, keyCode,
                                discordButtons.size()));
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (communityFocus == CommunityFocus.ACTION) activateSelected();
                    else if (communityFocus == CommunityFocus.LIST && communityListIndex >= 0
                            && communityListIndex < communityRows.size()) {
                        communityRows.get(communityListIndex).view.performClick();
                    } else selectDiscordQuickSection(discordQuickSection);
                    return true;
                }
                return isGamepadEvent(event) || isCommunityNavigationKey(keyCode);
            }

            // A prompt is answered with A or Back only: no shortcut may close or replace it.
            if (prompt != null && keyCode != KeyEvent.KEYCODE_DPAD_UP
                    && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                    && keyCode != KeyEvent.KEYCODE_BUTTON_A
                    && keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                    && keyCode != KeyEvent.KEYCODE_ENTER) {
                return isGamepadEvent(event) || super.dispatchKeyEvent(event);
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // The overlay's own repeat drives a held key, not the system's slow one.
                    if (event.getRepeatCount() == 0) pressNav(keyCode, NAV_KEY);
                    return true;

                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    activateSelected();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_X:
                    if (event.getRepeatCount() == 0) activateMouseEmulation();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_Y:
                    if (event.getRepeatCount() == 0) activateKeyboard();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_START:
                case KeyEvent.KEYCODE_MENU:
                    if (event.getRepeatCount() == 0) activateGuideButton();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_L2:
                    if (event.getRepeatCount() == 0) activateToggleStats();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_R2:
                    if (event.getRepeatCount() == 0) openMore();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (event.getRepeatCount() == 0) openCommunity();
                    return true;
            }
        }

        if (isGamepadEvent(event)
                || (overlayMode == OverlayMode.COMMUNITY
                && isCommunityNavigationKey(event.getKeyCode()))) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void focusCommunityList() {
        if (communityRows.isEmpty()) return;
        communityFocus = CommunityFocus.LIST;
        activeRegion = Region.DISCORD;
        setCommunityListIndex(Math.max(0, communityListIndex));
    }

    private void syncCommunityFocusFromFocusedView() {
        View focused = findFocus();
        if (focused == null) return;
        if (discordRailIcons.contains(focused)) {
            communityFocus = CommunityFocus.RAIL;
            activeRegion = Region.DISCORD;
            return;
        }
        for (int index = 0; index < communityRows.size(); index++) {
            if (communityRows.get(index).view == focused) {
                communityFocus = CommunityFocus.LIST;
                communityListIndex = index;
                activeRegion = Region.DISCORD;
                return;
            }
        }
        int actionIndex = discordButtons.indexOf(focused);
        if (actionIndex >= 0) {
            communityFocus = CommunityFocus.ACTION;
            discordIndex = actionIndex;
            activeRegion = Region.DISCORD;
        }
    }

    private void focusCommunityListOrRail() {
        if (communityRows.isEmpty()) focusCommunityRail();
        else focusCommunityList();
    }

    private void focusCommunityRail() {
        communityFocus = CommunityFocus.RAIL;
        activeRegion = Region.DISCORD;
        if (!discordRailIcons.isEmpty()) discordRailIcons.get(discordQuickSection).requestFocus();
    }

    private void setCommunityListIndex(int index) {
        if (communityRows.isEmpty()) return;
        communityListIndex = Math.max(0, Math.min(communityRows.size() - 1, index));
        View row = communityRows.get(communityListIndex).view;
        row.requestFocus();
        revealCommunityRow(row);
    }

    private void moveCommunityList(int direction) {
        if (communityRows.isEmpty()) return;
        setCommunityListIndex(Math.max(0, Math.min(communityRows.size() - 1,
                communityListIndex + direction)));
    }

    private void revealCommunityRow(View row) {
        if (row == null || discordContentScroll == null) return;
        row.post(() -> {
            android.graphics.Rect bounds = new android.graphics.Rect();
            row.getDrawingRect(bounds);
            discordContentScroll.offsetDescendantRectToMyCoords(row, bounds);
            int top = discordContentScroll.getScrollY();
            int bottom = top + discordContentScroll.getHeight();
            if (bounds.top < top) discordContentScroll.smoothScrollTo(0, bounds.top);
            else if (bounds.bottom > bottom) discordContentScroll.smoothScrollTo(0,
                    Math.max(0, bounds.bottom - discordContentScroll.getHeight()));
        });
    }

    private void backCommunity() {
        if (communitySubmode == CommunitySubmode.FRIEND_CHAT) {
            cancelCommunityChatKeyboardHold();
            communitySubmode = CommunitySubmode.ROOT;
            communitySection = CommunitySection.FRIENDS;
            discordQuickSection = 1;
            pendingChatFriendId = "";
            communityFocus = CommunityFocus.LIST;
            restoreCommunityPanelSize();
            renderDiscordCard();
            if (actionListener != null) actionListener.onDiscordCommunityBackToFriends();
            post(this::focusCommunityList);
            return;
        }
        if (communityBackNotifiesChannels(communitySubmode)) {
            communitySubmode = CommunitySubmode.ROOT;
            pendingGuildId = "";
            communityFocus = CommunityFocus.LIST;
            renderDiscordCard();
            if (actionListener != null) actionListener.onDiscordCommunityBackToChannels();
            post(this::focusCommunityList);
            return;
        }
        returnToMenu();
    }

    private boolean dispatchCommunityChatKey(int keyCode, KeyEvent source) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
            backCommunity();
            return true;
        }
        return isGamepadEvent(source) || isCommunityNavigationKey(keyCode);
    }

    /**
     * The quick Community panel has one content focus lane. Keep its vertical movement local
     * instead of letting Android search through the hidden overlay menu containers.
     */
    private void navigateCommunity(int direction) {
        if (discordButtons.isEmpty()) return;
        if (activeRegion != Region.DISCORD) {
            setDiscordIndex(0);
            return;
        }
        setDiscordIndex(Math.max(0, Math.min(discordButtons.size() - 1,
                discordIndex + direction)));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!isFullGamepadEvent(event) && event.getRepeatCount() == 0) back();
            return true;
        }

        if (isGamepadEvent(event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (overlayMode == OverlayMode.COMMUNITY) {
            boolean handled = handleCommunityMotion(event);
            return handled || isGamepadMotionEvent(event) || super.onGenericMotionEvent(event);
        }
        if (isGamepadMotionEvent(event)) {
            handleTriggers(event);
            float x = event.getAxisValue(MotionEvent.AXIS_X);
            float y = event.getAxisValue(MotionEvent.AXIS_Y);
            float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

            float combinedX = Math.abs(x) > Math.abs(hatX) ? x : hatX;
            float combinedY = Math.abs(y) > Math.abs(hatY) ? y : hatY;

            // A HAT (the DualSense D-pad on Android TV) reports only its changes: the press
            // edge steps at once and the held direction repeats until it is let go.
            int direction = communityMotionDirection(combinedX, combinedY,
                    motionNavKey == KeyEvent.KEYCODE_UNKNOWN
                            ? ANALOG_STICK_THRESHOLD : ANALOG_STICK_RELEASE);
            if (direction != motionNavKey) {
                releaseNav(motionNavKey, NAV_MOTION);
                motionNavKey = direction;
                if (direction != KeyEvent.KEYCODE_UNKNOWN) pressNav(direction, NAV_MOTION);
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    /** Pads that report L2/R2 only as axes reach Stats and Shortcuts on the press edge. */
    private void handleTriggers(MotionEvent event) {
        boolean left = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE)) > TRIGGER_THRESHOLD;
        boolean right = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS)) > TRIGGER_THRESHOLD;
        // A prompt is answered with A or Back only.
        if (left && !leftTriggerDown && prompt == null) activateToggleStats();
        if (right && !rightTriggerDown && prompt == null) openMore();
        leftTriggerDown = left;
        rightTriggerDown = right;
    }

    /** DualSense D-pad is HAT motion on Android TV; send its edges through the Community key reducer. */
    private boolean handleCommunityMotion(MotionEvent event) {
        if (!isGamepadMotionEvent(event) || event.getAction() != MotionEvent.ACTION_MOVE) return false;
        boolean rightStickHandled = handleCommunityRightStickScroll(event);
        InputDevice device = event.getDevice();
        boolean hasHat = device != null && (device.getMotionRange(MotionEvent.AXIS_HAT_X) != null
                || device.getMotionRange(MotionEvent.AXIS_HAT_Y) != null);
        float horizontal = hasHat ? event.getAxisValue(MotionEvent.AXIS_HAT_X)
                : event.getAxisValue(MotionEvent.AXIS_X);
        float vertical = hasHat ? event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                : event.getAxisValue(MotionEvent.AXIS_Y);
        int direction = communityMotionDirection(horizontal, vertical, hasHat ? .45f : .85f);
        boolean chatKeyboardVisible = discordChatKeyboard != null
                && discordChatKeyboard.getVisibility() == VISIBLE
                && discordChatContainer != null && discordChatContainer.getVisibility() == VISIBLE;
        if (shouldRouteCommunityChatKeyboard(overlayMode, communitySubmode, chatKeyboardVisible)) {
            communityMotionDirection = KeyEvent.KEYCODE_UNKNOWN;
            return discordChatKeyboard.updateDirectionalHat(direction) || rightStickHandled;
        }
        if (rightStickHandled) return true;
        if (direction == KeyEvent.KEYCODE_UNKNOWN) {
            boolean consumed = communityMotionDirection != KeyEvent.KEYCODE_UNKNOWN;
            communityMotionDirection = KeyEvent.KEYCODE_UNKNOWN;
            return consumed;
        }
        if (direction == communityMotionDirection) return true;
        communityMotionDirection = direction;
        if (Math.abs(event.getEventTime() - lastCommunityDirectionalKeyAt) < 80L) return true;
        return dispatchOverlayKey(new KeyEvent(KeyEvent.ACTION_DOWN, direction));
    }

    private boolean handleCommunityRightStickScroll(MotionEvent event) {
        InputDevice device = event.getDevice();
        int axis = device == null ? -1 : communityRightStickVerticalAxis(
                hasCommunityAxis(device, MotionEvent.AXIS_RX), hasCommunityAxis(device, MotionEvent.AXIS_RY),
                hasCommunityAxis(device, MotionEvent.AXIS_Z), hasCommunityAxis(device, MotionEvent.AXIS_RZ));
        if (axis < 0) return false;
        float value = event.getAxisValue(axis);
        if (Math.abs(value) < .5f) return false;
        long now = event.getEventTime();
        if (now - lastCommunityScrollTime < ANALOG_NAV_THROTTLE_MS) return true;
        ScrollView target = communitySubmode == CommunitySubmode.FRIEND_CHAT
                ? discordChatHistoryScroll : discordContentScroll;
        if (target == null) return true;
        int direction = value > 0 ? 1 : -1;
        if (target.canScrollVertically(direction)) {
            target.smoothScrollBy(0, direction * dp(communitySubmode == CommunitySubmode.FRIEND_CHAT
                    ? COMMUNITY_HISTORY_SCROLL_DP : COMMUNITY_LIST_SCROLL_DP));
            lastCommunityScrollTime = now;
        }
        return true;
    }

    static int communityRightStickVerticalAxis(boolean hasRx, boolean hasRy, boolean hasZ, boolean hasRz) {
        if (hasRx && hasRy) return MotionEvent.AXIS_RY;
        return hasZ && hasRz ? MotionEvent.AXIS_RZ : -1;
    }

    private static boolean hasCommunityAxis(InputDevice device, int axis) {
        return device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null
                || device.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD) != null;
    }

    static int communityMotionDirection(float horizontal, float vertical, float threshold) {
        float absX = Math.abs(horizontal);
        float absY = Math.abs(vertical);
        if (Math.max(absX, absY) < threshold) return KeyEvent.KEYCODE_UNKNOWN;
        if (absX > absY) return horizontal < 0f ? KeyEvent.KEYCODE_DPAD_LEFT
                : KeyEvent.KEYCODE_DPAD_RIGHT;
        return vertical < 0f ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private boolean isGamepadEvent(KeyEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
               (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
               (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    private boolean isFullGamepadEvent(KeyEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
               (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private int handleFlipFaceButtons(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return KeyEvent.KEYCODE_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_B: return KeyEvent.KEYCODE_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_X: return KeyEvent.KEYCODE_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_Y: return KeyEvent.KEYCODE_BUTTON_X;
            default: return keyCode;
        }
    }

    private boolean isGamepadMotionEvent(MotionEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private void back() {
        if (overlayMode == OverlayMode.COMMUNITY) backCommunity();
        else if (prompt != null) {
            // A B press meant for the game must not discard the prompt either.
            if (SystemClock.uptimeMillis() >= promptActionsAtMs) finishPrompt(PROMPT_DISMISSED);
        }
        else if (autopilotOpen && autopilotVariantsOpen) closeAutopilotVariants();
        else if (autopilotOpen) closeAutopilot();
        else if (usbOpen && usbConsent != null) showUsbList(null);
        else if (usbOpen) closeUsb();
        else if (gyroOpen) closeGyro();
        else if (streamOpen) closeStream();
        else if (moreOpen) closeMore();
        else closeMenu();
    }

    /**
     * One step now, then a fast repeat while held. A pad that reports one press both as a key
     * and as HAT motion moves once: the second source of the held direction is ignored.
     */
    private void pressNav(int keyCode, int source) {
        if (keyCode == heldNavKey && source != heldNavSource) return;
        releaseNav();
        heldNavKey = keyCode;
        heldNavSource = source;
        moveMenu(keyCode);
        boolean horizontal = keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!horizontal || (menuIndex >= 0 && menuIndex < menuActions.size()
                && repeatsHorizontally(menuActions.get(menuIndex)))) {
            postDelayed(navRepeat, NAV_REPEAT_DELAY_MS);
        }
    }

    private void releaseNav(int keyCode, int source) {
        if (keyCode == heldNavKey && source == heldNavSource) releaseNav();
    }

    private void releaseNav() {
        heldNavKey = KeyEvent.KEYCODE_UNKNOWN;
        removeCallbacks(navRepeat);
    }

    private void moveMenu(int keyCode) {
        if (menuButtons.isEmpty()) return;
        int action = menuActions.get(Math.max(0, Math.min(menuIndex, menuActions.size() - 1)));
        if (isStreamOption(action) && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            changeStream(action, keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return;
        }
        if (isGyroOption(action)
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            changeGyro(action, keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return;
        }
        if (action == ACTION_AUDIO_HAPTICS
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            changeAudioHaptics(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return;
        }
        if (action == ACTION_AUDIO_HAPTICS_STRENGTH
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            changeAudioHapticsStrength(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return;
        }
        setMenuIndex(menuTarget(menuIndex, keyCode, menuButtons.size(), menuPairStart));
    }

    /**
     * Linear focus through the panel without wrapping. "Back to game" and Home share a row at
     * {@code pairStart}: Left/Right move inside it, Down leaves it, Up from below lands on it.
     */
    static int menuTarget(int current, int keyCode, int count, int pairStart) {
        if (count <= 0) return current;
        boolean inPair = pairStart >= 0 && (current == pairStart || current == pairStart + 1);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return inPair ? pairStart : current;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return inPair && pairStart + 1 < count ? pairStart + 1 : current;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (inPair) return Math.max(0, pairStart - 1 < 0 ? current : pairStart - 1);
                if (pairStart >= 0 && current == pairStart + 2) return pairStart;
                return Math.max(0, current - 1);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (inPair) return Math.min(count - 1, pairStart + 2);
                return Math.min(count - 1, current + 1);
            default:
                return current;
        }
    }

    /**
     * FromTouch: a touch or a touchscreen-like pad touchpad puts the window in touch mode, where
     * the buttons cannot take focus and HAT/stick motion never leaves it; the pad does here.
     */
    private void setMenuIndex(int index) {
        if (index < 0 || index >= menuButtons.size()) return;
        menuIndex = index;
        activeRegion = Region.MENU;
        menuButtons.get(index).requestFocusFromTouch();
    }

    private void setDiscordIndex(int index) {
        if (index < 0 || index >= discordButtons.size()) return;
        for (OverlayMenuButton button : discordButtons) button.setSelected(false);
        discordIndex = index;
        discordButtons.get(index).setSelected(true);
        discordButtons.get(index).requestFocusFromTouch();
    }

    private void selectAndActivate(Region region, int index) {
        activeRegion = region;
        if (region == Region.MENU) setMenuIndex(index);
        else setDiscordIndex(index);
        activateSelected();
    }

    private void activateSelected() {
        int action;
        if (activeRegion == Region.MENU) {
            if (menuIndex < 0 || menuIndex >= menuActions.size()) return;
            action = menuActions.get(menuIndex);
        } else {
            if (discordIndex < 0 || discordIndex >= discordActions.size()) return;
            action = discordActions.get(discordIndex);
        }

        if (action == ACTION_MORE) {
            openMore();
            return;
        } else if (action == ACTION_USB_DEVICES) {
            openUsb();
            return;
        } else if (action == ACTION_USB_SHARE) {
            if (actionListener != null && usbConsent != null) {
                actionListener.onUsbDeviceChosen(usbConsent, true);
            }
            showUsbList(null);
            return;
        } else if (action == ACTION_USB_CANCEL) {
            showUsbList(null);
            return;
        } else if (action >= ACTION_USB_BASE && action < ACTION_USB_BASE + usbRows.size()) {
            UsbRow row = usbRows.get(action - ACTION_USB_BASE);
            if (!row.running) {
                showUsbList(row.deviceName);
            } else if (actionListener != null) {
                actionListener.onUsbDeviceChosen(row.deviceName, false);
            }
            return;
        } else if (action == ACTION_GYRO) {
            openGyro();
            return;
        } else if (action == ACTION_GYRO_CALIBRATE) {
            if (actionListener != null) actionListener.onCalibrateGyro();
            closeMenu();
            return;
        } else if (isGyroOption(action)) {
            changeGyro(action, 1);
            return;
        } else if (action == ACTION_AUDIO_HAPTICS) {
            changeAudioHaptics(1);
            return;
        } else if (action == ACTION_AUDIO_HAPTICS_STRENGTH) {
            changeAudioHapticsStrength(1);
            return;
        } else if (action == ACTION_STREAM_SETTINGS) {
            openStream();
            return;
        } else if (isStreamOption(action)) {
            changeStream(action, 1);
            return;
        } else if (action == ACTION_STREAM_APPLY) {
            if (!pendingStream.equals(currentStream) && actionListener != null) {
                actionListener.onApplyStreamSettings(pendingStream);
            }
            return;
        } else if (action == ACTION_EMERGENCY_STREAM) {
            if (actionListener != null) actionListener.onEmergencyStream(emergencyStream);
            return;
        } else if (action == ACTION_LEAVE_EMERGENCY_STREAM) {
            if (actionListener != null) actionListener.onLeaveEmergencyStream();
            return;
        } else if (action == ACTION_AUTOPILOT) {
            openAutopilot();
            return;
        } else if (action == ACTION_AUTOPILOT_TEST) {
            if (autopilotTest != AutopilotTest.UNAVAILABLE && actionListener != null) {
                actionListener.onAutopilotTest();
            }
            return;
        } else if (action == ACTION_AUTOPILOT_ADVICE) {
            if (actionListener != null) actionListener.onAutopilotApplyAdvice();
            return;
        } else if (action == ACTION_AUTOPILOT_GYRO) {
            if (actionListener != null) actionListener.onAutopilotGyroTrial();
            return;
        } else if (action == ACTION_AUTOPILOT_VARIANTS) {
            openAutopilotVariants();
            return;
        } else if (action >= ACTION_AUTOPILOT_VARIANT_BASE
                && action < ACTION_AUTOPILOT_VARIANT_BASE + autopilotVariants.size()) {
            if (actionListener != null) {
                actionListener.onAutopilotVariant(action - ACTION_AUTOPILOT_VARIANT_BASE);
            }
            return;
        } else if (prompt != null && action >= ACTION_PROMPT_BASE
                && action < ACTION_PROMPT_BASE + prompt.options.size()) {
            if (SystemClock.uptimeMillis() < promptActionsAtMs) return;
            finishPrompt(prompt.options.get(action - ACTION_PROMPT_BASE).id);
            return;
        }

        boolean shouldCloseMenu = false;

        if (actionListener != null) {
            if (action == ACTION_HOME) {
                actionListener.onHome();
                shouldCloseMenu = true;
            } else if (action == ACTION_END_GAME) {
                actionListener.onEndGame();
                shouldCloseMenu = true;
            } else if (action == ACTION_FREEZE_GAME) {
                actionListener.onFreezeGame();
                shouldCloseMenu = true;
            } else if (action == ACTION_SCREENSHOT || action == ACTION_SAVE_CLIP) {
                actionListener.onCapture(action == ACTION_SAVE_CLIP);
                shouldCloseMenu = true;
            } else if (action == ACTION_QUIT) {
                actionListener.onQuitSession();
                shouldCloseMenu = true;
            } else if (action == ACTION_SUSPEND_SESSION) {
                actionListener.onSuspendSession();
                shouldCloseMenu = true;
            } else if (action == ACTION_TOGGLE_STATS) {
                actionListener.onToggleStats();
                // Keep menu open
            } else if (action == ACTION_TOGGLE_MOUSE_EMULATION) {
                actionListener.onToggleMouseEmulation();
                shouldCloseMenu = true;
            } else if (action == ACTION_SHOW_KEYBOARD) {
                activateKeyboard();
                return;
            } else if (action == ACTION_SEND_GUIDE) {
                actionListener.onSendGuideButton();
                shouldCloseMenu = true;
            } else if (action == ACTION_MARK_MOMENT) {
                actionListener.onMarkMoment();
                // Keep menu open
            } else if (action == ACTION_REPORT_PROBLEM) {
                closeMenu();
                actionListener.onReportProblem();
                return;
            } else if (action == ACTION_DISCORD_MUTE) {
                activateDiscordMute();
                return;
            } else if (action == ACTION_DISCORD_LEAVE) {
                activateDiscordLeave();
                return;
            } else if (action == ACTION_DISCORD_REJOIN) {
                if (discordCanRejoin) actionListener.onDiscordRejoin();
                return;
            } else if (action == ACTION_DISCORD_DOCK) {
                actionListener.onDiscordDockToggle();
                return;
            } else if (action == ACTION_INSTALLATION_CONFIRMED) {
                actionListener.onInstallationConfirmed();
                shouldCloseMenu = true;
            } else if (action == ACTION_CLOSE) {
                closeMenu();
                return;
            } else if (action >= ACTION_CUSTOM_BASE) {
                int commandIndex = action - ACTION_CUSTOM_BASE;
                List<CustomCommand> commands = commandsManager.getCommands();
                if (commandIndex >= 0 && commandIndex < commands.size()) {
                    actionListener.onCustomCommand(commands.get(commandIndex));
                }
                // Keep menu open
            }
        }

        if (shouldCloseMenu) {
            closeMenu();
        }
    }

    private void activateDiscordMute() {
        if (actionListener != null) actionListener.onDiscordMute();
    }

    private void activateDiscordLeave() {
        if (actionListener != null) actionListener.onDiscordLeave();
    }

    public void closeMenu() {
        discordShortcutChord.reset();
        cancelCommunityChatKeyboardHold();
        overlayMode = OverlayMode.MENU;
        moreOpen = false;
        usbOpen = false;
        usbConsent = null;
        gyroOpen = false;
        streamOpen = false;
        autopilotOpen = false;
        autopilotVariantsOpen = false;
        Prompt unanswered = prompt;
        prompt = null;
        communityMotionDirection = KeyEvent.KEYCODE_UNKNOWN;
        invalidateCommunityProjectionCache();
        applyOverlayMode();
        hide(() -> {
            if (actionListener != null) {
                actionListener.onMenuClosed();
                // A prompt closed without a choice is dismissed.
                if (unanswered != null) actionListener.onPromptChoice(PROMPT_DISMISSED);
            }
        });
    }

    private void activateMouseEmulation() {
        if (actionListener != null) {
            actionListener.onToggleMouseEmulation();
        }
        closeMenu();
    }

    private void activateKeyboard() {
        // Hide first, then post the keyboard request so the window manager has a frame
        // to process the visibility change and return focus to the game view before the
        // IME is requested. Without this, newer Android silently drops showSoftInput()
        // because the overlay view still holds focus at the moment of the call.
        setVisibility(GONE);
        post(() -> {
            if (actionListener != null) {
                actionListener.onShowKeyboard();
            }
        });
    }

    private void activateGuideButton() {
        if (actionListener != null) {
            actionListener.onSendGuideButton();
        }
        closeMenu();
    }

    private void activateToggleStats() {
        if (actionListener != null) {
            actionListener.onToggleStats();
        }
        closeMenu();
    }

    public void show() {
        discordShortcutChord.reset();
        communityCancelKeyDown = false;
        invalidateCommunityProjectionCache();
        buildMenu();
        setVisibility(VISIBLE);

        invalidate();
        requestLayout();

        requestFocus();
        // A trigger held as the menu opens (gyro aim on L2) is not a press: wait for a release.
        leftTriggerDown = true;
        rightTriggerDown = true;
        motionNavKey = KeyEvent.KEYCODE_UNKNOWN;
        releaseNav();
        // The install card's button leads when present; otherwise "Back to game".
        post(() -> setMenuIndex(0));
    }

    public void hide(Runnable onComplete) {
        releaseNav();
        setVisibility(GONE);
        if (onComplete != null) {
            onComplete.run();
        }
    }
}
