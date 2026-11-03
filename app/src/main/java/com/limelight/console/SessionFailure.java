package com.limelight.console;

import com.limelight.R;
import com.limelight.diagnostics.MoonWakerDiagnostics;

import java.io.IOException;

/**
 * One stable reason code per launch, reuse or close path that ends on a session-failure
 * screen. The code is shown on that screen and recorded with its correlation, so a report
 * from the TV names exactly one path.
 */
public enum SessionFailure {
    // SessionOrchestrator.closePath: the host is unknown, before any path is chosen or asked.
    CLOSE_HOST_UNAVAILABLE("close.host_unavailable",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // path=retained-live: the live retained transport reported an unsuccessful termination.
    CLOSE_RETAINED_NOT_CLOSED("close.retained_not_closed",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // path=retained-live or reconnect/direct: the stream closed, but an unverified game may
    // still run on the host; the replacement must not launch.
    CLOSE_GAME_LEFT("close.game_left",
            R.string.applist_quit_fail, R.string.console_terminate_session_game_left),
    // path=retained-live: the coordinator refused to start the termination.
    CLOSE_RETAINED_TERMINATION_UNAVAILABLE("close.retained_termination_unavailable",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    CLOSE_RETAINED_INTERRUPTED("close.retained_interrupted",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // path=retained-live: no termination result within 60 s.
    CLOSE_RETAINED_TIMEOUT("close.retained_timeout",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // path=reconnect-fallback: the retained session was not awaiting reconnect any more.
    CLOSE_RETAINED_CHANGED("close.retained_changed_before_close",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // path=reconnect-fallback: the host-side control-plane termination did not confirm.
    CLOSE_RECONNECT_FALLBACK_NOT_CLOSED("close.reconnect_fallback_not_closed",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // path=direct-nonretained: the host-side control-plane termination did not confirm.
    CLOSE_DIRECT_NOT_CLOSED("close.direct_not_closed",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // path=reconnect or direct: the host's stream belongs to another TV, profile or client;
    // reported, never closed, and an uncertain session's recovery never hard-resets under it.
    CLOSE_OTHER_STREAM("close.other_stream",
            R.string.applist_quit_fail, R.string.console_terminate_session_other_stream),
    // Any other exception escaping the switch or close effect.
    CLOSE_UNEXPECTED_ERROR("close.unexpected_error",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // SessionOrchestrator: close returned CLOSED, but local facts still show another session
    // (live transport, suspended or reconnect record). A missing host observation is not this.
    CLOSE_SNAPSHOT_NOT_IDLE("close.snapshot_not_idle_after_close",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),

    // Retained provider switch (Effects.switchRetained -> switchRetainedProviderGame).
    SWITCH_NOT_ELIGIBLE("switch.not_eligible",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    SWITCH_REFUSED("switch.refused_not_eligible",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // Quick Resume: the host refused to freeze (or swap out) the switched game; it plays on.
    // The replacement follows with the ordinary stop (SessionOrchestrator.switchRefusal).
    SWITCH_FREEZE_REFUSED("switch.freeze_refused",
            R.string.applist_quit_fail, R.string.overlay_menu_freeze_failed),
    SWITCH_INTERRUPTED("switch.interrupted",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    SWITCH_TIMEOUT("switch.timeout",
            R.string.applist_quit_fail, R.string.console_previous_session_close_failed),
    // The host could not confirm that the game the switch leaves ended: it may still run.
    SWITCH_STOP_UNCONFIRMED("switch.stop_unconfirmed",
            R.string.applist_quit_fail, R.string.control_previous_game_unconfirmed),
    // Its screen shows only a switch meant to keep the running game (the same game, a freeze
    // or a swap): no session close was attempted.
    SWITCH_FAILED("switch.failed",
            R.string.playnite_launch_unavailable, R.string.console_switch_failed),
    // The stream still ran the switch of an earlier Play: this one changed nothing and closes
    // nothing; its Retry asks again once that switch ended.
    SWITCH_IN_PROGRESS("switch.in_progress",
            R.string.playnite_launch_unavailable, R.string.console_switch_failed),
    // The host refused the child's own play (time, schedule, sharing) before anything stopped:
    // the stream keeps its game and the child's message shows.
    SWITCH_CHILD_REFUSED("switch.child_refused",
            R.string.playnite_launch_unavailable, R.string.console_child_launch_unavailable),
    // A switch would attach to a game the host runs for another actor: never taken over (a
    // replacement the user confirms closes it instead).
    SESSION_HELD_BY_OTHER_ACTOR("session.held_by_other_actor",
            R.string.playnite_launch_unavailable, R.string.console_child_launch_session_in_use),

    // SessionOrchestrator: the canonical snapshot could not be trusted for this target.
    UNCERTAIN_RECOVERY_REQUIRED("uncertain.recovery_required",
            R.string.console_session_uncertain_title, R.string.console_session_uncertain_details),
    UNCERTAIN_REFRESH_FAILED("uncertain.refresh_failed",
            R.string.console_session_uncertain_title, R.string.console_session_uncertain_details),
    UNCERTAIN_AFTER_REFRESH("uncertain.still_uncertain_after_refresh",
            R.string.console_session_uncertain_title, R.string.console_session_uncertain_details),
    // SessionOrchestrator recovery: the host refused or never answered the automatic hard reset
    // of an uncertain session; the failure screen's Retry is the user's.
    RECOVERY_HARD_RESET_FAILED("recovery.hard_reset_failed",
            R.string.applist_quit_fail, R.string.console_session_recovery_failed_details),

    // SessionOrchestrator awaitHost: the woken host never answered within the wake bound.
    HOST_WAKE_TIMEOUT("host.wake_timeout",
            R.string.console_host_not_ready, R.string.console_host_timeout),
    // SessionOrchestrator awaitHost: the host answered, its Gateway did not within its bound.
    HOST_GATEWAY_TIMEOUT("host.gateway_timeout",
            R.string.console_host_not_ready, R.string.transition_gateway_unavailable),

    ORCHESTRATION_PASS_LIMIT("orchestration.pass_limit",
            R.string.console_host_not_ready, R.string.console_host_timeout),
    ORCHESTRATION_EXECUTOR_REJECTED("orchestration.executor_rejected",
            R.string.console_host_not_ready, R.string.console_host_timeout),

    // ConsoleActivity attachPresentationForSwitch: nothing was closed on these paths.
    ATTACH_RESUME_INTENT_MISSING("attach.resume_intent_missing",
            R.string.console_presentation_launch_failed_title,
            R.string.console_presentation_launch_failed_details),
    ATTACH_START_ACTIVITY_FAILED("attach.start_activity_failed",
            R.string.console_presentation_launch_failed_title,
            R.string.console_presentation_launch_failed_details),

    // ConsoleActivity reconnectSavedSessionForActor, same actor: the saved session could not be
    // resumed; detail names the refusal (no record, record or retained mismatch, no intent).
    RECONNECT_SAVED_SESSION_UNAVAILABLE("reconnect.saved_session_unavailable",
            R.string.console_presentation_launch_failed_title,
            R.string.console_reconnect_saved_session_unavailable),
    // ConsoleActivity reconnectSavedSessionForActor, actor handoff: failed after compensation.
    RECONNECT_ACTOR_HANDOFF_FAILED("reconnect.actor_handoff_failed",
            R.string.console_presentation_launch_failed_title,
            R.string.console_reconnect_actor_handoff_failed),

    // Game stream start: a Gateway control operation (acquire, reconcile or complete) answered HTTP 409.
    CONTROL_STREAM_CONFLICT("control.managed_stream_conflict",
            R.string.conn_error_title, R.string.control_stream_conflict),
    CONTROL_PREVIOUS_GAME_UNCONFIRMED("control.previous_game_unconfirmed",
            R.string.conn_error_title, R.string.control_previous_game_unconfirmed),
    CONTROL_STATE_CHANGED("control.state_changed",
            R.string.conn_error_title, R.string.control_state_changed),
    // A control operation on the host is uncertain (its holder went silent, abandoned it or
    // could not confirm its effect) and the host could not end it by itself; the stream error
    // screen offers the host panel's hard close next to this message. An operation that is
    // simply running (operation_in_progress) is an ordinary "try again in a moment".
    CONTROL_OPERATION_PENDING("control.operation_pending",
            R.string.conn_error_title, R.string.control_operation_pending),
    // A child start would carry a game the host did not grant this child session (e.g. the
    // parent's game on the shared profile): asking again changes nothing, a parent must act.
    CONTROL_CHILD_GAME_NOT_ENTITLED("control.child_game_not_entitled",
            R.string.conn_error_title, R.string.control_child_game_not_entitled),
    CONTROL_REJECTED("control.rejected",
            R.string.conn_error_title, R.string.control_rejected);

    public final String code;
    public final int title;
    public final int details;

    SessionFailure(String code, int title, int details) {
        this.code = code;
        this.title = title;
        this.details = details;
    }

    /** The stream error screen offers the host panel's hard close next to this failure. */
    public boolean offersHardTerminate() {
        return this == CONTROL_OPERATION_PENDING;
    }

    /** Maps the Gateway reason of a refused control operation to its failure. */
    public static SessionFailure fromControlRejection(String reason) {
        String value = reason == null ? "" : reason;
        switch (value) {
            // Another stream holds the host (another owner's, or an app MoonWaker did not
            // start): close it, then try again.
            case "managed_stream_conflict":
            case "stream_owner_mismatch":
            case "unmanaged_stream": return CONTROL_STREAM_CONFLICT;
            case "child_game_not_entitled": return CONTROL_CHILD_GAME_NOT_ENTITLED;
            // The previous game or child session is still ending: wait, then try again.
            case "retained_process_unconfirmed":
            case "retained_owner_unconfirmed":
            case "child_cleanup_required":
            case "child_cleanup_in_progress": return CONTROL_PREVIOUS_GAME_UNCONFIRMED;
            // What this start was bound to changed on the host: try again.
            case "stale_operation":
            case "stale_session":
            case "operation_id_reused":
            case "stream_generation_superseded":
            case "managed_stream_unavailable":
            case "child_session_unmanaged": return CONTROL_STATE_CHANGED;
            case "terminal_result_timeout":
            case "terminal_owner_required":
            case "reconcile_required":
            case "effect_result_uncertain":
            case "start_abandoned":
            case "reauthorization_fence_unconfirmed":
            case "gateway_restarted_before_terminal_result": return CONTROL_OPERATION_PENDING;
            default: return CONTROL_REJECTED;
        }
    }

    /** Carries the exact close path to the orchestrator instead of message text. */
    static final class Raised extends IOException {
        final SessionFailure reason;
        final String detail;
        /** The class of the unexpected exception this wraps; empty for a classified path. */
        final String errorType;

        Raised(SessionFailure reason, String detail) {
            this(reason, detail, "");
        }

        private Raised(SessionFailure reason, String detail, String errorType) {
            super(reason.code);
            this.reason = reason;
            this.detail = detail == null ? "" : detail;
            this.errorType = errorType;
        }
    }

    Raised raise() { return new Raised(this, ""); }

    /** Any other exception escaping a switch or close effect, keeping its class. */
    static Raised unexpected(Exception error) {
        return new Raised(CLOSE_UNEXPECTED_ERROR, "", error.getClass().getName());
    }

    /** The stream's refusal of a switch whose actor does not hold what the host runs. */
    public static final String SWITCH_OTHER_ACTOR_REASON = "retained_switch_other_actor";
    public static final String SWITCH_FREEZE_REFUSED_REASON = "retained_switch_freeze_refused";
    /** The stream refused a handed-off switch while it still runs an earlier one. */
    public static final String SWITCH_IN_PROGRESS_REASON = "retained_switch_in_progress";

    /** Classifies the failure reason reported by the attached stream for a retained switch. */
    static Raised switchFailed(String reason) {
        return new Raised(ofSwitchFailure(reason), reason == null ? "" : reason);
    }

    /** As {@link #switchFailed(String)}; a child's own host refusal (time, schedule, sharing) is its own. */
    static Raised switchFailed(boolean child, String reason) {
        return child && ConsoleActivity.childRefusalMessage(reason) != 0
                ? new Raised(SWITCH_CHILD_REFUSED, reason) : switchFailed(reason);
    }

    public static SessionFailure ofSwitchFailure(String reason) {
        String value = reason == null ? "" : reason;
        return "retained_switch_not_eligible".equals(value) ? SWITCH_REFUSED
                : SWITCH_FREEZE_REFUSED_REASON.equals(value) ? SWITCH_FREEZE_REFUSED
                : SWITCH_OTHER_ACTOR_REASON.equals(value) ? SESSION_HELD_BY_OTHER_ACTOR
                : SWITCH_IN_PROGRESS_REASON.equals(value) ? SWITCH_IN_PROGRESS
                : value.contains("game_stop_unconfirmed") ? SWITCH_STOP_UNCONFIRMED
                : SWITCH_FAILED;
    }

    /**
     * Records this failure with its correlation and the session state at that moment: the one
     * entry of the path that ends on its screen.
     */
    void record(long orchestrationId, String hostId, String profileId,
                SessionSnapshot snapshot, String detail, String errorType) {
        MoonWakerDiagnostics.record("WARN", "android.session", "session.failed",
                "reason", code, "orchestration_id", orchestrationId,
                "host_id", hostId, "profile_id", profileId,
                "state", snapshot.state.name(),
                "observation_state", snapshot.observationState.name(),
                "known_live", snapshot.retainedTransport,
                "app_id", snapshot.hostGameAppId, "game_id", snapshot.playniteGameId,
                "detail", safeDetail(detail), "error_type", errorType);
    }

    /** Keeps only a lowercase reason token; free text could carry identifiers or secrets. */
    public static String safeDetail(String detail) {
        return detail != null && detail.matches("[a-z_]{1,64}") ? detail : "";
    }
}
