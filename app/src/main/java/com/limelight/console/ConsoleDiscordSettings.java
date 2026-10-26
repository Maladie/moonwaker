package com.limelight.console;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.audio.DiscordAudioDownlink;
import com.limelight.discord.DiscordSocialClient;
import com.limelight.gateway.GatewayConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Settings › Discord: this TV's Discord account, notifications and playback volume, then the
 * selected host's Discord switches (kept on this TV per host and profile), its Discord devices
 * and the repair actions. The Community panel only links here. Host replies carry this class's
 * own generation, so a closing side panel cannot drop them.
 */
final class ConsoleDiscordSettings {
    private interface Request {
        void send(GatewayConnection connection) throws IOException;
    }

    /** Discord playback on this TV, in percent of the received level. */
    private static final int MIN_TV_GAIN = 50;
    private static final int MAX_TV_GAIN = 200;
    private static final int TV_GAIN_STEP = 5;

    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    private final HostGatewayClient client;
    private final HostGatewayStore store;
    private final Executor executor;
    private final Handler main;
    /** The Community panel's account-bound state (message drafts) to forget on unlink. */
    private final Runnable onUnlinked;
    private final SharedPreferences prefs;
    private String key = "";
    private int generation;
    private boolean loading;
    private boolean busy;
    /** The repair action in flight (its label), 0 for none or a silent read. */
    private int pendingAction;
    private HostGatewayClient.DiscordStatus status;
    private HostGatewayClient.DiscordAudioState devices;
    private String error = "";

