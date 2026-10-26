package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.console.NetworkLinkAdvice.Link;

import org.junit.Test;

public class NetworkLinkAdviceTest {
    @Test
    public void classifiesBandByFrequency() {
        assertEquals(Link.WIRED, NetworkLinkAdvice.classify(true, 2437));
        assertEquals(Link.WIFI_24, NetworkLinkAdvice.classify(false, 2437));
        assertEquals(Link.WIFI_FAST, NetworkLinkAdvice.classify(false, 5180));
        assertEquals(Link.WIFI_UNKNOWN, NetworkLinkAdvice.classify(false, -1));
        assertEquals("2,4", NetworkLinkAdvice.bandGhz(2437));
        assertEquals("5", NetworkLinkAdvice.bandGhz(5180));
        assertEquals("6", NetworkLinkAdvice.bandGhz(6115));
        assertNull(NetworkLinkAdvice.bandGhz(-1));
    }

    @Test
    public void recommendsFasterLinkOn24GhzOrWeakWifiOnly() {
        assertTrue(NetworkLinkAdvice.recommendFasterLink(Link.WIFI_24, 200_000, -1));
        assertTrue(NetworkLinkAdvice.recommendFasterLink(Link.WIFI_FAST, 20_000, -1));
        assertTrue(NetworkLinkAdvice.recommendFasterLink(Link.WIFI_UNKNOWN, 20_000, -1));
        assertFalse(NetworkLinkAdvice.recommendFasterLink(Link.WIFI_FAST, 200_000, -1));
        assertFalse(NetworkLinkAdvice.recommendFasterLink(Link.WIFI_FAST, -1, -1));
    }

    @Test
    public void cableIsFlaggedOnlyWhenPortIsSlowOrThroughputWeak() {
        assertTrue(NetworkLinkAdvice.recommendFasterLink(Link.WIRED, 90_000, 100));
        assertTrue(NetworkLinkAdvice.recommendFasterLink(Link.WIRED, -1, 10));
        assertTrue(NetworkLinkAdvice.recommendFasterLink(Link.WIRED, 20_000, 1000));
        assertTrue(NetworkLinkAdvice.recommendFasterLink(Link.WIRED, 20_000, -1));
        assertFalse(NetworkLinkAdvice.recommendFasterLink(Link.WIRED, 400_000, 1000));
        assertFalse(NetworkLinkAdvice.recommendFasterLink(Link.WIRED, -1, -1));
        assertFalse(NetworkLinkAdvice.recommendFasterLink(Link.WIRED, -1, 1000));
    }
}
