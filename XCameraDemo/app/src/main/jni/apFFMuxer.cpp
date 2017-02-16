#include "apFFMuxer.h"
#include "autolock.h"
#include "utils.h"
#include <jni.h>
#include <stdio.h>
#define LOG_TAG "FFMuxer"
#include "pplog.h"

extern "C" {
#include "NAL.h"
#include "andfifobuffer.h"
}

extern JavaVM* g_JavaVM;

#define FIFO_SIZE (1048576 * 8) // 8M

apFFMuxer *gs_muxer = NULL;

void enc_set_muxer(long muxer)
{
	gs_muxer = (apFFMuxer *)muxer;
}

int enc_set_sps_pps(uint8_t* sps_pps, int size)
{
	if (gs_muxer && gs_muxer->set_sps_pps(sps_pps, size))
		return 0;
	
	return -1;
}

int enc_write_frame(int is_video, uint8_t* data, int size, int64_t timestamp/* usec */)
{
	if (gs_muxer && gs_muxer->write_frame(is_video > 0 ? true : false, data, size, timestamp))
		return 0;

	return -1;
}

#define MAX_DATA_LEN 262144 // 256k
#define VERBOSE 0

static void dump_hex(const char *name, unsigned char *data, int len);

static const uint8_t nalu_header[4] = {0x00, 0x00, 0x00, 0x01};

void apFFMuxer::ff_log(void* user, int level, const char* fmt, va_list vl)
{
	char szLog[2048] = {0};
	vsprintf(szLog, fmt, vl);

	if (strstr(szLog, "first_dts") != NULL)
		return;

	switch (level) {
		case AV_LOG_PANIC:
		case AV_LOG_FATAL:
		case AV_LOG_ERROR:
			PPLOGE("ffmpeg(%d) %s", level, szLog);
			break;
		case AV_LOG_WARNING:
            PPLOGW("ffmpeg(%d) %s", level, szLog);
			break;
		case AV_LOG_INFO:
            PPLOGI("ffmpeg(%d) %s", level, szLog);
			break;
		case AV_LOG_DEBUG:
            PPLOGD("ffmpeg(%d) %s", level, szLog);
			break;
		case AV_LOG_VERBOSE:
		case AV_LOG_TRACE:
			PPLOGV("ffmpeg(%d) %s", level, szLog);
			break;
		default:
			PPLOGI("ffmpeg(%d) %s", level, szLog);
			break;
	}
}

apFFMuxer::apFFMuxer(void)
	:m_out_fmt_ctx(NULL), m_out_fmt(NULL), 
	m_video_stream(NULL), m_audio_stream(NULL), 
	m_video_stream_idx(-1), m_audio_stream_idx(-1),
	m_data(NULL), m_header_written(false),
	m_nalu_type(H264_STREAM_FMT_ANNEX),
	m_sps_data(NULL), m_sps_len(0),
	m_pps_data(NULL), m_pps_len(0),
	m_width(0), m_height(0), m_video_bitrate(0), m_framerate(0),
	m_audio_sample_rate(0), m_audio_channels(0), m_audio_bitrate(0),
	m_pBsfc_aac(NULL), m_stream_start_msec(0), m_fifo(NULL),
	m_stopping(0), m_dropframe(0), m_drop_video_frames(0),
	m_async_write_video_err(0), m_async_write_audio_err(0)
{
	av_register_all();
	avformat_network_init();

	av_log_set_level(AV_LOG_INFO);
	av_log_set_callback(ff_log);

	pthread_mutex_init(&m_lock, NULL);

	pthread_mutex_init(&m_mutex, NULL);
	pthread_cond_init(&m_cond, NULL);
}


apFFMuxer::~apFFMuxer(void)
{
	if (m_data) {
		av_free(m_data);
		m_data = NULL;
	}

	avformat_network_deinit();

	pthread_mutex_destroy(&m_lock);

	pthread_mutex_destroy(&m_mutex);
	pthread_cond_destroy(&m_cond);
}

