#include "andffencoder.h"
#include "anddefs.h"
#include "andsysutil.h"
#include "andlog.h"
#include "andfifobuffer.h"

#include "libavutil/avutil.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"
#include "libavutil/opt.h" //for av_opt_set
#include "libavutil/mem.h"

#define STREAM_PIX_FMT	PIX_FMT_YUV420P
#define OUT_WIDTH		320
#define OUT_HEIGHT		240


#define PB_BUF_SIZE		65536
#define DUMP_SIZE		65536
#define FFENC_FIFO_SIZE	1048576
#define GET_MAXSIZE		65536

const int sws_flags = SWS_FAST_BILINEAR;

struct ff_encoder_handle
{
	AVFormatContext*				oc;
	AVOutputFormat*					fmt;
	AVStream*						video_st;
	AVStream*						audio_st;
	uint8_t*						pb_buf;
	AVIOContext*					pb;
	//video
	AVFrame*						pEncFrame;
	AVFrame*						pFrame;
	struct SwsContext*				img_convert_ctx;
	unsigned int					nFrameSize;
	int								width;
	int								height;
	int								in_fmt;
	//sws
	int								pic_trans;		// is need transform input picture pixel format
	//audio
	AVFrame*						pAudioFrame;
	unsigned int					audio_frame_size;
	unsigned int					audio_buf_size;
	uint8_t*						audio_buf;
	//dump
	FifoBuffer						fifo;
	//stat
	int								in_frames;		// input frame count
	int								out_frames;		// output frame count
	long							start_sec, end_sec;
	long							start_usec, end_usec;
	//...
};

static void ff_log(void* user, int level, const char* fmt, va_list vl)
{
	char szLog[2048] = {0};
	vsprintf(szLog, fmt, vl);
	and_log_writeline_easy(0, LOG_DEBUG, "ffmpeg:%s", szLog);
}

static int ff_write_packet(void *opaque, uint8_t *buf, int buf_size)
{
	and_log_writeline_easy(0, LOG_INFO, "dump %d", buf_size);

	ff_encoder_handle *encoder = (ff_encoder_handle *)opaque;
	int ret;
	if(encoder) {
		ret = and_fifo_write(&encoder->fifo, (char *)buf, (unsigned int)buf_size);
		if(ret != buf_size)
			and_log_writeline_easy(0, LOG_WARN, "fifo overflowed %d.%d", ret, buf_size);
	}

	return buf_size;
}

static AVStream * add_videostream(ff_encoder_handle *ins);
static AVStream * add_audiostream(ff_encoder_handle *ins);
static AVFrame * alloc_picture(enum AVPixelFormat pix_fmt, int width, int height);

static int write_video_frame(ff_encoder_handle *handle, uint8_t* pBuffer, int datalen);
static int write_audio_frame(ff_encoder_handle *handle, uint8_t* pBuffer, int datalen);

#ifdef __ANDROID__
static int convert_jstring(JNIEnv* env, char *des_str, int* len, jstring str);

int 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderOpen(JNIEnv* env, jobject thiz,
													 int w, int h, int in_fmt, 
													 jstring profile, jstring enc_str)
{
	and_log_writeline_simple(0, LOG_INFO, "EasyffEncoderOpen()");

	//parse input and output filename
	char str_profile[256]	= {0};//preset
	char str_enc[256]		= {0};
	int str_len 			= 256;

	convert_jstring(env, str_profile, &str_len, profile);
	convert_jstring(env, str_enc, &str_len, enc_str);

	return AndCodec_EasyffEncoderOpen(w, h, in_fmt, str_profile, str_enc);
}

