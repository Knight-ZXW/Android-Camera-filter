#include "easydecoder.h"
#include "anddefs.h"
#include "andsysutil.h"
#include "andstr.h"
#include "andfifobuffer.h"
#include "andqueue.h"
#include "codecdef.h"
#include <pthread.h> // for sync

#define LOG_TAG "easydecoder"
#include "pplog.h"

#ifdef __ANDROID__
typedef struct fields_t {
	jfieldID    handle; // for save decoder handle
	jfieldID	listener; // for save listener handle
	jmethodID   post_event;
}fields_t;

static long getDecoder(JNIEnv* env, jobject thiz);
static long setDecoder(JNIEnv* env, jobject thiz, long decoder);
#endif

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#ifdef DEC_USE_SWSCALE
#include "libswscale/swscale.h"
#endif
#include "libavutil/avutil.h"
#include "libavutil/imgutils.h"

#define DECODER_FIFO_SIZE		(1048576 * 4) // 4MB
#define DECODER_FRAMESIZE_LEN	4

struct easy_decoder_handle
{
	int					id;
	int					width;
	int					height;
	int					out_framesize;
	//ffmpeg
	AVCodecContext*		dec_ctx;			// ffmpeg decoder context
	AVCodec*			dec;				// ffmpeg decoder
	AVFrame*			dec_frame;			// decoded yuv420p picture
	AVFrame*			out_frame;			// outout pixel format picture
#ifdef DEC_USE_SWSCALE
	struct SwsContext*	img_convert_ctx;	// do swscale context
#endif
	//data
	FifoBuffer			fifo;				// store decoded picture buffer
	SimpleQueue			queue;				// store opaque data
	//info
	int					pic_trans;			// is need transform input picture pixel format
	int					dec_frames_num;		// decoded frame count
	int					dec_pic_num;		// output picture count
	long				start_sec, end_sec;
	long				start_usec, end_usec;

	pthread_mutex_t		mutex;		// sync add() and get()
	//...
};

static AVFrame * alloc_picture(enum AVPixelFormat pix_fmt, int width, int height);
static int decode_data(easy_decoder_handle* handle, unsigned char* pData, int datalen,
					   unsigned char* pOpaque, int opaque_len);
static int decode_flush(easy_decoder_handle* handle);

static pthread_mutex_t sLock;
static fields_t fields;

static void ff_log(void* user, int level, const char* fmt, va_list vl)
{
	char szLog[2048] = {0};
	vsprintf(szLog, fmt, vl);
	PPLOGD("ffmpeg:%s", szLog);
}

#ifdef __ANDROID__
jboolean 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderOpen(JNIEnv* env, jobject thiz, 
	int w, int h, int out_fmt)
{
	PPLOGI("EasyDecoderOpen()");

	pthread_mutex_init(&sLock, NULL);

	jclass clazzEncoder = (*env)->FindClass(env, "com/gotye/sdk/EasyDecoder");
	if (clazzEncoder == NULL) {
		PPLOGE("decoder is null, EasyDecoderOpen failed");
		return JNI_FALSE;
	}

	fields.handle = (*env)->GetFieldID(env, clazzEncoder, "mHandle", "J");

	long ret = AndCodec_EasyDecoderOpen(w, h, out_fmt);
	if (ret == INVALID_HANDLE)
		return JNI_FALSE;

	setDecoder(env, thiz, ret);
	PPLOGI("EasyEncoderOpen done!");
	return JNI_TRUE;
}

