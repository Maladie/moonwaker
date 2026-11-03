package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.Display;

import com.limelight.R;
import com.limelight.binding.audio.AudioHaptics;
import com.limelight.binding.input.GyroAim;
import com.limelight.nvstream.jni.MoonBridge;

public class PreferenceConfiguration {
    public enum FormatOption {
        AUTO,
        FORCE_AV1,
        FORCE_HEVC,
        FORCE_H264,
    };

    public enum AnalogStickForScrolling {
        NONE,
        RIGHT,
        LEFT
    }

    public static final String RESOLUTION_PREF_STRING = "list_resolution";
    public static final String FPS_PREF_STRING = "list_fps";
    public static final String BITRATE_PREF_STRING = "seekbar_bitrate_kbps";
    private static final String ENABLE_ULTRA_LOW_LATENCY_PREF_STRING = "checkbox_ultra_low_latency";
    private static final String STRETCH_PREF_STRING = "checkbox_stretch_video";
    private static final String SOPS_PREF_STRING = "checkbox_enable_sops";
    private static final String DISABLE_TOASTS_PREF_STRING = "checkbox_disable_warnings";
    private static final String HOST_AUDIO_PREF_STRING = "checkbox_host_audio";
    private static final String DEADZONE_PREF_STRING = "seekbar_deadzone";
    private static final String OSC_OPACITY_PREF_STRING = "seekbar_osc_opacity";
    private static final String LANGUAGE_PREF_STRING = "list_languages";
    private static final String MULTI_CONTROLLER_PREF_STRING = "checkbox_multi_controller";
    static final String AUDIO_CONFIG_PREF_STRING = "list_audio_config";
    private static final String USB_DRIVER_PREF_SRING = "checkbox_usb_driver";
    private static final String VIDEO_FORMAT_PREF_STRING = "video_format";
    private static final String ONSCREEN_CONTROLLER_PREF_STRING = "checkbox_show_onscreen_controls";
    private static final String ONLY_L3_R3_PREF_STRING = "checkbox_only_show_L3R3";
    private static final String SHOW_GUIDE_BUTTON_PREF_STRING = "checkbox_show_guide_button";
    private static final String ENABLE_HDR_PREF_STRING = "checkbox_enable_hdr";
    private static final String ENABLE_PIP_PREF_STRING = "checkbox_enable_pip";
    private static final String ENABLE_PERF_OVERLAY_STRING = "checkbox_enable_perf_overlay";
    private static final String BIND_ALL_USB_STRING = "checkbox_usb_bind_all";
    private static final String ANALOG_SCROLLING_PREF_STRING = "analog_scrolling";
    private static final String MOUSE_NAV_BUTTONS_STRING = "checkbox_mouse_nav_buttons";
    public static final String UNLOCK_FPS_STRING = "checkbox_unlock_fps";
    private static final String VIBRATE_OSC_PREF_STRING = "checkbox_vibrate_osc";
    private static final String VIBRATE_FALLBACK_PREF_STRING = "checkbox_vibrate_fallback";
    private static final String VIBRATE_FALLBACK_STRENGTH_PREF_STRING = "seekbar_vibrate_fallback_strength";
    public static final String AUDIO_HAPTICS_PREF_STRING = "list_audio_haptics";
    public static final String AUDIO_HAPTICS_STRENGTH_PREF_STRING = "seekbar_audio_haptics_strength";
    private static final String FLIP_FACE_BUTTONS_PREF_STRING = "checkbox_flip_face_buttons";
    private static final String TOUCHSCREEN_TRACKPAD_PREF_STRING = "checkbox_touchscreen_trackpad";
    private static final String LATENCY_TOAST_PREF_STRING = "checkbox_enable_post_stream_toast";
    private static final String FRAME_PACING_PREF_STRING = "frame_pacing";
    private static final String ABSOLUTE_MOUSE_MODE_PREF_STRING = "checkbox_absolute_mouse_mode";
    private static final String ENABLE_AUDIO_FX_PREF_STRING = "checkbox_enable_audiofx";
    private static final String REDUCE_REFRESH_RATE_PREF_STRING = "checkbox_reduce_refresh_rate";
    private static final String ACTUAL_DISPLAY_REFRESH_RATE_PREF_STRING = "text_actual_display_refresh_rate";
    private static final String FULL_RANGE_PREF_STRING = "checkbox_full_range";
    private static final String GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING = "checkbox_gamepad_touchpad_as_mouse";
    private static final String GAMEPAD_MOTION_SENSORS_PREF_STRING = "checkbox_gamepad_motion_sensors";
    private static final String GAMEPAD_MOTION_FALLBACK_PREF_STRING = "checkbox_gamepad_motion_fallback";
    private static final String GAMEPAD_BATTERY_WARNING_PREF_STRING = "list_gamepad_battery_warning";
    private static final String OVERLAY_TRIGGER_BUTTON_PREF_STRING = "overlay_trigger_button";
    private static final String OVERLAY_HOLD_DURATION_PREF_STRING = "overlay_hold_duration";
    /** PlayStation Create: "menu" (tap the stream menu, hold a screenshot) or "game" (Select). */
    public static final String CREATE_BUTTON_PREF_STRING = "create_button";
    public static final String DEFAULT_CREATE_BUTTON = "menu";
    private static final String DISCORD_MUTE_SHORTCUT_PREF_STRING = "discord_mute_shortcut";
    private static final String DISCORD_LEAVE_SHORTCUT_PREF_STRING = "discord_leave_shortcut";
    private static final String AUTO_RESUME_STREAM_PREF_STRING = "checkbox_auto_resume_stream";
    public static final String EMERGENCY_MODE_PREF_STRING = "checkbox_emergency_mode";

