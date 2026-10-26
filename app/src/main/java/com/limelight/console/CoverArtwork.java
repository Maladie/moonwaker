package com.limelight.console;

import java.io.File;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A cover's artwork: the very file the carousel shows as the game's backdrop
 * ({@link PlayniteArtworkSpec#forBackdrop} in {@link PlayniteArtworkCache}), only when it is the
 * game's full background; never the portrait cover fallback. The game comes from the profile's
 * whole library, not from the filtered list a Home happens to have rendered.
 */
final class CoverArtwork {
    enum Outcome { FOUND, NO_ITEM, NO_BACKGROUND, CACHE_MISS }

    /**
     * How long a launch waits for its game's background that is not cached yet before it
     * starts on the standard cover: long enough for one artwork request over the LAN.
     */
    static final long FETCH_WAIT_MS = 1_500L;

    interface Delay {
        void post(Runnable action, long delayMillis);
    }

    final Outcome outcome;
    /** The cached background; set only when FOUND. */
    final File file;

    private CoverArtwork(Outcome outcome, File file) {
        this.outcome = outcome;
        this.file = file;
    }

    /** {@code game}: the library's entry for the cover's game, or null when it lists none. */
    static CoverArtwork find(PlayniteArtworkCache cache, HostProfileKey key,
                             PlayniteLibraryGame game) {
        if (key == null || game == null) return new CoverArtwork(Outcome.NO_ITEM, null);
        PlayniteArtworkSpec spec = PlayniteArtworkSpec.forBackdrop(game);
        if (!"background".equals(spec.kind)) return new CoverArtwork(Outcome.NO_BACKGROUND, null);
        File file = cache.get(key, game.playniteGameId, spec.kind, spec.version);
        return file != null && file.isFile() && file.length() > 0
                ? new CoverArtwork(Outcome.FOUND, file)
                : new CoverArtwork(Outcome.CACHE_MISS, null);
    }

    /**
     * A launch's artwork answer, bounded from this call: the returned consumer, called on the
     * UI thread with the cover lookup's or the fetch's answer, passes the first one to
     * {@code decided}; at {@link #FETCH_WAIT_MS} after this call it answers null (the standard
     * cover) instead. A later answer changes nothing on screen (a late file only stays cached
     * for the next launch). {@code delay} posts on the UI thread. {@code current} is read
     * there: once the Play that asked is no longer current (cancelled, or another Play took
     * the cover), {@code decided} never runs.
     */
    static <T> Consumer<T> within(Delay delay, BooleanSupplier current, Consumer<T> decided) {
        boolean[] answered = {false};
        Consumer<T> once = answer -> {
            if (answered[0] || !current.getAsBoolean()) return;
            answered[0] = true;
            decided.accept(answer);
        };
        delay.post(() -> once.accept(null), FETCH_WAIT_MS);
        return once;
    }
}