bool apFFMuxer::open(char *url)
{
	PPLOGI("open() dump_url: %s", url);
	int ret;
	
	if (strncmp(url, "rtmp://", 7) == 0 || strncmp(url, "udp://", 6) == 0 || 
		strncmp(url, "rtsp://", 7) == 0) {
		if (strncmp(url, "rtmp://", 7) == 0)
			ret = avformat_alloc_output_context2(&m_out_fmt_ctx, NULL, "flv", url);
		else if (strncmp(url, "udp://", 6) == 0)
			ret = avformat_alloc_output_context2(&m_out_fmt_ctx, NULL, "mpegts", url);
		else
			ret = avformat_alloc_output_context2(&m_out_fmt_ctx, NULL, "rtsp", url);
		if (ret < 0) {
			PPLOGE("Could not create output context");
			return false;
		}

		m_out_fmt = m_out_fmt_ctx->oformat;
		//m_out_fmt->flags |= AVFMT_NOFILE;

		m_fifo = (FifoBuffer *)malloc(sizeof(FifoBuffer));
		ret = and_fifo_create(m_fifo, FIFO_SIZE);
		if (ret < 0) {
			PPLOGE("failed to create fifo");
			return false;
		}

		PPLOGI("before start dump thread");
		ret = pthread_create(&m_dump_thread, NULL, dump_thread, this);
		if (ret < 0) {
			PPLOGE("failed to start dump thread");
			return false;
		}
		PPLOGI("start dump thread created");
	}
	else {
		m_out_fmt = av_guess_format(NULL, url, NULL);
		if (!m_out_fmt) {
			PPLOGE("Could not create output format");
			return false;
		}

		ret = avformat_alloc_output_context2(&m_out_fmt_ctx, m_out_fmt, NULL, NULL);
		if (ret < 0 || !m_out_fmt_ctx) {
			PPLOGE("Could not create output context");
			return false;
		}
	}

	strncpy(m_out_fmt_ctx->filename, url, 1024 - 1);

	if (!(m_out_fmt->flags & AVFMT_NOFILE)) {
        ret = avio_open(&m_out_fmt_ctx->pb, url, AVIO_FLAG_WRITE);
        if (ret < 0) {
            PPLOGE("Could not open output file '%s'", url);
            avformat_close_input(&m_out_fmt_ctx);
            return false;
        }
    }

	if (strstr(m_out_fmt->name, "matroska"/*mkv and webm*/) != NULL ||
		strstr(m_out_fmt->name, "mp4") != NULL ||
		strstr(m_out_fmt->name, "flv") != NULL)
	{
		m_nalu_type = H264_STREAM_FMT_MP4;
	}
	else {
		m_nalu_type = H264_STREAM_FMT_ANNEX;
	}

	PPLOGI("dump_url opened: %s", url);
	return true;
}

void* apFFMuxer::dump_thread(void* ptr)
{
#ifdef __ANDROID__
	JNIEnv *env = NULL;
    g_JavaVM->AttachCurrentThread(&env, NULL);
#endif

	PPLOGI("dump thread started");
    apFFMuxer* muxer = (apFFMuxer*)ptr;
    muxer->thread_impl();
	PPLOGI("dump thread exited");

#ifdef __ANDROID__
    g_JavaVM->DetachCurrentThread();
#endif
    return NULL;
}

void apFFMuxer::thread_impl()
{
	int ret;

	PPLOGI("apFFMuxer start to dump stream");
	
	// 128k to handle bitrate-burst black-screen switch to vivid frame
	const int buf_size = 65536 * 2;
	uint8_t *data = new uint8_t[buf_size];
	int size;
	int type;
	int64_t timestamp;

	while (!m_stopping) {
		pthread_mutex_lock(&m_mutex);

		while (and_fifo_used(m_fifo) < 4) {
			pthread_cond_wait(&m_cond, &m_mutex);
			if (m_stopping) {
				PPLOGI("receive quit signal");
				break;
			}
		}

		if (m_stopping) {
			pthread_mutex_unlock(&m_mutex);
			break;
		}

		and_fifo_read(m_fifo, (char *)&size, sizeof(int));
		and_fifo_read(m_fifo, (char *)&type, sizeof(int));
		and_fifo_read(m_fifo, (char *)&timestamp, sizeof(int64_t));
		if (size < 0 || size > buf_size) {
			PPLOGE("fifo pkt size is invalid: %d.%d", buf_size, size);
			pthread_mutex_unlock(&m_mutex);
			break;
		}
		ret = and_fifo_read(m_fifo, (char *)data, size);
		if (ret != size) {
			PPLOGE("fifo data is corrupted %d.%d", ret, size);
			pthread_mutex_unlock(&m_mutex);
			break;
		}

		pthread_mutex_unlock(&m_mutex);

		if (type) {
			if (!write_videoframe(data, size, timestamp))
				m_async_write_video_err = -1;
		}
		else {
			if (!write_audioframe(data, size, timestamp))
				m_async_write_audio_err = -1;
		}
	}
}

