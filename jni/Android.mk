LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := spokestack
LOCAL_SRC_FILES := \
	agc.cpp \
	vad.cpp \
	filter_audio/other/complex_bit_reverse.c \
	filter_audio/other/complex_fft.c \
	filter_audio/other/copy_set_operations.c \
	filter_audio/other/cross_correlation.c \
	filter_audio/other/division_operations.c \
	filter_audio/other/dot_product_with_scale.c \
	filter_audio/other/downsample_fast.c \
	filter_audio/other/energy.c \
	filter_audio/other/get_scaling_square.c \
	filter_audio/other/min_max_operations.c \
	filter_audio/other/real_fft.c \
	filter_audio/other/resample_by_2.c \
	filter_audio/other/resample_by_2_internal.c \
	filter_audio/other/resample_fractional.c \
	filter_audio/other/resample_48khz.c \
	filter_audio/other/spl_init.c \
	filter_audio/other/spl_sqrt.c \
	filter_audio/other/vector_scaling_operations.c \
	filter_audio/vad/vad_core.c \
	filter_audio/vad/vad_filterbank.c \
	filter_audio/vad/vad_gmm.c \
	filter_audio/vad/vad_sp.c \
	filter_audio/vad/webrtc_vad.c \
	filter_audio/agc/analog_agc.c \
	filter_audio/agc/digital_agc.c

include $(BUILD_SHARED_LIBRARY)

# build the library on the host platform
$(info $(shell cd jni && make -f Dev.mk))
