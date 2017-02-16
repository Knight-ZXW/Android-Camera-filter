#include "easyencoder.h"
#include "anddefs.h"
#include "andtunables.h"
#include "andparseconf.h"
#include "andsysutil.h"
#include "andstr.h"
#include "andfifobuffer.h"
#include "andqueue.h"
#include "codecdef.h" // for frame type
#include "andtiming_c.h"
#include "utils.h"
#include "apFFMuxer.h"
#ifdef USE_LIBYUV
#include "libyuv.h"
#endif
#define LOG_TAG "easyencoder"
#include "pplog.h"
#include <pthread.h> // for sync
#include <stdio.h> // for vsprintf

#ifdef __ANDROID__
typedef struct fields_t {
	jfieldID    handle; // for save encoder handle
	jfieldID	listener; // for save listener handle
	jmethodID   post_event;
}fields_t;

static long getEncoder(JNIEnv* env, jobject thiz);
static long setEncoder(JNIEnv* env, jobject thiz, long encoder);
#endif

//libx264
#include "stdint.h"
#include "x264.h"

#ifdef ENC_USE_FFMPEG
//ffmpeg
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"
#include "libavutil/avutil.h"
#include "libavutil/imgutils.h"
#endif

#define ENCODER_FIFO_SIZE		1048576
#define ENCODER_FRAMESIZE_LEN	4

#define TEST_IN_WIDTH			640
#define TEST_IN_HEIGHT			480
#define TEST_IN_FMT				2	//0-BRG565, 1-RGB24, 2-NV21, 3-YUV420p

#define SMART_QUALITY_WIDTH	640
#define SMART_QUALITY_HEIGHT	480
#define SMART_NOLATENCY_QUALITY	50

#define DEFAULT_HIGH_BITRATE	400
#define DEFAULT_LOW_BITRATE		200
#define LOW_WIDTH				320

//extern JavaVM *g_JavaVM;

static pthread_mutex_t sLock;
static fields_t fields;

static const uint8_t nalu_header[4] = {0x00, 0x00, 0x00, 0x01};
#define NAL_IDR_SLICE 5

struct easy_encoder_handle
{
	int				id;
	x264_t* 		encoder;		// x264 encoder handle
	x264_param_t 	param;			// x264 encoder param struct
	x264_picture_t 	pic_in;			// x264 encode picture inner buffer
	int				in_frames;		// input frame count
	int				out_frames;		// output frame count
	int				width;
	int				height;
	int				in_fmt;
	FifoBuffer		fifo;			// store encoded stream buffer
	SimpleQueue		queue;			// store opaque data
	long			start_sec, end_sec;
	long			start_usec, end_usec;
	// swsale
	int				pic_trans;		// is need transform input picture pixel format
	uint8_t*		video_src_data[4];
	int				video_src_linesize[4];
	uint8_t*		video_dst_data[4];
	int				video_dst_linesize[4];

	// debug
	int64_t			start_msec;
	int				avg_encode_msec;
#ifdef ENC_USE_FFMPEG
	struct SwsContext*	img_convert_ctx;
#endif
	pthread_mutex_t	mutex;		// sync add() and get()

	// muxer
	int				need_mux;

#ifdef USE_LIBYUV
	// rotate
	uint8*			rotate_data;
#endif

	//...
};

#ifdef __ANDROID__
static int convert_jstring(JNIEnv* env, char *des_str, int* len, jstring str);
#endif

static int encode_pic(easy_encoder_handle* handle, unsigned char* pData, int len, 
	unsigned char* pOpaque, int opaque_len);
static void x264_log(void *user, int level, const char *fmt, va_list vl);

static int calc_bitrate_crf_by_quality(int width, int quality, int *bitrate, int *crf);
static int calc_bitrate_by_resolution(int w, int quality);
static int calc_bitrate_static_by_resolution(int w, int quality);

char * AndCodec_EasyEncoderVersion()
{
	return AND_ENCODER_VERSION;
}

