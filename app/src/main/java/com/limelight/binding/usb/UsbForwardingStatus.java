package com.limelight.binding.usb;

import com.limelight.R;
import com.limelight.gateway.GatewayTransport;

/** One status line per forwarding state and host reason, for the console panel and the menu. */
public final class UsbForwardingStatus {
    private UsbForwardingStatus() { }

    /** Whether choosing the device stops it (it is shared or on its way) rather than shares it. */
    public static boolean isRunning(String state) {
        return UsbForwardingController.STATE_ACTIVE.equals(state)
                || UsbForwardingController.STATE_CONNECTING.equals(state)
                || UsbForwardingController.STATE_PERMISSION.equals(state)
                || UsbForwardingController.STATE_WAITING.equals(state)
                || UsbForwardingController.STATE_RECONNECTING.equals(state);
    }

    /** Unknown reasons read as a plain refusal. */
    public static int text(String state, String reason) {
        if (UsbForwardingController.STATE_ACTIVE.equals(state)) return R.string.usb_forwarding_active;
        if (UsbForwardingController.STATE_CONNECTING.equals(state)) return R.string.usb_forwarding_connecting;
        if (UsbForwardingController.STATE_PERMISSION.equals(state)) return R.string.usb_forwarding_permission;
        if (UsbForwardingController.STATE_RECONNECTING.equals(state)) return R.string.usb_forwarding_reconnecting;
        if (UsbForwardingController.STATE_WAITING.equals(state)) {
            switch (reason) {
                case "usb_host_busy": return R.string.usb_forwarding_wait_other_tv;
                case "usb_session_taken_over": return R.string.usb_forwarding_wait_taken_over;
                default: return R.string.usb_forwarding_wait_release;
            }
        }
        if (UsbForwardingController.STATE_REFUSED.equals(state)) {
            switch (reason) {
                case "usbip_missing": return R.string.usb_forwarding_host_missing;
                case "usb_limit_reached": return R.string.usb_forwarding_limit;
                case "usb_permission_denied":
                case "usb_permission_blocked": return R.string.usb_forwarding_permission_denied;
                case "usb_claim_failed": return R.string.usb_forwarding_claim_failed;
                case "usb_client_revoked":
                case "usb_profile_unavailable": return R.string.usb_forwarding_profile_revoked;
                case "usb_unsupported": return R.string.usb_forwarding_unsupported;
                case "usb_failed": return R.string.usb_forwarding_failed;
                case GatewayTransport.LICENSE_REQUIRED: return R.string.usb_forwarding_host_plus;
                default: return R.string.usb_forwarding_refused;
            }
        }
        return R.string.usb_forwarding_off;
    }
}