int 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderAdd(JNIEnv* env, jobject thiz,
													int ffenc, jobject picdata, int picdata_size,
													jobject audiodata, int audiodata_size,
													jobject opaque)
{
	and_log_writeline_simple(0, LOG_DEBUG, "EasyffEncoderAdd()");
#ifdef USE_NATIVE_IO
	unsigned char *p_pic = NULL;
	jlong pic_size = 0;
	if(picdata) {
		(*env)->GetDirectBufferAddress(env, picdata);
		if(!p_pic) {
			and_log_writeline_easy(0, LOG_ERROR, "failed to get direct addr");
			return -1;
		}
		and_log_writeline_easy(0, LOG_DEBUG, "add native io addr %p", p_pic);
		pic_size = (*env)->GetDirectBufferCapacity(env, picdata);
	}
	else {
		p_pic = NULL;
		pic_size = 0;
	}
	
	unsigned char *p_audio = NULL;
	jlong audio_size = 0;
	if(audiodata) {
		p_audio = (*env)->GetDirectBufferAddress(env, audiodata);
		if(!p_pic) {
			and_log_writeline_easy(0, LOG_ERROR, "failed to get direct addr");
			return -1;
		}
		and_log_writeline_easy(0, LOG_DEBUG, "add native io addr %p", p_audio);
		audio_size = (*env)->GetDirectBufferCapacity(env, p_audio);
	}
	else {
		p_audio = NULL;
		audio_size = 0;
	}
#else
	jbyte* p_pic = NULL;
	jsize pic_size = 0;
	if (picdata) {
		p_pic = (*env)->GetByteArrayElements(env, picdata, NULL);
		pic_size = (*env)->GetArrayLength(env, picdata);
	}
	else {
		p_pic = NULL;
		pic_size = 0;
	}
	

	jbyte* p_audio = NULL;
	jsize audio_size;
	if(audiodata) {
		p_audio = (*env)->GetByteArrayElements(env, audiodata, NULL);
		audio_size = (*env)->GetArrayLength(env, audiodata);
	}
	else {
		p_audio = NULL;
		audio_size = 0;
	}
#endif
	and_log_writeline_easy(0, LOG_DEBUG, "add size(a.v) %d.%d", pic_size, audio_size);

	jbyte* p_opaque =  (*env)->GetByteArrayElements(env, opaque, NULL);

	jsize opaque_len = (*env)->GetArrayLength(env, opaque);
	int n =	AndCodec_EasyffEncoderAdd(ffenc, (unsigned char *)p_pic, picdata_size,
		(unsigned char *)p_audio, audiodata_size,
		(unsigned char *)p_opaque, (int)opaque_len);

#ifndef USE_NATIVE_IO
	if(picdata)
		(*env)->ReleaseByteArrayElements(env, picdata, p_pic, 0);
	if(audiodata)
		(*env)->ReleaseByteArrayElements(env, audiodata, p_audio, 0);
#endif
	(*env)->ReleaseByteArrayElements(env, opaque,  p_opaque, 0);
	return n;
}

int 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderGet(JNIEnv* env, jobject thiz,
													int ffenc, jobject encdata, jobject opaque)
{
	and_log_writeline_simple(0, LOG_DEBUG, "EasyffEncoderGet()");
#ifdef USE_NATIVE_IO
	unsigned char *p_data = (*env)->GetDirectBufferAddress(env, encdata);
	if(!p_data) {
		and_log_writeline_simple(0, LOG_ERROR, "failed to get direct addr");
		return -1;
	}
	and_log_writeline_easy(0, LOG_INFO, "get addr %p", p_data);
	jlong data_size = (*env)->GetDirectBufferCapacity(env, encdata);
#else
	jbyte* p_data = (*env)->GetByteArrayElements(env, encdata, NULL);
	jsize data_size = (*env)->GetArrayLength(env, encdata);
#endif
	and_log_writeline_easy(0, LOG_DEBUG, "get: data buf_size %d", data_size);

	jbyte* p_opaque =  (*env)->GetByteArrayElements(env, opaque, NULL);

	int opaque_len = (*env)->GetArrayLength(env, opaque);

	int n = AndCodec_EasyffEncoderGet(ffenc, (unsigned char *)p_data, 
		(unsigned char *)p_opaque, &opaque_len);
#ifndef USE_NATIVE_IO
	(*env)->ReleaseByteArrayElements(env, encdata, p_data, 0);
#endif
	(*env)->ReleaseByteArrayElements(env, opaque, p_opaque, 0);
	return n;	
}

void 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderClose(JNIEnv* env, jobject thiz,
													int ffenc)
{
	and_log_writeline_easy(0, LOG_INFO, "EasyffEncoderClose() enc %d", ffenc);
	return AndCodec_EasyffEncoderClose(ffenc);
}

double 
Java_tv_xormedia_AndCodec_CodecLib_EasyffEncoderGetFPS(JNIEnv* env, jobject thiz, 
													   int ffenc)
{
	//and_log_writeline_simple(0, LOG_DEBUG, "EasyffEncoderGetFPS()");

	return AndCodec_EasyffEncoderGetFPS(ffenc);
}

