package com.limelight.console;

/**
 * The PIN pages of setting, changing or removing an adult profile's own app PIN from the TV:
 * the current PIN when there is one, then a new PIN typed twice. The host decides; nothing
 * here is kept after the request.
 */
final class AdultPinChange {
    enum Mode { SET, CHANGE, REMOVE }

    enum Step { CURRENT, NEW, CONFIRM }

    enum Next { ASK, MISMATCH, SUBMIT }

    final Mode mode;
    private Step step;
    private String currentPin;
    private String newPin;

    AdultPinChange(Mode mode) {
        this.mode = mode;
        restart();
    }

    Step step() {
        return step;
    }

    String currentPin() {
        return currentPin;
    }

    /** Null when the PIN is removed. */
    String newPin() {
        return mode == Mode.REMOVE ? null : newPin;
    }

    Next accept(String pin) {
        switch (step) {
            case CURRENT:
                currentPin = pin;
                if (mode == Mode.REMOVE) return Next.SUBMIT;
                step = Step.NEW;
                return Next.ASK;
            case NEW:
                newPin = pin;
                step = Step.CONFIRM;
                return Next.ASK;
            case CONFIRM:
            default:
                if (pin.equals(newPin)) return Next.SUBMIT;
                newPin = null;
                step = Step.NEW;
                return Next.MISMATCH;
        }
    }

    /** After the host refused the current PIN, or replaced it meanwhile. */
    void restart() {
        currentPin = null;
        newPin = null;
        step = mode == Mode.SET ? Step.NEW : Step.CURRENT;
    }
}
