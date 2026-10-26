package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Controller settings changed mid-stream: sensors, the re-announce and the derived deadzone. */
public class ControllerHandlerMotionSettingsTest {
    @Test public void playerOneFallsBackToTheDeviceOnlyWithoutPadSensors() {
        assertEquals("pad", ControllerHandler.motionSensors("pad", "device", true, true));
        assertEquals("device", ControllerHandler.motionSensors(null, "device", true, true));
        assertNull(ControllerHandler.motionSensors(null, "device", true, false));
        assertNull(ControllerHandler.motionSensors(null, "device", false, true));
    }

    @Test public void onlyNewlyGainedHostMotionOfAnUnannouncedPadNeedsAReannounce() {
        assertTrue(ControllerHandler.hostMissesMotion(null, "device", false, true));
        // Already announced: the host's earlier request re-registers the sensors.
        assertFalse(ControllerHandler.hostMissesMotion(null, "device", true, true));
        // Gyro aim reads the sensors on the client; the host gets no motion anyway.
        assertFalse(ControllerHandler.hostMissesMotion(null, "device", false, false));
        // Unchanged or dropped sensors ask for nothing.
        assertFalse(ControllerHandler.hostMissesMotion("device", "device", false, true));
        assertFalse(ControllerHandler.hostMissesMotion("device", null, false, true));
    }

    @Test public void reannounceDropsOnlyThatPadFromTheMask() {
        assertEquals((short) 0b101, ControllerHandler.withoutController((short) 0b111, 1));
        assertEquals((short) 0, ControllerHandler.withoutController((short) 1, 0));
        assertEquals((short) 0x7FFF, ControllerHandler.withoutController((short) 0xFFFF, 15));
    }

    @Test public void stickDeadzoneIsAFractionOfAtLeastOnePercent() {
        assertEquals(0.07, ControllerHandler.stickDeadzone(7), 1e-9);
        assertEquals(0.01, ControllerHandler.stickDeadzone(0), 1e-9);
        assertEquals(0.01, ControllerHandler.stickDeadzone(-5), 1e-9);
    }
}
