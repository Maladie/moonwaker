package com.limelight.binding.video;

public interface CrashListener {
    /** {@code renderer} failed past its codec recovery and stopped decoding (any thread). */
    void notifyCrash(MediaCodecDecoderRenderer renderer, Exception e);
}