jint 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderAdd(JNIEnv* env, jobject thiz,
	jobject decdata, jint start, jint byteCount, jobject opaque)
{
	PPLOGD("EasyEncoderAdd()");
	jbyte* p_decdata	= (*env)->GetByteArrayElements(env, decdata, NULL);

	PPLOGD("add size %d", byteCount);

	jbyte* p_opaque		= (*env)->GetByteArrayElements(env, opaque, NULL);
	int size			= (*env)->GetArrayLength(env, opaque);

	long handle = getDecoder(env, thiz);
	int n = AndCodec_EasyDecoderAdd(handle, (unsigned char *)p_decdata + start, byteCount, 
		 (unsigned char *)p_opaque, size);

	(*env)->ReleaseByteArrayElements(env, decdata, p_decdata, 0);
    (*env)->ReleaseByteArrayElements(env, opaque,  p_opaque, 0);
	return n;
}

jint 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderGet(JNIEnv* env, jobject thiz,
	jobject picdata, jobject opaque)
{
	PPLOGD("EasyEncoderGet()");

	jbyte* p_pic	= (*env)->GetByteArrayElements(env, picdata, NULL);
	//jsize pic_size	= (*env)->GetArrayLength(env, opaque);

	jbyte* p_opaque =  (*env)->GetByteArrayElements(env, opaque, NULL);
	int opaque_len	= (*env)->GetArrayLength(env, opaque);

	long handle = getDecoder(env, thiz);
	int n = AndCodec_EasyDecoderGet(handle, (unsigned char *)p_pic, 
		 (unsigned char *)p_opaque, &opaque_len);

	(*env)->ReleaseByteArrayElements(env, picdata, p_pic, 0);
    (*env)->ReleaseByteArrayElements(env, opaque,  p_opaque, 0);
	return n;
}

void 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderClose(JNIEnv* env, jobject thiz)
{
	PPLOGI("EasyEncoderClose()");

	long handle = getDecoder(env, thiz);
	AndCodec_EasyDecoderClose(handle);
}

double 
Java_com_gotye_sdk_EasyDecoder_EasyDecoderGetFPS(JNIEnv* env, jobject thiz)
{
	//PPLOGD("EasyEncoderGetFPS()");

	long handle = getDecoder(env, thiz);
	return AndCodec_EasyDecoderGetFPS(handle);
}

static long getDecoder(JNIEnv* env, jobject thiz)
{
	pthread_mutex_lock(&sLock);
	jlong handle = (*env)->GetLongField(env, thiz, fields.handle);
	pthread_mutex_unlock(&sLock);
	return handle;
}

static long setDecoder(JNIEnv* env, jobject thiz, long decoder)
{
	pthread_mutex_lock(&sLock);
	jlong old = (*env)->GetLongField(env, thiz, fields.handle);
	(*env)->SetLongField(env, thiz, fields.handle, decoder);
	pthread_mutex_unlock(&sLock);
	return old;
}

#endif // __ANDROID__

char * AndCodec_EasyDecoderVersion()
{
	return AND_DECODER_VERSION;
}

