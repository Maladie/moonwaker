package com.limelight.console;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Settings › Sound, the selected host's Windows audio: volume, mute and the default devices.
 * The profile Bridge serves it without the Discord client. This class owns the loaded state
 * and its requests for one host and profile; a stale reply (another host, a reopened
 * Settings) is dropped.
 */
final class ConsoleSystemAudioSettings {
    private interface Request {
        HostGatewayClient.SystemAudioState send(GatewayConnection connection) throws IOException;
    }

    private static final int VOLUME_STEP = 5;

    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    private final HostGatewayClient client;
    private final Executor executor;
    private final Handler main;
    private String key = "";
    private int generation;
    private HostGatewayClient.SystemAudioState state;
    private boolean loading;
    private boolean busy;
    private String error = "";
    /** A volume chosen while a request runs; sent next, so held arrows coalesce. */
    private int pendingVolume = -1;

    ConsoleSystemAudioSettings(Activity activity, ConsoleSettingsScreen screen,
                               HostGatewayClient client, Executor executor, Handler main) {
        this.activity = activity;
        this.screen = screen;
        this.client = client;
        this.executor = executor;
        this.main = main;
    }

    /** Forgets the loaded state, so Settings opened again shows the host's current audio. */
    void reset() {
        key = "";
    }

    /** {@code hostKey} names the host and profile the rows (and {@code connection}) belong to. */
    View[] rows(ConsolePanel panel, String hostKey, GatewayConnection connection) {
        if (!hostKey.equals(key)) {
            key = hostKey;
            generation++;
            state = null;
            loading = false;
            busy = false;
            error = "";
            pendingVolume = -1;
        }
        if (state == null && !loading && error.isEmpty()) load(connection);
        List<View> rows = new ArrayList<>();
        if (!error.isEmpty()) rows.add(panel.note(error));
        if (state == null) {
            if (loading) {
                rows.add(panel.note(s(R.string.console_system_audio_loading)));
            } else {
                TextView retry = panel.action(s(R.string.console_system_audio_retry));
                retry.setOnClickListener(view -> {
                    error = "";
                    load(connection);
                    screen.refresh();
                });
                rows.add(retry);
            }
            return rows.toArray(new View[0]);
        }
        if (!state.available) {
            rows.add(panel.note(state.error.isEmpty() ? s(R.string.console_system_audio_unavailable)
                    : s(R.string.console_system_audio_unavailable) + " " + state.error));
            return rows.toArray(new View[0]);
        }
        rows.add(panel.stepper(s(R.string.console_system_audio_volume), state.volume, 0, 100,
                VOLUME_STEP, this::percent, percent(VOLUME_STEP), null,
                value -> setVolume(connection, value)));
        LinearLayout mute = panel.toggle(s(R.string.console_system_audio_mute), state.muted);
        mute.setOnClickListener(view -> send(connection, client::toggleSystemMute, true));
        rows.add(mute);
        addDevice(rows, panel, connection, "output", R.string.console_system_audio_output);
        addDevice(rows, panel, connection, "input", R.string.console_system_audio_input);
        return rows.toArray(new View[0]);
    }

    private void addDevice(List<View> rows, ConsolePanel panel, GatewayConnection connection,
                           String flow, int label) {
        List<HostGatewayClient.AudioDevice> devices = new ArrayList<>();
        for (HostGatewayClient.AudioDevice device : state.devices) {
            if (flow.equals(device.flow)) devices.add(device);
        }
        if (devices.isEmpty()) return;
        CharSequence[] names = new CharSequence[devices.size()];
        int current = -1;
        for (int index = 0; index < names.length; index++) {
            names[index] = devices.get(index).name;
            if (devices.get(index).current) current = index;
        }
        LinearLayout row = panel.value(s(label), current < 0 ? "" : names[current].toString());
        int chosen = current;
        row.setOnClickListener(view -> screen.showChoices(s(label), "", names, chosen, index -> {
            if (index != chosen) {
                send(connection, c -> client.selectSystemAudioDevice(c, devices.get(index)), true);
            }
        }));
        rows.add(row);
    }

    private void setVolume(GatewayConnection connection, int value) {
        pendingVolume = value;
        if (!busy) sendPendingVolume(connection);
    }

    private void sendPendingVolume(GatewayConnection connection) {
        int value = pendingVolume;
        pendingVolume = -1;
        // The stepper already shows the value; re-rendering would end its editing.
        send(connection, c -> client.setSystemVolume(c, value), false);
    }

    private void load(GatewayConnection connection) {
        loading = true;
        int expected = generation;
        executor.execute(() -> {
            HostGatewayClient.SystemAudioState loaded = null;
            Exception failure = null;
            try {
                loaded = client.getSystemAudioState(connection);
            } catch (IOException | RuntimeException exception) {
                failure = exception;
            }
            HostGatewayClient.SystemAudioState result = loaded;
            Exception problem = failure;
            main.post(() -> {
                if (expected != generation) return;
                loading = false;
                state = result;
                error = problem == null ? "" : message(problem);
                refresh();
            });
        });
    }

    private void send(GatewayConnection connection, Request request, boolean render) {
        if (busy || loading) return;
        busy = true;
        int expected = generation;
        executor.execute(() -> {
            HostGatewayClient.SystemAudioState sent = null;
            Exception failure = null;
            try {
                sent = request.send(connection);
            } catch (IOException | RuntimeException exception) {
                failure = exception;
            }
            HostGatewayClient.SystemAudioState result = sent;
            Exception problem = failure;
            main.post(() -> {
                if (expected != generation) return;
                busy = false;
                if (result != null) state = result;
                error = problem == null ? "" : message(problem);
                if (pendingVolume >= 0) {
                    sendPendingVolume(connection);
                } else if (render || problem != null) {
                    refresh();
                }
            });
        });
    }

    private void refresh() {
        if (screen.isShowing()) screen.refresh();
    }

    /** The Bridge reports a default the host put back (a stream's virtual device). */
    private String message(Exception problem) {
        boolean reverted = problem instanceof HostGatewayClient.GatewayException
                && problem.getMessage() != null
                && problem.getMessage().contains("system_default_reverted");
        return s(reverted ? R.string.console_system_audio_default_reverted
                : R.string.console_system_audio_failed);
    }

    private String percent(int value) {
        return activity.getString(R.string.console_stream_percent, value);
    }

    private String s(int resource) {
        return activity.getString(resource);
    }
}
