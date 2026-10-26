package com.limelight.session;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.tvprovider.media.tv.WatchNextProgram;

import com.limelight.PosterContentProvider;
import com.limelight.R;
import com.limelight.console.ConsoleActivity;
import com.limelight.diagnostics.MoonWakerDiagnostics;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Mirrors the live stream into the TV home screen's Watch Next row: a subscriber of the
 * session model that keeps exactly the tile {@link WatchNextTile#of} names (or none). The
 * provider's rows are reconciled against it, so tiles a dead process left are removed at start.
 */
public final class WatchNextPublisher implements SessionModel.Listener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private final Context context;
    private final SessionModel model;
    private final SharedPreferences consent;
    private final Executor provider = Executors.newSingleThreadExecutor();
    /** The tile last handed to the provider; null until the first reconcile. */
    private String requestedKey;

    public WatchNextPublisher(Context application, SessionModel model,
                              SharedPreferences consent) {
        this.context = application;
        this.model = model;
        this.consent = consent;
    }

    /** Main thread: subscribes and reconciles once (orphan cleanup). */
    public void start() {
        consent.registerOnSharedPreferenceChangeListener(this);
        onSnapshot(model.subscribe(this));
    }

    @Override public void onSnapshot(SessionModel.Snapshot snapshot) {
        WatchNextTile tile = WatchNextTile.of(snapshot,
                (host, profile) -> WatchNextTile.consented(consent, host, profile));
        String key = tile == null ? "" : tile.key();
        if (key.equals(requestedKey)) return;
        requestedKey = key;
        provider.execute(() -> reconcile(tile));
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key != null && key.startsWith(WatchNextTile.CONSENT_PREFIX)) {
            onSnapshot(model.snapshot());
        }
    }

    private void reconcile(WatchNextTile tile) {
        ContentResolver resolver = context.getContentResolver();
        String key = tile == null ? null : tile.key();
        boolean present = false;
        try (Cursor rows = resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI,
                WatchNextProgram.PROJECTION, null, null, null)) {
            while (rows != null && rows.moveToNext()) {
                WatchNextProgram row = WatchNextProgram.fromCursor(rows);
                if (key != null && key.equals(row.getInternalProviderId())) {
                    present = true;
                } else {
                    resolver.delete(TvContractCompat.buildWatchNextProgramUri(row.getId()),
                            null, null);
                }
            }
            if (tile != null && !present) {
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        program(tile).toContentValues());
            }
            removeChannels(resolver);
            MoonWakerDiagnostics.record("INFO", "android.watch-next", "watch_next.reconciled",
                    "tile", tile != null, "kept", present,
                    "stream_session_id", tile == null ? "" : tile.streamSessionId,
                    "profile_id", tile == null ? "" : tile.profileId);
        } catch (RuntimeException unavailable) {
            // No TV provider (not a TV) or it refused: there is no home row to mirror into.
            MoonWakerDiagnostics.record("WARN", "android.watch-next", "watch_next.unavailable",
                    "error", unavailable.getClass().getSimpleName());
        }
    }

    /**
     * This tile is MoonWaker's only home-screen entry. A preview channel (Moonlight's per-host
     * game row) started a bare stream past the session owner, ending the kept one; any this
     * app still has is removed. The provider lists only this app's own channels.
     */
    private static void removeChannels(ContentResolver resolver) {
        try (Cursor channels = resolver.query(TvContractCompat.Channels.CONTENT_URI,
                new String[]{TvContractCompat.Channels._ID}, null, null, null)) {
            while (channels != null && channels.moveToNext()) {
                resolver.delete(TvContractCompat.buildChannelUri(channels.getLong(0)),
                        null, null);
            }
        }
    }

    private WatchNextProgram program(WatchNextTile tile) {
        WatchNextProgram.Builder builder = new WatchNextProgram.Builder();
        builder.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setType(TvContractCompat.PreviewProgramColumns.TYPE_GAME)
                .setTitle(tile.title.isEmpty()
                        ? context.getString(R.string.console_launch_desktop) : tile.title)
                .setDescription(context.getString(R.string.watch_next_resume_on, tile.hostName))
                // A new tile's artwork is a new URI: the launcher caches by URI.
                .setPosterArtUri(PosterContentProvider.createSessionArtUri(tile.streamSessionId)
                        .buildUpon().appendQueryParameter("v",
                                Integer.toHexString(tile.key().hashCode())).build())
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setIntent(ConsoleActivity.watchNextIntent(context, tile.streamSessionId))
                .setInternalProviderId(tile.key());
        return builder.build();
    }
}