    ConsoleDiscordSettings(Activity activity, ConsoleSettingsScreen screen,
                           HostGatewayClient client, HostGatewayStore store, Executor executor,
                           Handler main, Runnable onUnlinked) {
        this.activity = activity;
        this.screen = screen;
        this.client = client;
        this.store = store;
        this.executor = executor;
        this.main = main;
        this.onUnlinked = onUnlinked;
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    /** Forgets the host's state, so Settings opened again reads it afresh. */
    void reset() {
        key = "";
    }

    /**
     * The host profile's Discord account ({@code connection} null: none selected), then this
     * TV's DM notifications and how loud Discord plays here.
     */
    View[] accountRows(ConsolePanel panel, GatewayConnection connection) {
        List<View> rows = new ArrayList<>();
        DiscordSocialClient.attach(activity, connection);
        String status = DiscordSocialClient.getSnapshot().status;
        if (connection == null) {
            rows.add(panel.note(s(R.string.console_gateway_pair_required)));
        } else if (!DiscordSocialClient.isAvailable()) {
            boolean absent = status.equals("Unavailable on this host");
            boolean unreachable = status.startsWith("Unable to ");
            rows.add(panel.note(s(absent ? R.string.discord_social_sdk_unavailable
                    : unreachable ? R.string.discord_social_state_unavailable
                    : R.string.discord_social_checking)));
            // The host answers within a poll; show its answer while Settings stays open.
            if (!absent && !unreachable) {
                main.postDelayed(() -> { if (screen.isShowing()) screen.refresh(); }, 1500L);
            }
        } else {
            DiscordSocialClient.Snapshot snapshot = DiscordSocialClient.getSnapshot();
            if (DiscordCommunityView.isLinked(snapshot)) {
                rows.add(panel.value(s(R.string.console_discord_account),
                        snapshot.displayName.isEmpty() ? s(R.string.discord_community_connected)
                                : snapshot.displayName));
                TextView unlink = panel.destructive(panel.action(s(R.string.discord_social_unlink)));
                unlink.setOnClickListener(view -> ConsoleConfirmDialog.show(activity,
                        s(R.string.discord_social_unlink_title),
                        s(R.string.discord_social_unlink_details), s(R.string.console_cancel),
                        s(R.string.discord_social_unlink), () -> {
                            DiscordSocialClient.unlink();
                            onUnlinked.run();
                            screen.refresh();
                        }));
                rows.add(unlink);
            } else {
                TextView connect = panel.action(s(R.string.discord_social_connect_action));
                connect.setOnClickListener(view -> DiscordSocialClient.authorize(activity));
                rows.add(connect);
                // Shows the account once the QR is approved on the phone.
                if (status.equals("Awaiting Discord approval")) {
                    main.postDelayed(() -> { if (screen.isShowing()) screen.refresh(); }, 1500L);
                }
            }
        }
        LinearLayout notifications = panel.toggle(s(R.string.discord_dm_notifications_setting),
                DiscordDmNotificationPreferences.isEnabled(activity));
        notifications.setOnClickListener(view -> {
            boolean enabled = !DiscordDmNotificationPreferences.isEnabled(activity);
            DiscordDmNotificationPreferences.setEnabled(activity, enabled);
            DiscordDmNotificationCoordinator.getInstance().setNotificationsEnabled(enabled);
            panel.setChecked(notifications, enabled);
        });
        rows.add(notifications);
        int gain = Math.max(MIN_TV_GAIN, Math.min(MAX_TV_GAIN,
                prefs.getInt(DiscordAudioDownlink.GAIN_PREF, DiscordAudioDownlink.DEFAULT_GAIN)));
        rows.add(panel.stepper(s(R.string.console_discord_volume), gain, MIN_TV_GAIN, MAX_TV_GAIN,
                TV_GAIN_STEP, this::percent, percent(TV_GAIN_STEP), null,
                value -> prefs.edit().putInt(DiscordAudioDownlink.GAIN_PREF, value).apply()));
        return rows.toArray(new View[0]);
    }

    /**
     * The selected host's switches, devices and repair; {@code connection} null leaves only the
     * switches, which this TV keeps.
     */
    View[] hostRows(ConsolePanel panel, String hostId, String profileId,
                    GatewayConnection connection) {
        String hostKey = hostId + "/" + profileId;
        if (!hostKey.equals(key)) {
            key = hostKey;
            generation++;
            loading = false;
            busy = false;
            pendingAction = 0;
            status = null;
            devices = null;
            error = "";
        }
        List<View> rows = new ArrayList<>();
        rows.add(storeToggle(panel, R.string.discord_community_integration,
                () -> store.isDiscordEnabled(hostId, profileId),
                enabled -> store.setDiscordEnabled(hostId, profileId, enabled)));
        rows.add(storeToggle(panel, R.string.discord_community_auto_connect,
                () -> store.isDiscordAutoConnectEnabled(hostId, profileId),
                enabled -> store.setDiscordAutoConnectEnabled(hostId, profileId, enabled)));
        rows.add(storeToggle(panel, R.string.discord_community_auto_join,
                () -> store.isDiscordAutoJoinLastEnabled(hostId, profileId),
                enabled -> store.setDiscordAutoJoinLastEnabled(hostId, profileId, enabled)));
        if (connection == null) {
            rows.add(panel.note(s(R.string.playnite_gateway_not_configured)));
            return rows.toArray(new View[0]);
        }
        if (status == null && !loading && error.isEmpty()) refresh(connection, null, 0);

        rows.add(panel.section(s(R.string.discord_community_host_state)));
        if (pendingAction != 0) rows.add(panel.note(progressText(pendingAction)));
        if (!error.isEmpty()) rows.add(panel.note(error));
        if (status != null) rows.add(panel.note(statusText(status)));
        else if (loading) rows.add(panel.note(s(R.string.discord_community_loading)));
        if (devices != null) {
            addDevices(rows, panel, connection, "output", R.string.console_discord_output);
            addDevices(rows, panel, connection, "input", R.string.console_discord_input);
        }

        rows.add(panel.section(s(R.string.console_discord_repair)));
        rows.add(hostAction(panel, connection, R.string.discord_start_on_host,
                client::startDiscord));
        rows.add(hostAction(panel, connection, R.string.discord_connect_rpc,
                c -> client.connectDiscord(c, false)));
        rows.add(hostAction(panel, connection, R.string.discord_refresh_status, null));
        return rows.toArray(new View[0]);
    }

    private interface Flag { boolean get(); }

    private interface FlagWriter { void set(boolean enabled); }

    private LinearLayout storeToggle(ConsolePanel panel, int label, Flag value, FlagWriter write) {
        LinearLayout row = panel.toggle(s(label), value.get());
        row.setOnClickListener(view -> {
            write.set(!value.get());
            panel.setChecked(row, value.get());
        });
        return row;
    }

    private void addDevices(List<View> rows, ConsolePanel panel, GatewayConnection connection,
                            String flow, int label) {
        List<HostGatewayClient.AudioDevice> listed = new ArrayList<>();
        for (HostGatewayClient.AudioDevice device : devices.devices) {
            if (flow.equals(device.flow)) listed.add(device);
        }
        if (listed.isEmpty()) return;
        CharSequence[] names = new CharSequence[listed.size()];
        int current = -1;
        for (int index = 0; index < names.length; index++) {
            names[index] = listed.get(index).name;
            if (listed.get(index).current) current = index;
        }
        LinearLayout row = panel.value(s(label), current < 0 ? "" : names[current].toString());
        int chosen = current;
        row.setOnClickListener(view -> screen.showChoices(s(label), "", names, chosen, index -> {
            if (index != chosen) {
                refresh(connection, c -> client.selectDiscordAudioDevice(c, listed.get(index)), 0);
            }
        }));
        rows.add(row);
    }

    private TextView hostAction(ConsolePanel panel, GatewayConnection connection, int label,
                                Request request) {
        TextView row = panel.action(s(label));
        row.setOnClickListener(view -> {
            if (busy || loading) return;
            toast(progressText(label));
            refresh(connection, request, label);
            screen.refresh();
        });
        return row;
    }

    /**
     * Runs {@code request} (if any), then reads the host's Discord status and, once Discord is
     * ready there, its devices. A repair {@code action} (its label) reports its result.
     */
    private void refresh(GatewayConnection connection, Request request, int action) {
        if (request == null) loading = true;
        else busy = true;
        pendingAction = action;
        int expected = generation;
        executor.execute(() -> {
            HostGatewayClient.DiscordStatus readStatus = null;
            HostGatewayClient.DiscordAudioState readDevices = null;
            Exception failure = null;
            try {
                if (request != null) request.send(connection);
                readStatus = client.getDiscordStatus(connection);
                if (readStatus.rpcConnected && readStatus.authenticated) {
                    readDevices = client.getDiscordAudioState(connection);
                }
            } catch (IOException | RuntimeException exception) {
                failure = exception;
            }
            HostGatewayClient.DiscordStatus resultStatus = readStatus;
            HostGatewayClient.DiscordAudioState resultDevices = readDevices;
            Exception problem = failure;
            main.post(() -> {
                if (expected != generation) return;
                loading = false;
                busy = false;
                pendingAction = 0;
                if (resultStatus != null) status = resultStatus;
                devices = resultDevices;
                error = problem == null ? ""
                        : DiscordPanelController.discordNotReady(problem)
                        ? s(R.string.discord_community_host_not_ready)
                        : DiscordPanelController.safeCommunityJoinError(problem);
                if (action != 0) toast(problem != null
                        ? activity.getString(R.string.discord_host_action_failed, s(action), error)
                        : activity.getString(R.string.discord_host_action_done, s(action),
                        statusText(resultStatus)) + (resultStatus.rpcConnected
                        && !resultStatus.authenticated
                        ? " " + s(R.string.discord_host_action_authorize_hint) : ""));
                if (screen.isShowing()) screen.refresh();
            });
        });
    }

    private String statusText(HostGatewayClient.DiscordStatus value) {
        return activity.getString(R.string.discord_community_host_status,
                s(value.bridgeOnline ? R.string.discord_state_online : R.string.discord_state_offline),
                s(value.rpcConnected ? R.string.discord_state_online : R.string.discord_state_offline),
                s(value.authenticated ? R.string.discord_state_on : R.string.discord_state_off));
    }

    private String progressText(int action) {
        return activity.getString(R.string.discord_host_action_progress, s(action));
    }

    private void toast(String message) {
        ConsoleUiFeedback.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
    }

    private String percent(int value) {
        return activity.getString(R.string.console_stream_percent, value);
    }

    private String s(int resource) {
        return activity.getString(resource);
    }
}
