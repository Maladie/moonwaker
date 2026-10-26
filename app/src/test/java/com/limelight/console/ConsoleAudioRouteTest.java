package com.limelight.console;

import static org.junit.Assert.assertEquals;

import android.media.AudioDeviceInfo;

import org.junit.Test;

public class ConsoleAudioRouteTest {
    @Test public void aForwardedUsbHeadsetLeavesTheSoundsOnTheTvsOwnOutput() {
        int[] types = {AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_HDMI_ARC};
        assertEquals(2, ConsoleAudioRoute.tvOutputIndex(types));
        assertEquals(1, ConsoleAudioRoute.tvOutputIndex(new int[]{
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}));
        assertEquals(-1, ConsoleAudioRoute.tvOutputIndex(new int[]{
                AudioDeviceInfo.TYPE_USB_HEADSET}));
    }
}
