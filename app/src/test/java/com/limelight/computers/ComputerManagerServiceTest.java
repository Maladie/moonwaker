package com.limelight.computers;

import com.limelight.nvstream.http.ComputerDetails.State;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ComputerManagerServiceTest {
    @Test public void pollerLogsOnlyOnlineOfflineTransitions() {
        assertEquals("PC is offline", ComputerManagerService.stateChangeLog(
                "PC", State.UNKNOWN, State.OFFLINE));
        // Every further failed poll of a host that stays offline is silent.
        assertNull(ComputerManagerService.stateChangeLog("PC", State.OFFLINE, State.OFFLINE));
        assertEquals("PC is online", ComputerManagerService.stateChangeLog(
                "PC", State.OFFLINE, State.ONLINE));
        assertNull(ComputerManagerService.stateChangeLog("PC", State.ONLINE, State.ONLINE));
        // An expired poll result (UNKNOWN) is not a transition.
        assertNull(ComputerManagerService.stateChangeLog("PC", State.ONLINE, State.UNKNOWN));
        assertEquals("PC is offline", ComputerManagerService.stateChangeLog(
                "PC", State.ONLINE, State.OFFLINE));
    }
}
