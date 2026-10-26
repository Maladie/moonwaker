package com.limelight.console;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * A profile's screenshots and clips on the PC (#51, {@code GET /api/v1/captures}): the host's
 * list, newest first, and its grouping into the gallery's day sections. Pure.
 */
final class CaptureList {
    /** The Gateway's capture id: a file name in the profile's folder, never a path. */
    private static final Pattern ID = Pattern.compile(
            "[^<>:\"/\\\\|?*\\x00-\\x1f.][^<>:\"/\\\\|?*\\x00-\\x1f]{0,200}\\.(?:png|mp4)");

    static final class Capture {
        final String id;
        final boolean clip;
        final String gameName;
        final long createdMs;
        final long durationMs;
        final int width;
        final int height;

        Capture(String id, String gameName, long createdMs, long durationMs, int width,
                int height) {
            this.id = id;
            this.clip = id.endsWith(".mp4");
            this.gameName = gameName;
            this.createdMs = createdMs;
            this.durationMs = durationMs;
            this.width = width;
            this.height = height;
        }
    }

    enum Day { TODAY, YESTERDAY, EARLIER }

    /** One day of the gallery: its captures newest first; {@code dayStartMs} names it. */
    static final class Section {
        final Day day;
        final long dayStartMs;
        final List<Capture> captures = new ArrayList<>();

        Section(Day day, long dayStartMs) {
            this.day = day;
            this.dayStartMs = dayStartMs;
        }
    }

    final List<Capture> captures;
    /** The profile's folder on the PC, shown under the title; "" when the host gave none. */
    final String folder;

    CaptureList(List<Capture> captures, String folder) {
        this.captures = Collections.unmodifiableList(captures);
        this.folder = folder;
    }

    static CaptureList parse(JSONObject response) {
        List<Capture> captures = new ArrayList<>();
        JSONArray values = response.optJSONArray("captures");
        for (int index = 0; values != null && index < values.length() && index < 200; index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            String id = value.optString("id", "");
            String kind = value.optString("kind", "");
            if (!isId(id) || !kind.equals(id.endsWith(".mp4") ? "clip" : "screenshot")) continue;
            captures.add(new Capture(id, value.optString("game_name", "").trim(),
                    Math.max(0L, value.optLong("created_unix_ms", 0L)),
                    Math.max(0L, value.optLong("duration_ms", 0L)),
                    Math.max(0, value.optInt("width", 0)),
                    Math.max(0, value.optInt("height", 0))));
        }
        captures.sort((a, b) -> Long.compare(b.createdMs, a.createdMs));
        return new CaptureList(captures, response.optString("folder", "").trim());
    }

    static boolean isId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    static String requireId(String id) {
        if (!isId(id)) throw new IllegalArgumentException("Invalid capture id");
        return id;
    }

    /** Today, yesterday, then one section per earlier day, in {@code zone}. */
    List<Section> sections(long nowMs, TimeZone zone) {
        long today = startOfDay(nowMs, zone, 0);
        long yesterday = startOfDay(nowMs, zone, -1);
        List<Section> sections = new ArrayList<>();
        Section current = null;
        for (Capture capture : captures) {
            long start = capture.createdMs >= today ? today : startOfDay(capture.createdMs, zone, 0);
            if (current == null || current.dayStartMs != start) {
                current = new Section(start == today ? Day.TODAY
                        : start == yesterday ? Day.YESTERDAY : Day.EARLIER, start);
                sections.add(current);
            }
            current.captures.add(capture);
        }
        return sections;
    }

    /** The day {@code createdMs} falls on, seen at {@code nowMs}. */
    static Day day(long createdMs, long nowMs, TimeZone zone) {
        if (createdMs >= startOfDay(nowMs, zone, 0)) return Day.TODAY;
        return createdMs >= startOfDay(nowMs, zone, -1) ? Day.YESTERDAY : Day.EARLIER;
    }

    /** A clip's length as the badge shows it: "0:30", "1:05". */
    static String duration(long durationMs) {
        long seconds = Math.max(0L, Math.round(durationMs / 1000d));
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    int indexOf(String id) {
        for (int index = 0; index < captures.size(); index++) {
            if (captures.get(index).id.equals(id)) return index;
        }
        return -1;
    }

    /** The list without {@code id}, after the host deleted it. */
    CaptureList without(String id) {
        List<Capture> rest = new ArrayList<>(captures);
        rest.removeIf(capture -> capture.id.equals(id));
        return new CaptureList(rest, folder);
    }

    private static long startOfDay(long timeMs, TimeZone zone, int dayOffset) {
        Calendar calendar = Calendar.getInstance(zone, Locale.ROOT);
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, dayOffset);
        return calendar.getTimeInMillis();
    }
}
