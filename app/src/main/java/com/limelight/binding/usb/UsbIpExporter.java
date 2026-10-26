package com.limelight.binding.usb;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The server side of USB/IP 1.1.1 for one USB device of this TV, over one byte stream: the
 * Gateway tunnel, with no socket of its own. Control transfers run in order on one thread
 * (they are synchronous on Android); bulk, interrupt and isochronous transfers are queued
 * with the device and reaped on another. Isochronous OUT URBs (audio to a headset) wait in a
 * short jitter buffer before the device gets them, so a late network packet does not make
 * the device run dry. They are answered ahead of the device: the host keeps only a couple of
 * them in flight and sends the next on an answer, so answering on completion would cap the
 * stream at that window over the round trip (two 10 ms URBs per 30 ms: 67% of real time).
 * An OUT URB is answered as soon as less than the lead ({@link IsoLead}) of answered audio is
 * still ahead of it, so answers keep the device's pace and the host cannot run ahead. The lead
 * grows to the longest stall the host's URBs showed, so the TV queue covers the next one.
 */
public final class UsbIpExporter implements Closeable {
    static final int VERSION = 0x0111;
    static final int OP_REQ_IMPORT = 0x8003;
    static final int OP_REP_IMPORT = 0x0003;
    static final int CMD_SUBMIT = 1;
    static final int CMD_UNLINK = 2;
    static final int RET_SUBMIT = 3;
    static final int RET_UNLINK = 4;
    static final int DIRECTION_IN = 1;
    static final int MAX_TRANSFER_BYTES = 1 << 20;
    static final int MAX_ISO_PACKETS = 1024;
    static final int ECONNRESET = -104;
    static final int EPIPE = -32;
    static final int EINVAL = -22;
    // ponytail: prefill by URB count and time; lab knobs for Wi-Fi jitter against latency.
    // An endpoint with nothing at the device holds its OUT URBs until this many arrived or
    // this long passed, so the device starts (and restarts after a gap) with a reserve.
    static final int ISO_PREFILL_URBS = 3;
    static final long ISO_PREFILL_MS = 15;
    // ponytail: lead in packets, 1 ms each at full speed (a high-speed device's 125 us
    // microframes make it 8x shorter); lab knobs: host window x 10 ms + lead must cover the
    // round trip, and lead + host window bounds the audio queued on the TV. The lead starts
    // at the floor, grows at once to the longest gap between the host's URBs (a Wi-Fi stall
    // the TV queue must cover next time) up to the ceiling, and shrinks by one packet per
    // ISO_LEAD_DECAY_MS of audio without such a gap. A gap of ISO_STREAM_RESTART_MS or more
    // is the stream stopping, not jitter.
    static final int ISO_ACK_LEAD_PACKETS = 30;
    static final int ISO_ACK_LEAD_MAX_PACKETS = 120;
    static final long ISO_LEAD_DECAY_MS = 1_000;
    static final long ISO_STREAM_RESTART_MS = 1_000;
    static final int URB_ISO_ASAP = 0x0002;

    /** The one seam over {@code UsbDeviceConnection}; tests replace it. */
    public interface Device {
        /** The device descriptor followed by its configuration descriptors. */
        byte[] rawDescriptors();

        /** Bytes transferred, or a negative value when the device refused (stall). */
        int control(int requestType, int request, int value, int index, byte[] buffer, int length);

        boolean setConfiguration(int value);

        boolean setInterface(int interfaceNumber, int alternateSetting);

        /**
         * Queues a bulk, interrupt or isochronous transfer; it comes back from
         * {@link #awaitCompletion}. An isochronous IN transfer comes back with its packets at
         * {@code isoOffsets} of the buffer and their lengths in {@code isoActual}.
         */
        boolean queue(Transfer transfer);

        /** Blocks for the next completed transfer; null once the device is gone or closed. */
        Transfer awaitCompletion();

        void cancel(Transfer transfer);
    }

    /** The answer lead of an isochronous OUT stream, in packets; under {@code isoLock}. */
    static final class IsoLead {
        private final int ceiling;
        int packets = ISO_ACK_LEAD_PACKETS;
        int maxPackets = ISO_ACK_LEAD_PACKETS;
        private long decayMs;

