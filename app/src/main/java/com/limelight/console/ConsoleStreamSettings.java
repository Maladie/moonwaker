package com.limelight.console;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.audio.AudioHaptics;
import com.limelight.binding.audio.UsbMicrophoneService;
import com.limelight.binding.input.GyroAim;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.gateway.GatewayConnection;
import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.ControllerGlyphs;
import com.limelight.ui.overlay.OverlayMenuView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The global stream settings rows of the top-level Settings categories (Stream, Sound,
 * Controllers and devices, Sessions, About and diagnostics), written to the default
 * SharedPreferences under the keys the stream reads through {@link PreferenceConfiguration}.
 * The categories themselves are assembled by {@code ConsoleActivity.showSettings}.
 */
final class ConsoleStreamSettings {
    static final int BITRATE_MIN_KBPS = 500;
    static final int BITRATE_MAX_KBPS = 150_000;
    static final int BITRATE_STEP_KBPS = 1_000;

    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    private final SharedPreferences prefs;
    private final PackageManager packages;
    /** The selected host's Gateway for "Zgłoś problem"; null without one. */
    private final Supplier<GatewayConnection> reportConnection;
    /** Read on each Stream render, so the FPS list follows "Unlock all frame rates". */
    private DisplayOptions display;

    ConsoleStreamSettings(Activity activity, ConsoleSettingsScreen screen,
                          Supplier<GatewayConnection> reportConnection) {
        this.activity = activity;
        this.screen = screen;
        this.reportConnection = reportConnection;
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        packages = activity.getPackageManager();
    }

