LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)  
LOCAL_LDLIBS := -L$(LOCAL_PATH)/$(TARGET_ARCH_ABI) -llog -lminecraftpe
LOCAL_MODULE    := mcpelauncher

#corkscrew
corkscrew_generic_src_files := \
	libcorkscrew/backtrace.c \
	libcorkscrew/backtrace-helper.c \
	libcorkscrew/demangle.c \
	libcorkscrew/map_info.c \
	libcorkscrew/ptrace.c \
	libcorkscrew/symbol_table.c

corkscrew_arm_src_files := \
	libcorkscrew/arch-arm/backtrace-arm.c \
	libcorkscrew/arch-arm/ptrace-arm.c

corkscrew_x86_src_files := \
	libcorkscrew/arch-x86/backtrace-x86.c \
	libcorkscrew/arch-x86/ptrace-x86.c

LOCAL_SRC_FILES := nativepatch.c modscript.c modscript_nextgen.cpp modscript_ScriptLevelListener.cpp utf8proc_slim.c dobby.cpp marauders_map.c \
	modscript_renderer.cpp simpleuuid.c signalhandler.cpp modscript_cape.cpp controller_jni.cpp $(corkscrew_generic_src_files)

ifneq (,$(wildcard $(LOCAL_PATH)/scriptscramble.c))
    LOCAL_SRC_FILES += scriptscramble.c
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES += signalhandler_arm.cpp tamperpanic.s
else
    LOCAL_SRC_FILES += signalhandler_x86.cpp
endif

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_SRC_FILES += $(corkscrew_arm_src_files)
LOCAL_CFLAGS += -DCORKSCREW_HAVE_ARCH
endif

ifeq ($(TARGET_ARCH_ABI),x86)
LOCAL_SRC_FILES += $(corkscrew_x86_src_files)
LOCAL_CFLAGS += -DCORKSCREW_HAVE_ARCH
endif


LOCAL_C_INCLUDES += $(LOCAL_PATH)/libcorkscrew

LOCAL_SHARED_LIBRARIES := tinysubstrate-bin

include $(BUILD_SHARED_LIBRARY)

$(call import-add-path, prebuilts)

$(call import-module, tinysubstrate-bin)
