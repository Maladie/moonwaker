package com.limelight.console;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.R;

/**
 * The user's Home return over a running game, answered at the press: its cue plays and its
 * notice shows on the stream screen, and the Home it opens carries that notice on. The notice
 * first says the menu is opening; once Game decided the Esc it says what the return did.
 * Main thread only.
 */
public final class StreamReturnNotice {
    public enum Outcome { GAME_PAUSED, GAME_RUNNING }

    /** The latest user return, until a newer one; its Home carries its notice. */
    private static StreamReturnNotice latest;

    private final long id;
    private Outcome outcome;
    private ConsoleUiFeedback shownIn;
    private View card;

    private StreamReturnNotice(long id) {
        this.id = id;
    }

    /** The stream's cue and notice surface, for its Activity's lifetime. */
    public static final class Stream {
        private final ConsoleAudioEngine audio;
        private final ConsoleUiFeedback feedback;

        public Stream(Activity stream, FrameLayout root) {
            SharedPreferences preferences = stream.getSharedPreferences(
                    ConsoleActivity.PREFS, Activity.MODE_PRIVATE);
            audio = new ConsoleAudioEngine(stream);
            audio.setEffectsEnabled(preferences.getBoolean("ui_sounds", true));
            audio.setEffectsVolume(preferences.getInt("effects_volume", 40) / 100f);
            audio.prepareCues();
            feedback = new ConsoleUiFeedback(stream, root, null, false);
        }

        /** The press was detected: the cue now, the notice on the stream screen. */
        public long begin() {
            audio.play(ConsoleAudioSynthesis.Cue.SUCCESS);
            if (latest != null) latest.dismiss();
            latest = new StreamReturnNotice(SystemClock.elapsedRealtimeNanos());
            // Over everything on the stream screen, the Home return's cover included.
            feedback.raiseToTop();
            latest.show(feedback);
            return latest.id;
        }

        /** The stream is gone: so are its returns, wherever their notice shows. */
        public void release() {
            if (latest != null) latest.dismiss();
            latest = null;
            feedback.release();
            audio.release();
        }
    }

    /** What return {@code id} did; its notice says so wherever it shows now. */
    public static void decided(long id, Outcome outcome) {
        StreamReturnNotice notice = latest;
        if (notice == null || notice.id != id || notice.outcome != null) return;
        notice.outcome = outcome;
        if (notice.shownIn != null && notice.shownIn.isShowing(notice.card)) {
            ConsoleUiFeedback.setText(notice.card, notice.text());
        }
    }

    /** {@code activity} is destroyed: a notice it shows goes with it, never kept from here. */
    static void release(Activity activity) {
        StreamReturnNotice notice = latest;
        if (notice != null && notice.shownIn != null && notice.shownIn.activity() == activity) {
            notice.dismiss();
        }
    }

    /** Return {@code id} opened no Home after all: its notice goes. */
    public static void withdraw(long id) {
        StreamReturnNotice notice = latest;
        if (notice == null || notice.id != id) return;
        notice.dismiss();
        latest = null;
    }

    /**
     * The shown Home of return {@code id} takes its notice over while it still shows: one
     * card, no second cue. A notice already gone stays gone.
     */
    static void carryTo(Activity home, long id) {
        StreamReturnNotice notice = latest;
        ConsoleUiFeedback target = ConsoleUiFeedback.find(home);
        if (notice == null || notice.id != id || target == null || notice.shownIn == target
                || notice.shownIn == null || !notice.shownIn.isShowing(notice.card)) return;
        notice.dismiss();
        notice.show(target);
    }

    private void show(ConsoleUiFeedback feedback) {
        shownIn = feedback;
        // Whole at once: a slide-in is clipped at the screen edge, and the stream screen stops
        // drawing mid-slide while the Home opens over it (TV 2026-10-21).
        card = feedback.notify(ConsoleUiFeedback.Kind.INFO, text(), false, false);
    }

    private void dismiss() {
        if (shownIn != null) shownIn.dismiss(card);
        shownIn = null;
        card = null;
    }

    private CharSequence text() {
        return shownIn.activity().getText(outcome == null ? R.string.stream_home_returning
                : outcome == Outcome.GAME_PAUSED ? R.string.stream_home_game_paused
                : R.string.stream_home_game_running);
    }
}
