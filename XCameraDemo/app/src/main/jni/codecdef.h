#ifndef CODEC_DEF_H
#define CODEC_DEF_H

// H.264 NAL type
enum H264NALTYPE{
	H264NT_NAL = 0,
	H264NT_SLICE,		// 非IDR图像的编码条带
	H264NT_SLICE_DPA,	//编码条带数据分割块A
	H264NT_SLICE_DPB,	//编码条带数据分割块B
	H264NT_SLICE_DPC,	//编码条带数据分割块C
	H264NT_SLICE_IDR,	// IDR图像的编码条带
	H264NT_SEI,
	H264NT_SPS,
	H264NT_PPS,
};

#endif //CODEC_DEF_H

