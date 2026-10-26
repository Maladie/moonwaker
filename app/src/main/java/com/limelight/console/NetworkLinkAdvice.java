package com.limelight.console;

/** Classifies the TV's link for the first-run network step and decides whether to warn. */
final class NetworkLinkAdvice {
    enum Link { WIRED, WIFI_24, WIFI_FAST, WIFI_UNKNOWN }

    // Below this, a measured link gets the same recommendation as 2.4 GHz Wi-Fi.
    static final long WEAK_GOODPUT_KBPS = 50_000;
    // Many TVs ship a 100 Mb/s Ethernet port; at or below it Wi-Fi 5 GHz can beat the cable.
    static final long SLOW_ETHERNET_MBPS = 100;

    private NetworkLinkAdvice() {}

    static Link classify(boolean wired, int frequencyMhz) {
        if (wired) return Link.WIRED;
        if (frequencyMhz <= 0) return Link.WIFI_UNKNOWN;
        return frequencyMhz < 3000 ? Link.WIFI_24 : Link.WIFI_FAST;
    }

    /**
     * goodputKbps: measured value, or a negative number when unmeasured. ethernetMbps: port
     * speed, or a non-positive number when unknown (only used for a cable).
     */
    static boolean recommendFasterLink(Link link, long goodputKbps, long ethernetMbps) {
        boolean weak = goodputKbps >= 0 && goodputKbps < WEAK_GOODPUT_KBPS;
        if (link == Link.WIRED) {
            return weak || (ethernetMbps > 0 && ethernetMbps <= SLOW_ETHERNET_MBPS);
        }
        return link == Link.WIFI_24 || weak;
    }

    /** "2,4", "5" or "6"; null when the frequency is unknown. */
    static String bandGhz(int frequencyMhz) {
        if (frequencyMhz <= 0) return null;
        return frequencyMhz < 3000 ? "2,4" : frequencyMhz < 5925 ? "5" : "6";
    }
}
