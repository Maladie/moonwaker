package com.limelight.stream;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Pure, deterministic recommendation logic for supported Moonlight stream settings. */
public final class StreamingAutopilot {
    private static final int MIN_BITRATE_KBPS = 500;
    private static final int BITRATE_STEP_KBPS = 500;
    private static final int MAX_BITRATE_KBPS =
            Integer.MAX_VALUE / BITRATE_STEP_KBPS * BITRATE_STEP_KBPS;
    private static final int CODEC_H264 = 1;
    private static final int CODEC_HEVC = 1 << 1;
    private static final int[][] RESOLUTIONS = {
            {640, 360},
            {854, 480},
            {1280, 720},
            {1920, 1080},
            {2560, 1440},
            {3840, 2160}
    };
    private static final int[] FRAME_RATES = {30, 60, 90, 120};

    private StreamingAutopilot() { }

    public enum Confidence {
        HIGH,
        LOW
    }

    public enum Reason {
        NETWORK_MEASUREMENT_UNAVAILABLE,
        CONSERVATIVE_FALLBACK,
        WITHIN_KNOWN_LIMITS,
        HIGHEST_RANKED_CANDIDATE,
        BUDGET_BELOW_MINIMUM_MODE,
        BITRATE_CAPPED_TO_SAFE_BUDGET
    }

    public static final class Mode {
        public final int width;
        public final int height;
        public final int fps;

        public Mode(int width, int height, int fps) {
            this.width = width;
            this.height = height;
            this.fps = fps;
        }
    }

    public static final class Capabilities {
        public final int maxWidth;
        public final int maxHeight;
        public final int maxFps;
        public final List<Mode> modes;
        private final List<Mode> h264Modes;
        private final List<Mode> hevcModes;

        public Capabilities(int maxWidth, int maxHeight, int maxFps) {
            this(modesUpTo(maxWidth, maxHeight, maxFps));
        }

        public Capabilities(Mode... modes) {
            this(modes, modes);
        }

        private Capabilities(Mode[] h264Modes, Mode[] hevcModes) {
            if (h264Modes == null || hevcModes == null ||
                    h264Modes.length + hevcModes.length == 0) {
                throw new IllegalArgumentException("At least one stream mode is required");
            }
            List<Mode> supportedH264 = validatedModes(h264Modes);
            List<Mode> supportedHevc = validatedModes(hevcModes);
            List<Mode> supported = new ArrayList<>(supportedH264);
            for (Mode mode : supportedHevc) {
                if (!containsMode(supported, mode.width, mode.height, mode.fps)) {
                    supported.add(mode);
                }
            }
            int width = 0;
            int height = 0;
            int fps = 0;
            for (Mode mode : supported) {
                width = Math.max(width, mode.width);
                height = Math.max(height, mode.height);
                fps = Math.max(fps, mode.fps);
            }
            this.maxWidth = width;
            this.maxHeight = height;
            this.maxFps = fps;
            this.modes = Collections.unmodifiableList(supported);
            this.h264Modes = Collections.unmodifiableList(supportedH264);
            this.hevcModes = Collections.unmodifiableList(supportedHevc);
        }

        public static Capabilities upTo(int maxWidth, int maxHeight, int maxFps) {
            return new Capabilities(maxWidth, maxHeight, maxFps);
        }

        boolean supports(int width, int height, int fps) {
            return containsMode(modes, width, height, fps);
        }

        private int codecsFor(int width, int height, int fps) {
            int codecs = 0;
            if (containsMode(h264Modes, width, height, fps)) codecs |= CODEC_H264;
            if (containsMode(hevcModes, width, height, fps)) codecs |= CODEC_HEVC;
            return codecs;
        }
    }

    static Capabilities forCodecModes(List<Mode> h264Modes, List<Mode> hevcModes) {
        return new Capabilities(h264Modes.toArray(new Mode[0]), hevcModes.toArray(new Mode[0]));
    }

    public static final class Recommendation {
        public final int width;
        public final int height;
        public final int fps;
        public final int bitrateKbps;
        public final Confidence confidence;
        public final List<Reason> reasons;

        private Recommendation(Candidate candidate, int bitrateKbps,
                               Confidence confidence, Reason... reasons) {
            width = candidate.width;
            height = candidate.height;
            fps = candidate.fps;
            this.bitrateKbps = bitrateKbps;
            this.confidence = confidence;
            this.reasons = Collections.unmodifiableList(Arrays.asList(reasons));
        }
    }

