package com.limelight.binding.input;

import com.limelight.SourceText;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Wiring of the real key paths onto StreamMenuButtons (its behavior: StreamMenuButtonsTest). */
public class ControllerHandlerStreamMenuContractTest {
    @Test public void guideAndBackUseTheirMenuPressesAndNeverReachTheHostOrFinish()
            throws IOException {
        String source = SourceText.read(Paths.get(
                "src/main/java/com/limelight/binding/input/ControllerHandler.java"));
        String down = between(source, "public boolean handleButtonDown(KeyEvent event)",
                "case KeyEvent.KEYCODE_BUTTON_START:");
        String up = between(source, "public boolean handleButtonUp(KeyEvent event)",
                "case KeyEvent.KEYCODE_BUTTON_START:");
        String back = between(source, "public void handleBackDown(KeyEvent event)",
                "private void releaseTriggerButton(");

        assertTrue(down.contains("menuButtons.press(StreamMenuButtons.Press.GUIDE)"));
        assertFalse(down.contains("context.inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG"));
        assertTrue(up.contains("menuButtons.release(StreamMenuButtons.Press.GUIDE)"));
        assertTrue(back.contains("menuButtons.press(StreamMenuButtons.Press.BACK)"));
        assertTrue(back.contains("menuButtons.release(StreamMenuButtons.Press.BACK)"));
        assertTrue(source.contains("overlayMenuListener.onBackTap()"));
        // Back is local only: no host path for KEYCODE_BACK and no direct finish() on a tap.
        assertFalse(source.contains("case KeyEvent.KEYCODE_BACK:"));
        assertFalse(source.contains("wasRemoteBack"));
    }

    @Test public void onlyPlayStationCreateIsLocalAndAPendingTriggerStillReachesTheHost()
            throws IOException {
        String source = SourceText.read(Paths.get(
                "src/main/java/com/limelight/binding/input/ControllerHandler.java"));
        String create = between(source, "case KeyEvent.KEYCODE_BUTTON_SELECT:\n"
                + "            context.hasSelect = true;", "case KeyEvent.KEYCODE_DPAD_LEFT:");
        String up = between(source, "public boolean handleButtonUp(KeyEvent event)",
                "case KeyEvent.KEYCODE_DPAD_LEFT:");
        String local = between(source, "private boolean localCreate(",
                "private void flushPendingTrigger()");

        assertTrue(local.contains("prefConfig.createButtonMenu && context.vendorId == 0x054c"));
        assertTrue(create.contains("if (!localCreate(context))"));
        SourceText.assertOrdered(create, "flushPendingTrigger();",
                "menuButtons.press(StreamMenuButtons.Press.CREATE)");
        assertTrue(up.contains(
                "if (localCreate(context)) menuButtons.release(StreamMenuButtons.Press.CREATE);"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        return source.substring(from, source.indexOf(end, from));
    }
}