long AndCodec_EasyEncoderOpen(int w, int h, int in_fmt, 
							 const char* profile, const char* str_enc)
{
	PPLOGI("AndCodec_EasyEncoderOpen()");

	//int baseline = 0; // since v1.03 set to 0(false)

	const char *profile_disp[]	= {"low", "high", 
		"smartquality", "nolatency", "static"};// not x264 option
	const char enc_default[]		= "";
	const char* str_profile			= profile;
	const char* str_enc_settings	= str_enc;

	if (!str_profile) {
		PPLOGI("profile is null, use default profile");
		str_profile = profile_disp[1]; // high
	}
	else if (0 == and_sysutil_strlen(str_profile)) {
		PPLOGI("profile is empty, use default preset");
		str_profile = profile_disp[1]; // high
	}

	int found_profile = 0;
	int i = 0;
	
	int profile_num = sizeof( profile_disp ) / sizeof( profile_disp[0] );
	while ( i < profile_num ){
		if (and_sysutil_strcmp(str_profile, profile_disp[i]) == 0) {
			found_profile = 1;		
			break;
		}
		i++;
	}

	if (!found_profile) {
		PPLOGE("input profile is invalid: %s", str_profile);
		return INVALID_HANDLE;
	}

	if (!str_enc_settings) {
		PPLOGI("encode settings is null, use default encode settings: none");
		str_enc_settings = enc_default;
	}
	else if (0 == and_sysutil_strlen(str_enc_settings)) {
		PPLOGI("encode settings is empty, use default encode settings: none");
		str_enc_settings = enc_default;
	}

	PPLOGI("profile %s, enc_settings %s, width %d, height %d, in_fmt %d", 
		str_profile, str_enc_settings, w, h, in_fmt);
	
	easy_encoder_handle* handle = (easy_encoder_handle*)and_sysutil_malloc(sizeof(easy_encoder_handle));
	PPLOGI("easy_encoder handle allocated: addr %p, size %d, x264 param size %d", 
		handle, sizeof(easy_encoder_handle), sizeof(handle->param));
	memset(handle, 0, sizeof(easy_encoder_handle));

	handle->width		= w;
	handle->height		= h;
	handle->in_fmt		= in_fmt;
	handle->in_frames	= 0;
	handle->out_frames	= 0;

	int ret;

#ifdef ENC_USE_FFMPEG
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
		PPLOGE("wrong format %d.", handle->in_fmt);
		return INVALID_HANDLE;
	};

	if (AV_PIX_FMT_YUV420P != fmt) {
		handle->pic_trans = 1;

		ret = av_image_alloc(handle->video_src_data, handle->video_src_linesize, 
			handle->width, handle->height, fmt, 1);
		if(!ret) {
			PPLOGE("failed to alloc src image.");
			return INVALID_HANDLE;
		}

		ret = av_image_alloc(handle->video_dst_data, handle->video_dst_linesize, 
			handle->width, handle->height, AV_PIX_FMT_YUV420P, 1);
		if(!ret) {
			PPLOGE("failed to alloc des image.");
			return INVALID_HANDLE;
		}

		handle->img_convert_ctx = sws_getContext(
			handle->width, handle->height, fmt, 
			handle->width, handle->height, AV_PIX_FMT_YUV420P,
			SWS_FAST_BILINEAR, NULL, NULL, NULL);
		if(!handle->img_convert_ctx) {
			PPLOGE("failed to alloc sws context.");
			return INVALID_HANDLE;
		}
		PPLOGI("transfer = yes");
	}
	else {
		handle->pic_trans = 0;
	}
#else
	if (AND_PIXEL_FMT_YUV420P != handle->in_fmt) {
		PPLOGE("ffmpeg sws was not build-in");
		return INVALID_HANDLE;
	}

	handle->pic_trans = 0;
