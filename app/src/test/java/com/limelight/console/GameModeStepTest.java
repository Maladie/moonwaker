package com.limelight.console;

import com.limelight.session.MemoryPreferences;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GameModeStepTest {
    @Test public void gameOverrideWinsOverTheProfileSwitch() {
        assertTrue(GameModeStep.wanted(true, GameModeStep.Override.INHERIT));
        assertFalse(GameModeStep.wanted(false, GameModeStep.Override.INHERIT));
        assertTrue(GameModeStep.wanted(false, GameModeStep.Override.ALWAYS));
        assertFalse(GameModeStep.wanted(true, GameModeStep.Override.NEVER));
    }

    @Test public void optionsDefaultOnOnlyAsSubOptionsAndFollowTheGameOverride() {
        MemoryPreferences preferences = new MemoryPreferences();
        String game = "playnite:host:steam:1";
        assertFalse(GameModeStep.enabled(preferences, "HOST", "basia", game,
                GameModeStep.Option.GAME_MODE));
        assertTrue(GameModeStep.enabled(preferences, "HOST", "basia", game,
                GameModeStep.Option.WINDOWS_GAME_MODE));
        assertTrue(GameModeStep.enabled(preferences, "HOST", "basia", game,
                GameModeStep.Option.MUTE_OTHERS));
        GameModeStep.setProfileEnabled(preferences, "host", "basia",
                GameModeStep.Option.GAME_MODE, true);
        GameModeStep.setOverride(preferences, game, GameModeStep.Option.MUTE_OTHERS,
                GameModeStep.Override.NEVER);
        assertTrue(GameModeStep.enabled(preferences, "HOST", "basia", game,
                GameModeStep.Option.GAME_MODE));
        assertFalse(GameModeStep.enabled(preferences, "HOST", "basia", game,
                GameModeStep.Option.MUTE_OTHERS));
        // Another profile on the same host keeps its own switches.
        assertFalse(GameModeStep.enabled(preferences, "HOST", "kid", game,
                GameModeStep.Option.GAME_MODE));
        GameModeStep.setOverride(preferences, game, GameModeStep.Option.MUTE_OTHERS,
                GameModeStep.Override.INHERIT);
        assertTrue(GameModeStep.enabled(preferences, "HOST", "basia", game,
                GameModeStep.Option.MUTE_OTHERS));
    }

    @Test public void programsKeepOnlyWellFormedEntries() throws Exception {
        List<GameModeStep.Program> programs = GameModeStep.parsePrograms(new JSONObject(
                "{\"ok\": true, \"programs\": ["
                        + "{\"id\": \"10-100\", \"name\": \"Notepad\", \"image\": \"NOTEPAD.EXE\","
                        + " \"icon\": \"iVBORw0KGgo=\"},"
                        + "{\"id\": \"13-100\", \"name\": \"No icon\", \"icon\": \"%%%\"},"
                        + "{\"id\": \"0-1\", \"name\": \"Zero\"},"
                        + "{\"id\": \"11-100\", \"name\": \"  \"},"
                        + "{\"id\": \"12-100;rm\", \"name\": \"Forged\"}]}"));
        assertEquals(2, programs.size());
        assertEquals("10-100", programs.get(0).id);
        assertEquals("Notepad", programs.get(0).name);
        assertEquals("notepad.exe", programs.get(0).image);
        assertEquals((byte) 0x89, programs.get(0).icon[0]);
        // A malformed icon draws a monogram.
        assertEquals(0, programs.get(1).icon.length);
    }

    @Test public void unavailableListAndCloseAreErrors() throws Exception {
        for (String body : Arrays.asList("{\"ok\": false}", "{\"ok\": true}")) {
            try {
                GameModeStep.parsePrograms(new JSONObject(body));
                fail(body);
            } catch (IOException expected) {
                // the caller plays without the list
            }
            try {
                GameModeStep.parseRemaining(new JSONObject(body));
                fail(body);
            } catch (IOException expected) {
                // the caller notices the failure and plays
            }
        }
    }

    @Test public void remainingNamesTheProgramsStillOpenAndWhichAreHung() throws Exception {
        List<GameModeStep.Remaining> open = GameModeStep.parseRemaining(
                new JSONObject("{\"ok\": true, \"closed\": [\"10-100\"], \"remaining\": ["
                        + "{\"id\": \"11-100\", \"name\": \"Word - Save changes?\","
                        + " \"responding\": true},"
                        + "{\"id\": \"13-100\", \"name\": \"Frozen app\", \"responding\": false},"
                        + "{\"id\": \"12-100\", \"name\": \"\"}],"
                        + " \"refused\": [{\"id\": \"14-100\", \"name\": \"Explorer\"}]}"));
        assertEquals(3, open.size());
        // Refused (not closable after all): not closed, and never offered a force close.
        assertEquals("Explorer", open.get(2).name);
        assertTrue(open.get(2).responding);
        assertEquals("Word - Save changes?", open.get(0).name);
        assertTrue(open.get(0).responding);
        assertEquals("13-100", open.get(1).id);
        assertFalse(open.get(1).responding);
    }

    @Test public void forceCloseAnswersOnlyKnownResults() throws Exception {
        for (String result : Arrays.asList("closed", "gone", "responding", "refused", "failed")) {
            assertEquals(result, GameModeStep.parseForceResult(
                    new JSONObject().put("ok", true).put("result", result)));
        }
        for (String body : Arrays.asList("{\"ok\": true, \"result\": \"killed\"}",
                "{\"ok\": false, \"result\": \"closed\"}")) {
            try {
                GameModeStep.parseForceResult(new JSONObject(body));
                fail(body);
            } catch (IOException expected) {
                // shown as "could not close it"
            }
        }
    }
}
