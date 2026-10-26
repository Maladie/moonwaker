package com.limelight.console;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import com.limelight.R;
import com.limelight.binding.input.GyroAim;
import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The "Gyro" category rows, shared by Settings › Controllers and devices › Gyro (the global
 * defaults) and a game's overrides. Where the settings live is the {@link Store}'s business.
 */
final class ConsoleGyroSettings {
    interface Store {
        GyroAim.Settings current();

        void save(GyroAim.Settings settings);
    }

    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    /** Motion-sensor permission and the overlay/Home trigger buttons the rows depend on. */
    private final Supplier<PreferenceConfiguration> config;
    private final Store store;

    ConsoleGyroSettings(Activity activity, ConsoleSettingsScreen screen,
                        Supplier<PreferenceConfiguration> config, Store store) {
        this.activity = activity;
        this.screen = screen;
        this.config = config;
        this.store = store;
    }

    ConsoleSettingsScreen.Category category(int details) {
        return new ConsoleSettingsScreen.Category(s(R.string.console_gyro_category), s(details),
                this::rows);
    }

    private View[] rows(ConsolePanel panel) {
        PreferenceConfiguration prefs = config.get();
        GyroAim.Settings g = store.current();
        String blocked = prefs.gamepadMotionSensors ? null : s(R.string.console_gyro_reason_sensors);
        String hostOnly = blocked != null ? blocked
                : g.aim ? null : s(R.string.console_gyro_reason_host);
        String stickOnly = hostOnly != null ? hostOnly : g.output == GyroAim.Output.STICK ? null
                : s(R.string.console_gyro_deadzone_reason);
        boolean buttonUsed = g.activation != GyroAim.Activation.ALWAYS;
        boolean buttonMaskable = g.activation == GyroAim.Activation.HOLD_OFF
                || g.activation == GyroAim.Activation.TOGGLE;

        LinearLayout mode = choice(panel, R.string.console_gyro_mode,
                labels(R.string.console_gyro_mode_host, R.string.console_gyro_mode_aim),
                g.aim ? 1 : 0, index -> put("aim", index == 1));
        LinearLayout output = choice(panel, R.string.console_gyro_output,
                labels(R.string.console_gyro_output_mouse, R.string.console_gyro_output_stick),
                g.output.ordinal(), index -> put("output", GyroAim.Output.values()[index].name()));
        LinearLayout activation = choice(panel, R.string.console_gyro_activation,
                labels(R.string.console_gyro_activation_always, R.string.console_gyro_activation_hold_on,
                        R.string.console_gyro_activation_hold_off, R.string.console_gyro_activation_toggle),
                g.activation.ordinal(),
                index -> put("activation", GyroAim.Activation.values()[index].name()));
        List<GyroAim.Button> buttons = GyroAim.allowedButtons(prefs.overlayTriggerButton);
        String[] buttonLabels = new String[buttons.size()];
        for (int i = 0; i < buttonLabels.length; i++) buttonLabels[i] = buttonLabel(activity, buttons.get(i));
        LinearLayout button = panel.value(s(R.string.console_gyro_button), buttonLabel(activity, g.button));
        button.setOnClickListener(view -> screen.showChoices(s(R.string.console_gyro_button), "",
                buttonLabels, buttons.indexOf(store.current().button),
                index -> put("button", buttons.get(index).name())));
        LinearLayout deadzone = panel.stepper(s(R.string.console_gyro_deadzone),
                Math.round(g.stickDeadzone * 100), 0, 40, 1, this::percent, percent(1), null,
                value -> put("stickDeadzone", value / 100.0));

        boolean stick = g.output == GyroAim.Output.STICK;
        LinearLayout gearing = stick
                ? panel.stepper(s(R.string.console_gyro_stick_speed), g.stickTurnSpeed,
                        GyroAim.STICK_TURN_MIN, GyroAim.STICK_TURN_MAX, GyroAim.STICK_TURN_STEP,
                        value -> activity.getString(R.string.console_gyro_deg_s, value),
                        activity.getString(R.string.console_gyro_deg_s, GyroAim.STICK_TURN_STEP),
                        null, value -> put("stickTurnSpeed", value))
                : panel.stepper(s(R.string.console_gyro_mouse_360), g.mouseCountsPer360,
                        GyroAim.MOUSE_360_MIN, GyroAim.MOUSE_360_MAX, GyroAim.MOUSE_360_STEP,
                        String::valueOf, String.valueOf(GyroAim.MOUSE_360_STEP), null,
                        value -> put("mouseCountsPer360", value));

        return new View[]{
                row(panel, mode, R.string.console_gyro_desc_mode, blocked),
                row(panel, output, R.string.console_gyro_desc_output, hostOnly),
                row(panel, activation, R.string.console_gyro_desc_activation, hostOnly),
                row(panel, button, R.string.console_gyro_desc_button, hostOnly != null
                        ? hostOnly : buttonUsed ? null : s(R.string.console_gyro_button_reason)),
                row(panel, toggle(panel, R.string.console_gyro_button_only, "buttonGyroOnly",
                        s -> s.buttonGyroOnly), R.string.console_gyro_desc_button_only,
                        hostOnly != null ? hostOnly
                                : buttonMaskable ? null : s(R.string.console_gyro_button_only_reason)),
                row(panel, toggle(panel, R.string.console_gyro_stick_pause, "stickPausesGyro",
                        s -> s.stickPausesGyro), R.string.console_gyro_desc_stick_pause, hostOnly),
                row(panel, gearing, stick ? R.string.console_gyro_desc_stick_speed
                        : R.string.console_gyro_desc_mouse_360, hostOnly),
                row(panel, sensitivity(panel, R.string.console_gyro_sens_x, g.sensX, "sensX"),
                        R.string.console_gyro_desc_sens_x, hostOnly),
                row(panel, sensitivity(panel, R.string.console_gyro_sens_y, g.sensY, "sensY"),
                        R.string.console_gyro_desc_sens_y, hostOnly),
                row(panel, choice(panel, R.string.console_gyro_acceleration,
                        labels(R.string.console_gyro_acceleration_none,
                                R.string.console_gyro_acceleration_low,
                                R.string.console_gyro_acceleration_standard,
                                R.string.console_gyro_acceleration_high),
                        g.acceleration.ordinal(), index -> put("acceleration",
                                GyroAim.Acceleration.values()[index].name())),
                        R.string.console_gyro_desc_acceleration, hostOnly),
                row(panel, toggle(panel, R.string.console_gyro_invert_x, "invertX", s -> s.invertX),
                        R.string.console_gyro_desc_invert, hostOnly),
                row(panel, toggle(panel, R.string.console_gyro_invert_y, "invertY", s -> s.invertY),
                        R.string.console_gyro_desc_invert, hostOnly),
                row(panel, choice(panel, R.string.console_gyro_axis,
                        labels(R.string.console_gyro_axis_player, R.string.console_gyro_axis_yaw,
                                R.string.console_gyro_axis_roll), g.axis.ordinal(),
                        index -> put("axis", GyroAim.Axis.values()[index].name())),
                        R.string.console_gyro_desc_axis, hostOnly),
                row(panel, deadzone, R.string.console_gyro_desc_deadzone, stickOnly),
                row(panel, panel.stepper(s(R.string.console_gyro_curve),
                        Math.round(g.stickCurve * 10), 10, 30, 1,
                        tenths -> String.format(Locale.ROOT, "%.1f", tenths / 10f), "0.1", null,
                        tenths -> put("stickCurve", tenths / 10.0)),
                        R.string.console_gyro_desc_curve, stickOnly),
        };
    }