#endif

	tunables_load_defaults();

	ret = and_parseconf_parse(str_enc_settings);
	if(ret < 0) {
		PPLOGE("failed to parse config");
		return INVALID_HANDLE;
	}

	int bitrate = 0;
	int crf = 0;
	int w_h = handle->width;
	if (handle->height > handle->width)
		w_h = handle->height;

	if (and_sysutil_strcmp(str_profile, profile_disp[0]) == 0 ||
		and_sysutil_strcmp(str_profile, profile_disp[1]) == 0 )
	{
		// calculate bitrate and crf for "low" and "high" profile
		if ( calc_bitrate_crf_by_quality(w_h, tunable_quality, &bitrate, &crf) < 0)
			return INVALID_HANDLE;

		PPLOGI("width %d, quality %d: bitrate %d, crf %d",
			handle->width, tunable_quality, bitrate, crf);
		if (UINT_UNSET == tunable_crf_constant) {
			tunable_crf_constant = crf;
		}
		else {
			PPLOGI("use crf set value: %d", tunable_crf_constant);
		}

		if (UINT_UNSET == tunable_crf_constant_max) {
			tunable_crf_constant_max = crf + 5;
		}
		else {
			PPLOGI("use crf_max set value: %d", tunable_crf_constant_max);
		}

		if (0 == tunable_bitrate) {
			tunable_bitrate = bitrate;
		}
		else {
			PPLOGI("use bitrate set value: %d", tunable_bitrate);
		}
	}

	if (and_sysutil_strcmp(str_profile, profile_disp[0]) == 0) { // low
		PPLOGI("use \"low\" profile");

		x264_param_default_preset(&handle->param, "ultrafast", "zerolatency");
		x264_param_apply_profile(&handle->param, "baseline");

		handle->param.i_threads				= tunable_threads;
		handle->param.i_width				= handle->width;
		handle->param.i_height				= handle->height;
		handle->param.i_fps_num				= tunable_fps_num;
		handle->param.i_fps_den				= tunable_fps_den; //default 1
		//Intra refres:
		handle->param.i_keyint_max			= tunable_keyint_max;
		handle->param.b_intra_refresh		= 1;
		//Rate control:
		handle->param.rc.i_rc_method		= X264_RC_CRF;
		handle->param.rc.f_rf_constant		= (float)tunable_crf_constant;//22
		handle->param.rc.f_rf_constant_max	= (float)tunable_crf_constant_max;//24
		handle->param.rc.i_bitrate			= tunable_bitrate; // kbps
		handle->param.rc.i_vbv_buffer_size	= handle->param.rc.i_bitrate / 2;
		handle->param.rc.i_vbv_max_bitrate	= handle->param.rc.i_bitrate;
		//For streaming:
		handle->param.b_repeat_headers		= 1;
		handle->param.b_annexb				= 1;
	}
	else if(and_sysutil_strcmp(str_profile, profile_disp[1]) == 0) { // high
		PPLOGI("use \"high\" profile");

		//install_str_setting("veryfast", &tunable_preset);
		//install_str_setting("high", &tunable_profile);

		x264_param_default_preset(&handle->param, "veryfast", "zerolatency");

		handle->param.i_threads						= tunable_threads;
		handle->param.i_width						= handle->width;
		handle->param.i_height						= handle->height;
		handle->param.i_fps_num						= tunable_fps_num;
		handle->param.i_fps_den						= tunable_fps_den;

		handle->param.i_keyint_max					= tunable_keyint_max;
		handle->param.b_intra_refresh				= 0; // 1 cause long p frame seq warn: "ref > 1 + intra-refresh is not supported"
		handle->param.i_frame_reference				= 3; // 4
		handle->param.i_bframe						= 0;
		handle->param.b_cabac						= 0; // 1
		handle->param.b_open_gop					= 0; // 1

		//Rate control:
		handle->param.rc.i_rc_method				= X264_RC_CRF;
		handle->param.rc.f_rf_constant				= (float)tunable_crf_constant; // 22
		handle->param.rc.f_rf_constant_max			= (float)tunable_crf_constant_max; // 24
		handle->param.rc.i_bitrate					= bitrate; // kbps
		handle->param.rc.i_vbv_buffer_size			= handle->param.rc.i_bitrate / 2;
		handle->param.rc.i_vbv_max_bitrate			= handle->param.rc.i_bitrate;
		//For streaming:
		handle->param.b_repeat_headers				= 1;
		handle->param.b_annexb						= 1;

	}
	else if(and_sysutil_strcmp(str_profile, profile_disp[2]) == 0) { // smartquality
		PPLOGI("use \"smartquality\" profile");

		x264_param_default_preset(&handle->param, "veryfast", "zerolatency"); 	

		handle->param.i_threads						= tunable_threads;
		handle->param.i_width						= handle->width;
		handle->param.i_height						= handle->height;
		handle->param.i_fps_num						= tunable_fps_num;
		handle->param.i_fps_den						= tunable_fps_den;

		int correct_res = 0;
		if ( (handle->width == SMART_QUALITY_WIDTH && handle->height == SMART_QUALITY_HEIGHT) || 
			(handle->width == SMART_QUALITY_HEIGHT && handle->height == SMART_QUALITY_WIDTH) )
			correct_res = 1;
			
		if (!correct_res) {
			PPLOGE(
				"\"smartquality\" profile only support 640x480 resolution");
			return INVALID_HANDLE;
		}

		bitrate	= calc_bitrate_by_resolution(SMART_QUALITY_WIDTH, tunable_quality);
		if (bitrate < 0) {
			PPLOGE("failed to get bitrate");
			return INVALID_HANDLE;
		}

		if (0 == tunable_bitrate) {
			tunable_bitrate = bitrate;
		}
		else {
			PPLOGI("use bitrate set value: %d", tunable_bitrate);
		}

		handle->param.i_keyint_min					= tunable_fps_num / tunable_fps_den;
		handle->param.i_keyint_max					= tunable_fps_num * 2 / tunable_fps_den;

		handle->param.i_bframe						= 16;
		handle->param.i_bframe_adaptive				= 1;
		handle->param.i_bframe_pyramid				= 1;

		handle->param.i_frame_reference				= 3; // 4
		handle->param.b_cabac						= 1;

		//bitrate
		handle->param.rc.i_rc_method					= X264_RC_ABR;
		handle->param.rc.i_bitrate					= tunable_bitrate;
		handle->param.rc.f_ip_factor					= 4.0f;
		handle->param.rc.f_pb_factor					= 1.9f;

		//anaylse
		handle->param.analyse.b_mixed_references    = 1;
		handle->param.analyse.b_transform_8x8       = 1;
		handle->param.analyse.i_me_method           = X264_ME_HEX;
		handle->param.analyse.i_me_range            = 16;
		handle->param.analyse.i_subpel_refine       = 1;
		handle->param.analyse.b_psy                 = 0;
		handle->param.analyse.i_trellis             = 1;
	}
	else if(and_sysutil_strcmp(str_profile, profile_disp[3]) == 0) { // nolatency
		PPLOGI("use \"nolatency\" profile");

		x264_param_default_preset(&handle->param, "ultrafast", "zerolatency"); 	

		bitrate	= calc_bitrate_static_by_resolution(w_h, tunable_quality);
		if (bitrate < 0) {
			PPLOGE("failed to get bitrate");
			return INVALID_HANDLE;
		}

		if (0 == tunable_bitrate) {
			tunable_bitrate = bitrate;
		}
		else {
			PPLOGI("use bitrate set value: %d", tunable_bitrate);
		}

		handle->param.i_threads				= tunable_threads;
		handle->param.i_width				= handle->width;
		handle->param.i_height				= handle->height;
		handle->param.i_fps_num				= tunable_fps_num;
		handle->param.i_fps_den				= tunable_fps_den;
		handle->param.i_keyint_max			= tunable_keyint_max;

		handle->param.rc.i_bitrate			= tunable_bitrate / 1000; // kbps
		handle->param.rc.i_rc_method			= X264_RC_ABR;
		handle->param.rc.i_vbv_buffer_size	= tunable_bitrate / 1000;
		handle->param.rc.i_vbv_max_bitrate	= tunable_bitrate / 1000 * 2;
	}
	else if (and_sysutil_strcmp(str_profile, profile_disp[4]) == 0) { // static
		PPLOGI("use \"static\" profile");

		x264_param_default_preset(&handle->param, "ultrafast", "zerolatency"); 	

		bitrate	= calc_bitrate_static_by_resolution(w_h, tunable_quality);
		if (bitrate < 0) {
			PPLOGE("failed to get bitrate");
			return INVALID_HANDLE;
		}

		if (0 == tunable_bitrate) {
			tunable_bitrate = bitrate;
		}
		else {
			PPLOGI("use bitrate set value: %d", tunable_bitrate);
		}

		handle->param.i_threads				= tunable_threads;
		handle->param.i_width				= handle->width;
		handle->param.i_height				= handle->height;
		handle->param.i_fps_num				= tunable_fps_num;
		handle->param.i_fps_den				= tunable_fps_den;
		handle->param.i_keyint_max			= 1;
		handle->param.i_keyint_min			= 1;

		handle->param.rc.i_rc_method			= X264_RC_ABR;
		handle->param.rc.i_bitrate			= tunable_bitrate;
		handle->param.rc.f_rate_tolerance	= 0.1f;
		handle->param.b_intra_refresh		= 1;
	}
	else {
		PPLOGE("unknown profile: %s", str_profile);
		return INVALID_HANDLE;
	}

	//log
	handle->param.pf_log					= x264_log;
	handle->param.i_log_level 			= X264_LOG_INFO;

	//After this you can initialize the encoder as follows
	handle->encoder = x264_encoder_open(&handle->param);
	if (!handle->encoder) {
		PPLOGE("failed to open encoder");
		return INVALID_HANDLE;
	}
	
	ret = x264_picture_alloc(&handle->pic_in, X264_CSP_I420, w, h);
	if (0 != ret) {
		PPLOGE("failed to alloc picture");
		return INVALID_HANDLE;
	}
	handle->pic_in.opaque = (filesize_t *)and_sysutil_malloc(sizeof(filesize_t));
	
	ret = and_fifo_create(&handle->fifo, ENCODER_FIFO_SIZE);
	if (ret < 0) {
		PPLOGE("failed to create fifo");
		return INVALID_HANDLE;
	}
	
	ret = and_queue_init(&handle->queue, OPAQUE_DATA_LEN, QUEUE_SIZE);
	if (ret < 0) {
		PPLOGE("failed to create queue");
		return INVALID_HANDLE;
	}

	ret = pthread_mutex_init(&handle->mutex, 0);
	if (ret < 0) {
		PPLOGE("failed to create mutex");
		return INVALID_HANDLE;
	}
	
	// start to calculate encode time
	handle->start_sec	= and_sysutil_get_time_sec();
	handle->start_usec	= and_sysutil_get_time_usec();

	// debug
	handle->avg_encode_msec = 0;
	handle->start_msec = getNowMs();

	handle->need_mux = 0;

	PPLOGI("open encoder handle %p, encoder %p", 
		handle, handle->encoder);
	
	return (long)handle;
}