int apFFMuxer::add_video(int width, int height, int framerate, int bitrate)
{
	AutoLock lock(&m_lock);

	m_width			= width;
	m_height		= height;
	m_framerate		= framerate;
	m_video_bitrate	= bitrate;

	m_video_stream = add_videostream();
	if (!m_video_stream) {
		PPLOGE("failed to create video stream");
		return -1;
	}

	m_data = (uint8_t *)av_mallocz(MAX_DATA_LEN);
	return m_video_stream_idx;
}

int apFFMuxer::add_audio(int sample_rate, int channels, int bitrate)
{
	AutoLock lock(&m_lock);

	m_audio_sample_rate = sample_rate;
	m_audio_channels	= channels;
	m_audio_bitrate		= bitrate;

	if (m_audio_channels != 1 && m_audio_channels != 2) {
		PPLOGE("unsupported audio channels %d", m_audio_channels);
		return -1;
	}

	m_audio_stream = add_audiostream();
	if (!m_audio_stream) {
		PPLOGE("failed to create audio stream");
		return -1;
	}

	return m_audio_stream_idx;
}

bool apFFMuxer::set_metadata(int stream_idx, const char *key, const char* value)
{
	AutoLock lock(&m_lock);

	if (stream_idx >= (int)m_out_fmt_ctx->nb_streams) {
		PPLOGE("invalid stream_idx %d", stream_idx);
		return false;
	}

	AVStream *stream = m_out_fmt_ctx->streams[stream_idx];
	PPLOGI("set_metadata() key: %s, value: %s", key, value);
	return ( av_dict_set(&stream->metadata, key/*"rotation"*/, value, 0) >= 0 );
}

