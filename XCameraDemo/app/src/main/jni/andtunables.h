#ifndef AND_TUNABLES_H
#define AND_TUNABLES_H

#define UINT_UNSET	999
#define INT_UNSET	-1

/* tunables_load_defaults()
 * PURPOSE
 * Load the default values into the global settings variables.
 */
void tunables_load_defaults();

void install_str_setting(const char* p_value, const char** p_storage);

/* Configurable preferences */
/* Booleans */
extern int tunable_cabac;
extern int tunable_open_gop;
extern int tunable_repeat_headers;
extern int tunable_repeat_annexb;
extern int tunable_intra_refresh;

/* Integer/numeric defines */
extern unsigned int tunable_width;
extern unsigned int tunable_height;
extern unsigned int tunable_fps_num;
extern unsigned int tunable_fps_den;
extern unsigned int tunable_bitrate; // unit: k bps
extern unsigned int tunable_vbv_buffer_size;
extern unsigned int tunable_vbv_max_bitrate;
extern unsigned int tunable_crf_constant;
extern unsigned int tunable_crf_constant_max;
extern unsigned int tunable_frame_reference;
extern unsigned int tunable_bframe;
extern unsigned int tunable_keyint_max;
extern unsigned int tunable_threads;
extern unsigned int tunable_qp_constant;
extern unsigned int tunable_quality;
extern unsigned int tunable_rotate;

/* String defines */
extern const char* tunable_preset;	// "ultrafast", "fast", ...
extern const char* tunable_profile; // "baseline", "high", ...
extern const char* tunable_tune;	// "zerolatency", "film", "stillimage", ...
extern const char* tunable_gop_size;// "10", "25", "1x", "2x", ...

#endif //AND_TUNABLES_H

