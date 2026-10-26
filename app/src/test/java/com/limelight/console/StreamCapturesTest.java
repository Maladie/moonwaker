package com.limelight.console;

import static org.junit.Assert.assertEquals;

import com.limelight.R;

import org.junit.Test;

import java.io.IOException;

public class StreamCapturesTest {
    @Test public void eachHostAnswerHasItsOwnWords() {
        assertEquals(R.string.overlay_menu_screenshot_saved, StreamCaptures.message("", false));
        assertEquals(R.string.overlay_menu_clip_saved, StreamCaptures.message("", true));
        assertEquals(R.string.host_plus_required,
                StreamCaptures.message("license_required", true));
        assertEquals(R.string.overlay_menu_capture_buffer_off,
                StreamCaptures.message("buffer_off", true));
        assertEquals(R.string.overlay_menu_capture_buffer_starting,
                StreamCaptures.message("buffer_starting", true));
        assertEquals(R.string.overlay_menu_capture_buffer_empty,
                StreamCaptures.message("buffer_empty", true));
        assertEquals(R.string.overlay_menu_capture_screen_locked,
                StreamCaptures.message("screen_unavailable", false));
        assertEquals(R.string.overlay_menu_capture_unavailable,
                StreamCaptures.message("capture_unavailable", false));
        assertEquals(R.string.overlay_menu_capture_child_session,
                StreamCaptures.message("child_session_required", false));
        assertEquals(R.string.console_captures_drive_gone,
                StreamCaptures.message("capture_drive_unavailable", true));
        assertEquals(R.string.overlay_menu_capture_failed,
                StreamCaptures.message("failed", false));
    }

    @Test public void onlyAHostRefusalNamesAReason() {
        assertEquals("buffer_starting", StreamCaptures.reason(
                new HostGatewayClient.GatewayException("buffer_starting", 409)));
        assertEquals("failed", StreamCaptures.reason(
                new HostGatewayClient.GatewayException("The capture could not be saved.", 502)));
        assertEquals("failed", StreamCaptures.reason(new IOException("buffer_off")));
    }
}