    public static final String DEFAULT_RESOLUTION = "1280x720";
    public static final String DEFAULT_FPS = "60";
    private static final boolean DEFAULT_ENABLE_ULTRA_LOW_LATENCY = false;
    private static final boolean DEFAULT_STRETCH = false;
    private static final boolean DEFAULT_SOPS = true;
    private static final boolean DEFAULT_DISABLE_TOASTS = false;
    private static final boolean DEFAULT_HOST_AUDIO = false;
    private static final int DEFAULT_DEADZONE = 7;
    private static final int DEFAULT_OPACITY = 90;
    public static final String DEFAULT_LANGUAGE = "default";
    private static final boolean DEFAULT_MULTI_CONTROLLER = true;
    private static final boolean DEFAULT_USB_DRIVER = true;
    private static final String DEFAULT_VIDEO_FORMAT = "auto";

    private static final boolean ONSCREEN_CONTROLLER_DEFAULT = false;
    private static final boolean ONLY_L3_R3_DEFAULT = false;
    private static final boolean SHOW_GUIDE_BUTTON_DEFAULT = true;
    private static final boolean DEFAULT_ENABLE_HDR = false;
    private static final boolean DEFAULT_ENABLE_PIP = false;
    private static final boolean DEFAULT_ENABLE_PERF_OVERLAY = false;
    private static final boolean DEFAULT_BIND_ALL_USB = false;
    private static final String DEFAULT_ANALOG_STICK_FOR_SCROLLING = "right";
    private static final boolean DEFAULT_MOUSE_NAV_BUTTONS = false;
    private static final boolean DEFAULT_VIBRATE_OSC = true;
    private static final boolean DEFAULT_VIBRATE_FALLBACK = false;
    private static final int DEFAULT_VIBRATE_FALLBACK_STRENGTH = 100;
    public static final int DEFAULT_AUDIO_HAPTICS_STRENGTH = 100;
    public static final int AUDIO_HAPTICS_STRENGTH_MIN = 10;
    public static final int AUDIO_HAPTICS_STRENGTH_MAX = 200;
    public static final int AUDIO_HAPTICS_STRENGTH_STEP = 10;
    private static final boolean DEFAULT_FLIP_FACE_BUTTONS = false;
    private static final boolean DEFAULT_TOUCHSCREEN_TRACKPAD = true;
    private static final String DEFAULT_AUDIO_CONFIG = "2"; // Stereo
    private static final boolean DEFAULT_LATENCY_TOAST = false;
    private static final String DEFAULT_FRAME_PACING = "balanced";
    private static final boolean DEFAULT_ABSOLUTE_MOUSE_MODE = false;
    private static final boolean DEFAULT_ENABLE_AUDIO_FX = false;
    private static final boolean DEFAULT_REDUCE_REFRESH_RATE = false;
    private static final String DEFAULT_ACTUAL_DISPLAY_REFRESH_RATE = "";
    private static final boolean DEFAULT_FULL_RANGE = false;
    private static final boolean DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE = false;
    private static final boolean DEFAULT_GAMEPAD_MOTION_SENSORS = true;
    private static final boolean DEFAULT_GAMEPAD_MOTION_FALLBACK = false;
    private static final String DEFAULT_GAMEPAD_BATTERY_WARNING = "20";
    private static final String DEFAULT_OVERLAY_TRIGGER_BUTTON = "select";
    private static final String DEFAULT_OVERLAY_HOLD_DURATION = "1500";
    private static final String DEFAULT_DISCORD_MUTE_SHORTCUT = "x";
    private static final String DEFAULT_DISCORD_LEAVE_SHORTCUT = "y";
    private static final boolean DEFAULT_AUTO_RESUME_STREAM = false;

