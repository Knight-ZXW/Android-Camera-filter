#ifndef _AP_FF_MUXER_H_
#define _AP_FF_MUXER_H_

#ifdef __cplusplus
extern "C"
{
#ifdef _STDINT_H
#undef _STDINT_H
#include <stdint.h>
#endif

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

#include <pthread.h>

struct FifoBuffer;

class apFFMuxer
{
public:
	apFFMuxer(void);
	~apFFMuxer(void);

	bool open(char *url);

	// @return track id
	// -1 error
	// >0 success
	int add_video(int width, int height, int framerate, int bitrate/* bps */);

	// @return track id
	// -1 error
	// >0 success
	int add_audio(int sample_rate, int channels, int bitrate/* bps */);

	bool set_metadata(int stream_idx, const char *key, const char* value);

	bool set_sps_pps(uint8_t* sps_pps, int size);

	// with start code 00 00 00 01
	bool write_frame(bool is_video, uint8_t* data, int size, int64_t timestamp/* usec */);

	void close();

	// unit: kbps
	int get_bps();

	int get_buffering_size();

	static void ff_log(void* user, int level, const char* fmt, va_list vl);

	enum H264_STREAM_FMT {
		H264_STREAM_FMT_ANNEX, // 00 00 00 01: as start code, for mpegts
		H264_STREAM_FMT_MP4, // 00 00 aa bb: as nalu size(big endian), for mp4 flv mkv
	};

private:
	AVStream * add_videostream();

	AVStream * add_audiostream();

	// with start code 00 00 00 01
	void process_nal(uint8_t* data, int size);

	// with start code 00 00 00 01
	bool write_videoframe(uint8_t* data, int size, int64_t timestamp);

	bool write_audioframe(uint8_t* data, int size, int64_t timestamp);

	void write_nalu_startcode(uint8_t *buf);

	void write_nalu_size(uint8_t *buf, int size);

	bool fill_aac_extradata(AVCodecContext *c);

	bool write_header();

	static void* dump_thread(void* ptr);

	void thread_impl();

private:
	AVFormatContext*	m_out_fmt_ctx;
	AVOutputFormat*		m_out_fmt;
	AVStream*			m_video_stream;
	AVStream*			m_audio_stream;
	int					m_video_stream_idx;
	int					m_audio_stream_idx;
	uint8_t*			m_data;
	bool				m_header_written;

	H264_STREAM_FMT		m_nalu_type;
	uint8_t*			m_sps_data;
	int					m_sps_len;
	uint8_t*			m_pps_data;
	int					m_pps_len;

	int					m_width;
	int					m_height;
	int					m_video_bitrate;
	int					m_framerate;

	int					m_audio_sample_rate;
	int					m_audio_channels;
	int					m_audio_bitrate;

	AVBitStreamFilterContext*	m_pBsfc_aac;

	pthread_mutex_t		m_lock;

	int64_t				m_stream_start_msec;

	FifoBuffer*			m_fifo;
	pthread_t			m_dump_thread;
	pthread_mutex_t		m_mutex;
	pthread_cond_t		m_cond;
	int					m_stopping;
	int					m_dropframe;
	int					m_drop_video_frames;
	int					m_async_write_video_err;
	int					m_async_write_audio_err;
};
#endif

#ifdef __cplusplus
extern "C" {
#endif

void enc_set_muxer(long muxer);

// return 0 ok, < 0 error
int enc_set_sps_pps(uint8_t* sps_pps, int size);

// return 0 ok, < 0 error
int enc_write_frame(int is_video, uint8_t* data, int size, int64_t timestamp/* usec */);

#ifdef __cplusplus
}
#endif

#endif // _AP_FF_MUXER_H_