int AndCodec_EasyEncoderHeaders(long enc, unsigned char * headers, int *len)
{
	if (!enc) {
		PPLOGE("encoder handle is null");
		return -1;
	}
	if (!headers) {
		PPLOGE("headers data is null");
		return -1;
	}

	easy_encoder_handle* handle = (easy_encoder_handle *)enc;
	x264_nal_t * nals = NULL;
	int i_nals = 0;
	int outsize = x264_encoder_headers(handle->encoder, &nals, &i_nals);
	if (outsize <= 0) {
		PPLOGE("failed to get sps and pps");
		return -1;
	}

	if (*len <= outsize) {
		PPLOGE("sps and pps buffer is too small");
		return -1;
	}

	// remove IDR 5
	int32_t last_nalu_start = -1;
	int32_t IDR_frame_pos = -1;
	int32_t offset;
	for (offset = 0; offset < outsize; offset++ ) {
		if (memcmp(nals[0].p_payload + offset, nalu_header, 4) == 0 || 
			memcmp(nals[0].p_payload + offset, nalu_header + 1, 3) == 0 || 
			offset == outsize - 1) {
			//LOGI("find start code: %d", offset);

			if (last_nalu_start != -1) {
				uint8_t* pNAL = NULL;
				//int32_t sizeNAL = 0;

				// 00 00 00 00 xx data ...
				pNAL = nals[0].p_payload + last_nalu_start;
				//sizeNAL = offset - last_nalu_start;

				int nalType = pNAL[4] & 0x1f;
				if (nalType == NAL_IDR_SLICE) {
					IDR_frame_pos = last_nalu_start;
					break;
				}
			}

			last_nalu_start = offset;

			if (memcmp(nals[0].p_payload + offset, nalu_header, 4) == 0)
				offset += 3; // +1 by for()
			else
				offset += 2; // +1 by for()
		}
	}

	if (IDR_frame_pos != -1)
		outsize = IDR_frame_pos;

	memcpy(headers, nals[0].p_payload, outsize);
	*len = outsize;
	return outsize;
}

void AndCodec_EasyEncoderSetRotate(long enc, int rotate)
{
	PPLOGI("AndCodec_EasyEncoderSetRotate %d", rotate);

	easy_encoder_handle* handle = (easy_encoder_handle *)enc;	

	pthread_mutex_lock(&handle->mutex);
	if (rotate == tunable_rotate) {
		PPLOGI("rotate value NOT changed");
	}
	else if (rotate == 90 || rotate == 180 || rotate == 270) {
		tunable_rotate = rotate;
	}
	else {
		PPLOGW("invalid rotate value set %d", rotate);
	}

	pthread_mutex_unlock(&handle->mutex);
}

