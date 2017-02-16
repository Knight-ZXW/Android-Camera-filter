#ifndef CODEC_DEF_H
#define CODEC_DEF_H

// H.264 NAL type
enum H264NALTYPE{
	H264NT_NAL = 0,
	H264NT_SLICE,		// ��IDRͼ��ı�������
	H264NT_SLICE_DPA,	//�����������ݷָ��A
	H264NT_SLICE_DPB,	//�����������ݷָ��B
	H264NT_SLICE_DPC,	//�����������ݷָ��C
	H264NT_SLICE_IDR,	// IDRͼ��ı�������
	H264NT_SEI,
	H264NT_SPS,
	H264NT_PPS,
};

#endif //CODEC_DEF_H

