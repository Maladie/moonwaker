package com.limelight.console;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Local pinned games, one set per host and profile, and the two Library options that use the
 * local play record: pinning itself and "played N days ago". A child's profile only ever shows
 * pins that its own library lists, because pins are matched against the rendered library.
 */
public final class PinnedGames {
    private static final String PINS_ENABLED = "pinned_games_enabled";
    private static final String RELATIVE_PLAYED = "last_played_relative";

    private PinnedGames() {}

    static String key(String hostUuid, String profileId) {
        return "pinned_games." + (hostUuid == null ? "" : hostUuid.trim().toLowerCase(Locale.ROOT))
                + "." + (profileId == null ? "" : profileId);
    }

    public static boolean pinsEnabled(SharedPreferences preferences) {
        return preferences.getBoolean(PINS_ENABLED, true);
    }

    public static void setPinsEnabled(SharedPreferences preferences, boolean enabled) {
        preferences.edit().putBoolean(PINS_ENABLED, enabled).apply();
    }

    public static boolean relativePlayed(SharedPreferences preferences) {
        return preferences.getBoolean(RELATIVE_PLAYED, true);
    }

    public static void setRelativePlayed(SharedPreferences preferences, boolean enabled) {
        preferences.edit().putBoolean(RELATIVE_PLAYED, enabled).apply();
    }

    /** The pinned game ids; empty while pinning is switched off (the pins stay stored). */
    public static Set<String> ids(SharedPreferences preferences, String hostUuid,
                                  String profileId) {
        if (!pinsEnabled(preferences)) return Collections.emptySet();
        return new HashSet<>(preferences.getStringSet(key(hostUuid, profileId),
                Collections.emptySet()));
    }

    public static void set(SharedPreferences preferences, String hostUuid, String profileId,
                           String gameId, boolean pinned) {
        Set<String> values = new HashSet<>(preferences.getStringSet(key(hostUuid, profileId),
                Collections.emptySet()));
        if (pinned) values.add(gameId);
        else values.remove(gameId);
        preferences.edit().putStringSet(key(hostUuid, profileId), values).apply();
    }
}