    // PiP needs Oreo and the feature; Fire OS forbids it in its store guidelines.
    private boolean pipAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && packages.hasSystemFeature("android.software.picture_in_picture")
                && !packages.hasSystemFeature("com.amazon.software.fireos");
    }

    /** Settings › Stream: picture quality, then advanced video, the TV display and the PC. */
    View[] qualityRows(ConsolePanel panel) {
        display = DisplayOptions.read(activity, prefs);
        List<View> rows = new ArrayList<>(Arrays.asList(videoRows(panel)));
        rows.add(panel.section(s(R.string.console_stream_section_picture)));
        rows.add(panel.described(list(panel, R.string.console_stream_codec, "video_format", "auto",
                R.array.video_format_names, R.array.video_format_values),
                s(R.string.console_stream_desc_codec)));
        if (display.hdr != DisplayOptions.Hdr.UNSUPPORTED) {
            View hdr = toggle(panel, R.string.console_stream_hdr, "checkbox_enable_hdr", false);
            if (display.hdr == DisplayOptions.Hdr.BROKEN_FIRMWARE) {
                panel.setChecked(hdr, false);
                panel.disabled(hdr, s(R.string.console_stream_hdr_shield_reason));
            } else {
                panel.described(hdr, s(R.string.console_stream_desc_hdr));
            }
            rows.add(hdr);
        }
        rows.add(panel.described(toggle(panel, R.string.console_stream_full_range,
                "checkbox_full_range", false), s(R.string.console_stream_desc_full_range)));
        rows.add(panel.section(s(R.string.console_stream_section_display)));
        String refresh = prefs.getString("text_actual_display_refresh_rate", "");
        LinearLayout actual = panel.value(s(R.string.console_stream_actual_refresh),
                refreshLabel(refresh));
        actual.setOnClickListener(view -> chooseActualRefreshRate());
        rows.add(panel.described(actual, s(R.string.console_stream_desc_actual_refresh)));
        // The FPS list above depends on it, so the category is rendered again.
        rows.add(panel.described(toggle(panel, R.string.console_stream_unlock_fps,
                PreferenceConfiguration.UNLOCK_FPS_STRING, false, screen::refresh),
                s(R.string.console_stream_desc_unlock_fps)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_reduce_refresh,
                "checkbox_reduce_refresh_rate", false),
                s(R.string.console_stream_desc_reduce_refresh)));
        rows.add(panel.section(s(R.string.console_stream_section_host)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_sops,
                "checkbox_enable_sops", true), s(R.string.console_stream_desc_sops)));
        return rows.toArray(new View[0]);
    }

    /**
     * Resolution and FPS; the bitrate follows them (the recommended value), and a game's own
     * bitrate is chosen only in the stream menu.
     */
    private View[] videoRows(ConsolePanel panel) {
        String resolution = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING,
                PreferenceConfiguration.DEFAULT_RESOLUTION);
        String fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING,
                PreferenceConfiguration.DEFAULT_FPS);
        LinearLayout resolutionRow = panel.value(s(R.string.console_stream_resolution),
                label(display.resolutionNames, display.resolutionValues, resolution));
        resolutionRow.setOnClickListener(view -> chooseResolution());
        panel.described(resolutionRow, s(R.string.console_stream_desc_resolution));
        LinearLayout fpsRow = panel.value(s(R.string.console_stream_fps),
                label(display.fpsNames, display.fpsValues, fps));
        fpsRow.setOnClickListener(view -> chooseFps());
        panel.described(fpsRow, s(R.string.console_stream_desc_fps));
        return new View[]{resolutionRow, fpsRow,
                panel.described(list(panel, R.string.console_stream_frame_pacing, "frame_pacing",
                        "balanced", R.array.video_frame_pacing_names,
                        R.array.video_frame_pacing_values),
                        s(R.string.console_stream_desc_frame_pacing)),
                panel.section(s(R.string.console_stream_section_options)),
                panel.described(toggle(panel, R.string.console_stream_ultra_low_latency,
                        "checkbox_ultra_low_latency", false),
                        s(R.string.console_stream_desc_ultra_low_latency)),
                panel.described(toggle(panel, R.string.console_stream_stretch,
                        "checkbox_stretch_video", false),
                        s(R.string.console_stream_desc_stretch)),
                panel.described(toggle(panel, R.string.console_stream_emergency_mode,
                        PreferenceConfiguration.EMERGENCY_MODE_PREF_STRING, true),
                        s(R.string.console_stream_desc_emergency_mode))};
    }

    private void chooseResolution() {
        String current = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING,
                PreferenceConfiguration.DEFAULT_RESOLUTION);
        screen.showChoices(s(R.string.console_stream_resolution),
                s(R.string.console_stream_resolution_details), display.resolutionNames,
                Arrays.asList(display.resolutionValues).indexOf(current), index -> {
                    String value = display.resolutionValues[index];
                    Runnable apply = () -> {
                        prefs.edit().putString(PreferenceConfiguration.RESOLUTION_PREF_STRING, value)
                                .apply();
                        resetBitrate();
                        screen.refresh();
                    };
                    if (index >= display.nativeResolutionStart) confirmNative(
                            R.string.title_native_res_dialog, apply);
                    else apply.run();
                });
    }

    private void chooseFps() {
        String current = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING,
                PreferenceConfiguration.DEFAULT_FPS);
        screen.showChoices(s(R.string.console_stream_fps), s(R.string.console_stream_fps_details),
                display.fpsNames, Arrays.asList(display.fpsValues).indexOf(current), index -> {
                    String value = display.fpsValues[index];
                    Runnable apply = () -> {
                        prefs.edit().putString(PreferenceConfiguration.FPS_PREF_STRING, value).apply();
                        resetBitrate();
                        screen.refresh();
                    };
                    if (display.nativeFpsShown && index == display.fpsValues.length - 1) {
                        confirmNative(R.string.title_native_fps_dialog, apply);
                    } else {
                        apply.run();
                    }
                });
    }

    /** The former native resolution/FPS warning; the value applies once it is acknowledged. */
    private void confirmNative(int title, Runnable apply) {
        ConsoleConfirmDialog.show(activity, s(title), s(R.string.text_native_res_dialog),
                s(R.string.console_cancel), s(R.string.console_stream_choose), apply);
    }

    /** Resolution or FPS changed: the bitrate returns to the recommended value for both. */
    private void resetBitrate() {
        resetBitrate(prefs);
    }

    static void resetBitrate(SharedPreferences prefs) {
        prefs.edit().putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                PreferenceConfiguration.getDefaultBitrate(
                        prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING,
                                PreferenceConfiguration.DEFAULT_RESOLUTION),
                        prefs.getString(PreferenceConfiguration.FPS_PREF_STRING,
                                PreferenceConfiguration.DEFAULT_FPS))).apply();
    }

    /** Settings › Sound › Stream. */
    View[] audioRows(ConsolePanel panel) {
        return new View[]{
                panel.described(list(panel, R.string.console_stream_surround, "list_audio_config",
                        "2", R.array.audio_config_names, R.array.audio_config_values),
                        s(R.string.console_stream_desc_surround)),
                panel.described(toggle(panel, R.string.console_stream_audiofx,
                        "checkbox_enable_audiofx", false),
                        s(R.string.console_stream_desc_audiofx)),
                panel.described(toggle(panel, R.string.console_stream_host_audio,
                        "checkbox_host_audio", false), s(R.string.console_stream_desc_host_audio))};
    }

    /** Settings › Controllers and devices: one section per concern. */
    View[] controllerRows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        rows.add(panel.section(s(R.string.console_controls_section_pad)));
        rows.add(panel.described(percentStepper(panel, R.string.console_stream_deadzone,
                "seekbar_deadzone", 7, 0, 20, 1), s(R.string.console_stream_desc_deadzone)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_multi_controller,
                "checkbox_multi_controller", true),
                s(R.string.console_stream_desc_multi_controller)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_flip_face_buttons,
                "checkbox_flip_face_buttons", false),
                s(R.string.console_stream_desc_flip_face_buttons)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_touchpad_as_mouse,
                "checkbox_gamepad_touchpad_as_mouse", false),
                s(R.string.console_stream_desc_touchpad_as_mouse)));
        rows.add(panel.described(list(panel, R.string.console_stream_analog_scrolling,
                "analog_scrolling", "right", R.array.analog_scrolling_names,
                R.array.analog_scrolling_values),
                s(R.string.console_stream_desc_analog_scrolling)));
        rows.add(panel.described(list(panel, R.string.console_stream_battery_warning,
                "list_gamepad_battery_warning", "20", R.array.gamepad_battery_warning_names,
                R.array.gamepad_battery_warning_values),
                s(R.string.console_stream_desc_battery_warning)));

        rows.add(panel.section(s(R.string.console_controls_section_vibration)));
        Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            rows.add(panel.described(toggle(panel, R.string.console_stream_vibrate_fallback,
                    "checkbox_vibrate_fallback", false, screen::refresh),
                    s(R.string.console_stream_desc_vibrate_fallback)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                View strength = percentStepper(panel, R.string.console_stream_vibrate_strength,
                        "seekbar_vibrate_fallback_strength", 100, 0, 200, 5);
                if (!prefs.getBoolean("checkbox_vibrate_fallback", false)) {
                    panel.disabled(strength, s(R.string.console_stream_vibrate_strength_reason));
                }
                rows.add(strength);
            }
        }
        rows.add(panel.described(list(panel, R.string.console_stream_audio_haptics,
                PreferenceConfiguration.AUDIO_HAPTICS_PREF_STRING, AudioHaptics.MODE_OFF,
                R.array.audio_haptics_names, R.array.audio_haptics_values),
                s(R.string.console_stream_desc_audio_haptics) + "\n"
                        + HapticsPadSupport.describe(activity)));
        rows.add(percentStepper(panel, R.string.console_stream_audio_haptics_strength,
                PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_PREF_STRING,
                PreferenceConfiguration.DEFAULT_AUDIO_HAPTICS_STRENGTH,
                PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_MIN,
                PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_MAX,
                PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_STEP));

        rows.add(panel.section(s(R.string.console_controls_section_motion)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_motion_sensors,
                "checkbox_gamepad_motion_sensors", true),
                s(R.string.console_stream_desc_motion_sensors)));
        if (packages.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
                || packages.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
            rows.add(panel.described(toggle(panel, R.string.console_stream_motion_fallback,
                    "checkbox_gamepad_motion_fallback", false),
                    s(R.string.console_stream_desc_motion_fallback)));
        }
        // Gyro defaults for every game; pad sensors arrived in Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TextView gyro = panel.navigation(s(R.string.console_gyro_category));
            gyro.setOnClickListener(view -> openGyro());
            rows.add(panel.described(gyro, s(R.string.console_gyro_details_global)));
        }

        rows.add(panel.section(s(R.string.console_controls_section_buttons)));
        rows.add(panel.described(list(panel, R.string.console_stream_overlay_button,
                "overlay_trigger_button", "select", R.array.overlay_trigger_button_names,
                R.array.overlay_trigger_button_values),
                s(R.string.console_stream_desc_overlay_button)));
        rows.add(panel.described(list(panel, R.string.console_stream_create_button,
                PreferenceConfiguration.CREATE_BUTTON_PREF_STRING,
                PreferenceConfiguration.DEFAULT_CREATE_BUTTON, R.array.create_button_names,
                R.array.create_button_values),
                s(R.string.console_stream_desc_create_button)));
        rows.add(panel.described(list(panel, R.string.console_stream_hold_duration,
                "overlay_hold_duration", "1500", R.array.overlay_hold_duration_names,
                R.array.overlay_hold_duration_values),
                s(R.string.console_stream_desc_hold_duration)));
        TextView commands = panel.navigation(s(R.string.console_stream_commands));
        commands.setOnClickListener(view -> new ConsoleCustomCommands(activity, screen).open());
        rows.add(commands);
        rows.add(discordShortcut(panel, R.string.console_discord_mute_shortcut,
                "discord_mute_shortcut", "x"));
        rows.add(discordShortcut(panel, R.string.console_discord_leave_shortcut,
                "discord_leave_shortcut", "y"));

        rows.add(panel.section(s(R.string.console_controls_section_usb)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_usb_microphone,
                UsbMicrophoneService.PREF_ENABLED, false),
                s(R.string.console_stream_desc_usb_microphone)));
        if (packages.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            rows.add(panel.described(toggle(panel, R.string.console_stream_usb_driver,
                    "checkbox_usb_driver", true, screen::refresh),
                    s(R.string.console_stream_desc_usb_driver)));
            View bindAll = toggle(panel, R.string.console_stream_usb_bind_all,
                    "checkbox_usb_bind_all", false);
            if (!prefs.getBoolean("checkbox_usb_driver", true)) {
                panel.disabled(bindAll, s(R.string.console_stream_usb_bind_all_reason));
            }
            rows.add(bindAll);
        }

        rows.add(panel.section(s(R.string.console_stream_category_mouse)));
        rows.addAll(Arrays.asList(mouseRows(panel)));
        return rows.toArray(new View[0]);
    }

    /** A Guide chord for a Discord action during a stream, drawn as the stream overlay does. */
    private LinearLayout discordShortcut(ConsolePanel panel, int label, String key,
                                         String fallback) {
        String[] values = activity.getResources().getStringArray(R.array.discord_shortcut_values);
        boolean playStation = ControllerGlyphs.hasPlayStationController();
        CharSequence[] names = new CharSequence[values.length];
        for (int index = 0; index < values.length; index++) {
            ControllerGlyphs.Button glyph = OverlayMenuView.discordShortcutGlyph(values[index]);
            names[index] = glyph == null ? s(R.string.discord_shortcut_none)
                    : ControllerGlyphs.chord(activity, s(R.string.discord_shortcut_guide),
                    playStation, glyph);
        }
        int current = Arrays.asList(values).indexOf(prefs.getString(key, fallback));
        LinearLayout row = panel.value(s(label), current < 0 ? "" : names[current]);
        row.setOnClickListener(view -> screen.showChoices(s(label), "", names, current,
                index -> prefs.edit().putString(key, values[index]).apply()));
        return row;
    }

    /** The global gyro defaults, a screen of their own over Controllers and devices. */
    private void openGyro() {
        ConsoleSettingsScreen.Category gyro = new ConsoleGyroSettings(activity, screen,
                () -> PreferenceConfiguration.readPreferences(activity),
                new ConsoleGyroSettings.Store() {
                    @Override public GyroAim.Settings current() {
                        return AppPreferences.globalGyro(prefs);
                    }

                    @Override public void save(GyroAim.Settings settings) {
                        AppPreferences.saveGlobalGyro(prefs, settings);
                    }
                }).category(R.string.console_gyro_details_global);
        screen.push(s(R.string.console_settings_title) + "  ›  "
                        + s(R.string.console_settings_controllers),
                gyro.title, s(R.string.console_settings_controllers),
                Collections.singletonList(gyro), 0);
    }

    private View[] mouseRows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        boolean touchscreen = packages.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        if (touchscreen) {
            rows.add(panel.described(toggle(panel, R.string.console_stream_touchscreen_trackpad,
                    "checkbox_touchscreen_trackpad", true),
                    s(R.string.console_stream_desc_touchscreen_trackpad)));
        }
        rows.add(panel.described(toggle(panel, R.string.console_stream_mouse_nav_buttons,
                "checkbox_mouse_nav_buttons", false),
                s(R.string.console_stream_desc_mouse_nav_buttons)));
        // Pointer capture arrived in Oreo; SHIELD has raw mouse input in that mode anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !packages.hasSystemFeature("com.nvidia.feature.shield")) {
            rows.add(panel.described(toggle(panel, R.string.console_stream_absolute_mouse,
                    "checkbox_absolute_mouse_mode", false),
                    s(R.string.console_stream_desc_absolute_mouse)));
        }
        if (!touchscreen) return rows.toArray(new View[0]);
        boolean shown = prefs.getBoolean("checkbox_show_onscreen_controls", false);
        String reason = s(R.string.console_stream_onscreen_reason);
        rows.add(panel.section(s(R.string.console_stream_section_onscreen)));
        rows.add(panel.described(toggle(panel, R.string.console_stream_onscreen_controls,
                "checkbox_show_onscreen_controls", false, screen::refresh),
                s(R.string.console_stream_desc_onscreen_controls)));
        List<View> dependent = new ArrayList<>();
        Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            dependent.add(panel.described(toggle(panel, R.string.console_stream_vibrate_osc,
                    "checkbox_vibrate_osc", true), s(R.string.console_stream_desc_vibrate_osc)));
        }
        dependent.add(panel.described(toggle(panel, R.string.console_stream_only_l3r3,
                "checkbox_only_show_L3R3", false), s(R.string.console_stream_desc_only_l3r3)));
        dependent.add(panel.described(toggle(panel, R.string.console_stream_guide_button,
                "checkbox_show_guide_button", true), s(R.string.console_stream_desc_guide_button)));
        dependent.add(panel.described(percentStepper(panel, R.string.console_stream_osc_opacity,
                "seekbar_osc_opacity", 90, 0, 100, 5),
                s(R.string.console_stream_desc_osc_opacity)));
        for (View row : dependent) {
            if (!shown) panel.disabled(row, reason);
            rows.add(row);
        }
        TextView reset = panel.destructive(panel.action(s(R.string.console_stream_reset_osc)));
        panel.described(reset, s(R.string.console_stream_desc_reset_osc));
        reset.setOnClickListener(view -> ConsoleConfirmDialog.show(activity,
                s(R.string.dialog_title_reset_osc), s(R.string.dialog_text_reset_osc),
                s(R.string.no), s(R.string.yes), () -> {
                    activity.getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE,
                            Context.MODE_PRIVATE).edit().clear().apply();
                    ConsoleUiFeedback.makeText(activity, R.string.toast_reset_osc_success,
                            Toast.LENGTH_SHORT).show();
                }));
        rows.add(reset);
        return rows.toArray(new View[0]);
    }

    /** Settings › Sessions: what happens to a stream around sleep and leaving the app. */
    View[] sessionRows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        rows.add(panel.described(toggle(panel, R.string.console_stream_auto_resume,
                "checkbox_auto_resume_stream", false),
                s(R.string.console_stream_desc_auto_resume)));
        if (pipAvailable()) {
            rows.add(panel.described(toggle(panel, R.string.console_stream_pip,
                    "checkbox_enable_pip", false), s(R.string.console_stream_desc_pip)));
        }
        return rows.toArray(new View[0]);
    }

    /** Settings › About and diagnostics › Diagnostics. */
    View[] diagnosticsRows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>(Arrays.asList(
                panel.described(toggle(panel, R.string.console_stream_perf_overlay,
                        "checkbox_enable_perf_overlay", false),
                        s(R.string.console_stream_desc_perf_overlay)),
                panel.described(toggle(panel, R.string.console_stream_latency_toast,
                        "checkbox_enable_post_stream_toast", false),
                        s(R.string.console_stream_desc_latency_toast)),
                panel.described(toggle(panel, R.string.console_stream_disable_warnings,
                        "checkbox_disable_warnings", false),
                        s(R.string.console_stream_desc_disable_warnings))));
        // Reports go to the owner's Discord webhook: tester builds (-Ptesting) only.
        if (BuildConfig.TESTING) {
            TextView report = panel.action(s(R.string.problem_report_title));
            panel.described(report, s(R.string.problem_report_settings_detail));
            report.setOnClickListener(view ->
                    ProblemReportDialog.show(activity, reportConnection.get()));
            rows.add(report);
        }
        return rows.toArray(new View[0]);
    }

    /** Empty or "0" = automatic, as the stream reads it. */
    private void chooseActualRefreshRate() {
        String current = prefs.getString("text_actual_display_refresh_rate", "");
        TextView automatic = screen.choiceRow(s(R.string.console_stream_actual_refresh_auto),
                isBlankRate(current));
        automatic.setOnClickListener(view -> {
            prefs.edit().putString("text_actual_display_refresh_rate", "").apply();
            screen.closePage();
        });
        ConsolePanel panel = screen.panel();
        EditText field = panel.numberField(s(R.string.console_stream_actual_refresh),
                isBlankRate(current) ? "" : current, true);
        TextView save = panel.action(s(R.string.console_stream_save));
        save.setOnClickListener(view -> {
            String value = field.getText().toString().trim();
            if (parsePositive(value) <= 0) {
                field.setError(s(R.string.console_stream_invalid_refresh));
                return;
            }
            prefs.edit().putString("text_actual_display_refresh_rate", value).apply();
            screen.closePage();
        });
        screen.showPage(s(R.string.console_stream_actual_refresh),
                s(R.string.summary_actual_display_refresh_rate),
                new View[]{automatic, panel.section(s(R.string.console_stream_custom_hz)), field,
                        save}, 0);
    }

    private LinearLayout toggle(ConsolePanel panel, int label, String key, boolean fallback) {
        return toggle(panel, label, key, fallback, null);
    }

    /** A boolean preference; {@code after} runs once it is written (e.g. to re-render). */
    private LinearLayout toggle(ConsolePanel panel, int label, String key, boolean fallback,
                                Runnable after) {
        LinearLayout row = panel.toggle(s(label), prefs.getBoolean(key, fallback));
        row.setOnClickListener(view -> {
            boolean next = !prefs.getBoolean(key, fallback);
            prefs.edit().putBoolean(key, next).apply();
            panel.setChecked(row, next);
            if (after != null) after.run();
        });
        return row;
    }

    /** A string preference picked from a resource list. */
    private LinearLayout list(ConsolePanel panel, int label, String key, String fallback,
                              int names, int values) {
        LinearLayout row = panel.value(s(label), label(
                activity.getResources().getStringArray(names),
                activity.getResources().getStringArray(values), prefs.getString(key, fallback)));
        row.setOnClickListener(view -> listChooser(label, key, fallback, names, values));
        return row;
    }

    private void listChooser(int label, String key, String fallback, int names, int values) {
        String[] valueArray = activity.getResources().getStringArray(values);
        screen.showChoices(s(label), "", activity.getResources().getStringArray(names),
                Arrays.asList(valueArray).indexOf(prefs.getString(key, fallback)),
                index -> prefs.edit().putString(key, valueArray[index]).apply());
    }

    private LinearLayout percentStepper(ConsolePanel panel, int label, String key, int fallback,
                                        int min, int max, int step) {
        return panel.stepper(s(label), prefs.getInt(key, fallback), min, max, step, this::percent,
                percent(step), null, value -> prefs.edit().putInt(key, value).apply());
    }

    private String percent(int value) {
        return activity.getString(R.string.console_stream_percent, value);
    }

    static String megabits(Context context, int kbps) {
        return context.getString(R.string.console_stream_mbps, megabitNumber(kbps));
    }

    /** "20" or "0,5" (locale decimal) for a kbps value. */
    static String megabitNumber(int kbps) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(1);
        return format.format(kbps / 1000.0);
    }

    String refreshLabel(String rate) {
        return isBlankRate(rate) ? s(R.string.console_stream_actual_refresh_auto)
                : activity.getString(R.string.console_stream_hz, rate);
    }

    static boolean isBlankRate(String rate) {
        return parsePositive(rate) <= 0;
    }

    static double parsePositive(String value) {
        try {
            return value == null || value.trim().isEmpty() ? 0 : Double.parseDouble(value.trim());
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    /** The name shown for {@code value}, or the raw value when the list does not hold it. */
    static String label(CharSequence[] names, CharSequence[] values, String value) {
        int index = Arrays.asList(values).indexOf(value);
        return index >= 0 ? names[index].toString() : value;
    }

    private String s(int resource) {
        return activity.getString(resource);
    }

    /**
     * The resolution and FPS lists this display and its decoders support, native entries and
     * the HDR state, ported from the former preference screen. Like it, opening the screen moves
     * a saved resolution or FPS the display no longer supports to the next lower one and resets
     * the bitrate.
     */
    static final class DisplayOptions {
        enum Hdr { SUPPORTED, UNSUPPORTED, BROKEN_FIRMWARE }

        String[] resolutionNames;
        String[] resolutionValues;
        int nativeResolutionStart = Integer.MAX_VALUE;
        String[] fpsNames;
        String[] fpsValues;
        boolean nativeFpsShown;
        Hdr hdr = Hdr.SUPPORTED;

        static DisplayOptions read(Activity activity, SharedPreferences prefs) {
            DisplayOptions options = new DisplayOptions();
            options.resolutionNames = activity.getResources().getStringArray(R.array.resolution_names);
            options.resolutionValues = activity.getResources().getStringArray(R.array.resolution_values);
            options.fpsNames = activity.getResources().getStringArray(R.array.fps_names);
            options.fpsValues = activity.getResources().getStringArray(R.array.fps_values);
            PackageManager packages = activity.getPackageManager();
            Display display = activity.getWindowManager().getDefaultDisplay();
            float maxFps = display.getRefreshRate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxWidth = 0;
                // A native resolution without the cutout insets, for content kept off the notch.
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cutout = display.getCutout();
                    } else {
                        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                        if (insets != null) cutout = insets.getDisplayCutout();
                    }
                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();
                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);
                            int width = Math.max(metrics.widthPixels - widthInsets,
                                    metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets,
                                    metrics.heightPixels - heightInsets);
                            options.addNativeResolutions(activity, width, height, false);
                            hasInsets = true;
                        }
                    }
                }
                // Resolutions up to the display's own are always allowed: decoders can report
                // nonsense (e.g. an Amlogic AVC decoder claiming at most 384 px wide).
                for (Display.Mode candidate : display.getSupportedModes()) {
                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    // TVs report odd native modes; only offer them above 4K there.
                    if (!packages.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                            || width > 3840 || height > 2160) {
                        options.addNativeResolutions(activity, width, height, hasInsets);
                    }
                    if ((width >= 3840 || height >= 2160) && maxWidth < 3840) maxWidth = 3840;
                    else if ((width >= 2560 || height >= 1440) && maxWidth < 2560) maxWidth = 2560;
                    else if ((width >= 1920 || height >= 1080) && maxWidth < 1920) maxWidth = 1920;
                    if (candidate.getRefreshRate() > maxFps) maxFps = candidate.getRefreshRate();
                }
                maxWidth = Math.max(maxWidth, decoderWidthSlot(activity));
                LimeLog.info("Maximum resolution slot: " + maxWidth);
                if (maxWidth != 0) {
                    if (maxWidth < 3840) options.removeResolution(prefs,
                            PreferenceConfiguration.RES_4K, PreferenceConfiguration.RES_1440P);
                    if (maxWidth < 2560) options.removeResolution(prefs,
                            PreferenceConfiguration.RES_1440P, PreferenceConfiguration.RES_1080P);
                    if (maxWidth < 1920) options.removeResolution(prefs,
                            PreferenceConfiguration.RES_1080P, PreferenceConfiguration.RES_720P);
                    // 720p always stays.
                }
            } else {
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                options.addNativeResolutions(activity, Math.max(metrics.widthPixels,
                        metrics.heightPixels), Math.min(metrics.widthPixels, metrics.heightPixels),
                        false);
            }
            if (!prefs.getBoolean(PreferenceConfiguration.UNLOCK_FPS_STRING, false)) {
                // Some room for refresh rates rounded down; 30 and 60 FPS always stay.
                if (maxFps < 118) options.removeFps(prefs, "120", "90");
                if (maxFps < 88) options.removeFps(prefs, "90", "60");
            }
            options.addNativeFps(activity, maxFps);
            options.hdr = hdrState(display);
            return options;
        }

        /** -1 until read: the decoders do not change while the app runs. */
        private static int decoderWidthSlot = -1;

        /**
         * The widest AVC/HEVC decoder slot, read once per process: the lookup walks every codec
         * and ran on the main thread each time Settings showed Stream (opening Settings, and
         * every pass over the category).
         */
        private static int decoderWidthSlot(Activity activity) {
            if (decoderWidthSlot < 0) {
                MediaCodecHelper.initialize(activity,
                        GlPreferences.readPreferences(activity).glRenderer);
                decoderWidthSlot = Math.max(decoderMaxWidth("video/avc"),
                        decoderMaxWidth("video/hevc"));
            }
            return decoderWidthSlot;
        }

        /** The width slot (1280/1920/3840) a decoder reports, if it reports 720p at all. */
        private static int decoderMaxWidth(String mime) {
            MediaCodecInfo decoder = MediaCodecHelper.findProbableSafeDecoder(mime, -1);
            if (decoder == null) return 0;
            Range<Integer> widths = decoder.getCapabilitiesForType(mime).getVideoCapabilities()
                    .getSupportedWidths();
            LimeLog.info(mime + " supported width range: " + widths.getLower() + " - "
                    + widths.getUpper());
            if (!widths.contains(1280)) return 0;
            if (widths.contains(3840)) return 3840;
            return widths.contains(1920) ? 1920 : 1280;
        }

        private static Hdr hdrState(Display display) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return Hdr.UNSUPPORTED;
            Display.HdrCapabilities capabilities = display.getHdrCapabilities();
            boolean hdr10 = false;
            if (capabilities != null) {
                for (int type : capabilities.getSupportedHdrTypes()) {
                    hdr10 |= type == Display.HdrCapabilities.HDR_TYPE_HDR10;
                }
            }
            if (!hdr10) return Hdr.UNSUPPORTED;
            return PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()
                    ? Hdr.BROKEN_FIRMWARE : Hdr.SUPPORTED;
        }

        private void addNativeResolutions(Activity activity, int width, int height,
                                          boolean insetsRemoved) {
            if (PreferenceConfiguration.isSquarishScreen(width, height)) {
                addNativeResolution(activity, height, width, insetsRemoved, true);
            }
            addNativeResolution(activity, width, height, insetsRemoved, false);
        }

        private void addNativeResolution(Activity activity, int width, int height,
                                         boolean insetsRemoved, boolean portrait) {
            String value = width + "x" + height;
            if (Arrays.asList(resolutionValues).contains(value)) return;
            String name = activity.getString(insetsRemoved
                    ? R.string.resolution_prefix_native_fullscreen
                    : R.string.resolution_prefix_native);
            if (PreferenceConfiguration.isSquarishScreen(width, height)) {
                name += " " + activity.getString(portrait ? R.string.resolution_prefix_native_portrait
                        : R.string.resolution_prefix_native_landscape);
            }
            nativeResolutionStart = Math.min(nativeResolutionStart, resolutionValues.length);
            resolutionNames = append(resolutionNames, name + " (" + width + "×" + height + ")");
            resolutionValues = append(resolutionValues, value);
        }

        private void addNativeFps(Activity activity, float fps) {
            int rounded = Math.round(fps);
            if (rounded == 0) return;
            String value = Integer.toString(rounded);
            nativeFpsShown = !Arrays.asList(fpsValues).contains(value);
            if (!nativeFpsShown) return;
            fpsNames = append(fpsNames, activity.getString(R.string.resolution_prefix_native)
                    + " (" + value + " " + activity.getString(R.string.fps_suffix_fps) + ")");
            fpsValues = append(fpsValues, value);
        }

        private void removeResolution(SharedPreferences prefs, String value, String fallback) {
            int index = Arrays.asList(resolutionValues).indexOf(value);
            if (index < 0) return;
            resolutionNames = remove(resolutionNames, index);
            resolutionValues = remove(resolutionValues, index);
            if (nativeResolutionStart != Integer.MAX_VALUE && index < nativeResolutionStart) {
                nativeResolutionStart--;
            }
            moveOff(prefs, PreferenceConfiguration.RESOLUTION_PREF_STRING,
                    PreferenceConfiguration.DEFAULT_RESOLUTION, value, fallback);
        }

        private void removeFps(SharedPreferences prefs, String value, String fallback) {
            int index = Arrays.asList(fpsValues).indexOf(value);
            if (index < 0) return;
            fpsNames = remove(fpsNames, index);
            fpsValues = remove(fpsValues, index);
            moveOff(prefs, PreferenceConfiguration.FPS_PREF_STRING,
                    PreferenceConfiguration.DEFAULT_FPS, value, fallback);
        }

        private static void moveOff(SharedPreferences prefs, String key, String fallbackDefault,
                                    String removed, String fallback) {
            if (!prefs.getString(key, fallbackDefault).equalsIgnoreCase(removed)) return;
            prefs.edit().putString(key, fallback).apply();
            resetBitrate(prefs);
        }

        private static String[] append(String[] values, String value) {
            String[] longer = Arrays.copyOf(values, values.length + 1);
            longer[values.length] = value;
            return longer;
        }

        private static String[] remove(String[] values, int index) {
            List<String> shorter = new ArrayList<>(Arrays.asList(values));
            shorter.remove(index);
            return shorter.toArray(new String[0]);
        }
    }
}