void Java_tv_xormedia_testffencoder_CodecLib_EasyffEncoderTest()
{
	AndCodec_EasyffEncoderTest();
}

static int convert_jstring(JNIEnv* env, char *des_str, int* len, jstring str)
{
	const char *nativeString = (*env)->GetStringUTFChars(env, str, 0);     
	and_sysutil_strcpy(des_str, nativeString, *len);
	(*env)->ReleaseStringUTFChars(env, str, nativeString);

	return 0;
}
#endif

int AndCodec_EasyffEncoderOpen(int w, int h, int in_fmt, 
							   const char *profile, const char *enc_str)
{
	and_log_writeline_simple(0, LOG_INFO, "AndCodec_EasyffEncoderOpen()");

	and_log_init("/mnt/sdcard/easyFFencoder.log", LOG_INFO);
	
	av_log_set_callback(ff_log);
	
	av_register_all();
	
	int ret;
	
	ff_encoder_handle *handle = NULL;
	handle = (ff_encoder_handle *)and_sysutil_malloc(sizeof(ff_encoder_handle));
	if(!handle) {
		and_log_writeline_simple(0, LOG_ERROR, "FFMPEG fmt init error");
		return INVALID_HANDLE;
	}
	
	and_sysutil_memclr(handle, sizeof(ff_encoder_handle));
	handle->width		= w;
	handle->height		= h;
	handle->in_fmt		= in_fmt;
	handle->in_frames	= 0;
	handle->out_frames	= 0;

	enum AVPixelFormat fmt;
	switch(handle->in_fmt) {
	case AND_PIXEL_FMT_YUV420P:
		fmt = AV_PIX_FMT_YUV420P;
		break;
	case AND_PIXEL_FMT_NV21:
		fmt = AV_PIX_FMT_NV21;
		break;
	case AND_PIXEL_FMT_BGR24:
		fmt = AV_PIX_FMT_BGR24;
		break;
	default:
		and_log_writeline_easy(0, LOG_ERROR, "wrong format %d.", handle->in_fmt);
		return INVALID_HANDLE;
	};

	if(AV_PIX_FMT_YUV420P != fmt) {
		handle->pic_trans = 1;

		handle->img_convert_ctx = sws_getContext(
			handle->width, handle->height, fmt, 
			handle->width, handle->height, AV_PIX_FMT_YUV420P,
			SWS_FAST_BILINEAR, NULL, NULL, NULL);
		if(!handle->img_convert_ctx) {
			and_log_writeline_simple(0, LOG_ERROR, "failed to alloc sws context.");
			return INVALID_HANDLE;
		}
		and_log_writeline_simple(0, LOG_INFO, "transfer = yes");
	}
	else {
		handle->pic_trans = 0;
	}

	/*and_log_writeline_simple(0, LOG_INFO, "before guess format");
	encoder->fmt = av_guess_format("mpegts", NULL, NULL);
    if (!encoder->fmt) {
        and_log_writeline_simple(0, LOG_ERROR, "FFMPEG fmt init error");
		return -1;
    }*/

	/*and_log_writeline_simple(0, LOG_INFO, "before avformat_alloc_context");
    encoder->oc = avformat_alloc_context();
    if (!encoder->oc) {
        and_log_writeline_simple(0, LOG_ERROR, "FFMPEG oc init error.");
		return -1;
    }*/
	
	and_log_writeline_simple(0, LOG_INFO, "before avformat_alloc_output_context2");
	ret = avformat_alloc_output_context2(&handle->oc, handle->fmt, "mpegts", NULL);
    //encoder->oc->oformat = encoder->fmt;
	//encoder->oc->max_delay = 200000;

	and_log_writeline_simple(0, LOG_INFO, "before add video stream");
	handle->video_st = add_videostream(handle);
	if(!handle->video_st)
	{
		and_log_writeline_simple(0, LOG_ERROR, "ffmpeg failed to add video stream");
		return INVALID_HANDLE;
	}

	and_log_writeline_simple(0, LOG_INFO, "before add audio stream");
	handle->audio_st = add_audiostream(handle);
	if(!handle->audio_st)
	{
		and_log_writeline_simple(0, LOG_ERROR, "ffmpeg failed to add audio stream");
		return INVALID_HANDLE;
	}
	
	handle->pb_buf = (unsigned char *)av_mallocz(PB_BUF_SIZE);
	handle->pb = avio_alloc_context(handle->pb_buf, PB_BUF_SIZE, 1, handle, 
		NULL, ff_write_packet, NULL);
	if(!handle->pb){
		and_log_writeline_simple(0, LOG_ERROR, "FFMPEG failed to create pb");
		return INVALID_HANDLE;
	}
	handle->oc->pb = handle->pb;

	av_dump_format(handle->oc, 0, "dummy.ts", 1);

    /* write the stream header, if any */
    avformat_write_header(handle->oc, NULL);

	ret = and_fifo_create(&handle->fifo, FFENC_FIFO_SIZE);
	if(ret < 0) {
		and_log_writeline_simple(0, LOG_ERROR, "failed to create fifo");
		return INVALID_HANDLE;
	}

	// start to calc encode time
	handle->start_sec	= and_sysutil_get_time_sec();
	handle->start_usec	= and_sysutil_get_time_usec(); 

	and_log_writeline_easy(0, LOG_INFO, "open ffmpeg encoder handle %p", handle);

	return (int)handle;
}

