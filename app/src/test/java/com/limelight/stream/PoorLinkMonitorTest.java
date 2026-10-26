package com.limelight.stream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PoorLinkMonitorTest {
    @Test public void observedRateCoversTheLastTenSeconds() {
        PoorLinkMonitor link = new PoorLinkMonitor(0);
        assertEquals(0, link.observedKbps());
        // 40 Mb/s for 20 s, then 20 Mb/s: only the last window counts.
        long bytes = 0;
        for (int second = 0; second <= 20; second++) {
            link.addRx(second * 1_000L, bytes);
            bytes += 5_000_000L;
        }
        bytes -= 2_500_000L;
        for (int second = 21; second <= 31; second++) {
            link.addRx(second * 1_000L, bytes);
            bytes += 2_500_000L;
        }
        assertEquals(20_000, link.observedKbps());
        link.addRx(32_000L, -1);
        assertEquals(20_000, link.observedKbps());
    }

    @Test public void aFlappingLinkGetsOneFullNoticeEveryThreeMinutes() {
        PoorLinkMonitor link = new PoorLinkMonitor(0);
        link.poor();
        assertTrue(link.noticeDue(1_000));
        link.noticeShown(1_000);
        link.okay(10_000);
        link.poor();
        assertFalse(link.noticeDue(20_000));
        link.okay(30_000);
        assertTrue(link.noticeDue(1_000 + PoorLinkMonitor.NOTICE_REPEAT_MS));
    }

    @Test public void aNoticeNotShownLeavesTheNextOneDue() {
        PoorLinkMonitor link = new PoorLinkMonitor(0);
        link.poor();
        // During a calibration (or with nothing lower to offer) no notice was shown.
        assertTrue(link.noticeDue(1_000));
        assertTrue(link.noticeDue(5_000));
    }

    @Test public void theMenuOffersTheEmergencyModeWhilePoorAndTwoMinutesAfter() {
        PoorLinkMonitor link = new PoorLinkMonitor(0);
        assertFalse(link.offersEmergency(0));
        link.poor();
        assertTrue(link.isPoor());
        assertTrue(link.offersEmergency(500_000));
        link.okay(600_000);
        assertTrue(link.offersEmergency(600_000 + PoorLinkMonitor.OFFER_AFTER_OKAY_MS - 1));
        assertFalse(link.offersEmergency(600_000 + PoorLinkMonitor.OFFER_AFTER_OKAY_MS));
    }

    @Test public void improvedFiresOncePerStableStretch() {
        PoorLinkMonitor link = new PoorLinkMonitor(0);
        assertFalse(link.improved(PoorLinkMonitor.IMPROVED_AFTER_MS - 1));
        assertTrue(link.improved(PoorLinkMonitor.IMPROVED_AFTER_MS));
        assertFalse(link.improved(PoorLinkMonitor.IMPROVED_AFTER_MS + 1));
        link.poor();
        assertFalse(link.improved(400_000 + PoorLinkMonitor.IMPROVED_AFTER_MS));
        link.okay(410_000);
        assertFalse(link.improved(410_000 + PoorLinkMonitor.IMPROVED_AFTER_MS - 1));
        assertTrue(link.improved(410_000 + PoorLinkMonitor.IMPROVED_AFTER_MS));
    }
}
