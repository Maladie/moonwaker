package com.limelight.console;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class HapticsPadSupportTest {
    @Test
    public void listsEachPadWithItsVerdict() {
        assertEquals("DualSense: no\nXbox: yes", HapticsPadSupport.format(Arrays.asList(
                new HapticsPadSupport.Pad("DualSense", false),
                new HapticsPadSupport.Pad("Xbox", true)), "yes", "no", "none"));
    }

    @Test
    public void saysSoWhenNoPadIsConnected() {
        assertEquals("none", HapticsPadSupport.format(Collections.emptyList(), "yes", "no", "none"));
    }
}