int AndCodec_EasyffEncoderTest()
{
	and_log_writeline_simple(0, LOG_INFO, "AndCodec_EasyffEncoderTest()");

	int encoder;

	encoder = AndCodec_EasyffEncoderOpen(320, 240, 0, "ultrafast", NULL);
	if(encoder < 0) {
		and_log_writeline_simple(0, LOG_ERROR, "failed to open ffencoder");
		return -1;
	}

	int frameNo;
	uint8_t *p_pic		= NULL;
	uint8_t *p_audio	= NULL;
	uint8_t *p_dump		= NULL;
	const int audio_one_sec = 176400;
	const int audio_size = audio_one_sec / 5;
	uint8_t *p_audio_ptr = NULL;
	int add_audio_size = 0;
	int plan_encode_size;
	int encoded_audio = 0;
	int audio_frame_size;
	int ret;
	filesize_t	opaque_data = 0;
	int opaque_len = 8;

	p_pic = av_mallocz(OUT_WIDTH * OUT_HEIGHT * 2);
	p_audio = av_mallocz(audio_size);
	audio_frame_size = AndCodec_EasyffEncoderGetAudioFrameSize(encoder);
	and_log_writeline_easy(0, LOG_INFO, "audio frame size %d", audio_frame_size);
	p_dump = av_mallocz(DUMP_SIZE);

	int out_fd = and_sysutil_create_or_open_file("/mnt/sdcard/test/dump.ts", 0644);
	if(out_fd < 0) {
		and_log_writeline_simple(0, LOG_ERROR, "failed to open file");
		return -1;
	}

	for (frameNo = 0 ; frameNo < 500 ; frameNo++) {
		and_log_writeline_easy(0, LOG_INFO, "add frame %d", frameNo);

		memset(p_pic, frameNo, OUT_WIDTH * OUT_HEIGHT * 2);

		p_audio_ptr		= NULL;
		add_audio_size	= 0;

		if(0 == frameNo % 2) {
			plan_encode_size = ( audio_one_sec / 25 ) * frameNo;
			int c = (plan_encode_size - encoded_audio) / audio_frame_size;
			if(c > 0) {
				p_audio_ptr		= p_audio;
				add_audio_size	= c * audio_frame_size;
				encoded_audio  += c * audio_frame_size;
			}
		}

		ret = AndCodec_EasyffEncoderAdd(encoder, (unsigned char *)p_pic,
			OUT_WIDTH * OUT_HEIGHT * 2, p_audio_ptr, add_audio_size,
			(unsigned char *)&opaque_data, opaque_len);
		if (ret < 0) {
			and_log_writeline_simple(0, LOG_ERROR, "failed to add video\n");
			break;
		}

		ret = AndCodec_EasyffEncoderGet(encoder, (unsigned char *)p_dump, 
			(unsigned char *)&opaque_data, &opaque_len);
		if(ret < 0) {
			and_log_writeline_simple(0, LOG_ERROR, "failed to write dump\n");
			break;
		}

		if(ret > 0) {
			unsigned int write_size = (unsigned int)ret;
			ret = and_sysutil_write(out_fd, p_dump, write_size);
			if(ret != write_size)
				and_log_writeline_easy(0, LOG_WARN, "write mismatch %d.%d", ret, write_size);
		}
	}
		
	and_log_writeline_simple(0, LOG_INFO, "ffencoder test all done!");

	AndCodec_EasyffEncoderClose(encoder);
	and_sysutil_close(out_fd);
	return 0;
}

