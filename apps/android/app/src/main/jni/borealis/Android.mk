# Borealis-owned ndk-build module (picked up by the upstream jni/Android.mk through
# all-subdir-makefiles). It consumes borealis-core through the Prefab package published by the
# :borealis:native Gradle module.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := borealis_bridge
LOCAL_SRC_FILES := borealis_bridge.c
LOCAL_CFLAGS := -Wall -Wextra
LOCAL_SHARED_LIBRARIES := borealis_core
include $(BUILD_SHARED_LIBRARY)

$(call import-module,prefab/native)
