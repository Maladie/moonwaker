package com.limelight.console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The first-run wizard's rules (FIRST_RUN_WIZARD_DESIGN_PL.md section 2), without Android:
 * which steps a run includes, where Next, Back and Skip lead, the readiness checklist of step E,
 * the wake test and the "To finish" list. Facts come from the caller, read fresh from the host;
 * nothing here is stored.
 */
final class FirstRunPlan {
    /** Design steps A to I; H2 is CONTROLS, H3 FEATURES. */
    enum Step {
        WELCOME, INSTALL, PAIR, PROFILE, READINESS, NETWORK, FAMILY, EXTRAS, CONTROLS,
        FEATURES, DONE
    }

    /** How the wizard was opened. */
    enum Entry {
        /** No paired host on this TV: from A. */
        FIRST_RUN,
        /** Settings › About: from A, every step showing the current state. */
        SETTINGS,
        /** "Add Host" on the host screen: from B. */
        ADD_HOST,
        /** "Pair" on an unpaired host tile: C and D, then straight into the host. */
        PAIR_HOST
    }

    /** What the wizard knows about the chosen host now. */
    static final class Facts {
        final boolean paired;
        final boolean profileChosen;
        final boolean childProfile;
        final boolean childProfilesAvailable;
        final boolean featuresAvailable;

        Facts(boolean paired, boolean profileChosen, boolean childProfile,
              boolean childProfilesAvailable, boolean featuresAvailable) {
            this.paired = paired;
            this.profileChosen = profileChosen;
            this.childProfile = childProfile;
            this.childProfilesAvailable = childProfilesAvailable;
            this.featuresAvailable = featuresAvailable;
        }
    }

    private FirstRunPlan() { }

    /** Shown by itself only on a TV with no paired host, or to resume an interrupted run. */
    static boolean autoShow(boolean anyPairedHost, Step resumeStep) {
        return resumeStep != null || !anyPairedHost;
    }

    static Step first(Entry entry) {
        switch (entry) {
            case ADD_HOST: return Step.INSTALL;
            case PAIR_HOST: return Step.PAIR;
            default: return Step.WELCOME;
        }
    }

    static boolean included(Step step, Entry entry, Facts facts) {
        switch (step) {
            case WELCOME:
                return entry == Entry.FIRST_RUN || entry == Entry.SETTINGS;
            case INSTALL:
                return entry != Entry.PAIR_HOST;
            case PAIR:
            case PROFILE:
                return true;
            case DONE:
                return entry != Entry.PAIR_HOST;
            default:
                // A child does not set up the host; a re-pair goes straight back to its host.
                if (entry == Entry.PAIR_HOST || facts.childProfile) return false;
                if (step == Step.FAMILY) return facts.childProfilesAvailable;
                if (step == Step.FEATURES) return facts.featuresAvailable;
                return true;
        }
    }

    /** The progress dots: every step of this run, optional ones that do not apply left out. */
    static List<Step> steps(Entry entry, Facts facts) {
        List<Step> steps = new ArrayList<>();
        for (Step step : Step.values()) if (included(step, entry, facts)) steps.add(step);
        return Collections.unmodifiableList(steps);
    }

    /** Pairing and a profile are the only gates: without them there is nothing to play. */
    static boolean canContinue(Step step, Facts facts) {
        if (step == Step.PAIR) return facts.paired;
        if (step == Step.PROFILE) return facts.profileChosen;
        return true;
    }

    /** The step after {@code step}, or null when the run ends there. */
    static Step next(Step step, Entry entry, Facts facts) {
        for (int index = step.ordinal() + 1; index < Step.values().length; index++) {
            Step candidate = Step.values()[index];
            if (included(candidate, entry, facts)) return candidate;
        }
        return null;
    }

    /** The step before {@code step}, or null at the first one (Back then leaves the wizard). */
    static Step previous(Step step, Entry entry, Facts facts) {
        for (int index = step.ordinal() - 1; index >= 0; index--) {
            Step candidate = Step.values()[index];
            if (included(candidate, entry, facts)) return candidate;
        }
        return null;
    }

    /** Steps with "Skip"; the others offer Help instead and never strand the user. */
    static boolean skippable(Step step) {
        switch (step) {
            case READINESS:
            case NETWORK:
            case FAMILY:
            case EXTRAS:
            case FEATURES:
                return true;
            default:
                return false;
        }
    }

