package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.binding.input.GyroAim;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.Predicate;

public class AppPreferences {
    public static final String APP_PREFERENCES_FILE = "AppPreferences";
    /**
     * Settings › Controllers and devices › Gyro: the gyro object every game starts from
     * (default prefs).
     */
    public static final String PREF_GYRO_DEFAULTS = "gyro_aim_defaults";

    public static class AppSettings {
        public String resolution;
        public int fps;
        public String framePacing;
        public int bitrate;
        public double actualDisplayRefreshRate;
        public String enableHdr;
        public String enablePerfOverlay;
        /** Audio haptics mode, null to inherit. */
        public String audioHaptics;
        /** Audio haptics strength in percent, 0 to inherit. */
        public int audioHapticsStrength;
        public boolean useGlobalSettings;
        /** Gyro aiming, outside "use global settings": null = not overridden here. */
        public JSONObject gyro;

        public AppSettings() {
            this.useGlobalSettings = true;
        }

        public AppSettings(String resolution, int fps, String framePacing, int bitrate, double actualDisplayRefreshRate, String enableHdr, String enablePerfOverlay, boolean useGlobalSettings) {
            this.resolution = resolution;
            this.fps = fps;
            this.framePacing = framePacing;
            this.bitrate = bitrate;
            this.actualDisplayRefreshRate = actualDisplayRefreshRate;
            this.enableHdr = enableHdr;
            this.enablePerfOverlay = enablePerfOverlay;
            this.useGlobalSettings = useGlobalSettings;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("resolution", resolution);
            json.put("fps", fps);
            json.put("framePacing", framePacing);
            json.put("bitrate", bitrate);
            json.put("actualDisplayRefreshRate", actualDisplayRefreshRate);
            json.put("enableHdr", enableHdr);
            json.put("enablePerfOverlay", enablePerfOverlay);
            json.put("audioHaptics", audioHaptics);
            json.put("audioHapticsStrength", audioHapticsStrength);
            json.put("useGlobalSettings", useGlobalSettings);
            if (gyro != null) json.put("gyro", gyro);
            return json;
        }

        public static AppSettings fromJson(JSONObject json) throws JSONException {
            AppSettings settings = new AppSettings(
                json.optString("resolution", null),
                json.optInt("fps", 0),
                json.optString("framePacing", null),
                json.optInt("bitrate", 0),
                json.optDouble("actualDisplayRefreshRate", 0),
                json.optString("enableHdr", null),
                json.optString("enablePerfOverlay", null),
                json.optBoolean("useGlobalSettings", true)
            );
            settings.audioHaptics = json.optString("audioHaptics", null);
            settings.audioHapticsStrength = json.optInt("audioHapticsStrength", 0);
            settings.gyro = json.optJSONObject("gyro");
            return settings;
        }
    }

    /** The key a Playnite game's own settings live under, for its screen and its streams. */
    public static String playniteGameKey(String hostId, String gameId) {
        return "playnite:" + (hostId == null ? "" : hostId.trim()) + ":"
                + (gameId == null ? "" : gameId.trim());
    }

    /**
     * The settings under the game layer {@code gameKey}. Every Playnite game streams through
     * the same Sunshine launcher app, so that app's settings belong to no game: a Playnite
     * game starts from the global defaults, as Console › game settings shows it.
     */
    static AppSettings appLayer(Context context, String appKey, String gameKey) {
        return gameKey != null && gameKey.startsWith("playnite:")
                ? new AppSettings() : getAppSettings(context, appKey);
    }

    /**
     * The key a stream's own settings live under, as Console opens them: the Playnite game it
     * runs now, else its Quick Launch item, else its Sunshine app. Null on the neutral stream
     * target without a game: that target's Sunshine app is what every Playnite game inherits.
     */
    public static String streamSettingsKey(String hostId, int appId, String playniteGameId,
                                           String quickLaunchKey, boolean neutralTarget) {
        if (playniteGameId != null && !playniteGameId.trim().isEmpty()) {
            return playniteGameKey(hostId, playniteGameId);
        }
        if (neutralTarget) return null;
        if (quickLaunchKey != null && !quickLaunchKey.isEmpty()) return quickLaunchKey;
        return hostId + ":" + appId;
    }

    public static AppSettings getAppSettings(Context context, String appKey) {
        return getAppSettings(context.getSharedPreferences(APP_PREFERENCES_FILE,
                Context.MODE_PRIVATE), appKey);
    }

    /** Reads from an already opened {@link #APP_PREFERENCES_FILE}, for many apps in a row. */
    public static AppSettings getAppSettings(SharedPreferences prefs, String appKey) {
        String jsonString = prefs.getString(appKey, null);
        
        if (jsonString == null) {
            return new AppSettings();
        }
        
        try {
            JSONObject json = new JSONObject(jsonString);
            return AppSettings.fromJson(json);
        } catch (JSONException e) {
            return new AppSettings();
        }
    }

