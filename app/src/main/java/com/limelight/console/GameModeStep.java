package com.limelight.console;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * Game mode (Host Plus) before a start: the one owner of this step. It runs before the launch
 * transition begins, so the cover and readiness stay with LaunchTransitionController.
 *
 * <p>Two separate decisions: the list of the PC's programs to close ({@link GameModeWindow}),
 * and the host's game mode session for the stream (Windows Game Mode, muting other audio),
 * which starts whether programs were closed, skipped or there were none. An adult's choices
 * are the game's override, else the profile's switch; a child's are the parent's, which only
 * the host holds: it refuses a child's list without the parent's consent (and Play goes on as
 * if game mode were off) and applies the parent's options to the session.
 */
final class GameModeStep {
    enum Override { INHERIT, ALWAYS, NEVER }

    /** A game mode choice: its profile switch (with its default) and its per-game override. */
    enum Option {
        GAME_MODE("game_mode.", "game_mode_game.", false),
        WINDOWS_GAME_MODE("game_mode_windows.", "game_mode_windows_game.", true),
        MUTE_OTHERS("game_mode_mute.", "game_mode_mute_game.", true);

        final String profileKey;
        final String gameKey;
        final boolean profileDefault;

        Option(String profileKey, String gameKey, boolean profileDefault) {
            this.profileKey = profileKey;
            this.gameKey = gameKey;
            this.profileDefault = profileDefault;
        }
    }

    static final class Program {
        final String id;
        final String name;
        final String image;
        /** PNG bytes of the exe's icon; empty: the window draws a monogram. */
        final byte[] icon;

        Program(String id, String name, String image, byte[] icon) {
            this.id = id;
            this.name = name;
            this.image = image;
            this.icon = icon;
        }
    }

    /** A program still open after the close; {@code responding} false: it is hung. */
    static final class Remaining {
        final String id;
        final String name;
        final boolean responding;

        Remaining(String id, String name, boolean responding) {
            this.id = id;
            this.name = name;
            this.responding = responding;
        }
    }

    /**
     * How long the list may take before the window opens with skeleton tiles: a quick empty
     * answer then starts the game without any window.
     */
    static final long SKELETON_DELAY_MS = 350L;

    private final Activity activity;
    private final SharedPreferences preferences;
    private final HostGatewayClient client;
    private final Executor background;
    private final Handler main;
    private boolean listing;
    /** The window left up after a close with a hung program, while the game starts. */
    private GameModeWindow notice;
    /** Its force close confirmation while it is shown. */
    private android.app.Dialog confirmation;

    GameModeStep(Activity activity, SharedPreferences preferences, HostGatewayClient client,
                 Executor background, Handler main) {
        this.activity = activity;
        this.preferences = preferences;
        this.client = client;
        this.background = background;
        this.main = main;
    }

    static boolean profileEnabled(SharedPreferences preferences, String hostId,
                                  String profileId, Option option) {
        return preferences.getBoolean(option.profileKey + profileKey(hostId, profileId),
                option.profileDefault);
    }

    static void setProfileEnabled(SharedPreferences preferences, String hostId,
                                  String profileId, Option option, boolean enabled) {
        preferences.edit().putBoolean(option.profileKey + profileKey(hostId, profileId), enabled)
                .apply();
    }

    /** {@code gameKey}: the game's settings key (AppPreferences.playniteGameKey). */
    static Override override(SharedPreferences preferences, String gameKey, Option option) {
        try {
            return Override.valueOf(preferences.getString(option.gameKey + gameKey,
                    Override.INHERIT.name()));
        } catch (IllegalArgumentException unknown) {
            return Override.INHERIT;
        }
    }

    static void setOverride(SharedPreferences preferences, String gameKey, Option option,
                            Override value) {
        if (value == Override.INHERIT) preferences.edit().remove(option.gameKey + gameKey).apply();
        else preferences.edit().putString(option.gameKey + gameKey, value.name()).apply();
    }

    /** An adult's decision: the game's override wins over the profile switch. */
    static boolean wanted(boolean profileEnabled, Override override) {
        return override == Override.INHERIT ? profileEnabled : override == Override.ALWAYS;
    }

    static boolean enabled(SharedPreferences preferences, String hostId, String profileId,
                           String gameKey, Option option) {
        return wanted(profileEnabled(preferences, hostId, profileId, option),
                override(preferences, gameKey, option));
    }

