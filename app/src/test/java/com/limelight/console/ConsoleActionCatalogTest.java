package com.limelight.console;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleActionCatalogTest {
    @Test public void rememberedPairingNeedsASavedCertificateAndNeverOverridesAPoll() {
        // Not polled yet: the saved certificate is the remembered pairing; none is unpaired.
        assertTrue(ConsoleActionCatalog.isRememberedPaired(null, true));
        assertFalse(ConsoleActionCatalog.isRememberedPaired(null, false));
        // A poll reported the pairing: only that counts, with or without a certificate.
        for (com.limelight.nvstream.http.PairingManager.PairState state
                : com.limelight.nvstream.http.PairingManager.PairState.values()) {
            boolean paired = state == com.limelight.nvstream.http.PairingManager.PairState.PAIRED;
            org.junit.Assert.assertEquals(paired,
                    ConsoleActionCatalog.isRememberedPaired(state, true));
            org.junit.Assert.assertEquals(paired,
                    ConsoleActionCatalog.isRememberedPaired(state, false));
        }
        assertFalse(ConsoleActionCatalog.isRememberedPaired(null));
    }

    @Test
    public void activeAppHasResumeAndQuitButNotLaunch() {
        assertTrue(app(ConsoleActionCatalog.AppCapability.RESUME,
                true, true, true, false, false));
        assertTrue(app(ConsoleActionCatalog.AppCapability.QUIT,
                true, true, true, false, false));
        assertFalse(app(ConsoleActionCatalog.AppCapability.LAUNCH,
                true, true, true, false, false));
        assertFalse(app(ConsoleActionCatalog.AppCapability.HIDE,
                true, true, true, false, false));
    }

    private static boolean app(ConsoleActionCatalog.AppCapability capability,
                               boolean online, boolean paired, boolean current,
                               boolean another, boolean hidden) {
        return ConsoleActionCatalog.appActionVisible(capability, online, paired,
                current, another, hidden);
    }
}