bool apFFMuxer::set_sps_pps(uint8_t* sps_pps, int size)
{
	AutoLock lock(&m_lock);

	PPLOGI("set_sps_pps %p, size %d", sps_pps, size);
	dump_hex("sps_pps headers", sps_pps, size);

	int32_t last_nalu_start = -1;
	for (int32_t offset=0; offset < size; offset++ ) {
		if (m_sps_data && m_pps_data) {
			break;
		}

		if (memcmp(sps_pps + offset, nalu_header, 4) == 0 || 
			memcmp(sps_pps + offset, nalu_header + 1, 3) == 0 || 
			offset == size - 1) {
			//LOGI("find start code: %d", offset);

			if (last_nalu_start != -1) {
				uint8_t* pNAL = NULL;
				int32_t sizeNAL = 0;

				// 00 00 00 00 xx data ...
				pNAL = sps_pps + last_nalu_start;
				if (offset == size - 1)
					sizeNAL = size - last_nalu_start;
				else
					sizeNAL = offset - last_nalu_start;

				process_nal(pNAL, sizeNAL);
			}

			last_nalu_start = offset;

			if (memcmp(sps_pps + offset, nalu_header, 4) == 0)
				offset += 3; // +1 by for()
			else
				offset += 2; // +1 by for()
		}
	}

	if (!m_sps_data || !m_pps_data) {
		PPLOGE("failed to find sps and pps");
		return false;
	}

	// flv,mov,mp4
	if (m_nalu_type == H264_STREAM_FMT_MP4) {
		if (m_header_written) {
			PPLOGI("header already written");
			return true;
		}

		// Extradata contains PPS & SPS for AVCC format
		AVCodecContext *video_codec = m_video_stream->codec;
		int extradata_len = 8 + m_sps_len + 1 + 2 + m_pps_len;
		video_codec->extradata = (uint8_t*)av_mallocz(extradata_len);
		video_codec->extradata_size = extradata_len;

		/*
		bits    
		8   version ( always 0x01 )
		8   avc profile ( sps[0][1] )
		8   avc compatibility ( sps[0][2] )
		8   avc level ( sps[0][3] )
		6   reserved ( all bits on )
		2   NALULengthSizeMinusOne
		3   reserved ( all bits on )
		5   number of SPS NALUs (usually 1)
		repeated once per SPS:
		  16     SPS size
		  variable   SPS NALU data
		8   number of PPS NALUs (usually 1)
		repeated once per PPS
		  16    PPS size
		  variable PPS NALU data
		*/
		video_codec->extradata[0] = 0x01; // unsigned int(8) configurationVersion = 1
		video_codec->extradata[1] = m_sps_data[1];
		video_codec->extradata[2] = m_sps_data[2];
		video_codec->extradata[3] = m_sps_data[3];
		video_codec->extradata[4] = 0xFC | (4 - 1); // unsigned int(2) lengthSizeMinusOne
		video_codec->extradata[5] = 0xE0 | 1; // unsigned int(5) numOfSequenceParameterSets
		int tmp = m_sps_len;
		video_codec->extradata[6] = (tmp >> 8) & 0x00ff; // unsigned int(16) sequenceParameterSetLength
		video_codec->extradata[7] = tmp & 0x00ff; // unsigned int(16) sequenceParameterSetLength
		memcpy(video_codec->extradata + 8, m_sps_data, m_sps_len);
		video_codec->extradata[8+tmp] = 0x01; // unsigned int(8) numOfPictureParameterSets
		int tmp2 = m_pps_len;   
		video_codec->extradata[8+tmp+1] = (m_pps_len >> 8) & 0x00ff; // unsigned int(16) pictureParameterSetLength
		video_codec->extradata[8+tmp+2] = tmp2 & 0x00ff; // unsigned int(16) pictureParameterSetLength
		memcpy(video_codec->extradata + 8 + tmp + 3, m_pps_data, m_pps_len);

		// flv MUST write header after sps and pps got
		// mp4 is optional
		if (!write_header())
			return false;
	}

	return true;
}

bool apFFMuxer::write_header()
{
	int ret = avformat_write_header(m_out_fmt_ctx, NULL);
	if (ret < 0) {
		PPLOGE("failed to avformat_write_header in set_sps_pps");
		avformat_close_input(&m_out_fmt_ctx);
		return false;
	}

	PPLOGI("after avformat_write_header()");
	av_dump_format(m_out_fmt_ctx, 0, m_out_fmt_ctx->filename, 1);
	m_stream_start_msec = getNowMs();
	m_header_written = true;
	return true;
}

bool apFFMuxer::write_frame(bool is_video, uint8_t* data, int size, int64_t timestamp)
{
	AutoLock lock(&m_lock);

	if (VERBOSE) PPLOGI("write_frame() %s, data %p, size %d, timestamp %lld", 
		is_video? "video" : "audio", data, size, timestamp);

	if (H264_STREAM_FMT_ANNEX == m_nalu_type && !m_header_written) {
		if (!write_header())
			return false;
	}

	if (m_fifo) {
		if (is_video && m_async_write_video_err < 0) {
			PPLOGE("failed to write async video frame");
			return false;
		}
		if (!is_video && m_async_write_audio_err < 0) {
			PPLOGE("failed to write async audio frame");
			return false;
		}

		// size(4), type(4), timestamp(8), data
		int ret;
		int type = (is_video ? 1 : 0);
		int used = and_fifo_used(m_fifo);
		if (used + size + 8 > FIFO_SIZE) {
			PPLOGE("fifo overflow %d.%d", and_fifo_used(m_fifo) + size + 8, FIFO_SIZE);
			return false;
		}

		AutoLock lock(&m_mutex);

		if (m_dropframe == 0 && used >= m_video_bitrate / 8 * 3/* 3 sec data */) {
			and_fifo_reset(m_fifo);
			m_dropframe = 1;
			m_drop_video_frames = 0;
			PPLOGW("drop_frame started");
		}
		if (m_dropframe) {
			if (type) {
				// get nalu type
				int nalType = data[4] & 0x1f;
				if (nalType == NAL_IDR_SLICE) {
					m_dropframe = 0;
					PPLOGW("drop_frame finished");
				}
				else {
					PPLOGW("drop_frame #%d, nalType %d", m_drop_video_frames, nalType);
				}
			}
		}
		if (m_dropframe) {
			if (type) {
				PPLOGW("drop_frame #%d", m_drop_video_frames++);
			}
		}
		else {
			and_fifo_write(m_fifo, (char *)&size, sizeof(int));
			and_fifo_write(m_fifo, (char *)&type, sizeof(int));
			and_fifo_write(m_fifo, (char *)&timestamp, sizeof(int64_t));
			ret = and_fifo_write(m_fifo, (char *)data, size);
			if (ret != size) {
				PPLOGE("fifo overflow %d.%d", and_fifo_used(m_fifo) + size + 8, FIFO_SIZE);
				return false;
			}

			// notify data available
			pthread_cond_signal(&m_cond);
		}
		
		return true;
	}

	// direct write when write local file
	if (is_video)
		return write_videoframe(data, size, timestamp);
	else
		return write_audioframe(data, size, timestamp);
}