int AndCodec_EasyffEncoderGetAudioFrameSize(int ffenc)
{
	ff_encoder_handle *encoder = (ff_encoder_handle *)ffenc;

	if(!encoder)
		return -1;

	return encoder->audio_frame_size;
}

void AndCodec_EasyffEncoderClose(int ffenc)
{
	and_log_writeline_simple(0, LOG_INFO, "AndCodec_EasyffEncoderClose()");

	ff_encoder_handle *encoder = (ff_encoder_handle *)ffenc;

	av_write_trailer(encoder->oc);

	//close video
	if(encoder->video_st)
	{
		avcodec_close(encoder->video_st->codec);
		if(encoder->pEncFrame)
			avcodec_free_frame(&encoder->pEncFrame);
		if(encoder->pFrame)
			av_free(encoder->pFrame);
		if(encoder->img_convert_ctx)
			sws_freeContext(encoder->img_convert_ctx);
	}

	//close audio
	if(encoder->audio_st)
	{
		avcodec_close(encoder->audio_st->codec);
		av_free(encoder->audio_buf);
		av_free(encoder->pAudioFrame);
	}

	if(encoder->oc)
	{
		/* free the streams */
		int i;
		for(i = 0; i < (int)encoder->oc->nb_streams; i++) {
			av_freep(&encoder->oc->streams[i]->codec);
			av_freep(&encoder->oc->streams[i]);
		}
		encoder->oc->pb->opaque = NULL;//hard code,need fix
		avio_close(encoder->oc->pb);
		av_free(encoder->oc);
		encoder->oc = NULL;
	}

	and_sysutil_free(encoder);
	encoder = NULL;

	and_log_writeline_simple(0, LOG_INFO, "ffEncoder closed");
}

static AVStream * add_videostream(ff_encoder_handle *handle)
{
	AVStream*		st		= NULL;
	AVCodecContext*	c		= NULL;
	AVCodec*		codec	= NULL;

	codec = avcodec_find_encoder(CODEC_ID_H264);
	if (!codec) {
		and_log_writeline_simple(0, LOG_ERROR, "Could not find video codec.");
		return NULL;
	}

	st = avformat_new_stream(handle->oc, codec);
	if (!st) {
		and_log_writeline_simple(0, LOG_ERROR, "Could not allocate video stream.");
		return NULL;
	}

	st->id				= 0;
	c					= st->codec;

	av_opt_set(c->priv_data, "preset", "veryfast", 0);
	av_opt_set(c->priv_data, "tune", "zerolatency", 0);
	av_opt_set(c->priv_data, "profile", "main", 0);

	int bitrate			= 200000;//400k bps
	char x264conf[256]	= {0};
	int maxrate			= bitrate * 3 / 2000;//maxrate = 150% of bit_rate
	int bufsize			= maxrate / 2;
	sprintf(x264conf, "ratetol=0.01:fake-interlaced:vbv-bufsize=%d:vbv-maxrate=%d",		
		bufsize, maxrate);
	and_log_writeline_easy(0, LOG_INFO, "x264 option: %s", x264conf);
	int ret = av_opt_set(c->priv_data, "x264opts", x264conf, 0);
	if(ret != 0) {
		and_log_writeline_easy(0, LOG_ERROR, "failed to set x264: ret=%d", ret);
		return NULL;
	}
	c->qcompress		= 0.2f;

	c->bit_rate			= bitrate;
	c->width			= handle->width;
	c->height			= handle->height;
	c->time_base.num	= 1;
	c->time_base.den	= 10;
	c->gop_size			= 15;//fix me
	c->pix_fmt			= STREAM_PIX_FMT;

	if (handle->oc->oformat->flags & AVFMT_GLOBALHEADER)
		c->flags |= CODEC_FLAG_GLOBAL_HEADER;

    /* open the codec */
    if (avcodec_open2(c, codec, NULL) < 0) {
        and_log_writeline_simple(0, LOG_ERROR, "FFMPEG cannot open video codec");
		return NULL;
    }

	enum AVPixelFormat fmt;
	switch(handle->in_fmt) {
	case AND_PIXEL_FMT_YUV420P:
		fmt = AV_PIX_FMT_YUV420P;
		handle->nFrameSize = c->width * c->height * 3 / 2;
		break;
	case AND_PIXEL_FMT_NV21:
		fmt = AV_PIX_FMT_NV21;
		handle->nFrameSize = c->width * c->height * 3 / 2;
		break;
	case AND_PIXEL_FMT_BGR24:
		fmt = AV_PIX_FMT_BGR24;
		handle->nFrameSize = c->width * c->height * 3;
		break;
	default:
		and_log_writeline_easy(0, LOG_ERROR, "wrong format %d.", handle->in_fmt);
		return NULL;
	};

	handle->img_convert_ctx = sws_getContext(c->width, c->height, fmt,
		c->width, c->height, c->pix_fmt, sws_flags, NULL, NULL, NULL);
	if (handle->img_convert_ctx == NULL)	{
		and_log_writeline_simple(0, LOG_ERROR, "FFMPEG failed to init sws_context");
		return NULL;
	}

    handle->pEncFrame = alloc_picture(c->pix_fmt, c->width, c->height);
    if (!handle->pEncFrame) {
        and_log_writeline_simple(0, LOG_ERROR, "FFMPEG Alloc enc AVFrame error.");
		return NULL;
    }

	handle->pFrame = avcodec_alloc_frame();
	if (!handle->pFrame) {
        and_log_writeline_simple(0, LOG_ERROR, "FFMPEG Alloc tmp AVFrame error.");
		return NULL;
	}

	return st;
}

