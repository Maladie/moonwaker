package com.limelight.session;

import android.content.SharedPreferences;

import com.limelight.gateway.GatewayConnection;

import java.util.Locale;

/**
 * The Watch Next "Continue" tile one live stream earns: derived from a model snapshot and the
 * stream's own profile consent, never held as session state. The tile names only the profile
 * that owns the stream, with that stream's own title and artwork.
 */
public final class WatchNextTile {
    /** Per host and profile consent; the profile's user (a child's parent) turns it on. */
    public static final String CONSENT_PREFIX = "watch_next_tile.";

    public final String streamSessionId;
    public final String hostId;
    public final String profileId;
    /** The game's or streamed app's name; "" for the desktop. */
    public final String title;
    public final String hostName;
    /** The stream's cover artwork file; "" when none. */
    public final String artworkPath;

    private WatchNextTile(String streamSessionId, String hostId, String profileId, String title,
                          String hostName, String artworkPath) {
        this.streamSessionId = streamSessionId;
        this.hostId = hostId;
        this.profileId = profileId;
        this.title = title;
        this.hostName = hostName;
        this.artworkPath = artworkPath;
    }

    public interface Consent {
        boolean allows(String hostId, String profileId);
    }

    /**
     * The tile of {@code s}, or null: no live stream (a reconnect record is not one), a record
     * of another stream, profile or game, or a profile without consent.
     */
    public static WatchNextTile of(SessionModel.Snapshot s, Consent consent) {
        if (s.state != SessionModel.State.HOME_LIVE && s.state != SessionModel.State.PARKED_LIVE) {
            return null;
        }
        SessionResumeManager.PendingSession record = s.records.pending;
        if (record == null || !record.matches(s.streamSessionId)
                || !record.hostUuid.equalsIgnoreCase(s.hostId)
                || !record.profileId.equals(s.profileId)
                || !record.playniteGameId.equalsIgnoreCase(s.playniteGameId)
                || !consent.allows(s.hostId, s.profileId)) {
            return null;
        }
        boolean desktop = s.playniteGameId.isEmpty() && record.neutralStreamTarget;
        return new WatchNextTile(s.streamSessionId, s.hostId, s.profileId,
                desktop ? "" : record.appName(), record.hostName(),
                desktop ? "" : record.artworkPath());
    }

    /** Identifies what the tile shows; a changed title or artwork is a new tile. */
    public String key() {
        return String.join("\n", streamSessionId, hostId.toLowerCase(Locale.ROOT), profileId,
                title, hostName, artworkPath);
    }

    public enum Entry { RESUME, ENDED, OTHER_PROFILE }

    /**
     * What entering {@code enteredProfileId} on {@code enteredHostId} through the profile gate
     * does with the tile of {@code tileSessionId}: resume that stream, report it ended, or
     * leave a stream of another profile alone.
     */
    public static Entry entry(SessionModel.Snapshot s, boolean resumable, String tileSessionId,
                              String enteredHostId, String enteredProfileId) {
        boolean live = resumable && (s.state == SessionModel.State.HOME_LIVE
                || s.state == SessionModel.State.PARKED_LIVE)
                && s.streamSessionId.equals(tileSessionId);
        if (!live) return Entry.ENDED;
        return s.hostId.equalsIgnoreCase(enteredHostId)
                && s.profileId.equals(GatewayConnection.normalizeProfileId(enteredProfileId))
                ? Entry.RESUME : Entry.OTHER_PROFILE;
    }

    public static boolean consented(SharedPreferences preferences, String hostId,
                                    String profileId) {
        return preferences.getBoolean(consentKey(hostId, profileId), false);
    }

    public static void setConsent(SharedPreferences preferences, String hostId, String profileId,
                                  boolean enabled) {
        preferences.edit().putBoolean(consentKey(hostId, profileId), enabled).apply();
    }

    static String consentKey(String hostId, String profileId) {
        return CONSENT_PREFIX + (hostId == null ? "" : hostId.trim().toLowerCase(Locale.ROOT))
                + "." + GatewayConnection.normalizeProfileId(profileId);
    }
}
