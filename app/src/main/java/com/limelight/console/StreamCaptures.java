package com.limelight.console;

import android.content.SharedPreferences;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;
import com.limelight.gateway.GatewayTransport;

import java.io.IOException;
import java.util.Locale;

/**
 * Screenshots and the last 30 s during a stream (#51, Host Plus): the profile's buffer
 * setting, the host calls the stream makes off the UI thread and the words for each answer.
 * The host captures and enforces the licence; the TV only asks.
 */
public final class StreamCaptures {
    private static final String BUFFER_PREFIX = "captures_buffer/";

    private final HostGatewayClient client = new HostGatewayClient();
    private final GatewayConnection connection;

    public StreamCaptures(GatewayConnection connection) {
        this.connection = connection;
    }

    /** "Record the last 30 s" of this host's profile; on unless the profile turned it off. */
    public static boolean bufferEnabled(SharedPreferences preferences, String hostId,
                                        String profileId) {
        return preferences.getBoolean(bufferKey(hostId, profileId), true);
    }

    static void setBufferEnabled(SharedPreferences preferences, String hostId, String profileId,
                                 boolean enabled) {
        preferences.edit().putBoolean(bufferKey(hostId, profileId), enabled).apply();
    }

    /** Blocking. The profile's setting for this stream; the host's refusal, "" when taken. */
    public String setBuffer(boolean enabled) {
        try {
            client.setCaptureBuffer(connection, enabled);
            return "";
        } catch (IOException | RuntimeException error) {
            return reason(error);
        }
    }

    /** Blocking. A screenshot or ({@code clip}) the last 30 s: "" saved, else the reason. */
    public String take(boolean clip) {
        try {
            client.takeCapture(connection, clip);
            return "";
        } catch (IOException | RuntimeException error) {
            return reason(error);
        }
    }

    /** What the stream toast says about {@link #take}'s answer. */
    public static int message(String reason, boolean clip) {
        switch (reason) {
            case "":
                return clip ? R.string.overlay_menu_clip_saved
                        : R.string.overlay_menu_screenshot_saved;
            case GatewayTransport.LICENSE_REQUIRED:
                return R.string.host_plus_required;
            case "buffer_off":
                return R.string.overlay_menu_capture_buffer_off;
            case "buffer_starting":
                return R.string.overlay_menu_capture_buffer_starting;
            case "buffer_empty":
                return R.string.overlay_menu_capture_buffer_empty;
            case "screen_unavailable":
                return R.string.overlay_menu_capture_screen_locked;
            case "capture_unavailable":
                return R.string.overlay_menu_capture_unavailable;
            case "child_session_required":
                return R.string.overlay_menu_capture_child_session;
            case "capture_drive_unavailable":
                return R.string.console_captures_drive_gone;
            default:
                return R.string.overlay_menu_capture_failed;
        }
    }

    /** The host's refusal reason; a lost or garbled answer is "failed". */
    static String reason(Exception error) {
        String message = error instanceof HostGatewayClient.GatewayException
                ? error.getMessage() : null;
        return message != null && message.matches("[a-z_]{1,32}") ? message : "failed";
    }

    private static String bufferKey(String hostId, String profileId) {
        return BUFFER_PREFIX + (hostId == null ? "" : hostId.trim().toLowerCase(Locale.ROOT))
                + "/" + GatewayConnection.normalizeProfileId(profileId);
    }
}
