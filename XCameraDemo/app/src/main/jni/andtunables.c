#include "andtunables.h"
#include "andsysutil.h"

int tunable_cabac;
int tunable_open_gop;
int tunable_repeat_headers;
int tunable_repeat_annexb;
int tunable_intra_refresh;

unsigned int tunable_width;
unsigned int tunable_height;
unsigned int tunable_fps_num;
unsigned int tunable_fps_den;
unsigned int tunable_bitrate;
unsigned int tunable_vbv_buffer_size;
unsigned int tunable_vbv_max_bitrate;
unsigned int tunable_crf_constant;
unsigned int tunable_crf_constant_max;
unsigned int tunable_frame_reference;
unsigned int tunable_bframe;
unsigned int tunable_keyint_max;
unsigned int tunable_threads;
unsigned int tunable_qp_constant;
unsigned int tunable_quality;
unsigned int tunable_rotate;

const char* tunable_preset;	// "ultrafast", "fast", ...
const char* tunable_profile; // ""baseline", "high", ...
const char* tunable_tune;
const char* tunable_gop_size;

void
tunables_load_defaults()
{
	tunable_cabac			= INT_UNSET;
	tunable_open_gop		= INT_UNSET;
	tunable_repeat_headers	= 1;
	tunable_repeat_annexb	= 1;
	tunable_intra_refresh	= 1;

	tunable_width			= 320;
	tunable_height			= 240;
	tunable_fps_num			= 15;
	tunable_fps_den			= 1;
	tunable_bitrate			= 0;// kbps
	tunable_vbv_buffer_size	= 0;
	tunable_vbv_max_bitrate	= 0;
	tunable_crf_constant	= UINT_UNSET;// 21
	tunable_crf_constant_max= UINT_UNSET;// 1.0.4 change from 20 to 24//max must bigger than crf
	tunable_frame_reference	= UINT_UNSET;
	tunable_bframe			= UINT_UNSET;
	tunable_keyint_max		= 10;
	tunable_threads			= 1;
	tunable_quality			= 50;
	tunable_rotate			= 0;

	install_str_setting("ultrafast", &tunable_preset);
	install_str_setting("baseline", &tunable_profile);
	install_str_setting("zerolatency", &tunable_tune);
	install_str_setting("", &tunable_gop_size);
}

void
install_str_setting(const char* p_value, const char** p_storage)
{
	char* p_curr_val = (char*) *p_storage;
	if (p_curr_val != 0)
	{
		and_sysutil_free(p_curr_val);
	}
	if (p_value != 0)
	{
		p_value = and_sysutil_strdup(p_value);
	}
	*p_storage = p_value;
}


