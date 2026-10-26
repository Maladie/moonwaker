package com.limelight.binding.input;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamMenuButtonsTest {
    private final List<String> effects = new ArrayList<>();
    /** The pending holds, the menu button's and Share's, oldest first. */
    private final List<Runnable> scheduled = new ArrayList<>();
    /** The hold time each pending hold was scheduled with, in the same order. */
    private final List<Long> delays = new ArrayList<>();
    private final StreamMenuButtons buttons = new StreamMenuButtons(
            new StreamMenuButtons.Scheduler() {
                @Override public void postDelayed(Runnable task, long delayMs) {
                    scheduled.add(task);
                    delays.add(delayMs);
                }

                @Override public void remove(Runnable task) {
                    int index = scheduled.indexOf(task);
                    if (index < 0) return;
                    scheduled.remove(index);
                    delays.remove(index);
                }
            }, 1500, new StreamMenuButtons.Effects() {
                @Override public void openMenu() { effects.add("menu"); }
                @Override public void navigateBack() { effects.add("back"); }
                @Override public void navigateHome() { effects.add("home"); }
                @Override public void holdGuideOnHost() { effects.add("guide-host"); }
                @Override public void capture(boolean clip) {
                    effects.add(clip ? "clip" : "screenshot");
                }
            });

    /** The oldest pending hold reaches its time. */
    private void hold() {
        delays.remove(0);
        scheduled.remove(0).run();
    }

    @Test public void remoteBackTapIsStreamBack() {
        buttons.press(StreamMenuButtons.Press.BACK);
        assertFalse(buttons.release(StreamMenuButtons.Press.BACK));
        assertEquals(List.of("back"), effects);
        assertTrue(scheduled.isEmpty());
    }

    @Test public void remoteBackHoldOpensMenuAndItsReleaseIsNotABack() {
        buttons.press(StreamMenuButtons.Press.BACK);
        assertEquals(List.of(1500L), delays);
        hold();
        assertFalse(buttons.release(StreamMenuButtons.Press.BACK));
        assertEquals(List.of("menu"), effects);
    }

    @Test public void guideTapGoesHomeAndNeverReachesTheHost() {
        buttons.press(StreamMenuButtons.Press.GUIDE);
        assertFalse(buttons.release(StreamMenuButtons.Press.GUIDE));
        assertEquals(List.of("home"), effects);
    }

    @Test public void guideHoldGoesToTheHostInsteadOfTheMenu() {
        buttons.press(StreamMenuButtons.Press.GUIDE);
        hold();
        buttons.release(StreamMenuButtons.Press.GUIDE);
        assertEquals(List.of("guide-host"), effects);
    }

    @Test public void triggerTapIsForwardedAndHoldOpensMenu() {
        buttons.press(StreamMenuButtons.Press.TRIGGER);
        assertTrue(buttons.release(StreamMenuButtons.Press.TRIGGER));
        assertTrue(effects.isEmpty());
        buttons.press(StreamMenuButtons.Press.TRIGGER);
        hold();
        assertFalse(buttons.release(StreamMenuButtons.Press.TRIGGER));
        assertEquals(List.of("menu"), effects);
    }

    @Test public void chordCancelAndForeignReleaseRunNoTapEffect() {
        buttons.press(StreamMenuButtons.Press.GUIDE);
        assertFalse(buttons.release(StreamMenuButtons.Press.BACK));
        assertTrue(buttons.cancel());
        assertFalse(buttons.release(StreamMenuButtons.Press.GUIDE));
        assertFalse(buttons.cancel());
        assertTrue(scheduled.isEmpty());
        assertTrue(effects.isEmpty());
    }

    @Test public void createTapOpensTheMenuAndNeverReachesTheGame() {
        buttons.press(StreamMenuButtons.Press.CREATE);
        assertFalse(buttons.release(StreamMenuButtons.Press.CREATE));
        assertEquals(List.of("menu"), effects);
        assertTrue(scheduled.isEmpty());
    }

    @Test public void createHeldItsShortTimeTakesAScreenshotAndItsReleaseNothingMore() {
        buttons.press(StreamMenuButtons.Press.CREATE);
        assertEquals(List.of(StreamMenuButtons.CREATE_HOLD_MS), delays);
        hold();
        assertFalse(buttons.release(StreamMenuButtons.Press.CREATE));
        assertEquals(List.of("screenshot"), effects);
        assertTrue(scheduled.isEmpty());
    }

    @Test public void shareTapIsAScreenshotAndHoldSavesTheClip() {
        buttons.pressShare();
        buttons.releaseShare();
        buttons.pressShare();
        hold();
        buttons.releaseShare();
        assertEquals(List.of("screenshot", "clip"), effects);
        assertTrue(scheduled.isEmpty());
    }

    @Test public void shareDuringAPendingTriggerLeavesItToTheCallersChordRule() {
        // Start down, then Share on the same pad: the caller cancels the trigger (sending
        // Start to the host); Share's own hold runs on.
        buttons.press(StreamMenuButtons.Press.TRIGGER);
        buttons.pressShare();
        assertTrue(buttons.cancel());
        assertFalse(buttons.release(StreamMenuButtons.Press.TRIGGER));
        hold();
        buttons.releaseShare();
        assertEquals(List.of("clip"), effects);
        assertTrue(scheduled.isEmpty());
    }

    @Test public void createDuringAPendingTriggerLeavesItToTheCallersChordRule() {
        // Start down, then Create: the caller cancels the trigger first (Start goes to the
        // host and its release follows there); Create then runs as its own press.
        buttons.press(StreamMenuButtons.Press.TRIGGER);
        assertTrue(buttons.cancel());
        buttons.press(StreamMenuButtons.Press.CREATE);
        assertFalse(buttons.release(StreamMenuButtons.Press.TRIGGER));
        assertFalse(buttons.release(StreamMenuButtons.Press.CREATE));
        assertEquals(List.of("menu"), effects);
        assertTrue(scheduled.isEmpty());
    }

    @Test public void createDuringAShareHoldKeepsBoth() {
        buttons.pressShare();
        buttons.press(StreamMenuButtons.Press.CREATE);
        assertFalse(buttons.release(StreamMenuButtons.Press.CREATE));
        buttons.releaseShare();
        assertEquals(List.of("menu", "screenshot"), effects);
        buttons.pressShare();
        buttons.press(StreamMenuButtons.Press.CREATE);
        hold();
        hold();
        assertFalse(buttons.release(StreamMenuButtons.Press.CREATE));
        buttons.releaseShare();
        assertEquals(List.of("menu", "screenshot", "clip", "screenshot"), effects);
        assertTrue(scheduled.isEmpty());
    }

    @Test public void guideAndBackDuringAShareHoldKeepTheShare() {
        buttons.pressShare();
        buttons.press(StreamMenuButtons.Press.GUIDE);
        assertFalse(buttons.release(StreamMenuButtons.Press.GUIDE));
        buttons.press(StreamMenuButtons.Press.BACK);
        assertFalse(buttons.release(StreamMenuButtons.Press.BACK));
        buttons.releaseShare();
        assertEquals(List.of("home", "back", "screenshot"), effects);
    }

    @Test public void aRemovedPadsHoldsFireNothing() {
        buttons.press(StreamMenuButtons.Press.CREATE);
        buttons.pressShare();
        assertTrue(buttons.cancel());
        buttons.cancelShare();
        buttons.releaseShare();
        assertFalse(buttons.release(StreamMenuButtons.Press.CREATE));
        assertTrue(scheduled.isEmpty());
        assertTrue(effects.isEmpty());
    }
}
