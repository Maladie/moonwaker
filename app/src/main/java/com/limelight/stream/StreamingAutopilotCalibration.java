package com.limelight.stream;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One game's calibration stream: the candidate settings it runs, and the pure evaluation of its
 * one-second video-stat windows. The decoder thread adds windows while the activity arms and
 * pauses it, so the stateful methods are synchronized.
 */
public final class StreamingAutopilotCalibration {
    public static final long WARMUP_MS = 30_000L;
    /** The measurement a decisive result needs; one near a limit runs to the maximum. */
    public static final long MEASUREMENT_MS = 2 * 60_000L;
    public static final long MAX_MEASUREMENT_MS = 4 * 60_000L;
    /**
     * Windows this long in a row that break a limit even at twice its slack end the test.
     * RTT variance (a 1 ms limit) and received FPS (a still scene sends fewer frames) never
     * fail a window clearly.
     */
    public static final long CLEAR_FAILURE_MS = 20_000L;
    /**
     * Limit slack factors: a result is decisive when it breaks the same limits at a fifth less
     * and a quarter more slack; a window fails clearly when it breaks one at twice the slack.
     */
    static final double STRICT_MARGIN = 0.8;
    static final double LOOSE_MARGIN = 1.25;
    static final double CLEAR_FAILURE_MARGIN = 2.0;
    public static final double MIN_FPS_RATIO = 0.90;
    public static final double MAX_FRAME_LOSS_PERCENT = 1.0;
    public static final double MAX_RTT_AND_VARIANCE_MS = 100.0;
    public static final double MAX_RTT_VARIANCE_MS = 1.0;
    public static final double MIN_HOST_PROCESSING_COVERAGE_RATIO = 0.90;

    public enum Phase { WAITING, WARMING_UP, MEASURING, COMPLETE }

    public enum Reason {
        RECEIVED_FPS_LOW,
        RENDERED_FPS_LOW,
        FRAME_LOSS_HIGH,
        RTT_HIGH,
        RTT_VARIANCE_HIGH,
        DECODER_LATENCY_HIGH,
        HOST_PROCESSING_LATENCY_HIGH,
        HOST_PROCESSING_COVERAGE_LOW
    }

    public static final class Sample {
        public final long windowStartMs;
        public final long windowEndMs;
        public final double receivedFps;
        public final double renderedFps;
        public final double frameLossPercent;
        public final double rttMs;
        public final double rttVarianceMs;
        public final double decoderLatencyMs;
        public final double hostProcessingLatencyMs;
        public final double hostProcessingReportedRatio;

        public Sample(long windowStartMs, long windowEndMs, double receivedFps,
                      double renderedFps, double frameLossPercent, double rttMs,
                      double rttVarianceMs, double decoderLatencyMs,
                      double hostProcessingLatencyMs,
                      double hostProcessingReportedRatio) {
            if (windowStartMs < 0 || windowEndMs <= windowStartMs
                    || !nonNegative(receivedFps, renderedFps, frameLossPercent, rttMs,
                    rttVarianceMs, decoderLatencyMs, hostProcessingLatencyMs,
                    hostProcessingReportedRatio) || hostProcessingReportedRatio > 1) {
                throw new IllegalArgumentException("Invalid calibration sample");
            }
            this.windowStartMs = windowStartMs;
            this.windowEndMs = windowEndMs;
            this.receivedFps = receivedFps;
            this.renderedFps = renderedFps;
            this.frameLossPercent = frameLossPercent;
            this.rttMs = rttMs;
            this.rttVarianceMs = rttVarianceMs;
            this.decoderLatencyMs = decoderLatencyMs;
            this.hostProcessingLatencyMs = hostProcessingLatencyMs;
            this.hostProcessingReportedRatio = hostProcessingReportedRatio;
        }

        long durationMs() {
            return windowEndMs - windowStartMs;
        }