int apFFMuxer::get_bps()
{
	if (!m_out_fmt_ctx || !m_out_fmt_ctx->pb)
		return 0;

	int64_t total_size = avio_size(m_out_fmt_ctx->pb); // -38 in rtmp case
	if (total_size <= 0) {
		total_size = avio_tell(m_out_fmt_ctx->pb);
	}
	if (total_size < 0)
		total_size = 0;

	int64_t duration = getNowMs() - m_stream_start_msec;
	if (duration < 100)
		duration = 100;
	int kbps = (double)(total_size * 8) / (double)duration;
	return kbps;
}

int apFFMuxer::get_buffering_size()
{
	if (m_fifo) {
		return and_fifo_used(m_fifo);
	}

	return 0;
}

void apFFMuxer::close()
{
	PPLOGI("close()");
	m_stopping = 1;

	if (m_dump_thread) {
		pthread_mutex_lock(&m_mutex);
		pthread_cond_signal(&m_cond);
		pthread_mutex_unlock(&m_mutex);

		PPLOGI("stop(): before pthread_join %p", m_dump_thread);
		if (pthread_join(m_dump_thread, NULL) != 0) {
			PPLOGE("pthread_join error");
		}
		PPLOGI("after join");
	}

    /* close output */
    if (m_out_fmt && !(m_out_fmt->flags & AVFMT_NOFILE) && m_header_written/*avoid call av_write_trailer crash*/) {
		av_write_trailer(m_out_fmt_ctx);
		PPLOGI("after av_write_trailer()");

        avio_close(m_out_fmt_ctx->pb);
	}

    avformat_free_context(m_out_fmt_ctx);

	if (m_pBsfc_aac) {
		av_bitstream_filter_close(m_pBsfc_aac);
		m_pBsfc_aac = NULL;
	}

	if (m_fifo) {
		PPLOGI("fifo left data size: %d", and_fifo_used(m_fifo));
		and_fifo_close(m_fifo);
		free(m_fifo);
		m_fifo = NULL;
	}

	m_stopping = 0;
}

AVStream * apFFMuxer::add_videostream()
{
	AVStream*		st		= NULL;
	AVCodecContext*	c		= NULL;

	st = avformat_new_stream(m_out_fmt_ctx, NULL);
	if (!st) {
		PPLOGE("Could not allocate video stream.");
		return NULL;
	}

	st->id					= m_out_fmt_ctx->nb_streams - 1;
	st->avg_frame_rate.num	= m_framerate;
	st->avg_frame_rate.den	= 1;

	// fix invalid meta data frame_rate 
	AVRational ra;
	ra.num = m_framerate;
	ra.den = 1;
	av_stream_set_r_frame_rate(st, ra);

	c					= st->codec;
	c->pix_fmt			= AV_PIX_FMT_YUV420P;
	c->codec_id			= AV_CODEC_ID_H264;
	c->codec_type		= AVMEDIA_TYPE_VIDEO;

	c->bit_rate			= m_video_bitrate;
	c->width				= m_width;
	c->height			= m_height;
	c->gop_size			= 10;
	c->keyint_min		= 5;				// hard code to increase encode complexity

	//c->codec_tag = 0;
	if (m_out_fmt->flags & AVFMT_GLOBALHEADER)
		c->flags |= CODEC_FLAG_GLOBAL_HEADER;

	m_video_stream_idx = st->id;
	return st;
}

