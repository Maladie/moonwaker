package com.limelight.console;

/**
 * What an Intent returning to the console asks of it: skip the next automatic warm-up, open a
 * host's hard-close confirmation, and resume the stream a Watch Next tile names once the user
 * passed its profile gate. Taken from a fresh launch or a delivered Intent; a
 * recreation from saved state (configuration change or process death) keeps only its saved
 * pending suppression and never replays the Intent, whose extras the system re-delivers.
 */
final class ConsoleReturnRequests {
    private boolean suppressAutoWarmUp;
    /** The host whose hard-close confirmation opens at the next resume; "" when none. */
    private String hardTerminateHostId = "";
    /** The stream session a Watch Next tile opened the console for; "" when none. */
    private String watchNextSessionId = "";

    void created(boolean restored, boolean savedSuppress, boolean suppressExtra,
                 String hardTerminateExtra, String watchNextExtra) {
        if (restored) {
            suppressAutoWarmUp = savedSuppress;
            return;
        }
        delivered(suppressExtra, hardTerminateExtra);
        watchNextSessionId = watchNextExtra == null ? "" : watchNextExtra.trim();
    }

    /** An Intent delivered to the running console (CLEAR_TOP | SINGLE_TOP). */
    void delivered(boolean suppressExtra, String hardTerminateExtra) {
        if (suppressExtra) suppressAutoWarmUp = true;
        if (hardTerminateExtra != null && !hardTerminateExtra.isEmpty()) {
            hardTerminateHostId = hardTerminateExtra;
        }
    }

    boolean suppressesAutoWarmUp() {
        return suppressAutoWarmUp;
    }

    boolean takeSuppressAutoWarmUp() {
        boolean taken = suppressAutoWarmUp;
        suppressAutoWarmUp = false;
        return taken;
    }

    String watchNextSessionId() {
        return watchNextSessionId;
    }

    String takeWatchNextSessionId() {
        String taken = watchNextSessionId;
        watchNextSessionId = "";
        return taken;
    }

    String takeHardTerminateOffer() {
        String taken = hardTerminateHostId;
        hardTerminateHostId = "";
        return taken;
    }
}
