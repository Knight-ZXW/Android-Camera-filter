#include "audioencoder.h"
#include "anddefs.h"
#define LOG_TAG "audioencoder"
#include "pplog.h"

#ifdef _STDINT_H
#undef _STDINT_H
#include <stdint.h>
#endif
#include "libavutil/avutil.h"
#include "libavcodec/avcodec.h"

struct audioencoder_handle
{
	AVCodec *codec;
    AVCodecContext *c;
    AVFrame *frame;
	int buffer_size;
	uint8_t *samples;
	int samples_offset;
	AVBitStreamFilterContext*	bsfc_aac;
};

/* check that a given sample format is supported by the encoder */
static int check_sample_fmt(AVCodec *codec, enum AVSampleFormat sample_fmt)
{
    const enum AVSampleFormat *p = codec->sample_fmts;

    while (*p != AV_SAMPLE_FMT_NONE) {
        if (*p == sample_fmt)
            return 1;
        p++;
    }
    return 0;
}

long open_audio_encoder(int sample_rate, int channels, int bitrate, int add_adts_header)
{
	/* register all the codecs */
    avcodec_register_all();

	audioencoder_handle *handle = (audioencoder_handle *)malloc(sizeof(audioencoder_handle));
	memset(handle, 0, sizeof(audioencoder_handle));

	/* find the MP2 encoder */
    handle->codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
    if (!handle->codec) {
        PPLOGE("Codec AAC not found");
        goto ERROR;
    }

    handle->c = avcodec_alloc_context3(handle->codec);
    if (!handle->c) {
        PPLOGE("Could not allocate audio codec context");
        goto ERROR;
    }

    /* put sample parameters */
    handle->c->bit_rate = bitrate;// 64000;

    /* check that the encoder supports s16 pcm input */
    handle->c->sample_fmt = AV_SAMPLE_FMT_S16;
    if (!check_sample_fmt(handle->codec, handle->c->sample_fmt)) {
        PPLOGE("Encoder does not support sample format %s",
                av_get_sample_fmt_name(handle->c->sample_fmt));
        goto ERROR;
    }

    /* select other audio parameters supported by the encoder */
    handle->c->sample_rate    = sample_rate;
	handle->c->channel_layout = (channels == 1 ? AV_CH_LAYOUT_MONO : AV_CH_LAYOUT_STEREO);
	handle->c->channels       = channels;

    /* open it */
    if (avcodec_open2(handle->c, handle->codec, NULL) < 0) {
        PPLOGE("Could not open codec");
        goto ERROR;
    }

	/* frame containing input raw audio */
    handle->frame = av_frame_alloc();
    if (!handle->frame) {
        PPLOGE("Could not allocate audio frame");
        goto ERROR;
    }

    handle->frame->nb_samples     = handle->c->frame_size;
    handle->frame->format         = handle->c->sample_fmt;
    handle->frame->channel_layout = handle->c->channel_layout;

    /* the codec gives us the frame size, in samples,
     * we calculate the size of the samples buffer in bytes */
    handle->buffer_size = av_samples_get_buffer_size(NULL, handle->c->channels, handle->c->frame_size,
                                             handle->c->sample_fmt, 0);
    if (handle->buffer_size < 0) {
        PPLOGE("Could not get sample buffer size");
        goto ERROR;
    }
	PPLOGI("buffer_size %d, frame_size %d", handle->buffer_size, handle->c->frame_size);
	handle->samples = (uint8_t*)av_malloc(handle->buffer_size);
    if (!handle->samples) {
        PPLOGE("Could not allocate %d bytes for samples buffer", handle->buffer_size);
        goto ERROR;
    }
	handle->samples_offset = 0;

	if (!add_adts_header) {
		handle->bsfc_aac =  av_bitstream_filter_init("aac_adtstoasc");
		if (!handle->bsfc_aac) {
			PPLOGE("Could not aquire aac_adtstoasc filter");
			goto ERROR;
		}
	}

	if (handle->c->extradata_size > 0) {
		PPLOGI("extradata size %d, %02x %02x", handle->c->extradata_size,
			handle->c->extradata[0], handle->c->extradata[1]);
	}

	return (long)handle;

ERROR:
	if (handle) {
		if (handle->frame)
			av_frame_free(&handle->frame);
		if (handle->c) {
			avcodec_close(handle->c);
			av_free(handle->c);
		}

		free(handle);
		handle = NULL;
	}
	return INVALID_HANDLE;
}

