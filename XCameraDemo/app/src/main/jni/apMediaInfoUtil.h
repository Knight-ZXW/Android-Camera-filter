#ifndef _AP_MEDIA_INFO_UTIL_H_
#define _AP_MEDIA_INFO_UTIL_H_

#include "stdint.h"
#include "utils.h"

#define MAX_CHANNEL_CNT 8

typedef struct MediaInfo {
	char* format_name;
	int32_t duration_ms; //in millisecond 
	long long size_byte; //in byte
	int32_t width;
	int32_t height;
	double frame_rate;
	int32_t	bitrate;

	char* videocodec_name;
	int32_t audio_channels;
	int32_t video_channels;
	int32_t subtitle_channels;
	
	//we do not use dynamic mem alloc, for easy mem management.
	//use the ISO 639 language code (3 letters)
	//for detail, refer to http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
	//chinese language needs to map this logic:
	//	chi:chinese
	//	zho:chinese
	//	chs:simplified chinese
	//	cht:tranditional chinese
	int audio_streamIndexs[MAX_CHANNEL_CNT];
	char* audiocodec_names[MAX_CHANNEL_CNT];
	char* audiocodec_profiles[MAX_CHANNEL_CNT];
	char* audio_languages[MAX_CHANNEL_CNT];
	char* audio_titles[MAX_CHANNEL_CNT];
	
	int subtitle_streamIndexs[MAX_CHANNEL_CNT];
	char* subtitlecodec_names[MAX_CHANNEL_CNT];
	char* subtitle_languages[MAX_CHANNEL_CNT];
	char* subtitle_titles[MAX_CHANNEL_CNT];

	//all channels count, include audio / video / subtitle
	int32_t channels;

    MediaInfo() :
		format_name(NULL),
        duration_ms(0),
        size_byte(0),
        width(0),
        height(0),
		frame_rate(0.0),
		bitrate(0),

        videocodec_name(NULL),

        audio_channels(0),
        video_channels(0),
		subtitle_channels(0),

        channels(0) {
			for (int i=0 ; i<MAX_CHANNEL_CNT ; i++) {
				audio_streamIndexs[i]		= -1;
				audiocodec_names[i]			= NULL;
				audiocodec_profiles[i]		= NULL;
				audio_languages[i]			= NULL;
				audio_titles[i]				= NULL;

				subtitle_streamIndexs[i]	= -1;
				subtitlecodec_names[i]		= NULL;
				subtitle_languages[i]		= NULL;
				subtitle_titles[i]			= NULL;
			}
	}

	~MediaInfo() {
		SAFE_FREE(format_name);
		SAFE_FREE(videocodec_name);

		for (int i=0 ; i<MAX_CHANNEL_CNT ; i++) {
			SAFE_FREE(audiocodec_names[i]);
			SAFE_FREE(audiocodec_profiles[i]);
			SAFE_FREE(audio_languages[i]);
			SAFE_FREE(audio_titles[i]);

			SAFE_FREE(subtitlecodec_names[i]);
			SAFE_FREE(subtitle_languages[i]);
			SAFE_FREE(subtitle_titles[i]);
		}
	}
} MediaInfo;

extern "C" bool getMediaInfo(const char* url, MediaInfo* info);

#endif // _AP_MEDIA_INFO_UTIL_H_