#include "apAudioPlayer.h"
#include "oslesrender.h"
#include <jni.h>
#include <unistd.h> // for usleep

#ifdef __cplusplus
extern "C"
{
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
#ifndef INT64_MIN
#define INT64_MIN        (INT64_C(-9223372036854775807)-1)
#define INT64_MAX        (INT64_C(9223372036854775807))
#endif
}
#endif

#define LOG_TAG "apAudioPlayer"
#include "pplog.h"

#define OSLES_OUTPUT_BIT_BER_SAMPLE 16 // s16
#define AVCODEC_MAX_AUDIO_FRAME_SIZE 19200 // 100 msec of 48khz 2chnnel S16 audio

extern JavaVM *g_JavaVM;

static int64_t get_channel_layout(uint64_t channel_layout, int channels);

apAudioPlayer::apAudioPlayer(void)
	:m_fmt_ctx(NULL), m_url(NULL),
	m_audio_stream(NULL), m_audio_stream_idx(0), m_audio_dec_ctx(NULL), 
	m_audio_clock_msec(0), m_duration(0), m_audio_frame(NULL),
	m_swr(NULL), mSamples(NULL), m_out_channels(0), m_out_fmt(0), m_out_sample_rate(0),
	m_audio_render(NULL),
	m_loop(false), m_running(false), m_stopping(false), m_closed(false), m_eof(false),
	m_on_pcm(NULL), m_on_complete(NULL),
	m_seeking(false), m_seek_flag(0), m_seek_pos(0)
{
	av_register_all();
	av_log_set_level(AV_LOG_INFO);
	av_log_set_callback(ff_log_callback);
}

apAudioPlayer::~apAudioPlayer(void)
{
	close();
}

int apAudioPlayer::interrupt_l(void* ctx)
{
	apAudioPlayer* player = (apAudioPlayer*)ctx;
    if (player == NULL)
		return 1;

	return 0;
}

void apAudioPlayer::ff_log_callback(void* avcl, int level, const char* fmt, va_list vl)
{
	AVClass* avc = avcl ? *(AVClass**)avcl : NULL;
	const char * class_name = ((avc != NULL) ? avc->class_name : "N/A");
	
	char msg[1024] = {0};
	char log[4096] = {0};

	vsnprintf(msg, sizeof(msg), fmt, vl);
	snprintf(log, 4096, "ffmpeg[%d][%s] %s", level, class_name, msg);

	switch(level) {
		case AV_LOG_PANIC:
		case AV_LOG_FATAL:
		case AV_LOG_ERROR:
			PPLOGE("%s", log);
			break;
		case AV_LOG_WARNING:
            PPLOGW("%s", log);
			break;
		case AV_LOG_INFO:
            PPLOGI("%s", log);
			break;
		case AV_LOG_DEBUG:
            PPLOGD("%s", log);
			break;
		case AV_LOG_VERBOSE:
            PPLOGV("%s", log);
			break;
		case AV_LOG_MAX_OFFSET:
			break;
		default:
			PPLOGD("%s", log);
			break;
	}
}

bool apAudioPlayer::open(const char* url)
{
	return open_internal(url, -1, -1, -1);
}

bool apAudioPlayer::open(
	const char* url, int out_channels, int out_format, int out_sample_rate)
{
	return open_internal(url, out_channels, out_format, out_sample_rate);
}