int get_buffer_size(long handle)
{
	audioencoder_handle *h = (audioencoder_handle *)handle;
	return h->buffer_size;
}

int encode_audio(long handle, const char *data, int len, char *output, int out_max_len)
{
	//PPLOGD("encode_audio handle %d, data %p, size %d", handle, data, len);

	audioencoder_handle *h = (audioencoder_handle *)handle;

	int left = len;
	int offset = 0;
	int output_offset = 0;

	while (left >= h->buffer_size) {
		int write = h->buffer_size;
		if (h->samples_offset > 0)
			write -= h->samples_offset;
		memcpy(h->samples + h->samples_offset, data + offset, write);
		// reset samples_offset after 1 copy action
		h->samples_offset = 0;
		/* setup the data pointers in the AVFrame */
		int ret = avcodec_fill_audio_frame(h->frame, h->c->channels, h->c->sample_fmt,
									   (const uint8_t*)h->samples, h->buffer_size, 0);
		if (ret < 0) {
			PPLOGE("Could not setup audio frame");
			return -1;
		}

		AVPacket pkt;
		av_init_packet(&pkt);
		pkt.data = NULL; // packet data will be allocated by the encoder
		pkt.size = 0;

		/* encode the samples */
		int got_output;
		ret = avcodec_encode_audio2(h->c, &pkt, h->frame, &got_output);
		if (ret < 0) {
			PPLOGE("Error encoding audio frame");
			return -1;
		}
		if (got_output) {
			// remove 7 adts header
			if (h->bsfc_aac) {
				// will remove ADTS header 7 bytes, pkt.data += 7, pkt.size -= 7
				if (av_bitstream_filter_filter(h->bsfc_aac, h->c, NULL, 
					&pkt.data, &pkt.size, 
					pkt.data, pkt.size, 0) < 0)
				{
					PPLOGE("failed to aac_adtstoasc_filter()");
					return -1;
				}
			}
			if (output_offset + pkt.size >= out_max_len) {
				PPLOGE("output buf overflow(%d %d)", output_offset + pkt.size, out_max_len);
				return -1;
			}
			memcpy(output + output_offset, pkt.data, pkt.size);
			output_offset += pkt.size;
			av_free_packet(&pkt);
		}

		offset += write;
		left -= write;
	}

	if (left > 0) {
		memcpy(h->samples, data + offset, left);
		h->samples_offset = left;
	}

	return output_offset;
}

void close_audio_encoder(long handle)
{
	if (handle != INVALID_HANDLE) {
		audioencoder_handle *h = (audioencoder_handle *)handle;
		if (h->frame)
			av_frame_free(&h->frame);
		if (h->c) {
			avcodec_close(h->c);
			av_free(h->c);
		}
		if (h->bsfc_aac) {
			av_bitstream_filter_close(h->bsfc_aac);
			h->bsfc_aac = NULL;
		}
		free(h);
	}
}

/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
#include <pthread.h>
/* Header for class com_gotye_sdk_EasyAudioEncoder */

