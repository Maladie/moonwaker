package com.limelight.console;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.GyroAim;
import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.stream.StreamingAutopilot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * A game's stream overrides (mockup 18-stream-settings-game): {@link AppPreferences} under the
 * game's key, every row showing the inherited value until it is overridden.
 *
 * <p>Sunshine apps inherit from Settings › Stream. Playnite games and Quick Launch items
 * ("inherit" keys, passed to the stream as its second key) inherit from their Sunshine app's
 * overrides and then from Settings › Stream, as {@link AppPreferences#getEffectivePreferences}
 * resolves them at launch.
 *
 * <p>The second category, gyro aiming, is outside "use global settings": a game without its own
 * "gyro" object shows what it inherits (its Sunshine app's, else Settings › Controllers and
 * devices › Gyro), and the first change saves the whole object under the game's key.
 */
final class ConsoleAppStreamSettings {
    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    private String appKey;
    private boolean inherit;
    private AppPreferences.AppSettings settings;
    private PreferenceConfiguration inherited;
    private AppPreferences.AppSettings parent;
    private final String[] rowLabels = new String[Key.values().length];
    private ConsoleSettingsScreen.Category category;
    /** The host and adult profile whose game mode switches the game's overrides inherit. */
    private String gameModeHost;
    private String gameModeProfile;

    ConsoleAppStreamSettings(Activity activity, ConsoleSettingsScreen screen) {
        this.activity = activity;
        this.screen = screen;
    }

    /**
     * Adds the game's game mode overrides (inherit, always, never) over the profile's switches
     * (mockup 24f); a child's game mode is the parent's, so a child gets no rows.
     */
    ConsoleAppStreamSettings withGameMode(String hostId, String profileId, boolean child) {
        gameModeHost = child ? null : hostId;
        gameModeProfile = profileId;
        return this;
    }

    /**
     * Opens the overrides of {@code appKey}. {@code parentKey} is the Sunshine app a Playnite
     * game launches through (null when unknown); Quick Launch keys ("host:app:id") name theirs.
     */
    void open(String crumb, String appKey, String appName, boolean inheritAppSettings,
              String parentKey) {
        this.appKey = appKey;
        String[] parts = appKey.split(":");
        // Quick Launch keys are host:app:timestamp, their app is host:app. A Playnite game
        // (playnite:host:game) without a known Sunshine app inherits the global settings.
        boolean quickLaunch = !inheritAppSettings && parts.length == 3;
        inherit = inheritAppSettings || quickLaunch;
        if (quickLaunch) parentKey = parts[0] + ":" + parts[1];
        settings = AppPreferences.getAppSettings(activity, appKey);
        // What the stream gets.
        parent = parentKey != null ? AppPreferences.getAppSettings(activity, parentKey) : null;
        inherited = parentKey != null
                ? AppPreferences.getEffectivePreferences(activity, parentKey)
                : PreferenceConfiguration.readPreferences(activity);
        category = new ConsoleSettingsScreen.Category(s(R.string.console_stream_title),
                this::rows);
        List<ConsoleSettingsScreen.Category> categories = new ArrayList<>();
        categories.add(category);
        // Pad sensors (InputDevice.getSensorManager) arrived in Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            categories.add(new ConsoleGyroSettings(activity, screen, () -> inherited,
                    new ConsoleGyroSettings.Store() {
                        @Override public GyroAim.Settings current() {
                            return settings.gyro == null ? inherited.gyroAim
                                    : AppPreferences.gyroFromJson(settings.gyro, inherited.gyroAim);
                        }

                        @Override public void save(GyroAim.Settings gyro) {
                            settings.gyro = AppPreferences.gyroToJson(gyro);
                            ConsoleAppStreamSettings.this.save();
                        }
                    }).category(R.string.console_gyro_details));
        }
        screen.show(crumb, appName, s(R.string.console_app_stream_eyebrow), categories, 0);
    }

    /** The stream settings a game can override, in row order; each is one field of {@link AppPreferences.AppSettings}. */
    enum Key { RESOLUTION, FPS, FRAME_PACING, BITRATE, REFRESH_RATE, HDR, PERF_OVERLAY, AUDIO_HAPTICS,
        AUDIO_HAPTICS_STRENGTH }

    /** Where a row's effective value comes from. */
    enum Origin { GAME, APP, DEFAULT, BUILT_IN }

    static boolean hasValue(AppPreferences.AppSettings settings, Key key) {
        switch (key) {
            case RESOLUTION: return settings.resolution != null;
            case FPS: return settings.fps > 0;
            case FRAME_PACING: return settings.framePacing != null;
            case BITRATE: return settings.bitrate > 0;
            case REFRESH_RATE: return settings.actualDisplayRefreshRate > 0;
            case HDR: return settings.enableHdr != null;
            case AUDIO_HAPTICS: return settings.audioHaptics != null;
            case AUDIO_HAPTICS_STRENGTH: return settings.audioHapticsStrength > 0;
            default: return settings.enablePerfOverlay != null;
        }
    }

    /**
     * The game when it overrides the key (and "use global" is off), else the Sunshine app it
     * launches through, else Settings › Stream, or the built-in behaviour when that has no
     * value of its own ({@code defaultHasValue} false).
     */
    static Origin origin(Key key, AppPreferences.AppSettings game,
                         AppPreferences.AppSettings parent, boolean defaultHasValue) {
        if (!game.useGlobalSettings && hasValue(game, key)) return Origin.GAME;
        if (parent != null && !parent.useGlobalSettings && hasValue(parent, key)) return Origin.APP;
        return defaultHasValue ? Origin.DEFAULT : Origin.BUILT_IN;
    }

    /** Drops this key's override only; the other overrides and "use global" stay. */
    static void reset(AppPreferences.AppSettings settings, Key key) {
        switch (key) {
            case RESOLUTION: settings.resolution = null; break;
            case FPS: settings.fps = 0; break;
            case FRAME_PACING: settings.framePacing = null; break;
            case BITRATE: settings.bitrate = 0; break;
            case REFRESH_RATE: settings.actualDisplayRefreshRate = 0; break;
            case HDR: settings.enableHdr = null; break;
            case AUDIO_HAPTICS: settings.audioHaptics = null; break;
            case AUDIO_HAPTICS_STRENGTH: settings.audioHapticsStrength = 0; break;
            default: settings.enablePerfOverlay = null; break;
        }
    }

    private View[] rows(ConsolePanel panel) {
        LinearLayout useGlobal = panel.toggle(s(inherit ? R.string.console_app_stream_use_inherited
                : R.string.console_app_stream_use_global), settings.useGlobalSettings);
        useGlobal.setOnClickListener(view -> {
            settings.useGlobalSettings = !settings.useGlobalSettings;
            save();
            screen.refresh();
        });
        String[] resolutionNames = array(R.array.resolution_names);
        String[] resolutionValues = array(R.array.resolution_values);
        String[] fpsNames = array(R.array.fps_names);
        String[] fpsValues = array(R.array.fps_values);
        String[] pacingNames = array(R.array.video_frame_pacing_names);
        String[] pacingValues = array(R.array.video_frame_pacing_values);
        String inheritedResolution = inherited.width + "x" + inherited.height;
        String inheritedPacing = pacingNames[Math.max(0,
                Math.min(pacingNames.length - 1, inherited.framePacing))];
        String[] onOff = {s(R.string.console_stream_on), s(R.string.console_stream_off)};

        LinearLayout resolution = value(panel, Key.RESOLUTION, R.string.console_app_stream_resolution,
                settings.resolution == null ? inheritedLabel('f',
                        resolutionLabel(resolutionNames, resolutionValues, inheritedResolution))
                        : resolutionLabel(resolutionNames, resolutionValues, settings.resolution));
        resolution.setOnClickListener(view -> chooseStandardOrCustom(
                R.string.console_app_stream_resolution, resolutionNames, resolutionValues,
                settings.resolution, inheritedLabel('f', resolutionLabel(resolutionNames,
                        resolutionValues, inheritedResolution)),
                chosen -> settings.resolution = chosen, this::customResolution));
        String fpsValue = settings.fps > 0 ? String.valueOf(settings.fps) : null;
        LinearLayout fps = value(panel, Key.FPS, R.string.console_stream_fps, fpsValue == null
                ? inheritedLabel('f', fpsLabel(fpsNames, fpsValues, inherited.fps))
                : fpsLabel(fpsNames, fpsValues, settings.fps));
        fps.setOnClickListener(view -> chooseStandardOrCustom(R.string.console_stream_fps,
                fpsNames, fpsValues, fpsValue,
                inheritedLabel('f', fpsLabel(fpsNames, fpsValues, inherited.fps)),
                chosen -> settings.fps = chosen == null ? 0 : Integer.parseInt(chosen),
                this::customFps));
        LinearLayout bitrate = value(panel, Key.BITRATE, R.string.console_app_stream_bitrate,
                settings.bitrate > 0 ? megabits(settings.bitrate)
                        : inheritedLabel('m', megabits(inherited.bitrate)));
        bitrate.setOnClickListener(view -> chooseBitrate());
        LinearLayout pacing = value(panel, Key.FRAME_PACING, R.string.console_stream_frame_pacing,
                settings.framePacing == null ? inheritedLabel('n', inheritedPacing)
                        : ConsoleStreamSettings.label(pacingNames, pacingValues, settings.framePacing));
        pacing.setOnClickListener(view -> chooseOption(R.string.console_stream_frame_pacing,
                pacingNames, pacingValues, settings.framePacing, inheritedLabel('n', inheritedPacing),
                chosen -> settings.framePacing = chosen));
        LinearLayout refresh = value(panel, Key.REFRESH_RATE, R.string.console_stream_actual_refresh,
                settings.actualDisplayRefreshRate > 0
                        ? activity.getString(R.string.console_stream_hz,
                        rate(settings.actualDisplayRefreshRate)) : inheritedRefreshLabel());
        refresh.setOnClickListener(view -> chooseRefreshRate());
        String[] booleans = {"true", "false"};
        LinearLayout hdr = value(panel, Key.HDR, R.string.console_stream_hdr, settings.enableHdr == null
                ? inheritedLabel('n', onOff[inherited.enableHdr ? 0 : 1])
                : ConsoleStreamSettings.label(onOff, booleans, settings.enableHdr));
        hdr.setOnClickListener(view -> chooseOption(R.string.console_stream_hdr, onOff, booleans,
                settings.enableHdr, inheritedLabel('n', onOff[inherited.enableHdr ? 0 : 1]),
                chosen -> settings.enableHdr = chosen));
        LinearLayout stats = value(panel, Key.PERF_OVERLAY, R.string.console_stream_perf_overlay,
                settings.enablePerfOverlay == null
                        ? inheritedLabel('n', onOff[inherited.enablePerfOverlay ? 0 : 1])
                        : ConsoleStreamSettings.label(onOff, booleans, settings.enablePerfOverlay));
        stats.setOnClickListener(view -> chooseOption(R.string.console_stream_perf_overlay, onOff,
                booleans, settings.enablePerfOverlay,
                inheritedLabel('n', onOff[inherited.enablePerfOverlay ? 0 : 1]),
                chosen -> settings.enablePerfOverlay = chosen));

        String[] hapticsNames = array(R.array.audio_haptics_names);
        String[] hapticsValues = array(R.array.audio_haptics_values);
        String inheritedHaptics = inheritedLabel('f',
                ConsoleStreamSettings.label(hapticsNames, hapticsValues, inherited.audioHaptics));
        LinearLayout haptics = value(panel, Key.AUDIO_HAPTICS, R.string.console_stream_audio_haptics,
                settings.audioHaptics == null ? inheritedHaptics
                        : ConsoleStreamSettings.label(hapticsNames, hapticsValues,
                        settings.audioHaptics));
        haptics.setOnClickListener(view -> chooseOption(R.string.console_stream_audio_haptics,
                hapticsNames, hapticsValues, settings.audioHaptics, inheritedHaptics,
                chosen -> settings.audioHaptics = chosen));
        LinearLayout hapticsStrength = value(panel, Key.AUDIO_HAPTICS_STRENGTH,
                R.string.console_stream_audio_haptics_strength,
                settings.audioHapticsStrength > 0 ? percent(settings.audioHapticsStrength)
                        : inheritedLabel('f', percent(inherited.audioHapticsStrength)));
        hapticsStrength.setOnClickListener(view -> chooseHapticsStrength());

        View[] overrides = {resolution, fps, pacing, bitrate, refresh, hdr, stats, haptics,
                hapticsStrength}; // Key order
        // The reason the rows are dimmed replaces the subtitle, so the rows keep mockup 18's
        // positions (a reason line in a row would move every row below it).
        category.details = s(settings.useGlobalSettings
                ? inherit ? R.string.console_app_stream_disabled_inherited
                        : R.string.console_app_stream_disabled_global
                : inherit ? R.string.console_app_stream_details_inherited
                        : R.string.console_app_stream_details_global);
        if (settings.useGlobalSettings) {
            // The saved overrides stay. A Sunshine app ignores them while "use global" is on; a
            // Quick Launch item or Playnite game still applies them over its Sunshine app's own
            // overrides, and ignores them only when that app uses the global settings too
            // (AppPreferences.getEffectivePreferences).
            for (View row : overrides) {
                row.setEnabled(false);
                row.setFocusable(false);
            }
        }
        List<View> rows = new ArrayList<>();
        StreamingAutopilotController.sessionAdvice(activity, appKey)
                .ifPresent(advice -> addSessionAdvice(panel, rows, advice));
        if (gameModeHost != null) {
            // Outside "use global settings": game mode is not a stream setting.
            rows.add(panel.section(s(R.string.console_game_mode_label)));
            rows.add(gameModeRow(panel, GameModeStep.Option.GAME_MODE,
                    R.string.console_game_mode_label));
            rows.add(gameModeRow(panel, GameModeStep.Option.WINDOWS_GAME_MODE,
                    R.string.console_game_mode_windows_label));
            rows.add(gameModeRow(panel, GameModeStep.Option.MUTE_OTHERS,
                    R.string.console_game_mode_mute_label));
            rows.add(panel.separator());
        }
        rows.add(useGlobal);
        rows.add(panel.separator());
        for (int i = 0; i < overrides.length; i++) {
            Key key = Key.values()[i];
            // Audio haptics is a controller setting, named as in Settings › Controllers.
            if (key == Key.AUDIO_HAPTICS) {
                rows.add(panel.section(s(R.string.console_stream_category_controller)));
            }
            rows.add(overrides[i]);
            if (!settings.useGlobalSettings && hasValue(settings, key)) {
                TextView restore = panel.action(activity.getString(
                        R.string.console_app_stream_reset, rowLabels[i]));
                restore.setOnClickListener(view -> {
                    reset(settings, key);
                    save();
                    screen.refresh();
                });
                rows.add(restore);
            }
        }
        return rows.toArray(new View[0]);
    }

    private LinearLayout gameModeRow(ConsolePanel panel, GameModeStep.Option option, int label) {
        android.content.SharedPreferences preferences = activity.getSharedPreferences(
                ConsoleActivity.PREFS, Activity.MODE_PRIVATE);
        String[] onOff = {s(R.string.console_stream_on), s(R.string.console_stream_off)};
        String inherited = activity.getString(R.string.console_game_mode_inherit,
                onOff[GameModeStep.profileEnabled(preferences, gameModeHost, gameModeProfile,
                        option) ? 0 : 1]);
        CharSequence[] labels = {inherited, s(R.string.console_game_mode_always),
                s(R.string.console_game_mode_never)};
        GameModeStep.Override current = GameModeStep.override(preferences, appKey, option);
        LinearLayout row = panel.value(s(label), labels[current.ordinal()].toString());
        ((TextView) row.getChildAt(0)).setText(panel.withDetail(s(label),
                s(current == GameModeStep.Override.INHERIT
                        ? R.string.console_game_mode_origin_profile
                        : R.string.console_app_stream_origin_game)));
        row.setOnClickListener(view -> screen.showChoices(s(label), "", labels,
                current.ordinal(), index -> GameModeStep.setOverride(preferences, appKey, option,
                        GameModeStep.Override.values()[index])));
        return row;
    }

    /** Poor recent sessions: one click writes the proposed settings, or dismisses them. */
    private void addSessionAdvice(ConsolePanel panel, List<View> rows,
                                  StreamingAutopilot.SessionAdvice advice) {
        rows.add(panel.section(s(R.string.console_session_advice_title)));
        rows.add(panel.note(sessionAdviceText(activity, advice)));
        TextView accept = panel.action(s(R.string.console_session_advice_accept));
        accept.setOnClickListener(view -> {
            StreamingAutopilotController.acceptSessionAdvice(activity, appKey, advice);
            settings = AppPreferences.getAppSettings(activity, appKey);
            screen.refresh();
        });
        TextView dismiss = panel.action(s(R.string.console_session_advice_dismiss));
        dismiss.setOnClickListener(view -> {
            StreamingAutopilotController.forgetSessions(activity, appKey);
            screen.refresh();
        });
        rows.add(accept);
        rows.add(dismiss);
        rows.add(panel.separator());
    }

    static String sessionAdviceText(Activity activity, StreamingAutopilot.SessionAdvice advice) {
        StreamingAutopilot.SessionSummary latest = advice.latest;
        String measured = activity.getString(R.string.console_session_advice_measured,
                latest.frameLossPercent, latest.rttMs, latest.rttVarianceMs,
                latest.decoderLatencyMs, latest.renderedFps, latest.receivedFps,
                latest.actualBitrateKbps / 1000d);
        String from = activity.getString(R.string.console_autopilot_stream_format,
                latest.width, latest.height, latest.fps, latest.bitrateKbps / 1000d);
        String to = activity.getString(R.string.console_autopilot_stream_format,
                advice.width, advice.height, advice.fps, advice.bitrateKbps / 1000d);
        boolean network = advice.issues.contains(StreamingAutopilot.SessionIssue.FRAME_LOSS_HIGH)
                || advice.issues.contains(StreamingAutopilot.SessionIssue.NETWORK_JITTER_HIGH);
        boolean decoder = advice.issues.contains(
                StreamingAutopilot.SessionIssue.FRAMES_DROPPED_BY_DECODER)
                || advice.issues.contains(StreamingAutopilot.SessionIssue.DECODER_LATENCY_HIGH);
        int cause = network && decoder ? R.string.console_session_advice_cause_both
                : network ? R.string.console_session_advice_cause_network
                : R.string.console_session_advice_cause_decoder;
        return activity.getString(R.string.console_session_advice_details,
                advice.poorSessions, advice.sessions, activity.getString(cause), measured,
                from, to);
    }

    /** A value row with its origin under the label. */
    private LinearLayout value(ConsolePanel panel, Key key, int label, String value) {
        LinearLayout row = panel.value(s(label), value);
        rowLabels[key.ordinal()] = s(label);
        Origin origin = origin(key, settings, parent, key != Key.REFRESH_RATE
                || !ConsoleStreamSettings.isBlankRate(inherited.actualDisplayRefreshRate));
        int text;
        switch (origin) {
            case GAME: text = R.string.console_app_stream_origin_game; break;
            case APP: text = R.string.console_app_stream_origin_app; break;
            case DEFAULT: text = R.string.console_app_stream_origin_default; break;
            default: text = R.string.console_app_stream_origin_built_in; break;
        }
        ((TextView) row.getChildAt(0)).setText(panel.withDetail(s(label), s(text)));
        return row;
    }

    /** Inherited, the standard values, a saved custom value and "Custom…". */
    private void chooseStandardOrCustom(int title, String[] names, String[] values, String current,
                                        String inheritedLabel, Consumer<String> apply,
                                        Runnable custom) {
        List<CharSequence> labels = new ArrayList<>();
        List<String> choices = new ArrayList<>();
        labels.add(inheritedLabel);
        choices.add(null);
        labels.addAll(Arrays.asList(names));
        choices.addAll(Arrays.asList(values));
        if (current != null && !choices.contains(current)) {
            labels.add(activity.getString(R.string.console_stream_custom_saved,
                    title == R.string.console_stream_fps ? current + " FPS" : times(current)));
            choices.add(current);
        }
        labels.add(s(R.string.console_stream_custom));
        screen.showChoices(s(title), "", labels.toArray(new CharSequence[0]),
                choices.indexOf(current), index -> {
                    if (index == choices.size()) {
                        custom.run();
                        return;
                    }
                    apply.accept(choices.get(index));
                    save();
                });
    }

    private void chooseOption(int title, String[] names, String[] values, String current,
                              String inheritedLabel, Consumer<String> apply) {
        List<CharSequence> labels = new ArrayList<>();
        labels.add(inheritedLabel);
        labels.addAll(Arrays.asList(names));
        int chosen = current == null ? 0 : Arrays.asList(values).indexOf(current) + 1;
        screen.showChoices(s(title), "", labels.toArray(new CharSequence[0]), chosen, index -> {
            apply.accept(index == 0 ? null : values[index - 1]);
            save();
        });
    }

    private void customResolution() {
        ConsolePanel panel = screen.panel();
        String[] parts = settings.resolution == null ? new String[0] : settings.resolution.split("x");
        EditText width = panel.numberField(s(R.string.console_stream_width),
                parts.length == 2 ? parts[0] : "", false);
        EditText height = panel.numberField(s(R.string.console_stream_height),
                parts.length == 2 ? parts[1] : "", false);
        TextView save = panel.action(s(R.string.console_stream_save));
        save.setOnClickListener(view -> {
            int w = (int) ConsoleStreamSettings.parsePositive(width.getText().toString());
            int h = (int) ConsoleStreamSettings.parsePositive(height.getText().toString());
            if (!validResolution(w, h)) {
                height.setError(s(R.string.app_stream_invalid_resolution));
                return;
            }
            settings.resolution = w + "x" + h;
            save();
            screen.closePage();
        });
        screen.showPage(s(R.string.console_stream_custom), "", new View[]{
                panel.section(s(R.string.console_stream_width)), width,
                panel.section(s(R.string.console_stream_height)), height, save}, 1);
    }

    static boolean validResolution(int width, int height) {
        return width > 0 && height > 0 && width <= 7680 && height <= 4320;
    }

    private void customFps() {
        ConsolePanel panel = screen.panel();
        EditText field = panel.numberField(s(R.string.console_stream_fps),
                settings.fps > 0 ? String.valueOf(settings.fps) : "", false);
        TextView save = panel.action(s(R.string.console_stream_save));
        save.setOnClickListener(view -> {
            String text = field.getText().toString().trim();
            int fps;
            try {
                fps = Integer.parseInt(text);
            } catch (NumberFormatException invalid) {
                fps = 0;
            }
            if (fps <= 0) {
                field.setError(s(R.string.app_stream_invalid_fps));
                return;
            }
            settings.fps = fps;
            save();
            screen.closePage();
        });
        screen.showPage(s(R.string.console_stream_custom), "", new View[]{
                panel.section(s(R.string.console_stream_fps)), field, save}, 1);
    }

    private void chooseBitrate() {
        ConsolePanel panel = screen.panel();
        TextView inheritedRow = screen.choiceRow(inheritedLabel('m',
                megabits(inherited.bitrate)), settings.bitrate <= 0);
        inheritedRow.setOnClickListener(view -> {
            settings.bitrate = 0;
            save();
            screen.closePage();
        });
        int initial = settings.bitrate > 0 ? settings.bitrate : inherited.bitrate;
        // A higher value saved before the stepper had a limit stays until it is changed.
        LinearLayout stepper = panel.stepper(s(R.string.console_app_stream_bitrate), initial,
                ConsoleStreamSettings.BITRATE_MIN_KBPS,
                Math.max(ConsoleStreamSettings.BITRATE_MAX_KBPS, initial),
                ConsoleStreamSettings.BITRATE_STEP_KBPS, this::megabits,
                megabits(ConsoleStreamSettings.BITRATE_STEP_KBPS), new int[0], kbps -> {
                    settings.bitrate = kbps;
                    save();
                });
        screen.showPage(s(R.string.console_app_stream_bitrate), "",
                new View[]{inheritedRow, stepper}, settings.bitrate > 0 ? 1 : 0);
    }

    private void chooseHapticsStrength() {
        ConsolePanel panel = screen.panel();
        TextView inheritedRow = screen.choiceRow(inheritedLabel('f',
                percent(inherited.audioHapticsStrength)), settings.audioHapticsStrength <= 0);
        inheritedRow.setOnClickListener(view -> {
            settings.audioHapticsStrength = 0;
            save();
            screen.closePage();
        });
        int initial = settings.audioHapticsStrength > 0 ? settings.audioHapticsStrength
                : inherited.audioHapticsStrength;
        LinearLayout stepper = panel.stepper(s(R.string.console_stream_audio_haptics_strength),
                initial, PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_MIN,
                PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_MAX,
                PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_STEP, this::percent,
                percent(PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_STEP), null, value -> {
                    settings.audioHapticsStrength = value;
                    save();
                });
        screen.showPage(s(R.string.console_stream_audio_haptics_strength), "",
                new View[]{inheritedRow, stepper}, settings.audioHapticsStrength > 0 ? 1 : 0);
    }

    private String percent(int value) {
        return activity.getString(R.string.console_stream_percent, value);
    }

    private void chooseRefreshRate() {
        ConsolePanel panel = screen.panel();
        TextView inheritedRow = screen.choiceRow(inheritedRefreshLabel(),
                settings.actualDisplayRefreshRate <= 0);
        inheritedRow.setOnClickListener(view -> {
            settings.actualDisplayRefreshRate = 0;
            save();
            screen.closePage();
        });
        EditText field = panel.numberField(s(R.string.console_stream_actual_refresh),
                settings.actualDisplayRefreshRate > 0 ? rate(settings.actualDisplayRefreshRate)
                        : "", true);
        TextView save = panel.action(s(R.string.console_stream_save));
        save.setOnClickListener(view -> {
            double value = ConsoleStreamSettings.parsePositive(field.getText().toString());
            if (value <= 0) {
                field.setError(s(R.string.console_stream_invalid_refresh));
                return;
            }
            settings.actualDisplayRefreshRate = value;
            save();
            screen.closePage();
        });
        screen.showPage(s(R.string.console_stream_actual_refresh),
                s(R.string.summary_actual_display_refresh_rate), new View[]{inheritedRow,
                        panel.section(s(R.string.console_stream_custom_hz)), field, save}, 0);
    }

    private String megabits(int kbps) {
        return ConsoleStreamSettings.megabits(activity, kbps);
    }

    private void save() {
        AppPreferences.saveAppSettings(activity, appKey, settings);
        // Sessions with the old settings are no evidence about the new ones.
        StreamingAutopilotController.forgetSessions(activity, appKey);
    }

    private String inheritedRefreshLabel() {
        String rate = inherited.actualDisplayRefreshRate;
        if (ConsoleStreamSettings.isBlankRate(rate)) {
            return s(inherit ? R.string.console_app_stream_inherited_plain
                    : R.string.console_app_stream_global_plain);
        }
        return inheritedLabel('f', activity.getString(R.string.console_stream_hz, rate.trim()));
    }

    /** "Globalna (1080p)": Polish needs the grammatical gender of the row (f, m, n). */
    private String inheritedLabel(char gender, String value) {
        int resource;
        switch (gender) {
            case 'f': resource = inherit ? R.string.console_app_stream_inherited_f
                    : R.string.console_app_stream_global_f; break;
            case 'm': resource = inherit ? R.string.console_app_stream_inherited_m
                    : R.string.console_app_stream_global_m; break;
            default: resource = inherit ? R.string.console_app_stream_inherited_n
                    : R.string.console_app_stream_global_n; break;
        }
        return activity.getString(resource, value);
    }

    private static String resolutionLabel(String[] names, String[] values, String resolution) {
        int index = Arrays.asList(values).indexOf(resolution);
        return index >= 0 ? names[index] : times(resolution);
    }

    private static String fpsLabel(String[] names, String[] values, int fps) {
        int index = Arrays.asList(values).indexOf(String.valueOf(fps));
        return index >= 0 ? names[index] : fps + " FPS";
    }

    private static String times(String resolution) {
        return resolution.replace('x', '×');
    }

    private static String rate(double rate) {
        return rate == Math.rint(rate) ? String.valueOf((long) rate) : String.valueOf(rate);
    }

    private String[] array(int resource) {
        return activity.getResources().getStringArray(resource);
    }

    private String s(int resource) {
        return activity.getString(resource);
    }
}