    public static final int FRAME_PACING_MIN_LATENCY = 0;
    public static final int FRAME_PACING_BALANCED = 1;
    public static final int FRAME_PACING_CAP_FPS = 2;
    public static final int FRAME_PACING_MAX_SMOOTHNESS = 3;

    public static final String RES_720P = "1280x720";
    public static final String RES_1080P = "1920x1080";
    public static final String RES_1440P = "2560x1440";
    public static final String RES_4K = "3840x2160";

    public int width, height, fps;
    public int bitrate;
    public boolean enableUltraLowLatency;
    public FormatOption videoFormat;
    public int deadzonePercentage;
    public int oscOpacity;
    public boolean stretchVideo, enableSops, playHostAudio, disableWarnings;
    public String language;
    public boolean multiController, usbDriver, flipFaceButtons;
    public boolean onscreenController;
    public boolean onlyL3R3;
    public boolean showGuideButton;
    public boolean enableHdr;
    public boolean enablePip;
    public boolean enablePerfOverlay;
    public boolean enableLatencyToast;
    public boolean bindAllUsb;
    public AnalogStickForScrolling analogStickForScrolling;
    public boolean mouseNavButtons;
    public boolean vibrateOsc;
    public boolean vibrateFallbackToDevice;
    public int vibrateFallbackToDeviceStrength;
    /** {@link com.limelight.binding.audio.AudioHaptics} mode: off, game or music. */
    public String audioHaptics;
    public int audioHapticsStrength;
    public boolean touchscreenTrackpad;
    public MoonBridge.AudioConfiguration audioConfiguration;
    public int framePacing;
    public boolean absoluteMouseMode;
    public boolean enableAudioFx;
    public boolean reduceRefreshRate;
    public String actualDisplayRefreshRate;
    public boolean fullRange;
    public boolean gamepadMotionSensors;
    public boolean gamepadTouchpadAsMouse;
    public boolean gamepadMotionSensorsFallbackToDevice;
    public GyroAim.Settings gyroAim = GyroAim.Settings.DEFAULT;
    public int gamepadBatteryWarningThreshold;
    public String overlayTriggerButton;
    public int overlayHoldDurationMs;
    /** PlayStation Create is the stream's own (menu and screenshots) rather than the game's. */
    public boolean createButtonMenu;
    public String discordMuteShortcut;
    public String discordLeaveShortcut;
    public boolean autoResumeStream;
    /** A poor link offers the emergency mode (a notice and a menu card). */
    public boolean emergencyMode;

    public static boolean isNativeResolution(int width, int height) {
        // It's not a native resolution if it matches an existing resolution option
        if (width == 640 && height == 360) {
            return false;
        }
        else if (width == 854 && height == 480) {
            return false;
        }
        else if (width == 1280 && height == 720) {
            return false;
        }
        else if (width == 1920 && height == 1080) {
            return false;
        }
        else if (width == 2560 && height == 1440) {
            return false;
        }
        else if (width == 3840 && height == 2160) {
            return false;
        }

        return true;
    }

    // If we have a screen that has semi-square dimensions, we may want to change our behavior
    // to allow any orientation and vertical+horizontal resolutions.
    public static boolean isSquarishScreen(int width, int height) {
        float longDim = Math.max(width, height);
        float shortDim = Math.min(width, height);

        // We just put the arbitrary cutoff for a square-ish screen at 1.3
        return longDim / shortDim < 1.3f;
    }

