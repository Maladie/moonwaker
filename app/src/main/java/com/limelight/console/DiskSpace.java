package com.limelight.console;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings › Disk space and the install picker's space check. The host owns the facts: drive
 * space, game sizes from launcher manifests, and which installed games it offers for removal
 * ({@code GET /api/v1/storage}, adults only).
 */
final class DiskSpace {
    static final class Drive {
        final String path;
        final long freeBytes;
        final long totalBytes;

        Drive(String path, long freeBytes, long totalBytes) {
            this.path = path;
            this.freeBytes = freeBytes;
            this.totalBytes = totalBytes;
        }
    }

    static final class Candidate {
        final String gameId;
        final String name;
        final long sizeBytes;
        /** The host's last-played time, "" when never played. */
        final String lastPlayed;
        /** The drive's path as {@link #drives} lists it, "" when unknown. */
        final String drive;
        /** The host journal's state of a running uninstall, "" when none runs. */
        final String operation;

        Candidate(String gameId, String name, long sizeBytes, String lastPlayed, String drive,
                  String operation) {
            this.gameId = gameId;
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.lastPlayed = lastPlayed;
            this.drive = drive;
            this.operation = operation;
        }
    }

    final List<Drive> drives;
    final List<Candidate> candidates;
    /** The host's thresholds for a candidate, shown in the page's description. */
    final long minSizeBytes;
    final int idleDays;

    private DiskSpace(List<Drive> drives, List<Candidate> candidates, long minSizeBytes,
                      int idleDays) {
        this.drives = drives;
        this.candidates = candidates;
        this.minSizeBytes = minSizeBytes;
        this.idleDays = idleDays;
    }

    static DiskSpace parse(JSONObject response) {
        List<Drive> drives = new ArrayList<>();
        JSONArray values = response.optJSONArray("drives");
        for (int index = 0; values != null && index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            String path = value == null ? "" : value.optString("path", "").trim();
            if (path.isEmpty()) continue;
            drives.add(new Drive(path, Math.max(0, value.optLong("free_bytes", 0)),
                    Math.max(0, value.optLong("total_bytes", 0))));
        }
        List<Candidate> candidates = new ArrayList<>();
        values = response.optJSONArray("candidates");
        for (int index = 0; values != null && index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            String gameId = value == null ? "" : value.optString("game_id", "").trim();
            if (gameId.isEmpty()) continue;
            candidates.add(new Candidate(gameId, value.optString("name", gameId),
                    Math.max(0, value.optLong("size_bytes", 0)),
                    value.optString("last_played", "").trim(),
                    value.optString("drive", "").trim(),
                    value.optString("operation", "").trim()));
        }
        return new DiskSpace(Collections.unmodifiableList(drives),
                Collections.unmodifiableList(candidates),
                Math.max(0, response.optLong("min_size_bytes", 0)),
                Math.max(0, response.optInt("idle_days", 0)));
    }

    /**
     * The page's key of a candidate's row: the host's id (an Epic AppName keeps its case) in
     * lower case, as operation observations name the game (GameOperationsController.Key).
     */
    static String rowKey(String gameId) {
        return gameId == null ? "" : gameId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** A candidate's drive, or null when the host named none it lists. */
    Drive driveOf(Candidate candidate) {
        for (Drive drive : drives) {
            if (!candidate.drive.isEmpty() && drive.path.equalsIgnoreCase(candidate.drive)) {
                return drive;
            }
        }
        return null;
    }

    /** "D:" for "d:\\" or "D:\\" (a drive root as the host lists it); other paths as they are. */
    static String driveLabel(String path) {
        return path.matches("[A-Za-z]:\\\\")
                ? path.substring(0, 2).toUpperCase(java.util.Locale.ROOT) : path;
    }

    /** Below this share of free space a drive's bar turns amber and says so. */
    static final int LOW_FREE_PERCENT = 10;

    static boolean low(Drive drive) {
        return low(drive.freeBytes, drive.totalBytes);
    }

    static boolean low(long freeBytes, long totalBytes) {
        return totalBytes > 0 && freeBytes * 100 < totalBytes * LOW_FREE_PERCENT;
    }

    static int usedPercent(Drive drive) {
        if (drive.totalBytes <= 0) return 0;
        long used = Math.max(0, drive.totalBytes - drive.freeBytes);
        return (int) Math.min(100, used * 100 / drive.totalBytes);
    }

    /** The drive as it will be once {@code freedBytes} are gone from it. */
    static Drive after(Drive drive, long freedBytes) {
        return new Drive(drive.path, Math.min(drive.totalBytes, drive.freeBytes + freedBytes),
                drive.totalBytes);
    }

    /** True only when both numbers are known and the game does not fit: a warning, no block. */
    static boolean tooSmall(long sizeBytes, long freeBytes) {
        return sizeBytes > 0 && freeBytes >= 0 && sizeBytes > freeBytes;
    }
}