    public static void saveAppSettings(Context context, String appKey, AppSettings settings) {
        SharedPreferences prefs = context.getSharedPreferences(APP_PREFERENCES_FILE, Context.MODE_PRIVATE);
        try {
            JSONObject json = settings.toJson();
            prefs.edit().putString(appKey, json.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void applyStreamSettings(Context context, String appKey, int width, int height,
                                           int fps, int bitrateKbps) {
        saveAppSettings(context, appKey, copyWithStreamSettings(
                getAppSettings(context, appKey), width, height, fps, bitrateKbps));
    }

    /** The settings a live stream switched to, kept for its game's later streams. */
    public static void applyLiveStreamSettings(Context context, String appKey, int width,
                                               int height, int fps, int bitrateKbps,
                                               boolean hdr) {
        AppSettings settings = copyWithStreamSettings(getAppSettings(context, appKey), width,
                height, fps, bitrateKbps);
        settings.enableHdr = Boolean.toString(hdr);
        saveAppSettings(context, appKey, settings);
    }

    static AppSettings copyWithStreamSettings(AppSettings current, int width, int height, int fps,
                                              int bitrateKbps) {
        PreferenceConfiguration.validateStreamSettings(width, height, fps, bitrateKbps);
        AppSettings copy = new AppSettings(width + "x" + height, fps, current.framePacing,
                bitrateKbps, current.actualDisplayRefreshRate, current.enableHdr,
                current.enablePerfOverlay, false);
        copy.audioHaptics = current.audioHaptics;
        copy.audioHapticsStrength = current.audioHapticsStrength;
        copy.gyro = current.gyro;
        return copy;
    }

    /** A "gyro" object; missing keys (or no object) take their value from {@code d}. */
    public static GyroAim.Settings gyroFromJson(JSONObject json, GyroAim.Settings d) {
        if (json == null) return d;
        return new GyroAim.Builder()
                .aim(json.optBoolean("aim", d.aim))
                .output(GyroAim.Output.valueOf(json.optString("output", d.output.name())))
                .activation(GyroAim.Activation.valueOf(
                        json.optString("activation", d.activation.name())))
                .button(GyroAim.Button.valueOf(json.optString("button", d.button.name())))
                .buttonGyroOnly(json.optBoolean("buttonGyroOnly", d.buttonGyroOnly))
                .stickPausesGyro(json.optBoolean("stickPausesGyro", d.stickPausesGyro))
                .sensX((float) json.optDouble("sensX", d.sensX))
                .sensY((float) json.optDouble("sensY", d.sensY))
                .invertX(json.optBoolean("invertX", d.invertX))
                .invertY(json.optBoolean("invertY", d.invertY))
                .axis(GyroAim.Axis.valueOf(json.optString("axis", d.axis.name())))
                .acceleration(GyroAim.Acceleration.valueOf(
                        json.optString("acceleration", d.acceleration.name())))
                .stickDeadzone((float) json.optDouble("stickDeadzone", d.stickDeadzone))
                .stickCurve((float) json.optDouble("stickCurve", d.stickCurve))
                .stickTurnSpeed(json.optInt("stickTurnSpeed", d.stickTurnSpeed))
                .mouseCountsPer360(json.optInt("mouseCountsPer360", d.mouseCountsPer360))
                .build();
    }

    public static JSONObject gyroToJson(GyroAim.Settings s) {
        try {
            return new JSONObject()
                    .put("aim", s.aim)
                    .put("output", s.output.name())
                    .put("activation", s.activation.name())
                    .put("button", s.button.name())
                    .put("buttonGyroOnly", s.buttonGyroOnly)
                    .put("sensX", (double) s.sensX)
                    .put("sensY", (double) s.sensY)
                    .put("invertX", s.invertX)
                    .put("invertY", s.invertY)
                    .put("axis", s.axis.name())
                    .put("stickPausesGyro", s.stickPausesGyro)
                    .put("acceleration", s.acceleration.name())
                    .put("stickDeadzone", (double) s.stickDeadzone)
                    .put("stickCurve", (double) s.stickCurve)
                    .put("stickTurnSpeed", s.stickTurnSpeed)
                    .put("mouseCountsPer360", s.mouseCountsPer360);
        } catch (JSONException e) {
            throw new IllegalStateException(e); // only non-finite numbers throw
        }
    }

    /** Quick launch (Playnite game) → app → global; independent of "use global settings". */
    static GyroAim.Settings effectiveGyro(AppSettings quickLaunch, AppSettings app,
                                          GyroAim.Settings global) {
        return gyroFromJson(quickLaunch != null && quickLaunch.gyro != null
                ? quickLaunch.gyro : app.gyro, global);
    }

    /** The global gyro defaults; nothing saved (or unreadable) means the built-in defaults. */
    public static GyroAim.Settings globalGyro(SharedPreferences defaultPrefs) {
        String json = defaultPrefs.getString(PREF_GYRO_DEFAULTS, null);
        if (json == null) return GyroAim.Settings.DEFAULT;
        try {
            return gyroFromJson(new JSONObject(json), GyroAim.Settings.DEFAULT);
        } catch (JSONException e) {
            return GyroAim.Settings.DEFAULT;
        }
    }

    public static void saveGlobalGyro(SharedPreferences defaultPrefs, GyroAim.Settings settings) {
        defaultPrefs.edit().putString(PREF_GYRO_DEFAULTS, gyroToJson(settings).toString()).apply();
    }

    /** Saves the whole gyro object under {@code appKey}, e.g. from the stream menu. */
    public static void saveGyro(Context context, String appKey, GyroAim.Settings settings) {
        AppSettings app = getAppSettings(context, appKey);
        app.gyro = gyroToJson(settings);
        saveAppSettings(context, appKey, app);
    }

    /**
     * The key whose audio haptics setting ({@code overrides}: mode or strength) a stream of
     * {@code appKey} and its game's {@code gameKey} uses, as Console › game settings shows its
     * origin: the game's override, else the Sunshine app's, else null for Settings › Controllers.
     */
    static String audioHapticsOwner(String appKey, AppSettings app, String gameKey,
                                    AppSettings game, Predicate<AppSettings> overrides) {
        if (game != null && !game.useGlobalSettings && overrides.test(game)) return gameKey;
        if (!app.useGlobalSettings && overrides.test(app)) return appKey;
        return null;
    }

    static final Predicate<AppSettings> HAPTICS_MODE = s -> s.audioHaptics != null;
    static final Predicate<AppSettings> HAPTICS_STRENGTH = s -> s.audioHapticsStrength > 0;

    /**
     * Writes {@code mode} and {@code strengthPercent} where the stream's audio haptics come
     * from, so every screen agrees.
     */
    public static void saveEffectiveAudioHaptics(Context context, String appKey, String gameKey,
                                                 String mode, int strengthPercent) {
        AppSettings game = gameKey == null ? null : getAppSettings(context, gameKey);
        AppSettings app = appLayer(context, appKey, gameKey);
        String modeOwner = audioHapticsOwner(appKey, app, gameKey, game, HAPTICS_MODE);
        String strengthOwner = audioHapticsOwner(appKey, app, gameKey, game, HAPTICS_STRENGTH);
        SharedPreferences.Editor global =
                PreferenceManager.getDefaultSharedPreferences(context).edit();
        if (modeOwner == null) {
            global.putString(PreferenceConfiguration.AUDIO_HAPTICS_PREF_STRING, mode);
        } else {
            (modeOwner.equals(gameKey) ? game : app).audioHaptics = mode;
        }
        if (strengthOwner == null) {
            global.putInt(PreferenceConfiguration.AUDIO_HAPTICS_STRENGTH_PREF_STRING,
                    strengthPercent);
        } else {
            (strengthOwner.equals(gameKey) ? game : app).audioHapticsStrength = strengthPercent;
        }
        global.apply();
        if (game != null && (gameKey.equals(modeOwner) || gameKey.equals(strengthOwner))) {
            saveAppSettings(context, gameKey, game);
        }
        if (appKey.equals(modeOwner) || appKey.equals(strengthOwner)) {
            saveAppSettings(context, appKey, app);
        }
    }

    private static int getFramePacingValue(String framePacingString) {
        if (framePacingString == null) {
            return PreferenceConfiguration.FRAME_PACING_MIN_LATENCY;
        }
        switch (framePacingString) {
            case "latency":
                return PreferenceConfiguration.FRAME_PACING_MIN_LATENCY;
            case "balanced":
                return PreferenceConfiguration.FRAME_PACING_BALANCED;
            case "cap-fps":
                return PreferenceConfiguration.FRAME_PACING_CAP_FPS;
            case "smoothness":
                return PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS;
            default:
                return PreferenceConfiguration.FRAME_PACING_MIN_LATENCY;
        }
    }

    public static PreferenceConfiguration getEffectivePreferences(Context context, String appKey) {
        return getEffectivePreferences(context, appKey, null);
    }

    public static PreferenceConfiguration getEffectivePreferences(Context context, String appKey, String appKey2) {
        // Load quick launch settings (appKey2), app settings (appKey), and global settings
        AppSettings quickLaunchSettings = null;
        if (appKey2 != null) {
            quickLaunchSettings = getAppSettings(context, appKey2);
        }
        AppSettings appSettings = appLayer(context, appKey, appKey2);
        GyroAim.Settings gyro = effectiveGyro(quickLaunchSettings, appSettings,
                globalGyro(PreferenceManager.getDefaultSharedPreferences(context)));

        // If both quick launch and app use global settings, return global config directly
        if ((quickLaunchSettings == null || quickLaunchSettings.useGlobalSettings) && appSettings.useGlobalSettings) {
            PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
            config.gyroAim = gyro;
            return config;
        }

        // Start with global settings as the base
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
        config.gyroAim = gyro;

        // Apply settings hierarchy: quick launch -> app -> global (already loaded)
        // For each setting, use quick launch if set, otherwise app, otherwise keep global

        // Resolution
        String resolution = null;
        if (quickLaunchSettings != null && quickLaunchSettings.resolution != null) {
            resolution = quickLaunchSettings.resolution;
        } else if (!appSettings.useGlobalSettings && appSettings.resolution != null) {
            resolution = appSettings.resolution;
        }
        if (resolution != null) {
            String[] parts = resolution.split("x");
            if (parts.length == 2) {
                try {
                    config.width = Integer.parseInt(parts[0]);
                    config.height = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    // Keep global settings
                }
            }
        }

        // FPS
        int fps = 0;
        if (quickLaunchSettings != null && quickLaunchSettings.fps > 0) {
            fps = quickLaunchSettings.fps;
        } else if (!appSettings.useGlobalSettings && appSettings.fps > 0) {
            fps = appSettings.fps;
        }
        if (fps > 0) {
            config.fps = fps;
        }

        // Frame pacing
        String framePacing = null;
        if (quickLaunchSettings != null && quickLaunchSettings.framePacing != null) {
            framePacing = quickLaunchSettings.framePacing;
        } else if (!appSettings.useGlobalSettings && appSettings.framePacing != null) {
            framePacing = appSettings.framePacing;
        }
        if (framePacing != null) {
            config.framePacing = getFramePacingValue(framePacing);
        }

        // Bitrate
        int bitrate = 0;
        if (quickLaunchSettings != null && quickLaunchSettings.bitrate > 0) {
            bitrate = quickLaunchSettings.bitrate;
        } else if (!appSettings.useGlobalSettings && appSettings.bitrate > 0) {
            bitrate = appSettings.bitrate;
        }
        if (bitrate > 0) {
            config.bitrate = bitrate;
        }

        // Display refresh rate
        double actualDisplayRefreshRate = 0;
        if (quickLaunchSettings != null && quickLaunchSettings.actualDisplayRefreshRate > 0) {
            actualDisplayRefreshRate = quickLaunchSettings.actualDisplayRefreshRate;
        } else if (!appSettings.useGlobalSettings && appSettings.actualDisplayRefreshRate > 0) {
            actualDisplayRefreshRate = appSettings.actualDisplayRefreshRate;
        }
        if (actualDisplayRefreshRate > 0) {
            config.actualDisplayRefreshRate = String.valueOf(actualDisplayRefreshRate);
        }

        // HDR
        String enableHdrValue = null;
        if (quickLaunchSettings != null && quickLaunchSettings.enableHdr != null) {
            enableHdrValue = quickLaunchSettings.enableHdr;
        } else if (!appSettings.useGlobalSettings && appSettings.enableHdr != null) {
            enableHdrValue = appSettings.enableHdr;
        }
        if (enableHdrValue != null) {
            config.enableHdr = Boolean.parseBoolean(enableHdrValue);
        }

        // Performance overlay
        String enablePerfOverlayValue = null;
        if (quickLaunchSettings != null && quickLaunchSettings.enablePerfOverlay != null) {
            enablePerfOverlayValue = quickLaunchSettings.enablePerfOverlay;
        } else if (!appSettings.useGlobalSettings && appSettings.enablePerfOverlay != null) {
            enablePerfOverlayValue = appSettings.enablePerfOverlay;
        }
        if (enablePerfOverlayValue != null) {
            config.enablePerfOverlay = Boolean.parseBoolean(enablePerfOverlayValue);
        }

        // The layers the stream menu's audio haptics rows write (saveEffectiveAudioHaptics).
        String modeOwner = audioHapticsOwner(appKey, appSettings, appKey2, quickLaunchSettings,
                HAPTICS_MODE);
        if (modeOwner != null) {
            config.audioHaptics = (modeOwner.equals(appKey2) ? quickLaunchSettings
                    : appSettings).audioHaptics;
        }
        String strengthOwner = audioHapticsOwner(appKey, appSettings, appKey2,
                quickLaunchSettings, HAPTICS_STRENGTH);
        if (strengthOwner != null) {
            config.audioHapticsStrength = (strengthOwner.equals(appKey2) ? quickLaunchSettings
                    : appSettings).audioHapticsStrength;
        }

        return config;
    }
}
