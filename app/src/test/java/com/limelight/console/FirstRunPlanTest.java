package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.console.FirstRunPlan.Check;
import com.limelight.console.FirstRunPlan.Entry;
import com.limelight.console.FirstRunPlan.Facts;
import com.limelight.console.FirstRunPlan.Readiness;
import com.limelight.console.FirstRunPlan.Status;
import com.limelight.console.FirstRunPlan.Step;
import com.limelight.console.FirstRunPlan.WakePhase;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public class FirstRunPlanTest {
    private static final Facts ADULT = new Facts(true, true, false, true, true);
    private static final Facts NEW_TV = new Facts(false, false, false, false, false);
    private static final Facts CHILD = new Facts(true, true, true, true, true);

    @Test
    public void showsItselfOnlyWithoutPairedHostOrToResume() {
        assertTrue(FirstRunPlan.autoShow(false, null));
        assertFalse(FirstRunPlan.autoShow(true, null));
        assertTrue(FirstRunPlan.autoShow(true, Step.READINESS));
    }

    @Test
    public void entriesStartWhereTheDesignSays() {
        assertEquals(Step.WELCOME, FirstRunPlan.first(Entry.FIRST_RUN));
        assertEquals(Step.WELCOME, FirstRunPlan.first(Entry.SETTINGS));
        assertEquals(Step.INSTALL, FirstRunPlan.first(Entry.ADD_HOST));
        assertEquals(Step.PAIR, FirstRunPlan.first(Entry.PAIR_HOST));
    }

    @Test
    public void adultFirstRunWalksEveryStepInOrder() {
        assertEquals(Arrays.asList(Step.values()), FirstRunPlan.steps(Entry.FIRST_RUN, ADULT));
        Step step = FirstRunPlan.first(Entry.FIRST_RUN);
        int visited = 1;
        while ((step = FirstRunPlan.next(step, Entry.FIRST_RUN, ADULT)) != null) visited++;
        assertEquals(Step.values().length, visited);
    }

    @Test
    public void backRetracesTheSameSteps() {
        assertEquals(Step.CONTROLS, FirstRunPlan.previous(Step.FEATURES, Entry.FIRST_RUN, ADULT));
        assertEquals(Step.WELCOME, FirstRunPlan.previous(Step.INSTALL, Entry.FIRST_RUN, ADULT));
        assertNull(FirstRunPlan.previous(Step.WELCOME, Entry.FIRST_RUN, ADULT));
        assertNull(FirstRunPlan.previous(Step.INSTALL, Entry.ADD_HOST, ADULT));
        assertNull(FirstRunPlan.previous(Step.PAIR, Entry.PAIR_HOST, ADULT));
    }

    @Test
    public void addHostSkipsTheWelcome() {
        assertFalse(FirstRunPlan.steps(Entry.ADD_HOST, ADULT).contains(Step.WELCOME));
        assertTrue(FirstRunPlan.steps(Entry.ADD_HOST, ADULT).contains(Step.DONE));
    }

    @Test
    public void pairingATileEndsAfterTheProfile() {
        assertEquals(Arrays.asList(Step.PAIR, Step.PROFILE),
                FirstRunPlan.steps(Entry.PAIR_HOST, ADULT));
        assertNull(FirstRunPlan.next(Step.PROFILE, Entry.PAIR_HOST, ADULT));
    }

    @Test
    public void childProfileSkipsHostSetup() {
        assertEquals(Step.DONE, FirstRunPlan.next(Step.PROFILE, Entry.FIRST_RUN, CHILD));
        assertFalse(FirstRunPlan.steps(Entry.FIRST_RUN, CHILD).contains(Step.READINESS));
    }

    @Test
    public void optionalStepsDisappearWhenTheyDoNotApply() {
        Facts plain = new Facts(true, true, false, false, false);
        assertEquals(Step.EXTRAS, FirstRunPlan.next(Step.NETWORK, Entry.FIRST_RUN, plain));
        assertEquals(Step.DONE, FirstRunPlan.next(Step.CONTROLS, Entry.FIRST_RUN, plain));
    }

    @Test
    public void pairingAndProfileGateNext() {
        assertFalse(FirstRunPlan.canContinue(Step.PAIR, NEW_TV));
        assertFalse(FirstRunPlan.canContinue(Step.PROFILE, NEW_TV));
        assertTrue(FirstRunPlan.canContinue(Step.INSTALL, NEW_TV));
        assertTrue(FirstRunPlan.canContinue(Step.PAIR, ADULT));
        assertTrue(FirstRunPlan.canContinue(Step.PROFILE, ADULT));
    }

    @Test
    public void everyStepHasSkipOrHelp() {
        for (Step step : Step.values()) {
            boolean skip = FirstRunPlan.skippable(step);
            boolean gate = step == Step.INSTALL || step == Step.PAIR || step == Step.PROFILE;
            boolean plain = step == Step.WELCOME || step == Step.CONTROLS || step == Step.DONE;
            assertTrue(step.name(), skip || gate || plain);
        }
        assertFalse(FirstRunPlan.skippable(Step.PAIR));
        assertTrue(FirstRunPlan.skippable(Step.READINESS));
    }

    @Test
    public void skippingAnOptionalStepNeverJoinsTheToFinishList() {
        for (Step step : new Step[] {Step.NETWORK, Step.FAMILY, Step.EXTRAS, Step.FEATURES}) {
            assertTrue(FirstRunPlan.afterLeaving(EnumSet.noneOf(Step.class), step, true).isEmpty());
            assertFalse(FirstRunPlan.onTodoList(step));
        }
    }

    @Test
    public void readinessStaysOnTheListWhileARequiredRowIsOpen() {
        Set<Step> todo = FirstRunPlan.afterLeaving(EnumSet.noneOf(Step.class), Step.READINESS,
                true);
        assertEquals(EnumSet.of(Step.READINESS), todo);
        assertTrue(FirstRunPlan.afterLeaving(todo, Step.READINESS, false).isEmpty());
    }

    @Test
    public void checklistRowsFollowTheHostFacts() {
        Readiness ready = new Readiness(true, true, true, true, true, WakePhase.OK);
        for (Check check : Check.values()) assertEquals(Status.OK, FirstRunPlan.status(check, ready));
        assertFalse(FirstRunPlan.requiredOpen(ready));

        Readiness fresh = new Readiness(false, null, false, false, false, WakePhase.NOT_TESTED);
        assertEquals(Status.ATTENTION, FirstRunPlan.status(Check.VIBEPOLLO, fresh));
        assertEquals(Status.UNKNOWN, FirstRunPlan.status(Check.GAME_LIBRARY, fresh));
        assertEquals(Status.ATTENTION, FirstRunPlan.status(Check.STEAM_LIBRARY, fresh));
        assertEquals(Status.OPTIONAL, FirstRunPlan.status(Check.EPIC, fresh));
        assertEquals(Status.ATTENTION, FirstRunPlan.status(Check.REMOTE_SIGN_IN, fresh));
        assertEquals(Status.OPTIONAL, FirstRunPlan.status(Check.WAKE, fresh));
        assertTrue(FirstRunPlan.requiredOpen(fresh));
    }

    @Test
    public void onlyVibepolloAndTheLibraryAreRequired() {
        Readiness optionalMissing = new Readiness(true, true, false, false, false,
                WakePhase.FAILED);
        assertFalse(FirstRunPlan.requiredOpen(optionalMissing));
        assertEquals(Status.ATTENTION, FirstRunPlan.status(Check.WAKE, optionalMissing));
    }

    @Test
    public void wakeTestSleepsThenWakes() {
        WakePhase phase = WakePhase.SLEEPING;
        assertEquals(WakePhase.SLEEPING, FirstRunPlan.advanceWake(phase, true, 5_000L));
        phase = FirstRunPlan.advanceWake(phase, false, 9_000L);
        assertEquals(WakePhase.WAKING, phase);
        assertEquals(WakePhase.WAKING, FirstRunPlan.advanceWake(phase, false, 30_000L));
        assertEquals(WakePhase.OK, FirstRunPlan.advanceWake(phase, true, 40_000L));
        assertEquals(Status.CHECKING, FirstRunPlan.status(Check.WAKE,
                new Readiness(true, true, true, true, true, WakePhase.WAKING)));
    }

    @Test
    public void wakeTestFailsWhenThePcNeverSleepsOrNeverWakes() {
        assertEquals(WakePhase.FAILED, FirstRunPlan.advanceWake(WakePhase.SLEEPING, true,
                FirstRunPlan.SLEEP_TIMEOUT_MS + 1));
        assertEquals(WakePhase.FAILED, FirstRunPlan.advanceWake(WakePhase.WAKING, false,
                FirstRunPlan.WAKE_TIMEOUT_MS + 1));
        assertEquals(WakePhase.OK, FirstRunPlan.advanceWake(WakePhase.OK, false, 0L));
    }
}
