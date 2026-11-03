package com.limelight.console;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

/** Pure state rules used by every dashboard surface that exposes host/app actions. */
final class ConsoleActionCatalog {
    enum AppCapability {
        LAUNCH,
        RESUME,
        QUIT_AND_LAUNCH,
        QUIT,
        SETTINGS,
        HIDE,
        SHOW,
        DETAILS
    }

    private ConsoleActionCatalog() { }

    static boolean appActionVisible(AppCapability action, boolean online, boolean paired,
                                    boolean thisAppRunning, boolean anotherAppRunning,
                                    boolean hidden) {
        switch (action) {
            case LAUNCH:
                return online && paired && !thisAppRunning && !anotherAppRunning;
            case RESUME:
            case QUIT:
                return online && paired && thisAppRunning;
            case QUIT_AND_LAUNCH:
                return online && paired && anotherAppRunning;
            case SETTINGS:
            case DETAILS:
                return true;
            case HIDE:
                return !hidden && !thisAppRunning;
            case SHOW:
                return hidden;
            default:
                return false;
        }
    }

    static boolean isOnline(ComputerDetails host) {
        return host != null && host.state == ComputerDetails.State.ONLINE;
    }

    static boolean isPaired(ComputerDetails host) {
        return host != null && host.pairState == PairingManager.PairState.PAIRED;
    }

    /**
     * The one rule for a host the user may enter, play on, wake and warm up: its last poll
     * reported it paired, or no poll has reported its pairing yet (the pairing is not stored)
     * and its pairing certificate is saved. Whatever follows still goes through the pinned
     * certificate, and the first poll that answers replaces the remembered pairing.
     */
    static boolean isRememberedPaired(ComputerDetails host) {
        return host != null && isRememberedPaired(host.pairState, host.serverCert != null);
    }

    static boolean isRememberedPaired(PairingManager.PairState pairState,
                                      boolean savedCertificate) {
        return pairState == PairingManager.PairState.PAIRED
                || (pairState == null && savedCertificate);
    }
}