bool apAudioPlayer::open_internal(
	const char* url, int out_channels, int out_format, int out_sample_rate)
{
	PPLOGI("open_internal() %s", url);

	if (!url || strcmp(url, "") == 0) {
		PPLOGE("url is empty");
		return false;
	}

	if (m_url)
		delete m_url;

	int len = strlen(url) + 1;
	m_url = new char[len];
	strcpy(m_url, url);

	m_fmt_ctx = avformat_alloc_context();
	AVIOInterruptCB cb = {interrupt_l, this};
    m_fmt_ctx->interrupt_callback = cb;

	/* open input file, and allocate format context */
    if (avformat_open_input(&m_fmt_ctx, m_url, NULL, NULL) < 0) {
		PPLOGE("Could not open source file %s", m_url);
        return false;
    }

	/* retrieve stream information */
    if (avformat_find_stream_info(m_fmt_ctx, NULL) < 0) {
        PPLOGE("Could not find stream information");
        return false;
    }

	m_duration = (int)(m_fmt_ctx->duration * 1000 / AV_TIME_BASE);
	PPLOGI("duration: %d msec", m_duration);

	if (open_codec_context(&m_audio_stream_idx, AVMEDIA_TYPE_AUDIO) >= 0) {
        m_audio_stream = m_fmt_ctx->streams[m_audio_stream_idx];
        m_audio_dec_ctx = m_audio_stream->codec;
    }

	/*if (m_audio_dec_ctx->channels == 0) {
		PPLOGI("channels NOT set, do fill");
		switch (m_audio_dec_ctx->channel_layout) {
		case AV_CH_LAYOUT_MONO:
			m_audio_dec_ctx->channels = 1;
			break;
		case AV_CH_LAYOUT_STEREO:
			m_audio_dec_ctx->channels = 2;
			break;
		case AV_CH_LAYOUT_2POINT1:
		case AV_CH_LAYOUT_SURROUND:
			m_audio_dec_ctx->channels = 3;
			break;
		case AV_CH_LAYOUT_3POINT1:
		case AV_CH_LAYOUT_QUAD:
		case AV_CH_LAYOUT_2_2:
			m_audio_dec_ctx->channels = 4;
			break;
		case AV_CH_LAYOUT_4POINT1:
			m_audio_dec_ctx->channels = 5;
			break;
		case AV_CH_LAYOUT_5POINT1:
			m_audio_dec_ctx->channels = 6;
			break;
		case AV_CH_LAYOUT_6POINT1:
			m_audio_dec_ctx->channels = 7;
			break;
		default:
			PPLOGI("set channels to default 2");
			m_audio_dec_ctx->channels = 2;
			break;
		}
	}*/

	// fix wav file channel_layout NOT set problem
	m_audio_dec_ctx->channel_layout = get_channel_layout(
		m_audio_dec_ctx->channel_layout, m_audio_dec_ctx->channels);

	m_audio_frame = av_frame_alloc();
	if (!m_audio_frame) {
        PPLOGE("Could not allocate frame");
        return false;
    }

	if (out_channels != -1 && out_format != -1 && out_sample_rate != -1) {
		m_out_channels = out_channels;
		m_out_fmt = out_format;
		m_out_sample_rate = out_sample_rate;
	}
	else {
		PPLOGI("try use default audio config as output");

		m_out_channels = 1;
		
		if (m_audio_dec_ctx->sample_fmt != AV_SAMPLE_FMT_S16) {
			m_out_fmt = AV_SAMPLE_FMT_S16;
		}
		else {
			m_out_fmt = m_audio_dec_ctx->sample_fmt;
		}

		if (m_audio_dec_ctx->sample_rate < 4000 || m_audio_dec_ctx->sample_rate > 48000)
			m_out_sample_rate = 44100;
		else
			m_out_sample_rate = m_audio_dec_ctx->sample_rate;
	}

	char channel_layout_desc[64] = {0};
	av_get_channel_layout_string(channel_layout_desc, 64, 
		m_audio_dec_ctx->channels, m_audio_dec_ctx->channel_layout);
	PPLOGI("input: channel %d, layout %lld(%s), fmt %d, sample_rate %d", 
		m_audio_dec_ctx->channels, m_audio_dec_ctx->channel_layout, channel_layout_desc,
		m_audio_dec_ctx->sample_fmt, m_audio_dec_ctx->sample_rate);
	PPLOGI("output: channel %d, fmt %d, sample_rate %d", 
		m_out_channels, m_out_fmt, m_out_sample_rate);

	uint64_t out_channel_layout = 
		(m_out_channels > 1 ? AV_CH_LAYOUT_STEREO : AV_CH_LAYOUT_MONO);
	
	if (m_audio_dec_ctx->channels != m_out_channels ||
		m_audio_dec_ctx->sample_rate != m_out_sample_rate ||
		m_audio_dec_ctx->channel_layout != out_channel_layout ||
		m_audio_dec_ctx->sample_fmt != m_out_fmt)
	{
		PPLOGI("need resample");

		if (!init_swr()) {
			PPLOGE("failed to init swr");
			return false;
		}
		PPLOGI("swr created");

		mSamples = (int16_t*)av_malloc(AVCODEC_MAX_AUDIO_FRAME_SIZE);
		if (mSamples == NULL) {
			PPLOGE("No enough memory for audio conversion");
			return false;
		}
	}

	/* dump input information to stderr */
	av_dump_format(m_fmt_ctx, 0, m_url, 0);

	m_audio_render = new and_osles;
	if (!m_audio_render) {
		PPLOGE("failed to malloc audio render");
		return false;
	}

	int ret;
	ret = m_audio_render->open(
		m_out_sample_rate, m_out_channels, OSLES_OUTPUT_BIT_BER_SAMPLE);
	if (ret != 0) {
		PPLOGE("failed to open audio render");
		return false;
	}

	return true;
}

