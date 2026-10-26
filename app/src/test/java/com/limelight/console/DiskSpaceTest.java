package com.limelight.console;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiskSpaceTest {
    @Test public void parsesDrivesAndCandidatesDroppingInvalidRows() throws Exception {
        DiskSpace space = DiskSpace.parse(new JSONObject("{\"drives\":["
                + "{\"path\":\"C:\\\\\",\"free_bytes\":5,\"total_bytes\":9},{\"path\":\"\"},"
                + "\"junk\"],\"candidates\":[{\"game_id\":\"steam:1\",\"name\":\"Big\","
                + "\"size_bytes\":20,\"last_played\":\"\",\"drive\":\"c:\\\\\"},"
                + "{\"name\":\"no id\"}],"
                + "\"min_size_bytes\":15,\"idle_days\":60}"));
        assertEquals(15, space.minSizeBytes);
        assertEquals(60, space.idleDays);
        assertEquals(1, space.drives.size());
        assertEquals("C:\\", space.drives.get(0).path);
        assertEquals(9, space.drives.get(0).totalBytes);
        assertEquals(1, space.candidates.size());
        assertEquals("steam:1", space.candidates.get(0).gameId);
        assertEquals("", space.candidates.get(0).lastPlayed);
        assertEquals("", space.candidates.get(0).operation);
        assertTrue(DiskSpace.parse(new JSONObject("{}")).drives.isEmpty());
        assertEquals(5, space.driveOf(space.candidates.get(0)).freeBytes);
    }

    @Test public void lowSpaceUsageAndTheDriveAfterAnUninstall() {
        DiskSpace.Drive drive = new DiskSpace.Drive("D:\\", 9, 100);
        assertTrue(DiskSpace.low(drive));
        assertFalse(DiskSpace.low(10, 100));
        assertFalse(DiskSpace.low(0, 0));
        assertEquals(91, DiskSpace.usedPercent(drive));
        assertEquals(0, DiskSpace.usedPercent(new DiskSpace.Drive("E:\\", 0, 0)));
        assertEquals(59, DiskSpace.after(drive, 50).freeBytes);
        assertEquals(100, DiskSpace.after(drive, 500).freeBytes);
        assertEquals("D:", DiskSpace.driveLabel("D:\\"));
        assertEquals("C:", DiskSpace.driveLabel("c:\\"));
        assertEquals("\\\\nas\\games", DiskSpace.driveLabel("\\\\nas\\games"));
    }

    @Test public void runningUninstallComesFromTheHost() throws Exception {
        DiskSpace space = DiskSpace.parse(new JSONObject("{\"candidates\":[{\"game_id\":"
                + "\"epic:A\",\"size_bytes\":20,\"operation\":\"attention_required\"}]}"));
        assertEquals("attention_required", space.candidates.get(0).operation);
        assertEquals(null, space.driveOf(space.candidates.get(0)));
    }

    @Test public void rowKeyMatchesTheLowerCaseIdOfObservations() {
        assertEquals(DiskSpace.rowKey("epic:fortnite"), DiskSpace.rowKey("epic:Fortnite"));
        assertEquals("epic:fortnite", DiskSpace.rowKey(" epic:Fortnite "));
    }

    @Test public void warnsOnlyWhenBothNumbersAreKnownAndTheGameDoesNotFit() {
        assertTrue(DiskSpace.tooSmall(11, 10));
        assertFalse(DiskSpace.tooSmall(10, 10));
        assertFalse(DiskSpace.tooSmall(0, 10));
        assertFalse(DiskSpace.tooSmall(11, -1));
    }
}
