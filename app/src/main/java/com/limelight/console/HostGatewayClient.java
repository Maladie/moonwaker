package com.limelight.console;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.limelight.gateway.GatewayConnection;
import com.limelight.gateway.GatewayTransport;
import com.limelight.gateway.ControlOperationClient;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class HostGatewayClient {
    static final int DEFAULT_PORT = 8785;
    static final int REQUIRED_GAMEPLAY_PERMISSIONS = 0x07001F00;
    /** Also host entry's profile fetch, which falls back to the cached list on a slow Gateway. */
    static final int READ_TIMEOUT_MS = 5_000;
    private static final int PROFILES_READ_TIMEOUT_MS = 15_000;
    private static final int MANAGEMENT_TIMEOUT_MS = 15_000;
    static final int SUSPEND_SESSION_TIMEOUT_MS = 75_000;
    private static final long MAX_OBSERVATION_WALL_SKEW_MS = 365L * 24L * 60L * 60L * 1_000L;
    private static final int NETWORK_PROBE_BYTES = 8 * 1024 * 1024;
    private static final long NETWORK_TARGET_NANOS = 8_000_000_000L;
    private final GatewayTransport transport = new GatewayTransport();

    static final class Pairing {
        final GatewayConnection connection;
        final String clientId;
        final String streamPairTicket;
        final IntegrationProfiles profiles;

        Pairing(GatewayConnection connection, String clientId, String streamPairTicket,
                IntegrationProfiles profiles) {
            this.connection = connection;
            this.clientId = clientId;
            this.streamPairTicket = streamPairTicket == null ? "" : streamPairTicket;
            this.profiles = profiles;
        }
    }

    static final class Capabilities {
        final boolean vibepolloFix;
        final boolean discord;
        final boolean playnite;
        final boolean childProfiles;
        final boolean controlOperations;
        final boolean sessionSuspend;
        final boolean usbForwarding;
        /** The host has a Host Plus licence (F2); the host alone enforces it. */
        final boolean hostPlus;
        /** The host can save screenshots and clips (#51, Host Plus): ffmpeg and its helper. */
        final boolean captures;
        /** Adult-profile first-run facts; null for child profiles or an older Gateway. */
        final SetupFacts setup;

        Capabilities(boolean vibepolloFix, boolean discord,
                     boolean playnite, boolean childProfiles,
                     boolean controlOperations, boolean sessionSuspend, boolean usbForwarding,
                     boolean hostPlus, boolean captures, SetupFacts setup) {
            this.hostPlus = hostPlus;
            this.captures = captures;
            this.setup = setup;
            this.vibepolloFix = vibepolloFix;
            this.discord = discord;
            this.playnite = playnite;
            this.childProfiles = childProfiles;
            this.controlOperations = controlOperations;
            this.sessionSuspend = sessionSuspend;
            this.usbForwarding = usbForwarding;
        }
    }

    /** Host setup facts; a null Boolean means the host could not tell. Never secrets. */
    static final class SetupFacts {
        static final String DISCORD_LINKED = "linked";
        static final String DISCORD_NOT_LINKED = "not_linked";
        static final String DISCORD_UNKNOWN = "unknown";

        final Boolean steamApiKeySaved;
        final Boolean steamIdAvailable;
        final Boolean epicSignedIn;
        final String epicDisplayName;
        final String discordState;

        SetupFacts(Boolean steamApiKeySaved, Boolean steamIdAvailable, Boolean epicSignedIn,
                   String epicDisplayName, String discordState) {
            this.steamApiKeySaved = steamApiKeySaved;
            this.steamIdAvailable = steamIdAvailable;
            this.epicSignedIn = epicSignedIn;
            this.epicDisplayName = epicDisplayName;
            this.discordState = discordState;
        }

        static SetupFacts parse(JSONObject setup) {
            if (setup == null) return null;
            JSONObject steam = setup.optJSONObject("steam");
            JSONObject epic = setup.optJSONObject("epic");
            JSONObject discord = setup.optJSONObject("discord");
            String state = discord == null ? "" : discord.optString("state", "");
            return new SetupFacts(
                    tristate(steam, "api_key_saved"), tristate(steam, "steam_id_available"),
                    tristate(epic, "signed_in"),
                    epic == null ? "" : epic.optString("display_name", ""),
                    DISCORD_LINKED.equals(state) || DISCORD_NOT_LINKED.equals(state)
                            ? state : DISCORD_UNKNOWN);
        }

        private static Boolean tristate(JSONObject object, String name) {
            return object != null && object.opt(name) instanceof Boolean
                    ? (Boolean) object.opt(name) : null;
        }
    }

    static final class VibepolloIdentityChallenge {
        final String id;
        final String challenge;
        final int expiresInSeconds;

        VibepolloIdentityChallenge(String id, String challenge, int expiresInSeconds) {
            this.id = id;
            this.challenge = challenge;
            this.expiresInSeconds = Math.max(1, expiresInSeconds);
        }
    }

    private static final String IDENTITY_CHALLENGE_PREFIX =
            "moonwaker-vibepollo-identity-v1\n";

    static final class IntegrationProfile {
        final String id;
        final String name;
        final boolean useProfile;
        final boolean remoteSignIn;
        final String sessionState;
        final String remoteSignInState;
        final boolean pinRequired;
        final boolean discordBridgeOnline;
        final boolean discordRpcConnected;
        final boolean discordAuthenticated;
        final boolean vibepolloBridgeOnline;
        final boolean playniteBridgeOnline;
        final boolean playniteConnectorConnected;
        final boolean manageChildren;
        final int ownChildrenCount;
        /** Public profile metadata returned by the child-aware /profiles endpoint. */
        final String kind;
        final String parentProfileId;
        final String executionProfileId;
        final String avatarId;
        final boolean enabled;
        final long policyRevision;
        final long usageRevision;
        final long remainingDailySeconds;
        final long playableNowSeconds;
        final String nextAllowedAt;
        final String windowEnd;
        final String serverTime;
        final String reason;

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline) {
            this(id, name, discordBridgeOnline, discordRpcConnected,
                    discordAuthenticated, vibepolloBridgeOnline, false, false,
                    true, false, "unknown", "unavailable");
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline) {
            this(id, name, discordBridgeOnline, discordRpcConnected,
                    discordAuthenticated, vibepolloBridgeOnline, playniteBridgeOnline,
                    false, true, false,
                    "unknown", "unavailable");
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline,
                           boolean playniteConnectorConnected) {
            this(id, name, discordBridgeOnline, discordRpcConnected,
                    discordAuthenticated, vibepolloBridgeOnline, playniteBridgeOnline,
                    playniteConnectorConnected, true, false,
                    "unknown", "unavailable");
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline,
                           boolean playniteConnectorConnected,
                           boolean useProfile,
                           boolean remoteSignIn, String sessionState,
                           String remoteSignInState) {
            this(id, name, discordBridgeOnline, discordRpcConnected,
                    discordAuthenticated, vibepolloBridgeOnline, playniteBridgeOnline,
                    playniteConnectorConnected, useProfile,
                    remoteSignIn, sessionState, remoteSignInState, false, false);
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline,
                           boolean playniteConnectorConnected,
                           boolean useProfile,
                           boolean remoteSignIn, String sessionState,
                           String remoteSignInState, boolean pinRequired) {
            this(id, name, discordBridgeOnline, discordRpcConnected,
                    discordAuthenticated, vibepolloBridgeOnline, playniteBridgeOnline,
                    playniteConnectorConnected, useProfile,
                    remoteSignIn, sessionState, remoteSignInState, pinRequired, false);
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline,
                           boolean playniteConnectorConnected,
                           boolean useProfile,
                           boolean remoteSignIn, String sessionState,
                           String remoteSignInState, boolean pinRequired,
                           boolean manageChildren) {
            this(id, name, discordBridgeOnline, discordRpcConnected,
                    discordAuthenticated, vibepolloBridgeOnline, playniteBridgeOnline,
                    playniteConnectorConnected, useProfile,
                    remoteSignIn, sessionState, remoteSignInState, pinRequired,
                    manageChildren, 0);
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline,
                           boolean playniteConnectorConnected,
                           boolean useProfile,
                           boolean remoteSignIn, String sessionState,
                           String remoteSignInState, boolean pinRequired,
                           boolean manageChildren, int ownChildrenCount) {
            this(id, name, discordBridgeOnline, discordRpcConnected, discordAuthenticated,
                    vibepolloBridgeOnline, playniteBridgeOnline, playniteConnectorConnected,
                    useProfile, remoteSignIn, sessionState,
                    remoteSignInState, pinRequired, manageChildren, ownChildrenCount,
                    "standard", "", id, "", true, 0L, 0L, 0L, 0L,
                    "", "", "");
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline,
                           boolean playniteConnectorConnected,
                           boolean useProfile,
                           boolean remoteSignIn, String sessionState,
                           String remoteSignInState, boolean pinRequired,
                           boolean manageChildren, int ownChildrenCount,
                           String kind, String parentProfileId,
                           String executionProfileId, String avatarId, boolean enabled,
                           long policyRevision, long usageRevision,
                           long remainingDailySeconds, long playableNowSeconds,
                           String nextAllowedAt, String serverTime, String reason) {
            this(id, name, discordBridgeOnline, discordRpcConnected, discordAuthenticated,
                    vibepolloBridgeOnline, playniteBridgeOnline, playniteConnectorConnected,
                    useProfile, remoteSignIn, sessionState,
                    remoteSignInState, pinRequired, manageChildren, ownChildrenCount,
                    kind, parentProfileId, executionProfileId, avatarId, enabled,
                    policyRevision, usageRevision, remainingDailySeconds, playableNowSeconds,
                    nextAllowedAt, serverTime, reason, "");
        }

        IntegrationProfile(String id, String name, boolean discordBridgeOnline,
                           boolean discordRpcConnected, boolean discordAuthenticated,
                           boolean vibepolloBridgeOnline, boolean playniteBridgeOnline,
                           boolean playniteConnectorConnected,
                           boolean useProfile,
                           boolean remoteSignIn, String sessionState,
                           String remoteSignInState, boolean pinRequired,
                           boolean manageChildren, int ownChildrenCount,
                           String kind, String parentProfileId,
                           String executionProfileId, String avatarId, boolean enabled,
                           long policyRevision, long usageRevision,
                           long remainingDailySeconds, long playableNowSeconds,
                           String nextAllowedAt, String serverTime, String reason,
                           String windowEnd) {
            this.id = id;
            this.name = name;
            this.useProfile = useProfile;
            this.remoteSignIn = remoteSignIn;
            this.pinRequired = pinRequired;
            this.sessionState = normalizedState(sessionState, "unknown");
            this.remoteSignInState = normalizedState(remoteSignInState, "unavailable");
            this.discordBridgeOnline = discordBridgeOnline;
            this.discordRpcConnected = discordRpcConnected;
            this.discordAuthenticated = discordAuthenticated;
            this.vibepolloBridgeOnline = vibepolloBridgeOnline;
            this.playniteBridgeOnline = playniteBridgeOnline;
            this.playniteConnectorConnected = playniteConnectorConnected;
            this.manageChildren = manageChildren;
            this.ownChildrenCount = Math.max(0, ownChildrenCount);
            this.kind = "child".equalsIgnoreCase(kind == null ? "" : kind.trim())
                    ? "child" : "standard";
            this.parentProfileId = parentProfileId == null ? "" : parentProfileId.trim();
            String execution = executionProfileId == null ? "" : executionProfileId.trim();
            this.executionProfileId = execution.isEmpty() ? this.id : execution;
            this.avatarId = avatarId == null ? "" : avatarId.trim();
            this.enabled = enabled;
            this.policyRevision = Math.max(0L, policyRevision);
            this.usageRevision = Math.max(0L, usageRevision);
            this.remainingDailySeconds = Math.max(0L, remainingDailySeconds);
            this.playableNowSeconds = Math.max(0L, playableNowSeconds);
            this.nextAllowedAt = nextAllowedAt == null ? "" : nextAllowedAt.trim();
            this.windowEnd = windowEnd == null ? "" : windowEnd.trim();
            this.serverTime = serverTime == null ? "" : serverTime.trim();
            this.reason = reason == null ? "" : reason.trim();
        }

        boolean isChild() {
            return "child".equals(kind);
        }
    }

    static final class PlayniteGame {
        final String id;
        final String name;
        final boolean installed;
        final boolean installing;
        final boolean hidden;
        final boolean favorite;
        final String cover;
        final String background;
        final String hero;
        final String lastPlayed;
        final String description;
        final int playCount;
        final String source;
        final String provider;
        final String providerGameId;
        final String playniteGameId;
        final String libraryKey;
        final String libraryName;
        final boolean canLaunch;
        final boolean canInstall;
        final boolean canUninstall;
        final boolean requiresConnector;
        final boolean startBeforeStream;
        final String genres;
        final String artworkVersion;
        final boolean installRequiresAttention;
        final String installAttentionReason;
        final String installWindowTitle;
        final String installLauncher;
        final String operationState;
        final int operationProgress;
        final boolean uninstalling;
        /** {@code updateState}/{@code updateProgress}: the provider's (Steam, Epic) pending update, "" / -1 if none. */
        String updateState = "";
        int updateProgress = -1;
        /** {@code ageRating} of a parent library record; null when unrated. */
        GameAgeRating ageRating;
        final long playtimeSeconds;
        /** Compatibility view for older console code. New code uses seconds. */
        final long playtimeMinutes;

        PlayniteGame(String id, String name, boolean installed, boolean favorite,
                     String cover, String background, String lastPlayed,
                     long playtimeMinutes) {
            this(id, name, installed, false, favorite, cover, background, lastPlayed,
                    "", 0, "", "", Math.max(0L, playtimeMinutes) * 60L);
        }

        PlayniteGame(String id, String name, boolean installed, boolean hidden,
                     boolean favorite, String cover, String background, String lastPlayed,
                     String source, String artworkVersion, long playtimeSeconds) {
            this(id, name, installed, hidden, favorite, cover, background, lastPlayed,
                    "", 0, source, artworkVersion, playtimeSeconds);
        }

        PlayniteGame(String id, String name, boolean installed, boolean hidden,
                     boolean favorite, String cover, String background, String lastPlayed,
                     String description, String source, String artworkVersion,
                     long playtimeSeconds) {
            this(id, name, installed, hidden, favorite, cover, background, lastPlayed,
                    description, 0, source, artworkVersion, playtimeSeconds);
        }

        PlayniteGame(String id, String name, boolean installed, boolean hidden,
                     boolean favorite, String cover, String background, String lastPlayed,
                     String description, int playCount, String source, String artworkVersion,
                     long playtimeSeconds) {
            this(id, name, installed, false, hidden, favorite, cover, background, lastPlayed,
                    description, playCount, source, artworkVersion, playtimeSeconds);
        }

        PlayniteGame(String id, String name, boolean installed, boolean installing,
                     boolean hidden, boolean favorite, String cover, String background,
                     String lastPlayed, String description, int playCount, String source,
                     String artworkVersion, long playtimeSeconds) {
            this(id, name, installed, installing, hidden, favorite, cover, background,
                    lastPlayed, description, playCount, source, "", artworkVersion,
                    playtimeSeconds);
        }

        PlayniteGame(String id, String name, boolean installed, boolean installing,
                     boolean hidden, boolean favorite, String cover, String background,
                     String lastPlayed, String description, int playCount, String source,
                     String genres, String artworkVersion, long playtimeSeconds) {
            this(id, name, installed, installing, hidden, favorite, cover, background,
                    lastPlayed, description, playCount, source, genres, artworkVersion,
                    playtimeSeconds, false, "", "", "");
        }

        PlayniteGame(String id, String name, boolean installed, boolean installing,
                     boolean hidden, boolean favorite, String cover, String background,
                     String lastPlayed, String description, int playCount, String source,
                     String genres, String artworkVersion, long playtimeSeconds,
                     boolean installRequiresAttention, String installAttentionReason,
                     String installWindowTitle, String installLauncher) {
            this(id, name, installed, installing, hidden, favorite, cover, background,
                    lastPlayed, description, playCount, source, genres, artworkVersion,
                    playtimeSeconds, installRequiresAttention, installAttentionReason,
                    installWindowTitle, installLauncher, "", -1, false);
        }

        PlayniteGame(String id, String name, boolean installed, boolean installing,
                     boolean hidden, boolean favorite, String cover, String background,
                     String lastPlayed, String description, int playCount, String source,
                     String genres, String artworkVersion, long playtimeSeconds,
                     boolean installRequiresAttention, String installAttentionReason,
                     String installWindowTitle, String installLauncher,
                     String operationState, int operationProgress, boolean uninstalling) {
            this(id, name, installed, installing, hidden, favorite, cover, background,
                    lastPlayed, description, playCount, source, genres, artworkVersion,
                    playtimeSeconds, installRequiresAttention, installAttentionReason,
                    installWindowTitle, installLauncher, operationState, operationProgress,
                    uninstalling, providerFrom(source), "",
                    isGuid(id) ? id : "", sourceKey(source),
                    source == null || source.trim().isEmpty() ? "Playnite" : source,
                    true, true, true, isGuid(id), false);
        }

        PlayniteGame(String id, String name, boolean installed, boolean installing,
                     boolean hidden, boolean favorite, String cover, String background,
                     String lastPlayed, String description, int playCount, String source,
                     String genres, String artworkVersion, long playtimeSeconds,
                     boolean installRequiresAttention, String installAttentionReason,
                     String installWindowTitle, String installLauncher,
                     String operationState, int operationProgress, boolean uninstalling,
                     String provider, String providerGameId,
                     String playniteGameId, String libraryKey, String libraryName,
                     boolean canLaunch, boolean canInstall, boolean canUninstall,
                     boolean requiresConnector, boolean startBeforeStream) {
            this(id, name, installed, installing, hidden, favorite, cover, background,
                    "", lastPlayed, description, playCount, source, genres,
                    artworkVersion, playtimeSeconds, installRequiresAttention,
                    installAttentionReason, installWindowTitle, installLauncher,
                    operationState, operationProgress, uninstalling,
                    provider, providerGameId, playniteGameId, libraryKey, libraryName,
                    canLaunch, canInstall, canUninstall, requiresConnector,
                    startBeforeStream);
        }

        PlayniteGame(String id, String name, boolean installed, boolean installing,
                     boolean hidden, boolean favorite, String cover, String background,
                     String hero, String lastPlayed, String description, int playCount,
                     String source, String genres, String artworkVersion,
                     long playtimeSeconds, boolean installRequiresAttention,
                     String installAttentionReason, String installWindowTitle,
                     String installLauncher, String operationState, int operationProgress,
                     boolean uninstalling, String provider,
                     String providerGameId, String playniteGameId, String libraryKey,
                     String libraryName, boolean canLaunch, boolean canInstall,
                     boolean canUninstall, boolean requiresConnector,
                     boolean startBeforeStream) {
            this.id = id;
            this.name = name;
            this.installed = installed;
            this.installing = installing;
            this.hidden = hidden;
            this.favorite = favorite;
            this.cover = cover;
            this.background = background;
            this.hero = hero == null ? "" : hero;
            this.lastPlayed = lastPlayed;
            this.description = description;
            this.playCount = Math.max(0, playCount);
            this.source = source;
            this.provider = provider == null ? "" : provider;
            this.providerGameId = providerGameId == null ? "" : providerGameId;
            this.playniteGameId = playniteGameId == null ? "" : playniteGameId;
            this.libraryKey = libraryKey == null ? "" : libraryKey;
            this.libraryName = libraryName == null ? "" : libraryName;
            this.canLaunch = canLaunch;
            this.canInstall = canInstall;
            this.canUninstall = canUninstall;
            this.requiresConnector = requiresConnector;
            this.startBeforeStream = startBeforeStream;
            this.genres = genres;
            this.artworkVersion = artworkVersion;
            this.installRequiresAttention = installRequiresAttention;
            this.installAttentionReason = installAttentionReason;
            this.installWindowTitle = installWindowTitle == null ? "" : installWindowTitle;
            this.installLauncher = installLauncher == null ? "" : installLauncher;
            this.operationState = operationState == null ? "" : operationState;
            this.operationProgress = operationProgress;
            this.uninstalling = uninstalling;
            this.playtimeSeconds = Math.max(0L, playtimeSeconds);
            this.playtimeMinutes = this.playtimeSeconds / 60L;
        }

        private static boolean isGuid(String value) {
            return value != null && PLAYNITE_GUID.matcher(value).matches();
        }

        private static String providerFrom(String source) {
            String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
            return PROVIDER_NAME.matcher(value).matches() ? value : "playnite";
        }

        private static String sourceKey(String source) {
            String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
            return value.isEmpty() ? "playnite" : value;
        }
    }

    static final class PlayniteLibrary {
        final List<PlayniteGame> games;
        final String nextCursor;
        final int total;
        final String revision;
        final String apiVersion;

        PlayniteLibrary(List<PlayniteGame> games, String nextCursor, int total) {
            this(games, nextCursor, total, "", "");
        }

        PlayniteLibrary(List<PlayniteGame> games, String nextCursor, int total,
                        String revision, String apiVersion) {
            this.games = Collections.unmodifiableList(games);
            this.nextCursor = nextCursor;
            this.total = total;
            this.revision = revision == null ? "" : revision;
            this.apiVersion = apiVersion == null ? "" : apiVersion;
        }
    }

    public enum Evidence { VERIFIED, ABSENT, PENDING, UNAVAILABLE }

    public static final class SessionObservation {
        final String producerBootId;
        final long producerRevision;
        final long observedAtUnixMs;
        final long freshForMs;
        final String stateEpisode;
        final long stateSinceUnixMs;
        final String actorProfileId;
        final String executionProfileId;
        final long requestOrdinal;
        final String operationId;
        final String streamSessionId;
        final long controlGeneration;
        final long actorLeaseEpoch;
        final String providerState;
        final Evidence connectorEvidence;
        final String gameState;
        final Evidence gameEvidence;
        final String gameId;
        final Evidence processEvidence;
        final int processId;
        final String processToken;
        final Evidence windowEvidence;
        final boolean windowReady;
        final String targetKind;
        final int stableSamples;
        final String reason;
        final boolean hostGuideAllowed;
        final boolean requiresConnector;
        final boolean startBeforeStream;
        /** {@code window.previous_launch}: a stale earlier launch's verified game process. */
        String previousLaunchGameId = "";
        String previousLaunchProcessToken = "";
        /** Optional {@code window.previous_launch.name}: display text only, never an identity. */
        String previousLaunchGameName = "";
        /** {@code window.update.progress} of a game update (0-100), -1 without byte counts. */
        int updateProgress = -1;
        /** {@code window.update.phase} is "patching" (Steam stages and commits the files). */
        boolean updatePatching;
        /**
         * Host wall clock (same clock as {@link #observedAtUnixMs}) starts, display only:
         * {@code correlation.stream_started_unix_ms} of this actor's stream and
         * {@code process.started_unix_ms} of the verified game process; 0 when unknown.
         */
        long streamStartedUnixMs;
        long processStartedUnixMs;
        /**
         * {@code streamer}: the Sunshine/Vibepollo app the host's streamer runs (the app id
         * clients launch), 0 when it runs none, {@link SessionSnapshot#STREAMER_APP_UNKNOWN}
         * when the host could not tell. Host-global, not scoped to the actor.
         */
        int streamerAppId = SessionSnapshot.STREAMER_APP_UNKNOWN;
        /** {@code frozen}: the Quick Resume slot's game as this actor may see it; null: empty. */
        SessionSnapshot.FrozenGame frozen;

        SessionObservation(String producerBootId, long producerRevision,
                           long observedAtUnixMs, long freshForMs,
                           String stateEpisode, long stateSinceUnixMs,
                           String actorProfileId, String executionProfileId,
                           long requestOrdinal,
                           String operationId, String streamSessionId,
                           long controlGeneration, long actorLeaseEpoch,
                           String providerState, Evidence connectorEvidence,
                           String gameState, Evidence gameEvidence, String gameId,
                           Evidence processEvidence, int processId, String processToken,
                           Evidence windowEvidence, boolean windowReady,
                           String targetKind, int stableSamples, String reason,
                           boolean hostGuideAllowed, boolean requiresConnector,
                           boolean startBeforeStream) {
            this.producerBootId = producerBootId;
            this.producerRevision = producerRevision;
            this.observedAtUnixMs = observedAtUnixMs;
            this.freshForMs = freshForMs;
            this.stateEpisode = stateEpisode;
            this.stateSinceUnixMs = stateSinceUnixMs;
            this.actorProfileId = actorProfileId;
            this.executionProfileId = executionProfileId;
            this.requestOrdinal = requestOrdinal;
            this.operationId = operationId;
            this.streamSessionId = streamSessionId;
            this.controlGeneration = controlGeneration;
            this.actorLeaseEpoch = actorLeaseEpoch;
            this.providerState = providerState;
            this.connectorEvidence = connectorEvidence;
            this.gameState = gameState;
            this.gameEvidence = gameEvidence;
            this.gameId = gameId;
            this.processEvidence = processEvidence;
            this.processId = processId;
            this.processToken = processToken;
            this.windowEvidence = windowEvidence;
            this.windowReady = windowReady;
            this.targetKind = targetKind;
            this.stableSamples = stableSamples;
            this.reason = reason;
            this.hostGuideAllowed = hostGuideAllowed;
            this.requiresConnector = requiresConnector;
            this.startBeforeStream = startBeforeStream;
        }
    }

    static final class PlayniteEvent {
        final long sequence;
        final String name;
        final String gameId;
        final String gameName;
        /** Why an operation failed (e.g. "not_enough_disk_space"), "" otherwise. */
        final String reason;

        PlayniteEvent(long sequence, String name, String gameId, String gameName,
                      String reason) {
            this.sequence = sequence;
            this.name = name;
            this.gameId = gameId;
            this.gameName = gameName == null ? "" : gameName.trim();
            this.reason = reason == null ? "" : reason.trim();
        }
    }

    static final class PlayniteEvents {
        final List<PlayniteEvent> events;
        final long latestSequence;

        PlayniteEvents(List<PlayniteEvent> events, long latestSequence) {
            this.events = Collections.unmodifiableList(events);
            this.latestSequence = latestSequence;
        }
    }

    static final class IntegrationProfiles {
        final List<IntegrationProfile> profiles;
        final String suggestedProfileId;

        IntegrationProfiles(List<IntegrationProfile> profiles, String suggestedProfileId) {
            this.profiles = Collections.unmodifiableList(profiles);
            this.suggestedProfileId = suggestedProfileId;
        }

        IntegrationProfile find(String profileId) {
            for (IntegrationProfile profile : profiles) {
                if (profile.id.equals(profileId)) return profile;
            }
            return null;
        }
    }

    static final class ChildManagementAuthorization {
        final String parentProfileId;
        final String authorizationId;
        final int expiresInSeconds;

        ChildManagementAuthorization(String parentProfileId, String authorizationId,
                                     int expiresInSeconds) {
            this.parentProfileId = parentProfileId == null ? "" : parentProfileId;
            this.authorizationId = authorizationId == null ? "" : authorizationId;
            this.expiresInSeconds = Math.max(1, expiresInSeconds);
        }
    }

    static final class ChildProfile {
        final String id;
        final String name;
        final String avatarId;
        final boolean enabled;
        final String parentProfileId;
        final int policyRevision;
        final List<ConsoleChildProfileEditor.Day> weekdays;
        /** {@link ChildGameAccess#AGE_LIMITS}; "manual" when the host sends none. */
        final String ageLimit;
        /** Canonical catalog ids ({@link ChildGameAccess#canonicalGameId}) shared by hand. */
        final Set<String> allowedGameIds;
        /** Canonical catalog ids hidden by hand although the age category lets them through. */
        final Set<String> blockedGameIds;
        final boolean gameMode;
        final boolean gameModeWindows;
        final boolean gameModeMute;

        ChildProfile(String id, String name, String avatarId, boolean enabled,
                     String parentProfileId, int policyRevision,
                     List<ConsoleChildProfileEditor.Day> weekdays, String ageLimit,
                     Collection<String> allowedGameIds, Collection<String> blockedGameIds,
                     boolean gameMode, boolean gameModeWindows, boolean gameModeMute) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.avatarId = avatarId == null ? "" : avatarId;
            this.enabled = enabled;
            this.parentProfileId = parentProfileId == null ? "" : parentProfileId;
            this.policyRevision = policyRevision;
            this.weekdays = Collections.unmodifiableList(new ArrayList<>(weekdays));
            this.ageLimit = ChildGameAccess.ageLimit(ageLimit);
            this.allowedGameIds = canonicalIds(allowedGameIds);
            this.blockedGameIds = canonicalIds(blockedGameIds);
            this.gameMode = gameMode;
            this.gameModeWindows = gameModeWindows;
            this.gameModeMute = gameModeMute;
        }

        private static Set<String> canonicalIds(Collection<String> ids) {
            Set<String> result = new LinkedHashSet<>();
            for (String id : ids) result.add(ChildGameAccess.canonicalGameId(id));
            return Collections.unmodifiableSet(result);
        }
    }

    static final class ChildProfiles {
        final String parentProfileId;
        final int revision;
        final List<ChildProfile> children;

        ChildProfiles(String parentProfileId, int revision, List<ChildProfile> children) {
            this.parentProfileId = parentProfileId == null ? "" : parentProfileId;
            this.revision = revision;
            this.children = Collections.unmodifiableList(new ArrayList<>(children));
        }
    }

    static final class ChildShare {
        final String id;
        final String name;
        final String ageLimit;
        /** What the Gateway decided for this child; {@link ChildGameAccess.State#access}. */
        final ChildGameAccess.State state;

        ChildShare(String id, String name, String ageLimit, ChildGameAccess.State state) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.ageLimit = ChildGameAccess.ageLimit(ageLimit);
            this.state = state;
        }
    }

    static final class ChildSharing {
        final String parentProfileId;
        final String gameKey;
        final int revision;
        /** The game's rating; null when unrated. */
        final GameAgeRating ageRating;
        /**
         * The minimum age the Gateway compares (3.4a), null when unrated: sent back unchanged as
         * sharing/set's {@code expected_min_age}.
         */
        final Integer minAge;
        final List<ChildShare> children;

        ChildSharing(String parentProfileId, String gameKey, int revision,
                     GameAgeRating ageRating, Integer minAge, List<ChildShare> children) {
            this.parentProfileId = parentProfileId == null ? "" : parentProfileId;
            this.gameKey = gameKey == null ? "" : gameKey;
            this.revision = revision;
            this.ageRating = ageRating;
            this.minAge = minAge;
            this.children = Collections.unmodifiableList(new ArrayList<>(children));
        }
    }

    static final class ChildMutationResult {
        final String parentProfileId;
        final String gameKey;
        final int revision;
        final ChildProfile child;
        final List<String> selectedChildIds;
        final boolean cleanupRequired;

        ChildMutationResult(String parentProfileId, String gameKey, int revision,
                            ChildProfile child, List<String> selectedChildIds,
                            boolean cleanupRequired) {
            this.parentProfileId = parentProfileId == null ? "" : parentProfileId;
            this.gameKey = gameKey == null ? "" : gameKey;
            this.revision = revision;
            this.child = child;
            this.selectedChildIds = Collections.unmodifiableList(
                    new ArrayList<>(selectedChildIds));
            this.cleanupRequired = cleanupRequired;
        }
    }

    static final class WindowsSession {
        final String state;
        final String reason;
        final String attemptId;
        final String requestId;
        final String actorProfileId;
        final String executionProfileId;
        final int retryAfterMs;

        WindowsSession(String state, String reason, String attemptId, int retryAfterMs) {
            this(state, reason, attemptId, retryAfterMs, "", "", "");
        }

        WindowsSession(String state, String reason, String attemptId, int retryAfterMs,
                       String requestId, String actorProfileId,
                       String executionProfileId) {
            this.state = normalizedState(state, "failed");
            this.reason = normalizedState(reason, "manual_sign_in_required");
            this.attemptId = attemptId == null ? "" : attemptId.trim();
            this.requestId = requestId == null ? "" : requestId.trim();
            this.actorProfileId = actorProfileId == null ? "" : actorProfileId.trim();
            this.executionProfileId = executionProfileId == null
                    ? "" : executionProfileId.trim();
            this.retryAfterMs = retryAfterMs > 0 ? retryAfterMs : 1_000;
        }
    }

    static final class RepairStatus {
        final boolean online;
        final String version;
        final String error;

        RepairStatus(boolean online, String version, String error) {
            this.online = online;
            this.version = version;
            this.error = error;
        }
    }

    static final class DiscordStatus {
        final boolean bridgeOnline;
        final boolean rpcConnected;
        final boolean authenticated;
        final String error;

        DiscordStatus(boolean bridgeOnline, boolean rpcConnected, boolean authenticated, String error) {
            this.bridgeOnline = bridgeOnline;
            this.rpcConnected = rpcConnected;
            this.authenticated = authenticated;
            this.error = error;
        }
    }

    static final class DiscordGuild {
        final String id;
        final String name;

        DiscordGuild(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class DiscordChannel {
        final String id;
        final String guildId;
        final String guildName;
        final String name;
        final int people;
        final boolean favorite;

        DiscordChannel(String id, String guildId, String guildName, String name,
                       int people, boolean favorite) {
            this.id = id;
            this.guildId = guildId;
            this.guildName = guildName;
            this.name = name;
            this.people = people;
            this.favorite = favorite;
        }
    }

    static final class DiscordHome {
        final List<DiscordChannel> favorites;
        final List<DiscordChannel> recent;
        final List<DiscordGuild> guilds;

        DiscordHome(List<DiscordChannel> favorites, List<DiscordChannel> recent,
                    List<DiscordGuild> guilds) {
            this.favorites = Collections.unmodifiableList(favorites);
            this.recent = Collections.unmodifiableList(recent);
            this.guilds = Collections.unmodifiableList(guilds);
        }
    }

    static final class DiscordVoice {
        final boolean connected;
        final String channelId;
        final String channelName;
        final String guildId;
        final boolean muted;
        final boolean deafened;
        final int participants;
        final List<DiscordParticipant> participantList;

        DiscordVoice(boolean connected, String channelId, String channelName, String guildId,
                     boolean muted, boolean deafened, int participants) {
            this(connected, channelId, channelName, guildId, muted, deafened,
                    participants, Collections.emptyList());
        }

        DiscordVoice(boolean connected, String channelId, String channelName, String guildId,
                     boolean muted, boolean deafened, int participants,
                     List<DiscordParticipant> participantList) {
            this.connected = connected;
            this.channelId = channelId;
            this.channelName = channelName;
            this.guildId = guildId;
            this.muted = muted;
            this.deafened = deafened;
            this.participants = participants;
            this.participantList = Collections.unmodifiableList(participantList);
        }
    }

    static final class DiscordParticipant {
        final String id;
        final String name;
        final String username;
        final int volume;
        final boolean muted;
        final boolean deafened;
        final boolean speaking;
        final boolean self;
        final boolean bot;
        final boolean canSetVolume;
        final String audioError;

        DiscordParticipant(String id, String name, int volume, boolean muted,
                           boolean speaking, boolean self) {
            this(id, name, "", volume, muted, false, speaking, self,
                    false, true, "");
        }

        DiscordParticipant(String id, String name, String username, int volume,
                           boolean muted, boolean deafened, boolean speaking,
                           boolean self, boolean bot, boolean canSetVolume,
                           String audioError) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.volume = volume;
            this.muted = muted;
            this.deafened = deafened;
            this.speaking = speaking;
            this.self = self;
            this.bot = bot;
            this.canSetVolume = canSetVolume;
            this.audioError = audioError;
        }
    }

    static final class AudioDevice {
        final String id;
        final String name;
        final String flow;
        final boolean current;

        AudioDevice(String id, String name, String flow, boolean current) {
            this.id = id;
            this.name = name;
            this.flow = flow;
            this.current = current;
        }
    }

    /** The Discord client's own input and output devices (needs Discord on the host). */
    static final class DiscordAudioState {
        final List<AudioDevice> devices;

        DiscordAudioState(List<AudioDevice> devices) {
            this.devices = Collections.unmodifiableList(devices);
        }
    }

    /** Windows volume, mute and active devices; served without the Discord client. */
    static final class SystemAudioState {
        final boolean available;
        final int volume;
        final boolean muted;
        final List<AudioDevice> devices;
        final String error;

        SystemAudioState(boolean available, int volume, boolean muted,
                         List<AudioDevice> devices, String error) {
            this.available = available;
            this.volume = volume;
            this.muted = muted;
            this.devices = Collections.unmodifiableList(devices);
            this.error = error;
        }
    }

    static final class GatewayException extends IOException {
        final int statusCode;
        final int retryAfterSeconds;

        GatewayException(String message, int statusCode) {
            this(message, statusCode, 0);
        }

        GatewayException(String message, int statusCode, int retryAfterSeconds) {
            super(message);
            this.statusCode = statusCode;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    static String endpointForHost(String address) {
        String host = address == null ? "" : address.trim();
        if (host.startsWith("[")) {
            return "https://" + host + ":" + DEFAULT_PORT;
        }
        if (host.indexOf(':') >= 0) {
            return "https://[" + host + "]:" + DEFAULT_PORT;
        }
        return "https://" + host + ":" + DEFAULT_PORT;
    }

    Pairing pair(String endpoint, String code, String clientName) throws IOException {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new GatewayException("The pairing code must contain six digits.", 0);
        }
        JSONObject request = new JSONObject();
        try {
            request.put("code", code);
            request.put("client_name", clientName);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        GatewayTransport.PairingResponse pairing;
        try {
            pairing = transport.postPairingJson(
                    endpoint, "/api/v1/pair", request, READ_TIMEOUT_MS);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
        JSONObject response = pairing.response();
        String fingerprint = pairing.certificateSha256();
        String token = response.optString("token", "");
        if (token.isEmpty()) throw new GatewayException("The gateway returned no client token.", 0);
        IntegrationProfiles profiles = parseIntegrationProfiles(response);
        String selectedProfile = pairingProfileId(profiles);
        return new Pairing(new GatewayConnection(endpoint, token, fingerprint, selectedProfile),
                response.optString("client_id", ""),
                response.optString("stream_pair_ticket", ""), profiles);
    }

    static String pairingProfileId(IntegrationProfiles profiles) {
        if (profiles != null && profiles.find(profiles.suggestedProfileId) != null) {
            return profiles.suggestedProfileId;
        }
        return profiles != null && !profiles.profiles.isEmpty()
                ? profiles.profiles.get(0).id : GatewayConnection.DEFAULT_PROFILE_ID;
    }

    String requestVibepolloPairingTicket(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection,
                "/api/v1/vibepollo/pair/ticket", "POST", new JSONObject(),
                READ_TIMEOUT_MS);
        String ticket = response.optString("stream_pair_ticket", "");
        if (ticket.isEmpty()) {
            throw new GatewayException("The gateway returned no stream pairing ticket.", 0);
        }
        return ticket;
    }

    VibepolloIdentityChallenge requestVibepolloIdentityChallenge(
            GatewayConnection connection) throws IOException {
        JSONObject response = transport.postJson(connection,
                "/api/v1/vibepollo/identity/challenge", new JSONObject(), READ_TIMEOUT_MS);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The existing Vibepollo pairing could not be verified."), 0);
        }
        String id = requireOpaqueId(response.optString("challenge_id", ""), "challenge_id");
        String challenge = response.optString("challenge", "");
        if (!isValidIdentityChallenge(challenge)) {
            throw new GatewayException("Invalid identity challenge.", 0);
        }
        return new VibepolloIdentityChallenge(id, challenge,
                response.optInt("expires_seconds", 60));
    }

    static boolean isValidIdentityChallenge(String challenge) {
        return challenge != null && challenge.startsWith(IDENTITY_CHALLENGE_PREFIX)
                && challenge.length() <= 512
                && challenge.substring(IDENTITY_CHALLENGE_PREFIX.length())
                .matches("[\\x20-\\x7e\\n]+");
    }

    void bindExistingVibepolloIdentity(GatewayConnection connection,
                                       String challengeId, String certificateSha256,
                                       String signature) throws IOException {
        String id = requireOpaqueId(challengeId, "challenge_id");
        String fingerprint = certificateSha256 == null ? ""
                : certificateSha256.trim().toLowerCase(Locale.ROOT);
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid client certificate fingerprint");
        }
        if (signature == null || signature.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing identity signature");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("challenge_id", id);
            body.put("certificate_sha256", fingerprint);
            body.put("signature", signature.trim());
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = transport.postJson(connection,
                "/api/v1/vibepollo/identity/bind", body, MANAGEMENT_TIMEOUT_MS);
        if (!response.optBoolean("ok", false) || !response.optBoolean("bound", false)) {
            throw new GatewayException(response.optString("error",
                    "The existing Vibepollo pairing could not be bound."), 0);
        }
    }

    JSONObject pairVibepolloClient(GatewayConnection connection, String ticket,
                                   String pin, String name) throws IOException {
        if (ticket == null || ticket.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing stream pairing ticket");
        }
        if (pin == null || !pin.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Invalid Moonlight pairing PIN");
        }
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty() || safeName.length() > 80 ||
                safeName.matches(".*[\\x00-\\x1f\\x7f].*")) {
            throw new IllegalArgumentException("Invalid Moonlight client name");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("ticket", ticket);
            body.put("pin", pin);
            body.put("name", safeName);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/vibepollo/pair", "POST", body, 55_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The Moonlight client could not be paired."), 0);
        }
        return response;
    }

    Capabilities getCapabilities(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection, "/api/v1/capabilities", "GET",
                null, READ_TIMEOUT_MS);
        return parseCapabilities(response);
    }

    ReleaseUpdates getUpdates(GatewayConnection connection, String publicKey, String packageName)
            throws IOException {
        return ReleaseUpdates.parse(request(connection, "/api/v1/updates", "GET",
                null, READ_TIMEOUT_MS), publicKey, packageName);
    }

    static Capabilities parseCapabilities(JSONObject response) {
        JSONObject capabilities = response.optJSONObject("capabilities");
        return new Capabilities(available(capabilities, "vibepollo_fix"),
                available(capabilities, "discord"),
                available(capabilities, "playnite"),
                available(capabilities, "child_profiles_v1"),
                available(capabilities, "control_operations_v2"),
                available(capabilities, "session_suspend"),
                available(capabilities, "usb_forwarding"),
                available(capabilities, "host_plus"),
                available(capabilities, "captures"),
                SetupFacts.parse(response.optJSONObject("setup")));
    }

    IntegrationProfiles getIntegrationProfiles(GatewayConnection connection) throws IOException {
        return getIntegrationProfiles(connection, PROFILES_READ_TIMEOUT_MS);
    }

    IntegrationProfiles getIntegrationProfiles(GatewayConnection connection, int readTimeoutMs)
            throws IOException {
        JSONObject response = request(connection, "/api/v1/profiles", "GET",
                null, readTimeoutMs);
        return parseIntegrationProfiles(response);
    }

    void verifyProfilePin(GatewayConnection connection, String pin) throws IOException {
        if (pin == null || !pin.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("PIN must contain four digits");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("pin", pin);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection, "/api/v1/profiles/pin/verify", "POST",
                body, READ_TIMEOUT_MS);
        if (!response.optBoolean("unlocked", false)) {
            throw new GatewayException("pin_verification_failed", 502);
        }
    }

    /**
     * Sets, changes or removes the selected adult profile's app PIN on the host; a null
     * {@code newPin} removes it. Returns whether the profile now requires a PIN. Replaying
     * the same {@code requestId} returns the recorded outcome.
     */
    boolean changeProfilePin(GatewayConnection connection, String currentPin, String newPin,
                             String requestId) throws IOException {
        try {
            JSONObject response = transport.postJson(connection, "/api/v1/profiles/pin/change",
                    profilePinChangeBody(currentPin, newPin), requireSessionRequestId(requestId),
                    READ_TIMEOUT_MS);
            if (!response.optBoolean("ok", false)) {
                throw new GatewayException("pin_change_failed", 502);
            }
            return response.optBoolean("pin_required", newPin != null);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    /**
     * The first-run wizard's first PIN for a profile without one. The host accepts it only
     * within its one-shot window right after this TV paired; otherwise it fails with
     * {@code first_pin_grant_required}.
     */
    boolean setFirstProfilePin(GatewayConnection connection, String pin, String requestId)
            throws IOException {
        return changeProfilePin(connection, null, pin, requestId);
    }

    /**
     * #47: the profile's Steam Web API key ({@code steam}) or an Epic authorization code,
     * stored by the host as that Windows user. Fails with the host's reason, e.g.
     * {@code epic_sign_in_failed}; the value is never echoed back.
     */
    void setupAccount(GatewayConnection connection, boolean steam, String value,
                      String requestId) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put(steam ? "api_key" : "code", value);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        try {
            JSONObject response = transport.postJson(connection,
                    steam ? "/api/v1/setup/steam-key" : "/api/v1/setup/epic-login", body,
                    requireSessionRequestId(requestId), steam ? READ_TIMEOUT_MS : 90_000);
            if (!response.optBoolean("ok", false)) {
                throw new GatewayException("bridge_unavailable", 502);
            }
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    static JSONObject profilePinChangeBody(String currentPin, String newPin) {
        if ((currentPin == null && newPin == null)
                || (currentPin != null && !currentPin.matches("[0-9]{4}"))
                || (newPin != null && !newPin.matches("[0-9]{4}"))) {
            throw new IllegalArgumentException("PIN must contain four digits");
        }
        JSONObject body = new JSONObject();
        try {
            if (currentPin != null) body.put("current_pin", currentPin);
            if (newPin != null) body.put("new_pin", newPin);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return body;
    }

    /**
     * Names of the host's local Windows accounts that may get an adult profile. The
     * connection's profile is the authorizing adult (with its {@code currentPin} when it has
     * one); a TV granted no adult profile yet is authorized by its pairing grant instead.
     */
    List<String> adultProfileAccounts(GatewayConnection connection, String currentPin)
            throws IOException {
        try {
            JSONObject response = transport.postJson(connection,
                    "/api/v1/profiles/adult/accounts", adultProfileBody(currentPin),
                    READ_TIMEOUT_MS);
            JSONArray names = response.optJSONArray("accounts");
            if (!response.optBoolean("ok", false) || names == null) {
                throw new GatewayException("adult_accounts_failed", 502);
            }
            List<String> accounts = new ArrayList<>();
            for (int index = 0; index < names.length() && index < 64; index++) {
                String name = names.optString(index, "").trim();
                if (!name.isEmpty()) accounts.add(name);
            }
            return accounts;
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    /**
     * Creates an adult profile bound to {@code accountName}, one of
     * {@link #adultProfileAccounts}, authorized as there; {@code newPin}, when not null, is the
     * new profile's first app PIN. Returns the new profile's ID. Replaying the same
     * {@code requestId} returns the same profile.
     */
    String createAdultProfile(GatewayConnection connection, String accountName,
                                String currentPin, String newPin, String requestId)
            throws IOException {
        JSONObject body = adultProfileBody(currentPin);
        if (accountName == null || accountName.trim().isEmpty()
                || (newPin != null && !newPin.matches("[0-9]{4}"))) {
            throw new IllegalArgumentException("Invalid adult profile request");
        }
        try {
            body.put("account_name", accountName);
            if (newPin != null) body.put("new_pin", newPin);
            JSONObject response = transport.postJson(connection, "/api/v1/profiles/adult",
                    body, requireSessionRequestId(requestId), READ_TIMEOUT_MS);
            JSONObject profile = response.optJSONObject("profile");
            String id = profile == null ? "" : profile.optString("id", "");
            if (!response.optBoolean("ok", false) || id.isEmpty()) {
                throw new GatewayException("adult_profile_failed", 502);
            }
            return id;
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    private static JSONObject adultProfileBody(String currentPin) {
        if (currentPin != null && !currentPin.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("PIN must contain four digits");
        }
        JSONObject body = new JSONObject();
        try {
            if (currentPin != null) body.put("current_pin", currentPin);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return body;
    }

    ChildManagementAuthorization authorizeChildManagement(
            GatewayConnection connection, String parentProfileId, String pin) throws IOException {
        String parent = requireProfileId(parentProfileId);
        if (pin == null || !pin.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("PIN must contain four digits");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("parent_profile_id", parent);
            body.put("pin", pin);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection, "/api/v1/profiles/children/authorize",
                "POST", body, MANAGEMENT_TIMEOUT_MS);
        requireChildOk(response, "Child profile management could not be authorized.");
        requireResponseParent(response, parent);
        String authorizationId = requireOpaqueId(response.optString("authorization_id", ""),
                "authorization_id");
        return new ChildManagementAuthorization(
                response.optString("parent_profile_id", ""), authorizationId,
                response.optInt("expires_in_seconds", 300));
    }

    ChildProfiles listChildProfiles(GatewayConnection connection, String parentProfileId,
                                    String authorizationId) throws IOException {
        String parent = requireProfileId(parentProfileId);
        JSONObject response = request(connection, "/api/v1/profiles/children/list", "POST",
                childManagementBody(parent, authorizationId), MANAGEMENT_TIMEOUT_MS);
        requireChildOk(response, "Child profiles could not be loaded.");
        requireResponseParent(response, parent);
        JSONArray values = response.optJSONArray("children");
        if (values == null) throw invalidChildResponse();
        List<ChildProfile> children = new ArrayList<>();
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                ChildProfile child = parseChildProfile(value, parent);
                if (child == null || !parent.equals(child.parentProfileId)) {
                    throw invalidChildResponse();
                }
                children.add(child);
            }
        }
        return new ChildProfiles(response.optString("parent_profile_id", ""),
                response.optInt("revision", 0), children);
    }

    ChildMutationResult createChildProfile(
            GatewayConnection connection, String parentProfileId, String authorizationId,
            String requestId, int expectedRevision,
            ConsoleChildProfileEditor.Draft draft) throws IOException {
        return mutateChildProfile(connection, "create", parentProfileId, authorizationId,
                requestId, expectedRevision, "", draft);
    }

    ChildMutationResult updateChildProfile(
            GatewayConnection connection, String parentProfileId, String authorizationId,
            String childProfileId, String requestId, int expectedRevision,
            ConsoleChildProfileEditor.Draft draft) throws IOException {
        return mutateChildProfile(connection, "update", parentProfileId, authorizationId,
                requestId, expectedRevision, childProfileId, draft);
    }

    ChildMutationResult deleteChildProfile(
            GatewayConnection connection, String parentProfileId, String authorizationId,
            String childProfileId, String requestId, int expectedRevision) throws IOException {
        String child = requireOpaqueId(childProfileId, "child_profile_id");
        JSONObject body = childManagementBody(parentProfileId, authorizationId);
        try {
            body.put("child_profile_id", child);
            body.put("request_id", requireOpaqueId(requestId, "request_id"));
            body.put("expected_revision", expectedRevision);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection, "/api/v1/profiles/children/delete",
                "POST", body, MANAGEMENT_TIMEOUT_MS);
        requireChildOk(response, "Child profile could not be deleted.");
        String parent = requireProfileId(parentProfileId);
        requireResponseParent(response, parent);
        JSONObject childValue = response.optJSONObject("child");
        ChildProfile deletedProfile = childValue == null
                ? null : parseChildProfile(childValue, "");
        if (deletedProfile != null && !parent.equals(deletedProfile.parentProfileId)) {
            throw invalidChildResponse();
        }
        return new ChildMutationResult(
                response.optString("parent_profile_id", ""),
                response.optString("game_key", ""), response.optInt("revision", 0),
                deletedProfile,
                parseStringArray(response.optJSONArray("selected_child_ids")),
                response.optBoolean("cleanup_required", false));
    }

    ChildSharing getChildSharing(GatewayConnection connection, String parentProfileId,
                                 String authorizationId, String gameId) throws IOException {
        JSONObject body = childManagementBody(parentProfileId, authorizationId);
        try {
            body.put("game_id", requestGameId(requireGameId(gameId)));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection, "/api/v1/profiles/children/sharing/get",
                "POST", body, MANAGEMENT_TIMEOUT_MS);
        requireChildOk(response, "Game sharing could not be loaded.");
        requireResponseParent(response, requireProfileId(parentProfileId));
        return parseChildSharing(response);
    }

    /**
     * sharing/get (3.4a): access per child is its {@code state}; {@code granted} repeats it. The
     * top-level {@code min_age} (a number or null) is required.
     */
    static ChildSharing parseChildSharing(JSONObject response) throws IOException {
        JSONArray values = response.optJSONArray("children");
        if (values == null || !response.has("min_age")) throw invalidChildResponse();
        Object minAgeValue = response.opt("min_age");
        if (minAgeValue != JSONObject.NULL && !(minAgeValue instanceof Integer)) {
            throw invalidChildResponse();
        }
        Integer minAge = minAgeValue == JSONObject.NULL ? null : (Integer) minAgeValue;
        List<ChildShare> children = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            String id = value.optString("id", "").trim();
            if (id.isEmpty()) continue;
            ChildGameAccess.State state =
                    ChildGameAccess.State.fromWire(value.optString("state", ""));
            if (state == null) throw invalidChildResponse();
            children.add(new ChildShare(id, value.optString("name", "").trim(),
                    parseAgeLimit(value), state));
        }
        return new ChildSharing(response.optString("parent_profile_id", ""),
                response.optString("game_key", ""), response.optInt("revision", 0),
                GameAgeRating.parse(response.optJSONObject("ageRating")), minAge, children);
    }

    ChildMutationResult setChildSharing(
            GatewayConnection connection, String parentProfileId, String authorizationId,
            String gameId, List<String> selectedChildIds, int expectedRevision,
            Integer expectedMinAge, String requestId) throws IOException {
        JSONObject body = childSharingRequestBody(parentProfileId, authorizationId, gameId,
                selectedChildIds, expectedRevision, expectedMinAge, requestId);
        List<String> unique = parseStringArray(body.optJSONArray("selected_child_ids"));
        JSONObject response = request(connection, "/api/v1/profiles/children/sharing/set",
                "POST", body, MANAGEMENT_TIMEOUT_MS);
        requireChildOk(response, "Game sharing could not be updated.");
        String parent = requireProfileId(parentProfileId);
        requireResponseParent(response, parent);
        JSONArray echoedValues = response.optJSONArray("selected_child_ids");
        if (echoedValues == null) throw invalidChildResponse();
        List<String> echoed = parseStringArray(echoedValues);
        if (!sameIds(unique, echoed)) throw invalidChildResponse();
        return new ChildMutationResult(
                response.optString("parent_profile_id", ""),
                response.optString("game_key", ""), response.optInt("revision", 0), null,
                echoed,
                response.optBoolean("cleanup_required", false));
    }

    private ChildMutationResult mutateChildProfile(
            GatewayConnection connection, String operation, String parentProfileId,
            String authorizationId, String requestId, int expectedRevision,
            String childProfileId, ConsoleChildProfileEditor.Draft draft) throws IOException {
        if (!"create".equals(operation) && !"update".equals(operation)) {
            throw new IllegalArgumentException("Unknown child profile operation");
        }
        JSONObject body = childManagementBody(parentProfileId, authorizationId);
        try {
            body.put("request_id", requireOpaqueId(requestId, "request_id"));
            body.put("expected_revision", expectedRevision);
            if ("update".equals(operation)) {
                body.put("child_profile_id", requireOpaqueId(childProfileId,
                        "child_profile_id"));
            } else if (draft != null) {
                body.put("grant_current_device", draft.grantCurrentDevice);
            }
            body.put("draft", childDraftBody(draft));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection, "/api/v1/profiles/children/" + operation,
                "POST", body, MANAGEMENT_TIMEOUT_MS);
        requireChildOk(response, "Child profile could not be saved.");
        JSONObject childValue = response.optJSONObject("child");
        ChildProfile child = childValue == null ? null : parseChildProfile(childValue, "");
        String parent = requireProfileId(parentProfileId);
        requireResponseParent(response, parent);
        if (child == null || !parent.equals(child.parentProfileId)) {
            throw invalidChildResponse();
        }
        return new ChildMutationResult(
                response.optString("parent_profile_id", ""),
                response.optString("game_key", ""), response.optInt("revision", 0),
                child,
                parseStringArray(response.optJSONArray("selected_child_ids")),
                response.optBoolean("cleanup_required", false));
    }

    private static JSONObject childManagementBody(String parentProfileId,
                                                  String authorizationId) {
        String parent = requireProfileId(parentProfileId);
        String authorization = requireOpaqueId(authorizationId, "authorization_id");
        JSONObject body = new JSONObject();
        try {
            body.put("parent_profile_id", parent);
            body.put("authorization_id", authorization);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return body;
    }

    static JSONObject childSharingRequestBody(String parentProfileId, String authorizationId,
                                              String gameId, List<String> selectedChildIds,
                                              int expectedRevision, Integer expectedMinAge,
                                              String requestId) throws IOException {
        JSONObject body = childManagementBody(parentProfileId, authorizationId);
        JSONArray selected = new JSONArray();
        if (selectedChildIds != null) {
            for (String value : selectedChildIds) {
                String id = requireOpaqueId(value, "child_profile_id");
                boolean present = false;
                for (int index = 0; index < selected.length(); index++) {
                    if (id.equals(selected.optString(index, ""))) {
                        present = true;
                        break;
                    }
                }
                if (!present) selected.put(id);
            }
        }
        try {
            body.put("game_id", requestGameId(requireGameId(gameId)));
            body.put("selected_child_ids", selected);
            body.put("expected_revision", expectedRevision);
            // The rating the parent saw (3.4a): a changed one is a 409 conflict, nothing saved.
            body.put("expected_min_age",
                    expectedMinAge == null ? JSONObject.NULL : expectedMinAge);
            body.put("request_id", requireOpaqueId(requestId, "request_id"));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return body;
    }

    static JSONObject childDraftBody(ConsoleChildProfileEditor.Draft draft)
            throws JSONException {
        if (draft == null) throw new IllegalArgumentException("Missing child profile draft");
        if (draft.weekdays == null || draft.weekdays.size() != 7) {
            throw new IllegalArgumentException("Child profile draft must contain seven days");
        }
        String name = draft.name == null ? "" : draft.name.trim();
        if (name.isEmpty() || name.length() > 80 || hasControlCharacters(name)) {
            throw new IllegalArgumentException("Invalid child profile name");
        }
        String avatar = draft.avatar == null ? "" : draft.avatar.trim();
        if (!avatar.isEmpty() && !isAvatarId(avatar)) {
            throw new IllegalArgumentException("Invalid child profile avatar");
        }
        JSONObject weekdays = new JSONObject();
        String[] names = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
        for (int index = 0; index < names.length; index++) {
            ConsoleChildProfileEditor.Day day = draft.weekdays.get(index);
            if (day == null || day.startMinute < 0 || day.startMinute > 1440
                    || day.endMinute < 0 || day.endMinute > 1440
                    || day.dailyLimitSeconds < 0 || day.dailyLimitSeconds > 86_400
                    || day.enabled && day.startMinute >= day.endMinute) {
                throw new IllegalArgumentException("Invalid child profile schedule");
            }
            weekdays.put(names[index], new JSONObject()
                    .put("enabled", day.enabled)
                    .put("start_minute", day.startMinute)
                    .put("end_minute", day.endMinute)
                    .put("daily_limit_seconds", day.dailyLimitSeconds));
        }
        return new JSONObject()
                .put("name", name)
                .put("avatar_id", avatar)
                .put("enabled", draft.enabled)
                .put("game_mode", draft.gameMode)
                .put("game_mode_windows", draft.gameModeWindows)
                .put("game_mode_mute", draft.gameModeMute)
                .put("age_limit", ChildGameAccess.ageLimit(draft.ageLimit))
                .put("schedule", new JSONObject().put("weekdays", weekdays));
    }

    /** 3.4a: a missing {@code age_limit} is "manual"; anything but the six strings is invalid. */
    private static String parseAgeLimit(JSONObject value) throws IOException {
        if (!value.has("age_limit")) return ChildGameAccess.MANUAL;
        Object limit = value.opt("age_limit");
        if (!ChildGameAccess.AGE_LIMITS.contains(limit)) throw invalidChildResponse();
        return (String) limit;
    }

    static ChildProfile parseChildProfile(JSONObject value, String fallbackParent)
            throws IOException {
        String id = value.optString("id", "").trim();
        if (id.isEmpty()) return null;
        JSONObject schedule = value.optJSONObject("schedule");
        JSONObject weekdays = schedule == null ? null : schedule.optJSONObject("weekdays");
        String[] names = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
        List<ConsoleChildProfileEditor.Day> days = new ArrayList<>(7);
        for (String name : names) {
            JSONObject day = weekdays == null ? null : weekdays.optJSONObject(name);
            days.add(new ConsoleChildProfileEditor.Day(
                    day != null && day.optBoolean("enabled", false),
                    day == null ? 0 : day.optInt("start_minute", 0),
                    day == null ? 1440 : day.optInt("end_minute", 1440),
                    day == null ? 0 : day.optInt("daily_limit_seconds", 0)));
        }
        // Only a catalog id is an avatar; anything else (an old colour name) is the generated
        // art the editor shows, and the draft carries that, so it can be saved.
        String avatar = value.optString("avatar_id", "");
        return new ChildProfile(id, value.optString("name", "").trim(),
                isAvatarId(avatar) ? avatar : "", value.optBoolean("enabled", false),
                value.optString("parent_profile_id", fallbackParent),
                value.optInt("policy_revision", 0), days, parseAgeLimit(value),
                parseStringArray(value.optJSONArray("allowed_game_ids")),
                parseStringArray(value.optJSONArray("blocked_game_ids")),
                value.optBoolean("game_mode", false),
                value.optBoolean("game_mode_windows", false),
                value.optBoolean("game_mode_mute", false));
    }

    private static List<String> parseStringArray(JSONArray values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (int index = 0; index < values.length(); index++) {
            String value = values.optString(index, "").trim();
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private static void requireChildOk(JSONObject response, String fallback) throws IOException {
        if (response == null || !response.optBoolean("ok", false)) {
            String reason = response == null ? "" : response.optString("error", "");
            throw new GatewayException(reason.isEmpty() ? fallback : reason, 0);
        }
    }

    private static void requireResponseParent(JSONObject response, String expected)
            throws IOException {
        if (response == null || !expected.equals(response.optString("parent_profile_id", ""))) {
            throw invalidChildResponse();
        }
    }

    private static boolean sameIds(List<String> expected, List<String> actual) {
        return expected.size() == actual.size() && expected.containsAll(actual)
                && actual.containsAll(expected);
    }

    private static GatewayException invalidChildResponse() {
        return new GatewayException("invalid_response", 0);
    }

    private static String requireProfileId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Invalid parent profile ID");
        }
        return normalized;
    }

    private static String requireOpaqueId(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}")) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return normalized;
    }

    private static String requireGameId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!isPlayniteId(normalized)) throw new IllegalArgumentException("Invalid game record ID");
        return normalized;
    }

    private static boolean hasControlCharacters(String value) {
        return value.matches(".*[\\x00-\\x1f\\x7f].*");
    }

    WindowsSession ensureWindowsSession(GatewayConnection connection, String requestId)
            throws IOException {
        try {
            return parseWindowsSession(transport.postJson(connection,
                    "/api/v1/system/session/ensure", new JSONObject(), requestId, 15_000));
        } catch (GatewayTransport.GatewayException error) {
            WindowsSession failure = sessionFailure(error);
            if (failure != null) return failure;
            throw mapException(error);
        }
    }

    WindowsSession switchWindowsSession(GatewayConnection connection, String requestId)
            throws IOException {
        try {
            return parseWindowsSession(transport.postJson(connection,
                    "/api/v1/system/session/switch", new JSONObject(), requestId, 15_000));
        } catch (GatewayTransport.GatewayException error) {
            WindowsSession failure = sessionFailure(error);
            if (failure != null) return failure;
            throw mapException(error);
        }
    }

    WindowsSession getWindowsSessionStatus(GatewayConnection connection,
                                           String requestId, String attemptId)
            throws IOException {
        String attempt = requireAttemptId(attemptId);
        String request = requireSessionRequestId(requestId);
        String path = "/api/v1/system/session/status?attempt_id="
                + URLEncoder.encode(attempt, StandardCharsets.UTF_8.name())
                + "&request_id="
                + URLEncoder.encode(request, StandardCharsets.UTF_8.name());
        try {
            return parseWindowsSession(transport.getJson(connection, path, 10_000));
        } catch (GatewayTransport.GatewayException error) {
            WindowsSession failure = sessionFailure(error);
            if (failure != null) return failure;
            throw mapException(error);
        }
    }

    void cancelWindowsSession(GatewayConnection connection, String requestId,
                              String attemptId) throws IOException {
        String request = requireSessionRequestId(requestId);
        JSONObject body = sessionCancellationBody(request, attemptId);
        try {
            transport.postJson(connection, "/api/v1/system/session/cancel",
                    body, request, 10_000);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    static JSONObject sessionCancellationBody(String requestId, String attemptId) {
        JSONObject body = new JSONObject();
        try {
            body.put("request_id", requireSessionRequestId(requestId));
            body.put("attempt_id", requireAttemptId(attemptId));
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return body;
    }

    static WindowsSession parseWindowsSession(JSONObject response) {
        return new WindowsSession(response.optString("state", "failed"),
                response.optString("reason", "manual_sign_in_required"),
                response.optString("attempt_id", ""),
                response.optInt("retry_after_ms", 1_000),
                response.optString("request_id", ""),
                response.optString("actor_profile_id", ""),
                response.optString("execution_profile_id", ""));
    }

    static WindowsSession sessionFailure(
            GatewayTransport.GatewayException error) {
        if (error.statusCode() == 403) {
            return new WindowsSession("action_required",
                    "remote_sign_in_not_granted", "", 1_000);
        }
        if (error.statusCode() == 503) {
            return new WindowsSession("failed", "broker_unavailable", "", 1_000);
        }
        if (error.statusCode() < 400) return null;
        String reason = normalizedState(error.getMessage(), "manual_sign_in_required");
        if (!reason.matches("[a-z0-9_]{1,64}")) reason = "manual_sign_in_required";
        String state = "attempt_expired".equals(reason) ? "expired"
                : "attempt_cancelled".equals(reason) ? "cancelled" : "action_required";
        return new WindowsSession(state, reason, "", 1_000);
    }

    private static String requireSessionRequestId(String requestId) {
        String value = requestId == null ? "" : requestId.trim();
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Invalid session request ID");
        }
        return value;
    }

    private static String requireAttemptId(String attemptId) {
        String value = attemptId == null ? "" : attemptId.trim();
        if (!value.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid remote sign-in attempt ID");
        }
        return value;
    }

    GatewayTransport.NetworkDownloadSample measureNetworkDownload(
            GatewayConnection connection, int sizeBytes) throws IOException {
        return measureNetworkDownload(connection, sizeBytes, 15_000);
    }

    GatewayTransport.NetworkDownloadSample measureAdaptiveNetworkDownload(
            GatewayConnection connection) throws IOException {
        GatewayTransport.NetworkDownloadSample probe = measureNetworkDownload(
                connection, NETWORK_PROBE_BYTES, 15_000);
        return measureNetworkDownload(connection,
                adaptiveNetworkDownloadSize(probe.bytes, probe.elapsedNanos), 30_000);
    }

    static int adaptiveNetworkDownloadSize(long bytes, long elapsedNanos) {
        if (bytes <= 0 || elapsedNanos <= 0) {
            throw new IllegalArgumentException("Network sample must be positive");
        }
        double estimate = bytes * (double) NETWORK_TARGET_NANOS / elapsedNanos;
        return (int) Math.round(Math.max(NETWORK_PROBE_BYTES,
                Math.min(GatewayTransport.NETWORK_DOWNLOAD_MAX_BYTES, estimate)));
    }

    private GatewayTransport.NetworkDownloadSample measureNetworkDownload(
            GatewayConnection connection, int sizeBytes, int readTimeoutMs) throws IOException {
        try {
            return transport.measureNetworkDownload(connection, sizeBytes, readTimeoutMs);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    static IntegrationProfiles parseIntegrationProfiles(JSONObject response) {
        JSONArray values = response.optJSONArray("profiles");
        List<IntegrationProfile> profiles = new ArrayList<>();
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                String id = value.optString("id", "").trim();
                if (!id.matches("[A-Za-z0-9._-]{1,64}")) continue;
                String kind = value.optString("kind", "standard").trim();
                boolean child = "child".equalsIgnoreCase(kind);
                String name = value.optString("name", "").trim();
                // A malformed child row must never make its opaque ID user-visible.
                if (name.isEmpty() && !child) name = id;
                JSONObject permissions = value.optJSONObject("permissions");
                profiles.add(new IntegrationProfile(
                        id, name,
                        value.optBoolean("discord_bridge_online", false),
                        value.optBoolean("discord_rpc_connected", false),
                        value.optBoolean("discord_authenticated", false),
                        value.optBoolean("vibepollo_bridge_online", false),
                        value.optBoolean("game_provider_bridge_online",
                                value.optBoolean("playnite_bridge_online", false)),
                        value.optBoolean("playnite_connector_connected", false),
                        permissions == null || permissions.optBoolean("use_profile", true),
                        permissions != null && permissions.optBoolean(
                                "remote_sign_in", false),
                        value.optString("session_state", "unknown"),
                        value.optString("remote_sign_in_state", "unavailable"),
                        value.optBoolean("pin_required", false),
                        permissions != null && permissions.optBoolean(
                                "manage_children", false),
                        value.optInt("own_children_count", 0),
                        kind,
                        value.optString("parent_profile_id", ""),
                        value.optString("execution_profile_id", id),
                        value.optString("avatar_id", ""),
                        value.optBoolean("enabled", true),
                        value.optLong("policy_revision", 0L),
                        value.optLong("usage_revision", 0L),
                        value.optLong("remaining_daily_seconds", 0L),
                        value.optLong("playable_now_seconds", 0L),
                        value.optString("next_allowed_at", ""),
                        value.optString("server_time", ""),
                        value.optString("reason", ""),
                        value.optString("window_end", "")));
            }
        }
        return new IntegrationProfiles(profiles,
                response.optString("suggested_profile_id", ""));
    }

    RepairStatus getVibepolloRepairStatus(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection, "/api/v1/vibepollo/repair/status", "GET",
                null, READ_TIMEOUT_MS);
        JSONObject host = response.optJSONObject("host");
        JSONObject bridge = response.optJSONObject("bridge");
        return new RepairStatus(response.optBoolean("ok", false) &&
                host != null && host.optBoolean("online", false),
                host != null ? host.optString("version", "") : "",
                bridge != null ? bridge.optString("api_error", "") : "");
    }

    JSONObject runVibepolloRepair(GatewayConnection connection, String action) throws IOException {
        if (!"restart".equals(action) && !"reset-display".equals(action) &&
                !"export-logs".equals(action)) {
            throw new IllegalArgumentException("Unknown repair action");
        }
        return request(connection, "/api/v1/vibepollo/repair/" + action, "POST",
                new JSONObject(),
                "export-logs".equals(action) ? 25_000 : 8_000);
    }

    JSONObject sleepHost(GatewayConnection connection) throws IOException {
        return request(connection, "/api/v1/system/sleep", "POST",
                new JSONObject(), READ_TIMEOUT_MS);
    }

    /** The host's pending restart and Windows Update scan, as the Gateway reports them. */
    static final class PowerStatus {
        final boolean rebootRequired;
        /** The host started again after a MoonWaker shutdown, so shutting down is safe. */
        final boolean wakeFromShutdownVerified;
        /** idle, running, done or failed; empty for a child, who sees only the restart. */
        final String scanState;
        final int availableUpdates;
        final int downloadedUpdates;

        PowerStatus(boolean rebootRequired, boolean wakeFromShutdownVerified, String scanState,
                    int availableUpdates, int downloadedUpdates) {
            this.rebootRequired = rebootRequired;
            this.wakeFromShutdownVerified = wakeFromShutdownVerified;
            this.scanState = scanState;
            this.availableUpdates = availableUpdates;
            this.downloadedUpdates = downloadedUpdates;
        }
    }

    PowerStatus getPowerStatus(GatewayConnection connection) throws IOException {
        return parsePowerStatus(request(connection, "/api/v1/system/power", "GET",
                null, READ_TIMEOUT_MS));
    }

    static PowerStatus parsePowerStatus(JSONObject response) {
        JSONObject update = response.optJSONObject("windows_update");
        return new PowerStatus(response.optBoolean("reboot_required", false),
                response.optBoolean("wake_from_shutdown_verified", false),
                update == null ? "" : update.optString("state", ""),
                update == null ? 0 : update.optInt("available", 0),
                update == null ? 0 : update.optInt("downloaded", 0));
    }

    JSONObject scanWindowsUpdate(GatewayConnection connection) throws IOException {
        return request(connection, "/api/v1/system/windows-update/scan", "POST",
                new JSONObject(), READ_TIMEOUT_MS);
    }

    /** Restart or shut down the host; the caller has the user's confirmation. */
    JSONObject powerHost(GatewayConnection connection, boolean shutdown) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("confirmed", true);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return request(connection, shutdown ? "/api/v1/system/shutdown"
                : "/api/v1/system/restart", "POST", body, READ_TIMEOUT_MS);
    }

    JSONObject suspendSession(GatewayConnection connection, JSONObject session,
                              String suspendId) throws IOException {
        try {
            // The host waits for the permit and freezes the game (Quick Resume) before it
            // sleeps: up to ~65 s.
            return transport.postJson(connection, "/api/v1/system/suspend-session",
                    session, suspendId, SUSPEND_SESSION_TIMEOUT_MS);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    PlayniteLibrary getPlayniteLibrary(GatewayConnection connection, String cursor, int limit)
            throws IOException {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid page size");
        String safeCursor = cursor == null ? "" : cursor;
        String encoded = URLEncoder.encode(safeCursor, StandardCharsets.UTF_8.name());
        JSONObject response = request(connection,
                "/api/v1/library?cursor=" + encoded + "&limit=" + limit,
                "GET", null, 12_000);
        return parsePlayniteLibrary(response.optJSONObject("library"));
    }

    String refreshPlayniteLibrary(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection,
                "/api/v1/library/refresh", "POST", new JSONObject(),
                8_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The Playnite library refresh could not be started."), 0);
        }
        JSONObject result = response.optJSONObject("result");
        return result == null ? "" : result.optString("previous_revision", "");
    }

    byte[] getPlayniteArtwork(GatewayConnection connection, String gameId, String kind)
            throws IOException {
        if (!isPlayniteId(gameId)) throw new IllegalArgumentException("Invalid game record ID");
        if (!"cover".equals(kind) && !"background".equals(kind)
                && !"hero".equals(kind) && !"icon".equals(kind)) {
            throw new IllegalArgumentException("Invalid Playnite artwork kind");
        }
        try {
            return transport.getBinary(connection,
                    "/api/v1/artwork?game_id=" + gameId + "&kind=" + kind,
                    "image/*", 12_000);
        } catch (GatewayTransport.GatewayException error) {
            throw new GatewayException("Playnite artwork is unavailable.", error.statusCode());
        } catch (GatewayTransport.ResponseTooLargeException error) {
            throw new IOException("Playnite artwork is too large.");
        }
    }

    /** Avatar ids the host serves ("builtin:x", "custom:y") with each image's revision. */
    static boolean isAvatarId(String value) {
        return value != null && value.matches("(builtin|custom):[a-z0-9][a-z0-9_-]{0,47}");
    }

    /** One image of the host's avatar catalog; {@code group} sorts it in the picker. */
    static final class Avatar {
        final String id;
        final String revision;
        final String group;

        Avatar(String id, String revision, String group) {
            this.id = id;
            this.revision = revision;
            this.group = group;
        }
    }

    /** The host's avatar catalog in the host's order, grouped. */
    List<Avatar> getAvatars(GatewayConnection connection) throws IOException {
        return parseAvatars(request(connection, "/api/v1/avatars", "GET", null, READ_TIMEOUT_MS));
    }

    static List<Avatar> parseAvatars(JSONObject response) {
        Map<String, Avatar> avatars = new LinkedHashMap<>();
        JSONArray values = response.optJSONArray("avatars");
        for (int index = 0; values != null && index < values.length() && index < 160; index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            String id = value.optString("id", "");
            String revision = value.optString("revision", "");
            String group = value.optString("group", "");
            if (isAvatarId(id) && revision.matches("[0-9a-f]{1,64}")
                    && group.matches("[a-z0-9][a-z0-9-]{0,23}")) {
                avatars.putIfAbsent(id, new Avatar(id, revision, group));
            }
        }
        return new ArrayList<>(avatars.values());
    }

    byte[] getAvatarImage(GatewayConnection connection, String avatarId) throws IOException {
        if (!isAvatarId(avatarId)) throw new IllegalArgumentException("Invalid avatar ID");
        try {
            return transport.getBinary(connection, "/api/v1/avatars/image?id="
                    + URLEncoder.encode(avatarId, StandardCharsets.UTF_8.name()), "image/*", 12_000);
        } catch (GatewayTransport.GatewayException error) {
            throw new GatewayException("The avatar is unavailable.", error.statusCode());
        } catch (GatewayTransport.ResponseTooLargeException error) {
            throw new IOException("The avatar is too large.");
        }
    }

    /** What the host answered a problem report: sent, or the reason it was not. */
    static final class ReportResult {
        final boolean sent;
        /** "sent", report_not_configured, rate_limited, webhook_failed, invalid_bundle, … */
        final String reason;
        /** For webhook_failed: Discord's side, e.g. discord_http_404 or network_unreachable. */
        final String detail;
        final int retryAfterSeconds;

        ReportResult(boolean sent, String reason, String detail, int retryAfterSeconds) {
            this.sent = sent;
            this.reason = reason;
            this.detail = detail;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    /**
     * "Zgłoś problem": the zipped TV bundle; the host adds its own logs for the bundle's
     * window and posts both to the owner's Discord. A host refusal is a result; a transport
     * failure throws, and the same request ID may be retried.
     */
    ReportResult sendProblemReport(GatewayConnection connection, byte[] bundle, String requestId)
            throws IOException {
        try {
            transport.postReport(connection, bundle, requestId, 90_000);
            return new ReportResult(true, "sent", "", 0);
        } catch (GatewayTransport.GatewayException error) {
            JSONObject body = error.responseBody();
            String fallback = "http_" + error.statusCode();
            return new ReportResult(false, body == null ? fallback
                    : body.optString("reason", fallback), body == null ? ""
                    : body.optString("detail", ""), error.retryAfterSeconds());
        }
    }

    /** Sets the connection profile's own avatar; "" restores the generated one. */
    void setProfileAvatar(GatewayConnection connection, String avatarId) throws IOException {
        String value = avatarId == null ? "" : avatarId;
        if (!value.isEmpty() && !isAvatarId(value)) {
            throw new IllegalArgumentException("Invalid avatar ID");
        }
        try {
            request(connection, "/api/v1/profiles/avatar", "POST",
                    new JSONObject().put("avatar_id", value), READ_TIMEOUT_MS);
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    SessionObservation getSessionObservation(GatewayConnection connection,
                                             String executionProfileId,
                                             long requestOrdinal) throws IOException {
        if (requestOrdinal <= 0L) throw new IllegalArgumentException("request ordinal");
        String path = "/api/v1/session/observation?request_ordinal=" + requestOrdinal;
        return parseSessionObservation(request(connection, path, "GET", null,
                        READ_TIMEOUT_MS), connection.profileId(), executionProfileId,
                requestOrdinal, System.currentTimeMillis());
    }

    static SessionObservation parseSessionObservation(
            JSONObject response, String expectedActor, String expectedExecution,
            long expectedOrdinal, long receivedWallMs)
            throws IOException {
        if (response == null || !response.optBoolean("ok", false)) {
            throw new IOException(response == null ? "observation_unavailable"
                    : response.optString("reason", "observation_unavailable"));
        }
        if (requiredLong(response, "schema_version") != 2L) {
            throw new IOException("unsupported_observation_schema");
        }
        JSONObject producer = requiredObject(response, "producer");
        JSONObject scope = requiredObject(response, "scope");
        JSONObject correlation = requiredObject(response, "correlation");
        JSONObject provider = requiredObject(response, "provider");
        JSONObject game = requiredObject(response, "game");
        JSONObject process = requiredObject(response, "process");
        JSONObject window = requiredObject(response, "window");
        JSONObject capabilities = requiredObject(response, "capabilities");
        String boot = requiredString(producer, "boot_id").trim();
        long revision = requiredLong(producer, "revision");
        long observedAt = requiredLong(response, "observed_at_unix_ms");
        long freshFor = requiredLong(response, "fresh_for_ms");
        long stateSince = requiredLong(response, "state_since_unix_ms");
        String episode = requiredString(response, "state_episode").trim();
        long wallSkew = receivedWallMs > 0L
                ? observedAt >= receivedWallMs
                ? observedAt - receivedWallMs : receivedWallMs - observedAt
                : Long.MAX_VALUE;
        if (!boot.matches("[A-Za-z0-9._:-]{1,128}") || revision <= 0L
                || !episode.matches("[A-Za-z0-9._:-]{1,192}")
                || observedAt <= 0L || stateSince <= 0L || stateSince > observedAt
                || freshFor <= 0L || freshFor > 15_000L
                || receivedWallMs <= 0L || wallSkew > MAX_OBSERVATION_WALL_SKEW_MS) {
            throw new IOException("stale_or_malformed_observation");
        }
        String actor = requiredString(scope, "actor_profile_id");
        String execution = requiredString(scope, "execution_profile_id");
        long ordinal = requiredLong(correlation, "request_ordinal");
        String operation = requiredString(correlation, "operation_id");
        String streamSession = requiredString(correlation, "stream_session_id");
        long controlGeneration = requiredLong(correlation, "control_generation");
        long actorLeaseEpoch = requiredLong(correlation, "actor_lease_epoch");
        long streamStarted = requiredLong(correlation, "stream_started_unix_ms");
        long processStarted = requiredLong(process, "started_unix_ms");
        if (streamStarted < 0L || processStarted < 0L) {
            throw new IOException("stale_or_malformed_observation");
        }
        JSONObject streamer = requiredObject(response, "streamer");
        Evidence streamerEvidence = evidence(streamer, "evidence");
        int streamerAppId = requiredInt(streamer, "app_id");
        if (streamerEvidence == Evidence.PENDING
                || (streamerEvidence == Evidence.VERIFIED) != (streamerAppId > 0)
                || streamerAppId < 0) {
            throw new IOException("inconsistent_observation_evidence");
        }
        if (!GatewayConnection.normalizeProfileId(expectedActor).equals(actor)
                || !GatewayConnection.normalizeProfileId(expectedExecution).equals(execution)
                || ordinal != expectedOrdinal) {
            throw new IOException("observation_correlation_mismatch");
        }
        Evidence connectorEvidence = evidence(provider, "connector_evidence");
        Evidence gameEvidence = evidence(game, "evidence");
        Evidence processEvidence = evidence(process, "evidence");
        Evidence windowEvidence = evidence(window, "evidence");
        String gameId = requiredString(game, "id").trim();
        int processId = requiredInt(process, "process_id");
        String processToken = requiredString(process, "process_token");
        boolean ready = requiredBoolean(window, "ready");
        if ((gameEvidence == Evidence.VERIFIED) != isPlayniteId(gameId)
                || (gameEvidence == Evidence.ABSENT && !gameId.isEmpty())
                || (processEvidence == Evidence.VERIFIED
                && (gameEvidence != Evidence.VERIFIED || processId <= 0
                || !processToken.matches("[0-9a-f]{64}")))
                || (processEvidence != Evidence.VERIFIED
                && (processId != 0 || !processToken.isEmpty() || processStarted != 0L))
                || (ready && (windowEvidence != Evidence.VERIFIED
                || processEvidence != Evidence.VERIFIED))) {
            throw new IOException("inconsistent_observation_evidence");
        }
        String windowReason = requiredState(window, "reason");
        String previousGameId = "";
        String previousToken = "";
        String previousName = "";
        if ("previous_launch_game_running".equals(windowReason)) {
            JSONObject previous = requiredObject(window, "previous_launch");
            previousGameId = requiredString(previous, "game_id").trim();
            previousToken = requiredString(previous, "process_token");
            Object name = previous.opt("name");
            previousName = name instanceof String ? ((String) name).trim() : "";
            if (previousName.length() > 120) previousName = previousName.substring(0, 120);
            if (!isPlayniteId(previousGameId) || requiredInt(previous, "process_id") <= 0
                    || !previousToken.matches("[0-9a-f]{64}")) {
                throw new IOException("inconsistent_observation_evidence");
            }
        }
        int updateProgress = -1;
        boolean updatePatching = false;
        if ("game_updating".equals(windowReason)) {
            JSONObject update = requiredObject(window, "update");
            updateProgress = requiredInt(update, "progress");
            String phase = requiredString(update, "phase");
            if (updateProgress < -1 || updateProgress > 100
                    || !("downloading".equals(phase) || "patching".equals(phase))) {
                throw new IOException("invalid_observation_progress");
            }
            updatePatching = "patching".equals(phase);
        }
        SessionObservation parsed = new SessionObservation(boot, revision, observedAt, freshFor,
                episode, stateSince, actor, execution, ordinal,
                operation, streamSession, controlGeneration, actorLeaseEpoch,
                requiredState(provider, "state"),
                connectorEvidence,
                requiredState(game, "state"),
                gameEvidence, gameId, processEvidence, processId, processToken,
                windowEvidence, ready,
                requiredString(window, "target_kind"),
                requiredInt(window, "stable_samples"),
                windowReason,
                requiredBoolean(capabilities, "host_guide_allowed"),
                requiredBoolean(capabilities, "requires_connector"),
                requiredBoolean(capabilities, "start_before_stream"));
        parsed.previousLaunchGameId = previousGameId;
        parsed.previousLaunchProcessToken = previousToken;
        parsed.previousLaunchGameName = previousName;
        parsed.updateProgress = updateProgress;
        parsed.updatePatching = updatePatching;
        parsed.streamStartedUnixMs = streamStarted;
        parsed.processStartedUnixMs = processStarted;
        parsed.streamerAppId = streamerEvidence == Evidence.UNAVAILABLE
                ? SessionSnapshot.STREAMER_APP_UNKNOWN : streamerAppId;
        parsed.frozen = frozenGame(response);
        return parsed;
    }

    /** The required {@code frozen} fact: null for an empty slot. */
    static SessionSnapshot.FrozenGame frozenGame(JSONObject response) throws IOException {
        if (!response.has("frozen")) throw new IOException("missing_observation_frozen");
        if (response.isNull("frozen")) return null;
        JSONObject frozen = requiredObject(response, "frozen");
        boolean entitled = requiredBoolean(frozen, "entitled");
        boolean own = requiredBoolean(frozen, "own");
        String gameId = requiredString(frozen, "game_id").trim();
        String reason = requiredString(frozen, "reason");
        long frozenAt = requiredLong(frozen, "frozen_at_unix_ms");
        String actor = requiredString(frozen, "actor_profile_id");
        boolean consistent = entitled
                ? isPlayniteId(gameId) && frozenAt > 0L
                && reason.matches("user_switch|child_time_expired|host_sleep")
                : gameId.isEmpty() && !own;
        if (!consistent || frozenAt < 0L) {
            throw new IOException("inconsistent_observation_frozen");
        }
        return new SessionSnapshot.FrozenGame(entitled, own, gameId, reason, frozenAt,
                actor.trim());
    }

    private static JSONObject requiredObject(JSONObject parent, String name)
            throws IOException {
        JSONObject value = parent.optJSONObject(name);
        if (value == null) throw new IOException("missing_observation_" + name);
        return value;
    }

    private static String requiredString(JSONObject parent, String name) throws IOException {
        Object value = parent.opt(name);
        if (!(value instanceof String)) throw new IOException("missing_observation_" + name);
        return (String) value;
    }

    private static long requiredLong(JSONObject parent, String name) throws IOException {
        Object value = parent.opt(name);
        if (!(value instanceof Number)) throw new IOException("missing_observation_" + name);
        long result = ((Number) value).longValue();
        if (((Number) value).doubleValue() != (double) result) {
            throw new IOException("invalid_observation_" + name);
        }
        return result;
    }

    private static int requiredInt(JSONObject parent, String name) throws IOException {
        long value = requiredLong(parent, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException("invalid_observation_" + name);
        }
        return (int) value;
    }

    private static String requiredState(JSONObject parent, String name) throws IOException {
        String value = requiredString(parent, name).trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z_]{1,40}")) {
            throw new IOException("invalid_observation_" + name);
        }
        return value;
    }

    private static boolean requiredBoolean(JSONObject parent, String name) throws IOException {
        Object value = parent.opt(name);
        if (!(value instanceof Boolean)) throw new IOException("missing_observation_" + name);
        return (Boolean) value;
    }

    private static Evidence evidence(JSONObject parent, String name) throws IOException {
        try {
            return Evidence.valueOf(parent.optString(name, ""));
        } catch (IllegalArgumentException error) {
            throw new IOException("invalid_observation_evidence", error);
        }
    }

    private static String normalizedState(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z_]{1,40}") ? normalized : fallback;
    }

    void showPlayniteFullscreen(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection,
                "/api/v1/playnite/show-fullscreen", "POST", new JSONObject(),
                10_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "Playnite Fullscreen could not be restored."), 0);
        }
    }

    boolean gameInForeground(GatewayConnection connection, int readTimeoutMs)
            throws IOException {
        JSONObject response = request(connection, "/api/v1/session/foreground-game", "GET",
                null, readTimeoutMs);
        return response.optBoolean("ok", false)
                && response.optBoolean("game_foreground", false);
    }

    void focusPlayniteGame(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection,
                "/api/v1/game/focus", "POST", new JSONObject(),
                8_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The game window could not be focused."), 0);
        }
    }

    /** Game mode (Host Plus): the PC's windowed programs the host would close before the game. */
    List<GameModeStep.Program> gameModePrograms(GatewayConnection connection, String gameId)
            throws IOException {
        JSONObject response = request(connection, "/api/v1/game-mode/programs?game_id="
                + URLEncoder.encode(requestGameId(requireGameId(gameId)),
                StandardCharsets.UTF_8.name()), "GET", null, 8_000);
        return GameModeStep.parsePrograms(response);
    }

    /** The host's game mode for the stream: Windows Game Mode and muting other audio. */
    void startGameModeSession(GatewayConnection connection, String gameId,
                              boolean windowsGameMode, boolean muteOthers) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(requireGameId(gameId)));
            body.put("windows_game_mode", windowsGameMode);
            body.put("mute_others", muteOthers);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection, "/api/v1/game-mode/session", "POST", body,
                3_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException("game_mode_session_failed", 0);
        }
    }

    /** Ends one hung program the user confirmed; the host checks the hang again. */
    String forceCloseGameModeProgram(GatewayConnection connection, String gameId,
                                     String programId) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(requireGameId(gameId)));
            body.put("program", programId);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return GameModeStep.parseForceResult(request(connection, "/api/v1/game-mode/force-close",
                "POST", body, 15_000));
    }

    /** Graceful close of the chosen programs; returns those still open. */
    List<GameModeStep.Remaining> closeGameModePrograms(GatewayConnection connection, String gameId,
                                                       List<String> programIds) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(requireGameId(gameId)));
            body.put("programs", new JSONArray(programIds));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return GameModeStep.parseRemaining(request(connection, "/api/v1/game-mode/close",
                "POST", body, 15_000));
    }

    /** Starts the game; returns the Bridge boot that accepted the start. */
    String startGame(GatewayConnection connection, String gameId) throws IOException {
        if (!isPlayniteId(gameId)) {
            throw new IllegalArgumentException("Invalid game record ID");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(gameId));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/game/start", "POST", body, 25_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The game could not be started."), 0);
        }
        String rejection = gameStartRejectionReason(response);
        if (!rejection.isEmpty()) throw new GatewayException(rejection, 0);
        return gameStartBootId(response);
    }

    /**
     * Thaws the frozen game as the current game (Quick Resume); readiness is proved again, as
     * after {@link #startGame}. Returns the Bridge boot that accepted it; a refusal raises its
     * reason, like a refused start.
     */
    String thawGame(GatewayConnection connection, String gameId) throws IOException {
        JSONObject response = request(connection, "/api/v1/game/thaw", "POST",
                gameStopBody(gameId, null), 25_000);
        QuickResumeAnswer answer = QuickResumeAnswer.of(response);
        if (!answer.accepted) throw new GatewayException(answer.reason, 0);
        return gameStartBootId(response);
    }

    /**
     * Freezes the verified current game; the answer says what the host did. {@code
     * thawGameId} (the slot's game, never with an eviction) is the swap: the host makes it the
     * current game in the same operation.
     */
    QuickResumeAnswer freezeGame(GatewayConnection connection, String gameId,
                                 String processToken, String evictFrozenGameId,
                                 String thawGameId)
            throws IOException {
        if (processToken == null || !processToken.matches("[0-9a-f]{64}")) {
            throw new IOException("An exact process token is required for freeze");
        }
        JSONObject body = gameStopBody(gameId, processToken);
        try {
            if (evictFrozenGameId != null && !evictFrozenGameId.isEmpty()) {
                body.put("evict_frozen_game_id", requestGameId(evictFrozenGameId));
            }
            if (thawGameId != null && !thawGameId.isEmpty()) {
                body.put("thaw_game_id", requestGameId(thawGameId));
            }
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return QuickResumeAnswer.of(request(connection, "/api/v1/game/freeze", "POST", body,
                50_000));
    }

    /** Thaws and gracefully closes the frozen game; works without a stream. */
    QuickResumeAnswer closeFrozenGame(GatewayConnection connection, String gameId)
            throws IOException {
        return QuickResumeAnswer.of(request(connection, "/api/v1/game/close-frozen", "POST",
                gameStopBody(gameId, null), 30_000));
    }

    /** A Quick Resume answer: accepted, or the host's refusal reason. */
    public static final class QuickResumeAnswer {
        public final boolean accepted;
        public final String reason;
        /** {@code frozen_slot_occupied}: the game holding the slot; "" otherwise. */
        public final String frozenGameId;

        QuickResumeAnswer(boolean accepted, String reason, String frozenGameId) {
            this.accepted = accepted;
            this.reason = reason;
            this.frozenGameId = frozenGameId;
        }

        static QuickResumeAnswer of(JSONObject response) {
            JSONObject result = response.optJSONObject("result");
            if (result == null) result = new JSONObject();
            boolean accepted = response.optBoolean("ok", false)
                    && result.optBoolean("accepted", true);
            String reason = accepted ? "" : result.optString("reason",
                    response.optString("error", "rejected"));
            String occupant = result.optString("frozen_game_id", "").trim();
            return new QuickResumeAnswer(accepted,
                    accepted || !reason.isEmpty() ? reason : "rejected",
                    isPlayniteId(occupant) ? occupant : "");
        }
    }

    /** {@code result.boot_id}: the Bridge boot whose observation knows this start. */
    static String gameStartBootId(JSONObject response) throws IOException {
        JSONObject result = response.optJSONObject("result");
        String boot = result == null ? "" : result.optString("boot_id", "").trim();
        if (!boot.matches("[A-Za-z0-9._:-]{1,128}")) throw new IOException("game_start_unbound");
        return boot;
    }

    static String gameStartRejectionReason(JSONObject response) {
        if (response == null) return "game_start_rejected";
        if (response.has("accepted") && !response.optBoolean("accepted", true)) {
            return response.optString("reason", "game_start_rejected");
        }
        JSONObject result = response.optJSONObject("result");
        if (result != null && result.has("accepted")
                && !result.optBoolean("accepted", true)) {
            return result.optString("reason",
                    response.optString("reason", "game_start_rejected"));
        }
        return "";
    }

    void stopGameWithinControl(GatewayConnection connection, String gameId,
                               ControlOperationClient.Permit permit) throws IOException {
        if (permit == null) throw new NullPointerException("permit");
        if (!permit.processToken.matches("[0-9a-f]{64}")) {
            throw new IOException("Control permit has no exact process identity");
        }
        JSONObject body = gameStopBody(gameId, permit.processToken);
        try {
            body.put("control_operation", new JSONObject()
                    .put("operation_id", permit.operationId)
                    .put("generation", permit.generation)
                    .put("actor_lease_epoch", permit.actorLeaseEpoch));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/game/stop-verified", "POST", body, 30_000);
        stoppedCurrent(response, gameId, true);
    }

    void hardResetSession(GatewayConnection connection, String childSessionId)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("force", true);
            if (childSessionId != null && !childSessionId.trim().isEmpty()) {
                body.put("session_id", childSessionId.trim());
            }
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/session/hard-reset", "POST", body, 25_000);
        if (!response.optBoolean("ok", false)
                || !response.optBoolean("accepted", false)) {
            throw new GatewayException(response.optString("error",
                    "The host session could not be hard-reset."), 0);
        }
    }

    boolean stopGame(GatewayConnection connection, String gameId, String processToken)
            throws IOException {
        if (processToken == null || !processToken.matches("[0-9a-f]{64}")) {
            throw new IOException("An exact process token is required for provider stop");
        }
        JSONObject body = gameStopBody(gameId, processToken);
        JSONObject response = request(connection,
                "/api/v1/game/stop-verified", "POST", body, 30_000);
        return stoppedCurrent(response, gameId, true);
    }

    /** Drops this game's start that never produced a process; the host refuses once one exists. */
    void abandonStart(GatewayConnection connection, String gameId) throws IOException {
        JSONObject response = request(connection,
                "/api/v1/game/abandon-start", "POST", gameStopBody(gameId, null), 10_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error", "start_not_abandonable"), 0);
        }
    }

    static JSONObject gameStopBody(String gameId, String processToken) throws IOException {
        if (!isPlayniteId(gameId)) {
            throw new IllegalArgumentException("Invalid game record ID");
        }
        if (processToken != null && !processToken.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid process token");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(gameId));
            if (processToken != null) body.put("expected_process_token", processToken);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return body;
    }

    static boolean stoppedCurrent(JSONObject response, String gameId, boolean verified)
            throws IOException {
        if (!response.optBoolean("ok", false)
                || !response.optBoolean("accepted", true)) {
            throw new GatewayException(response.optString("error",
                    response.optString("reason", "The game could not be stopped.")), 0);
        }
        if (!verified) return true;
        JSONObject result = response.optJSONObject("result");
        if (result == null) result = response;
        if (!result.optBoolean("accepted", true)
                || !SessionSnapshot.normalize(gameId).equals(
                SessionSnapshot.normalize(result.optString("stopped_game_id", "")))
                || !(result.opt("stopped_current") instanceof Boolean)) {
            throw new GatewayException("game_stop_identity_unconfirmed", 0);
        }
        return result.optBoolean("stopped_current");
    }

    // ------------------------------------------------------------------ captures (#51)

    /** The largest clip the Gateway serves. */
    static final int CAPTURE_CLIP_MAX_BYTES = 256 * 1024 * 1024;

    /**
     * The profile's setting for this stream, sent once after it starts; the host keeps the
     * buffer of the last 30 s running while the stream lives.
     */
    void setCaptureBuffer(GatewayConnection connection, boolean enabled) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("enabled", enabled);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        request(connection, "/api/v1/captures/buffer", "POST", body, 10_000);
    }

    /**
     * A screenshot, or ({@code clip}) the buffer's last 30 s, saved on the PC. A refusal
     * throws its reason as the message, e.g. "buffer_starting" or "license_required".
     */
    void takeCapture(GatewayConnection connection, boolean clip) throws IOException {
        request(connection, clip ? "/api/v1/captures/clip" : "/api/v1/captures/screenshot",
                "POST", new JSONObject(), clip ? 75_000 : 25_000);
    }

    CaptureList captures(GatewayConnection connection) throws IOException {
        return CaptureList.parse(request(connection, "/api/v1/captures", "GET", null, 10_000));
    }

    /** A capture's JPEG thumbnail, or ({@code preview}) a screenshot at most 1920 px wide. */
    byte[] captureImage(GatewayConnection connection, String captureId, boolean preview)
            throws IOException {
        try {
            return transport.getBinary(connection, (preview ? "/api/v1/captures/preview?id="
                    : "/api/v1/captures/thumbnail?id=") + captureQuery(captureId), "image/jpeg",
                    preview ? 20_000 : 12_000);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    /** A clip downloaded into {@code target}; a failed download leaves no file. */
    void downloadCaptureClip(GatewayConnection connection, String captureId, File target)
            throws IOException {
        if (!captureId.endsWith(".mp4")) throw new IllegalArgumentException("Not a clip");
        try {
            transport.downloadToFile(connection, "/api/v1/captures/video?id="
                    + captureQuery(captureId), "video/mp4", CAPTURE_CLIP_MAX_BYTES, target, 30_000);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    /** The host moves the file to the PC's Recycle Bin. */
    void deleteCapture(GatewayConnection connection, String captureId) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("id", CaptureList.requireId(captureId));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        request(connection, "/api/v1/captures/delete", "POST", body, 10_000);
    }

    /**
     * The host-wide drive for captures ({@code drive}, "" for each user's Videos folder) and
     * the host's fixed drives ({@code drives}, as Disk space lists them); adults only.
     */
    JSONObject captureDrive(GatewayConnection connection) throws IOException {
        return request(connection, "/api/v1/captures/drive", "GET", null, 10_000);
    }

    /**
     * Saves new captures of every profile on {@code drive} ("D:\", or "" for the Videos
     * folder). A drive the PC cannot write to throws "capture_drive_unavailable".
     */
    void setCaptureDrive(GatewayConnection connection, String drive) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("drive", drive);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        request(connection, "/api/v1/captures/drive", "POST", body, 15_000);
    }

    private static String captureQuery(String captureId) throws IOException {
        return URLEncoder.encode(CaptureList.requireId(captureId),
                StandardCharsets.UTF_8.name());
    }

    /** Install locations per launcher; the host lists only what it would accept. */
    JSONObject installTargets(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection, "/api/v1/install/targets", "GET", null, 10_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "Install locations are unavailable."), 0);
        }
        return response;
    }

    /** Drives and uninstall candidates for Settings › Disk space; the host refuses a child. */
    JSONObject storage(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection, "/api/v1/storage", "GET", null, 10_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "Disk space is unavailable."), 0);
        }
        return response;
    }

    /**
     * Bytes the game needs on disk, 0 when no launcher knows. Legendary may download the
     * manifest first: the Gateway waits up to 20 s for it, this a little longer.
     */
    long installSize(GatewayConnection connection, String gameId) throws IOException {
        JSONObject response = request(connection, "/api/v1/install/size?game_id="
                + URLEncoder.encode(requestGameId(gameId), StandardCharsets.UTF_8.name()),
                "GET", null, 22_000);
        return Math.max(0L, response.optLong("size_bytes", 0L));
    }

    /** {@code installTarget} null or empty: the host's default location. */
    void installPlayniteGame(GatewayConnection connection, String gameId, String installTarget)
            throws IOException {
        if (!isPlayniteId(gameId)) {
            throw new IllegalArgumentException("Invalid game record ID");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(gameId));
            if (installTarget != null && !installTarget.isEmpty()) {
                body.put("install_target", installTarget);
            }
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/game/install", "POST", body, 15_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The game installation could not be started."), 0);
        }
    }

    void uninstallPlayniteGame(GatewayConnection connection, String gameId) throws IOException {
        if (!isPlayniteId(gameId)) {
            throw new IllegalArgumentException("Invalid game record ID");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(gameId));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/game/uninstall", "POST", body, 15_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The game could not be uninstalled."), 0);
        }
    }

    void focusPlayniteInstallation(GatewayConnection connection, String gameId) throws IOException {
        if (!isPlayniteId(gameId)) {
            throw new IllegalArgumentException("Invalid Playnite game ID");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(gameId));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/game/install/focus", "POST", body, 8_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The installation window could not be opened."), 0);
        }
    }

    boolean verifyPlayniteInstallation(GatewayConnection connection, String gameId) throws IOException {
        if (!isPlayniteId(gameId)) {
            throw new IllegalArgumentException("Invalid Playnite game ID");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("game_id", requestGameId(gameId));
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        JSONObject response = request(connection,
                "/api/v1/game/install/verify", "POST", body, 10_000);
        if (!response.optBoolean("ok", false)) {
            throw new GatewayException(response.optString("error",
                    "The installation could not be verified."), 0);
        }
        JSONObject result = response.optJSONObject("result");
        return result != null && !result.optBoolean("requires_attention", true);
    }

    PlayniteEvents getPlayniteEvents(GatewayConnection connection, long after) throws IOException {
        return getPlayniteEvents(connection, after, "");
    }

    PlayniteEvents getPlayniteEvents(GatewayConnection connection, long after,
                                     String transitionId) throws IOException {
        if (after < 0) throw new IllegalArgumentException("Invalid Playnite event sequence");
        String correlation = transitionId == null ? "" : transitionId.trim();
        if (correlation.length() > 128
                || !correlation.matches("[A-Za-z0-9._:-]*")) {
            throw new IllegalArgumentException("Invalid transition ID");
        }
        JSONObject response = request(connection,
                "/api/v1/game/events?after=" + after + "&transition_id=" +
                        URLEncoder.encode(correlation, StandardCharsets.UTF_8.name()),
                "GET", null, 25_000);
        String echoedCorrelation = response.optString("transition_id", "");
        if (!echoedCorrelation.isEmpty() && !correlation.equals(echoedCorrelation)) {
            throw new GatewayException("Mismatched transition response.", 409);
        }
        JSONObject envelope = response.optJSONObject("events");
        JSONArray values = envelope != null ? envelope.optJSONArray("events") : null;
        List<PlayniteEvent> result = new ArrayList<>();
        long latest = after;
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                long sequence = value.optLong("sequence", -1);
                if (sequence <= after) continue;
                JSONObject payload = value.optJSONObject("payload");
                String gameId = payload != null ? payload.optString("id", "") : "";
                result.add(new PlayniteEvent(sequence,
                        value.optString("event", ""), gameId,
                        payload != null ? payload.optString("name", "") : "",
                        payload != null ? payload.optString("reason", "") : ""));
                latest = Math.max(latest, sequence);
            }
        }
        return new PlayniteEvents(result, latest);
    }

    static PlayniteLibrary parsePlayniteLibrary(JSONObject library) {
        JSONObject safe = library == null ? new JSONObject() : library;
        JSONArray values = safe.optJSONArray("games");
        List<PlayniteGame> games = new ArrayList<>();
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                String id = value.optString("id", "");
                if (!isPlayniteId(id)) continue;
                String name = value.optString("name", "").trim();
                if (name.isEmpty()) continue;
                String normalizedId = requestGameId(id);
                String source = firstText(value, "source", "sourceName", "source_name");
                String provider = providerOrLegacy(firstText(value, "provider"), source);
                String providerGameId = firstText(
                        value, "providerGameId", "provider_game_id");
                boolean exactIdentity = hasExactProviderIdentity(
                        normalizedId, provider, providerGameId);
                String libraryKey = firstText(value, "libraryKey", "library_key");
                String libraryName = firstText(value, "libraryName", "library_name");
                long seconds = value.has("playtimeSeconds")
                        ? value.optLong("playtimeSeconds", 0L)
                        : value.has("playtime_seconds")
                        ? value.optLong("playtime_seconds", 0L)
                        : value.has("playtimeMinutes")
                        ? value.optLong("playtimeMinutes", 0L) * 60L
                        : value.has("playtime_minutes")
                        ? value.optLong("playtime_minutes", 0L) * 60L
                        : value.optLong("playtime", 0L);
                String metadataPlayniteId = firstText(
                        value, "playniteGameId", "playnite_game_id");
                JSONObject providerCapabilities = value.optJSONObject("providerCapabilities");
                if (providerCapabilities == null) {
                    providerCapabilities = value.optJSONObject("provider_capabilities");
                }
                boolean requiresConnector = providerCapabilities == null
                        ? !metadataPlayniteId.isEmpty()
                        : providerCapabilities.optBoolean("requiresConnector",
                        providerCapabilities.optBoolean("requires_connector", true));
                boolean startBeforeStream = providerCapabilities != null
                        && providerCapabilities.optBoolean("startBeforeStream",
                        providerCapabilities.optBoolean("start_before_stream", false));
                PlayniteGame parsedGame = new PlayniteGame(normalizedId, name,
                        value.optBoolean("installed", value.optBoolean("isInstalled", false)),
                        value.optBoolean("installing", value.optBoolean("isInstalling", false)),
                        value.optBoolean("hidden", value.optBoolean("isHidden", false)),
                        value.optBoolean("favorite", value.optBoolean("isFavorite", false)),
                        firstText(value, "cover", "coverImage", "cover_image", "boxArtPath"),
                        firstText(value, "background", "backgroundImage", "background_image",
                                "backgroundImagePath"),
                        firstText(value, "hero", "heroImage", "hero_image",
                                "heroImagePath"),
                        firstText(value, "lastPlayed", "last_played", "lastActivity"),
                        firstText(value, "description", "overview", "summary"),
                        Math.max(0, value.has("playCount")
                                ? value.optInt("playCount", 0)
                                : value.optInt("play_count", 0)),
                        source,
                        joinedText(value, "genres", "genre"),
                        firstText(value, "artworkVersion", "artwork_version", "artworkHash",
                                "artwork_hash", "cover"),
                        Math.max(0L, seconds),
                        value.optBoolean("installRequiresAttention",
                                value.optBoolean("install_requires_attention", false)),
                        firstText(value, "installAttentionReason",
                                "install_attention_reason"),
                        firstText(value, "installWindowTitle", "install_window_title"),
                        firstText(value, "installLauncher", "install_launcher"),
                        firstText(value, "operationState", "operation_state"),
                        value.isNull("operationProgress") ? -1
                                : value.optInt("operationProgress", -1),
                        value.optBoolean("uninstalling", false),
                        provider,
                        providerGameId,
                        metadataPlayniteId,
                        libraryKey.isEmpty() ? PlayniteGame.sourceKey(source) : libraryKey,
                        libraryName.isEmpty() ? PlayniteGame.sourceKey(source).equals("playnite")
                                ? "Playnite" : source : libraryName,
                        capability(value, "launch", exactIdentity),
                        capability(value, "install", exactIdentity),
                        capability(value, "uninstall", exactIdentity),
                        requiresConnector, startBeforeStream);
                parsedGame.updateState = value.optString("updateState", "").trim();
                parsedGame.updateProgress = value.isNull("updateProgress") ? -1
                        : value.optInt("updateProgress", -1);
                parsedGame.ageRating = GameAgeRating.parse(value.optJSONObject("ageRating"));
                games.add(parsedGame);
            }
        }
        return new PlayniteLibrary(games, safe.optString("next_cursor", ""),
                safe.optInt("total", games.size()),
                firstText(safe, "revision", "library_revision", "hash"),
                firstText(safe, "api_version", "apiVersion", "version"));
    }

    private static String firstText(JSONObject value, String... keys) {
        for (String key : keys) {
            String result = value.optString(key, "").trim();
            if (!result.isEmpty()) return result;
        }
        return "";
    }

    private static boolean capability(JSONObject value, String name, boolean exactIdentity) {
        JSONObject capabilities = value.optJSONObject("capabilities");
        return exactIdentity && (capabilities == null || capabilities.optBoolean(name, false));
    }

    private static String joinedText(JSONObject value, String... keys) {
        for (String key : keys) {
            JSONArray array = value.optJSONArray(key);
            if (array != null) {
                StringBuilder result = new StringBuilder();
                for (int index = 0; index < array.length(); index++) {
                    String item = array.optString(index, "").trim();
                    if (item.isEmpty()) continue;
                    if (result.length() > 0) result.append(", ");
                    result.append(item);
                }
                if (result.length() > 0) return result.toString();
            }
            String text = value.optString(key, "").trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static final Pattern PLAYNITE_GUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern PROVIDER_SCOPED_ID = Pattern.compile(
            "[a-z][a-z0-9_-]{1,31}:[A-Za-z0-9._-]{1,128}");
    private static final Pattern PROVIDER_NAME = Pattern.compile("[a-z][a-z0-9_-]{1,31}");
    private static final Pattern PLAYNITE_RECORD_ID = Pattern.compile(
            "(?i)(?:playnite:)?[0-9a-f]{8}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    static boolean isPlayniteId(String value) {
        return value != null && (PLAYNITE_GUID.matcher(value).matches()
                || PROVIDER_SCOPED_ID.matcher(value).matches());
    }

    static String providerOrLegacy(String provider, String source) {
        String explicit = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (PROVIDER_NAME.matcher(explicit).matches()) return explicit;
        String legacy = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return PROVIDER_NAME.matcher(legacy).matches() ? legacy : "playnite";
    }

    static boolean hasExactProviderIdentity(String id, String provider,
                                            String providerGameId) {
        String recordId = id == null ? "" : id.trim();
        String owner = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        String providerId = providerGameId == null ? "" : providerGameId.trim();
        if ("playnite".equals(owner)) {
            return PLAYNITE_RECORD_ID.matcher(recordId).matches();
        }
        return !providerId.isEmpty() && recordId.equals(owner + ":" + providerId);
    }

    private static String requestGameId(String value) {
        String result = value == null ? "" : value.trim();
        int separator = result.indexOf(':');
        return separator > 0
                ? result.substring(0, separator).toLowerCase(Locale.ROOT)
                + result.substring(separator) : result.toLowerCase(Locale.ROOT);
    }

    DiscordStatus getDiscordStatus(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection, "/api/v1/discord/status", "GET",
                null, READ_TIMEOUT_MS);
        return new DiscordStatus(
                response.optBoolean("bridge_online", false),
                response.optBoolean("rpc_connected", false),
                response.optBoolean("authenticated", false),
                response.optString("error", ""));
    }

    DiscordHome getDiscordHome(GatewayConnection connection, boolean force) throws IOException {
        JSONObject response = request(connection,
                "/api/v1/discord/home" + (force ? "?force=true" : ""), "GET",
                null, 12_000);
        JSONObject home = response.optJSONObject("home");
        return new DiscordHome(
                parseSavedChannels(home != null ? home.optJSONArray("favorites") : null, true),
                parseSavedChannels(home != null ? home.optJSONArray("recent") : null, false),
                parseGuilds(home != null ? home.optJSONArray("guilds") : null));
    }

    List<DiscordChannel> getDiscordChannels(GatewayConnection connection, DiscordGuild guild,
                                            boolean force) throws IOException {
        if (!isDiscordId(guild.id)) throw new IllegalArgumentException("Invalid Discord guild ID");
        JSONObject response = request(connection,
                "/api/v1/discord/channels?guild_id=" + guild.id + (force ? "&force=true" : ""), "GET",
                null, 15_000);
        JSONObject value = response.optJSONObject("channels");
        JSONArray channels = value != null ? value.optJSONArray("channels") : null;
        List<DiscordChannel> result = new ArrayList<>();
        if (channels == null) return result;
        for (int index = 0; index < channels.length(); index++) {
            JSONObject channel = channels.optJSONObject(index);
            if (channel == null) continue;
            String id = channel.optString("id", "");
            if (!isDiscordId(id)) continue;
            result.add(new DiscordChannel(id, guild.id, guild.name,
                    channel.optString("name", "Voice channel"),
                    channel.optInt("people", 0), channel.optBoolean("favorite", false)));
        }
        return result;
    }

    DiscordVoice getDiscordVoice(GatewayConnection connection, boolean force) throws IOException {
        JSONObject response = request(connection,
                "/api/v1/discord/voice" + (force ? "?force=true" : ""), "GET",
                null, 12_000);
        JSONObject voice = response.optJSONObject("voice");
        if (voice == null) voice = new JSONObject();
        JSONObject channel = voice.optJSONObject("channel");
        JSONArray participants = voice.optJSONArray("participants");
        List<DiscordParticipant> participantList = new ArrayList<>();
        if (participants != null) {
            for (int index = 0; index < participants.length(); index++) {
                JSONObject participant = participants.optJSONObject(index);
                if (participant == null) continue;
                String id = participant.optString("id", "");
                if (!isDiscordId(id)) continue;
                participantList.add(new DiscordParticipant(id,
                        participant.optString("name", "Discord user"),
                        participant.optString("username", ""),
                        participant.optInt("volume", 100),
                        participant.optBoolean("muted", false),
                        participant.optBoolean("deafened", false),
                        participant.optBoolean("speaking", false),
                        participant.optBoolean("is_self", false),
                        participant.optBoolean("bot", false),
                        participant.optBoolean("can_set_volume", true),
                        participant.optString("audio_error", "")));
            }
        }
        return new DiscordVoice(
                voice.optBoolean("connected", false),
                channel != null ? channel.optString("id", "") : "",
                channel != null ? channel.optString("name", "") : "",
                channel != null ? channel.optString("guild_id", "") : "",
                voice.optBoolean("mute", false),
                voice.optBoolean("deafen", false),
                participantList.size(), participantList);
    }

    JSONObject connectDiscord(GatewayConnection connection, boolean force) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("force", force);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return discordAction(connection, "connect", body, 25_000);
    }

    JSONObject startDiscord(GatewayConnection connection) throws IOException {
        // The host answers once Discord's RPC is up, or after its own 25 s wait.
        return discordAction(connection, "start", new JSONObject(), 40_000);
    }

    JSONObject joinDiscordChannel(GatewayConnection connection, DiscordChannel channel) throws IOException {
        return joinDiscordChannel(connection, channel.id, channel.guildId,
                channel.guildName, channel.name);
    }

    JSONObject joinDiscordChannel(GatewayConnection connection, String channelId, String guildId,
                                  String guildName, String channelName) throws IOException {
        if (!isDiscordId(channelId) || !isDiscordId(guildId)) {
            throw new IllegalArgumentException("Invalid Discord channel");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("channel_id", channelId);
            body.put("guild_id", guildId);
            body.put("guild_name", guildName != null ? guildName : "");
            body.put("channel_name", channelName != null ? channelName : "");
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return discordAction(connection, "join", body, 25_000);
    }

    JSONObject leaveDiscordChannel(GatewayConnection connection) throws IOException {
        return discordAction(connection, "leave", new JSONObject(), 15_000);
    }

    JSONObject setDiscordVoiceFlag(GatewayConnection connection, String action, String value) throws IOException {
        if (!"mute".equals(action) && !"deafen".equals(action)) {
            throw new IllegalArgumentException("Unknown Discord voice action");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("value", value);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return discordAction(connection, action, body, 12_000);
    }

    JSONObject changeDiscordParticipantVolume(GatewayConnection connection, String userId,
                                               int delta) throws IOException {
        if (!isDiscordId(userId) || (delta != -10 && delta != 10)) {
            throw new IllegalArgumentException("Invalid Discord participant volume change");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("delta", delta);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return discordAction(connection, "user-volume", body, 12_000);
    }

    JSONObject setDiscordParticipantVolume(GatewayConnection connection, String userId,
                                            int volume) throws IOException {
        if (!isDiscordId(userId)
                || volume < DiscordFeatureContract.MIN_PARTICIPANT_VOLUME
                || volume > DiscordFeatureContract.MAX_PARTICIPANT_VOLUME
                || volume % DiscordFeatureContract.PARTICIPANT_VOLUME_STEP != 0) {
            throw new IllegalArgumentException("Invalid Discord participant volume");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("volume", volume);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return discordAction(connection, "user-volume", body, 12_000);
    }

    JSONObject toggleDiscordParticipantMute(GatewayConnection connection, String userId)
            throws IOException {
        if (!isDiscordId(userId)) {
            throw new IllegalArgumentException("Invalid Discord participant");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return discordAction(connection, "user-mute", body, 12_000);
    }

    DiscordAudioState getDiscordAudioState(GatewayConnection connection) throws IOException {
        JSONObject response = request(connection, "/api/v1/discord/audio", "GET",
                null, 15_000);
        JSONObject audio = response.optJSONObject("audio");
        return new DiscordAudioState(parseAudioDevices(
                audio == null ? null : audio.optJSONArray("discord_devices")));
    }

    JSONObject selectDiscordAudioDevice(GatewayConnection connection, AudioDevice device)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("kind", validAudioDevice(device).flow);
            body.put("device_id", device.id);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return discordAction(connection, "audio/select", body, 15_000);
    }

    SystemAudioState getSystemAudioState(GatewayConnection connection) throws IOException {
        return parseSystemAudio(request(connection, "/api/v1/system/audio", "GET", null, 15_000));
    }

    SystemAudioState selectSystemAudioDevice(GatewayConnection connection, AudioDevice device)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("device_id", validAudioDevice(device).id);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return systemAudioAction(connection, "select", body, 15_000);
    }

    SystemAudioState setSystemVolume(GatewayConnection connection, int value)
            throws IOException {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Invalid system volume");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("value", value);
        } catch (JSONException impossible) {
            throw new IOException(impossible);
        }
        return systemAudioAction(connection, "volume", body, 12_000);
    }

    SystemAudioState toggleSystemMute(GatewayConnection connection) throws IOException {
        return systemAudioAction(connection, "mute", new JSONObject(), 12_000);
    }

    private SystemAudioState systemAudioAction(GatewayConnection connection, String action,
                                               JSONObject body, int timeoutMs)
            throws IOException {
        return parseSystemAudio(request(connection, "/api/v1/system/audio/" + action, "POST",
                body, timeoutMs));
    }

    static SystemAudioState parseSystemAudio(JSONObject response) {
        JSONObject audio = response.optJSONObject("audio");
        if (audio == null) audio = new JSONObject();
        return new SystemAudioState(audio.optBoolean("available", false),
                audio.optInt("volume", 0), audio.optBoolean("muted", false),
                parseAudioDevices(audio.optJSONArray("devices")), audio.optString("error", ""));
    }

    private static AudioDevice validAudioDevice(AudioDevice device) {
        if (device == null || device.id == null ||
                !device.id.matches("[A-Za-z0-9._:{}-]{1,220}") ||
                (!"input".equals(device.flow) && !"output".equals(device.flow))) {
            throw new IllegalArgumentException("Invalid audio device");
        }
        return device;
    }

    private JSONObject discordAction(GatewayConnection connection, String action, JSONObject body,
                                     int timeoutMs) throws IOException {
        return request(connection, "/api/v1/discord/" + action, "POST",
                body, timeoutMs);
    }

    private static List<DiscordGuild> parseGuilds(JSONArray values) {
        List<DiscordGuild> result = new ArrayList<>();
        if (values == null) return result;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            String id = value.optString("id", "");
            if (!isDiscordId(id)) continue;
            result.add(new DiscordGuild(id, value.optString("name", "Discord server")));
        }
        return result;
    }

    private static List<AudioDevice> parseAudioDevices(JSONArray values) {
        List<AudioDevice> result = new ArrayList<>();
        if (values == null) return result;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            String id = value.optString("id", "");
            String flow = value.optString("flow", "");
            if (!id.matches("[A-Za-z0-9._:{}-]{1,220}") ||
                    (!"input".equals(flow) && !"output".equals(flow))) continue;
            result.add(new AudioDevice(id, value.optString("name", "Audio device"),
                    flow, value.optBoolean("is_default", false)));
        }
        return result;
    }

    private static List<DiscordChannel> parseSavedChannels(JSONArray values, boolean favorite) {
        List<DiscordChannel> result = new ArrayList<>();
        if (values == null) return result;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            String id = value.optString("channel_id", "");
            String guildId = value.optString("guild_id", "");
            if (!isDiscordId(id) || !isDiscordId(guildId)) continue;
            result.add(new DiscordChannel(id, guildId,
                    value.optString("guild_name", "Discord"),
                    value.optString("channel_name", "Voice channel"), -1, favorite));
        }
        return result;
    }

    static boolean isDiscordId(String value) {
        return value != null && value.matches("[0-9]{5,32}");
    }

    private static boolean available(JSONObject capabilities, String name) {
        if (capabilities == null) return false;
        JSONObject capability = capabilities.optJSONObject(name);
        return capability != null && capability.optBoolean("available", false);
    }

    JSONObject request(GatewayConnection connection, String path, String method,
                       JSONObject body, int readTimeoutMs) throws IOException {
        try {
            return "POST".equals(method)
                    ? transport.postJson(connection, path, body, readTimeoutMs)
                    : transport.getJson(connection, path, readTimeoutMs);
        } catch (GatewayTransport.GatewayException error) {
            throw mapException(error);
        }
    }

    private static GatewayException mapException(GatewayTransport.GatewayException error) {
        return new GatewayException(error.getMessage(), error.statusCode(),
                error.retryAfterSeconds());
    }
}
