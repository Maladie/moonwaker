package com.limelight.binding.usb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsbForwardingControllerTest {
    @Test public void busIdComesFromTheAndroidDeviceName() {
        assertArrayEquals(new int[]{1, 5}, UsbForwardingController.busAndDevice("/dev/bus/usb/001/005"));
        assertEquals("2-117", UsbForwardingController.busId("/dev/bus/usb/002/117"));
    }

    @Test public void refusalsAreSortedIntoFinalAndWaiting() {
        assertTrue(UsbForwardingController.isDeviceRefusal("usb_import_mismatch"));
        assertFalse(UsbForwardingController.isDeviceRefusal("usb_limit_reached"));
        // Another TV's devices wait for it to let go.
        assertTrue(UsbForwardingController.isWaitingReason("usb_host_busy"));
        assertTrue(UsbForwardingController.isWaitingReason("usb_session_taken_over"));
        assertFalse(UsbForwardingController.isWaitingReason("usbip_missing"));
        assertFalse(UsbForwardingController.isWaitingReason("host_unavailable"));
    }
}
