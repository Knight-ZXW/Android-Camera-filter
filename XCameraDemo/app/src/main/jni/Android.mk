LOCAL_PATH := $(call my-dir)

BUILD_ENCODER		:= yes
BUILD_DECODER		:= yes
BUILD_MUXER			:= yes
BUILD_MEDIAINFO		:= yes
BUILD_AUDIOPLAYER	:= yes
BUILD_LIBRTMP		:= yes
BUILD_ARCSOFTWARE	:= yes
BUILD_FACEUNITY		:= yes

X264_LIB_PATH 	:= $(LOCAL_PATH)/x264/lib/$(TARGET_ARCH_ABI)
FDKAAC_LIB_PATH	:= $(LOCAL_PATH)/fdk-aac/lib/$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)
LOCAL_MODULE 	:= x264
LOCAL_SRC_FILES := x264/lib/$(TARGET_ARCH_ABI)/libx264.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE 	:= fdk-aac
LOCAL_SRC_FILES := fdk-aac/lib/$(TARGET_ARCH_ABI)/libfdk-aac.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE 	:= ffmpeg
LOCAL_SRC_FILES := ffmpeg/lib/$(TARGET_ARCH_ABI)/libffmpeg.a
include $(PREBUILT_STATIC_LIBRARY)

ifdef BUILD_LIBRTMP
include $(CLEAR_VARS)
LOCAL_MODULE 	:= rtmp
LOCAL_SRC_FILES := rtmpdump/lib/$(TARGET_ARCH_ABI)/librtmp.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE 	:= ssl
LOCAL_SRC_FILES := rtmpdump/lib/$(TARGET_ARCH_ABI)/libssl.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE 	:= crypto
LOCAL_SRC_FILES := rtmpdump/lib/$(TARGET_ARCH_ABI)/libcrypto.a
include $(PREBUILT_STATIC_LIBRARY)
endif

include $(CLEAR_VARS)
LOCAL_MODULE 	:= yuv
LOCAL_SRC_FILES := libyuv/lib/$(TARGET_ARCH_ABI)/libyuv.a
include $(PREBUILT_STATIC_LIBRARY)

ifdef BUILD_ARCSOFTWARE
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
include $(CLEAR_VARS)
LOCAL_MODULE 	:= ArcSoftSpotlight
LOCAL_SRC_FILES := facedetect/com.smile.gifmaker/libArcSoftSpotlight.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE 	:= expression-detect
LOCAL_SRC_FILES := facedetect/com.smile.gifmaker/libexpression-detect.so
include $(PREBUILT_SHARED_LIBRARY)
endif
endif

ifdef BUILD_FACEUNITY
include $(CLEAR_VARS)
LOCAL_MODULE 	:= nama
LOCAL_SRC_FILES := facedetect/faceunity/$(TARGET_ARCH_ABI)/libnama.so
include $(PREBUILT_SHARED_LIBRARY)
endif

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
endif

include $(CLEAR_VARS)
LOCAL_MODULE			:= andcodec
LOCAL_C_INCLUDES		:= $(LOCAL_PATH)/x264/include $(LOCAL_PATH)/ffmpeg/include $(LOCAL_PATH)/libyuv/include
LOCAL_SRC_FILES 		:= andjniload.cpp andsysutil.c andutility.c andstr.c andlog.c andtiming_c.c utils.c \
	andfifobuffer.c andqueue.c andtunables.c andparseconf.c pplog.cpp
ifdef BUILD_ENCODER
LOCAL_SRC_FILES 		+= easyencoder.c audioencoder.c
endif
ifdef BUILD_DECODER
LOCAL_SRC_FILES 		+= easydecoder.c
endif
ifdef BUILD_MUXER
LOCAL_SRC_FILES 		+= easymuxer.cpp apFFMuxer.cpp
endif
ifdef BUILD_MEDIAINFO
LOCAL_SRC_FILES 		+= apMediaInfoUtil.cpp easymediainfo.cpp
endif
ifdef BUILD_AUDIOPLAYER
LOCAL_SRC_FILES			+= easyaudioplayer.cpp apAudioPlayer.cpp oslesrender.cpp
endif
LOCAL_CFLAGS    		:= -Wall -D__STDC_CONSTANT_MACROS #fix ffmpeg compile error
LOCAL_CFLAGS			+= -DNO_DEBUG
LOCAL_STATIC_LIBRARIES 	= ffmpeg yuv
ifdef BUILD_ENCODER
LOCAL_STATIC_LIBRARIES 	+= x264 fdk-aac
endif
ifdef BUILD_LIBRTMP
LOCAL_STATIC_LIBRARIES 	+= rtmp ssl crypto
endif
LOCAL_LDLIBS 			:= -llog -lz
ifeq ($(TARGET_ARCH_ABI),x86)
LOCAL_LDLIBS 			+= -Wl,--no-warn-shared-textrel #fix shared library text segment is not shareable
endif
ifdef BUILD_ENCODER
LOCAL_LDLIBS 			+= -L$(X264_LIB_PATH) -L$(FDKAAC_LIB_PATH)
endif
ifdef BUILD_AUDIOPLAYER
LOCAL_LDLIBS			+= -lOpenSLES 
endif
ifdef BUILD_ARCSOFTWARE
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_SHARED_LIBRARIES	+= ArcSoftSpotlight expression-detect
endif
endif
ifdef BUILD_FACEUNITY
LOCAL_SHARED_LIBRARIES	+= nama 
endif
include $(BUILD_SHARED_LIBRARY)