long AndCodec_EasyDecoderOpen(int w, int h, int out_fmt)
{
	PPLOGI("AndCodec_EasyDecoderOpen()");

	av_log_set_callback(ff_log);

	easy_decoder_handle* handle = (easy_decoder_handle*)and_sysutil_malloc(sizeof(easy_decoder_handle));
	PPLOGI("easy_decoder handle allocated: addr %p, size %d", handle, sizeof(easy_decoder_handle));
	memset(handle, 0, sizeof(easy_decoder_handle));

	//parse input
	handle->width			= w;
	handle->height			= h;
	handle->dec_frames_num	= 0;
	handle->dec_pic_num		= 0;
	handle->start_sec	= and_sysutil_get_time_sec();
	handle->start_usec	= and_sysutil_get_time_usec(); 

	enum AVPixelFormat out_pix_fmt;

	switch(out_fmt) {
	case AND_PIXEL_FMT_RGB565:
		out_pix_fmt = AV_PIX_FMT_RGB565LE;
		break;
	case AND_PIXEL_FMT_YUV420P:
		out_pix_fmt = AV_PIX_FMT_YUV420P;
		break;
	default:
		out_pix_fmt = AV_PIX_FMT_NONE;
		break;
	}

	PPLOGI("input: %d x %d, out_fmt:%d  ffmpeg_pix_fmt:%d", 
		handle->width, handle->height, out_fmt, (int)out_pix_fmt);

	if (out_pix_fmt == -1) {
		PPLOGE("unsupport output pixel format:%d", out_fmt);
		return INVALID_HANDLE;
	}

	if (out_pix_fmt == AV_PIX_FMT_YUV420P) {
		handle->pic_trans = 0;
		handle->out_framesize = handle->width * handle->height * 3 / 2;
	}
	else {
		handle->pic_trans = 1;
		handle->out_framesize = handle->width * handle->height * 2;
	}

	avcodec_register_all();

	int ret;
	int succeed = 0;
	do {

		PPLOGI("find decoder.");
		handle->dec = avcodec_find_decoder(AV_CODEC_ID_H264);

		PPLOGI("alloc context.");
		handle->dec_ctx = avcodec_alloc_context3(handle->dec);
		handle->dec_ctx->width	 = handle->width;
		handle->dec_ctx->height	 = handle->height;
		handle->dec_ctx->pix_fmt = AV_PIX_FMT_YUV420P;

		PPLOGI("open codec.");
		ret = avcodec_open2(handle->dec_ctx, handle->dec, NULL);
		if(ret < 0) {
			PPLOGE("failed to open video decoder.");
			break;
		}

		if(handle->pic_trans > 0)
		{
#ifdef DEC_USE_SWSCALE
			PPLOGI("alloc sws context.");
			handle->img_convert_ctx = sws_getContext(
					handle->dec_ctx->width, handle->dec_ctx->height,
					handle->dec_ctx->pix_fmt, 
					handle->width, handle->height, 
					out_pix_fmt,
					SWS_FAST_BILINEAR, NULL, NULL, NULL);
			if(!handle->img_convert_ctx) {
				PPLOGE("failed to alloc sws context: %dx%d@%d, %dx%d@%d",
					handle->dec_ctx->width, handle->dec_ctx->height,
					handle->dec_ctx->pix_fmt, 
					handle->width, handle->height, 
					out_pix_fmt);
				break;
			}
#endif
			PPLOGI("alloc out picture.");
			handle->out_frame = alloc_picture(out_pix_fmt, handle->width, handle->height);
			if(!handle->out_frame){
				PPLOGE("failed to alloc out frame.");
				break;
			}
		}

		handle->dec_frame = av_frame_alloc();
		if(!handle->dec_frame){
			PPLOGE("failed to alloc dec frame.");
			break;
		}

		//all done!
		succeed = 1;
	}while(0);

	if(!succeed)
		return INVALID_HANDLE;

	ret = pthread_mutex_init(&handle->mutex, 0);
	if (ret < 0) {
		PPLOGE("failed to create mutex");
		return INVALID_HANDLE;
	}

	PPLOGI("create fifo.");
	ret = and_fifo_create(&handle->fifo, DECODER_FIFO_SIZE);
	if (ret < 0) {
		PPLOGE("failed to create fifo");
		return INVALID_HANDLE;
	}

	PPLOGI("create queue.");
	ret = and_queue_init(&handle->queue, OPAQUE_DATA_LEN, QUEUE_SIZE);
	if (ret < 0) {
		PPLOGE("failed to create queue");
		return INVALID_HANDLE;
	}

	PPLOGI("open decoder handle %p", handle);
	return (long)handle;
}

int AndCodec_EasyDecoderAdd(long dec, unsigned char* decdata, int decdata_size, 
							unsigned char* opaque, int opaque_len)
{
	PPLOGD("AndCodec_EasyDecoderAdd()");

	if(!dec) {
		PPLOGE("decoder handle is null");
		return -1;
	}
	if(!decdata) {
		PPLOGE("decode data is null");
		return -1;
	}

	easy_decoder_handle* handle = (easy_decoder_handle *)dec;
	return decode_data(handle, decdata, decdata_size, opaque, opaque_len);
}