#ifdef __cplusplus
extern "C" {
#endif

typedef struct fields_t {
	jfieldID    handle; // for save encoder handle
	jfieldID	listener; // for save listener handle
	jmethodID   ondata_callback;
} fields_t;

static pthread_mutex_t sLock;
static fields_t fields;

static long getEncoder(JNIEnv* env, jobject thiz)
{
	pthread_mutex_lock(&sLock);
	jlong handle = (*env)->GetLongField(env, thiz, fields.handle);
	pthread_mutex_unlock(&sLock);
	return handle;
}

static long setEncoder(JNIEnv* env, jobject thiz, long encoder)
{
	pthread_mutex_lock(&sLock);
	jlong old = (*env)->GetLongField(env, thiz, fields.handle);
	(*env)->SetLongField(env, thiz, fields.handle, encoder);
	pthread_mutex_unlock(&sLock);
	return old;
}

/*
 * Class:     com_gotye_sdk_EasyAudioEncoder
 * Method:    EasyAudioEncoderOpen
 * Signature: (IIIZ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_gotye_sdk_EasyAudioEncoder_EasyAudioEncoderOpen
  (JNIEnv *env, jobject thiz, jint sample_rate, jint channels, jint bitrate, 
  jboolean bAddAdtsHeader)
{
	PPLOGI("EasyAudioEncoderOpen()");

	pthread_mutex_init(&sLock, NULL);

	jclass clazzEncoder = (*env)->FindClass(env, "com/gotye/sdk/EasyAudioEncoder");
	if (clazzEncoder == NULL) {
		PPLOGE("failed to find class com/gotye/sdk/EasyAudioEncoder");
		jclass exceptionClass = (*env)->FindClass(env, "java/lang/RuntimeException");
		(*env)->ThrowNew(env, exceptionClass, "failed to find class com/gotye/sdk/EasyAudioEncoder");
		return JNI_FALSE;
	}

	fields.handle = (*env)->GetFieldID(env, clazzEncoder, "mHandle", "J");
	if (fields.handle == NULL) {
		PPLOGE("failed to get mHandle FieldID");
		jclass exceptionClass = (*env)->FindClass(env, "java/lang/RuntimeException");
		(*env)->ThrowNew(env, exceptionClass, "failed to get mHandle FieldID");
		return JNI_FALSE;
	}

	long handle = open_audio_encoder(sample_rate, channels, bitrate, bAddAdtsHeader ? 1 : 0);
	if (handle == INVALID_HANDLE) {
		PPLOGE("failed to open audio encoder");
		return JNI_FALSE;
	}

	setEncoder(env, thiz, handle);
	PPLOGI("EasyAudioEncoderOpen done!");
	return JNI_TRUE;
}

/*
 * Class:     com_gotye_sdk_EasyAudioEncoder
 * Method:    EasyAudioEncoderGetBufSize
 * Signature: (I)V
 */
JNIEXPORT jint JNICALL Java_com_gotye_sdk_EasyAudioEncoder_EasyAudioEncoderGetBufSize
  (JNIEnv *env, jobject thiz)
{
	long handle = getEncoder(env, thiz);
	return get_buffer_size(handle);
}

/*
 * Class:     com_gotye_sdk_EasyAudioEncoder
 * Method:    EasyAudioEncoderAdd
 * Signature: ([BII[B)I
 */
JNIEXPORT jint JNICALL Java_com_gotye_sdk_EasyAudioEncoder_EasyAudioEncoderAdd
  (JNIEnv *env, jobject thiz, 
  jbyteArray pcm_data, jint start, jint byteCount, jbyteArray output_data)
{
	//PPLOGD("EasyAudioEncoderAdd()");

	jbyte* p_pcm_data = (*env)->GetByteArrayElements(env, pcm_data, NULL);
	jbyte* p_output_data =  (*env)->GetByteArrayElements(env, output_data, NULL);
	jsize out_maxlen = (*env)->GetArrayLength(env, output_data);

	long handle = getEncoder(env, thiz);
	int n =	encode_audio(handle, (const char *)p_pcm_data + start, byteCount, 
		(char *)p_output_data, out_maxlen);

	(*env)->ReleaseByteArrayElements(env, pcm_data,  p_pcm_data, 0);
    (*env)->ReleaseByteArrayElements(env, output_data,  p_output_data, 0);
	return n;
}

/*
 * Class:     com_gotye_sdk_EasyAudioEncoder
 * Method:    EasyAudioEncoderClose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_gotye_sdk_EasyAudioEncoder_EasyAudioEncoderClose
  (JNIEnv *env, jobject thiz)
{
	long handle = getEncoder(env, thiz);
	close_audio_encoder(handle);

	setEncoder(env, thiz, INVALID_HANDLE);
	pthread_mutex_destroy(&sLock);
}

/*
 * Class:     com_gotye_sdk_EasyAudioEncoder
 * Method:    EasyAudioEncoderSetMuxer
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_gotye_sdk_EasyAudioEncoder_EasyAudioEncoderSetMuxer
  (JNIEnv *env, jobject thiz, jlong muxer)
{
}

#ifdef __cplusplus
}
#endif