AVStream * apFFMuxer::add_audiostream()
{
	AVStream*		st = NULL;
	AVCodecContext*	c = NULL;

	st = avformat_new_stream(m_out_fmt_ctx, NULL);
	if (!st) {
		PPLOGE("Could not allocate audio stream.");
		return NULL;
	}

	st->id				= m_out_fmt_ctx->nb_streams - 1;
	c					= st->codec;

	c->codec_id			= AV_CODEC_ID_AAC;
	c->codec_type		= AVMEDIA_TYPE_AUDIO;
	c->profile			= FF_PROFILE_AAC_LOW;

	c->sample_rate		= m_audio_sample_rate;
	c->sample_fmt		= AV_SAMPLE_FMT_S16; // hard code
	c->channels			= m_audio_channels;
	c->channel_layout	= m_audio_channels == 1 ? AV_CH_LAYOUT_MONO : AV_CH_LAYOUT_STEREO;
	c->bit_rate			= m_audio_bitrate;

	if (m_out_fmt->flags & AVFMT_GLOBALHEADER)
		c->flags |= CODEC_FLAG_GLOBAL_HEADER;

	m_audio_stream_idx = st->id;

	if (H264_STREAM_FMT_MP4 == m_nalu_type && !fill_aac_extradata(c)) {
		PPLOGE("failed to fill aac extra data");
		return NULL;
	}

	// flv(rtmp), mp4 dont need adts header (7 bytes) to write_frame
	// but mpegets need it
	// so java side will add adts header before aac data if muxer is mpegts
	
	return st;
}

bool apFFMuxer::write_videoframe(uint8_t* data, int size, int64_t timestamp)
{
	if (VERBOSE) {
		PPLOGI("write_videoframe() data %p, size %d, timestamp %lld", 
			data, size, timestamp);
	}

	if (m_video_stream_idx == -1) {
		PPLOGE("video straem is empty");
		return false;
	}

	/* Write the compressed frame to the media file. */
	int ret;

	int nalType = data[4] & 0x1f;
	int offset = 0;

	if (H264_STREAM_FMT_ANNEX == m_nalu_type && nalType == NAL_IDR_SLICE) {
		// copy sps nalu
		write_nalu_startcode(m_data + offset);
		offset += 4;
		memcpy(m_data + offset, m_sps_data, m_sps_len);
		offset += m_sps_len;

		// copy pps nalu
		write_nalu_startcode(m_data + offset);
		offset += 4;
		memcpy(m_data + offset, m_pps_data, m_pps_len);
		offset += m_pps_len;
		PPLOGD("sps and pps is added before IDR frame");
	}

	// copy pic nalu
	if (H264_STREAM_FMT_ANNEX == m_nalu_type)
		write_nalu_startcode(m_data + offset);
	else
		write_nalu_size(m_data + offset, size - 4);
	offset += 4;
	if (offset + (size - 4) >= MAX_DATA_LEN) {
		PPLOGE("pkt data len is overflow: %d %d", 
			offset + (size - 4), MAX_DATA_LEN);
		return false;
	}
	memcpy(m_data + offset, data + 4, size - 4);
	offset += (size - 4);

	AVRational usec_timebase;
	usec_timebase.num = 1;
	usec_timebase.den = 1000000;

	int duration = 1000000 / m_framerate;

	AVPacket pkt = { 0 };
	av_init_packet(&pkt);
	pkt.data			= m_data;
	pkt.size			= offset;
	pkt.stream_index	= m_video_stream_idx;
	pkt.dts = pkt.pts	= av_rescale_q(timestamp, usec_timebase, m_video_stream->time_base);
	pkt.duration		= av_rescale_q(duration, usec_timebase, m_video_stream->time_base);
	pkt.pos				= -1;

	// libx264 encoded data will append sps and pps before IDR frame
	if (nalType == NAL_IDR_SLICE || nalType == NAL_SPS)
		pkt.flags	= AV_PKT_FLAG_KEY;
	else
		pkt.flags	= 0;

	ret = av_interleaved_write_frame(m_out_fmt_ctx, &pkt);
	if ( ret != 0) {
		char msg[256] = {0};
		av_strerror(ret, msg, 256);
		PPLOGE("failed to write video frame. err = %d(%s)", ret, msg);
		return false;
	}

	return true;
}