    /**
     * Runs the step for a fresh start of {@code gameId}, then {@code play} (unless Back closed
     * the window). {@code current}: the host and profile are still the selected ones.
     */
    void run(String hostId, String profileId, boolean child, GatewayConnection connection,
             String gameId, String gameName, String gameKey, File cover, File backdrop,
             BooleanSupplier current, Runnable play) {
        if (connection == null || !child && !enabled(preferences, hostId, profileId, gameKey,
                Option.GAME_MODE)) {
            play.run();
            return;
        }
        if (listing) return; // a repeated Play while the host answers
        listing = true;
        boolean windows = !child && enabled(preferences, hostId, profileId, gameKey,
                Option.WINDOWS_GAME_MODE);
        boolean mute = !child && enabled(preferences, hostId, profileId, gameKey,
                Option.MUTE_OTHERS);
        Runnable sessionThenPlay = () -> startSession(connection, gameId, child || windows || mute,
                windows, mute, play);
        GameModeWindow[] window = {null};
        boolean[] answered = {false};
        GameModeWindow.Listener choices = new GameModeWindow.Listener() {
            @java.lang.Override public void closeAndPlay(List<String> programIds) {
                closeThenPlay(window[0], connection, gameId, programIds, sessionThenPlay);
            }

            @java.lang.Override public void playWithoutClosing() {
                sessionThenPlay.run();
            }

            @java.lang.Override public void forceClose(String programId, String name) {
                confirmForceClose(window[0], connection, gameId, programId, name);
            }
        };
        main.postDelayed(() -> {
            if (!answered[0] && alive() && current.getAsBoolean()) {
                window[0] = GameModeWindow.show(activity, gameName, cover, backdrop, windows,
                        mute, choices, background);
            }
        }, SKELETON_DELAY_MS);
        background.execute(() -> {
            List<Program> programs;
            boolean allowed = true;
            try {
                programs = client.gameModePrograms(connection, gameId);
            } catch (HostGatewayClient.GatewayException error) {
                programs = Collections.emptyList();
                allowed = error.statusCode != 403; // a child without the parent's consent
            } catch (IOException | RuntimeException unavailable) {
                programs = Collections.emptyList();
            }
            List<Program> listed = programs;
            boolean gameModeAllowed = allowed;
            main.post(() -> {
                listing = false;
                answered[0] = true;
                boolean shown = window[0] != null && window[0].isShowing();
                if (!alive() || !current.getAsBoolean()) {
                    if (shown) window[0].dismiss();
                    return;
                }
                if (window[0] != null && !shown) return; // Back closed it: no start
                if (!listed.isEmpty()) {
                    if (window[0] == null) {
                        window[0] = GameModeWindow.show(activity, gameName, cover, backdrop,
                                windows, mute, choices, background);
                    }
                    window[0].showPrograms(listed);
                    return;
                }
                if (shown) window[0].dismiss();
                if (gameModeAllowed) sessionThenPlay.run();
                else play.run();
            });
        });
    }

    /** Never blocks the game: what stayed open, or a failed close, is only a notice. */
    private void closeThenPlay(GameModeWindow window, GatewayConnection connection, String gameId,
                               List<String> ids, Runnable then) {
        if (ids.isEmpty()) {
            if (window != null) window.dismiss();
            then.run();
            return;
        }
        background.execute(() -> {
            List<Remaining> remaining;
            String failure = null;
            try {
                remaining = client.closeGameModePrograms(connection, gameId, ids);
            } catch (IOException | RuntimeException failed) {
                remaining = Collections.emptyList();
                failure = activity.getString(R.string.console_game_mode_close_failed);
            }
            List<Remaining> open = remaining;
            String shown = failure != null ? failure : notice(open);
            main.post(() -> {
                if (!alive() || window == null || !window.isShowing()) return;
                boolean hung = false;
                for (Remaining program : open) hung |= !program.responding;
                if (hung) {
                    // The game starts at once; the window stays as the result, for a force close.
                    window.showResult(open);
                    notice = window;
                } else {
                    window.dismiss();
                    if (shown != null) {
                        ConsoleUiFeedback.makeText(activity, shown, Toast.LENGTH_LONG).show();
                    }
                }
                then.run();
            });
        });
    }

    private String notice(List<Remaining> open) {
        if (open.isEmpty()) return null;
        List<String> names = new ArrayList<>();
        for (Remaining program : open) {
            names.add(program.responding ? program.name
                    : activity.getString(R.string.console_game_mode_hung, program.name));
        }
        return activity.getString(R.string.console_game_mode_not_closed, String.join(", ", names));
    }

