package com.limelight.console;

import com.limelight.BuildConfig;
import com.limelight.R;

/**
 * Host panel structure (UX_MENU_PL.md section 3.3): three main actions (Pulpit, Obudź or Uśpij,
 * Zamknij sesję; with an active session: Wróć do streamu, Zamknij sesję, Uśpij), a "Więcej"
 * group, and the destructive actions last. An adult profile also gets Uruchom ponownie and Wyłącz
 * after Uśpij and Windows Update under "Więcej". The caller resolves the facts from its stores and
 * performs the chosen action.
 */
final class ConsoleHostPanel {
    enum Action {
        RESUME(R.drawable.ic_console_play),
        DESKTOP(R.drawable.ic_console_monitor), WAKE(R.drawable.ic_console_sun),
        SLEEP(R.drawable.ic_console_moon), RESTART(R.drawable.ic_console_restart),
        SHUTDOWN(R.drawable.ic_console_power), CLOSE_SESSION(R.drawable.ic_console_close),
        WINDOWS_UPDATE(R.drawable.ic_console_download),
        TEST_CONNECTION(R.drawable.ic_console_pulse), REPORT_PROBLEM(R.drawable.ic_console_attention),
        CHANGE_HOST(R.drawable.ic_console_leave_host),
        HARD_TERMINATE(R.drawable.ic_console_power),
        UNPAIR(R.drawable.ic_console_unlink), REMOVE(R.drawable.ic_console_trash),
        FINISH_SETUP(R.drawable.ic_console_checklist), DISMISS_SETUP(R.drawable.ic_console_bell_off);

        /** Line icon of the row (section 4.7). */
        final int icon;

        Action(int icon) {
            this.icon = icon;
        }
    }

    static final class Facts {
        /** The host reports ONLINE; power then offers sleep instead of wake. */
        boolean reachable;
        /** Online and not closing a session. */
        boolean online;
        boolean paired;
        boolean gatewayAvailable;
        /** Wake is possible right now (not terminating, wakeable, not already waking). */
        boolean canWake;
        boolean streamRunning;
        /** Opened from this host's home screen, so going back to host selection makes sense. */
        boolean inHost;
        /** A fresh decision read reports a session the host holds: active or paused. */
        boolean sessionActive;
        /** That session can be resumed (the same check the resume action makes). */
        boolean resumeAvailable;
        /** A child profile is selected: it may sleep the host but not restart, shut down or
         *  check for updates. */
        boolean child;
        /** The first-run wizard has steps left "To finish" for this host. */
        boolean setupPending;
    }

    static ConsoleActionMenu<Action> menu(Facts facts) {
        ConsoleActionMenu<Action> menu = new ConsoleActionMenu<>();
        // An active session can be closed even while the streamer's app is unknown.
        if (facts.setupPending) {
            menu.add(Action.FINISH_SETUP, R.string.first_run_finish_setup, true)
                    .add(Action.DISMISS_SETUP, R.string.first_run_dismiss_setup, true);
        }
        boolean closeSession = facts.online && facts.paired
                && (facts.streamRunning || facts.sessionActive);
        if (facts.sessionActive) {
            menu.add(Action.RESUME, R.string.console_return_to_stream, facts.resumeAvailable)
                    .add(Action.CLOSE_SESSION, R.string.console_close_session, closeSession);
            power(menu, facts);
        } else {
            menu.add(Action.DESKTOP, R.string.console_launch_desktop, facts.online && facts.paired);
            power(menu, facts);
            menu.add(Action.CLOSE_SESSION, R.string.console_close_session, closeSession);
        }
        menu.group(R.string.console_section_more)
                .add(Action.TEST_CONNECTION, R.string.pcview_menu_test_network, true);
        // Reports go to the owner's Discord webhook: tester builds (-Ptesting) only.
        if (BuildConfig.TESTING) {
            menu.add(Action.REPORT_PROBLEM, R.string.problem_report_title, true);
        }
        if (facts.reachable && !facts.child) {
            menu.add(Action.WINDOWS_UPDATE, R.string.console_windows_update,
                    facts.online && facts.gatewayAvailable);
        }
        if (facts.inHost) menu.add(Action.CHANGE_HOST, R.string.console_change_host, true);
        menu.addDestructive(Action.HARD_TERMINATE, R.string.console_hard_terminate_session,
                facts.online && facts.paired && facts.gatewayAvailable)
                .addDestructive(Action.UNPAIR, R.string.console_unpair_host,
                        facts.online && facts.paired);
        // Host selection only; removal is local, so it works for an unreachable host too.
        if (!facts.inHost) menu.addDestructive(Action.REMOVE, R.string.console_remove_host, true);
        return menu.done();
    }

    private static void power(ConsoleActionMenu<Action> menu, Facts facts) {
        if (facts.reachable) {
            boolean power = facts.online && facts.gatewayAvailable;
            menu.add(Action.SLEEP, R.string.console_sleep_host, power);
            if (!facts.child) {
                menu.add(Action.RESTART, R.string.console_restart_host, power)
                        .add(Action.SHUTDOWN, R.string.console_shutdown_host, power);
            }
        } else {
            menu.add(Action.WAKE, R.string.console_wake_host, facts.canWake);
        }
    }

    private ConsoleHostPanel() { }
}