int apAudioPlayer::open_codec_context(int *stream_idx, int media_type)
{
    int ret;
    AVStream *st;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;

	enum AVMediaType type = (AVMediaType)media_type;

    ret = av_find_best_stream(m_fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
		PPLOGE("Could not find best %s stream in input file '%s'",
			av_get_media_type_string(type), m_url);
        return ret;
    } else {
        *stream_idx = ret;
        st = m_fmt_ctx->streams[*stream_idx];

        /* find decoder for the stream */
        dec_ctx = st->codec;
        dec = avcodec_find_decoder(dec_ctx->codec_id);
        if (!dec) {
            PPLOGE("Failed to find %s codec", av_get_media_type_string(type));
            return AVERROR(EINVAL);
        }

        /* Init the decoders, with or without reference counting */
        av_dict_set(&opts, "refcounted_frames", "1", 0);
        if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
            PPLOGE("Failed to open %s codec", av_get_media_type_string(type));
            return ret;
        }
    }

    return 0;
}

int apAudioPlayer::getCurrentPosition()
{
	if (!m_running || m_stopping) {
		return 0;
	}

	if (m_seeking)
		return m_seek_pos;

	int pos = m_audio_clock_msec - m_audio_render->get_latency();
	if (pos < 0)
		pos = 0;

	return pos;
}

int apAudioPlayer::getDuration()
{
	return m_duration;
}

void apAudioPlayer::setVolume(float vol)
{
	if (m_audio_render) {
		m_audio_render->setVol(vol);
	}
}

float apAudioPlayer::getVolume()
{
	if (m_audio_render) {
		return m_audio_render->getVol();
	}

	return 0.0f;
}

bool apAudioPlayer::play()
{
	if (m_running) {
		PPLOGW("already running");
		return true;
	}

	if (m_audio_render->play() != 0) {
		PPLOGE("failed to start audio render");
		return false;
	}

	PPLOGI("before start decode thread");
	int ret = pthread_create(&mThread, NULL, decode_thread, this);
	if (ret != 0) {
		PPLOGE("pthread_create error: %d", ret);
		return false;
	}

	PPLOGI("decode thread created");
	m_running = true;
	return true;
}

void apAudioPlayer::seekTo(int msec)
{
	PPLOGI("seekTo() %d", msec);

	if (m_seeking) {
		PPLOGW("already in seeking state");
		return;
	}

	int incr;
	if (msec > m_audio_clock_msec)
		incr = 1;
	else
		incr = -1;

	m_seek_pos		= msec;
	m_seeking		= true;
	m_eof			= false;
	m_seek_flag		= incr < 0 ? AVSEEK_FLAG_BACKWARD : 0;
}

void apAudioPlayer::close()
{
	if (m_closed)
		return;

	if (m_running) {
		m_stopping = true;

		PPLOGI("stop(): decode_thread before pthread_join %p", mThread);
		int ret = pthread_join(mThread, NULL);
		if (ret != 0)
			PPLOGE("pthread_join error %d", ret);

		PPLOGI("stop(): after thread join");
	}

	if (m_audio_dec_ctx) {
		avcodec_close(m_audio_dec_ctx);
		m_audio_dec_ctx = NULL;
	}
	if (m_fmt_ctx) {
		m_fmt_ctx->interrupt_callback.callback = NULL;
		m_fmt_ctx->interrupt_callback.opaque = NULL;
		avformat_close_input(&m_fmt_ctx);
	}

	if (m_audio_frame != NULL) {
		av_frame_free(&m_audio_frame);
	}
	if (m_swr != NULL) {
		swr_free(&m_swr);
		m_swr = NULL;
	}
	if (m_url) {
		delete m_url;
		m_url = NULL;
	}

	if (m_audio_render) {
		delete m_audio_render;
		m_audio_render = NULL;
	}

	m_closed = true;
	PPLOGI("close done!");
}

