package com.limelight.binding.usb;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Bulk, interrupt and isochronous transfers of one device as usbfs URBs, with one reaper.
 * Pure Java over {@link Io}, so the mapping between USB/IP transfers and URBs is tested on
 * the JVM; {@link Usbfs} is the native implementation.
 */
final class UsbfsTransfers {
    /** usbfs URB types. */
    static final int URB_ISO = 0;
    static final int URB_INTERRUPT = 1;
    static final int URB_BULK = 3;
    /** usbfs takes at most this many packets in one isochronous URB. */
    static final int MAX_ISO_PACKETS = 128;
    private static final int REAP_TIMEOUT_MS = 200;
    private static final long CLOSE_DRAIN_MS = 1_000;

    interface Io {
        /** A URB handle, or a negative errno. */
        long submit(int fd, int type, int endpoint, byte[] data, int length, int[] isoLengths);

        /** A completed URB, 0 after the timeout, or a negative errno once nothing is left. */
        long reap(int fd, int timeoutMs);

        /** Copies the results (meta: actual length, start frame, error count), frees the URB. */
        int finish(long urb, byte[] data, int[] isoActual, int[] isoStatus, int[] meta);

        int discard(int fd, long urb);
    }

    private static final class InFlight {
        final UsbIpExporter.Transfer transfer;
        final byte[] data;

        InFlight(UsbIpExporter.Transfer transfer, byte[] data) {
            this.transfer = transfer;
            this.data = data;
        }
    }

    private final Io io;
    private final int fd;
    /** usbfs URB type of an endpoint address, or -1 when the device has no such endpoint. */
    private final IntUnaryOperator urbType;
    private final Map<Long, InFlight> inFlight = new HashMap<>();
    private volatile boolean closed;

    UsbfsTransfers(Io io, int fd, IntUnaryOperator urbType) {
        this.io = io;
        this.fd = fd;
        this.urbType = urbType;
    }

    boolean queue(UsbIpExporter.Transfer transfer) {
        int type = urbType.applyAsInt(transfer.address());
        boolean iso = transfer.isoLengths != null;
        if (type < 0 || iso != (type == URB_ISO)
                || iso && transfer.isoLengths.length > MAX_ISO_PACKETS) {
            return false;
        }
        byte[] data;
        if (iso) {
            // usbfs lays isochronous packets one after the other at their lengths.
            int total = 0;
            for (int length : transfer.isoLengths) total += length;
            data = new byte[total];
            if (!transfer.in) {
                int position = 0;
                for (int index = 0; index < transfer.isoLengths.length; index++) {
                    System.arraycopy(transfer.buffer.array(), transfer.isoOffsets[index], data,
                            position, transfer.isoLengths[index]);
                    position += transfer.isoLengths[index];
                }
            }
        } else {
            data = transfer.buffer.array();
        }
        synchronized (this) {
            if (closed) return false;
            long urb = io.submit(fd, type, transfer.address(), data, data.length,
                    transfer.isoLengths);
            if (urb <= 0) return false;
            transfer.handle = urb;
            inFlight.put(urb, new InFlight(transfer, data));
        }
        return true;
    }

    /** The next completed transfer, or null once the device is gone or this is closed. */
    UsbIpExporter.Transfer awaitCompletion() {
        while (!closed) {
            long urb = io.reap(fd, REAP_TIMEOUT_MS);
            if (urb < 0) {
                com.limelight.diagnostics.MoonWakerDiagnostics.record("WARN", "android.usb",
                        "usbfs.reap_failed", "reason", "errno_" + (-urb));
                return null;
            }
            if (urb == 0) continue;
            InFlight done;
            synchronized (this) {
                done = inFlight.remove(urb);
            }
            if (done != null) return complete(urb, done);
        }
        return null;
    }

    private UsbIpExporter.Transfer complete(long urb, InFlight done) {
        UsbIpExporter.Transfer transfer = done.transfer;
        int packets = transfer.isoLengths == null ? 0 : transfer.isoLengths.length;
        int[] meta = new int[3];
        transfer.isoActual = new int[packets];
        transfer.isoStatus = new int[packets];
        transfer.status = io.finish(urb, done.data, transfer.isoActual, transfer.isoStatus, meta);
        transfer.handle = null;
        transfer.actualLength = meta[0];
        transfer.startFrame = meta[1];
        transfer.errorCount = meta[2];
        if (packets > 0 && transfer.in) {
            // Back to the offsets the host asked for.
            int position = 0;
            for (int index = 0; index < packets; index++) {
                System.arraycopy(done.data, position, transfer.buffer.array(),
                        transfer.isoOffsets[index], transfer.isoActual[index]);
                position += transfer.isoLengths[index];
            }
        }
        return transfer;
    }

    /** The URB completes (discarded) through {@link #awaitCompletion}. */
    synchronized void cancel(UsbIpExporter.Transfer transfer) {
        Object urb = transfer.handle;
        if (urb instanceof Long && inFlight.containsKey(urb)) io.discard(fd, (Long) urb);
    }

    /** Discards what is in flight and frees it; the descriptor is closed after this. */
    void close() {
        synchronized (this) {
            closed = true;
            for (Long urb : inFlight.keySet()) io.discard(fd, urb);
        }
        long deadline = System.currentTimeMillis() + CLOSE_DRAIN_MS;
        while (System.currentTimeMillis() < deadline) {
            synchronized (this) {
                if (inFlight.isEmpty()) return;
            }
            long urb = io.reap(fd, REAP_TIMEOUT_MS);
            if (urb < 0) return;
            InFlight done;
            synchronized (this) {
                done = inFlight.remove(urb);
            }
            if (done != null) complete(urb, done);
        }
    }
}
