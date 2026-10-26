package com.limelight.console;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.SoundEffectConstants;

import com.limelight.R;
import com.limelight.binding.usb.UsbForwardingController;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns menu ambience, the entry intro jingle and short, rate-limited console cues. */
final class ConsoleAudioEngine {
    private static final String TAG = "MoonWakerAudio";
    private static final int SAMPLE_RATE = 22_050;
    private static final float DEFAULT_HOST_SELECTION_VOLUME = 0.40f;
    private static final float DEFAULT_MENU_VOLUME = 0.30f;
    private static final float DEFAULT_EFFECTS_VOLUME = 0.40f;
    private static final long NAVIGATION_THROTTLE_MS = 90L;
    private static final int FADE_STEPS = 18;
    private static final long FADE_STEP_MS = 35L;
    /** Leaving a console (stop, release) ramps the music down this fast instead of cutting it. */
    private static final int LEAVE_FADE_STEPS = 8;
    /** A NAVIGATE tick answers a directional key pressed at most this long ago. */
    static final long NAVIGATION_INPUT_WINDOW_MS = 320L;
    /** Players outlive release() until the leave fade and the longest cue have played out. */
    private static final long RELEASE_DELAY_MS = 800L;
    /** Both loops stay silent this long once the intro starts: the jingle speaks alone. */
    static final long INTRO_HOLD_MS = ConsoleIntroTiming.MINIMUM_MS;
    /** Ambience gain at stepper 1 and 10; each music asset peaks near 0 dBFS (about -12 LUFS). */
    static final float AMBIENT_MIN_DB = -42f;
    static final float AMBIENT_MAX_DB = -10f;

    /** One looping track every console of this process shares (main thread only). */
    private static final class Loop {
        final int resource;
        ConsoleMusicLoop player;
        boolean preparing;
        float volume;
        int fadeGeneration;

        Loop(int resource) {
            this.resource = resource;
        }
    }

    /*
     * Host selection, its profile gate and PIN play the entry loop; a host's menus play the
     * menu loop. Consoles overlap while one hands the screen to the next (a warm-up's stream
     * Home, the console under it): a loop never stops, restarts or seeks across that handover,
     * and only the console shown last (the controller) sets the gains.
     */
    private static final Loop ENTRY_LOOP = new Loop(R.raw.moonwaker_entry_ambient);
    private static final Loop MENU_LOOP = new Loop(R.raw.moonwaker_ambient_background);
    private static int liveEngines;
    private static ConsoleAudioEngine controller;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService preparationExecutor = Executors.newSingleThreadExecutor();
    private final AudioManager systemAudio;
    /** One static track per cue: unlike SoundPool, a track can be routed (ConsoleAudioRoute). */
    private final Map<ConsoleAudioSynthesis.Cue, AudioTrack> cueTracks =
            new EnumMap<>(ConsoleAudioSynthesis.Cue.class);
    private final Runnable routeChanged = this::applyRoute;
    private MediaPlayer jinglePlayer;
    /** The last cue asked for before the cues were ready. */
    private ConsoleAudioSynthesis.Cue pendingCue;
    /** Uptime until which both loops yield to the intro jingle. */
    private long introHoldUntil;
    private boolean preparationStarted;
    private boolean released;
    private boolean resumed;
    private boolean menuVisible = true;
    private boolean hostSelectionVisible = true;
    private boolean effectsEnabled = true;
    private boolean ambientEnabled = true;
    private float hostSelectionVolume = DEFAULT_HOST_SELECTION_VOLUME;
    private float menuVolume = DEFAULT_MENU_VOLUME;
    private float effectsVolume = DEFAULT_EFFECTS_VOLUME;
    private long lastNavigationAt;
    private long resumedAt;
    private long lastDirectionalInputAt = Long.MIN_VALUE / 2;

    ConsoleAudioEngine(Context context) {
        this.context = context.getApplicationContext();
        systemAudio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        liveEngines++;
        UsbForwardingController.get(this.context).addListener(routeChanged);
    }

    synchronized void setEffectsEnabled(boolean enabled) {
        effectsEnabled = enabled;
    }

    synchronized void setAmbientEnabled(boolean enabled) {
        ambientEnabled = enabled;
        updateAmbient(false);
    }