bool apFFMuxer::write_audioframe(uint8_t* data, int size, int64_t timestamp)
{
	if (VERBOSE) PPLOGI("write_audioframe() data %p, size %d, timestamp %lld", data, size, timestamp);
	
	if (m_audio_stream_idx == -1) {
		PPLOGE("audio straem is empty");
		return false;
	}

	int ret;

	AVRational usec_timebase;
	usec_timebase.num = 1;
	usec_timebase.den = 1000000;

	AVPacket pkt = { 0 };
	av_init_packet(&pkt);

	int duration = size * 1000000 / m_audio_sample_rate * m_audio_channels * 2;

	pkt.data			= data;
	pkt.size			= size;
	pkt.stream_index	= m_audio_stream_idx;
	pkt.dts = pkt.pts	= av_rescale_q(timestamp, usec_timebase, m_audio_stream->time_base);
	pkt.duration		= (int)av_rescale_q(duration, usec_timebase, m_audio_stream->time_base);
	pkt.pos				= -1;
	pkt.flags			= 0;

	if (m_pBsfc_aac) {
		// will remove ADTS header 7 bytes, pkt.data += 7, pkt.size -= 7
		if (av_bitstream_filter_filter(m_pBsfc_aac, m_audio_stream->codec, NULL, 
			&pkt.data, &pkt.size, 
			pkt.data, pkt.size, 0) < 0)
		{
			PPLOGE("failed to aac_adtstoasc_filter()");
			return false;
		}
	}

	ret = av_interleaved_write_frame(m_out_fmt_ctx, &pkt);
	if ( ret != 0) {
		char msg[256] = {0};
		av_strerror(ret, msg, 256);
		PPLOGE("failed to write audio frame. err = %d(%s)", ret, msg);
		return false;
	}

	return true;
}

void apFFMuxer::write_nalu_startcode(uint8_t *buf)
{
	buf[0] = 0x00;
	buf[1] = 0x00;
	buf[2] = 0x00;
	buf[3] = 0x01;
}

void apFFMuxer::write_nalu_size(uint8_t *buf, int size)
{
	uint8_t size_header[4] = {0};

	memcpy(size_header, &size, 4);
	buf[0] = size_header[3];
	buf[1] = size_header[2];
	buf[2] = size_header[1];
	buf[3] = size_header[0];
}

// with start code 00 00 00 01
void apFFMuxer::process_nal(uint8_t* data, int size)
{
	int nalType = data[4] & 0x1f;
	PPLOGD("nalType %d, size %d", nalType, size);

	if (nalType == NAL_SPS) {
		if (m_sps_data) {
			av_free(m_sps_data);
			m_sps_data = NULL;
			m_sps_len = 0;
		}

		m_sps_data = (uint8_t *)av_mallocz(size - 4);
		memcpy(m_sps_data, data + 4, size - 4);
		m_sps_len = size - 4;
		PPLOGD("sps got, size %d", m_sps_len);
		//dump_hex("sps", m_sps_data, m_sps_len);
	}
	else if (nalType == NAL_PPS) {
		if (m_pps_data) {
			av_free(m_pps_data);
			m_pps_data = NULL;
			m_pps_len = 0;
		}

		m_pps_data = (uint8_t *)av_mallocz(size - 4);
		memcpy(m_pps_data, data + 4, size - 4);
		m_pps_len = size - 4;
		PPLOGD("pps got, size %d", m_pps_len);
		//dump_hex("pps", m_pps_data, m_pps_len);
	}
	else if (nalType == NAL_SEI) {
		char str_sei[1024] = {0};
		memcpy(str_sei, data + 4, size - 4);
		str_sei[size - 4] = '\0';
		PPLOGD("sei got, size %d, context %s", size - 4, str_sei);
	}
}

