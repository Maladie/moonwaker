package com.limelight.console;

import android.content.SharedPreferences;

import java.util.Locale;

/**
 * "Freeze when switching games" (Quick Resume, owner decision 6): per host, like the stream
 * warm-up, since the frozen slot is the host's. Off by default.
 */
final class QuickResumePreferences {
    private static final String KEY_PREFIX = "freeze_on_switch.";

    private QuickResumePreferences() { }

    static boolean freezesOnSwitch(SharedPreferences preferences, String hostUuid) {
        return preferences.getBoolean(key(hostUuid), false);
    }

    static void setFreezesOnSwitch(SharedPreferences preferences, String hostUuid,
                                   boolean enabled) {
        preferences.edit().putBoolean(key(hostUuid), enabled).apply();
    }

    private static String key(String hostUuid) {
        return KEY_PREFIX + (hostUuid == null ? "" : hostUuid.trim().toLowerCase(Locale.ROOT));
    }
}
