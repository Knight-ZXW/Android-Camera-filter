#ifndef ANDFFENCODER_H
#define ANDFFENCODER_H

#ifdef __ANDROID__
#include <jni.h>
#endif



typedef struct ff_encoder_handle ff_encoder_handle;

int AndCodec_EasyffEncoderOpen(int w, int h, int in_fmt, 
							 const char *profile, const char *enc_str);

int AndCodec_EasyffEncoderAdd(int ffenc, unsigned char *picdata, int picdata_size,
							  unsigned char* audiodata, int audiodata_size,
							unsigned char *opaque, int opaque_len);

int AndCodec_EasyffEncoderGet(int ffenc, unsigned char *encdata, 
							unsigned char *opaque, int *opaque_len);

int AndCodec_EasyffEncoderGetAudioFrameSize(int ffenc);

void AndCodec_EasyffEncoderClose(int ffenc);

double AndCodec_EasyffEncoderGetFPS(int ffenc);

int AndCodec_EasyffEncoderTest();

#ifdef __ANDROID__
JNIEXPORT void 
Java_tv_xormedia_testffencoder_CodecLib_EasyffEncoderTest();

//@return <0 error
//@return >0 handle
JNIEXPORT int 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderOpen(JNIEnv* env, jobject thiz,
												  int w, int h, int in_fmt, 
												  jstring profile, jstring enc_str);

//@return <0 error
//@return 0 ok
JNIEXPORT int 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderAdd(JNIEnv* env, jobject thiz,
												 int ffenc, jobject picdata, int picdata_size,
												 jobject audiodata, int audiodata_size,
												 jobject opaque);

//encdata alloc by user. it should be not less picdata_size. actual encdata size is returned by encdata_size.
//@return <0 error
//@return 0 no data
//@return >0 data size
JNIEXPORT int 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderGet(JNIEnv* env, jobject thiz,
												 int ffenc, jobject encdata, jobject opaque);

JNIEXPORT void 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderClose(JNIEnv* env, jobject thiz, int ffenc);

JNIEXPORT double
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderGetFPS(JNIEnv* env, jobject thiz, int ffenc);
#endif

#endif //ANDFFENCODER_H



