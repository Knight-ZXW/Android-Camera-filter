#ifndef _AUDIO_PLAYER_H_
#define _AUDIO_PLAYER_H_

#include <stdint.h>
#include <stdarg.h>
#include <pthread.h>

struct AVFormatContext;
struct AVStream;
struct AVCodecContext;
struct AVFrame;
struct AVPacket;
struct SwrContext;

class and_osles;

typedef void(*pcm_callback)(const char *data, int size, int timestamp/*msec*/);
typedef void(*complete_callback)();
typedef void(*eof_callback)();

class apAudioPlayer
{
public:
	apAudioPlayer(void);

	~apAudioPlayer(void);

	bool open(const char* url);

	bool open(const char* url, int out_channels, int out_format, int out_sample_rate);

	bool play();

	void close();

	void setLoop(bool isLoop) {m_loop = isLoop;}

	void setCallback(pcm_callback pcm_cb, complete_callback complete_cb, eof_callback eof_cb) {
		m_on_pcm		= pcm_cb;
		m_on_complete	= complete_cb;
		m_on_eof		= eof_cb;
	}

	int getCurrentPosition();

	int getDuration();

	int getOutputSampleRate() { return m_out_sample_rate;}

	void setVolume(float vol);

	float getVolume();

	void seekTo(int msec);

private:
	bool open_internal(
		const char* url, int out_channels, int out_format, int out_sample_rate);

	static int interrupt_l(void* ctx);

	static void ff_log_callback(void* avcl, int level, const char* fmt, va_list vl);

	static void* decode_thread(void* ptr);

	void thread_impl();

	int open_codec_context(int *stream_idx, int media_type);

	int decode_packet(AVPacket *pkt, int *got_frame, int cached);

	bool init_swr();

	bool seek_l();
private:
	AVFormatContext*	m_fmt_ctx;
	char*				m_url;
	AVStream *			m_audio_stream;
	int					m_audio_stream_idx;
	AVCodecContext*		m_audio_dec_ctx;
	int					m_audio_clock_msec;
	int					m_duration;
	AVFrame*			m_audio_frame;

	SwrContext*			m_swr;
	int16_t*			mSamples;
	int					m_out_channels;
	int					m_out_fmt;
	int					m_out_sample_rate;

	and_osles*			m_audio_render;

	pthread_t			mThread;
	bool				m_loop;
	bool				m_running;
	bool				m_stopping;
	bool				m_closed;
	bool				m_eof;

	pcm_callback		m_on_pcm;
	complete_callback	m_on_complete;
	eof_callback		m_on_eof;

	bool				m_seeking;
	int					m_seek_flag;
	int					m_seek_pos; // msec
};

#endif