int AndCodec_EasyDecoderFlush(long dec)
{
	PPLOGD("AndCodec_EasyDecoderFlush()");

	if(!dec) {
		PPLOGE("decoder handle is null");
		return -1;
	}

	easy_decoder_handle* handle = (easy_decoder_handle *)dec;
	return decode_flush(handle);
}

int AndCodec_EasyDecoderGet(long dec, unsigned char* picdata, 
							unsigned char* opaque, int *opaque_len)
{
	PPLOGD("AndCodec_EasyDecoderGet()");

	if(!dec) {
		PPLOGE("decoder handle is null");
		return -1;
	}
	if(!picdata) {
		PPLOGE("picture data is null");
		return -1;
	}

	easy_decoder_handle* handle = (easy_decoder_handle *)dec;

	pthread_mutex_lock(&handle->mutex);

	int readed;
	int frame_size;
	int ret;

	if (and_fifo_used(&handle->fifo) < DECODER_FRAMESIZE_LEN) {
		readed = 0;
		goto exit;
	}

	readed = and_fifo_read(&handle->fifo, (char *)&frame_size, DECODER_FRAMESIZE_LEN);
	PPLOGD("frame size %d", frame_size);
	readed = and_fifo_read(&handle->fifo, (char *)picdata, frame_size);
	if (readed < frame_size) {
		PPLOGE("frame data is corrupt %d.%d", frame_size, readed);
		readed = -1;
		goto exit;
	}

	ret = and_queue_get(&handle->queue, (void *)opaque);
	if(ret < 0) {
		PPLOGE("failed to get opaque data.");
		readed = -1;
		goto exit;
	}

	//and_sysutil_memcpy(opaque, (void *)&opaque_data, opa_size);
	if(opaque_len)
		*opaque_len = OPAQUE_DATA_LEN;
	
exit:
	pthread_mutex_unlock(&handle->mutex);
	return readed;
}

double AndCodec_EasyDecoderGetFPS(long dec)
{
	PPLOGD("AndCodec_EasyEncoderGetFPS()");

	easy_decoder_handle* handle = (easy_decoder_handle *)dec;

	double elapsed;
	double fps;

	handle->end_sec		= and_sysutil_get_time_sec();
	handle->end_usec	= and_sysutil_get_time_usec();
	elapsed = (double) (handle->end_sec - handle->start_sec);
	elapsed += (double) (handle->end_usec - handle->start_usec) /
		(double) 1000000;

	if (elapsed <= 0.01)
		elapsed = 0.01f;

	fps = (double)handle->dec_frames_num / elapsed;
	PPLOGD("fps: %.2f(%d frames/%.3f sec)", 
		fps, handle->dec_frames_num, elapsed);

	return fps;
}

void AndCodec_EasyDecoderClose(long dec)
{
	PPLOGI("AndCodec_EasyDecoderClose()");

	easy_decoder_handle* handle = (easy_decoder_handle *)dec;
	PPLOGI("decoder handle %x", handle);

	if(handle->dec_frame)
		av_frame_free(&handle->dec_frame);

	if(handle->pic_trans && handle->out_frame) 
		av_frame_free(&handle->out_frame);

	if(handle->dec_ctx) {
		avcodec_close(handle->dec_ctx);
	    av_free(handle->dec_ctx);
	}
	
	pthread_mutex_destroy(&handle->mutex);

	and_fifo_close(&handle->fifo);
	and_queue_close(&handle->queue);

	and_sysutil_free(handle);
}

