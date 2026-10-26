package com.limelight.console;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.InputDevice;

import com.limelight.binding.audio.AudioHaptics;
import com.limelight.binding.input.GyroAim;
import com.limelight.console.FeatureShowcase.Feature;
import com.limelight.console.FeatureShowcase.Need;

import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Owns what "What MoonWaker can do" needs from the TV: which cards this TV and host support
 * and the stored last-seen set for the once-after-update "What's new". Callers: Settings › About
 * ({@link #open}), the update announcement ({@link #announceIfDue}) and the first-run wizard
 * ({@link #open} with its step label and an {@code onNext}).
 */
final class FeatureShowcaseLauncher {
    static final String PREF_SEEN = "feature_showcase_seen";
    private static final int VENDOR_SONY = 0x054c;
    private static final int VENDOR_NINTENDO = 0x057e;

    private final Activity activity;
    private final SharedPreferences preferences;
    private final FeatureShowcaseScreen screen;
    private final FeatureShowcaseScreen.Defaults defaults;

    FeatureShowcaseLauncher(Activity activity, SharedPreferences preferences,
                            boolean reducedMotion) {
        this.activity = activity;
        this.preferences = preferences;
        defaults = new GlobalDefaults(PreferenceManager.getDefaultSharedPreferences(activity));
        screen = new FeatureShowcaseScreen(activity, reducedMotion);
    }

    /**
     * Shows every card this TV and host support. {@code capabilities} is the selected host's
     * (null: the host-dependent cards stay hidden); {@code eyebrow} and {@code onNext} are the
     * wizard step's label and its X action, or "" and null.
     */
    void open(HostGatewayClient.Capabilities capabilities, String eyebrow, Runnable onNext) {
        Set<Need> met = met(capabilities);
        List<Feature> cards = FeatureShowcase.visible(met);
        if (cards.isEmpty()) return;
        screen.show(cards, eyebrow, false, defaults, onNext, met.contains(Need.PAD));
        remember(cards);
    }

    /**
     * After an update, shows only the cards not seen yet. A fresh install stores the whole
     * catalog and shows nothing; the first-run wizard shows the screen.
     * @return whether the announcement was shown
     */
    boolean announceIfDue(HostGatewayClient.Capabilities capabilities) {
        Set<String> seen = preferences.getStringSet(PREF_SEEN, null);
        Set<Need> met = met(capabilities);
        List<Feature> visible = FeatureShowcase.visible(met);
        List<Feature> fresh = FeatureShowcase.unseen(seen, visible, upgraded());
        if (seen == null && !upgraded()) {
            preferences.edit().putStringSet(PREF_SEEN, FeatureShowcase.allIds()).apply();
            return false;
        }
        if (fresh.isEmpty()) return false;
        screen.show(fresh, "", true, defaults, null, met.contains(Need.PAD));
        remember(fresh);
        return true;
    }

    /** "Turn on by default" flips the same global settings the stream settings screen edits. */
    private static final class GlobalDefaults implements FeatureShowcaseScreen.Defaults {
        private final SharedPreferences prefs;

        GlobalDefaults(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override public boolean available(Feature feature) {
            // Gyro settings exist where pad sensors do (Android 12).
            return feature != Feature.GYRO || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        }

        @Override public boolean isOn(Feature feature) {
            if (feature == Feature.GYRO) return AppPreferences.globalGyro(prefs).aim;
            return !AudioHaptics.MODE_OFF.equals(prefs.getString(
                    PreferenceConfiguration.AUDIO_HAPTICS_PREF_STRING, AudioHaptics.MODE_OFF));
        }

        @Override public void set(Feature feature, boolean on) {
            if (feature == Feature.GYRO) {
                GyroAim.Settings current = AppPreferences.globalGyro(prefs);
                AppPreferences.saveGlobalGyro(prefs, current.toBuilder().aim(on).build());
            } else {
                prefs.edit().putString(PreferenceConfiguration.AUDIO_HAPTICS_PREF_STRING,
                        on ? AudioHaptics.MODE_GAME : AudioHaptics.MODE_OFF).apply();
            }
        }
    }

    boolean isShowing() {
        return screen.isShowing();
    }

    private void remember(List<Feature> shown) {
        preferences.edit().putStringSet(PREF_SEEN, FeatureShowcase.markSeen(
                preferences.getStringSet(PREF_SEEN, null), shown)).apply();
    }

    private boolean upgraded() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(), 0);
            return info.lastUpdateTime > info.firstInstallTime;
        } catch (PackageManager.NameNotFoundException impossible) {
            return false;
        }
    }

    private Set<Need> met(HostGatewayClient.Capabilities capabilities) {
        Set<Need> met = EnumSet.of(Need.NONE);
        if (capabilities != null) {
            met.add(Need.HOST);
            if (capabilities.discord) met.add(Need.HOST_DISCORD);
            if (capabilities.sessionSuspend) met.add(Need.HOST_SUSPEND);
            if (capabilities.controlOperations) met.add(Need.HOST_GAMES);
            if (capabilities.childProfiles) met.add(Need.HOST_CHILDREN);
            if (capabilities.usbForwarding) met.add(Need.HOST_USB);
            if (capabilities.hostPlus) met.add(Need.HOST_PLUS);
        }
        if (activity.getPackageManager().hasSystemFeature("com.google.android.tv")) {
            met.add(Need.GOOGLE_TV);
        }
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || (device.getSources() & InputDevice.SOURCE_GAMEPAD)
                    != InputDevice.SOURCE_GAMEPAD) continue;
            met.add(Need.PAD);
            if (hasGyro(device)) met.add(Need.PAD_GYRO);
        }
        return met;
    }

    /**
     * The name of a connected pad with a gyroscope (Streaming Autopilot may propose gyro
     * aiming); null without one.
     */
    static String gyroPadName() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && (device.getSources() & InputDevice.SOURCE_GAMEPAD)
                    == InputDevice.SOURCE_GAMEPAD && hasGyro(device)) return device.getName();
        }
        return null;
    }

    /**
     * Same gate as ControllerHandler: InputDevice.getSensorManager() crashes on Android 12 unless
     * the pad could plausibly have motion sensors.
     */
    private static boolean hasGyro(InputDevice device) {
        boolean plausible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                || Build.VERSION.SDK_INT == Build.VERSION_CODES.S
                && (device.getVendorId() == VENDOR_SONY || device.getVendorId() == VENDOR_NINTENDO);
        return plausible && device.getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
    }
}
