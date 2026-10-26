package com.limelight.stream;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A stream's video settings as the overlay changes them live: what the stream uses now, or what
 * it reconnects with. Pure; the options come from the client and host capability probes.
 */
public final class LiveStreamSettings {
    public final int width;
    public final int height;
    public final int fps;
    public final int bitrateKbps;
    public final boolean hdr;

    public LiveStreamSettings(int width, int height, int fps, int bitrateKbps, boolean hdr) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrateKbps = bitrateKbps;
        this.hdr = hdr;
    }

    /** What this TV and host can both stream; the current mode is always one of them. */
    public static final class Options {
        final List<StreamingAutopilot.Mode> modes;
        public final boolean hdrAvailable;

        public Options(List<StreamingAutopilot.Mode> common, LiveStreamSettings current,
                       boolean hdrAvailable) {
            List<StreamingAutopilot.Mode> modes = new ArrayList<>(common);
            if (fpsFor(modes, current.width, current.height).indexOf(current.fps) < 0) {
                modes.add(new StreamingAutopilot.Mode(current.width, current.height,
                        current.fps));
            }
            modes.sort(Comparator.<StreamingAutopilot.Mode>comparingLong(
                    m -> (long) m.width * m.height).thenComparingInt(m -> m.width)
                    .thenComparingInt(m -> m.fps));
            this.modes = Collections.unmodifiableList(modes);
            this.hdrAvailable = hdrAvailable || current.hdr;
        }

        /** Only the current mode: nothing to switch to. */
        public static Options currentOnly(LiveStreamSettings current) {
            return new Options(Collections.emptyList(), current, false);
        }

        public boolean offersModes() {
            return modes.size() > 1;
        }
    }

    public LiveStreamSettings withBitrate(int bitrateKbps) {
        return new LiveStreamSettings(width, height, fps, bitrateKbps, hdr);
    }

    /**
     * The next resolution ({@code step} +1 larger, -1 smaller, wrapping), at the highest frame
     * rate it offers not above this one.
     */
    public LiveStreamSettings nextResolution(Options options, int step) {
        List<StreamingAutopilot.Mode> resolutions = new ArrayList<>();
        for (StreamingAutopilot.Mode mode : options.modes) {
            StreamingAutopilot.Mode last = resolutions.isEmpty()
                    ? null : resolutions.get(resolutions.size() - 1);
            if (last == null || last.width != mode.width || last.height != mode.height) {
                resolutions.add(mode);
            }
        }
        int index = 0;
        while (index < resolutions.size() && (resolutions.get(index).width != width
                || resolutions.get(index).height != height)) index++;
        StreamingAutopilot.Mode next =
                resolutions.get(Math.floorMod(index + step, resolutions.size()));
        List<Integer> rates = fpsFor(options.modes, next.width, next.height);
        int chosen = rates.get(0);
        for (int rate : rates) if (rate <= fps) chosen = rate;
        return new LiveStreamSettings(next.width, next.height, chosen, bitrateKbps, hdr);
    }

    /** The next frame rate this resolution offers ({@code step} +1 or -1, wrapping). */
    public LiveStreamSettings nextFps(Options options, int step) {
        List<Integer> rates = fpsFor(options.modes, width, height);
        int next = rates.get(Math.floorMod(rates.indexOf(fps) + step, rates.size()));
        return new LiveStreamSettings(width, height, next, bitrateKbps, hdr);
    }

    public LiveStreamSettings toggleHdr(Options options) {
        return options.hdrAvailable
                ? new LiveStreamSettings(width, height, fps, bitrateKbps, !hdr) : this;
    }

    /**
     * The emergency mode for a struggling link: a quarter below both this bitrate and what the
     * link carried ({@code observedKbps}, 0 when unknown), at the mode no larger and no faster
     * than this one whose usual bitrate fits within a third over that budget. Frame rate is kept
     * before resolution; with nothing fitting, the smallest such mode (the options always hold
     * this one). Never more bitrate than now; HDR stays.
     */
    public LiveStreamSettings emergency(Options options, int observedKbps) {
        long ceiling = observedKbps > 0 ? Math.min(observedKbps, bitrateKbps) : bitrateKbps;
        int budget = (int) Math.min(bitrateKbps,
                Math.max(EMERGENCY_MIN_KBPS, ceiling * 3 / 4 / 500 * 500));
        StreamingAutopilot.Mode best = null;
        StreamingAutopilot.Mode smallest = null;
        for (StreamingAutopilot.Mode mode : options.modes) {
            if (mode.width > width || mode.height > height || mode.fps > fps) continue;
            // Modes are sorted smallest first.
            if (smallest == null) smallest = mode;
            if ((long) PreferenceConfiguration.getDefaultBitrate(mode.width + "x" + mode.height,
                    Integer.toString(mode.fps)) * 3 > budget * 4L) continue;
            if (best == null || mode.fps > best.fps || (mode.fps == best.fps
                    && (long) mode.width * mode.height > (long) best.width * best.height)) {
                best = mode;
            }
        }
        if (best == null) best = smallest;
        return new LiveStreamSettings(best.width, best.height, best.fps, budget, hdr);
    }

    /** The lowest bitrate the stream reconnects with (Game clamps runtime bitrates to it). */
    static final int EMERGENCY_MIN_KBPS = 1000;

    /** These settings as one intent or resume-record string; {@link #parse} reads it. */
    public String encode() {
        return width + "," + height + "," + fps + "," + bitrateKbps + "," + (hdr ? 1 : 0);
    }

    /** Null for anything {@link #encode} did not write. */
    public static LiveStreamSettings parse(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(",");
        if (parts.length != 5) return null;
        try {
            return new LiveStreamSettings(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    "1".equals(parts[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A resolution as the mockups name it: "720p", "1440p", "4K" for 16:9 modes, else
     * "2560×1080".
     */
    public static String resolutionName(int width, int height) {
        if (width * 9 != height * 16) return width + "×" + height;
        return height == 2160 ? "4K" : height + "p";
    }

    private static List<Integer> fpsFor(List<StreamingAutopilot.Mode> modes, int width,
                                        int height) {
        List<Integer> rates = new ArrayList<>();
        for (StreamingAutopilot.Mode mode : modes) {
            if (mode.width == width && mode.height == height) rates.add(mode.fps);
        }
        return rates;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LiveStreamSettings)) return false;
        LiveStreamSettings that = (LiveStreamSettings) other;
        return width == that.width && height == that.height && fps == that.fps
                && bitrateKbps == that.bitrateKbps && hdr == that.hdr;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, fps, bitrateKbps, hdr);
    }

    @Override
    public String toString() {
        return width + "x" + height + "@" + fps + " " + bitrateKbps + "kbps"
                + (hdr ? " HDR" : "");
    }
}
