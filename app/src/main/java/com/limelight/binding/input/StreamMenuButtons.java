package com.limelight.binding.input;

/**
 * MoonWaker: the single owner of what a local stream-menu button does on a tap versus a hold.
 * One press is tracked at a time, Xbox Share apart from it (it never reaches the host or
 * the menu, so it competes with no other press); a hold fires after {@code holdMs} while it
 * is still down.
 * <ul>
 *   <li>{@link Press#TRIGGER} (configured pad hold trigger): tap reaches the host, hold opens the menu.</li>
 *   <li>{@link Press#CREATE} (PlayStation Create set to "Menu and screenshots", never sent to
 *       the host): tap opens the menu, held {@link #CREATE_HOLD_MS} a screenshot (as on a PS5).
 *       A trigger pending when it goes down is the caller's chord: it sends it to the host.</li>
 *   <li>{@link Press#BACK} (remote or pad Back, never sent to the host): tap is stream Back,
 *       hold opens the menu.</li>
 *   <li>{@link Press#GUIDE} (pad PS/Guide): tap goes Home (the console carousel), hold sends
 *       Guide to the host until release.</li>
 *   <li>{@link #pressShare} (Xbox Share, never sent to the host): tap is a screenshot, hold
 *       saves the last 30 s.</li>
 * </ul>
 */
final class StreamMenuButtons {
    /** Create held this long takes a screenshot; released earlier it opens the menu. */
    static final long CREATE_HOLD_MS = 600;

    enum Press { TRIGGER, CREATE, BACK, GUIDE }

    interface Scheduler {
        void postDelayed(Runnable task, long delayMs);
        void remove(Runnable task);
    }

    interface Effects {
        void openMenu();
        void navigateBack();
        void navigateHome();
        void holdGuideOnHost();
        /** A screenshot ({@code clip} false) or the last 30 s saved on the PC. */
        void capture(boolean clip);
    }

    private final Scheduler scheduler;
    private long holdMs;
    private final Effects effects;
    private final Runnable timeout = this::fire;
    private final Runnable shareTimeout = this::fireShare;
    private Press pending;
    private boolean shareDown;

    StreamMenuButtons(Scheduler scheduler, long holdMs, Effects effects) {
        this.scheduler = scheduler;
        this.holdMs = holdMs;
        this.effects = effects;
    }

    void setHoldMs(long holdMs) {
        this.holdMs = holdMs;
    }

    void press(Press press) {
        cancel();
        pending = press;
        scheduler.postDelayed(timeout, press == Press.CREATE ? CREATE_HOLD_MS : holdMs);
    }

    /**
     * Ends {@code press} if it is the pending one. A tap runs its effect; returns true only for
     * a TRIGGER tap, which the caller still forwards to the host.
     */
    boolean release(Press press) {
        if (pending != press) return false;
        cancel();
        switch (press) {
            case BACK:
                effects.navigateBack();
                return false;
            case GUIDE:
                effects.navigateHome();
                return false;
            case CREATE:
                effects.openMenu();
                return false;
            default:
                return true;
        }
    }

    /** Drops the pending press without its tap effect; returns whether one was pending. */
    boolean cancel() {
        if (pending == null) return false;
        scheduler.remove(timeout);
        pending = null;
        return true;
    }

    private void fire() {
        Press held = pending;
        pending = null;
        if (held == Press.GUIDE) effects.holdGuideOnHost();
        else if (held == Press.CREATE) effects.capture(false);
        else if (held != null) effects.openMenu();
    }

    /** Xbox Share down: a tap is a screenshot, a hold the last 30 s. */
    void pressShare() {
        cancelShare();
        shareDown = true;
        scheduler.postDelayed(shareTimeout, holdMs);
    }

    void releaseShare() {
        if (!shareDown) return;
        cancelShare();
        effects.capture(false);
    }

    /** Drops a held Share without its capture (its pad went away). */
    void cancelShare() {
        if (!shareDown) return;
        scheduler.remove(shareTimeout);
        shareDown = false;
    }

    private void fireShare() {
        if (!shareDown) return;
        shareDown = false;
        effects.capture(true);
    }
}
