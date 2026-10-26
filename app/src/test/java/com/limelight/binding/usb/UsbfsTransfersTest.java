package com.limelight.binding.usb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class UsbfsTransfersTest {
    /** usbfs as seen from Java: what was submitted, and what reap and finish hand back. */
    static final class FakeIo implements UsbfsTransfers.Io {
        final List<Object[]> submitted = new ArrayList<>();
        final List<Long> discarded = new ArrayList<>();
        final Deque<Long> completions = new ArrayDeque<>();
        byte[] inData = new byte[0];
        int[] isoActual = new int[0];
        int status;
        long next = 7;

        @Override public long submit(int fd, int type, int endpoint, byte[] data, int length,
                                     int[] isoLengths) {
            submitted.add(new Object[]{fd, type, endpoint, data.clone(), length, isoLengths});
            return next++;
        }

        @Override public long reap(int fd, int timeoutMs) {
            Long urb = completions.poll();
            return urb == null ? -19 : urb;
        }

        @Override public int finish(long urb, byte[] data, int[] actual, int[] packetStatus,
                                    int[] meta) {
            System.arraycopy(inData, 0, data, 0, Math.min(inData.length, data.length));
            System.arraycopy(isoActual, 0, actual, 0, Math.min(isoActual.length, actual.length));
            meta[0] = inData.length;
            meta[1] = 99;
            return status;
        }

        @Override public int discard(int fd, long urb) {
            discarded.add(urb);
            completions.add(urb);
            return 0;
        }
    }

    private static UsbIpExporter.Transfer iso(boolean in, byte[] buffer, int[] offsets,
                                              int[] lengths) {
        UsbIpExporter.Transfer transfer = new UsbIpExporter.Transfer(5, in ? 1 : 2, in,
                new byte[8], ByteBuffer.wrap(buffer));
        transfer.isoOffsets = offsets;
        transfer.isoLengths = lengths;
        return transfer;
    }

    private static UsbfsTransfers transfers(FakeIo io) {
        // 0x81 isochronous IN, 0x02 isochronous OUT, 0x83 interrupt IN.
        return new UsbfsTransfers(io, 42, address -> address == 0x81 || address == 0x02
                ? UsbfsTransfers.URB_ISO : address == 0x83 ? UsbfsTransfers.URB_INTERRUPT : -1);
    }

    @Test public void isochronousOutIsCompactedForUsbfs() {
        FakeIo io = new FakeIo();
        // The host left a gap between its packets; usbfs wants them one after the other.
        UsbIpExporter.Transfer out = iso(false, new byte[]{1, 2, 0, 0, 3, 4}, new int[]{0, 4},
                new int[]{2, 2});
        assertTrue(transfers(io).queue(out));
        Object[] call = io.submitted.get(0);
        assertEquals(42, call[0]);
        assertEquals(UsbfsTransfers.URB_ISO, call[1]);
        assertEquals(0x02, call[2]);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, (byte[]) call[3]);
        assertArrayEquals(new int[]{2, 2}, (int[]) call[5]);
    }

    @Test public void isochronousInGoesBackToTheHostsOffsets() {
        FakeIo io = new FakeIo();
        UsbfsTransfers transfers = transfers(io);
        UsbIpExporter.Transfer in = iso(true, new byte[8], new int[]{0, 4}, new int[]{3, 3});
        assertTrue(transfers.queue(in));
        // usbfs: packet 0 at 0, packet 1 at 3 (its requested length), 3 and 2 bytes received.
        io.inData = new byte[]{1, 2, 3, 4, 5, 0};
        io.isoActual = new int[]{3, 2};
        io.completions.add(7L);
        assertSame(in, transfers.awaitCompletion());
        assertArrayEquals(new byte[]{1, 2, 3, 0, 4, 5, 0, 0}, in.buffer.array());
        assertArrayEquals(new int[]{3, 2}, in.isoActual);
        assertEquals(99, in.startFrame);
    }

    @Test public void interruptKeepsItsBufferAndReportsTheDevicesStatus() {
        FakeIo io = new FakeIo();
        UsbfsTransfers transfers = transfers(io);
        UsbIpExporter.Transfer interrupt = new UsbIpExporter.Transfer(6, 3, true, new byte[8],
                ByteBuffer.wrap(new byte[4]));
        assertTrue(transfers.queue(interrupt));
        io.inData = new byte[]{9, 8};
        io.status = -32;
        io.completions.add(7L);
        assertSame(interrupt, transfers.awaitCompletion());
        // A stall is reported, unlike UsbRequest's empty success.
        assertEquals(-32, interrupt.status);
        assertEquals(2, interrupt.actualLength);
    }

    @Test public void urbsTheDeviceCannotTakeAreRefused() {
        FakeIo io = new FakeIo();
        UsbfsTransfers transfers = transfers(io);
        // An isochronous transfer on an interrupt endpoint, a missing endpoint, too many packets.
        UsbIpExporter.Transfer wrongType = iso(true, new byte[2], new int[]{0}, new int[]{2});
        UsbIpExporter.Transfer onInterrupt = new UsbIpExporter.Transfer(1, 3, true, new byte[8],
                ByteBuffer.wrap(new byte[2]));
        onInterrupt.isoOffsets = wrongType.isoOffsets;
        onInterrupt.isoLengths = wrongType.isoLengths;
        assertFalse(transfers.queue(onInterrupt));
        assertFalse(transfers.queue(new UsbIpExporter.Transfer(2, 9, true, new byte[8],
                ByteBuffer.wrap(new byte[2]))));
        int packets = UsbfsTransfers.MAX_ISO_PACKETS + 1;
        assertFalse(transfers.queue(iso(true, new byte[packets], new int[packets],
                new int[packets])));
        assertTrue(io.submitted.isEmpty());
    }

    @Test public void cancelDiscardsAndCloseDrainsEverything() {
        FakeIo io = new FakeIo();
        UsbfsTransfers transfers = transfers(io);
        UsbIpExporter.Transfer first = iso(true, new byte[2], new int[]{0}, new int[]{2});
        UsbIpExporter.Transfer second = iso(true, new byte[2], new int[]{0}, new int[]{2});
        assertTrue(transfers.queue(first));
        assertTrue(transfers.queue(second));
        transfers.cancel(first);
        assertEquals(List.of(7L), io.discarded);
        assertSame(first, transfers.awaitCompletion());
        transfers.close();
        assertEquals(List.of(7L, 8L), io.discarded);
        assertNull(second.handle);
        assertFalse(transfers.queue(iso(true, new byte[2], new int[]{0}, new int[]{2})));
    }
}
