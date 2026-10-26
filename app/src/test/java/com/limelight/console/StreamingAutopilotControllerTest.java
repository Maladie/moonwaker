package com.limelight.console;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.stream.StreamingAutopilot;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamingAutopilotControllerTest {
    private static final StreamingAutopilot.Capabilities CAPABILITIES =
            StreamingAutopilot.Capabilities.upTo(3840, 2160, 120);

    @Test public void measuredSampleProducesBudgetAndRecommendation() {
        StreamingAutopilotController.NetworkEvaluation result = combine(
                StreamingAutopilotController.NetworkStatus.MEASURED,
                80_000_000L, 8_000_000_000L);

        assertEquals(StreamingAutopilotController.NetworkStatus.MEASURED, result.status);
        assertEquals(80_000, result.goodputKbps.getAsLong());
        assertEquals(59_000, result.safeBitrateKbps.getAsInt());
        assertTrue(result.sample.isPresent());
        assertTrue(result.recommendation.isPresent());
    }

    @Test public void failedMeasurementUsesCapabilityOnlyFallback() {
        StreamingAutopilotController.NetworkEvaluation result = combine(
                StreamingAutopilotController.NetworkStatus.FAILED, 0, 0);

        assertEquals(StreamingAutopilotController.NetworkStatus.FAILED, result.status);
        assertFalse(result.sample.isPresent());
        assertFalse(result.safeBitrateKbps.isPresent());
        assertEquals(StreamingAutopilot.Confidence.LOW,
                result.recommendation.get().confidence);
    }

    @Test public void unavailableMeasurementUsesExplicitCapabilityOnlyFallback() {
        StreamingAutopilotController.NetworkEvaluation result = combine(
                StreamingAutopilotController.NetworkStatus.UNAVAILABLE, 0, 0);

        assertEquals(StreamingAutopilotController.NetworkStatus.UNAVAILABLE, result.status);
        assertFalse(result.sample.isPresent());
        assertTrue(result.recommendation.isPresent());
        assertEquals(StreamingAutopilot.Confidence.LOW,
                result.recommendation.get().confidence);
    }

    @Test public void unusableMeasuredBudgetHasNoRecommendation() {
        StreamingAutopilotController.NetworkEvaluation result = combine(
                StreamingAutopilotController.NetworkStatus.MEASURED,
                1_000_000L, 8_000_000_000L);

        assertEquals(StreamingAutopilotController.NetworkStatus.TOO_SLOW, result.status);
        assertEquals(1_000, result.goodputKbps.getAsLong());
        assertEquals(0, result.safeBitrateKbps.getAsInt());
        assertFalse(result.recommendation.isPresent());
    }

    @Test public void sessionHistoryKeepsTheNewestFiveAndRoundTrips() {
        String stored = null;
        for (int i = 1; i <= 7; i++) {
            stored = StreamingAutopilotController.appendSession(stored,
                    new StreamingAutopilot.SessionSummary(1920, 1080, 60, i * 1_000,
                            200_000L + i, 59.5, 58.25, 1.5, 12, 3.5, 4.25, 900 * i));
        }
        List<StreamingAutopilot.SessionSummary> sessions =
                StreamingAutopilotController.parseSessions(stored);

        assertEquals(StreamingAutopilot.SESSION_HISTORY_LIMIT, sessions.size());
        assertEquals(3_000, sessions.get(0).bitrateKbps);
        StreamingAutopilot.SessionSummary newest = sessions.get(4);
        assertEquals(7_000, newest.bitrateKbps);
        assertEquals(1920, newest.width);
        assertEquals(1080, newest.height);
        assertEquals(60, newest.fps);
        assertEquals(200_007L, newest.measuredMs);
        assertEquals(59.5, newest.receivedFps, 0);
        assertEquals(58.25, newest.renderedFps, 0);
        assertEquals(1.5, newest.frameLossPercent, 0);
        assertEquals(12, newest.rttMs, 0);
        assertEquals(3.5, newest.rttVarianceMs, 0);
        assertEquals(4.25, newest.decoderLatencyMs, 0);
        assertEquals(6_300, newest.actualBitrateKbps);
    }

    @Test public void unreadableSessionHistoryIsNoEvidence() {
        assertTrue(StreamingAutopilotController.parseSessions(null).isEmpty());
        assertTrue(StreamingAutopilotController.parseSessions("{").isEmpty());
        assertTrue(StreamingAutopilotController.parseSessions("[{\"width\":1}]").isEmpty());
    }

    @Test public void gyroIsProposedForShootersActionAndThirdPerson() {
        org.junit.Assert.assertTrue(StreamingAutopilotController.proposesGyro("Shooter, RPG"));
        org.junit.Assert.assertTrue(StreamingAutopilotController.proposesGyro("Action"));
        org.junit.Assert.assertTrue(StreamingAutopilotController.proposesGyro(
                "Third-Person Shooter"));
        org.junit.Assert.assertFalse(StreamingAutopilotController.proposesGyro("Puzzle, Strategy"));
        org.junit.Assert.assertFalse(StreamingAutopilotController.proposesGyro(null));
    }

    private static StreamingAutopilotController.NetworkEvaluation combine(
            StreamingAutopilotController.NetworkStatus status, long bytes, long elapsedNanos) {
        return StreamingAutopilotController.combine(
                CAPABILITIES, CAPABILITIES, PreferenceConfiguration.FormatOption.AUTO,
                status, bytes, elapsedNanos);
    }
}
