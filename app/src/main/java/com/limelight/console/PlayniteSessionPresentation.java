package com.limelight.console;

import java.util.ArrayList;
import java.util.List;

/** Pure game-tile projection over one resolved session snapshot. */
final class PlayniteSessionPresentation {
    enum State {
        READY,
        RESUME_ACTIVE,
        RESUME_SUSPENDED,
        RECOVERY_REQUIRED,
        /** The host's Quick Resume slot holds this game frozen: Play thaws it. */
        FROZEN
    }

    static final class Projection {
        final String resumeGameId;
        final String suspendedGameId;
        /** The game the host holds frozen for this actor; "" when none. */
        final String frozenGameId;
        final SessionSnapshot snapshot;
        private final String selectedGameId;

        private Projection(SessionSnapshot snapshot, String selectedGameId) {
            this.snapshot = snapshot;
            this.selectedGameId = selectedGameId;
            this.resumeGameId = snapshot.state == SessionSnapshot.State.ACTIVE
                    || snapshot.state == SessionSnapshot.State.RECONNECT_REQUIRED
                    ? selectedGameId : "";
            this.suspendedGameId = snapshot.isSuspended()
                    ? selectedGameId : "";
            this.frozenGameId = snapshot.frozenGameId();
        }

        State stateFor(String gameId) {
            // The host's frozen fact is newer than a game a status line still holds running.
            if (!frozenGameId.isEmpty()
                    && frozenGameId.equals(SessionSnapshot.normalize(gameId))) {
                return State.FROZEN;
            }
            if (selectedGameId.isEmpty()
                    || !selectedGameId.equals(SessionSnapshot.normalize(gameId))) {
                return State.READY;
            }
            if (snapshot.observationState
                    == SessionSnapshot.ObservationState.RECOVERY_REQUIRED) {
                return State.RECOVERY_REQUIRED;
            }
            if (snapshot.isSuspended()) {
                return State.RESUME_SUSPENDED;
            }
            if (snapshot.state == SessionSnapshot.State.ACTIVE
                    || snapshot.state == SessionSnapshot.State.RECONNECT_REQUIRED) {
                return State.RESUME_ACTIVE;
            }
            return State.READY;
        }

        String signature() {
            return resumeGameId + "|" + suspendedGameId + "|" + frozenGameId;
        }
    }

    static Projection project(SessionSnapshot snapshot,
                              List<PlayniteDashboardItem> items,
                              String previouslySelectedGameId) {
        String gameId = shownGameId(snapshot);
        if (!snapshot.isResumeAvailable() && gameId.isEmpty()) {
            return new Projection(snapshot, "");
        }
        String selected = exactPlayniteMatch(gameId, items);
        if (!gameId.isEmpty()) {
            return new Projection(snapshot, selected);
        }
        List<PlayniteDashboardItem> appMatches = new ArrayList<>();
        for (PlayniteDashboardItem item : items) {
            if (snapshot.hostGameAppId != 0 && item.sunshineAppId != null
                    && item.sunshineAppId == snapshot.hostGameAppId) {
                appMatches.add(item);
            }
        }
        if (appMatches.size() == 1) {
            selected = SessionSnapshot.normalize(appMatches.get(0).stableId());
        }
        return new Projection(snapshot, selected);
    }

    /**
     * The session's game as its tiles show it. Between host observations the snapshot's game
     * goes empty (evidence stale or pending) while the session itself stays: the tile then
     * shows the game last observed running, as the host status line does, instead of flipping
     * from Resume to Ready and back on every poll. Presentation only, like
     * {@link SessionSnapshot.Display}.
     */
    static String shownGameId(SessionSnapshot snapshot) {
        if (!snapshot.playniteGameId.isEmpty()) return snapshot.playniteGameId;
        boolean session = snapshot.state == SessionSnapshot.State.ACTIVE
                || snapshot.state == SessionSnapshot.State.RECONNECT_REQUIRED
                || snapshot.isSuspended();
        return session ? snapshot.display.runningGameId : "";
    }

    private static String exactPlayniteMatch(String gameId,
                                             List<PlayniteDashboardItem> items) {
        if (gameId.isEmpty()) return "";
        for (PlayniteDashboardItem item : items) {
            if (gameId.equals(
                    SessionSnapshot.normalize(item.stableId()))) {
                return SessionSnapshot.normalize(item.stableId());
            }
        }
        return "";
    }

    private PlayniteSessionPresentation() { }
}