    /** The row's explanation, or the reason it cannot be changed now (mockup 18). */
    private View row(ConsolePanel panel, View row, int description, String reason) {
        return reason != null ? panel.disabled(row, reason) : panel.described(row, s(description));
    }

    private LinearLayout choice(ConsolePanel panel, int label, String[] names, int current,
                                IntConsumer apply) {
        LinearLayout row = panel.value(s(label), names[current]);
        row.setOnClickListener(view -> screen.showChoices(s(label), "", names, current, apply));
        return row;
    }

    private LinearLayout toggle(ConsolePanel panel, int label, String key,
                                Predicate<GyroAim.Settings> value) {
        LinearLayout row = panel.toggle(s(label), value.test(store.current()));
        row.setOnClickListener(view -> {
            boolean next = !value.test(store.current());
            put(key, next);
            panel.setChecked(row, next);
        });
        return row;
    }

    /** A multiplier of 0.1–5.0 in steps of 0.1, kept as tenths by the stepper. */
    private LinearLayout sensitivity(ConsolePanel panel, int label, float value, String key) {
        return panel.stepper(s(label), Math.round(value * 10), 1, 50, 1,
                ConsoleGyroSettings::times10, times10(1), null, tenths -> put(key, tenths / 10.0));
    }

    static String times10(int tenths) {
        return String.format(Locale.ROOT, "%.1f×", tenths / 10f);
    }

    static String buttonLabel(Activity activity, GyroAim.Button button) {
        return button == GyroAim.Button.TOUCHPAD
                ? activity.getString(R.string.console_gyro_button_touchpad) : button.name();
    }

    private void put(String key, Object value) {
        JSONObject json = AppPreferences.gyroToJson(store.current());
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        store.save(AppPreferences.gyroFromJson(json, GyroAim.Settings.DEFAULT));
    }

    private String percent(int value) {
        return activity.getString(R.string.console_stream_percent, value);
    }

    private String[] labels(int... resources) {
        String[] labels = new String[resources.length];
        for (int i = 0; i < resources.length; i++) labels[i] = s(resources[i]);
        return labels;
    }

    private String s(int resource) {
        return activity.getString(resource);
    }
}