int AndCodec_EasyEncoderAdd(long enc, unsigned char* picdata, int picdata_size, unsigned char* opaque, int opaque_len)
{
	PPLOGD("AndCodec_EasyEncoderAdd opaque_len: %d", opaque_len);

	if(!enc) {
		PPLOGE("encoder handle is null");
		return -1;
	}
	if(!picdata) {
		PPLOGE("picture data is null");
		return -1;
	}

	easy_encoder_handle* handle = (easy_encoder_handle *)enc;
	return encode_pic(handle, picdata, picdata_size, opaque, opaque_len);
}


int AndCodec_EasyEncoderGet(long enc, unsigned char* encdata, int encdata_max_len,
							unsigned char* opaque, int *opaque_len)
{
	if (!enc) {
		PPLOGE("encoder handle is null");
		return -1;
	}
	if (!encdata || encdata_max_len == 0) {
		PPLOGE("encoded data is null");
		return -1;
	}

	easy_encoder_handle* handle = (easy_encoder_handle *)enc;	

	pthread_mutex_lock(&handle->mutex);

	int readed = -1;
	int frame_size;
	int ret;
	OpaqueData opaque_data;
	
	if (and_fifo_used(&handle->fifo) < ENCODER_FRAMESIZE_LEN) {
		readed = 0;
		goto exit;
	}

	readed = and_fifo_read(&handle->fifo, (char *)&frame_size, ENCODER_FRAMESIZE_LEN);
	if (frame_size > encdata_max_len) {
		readed = -1;
		PPLOGE("enc data buffer is too small %d %d", frame_size, encdata_max_len);
		goto exit;
	}
	readed = and_fifo_read(&handle->fifo, (char *)encdata, frame_size);
	if (readed < frame_size) {
		PPLOGE("frame data is corrupt %d.%d", frame_size, readed);
		readed = -1;
		goto exit;
	}

	ret = and_queue_get(&handle->queue, (void *)&opaque_data);
	if (ret < 0) {
		readed = -1;
		goto exit;
	}

	// add frame info
	// 00 00 00 01 nalu_header
	int nType = encdata[4] & 0x1F;
	if ( nType <= H264NT_PPS ) {
		switch(nType) {
		case H264NT_SLICE_IDR:
		case H264NT_SPS: // b_repeat_headers=1 libx264 add sps and pps before each IDR frame
			opaque_data.uchar_d0 = 1; //I frames
			break;
		case H264NT_SLICE:
			opaque_data.uchar_d0 = 2; //P frames
			break;
		case H264NT_SLICE_DPA:
		case H264NT_SLICE_DPB:
		case H264NT_SLICE_DPC:
			opaque_data.uchar_d0 = 3; //B frames
			break;
		default:
			opaque_data.uchar_d0 = 0; //unknown frames
			break;
		}
	}
	else {
		PPLOGE("h264 encoded data was corrupted %d", nType);
		readed = -1;
		goto exit;
	}
		
	and_sysutil_memcpy(opaque, (void *)&opaque_data, OPAQUE_DATA_LEN);
	if (opaque_len) {
		*opaque_len = OPAQUE_DATA_LEN;
	}

exit:
	pthread_mutex_unlock(&handle->mutex);
	return readed;
}

void AndCodec_EasyEncoderClose(long enc)
{
	PPLOGI("AndCodec_EasyEncoderClose()");

	easy_encoder_handle* handle = (easy_encoder_handle *)enc;	
	PPLOGI("encoder handle %x, encoder %x", handle, handle->encoder);

	if (handle->encoder) {
		int64_t duration = getNowMs() - handle->start_msec;
		PPLOGI("encode %d frames, out %d frames, duration %lld msec, enc_avg_time %d msec", 
			handle->in_frames, handle->out_frames, duration, handle->avg_encode_msec);

		PPLOGI("EasyEncoderClose before encoder close.");
		x264_encoder_close(handle->encoder);

		PPLOGI("EasyEncoderClose. before pic free.");
		if(handle->pic_in.opaque)
			and_sysutil_free(handle->pic_in.opaque);
		x264_picture_clean(&handle->pic_in);

		PPLOGI("EasyEncoderClose. before fifo & queue free.");
		and_fifo_close(&handle->fifo);
		and_queue_close(&handle->queue);
		PPLOGI("EasyEncoderClose. after fifo & queue free.");

#ifdef ENC_USE_FFMPEG
		if (handle->img_convert_ctx)
			sws_freeContext(handle->img_convert_ctx);
		if (handle->video_src_data[0])
			av_free(handle->video_src_data[0]);
		if (handle->video_dst_data[0])
			av_free(handle->video_dst_data[0]);
#endif

#ifdef USE_LIBYUV
		if (handle->rotate_data) {
			free(handle->rotate_data);
			handle->rotate_data = NULL;
		}
#endif

		pthread_mutex_destroy(&handle->mutex);
	}
	and_sysutil_free(handle);

	PPLOGI("EasyEncoder Closed");
}

double AndCodec_EasyEncoderGetFPS(long enc)
{
	PPLOGD("AndCodec_EasyEncoderGetFPS()");

	easy_encoder_handle* handle = (easy_encoder_handle *)enc;

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
	PPLOGD("fps: %.2f(%d frames/%.3f sec)",
		fps, handle->out_frames, elapsed);

	return fps;
}

