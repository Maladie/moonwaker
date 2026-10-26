package com.limelight.stream;

import java.util.ArrayDeque;

/**
 * One stream's link: the core's poor/okay reports and the bytes it received, and from them when
 * the emergency mode is offered. Pure and clock-free (callers pass uptime ms); the decoder thread
 * adds bytes while the activity reports status, so every method is synchronized.
 */
public final class PoorLinkMonitor {
    /** What the link carried is judged over this long. */
    static final long RX_WINDOW_MS = 10_000L;
    /** A full notice returns no sooner than this, however often the link drops. */
    static final long NOTICE_REPEAT_MS = 3 * 60_000L;
    /** The menu keeps offering the emergency mode this long after the link recovered. */
    static final long OFFER_AFTER_OKAY_MS = 2 * 60_000L;
    /** An emergency stream this long without a poor report suggests going back. */
    static final long IMPROVED_AFTER_MS = 5 * 60_000L;

    private final ArrayDeque<long[]> rx = new ArrayDeque<>();
    private boolean poor;
    private long okayAtMs = Long.MIN_VALUE;
    private long lastNoticeMs = Long.MIN_VALUE;
    private long stableSinceMs;
    private boolean improvedNoticed;

    public PoorLinkMonitor(long nowMs) {
        stableSinceMs = nowMs;
    }

    /** {@code rxBytes} is the app's received byte counter; negative when unsupported. */
    public synchronized void addRx(long nowMs, long rxBytes) {
        if (rxBytes < 0) return;
        rx.addLast(new long[]{nowMs, rxBytes});
        while (rx.size() > 2 && nowMs - rx.peekFirst()[0] > RX_WINDOW_MS) rx.removeFirst();
    }

    /** Kb/s received over the last window; 0 when unknown. */
    public synchronized int observedKbps() {
        if (rx.size() < 2) return 0;
        long[] first = rx.peekFirst(), last = rx.peekLast();
        long elapsed = last[0] - first[0];
        return elapsed <= 0 || last[1] < first[1]
                ? 0 : (int) ((last[1] - first[1]) * 8L / elapsed);
    }

    public synchronized void poor() {
        poor = true;
    }

    /** A full notice may show now: none yet, or the last one long enough ago. */
    public synchronized boolean noticeDue(long nowMs) {
        return lastNoticeMs == Long.MIN_VALUE || nowMs - lastNoticeMs >= NOTICE_REPEAT_MS;
    }

    public synchronized void noticeShown(long nowMs) {
        lastNoticeMs = nowMs;
    }

    public synchronized void okay(long nowMs) {
        if (!poor) return;
        poor = false;
        okayAtMs = nowMs;
        stableSinceMs = nowMs;
        improvedNoticed = false;
    }

    public synchronized boolean isPoor() {
        return poor;
    }

    /** The menu offers the emergency mode while the link is poor and shortly after. */
    public synchronized boolean offersEmergency(long nowMs) {
        return poor || (okayAtMs != Long.MIN_VALUE && nowMs - okayAtMs < OFFER_AFTER_OKAY_MS);
    }

    /** True once per stable stretch: no poor report for {@link #IMPROVED_AFTER_MS}. */
    public synchronized boolean improved(long nowMs) {
        if (poor || improvedNoticed || nowMs - stableSinceMs < IMPROVED_AFTER_MS) return false;
        improvedNoticed = true;
        return true;
    }
}
