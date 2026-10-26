package com.limelight.console;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * One looping music asset, decoded here and written to a streaming AudioTrack. While a USB
 * audio device is forwarded Android keeps it as the media route, and every music player
 * starting elsewhere invalidates all music tracks. MediaPlayer answers each invalidation by
 * restarting its decoder from the last anchor, so the same fragment repeated for as long as
 * the invalidations came (NuPlayer restartAudio, identical timeUs). This track is restored
 * in place and the writer simply goes on: a dropped buffer at most, never a replay.
 */
final class ConsoleMusicLoop {
    private static final String TAG = "MoonWakerAudio";
    private static final long CODEC_TIMEOUT_US = 10_000L;

    private final MediaExtractor extractor;
    private final MediaCodec decoder;
    private final AudioTrack track;
    private final int bytesPerSecond;
    private volatile boolean released;

    private ConsoleMusicLoop(MediaExtractor extractor, MediaCodec decoder, AudioTrack track,
                             int bytesPerSecond) {
        this.extractor = extractor;
        this.decoder = decoder;
        this.track = track;
        this.bytesPerSecond = bytesPerSecond;
    }

    /** A silent, paused loop of {@code resource}, ready to start. Not on the main thread. */
    static ConsoleMusicLoop open(Context context, int resource) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            try (AssetFileDescriptor descriptor = context.getResources()
                    .openRawResourceFd(resource)) {
                if (descriptor == null) throw new IOException("Music resource is compressed");
                extractor.setDataSource(descriptor.getFileDescriptor(),
                        descriptor.getStartOffset(), descriptor.getLength());
            }
            if (extractor.getTrackCount() < 1) throw new IOException("Music resource is empty");
            extractor.selectTrack(0);
            MediaFormat format = extractor.getTrackFormat(0);
            int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();
            int mask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(mask)
                            .build())
                    .setBufferSizeInBytes(4 * AudioTrack.getMinBufferSize(
                            rate, mask, AudioFormat.ENCODING_PCM_16BIT))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                    .build();
            track.setVolume(0f);
            ConsoleMusicLoop loop = new ConsoleMusicLoop(extractor, decoder, track,
                    rate * channels * 2);
            Thread writer = new Thread(loop::run, "MoonWaker-music");
            writer.setDaemon(true);
            writer.start();
            return loop;
        } catch (IOException | RuntimeException error) {
            if (decoder != null) decoder.release();
            extractor.release();
            throw error;
        }
    }

    void start() {
        if (!released) track.play();
    }

    /** Stops where it is: the next start goes on from here. */
    void pause() {
        if (!released) track.pause();
    }

    boolean isPlaying() {
        return !released && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    void setVolume(float volume) {
        if (!released) track.setVolume(volume);
    }

    void setPreferredDevice(AudioDeviceInfo device) {
        if (!released) track.setPreferredDevice(device);
    }

    /** The writer lets go of the decoder and the track as it ends. */
    void release() {
        released = true;
        track.stop(); // A write waiting on a paused track returns.
    }

    private void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (!released) {
                int input = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                if (input >= 0) {
                    ByteBuffer buffer = decoder.getInputBuffer(input);
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        // The loop starts over without a gap or a decoder flush.
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        size = Math.max(0, extractor.readSampleData(buffer, 0));
                    }
                    decoder.queueInputBuffer(input, 0, size, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
                int output = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (output >= 0) {
                    ByteBuffer pcm = decoder.getOutputBuffer(output);
                    pcm.limit(info.offset + info.size).position(info.offset);
                    write(pcm);
                    decoder.releaseOutputBuffer(output, false);
                }
            }
        } catch (RuntimeException error) {
            if (!released) Log.w(TAG, "Console music stopped", error);
        } finally {
            decoder.release();
            extractor.release();
            track.release();
        }
    }

    private void write(ByteBuffer pcm) {
        while (pcm.hasRemaining() && !released) {
            int written = track.write(pcm, pcm.remaining(), AudioTrack.WRITE_BLOCKING);
            int dropped = droppedAfterWrite(pcm.remaining(), written);
            if (dropped > 0) {
                // An output that could not be restored: this audio is lost, in real time.
                pcm.position(pcm.limit());
                SystemClock.sleep(Math.max(1L, dropped * 1000L / bytesPerSecond));
            }
        }
    }

    /**
     * Bytes of {@code remaining} to give up after a write returned {@code written}: all of
     * them on an error, so a failing write never offers the same audio again.
     */
    static int droppedAfterWrite(int remaining, int written) {
        return written < 0 ? remaining : 0;
    }
}