static int decode_data(easy_decoder_handle* handle, unsigned char* pData, int datalen,
					   unsigned char* pOpaque, int opaque_len)
{
	pthread_mutex_lock(&handle->mutex);

	AVPacket pkt;
	int got_frame = 0;
	int ret;
	int written;
	int len;

	if(opaque_len != OPAQUE_DATA_LEN) {
		PPLOGE("opaque data size is wrong %d.%d", 
			opaque_len, OPAQUE_DATA_LEN);
		got_frame = -1;
		goto exit;
	}

	av_init_packet(&pkt);
	pkt.data = NULL;
	pkt.size = 0;

	pkt.data = pData;
	pkt.size = datalen;
	PPLOGD("decode frame size:[%d] %d", handle->dec_frames_num, datalen);

	while (pkt.size > 0) {
		ret = avcodec_decode_video2(handle->dec_ctx, handle->dec_frame, &got_frame, &pkt);
		if (ret < 0) {
			PPLOGW("failed to decode #%d %d, ret %d", 
				handle->dec_frames_num, datalen, ret);	
			break;
		}
		handle->dec_frames_num++;

		if (got_frame) {
			PPLOGD("got pic. type %d", handle->dec_frame->pict_type);
			handle->dec_pic_num++;

			filesize_t timestamp;
			if (handle->dec_frame->opaque) {
				and_sysutil_memcpy(&timestamp, handle->dec_frame->opaque, sizeof(filesize_t));
				PPLOGI("frame opaque %lld", timestamp);
			}

			if (handle->pic_trans)
			{
#ifdef DEC_USE_SWSCALE
				//to out_pix_fmt rgb565le
				PPLOGD("scale begin. linesize_in:%d linesize_out:%d h:%d", 
					handle->dec_frame->linesize[0], handle->out_frame->linesize[0], handle->dec_ctx->height);
				sws_scale(handle->img_convert_ctx, 
					(uint8_t const**)handle->dec_frame->data, handle->dec_frame->linesize,
					0, handle->dec_ctx->height,
					handle->out_frame->data, handle->out_frame->linesize);

				len = handle->out_frame->linesize[0]* handle->height;
				PPLOGD("scale end.");
#else
				PPLOGE("swscale NOT build-int");
				got_frame = -1;
				goto exit;
#endif
			}
			else  //420p only
			{
				handle->out_frame = handle->dec_frame;
				len = handle->out_frame->linesize[0]* handle->height * 3 / 2 ;
			}

			PPLOGD("line size:%d; pic len:%d.", handle->out_frame->linesize[0], len);
			written = and_fifo_write(&handle->fifo, (void *)&len, DECODER_FRAMESIZE_LEN);
			if (written != DECODER_FRAMESIZE_LEN) {
				PPLOGE("failed to write data size %d - %d", len, written);
				got_frame = -1;
				goto exit;
			}

			written = 0;
			if(handle->pic_trans) // rgb565le
			{
				written = and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[0], len);
			}
			else //420p only
			{
				written += and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[0], handle->out_frame->linesize[0] * handle->height);
				written += and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[1], handle->out_frame->linesize[1] * handle->height/2);
				written += and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[2], handle->out_frame->linesize[2] * handle->height/2);
			}
			if (written != len) {
				PPLOGE("failed to write %d - %d", len, written);
				got_frame = -1;
				goto exit;
			}

			if ( and_queue_put(&handle->queue, (void *)pOpaque) < 0) {
				got_frame = -1;
				goto exit;
			}
			//all done!
			break;
		}

		if(pkt.data) {
			pkt.data += ret;
			pkt.size -= ret;
		}
	}

	av_free_packet(&pkt);
	pkt.data = NULL;
	pkt.size = 0;

	if(!got_frame) {
		PPLOGW("no picture decoded out in #%d", handle->dec_frames_num);
		
		if ( and_queue_put(&handle->queue, (void *)pOpaque) < 0) {
			got_frame = -1;
			goto exit;
		}
	}

	// handle->dec_frame->key_frame
	if (got_frame /*&& handle->dec_frames_num > 1*/ && 
		AV_PICTURE_TYPE_I == handle->dec_frame->pict_type ) {
		while (decode_flush(handle) > 0) {
			PPLOGI("flush picture out in #%d", handle->dec_frames_num);
		}

		int opaque_num		= and_queue_used(&handle->queue);
		int cache_pic_num	= and_fifo_used(&handle->fifo) / handle->out_framesize;
		int pop_num = opaque_num - cache_pic_num;
		if (pop_num > 0) {
			PPLOGI("opaque list %d, cache picture %d, to drop %d", 
				opaque_num, cache_pic_num, pop_num);

			OpaqueData op;
			while (pop_num > 0) {
				and_queue_get(&handle->queue, (void *)&op);
				PPLOGI("discard opaque data");
				pop_num--;
			}
		}
	}

