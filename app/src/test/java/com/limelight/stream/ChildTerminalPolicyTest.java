package com.limelight.stream;

import com.limelight.R;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChildTerminalPolicyTest {
    @Test public void gameStoppedRetainsButBudgetAndUncertainCleanupTerminate() {
        assertEquals(ChildTerminalPolicy.Action.RETAIN_GAME_ONLY,
                ChildTerminalPolicy.action("game_stopped", false));
        assertEquals(ChildTerminalPolicy.Action.TERMINATE_SESSION,
                ChildTerminalPolicy.action("time_expired", false));
        assertEquals(ChildTerminalPolicy.Action.TERMINATE_SESSION,
                ChildTerminalPolicy.action("game_stopped", true));
    }

    @Test public void eachHostEndReasonHasItsOwnNotice() {
        assertEquals(R.string.child_session_game_ended_message,
                ChildTerminalPolicy.message("game_ended"));
        assertEquals(R.string.child_session_game_replaced_message,
                ChildTerminalPolicy.message("game_replaced"));
        assertEquals(R.string.child_session_game_unobservable_message,
                ChildTerminalPolicy.message("GAME_UNOBSERVABLE"));
        assertEquals(R.string.child_session_time_expired_message,
                ChildTerminalPolicy.message("daily_limit_reached"));
        assertEquals(R.string.child_session_game_access_revoked_message,
                ChildTerminalPolicy.message("game_not_shared"));
        assertEquals(R.string.child_session_ended_message,
                ChildTerminalPolicy.message("child_session_unavailable"));
        assertEquals(R.string.child_session_ended_message, ChildTerminalPolicy.message(null));
    }
}
