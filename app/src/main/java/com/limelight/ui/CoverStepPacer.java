package com.limelight.ui;

import java.util.Arrays;

/**
 * When each completed step's segment fills on the cover. Steps the host completes together or
 * while nobody sees the cover (a warm stream decided before its window showed) never jump: each
 * fills after the one before it, at most one per {@code stepMs}, and only from the moment the
 * cover is seen. Pure clock arithmetic (elapsedRealtime ms): independent of the animator duration scale.
 */
final class CoverStepPacer {
    /** Not completed. */
    private static final long OPEN = Long.MIN_VALUE;
    /** Completed while unseen: fills once the cover is seen. */
    private static final long PENDING = Long.MAX_VALUE;
    /** Full from the start (a handed-over cover's step, a stopped cover). */
    private static final long SHOWN = Long.MIN_VALUE + 1;

    private final long stepMs;
    private final long fillMs;
    private final long[] start;
    /** The earliest moment the next fill may start. */
    private long nextFree;

    CoverStepPacer(int steps, long stepMs, long fillMs) {
        this.stepMs = stepMs;
        this.fillMs = fillMs;
        start = new long[steps];
        Arrays.fill(start, OPEN);
    }

    /**
     * Step {@code index} completed. Returns its fill start, or -1 when it waits to be seen or
     * was completed already. {@code shown} applies it at once (a handed-over cover).
     */
    long done(int index, boolean seen, boolean shown, long now) {
        if (start[index] != OPEN) return -1L;
        if (shown) {
            start[index] = SHOWN;
            nextFree = Math.max(nextFree, now);
            return 0L;
        }
        if (!seen) {
            start[index] = PENDING;
            return -1L;
        }
        return schedule(index, now);
    }

    void open(int index) {
        start[index] = OPEN;
    }

    /** The cover is seen from {@code now}: waiting steps fill in turn after {@code delayMs}. */
    void seen(long now, long delayMs) {
        for (int index = 0; index < start.length; index++) {
            if (start[index] == PENDING) schedule(index, now + delayMs);
        }
    }

    private long schedule(int index, long earliest) {
        long at = Math.max(earliest, nextFree);
        start[index] = at;
        nextFree = at + stepMs;
        return at;
    }

    /** 0-1: how full segment {@code index} is at {@code now} (decelerating). */
    float fill(int index, long now) {
        long at = start[index];
        if (at == SHOWN) return 1f;
        if (at == OPEN || at == PENDING || now < at) return 0f;
        if (fillMs <= 0L || now - at >= fillMs) return 1f;
        float t = (now - at) / (float) fillMs;
        return 1f - (1f - t) * (1f - t);
    }

    /** A completed step is waiting or still filling: the current one waits for it. */
    boolean busy(long now) {
        for (long at : start) {
            if (at == PENDING || at != OPEN && at != SHOWN && now < at + fillMs) return true;
        }
        return false;
    }

    /** Every completed step full at once (the cover stops). */
    void finish() {
        for (int index = 0; index < start.length; index++) {
            if (start[index] != OPEN) start[index] = SHOWN;
        }
    }
}