    /** Owner 2026-10-24: only a hung program, only after a destructive confirmation. */
    private void confirmForceClose(GameModeWindow window, GatewayConnection connection,
                                   String gameId, String programId, String name) {
        confirmation = ConsoleConfirmDialog.showDestructive(activity,
                activity.getString(R.string.console_game_mode_force_title, name),
                activity.getString(R.string.console_game_mode_force_message, name),
                activity.getString(R.string.console_cancel),
                activity.getString(R.string.console_game_mode_force_close),
                () -> background.execute(() -> {
                    String outcome;
                    try {
                        outcome = client.forceCloseGameModeProgram(connection, gameId, programId);
                    } catch (IOException | RuntimeException failed) {
                        outcome = "failed";
                    }
                    String result = outcome;
                    main.post(() -> {
                        if (alive() && window != null && window.isShowing()) {
                            window.showForceResult(programId, result);
                        }
                    });
                }), null, null);
    }

    /** The screen went away (the stream took over): the result window goes with it. */
    void dismissNotice() {
        if (confirmation != null && confirmation.isShowing()) confirmation.dismiss();
        confirmation = null;
        if (notice != null) notice.dismiss();
        notice = null;
    }

    /** The host's game mode session, then the start; a failure only skips the session. */
    private void startSession(GatewayConnection connection, String gameId, boolean wanted,
                              boolean windows, boolean mute, Runnable play) {
        if (!wanted) {
            play.run();
            return;
        }
        background.execute(() -> {
            try {
                client.startGameModeSession(connection, gameId, windows, mute);
            } catch (IOException | RuntimeException ignored) {
                // Game mode never blocks the game.
            }
            main.post(() -> {
                if (alive()) play.run();
            });
        });
    }

    private boolean alive() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    static List<Program> parsePrograms(JSONObject response) throws IOException {
        JSONArray values = response == null ? null : response.optJSONArray("programs");
        if (response == null || !response.optBoolean("ok", false) || values == null) {
            throw new HostGatewayClient.GatewayException("game_mode_unavailable", 0);
        }
        List<Program> programs = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            String id = value == null ? "" : value.optString("id", "");
            String name = value == null ? "" : value.optString("name", "").trim();
            if (!id.matches("[1-9][0-9]{0,9}-[1-9][0-9]{0,19}") || name.isEmpty()) continue;
            byte[] icon;
            try {
                icon = java.util.Base64.getDecoder().decode(value.optString("icon", ""));
            } catch (IllegalArgumentException malformed) {
                icon = new byte[0];
            }
            programs.add(new Program(id, name, value.optString("image", "")
                    .toLowerCase(Locale.ROOT), icon));
        }
        return programs;
    }

    static List<Remaining> parseRemaining(JSONObject response) throws IOException {
        JSONArray values = response == null ? null : response.optJSONArray("remaining");
        if (response == null || !response.optBoolean("ok", false) || values == null) {
            throw new HostGatewayClient.GatewayException("game_mode_close_failed", 0);
        }
        List<Remaining> open = new ArrayList<>();
        // Refused programs (not closable after all) are not closed either: never force-closed.
        JSONArray refused = response.optJSONArray("refused");
        for (JSONArray list : new JSONArray[]{values, refused == null ? new JSONArray() : refused}) {
            for (int index = 0; index < list.length(); index++) {
                JSONObject value = list.optJSONObject(index);
                String id = value == null ? "" : value.optString("id", "");
                String name = value == null ? "" : value.optString("name", "").trim();
                if (!name.isEmpty()) {
                    open.add(new Remaining(id, name, list == refused
                            || value.optBoolean("responding", true)));
                }
            }
        }
        return open;
    }

    /** One of closed, gone, responding, refused, failed. */
    static String parseForceResult(JSONObject response) throws IOException {
        String result = response == null || !response.optBoolean("ok", false) ? ""
                : response.optString("result", "");
        if (!result.matches("closed|gone|responding|refused|failed")) {
            throw new HostGatewayClient.GatewayException("game_mode_force_close_failed", 0);
        }
        return result;
    }

    private static String profileKey(String hostId, String profileId) {
        return (hostId == null ? "" : hostId.trim().toLowerCase(Locale.ROOT))
                + "/" + (profileId == null ? "" : profileId.trim());
    }
}
