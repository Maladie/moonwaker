package com.limelight.console;

import android.content.Context;

import com.limelight.R;
import com.limelight.LimeLog;
import com.limelight.gateway.GatewayConnection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The host side of the Discord Community panel: its Gateway requests (servers, channels, voice,
 * host options and audio) on the paired host's connection. Pairing, profiles and forgetting a
 * host belong to the host tile and its panel.
 */
final class DiscordPanelController {
    interface CommunityHomeCallback {
        void onHome(HostGatewayClient.DiscordHome home);
        /** {@code message}: why, as a string resource the Community feed shows. */
        void onUnavailable(int message);
    }
    interface CommunityChannelsCallback {
        void onChannels(List<HostGatewayClient.DiscordChannel> channels);
        void onUnavailable();
    }
    interface CommunityActionCallback {
        void onComplete(HostGatewayClient.DiscordVoice verifiedVoice);
        void onError(String safeMessage);
    }
    enum CommunityVoiceAction { LEAVE, MUTE, DEAFEN }
    interface CommunityVoiceCallback {
        void onVoice(HostGatewayClient.DiscordVoice voice);
        void onError(String safeMessage);
    }

    private final Context context;
    private final android.os.Handler mainHandler;
    private final ExecutorService executor;
    private final HostGatewayClient client = new HostGatewayClient();
    private final HostGatewayStore store;
    private final AtomicInteger requestGeneration = new AtomicInteger();

    private String hostUuid;
    private String hostAddress;

