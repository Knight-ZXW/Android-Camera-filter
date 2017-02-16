#ifndef AND_DEFS_H
#define AND_DEFS_H

#include <stdint.h> // for uint64_t, ...

#undef USE_NATIVE_IO		//for java native buffer

#define AND_ENCODER_VERSION "1.05"
#undef ENC_USE_FFMPEG
#define USE_LIBYUV

// 1.02 support BGR24LE as input
// 1.03
//		1) change encoder profile "low" and "high", default is "high", I,P frames
//		2) opaque data change from 8 bytes to 16 bytes, support identify encoded frame type(I, P)
//		3) split code to IOSCodec lib
// 1.04 
//		1) change code for stable
//		2) GetFPS() return value change from int to double
//		3) update libx264 version from "135" to "140"
//		4) add "quality" option in EasyEncoderOpen() "enc_str"
//			range from 1(worst) to 100(best)
//		5) add "gop_size" option in EasyEncoderOpen() "enc_str"
//			usage:
//			a) integer	e.g. 15 (gop size set to 15)
//			b) time		e.g. 2x (gop size is 2 times as fps)
// 1.05
//		1) add "smartquality" and "nolatency" encoder profile, ONLY for 640x480 resolution
//		2) default "quality" value is 50
//		3) tune "crf" calc function
//		4) change add() function, if no h264 data out, opaque data will be discarded
// 1.06
//		1) "nolatency" encoder profile support resolution other than "640x480"
//		2) add "static" encoder profile
// 1.07
//		1) add mutex in add() and get() function
//		2) "smartquality" i_keyint_max change from "5x fps" to "2x fps"

#define AND_DECODER_VERSION "1.03"
// 1.02
//		1) change code for stable
//		2) GetFPS() return value change from int to double
//		3) update FFMPEG version from 1.2.3 to 2.0.2,
//			new version integrated with with libx264 libfdk-aac
// 1.03
//		1) support 1280x720 decode
//		2) change add() function, if no h264 data out, opaque data will be discarded
// 1.04
//		1) add mutex in add() and get() function
//		2) change AndCodec_EasyDecoderAdd() implement
//			if no picture decoded out, opaque data will always be added to queue.
//			this opaque data may be fetch later
//			opaque queue will do a clean job when I frame was decoded out

#define INVALID_HANDLE	-1

#define OPAQUE_DATA_LEN	16

#define STR_MAX_LEN		256
#define QUEUE_SIZE		128
#define FIFO_SIZE		1048576
#define STRING_BUF_LEN	1024

enum FrameType {
	FRAME_TYPE_I = 1,
	FRAME_TYPE_P,
	FRAME_TYPE_B
};

// total len: 16 byte
typedef struct OpaqueData_t{
	// time stamp usec
	uint64_t ll_d0;

	//for Frame type: 0-Unknown frame, 1-I frame, 2-P frame, 3-B frame
	uint8_t	uchar_d0;

	//reserved
	uint8_t	uchar_d1;
	uint8_t	uchar_d2;
	uint8_t	uchar_d3;
	uint8_t	uchar_d4;
	uint8_t	uchar_d5;
	uint8_t	uchar_d6;
	uint8_t uchar_d7;
} OpaqueData; 

enum AndPixFormat{
	AND_PIXEL_FMT_RGB565 = 0, // change from BGR565BE to RGB565LE since v1.01
	AND_PIXEL_FMT_BGR24,  // change from RGB24 to BGR24 since v1.02
	AND_PIXEL_FMT_NV21,
	AND_PIXEL_FMT_YUV420P,
};

#endif /* AND_DEFS_H */