static AVStream * add_audiostream(ff_encoder_handle *ins)
{
	AVStream*		st		= NULL;
	AVCodecContext*	c		= NULL;
	AVCodec*		codec	= NULL;
	int				ret;

	codec = avcodec_find_encoder(CODEC_ID_AAC);
	if (!codec) {
		and_log_writeline_simple(0, LOG_ERROR, "Could not find audio codec.");
		return NULL;
	}

	st = avformat_new_stream(ins->oc, codec);
	if (!st) {
		and_log_writeline_simple(0, LOG_ERROR, "Could not allocate audio stream.");
		return NULL;
	}

	st->id			= 1;
	c				= st->codec;
	c->codec_id		= CODEC_ID_AAC;
	c->codec_type	= AVMEDIA_TYPE_AUDIO;

	c->sample_rate	=	44100;//44.1kHz
	c->sample_fmt	=	AV_SAMPLE_FMT_S16;
	c->channels		=	1;
	c->bit_rate		=	64000;//64k

	ret = avcodec_open2(c, codec, NULL);
	if (ret < 0) {
		and_log_writeline_easy(0, LOG_ERROR, "FFMPEG cannot open audio codec err:%d, %s", ret, c->codec_name);
		return NULL;
	}

	ins->pAudioFrame = avcodec_alloc_frame();

	ins->pAudioFrame->nb_samples		= c->frame_size;
	ins->pAudioFrame->format			= c->sample_fmt;
	ins->pAudioFrame->channel_layout	= c->channel_layout;

	int len = av_samples_get_buffer_size(NULL, c->channels, 
		c->frame_size, c->sample_fmt, 0);
	if (len < 16) {
		and_log_writeline_easy(0, LOG_ERROR, "failed to calc audio frame buffer len:%d", len);
		return NULL;
	}

	ins->audio_frame_size = c->frame_size * c->channels * 2;
	ins->audio_buf_size = len;
	ins->audio_buf = (uint8_t*)av_mallocz(ins->audio_buf_size);

	ret = avcodec_fill_audio_frame(ins->pAudioFrame, c->channels,
		c->sample_fmt, ins->audio_buf, len, 1);
	if(ret < 0) {
		and_log_writeline_simple(0, LOG_ERROR, "failed to assign sample buffer for audio.");
		return NULL;
	}

	ins->audio_frame_size = c->frame_size * c->channels * 2;
	and_log_writeline_easy(0, LOG_INFO, "audio frame size %d", ins->audio_frame_size);
	return st;
}

