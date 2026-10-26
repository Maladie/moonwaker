package com.limelight.binding.usb;

/** The native usbfs calls (jni/moonwaker-usbfs); {@link UsbfsTransfers} owns their use. */
final class Usbfs implements UsbfsTransfers.Io {
    static {
        System.loadLibrary("moonwaker-usbfs");
    }

    @Override public long submit(int fd, int type, int endpoint, byte[] data, int length,
                                 int[] isoLengths) {
        return nativeSubmit(fd, type, endpoint, data, length, isoLengths);
    }

    @Override public long reap(int fd, int timeoutMs) {
        return nativeReap(fd, timeoutMs);
    }

    @Override public int finish(long urb, byte[] data, int[] isoActual, int[] isoStatus,
                                int[] meta) {
        return nativeFinish(urb, data, isoActual, isoStatus, meta);
    }

    @Override public int discard(int fd, long urb) {
        return nativeDiscard(fd, urb);
    }

    private static native long nativeSubmit(int fd, int type, int endpoint, byte[] data,
                                            int length, int[] isoLengths);

    private static native long nativeReap(int fd, int timeoutMs);

    private static native int nativeFinish(long urb, byte[] data, int[] isoActual,
                                           int[] isoStatus, int[] meta);

    private static native int nativeDiscard(int fd, long urb);
}