    /** An empty bitrate budget means that no network measurement is available. */
    public static Recommendation recommend(Capabilities client, Capabilities host,
                                           OptionalInt safeBitrateKbps) {
        return recommend(client, host, safeBitrateKbps,
                PreferenceConfiguration.FormatOption.AUTO);
    }

    public static Recommendation recommend(Capabilities client, Capabilities host,
                                           OptionalInt safeBitrateKbps,
                                           PreferenceConfiguration.FormatOption format) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(safeBitrateKbps, "safeBitrateKbps");
        Objects.requireNonNull(format, "format");
        if (safeBitrateKbps.isPresent()
                && safeBitrateKbps.getAsInt() < MIN_BITRATE_KBPS) {
            throw new IllegalArgumentException("Safe bitrate budget is below the supported minimum");
        }
        int allowedCodecs = allowedCodecs(format);

        if (!safeBitrateKbps.isPresent()) {
            Candidate fallback = bestCandidate(client, host, 1280, 720, 60,
                    OptionalInt.empty(), allowedCodecs);
            return new Recommendation(fallback, fallback.defaultBitrateKbps, Confidence.LOW,
                    Reason.NETWORK_MEASUREMENT_UNAVAILABLE,
                    Reason.CONSERVATIVE_FALLBACK);
        }

        int budget = safeBitrateKbps.getAsInt() / BITRATE_STEP_KBPS * BITRATE_STEP_KBPS;
        Candidate candidate = bestCandidate(client, host, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, OptionalInt.of(budget), allowedCodecs);
        if (candidate != null) {
            return new Recommendation(candidate, candidate.defaultBitrateKbps, Confidence.HIGH,
                    Reason.WITHIN_KNOWN_LIMITS,
                    Reason.HIGHEST_RANKED_CANDIDATE);
        }

