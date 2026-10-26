package com.limelight.console;

import com.limelight.stream.StreamingAutopilot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConsoleAutopilotPanelTest {
    private static StreamingAutopilot.Mode mode(int width, int height, int fps) {
        return new StreamingAutopilot.Mode(width, height, fps);
    }

    /** Mockup C1's TV chip: the largest mode, then the fastest when it is faster. */
    @Test public void theTvChipNamesTheLargestAndTheFastestMode() {
        assertEquals("4K 60 \u00B7 1440p 120", ConsoleAutopilotPanel.displayModes(
                new StreamingAutopilot.Capabilities(mode(1920, 1080, 60), mode(2560, 1440, 120),
                        mode(3840, 2160, 30), mode(3840, 2160, 60))));
        assertEquals("1080p 60", ConsoleAutopilotPanel.displayModes(
                new StreamingAutopilot.Capabilities(mode(1280, 720, 60),
                        mode(1920, 1080, 60))));
    }
}
