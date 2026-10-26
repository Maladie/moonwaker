package com.limelight.console;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import com.limelight.binding.usb.UsbForwardingController;

/**
 * Where MoonWaker's own sounds play. A USB audio device forwarded to the host is claimed
 * away from Android's USB audio driver, yet Android keeps routing media to it (its output
 * fails to open, so everything is silent). While one is forwarded, MoonWaker's players
 * prefer the TV's own output; otherwise the system chooses.
 */
final class ConsoleAudioRoute {
    private ConsoleAudioRoute() { }

    /** The output MoonWaker's players should prefer now, or null for the system's choice. */
    static AudioDeviceInfo preferredOutput(Context context) {
        if (!UsbForwardingController.forwardsAudio()) return null;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return null;
        AudioDeviceInfo[] outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        int[] types = new int[outputs.length];
        for (int index = 0; index < outputs.length; index++) types[index] = outputs[index].getType();
        int chosen = tvOutputIndex(types);
        return chosen < 0 ? null : outputs[chosen];
    }

    /** Index of the most preferred TV output among {@code types}, or -1. */
    static int tvOutputIndex(int[] types) {
        // A soundbar or receiver first, then the TV itself.
        int[] preferred = {AudioDeviceInfo.TYPE_HDMI_EARC, AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_HDMI};
        for (int wanted : preferred) {
            for (int index = 0; index < types.length; index++) {
                if (types[index] == wanted) return index;
            }
        }
        return -1;
    }
}
