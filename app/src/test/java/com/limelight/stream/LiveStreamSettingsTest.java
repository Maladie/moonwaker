package com.limelight.stream;

import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveStreamSettingsTest {
    private static StreamingAutopilot.Mode mode(int width, int height, int fps) {
        return new StreamingAutopilot.Mode(width, height, fps);
    }

    private static final List<StreamingAutopilot.Mode> COMMON = Arrays.asList(
            mode(1280, 720, 60), mode(1280, 720, 120),
            mode(1920, 1080, 30), mode(1920, 1080, 60), mode(1920, 1080, 120),
            mode(3840, 2160, 60));

    @Test public void resolutionsAreNamedAsTheMockupsNameThem() {
        assertEquals("720p", LiveStreamSettings.resolutionName(1280, 720));
        assertEquals("1440p", LiveStreamSettings.resolutionName(2560, 1440));
        assertEquals("4K", LiveStreamSettings.resolutionName(3840, 2160));
        assertEquals("2560×1080", LiveStreamSettings.resolutionName(2560, 1080));
    }

    @Test public void commonModesNeedACodecBothSidesShareAndTheFormatAllows() {
        StreamingAutopilot.Capabilities client = StreamingAutopilot.forCodecModes(
                Arrays.asList(mode(1920, 1080, 60), mode(1280, 720, 60)),
                Arrays.asList(mode(3840, 2160, 60), mode(1920, 1080, 60)));
        StreamingAutopilot.Capabilities host = StreamingAutopilot.forCodecModes(
                Collections.singletonList(mode(3840, 2160, 60)),
                Arrays.asList(mode(1920, 1080, 60), mode(1280, 720, 60)));

        List<StreamingAutopilot.Mode> auto = StreamingAutopilot.commonModes(client, host,
                PreferenceConfiguration.FormatOption.AUTO);
        assertEquals(1, auto.size());
        assertEquals(1920, auto.get(0).width);
        assertTrue(StreamingAutopilot.commonModes(client, host,
                PreferenceConfiguration.FormatOption.FORCE_H264).isEmpty());
    }

    @Test public void resolutionCyclesUpAndKeepsTheHighestFrameRateNotAboveTheCurrent() {
        LiveStreamSettings current = new LiveStreamSettings(1280, 720, 120, 20000, false);
        LiveStreamSettings.Options options =
                new LiveStreamSettings.Options(COMMON, current, false);

        LiveStreamSettings next = current.nextResolution(options, 1);
        assertEquals(new LiveStreamSettings(1920, 1080, 120, 20000, false), next);
        LiveStreamSettings uhd = next.nextResolution(options, 1);
        // 4K offers only 60: nothing is at or below 120 but 60.
        assertEquals(new LiveStreamSettings(3840, 2160, 60, 20000, false), uhd);
        assertEquals(new LiveStreamSettings(1280, 720, 60, 20000, false),
                uhd.nextResolution(options, 1));
    }

    @Test public void frameRateCyclesWithinTheResolution() {
        LiveStreamSettings current = new LiveStreamSettings(1920, 1080, 60, 20000, false);
        LiveStreamSettings.Options options =
                new LiveStreamSettings.Options(COMMON, current, false);

        assertEquals(120, current.nextFps(options, 1).fps);
        assertEquals(30, current.nextFps(options, 1).nextFps(options, 1).fps);
        assertEquals(30, current.nextFps(options, -1).fps);
        assertEquals(1280, current.nextResolution(options, -1).width);
    }

    @Test public void theRunningModeIsAlwaysOfferedEvenOutsideTheStandardList() {
        LiveStreamSettings custom = new LiveStreamSettings(1600, 900, 60, 15000, false);
        LiveStreamSettings.Options options =
                new LiveStreamSettings.Options(COMMON, custom, false);

        assertEquals(1920, custom.nextResolution(options, 1).width);
        LiveStreamSettings back = custom;
        for (int step = 0; step < 4; step++) back = back.nextResolution(options, 1);
        assertEquals(1600, back.width);
        assertFalse(LiveStreamSettings.Options.currentOnly(custom).offersModes());
    }

    @Test public void hdrTogglesOnlyWhereAvailableAndCanAlwaysBeTurnedOff() {
        LiveStreamSettings sdr = new LiveStreamSettings(1920, 1080, 60, 20000, false);
        assertEquals(sdr, sdr.toggleHdr(new LiveStreamSettings.Options(COMMON, sdr, false)));
        assertTrue(sdr.toggleHdr(new LiveStreamSettings.Options(COMMON, sdr, true)).hdr);

        LiveStreamSettings hdr = new LiveStreamSettings(1920, 1080, 60, 20000, true);
        assertFalse(hdr.toggleHdr(new LiveStreamSettings.Options(COMMON, hdr, false)).hdr);
    }

    @Test public void emergencyKeepsTheFrameRateAndDropsResolutionToWhatTheLinkCarries() {
        LiveStreamSettings uhd = new LiveStreamSettings(3840, 2160, 60, 80000, true);
        LiveStreamSettings.Options options = new LiveStreamSettings.Options(COMMON, uhd, true);

        // 21 Mb/s carried: 15.5 Mb/s, and 1080p60 (usually 20) is within a third over it.
        assertEquals(new LiveStreamSettings(1920, 1080, 60, 15500, true),
                uhd.emergency(options, 21000));
    }

    @Test public void emergencyWithoutAnObservedRateLosesAQuarterOfTheBitrate() {
        LiveStreamSettings fhd = new LiveStreamSettings(1920, 1080, 60, 20000, false);
        LiveStreamSettings.Options options = new LiveStreamSettings.Options(COMMON, fhd, false);

        assertEquals(new LiveStreamSettings(1920, 1080, 60, 15000, false),
                fhd.emergency(options, 0));
        // More carried than sent: the stream's own bitrate is the ceiling.
        assertEquals(15000, fhd.emergency(options, 90000).bitrateKbps);
    }

    @Test public void emergencyNeverRaisesAnythingAndEndsAtTheSmallestMode() {
        LiveStreamSettings fast = new LiveStreamSettings(1920, 1080, 120, 28000, false);
        LiveStreamSettings.Options options = new LiveStreamSettings.Options(COMMON, fast, false);

        assertEquals(new LiveStreamSettings(1280, 720, 60, 4500, false),
                fast.emergency(options, 6000));
        assertEquals(1000, fast.emergency(options, 500).bitrateKbps);
        LiveStreamSettings slow = new LiveStreamSettings(1920, 1080, 30, 10000, false);
        assertEquals(new LiveStreamSettings(1920, 1080, 30, 7500, false),
                slow.emergency(new LiveStreamSettings.Options(COMMON, slow, false), 0));
        // Only the current mode known (the probe has not answered): only the bitrate drops.
        assertEquals(new LiveStreamSettings(1920, 1080, 120, 4500, false),
                fast.emergency(LiveStreamSettings.Options.currentOnly(fast), 6000));
    }

    @Test public void emergencyIsNeverFasterOrRicherThanTheStream() {
        // A custom 1080p30 stream on a host that offers only 720p60: nothing at or under 30 FPS.
        LiveStreamSettings custom = new LiveStreamSettings(1920, 1080, 30, 800, false);
        LiveStreamSettings.Options options = new LiveStreamSettings.Options(
                Collections.singletonList(mode(1280, 720, 60)), custom, false);

        LiveStreamSettings emergency = custom.emergency(options, 0);
        assertEquals(30, emergency.fps);
        assertEquals(800, emergency.bitrateKbps);
    }

    @Test public void encodedSettingsReadBack() {
        LiveStreamSettings settings = new LiveStreamSettings(2560, 1440, 120, 56500, true);
        assertEquals(settings, LiveStreamSettings.parse(settings.encode()));
        assertEquals(null, LiveStreamSettings.parse(null));
        assertEquals(null, LiveStreamSettings.parse("2560,1440,x,1,0"));
    }
}