    /**
     * "To finish" holds only what is really missing: the readiness step while a required row is
     * open. Skipping an optional step (quality, family, extras, features) is a choice, not a gap.
     */
    static boolean onTodoList(Step step) {
        return step == Step.READINESS;
    }

    /** "To finish" after leaving {@code step}. */
    static Set<Step> afterLeaving(Set<Step> todo, Step step, boolean requiredOpen) {
        Set<Step> result = todo.isEmpty() ? EnumSet.noneOf(Step.class) : EnumSet.copyOf(todo);
        if (!onTodoList(step)) return result;
        if (requiredOpen) result.add(step);
        else result.remove(step);
        return result;
    }

    // --- Step E: readiness checklist -------------------------------------------------------

    enum Check {
        VIBEPOLLO(true), GAME_LIBRARY(true), STEAM_LIBRARY(false), EPIC(false),
        REMOTE_SIGN_IN(false), WAKE(false);

        final boolean required;

        Check(boolean required) {
            this.required = required;
        }
    }

    /** OK green, ATTENTION amber "!", OPTIONAL a hollow circle, UNKNOWN until the host says. */
    enum Status { OK, ATTENTION, OPTIONAL, UNKNOWN, CHECKING }

    enum WakePhase { NOT_TESTED, SLEEPING, WAKING, OK, FAILED }

    /** Host facts for the checklist; a null Boolean is a fact the host could not tell. */
    static final class Readiness {
        final Boolean vibepollo;
        final Boolean gameLibrary;
        final Boolean steamApiKeySaved;
        final Boolean epicSignedIn;
        final Boolean remoteSignInReady;
        final WakePhase wake;

        Readiness(Boolean vibepollo, Boolean gameLibrary, Boolean steamApiKeySaved,
                  Boolean epicSignedIn, Boolean remoteSignInReady, WakePhase wake) {
            this.vibepollo = vibepollo;
            this.gameLibrary = gameLibrary;
            this.steamApiKeySaved = steamApiKeySaved;
            this.epicSignedIn = epicSignedIn;
            this.remoteSignInReady = remoteSignInReady;
            this.wake = wake;
        }
    }

    static Status status(Check check, Readiness readiness) {
        switch (check) {
            case VIBEPOLLO: return flag(readiness.vibepollo, Status.ATTENTION);
            case GAME_LIBRARY: return flag(readiness.gameLibrary, Status.ATTENTION);
            // Without the key only installed games show: worth the "!", not required.
            case STEAM_LIBRARY: return flag(readiness.steamApiKeySaved, Status.ATTENTION);
            case EPIC: return flag(readiness.epicSignedIn, Status.OPTIONAL);
            // Not required, but recommended: a locked PC otherwise needs a keyboard.
            case REMOTE_SIGN_IN: return flag(readiness.remoteSignInReady, Status.ATTENTION);
            default:
                switch (readiness.wake) {
                    case OK: return Status.OK;
                    case FAILED: return Status.ATTENTION;
                    case SLEEPING:
                    case WAKING: return Status.CHECKING;
                    default: return Status.OPTIONAL;
                }
        }
    }

    private static Status flag(Boolean value, Status whenFalse) {
        return value == null ? Status.UNKNOWN : value ? Status.OK : whenFalse;
    }

    /** A required row that is not green; unknown counts as open. */
    static boolean requiredOpen(Readiness readiness) {
        for (Check check : Check.values()) {
            if (check.required && status(check, readiness) != Status.OK) return true;
        }
        return false;
    }

    // --- Wake test -------------------------------------------------------------------------

    /** Until the PC stops answering after the sleep request. */
    static final long SLEEP_TIMEOUT_MS = 60_000L;
    /** From the Wake-on-LAN packet until the PC answers again. */
    static final long WAKE_TIMEOUT_MS = 120_000L;

    /**
     * The wake test's next phase from one observation: the PC went down after the sleep
     * request (the caller then sends Wake-on-LAN), answered again after it, or ran out of time.
     */
    static WakePhase advanceWake(WakePhase phase, boolean online, long phaseElapsedMs) {
        if (phase == WakePhase.SLEEPING) {
            if (!online) return WakePhase.WAKING;
            return phaseElapsedMs > SLEEP_TIMEOUT_MS ? WakePhase.FAILED : phase;
        }
        if (phase == WakePhase.WAKING) {
            if (online) return WakePhase.OK;
            return phaseElapsedMs > WAKE_TIMEOUT_MS ? WakePhase.FAILED : phase;
        }
        return phase;
    }
}
