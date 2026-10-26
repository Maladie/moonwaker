package com.limelight.console;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ConsoleHeldRendersTest {
    @Test public void eachKindRunsOnceItsLatestInFirstHeldOrder() {
        List<String> ran = new ArrayList<>();
        ConsoleHeldRenders held = new ConsoleHeldRenders();
        held.hold("library", () -> ran.add("library 1"));
        held.hold("hosts", () -> ran.add("hosts"));
        held.hold("library", () -> ran.add("library 2"));
        assertEquals(new ArrayList<>(), ran);

        held.release();
        assertEquals(Arrays.asList("library 2", "hosts"), ran);
        held.release();
        assertEquals(2, ran.size());
    }

    @Test public void aRenderHeldWhileReleasingWaitsForTheNextRelease() {
        List<String> ran = new ArrayList<>();
        ConsoleHeldRenders held = new ConsoleHeldRenders();
        held.hold("library", () -> held.hold("hosts", () -> ran.add("hosts")));
        held.release();
        assertEquals(new ArrayList<>(), ran);
        held.release();
        assertEquals(Arrays.asList("hosts"), ran);
    }
}