    synchronized void setHostSelectionVolume(float volume) {
        hostSelectionVolume = clamp(volume);
        updateAmbient(false);
    }

    synchronized void setMenuVolume(float volume) {
        menuVolume = clamp(volume);
        updateAmbient(false);
    }

    synchronized void setEffectsVolume(float volume) {
        effectsVolume = clamp(volume);
    }

    synchronized void setMenuVisible(boolean visible) {
        menuVisible = visible;
        updateAmbient(false);
    }

    synchronized void setHostSelectionVisible(boolean visible) {
        hostSelectionVisible = visible;
        updateAmbient(false);
    }

    /** The entry intro starts: the jingle plays and both loops hold until it has spoken. */
    synchronized void playIntro() {
        if (released || !resumed) return;
        introHoldUntil = SystemClock.uptimeMillis() + INTRO_HOLD_MS;
        updateAmbient(false);
        mainHandler.postDelayed(() -> updateAmbient(false), INTRO_HOLD_MS);
        if (!ambientEnabled || jinglePlayer == null) return;
        float gain = ambientGain(hostSelectionVolume);
        jinglePlayer.setVolume(gain, gain);
        // A finished jingle starts from its beginning; one still sounding just plays on.
        if (!jinglePlayer.isPlaying()) jinglePlayer.start();
    }

    /** This console is shown: it takes the shared ambience over at its live gain. */
    synchronized void resume() {
        if (released) return;
        resumed = true;
        resumedAt = SystemClock.uptimeMillis();
        controller = this;
        prepareIfNeeded(true);
        updateAmbient(false);
    }

    /** An engine never shown as a console (the stream's): its cues only, prepared now. */
    synchronized void prepareCues() {
        prepareIfNeeded(false);
    }

    /** This console is no longer visible; the ambience fades only if no console took it over. */
    synchronized void pause() {
        resumed = false;
        updateAmbient(true);
    }

    /** A directional key reached this console; {@code downTime} is its uptime press time. */
    synchronized void noteDirectionalInput(long downTime) {
        if (directionalInputCounts(resumed, resumedAt, downTime)) {
            lastDirectionalInputAt = SystemClock.uptimeMillis();
        }
    }

    /**
     * Only a key pressed on this resumed console counts. A key held or queued from the stream,
     * or another activity, across the return must not tick when focus is restored.
     */
    static boolean directionalInputCounts(boolean resumed, long resumedAt, long downTime) {
        return resumed && downTime >= resumedAt;
    }

    /** NAVIGATE follows real directional input, throttled; programmatic focus stays silent. */
    static boolean navigationCueDue(long now, long lastDirectionalInputAt, long lastNavigationAt) {
        return now - lastDirectionalInputAt < NAVIGATION_INPUT_WINDOW_MS
                && now - lastNavigationAt >= NAVIGATION_THROTTLE_MS;
    }

    synchronized void play(ConsoleAudioSynthesis.Cue cue) {
        if (released || !effectsEnabled) return;
        if (cue == ConsoleAudioSynthesis.Cue.NAVIGATE) {
            long now = SystemClock.uptimeMillis();
            if (!navigationCueDue(now, lastDirectionalInputAt, lastNavigationAt)) return;
            lastNavigationAt = now;
        }
        AudioTrack track = cueTracks.get(cue);
        if (track == null) {
            // A cue asked for while this console's cues are still being prepared (one that
            // opens with a notice) plays once they are; a navigation tick is only for now.
            if (cueTracks.isEmpty() && cue != ConsoleAudioSynthesis.Cue.NAVIGATE) {
                pendingCue = cue;
            }
            return;
        }
        float volume = effectsVolume * (cue == ConsoleAudioSynthesis.Cue.NAVIGATE
                ? 0.95f : 1f);
        try {
            // A cue still sounding starts again from its beginning.
            track.stop();
            track.reloadStaticData();
            track.setVolume(volume);
            track.play();
        } catch (IllegalStateException unavailable) {
            Log.w(TAG, "Console cue unavailable", unavailable);
        }
    }

    synchronized void playSystemConfirm() {
        if (!released && effectsEnabled && systemAudio != null) {
            systemAudio.playSoundEffect(SoundEffectConstants.CLICK, effectsVolume);
        }
    }