    public static boolean isSquarishScreen(Display display) {
        int width, height;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            width = display.getMode().getPhysicalWidth();
            height = display.getMode().getPhysicalHeight();
        }
        else {
            width = display.getWidth();
            height = display.getHeight();
        }

        return isSquarishScreen(width, height);
    }

    private static int getWidthFromResolutionString(String resString) {
        return Integer.parseInt(resString.split("x")[0]);
    }

    private static int getHeightFromResolutionString(String resString) {
        return Integer.parseInt(resString.split("x")[1]);
    }

    public static int getDefaultBitrate(String resString, String fpsString) {
        int width = getWidthFromResolutionString(resString);
        int height = getHeightFromResolutionString(resString);
        int fps = Integer.parseInt(fpsString);

        // This logic is shamelessly stolen from Moonlight Qt:
        // https://github.com/moonlight-stream/moonlight-qt/blob/master/app/settings/streamingpreferences.cpp

        // Don't scale bitrate linearly beyond 60 FPS. It's definitely not a linear
        // bitrate increase for frame rate once we get to values that high.
        double frameRateFactor = (fps <= 60 ? fps : (Math.sqrt(fps / 60.f) * 60.f)) / 30.f;

        // TODO: Collect some empirical data to see if these defaults make sense.
        // We're just using the values that the Shield used, as we have for years.
        int[] pixelVals = {
            640 * 360,
            854 * 480,
            1280 * 720,
            1920 * 1080,
            2560 * 1440,
            3840 * 2160,
            -1,
        };
        int[] factorVals = {
            1,
            2,
            5,
            10,
            20,
            40,
            -1
        };

        // Calculate the resolution factor by linear interpolation of the resolution table
        float resolutionFactor;
        int pixels = width * height;
        for (int i = 0; ; i++) {
            if (pixels == pixelVals[i]) {
                // We can bail immediately for exact matches
                resolutionFactor = factorVals[i];
                break;
            }
            else if (pixels < pixelVals[i]) {
                if (i == 0) {
                    // Never go below the lowest resolution entry
                    resolutionFactor = factorVals[i];
                }
                else {
                    // Interpolate between the entry greater than the chosen resolution (i) and the entry less than the chosen resolution (i-1)
                    resolutionFactor = ((float)(pixels - pixelVals[i-1]) / (pixelVals[i] - pixelVals[i-1])) * (factorVals[i] - factorVals[i-1]) + factorVals[i-1];
                }
                break;
            }
            else if (pixelVals[i] == -1) {
                // Never go above the highest resolution entry
                resolutionFactor = factorVals[i-1];
                break;
            }
        }

        return (int)Math.round(resolutionFactor * frameRateFactor) * 1000;
    }

    public static int getDefaultBitrate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return getDefaultBitrate(
                prefs.getString(RESOLUTION_PREF_STRING, DEFAULT_RESOLUTION),
                prefs.getString(FPS_PREF_STRING, DEFAULT_FPS));
    }

    public static void applyStreamSettings(Context context, int width, int height, int fps,
                                           int bitrateKbps) {
        validateStreamSettings(width, height, fps, bitrateKbps);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(RESOLUTION_PREF_STRING, width + "x" + height)
                .putString(FPS_PREF_STRING, Integer.toString(fps))
                .putInt(BITRATE_PREF_STRING, bitrateKbps)
                .apply();
    }

    static void validateStreamSettings(int width, int height, int fps, int bitrateKbps) {
        if (width <= 0 || height <= 0 || fps <= 0 || bitrateKbps < 500) {
            throw new IllegalArgumentException("Invalid stream settings");
        }
    }

    private static FormatOption getVideoFormatValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(VIDEO_FORMAT_PREF_STRING, DEFAULT_VIDEO_FORMAT);
        if (str.equals("auto")) {
            return FormatOption.AUTO;
        }
        else if (str.equals("forceav1")) {
            return FormatOption.FORCE_AV1;
        }
        else if (str.equals("forceh265")) {
            return FormatOption.FORCE_HEVC;
        }
        else if (str.equals("neverh265")) {
            return FormatOption.FORCE_H264;
        }
        else {
            // Should never get here
            return FormatOption.AUTO;
        }
    }

    private static int getFramePacingValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(FRAME_PACING_PREF_STRING, DEFAULT_FRAME_PACING);
        if (str.equals("latency")) {
            return FRAME_PACING_MIN_LATENCY;
        }
        else if (str.equals("balanced")) {
            return FRAME_PACING_BALANCED;
        }
        else if (str.equals("cap-fps")) {
            return FRAME_PACING_CAP_FPS;
        }
        else if (str.equals("smoothness")) {
            return FRAME_PACING_MAX_SMOOTHNESS;
        }
        else {
            // Should never get here
            return FRAME_PACING_MIN_LATENCY;
        }
    }

    private static AnalogStickForScrolling getAnalogStickForScrollingValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String str = prefs.getString(ANALOG_SCROLLING_PREF_STRING, DEFAULT_ANALOG_STICK_FOR_SCROLLING);
        if (str.equals("right")) {
            return AnalogStickForScrolling.RIGHT;
        }
        else if (str.equals("left")) {
            return AnalogStickForScrolling.LEFT;
        }
        else {
            return AnalogStickForScrolling.NONE;
        }
    }

    public static void saveLanguage(Context context, String language) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(LANGUAGE_PREF_STRING, language).apply();
    }

    public static boolean isShieldAtvFirmwareWithBrokenHdr() {
        // This particular Shield TV firmware crashes when using HDR
        // https://www.nvidia.com/en-us/geforce/forums/notifications/comment/155192/
        return Build.MANUFACTURER.equalsIgnoreCase("NVIDIA") &&
                Build.FINGERPRINT.contains("PPR1.180610.011/4079208_2235.1395");
    }

    /** The stream menu's hold button: "select", "start" or "lb_rb". */
    public static String overlayTriggerButton(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(OVERLAY_TRIGGER_BUTTON_PREF_STRING, DEFAULT_OVERLAY_TRIGGER_BUTTON);
    }

    /** How the stream menu opens with the saved settings, for screens that name it. */
    public static String menuGesture(Context context) {
        return menuGesture(context, DEFAULT_CREATE_BUTTON.equals(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(CREATE_BUTTON_PREF_STRING, DEFAULT_CREATE_BUTTON)),
                overlayTriggerButton(context));
    }

    /**
     * How the stream menu opens, lower case for mid-sentence: "hold Start", or with PlayStation
     * Create as the menu "press Create or hold Select" (Select of every other pad).
     */
    public static String menuGesture(Context context, boolean createButtonMenu, String trigger) {
        int button = "start".equals(trigger) ? R.string.overlay_trigger_start
                : "lb_rb".equals(trigger) ? R.string.overlay_trigger_lb_rb
                : R.string.overlay_trigger_select;
        String hold = context.getString(R.string.overlay_menu_gesture_hold,
                context.getString(button));
        return createButtonMenu ? context.getString(R.string.overlay_menu_gesture_create, hold)
                : hold;
    }

    /**
     * The controller settings a running stream applies live, taken from {@code current}
     * (read now); connection-time settings stay as the stream started.
     */
    public void takeControllerSettings(PreferenceConfiguration current) {
        gyroAim = current.gyroAim;
        gamepadMotionSensors = current.gamepadMotionSensors;
        gamepadMotionSensorsFallbackToDevice = current.gamepadMotionSensorsFallbackToDevice;
        deadzonePercentage = current.deadzonePercentage;
        overlayTriggerButton = current.overlayTriggerButton;
        overlayHoldDurationMs = current.overlayHoldDurationMs;
        createButtonMenu = current.createButtonMenu;
        flipFaceButtons = current.flipFaceButtons;
        gamepadTouchpadAsMouse = current.gamepadTouchpadAsMouse;
        analogStickForScrolling = current.analogStickForScrolling;
        vibrateFallbackToDevice = current.vibrateFallbackToDevice;
        vibrateFallbackToDeviceStrength = current.vibrateFallbackToDeviceStrength;
        gamepadBatteryWarningThreshold = current.gamepadBatteryWarningThreshold;
    }

    public static PreferenceConfiguration readPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceConfiguration config = new PreferenceConfiguration();

        String resStr = prefs.getString(RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
        config.width = PreferenceConfiguration.getWidthFromResolutionString(resStr);
        config.height = PreferenceConfiguration.getHeightFromResolutionString(resStr);
        config.fps = Integer.parseInt(prefs.getString(FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS));

        if (!prefs.contains(GAMEPAD_MOTION_SENSORS_PREF_STRING) && Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            // Android 12 has a nasty bug that causes crashes when the app touches the InputDevice's
            // associated InputDeviceSensorManager (just calling getSensorManager() is enough).
            // As a workaround, we will override the default value for the gamepad motion sensor
            // option to disabled on Android 12 to reduce the impact of this bug.
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/8970010a5e9f3dc5c069f56b4147552accfcbbeb
            prefs.edit().putBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, false).apply();
        }

        // Set with the resolution and FPS (their recommended value); a game's own comes from
        // the stream menu through its app settings.
        config.bitrate = prefs.getInt(BITRATE_PREF_STRING, getDefaultBitrate(context));

        String audioConfig = prefs.getString(AUDIO_CONFIG_PREF_STRING, DEFAULT_AUDIO_CONFIG);
        if (audioConfig.equals("71")) {
            config.audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_71_SURROUND;
        }
        else if (audioConfig.equals("51")) {
            config.audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_51_SURROUND;
        }
        else /* if (audioConfig.equals("2")) */ {
            config.audioConfiguration = MoonBridge.AUDIO_CONFIGURATION_STEREO;
        }

        config.videoFormat = getVideoFormatValue(context);
        config.framePacing = getFramePacingValue(context);

        config.analogStickForScrolling = getAnalogStickForScrollingValue(context);

        config.deadzonePercentage = prefs.getInt(DEADZONE_PREF_STRING, DEFAULT_DEADZONE);

        config.oscOpacity = prefs.getInt(OSC_OPACITY_PREF_STRING, DEFAULT_OPACITY);

        config.language = prefs.getString(LANGUAGE_PREF_STRING, DEFAULT_LANGUAGE);

        // Checkbox preferences
        config.disableWarnings = prefs.getBoolean(DISABLE_TOASTS_PREF_STRING, DEFAULT_DISABLE_TOASTS);
        config.enableUltraLowLatency = prefs.getBoolean(ENABLE_ULTRA_LOW_LATENCY_PREF_STRING, DEFAULT_ENABLE_ULTRA_LOW_LATENCY);
        config.enableSops = prefs.getBoolean(SOPS_PREF_STRING, DEFAULT_SOPS);
        config.stretchVideo = prefs.getBoolean(STRETCH_PREF_STRING, DEFAULT_STRETCH);
        config.playHostAudio = prefs.getBoolean(HOST_AUDIO_PREF_STRING, DEFAULT_HOST_AUDIO);
        config.multiController = prefs.getBoolean(MULTI_CONTROLLER_PREF_STRING, DEFAULT_MULTI_CONTROLLER);
        config.usbDriver = prefs.getBoolean(USB_DRIVER_PREF_SRING, DEFAULT_USB_DRIVER);
        config.onscreenController = prefs.getBoolean(ONSCREEN_CONTROLLER_PREF_STRING, ONSCREEN_CONTROLLER_DEFAULT);
        config.onlyL3R3 = prefs.getBoolean(ONLY_L3_R3_PREF_STRING, ONLY_L3_R3_DEFAULT);
        config.showGuideButton = prefs.getBoolean(SHOW_GUIDE_BUTTON_PREF_STRING, SHOW_GUIDE_BUTTON_DEFAULT);
        config.enableHdr = prefs.getBoolean(ENABLE_HDR_PREF_STRING, DEFAULT_ENABLE_HDR) && !isShieldAtvFirmwareWithBrokenHdr();
        config.enablePip = prefs.getBoolean(ENABLE_PIP_PREF_STRING, DEFAULT_ENABLE_PIP);
        config.enablePerfOverlay = prefs.getBoolean(ENABLE_PERF_OVERLAY_STRING, DEFAULT_ENABLE_PERF_OVERLAY);
        config.bindAllUsb = prefs.getBoolean(BIND_ALL_USB_STRING, DEFAULT_BIND_ALL_USB);
        config.mouseNavButtons = prefs.getBoolean(MOUSE_NAV_BUTTONS_STRING, DEFAULT_MOUSE_NAV_BUTTONS);
        config.vibrateOsc = prefs.getBoolean(VIBRATE_OSC_PREF_STRING, DEFAULT_VIBRATE_OSC);
        config.vibrateFallbackToDevice = prefs.getBoolean(VIBRATE_FALLBACK_PREF_STRING, DEFAULT_VIBRATE_FALLBACK);
        config.vibrateFallbackToDeviceStrength = prefs.getInt(VIBRATE_FALLBACK_STRENGTH_PREF_STRING, DEFAULT_VIBRATE_FALLBACK_STRENGTH);
        config.audioHaptics = prefs.getString(AUDIO_HAPTICS_PREF_STRING, AudioHaptics.MODE_OFF);
        config.audioHapticsStrength = prefs.getInt(AUDIO_HAPTICS_STRENGTH_PREF_STRING, DEFAULT_AUDIO_HAPTICS_STRENGTH);
        config.flipFaceButtons = prefs.getBoolean(FLIP_FACE_BUTTONS_PREF_STRING, DEFAULT_FLIP_FACE_BUTTONS);
        config.touchscreenTrackpad = prefs.getBoolean(TOUCHSCREEN_TRACKPAD_PREF_STRING, DEFAULT_TOUCHSCREEN_TRACKPAD);
        config.enableLatencyToast = prefs.getBoolean(LATENCY_TOAST_PREF_STRING, DEFAULT_LATENCY_TOAST);
        config.absoluteMouseMode = prefs.getBoolean(ABSOLUTE_MOUSE_MODE_PREF_STRING, DEFAULT_ABSOLUTE_MOUSE_MODE);
        config.enableAudioFx = prefs.getBoolean(ENABLE_AUDIO_FX_PREF_STRING, DEFAULT_ENABLE_AUDIO_FX);
        config.reduceRefreshRate = prefs.getBoolean(REDUCE_REFRESH_RATE_PREF_STRING, DEFAULT_REDUCE_REFRESH_RATE);
        config.actualDisplayRefreshRate = prefs.getString(ACTUAL_DISPLAY_REFRESH_RATE_PREF_STRING, DEFAULT_ACTUAL_DISPLAY_REFRESH_RATE);
        config.fullRange = prefs.getBoolean(FULL_RANGE_PREF_STRING, DEFAULT_FULL_RANGE);
        config.gamepadTouchpadAsMouse = prefs.getBoolean(GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING, DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE);
        config.gamepadMotionSensors = prefs.getBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, DEFAULT_GAMEPAD_MOTION_SENSORS);
        config.gamepadMotionSensorsFallbackToDevice = prefs.getBoolean(GAMEPAD_MOTION_FALLBACK_PREF_STRING, DEFAULT_GAMEPAD_MOTION_FALLBACK);
        config.gyroAim = AppPreferences.globalGyro(prefs);
        config.gamepadBatteryWarningThreshold = Integer.parseInt(prefs.getString(GAMEPAD_BATTERY_WARNING_PREF_STRING, DEFAULT_GAMEPAD_BATTERY_WARNING));
        config.overlayTriggerButton = overlayTriggerButton(context);
        config.overlayHoldDurationMs = Integer.parseInt(prefs.getString(OVERLAY_HOLD_DURATION_PREF_STRING, DEFAULT_OVERLAY_HOLD_DURATION));
        config.discordMuteShortcut = prefs.getString(DISCORD_MUTE_SHORTCUT_PREF_STRING,
                DEFAULT_DISCORD_MUTE_SHORTCUT);
        config.discordLeaveShortcut = prefs.getString(DISCORD_LEAVE_SHORTCUT_PREF_STRING,
                DEFAULT_DISCORD_LEAVE_SHORTCUT);
        config.createButtonMenu = DEFAULT_CREATE_BUTTON.equals(
                prefs.getString(CREATE_BUTTON_PREF_STRING, DEFAULT_CREATE_BUTTON));
        config.autoResumeStream = prefs.getBoolean(AUTO_RESUME_STREAM_PREF_STRING, DEFAULT_AUTO_RESUME_STREAM);
        config.emergencyMode = prefs.getBoolean(EMERGENCY_MODE_PREF_STRING, true);

        return config;
    }
}