        IsoLead(int ceiling) {
            this.ceiling = ceiling;
        }

        /** The host's next OUT URB arrived {@code gapMs} after its previous one. */
        void arrived(long gapMs) {
            if (gapMs >= ISO_STREAM_RESTART_MS) return;
            if (gapMs > packets) {
                packets = (int) Math.min(ceiling, gapMs);
                maxPackets = Math.max(maxPackets, packets);
                decayMs = 0;
                return;
            }
            if (packets == ISO_ACK_LEAD_PACKETS) return;
            decayMs += gapMs;
            for (; decayMs >= ISO_LEAD_DECAY_MS && packets > ISO_ACK_LEAD_PACKETS;
                 decayMs -= ISO_LEAD_DECAY_MS) {
                packets--;
            }
        }
    }

    /** One URB. The device fills {@code buffer} (IN) and sets {@code actualLength}. */
    public static final class Transfer {
        final int seqnum;
        final int endpoint;
        final boolean in;
        final byte[] setup;
        final ByteBuffer buffer;
        int actualLength;
        int status;
        volatile Object handle;
        boolean started;
        volatile int unlinkSeqnum;
        /** Isochronous only: what the host asked for and what the device did, per packet. */
        int[] isoOffsets;
        int[] isoLengths;
        int[] isoActual;
        int[] isoStatus;
        int startFrame;
        int errorCount;
        /** An isochronous OUT transfer the device has (counted in {@code isoAtDevice}). */
        boolean atDevice;
        /** An isochronous OUT transfer answered ahead of the device (in {@code isoAckedPackets}). */
        boolean acked;

        Transfer(int seqnum, int endpoint, boolean in, byte[] setup, ByteBuffer buffer) {
            this.seqnum = seqnum;
            this.endpoint = endpoint;
            this.in = in;
            this.setup = setup;
            this.buffer = buffer;
        }

        /** The endpoint address with its direction bit, as Android names endpoints. */
        int address() {
            return endpoint | (in ? 0x80 : 0);
        }
    }

