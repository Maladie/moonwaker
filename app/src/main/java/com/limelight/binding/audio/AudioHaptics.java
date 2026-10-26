package com.limelight.binding.audio;

import java.util.function.IntConsumer;

/**
 * Audio haptics: the stream's bass drives the pads' low-frequency motor. {@link #process} runs
 * on the audio thread inside {@code playDecodedAudio}: no allocation, no blocking. The sink gets
 * a motor value (0-65535) only on a change, at most once per {@link #UPDATE_MS} ms of audio,
 * and must hand it off to another thread.
 *
 * <p>Mono downmix, a 120 Hz biquad low-pass, an RMS envelope (smoothed power, so a held note
 * does not ripple), then a drive above an adaptive floor: {@code env - margin * average}.
 * "Game" uses a slow floor, so a bass rise rumbles and fades as it becomes the background;
 * "music" uses a short one and a larger margin, so only hits well above the recent bass pulse.
 * Either way steady bass settles to nothing. A noise gate and a strength curve map the drive.
 */
public final class AudioHaptics {
    public static final String MODE_OFF = "off";
    public static final String MODE_GAME = "game";
    public static final String MODE_MUSIC = "music";

    static final int UPDATE_MS = 40;
    private static final double CUTOFF_HZ = 120;
    private static final double Q = 0.7071;
    // ponytail: calibration constants, guessed for typical mixes; tune on the TV by feel, the
    // strength setting covers per-user taste. GATE: drive below it is silence. FULL: drive that
    // reaches the full motor. *_MARGIN: how far above its running average the envelope must be
    // (higher = fewer, harder hits). *_AVERAGE_S: how fast a level becomes "background".
    static final double GATE = 0.01;
    static final double FULL = 0.35;
    static final double GAME_MARGIN = 1.15;
    static final double GAME_AVERAGE_S = 2.0;
    static final double MUSIC_MARGIN = 1.6;
    static final double MUSIC_AVERAGE_S = 0.4;
    /** Smallest change (of 255) worth a Bluetooth report. */
    private static final int MIN_STEP = 4;

    private final boolean music;
    private final double strength;
    private final double margin;
    private volatile IntConsumer sink;

    private int sampleRate;
    private double b0, b1, b2, a1, a2;
    private double x1, x2, y1, y2;
    private double attack, release, averageCoef;
    private double power, average;
    private int samplesSinceUpdate, updateSamples;
    private int last;

    /** Null when {@code mode} is off. */
    public static AudioHaptics create(String mode, int strengthPercent, IntConsumer sink) {
        if (!MODE_GAME.equals(mode) && !MODE_MUSIC.equals(mode)) return null;
        return new AudioHaptics(MODE_MUSIC.equals(mode), strengthPercent, sink);
    }

    AudioHaptics(boolean music, int strengthPercent, IntConsumer sink) {
        this.music = music;
        this.strength = Math.max(0, strengthPercent) / 100.0;
        this.margin = music ? MUSIC_MARGIN : GAME_MARGIN;
        this.sink = sink;
    }

    /** The pads' owner is gone: no more updates, and no reference to it is kept. */
    public void detach() {
        sink = null;
    }

    /** Analyses one interleaved 16-bit buffer. */
    public void process(short[] pcm, int channels, int rate) {
        IntConsumer target = sink;
        if (target == null || channels <= 0) return;
        if (rate != sampleRate) configure(rate);
        double peak = 0;
        int frames = pcm.length / channels;
        for (int f = 0, i = 0; f < frames; f++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) sum += pcm[i++];
            double x = sum / (channels * 32768.0);
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            double square = 2 * y * y; // a sine's peak squared, so env is in amplitude units
            power += (square > power ? attack : release) * (square - power);
            double env = Math.sqrt(power);
            average += averageCoef * (env - average);
            double drive = env - margin * average;
            if (drive > peak) peak = drive;
        }
        int value = amplitude(peak, strength);
        if (samplesSinceUpdate < updateSamples) samplesSinceUpdate += frames;
        if (samplesSinceUpdate < updateSamples) return;
        if (Math.abs(value - last) < MIN_STEP && (value == 0) == (last == 0)) return;
        samplesSinceUpdate = 0;
        last = value;
        target.accept(value * 257);
    }

    /** The motor value sent: the game's rumble wins whenever it is stronger, never muted. */
    public static short merge(short gameMotor, int audioMotor) {
        return (short) Math.max(gameMotor & 0xffff, audioMotor);
    }

    /** Gate and curve: 0-255 from a drive level (1 = full-scale bass). */
    static int amplitude(double drive, double strength) {
        if (drive <= GATE) return 0;
        double normalized = Math.min(1, (drive - GATE) / (FULL - GATE));
        return (int) Math.min(255, Math.round(Math.pow(normalized, 0.7) * strength * 255));
    }

    private void configure(int rate) {
        sampleRate = rate;
        double w0 = 2 * Math.PI * CUTOFF_HZ / rate;
        double alpha = Math.sin(w0) / (2 * Q);
        double cos = Math.cos(w0);
        double a0 = 1 + alpha;
        b0 = (1 - cos) / 2 / a0;
        b1 = (1 - cos) / a0;
        b2 = b0;
        a1 = -2 * cos / a0;
        a2 = (1 - alpha) / a0;
        // On power: the amplitude's time constants are twice these.
        attack = coefficient(0.010, rate);
        release = coefficient(music ? 0.040 : 0.075, rate);
        averageCoef = coefficient(music ? MUSIC_AVERAGE_S : GAME_AVERAGE_S, rate);
        updateSamples = rate * UPDATE_MS / 1000;
        // The first change goes out at once; the limit spaces the ones after it.
        samplesSinceUpdate = updateSamples;
    }

    private static double coefficient(double seconds, int rate) {
        return 1 - Math.exp(-1 / (seconds * rate));
    }
}