static int write_video_frame(ff_encoder_handle *handle, uint8_t* pBuffer, int datalen)
{
	int				got_packet;
	int				ret;
	AVCodecContext*	c = NULL;
	AVPacket		pkt = {0};

	c = handle->video_st->codec;
	av_init_packet(&pkt);
	pkt.pts = pkt.dts = 0;

	if (pBuffer && datalen > 0) {
		if(handle->pic_trans) { //nv21 or bgr24
			enum AVPixelFormat fmt;
			switch(handle->in_fmt) {
			case AND_PIXEL_FMT_NV21:
				fmt = AV_PIX_FMT_NV21;
				break;
			case AND_PIXEL_FMT_BGR24:
				fmt = AV_PIX_FMT_BGR24;
				break;
			default:
				and_log_writeline_easy(0, LOG_ERROR, "wrong format %d.", handle->in_fmt);
				return -1;
			};

			ret = avpicture_fill((AVPicture *)handle->pFrame, pBuffer, fmt, 
				c->width, c->height);
			if(ret < 0) {
				and_log_writeline_simple(0, LOG_ERROR, "failed to fill picture");
				return -1;
			}

			ret = sws_scale(handle->img_convert_ctx, 
				(const uint8_t **)handle->pFrame->data, handle->pFrame->linesize,
				0, handle->height,
				handle->pEncFrame->data, handle->pEncFrame->linesize);
			if(ret != handle->height) {
				and_log_writeline_easy(0, LOG_ERROR, "failed to do sws %d.%d",
					ret, handle->height, handle->pFrame->linesize[0], 
					handle->pEncFrame->linesize[0]);
				return -1;
			}
			and_log_writeline_simple(0, LOG_DEBUG, "scale end.");
		}
		else { //yuv420p
			ret = avpicture_fill((AVPicture *)handle->pEncFrame, pBuffer, AV_PIX_FMT_YUV420P, 
				c->width, c->height);
			if(ret < 0) {
				and_log_writeline_simple(0, LOG_ERROR, "failed to fill picture");
				return -1;
			}
 		}
	}

	handle->pEncFrame->pts = c->frame_number;

	/* prepare a dummy image */
	/* Y */
	/*int x,y;
	static int i=0;
	for(y=0;y<c->height;y++) {
		for(x=0;x<c->width;x++) {
			handle->pEncFrame->data[0][y * handle->pEncFrame->linesize[0] + x] = x + y + i * 3;
		}
	}*/

	/* Cb and Cr */
	/*for(y=0;y<c->height/2;y++) {
		for(x=0;x<c->width/2;x++) {
			handle->pEncFrame->data[1][y * handle->pEncFrame->linesize[1] + x] = 128 + y + i * 2;
			handle->pEncFrame->data[2][y * handle->pEncFrame->linesize[2] + x] = 64 + x + i * 5;
		}
	}
	i++;*/

	ret = avcodec_encode_video2(c, &pkt, handle->pEncFrame, &got_packet);
	if (ret < 0)
	{
		and_log_writeline_simple(0, LOG_ERROR, "failed to encode video");
		return -1;
	}
	handle->in_frames++;

	if (got_packet)
	{
		and_log_writeline_easy(0, LOG_DEBUG, "out pkt_size %d", pkt.size);
		if (c->coded_frame->key_frame)
			pkt.flags |= AV_PKT_FLAG_KEY;
		pkt.stream_index = handle->video_st->index;

		if (pkt.pts != AV_NOPTS_VALUE )
		{
			pkt.pts = av_rescale_q(pkt.pts, c->time_base, handle->video_st->time_base);
		}
		if(pkt.dts != AV_NOPTS_VALUE )
		{
			pkt.dts = av_rescale_q(pkt.dts, c->time_base, handle->video_st->time_base);
		}

		ret = av_interleaved_write_frame(handle->oc, &pkt);//av_interleaved_write_frame av_write_frame
		if ( ret != 0)
		{
			and_log_writeline_easy(0, LOG_ERROR, "failed to write video frame. err:%d", ret);
			av_free_packet(&pkt);
			return -1;
		}
		handle->out_frames++;
	}

	av_free_packet(&pkt);
	return 0;
}