    private final Device device;
    private final int busNumber;
    private final int deviceNumber;
    private final InputStream input;
    private final DataOutputStream output;
    private final Map<Integer, Transfer> pending = new HashMap<>();
    private final LinkedBlockingQueue<Transfer> controls = new LinkedBlockingQueue<>();
    /** The jitter buffer: held isochronous OUT transfers and how many each endpoint's device
     * has, under {@code isoLock}, which is taken before {@code this}, never inside it. */
    private final Object isoLock = new Object();
    private final Map<Integer, List<Transfer>> isoHeld = new HashMap<>();
    private final Map<Integer, Integer> isoAtDevice = new HashMap<>();
    /** Per endpoint, under {@code isoLock}: OUT transfers not answered yet, in arrival order,
     * and the packets of answered ones the device has not played yet. */
    private final Map<Integer, ArrayDeque<Transfer>> isoUnacked = new HashMap<>();
    private final Map<Integer, Integer> isoAckedPackets = new HashMap<>();
    private final IsoLead isoLead;
    private final long prefillMs;
    /** Isochronous OUT telemetry for the attachment's end event, under {@code isoLock}. */
    private long isoOutUrbs, isoOutPackets, isoOutBytes, isoOutAsap, isoOutPrefills;
    private long isoOutWaitedAcks, isoOutUnderruns;
    private long isoOutRefused, isoOutPacketErrors, isoOutLastArrival, isoOutGapMaxNanos;
    private int isoOutPacketMin = Integer.MAX_VALUE, isoOutPacketMax, isoOutUrbMaxPackets;
    private int isoOutMaxAtDevice;
    private final ScheduledExecutorService isoTimer = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "MoonWaker-usbip-iso");
                thread.setDaemon(true);
                thread.setPriority(Thread.MAX_PRIORITY);
                return thread;
            });
    private volatile boolean closed;
    /** Why the tunnel ended first (diagnostics); empty while it runs. */
    private volatile String endReason = "";
    private volatile String endError = "";

    public UsbIpExporter(Device device, int busNumber, int deviceNumber, InputStream input,
                         OutputStream output) {
        this(device, busNumber, deviceNumber, input, output, ISO_ACK_LEAD_MAX_PACKETS,
                ISO_PREFILL_MS);
    }

    /**
     * Tests: at a ceiling of {@link #ISO_ACK_LEAD_PACKETS} the lead stays put, and a long
     * prefill time keeps a slow machine's pauses from flushing a prefill early.
     */
    UsbIpExporter(Device device, int busNumber, int deviceNumber, InputStream input,
                  OutputStream output, int leadCeiling, long prefillMs) {
        isoLead = new IsoLead(leadCeiling);
        this.prefillMs = prefillMs;
        this.device = device;
        this.busNumber = busNumber;
        this.deviceNumber = deviceNumber;
        this.input = input;
        this.output = new DataOutputStream(new BufferedOutputStream(output, 64 * 1024));
    }

    public String busId() {
        return busNumber + "-" + deviceNumber;
    }

    /**
     * Serves the import and then every URB until the tunnel or the device ends;
     * {@code imported} runs once the host has imported the device.
     */
    public void run(Runnable imported) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input, 64 * 1024));
        importDevice(in);
        imported.run();
        Thread control = new Thread(() -> guarded(this::serveControls),
                "MoonWaker-usbip-control-" + busId());
        Thread reaper = new Thread(() -> guarded(this::reap), "MoonWaker-usbip-reaper-" + busId());
        control.setDaemon(true);
        reaper.setDaemon(true);
        // Audio keeps real time through these: the reaper and this reading thread.
        reaper.setPriority(Thread.MAX_PRIORITY);
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        control.start();
        reaper.start();
        try {
            while (!closed) readCommand(in);
        } catch (EOFException endOfTunnel) {
            // The Gateway or the TV ended the tunnel.
            end("tunnel_eof", null);
        } catch (IOException failed) {
            end(failed instanceof ProtocolException ? "protocol" : "tunnel_read_failed", failed);
            throw failed;
        } finally {
            close();
            control.interrupt();
            isoTimer.shutdownNow();
            synchronized (this) {
                for (Transfer transfer : pending.values()) {
                    if (transfer.endpoint != 0) device.cancel(transfer);
                }
                pending.clear();
            }
        }
    }

    private void importDevice(DataInputStream in) throws IOException {
        int version = in.readUnsignedShort();
        int code = in.readUnsignedShort();
        in.readInt();
        byte[] busId = new byte[32];
        in.readFully(busId);
        if (version != VERSION || code != OP_REQ_IMPORT) {
            throw new ProtocolException("Expected a USB/IP import request.");
        }
        boolean matches = busId().equals(cString(busId));
        synchronized (this) {
            output.writeShort(VERSION);
            output.writeShort(OP_REP_IMPORT);
            output.writeInt(matches ? 0 : 1);
            if (matches) output.write(deviceRecord());
            output.flush();
        }
        if (!matches) throw new ProtocolException("The import names another device.");
    }

    /** {@code usbip_usb_device}: path, bus ID, numbers, speed and the descriptor summary. */
    byte[] deviceRecord() {
        byte[] raw = device.rawDescriptors();
        if (raw == null || raw.length < 18 || raw[1] != 1) {
            throw new IllegalStateException("The device has no device descriptor.");
        }
        int configuration = raw.length >= 18 + 9 && raw[18 + 1] == 2 ? 18 : -1;
        ByteBuffer record = ByteBuffer.allocate(312);
        record.put(("/moonwaker/" + busId()).getBytes(StandardCharsets.US_ASCII));
        record.position(256);
        record.put(busId().getBytes(StandardCharsets.US_ASCII));
        record.position(288);
        record.putInt(busNumber).putInt(deviceNumber).putInt(speed(raw));
        record.putShort(littleShort(raw, 8)).putShort(littleShort(raw, 10))
                .putShort(littleShort(raw, 12));
        record.put(raw[4]).put(raw[5]).put(raw[6]);
        record.put(configuration < 0 ? 0 : raw[configuration + 5]);
        record.put(raw[17]);
        record.put(configuration < 0 ? 0 : raw[configuration + 4]);
        return record.array();
    }

    /**
     * USB/IP speed (2 full, 3 high, 5 super). Android does not report the link speed.
     * ponytail: read from the descriptors (bcdUSB 3.x, or a bulk endpoint of 512 bytes or an
     * interrupt endpoint above 64 bytes means high speed); a high-speed device with only
     * small endpoints is announced as full speed, which only coarsens its polling interval.
     */
    static int speed(byte[] raw) {
        if ((littleShort(raw, 2) & 0xffff) >= 0x0300) return 5;
        for (int index = 18; index + 7 <= raw.length && raw[index] >= 2; index += raw[index] & 0xff) {
            if (raw[index + 1] != 5) continue;
            int type = raw[index + 3] & 0x03;
            int size = littleShort(raw, index + 4) & 0x7ff;
            if ((type == 2 && size == 512) || (type == 3 && size > 64)) return 3;
        }
        return 2;
    }

    private static short littleShort(byte[] data, int offset) {
        return (short) ((data[offset] & 0xff) | (data[offset + 1] & 0xff) << 8);
    }

    private static String cString(byte[] value) {
        int end = 0;
        while (end < value.length && value[end] != 0) end++;
        return new String(value, 0, end, StandardCharsets.US_ASCII);
    }

    private void readCommand(DataInputStream in) throws IOException {
        int command = in.readInt();
        int seqnum = in.readInt();
        in.readInt();
        int direction = in.readInt();
        int endpoint = in.readInt();
        if (command == CMD_UNLINK) {
            int target = in.readInt();
            in.skipBytes(24);
            unlink(seqnum, target);
            return;
        }
        if (command != CMD_SUBMIT) throw new ProtocolException("Unknown USB/IP command.");
        int flags = in.readInt();
        int length = in.readInt();
        in.readInt();
        int packets = in.readInt();
        in.readInt();
        byte[] setup = new byte[8];
        in.readFully(setup);
        boolean directionIn = direction == DIRECTION_IN;
        if (length < 0 || length > MAX_TRANSFER_BYTES || endpoint < 0 || endpoint > 15) {
            throw new ProtocolException("Invalid USB/IP submit.");
        }
        byte[] data = new byte[length];
        if (!directionIn) in.readFully(data);
        Transfer transfer = new Transfer(seqnum, endpoint, directionIn, setup, ByteBuffer.wrap(data));
        if (packets != 0 && packets != -1) {
            if (packets < 0 || packets > MAX_ISO_PACKETS || endpoint == 0) {
                throw new ProtocolException("Invalid USB/IP isochronous submit.");
            }
            // iso_packet_descriptor: offset, length, actual_length, status.
            transfer.isoOffsets = new int[packets];
            transfer.isoLengths = new int[packets];
            for (int index = 0; index < packets; index++) {
                transfer.isoOffsets[index] = in.readInt();
                transfer.isoLengths[index] = in.readInt();
                in.readInt();
                in.readInt();
                if (transfer.isoOffsets[index] < 0 || transfer.isoLengths[index] < 0
                        || (long) transfer.isoOffsets[index] + transfer.isoLengths[index] > length) {
                    throw new ProtocolException("Invalid USB/IP isochronous packet.");
                }
            }
        }
        synchronized (this) {
            pending.put(seqnum, transfer);
        }
        if (endpoint == 0) {
            controls.add(transfer);
        } else if (transfer.isoLengths != null && !directionIn) {
            offerIsoOut(transfer, (flags & URB_ISO_ASAP) != 0);
        } else if (!device.queue(transfer)) {
            transfer.status = transfer.isoLengths != null ? EINVAL : EPIPE;
            complete(transfer);
        }
    }

    /** The jitter buffer in front of a device's isochronous OUT endpoint. */
    private void offerIsoOut(Transfer transfer, boolean asap) {
        int address = transfer.address();
        synchronized (isoLock) {
            boolean restart = countIsoOut(transfer, asap);
            isoUnacked.computeIfAbsent(address, key -> new ArrayDeque<>()).add(transfer);
            ackIsoOut(address);
            if (!transfer.acked) isoOutWaitedAcks++;
            List<Transfer> held = isoHeld.computeIfAbsent(address, key -> new ArrayList<>());
            if (isoAtDevice.getOrDefault(address, 0) > 0 && held.isEmpty()) {
                queueIsoOut(transfer);
                return;
            }
            held.add(transfer);
            // A prefill of an endpoint that played, with no stop between, means the device
            // ran dry: a gap the user hears.
            if (held.size() == 1) {
                isoOutPrefills++;
                if (!restart && isoAtDevice.containsKey(address)) isoOutUnderruns++;
            }
            if (held.size() >= ISO_PREFILL_URBS) {
                flushIsoOut(address);
            } else if (held.size() == 1 && !isoTimer.isShutdown()) {
                isoTimer.schedule(() -> {
                    synchronized (isoLock) {
                        // Only this prefill: one the count already flushed must not cut
                        // the reserve of the next one short.
                        if (isoHeld.get(address) == held) flushIsoOut(address);
                    }
                }, prefillMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Under {@code isoLock}: answers the endpoint's waiting OUT transfers, in order, while
     * less than the lead of answered audio is ahead of them. The answer says every packet
     * went out; a late device failure only counts in the telemetry.
     */
    private void ackIsoOut(int address) {
        ArrayDeque<Transfer> unacked = isoUnacked.get(address);
        while (unacked != null && !unacked.isEmpty()
                && isoAckedPackets.getOrDefault(address, 0) < isoLead.packets) {
            Transfer next = unacked.poll();
            boolean answered;
            try {
                synchronized (this) {
                    // An unlinked one is answered by its unlink instead.
                    answered = next.unlinkSeqnum == 0 && pending.remove(next.seqnum) != null;
                    if (answered) answer(next, true);
                }
            } catch (IOException tunnelFailed) {
                end("tunnel_write_failed", tunnelFailed);
                return;
            }
            if (!answered) continue;
            next.acked = true;
            isoAckedPackets.merge(address, next.isoLengths.length, Integer::sum);
        }
    }

    /** Under {@code isoLock}: an OUT transfer left the jitter buffer and the device. */
    private void isoOutDone(Transfer transfer) {
        int address = transfer.address();
        if (transfer.acked) {
            transfer.acked = false;
            isoAckedPackets.merge(address, -transfer.isoLengths.length, Integer::sum);
        } else {
            ArrayDeque<Transfer> unacked = isoUnacked.get(address);
            if (unacked != null) unacked.remove(transfer);
        }
        ackIsoOut(address);
    }

    /**
     * Under {@code isoLock}: what the host sends, packet sizes and arrival gaps, which steer
     * the lead. True for the stream's first URB and one after a stop.
     */
    private boolean countIsoOut(Transfer transfer, boolean asap) {
        long now = System.nanoTime();
        long gapMs = isoOutUrbs > 0 ? (now - isoOutLastArrival) / 1_000_000 : Long.MAX_VALUE;
        if (isoOutUrbs > 0) {
            isoOutGapMaxNanos = Math.max(isoOutGapMaxNanos, now - isoOutLastArrival);
            isoLead.arrived(gapMs);
        }
        isoOutLastArrival = now;
        isoOutUrbs++;
        if (asap) isoOutAsap++;
        isoOutPackets += transfer.isoLengths.length;
        isoOutUrbMaxPackets = Math.max(isoOutUrbMaxPackets, transfer.isoLengths.length);
        for (int length : transfer.isoLengths) {
            isoOutBytes += length;
            isoOutPacketMin = Math.min(isoOutPacketMin, length);
            isoOutPacketMax = Math.max(isoOutPacketMax, length);
        }
        return gapMs >= ISO_STREAM_RESTART_MS;
    }

    /**
     * Diagnostic fields of the isochronous OUT stream (audio to a headset): what the host
     * sent (bytes over the attachment's duration give the data rate, 192 per ms for 48 kHz
     * 16-bit stereo), the deepest device queue, how often the device ran dry, how many URBs
     * waited for the device's pace to be answered (the host would have run ahead) and the
     * lead at the end and at its largest.
     */
    public Object[] isoOutFields() {
        synchronized (isoLock) {
            if (isoOutUrbs == 0) return new Object[0];
            return new Object[]{"iso_out_urbs", isoOutUrbs, "iso_out_packets", isoOutPackets,
                    "iso_out_bytes", isoOutBytes, "iso_out_packet_min_bytes", isoOutPacketMin,
                    "iso_out_packet_max_bytes", isoOutPacketMax,
                    "iso_out_urb_max_packets", isoOutUrbMaxPackets, "iso_out_asap_urbs", isoOutAsap,
                    "iso_out_gap_max_ms", isoOutGapMaxNanos / 1_000_000,
                    "iso_out_max_at_device", isoOutMaxAtDevice, "iso_out_prefills", isoOutPrefills,
                    "iso_out_refused", isoOutRefused, "iso_out_packet_errors", isoOutPacketErrors,
                    "iso_out_waited_acks", isoOutWaitedAcks, "iso_out_underruns", isoOutUnderruns,
                    "iso_out_lead_packets", isoLead.packets,
                    "iso_out_lead_max_packets", isoLead.maxPackets};
        }
    }

    /** Under {@code isoLock}: hands every held transfer of the endpoint to the device. */
    private void flushIsoOut(int address) {
        List<Transfer> held = isoHeld.remove(address);
        if (held == null) return;
        for (Transfer transfer : held) queueIsoOut(transfer);
    }

    /** Under {@code isoLock}, in arrival order. */
    private void queueIsoOut(Transfer transfer) {
        if (transfer.unlinkSeqnum != 0) {
            complete(transfer);
            return;
        }
        if (device.queue(transfer)) {
            transfer.atDevice = true;
            isoOutMaxAtDevice = Math.max(isoOutMaxAtDevice,
                    isoAtDevice.merge(transfer.address(), 1, Integer::sum));
        } else {
            isoOutRefused++;
            transfer.status = EINVAL;
            complete(transfer);
        }
    }

    private void unlink(int seqnum, int target) throws IOException {
        Transfer transfer;
        synchronized (this) {
            transfer = pending.get(target);
            if (transfer == null) {
                // Already answered: the unlink found nothing to cancel.
                writeRetUnlink(seqnum, 0);
                return;
            }
            transfer.unlinkSeqnum = seqnum;
            if (transfer.endpoint == 0 && !transfer.started && controls.remove(transfer)) {
                pending.remove(target);
                writeRetUnlink(seqnum, ECONNRESET);
                return;
            }
        }
        if (transfer.endpoint == 0) return; // A running control transfer answers when it ends.
        boolean held;
        synchronized (isoLock) {
            List<Transfer> waiting = isoHeld.get(transfer.address());
            held = waiting != null && waiting.remove(transfer);
        }
        if (held) {
            complete(transfer);
        } else {
            device.cancel(transfer);
        }
    }

    /** A failure on a device thread ends this tunnel, never the app. */
    private void guarded(Runnable work) {
        try {
            work.run();
        } catch (Throwable unexpected) {
            end("thread_failed", unexpected);
        }
    }

    private void serveControls() {
        try {
            while (!closed) {
                Transfer transfer = controls.take();
                synchronized (this) {
                    if (!pending.containsKey(transfer.seqnum)) continue;
                    transfer.started = true;
                }
                runControl(transfer);
                complete(transfer);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private void runControl(Transfer transfer) {
        ByteBuffer setup = ByteBuffer.wrap(transfer.setup).order(ByteOrder.LITTLE_ENDIAN);
        int requestType = setup.get() & 0xff;
        int request = setup.get() & 0xff;
        int value = setup.getShort() & 0xffff;
        int index = setup.getShort() & 0xffff;
        int length = Math.min(setup.getShort() & 0xffff, transfer.buffer.capacity());
        boolean done;
        if (requestType == 0x00 && request == 0x09) {
            // SET_CONFIGURATION and SET_INTERFACE go through the API, as Linux usbip-host does,
            // so the kernel's interface state follows.
            done = device.setConfiguration(value);
        } else if (requestType == 0x01 && request == 0x0b) {
            done = device.setInterface(index, value);
        } else {
            int result = device.control(requestType, request, value, index,
                    transfer.buffer.array(), length);
            done = result >= 0;
            transfer.actualLength = Math.max(0, result);
        }
        transfer.status = done ? 0 : EPIPE;
    }

    private void reap() {
        while (!closed) {
            Transfer transfer = device.awaitCompletion();
            if (transfer == null) {
                // The device was unplugged or closed: end the tunnel.
                end("device_gone", null);
                return;
            }
            complete(transfer);
        }
    }

    private void complete(Transfer transfer) {
        if (transfer.isoLengths != null && !transfer.in) {
            synchronized (isoLock) {
                if (transfer.atDevice) {
                    transfer.atDevice = false;
                    // An unlinked URB (the stream stopping) is not a failed one.
                    for (int index = 0; transfer.unlinkSeqnum == 0 && transfer.isoStatus != null
                            && index < transfer.isoStatus.length; index++) {
                        if (transfer.isoStatus[index] != 0) isoOutPacketErrors++;
                    }
                    // At zero the endpoint's next URB starts a new prefill.
                    isoAtDevice.merge(transfer.address(), -1, Integer::sum);
                }
                isoOutDone(transfer);
            }
        }
        try {
            synchronized (this) {
                // Not pending any more: answered ahead of the device, or its unlink already
                // answered for it.
                if (pending.remove(transfer.seqnum) == null) return;
                if (transfer.unlinkSeqnum != 0) {
                    writeRetUnlink(transfer.unlinkSeqnum, ECONNRESET);
                } else {
                    answer(transfer, false);
                }
            }
        } catch (IOException tunnelFailed) {
            end("tunnel_write_failed", tunnelFailed);
        }
    }

    private void end(String reason, Throwable error) {
        synchronized (this) {
            if (endReason.isEmpty()) {
                endReason = closed ? "closed" : reason;
                endError = error == null ? "" : error.getClass().getName();
            }
        }
        closeQuietly();
    }

    /** Why this tunnel ended: tunnel_eof, device_gone, protocol, thread_failed, ... */
    public String endReason() {
        return endReason.isEmpty() ? "closed" : endReason;
    }

    /** The exception class behind {@link #endReason()}, if any. */
    public String endError() {
        return endError;
    }

    /**
     * RET_SUBMIT. An isochronous IN answer carries its packets packed one after the other at
     * their actual lengths, then one descriptor per packet, as Linux usbip-host sends it.
     * An isochronous OUT answer {@code ahead} of the device says every packet went out, and
     * reads nothing the reaper may be writing.
     */
    private void answer(Transfer transfer, boolean ahead) throws IOException {
        int packets = transfer.isoLengths == null ? 0 : transfer.isoLengths.length;
        int status = ahead ? 0 : transfer.status;
        boolean done = status == 0;
        int[] actuals = new int[packets];
        int actual;
        if (packets > 0) {
            actual = 0;
            for (int index = 0; done && (ahead || transfer.isoActual != null)
                    && index < packets; index++) {
                actuals[index] = ahead ? transfer.isoLengths[index] : Math.max(0,
                        Math.min(transfer.isoActual[index], transfer.isoLengths[index]));
                actual += actuals[index];
            }
        } else {
            actual = done ? Math.min(transfer.actualLength, transfer.buffer.capacity()) : 0;
        }
        output.writeInt(RET_SUBMIT);
        output.writeInt(transfer.seqnum);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(status);
        output.writeInt(actual);
        output.writeInt(ahead ? 0 : transfer.startFrame);
        output.writeInt(packets);
        output.writeInt(ahead ? 0 : transfer.errorCount);
        output.write(new byte[8]);
        if (transfer.in && done) {
            if (packets == 0) {
                output.write(transfer.buffer.array(), 0, actual);
            } else {
                for (int index = 0; index < packets; index++) {
                    output.write(transfer.buffer.array(), transfer.isoOffsets[index], actuals[index]);
                }
            }
        }
        for (int index = 0; index < packets; index++) {
            output.writeInt(transfer.isoOffsets[index]);
            output.writeInt(transfer.isoLengths[index]);
            output.writeInt(actuals[index]);
            output.writeInt(ahead ? 0 : done && transfer.isoStatus != null
                    ? transfer.isoStatus[index] : status);
        }
        output.flush();
    }

    private void writeRetUnlink(int seqnum, int status) throws IOException {
        output.writeInt(RET_UNLINK);
        output.writeInt(seqnum);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(status);
        output.write(new byte[24]);
        output.flush();
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // Closing a broken tunnel.
        }
    }

    /** Ends the tunnel side; the owner closes the device connection, which ends reaping. */
    @Override
    public void close() throws IOException {
        closed = true;
        input.close();
    }
}