        private static boolean nonNegative(double... values) {
            for (double value : values) {
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class Result {
        public final boolean passed;
        /** A limit broken clearly for {@link #CLEAR_FAILURE_MS} ended the test early. */
        public final boolean earlyEnd;
        public final boolean lowerBitrateSuggested;
        public final boolean lowerModeSuggested;
        public final int sampleCount;
        public final long measuredMs;
        public final double receivedFps;
        public final double renderedFps;
        public final double frameLossPercent;
        public final double rttMs;
        public final double rttVarianceMs;
        public final double decoderLatencyMs;
        public final double hostProcessingLatencyMs;
        public final double hostProcessingReportedRatio;
        public final List<Reason> reasons;

        private Result(boolean passed, boolean earlyEnd, boolean lowerBitrateSuggested,
                       boolean lowerModeSuggested, int sampleCount, Totals totals,
                       List<Reason> reasons) {
            this.passed = passed;
            this.earlyEnd = earlyEnd;
            this.lowerBitrateSuggested = lowerBitrateSuggested;
            this.lowerModeSuggested = lowerModeSuggested;
            this.sampleCount = sampleCount;
            measuredMs = totals.durationMs;
            receivedFps = totals.average(totals.receivedFps);
            renderedFps = totals.average(totals.renderedFps);
            frameLossPercent = totals.average(totals.frameLossPercent);
            rttMs = totals.average(totals.rttMs);
            rttVarianceMs = totals.average(totals.rttVarianceMs);
            decoderLatencyMs = totals.average(totals.decoderLatencyMs);
            hostProcessingLatencyMs = totals.average(totals.hostProcessingLatencyMs);
            hostProcessingReportedRatio = totals.average(
                    totals.hostProcessingReportedRatio);
            this.reasons = Collections.unmodifiableList(reasons);
        }
    }

    public static final class Progress {
        public final Phase phase;
        public final long completedMs;
        public final long totalMs;
        public final Result result;

        private Progress(Phase phase, long completedMs, long totalMs, Result result) {
            this.phase = phase;
            this.completedMs = completedMs;
            this.totalMs = totalMs;
            this.result = result;
        }
    }

    public static final class Adjustment {
        public final int width;
        public final int height;
        public final int fps;
        public final int bitrateKbps;
        public final boolean conservative;

        private Adjustment(int width, int height, int fps, int bitrateKbps,
                           boolean conservative) {
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrateKbps = bitrateKbps;
            this.conservative = conservative;
        }
    }

    /** The game whose settings the result is for (its settings key). */
    public final String appKey;
    /** The analysis variant under test; null for the settings the stream ran already. */
    public final StreamingAutopilot.VariantKind variant;
    public final int width;
    public final int height;
    public final int fps;
    public final int bitrateKbps;
    private final Totals totals = new Totals();
    private long armedAtMs = -1L;
    private boolean paused;
    /** Windows starting before this are left out: the menu was open in them. */
    private long resumedAtMs = -1L;
    private long warmupMs;
    private long measurementMs = MEASUREMENT_MS;
    /** The windows failing clearly in a row, what an early end is judged on. */
    private Totals clearFailure = new Totals();
    private int clearFailureCount;
    private final java.util.EnumSet<Reason> clearFailureReasons =
            java.util.EnumSet.noneOf(Reason.class);
    private int sampleCount;
    private Result result;

    public StreamingAutopilotCalibration(String appKey, StreamingAutopilot.VariantKind variant,
                                         int width, int height, int fps, int bitrateKbps) {
        if (appKey == null || appKey.trim().isEmpty() || width <= 0 || height <= 0 || fps <= 0
                || bitrateKbps < 500) {
            throw new IllegalArgumentException("Invalid calibration candidate");
        }
        this.appKey = appKey.trim();
        this.variant = variant;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrateKbps = bitrateKbps;
    }

    public synchronized void arm(long nowMs) {
        if (nowMs < 0 || armedAtMs >= 0) throw new IllegalStateException("Calibration already armed");
        armedAtMs = nowMs;
    }

    public synchronized boolean isArmed() {
        return armedAtMs >= 0;
    }

    /**
     * The stream menu is open: nobody plays and the scene stands still, so the host sends fewer
     * frames. Nothing is measured until {@link #resume}.
     */
    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume(long nowMs) {
        paused = false;
        resumedAtMs = nowMs;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized Progress add(Sample sample) {
        if (result != null) return progress(Phase.COMPLETE, totals.durationMs, result);
        if (armedAtMs < 0) return progress(Phase.WAITING, 0, null);
        if (paused || sample.windowStartMs < armedAtMs || sample.windowStartMs < resumedAtMs) {
            return warmupMs < WARMUP_MS ? progress(Phase.WARMING_UP, warmupMs, null)
                    : progress(Phase.MEASURING, totals.durationMs, null);
        }
        if (warmupMs < WARMUP_MS) {
            warmupMs += sample.durationMs();
            return progress(Phase.WARMING_UP, Math.min(warmupMs, WARMUP_MS), null);
        }
        totals.add(sample);
        sampleCount++;
        List<Reason> clear = issues(sample, CLEAR_FAILURE_MARGIN);
        clear.remove(Reason.RTT_VARIANCE_HIGH);
        clear.remove(Reason.RECEIVED_FPS_LOW);
        if (clear.isEmpty()) {
            clearFailure = new Totals();
            clearFailureCount = 0;
            clearFailureReasons.clear();
        } else {
            clearFailure.add(sample);
            clearFailureCount++;
            clearFailureReasons.addAll(clear);
        }
        if (clearFailure.durationMs >= CLEAR_FAILURE_MS) {
            // Windows failing on different limits can average under each: what failed counts.
            result = evaluate(clearFailure, clearFailureCount, clearFailureReasons, true);
            return progress(Phase.COMPLETE, clearFailure.durationMs, result);
        }
        if (totals.durationMs < measurementMs) {
            return progress(Phase.MEASURING, totals.durationMs, null);
        }
        if (measurementMs < MAX_MEASUREMENT_MS && !decisive()) {
            measurementMs = MAX_MEASUREMENT_MS;
            return progress(Phase.MEASURING, totals.durationMs, null);
        }
        result = evaluate(totals, sampleCount, java.util.EnumSet.noneOf(Reason.class), false);
        return progress(Phase.COMPLETE, totals.durationMs, result);
    }

    /** The averages break the same limits with a little less and a little more slack. */
    private boolean decisive() {
        List<Reason> reasons = averageIssues(totals, 1);
        return reasons.equals(averageIssues(totals, STRICT_MARGIN))
                && reasons.equals(averageIssues(totals, LOOSE_MARGIN));
    }

    private List<Reason> averageIssues(Totals totals, double margin) {
        return issues(fps, margin, totals.average(totals.receivedFps),
                totals.average(totals.renderedFps), totals.average(totals.frameLossPercent),
                totals.average(totals.rttMs), totals.average(totals.rttVarianceMs),
                totals.average(totals.decoderLatencyMs),
                totals.average(totals.hostProcessingLatencyMs),
                totals.average(totals.hostProcessingReportedRatio));
    }

    /**
     * What the result suggests instead of this candidate; a custom mode (an in-stream test of
     * the settings running) keeps its mode and only loses bitrate.
     */
    public Adjustment adjustedSettings(Result result) {
        if (result == null) throw new IllegalArgumentException("No calibration result");
        return adjustedSettings(width, height, fps, bitrateKbps,
                result.lowerBitrateSuggested, result.lowerModeSuggested);
    }

    /**
     * Lowers the mode (next lower FPS, then the next lower resolution at 30 FPS) and/or the
     * bitrate by a quarter. A custom mode, or one with nothing lower, keeps its mode and only
     * loses bitrate.
     */
    public static Adjustment adjustedSettings(int width, int height, int fps, int bitrateKbps,
                                              boolean lowerBitrate, boolean lowerMode) {
        if (bitrateKbps < 500) throw new IllegalArgumentException("Invalid bitrate");
        if (!lowerBitrate && !lowerMode) {
            return new Adjustment(width, height, fps, bitrateKbps, false);
        }
        StreamingAutopilot.Mode lower = null;
        if (lowerMode && findMode(width, height, fps) != null) {
            for (StreamingAutopilot.Mode mode : StreamingAutopilot.standardModes()) {
                if (mode.width == width && mode.height == height && mode.fps < fps
                        && (lower == null || mode.fps > lower.fps)) {
                    lower = mode;
                }
            }
            if (lower == null) {
                int currentPixels = width * height;
                for (StreamingAutopilot.Mode mode : StreamingAutopilot.standardModes()) {
                    int pixels = mode.width * mode.height;
                    if (mode.fps == 30 && pixels < currentPixels
                            && (lower == null
                            || pixels > lower.width * lower.height)) {
                        lower = mode;
                    }
                }
            }
        }

        int adjustedBitrate = bitrateKbps;
        if (lowerBitrate || lower == null) {
            long reduced = (long) bitrateKbps * 3 / 4;
            adjustedBitrate = (int) Math.max(500L, reduced / 500 * 500);
        }
        if (lower == null) {
            return new Adjustment(width, height, fps, adjustedBitrate, true);
        }
        int defaultBitrate = PreferenceConfiguration.getDefaultBitrate(
                lower.width + "x" + lower.height, Integer.toString(lower.fps));
        return new Adjustment(lower.width, lower.height, lower.fps,
                Math.min(adjustedBitrate, defaultBitrate), true);
    }

    private static StreamingAutopilot.Mode findMode(int width, int height, int fps) {
        for (StreamingAutopilot.Mode mode : StreamingAutopilot.standardModes()) {
            if (mode.width == width && mode.height == height && mode.fps == fps) return mode;
        }
        return null;
    }

    /** {@code failed}: reasons that hold whatever the averages say (an early end's). */
    private Result evaluate(Totals totals, int sampleCount, java.util.Set<Reason> failed,
                            boolean earlyEnd) {
        List<Reason> reasons = averageIssues(totals, 1);
        for (Reason reason : failed) if (!reasons.contains(reason)) reasons.add(reason);
        boolean lowerBitrate = reasons.contains(Reason.FRAME_LOSS_HIGH)
                || reasons.contains(Reason.RTT_HIGH)
                || reasons.contains(Reason.RTT_VARIANCE_HIGH);
        boolean lowerMode = reasons.contains(Reason.RECEIVED_FPS_LOW)
                || reasons.contains(Reason.RENDERED_FPS_LOW)
                || reasons.contains(Reason.DECODER_LATENCY_HIGH)
                || reasons.contains(Reason.HOST_PROCESSING_LATENCY_HIGH)
                || reasons.contains(Reason.HOST_PROCESSING_COVERAGE_LOW);
        return new Result(reasons.isEmpty(), earlyEnd, lowerBitrate, lowerMode,
                sampleCount, totals, reasons);
    }

    /** The limits one window breaks: what the test's live readout marks. */
    public List<Reason> issues(Sample sample) {
        return issues(sample, 1);
    }

    private List<Reason> issues(Sample sample, double margin) {
        return issues(fps, margin, sample.receivedFps, sample.renderedFps,
                sample.frameLossPercent, sample.rttMs, sample.rttVarianceMs,
                sample.decoderLatencyMs, sample.hostProcessingLatencyMs,
                sample.hostProcessingReportedRatio);
    }

    /**
     * {@code margin} scales each limit's slack: the upper limits themselves, and the distance of
     * the lower ones (FPS, host coverage) below 100%.
     */
    private static List<Reason> issues(int targetFps, double margin, double receivedFps,
                                       double renderedFps, double loss, double rtt,
                                       double variance, double decoder, double host,
                                       double hostCoverage) {
        List<Reason> reasons = new ArrayList<>();
        double minimumFps = targetFps * (1 - (1 - MIN_FPS_RATIO) * margin);
        double frameIntervalMs = 1_000d / targetFps * margin;
        double minimumCoverage = 1 - (1 - MIN_HOST_PROCESSING_COVERAGE_RATIO) * margin;
        if (receivedFps < minimumFps) reasons.add(Reason.RECEIVED_FPS_LOW);
        if (renderedFps < minimumFps) reasons.add(Reason.RENDERED_FPS_LOW);
        if (loss > MAX_FRAME_LOSS_PERCENT * margin) reasons.add(Reason.FRAME_LOSS_HIGH);
        if (rtt + variance > MAX_RTT_AND_VARIANCE_MS * margin) reasons.add(Reason.RTT_HIGH);
        if (variance > MAX_RTT_VARIANCE_MS * margin) reasons.add(Reason.RTT_VARIANCE_HIGH);
        if (decoder > frameIntervalMs) reasons.add(Reason.DECODER_LATENCY_HIGH);
        if (host > frameIntervalMs) reasons.add(Reason.HOST_PROCESSING_LATENCY_HIGH);
        if (hostCoverage > 0 && hostCoverage < minimumCoverage) {
            reasons.add(Reason.HOST_PROCESSING_COVERAGE_LOW);
        }
        return reasons;
    }

    private Progress progress(Phase phase, long completed, Result completedResult) {
        long total = phase == Phase.WARMING_UP ? WARMUP_MS
                : phase == Phase.MEASURING || phase == Phase.COMPLETE
                ? measurementMs : 0;
        return new Progress(phase, completed, total, completedResult);
    }

    private static final class Totals {
        long durationMs;
        double receivedFps;
        double renderedFps;
        double frameLossPercent;
        double rttMs;
        double rttVarianceMs;
        double decoderLatencyMs;
        double hostProcessingLatencyMs;
        double hostProcessingReportedRatio;

        void add(Sample sample) {
            long duration = sample.durationMs();
            durationMs += duration;
            receivedFps += sample.receivedFps * duration;
            renderedFps += sample.renderedFps * duration;
            frameLossPercent += sample.frameLossPercent * duration;
            rttMs += sample.rttMs * duration;
            rttVarianceMs += sample.rttVarianceMs * duration;
            decoderLatencyMs += sample.decoderLatencyMs * duration;
            hostProcessingLatencyMs += sample.hostProcessingLatencyMs * duration;
            hostProcessingReportedRatio += sample.hostProcessingReportedRatio * duration;
        }

        double average(double value) {
            return durationMs == 0 ? 0 : value / durationMs;
        }
    }
}