bool apFFMuxer::fill_aac_extradata(AVCodecContext *c)
{
	int16_t aacObjectType = 2; // 2: AAC LC (Low Complexity)
	int16_t sampleRateIdx;
	int16_t numChannels;

	/*Sampling Frequencies
	0: 96000 Hz
	1: 88200 Hz
	2: 64000 Hz
	3: 48000 Hz
	4: 44100 Hz
	5: 32000 Hz
	6: 24000 Hz
	7: 22050 Hz
	8: 16000 Hz
	9: 12000 Hz
	10: 11025 Hz
	11: 8000 Hz
	12: 7350 Hz
	13: Reserved
	14: Reserved
	15: frequency is written explictly
	*/
	int sample_rate = c->sample_rate;
	// That's not a bug. HE-AAC files contain half their actual sampling rate in their headers
	// mkvmerge will issue warnings in such a case, and you have to select with --aac-is-sbr (or mmg's corresponding GUI element).
	if (c->profile == FF_PROFILE_AAC_HE || c->profile == FF_PROFILE_AAC_HE_V2)
		sample_rate /= 2;

	switch (sample_rate) {
	case 96000:
		sampleRateIdx = 0;
		break;
	case 88200:
		sampleRateIdx = 1;
		break;
	case 64000:
		sampleRateIdx = 2;
		break;
	case 48000:
		sampleRateIdx = 3;
		break;
	case 44100:
		sampleRateIdx = 4;
		break;
	case 32000:
		sampleRateIdx = 5;
		break;
	case 24000:
		sampleRateIdx = 6;
		break;
	case 22050:
		sampleRateIdx = 7;
		break;
	case 16000:
		sampleRateIdx = 8;
		break;
	case 12000:
		sampleRateIdx = 9;
		break;
	case 11025:
		sampleRateIdx = 10;
		break;
	case 8000:
		sampleRateIdx = 11;
		break;
	case 7350:
		sampleRateIdx = 12;
		break;
	default:
		PPLOGE("unsupported audio sample rate %d", sample_rate);
		return false;
	}

	if (c->channels != 0) {
		numChannels = c->channels;
	}
	else {
		/*Channel Configurations
		0: Defined in AOT Specifc Config
		1: 1 channel: front-center
		2: 2 channels: front-left, front-right
		3: 3 channels: front-center, front-left, front-right
		4: 4 channels: front-center, front-left, front-right, back-center
		5: 5 channels: front-center, front-left, front-right, back-left, back-right
		6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
		7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
		8-15: Reserved*/
		switch(c->channel_layout) {
		case AV_CH_LAYOUT_MONO:
			numChannels = 1; 
			break;
		case AV_CH_LAYOUT_STEREO:
			numChannels = 2; 
			break;
		case AV_CH_LAYOUT_2POINT1:
		case AV_CH_LAYOUT_2_1:
		case AV_CH_LAYOUT_SURROUND:
			numChannels = 3; 
			break;
		case AV_CH_LAYOUT_4POINT0:
			numChannels = 4; 
			break;
		case AV_CH_LAYOUT_5POINT0_BACK:
			numChannels = 5; 
			break;
		case AV_CH_LAYOUT_5POINT1_BACK:
			numChannels = 6; 
			break;
		default:
			PPLOGE("unsupported audio channel layout %lld", c->channel_layout);
			return false;
		}
	}

	int64_t extra_data = (aacObjectType << 11) | (sampleRateIdx << 7) | (numChannels << 3);

	c->extradata_size = 2;
	c->extradata = (uint8_t*)av_mallocz(c->extradata_size);

	// big endian
	uint8_t tmp[2] = {0};
	memcpy(tmp, &extra_data, 2);
	c->extradata[0] = tmp[1];
	c->extradata[1] = tmp[0];

	return true;
}

static void dump_hex(const char *name, unsigned char *data, int len)
{
	char str_line[1024] = {0};
	char tmp[4] = {0};
	strcpy(str_line, name);
	strcat(str_line, ": ");
	for (int i=0;i<len;i++) {
		sprintf(tmp, "%02x ", data[i]);
		strcat(str_line, tmp);
	}
	
	PPLOGD(str_line);
}