bool apAudioPlayer::seek_l()
{
	int stream_index = m_audio_stream_idx;
	int64_t seek_target = m_seek_pos * AV_TIME_BASE / 1000;
	seek_target= av_rescale_q(
		seek_target, 
		AV_TIME_BASE_Q, 
		m_fmt_ctx->streams[stream_index]->time_base);

	int64_t seek_min = INT64_MIN;
	int64_t seek_max = INT64_MAX;
    if (avformat_seek_file(m_fmt_ctx, stream_index, 
		seek_min, seek_target, seek_max, m_seek_flag) < 0) {
		PPLOGE("failed to seek to: %d msec", m_seek_pos);
		return false;
    }
				
    PPLOGI("after seek to :%d msec", m_seek_pos);

	m_audio_render->flush();
	return true;
}

void* apAudioPlayer::decode_thread(void* ptr)
{
#ifdef __ANDROID__
	// LOGXX need jvm
	JNIEnv *env = NULL;
    g_JavaVM->AttachCurrentThread(&env, NULL);
#endif
	PPLOGI("decode_thread started");

	apAudioPlayer* player = (apAudioPlayer*)ptr;
	player->thread_impl();

	PPLOGI("decode_thread exited");
#ifdef __ANDROID__
    g_JavaVM->DetachCurrentThread();
#endif
    return NULL;
}

void apAudioPlayer::thread_impl()
{
	PPLOGI("apAudioPlayer start to play audio");

	/* initialize packet, set data to NULL, let the demuxer fill it */
	AVPacket pkt;
	int ret;

    av_init_packet(&pkt);
    pkt.data = NULL;
    pkt.size = 0;
	
	while (!m_stopping) {
		if (m_seeking) {
			seek_l();
			m_seeking = false;
		}

		if (m_eof) {
			if (m_audio_render->get_latency() < 1024) {
				m_audio_clock_msec = 0;
			
				if (m_on_eof) {
					m_on_eof();
				}

				if (m_loop) {
					ret = avformat_seek_file(m_fmt_ctx, m_audio_stream_idx, 0, 0, 0, AVSEEK_FLAG_BACKWARD);
					if (ret >= 0) {
						PPLOGI("seek back to 0(loop)");
						m_eof = false;
						continue;
					}

					PPLOGE("failed to seek to 0");
				}

				if (m_on_complete) {
					m_on_complete();
				}

				break;
			}
			else {
				usleep(1e5);
				continue;
			}
		}

		ret = av_read_frame(m_fmt_ctx, &pkt);
		if (ret < 0) {
			PPLOGI("meet end of file: %d", ret);
			m_eof = true;
			continue;
		}

		AVPacket orig_pkt = pkt;
		int got_frame = 0;
		bool first_pkt_data = true;
		do {
			// ape audio Ì×Âí¸Ë ret=0 even got_frame=1
			// seq like: 0,0,0,...,167060(full pkt size is 167060)
			ret = decode_packet(&pkt, &got_frame, 0);
			if (ret < 0)
				break;

			if (got_frame && m_audio_frame->linesize[0] != 0) {
				void* audio_buffer = NULL;
				uint32_t audio_buffer_size = 0;

				if (m_swr != NULL) {
					const int dst_channels = m_out_channels;
					const int dst_sample_byte = 2; // S16: 2 bytes per sample

					int32_t sampleInCount = m_audio_frame->nb_samples;
					PPLOGD("sampleInCount: %d", sampleInCount);
					int sampleOutCount = (int)av_rescale_rnd(
						swr_get_delay(m_swr, m_audio_dec_ctx->sample_rate) + sampleInCount,
						m_out_sample_rate/*dst rate*/,
						m_audio_dec_ctx->sample_rate/*src rate*/,
						AV_ROUND_UP);

					int sampleCountOutput = swr_convert(m_swr,
						(uint8_t**)(&mSamples), sampleOutCount,
						(const uint8_t**)(m_audio_frame->extended_data), sampleInCount);
					if (sampleCountOutput < 0) {
						PPLOGE("Audio convert sampleformat(%d) failed, ret %d", 
							m_audio_dec_ctx->sample_fmt, sampleCountOutput);
						break;
					}
					else if (sampleCountOutput == 0) {
						PPLOGW("no audio data in the frame");
					}
					else {
						audio_buffer = mSamples;
						audio_buffer_size = sampleCountOutput * dst_channels * dst_sample_byte;
						PPLOGD("swr output: sample: %d, size: %d", 
							sampleCountOutput, audio_buffer_size);
					}
				}
				else {
					// no swr
					audio_buffer = m_audio_frame->data[0];
					// 2015.1.28 guoliangma fix noisy audio play problem 
					// some clip linesize is bigger than actual data size
					// e.g. linesize[0] = 2048 and nb_samples = 502
					audio_buffer_size = 
						m_audio_frame->nb_samples * m_audio_frame->channels * 2 /*s16*/;
				}

				while (!m_stopping) {
						if (m_audio_render->free_size() >= (int)audio_buffer_size)
							break;
		
						usleep(1000 * 5);// 5 msec
				}
				
				int written;
				written = m_audio_render->write_data((const char *)audio_buffer, audio_buffer_size);
				if (written != (int)audio_buffer_size) // may occur when stopping
					PPLOGW("fifo overflow(osles) %d -> %d", audio_buffer_size, written);

				if (first_pkt_data) {
					m_audio_clock_msec = (int)(pkt.pts * av_q2d(m_audio_stream->time_base) * 1000);
					//PPLOGI("m_audio_clock_msec %d", m_audio_clock_msec);
					first_pkt_data = false;
				}
				else {
					m_audio_clock_msec += (m_audio_frame->nb_samples * 1000 / m_audio_dec_ctx->sample_rate);
					//PPLOGI("m_audio_clock_msec(add) %d(%d)", m_audio_clock_msec, m_audio_frame->nb_samples);
				}
				if (m_on_pcm) {
					m_on_pcm((const char *)audio_buffer, audio_buffer_size, m_audio_clock_msec);
				}

				/* If we use the new API with reference counting, we own the data and need
				 * to de-reference it when we don't use it anymore */
				av_frame_unref(m_audio_frame);
			}

			pkt.data += ret;
			pkt.size -= ret;
		} while (pkt.size > 0);
		av_free_packet(&orig_pkt);
	}
}

