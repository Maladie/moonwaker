package com.limelight.console;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class AdultPinChangeTest {
    @Test public void changeAsksCurrentThenNewTwice() {
        AdultPinChange change = new AdultPinChange(AdultPinChange.Mode.CHANGE);
        assertEquals(AdultPinChange.Step.CURRENT, change.step());
        assertEquals(AdultPinChange.Next.ASK, change.accept("2468"));
        assertEquals(AdultPinChange.Next.ASK, change.accept("1357"));
        assertEquals(AdultPinChange.Step.CONFIRM, change.step());
        assertEquals(AdultPinChange.Next.SUBMIT, change.accept("1357"));
        assertEquals("2468", change.currentPin());
        assertEquals("1357", change.newPin());
    }

    @Test public void mismatchedConfirmationAsksTheNewPinAgain() {
        AdultPinChange change = new AdultPinChange(AdultPinChange.Mode.SET);
        assertEquals(AdultPinChange.Step.NEW, change.step());
        change.accept("1357");
        assertEquals(AdultPinChange.Next.MISMATCH, change.accept("1358"));
        assertEquals(AdultPinChange.Step.NEW, change.step());
        assertNull(change.newPin());
        change.accept("4321");
        assertEquals(AdultPinChange.Next.SUBMIT, change.accept("4321"));
        assertNull(change.currentPin());
        assertEquals("4321", change.newPin());
    }

    @Test public void removeNeedsOnlyTheCurrentPinAndRestartForgetsIt() {
        AdultPinChange change = new AdultPinChange(AdultPinChange.Mode.REMOVE);
        assertEquals(AdultPinChange.Next.SUBMIT, change.accept("2468"));
        assertEquals("2468", change.currentPin());
        assertNull(change.newPin());
        change.restart();
        assertEquals(AdultPinChange.Step.CURRENT, change.step());
        assertNull(change.currentPin());
    }

    @Test public void requestBodyCarriesOnlyThePinsGiven() throws Exception {
        JSONObject first = HostGatewayClient.profilePinChangeBody(null, "4321");
        assertEquals("4321", first.getString("new_pin"));
        assertFalse(first.has("current_pin"));
        JSONObject remove = HostGatewayClient.profilePinChangeBody("2468", null);
        assertEquals("2468", remove.getString("current_pin"));
        assertFalse(remove.has("new_pin"));
        assertThrows(IllegalArgumentException.class,
                () -> HostGatewayClient.profilePinChangeBody(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> HostGatewayClient.profilePinChangeBody("2468", "12345"));
    }
}