        Candidate minimum = lowestCandidate(client, host, allowedCodecs);
        return new Recommendation(minimum, budget, Confidence.LOW,
                Reason.BUDGET_BELOW_MINIMUM_MODE,
                Reason.BITRATE_CAPPED_TO_SAFE_BUDGET);
    }

    /** What a variant trades for: the user picks one of the analysis' variants. */
    public enum VariantKind {
        /** {@link #recommend}: the highest-ranked mode within the budget. */
        BALANCED,
        /** More pixels than BALANCED at 60 FPS or more, carried at 3/4 of its usual bitrate. */
        SHARPNESS,
        /** A higher frame rate than BALANCED, at the largest resolution whose usual bitrate fits. */
        SMOOTHNESS,
        /** BALANCED's mode at the whole budget, when that is a quarter more bitrate or better. */
        CLEAN,
        /** The best mode up to 60 FPS whose usual bitrate is half the budget or less. */
        STABLE
    }

    public static final class Variant {
        public final VariantKind kind;
        public final int width;
        public final int height;
        public final int fps;
        public final int bitrateKbps;

        Variant(VariantKind kind, int width, int height, int fps, int bitrateKbps) {
            this.kind = kind;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrateKbps = bitrateKbps;
        }

        private boolean sameSettings(Variant other) {
            return width == other.width && height == other.height && fps == other.fps
                    && bitrateKbps == other.bitrateKbps;
        }
    }

    /**
     * BALANCED first, then the other kinds that exist and differ from the ones before them.
     * Without a measured budget, or below the smallest mode's, BALANCED alone.
     */
    public static List<Variant> variants(Capabilities client, Capabilities host,
                                         OptionalInt safeBitrateKbps,
                                         PreferenceConfiguration.FormatOption format) {
        Recommendation balanced = recommend(client, host, safeBitrateKbps, format);
        List<Variant> variants = new ArrayList<>();
        variants.add(new Variant(VariantKind.BALANCED, balanced.width, balanced.height,
                balanced.fps, balanced.bitrateKbps));
        if (!safeBitrateKbps.isPresent()
                || balanced.reasons.contains(Reason.BUDGET_BELOW_MINIMUM_MODE)) {
            return variants;
        }
        int budget = safeBitrateKbps.getAsInt() / BITRATE_STEP_KBPS * BITRATE_STEP_KBPS;
        List<Candidate> modes = new ArrayList<>();
        for (Mode mode : commonModes(client, host, format)) {
            modes.add(candidate(new int[]{mode.width, mode.height}, mode.fps));
        }

        long balancedPixels = (long) balanced.width * balanced.height;
        Candidate sharp = null;
        for (Candidate mode : modes) {
            if (mode.fps < 60 || mode.pixels() <= balancedPixels
                    || (long) mode.defaultBitrateKbps * 3 > budget * 4L) continue;
            if (sharp == null || mode.pixels() > sharp.pixels()
                    || (mode.pixels() == sharp.pixels() && mode.fps > sharp.fps)) sharp = mode;
        }
        if (sharp != null) {
            add(variants, new Variant(VariantKind.SHARPNESS, sharp.width, sharp.height,
                    sharp.fps, Math.min(sharp.defaultBitrateKbps, budget)));
        }

        Candidate smooth = null;
        for (Candidate mode : modes) {
            if (mode.fps <= balanced.fps || mode.defaultBitrateKbps > budget) continue;
            if (smooth == null || mode.fps > smooth.fps
                    || (mode.fps == smooth.fps && mode.pixels() > smooth.pixels())) smooth = mode;
        }
        if (smooth != null) {
            int bitrate = Math.min(smooth.defaultBitrateKbps * 5 / 4, budget);
            add(variants, new Variant(VariantKind.SMOOTHNESS, smooth.width, smooth.height,
                    smooth.fps, bitrate / BITRATE_STEP_KBPS * BITRATE_STEP_KBPS));
        }

        if (budget * 4L >= balanced.bitrateKbps * 5L) {
            add(variants, new Variant(VariantKind.CLEAN, balanced.width, balanced.height,
                    balanced.fps, budget));
        }

        Candidate stable = null;
        for (Candidate mode : modes) {
            if (mode.fps > 60 || mode.defaultBitrateKbps * 2L > budget) continue;
            if (stable == null || mode.isBetterThan(stable)) stable = mode;
        }
        if (stable != null) {
            add(variants, new Variant(VariantKind.STABLE, stable.width, stable.height,
                    stable.fps, stable.defaultBitrateKbps));
        }
        return variants;
    }

    /**
     * The next step up after a passed test of these settings (mockup O7, "Try higher"): the
     * variant with the least bitrate above them, the same mode first on a tie; null at the top.
     */
    public static Variant higher(List<Variant> variants, int width, int height, int fps,
                                 int bitrateKbps) {
        Variant next = null;
        for (Variant variant : variants) {
            if (variant.bitrateKbps <= bitrateKbps) continue;
            boolean sameMode = variant.width == width && variant.height == height
                    && variant.fps == fps;
            if (next == null || variant.bitrateKbps < next.bitrateKbps
                    || (variant.bitrateKbps == next.bitrateKbps && sameMode
                    && !(next.width == width && next.height == height && next.fps == fps))) {
                next = variant;
            }
        }
        return next;
    }

    private static void add(List<Variant> variants, Variant variant) {
        for (Variant existing : variants) if (existing.sameSettings(variant)) return;
        variants.add(variant);
    }

    /** The discrete modes both sides can stream with a shared codec the format allows. */
    public static List<Mode> commonModes(Capabilities client, Capabilities host,
                                         PreferenceConfiguration.FormatOption format) {
        int allowedCodecs = allowedCodecs(format);
        List<Mode> common = new ArrayList<>();
        for (int[] resolution : RESOLUTIONS) {
            for (int fps : FRAME_RATES) {
                if ((client.codecsFor(resolution[0], resolution[1], fps)
                        & host.codecsFor(resolution[0], resolution[1], fps)
                        & allowedCodecs) != 0) {
                    common.add(new Mode(resolution[0], resolution[1], fps));
                }
            }
        }
        return common;
    }

    private static int allowedCodecs(PreferenceConfiguration.FormatOption format) {
        return format == PreferenceConfiguration.FormatOption.FORCE_H264
                ? CODEC_H264 : format == PreferenceConfiguration.FormatOption.FORCE_HEVC
                ? CODEC_HEVC : CODEC_H264 | CODEC_HEVC;
    }

    public static int safeBitrateBudgetKbps(long bytes, long elapsedNanos) {
        if (bytes <= 0 || elapsedNanos <= 0) {
            throw new IllegalArgumentException("Network sample must be positive");
        }
        double budget = bytes * 8_000_000d / elapsedNanos * 0.75 - 1_000;
        if (budget <= 0) return 0;
        long bounded = (long) Math.min(budget, MAX_BITRATE_KBPS);
        return (int) (bounded / BITRATE_STEP_KBPS * BITRATE_STEP_KBPS);
    }

    /** Measured time a session needs before its summary counts as evidence. */
    public static final long SESSION_MIN_MEASURED_MS = 3 * 60_000L;
    /** Time after the stream is shown that is left out (decoder and game warm-up). */
    public static final long SESSION_WARMUP_MS = 15_000L;
    public static final int SESSION_HISTORY_LIMIT = 5;
    public static final double SESSION_MAX_FRAME_LOSS_PERCENT = 2.0;
    public static final double SESSION_MAX_RTT_VARIANCE_MS = 15.0;
    public static final double SESSION_MIN_RENDERED_RATIO = 0.90;

    public enum SessionIssue {
        FRAME_LOSS_HIGH,
        NETWORK_JITTER_HIGH,
        FRAMES_DROPPED_BY_DECODER,
        DECODER_LATENCY_HIGH
    }

    /** One stream's quality, with the settings it ran with. */
    public static final class SessionSummary {
        public final int width;
        public final int height;
        public final int fps;
        public final int bitrateKbps;
        public final long measuredMs;
        public final double receivedFps;
        public final double renderedFps;
        public final double frameLossPercent;
        public final double rttMs;
        public final double rttVarianceMs;
        public final double decoderLatencyMs;
        /** Received bitrate over the measured time; 0 when the device did not report it. */
        public final int actualBitrateKbps;

        public SessionSummary(int width, int height, int fps, int bitrateKbps, long measuredMs,
                              double receivedFps, double renderedFps, double frameLossPercent,
                              double rttMs, double rttVarianceMs, double decoderLatencyMs,
                              int actualBitrateKbps) {
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrateKbps = bitrateKbps;
            this.measuredMs = measuredMs;
            this.receivedFps = receivedFps;
            this.renderedFps = renderedFps;
            this.frameLossPercent = frameLossPercent;
            this.rttMs = rttMs;
            this.rttVarianceMs = rttVarianceMs;
            this.decoderLatencyMs = decoderLatencyMs;
            this.actualBitrateKbps = actualBitrateKbps;
        }

        boolean sameSettings(SessionSummary other) {
            return width == other.width && height == other.height && fps == other.fps
                    && bitrateKbps == other.bitrateKbps;
        }
    }

    /**
     * Time-weighted totals of one stream's one-second video-stat windows. The decoder thread
     * adds, the activity reads the summary; both are synchronized.
     */
    public static final class SessionQuality {
        private final int width;
        private final int height;
        private final int fps;
        private final int bitrateKbps;
        private long shownAtMs = -1L;
        private long measuredMs;
        private double receivedFps;
        private double renderedFps;
        private double frameLossPercent;
        private double rttMs;
        private double rttVarianceMs;
        private double decoderLatencyMs;
        private long firstRxBytes = -1L;
        private long firstRxAtMs;
        private long lastRxBytes;
        private long lastRxAtMs;

        public SessionQuality(int width, int height, int fps, int bitrateKbps) {
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrateKbps = bitrateKbps;
        }

        /** Windows before the stream was shown, and its warm-up, are left out. */
        public synchronized void shown(long nowMs) {
            if (shownAtMs < 0) shownAtMs = nowMs;
        }

        /** {@code rxBytes} is the app's received byte counter, negative when unsupported. */
        public synchronized void add(StreamingAutopilotCalibration.Sample sample, long rxBytes) {
            if (shownAtMs < 0 || sample.windowStartMs < shownAtMs + SESSION_WARMUP_MS) return;
            long duration = sample.durationMs();
            measuredMs += duration;
            receivedFps += sample.receivedFps * duration;
            renderedFps += sample.renderedFps * duration;
            frameLossPercent += sample.frameLossPercent * duration;
            rttMs += sample.rttMs * duration;
            rttVarianceMs += sample.rttVarianceMs * duration;
            decoderLatencyMs += sample.decoderLatencyMs * duration;
            if (rxBytes < 0) return;
            if (firstRxBytes < 0) {
                firstRxBytes = rxBytes;
                firstRxAtMs = sample.windowEndMs;
            }
            lastRxBytes = rxBytes;
            lastRxAtMs = sample.windowEndMs;
        }

        /** The summary, or null when too little was measured to judge the session. */
        public synchronized SessionSummary summary() {
            if (measuredMs < SESSION_MIN_MEASURED_MS) return null;
            long rxElapsedMs = lastRxAtMs - firstRxAtMs;
            int actualKbps = firstRxBytes < 0 || rxElapsedMs <= 0 || lastRxBytes < firstRxBytes
                    ? 0 : (int) ((lastRxBytes - firstRxBytes) * 8L / rxElapsedMs);
            return new SessionSummary(width, height, fps, bitrateKbps, measuredMs,
                    receivedFps / measuredMs, renderedFps / measuredMs,
                    frameLossPercent / measuredMs, rttMs / measuredMs,
                    rttVarianceMs / measuredMs, decoderLatencyMs / measuredMs, actualKbps);
        }
    }

    /** A change proposed after poor sessions with the same settings. */
    public static final class SessionAdvice {
        public final int width;
        public final int height;
        public final int fps;
        public final int bitrateKbps;
        public final List<SessionIssue> issues;
        public final SessionSummary latest;
        public final int poorSessions;
        public final int sessions;

        private SessionAdvice(StreamingAutopilotCalibration.Adjustment adjustment,
                              List<SessionIssue> issues, SessionSummary latest,
                              int poorSessions, int sessions) {
            width = adjustment.width;
            height = adjustment.height;
            fps = adjustment.fps;
            bitrateKbps = adjustment.bitrateKbps;
            this.issues = Collections.unmodifiableList(issues);
            this.latest = latest;
            this.poorSessions = poorSessions;
            this.sessions = sessions;
        }
    }

    public static List<SessionIssue> sessionIssues(SessionSummary session) {
        List<SessionIssue> issues = new ArrayList<>();
        if (session.frameLossPercent > SESSION_MAX_FRAME_LOSS_PERCENT) {
            issues.add(SessionIssue.FRAME_LOSS_HIGH);
        }
        if (session.rttVarianceMs > SESSION_MAX_RTT_VARIANCE_MS) {
            issues.add(SessionIssue.NETWORK_JITTER_HIGH);
        }
        // Received FPS is not judged: the host sends fewer frames while the picture is still.
        if (session.receivedFps > 0
                && session.renderedFps < session.receivedFps * SESSION_MIN_RENDERED_RATIO) {
            issues.add(SessionIssue.FRAMES_DROPPED_BY_DECODER);
        }
        if (session.fps > 0 && session.decoderLatencyMs > 1_000d / session.fps) {
            issues.add(SessionIssue.DECODER_LATENCY_HIGH);
        }
        return issues;
    }

    /**
     * {@code history} is oldest first. Advice needs the latest session to be poor and at least
     * half of the sessions with its settings too; network issues lower the bitrate, decoder
     * issues the mode.
     */
    public static Optional<SessionAdvice> adviseAfterSessions(List<SessionSummary> history) {
        if (history.isEmpty()) return Optional.empty();
        SessionSummary latest = history.get(history.size() - 1);
        List<SessionIssue> latestIssues = sessionIssues(latest);
        if (latestIssues.isEmpty()) return Optional.empty();
        int sessions = 0;
        int poor = 0;
        for (SessionSummary session : history) {
            if (!session.sameSettings(latest)) continue;
            sessions++;
            if (!sessionIssues(session).isEmpty()) poor++;
        }
        if (poor * 2 < sessions) return Optional.empty();
        boolean lowerBitrate = latestIssues.contains(SessionIssue.FRAME_LOSS_HIGH)
                || latestIssues.contains(SessionIssue.NETWORK_JITTER_HIGH);
        boolean lowerMode = latestIssues.contains(SessionIssue.FRAMES_DROPPED_BY_DECODER)
                || latestIssues.contains(SessionIssue.DECODER_LATENCY_HIGH);
        StreamingAutopilotCalibration.Adjustment adjustment =
                StreamingAutopilotCalibration.adjustedSettings(latest.width, latest.height,
                        latest.fps, Math.max(MIN_BITRATE_KBPS, latest.bitrateKbps),
                        lowerBitrate, lowerMode);
        if (adjustment.width == latest.width && adjustment.height == latest.height
                && adjustment.fps == latest.fps && adjustment.bitrateKbps == latest.bitrateKbps) {
            return Optional.empty();
        }
        return Optional.of(new SessionAdvice(adjustment, latestIssues, latest, poor, sessions));
    }

    private static Candidate bestCandidate(Capabilities client, Capabilities host,
                                           int maxWidth, int maxHeight, int maxFps,
                                           OptionalInt budget, int allowedCodecs) {
        Candidate best = null;
        for (int[] resolution : RESOLUTIONS) {
            for (int fps : FRAME_RATES) {
                Candidate candidate = candidate(resolution, fps);
                if (!candidate.fits(maxWidth, maxHeight, maxFps)
                        || (client.codecsFor(candidate.width, candidate.height, candidate.fps)
                        & host.codecsFor(candidate.width, candidate.height, candidate.fps)
                        & allowedCodecs) == 0
                        || (budget.isPresent()
                        && candidate.defaultBitrateKbps > budget.getAsInt())) {
                    continue;
                }
                if (best == null || candidate.isBetterThan(best)) best = candidate;
            }
        }
        if (best == null && !budget.isPresent()) {
            throw new IllegalArgumentException("No discrete stream mode fits the capabilities");
        }
        return best;
    }

    private static Candidate lowestCandidate(Capabilities client, Capabilities host,
                                             int allowedCodecs) {
        for (int[] resolution : RESOLUTIONS) {
            for (int fps : FRAME_RATES) {
                Candidate candidate = candidate(resolution, fps);
                if ((client.codecsFor(candidate.width, candidate.height, candidate.fps)
                        & host.codecsFor(candidate.width, candidate.height, candidate.fps)
                        & allowedCodecs) != 0) {
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("No discrete stream mode fits the capabilities");
    }

    private static Mode[] modesUpTo(int maxWidth, int maxHeight, int maxFps) {
        if (maxWidth <= 0 || maxHeight <= 0 || maxFps <= 0) {
            throw new IllegalArgumentException("Stream capability limits must be positive");
        }
        List<Mode> modes = new ArrayList<>();
        for (int[] resolution : RESOLUTIONS) {
            for (int fps : FRAME_RATES) {
                if (resolution[0] <= maxWidth && resolution[1] <= maxHeight && fps <= maxFps) {
                    modes.add(new Mode(resolution[0], resolution[1], fps));
                }
            }
        }
        return modes.toArray(new Mode[0]);
    }

    static Mode[] standardModes() {
        return modesUpTo(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static boolean isDiscreteMode(int width, int height, int fps) {
        for (int[] resolution : RESOLUTIONS) {
            if (resolution[0] != width || resolution[1] != height) continue;
            for (int candidateFps : FRAME_RATES) {
                if (candidateFps == fps) return true;
            }
        }
        return false;
    }

    private static List<Mode> validatedModes(Mode[] modes) {
        List<Mode> supported = new ArrayList<>(modes.length);
        for (Mode mode : modes) {
            if (mode == null || !isDiscreteMode(mode.width, mode.height, mode.fps)) {
                throw new IllegalArgumentException("Unsupported discrete stream mode");
            }
            supported.add(mode);
        }
        return supported;
    }

    private static boolean containsMode(List<Mode> modes, int width, int height, int fps) {
        for (Mode mode : modes) {
            if (mode.width == width && mode.height == height && mode.fps == fps) return true;
        }
        return false;
    }

    private static Candidate candidate(int[] resolution, int fps) {
        int bitrate = PreferenceConfiguration.getDefaultBitrate(
                resolution[0] + "x" + resolution[1], Integer.toString(fps));
        return new Candidate(resolution[0], resolution[1], fps, bitrate);
    }

    private static final class Candidate {
        final int width;
        final int height;
        final int fps;
        final int defaultBitrateKbps;

        Candidate(int width, int height, int fps, int defaultBitrateKbps) {
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.defaultBitrateKbps = defaultBitrateKbps;
        }

        long pixels() {
            return (long) width * height;
        }

        boolean fits(int maxWidth, int maxHeight, int maxFps) {
            return width <= maxWidth && height <= maxHeight && fps <= maxFps;
        }

        boolean isBetterThan(Candidate other) {
            if (defaultBitrateKbps != other.defaultBitrateKbps) {
                return defaultBitrateKbps > other.defaultBitrateKbps;
            }
            if (fps != other.fps) return fps > other.fps;
            return width * height > other.width * other.height;
        }
    }
}
