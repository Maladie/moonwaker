// Asynchronous usbfs URBs on the descriptor of an Android UsbDeviceConnection
// (com.limelight.binding.usb.Usbfs). The public Java API has no isochronous transfers;
// usbfs has them on the same descriptor without root, as libusb uses it.
//
// Every URB this file submits is reaped by Usbfs.reap and freed by Usbfs.finish. Nothing
// else may reap on the same descriptor (UsbRequest.requestWait would take these URBs), so
// the caller sends all bulk, interrupt and isochronous transfers through here.
//
// A URB handle is its address zero-extended (uintptr_t) into the jlong, so it is always
// positive and negative values stay errnos: on a 32-bit process a heap address above 2 GiB
// sign-extended through intptr_t would read as an error.

#include <errno.h>
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <poll.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>

JNIEXPORT jlong JNICALL
Java_com_limelight_binding_usb_Usbfs_nativeSubmit(JNIEnv *env, jclass clazz, jint fd,
                                                  jint type, jint endpoint, jbyteArray data,
                                                  jint length, jintArray isoLengths) {
    (void) clazz;
    jsize packets = isoLengths != NULL ? (*env)->GetArrayLength(env, isoLengths) : 0;
    struct usbdevfs_urb *urb = calloc(1, sizeof(struct usbdevfs_urb) +
            (size_t) packets * sizeof(struct usbdevfs_iso_packet_desc));
    void *buffer = length > 0 ? malloc((size_t) length) : NULL;
    if (urb == NULL || (length > 0 && buffer == NULL)) {
        free(buffer);
        free(urb);
        return -ENOMEM;
    }
    urb->type = (unsigned char) type;
    urb->endpoint = (unsigned char) endpoint;
    urb->buffer = buffer;
    urb->buffer_length = length;
    urb->usercontext = urb;
    if ((endpoint & 0x80) == 0 && length > 0) {
        (*env)->GetByteArrayRegion(env, data, 0, length, buffer);
    }
    if (type == USBDEVFS_URB_TYPE_ISO) {
        // As soon as possible after what is queued: a gap is a glitch, not an error.
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->number_of_packets = packets;
        jint *lengths = (*env)->GetIntArrayElements(env, isoLengths, NULL);
        for (jsize index = 0; index < packets; index++) {
            urb->iso_frame_desc[index].length = (unsigned int) lengths[index];
        }
        (*env)->ReleaseIntArrayElements(env, isoLengths, lengths, JNI_ABORT);
    }
    if (ioctl(fd, USBDEVFS_SUBMITURB, urb) < 0) {
        int error = errno;
        free(buffer);
        free(urb);
        return -error;
    }
    return (jlong) (uintptr_t) urb;
}

// A completed URB, 0 when none completed within the timeout, or -errno once the device
// is gone and nothing is left to reap. A timeout instead of a blocking reap lets the
// caller stop: a reap blocked in the kernel does not return when another thread closes
// the descriptor.
JNIEXPORT jlong JNICALL
Java_com_limelight_binding_usb_Usbfs_nativeReap(JNIEnv *env, jclass clazz, jint fd,
                                                jint timeoutMs) {
    (void) env;
    (void) clazz;
    struct pollfd ready = {.fd = fd, .events = POLLOUT};
    int polled = poll(&ready, 1, timeoutMs);
    if (polled < 0) return errno == EINTR ? 0 : -errno;
    struct usbdevfs_urb *urb = NULL;
    if (ioctl(fd, USBDEVFS_REAPURBNDELAY, &urb) == 0) return (jlong) (uintptr_t) urb;
    if (errno == EAGAIN) {
        return (ready.revents & (POLLERR | POLLHUP | POLLNVAL)) != 0 ? -ENODEV : 0;
    }
    return -errno;
}

// Copies what the URB returned and frees it. meta: actual length, start frame, error
// count. An isochronous IN buffer holds its packets one after the other at their
// requested lengths, as usbfs lays them out.
JNIEXPORT jint JNICALL
Java_com_limelight_binding_usb_Usbfs_nativeFinish(JNIEnv *env, jclass clazz, jlong handle,
                                                  jbyteArray data, jintArray isoActual,
                                                  jintArray isoStatus, jintArray meta) {
    (void) clazz;
    struct usbdevfs_urb *urb = (struct usbdevfs_urb *) (uintptr_t) handle;
    int status = urb->status;
    if ((urb->endpoint & 0x80) != 0 && urb->buffer_length > 0) {
        jsize copied = urb->type == USBDEVFS_URB_TYPE_ISO ? urb->buffer_length
                : (urb->actual_length < urb->buffer_length ? urb->actual_length
                        : urb->buffer_length);
        if (copied > 0) (*env)->SetByteArrayRegion(env, data, 0, copied, urb->buffer);
    }
    if (urb->type == USBDEVFS_URB_TYPE_ISO) {
        jint *actual = (*env)->GetIntArrayElements(env, isoActual, NULL);
        jint *packetStatus = (*env)->GetIntArrayElements(env, isoStatus, NULL);
        for (int index = 0; index < urb->number_of_packets; index++) {
            actual[index] = (jint) urb->iso_frame_desc[index].actual_length;
            packetStatus[index] = (jint) urb->iso_frame_desc[index].status;
        }
        (*env)->ReleaseIntArrayElements(env, isoStatus, packetStatus, 0);
        (*env)->ReleaseIntArrayElements(env, isoActual, actual, 0);
    }
    jint results[3] = {urb->actual_length, urb->start_frame, urb->error_count};
    (*env)->SetIntArrayRegion(env, meta, 0, 3, results);
    free(urb->buffer);
    free(urb);
    return status;
}

JNIEXPORT jint JNICALL
Java_com_limelight_binding_usb_Usbfs_nativeDiscard(JNIEnv *env, jclass clazz, jint fd,
                                                   jlong handle) {
    (void) env;
    (void) clazz;
    return ioctl(fd, USBDEVFS_DISCARDURB, (void *) (uintptr_t) handle) < 0 ? -errno : 0;
}
