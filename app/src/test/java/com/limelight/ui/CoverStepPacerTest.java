package com.limelight.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoverStepPacerTest {
    private static final long STEP = 450L;
    private static final long FILL = 280L;
    private static final long DELAY = 150L;

    /** A connected stream completes steps 1 and 2 at once: step 2 still passes visibly. */
    @Test public void stepsCompletedTogetherFillInTurnWhileSeen() {
        CoverStepPacer pacer = new CoverStepPacer(5, STEP, FILL);
        assertEquals(1000L, pacer.done(0, true, false, 1000L));
        assertEquals(1000L + STEP, pacer.done(1, true, false, 1000L));
        assertEquals(0f, pacer.fill(1, 1000L + STEP - 1), 0f);
        assertTrue(pacer.busy(1000L + STEP));
        assertEquals(1f, pacer.fill(0, 1000L + FILL), 0f);
        assertEquals(1f, pacer.fill(1, 1000L + STEP + FILL), 0f);
        // The current step's sweep waits until the passed ones are full.
        assertFalse(pacer.busy(1000L + STEP + FILL));
    }

    /**
     * A warm stream decides steps 1-2 before its window shows: nothing fills unseen; once seen,
     * each fills in turn after the entrance delay, each held STEP before the next.
     */
    @Test public void stepsCompletedUnseenWaitForTheCoverToBeSeen() {
        CoverStepPacer pacer = new CoverStepPacer(5, STEP, FILL);
        assertEquals(-1L, pacer.done(0, false, false, 500L));
        assertEquals(-1L, pacer.done(1, false, false, 520L));
        assertEquals(0f, pacer.fill(0, 5000L), 0f);
        assertTrue(pacer.busy(5000L));
        pacer.seen(6000L, DELAY);
        assertEquals(0f, pacer.fill(0, 6000L + DELAY - 1), 0f);
        assertEquals(1f, pacer.fill(0, 6000L + DELAY + FILL), 0f);
        assertEquals(0f, pacer.fill(1, 6000L + DELAY + STEP - 1), 0f);
        assertTrue(pacer.fill(1, 6000L + DELAY + STEP + FILL / 2) > 0f);
        assertFalse(pacer.busy(6000L + DELAY + STEP + FILL));
    }

    /** A step completing right after one filled waits its turn; a later one starts at once. */
    @Test public void nextStepWaitsForTheMinimumPassOfThePreviousOne() {
        CoverStepPacer pacer = new CoverStepPacer(5, STEP, FILL);
        pacer.done(0, true, false, 0L);
        assertEquals(STEP, pacer.done(1, true, false, 100L));
        assertEquals(5000L, pacer.done(2, true, false, 5000L));
    }

    /** A handed-over cover continues what the launching window showed: no refill. */
    @Test public void handedOverStepsShowFullAtOnce() {
        CoverStepPacer pacer = new CoverStepPacer(5, STEP, FILL);
        pacer.done(0, false, true, 100L);
        assertEquals(1f, pacer.fill(0, 100L), 0f);
        assertEquals(100L + 1, pacer.done(1, true, false, 100L + 1));
    }

    /** Reduced motion: no fill motion, but the steps still pass one at a time. */
    @Test public void reducedMotionStillPacesWithoutMotion() {
        CoverStepPacer pacer = new CoverStepPacer(5, STEP, 0L);
        pacer.done(0, true, false, 0L);
        pacer.done(1, true, false, 0L);
        assertEquals(1f, pacer.fill(0, 0L), 0f);
        assertEquals(0f, pacer.fill(1, STEP - 1), 0f);
        assertEquals(1f, pacer.fill(1, STEP), 0f);
    }

    @Test public void reopenedStepsEmptyAndFinishFillsEverythingDone() {
        CoverStepPacer pacer = new CoverStepPacer(5, STEP, FILL);
        pacer.done(0, false, false, 0L);
        pacer.done(1, true, false, 0L);
        pacer.open(1);
        assertEquals(0f, pacer.fill(1, 10_000L), 0f);
        pacer.finish();
        assertEquals(1f, pacer.fill(0, 0L), 0f);
        assertFalse(pacer.busy(0L));
    }
}
