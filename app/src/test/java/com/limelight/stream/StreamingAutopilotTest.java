package com.limelight.stream;

import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StreamingAutopilotTest {
    @Test public void safeBudgetKeepsHeadroomAndAudioAtEightyMegabits() {
        assertEquals(59_000, StreamingAutopilot.safeBitrateBudgetKbps(
                80_000_000L, 8_000_000_000L));
    }

    @Test public void safeBudgetKeepsHeadroomAndAudioAtFiveHundredMegabits() {
        assertEquals(374_000, StreamingAutopilot.safeBitrateBudgetKbps(
                500_000_000L, 8_000_000_000L));
    }

    @Test public void safeBudgetReturnsZeroBelowUsefulMinimum() {
        assertEquals(0, StreamingAutopilot.safeBitrateBudgetKbps(
                1_000_000L, 8_000_000_000L));
    }

    @Test public void safeBudgetClampsBeforeIntegerOverflow() {
        assertEquals(2_147_483_500, StreamingAutopilot.safeBitrateBudgetKbps(
                Long.MAX_VALUE, 1));
    }

    @Test public void skipsModeWhoseClientAndHostCodecPathsAreDisjoint() {
        StreamingAutopilot.Capabilities client = codecCapabilities(
                new StreamingAutopilot.Mode[]{mode(3840, 2160, 60)},
                new StreamingAutopilot.Mode[]{mode(1920, 1080, 60)});
        StreamingAutopilot.Capabilities host = codecCapabilities(
                new StreamingAutopilot.Mode[0],
                new StreamingAutopilot.Mode[]{mode(3840, 2160, 60), mode(1920, 1080, 60)});

        StreamingAutopilot.Recommendation result = StreamingAutopilot.recommend(
                client, host, OptionalInt.of(100_000));

        assertMode(result, 1920, 1080, 60, 20_000);
    }

    @Test public void forceH264NeverUsesAnHevcOnlyMode() {
        StreamingAutopilot.Capabilities client = codecCapabilities(
                new StreamingAutopilot.Mode[]{mode(1280, 720, 60)},
                new StreamingAutopilot.Mode[]{mode(1920, 1080, 60)});
        StreamingAutopilot.Capabilities host = codecCapabilities(
                new StreamingAutopilot.Mode[]{mode(1280, 720, 60)},
                new StreamingAutopilot.Mode[]{mode(1920, 1080, 60)});

        StreamingAutopilot.Recommendation result = StreamingAutopilot.recommend(
                client, host, OptionalInt.of(100_000),
                com.limelight.preferences.PreferenceConfiguration.FormatOption.FORCE_H264);

        assertMode(result, 1280, 720, 60, 10_000);
    }

    @Test public void explicitCapabilitiesDoNotCombineUnsupportedResolutionAndFps() {
        StreamingAutopilot.Capabilities client = new StreamingAutopilot.Capabilities(
                new StreamingAutopilot.Mode(3840, 2160, 60),
                new StreamingAutopilot.Mode(1920, 1080, 120));

        StreamingAutopilot.Recommendation result = StreamingAutopilot.recommend(
                client, StreamingAutopilot.Capabilities.upTo(3840, 2160, 120),
                OptionalInt.of(150_000));

        assertMode(result, 3840, 2160, 60, 80_000);
    }

    @Test public void selectsTheBestCandidateWithinEveryMeasuredLimit() {
        StreamingAutopilot.Recommendation result = StreamingAutopilot.recommend(
                capabilities(3840, 2160, 120),
                capabilities(2560, 1440, 60),
                OptionalInt.of(40_000));

        assertMode(result, 2560, 1440, 60, 40_000);
        assertEquals(StreamingAutopilot.Confidence.HIGH, result.confidence);
    }

    @Test public void safeBitrateBudgetSelectsALowerDiscreteMode() {
        StreamingAutopilot.Recommendation result = StreamingAutopilot.recommend(
                capabilities(3840, 2160, 120),
                capabilities(3840, 2160, 120),
                OptionalInt.of(20_000));

        assertMode(result, 1920, 1080, 60, 20_000);
        assertTrue(result.bitrateKbps <= 20_000);
    }

    @Test public void missingNetworkMeasurementUsesAConservativeFallback() {
        StreamingAutopilot.Recommendation result = StreamingAutopilot.recommend(
                capabilities(3840, 2160, 120),
                capabilities(3840, 2160, 120),
                OptionalInt.empty());

        assertMode(result, 1280, 720, 60, 10_000);
        assertEquals(StreamingAutopilot.Confidence.LOW, result.confidence);
        assertEquals(StreamingAutopilot.Reason.NETWORK_MEASUREMENT_UNAVAILABLE,
                result.reasons.get(0));
    }

    @Test public void fallbackAndVeryLowBudgetNeverExceedLimits() {
        StreamingAutopilot.Capabilities client = capabilities(854, 480, 30);
        StreamingAutopilot.Capabilities host = capabilities(1920, 1080, 120);

        StreamingAutopilot.Recommendation fallback = StreamingAutopilot.recommend(
                client, host, OptionalInt.empty());
        assertMode(fallback, 854, 480, 30, 2_000);

        StreamingAutopilot.Recommendation constrained = StreamingAutopilot.recommend(
                client, host, OptionalInt.of(750));
        assertMode(constrained, 640, 360, 30, 500);
        assertEquals(StreamingAutopilot.Confidence.LOW, constrained.confidence);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsABudgetBelowTheSupportedStreamMinimum() {
        StreamingAutopilot.recommend(
                capabilities(1920, 1080, 60),
                capabilities(1920, 1080, 60),
                OptionalInt.of(499));
    }

    @Test public void sessionQualitySkipsUnshownTimeAndWarmUpAndNeedsThreeMinutes() {
        StreamingAutopilot.SessionQuality quality =
                new StreamingAutopilot.SessionQuality(1920, 1080, 60, 20_000);
        quality.add(window(0, 0, 50), 0);
        quality.shown(1_000L);
        long warmUpSeconds = StreamingAutopilot.SESSION_WARMUP_MS / 1_000L;
        for (int second = 1; second < 1 + warmUpSeconds; second++) {
            quality.add(window(second, 50, 50), second * 2_500_000L);
        }
        long measuredSeconds = StreamingAutopilot.SESSION_MIN_MEASURED_MS / 1_000L;
        long first = 1 + warmUpSeconds;
        for (long second = first; second < first + measuredSeconds - 1; second++) {
            quality.add(window(second, 0.5, 2), second * 2_500_000L);
        }
        assertNull(quality.summary());

        long last = first + measuredSeconds - 1;
        quality.add(window(last, 0.5, 2), last * 2_500_000L);
        StreamingAutopilot.SessionSummary summary = quality.summary();

        assertEquals(StreamingAutopilot.SESSION_MIN_MEASURED_MS, summary.measuredMs);
        assertEquals(0.5, summary.frameLossPercent, 1e-9);
        assertEquals(2, summary.rttVarianceMs, 1e-9);
        assertEquals(20_000, summary.actualBitrateKbps);
        assertEquals(20_000, summary.bitrateKbps);
    }

    @Test public void unsupportedByteCounterLeavesActualBitrateUnknown() {
        StreamingAutopilot.SessionQuality quality =
                new StreamingAutopilot.SessionQuality(1920, 1080, 60, 20_000);
        quality.shown(0L);
        long seconds = (StreamingAutopilot.SESSION_WARMUP_MS
                + StreamingAutopilot.SESSION_MIN_MEASURED_MS) / 1_000L;
        for (long second = 0; second < seconds; second++) {
            quality.add(window(second, 0, 1), -1L);
        }
        assertEquals(0, quality.summary().actualBitrateKbps);
    }

    @Test public void goodLatestSessionHasNoAdvice() {
        assertFalse(StreamingAutopilot.adviseAfterSessions(Arrays.asList(
                session(1920, 1080, 60, 20_000, 5, 1, 59, 3),
                session(1920, 1080, 60, 20_000, 0.2, 1, 59, 3))).isPresent());
        assertFalse(StreamingAutopilot.adviseAfterSessions(
                Collections.emptyList()).isPresent());
    }

    @Test public void frameLossOrJitterLowersBitrateByAQuarter() {
        StreamingAutopilot.SessionAdvice advice = StreamingAutopilot.adviseAfterSessions(
                Collections.singletonList(session(1920, 1080, 60, 20_400, 3, 1, 59, 3)))
                .get();
        assertAdvice(advice, 1920, 1080, 60, 15_000);
        assertEquals(Collections.singletonList(StreamingAutopilot.SessionIssue.FRAME_LOSS_HIGH),
                advice.issues);

        StreamingAutopilot.SessionAdvice jitter = StreamingAutopilot.adviseAfterSessions(
                Collections.singletonList(session(1920, 1080, 60, 20_000, 0, 20, 59, 3)))
                .get();
        assertAdvice(jitter, 1920, 1080, 60, 15_000);
        assertTrue(jitter.issues.contains(StreamingAutopilot.SessionIssue.NETWORK_JITTER_HIGH));
    }

    @Test public void decoderTroubleLowersTheFrameRateAndCapsItsBitrate() {
        StreamingAutopilot.SessionAdvice dropped = StreamingAutopilot.adviseAfterSessions(
                Collections.singletonList(session(1920, 1080, 60, 40_000, 0, 1, 50, 3)))
                .get();
        int default1080p30 = PreferenceConfiguration.getDefaultBitrate("1920x1080", "30");
        assertAdvice(dropped, 1920, 1080, 30, Math.min(40_000, default1080p30));
        assertEquals(Collections.singletonList(
                StreamingAutopilot.SessionIssue.FRAMES_DROPPED_BY_DECODER), dropped.issues);

        StreamingAutopilot.SessionAdvice slow = StreamingAutopilot.adviseAfterSessions(
                Collections.singletonList(session(1920, 1080, 60, 40_000, 0, 1, 59, 17)))
                .get();
        assertEquals(30, slow.fps);
        assertTrue(slow.issues.contains(StreamingAutopilot.SessionIssue.DECODER_LATENCY_HIGH));
    }

    @Test public void receivedFpsAloneIsNotJudged() {
        // A still picture: the host sends 20 FPS and every one of them is shown.
        assertFalse(StreamingAutopilot.adviseAfterSessions(Collections.singletonList(
                new StreamingAutopilot.SessionSummary(1920, 1080, 60, 20_000, 600_000L,
                        20, 20, 0, 5, 1, 3, 4_000))).isPresent());
    }

    @Test public void customModeOnlyLosesBitrate() {
        StreamingAutopilot.SessionAdvice advice = StreamingAutopilot.adviseAfterSessions(
                Collections.singletonList(session(3440, 1440, 60, 40_000, 0, 1, 50, 3)))
                .get();
        assertAdvice(advice, 3440, 1440, 60, 30_000);
    }

    @Test public void adviceNeedsHalfOfTheSessionsWithTheLatestSettingsPoor() {
        StreamingAutopilot.SessionSummary good = session(1920, 1080, 60, 20_000, 0, 1, 59, 3);
        StreamingAutopilot.SessionSummary poor = session(1920, 1080, 60, 20_000, 4, 1, 59, 3);
        StreamingAutopilot.SessionSummary otherPoor =
                session(2560, 1440, 60, 40_000, 4, 1, 59, 3);

        assertFalse(StreamingAutopilot.adviseAfterSessions(
                Arrays.asList(good, good, poor)).isPresent());
        StreamingAutopilot.SessionAdvice half = StreamingAutopilot.adviseAfterSessions(
                Arrays.asList(poor, good, good, poor)).get();
        assertEquals(2, half.poorSessions);
        assertEquals(4, half.sessions);
        StreamingAutopilot.SessionAdvice afterChange = StreamingAutopilot.adviseAfterSessions(
                Arrays.asList(otherPoor, otherPoor, good, poor)).get();
        assertEquals(1, afterChange.poorSessions);
        assertEquals(2, afterChange.sessions);
    }

    private static StreamingAutopilotCalibration.Sample window(long second, double loss,
                                                               double rttVarianceMs) {
        return new StreamingAutopilotCalibration.Sample(second * 1_000L,
                (second + 1) * 1_000L, 60, 59, loss, 10, rttVarianceMs, 3, 0, 0);
    }

    private static StreamingAutopilot.SessionSummary session(int width, int height, int fps,
                                                             int bitrateKbps, double loss,
                                                             double rttVarianceMs,
                                                             double renderedFps,
                                                             double decoderMs) {
        return new StreamingAutopilot.SessionSummary(width, height, fps, bitrateKbps,
                600_000L, 60, renderedFps, loss, 10, rttVarianceMs, decoderMs, bitrateKbps);
    }

    private static void assertAdvice(StreamingAutopilot.SessionAdvice advice,
                                     int width, int height, int fps, int bitrateKbps) {
        assertEquals(width, advice.width);
        assertEquals(height, advice.height);
        assertEquals(fps, advice.fps);
        assertEquals(bitrateKbps, advice.bitrateKbps);
    }

    private static StreamingAutopilot.Capabilities capabilities(int width, int height, int fps) {
        return new StreamingAutopilot.Capabilities(width, height, fps);
    }

    private static StreamingAutopilot.Capabilities codecCapabilities(
            StreamingAutopilot.Mode[] h264, StreamingAutopilot.Mode[] hevc) {
        return StreamingAutopilot.forCodecModes(Arrays.asList(h264), Arrays.asList(hevc));
    }

    private static StreamingAutopilot.Mode mode(int width, int height, int fps) {
        return new StreamingAutopilot.Mode(width, height, fps);
    }

    private static void assertMode(StreamingAutopilot.Recommendation result,
                                   int width, int height, int fps, int bitrateKbps) {
        assertEquals(width, result.width);
        assertEquals(height, result.height);
        assertEquals(fps, result.fps);
        assertEquals(bitrateKbps, result.bitrateKbps);
    }
}