#ifdef __ANDROID__
jboolean 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderOpen(JNIEnv* env, jobject thiz,
													int w, int h, int in_fmt, 
													jstring profile, jstring enc_str)
{
	PPLOGI("EasyEncoderOpen()");

	pthread_mutex_init(&sLock, NULL);

	jclass clazzEncoder = (*env)->FindClass(env, "com/gotye/sdk/EasyEncoder");
	if (clazzEncoder == NULL) {
		PPLOGE("failed to find class com/gotye/sdk/EasyEncoder");
		jclass exceptionClass = (*env)->FindClass(env, "java/lang/RuntimeException");
		(*env)->ThrowNew(env, exceptionClass, "failed to find class com/gotye/sdk/EasyEncoder");
		return JNI_FALSE;
	}

	fields.handle = (*env)->GetFieldID(env, clazzEncoder, "mHandle", "J");
	if (fields.handle == NULL) {
		PPLOGE("failed to get mHandle FieldID");
		jclass exceptionClass = (*env)->FindClass(env, "java/lang/RuntimeException");
		(*env)->ThrowNew(env, exceptionClass, "failed to get mHandle FieldID");
		return JNI_FALSE;
	}
	
	//parse input and output filename
	char str_profile[256]	= {0};//preset
	char str_enc[256]		= {0};
	int str_len 			= 256;
	
	convert_jstring(env, str_profile, &str_len, profile);
	convert_jstring(env, str_enc, &str_len, enc_str);
	
	long handle = AndCodec_EasyEncoderOpen(w, h, in_fmt, str_profile, str_enc);
	if (handle == INVALID_HANDLE)
		return JNI_FALSE;

	setEncoder(env, thiz, handle);
	PPLOGI("EasyEncoderOpen done!");
	return JNI_TRUE;
}

jint
Java_com_gotye_sdk_EasyEncoder_EasyEncoderHeaders(JNIEnv* env, jobject thiz, 
												jobject headers)
{
	jbyte* p_headers = (*env)->GetByteArrayElements(env, headers, NULL);
	jsize data_size = (*env)->GetArrayLength(env, headers);

	long handle = getEncoder(env, thiz);
	int n = AndCodec_EasyEncoderHeaders(handle, (unsigned char *)p_headers, &data_size);
	(*env)->ReleaseByteArrayElements(env, headers,  p_headers, 0);
	return n;
}

void
Java_com_gotye_sdk_EasyEncoder_EasyEncoderSetRotate(JNIEnv* env, jobject thiz, 
												int rotate)
{
	long handle = getEncoder(env, thiz);
	AndCodec_EasyEncoderSetRotate(handle, rotate);
}

jint 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderAdd(JNIEnv* env, jobject thiz,
												jobject picdata, jobject opaque)
{
	PPLOGD("EasyEncoderAdd()");

	jbyte* p_pic = (*env)->GetByteArrayElements(env, picdata, NULL);
	jsize pic_size = (*env)->GetArrayLength(env, picdata);

	jbyte* p_opaque =  (*env)->GetByteArrayElements(env, opaque, NULL);
	
	jsize opaque_len = (*env)->GetArrayLength(env, opaque);

	long handle = getEncoder(env, thiz);
	int n =	AndCodec_EasyEncoderAdd(handle, (unsigned char *)p_pic, pic_size,
		(unsigned char *)p_opaque, (int)opaque_len);

	(*env)->ReleaseByteArrayElements(env, picdata,  p_pic, 0);
    (*env)->ReleaseByteArrayElements(env, opaque,  p_opaque, 0);
	return n;
}

jint 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderGet(JNIEnv* env, jobject thiz,
												jobject encdata, jobject opaque)
{
	jbyte* p_data = (*env)->GetByteArrayElements(env, encdata, NULL);
	jsize data_size = (*env)->GetArrayLength(env, encdata);

	jbyte* p_opaque =  (*env)->GetByteArrayElements(env, opaque, NULL);
	int opaque_len = (*env)->GetArrayLength(env, opaque);

	long handle = getEncoder(env, thiz);
	int n = AndCodec_EasyEncoderGet(handle, (unsigned char *)p_data, data_size,
		(unsigned char *)p_opaque, &opaque_len);

	(*env)->ReleaseByteArrayElements(env, encdata,  p_data, 0);
	(*env)->ReleaseByteArrayElements(env, opaque, p_opaque, 0);
	return n;
}

void 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderSetMuxer(JNIEnv* env, jobject thiz, 
												jlong muxer)
{
	PPLOGI("EasyEncoderSetMuxer() %d", muxer);

	easy_encoder_handle* handle = (easy_encoder_handle *)getEncoder(env, thiz);
	handle->need_mux = (muxer != 0);
	enc_set_muxer(muxer);
}

void 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderClose(JNIEnv* env, jobject thiz)
{
	PPLOGI("EasyEncoderClose()");

	long handle = getEncoder(env, thiz);
	AndCodec_EasyEncoderClose(handle);

	setEncoder(env, thiz, INVALID_HANDLE);
	pthread_mutex_destroy(&sLock);

	PPLOGI("EasyEncoderClose() done");
}

