package com.limelight.binding.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.R;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class UsbForwardingStatusTest {
    @Test public void everyHostReasonReadsAsItsOwnLine() {
        assertEquals(R.string.usb_forwarding_limit, UsbForwardingStatus.text(
                UsbForwardingController.STATE_REFUSED, "usb_limit_reached"));
        assertEquals(R.string.usb_forwarding_host_missing, UsbForwardingStatus.text(
                UsbForwardingController.STATE_REFUSED, "usbip_missing"));
        assertEquals(R.string.usb_forwarding_refused, UsbForwardingStatus.text(
                UsbForwardingController.STATE_REFUSED, "usb_something_new"));
        assertEquals(R.string.usb_forwarding_wait_other_tv, UsbForwardingStatus.text(
                UsbForwardingController.STATE_WAITING, "usb_host_busy"));
        assertEquals(R.string.usb_forwarding_off, UsbForwardingStatus.text(
                UsbForwardingController.STATE_OFF, ""));
        // A build without the native module, or a failed worker, says so instead of crashing.
        assertEquals(R.string.usb_forwarding_unsupported, UsbForwardingStatus.text(
                UsbForwardingController.STATE_REFUSED, "usb_unsupported"));
        assertEquals(R.string.usb_forwarding_failed, UsbForwardingStatus.text(
                UsbForwardingController.STATE_REFUSED, "usb_failed"));
        assertTrue(UsbForwardingStatus.isRunning(UsbForwardingController.STATE_WAITING));
        assertFalse(UsbForwardingStatus.isRunning(UsbForwardingController.STATE_REFUSED));
    }

    @Test public void theBarShowsTheMostRelevantDevice() {
        UsbForwardingController.Entry off = entry(UsbForwardingController.STATE_OFF);
        UsbForwardingController.Entry refused = entry(UsbForwardingController.STATE_REFUSED);
        UsbForwardingController.Entry waiting = entry(UsbForwardingController.STATE_WAITING);
        UsbForwardingController.Entry active = entry(UsbForwardingController.STATE_ACTIVE);
        assertEquals(UsbForwardingController.STATE_OFF,
                UsbForwardingController.summaryState(Collections.singletonList(off)));
        assertEquals(UsbForwardingController.STATE_REFUSED,
                UsbForwardingController.summaryState(Arrays.asList(off, refused)));
        assertEquals(UsbForwardingController.STATE_WAITING,
                UsbForwardingController.summaryState(Arrays.asList(waiting, refused)));
        assertEquals(UsbForwardingController.STATE_ACTIVE,
                UsbForwardingController.summaryState(Arrays.asList(refused, waiting, active)));
    }

    private static UsbForwardingController.Entry entry(String state) {
        return new UsbForwardingController.Entry("/dev/bus/usb/001/002", "Wheel", true, state, "");
    }
}
