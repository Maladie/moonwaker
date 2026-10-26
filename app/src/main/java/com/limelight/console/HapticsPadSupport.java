package com.limelight.console;

import android.content.Context;
import android.view.InputDevice;

import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Which connected pads can play audio haptics, as one line per pad for the setting's description
 * and the feature showcase. Audio haptics rumble the pad through Android, so the answer is
 * {@link ControllerHandler#canRumble}, the check the stream itself uses.
 */
final class HapticsPadSupport {
    static final class Pad {
        final String name;
        final boolean rumbles;

        Pad(String name, boolean rumbles) {
            this.name = name;
            this.rumbles = rumbles;
        }
    }

    private HapticsPadSupport() {
    }

    /** The connected pads (virtual devices excluded) and whether each one rumbles. */
    static List<Pad> connected() {
        List<Pad> pads = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual() || (device.getSources()
                    & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) continue;
            pads.add(new Pad(ConsoleActivity.compactControllerName(device.getName()),
                    ControllerHandler.canRumble(device)));
        }
        return pads;
    }

    static String describe(Context context) {
        return format(connected(), context.getString(R.string.haptics_pad_supported),
                context.getString(R.string.haptics_pad_unsupported),
                context.getString(R.string.haptics_pad_none));
    }

    static String format(List<Pad> pads, String supported, String unsupported, String none) {
        if (pads.isEmpty()) return none;
        StringBuilder text = new StringBuilder();
        for (Pad pad : pads) {
            if (text.length() > 0) text.append('\n');
            text.append(pad.name).append(": ").append(pad.rumbles ? supported : unsupported);
        }
        return text.toString();
    }
}