jdouble 
Java_com_gotye_sdk_EasyEncoder_EasyEncoderGetFPS(JNIEnv* env, jobject thiz)
{
	long handle = getEncoder(env, thiz);
	return AndCodec_EasyEncoderGetFPS(handle);
}

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

static int convert_jstring(JNIEnv* env, char *des_str, int* len, jstring str)
{
	const char *nativeString = (*env)->GetStringUTFChars(env, str, 0);     
	and_sysutil_strcpy(des_str, nativeString, *len);
	(*env)->ReleaseStringUTFChars(env, str, nativeString);
	
	return 0;
}

#endif

static int encode_pic(easy_encoder_handle* handle, unsigned char* pData, int len, 
	unsigned char* pOpaque, int opaque_len)
{
	PPLOGD("encode_pic()");
	
	pthread_mutex_lock(&handle->mutex);
	
	(void)opaque_len;
	int factor;
	int offset;
	int to_read;
	int written;
	x264_nal_t* nals;
	int i_nals;
	int out_size = -1;
	x264_picture_t* p_pic;
	x264_picture_t	picout;
	int j;

	if (pData && len > 0) {//fix me
		if (handle->pic_trans) { //nv21 or bgr24
#ifndef ENC_USE_FFMPEG
			PPLOGE("failed to do sws, ffmpeg was not build-in.");
			goto exit;
#else
			PPLOGD("start to do sws.");
			switch (handle->in_fmt) {
			case AND_PIXEL_FMT_NV21:
				and_sysutil_memcpy(handle->video_src_data[0], pData, 
					handle->video_src_linesize[0] * handle->height);
				and_sysutil_memcpy(handle->video_src_data[1], 
					pData + handle->video_src_linesize[0] * handle->height, 
					handle->video_src_linesize[1] * handle->height / 2);
				break;
			case AND_PIXEL_FMT_BGR24:
				and_sysutil_memcpy(handle->video_src_data[0], pData, 
					handle->video_src_linesize[0] * handle->height);
				break;
			default:
				PPLOGE("wrong pixel fmt %d to do sws", handle->in_fmt);
				goto exit;
			}

			int ret = sws_scale(handle->img_convert_ctx, 
				(uint8_t const**)handle->video_src_data, handle->video_src_linesize,
				0, handle->height,
				handle->video_dst_data, handle->video_dst_linesize);
			if(ret != handle->height) {
				PPLOGE("failed to do sws %d.%d",
					ret, handle->height, handle->video_src_linesize[0], 
					handle->video_dst_linesize[0]);
				goto exit;
			}
			PPLOGD("scale end.");
			
			// alway copy to yuv420p x264 pic struct
			for(j=0; j<3 ;j++) {
				if(0 == j)
					factor = 1;
				else
					factor = 2;
				to_read = handle->video_dst_linesize[j] * handle->height / factor;
				and_sysutil_memcpy(handle->pic_in.img.plane[j], handle->video_dst_data[j], to_read);
			}
#endif
		}
		else { //yuv420p
#ifdef USE_LIBYUV
			// rotate
			if (tunable_rotate != 0) {
				int src_width = handle->width;
				int src_height = handle->height;
				if (tunable_rotate == 90 || tunable_rotate == 270) {
					src_width = handle->height;
					src_height = handle->width;
				}

				int dst_i420_size = handle->width * handle->height * 3 / 2;
				if (!handle->rotate_data) {
					handle->rotate_data = (uint8 *)malloc(dst_i420_size);
					if (handle->rotate_data == NULL) {
						PPLOGE("failed to malloc rotate data");
						goto exit;
					}
				}

				const uint8 *src_y = pData;
				int src_stride_y = src_width;
				const uint8 *src_u = src_y + src_stride_y * src_height;
				int src_stride_u = src_width / 2;
				const uint8 *src_v = src_u + src_stride_u * src_height / 2;
				int src_stride_v = src_width / 2;

				uint8 *dst_y = handle->rotate_data;
				int dst_stride_y = handle->pic_in.img.i_stride[0];
				uint8 *dst_u = dst_y + handle->pic_in.img.i_stride[0] * handle->height;
				int dst_stride_u = handle->pic_in.img.i_stride[1];
				uint8 *dst_v = dst_u + handle->pic_in.img.i_stride[1] * handle->height / 2;
				int dst_stride_v = handle->pic_in.img.i_stride[2];

				int rotate_result = I420Rotate(
					src_y, src_stride_y,
					src_u, src_stride_u,
					src_v, src_stride_v,
					dst_y, dst_stride_y,
					dst_u, dst_stride_u,
					dst_v, dst_stride_v,
					src_width, src_height, tunable_rotate);
				if (rotate_result < 0) {
					PPLOGE("failed to do rotate");
					goto exit;
				}

				memcpy(pData, handle->rotate_data, dst_i420_size);
			}
#endif
			// fill img data
			offset = 0;
			for (j=0;j<3;j++) {
				if (0 == j)
					factor = 1;
				else
					factor = 2;
				to_read = handle->pic_in.img.i_stride[j] * handle->height / factor;
				if (offset + to_read > len) {
					PPLOGE("image data length is invalid #%d %d.%d", 
						handle->in_frames, offset + to_read, len);
				}
				and_sysutil_memcpy(handle->pic_in.img.plane[j], pData + offset, to_read);
				offset += to_read;
			}
		}

		p_pic = &handle->pic_in;
	}
	else { // encode flush case
		p_pic = 0;
	}
	
	PPLOGD("after copy data");

	int64_t from = getNowMs();
	out_size = x264_encoder_encode(handle->encoder, &nals, &i_nals, p_pic, &picout);
	int64_t encode_msec = getNowMs() - from;
	handle->avg_encode_msec = (handle->avg_encode_msec * 4 + encode_msec) / 5;

	if (out_size < 0) {
		PPLOGE("failed to encode in #%d, out_size %d", 
			handle->in_frames, out_size);
		out_size = -1;
		goto exit;
	}

	PPLOGD("encode #%d", handle->in_frames);
	handle->in_frames++;
	
	if (out_size > 0) {
		if (handle->need_mux) {
			OpaqueData *opaque_data = (OpaqueData *)pOpaque;
			enc_write_frame(1, nals[0].p_payload, out_size, opaque_data->ll_d0);
		}
		else {
			written = and_fifo_write(&handle->fifo, (char *)&out_size, ENCODER_FRAMESIZE_LEN);
			if (written != ENCODER_FRAMESIZE_LEN) {
				PPLOGE("fifo overflowed #%d %d.%d", 
					handle->out_frames, ENCODER_FRAMESIZE_LEN, written);
				out_size = -1;
				goto exit;
			}
		
			written = and_fifo_write(&handle->fifo, (char *)nals[0].p_payload, out_size);
			if (written < out_size) {
				PPLOGW("fifo overflowed #%d %d.%d", 
					handle->out_frames, out_size, written);
			}
			if (and_queue_put(&handle->queue, (void *)pOpaque) <0)
				goto exit;
		
			PPLOGD("dump out[%d] size %d, nal %d", 
				handle->out_frames, written, i_nals);
			handle->out_frames++;
		}
	}
	else {
		PPLOGW("no data out in #%d", handle->in_frames);
	}

exit:
	pthread_mutex_unlock(&handle->mutex);
	return out_size;
}

