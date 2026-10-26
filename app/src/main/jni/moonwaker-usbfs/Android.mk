# MoonWaker: asynchronous usbfs URBs (bulk, interrupt, isochronous) for USB forwarding.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := moonwaker-usbfs
LOCAL_SRC_FILES := moonwaker_usbfs.c
LOCAL_CFLAGS    := -Wall -Werror
include $(BUILD_SHARED_LIBRARY)
