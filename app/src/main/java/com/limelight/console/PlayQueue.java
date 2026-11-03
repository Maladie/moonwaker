package com.limelight.console;

import android.content.Context;

import java.util.function.LongSupplier;

/**
 * The one Play queued behind a close on its host: an app-scoped fact (main thread) that
 * outlives the screen that queued it, so a stream Home replaced by the console, or a recreated
 * console, still shows and starts it. {@link SessionOrchestrator} alone decides when it is
 * queued, started or dropped; screens only render it. The user leaving MoonWaker clears it.
 * It also carries a Play handed to an attaching stream screen for its switch, whose failure the
 * stream reports back here for the next screen's table to decide.
 */
public final class PlayQueue {
    /** The Application holds the process's queue. */
    public interface Owner { PlayQueue playQueue(); }

    private final LongSupplier clock;
    private PlayIntent intent;
    /** When this intent was first queued; re-queues of the same intent keep it. */
    private long since;
    /** Bumped on every change: fences a timeout armed for an older entry. */
    private long generation;
    /**
     * The Play whose switch the table handed to an attaching stream screen; its failure comes
     * back here for the screen shown next to decide. Null when none runs.
     */
    private Handoff handoff;
    private long handoffs;
    /** The resumed screen's queue step: a switch failure reaches it at once. */
    private Runnable resumed;

    /** A Play handed to the stream screen for its switch (ATTACH_PRESENTATION). */
    static final class Handoff {
        final long id;
        final PlayIntent intent;
        /** The switch was meant to keep the running game (switchKeepsRunningGame). */
        final boolean keepsRunningGame;
        /** The stream screen's failure reason once it reported one; null while it runs. */
        String failure;
        /** When that failure was reported ({@link #now}). */
        long failedAt;

        Handoff(long id, PlayIntent intent, boolean keepsRunningGame) {
            this.id = id;
            this.intent = intent;
            this.keepsRunningGame = keepsRunningGame;
        }
    }

    /** {@code clock}: monotonic milliseconds (the main thread's uptime in the app). */
    public PlayQueue(LongSupplier clock) {
        this.clock = clock;
    }

    public static PlayQueue of(Context context) {
        return ((Owner) context.getApplicationContext()).playQueue();
    }

    /** MoonWaker went to the background (no Activity of it started): nothing waits for it. */
    public void appLeft() {
        clear();
        handoff = null;
    }

    /** Hands {@code next}'s switch to a stream screen; returns the id its report carries. */
    long handOff(PlayIntent next, boolean keepsRunningGame) {
        handoff = new Handoff(++handoffs, next, keepsRunningGame);
        return handoff.id;
    }

    /**
     * The stream screen's switch of handoff {@code id} failed with {@code error}. Only the
     * current handoff's first report counts: a newer Play or handoff makes an older one stale.
     */
    public void switchFailed(long id, String error) {
        if (handoff == null || handoff.id != id || handoff.failure != null) return;
        handoff.failure = error == null ? "" : error;
        handoff.failedAt = clock.getAsLong();
        if (resumed != null) resumed.run();
    }

    /** {@code step} is the resumed screen's: a switch failure reported from now on runs it. */
    void resumed(Runnable step) {
        resumed = step;
    }

    /** The screen of {@code step} left; a screen resumed since keeps its own. */
    void paused(Runnable step) {
        if (resumed == step) resumed = null;
    }

    /** The handoff whose switch failed, taken by the screen deciding it; null when none. */
    Handoff takeFailedHandoff() {
        Handoff failed = handoff;
        if (failed == null || failed.failure == null) return null;
        handoff = null;
        return failed;
    }

    /** A newer Play: a handed-off switch still running no longer speaks for the user. */
    void dropHandoff() {
        handoff = null;
    }

    PlayIntent intent() { return intent; }
    long since() { return since; }
    long generation() { return generation; }
    long now() { return clock.getAsLong(); }

    /** Queues {@code next}, first queued at {@code firstQueuedAt}; returns its generation. */
    long put(PlayIntent next, long firstQueuedAt) {
        intent = next;
        since = firstQueuedAt;
        return ++generation;
    }

    /** Empties the queue; returns what it held, or null. */
    PlayIntent clear() {
        PlayIntent held = intent;
        if (held == null) return null;
        intent = null;
        since = 0L;
        generation++;
        return held;
    }
}
