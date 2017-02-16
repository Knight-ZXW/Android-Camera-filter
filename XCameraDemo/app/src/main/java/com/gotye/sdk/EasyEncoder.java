package com.gotye.sdk;

import android.content.Context;

import com.gotye.bibo.util.ColorFormat;
import com.gotye.bibo.util.LogUtil;
import com.gotye.bibo.util.Util;

import java.util.Locale;

public class EasyEncoder implements EncoderInterface {
	public static final String TAG = "EasyEncoder";
	
	public static final int CLIP_FPS				= 15;
	public static final int INVALID_HANDLE 		    = -1;
	
	public static final int ENC_IN_FMT_BGR24		= 1;
	public static final int ENC_IN_FMT_NV21		    = 2;
	public static final int ENC_IN_FMT_YUV420P	    = 3;

	private long mHandle = INVALID_HANDLE;
	private PPEncoder mEncoder;
	private String mEncOption;
	private int mWidth, mHeight;
	private int mInFmt;
	private int mFrameRate;
    private int mBitRate;
	private long mEncFrameCnt;
    private long mOutputFrameCnt;
	private byte[] yuv420;
    private byte[] headers;
	private byte[] mH264;
	private byte[] mOutOpaque;
	private OnDataListener mOnDataListener;

    private long mMuxerHandle = INVALID_HANDLE;

    // stat
	private int mAvgPixelConvertMsec;
	
	public EasyEncoder(Context ctx, PPEncoder enc) {
		mEncoder = enc;
	}
	
	@Override
	public void setEncoderOption(String option) {
		// TODO Auto-generated method stub
		mEncOption = option;

        if (mHandle != INVALID_HANDLE && option.contains("rotate=")) {
            int pos1, pos2;
            pos1 = option.indexOf("rotate=");
            pos2 = option.indexOf(",", pos1);
            if (pos2 == -1)
                pos2 = option.length();
            int rotate = Integer.valueOf(option.substring(pos1 + 7, pos2));
            EasyEncoderSetRotate(rotate);
        }
	}
	
	@Override
	public boolean open(int width, int height, int in_fmt, int framerate, int bitrate) {
		mWidth		= width;
		mHeight		= height;
		mInFmt		= in_fmt;
		mFrameRate	= framerate;
        mBitRate    = bitrate;
		
		yuv420 = new byte[width * height * 3 / 2];
        headers = new byte[4096]; // x264 contains SEI in header, so maybe very big
		mH264 = new byte[width * height * 3 / 2];
		mOutOpaque = new byte[16];
		mEncFrameCnt = mOutputFrameCnt = 0L;
        mAvgPixelConvertMsec = 0;
		
		if (mInFmt != Util.PREVIEW_PIX_FORMAT_YV12 && mInFmt != Util.PREVIEW_PIX_FORMAT_NV21) {
			LogUtil.error(TAG, "Java: unsupported input format");
			return false;
		}

        String option = String.format("fps_num=%d,fps_den=1,bitrate=%d,gop_size=%dx",
				mFrameRate, mBitRate, 5);
		if (mEncOption != null) {
			option += ",";
			option += mEncOption;
		}
		boolean success = EasyEncoderOpen(width, height, ENC_IN_FMT_YUV420P,
                "nolatency", option);

        if (mMuxerHandle != INVALID_HANDLE)
            EasyEncoderSetMuxer(mMuxerHandle);

        int len = EasyEncoderHeaders(headers);
        if (len < 0) {
            LogUtil.error(TAG, "failed to get sps and pps");
            return false;
        }

        if (mOnDataListener != null)
            mOnDataListener.onSpsPps(mEncoder, headers, 0, len);

        return success;
	}

	@Override
	public void setOnDataListener(OnDataListener listener) {
		// TODO Auto-generated method stub
		mOnDataListener = listener;
	}

