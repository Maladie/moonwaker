package com.limelight.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** When the Watch Next tile shows, what it shows, and what its deep link does after the gate. */
public class WatchNextTileTest {
    private final SessionModelDriver driver = new SessionModelDriver();
    private final MemoryPreferences consent = new MemoryPreferences();
    private final SessionModel.Controller owner = new SessionModel.Controller() {
        private final long generation = SessionModelDriver.nextTransportGeneration();
        @Override public boolean isRetainedTransportLive() { return true; }
        @Override public long transportGeneration() { return generation; }
        @Override public boolean hasAttachedPresentation() { return false; }
        @Override public SessionModel.ParkOutcome parkRetainedTransport() {
            return SessionModel.ParkOutcome.PARKED;
        }
        @Override public void disconnectRetainedStream(
                SessionModel.TerminationCallback completion) { }
        @Override public void terminateRetainedSession(
                String gameId, SessionModel.TerminationCallback completion) { }
        @Override public void switchGame(SessionModel.SwitchRequest request,
                                         SessionModel.SwitchCallback completion) { }
    };

    private void record(String sessionId, String profileId, String gameId, boolean neutral) {
        driver.resume.values.clear();
        driver.resume.values.put("pending", true);
        driver.resume.values.put("streamSessionId", sessionId);
        driver.resume.values.put("pcUuid", "host");
        driver.resume.values.put("profileId", profileId);
        driver.resume.values.put("transitionGameId", gameId);
        driver.resume.values.put("neutralStreamTarget", neutral);
        driver.resume.values.put("appName", "Hades");
        driver.resume.values.put("pcName", "Gaming PC");
        driver.resume.values.put("loadingArtwork", "/cache/" + profileId + "/hades.jpg");
        driver.refreshRecords();
    }

    private WatchNextTile tile() {
        return WatchNextTile.of(driver.snapshot(),
                (host, profile) -> WatchNextTile.consented(consent, host, profile));
    }

    private void live(String profileId, String gameId) {
        driver.enterHome(owner, "s1", "host", profileId, 7, gameId);
        record("s1", profileId, gameId, gameId.isEmpty());
    }

    @Test public void liveGameOfAConsentingProfileShowsItsOwnTitleAndArtwork() {
        WatchNextTile.setConsent(consent, "HOST", "parent", true);
        live("parent", "hades");

        WatchNextTile tile = tile();

        assertNotNull(tile);
        assertEquals("s1", tile.streamSessionId);
        assertEquals("parent", tile.profileId);
        assertEquals("Hades", tile.title);
        assertEquals("Gaming PC", tile.hostName);
        assertEquals("/cache/parent/hades.jpg", tile.artworkPath);
    }

    @Test public void desktopStreamIsTheDesktopTileWithoutGameArtwork() {
        WatchNextTile.setConsent(consent, "host", "parent", true);
        live("parent", "");

        WatchNextTile tile = tile();

        assertEquals("", tile.title);
        assertEquals("", tile.artworkPath);
    }

    @Test public void noTileWithoutTheOwningProfilesConsent() {
        // The parent's consent says nothing about the child's own stream, and the reverse.
        WatchNextTile.setConsent(consent, "host", "parent", true);
        live("kid", "hades");
        assertNull(tile());

        WatchNextTile.setConsent(consent, "host", "parent", false);
        WatchNextTile.setConsent(consent, "host", "kid", true);
        assertNotNull(tile());
        assertEquals("/cache/kid/hades.jpg", tile().artworkPath);
    }

    @Test public void consentTurnedOffRemovesTheTile() {
        WatchNextTile.setConsent(consent, "host", "parent", true);
        live("parent", "hades");
        assertNotNull(tile());

        WatchNextTile.setConsent(consent, "host", "parent", false);

        assertNull(tile());
    }

    @Test public void parkedStreamKeepsTheTile() {
        WatchNextTile.setConsent(consent, "host", "parent", true);
        live("parent", "hades");
        assertTrue(driver.parkForBackground("s1"));
        driver.markParked("s1");

        assertEquals(SessionModel.State.PARKED_LIVE, driver.state());
        assertNotNull(tile());
    }

    @Test public void noTileWithoutALiveStream() {
        WatchNextTile.setConsent(consent, "host", "parent", true);
        // Process start: only the reconnect record survives, which is not a live connection.
        record("s1", "parent", "hades", false);
        assertEquals(SessionModel.State.NONE, driver.state());
        assertNull(tile());

        live("parent", "hades");
        driver.markReconnectRequired("s1");
        assertNull(tile());
    }

    @Test public void closedStreamRemovesTheTile() {
        WatchNextTile.setConsent(consent, "host", "parent", true);
        live("parent", "hades");
        assertNotNull(tile());

        driver.closedIntentionally(owner, "s1");

        assertNull(tile());
    }

    @Test public void recordOfAnotherStreamProfileOrGameEarnsNoTile() {
        WatchNextTile.setConsent(consent, "host", "parent", true);
        WatchNextTile.setConsent(consent, "host", "kid", true);
        driver.enterHome(owner, "s1", "host", "parent", 7, "hades");
        record("s0", "parent", "hades", false);
        assertNull(tile());
        record("s1", "kid", "hades", false);
        assertNull(tile());
        record("s1", "parent", "celeste", false);
        assertNull(tile());
    }

    @Test public void aChangedGameIsANewTile() {
        WatchNextTile.setConsent(consent, "host", "parent", true);
        live("parent", "hades");
        String before = tile().key();
        live("parent", "");

        assertNotEquals(before, tile().key());
    }

    @Test public void deepLinkResumesOnlyTheSameLiveStreamForItsOwnProfile() {
        live("parent", "hades");
        SessionModel.Snapshot s = driver.snapshot();

        assertEquals(WatchNextTile.Entry.RESUME,
                WatchNextTile.entry(s, true, "s1", "HOST", "parent"));
        assertEquals(WatchNextTile.Entry.OTHER_PROFILE,
                WatchNextTile.entry(s, true, "s1", "host", "kid"));
        assertEquals(WatchNextTile.Entry.OTHER_PROFILE,
                WatchNextTile.entry(s, true, "s1", "other-host", "parent"));
        // A newer stream is not the tile's: the user lands in the library with a notice.
        assertEquals(WatchNextTile.Entry.ENDED,
                WatchNextTile.entry(s, true, "s0", "host", "parent"));
        // The model holds the stream but it cannot be shown at once.
        assertEquals(WatchNextTile.Entry.ENDED,
                WatchNextTile.entry(s, false, "s1", "host", "parent"));
    }

    @Test public void deepLinkAfterTheStreamEndedReportsIt() {
        live("parent", "hades");
        driver.closedIntentionally(owner, "s1");

        assertEquals(WatchNextTile.Entry.ENDED,
                WatchNextTile.entry(driver.snapshot(), false, "s1", "host", "parent"));
    }
}
