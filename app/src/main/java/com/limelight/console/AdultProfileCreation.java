package com.limelight.console;

/**
 * The TV's "Add profile": the authorizing adult's PIN when it has one, a Windows account from
 * the host's list, then optionally the new profile's first PIN typed twice. The host decides;
 * nothing here is kept after the request.
 */
final class AdultProfileCreation {
    enum Step { ACTOR_PIN, ACCOUNT, NEW_PIN }

    /** What a refused request leads to. */
    enum Retry { ACTOR_PIN, ACCOUNT, NOT_AUTHORIZED, FAILED }

    private final boolean actorPinRequired;
    private Step step;
    private String actorPin;
    private String accountName;
    private String requestId;
    private AdultPinChange newPin;

    AdultProfileCreation(boolean actorPinRequired) {
        this.actorPinRequired = actorPinRequired;
        step = actorPinRequired ? Step.ACTOR_PIN : Step.ACCOUNT;
    }

    Step step() {
        return step;
    }

    String actorPin() {
        return actorPin;
    }

    String accountName() {
        return accountName;
    }

    String requestId() {
        return requestId;
    }

    /** The new profile's PIN pages; null without a PIN. */
    AdultPinChange newPinChange() {
        return newPin;
    }

    /** The new profile's first PIN, or null for none. */
    String newPin() {
        return newPin == null ? null : newPin.newPin();
    }

    void acceptActorPin(String pin) {
        actorPin = pin;
        step = Step.ACCOUNT;
    }

    /**
     * Chooses the account; one request ID per choice, so a retried create replays. Returns
     * whether to submit now (no PIN for the new profile).
     */
    boolean chooseAccount(String name, boolean withPin, String requestId) {
        accountName = name;
        this.requestId = requestId;
        newPin = withPin ? new AdultPinChange(AdultPinChange.Mode.SET) : null;
        step = withPin ? Step.NEW_PIN : Step.ACCOUNT;
        return !withPin;
    }

    Retry refused(String reason) {
        newPin = null;
        if ("invalid_pin".equals(reason) || "rate_limited".equals(reason)
                || "current_pin_required".equals(reason)) {
            actorPin = null;
            step = actorPinRequired ? Step.ACTOR_PIN : Step.ACCOUNT;
            return actorPinRequired ? Retry.ACTOR_PIN : Retry.FAILED;
        }
        step = Step.ACCOUNT;
        if ("windows_account_in_use".equals(reason)
                || "windows_account_unsupported".equals(reason)) return Retry.ACCOUNT;
        if ("first_pin_grant_required".equals(reason)
                || "child_action_not_allowed".equals(reason)) return Retry.NOT_AUTHORIZED;
        return Retry.FAILED;
    }
}