int apAudioPlayer::decode_packet(AVPacket *pkt, int *got_frame, int cached)
{
    int ret = 0;
    int decoded = pkt->size;

    *got_frame = 0;

    if (pkt->stream_index == m_audio_stream_idx) {
        /* decode audio frame */
        ret = avcodec_decode_audio4(m_audio_dec_ctx, m_audio_frame, got_frame, pkt);
        if (ret < 0) {
            PPLOGE("Error decoding audio frame (%d)", ret);
            return ret;
        }

		/* Some audio decoders decode only part of the packet, and have to be
         * called again with the remainder of the packet data.
         * Sample: fate-suite/lossless-audio/luckynight-partial.shn
         * Also, some decoders might over-read the packet. */
        decoded = FFMIN(ret, pkt->size);
    }
	else {
		// omit other packet
	}

    return decoded;
}

bool apAudioPlayer::init_swr()
{
	if (m_swr) {
		swr_free(&m_swr);
		m_swr = NULL;
	}

	m_swr = swr_alloc_set_opts(m_swr,
		m_out_channels > 1 ? AV_CH_LAYOUT_STEREO : AV_CH_LAYOUT_MONO,
		AV_SAMPLE_FMT_S16/*hard code now*/,
		m_out_sample_rate,
		m_audio_dec_ctx->channel_layout,
		m_audio_dec_ctx->sample_fmt,
		m_audio_dec_ctx->sample_rate,
		0, 0);
	int ret = swr_init(m_swr);
	if (ret < 0 || m_swr == NULL) {
		PPLOGE("swr_init failed: %d %p", ret, m_swr);
		return false;
	}

	return true;
}

int64_t get_channel_layout(uint64_t channel_layout, int channels)
{
	if (channel_layout != 0)
		return channel_layout;

	PPLOGI("channel_layout NOT set, try to fill it");

	// 2015.1.19 guoliangma mark(it's very important)
	// fix channel_layout param is not accurate for some video.

	uint64_t out_channelLayout = AV_CH_LAYOUT_MONO;// default layout

	switch (channels) {
	case 1:
		out_channelLayout = AV_CH_LAYOUT_MONO;
		break;
	case 2:
		out_channelLayout = AV_CH_LAYOUT_STEREO;
		break;
	case 3:
		out_channelLayout = AV_CH_LAYOUT_2POINT1;
		break;
	case 4:
		out_channelLayout = AV_CH_LAYOUT_3POINT1;
		break;
	case 5:
		out_channelLayout = AV_CH_LAYOUT_4POINT1;
		break;
	case 6:
		out_channelLayout = AV_CH_LAYOUT_5POINT1;
		break;
	case 7:
		out_channelLayout = AV_CH_LAYOUT_6POINT1;
		break;
	case 8:
		out_channelLayout = AV_CH_LAYOUT_7POINT1;
		break;
	default:
		PPLOGE("channels is invalid: %d", channels);
		break;
	}

	return out_channelLayout;
}