exit:
	pthread_mutex_unlock(&handle->mutex);
	return got_frame;
}

static int decode_flush(easy_decoder_handle* handle)
{
	AVPacket pkt;
	int got_frame = 0;
	int ret;
	int written;
	int len;

	av_init_packet(&pkt);
	pkt.data = NULL;
	pkt.size = 0;

	PPLOGD("decode flush: #%d", handle->dec_frames_num);

	ret = avcodec_decode_video2(handle->dec_ctx, handle->dec_frame, &got_frame, &pkt);
	if (ret < 0) {
		PPLOGW("failed to decode flush #%d, ret %d", 
			handle->dec_frames_num, ret);	
		return -1;
	}

	if (got_frame) {
		PPLOGD("got pic.");
		handle->dec_pic_num++;

		if(handle->pic_trans) {
#ifdef DEC_USE_SWSCALE
			//to out_pix_fmt 
			PPLOGD("scale begin. linesize_in:%d linesize_out:%d h:%d", 
				handle->dec_frame->linesize[0], handle->out_frame->linesize[0], handle->dec_ctx->height);
			sws_scale(handle->img_convert_ctx, 
				(uint8_t const**)handle->dec_frame->data, handle->dec_frame->linesize,
				0, handle->dec_ctx->height,
				handle->out_frame->data, handle->out_frame->linesize);

			len = handle->out_frame->linesize[0]* handle->height;
			PPLOGD("scale end.");
#else
			PPLOGE("swscale NOT build-int");
			return -1;
#endif
		}
		else  //420p only
		{
			handle->out_frame = handle->dec_frame;
			len = handle->out_frame->linesize[0]* handle->height * 3 / 2 ;
		}

		PPLOGD("line size:%d; pic len:%d.", handle->out_frame->linesize[0], len);
		written = and_fifo_write(&handle->fifo, (void *)&len, DECODER_FRAMESIZE_LEN);
		if (written != DECODER_FRAMESIZE_LEN) {
			PPLOGE("failed to write data size %d - %d", len, written);
			return -1;
		}

		written = 0;
		if(handle->pic_trans) {
			written = and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[0], len);
		}
		else {
			written += and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[0], handle->out_frame->linesize[0] * handle->height);
			written += and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[1], handle->out_frame->linesize[1] * handle->height/2);
			written += and_fifo_write(&handle->fifo, (void *)handle->out_frame->data[2], handle->out_frame->linesize[2] * handle->height/2);
		}
		if (written != len) {
			PPLOGE("failed to write %d - %d", len, written);
			return -1;
		}
	}

	return got_frame;
}

static AVFrame * alloc_picture(enum AVPixelFormat pix_fmt, int width, int height)
{
	AVFrame *picture;
	uint8_t *picture_buf;
	int size;

	picture = av_frame_alloc();
	if (!picture)
		return NULL;
	size = avpicture_get_size(pix_fmt, width, height);
	picture_buf = (uint8_t *)av_malloc(size);
	if (!picture_buf) {
		av_free(picture);
		return NULL;
	}
	avpicture_fill((AVPicture *)picture, picture_buf,
		pix_fmt, width, height);
	return picture;
}