static void x264_log(void *user, int level, const char *fmt, va_list vl)
{
	(void)user;
	static char szLog[STRING_BUF_LEN] = {0};

	vsprintf(szLog, fmt, vl);

	switch(level) {
		case X264_LOG_ERROR:
			PPLOGE("x264(%d): %s", level, szLog);
			break;
		case X264_LOG_WARNING:
			PPLOGW("x264(%d): %s", level, szLog);
			break;
		case X264_LOG_INFO:
			PPLOGI("x264(%d): %s", level, szLog);
			break;
		case X264_LOG_DEBUG:
			PPLOGD("x264(%d): %s", level, szLog);
			break;
		default:
			PPLOGD("x264(%d): %s", level, szLog);
			break;
	}
}

static int calc_bitrate_crf_by_quality(int width, int quality, int *bitrate, int *crf)
{
	if( quality <= 0 || quality > 100) {
		PPLOGE("invalid quality input: %d", quality);
		return -1;
	}

	//profile #24    320x240
	const int qvga_bitrate[]  = { 100, 200, 400, 800 };      //1600 upper limit
	const int qvga_crf_base[] = { 24, 20, 15, 11 };   

	//profile #25   640x480
	const int vga_bitrate[]   = { 200, 400, 800, 1600 };    //3200 upper limit
	//const int vga_crf_base[]  = { 26, 21, 18, 14 };
	const int vga_crf_base[]  = { 26, 21, 18, 14 };

	const int level_count     = 4;
	const int bitrate_level[] = { 25, 50, 75, 100 };

	int sub_quality = 0;
	int i;
	for (i = 0; i < level_count; i++) {
		if(quality <= bitrate_level[i])
		{
			//sub_quality = (bitrate_level[i] - quality) * 3 / 5 ;
			sub_quality = (bitrate_level[i] - quality) * 9 / 25 ;

			if(width == 320) //profile #24
			{
				*bitrate =  qvga_bitrate[i];
				*crf     = sub_quality + qvga_crf_base[i];
			}
			else if(width == 640) //profile #25
			{
				*bitrate = vga_bitrate[i];
				*crf     = sub_quality + vga_crf_base[i];
			}
			else   //720p
			{
				*bitrate = 4000;
				*crf     = 23;
			}

			break;
		}
	}

	return 0;
}

static int calc_bitrate_by_resolution(int w, int quality)
{
	int bitrate = 0;

	if (w != SMART_QUALITY_WIDTH) {
		PPLOGE("only support for 640x480: %d", w);
		return -1;
	}

	if (quality == 50)
		bitrate				= 650;
	else if (quality < 50)
		bitrate				= (int)((650 - 400) / 48 * (quality - 1)) + 400;
	else
		bitrate				= (int)((2000 - 650) / 49 * (quality - 51)) + 650;

	PPLOGI("no latency: input width %d, quality %d, bitrate %d", 
		w, quality, bitrate);
	return bitrate;
}

static int calc_bitrate_static_by_resolution(int w, int quality)
{
	int bitrate = 0;

	if (w <= 320) { // 25-128k bps, 40-256k
		bitrate = 850  * quality / 100 - 85;
		if(bitrate < 100)
			bitrate = 100;
	}
	else if (w <= 640){ // 200k - 1.6M bps
		bitrate = 1400 * quality / 100 + 200;
	}
	else {	// 800k - 4M bps
		bitrate = 3200 * quality / 100 + 800;
	}

	PPLOGI("static: input width %d, quality %d, bitrate %d", 
		w, quality, bitrate);
	return bitrate;
}




