#ifndef EASY_DECODER_H
#define EASY_DECODER_H

#include <jni.h>

typedef struct easy_decoder_handle easy_decoder_handle;

char * AndCodec_EasyDecoderVersion();

// @return INVALID_HANDLE: error, return others: decoder handle
// decoder handle use as Add() Get() Close() GetFPS() input param
long AndCodec_EasyDecoderOpen(int w, int h, int out_fmt);

// @param dec: decoder handle
// @param decdata: h264 data to encode
// @param decdata_size: h264 data size in byte
// @param opaque: opaque data
// @param opaque: opaque data len(should be 16 byte)
// @return <0: error
// @return =0: no picture decoded out(opaque data will be always added to list, cached picture will available later)
// @return >0: new picture decoded out
int AndCodec_EasyDecoderAdd(long dec, unsigned char* decdata, int decdata_size, 
							unsigned char* opaque, int opaque_len);

// @param dec: decoder handle
// @param picdata: receive decoded picture data
// @param opaque: opaque data
// @param opaque: opaque data len(should be 16 byte)
// @return <0: error
// @return 0: no picture got
// @return >0: out picture size
int AndCodec_EasyDecoderGet(long dec, unsigned char* picdata, 
							unsigned char* opaque, int *opaque_len);

int AndCodec_EasyDecoderFlush(long dec);

void AndCodec_EasyDecoderClose(long dec);

double AndCodec_EasyDecoderGetFPS(long dec);

//@return <0 error
//@return >0 handle
JNIEXPORT jboolean 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderOpen(JNIEnv* env, jobject thiz, 
	jint w, jint h, jint out_fmt);

JNIEXPORT jint 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderAdd(JNIEnv* env, jobject thiz, 
	jobject decdata, jint start, jint byteCount, jobject opaque);

//picdata alloc by user. Actual picdata size is returned by picdata_size.
//@return <0 error
//@return 0 no picture
//@return >0 pic size	
JNIEXPORT jint 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderGet(JNIEnv* env, jobject thiz, 
	jobject picdata, jobject opaque);

JNIEXPORT void 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderClose(JNIEnv* env, jobject thiz);

JNIEXPORT double 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderGetFPS(JNIEnv* env, jobject thiz);

#endif //EASY_DECODER_H

