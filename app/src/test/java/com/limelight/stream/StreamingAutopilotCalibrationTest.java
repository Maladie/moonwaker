package com.limelight.stream;

import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamingAutopilotCalibrationTest {
    @Test public void usesThirtySecondWarmupAndTwoToFourMinuteMeasurement() {
        assertEquals(30_000L, StreamingAutopilotCalibration.WARMUP_MS);
        assertEquals(2 * 60_000L, StreamingAutopilotCalibration.MEASUREMENT_MS);
        assertEquals(4 * 60_000L, StreamingAutopilotCalibration.MAX_MEASUREMENT_MS);
    }

    @Test public void aResultNearALimitRunsTheFullFourMinutes() {
        // Loss 0.9% passes the 1% limit, but not a fifth less slack.
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                60, 59, .9, 20, .5, 5, 0, 0));

        assertTrue(result.passed);
        assertEquals(StreamingAutopilotCalibration.MAX_MEASUREMENT_MS, result.measuredMs);
    }

    @Test public void aDecisiveResultEndsAfterTwoMinutes() {
        StreamingAutopilotCalibration.Result passed = complete(new SampleValues(
                60, 59, 0, 20, .5, 5, 0, 0));
        // Loss 1.5% fails at the limit and at a quarter more slack: decided.
        StreamingAutopilotCalibration.Result failed = complete(new SampleValues(
                60, 59, 1.5, 20, .5, 5, 0, 0));

        assertEquals(StreamingAutopilotCalibration.MEASUREMENT_MS, passed.measuredMs);
        assertFalse(failed.passed);
        assertEquals(StreamingAutopilotCalibration.MEASUREMENT_MS, failed.measuredMs);
    }

    @Test public void aCleanStartDoesNotHideTheFailureThatEndsTheTest() {
        StreamingAutopilotCalibration calibration =
                new StreamingAutopilotCalibration("game", null, 1920, 1080, 60, 20_000);
        int warmupSeconds = (int) (StreamingAutopilotCalibration.WARMUP_MS / 1_000L);
        calibration.arm(0);
        int second = 0;
        for (; second < warmupSeconds + 70; second++) calibration.add(good(second));
        StreamingAutopilotCalibration.Progress progress = null;
        SampleValues lossy = new SampleValues(60, 59, 3, 20, .5, 5, 0, 0);
        for (int end = second + 20; second < end; second++) progress = calibration.add(lossy.at(second));

        assertEquals(StreamingAutopilotCalibration.Phase.COMPLETE, progress.phase);
        assertFalse(progress.result.passed);
        assertTrue(progress.result.reasons.contains(
                StreamingAutopilotCalibration.Reason.FRAME_LOSS_HIGH));
    }

    @Test public void windowsFailingOnDifferentLimitsStillEndInAFailure() {
        StreamingAutopilotCalibration calibration =
                new StreamingAutopilotCalibration("game", null, 1920, 1080, 60, 20_000);
        int warmupSeconds = (int) (StreamingAutopilotCalibration.WARMUP_MS / 1_000L);
        calibration.arm(0);
        int second = 0;
        for (; second < warmupSeconds; second++) calibration.add(good(second));
        // Loss 2.5%, a 34 ms decoder and a 34 ms host in turn: each window breaks one limit
        // twice over, yet every average stays under its limit (1%, 16.7 ms, 16.7 ms).
        SampleValues[] turns = {new SampleValues(60, 59, 2.5, 20, .5, 5, 0, 0),
                new SampleValues(60, 59, 0, 20, .5, 34, 0, 0),
                new SampleValues(60, 59, 0, 20, .5, 5, 34, 0)};
        StreamingAutopilotCalibration.Progress progress = null;
        for (int window = 0; window < 20; window++, second++) {
            progress = calibration.add(turns[window % 3].at(second));
        }

        assertEquals(StreamingAutopilotCalibration.Phase.COMPLETE, progress.phase);
        assertFalse(progress.result.passed);
    }

    @Test public void jitterOrAStillSceneNeverEndTheTestEarly() {
        // RTT variance 3 ms (over twice its 1 ms limit) and 25 received FPS of 60.
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                25, 59, 0, 20, 3, 5, 0, 0));

        assertTrue(result.measuredMs >= StreamingAutopilotCalibration.MEASUREMENT_MS);
    }

    @Test public void twentySecondsOfClearFailureEndTheTestEarly() {
        // 5% loss is over even twice the 1% limit in every window.
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                60, 59, 5, 20, .5, 5, 0, 0));

        assertFalse(result.passed);
        assertTrue(result.lowerBitrateSuggested);
        assertEquals(StreamingAutopilotCalibration.CLEAR_FAILURE_MS, result.measuredMs);
        assertTrue(result.earlyEnd);
    }

    @Test public void ignoresPreArmThenUsesConfiguredWarmupAndMeasurement() {
        StreamingAutopilotCalibration calibration = new StreamingAutopilotCalibration("game", null, 1920, 1080, 60, 20_000);
        int warmupSeconds = (int) (StreamingAutopilotCalibration.WARMUP_MS / 1_000L);
        int measurementSeconds =
                (int) (StreamingAutopilotCalibration.MEASUREMENT_MS / 1_000L);
        calibration.arm(1_000L);
        assertEquals(StreamingAutopilotCalibration.Phase.WARMING_UP,
                calibration.add(good(0)).phase);
        for (int second = 1; second <= warmupSeconds; second++) {
            assertEquals(StreamingAutopilotCalibration.Phase.WARMING_UP,
                    calibration.add(good(second)).phase);
        }
        int measurementStart = warmupSeconds + 1;
        for (int second = measurementStart;
             second < measurementStart + measurementSeconds - 1; second++) {
            assertEquals(StreamingAutopilotCalibration.Phase.MEASURING,
                    calibration.add(good(second)).phase);
        }

        StreamingAutopilotCalibration.Progress complete = calibration.add(
                good(measurementStart + measurementSeconds - 1));

        assertEquals(StreamingAutopilotCalibration.Phase.COMPLETE, complete.phase);
        assertTrue(complete.result.passed);
        assertEquals(measurementSeconds, complete.result.sampleCount);
        assertEquals(60d, complete.result.receivedFps, .01);
        assertEquals(59d, complete.result.renderedFps, .01);
    }

    @Test public void lossAndJitterRecommendLowerBitrate() {
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                60, 59, 2, 20, 2, 5, 5, 1));

        assertFalse(result.passed);
        assertTrue(result.lowerBitrateSuggested);
        assertFalse(result.lowerModeSuggested);
        assertTrue(result.reasons.contains(
                StreamingAutopilotCalibration.Reason.FRAME_LOSS_HIGH));
        assertTrue(result.reasons.contains(
                StreamingAutopilotCalibration.Reason.RTT_VARIANCE_HIGH));
    }

    @Test public void fpsAndPipelineLatencyRecommendLowerModeUsingFrameInterval() {
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                50, 49, 0, 20, .5, 17, 18, .5));

        assertFalse(result.passed);
        assertFalse(result.lowerBitrateSuggested);
        assertTrue(result.lowerModeSuggested);
        assertTrue(result.reasons.contains(
                StreamingAutopilotCalibration.Reason.RECEIVED_FPS_LOW));
        assertTrue(result.reasons.contains(
                StreamingAutopilotCalibration.Reason.DECODER_LATENCY_HIGH));
        assertTrue(result.reasons.contains(
                StreamingAutopilotCalibration.Reason.HOST_PROCESSING_LATENCY_HIGH));
        assertTrue(result.reasons.contains(
                StreamingAutopilotCalibration.Reason.HOST_PROCESSING_COVERAGE_LOW));
    }

    @Test public void networkFailureReducesBitrateByQuarterAndRoundsDown() {
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                60, 59, 2, 20, .5, 5, 5, 1));

        StreamingAutopilotCalibration.Adjustment adjusted =
                StreamingAutopilotCalibration.adjustedSettings(
                        1920, 1080, 60, 20_400,
                        result.lowerBitrateSuggested, result.lowerModeSuggested);

        assertEquals(1920, adjusted.width);
        assertEquals(1080, adjusted.height);
        assertEquals(60, adjusted.fps);
        assertEquals(15_000, adjusted.bitrateKbps);
        assertTrue(adjusted.conservative);
    }

    @Test public void pipelineFailureUsesNextLowerFpsAndCapsItsBitrate() {
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                80, 79, 0, 20, .5, 5, 5, 1), 120);

        StreamingAutopilotCalibration.Adjustment adjusted =
                StreamingAutopilotCalibration.adjustedSettings(
                        1920, 1080, 120, 100_000,
                        result.lowerBitrateSuggested, result.lowerModeSuggested);

        assertEquals(1920, adjusted.width);
        assertEquals(1080, adjusted.height);
        assertEquals(90, adjusted.fps);
        assertEquals(PreferenceConfiguration.getDefaultBitrate(
                "1920x1080", "90"), adjusted.bitrateKbps);
    }

    @Test public void pipelineFailureAtThirtyFpsUsesNextLowerResolution() {
        StreamingAutopilotCalibration.Result result = complete(new SampleValues(
                20, 20, 0, 20, .5, 40, 40, 1), 30);

        StreamingAutopilotCalibration.Adjustment adjusted =
                StreamingAutopilotCalibration.adjustedSettings(
                        1920, 1080, 30, 100_000,
                        result.lowerBitrateSuggested, result.lowerModeSuggested);

        assertEquals(1280, adjusted.width);
        assertEquals(720, adjusted.height);
        assertEquals(30, adjusted.fps);
        assertEquals(PreferenceConfiguration.getDefaultBitrate(
                "1280x720", "30"), adjusted.bitrateKbps);
    }

    private static StreamingAutopilotCalibration.Result complete(SampleValues values) {
        return complete(values, 60);
    }

    private static StreamingAutopilotCalibration.Result complete(
            SampleValues values, int targetFps) {
        StreamingAutopilotCalibration calibration =
                new StreamingAutopilotCalibration("game", null, 1920, 1080, targetFps, 20_000);
        int warmupSeconds = (int) (StreamingAutopilotCalibration.WARMUP_MS / 1_000L);
        int measurementSeconds =
                (int) (StreamingAutopilotCalibration.MAX_MEASUREMENT_MS / 1_000L);
        calibration.arm(0);
        for (int second = 0; second < warmupSeconds; second++) {
            calibration.add(values.at(second));
        }
        for (int second = warmupSeconds;
             second < warmupSeconds + measurementSeconds; second++) {
            StreamingAutopilotCalibration.Progress progress =
                    calibration.add(values.at(second));
            if (progress.phase == StreamingAutopilotCalibration.Phase.COMPLETE) {
                return progress.result;
            }
        }
        throw new AssertionError("Calibration never completed");
    }

    @Test public void anOpenMenuPausesTheMeasurement() {
        StreamingAutopilotCalibration calibration =
                new StreamingAutopilotCalibration("game", null, 1920, 1080, 60, 20_000);
        int warmupSeconds = (int) (StreamingAutopilotCalibration.WARMUP_MS / 1_000L);
        calibration.arm(0);
        for (int second = 0; second < warmupSeconds; second++) calibration.add(good(second));
        calibration.add(good(warmupSeconds));
        assertEquals(2_000L, calibration.add(good(warmupSeconds + 1)).completedMs);

        // The menu is open for 10 s: a still scene's windows are left out, and so is the one
        // the menu closed in.
        calibration.pause();
        assertTrue(calibration.isPaused());
        for (int second = warmupSeconds + 2; second < warmupSeconds + 12; second++) {
            assertEquals(2_000L, calibration.add(still(second)).completedMs);
        }
        calibration.resume((warmupSeconds + 12) * 1_000L + 500);
        assertEquals(2_000L, calibration.add(still(warmupSeconds + 12)).completedMs);
        assertEquals(3_000L, calibration.add(good(warmupSeconds + 13)).completedMs);
    }

    @Test public void oneWindowsIssuesUseTheCandidatesFrameRate() {
        StreamingAutopilotCalibration calibration =
                new StreamingAutopilotCalibration("game", null, 1920, 1080, 60, 20_000);
        assertTrue(calibration.issues(good(0)).isEmpty());
        assertTrue(calibration.issues(still(0)).contains(
                StreamingAutopilotCalibration.Reason.RECEIVED_FPS_LOW));
    }

    private static StreamingAutopilotCalibration.Sample still(int second) {
        return new SampleValues(5, 5, 0, 20, .5, 5, 0, 0).at(second);
    }

    private static StreamingAutopilotCalibration.Sample good(int second) {
        return new SampleValues(60, 59, 0, 20, .5, 5, 0, 0).at(second);
    }

    private static final class SampleValues {
        final double received;
        final double rendered;
        final double loss;
        final double rtt;
        final double variance;
        final double decoder;
        final double host;
        final double hostCoverage;

        SampleValues(double received, double rendered, double loss, double rtt,
                     double variance, double decoder, double host) {
            this(received, rendered, loss, rtt, variance, decoder, host, 1);
        }

        SampleValues(double received, double rendered, double loss, double rtt,
                     double variance, double decoder, double host,
                     double hostCoverage) {
            this.received = received;
            this.rendered = rendered;
            this.loss = loss;
            this.rtt = rtt;
            this.variance = variance;
            this.decoder = decoder;
            this.host = host;
            this.hostCoverage = hostCoverage;
        }

        StreamingAutopilotCalibration.Sample at(int second) {
            return new StreamingAutopilotCalibration.Sample(second * 1_000L,
                    (second + 1) * 1_000L, received, rendered, loss, rtt,
                    variance, decoder, host, hostCoverage);
        }
    }
}
