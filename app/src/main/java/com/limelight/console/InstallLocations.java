package com.limelight.console;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Install locations the host lists per launcher (docs/agents/INSTALL_LOCATION_DESIGN.md) and
 * this TV's choices for them: a default per host and launcher, and the last one used.
 * An empty path means the host's own default.
 */
final class InstallLocations {
    static final String STEAM = "steam";
    static final String EPIC = "epic";

    static final class Target {
        final String path;
        final long freeBytes;
        final long totalBytes;
        final boolean hostDefault;

        Target(String path, long freeBytes, long totalBytes, boolean hostDefault) {
            this.path = path;
            this.freeBytes = freeBytes;
            this.totalBytes = totalBytes;
            this.hostDefault = hostDefault;
        }
    }

    private static final String DEFAULT_PREFIX = "install_location.default.";
    private static final String LAST_PREFIX = "install_location.last.";

    private InstallLocations() { }

    /** The launcher whose location can be chosen, else null (other games install as before). */
    static String launcher(PlayniteLibraryGame game) {
        String provider = game == null ? "" : game.provider;
        return STEAM.equals(provider) || EPIC.equals(provider) ? provider : null;
    }

    /** One launcher's targets from a {@code /install/targets} response. */
    static List<Target> parse(JSONObject response, String launcher) {
        JSONObject targets = response == null ? null : response.optJSONObject("targets");
        JSONArray values = targets == null ? null : targets.optJSONArray(launcher);
        if (values == null) return Collections.emptyList();
        List<Target> result = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            String path = value == null ? "" : value.optString("path", "").trim();
            if (path.isEmpty()) continue;
            result.add(new Target(path, Math.max(0, value.optLong("free_bytes", 0)),
                    Math.max(0, value.optLong("total_bytes", 0)),
                    value.optBoolean("default", false)));
        }
        return result;
    }

    /**
     * The row to focus first: the default setting, else the last used, else the host default.
     * Index 0 is "Host default"; target {@code i} is row {@code i + 1}.
     */
    static int preselected(List<Target> targets, String defaultPath, String lastPath) {
        int chosen = indexOf(targets, defaultPath);
        if (chosen < 0) chosen = indexOf(targets, lastPath);
        return chosen + 1;
    }

    static int indexOf(List<Target> targets, String path) {
        if (path == null || path.isEmpty()) return -1;
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).path.equalsIgnoreCase(path)) return index;
        }
        return -1;
    }

    static String defaultPath(SharedPreferences preferences, String hostUuid, String launcher) {
        return preferences.getString(key(DEFAULT_PREFIX, hostUuid, launcher), "");
    }

    static void setDefaultPath(SharedPreferences preferences, String hostUuid, String launcher,
                               String path) {
        preferences.edit().putString(key(DEFAULT_PREFIX, hostUuid, launcher),
                path == null ? "" : path).apply();
    }

    static String lastPath(SharedPreferences preferences, String hostUuid, String launcher) {
        return preferences.getString(key(LAST_PREFIX, hostUuid, launcher), "");
    }

    static void setLastPath(SharedPreferences preferences, String hostUuid, String launcher,
                            String path) {
        preferences.edit().putString(key(LAST_PREFIX, hostUuid, launcher),
                path == null ? "" : path).apply();
    }

    static String key(String prefix, String hostUuid, String launcher) {
        return prefix + (hostUuid == null ? "" : hostUuid.trim().toLowerCase(Locale.ROOT))
                + "." + launcher;
    }
}
