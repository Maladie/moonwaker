package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

public class CaptureListTest {
    private static final TimeZone WARSAW = TimeZone.getTimeZone("Europe/Warsaw");
    /** 2026-10-25 21:50 in Warsaw (CET after the night's change). */
    private static final long NOW = 1_792_961_400_000L;
    private static final long HOUR = 3_600_000L;

    private static JSONObject item(String id, String kind, long created) throws Exception {
        return new JSONObject().put("id", id).put("kind", kind).put("game_name", " Hollow Knight ")
                .put("created_unix_ms", created).put("duration_ms", 30_000)
                .put("width", 2560).put("height", 1440).put("bytes", 1234);
    }

    private static CaptureList list(JSONObject... items) throws Exception {
        return CaptureList.parse(new JSONObject().put("captures", new JSONArray(Arrays.asList(items)))
                .put("folder", "C:\\Users\\Basia\\Videos\\MoonWaker\\Basia"));
    }

    @Test public void parsesTheHostListNewestFirstAndDropsWhatIsNotACapture() throws Exception {
        CaptureList list = list(
                item("Hollow Knight 2026-10-25 18-05-00.mp4", "clip", NOW - 4 * HOUR),
                item("Hollow Knight 2026-10-25 21-47-00.png", "screenshot", NOW - 3 * 60_000L),
                item("..\\evil.png", "screenshot", NOW),
                item("a.png", "clip", NOW),
                item(".hidden.png", "screenshot", NOW),
                item("notes.txt", "screenshot", NOW));
        assertEquals(2, list.captures.size());
        CaptureList.Capture shot = list.captures.get(0);
        assertEquals("Hollow Knight 2026-10-25 21-47-00.png", shot.id);
        assertFalse(shot.clip);
        assertEquals("Hollow Knight", shot.gameName);
        assertEquals(2560, shot.width);
        assertTrue(list.captures.get(1).clip);
        assertEquals("C:\\Users\\Basia\\Videos\\MoonWaker\\Basia", list.folder);
        assertEquals(1, list.indexOf("Hollow Knight 2026-10-25 18-05-00.mp4"));
        assertEquals(1, list.without(shot.id).captures.size());
    }

    @Test public void groupsIntoTodayYesterdayAndEarlierDays() throws Exception {
        CaptureList list = list(item("a.png", "screenshot", NOW - HOUR),
                item("b.mp4", "clip", NOW - 21 * HOUR),
                item("c.png", "screenshot", NOW - 23 * HOUR),
                item("d.png", "screenshot", NOW - 50 * HOUR),
                item("e.png", "screenshot", NOW - 80 * HOUR));
        List<CaptureList.Section> sections = list.sections(NOW, WARSAW);
        assertEquals(4, sections.size());
        assertEquals(CaptureList.Day.TODAY, sections.get(0).day);
        assertEquals(2, sections.get(0).captures.size());   // 20:50 and 01:50 today (still CEST)
        assertEquals(CaptureList.Day.YESTERDAY, sections.get(1).day);
        assertEquals("c.png", sections.get(1).captures.get(0).id);
        assertEquals(CaptureList.Day.EARLIER, sections.get(2).day);
        assertEquals(CaptureList.Day.EARLIER, sections.get(3).day);
        assertEquals(CaptureList.Day.YESTERDAY, CaptureList.day(NOW - 23 * HOUR, NOW, WARSAW));
    }

    @Test public void clipLengthReadsLikeTheBadge() {
        assertEquals("0:30", CaptureList.duration(30_000));
        assertEquals("0:18", CaptureList.duration(17_600));
        assertEquals("1:05", CaptureList.duration(65_000));
        assertEquals("0:00", CaptureList.duration(-5));
    }

    @Test public void anIdIsAFileNameNeverAPath() {
        assertEquals("a b.mp4", CaptureList.requireId("a b.mp4"));
        for (String bad : new String[]{"", "a/b.png", "a\\b.png", "C:x.png", "a.jpg", ".png"}) {
            try {
                CaptureList.requireId(bad);
                fail(bad);
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
        }
    }
}
