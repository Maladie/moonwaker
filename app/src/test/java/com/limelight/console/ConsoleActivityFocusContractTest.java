package com.limelight.console;

import com.limelight.SourceText;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleActivityFocusContractTest {
    @Test public void carouselDiffOnlyRestoresFocusOwnedByCarousel() throws IOException {
        Path source = Paths.get("src/main/java/com/limelight/console/ConsoleActivity.java");
        String text = SourceText.read(source);
        String method = text.substring(text.indexOf("private void applyPlayniteDiff"),
                text.indexOf("private void moveCarouselCard("));

        assertTrue(method.contains("isDescendant(appRow, currentFocus)"));
        assertTrue(method.contains("Object focusTag = carouselFocused"));
        // A focused card moves with the order too: recency is never held back by focus.
        assertFalse(method.contains("focusedId == null"));
        assertTrue(method.contains("moveCarouselCard(card, currentIndex, index);"));
    }

    @Test public void downFromTheTopBarPicksTheNearestVisibleCard() {
        int[] lefts = {-200, 0, 100, 200, 300, 900};
        int[] rights = {-100, 90, 190, 290, 390, 990};
        // Viewport 0..800: the off-screen cards at either end never count.
        assertEquals(3, ConsoleActivity.nearestVisibleCard(lefts, rights, 0, 800, 260));
        assertEquals(1, ConsoleActivity.nearestVisibleCard(lefts, rights, 0, 800, -500));
        assertEquals(4, ConsoleActivity.nearestVisibleCard(lefts, rights, 0, 800, 2000));
        // Equal distance: the left card.
        assertEquals(2, ConsoleActivity.nearestVisibleCard(lefts, rights, 0, 800, 195));
        assertEquals(-1, ConsoleActivity.nearestVisibleCard(new int[0], new int[0], 0, 800, 10));
    }

    /**
     * Down from the top bar returns to the carousel's selection while it is in view; without
     * one in view, a wide host pill at the carousel's start lands on the first card.
     */
    @Test public void downFromTheTopBarReturnsToTheSelectionInView() {
        int[] lefts = {0, 100, 200, 300, 900};
        int[] rights = {90, 190, 290, 390, 990};
        assertEquals(2, ConsoleActivity.topBarDownTarget(2, lefts, rights, 0, 800, 50));
        // Scrolled out of view: the nearest visible card instead.
        assertEquals(1, ConsoleActivity.topBarDownTarget(4, lefts, rights, 0, 800, 150));
        assertEquals(1, ConsoleActivity.topBarDownTarget(-1, lefts, rights, 0, 800, 150));
        // The pill's origin is its leading card width (45 = 90 / 2), not its middle (180).
        assertEquals(0, ConsoleActivity.topBarDownTarget(-1, lefts, rights, 0, 800, 45));
    }

    /** A saved home focus names a carousel card (a game or the full-library tile) by id. */
    @Test public void carouselTagsNameTheirCard() {
        assertEquals("game", ConsoleActivity.carouselGameId("playnite:game"));
        assertEquals("__library__", ConsoleActivity.carouselGameId("playnite:__library__"));
        assertEquals(null, ConsoleActivity.carouselGameId("playnite:"));
        assertEquals(null, ConsoleActivity.carouselGameId("console.settings"));
        assertEquals(null, ConsoleActivity.carouselGameId(null));
    }
}