    synchronized void playSystemBack() {
        if (!released && effectsEnabled && systemAudio != null) {
            systemAudio.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN, effectsVolume);
        }
    }

    synchronized void release() {
        if (released) return;
        // Let the music ramp down and a playing cue finish; releasing them now cuts mid-note.
        resumed = false;
        updateAmbient(true);
        released = true;
        liveEngines--;
        UsbForwardingController.get(context).removeListener(routeChanged);
        preparationExecutor.shutdownNow();
        mainHandler.postDelayed(this::freePlayers, RELEASE_DELAY_MS);
    }

    private synchronized void freePlayers() {
        for (AudioTrack track : cueTracks.values()) track.release();
        cueTracks.clear();
        if (jinglePlayer != null) {
            jinglePlayer.release();
            jinglePlayer = null;
        }
        if (controller == this) controller = null;
        // The last console is gone: the process keeps no ambience player.
        if (liveEngines > 0 || controller != null) return;
        freeLoop(ENTRY_LOOP);
        freeLoop(MENU_LOOP);
    }

    private static void freeLoop(Loop loop) {
        loop.fadeGeneration++;
        if (loop.player != null) {
            loop.player.release();
            loop.player = null;
        }
        loop.volume = 0f;
    }

    private synchronized void prepareIfNeeded(boolean music) {
        if (preparationStarted || released) return;
        preparationStarted = true;
        boolean prepareEntry = music && claimPreparation(ENTRY_LOOP);
        boolean prepareMenu = music && claimPreparation(MENU_LOOP);
        preparationExecutor.execute(() -> {
            Map<ConsoleAudioSynthesis.Cue, AudioTrack> preparedCues =
                    new EnumMap<>(ConsoleAudioSynthesis.Cue.class);
            MediaPlayer preparedJingle = null;
            ConsoleMusicLoop preparedEntry = null;
            ConsoleMusicLoop preparedMenu = null;
            try {
                for (ConsoleAudioSynthesis.Cue cue : ConsoleAudioSynthesis.Cue.values()) {
                    preparedCues.put(cue, prepareCue(cue));
                }
                if (music) preparedJingle = prepareJingle();
                if (prepareEntry) {
                    preparedEntry = ConsoleMusicLoop.open(context, ENTRY_LOOP.resource);
                }
                if (prepareMenu) {
                    preparedMenu = ConsoleMusicLoop.open(context, MENU_LOOP.resource);
                }
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Unable to prepare console audio", error);
            }
            MediaPlayer jingle = preparedJingle;
            ConsoleMusicLoop entry = preparedEntry;
            ConsoleMusicLoop menu = preparedMenu;
            mainHandler.post(() -> installPreparedPlayers(preparedCues, jingle,
                    prepareEntry, entry, prepareMenu, menu));
        });
    }

    private static boolean claimPreparation(Loop loop) {
        if (loop.player != null || loop.preparing) return false;
        loop.preparing = true;
        return true;
    }

    private static AudioTrack prepareCue(ConsoleAudioSynthesis.Cue cue) {
        short[] samples = ConsoleAudioSynthesis.renderCue(cue, SAMPLE_RATE);
        AudioTrack track = new AudioTrack(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(), new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(), samples.length * 2, AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (track.getState() != AudioTrack.STATE_NO_STATIC_DATA
                || track.write(samples, 0, samples.length) != samples.length) {
            track.release();
            throw new IllegalStateException("Console cue track unavailable");
        }
        return track;
    }

    private MediaPlayer prepareJingle() throws IOException {
        MediaPlayer player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        try (AssetFileDescriptor descriptor = context.getResources()
                .openRawResourceFd(R.raw.moonwaker_intro_jingle)) {
            if (descriptor == null) throw new IOException("Music resource is compressed");
            player.setDataSource(descriptor.getFileDescriptor(),
                    descriptor.getStartOffset(), descriptor.getLength());
        }
        player.setVolume(0f, 0f);
        player.prepare();
        return player;
    }

    private synchronized void installPreparedPlayers(
            Map<ConsoleAudioSynthesis.Cue, AudioTrack> preparedCues, MediaPlayer preparedJingle,
            boolean entryHere, ConsoleMusicLoop preparedEntry,
            boolean menuHere, ConsoleMusicLoop preparedMenu) {
        if (entryHere) installLoop(ENTRY_LOOP, preparedEntry);
        if (menuHere) installLoop(MENU_LOOP, preparedMenu);
        if (released) {
            for (AudioTrack track : preparedCues.values()) track.release();
            if (preparedJingle != null) preparedJingle.release();
            return;
        }
        cueTracks.putAll(preparedCues);
        jinglePlayer = preparedJingle;
        applyRoute();
        ConsoleAudioSynthesis.Cue pending = pendingCue;
        pendingCue = null;
        if (pending != null && resumed) play(pending);
        Log.i(TAG, "Console sounds ready, shared ambience "
                + (ENTRY_LOOP.player != null && MENU_LOOP.player != null
                ? "ready" : "unavailable"));
    }

    private static void installLoop(Loop loop, ConsoleMusicLoop player) {
        loop.preparing = false;
        if (loop.player != null || player == null || liveEngines == 0) {
            if (player != null) player.release();
            return;
        }
        loop.player = player;
        loop.volume = 0f;
        if (controller != null) {
            synchronized (controller) {
                controller.applyRoute();
                controller.updateAmbient(false);
            }
        }
    }

    /** This console's players and the shared loops follow ConsoleAudioRoute. Main thread. */
    private synchronized void applyRoute() {
        if (released) return;
        AudioDeviceInfo output = ConsoleAudioRoute.preferredOutput(context);
        for (AudioTrack track : cueTracks.values()) track.setPreferredDevice(output);
        if (jinglePlayer != null) jinglePlayer.setPreferredDevice(output);
        if (ENTRY_LOOP.player != null) ENTRY_LOOP.player.setPreferredDevice(output);
        if (MENU_LOOP.player != null) MENU_LOOP.player.setPreferredDevice(output);
    }

    /**
     * {@code leaving}: this console is going away, so the music ramps down quickly. Only the
     * controller moves the shared gains: a console under the one shown changes nothing.
     */
    private synchronized void updateAmbient(boolean leaving) {
        if (released || controller != this) return;
        boolean audible = resumed && menuVisible && ambientEnabled
                && SystemClock.uptimeMillis() >= introHoldUntil;
        int steps = leaving ? LEAVE_FADE_STEPS : FADE_STEPS;
        fadeLoop(ENTRY_LOOP, audible && hostSelectionVisible
                ? ambientGain(hostSelectionVolume) : 0f, steps);
        fadeLoop(MENU_LOOP, audible && !hostSelectionVisible
                ? ambientGain(menuVolume) : 0f, steps);
    }

    /** Every gain change, including the stop, is a ramp; the player pauses only at silence. */
    private void fadeLoop(Loop loop, float target, int steps) {
        if (loop.player == null) return;
        // A paused loop resumes where it stopped: no seek, no restart.
        if (target > 0f && !loop.player.isPlaying()) loop.player.start();
        int generation = ++loop.fadeGeneration;
        float start = loop.volume;
        for (int step = 1; step <= steps; step++) {
            int currentStep = step;
            mainHandler.postDelayed(() -> applyFadeStep(
                    loop, generation, start, target, currentStep, steps), FADE_STEP_MS * step);
        }
    }

    private static void applyFadeStep(Loop loop, int generation, float start, float target,
                                      int step, int steps) {
        if (generation != loop.fadeGeneration || loop.player == null) return;
        float fraction = step / (float) steps;
        loop.volume = clamp(start + (target - start) * fraction);
        loop.player.setVolume(loop.volume);
        if (step == steps && target == 0f && loop.player.isPlaying()) {
            loop.player.pause();
        }
    }

    /**
     * Maps a music slider level (0..1, ten steps) to player gain with equal dB per step,
     * so step 1 sits barely audible under the UI and step 10 keeps 10 dB of headroom.
     */
    static float ambientGain(float level) {
        if (level <= 0f) return 0f;
        float steps = ConsolePanel.STEPS;
        float step = Math.max(1f, Math.min(steps, level * steps));
        float db = AMBIENT_MIN_DB + (step - 1f) * (AMBIENT_MAX_DB - AMBIENT_MIN_DB) / (steps - 1f);
        return (float) Math.pow(10.0, db / 20.0);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
