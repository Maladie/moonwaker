package com.limelight.binding.usb;

import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link UsbIpExporter.Device} on an Android {@link UsbDeviceConnection}. Control transfers
 * use the public API; bulk, interrupt and isochronous transfers are usbfs URBs on the same
 * descriptor ({@link UsbfsTransfers}), since the public API has no isochronous transfers and
 * its own reaping would take those URBs. Every interface is claimed with {@code force}, so
 * Android's own driver (usbhid, snd-usb-audio) lets go while the device is forwarded and
 * gets it back on close.
 */
final class AndroidUsbIpDevice implements UsbIpExporter.Device, Closeable {
    private static final int CONTROL_TIMEOUT_MS = 5_000;

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final UsbfsTransfers transfers;
    /** usbfs URB type per endpoint address, over every alternate setting. */
    private final Map<Integer, Integer> endpointTypes = new HashMap<>();
    private UsbConfiguration configuration;

    private AndroidUsbIpDevice(UsbDevice device, UsbDeviceConnection connection) {
        this.device = device;
        this.connection = connection;
        transfers = new UsbfsTransfers(new Usbfs(), connection.getFileDescriptor(), this::urbType);
    }

    /** Opens and claims the device; the caller already holds the user's permission. */
    static AndroidUsbIpDevice open(UsbManager manager, UsbDevice device) throws IOException {
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) throw new IOException("The USB device could not be opened.");
        AndroidUsbIpDevice opened = new AndroidUsbIpDevice(device, connection);
        if (device.getConfigurationCount() == 0 || !opened.claim(device.getConfiguration(0))) {
            opened.close();
            throw new IOException("The USB device could not be claimed.");
        }
        return opened;
    }

    private synchronized boolean claim(UsbConfiguration next) {
        endpointTypes.clear();
        for (int index = 0; index < next.getInterfaceCount(); index++) {
            UsbInterface usbInterface = next.getInterface(index);
            if (usbInterface.getAlternateSetting() == 0
                    && !connection.claimInterface(usbInterface, true)) {
                return false;
            }
            for (int point = 0; point < usbInterface.getEndpointCount(); point++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(point);
                endpointTypes.put(endpoint.getAddress(), urbTypeOf(endpoint.getType()));
            }
        }
        configuration = next;
        return true;
    }

    private static int urbTypeOf(int endpointType) {
        switch (endpointType) {
            case UsbConstants.USB_ENDPOINT_XFER_ISOC: return UsbfsTransfers.URB_ISO;
            case UsbConstants.USB_ENDPOINT_XFER_INT: return UsbfsTransfers.URB_INTERRUPT;
            case UsbConstants.USB_ENDPOINT_XFER_BULK: return UsbfsTransfers.URB_BULK;
            default: return -1;
        }
    }

    private synchronized int urbType(int address) {
        Integer type = endpointTypes.get(address);
        return type == null ? -1 : type;
    }

    /**
     * Android reconnects the kernel driver of each interface as it is released. The audio
     * driver binds to the AudioControl interface (first) and claims the streaming ones after
     * it then: released before them, it found them still claimed and the TV's USB sound card
     * came back without playback or capture. So the interfaces go back last to first.
     */
    private void release() {
        if (configuration == null) return;
        for (int index = configuration.getInterfaceCount() - 1; index >= 0; index--) {
            UsbInterface usbInterface = configuration.getInterface(index);
            if (usbInterface.getAlternateSetting() == 0) connection.releaseInterface(usbInterface);
        }
        configuration = null;
    }

    @Override
    public byte[] rawDescriptors() {
        return connection.getRawDescriptors();
    }

    @Override
    public int control(int requestType, int request, int value, int index, byte[] buffer,
                       int length) {
        return connection.controlTransfer(requestType, request, value, index, buffer, length,
                CONTROL_TIMEOUT_MS);
    }

    @Override
    public synchronized boolean setConfiguration(int value) {
        if (value == 0 || configuration != null && configuration.getId() == value) return true;
        for (int index = 0; index < device.getConfigurationCount(); index++) {
            UsbConfiguration next = device.getConfiguration(index);
            if (next.getId() != value) continue;
            release();
            return connection.setConfiguration(next) && claim(next);
        }
        return false;
    }

    /** USBDEVFS_SETINTERFACE: an audio interface starts streaming on its alternate setting. */
    @Override
    public synchronized boolean setInterface(int interfaceNumber, int alternateSetting) {
        if (configuration == null) return false;
        for (int index = 0; index < configuration.getInterfaceCount(); index++) {
            UsbInterface usbInterface = configuration.getInterface(index);
            if (usbInterface.getId() == interfaceNumber
                    && usbInterface.getAlternateSetting() == alternateSetting) {
                return connection.setInterface(usbInterface);
            }
        }
        return false;
    }

    @Override
    public boolean queue(UsbIpExporter.Transfer transfer) {
        return transfers.queue(transfer);
    }

    @Override
    public UsbIpExporter.Transfer awaitCompletion() {
        return transfers.awaitCompletion();
    }

    @Override
    public void cancel(UsbIpExporter.Transfer transfer) {
        transfers.cancel(transfer);
    }

    @Override
    public synchronized void close() {
        transfers.close();
        release();
        connection.close();
    }
}
