package com.limelight.stream;

import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StreamingAutopilotVariantsTest {
    private static final PreferenceConfiguration.FormatOption AUTO =
            PreferenceConfiguration.FormatOption.AUTO;
    private static final StreamingAutopilot.Capabilities HOST =
            StreamingAutopilot.Capabilities.upTo(3840, 2160, 120);

    private static StreamingAutopilot.Mode mode(int width, int height, int fps) {
        return new StreamingAutopilot.Mode(width, height, fps);
    }

    private static String describe(List<StreamingAutopilot.Variant> variants) {
        StringBuilder text = new StringBuilder();
        for (StreamingAutopilot.Variant v : variants) {
            text.append(v.kind).append(' ').append(v.width).append('x').append(v.height)
                    .append('@').append(v.fps).append(' ').append(v.bitrateKbps).append('\n');
        }
        return text.toString();
    }

    /** The mockup's TV: 4K at 60 Hz, 1080p at 120 Hz; 60 Mb/s safe budget. */
    @Test public void aTvWithRoomOffersEveryKind() {
        StreamingAutopilot.Capabilities tv = new StreamingAutopilot.Capabilities(
                mode(1280, 720, 60), mode(1280, 720, 120), mode(1920, 1080, 30),
                mode(1920, 1080, 60), mode(1920, 1080, 120), mode(2560, 1440, 60),
                mode(3840, 2160, 30), mode(3840, 2160, 60));

        assertEquals("BALANCED 2560x1440@60 40000\n"
                        + "SHARPNESS 3840x2160@60 60000\n"
                        + "SMOOTHNESS 1920x1080@120 35000\n"
                        + "CLEAN 2560x1440@60 60000\n"
                        + "STABLE 1920x1080@60 20000\n",
                describe(StreamingAutopilot.variants(tv, HOST, OptionalInt.of(60000), AUTO)));
    }

    /** Mockup O7: Balanced passed, Clean picture (same mode, 60 Mb/s) is the next step up. */
    @Test public void aPassedTestStepsUpToTheLeastBitrateAboveSameModeFirst() {
        StreamingAutopilot.Capabilities tv = new StreamingAutopilot.Capabilities(
                mode(1280, 720, 60), mode(1280, 720, 120), mode(1920, 1080, 30),
                mode(1920, 1080, 60), mode(1920, 1080, 120), mode(2560, 1440, 60),
                mode(3840, 2160, 30), mode(3840, 2160, 60));
        List<StreamingAutopilot.Variant> variants =
                StreamingAutopilot.variants(tv, HOST, OptionalInt.of(60000), AUTO);

        assertEquals(StreamingAutopilot.VariantKind.CLEAN, StreamingAutopilot.higher(
                variants, 2560, 1440, 60, 40000).kind);
        assertEquals(StreamingAutopilot.VariantKind.SMOOTHNESS, StreamingAutopilot.higher(
                variants, 1920, 1080, 60, 20000).kind);
        assertNull(StreamingAutopilot.higher(variants, 3840, 2160, 60, 60000));
    }

    @Test public void oneModeLeavesOnlyWhatDiffers() {
        StreamingAutopilot.Capabilities tv =
                new StreamingAutopilot.Capabilities(mode(1920, 1080, 60));

        // Stable would be Balanced again; nothing is sharper or smoother.
        assertEquals("BALANCED 1920x1080@60 20000\nCLEAN 1920x1080@60 60000\n",
                describe(StreamingAutopilot.variants(tv, HOST, OptionalInt.of(60000), AUTO)));
    }

    @Test public void withoutAMeasuredBudgetOrBelowTheSmallestModeOnlyBalanced() {
        StreamingAutopilot.Capabilities tv = StreamingAutopilot.Capabilities.upTo(3840, 2160, 120);

        assertEquals("BALANCED 1280x720@60 10000\n",
                describe(StreamingAutopilot.variants(tv, HOST, OptionalInt.empty(), AUTO)));
        assertEquals(1, StreamingAutopilot.variants(new StreamingAutopilot.Capabilities(
                mode(1280, 720, 60)), HOST, OptionalInt.of(2000), AUTO).size());
    }
}