int write_audio_frame(ff_encoder_handle *handle, uint8_t* pBuffer, int datalen)
{
	and_log_writeline_simple(0, LOG_DEBUG, "write_audio_frame");
	
	int got_packet, ret;
	AVPacket pkt = {0};
	av_init_packet(&pkt);

	AVCodecContext* c  = handle->audio_st->codec;
	pkt.pts = pkt.dts = 0;

	and_sysutil_memcpy(handle->audio_buf, pBuffer, handle->audio_buf_size);

	ret = avcodec_encode_audio2(c, &pkt, handle->pAudioFrame, &got_packet);
	if (ret < 0) {
		and_log_writeline_simple(0, LOG_ERROR, "Error encoding audio frame");
		return -1;
	}

	if (got_packet)
	{
		pkt.stream_index = handle->audio_st->index;
		ret = av_interleaved_write_frame(handle->oc, &pkt);
		if ( ret != 0)
		{
			and_log_writeline_easy(0, LOG_ERROR, "failed to write audio frame. err = %d", ret);
			av_free_packet(&pkt);
			return -1;
		}	
	}

	av_free_packet(&pkt);
	return 0;
}

int 
AndCodec_EasyffEncoderAdd(int ffenc, unsigned char *picdata, int picdata_size,
	unsigned char* audiodata, int audiodata_size,
	unsigned char *opaque, int opaque_len)
{
	and_log_writeline_simple(0, LOG_DEBUG, "AndCodec_EasyffEncoderAdd()");

	if(!ffenc) {
		and_log_writeline_simple(0, LOG_ERROR, "ffmpeg encoder handle is null");
		return -1;
	}

	ff_encoder_handle *encoder = (ff_encoder_handle *)ffenc;
	if(!encoder)
		return -1;

	int ret;

	if(picdata && picdata_size > 0) {
		ret = write_video_frame(encoder, picdata, picdata_size);
		if(ret < 0)
			return -1;
	}

	if(audiodata && audiodata_size > 0) {
		and_log_writeline_simple(0, LOG_DEBUG, "start to encode audio");
		unsigned int left = audiodata_size;
		unsigned int offset = 0;
		while(left >= encoder->audio_frame_size) {
			ret = write_audio_frame(encoder, audiodata + offset, 
				encoder->audio_frame_size);
			if(ret < 0)
				return -1;

			offset	+= encoder->audio_frame_size;
			left	-= encoder->audio_frame_size;
		}
		if(offset != audiodata_size) {
			and_log_writeline_easy(0, LOG_WARN, "audio sample not all used %d.%d", 
				offset, audiodata_size);
		}
	}

	return 0;
}

int
AndCodec_EasyffEncoderGet(int ffenc, unsigned char *encdata, 
							  unsigned char *opaque, int *opaque_len)
{
	and_log_writeline_easy(0, LOG_DEBUG, "AndCodec_EasyffEncoderGet()");

	ff_encoder_handle *encoder = (ff_encoder_handle *)ffenc;
	if(!encoder)
		return -1;

	return and_fifo_read(&encoder->fifo, (char *)encdata, GET_MAXSIZE);
}

double
AndCodec_EasyffEncoderGetFPS(int ffenc)
{
	//and_log_writeline_simple(0, LOG_DEBUG, "AndCodec_EasyffEncoderGetFPS()");

	ff_encoder_handle* handle = (ff_encoder_handle *)ffenc;

	double elapsed;
	double fps;

	handle->end_sec		= and_sysutil_get_time_sec();
	handle->end_usec	= and_sysutil_get_time_usec();
	elapsed = (double) (handle->end_sec - handle->start_sec);
	elapsed += (double) (handle->end_usec - handle->start_usec) /
		(double) 1000000;

	if (elapsed <= 0.01)
		elapsed = 0.01f;

	fps = (double)handle->out_frames / elapsed;
	and_log_writeline_easy(0, LOG_DEBUG, "fps: %.2f(%d frames/%.3f sec)",
		fps, handle->out_frames, elapsed);

	return fps;
}

static AVFrame * alloc_picture(enum AVPixelFormat pix_fmt, int width, int height)
{
	AVFrame *picture;
	uint8_t *picture_buf;
	int size;

	picture = avcodec_alloc_frame();
	if (!picture)
		return NULL;
	size = avpicture_get_size(pix_fmt, width, height);
	picture_buf = (uint8_t *)av_mallocz(size);
	if (!picture_buf) {
		av_free(picture);
		return NULL;
	}
	avpicture_fill((AVPicture *)picture, picture_buf,
		pix_fmt, width, height);
	return picture;
}

