package com.limelight.binding.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AudioHapticsTest {
    private static final int RATE = 48000;
    private static final int FRAMES = 240; // 5 ms buffers, as Opus delivers them

    private final List<Integer> sent = new ArrayList<>();

    @Test
    public void offModeCreatesNothing() {
        assertNull(AudioHaptics.create(AudioHaptics.MODE_OFF, 100, sent::add));
        assertNull(AudioHaptics.create(null, 100, sent::add));
    }

    @Test
    public void bassRumblesAndTrebleDoesNot() {
        AudioHaptics bass = game();
        feed(bass, 60, 0.5, 400);
        assertTrue("60 Hz rumbles", last() > 200 * 257);

        sent.clear();
        AudioHaptics treble = game();
        feed(treble, 2000, 0.5, 400);
        assertTrue("2 kHz stays under the gate", sent.isEmpty());
    }

    @Test
    public void envelopeRisesFastAndFallsSlowly() {
        AudioHaptics haptics = game();
        feed(haptics, 60, 0.5, 10);
        assertTrue("on within 10 ms", last() > 0);
        feed(haptics, 60, 0.5, 200);
        int on = last();
        feed(haptics, 60, 0, 60);
        assertTrue("still decaying 60 ms after the bass stopped", last() > 0 && last() < on);
        feed(haptics, 60, 0, 1500);
        assertEquals("off after silence", 0, last());
    }

    @Test
    public void quietNoiseIsGated() {
        feed(game(), 60, 0.005, 400);
        assertTrue(sent.isEmpty());
    }

    @Test
    public void musicPulsesOnOnsetsNotOnSustainedBass() {
        AudioHaptics haptics = music();
        feed(haptics, 60, 0.5, 40);
        assertTrue("onset pulses", last() > 0);
        feed(haptics, 60, 0.5, 2000);
        assertEquals("sustained bass settles", 0, last());
    }

    @Test
    public void gameSteadyBassFadesToNothingAndAHitStillRumbles() {
        AudioHaptics haptics = game();
        feed(haptics, 60, 0.4, 8000);
        assertEquals("steady background bass is no constant rumble", 0, last());
        sent.clear();
        feed(haptics, 60, 0.4, 2000);
        assertTrue("stays quiet", sent.isEmpty());
        feed(haptics, 60, 0.9, 150);
        assertTrue("a louder bass hit over it rumbles", last() > 100 * 257);
    }

    @Test
    public void musicIgnoresASwellingBassLineAndPulsesOnHits() {
        AudioHaptics haptics = music();
        for (int i = 0; i < 20; i++) feed(haptics, 60, i % 2 == 0 ? 0.35 : 0.45, 100);
        assertEquals(0, last());
        sent.clear();
        for (int i = 0; i < 20; i++) feed(haptics, 60, i % 2 == 0 ? 0.35 : 0.45, 100);
        assertTrue("a bass line moving +-15% does not buzz", sent.isEmpty());
        feed(haptics, 60, 0.9, 80);
        assertTrue("a kick over it pulses", last() > 0);
        feed(haptics, 60, 0.4, 1000);
        assertEquals("and the pulse ends", 0, last());
    }

    @Test
    public void updatesAreRateLimited() {
        AudioHaptics haptics = game();
        feed(haptics, 60, 0.5, 1000);
        // One update per 40 ms at most.
        assertTrue(sent.size() <= 1000 / AudioHaptics.UPDATE_MS);
    }

    @Test
    public void detachedHapticsSendNothing() {
        AudioHaptics haptics = game();
        haptics.detach();
        feed(haptics, 60, 0.5, 400);
        assertTrue(sent.isEmpty());
    }

    @Test
    public void strengthScalesAndCurveIsGated() {
        assertEquals(0, AudioHaptics.amplitude(AudioHaptics.GATE, 1));
        assertEquals(255, AudioHaptics.amplitude(AudioHaptics.FULL, 1));
        assertEquals(128, AudioHaptics.amplitude(AudioHaptics.FULL, 0.5));
        assertEquals(255, AudioHaptics.amplitude(1, 2));
    }

    @Test
    public void gameRumbleWinsAndIsNeverMuted() {
        assertEquals((short) 0xC000, AudioHaptics.merge((short) 0xC000, 0x4000));
        assertEquals((short) 0x8000, AudioHaptics.merge((short) 0x1000, 0x8000));
        assertEquals((short) 0xFFFF, AudioHaptics.merge((short) 0xFFFF, 0));
        assertEquals((short) 0, AudioHaptics.merge((short) 0, 0));
    }

    private AudioHaptics game() {
        return AudioHaptics.create(AudioHaptics.MODE_GAME, 100, sent::add);
    }

    private AudioHaptics music() {
        return AudioHaptics.create(AudioHaptics.MODE_MUSIC, 100, sent::add);
    }

    private int last() {
        return sent.isEmpty() ? 0 : sent.get(sent.size() - 1);
    }

    private long phase;

    /** Stereo sine of {@code hz} at {@code amplitude} (1 = full scale) for {@code ms}. */
    private void feed(AudioHaptics haptics, double hz, double amplitude, int ms) {
        short[] buffer = new short[FRAMES * 2];
        for (int done = 0; done < ms * RATE / 1000; done += FRAMES) {
            for (int f = 0; f < FRAMES; f++, phase++) {
                short s = (short) Math.round(amplitude * 32767
                        * Math.sin(2 * Math.PI * hz * phase / RATE));
                buffer[2 * f] = s;
                buffer[2 * f + 1] = s;
            }
            haptics.process(buffer, 2, RATE);
        }
    }
}