	@Override
	public void setOnNotifyListener(OnNotifyListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean addFrame(byte[] picdata, long timestamp/*usec*/) {
		LogUtil.debug(TAG, String.format("Java: addFrame() in_len %d", picdata.length));

		long startMsec = System.currentTimeMillis();

		if (mInFmt == Util.PREVIEW_PIX_FORMAT_YV12)
			ColorFormat.swapYV12toI420(picdata, yuv420, mWidth, mHeight);
		else
			ColorFormat.NV21toYUV420Planar(picdata, yuv420, mWidth, mHeight);

		int used = (int)(System.currentTimeMillis() - startMsec);
		mAvgPixelConvertMsec = (mAvgPixelConvertMsec * 4 + used) / 5;

		byte[] opaque = LongToOpaque(timestamp);
        int retAdd = EasyEncoderAdd(yuv420, opaque);
		if (retAdd < 0) {
			LogUtil.error(TAG, "failed to EasyEncoderAdd()");
			return false;
		}

        mEncFrameCnt++;

		if (mMuxerHandle == INVALID_HANDLE) {
            while (true) {
                int ret = EasyEncoderGet(mH264, mOutOpaque);
                if (ret < 0) {
                    LogUtil.error(TAG, "failed to EasyEncoderGet()");
                    return false;
                } else if (ret == 0) {
                    break;
                } else {
                    if (mOnDataListener != null) {
                        long out_timestamp = OpaqueToTimestamp(mOutOpaque);
                        int frame_type = OpaqueToType(mOutOpaque);

                        char cFrameType;
                        switch (frame_type) {
                            case 0:
                                cFrameType = 'U';
                                break;
                            case 1:
                                cFrameType = 'I';
                                break;
                            case 2:
                                cFrameType = 'P';
                                break;
                            case 3:
                                cFrameType = 'B';
                                break;
                            default:
                                cFrameType = 'E';
                                break;
                        }

                        //LogUtil.debug(TAG, String.format("Java: get size %d, frame #%d(%d usec), frame_type: %c",
                        //        ret, mOutputFrameCnt, out_timestamp, cFrameType));
                        mOnDataListener.onData(mEncoder, mH264, 0, ret, out_timestamp);
                    }

                    mOutputFrameCnt++;
                }
            }
        }
		
		return true;
	}

	@Override
	public void setMuxer(long muxer) {
        LogUtil.info(TAG, "Java: setMuxer " + muxer);
        mMuxerHandle = muxer;
	}
	
	@Override
	public void close() {
		EasyEncoderClose();

		LogUtil.info(TAG, String.format(Locale.US,
                "Java: encode %d frames, output %d frames, average pixel convert time %d msec",
                mEncFrameCnt, mOutputFrameCnt, mAvgPixelConvertMsec));
	}
	
	// byte to long
	private static long ByteToLong(byte[] b) {
		long s = 0;
		long s0 = b[0] & 0xff;// MSB
		long s1 = b[1] & 0xff;
		long s2 = b[2] & 0xff;
		long s3 = b[3] & 0xff;
		long s4 = b[4] & 0xff;// MSB
		long s5 = b[5] & 0xff;
		long s6 = b[6] & 0xff;
		long s7 = b[7] & 0xff;

		// s0 unchange
		s1 <<= 8;
		s2 <<= 16;
		s3 <<= 24;
		s4 <<= 8 * 4;
		s5 <<= 8 * 5;
		s6 <<= 8 * 6;
		s7 <<= 8 * 7;
		s = s0 | s1 | s2 | s3 | s4 | s5 | s6 | s7;
		return s;
	}

    // opaque(16 byte) to timestamp
    private static long OpaqueToTimestamp(byte[] opaque) {
        return ByteToLong(opaque);
    }

	// opaque(16 byte) to frame type 
	private static int OpaqueToType(byte[] b) { 
        int type = b[8];
        return type; 
    }

    // long to byte
    private static byte[] LongToByte(long number) {
        long temp = number;
        byte[] b = new byte[8];
        for (int i = 0; i < b.length; i++) {
            b[i] = new Long(temp & 0xff).byteValue();
            temp = temp >> 8; // right shift 8
        }
        return b;
    }

    // long to opaque(16 byte)
    private static byte[] LongToOpaque(long timestamp) {
        long temp = timestamp;
        byte[] b = new byte[16]; //0-7 time stamp, 8-15 customized
        for (int i = 0; i < 8; i++) {
            b[i] = new Long(temp & 0xff).byteValue();
            temp = temp >> 8; // right shift 8
        }
        return b;
    }
	
	// for encoder
	private native boolean EasyEncoderOpen(int w, int h, int in_fmt,
										   String profile, String enc_str);
	
	private native int EasyEncoderAdd(byte[] picdata, byte[] opaque);
	
	private native int EasyEncoderGet(byte[] encdata, byte[] opaque);

    private native int EasyEncoderHeaders(byte[] headers);

	private native int EasyEncoderSetRotate(int rotate);
	
	private native void EasyEncoderClose();
	
	private native double EasyEncoderGetFPS();

    private native void EasyEncoderSetMuxer(long muxer);
	
    // Load the .so
    static {
    	System.loadLibrary("andcodec");
    }
    
}
