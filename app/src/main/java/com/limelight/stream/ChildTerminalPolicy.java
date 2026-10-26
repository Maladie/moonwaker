package com.limelight.stream;

import com.limelight.R;

/** Classifies a host child-session terminal result without retaining an Activity. */
public final class ChildTerminalPolicy {
    public enum Action { RETAIN_GAME_ONLY, TERMINATE_SESSION }

    private ChildTerminalPolicy() { }

    public static Action action(String reason, boolean cleanupRequired) {
        return !cleanupRequired && "game_stopped".equalsIgnoreCase(
                reason == null ? "" : reason.trim())
                ? Action.RETAIN_GAME_ONLY : Action.TERMINATE_SESSION;
    }

    /** The notice for the reason the host (or this client) ended a child's session. */
    public static int message(String reason) {
        switch (reason == null ? "" : reason.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "game_access_revoked":
            case "game_not_shared": return R.string.child_session_game_access_revoked_message;
            case "time_expired":
            case "daily_limit_reached":
            case "outside_schedule": return R.string.child_session_time_expired_message;
            case "game_ended": return R.string.child_session_game_ended_message;
            case "game_replaced": return R.string.child_session_game_replaced_message;
            case "game_unobservable": return R.string.child_session_game_unobservable_message;
            default: return R.string.child_session_ended_message;
        }
    }
}
