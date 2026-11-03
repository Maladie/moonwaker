package com.limelight.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SuspendedSessionStoreTest {
    private static SuspendedSessionStore.Session session(String suspendId) {
        return new SuspendedSessionStore.Session(suspendId, "host", "parent", 42,
                "game", "Title", "", 100L, "parent");
    }

    @Test public void onlyExactResumeIdentityCompletesTheResume() {
        for (Object[] wrong : new Object[][] {{"suspend-b", "host", 42, "game"},
                {"suspend-a", "other", 42, "game"}, {"suspend-a", "host", 7, "game"},
                {"suspend-a", "host", 42, "other"}}) {
            MemoryPreferences prefs = saved(session("suspend-a"));
            assertFalse(java.util.Arrays.toString(wrong),
                    SuspendedSessionStore.markResumedForExecutionIfMatches(prefs,
                            (String) wrong[0], (String) wrong[1], "parent", (int) wrong[2],
                            (String) wrong[3], "stream-1"));
            // A failed attempt leaves the same record resumable.
            assertEquals(0L, SuspendedSessionStore.load(prefs.getAll(), "host", "parent")
                    .resumedAt);
        }
        MemoryPreferences prefs = saved(session("suspend-a"));
        assertTrue(SuspendedSessionStore.markResumedForExecutionIfMatches(prefs, "suspend-a",
                "HOST", "parent", 42, "GAME", "stream-1"));
        assertEquals("stream-1", SuspendedSessionStore.load(prefs.getAll(), "host", "parent")
                .resumedStreamSessionId);
    }

    private static MemoryPreferences saved(SuspendedSessionStore.Session session) {
        MemoryPreferences prefs = new MemoryPreferences();
        SuspendedSessionStore.save(prefs, session);
        return prefs;
    }

    @Test public void oldActivityCannotMatchNewerSuspendRecord() {
        SuspendedSessionStore.Session replacement = session("suspend-b");
        assertFalse(SuspendedSessionStore.matches(replacement, "suspend-a"));
        assertTrue(SuspendedSessionStore.matches(replacement, "suspend-b"));
    }

    @Test public void resumeIdentityCannotCrossProfilesOnTheSameHost() {
        MemoryPreferences prefs = saved(new SuspendedSessionStore.Session(
                "suspend-a", "host", "Basia", 42, "game", "Title", "", 100L, "Basia"));

        assertFalse(SuspendedSessionStore.markResumedForExecutionIfMatches(prefs, "suspend-a",
                "host", "Gry", 42, "game", "stream-1"));
        assertTrue(SuspendedSessionStore.markResumedForExecutionIfMatches(prefs, "suspend-a",
                "host", "Basia", 42, "game", "stream-1"));
    }

    /** A-11: a record without its execution profile is not a record (no actor fallback). */
    @Test public void recordWithoutItsExecutionProfileIsNotARecord() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit().putString("host.profile.parent", "{\"profile_id\":\"parent\","
                + "\"suspend_id\":\"suspend-a\",\"host_id\":\"host\","
                + "\"sunshine_app_id\":42,\"suspended_at\":100}").apply();
        assertNull(SuspendedSessionStore.load(prefs.getAll(), "host", "parent"));
    }

    @Test public void recordWithoutItsOwnSuspendIdIsNotARecord() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit().putString("host.profile.parent", "{\"profile_id\":\"parent\","
                + "\"host_id\":\"host\",\"sunshine_app_id\":42,\"suspended_at\":100}").apply();
        assertNull(SuspendedSessionStore.load(prefs.getAll(), "host", "parent"));
        assertNull(SuspendedSessionStore.loadForExecutionProfile(prefs.getAll(), "host",
                "parent"));
    }

    @Test public void modelSavesEndsAndRestoresRecordsAcrossProcessDeath() {
        SessionModelDriver driver = new SessionModelDriver();
        driver.post(new SessionEvent.SuspendedSaved(session("suspend-a")));
        assertEquals("suspend-a", driver.snapshot().records
                .suspendedForExecution("host", "parent").suspendId);

        // A new process restores the record from the store, not from a live connection.
        SessionModel restored = new SessionModel(Runnable::run, Runnable::run, () -> false,
                () -> 0L, (level, component, event, fields) -> true,
                driver.resume, driver.suspended, driver.sleep, 0L);
        assertEquals("suspend-a", restored.records()
                .suspendedForExecution("host", "parent").suspendId);
        assertEquals(SessionModel.State.NONE, restored.state());

        assertTrue(driver.call(SessionEvent.SuspendedEnded.forExecution(
                "host", "parent", "suspend-a")));
        assertNull(driver.snapshot().records.suspendedForExecution("host", "parent"));
        assertTrue(driver.snapshot().records.recentlyEndedForExecution("host", "parent"));
    }

    @Test public void hostSelectionRequestIsConsumedOnce() {
        SessionModelDriver driver = new SessionModelDriver();
        driver.post(new SessionEvent.HostSelectionRequested("HOST"));
        assertEquals("host", driver.call(new SessionEvent.HostSelectionConsumed()));
        assertEquals("", driver.call(new SessionEvent.HostSelectionConsumed()));
    }

    @Test public void hostSleepRecordIsIdentifiedByItsRequestTime() {
        SessionModelDriver driver = new SessionModelDriver();
        driver.post(new SessionEvent.HostPowerRequested("host",
                HostPowerStateStore.Kind.SLEEP, "", 5L));
        assertEquals(5L, driver.snapshot().records.hostPower("host").requestedAt);
        assertFalse(driver.call(new SessionEvent.HostPowerCleared("host", 4L)));
        assertTrue(driver.call(new SessionEvent.HostPowerCleared("host", 5L)));
        assertNull(driver.snapshot().records.hostPower("host"));
    }
}
