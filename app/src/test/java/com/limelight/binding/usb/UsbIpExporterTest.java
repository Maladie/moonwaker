package com.limelight.binding.usb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.limelight.diagnostics.MoonWakerDiagnostics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class UsbIpExporterTest {
    private static final byte[] DEVICE = {18, 1, 0x00, 0x02, 0, 0, 0, 64,
            0x6d, 0x04, 0x4f, (byte) 0xc2, 0x00, 0x01, 1, 2, 0, 1};
    private static final byte[] CONFIG = {9, 2, 25, 0, 1, 1, 0, (byte) 0x80, 50,
            9, 4, 0, 0, 1, 3, 0, 0, 0,
            7, 5, (byte) 0x81, 3, 64, 0, 1};

    private FakeDevice device;
    private Socket gateway;
    private DataInputStream fromTv;
    private DataOutputStream toTv;
    private Thread serving;
    private UsbIpExporter exporter;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicBoolean imported =
            new java.util.concurrent.atomic.AtomicBoolean();

    static final class FakeDevice implements UsbIpExporter.Device {
        byte[] raw = concat(DEVICE, CONFIG);
        final List<String> calls = new ArrayList<>();
        final BlockingQueue<UsbIpExporter.Transfer> queued = new LinkedBlockingQueue<>();
        final BlockingQueue<Object> completed = new LinkedBlockingQueue<>();
        volatile boolean stall;

        @Override public byte[] rawDescriptors() { return raw; }

        @Override
        public synchronized int control(int requestType, int request, int value, int index,
                                        byte[] buffer, int length) {
            calls.add("control " + requestType + "/" + request);
            if (stall) return -1;
            if (request == 6 && value >> 8 == 1) {
                int size = Math.min(length, DEVICE.length);
                System.arraycopy(DEVICE, 0, buffer, 0, size);
                return size;
            }
            return length;
        }

        @Override public synchronized boolean setConfiguration(int value) {
            calls.add("configuration " + value);
            return true;
        }

        @Override public synchronized boolean setInterface(int number, int alternate) {
            calls.add("interface " + number + "/" + alternate);
            return true;
        }

        @Override public boolean queue(UsbIpExporter.Transfer transfer) {
            if (transfer.endpoint != 1 && transfer.endpoint != 2) return false;
            queued.add(transfer);
            return true;
        }

        @Override public UsbIpExporter.Transfer awaitCompletion() {
            try {
                Object next = completed.take();
                return next instanceof UsbIpExporter.Transfer ? (UsbIpExporter.Transfer) next : null;
            } catch (InterruptedException stopped) {
                return null;
            }
        }

        @Override public synchronized void cancel(UsbIpExporter.Transfer transfer) {
            calls.add("cancel " + transfer.seqnum);
            completed.add(transfer);
        }

        void finish(UsbIpExporter.Transfer transfer, byte[] data) {
            transfer.buffer.put(data);
            transfer.actualLength = transfer.buffer.position();
            completed.add(transfer);
        }
    }

    static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @Before
    public void connect() throws Exception {
        device = new FakeDevice();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            gateway = new Socket(server.getInetAddress(), server.getLocalPort());
            Socket tv = server.accept();
            // A fixed lead and a long prefill: a slow test machine's pauses move neither.
            exporter = new UsbIpExporter(device, 1, 2, tv.getInputStream(),
                    tv.getOutputStream(), UsbIpExporter.ISO_ACK_LEAD_PACKETS, 300);
            serving = new Thread(() -> {
                try {
                    exporter.run(() -> imported.set(true));
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    device.completed.add("closed");
                }
            });
        }
        gateway.setSoTimeout(5_000);
        fromTv = new DataInputStream(gateway.getInputStream());
        toTv = new DataOutputStream(gateway.getOutputStream());
        serving.start();
    }

    @After
    public void close() throws Exception {
        gateway.close();
        serving.join(5_000);
    }

    private void requestImport(String busId) throws IOException {
        toTv.writeShort(0x0111);
        toTv.writeShort(0x8003);
        toTv.writeInt(0);
        toTv.write(Arrays.copyOf(busId.getBytes(StandardCharsets.US_ASCII), 32));
        toTv.flush();
    }

    private ByteBuffer imported() throws IOException {
        requestImport("1-2");
        assertEquals(0x0111, fromTv.readUnsignedShort());
        assertEquals(0x0003, fromTv.readUnsignedShort());
        assertEquals(0, fromTv.readInt());
        byte[] record = new byte[312];
        fromTv.readFully(record);
        return ByteBuffer.wrap(record);
    }

    private void submit(int seqnum, int direction, int endpoint, int length, byte[] setup,
                        byte[] data, int packets) throws IOException {
        toTv.writeInt(1);
        toTv.writeInt(seqnum);
        toTv.writeInt(0x10002);
        toTv.writeInt(direction);
        toTv.writeInt(endpoint);
        toTv.writeInt(0);
        toTv.writeInt(length);
        toTv.writeInt(0);
        toTv.writeInt(packets);
        toTv.writeInt(0);
        toTv.write(setup);
        toTv.write(data);
        toTv.flush();
    }

    private static byte[] setup(int requestType, int request, int value, int index, int length) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).put((byte) requestType)
                .put((byte) request).putShort((short) value).putShort((short) index)
                .putShort((short) length).array();
    }

    private void unlink(int seqnum, int target) throws IOException {
        toTv.writeInt(2);
        toTv.writeInt(seqnum);
        toTv.writeInt(0x10002);
        toTv.writeInt(0);
        toTv.writeInt(0);
        toTv.writeInt(target);
        toTv.write(new byte[24]);
        toTv.flush();
    }

    /** {command, seqnum, status, actual} and the payload of an IN answer. */
    private int[] answer(byte[] payload) throws IOException {
        int command = fromTv.readInt();
        int seqnum = fromTv.readInt();
        fromTv.skipBytes(12);
        int status = fromTv.readInt();
        int actual = command == 3 ? fromTv.readInt() : 0;
        fromTv.skipBytes(command == 3 ? 20 : 24);
        if (payload != null) fromTv.readFully(payload, 0, actual);
        return new int[]{command, seqnum, status, actual};
    }

    @Test
    public void importDescribesTheDevice() throws Exception {
        ByteBuffer record = imported();
        for (int wait = 0; wait < 500 && !imported.get(); wait++) Thread.sleep(10);
        assertTrue(imported.get());
        byte[] busId = new byte[3];
        record.position(256);
        record.get(busId);
        assertEquals("1-2", new String(busId, StandardCharsets.US_ASCII));
        record.position(288);
        assertEquals(1, record.getInt());
        assertEquals(2, record.getInt());
        assertEquals(2, record.getInt());
        assertEquals(0x046d, record.getShort() & 0xffff);
        assertEquals(0xc24f, record.getShort() & 0xffff);
        assertEquals(0x0100, record.getShort() & 0xffff);
        assertEquals(0, record.get());
        record.position(record.position() + 2);
        assertEquals(1, record.get());
        assertEquals(1, record.get());
        assertEquals(1, record.get());
    }

    @Test
    public void importOfAnotherDeviceIsRefused() throws Exception {
        requestImport("1-3");
        fromTv.skipBytes(4);
        assertEquals(1, fromTv.readInt());
        serving.join(5_000);
        assertTrue(failure.get() instanceof ProtocolException);
        // The owner never hears "imported" for a refused import.
        assertFalse(imported.get());
    }

    @Test
    public void controlTransfersRunOnTheDeviceAndConfigurationGoesThroughTheApi()
            throws Exception {
        imported();
        submit(1, 1, 0, 18, setup(0x80, 6, 0x0100, 0, 18), new byte[0], 0);
        byte[] descriptor = new byte[18];
        assertArrayEquals(new int[]{3, 1, 0, 18}, answer(descriptor));
        assertArrayEquals(DEVICE, descriptor);
        submit(2, 0, 0, 0, setup(0x00, 9, 1, 0, 0), new byte[0], 0);
        assertArrayEquals(new int[]{3, 2, 0, 0}, answer(null));
        submit(3, 0, 0, 0, setup(0x01, 11, 1, 0, 0), new byte[0], 0);
        assertArrayEquals(new int[]{3, 3, 0, 0}, answer(null));
        device.stall = true;
        submit(4, 0, 0, 2, setup(0x21, 9, 0x0200, 0, 2), new byte[]{1, 2}, 0);
        assertArrayEquals(new int[]{3, 4, UsbIpExporter.EPIPE, 0}, answer(null));
        assertEquals(Arrays.asList("control 128/6", "configuration 1", "interface 0/1",
                "control 33/9"), device.calls);
    }

    @Test
    public void interruptTransfersAreQueuedAndAnsweredWithTheirData() throws Exception {
        imported();
        submit(5, 1, 1, 64, new byte[8], new byte[0], -1);
        UsbIpExporter.Transfer input = device.queued.poll(5, TimeUnit.SECONDS);
        assertEquals(0x81, input.address());
        submit(6, 0, 2, 3, new byte[8], new byte[]{7, 8, 9}, 0);
        UsbIpExporter.Transfer output = device.queued.poll(5, TimeUnit.SECONDS);
        assertArrayEquals(new byte[]{7, 8, 9}, output.buffer.array());
        output.actualLength = 3;
        device.completed.add(output);
        assertArrayEquals(new int[]{3, 6, 0, 3}, answer(null));
        device.finish(input, new byte[]{1, 2, 3, 4});
        byte[] report = new byte[4];
        assertArrayEquals(new int[]{3, 5, 0, 4}, answer(report));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, report);
        // An endpoint the device does not have stalls instead of hanging.
        submit(7, 1, 9, 8, new byte[8], new byte[0], 0);
        assertArrayEquals(new int[]{3, 7, UsbIpExporter.EPIPE, 0}, answer(null));
    }

    /** CMD_SUBMIT of an isochronous URB: buffer (OUT), then one descriptor per packet. */
    private void submitIso(int seqnum, int direction, int endpoint, byte[] buffer,
                           int[] offsets, int[] lengths) throws IOException {
        submit(seqnum, direction, endpoint, buffer.length, new byte[8],
                direction == 0 ? buffer : new byte[0], offsets.length);
        for (int index = 0; index < offsets.length; index++) {
            toTv.writeInt(offsets[index]);
            toTv.writeInt(lengths[index]);
            toTv.writeInt(0);
            toTv.writeInt(0);
        }
        toTv.flush();
    }

    @Test
    public void isochronousInComesBackPackedWithItsDescriptors() throws Exception {
        imported();
        submitIso(10, 1, 1, new byte[12], new int[]{0, 4, 8}, new int[]{4, 4, 4});
        UsbIpExporter.Transfer transfer = device.queued.poll(5, TimeUnit.SECONDS);
        assertArrayEquals(new int[]{0, 4, 8}, transfer.isoOffsets);
        byte[] buffer = transfer.buffer.array();
        System.arraycopy(new byte[]{1, 2, 3, 4}, 0, buffer, 0, 4);
        System.arraycopy(new byte[]{5, 6}, 0, buffer, 4, 2);
        transfer.isoActual = new int[]{4, 2, 0};
        transfer.isoStatus = new int[]{0, 0, -18};
        transfer.startFrame = 77;
        transfer.errorCount = 1;
        device.completed.add(transfer);
        assertEquals(3, fromTv.readInt());
        assertEquals(10, fromTv.readInt());
        fromTv.skipBytes(12);
        assertEquals(0, fromTv.readInt());
        assertEquals(6, fromTv.readInt());
        assertEquals(77, fromTv.readInt());
        assertEquals(3, fromTv.readInt());
        assertEquals(1, fromTv.readInt());
        fromTv.skipBytes(8);
        byte[] packed = new byte[6];
        fromTv.readFully(packed);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, packed);
        int[][] expected = {{0, 4, 4, 0}, {4, 4, 2, 0}, {8, 4, 0, -18}};
        for (int[] descriptor : expected) {
            assertArrayEquals(descriptor, new int[]{fromTv.readInt(), fromTv.readInt(),
                    fromTv.readInt(), fromTv.readInt()});
        }
    }

    @Test
    public void isochronousOutWaitsForAReserveThenFlowsThrough() throws Exception {
        imported();
        submitIso(20, 0, 2, new byte[]{1, 1}, new int[]{0}, new int[]{2});
        submitIso(21, 0, 2, new byte[]{2, 2}, new int[]{0}, new int[]{2});
        // Held in the jitter buffer: two of three, and less than the prefill time.
        assertEquals(null, device.queued.poll(5, TimeUnit.MILLISECONDS));
        submitIso(22, 0, 2, new byte[]{3, 3}, new int[]{0}, new int[]{2});
        for (int seqnum = 20; seqnum <= 22; seqnum++) {
            assertEquals(seqnum, device.queued.poll(5, TimeUnit.SECONDS).seqnum);
        }
        // The device has a reserve now: the next one goes straight through.
        submitIso(23, 0, 2, new byte[]{4, 4}, new int[]{0}, new int[]{2});
        assertEquals(23, device.queued.poll(5, TimeUnit.SECONDS).seqnum);
    }

    @Test
    public void isochronousOutTelemetryCountsADeviceThatRanDry() throws Exception {
        imported();
        submitIso(50, 0, 2, new byte[4], new int[]{0, 2}, new int[]{2, 2});
        submitIso(51, 0, 2, new byte[3], new int[]{0}, new int[]{3});
        submitIso(52, 0, 2, new byte[2], new int[]{0}, new int[]{2});
        // All three reach the device before any completes, so the reserve peaks at three.
        List<UsbIpExporter.Transfer> atDevice = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            atDevice.add(device.queued.poll(5, TimeUnit.SECONDS));
        }
        for (UsbIpExporter.Transfer transfer : atDevice) {
            transfer.isoActual = transfer.isoLengths.clone();
            transfer.isoStatus = new int[transfer.isoLengths.length];
            if (transfer.seqnum == 52) transfer.isoStatus[0] = -18;
            device.completed.add(transfer);
        }
        // RET_SUBMIT ahead of the device: header and one descriptor per packet (OUT carries
        // no data back).
        fromTv.readFully(new byte[48 + 2 * 16 + 2 * (48 + 16)]);
        // The last completion counted (the reaper keeps order): the device has nothing left.
        for (int wait = 0; wait < 500 && !Long.valueOf(1).equals(field("iso_out_packet_errors"));
                wait++) {
            Thread.sleep(10);
        }
        // The device ran dry: the next URB waits for a new reserve.
        submitIso(53, 0, 2, new byte[2], new int[]{0}, new int[]{2});
        assertEquals(53, device.queued.poll(5, TimeUnit.SECONDS).seqnum);
        List<Object> fields = Arrays.asList(exporter.isoOutFields());
        assertEquals(4L, fields.get(fields.indexOf("iso_out_urbs") + 1));
        assertEquals(5L, fields.get(fields.indexOf("iso_out_packets") + 1));
        assertEquals(11L, fields.get(fields.indexOf("iso_out_bytes") + 1));
        assertEquals(2, fields.get(fields.indexOf("iso_out_packet_min_bytes") + 1));
        assertEquals(3, fields.get(fields.indexOf("iso_out_packet_max_bytes") + 1));
        assertEquals(3, fields.get(fields.indexOf("iso_out_max_at_device") + 1));
        assertEquals(2L, fields.get(fields.indexOf("iso_out_prefills") + 1));
        assertEquals(1L, fields.get(fields.indexOf("iso_out_packet_errors") + 1));
        assertEquals(0L, fields.get(fields.indexOf("iso_out_waited_acks") + 1));
        assertEquals(UsbIpExporter.ISO_ACK_LEAD_PACKETS,
                fields.get(fields.indexOf("iso_out_lead_packets") + 1));
        // usb.attachment_ended keeps every one of them.
        for (int index = 0; index < fields.size(); index += 2) {
            assertTrue((String) fields.get(index),
                    MoonWakerDiagnostics.allowsField((String) fields.get(index)));
        }
    }

    @Test
    public void theLeadGrowsToAStallAtOnceAndShrinksSlowly() {
        int floor = UsbIpExporter.ISO_ACK_LEAD_PACKETS;
        UsbIpExporter.IsoLead lead = new UsbIpExporter.IsoLead(120);
        lead.arrived(10);
        assertEquals(floor, lead.packets);
        // A 90 ms stall: the TV queue covers the next one.
        lead.arrived(90);
        assertEquals(90, lead.packets);
        // Capped, and a stop of the stream is not jitter.
        lead.arrived(400);
        assertEquals(120, lead.packets);
        lead.arrived(UsbIpExporter.ISO_STREAM_RESTART_MS);
        assertEquals(120, lead.packets);
        // One packet per decay period of steady 10 ms arrivals.
        for (long ms = 0; ms < 3 * UsbIpExporter.ISO_LEAD_DECAY_MS; ms += 10) lead.arrived(10);
        assertEquals(117, lead.packets);
        for (int index = 0; index < 100_000; index++) lead.arrived(10);
        assertEquals(floor, lead.packets);
        assertEquals(120, lead.maxPackets);
        // A fixed lead (ceiling at the floor) never moves.
        UsbIpExporter.IsoLead fixed = new UsbIpExporter.IsoLead(floor);
        fixed.arrived(90);
        assertEquals(floor, fixed.packets);
    }

    /** An isochronous OUT URB of one lead of one-byte packets. */
    private void submitLead(int seqnum) throws IOException {
        int lead = UsbIpExporter.ISO_ACK_LEAD_PACKETS;
        int[] offsets = new int[lead];
        int[] lengths = new int[lead];
        Arrays.fill(lengths, 1);
        for (int index = 0; index < lead; index++) offsets[index] = index;
        submitIso(seqnum, 0, 2, new byte[lead], offsets, lengths);
    }

    private Object field(String name) {
        List<Object> fields = Arrays.asList(exporter.isoOutFields());
        return fields.get(fields.indexOf(name) + 1);
    }

    @Test
    public void isochronousOutIsAnsweredAheadOfTheDeviceUpToTheLead() throws Exception {
        imported();
        int lead = UsbIpExporter.ISO_ACK_LEAD_PACKETS;
        submitLead(60);
        // Answered before the device played it: the host may send the next one.
        assertArrayEquals(new int[]{3, 60, 0, lead}, answer(null));
        fromTv.skipBytes(lead * 16);
        submitIso(61, 0, 2, new byte[]{1, 1}, new int[]{0, 1}, new int[]{1, 1});
        // A lead of audio is ahead of it: it waits for the device's pace.
        UsbIpExporter.Transfer first = device.queued.poll(5, TimeUnit.SECONDS);
        assertEquals(60, first.seqnum);
        assertEquals(61, device.queued.poll(5, TimeUnit.SECONDS).seqnum);
        Thread.sleep(50);
        assertEquals(0, gateway.getInputStream().available());
        first.isoStatus = new int[lead];
        device.completed.add(first);
        assertArrayEquals(new int[]{3, 61, 0, 2}, answer(null));
        fromTv.skipBytes(2 * 16);
        assertEquals(1L, field("iso_out_waited_acks"));
    }

    @Test
    public void aLoneIsochronousOutLeavesTheBufferAfterThePrefillTime() throws Exception {
        imported();
        submitIso(30, 0, 2, new byte[]{1, 1}, new int[]{0}, new int[]{2});
        assertEquals(30, device.queued.poll(5, TimeUnit.SECONDS).seqnum);
    }

    @Test
    public void unlinkOfAnAnsweredIsochronousOutFindsNothingToCancel() throws Exception {
        imported();
        submitIso(42, 0, 2, new byte[]{1, 1}, new int[]{0}, new int[]{2});
        assertArrayEquals(new int[]{3, 42, 0, 2}, answer(null));
        fromTv.skipBytes(16);
        unlink(43, 42);
        assertArrayEquals(new int[]{4, 43, 0, 0}, answer(null));
        // Its audio still plays.
        assertEquals(42, device.queued.poll(5, TimeUnit.SECONDS).seqnum);
    }

    @Test
    public void unlinkOfAHeldIsochronousOutNeverReachesTheDevice() throws Exception {
        imported();
        int lead = UsbIpExporter.ISO_ACK_LEAD_PACKETS;
        submitLead(39);
        assertArrayEquals(new int[]{3, 39, 0, lead}, answer(null));
        fromTv.skipBytes(lead * 16);
        // Not answered yet: a lead of audio is ahead of it.
        submitIso(40, 0, 2, new byte[]{1, 1}, new int[]{0}, new int[]{2});
        unlink(41, 40);
        assertArrayEquals(new int[]{4, 41, UsbIpExporter.ECONNRESET, 0}, answer(null));
        // Unless the prefill time ran out first: then the device got it and it was cancelled.
        Thread.sleep(100);
        boolean queued = device.queued.stream().anyMatch(transfer -> transfer.seqnum == 40);
        assertTrue(!queued || device.calls.contains("cancel 40"));
    }

    @Test
    public void unlinkCancelsAPendingUrbAndAnswersOnlyTheUnlink() throws Exception {
        imported();
        submit(5, 1, 1, 64, new byte[8], new byte[0], 0);
        device.queued.poll(5, TimeUnit.SECONDS);
        unlink(6, 5);
        assertArrayEquals(new int[]{4, 6, UsbIpExporter.ECONNRESET, 0}, answer(null));
        assertTrue(device.calls.contains("cancel 5"));
        unlink(7, 99);
        assertArrayEquals(new int[]{4, 7, 0, 0}, answer(null));
    }

    @Test
    public void anUnpluggedDeviceEndsTheTunnel() throws Exception {
        imported();
        device.completed.add("gone");
        try {
            fromTv.readInt();
            fail("The tunnel stayed open.");
        } catch (EOFException expected) {
            // The exporter closed its side.
        } catch (IOException reset) {
            // Equally closed.
        }
    }

    @Test
    public void speedFollowsTheDescriptors() {
        assertEquals(2, UsbIpExporter.speed(concat(DEVICE, CONFIG)));
        byte[] bulk = concat(DEVICE, CONFIG);
        bulk[bulk.length - 4] = 2;
        bulk[bulk.length - 3] = 0;
        bulk[bulk.length - 2] = 2;
        assertEquals(3, UsbIpExporter.speed(bulk));
        byte[] superSpeed = concat(DEVICE, CONFIG);
        superSpeed[3] = 3;
        assertEquals(5, UsbIpExporter.speed(superSpeed));
    }
}
