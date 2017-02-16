#ifndef EASY_ENCODER_H
#define EASY_ENCODER_H

#ifdef __ANDROID__
#include <jni.h>
#endif

typedef struct easy_encoder_handle easy_encoder_handle;

char * AndCodec_EasyEncoderVersion();

// @param profile: available option: "low", "high", "smartquality", "nolatency"
//		can be NULL or empty string "", will use default "high"
// @param enc_str: some x264 tunable settings(separated by comma ',')
//		can be NUll or empty string ""
// enc_str option list:
//		1) quality(1-100)
//		2) gop_size(integer[15] or factor[2x] to fps)
// quick profile and enc_str settings:
//		1) "low", ""
//		2) "high", ""
//		3) "smartquality", "quality=35"
//		4) "", ""
// @return INVALID_HANDLE: error, return others: encoder handle
//		encoder handle use as Add() Get() Close() GetFPS() input param
long AndCodec_EasyEncoderOpen(int w, int h, int in_fmt, 
							 const char *profile, const char *enc_str);

// @param enc: encoder handle
// @param picdata: picture data to encode
// @param picdata_size: picture data size in byte
// @param opaque: opaque data
// @param opaque: opaque data len(should be 16 byte)
// @return <0: error
// @return =0: no encoded data out(opaque data will be discarded)
// @return >0: encoded data size
int AndCodec_EasyEncoderAdd(long enc, unsigned char *picdata, int picdata_size, 
							unsigned char *opaque, int opaque_len);

// @param dec: encoder handle
// @param encdata: receive encoded h264 data
// @param encdata_max_len: buffer size
// @param opaque: opaque data
// @param opaque_len: receive opaque data len(could be null)
// @return <0: error
// @return =0: no h264 data got
// @return >0: out h264 data size
int AndCodec_EasyEncoderGet(long enc, unsigned char *encdata, int encdata_max_len,
							unsigned char *opaque, int *opaque_len);

void AndCodec_EasyEncoderSetRotate(long enc, int rotate);

int AndCodec_EasyEncoderHeaders(long enc, unsigned char * headers, int *len);

void AndCodec_EasyEncoderClose(long enc);

double AndCodec_EasyEncoderGetFPS(long enc);

#ifdef __ANDROID__
//@return <0 error, return >0 handle
JNIEXPORT jboolean 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderOpen(JNIEnv* env, jobject thiz,
	int w, int h, int in_fmt, 
	jstring profile, jstring enc_str);

//@return <0 error, return >0 headers size
JNIEXPORT jint
Java_com_gotye_sdk_EasyEncoder_EasyEncoderHeaders(JNIEnv* env, jobject thiz, 
												jobject headers);

// valid value: 0, 90, 180, 270
JNIEXPORT void
Java_com_gotye_sdk_EasyEncoder_EasyEncoderSetRotate(JNIEnv* env, jobject thiz, 
												int rotate);

//@return <0 error, return 0 ok
JNIEXPORT jint 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderAdd(JNIEnv* env, jobject thiz,
	jobject picdata, jobject opaque);

//encdata alloc by user. it should be not less picdata_size. actual encdata size is returned by encdata_size.
//@return <0 error, return 0 no data, return >0 data size
JNIEXPORT jint 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderGet(JNIEnv* env, jobject thiz,
	jobject encdata, jobject opaque);

JNIEXPORT void 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderClose(JNIEnv* env, jobject thiz);

JNIEXPORT void 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderSetMuxer(JNIEnv* env, jobject thiz, 
	jlong muxer);

JNIEXPORT jdouble 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderGetFPS(JNIEnv* env, jobject thiz);
#endif

#endif //EASY_ENCODER_H

