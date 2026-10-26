# Every module directory: moonlight-core, moonwaker-usbfs (USB forwarding) and,
# for the root flavor, evdev_reader. A new directory needs a change here too, or Gradle keeps
# the module list of its cached ndk-build configuration and leaves the library out of the APK.
include $(call all-subdir-makefiles)
