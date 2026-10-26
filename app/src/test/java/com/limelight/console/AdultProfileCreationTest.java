package com.limelight.console;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdultProfileCreationTest {
    @Test public void adultWithPinProvesItThenPicksAnAccountAndANewPin() {
        AdultProfileCreation creation = new AdultProfileCreation(true);
        assertEquals(AdultProfileCreation.Step.ACTOR_PIN, creation.step());
        creation.acceptActorPin("2468");
        assertEquals(AdultProfileCreation.Step.ACCOUNT, creation.step());
        assertFalse(creation.chooseAccount("Ola", true, "req-1"));
        assertEquals(AdultProfileCreation.Step.NEW_PIN, creation.step());
        AdultPinChange pin = creation.newPinChange();
        assertEquals(AdultPinChange.Next.ASK, pin.accept("1357"));
        assertEquals(AdultPinChange.Next.SUBMIT, pin.accept("1357"));
        assertEquals("2468", creation.actorPin());
        assertEquals("Ola", creation.accountName());
        assertEquals("1357", creation.newPin());
        assertEquals("req-1", creation.requestId());
    }

    @Test public void withoutPinsTheAccountSubmitsAtOnce() {
        AdultProfileCreation creation = new AdultProfileCreation(false);
        assertEquals(AdultProfileCreation.Step.ACCOUNT, creation.step());
        assertTrue(creation.chooseAccount("Jan", false, "req-1"));
        assertNull(creation.actorPin());
        assertNull(creation.newPin());
    }

    @Test public void refusalsLeadBackToTheRightStep() {
        AdultProfileCreation creation = new AdultProfileCreation(true);
        creation.acceptActorPin("1111");
        creation.chooseAccount("Ola", true, "req-1");
        assertEquals(AdultProfileCreation.Retry.ACTOR_PIN, creation.refused("invalid_pin"));
        assertEquals(AdultProfileCreation.Step.ACTOR_PIN, creation.step());
        assertNull(creation.actorPin());
        assertNull(creation.newPinChange());
        creation.acceptActorPin("2468");
        assertEquals(AdultProfileCreation.Retry.ACCOUNT,
                creation.refused("windows_account_in_use"));
        assertEquals("2468", creation.actorPin());
        assertEquals(AdultProfileCreation.Retry.NOT_AUTHORIZED,
                new AdultProfileCreation(false).refused("first_pin_grant_required"));
        assertEquals(AdultProfileCreation.Retry.FAILED,
                new AdultProfileCreation(false).refused("broker_unavailable"));
    }
}
