#include "apMediaInfoUtil.h"
#define LOG_TAG "MediaInfoUtil"
#include "pplog.h"
#include <stdio.h>
#include <stdlib.h>

#ifdef _MSC_VER
#define my_strdup _strdup
#else
#define my_strdup strdup
#endif

extern "C"
{
#ifdef __cplusplus
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

extern "C" bool getMediaInfo(const char* url, MediaInfo* info)
{
	PPLOGI("player op getMediaDetailInfo() %s", url);

	av_register_all();

    if (url == NULL || info == NULL)
		return false;

    info->size_byte = 0;

    AVFormatContext* movieFile = avformat_alloc_context();
    PPLOGI("before avformat_open_input() %s", url);
    if (0 != avformat_open_input(&movieFile, url, NULL, NULL)) {
		PPLOGE("failed to avformat_open_input: %s", url);
		return false;
	}
    
	if(avformat_find_stream_info(movieFile, NULL) < 0) {
		PPLOGE("failed to avformat_find_stream_info: %s", url);
		return false;
	}

	info->duration_ms	= (int32_t)(movieFile->duration * 1000 / AV_TIME_BASE);
	info->format_name	= my_strdup(movieFile->iformat->name);
	info->bitrate		= movieFile->bit_rate;

	uint32_t streamsCount = movieFile->nb_streams;
	PPLOGI("stream count: %d", streamsCount);

	info->channels = streamsCount;

	info->audio_channels	= 0;
	info->video_channels	= 0;
	info->subtitle_channels = 0;

	for (int32_t i = 0; i < (int)streamsCount; i++) {
		if (movieFile->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
			info->video_channels++;
			AVStream* stream = movieFile->streams[i];
			if (stream == NULL) {
				PPLOGE("stream is NULL");
				continue;
			}
			AVCodecContext* codec_ctx = stream->codec;
			if (codec_ctx == NULL) {
				PPLOGE("codec_ctx is NULL");
				continue;
			}
			info->width			= codec_ctx->width;
			info->height		= codec_ctx->height;
			info->frame_rate	= av_q2d(stream->avg_frame_rate);

			AVCodec* codec = avcodec_find_decoder(codec_ctx->codec_id);
			if (codec == NULL) {
				PPLOGE("avcodec_find_decoder() video %d(%s) failed",
					codec_ctx->codec_id, avcodec_get_name(codec_ctx->codec_id));
				continue;
			}

			info->videocodec_name = my_strdup(avcodec_get_name(codec_ctx->codec_id));
		}
		else if (movieFile->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
			AVStream* stream = movieFile->streams[i];
			AVCodecContext *codec_ctx = stream->codec;

			int audio_index = info->audio_channels;
			if (audio_index >= MAX_CHANNEL_CNT) {
				PPLOGW("audio channel count exceed MAX_CHANNEL_CNT: %d", audio_index);
				continue;
			}

			if (FF_PROFILE_UNKNOWN != codec_ctx->profile) {
				PPLOGI("audio stream #%d:%d audiocodec_profile(profile) %d", i, audio_index, codec_ctx->profile);

				const AVCodec* codec = NULL;
				if (codec_ctx->codec)
					codec = codec_ctx->codec;
				else
					codec = avcodec_find_decoder(codec_ctx->codec_id);

				if (codec) {
					const char* profile_name = av_get_profile_name(codec, codec_ctx->profile);
					if (profile_name) {
						info->audiocodec_profiles[audio_index] = my_strdup(profile_name);
						PPLOGI("audio stream #%d:%d audiocodec_profile(profile) %d(%s)",
							i, audio_index, codec_ctx->profile, info->audiocodec_profiles[audio_index]);
					}
				}
				else {
					PPLOGW("avcodec_find_decoder audio %d(%s) failed",
						codec_ctx->codec_id, avcodec_get_name(codec_ctx->codec_id));
				}
			}
			if (info->audiocodec_profiles[audio_index] == NULL && codec_ctx->codec_tag) {
				char tag_buf[32] = {0};
				av_get_codec_tag_string(tag_buf, sizeof(tag_buf), codec_ctx->codec_tag);
				info->audiocodec_profiles[audio_index] = my_strdup(tag_buf);
				PPLOGI("audio stream #%d:%d audiocodec_profile(tag) %s", 
					i, audio_index, info->audiocodec_profiles[audio_index]);
			}

			info->audio_streamIndexs[audio_index] = i;
			info->audiocodec_names[audio_index] = my_strdup(avcodec_get_name(codec_ctx->codec_id));

			info->audio_channels++;
		}
		else if(movieFile->streams[i]->codec->codec_type == AVMEDIA_TYPE_SUBTITLE) {
			AVStream* stream = movieFile->streams[i];
			int subtitle_index = info->subtitle_channels;
			if (subtitle_index >= MAX_CHANNEL_CNT) {
				PPLOGW("subtitle channel count exceed MAX_CHANNEL_CNT: %d", subtitle_index);
				continue;
			}

			info->subtitle_streamIndexs[subtitle_index] = i;
			info->subtitlecodec_names[subtitle_index] = my_strdup(avcodec_get_name(stream->codec->codec_id));

			info->subtitle_channels++;
		}
	} // end of for() get stream info

	if (movieFile != NULL) {
        // Close stream
        PPLOGI("avformat_close_input()");
        avformat_close_input(&movieFile);
    }

    PPLOGI("File duration: %d msec, size: %lld", info->duration_ms, info->size_byte);
    PPLOGI("width: %d, height: %d", info->width, info->height);
	PPLOGI("frame_rate: %.2f, bitrate: %d bps", info->frame_rate, info->bitrate);
    PPLOGI("format_name: %s", info->format_name != NULL ? info->format_name : "N/A");
	PPLOGI("videocodec_name: %s", info->videocodec_name != NULL ? info->videocodec_name : "N/A");
    PPLOGI("video_channels: %d, audio_channels:%d, subtitle_channels:%d", 
		info->video_channels, info->audio_channels, info->subtitle_channels);

    return true;
}