    DiscordPanelController(Context context, android.os.Handler mainHandler, ExecutorService executor) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.executor = executor;
        store = new HostGatewayStore(context);
    }

    void destroy() {
        requestGeneration.incrementAndGet();
    }

    void closePanel() {
        requestGeneration.incrementAndGet();
    }

    static int discordIndicatorColor(boolean bridgeOnline, boolean authenticated,
                                     boolean rpcConnected, boolean voiceConnected) {
        if (!bridgeOnline) return 0xFFFFB74D;
        if (!authenticated || !rpcConnected) return 0xFFFF6B6B;
        return voiceConnected ? 0xFF36B96C : 0xFF4DA3FF;
    }

    static boolean discordNeedsReconnect(HostGatewayClient.DiscordStatus status) {
        return status != null && !status.rpcConnected;
    }

    private HostGatewayClient.DiscordHome loadDiscordHome(
            GatewayConnection connection, boolean force) throws Exception {
        try {
            return client.getDiscordHome(connection, force);
        } catch (Exception firstFailure) {
            HostGatewayClient.DiscordStatus status = client.getDiscordStatus(connection);
            if (shouldConnectDiscord(status)) {
                client.connectDiscord(connection, false);
                Thread.sleep(350L);
                return client.getDiscordHome(connection, true);
            }
            if (!status.bridgeOnline || !status.rpcConnected || !status.authenticated) {
                throw firstFailure;
            }
            // A healthy RPC bridge can briefly return stale home state while its
            // guild cache refreshes. Confirm health, then make one forced retry.
            Thread.sleep(350L);
            return client.getDiscordHome(connection, true);
        }
    }

    static boolean shouldConnectDiscord(HostGatewayClient.DiscordStatus status) {
        return status != null && status.bridgeOnline
                && (!status.rpcConnected || !status.authenticated);
    }

    private GatewayConnection connection() {
        return hostUuid == null ? null : store.loadForHost(hostUuid, hostAddress);
    }

    void loadCommunityHome(String uuid, String address, CommunityHomeCallback callback) {
        hostUuid = uuid;
        hostAddress = address;
        GatewayConnection current = connection();
        if (current == null) {
            callback.onUnavailable(R.string.discord_community_host_unavailable);
            return;
        }
        if (!store.isDiscordEnabled(hostUuid, current.profileId())) {
            callback.onUnavailable(R.string.discord_community_host_disabled);
            return;
        }
        executor.execute(() -> {
            try {
                HostGatewayClient.DiscordHome home = loadDiscordHome(current, false);
                mainHandler.post(() -> callback.onHome(home));
            } catch (Exception error) {
                int message = homeUnavailableMessage(error);
                mainHandler.post(() -> callback.onUnavailable(message));
            }
        });
    }

    void loadCommunityGuildChannels(String uuid, String address,
                                    HostGatewayClient.DiscordGuild guild,
                                    CommunityChannelsCallback callback) {
        hostUuid = uuid;
        hostAddress = address;
        GatewayConnection current = connection();
        if (current == null || !store.isDiscordEnabled(hostUuid, current.profileId())) {
            callback.onUnavailable();
            return;
        }
        executor.execute(() -> {
            try {
                List<HostGatewayClient.DiscordChannel> channels = client.getDiscordChannels(current, guild, false);
                mainHandler.post(() -> callback.onChannels(channels));
            } catch (Exception ignored) {
                mainHandler.post(callback::onUnavailable);
            }
        });
    }

    void joinCommunityChannel(String uuid, String address,
                              HostGatewayClient.DiscordChannel channel,
                              CommunityActionCallback callback) {
        hostUuid = uuid;
        hostAddress = address;
        GatewayConnection current = connection();
        if (current == null || !store.isDiscordEnabled(hostUuid, current.profileId())) {
            callback.onError(context.getString(R.string.discord_join_not_confirmed));
            return;
        }
        final String requestHostUuid = hostUuid;
        final String requestProfileId = current.profileId();
        executor.execute(() -> {
            try {
                client.joinDiscordChannel(current, channel);
                HostGatewayClient.DiscordVoice verifiedVoice = confirmCommunityJoin(current, channel);
                if (verifiedVoice == null) {
                    String message = context.getString(R.string.discord_join_not_confirmed);
                    LimeLog.warning("Community Discord join failed (HTTP 200): " + message);
                    mainHandler.post(() -> callback.onError(message));
                    return;
                }
                store.saveLastDiscordChannel(requestHostUuid, requestProfileId, channel.id,
                        channel.guildId, channel.guildName, channel.name);
                mainHandler.post(() -> callback.onComplete(verifiedVoice));
            } catch (Exception error) {
                int statusCode = error instanceof HostGatewayClient.GatewayException
                        ? ((HostGatewayClient.GatewayException) error).statusCode : 0;
                String message = communityVoiceError(error);
                LimeLog.warning("Community Discord join failed (HTTP " + statusCode + "): "
                        + safeCommunityJoinError(error));
                mainHandler.post(() -> callback.onError(message));
            }
        });
    }

    void loadCommunityVoice(String uuid, String address,
                            CommunityVoiceCallback callback) {
        configureCommunityHost(uuid, address);
        GatewayConnection current = connection();
        if (current == null || !store.isDiscordEnabled(hostUuid, current.profileId())) {
            callback.onError(context.getString(R.string.discord_join_not_confirmed));
            return;
        }
        final int request = requestGeneration.get();
        executor.execute(() -> {
            try {
                HostGatewayClient.DiscordVoice voice = client.getDiscordVoice(current, true);
                mainHandler.post(() -> { if (request == requestGeneration.get()) callback.onVoice(voice); });
            } catch (Exception error) {
                String message = communityVoiceError(error);
                LimeLog.warning("Community Discord voice refresh failed: " + safeCommunityJoinError(error));
                mainHandler.post(() -> { if (request == requestGeneration.get()) callback.onError(message); });
            }
        });
    }

    void performCommunityVoiceAction(String uuid, String address,
                                     CommunityVoiceAction action, CommunityVoiceCallback callback) {
        configureCommunityHost(uuid, address);
        GatewayConnection current = connection();
        if (current == null || !store.isDiscordEnabled(hostUuid, current.profileId())) {
            callback.onError(context.getString(R.string.discord_join_not_confirmed));
            return;
        }
        final int request = requestGeneration.get();
        executor.execute(() -> {
            try {
                HostGatewayClient.DiscordVoice before = client.getDiscordVoice(current, true);
                if (action == CommunityVoiceAction.LEAVE) client.leaveDiscordChannel(current);
                else client.setDiscordVoiceFlag(current,
                        action == CommunityVoiceAction.MUTE ? "mute" : "deafen", "toggle");
                HostGatewayClient.DiscordVoice voice = confirmCommunityVoiceAction(current, action, before);
                if (voice == null) {
                    String message = context.getString(R.string.discord_join_not_confirmed);
                    mainHandler.post(() -> { if (request == requestGeneration.get()) callback.onError(message); });
                    return;
                }
                mainHandler.post(() -> { if (request == requestGeneration.get()) callback.onVoice(voice); });
            } catch (Exception error) {
                String message = communityVoiceError(error);
                LimeLog.warning("Community Discord voice action failed: " + safeCommunityJoinError(error));
                mainHandler.post(() -> { if (request == requestGeneration.get()) callback.onError(message); });
            }
        });
    }

    private HostGatewayClient.DiscordVoice confirmCommunityVoiceAction(GatewayConnection connection,
                                                                        CommunityVoiceAction action,
                                                                        HostGatewayClient.DiscordVoice before)
            throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HostGatewayClient.DiscordVoice voice = client.getDiscordVoice(connection, true);
            boolean confirmed = action == CommunityVoiceAction.LEAVE ? !voice.connected
                    : voice.connected && (action == CommunityVoiceAction.MUTE
                    ? voice.muted != before.muted : voice.deafened != before.deafened);
            if (confirmed) return voice;
            if (attempt < 2) {
                try { Thread.sleep(350L); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
            }
        }
        return null;
    }

    private void configureCommunityHost(String uuid, String address) {
        hostUuid = uuid;
        hostAddress = address;
    }

    private HostGatewayClient.DiscordVoice confirmCommunityJoin(GatewayConnection connection,
                                                                 HostGatewayClient.DiscordChannel channel)
            throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HostGatewayClient.DiscordVoice voice = client.getDiscordVoice(connection, true);
            if (matchesJoinedVoice(channel.id, voice)) return voice;
            if (attempt < 2) {
                try {
                    Thread.sleep(350L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    static boolean matchesJoinedVoice(String channelId, HostGatewayClient.DiscordVoice voice) {
        return voice != null && voice.connected && channelId != null && channelId.equals(voice.channelId);
    }

    /**
     * The Gateway answers 409 on Discord routes while Discord is not ready on the host (Bridge
     * offline, client not running or not authorized).
     */
    static boolean discordNotReady(Throwable error) {
        return error instanceof HostGatewayClient.GatewayException
                && ((HostGatewayClient.GatewayException) error).statusCode == 409;
    }

    /**
     * 409 (Discord not ready) and 502 (the Bridge could not reach Discord, e.g. the RPC connect
     * this load tried) both mean Discord on the host needs attention, not that the host is gone.
     */
    static int homeUnavailableMessage(Throwable error) {
        boolean hostDiscord = discordNotReady(error)
                || error instanceof HostGatewayClient.GatewayException
                && ((HostGatewayClient.GatewayException) error).statusCode == 502;
        return hostDiscord ? R.string.discord_community_host_not_ready
                : R.string.discord_community_host_unavailable;
    }

    private String communityVoiceError(Throwable error) {
        return discordNotReady(error)
                ? context.getString(R.string.discord_community_host_not_ready)
                : safeCommunityJoinError(error);
    }

    static String safeCommunityJoinError(Throwable error) {
        if (error instanceof HostGatewayClient.GatewayException) {
            HostGatewayClient.GatewayException gateway = (HostGatewayClient.GatewayException) error;
            return "HTTP " + gateway.statusCode + ": " + sanitizeCommunityJoinMessage(gateway.getMessage());
        }
        if (error instanceof java.net.SocketTimeoutException) return "Timed out waiting for host.";
        if (error instanceof IOException) return sanitizeCommunityJoinMessage(error.getMessage());
        return "Discord action failed.";
    }

    static String sanitizeCommunityJoinMessage(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        cleaned = cleaned.replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer [redacted]");
        cleaned = cleaned.replaceAll("https?://[^\\s]+", "[host]");
        if (cleaned.isEmpty()) cleaned = "Host gateway request failed.";
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }
}
