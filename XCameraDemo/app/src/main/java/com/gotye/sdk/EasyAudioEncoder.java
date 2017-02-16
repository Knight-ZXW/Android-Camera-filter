package com.gotye.sdk;

import android.content.Context;

import com.gotye.bibo.util.Constants;
import com.gotye.bibo.util.LogUtil;


public class EasyAudioEncoder implements AudioEncoderInterface {
    private static final String TAG = "EasyAudioEncoder";

    private static final int INVALID_HANDLE = -1;
    private static final int BUF_SIZE       = 48 * 2 * 50; // 48 khz, 2 channel for 50 msec

	private long mHandle = INVALID_HANDLE;
	private PPAudioEncoder mEncoder;
	private OnAudioDataListener mOnDataListener;
    private byte[] mPcmData = null;
    private int mPcmDataOffset = 0;
	private byte[] mEncodedData = null;
    private int mBufSize; // one encode sample size
    private int mOneSecDataSize;
	private long mTotalDataSize;

    private long mMuxerHandle = INVALID_HANDLE;

	public EasyAudioEncoder(Context ctx, PPAudioEncoder enc) {
		mEncoder = enc;
	}
	
	@Override
	public boolean open(int sample_rate, int channels, int bitrate, boolean bAddAdtsHeader) {
        mPcmData = new byte[BUF_SIZE];
		mEncodedData = new byte[BUF_SIZE];
		boolean ret = EasyAudioEncoderOpen(sample_rate, channels, bitrate, bAddAdtsHeader);
        mBufSize = EasyAudioEncoderGetBufSize();
        mOneSecDataSize = sample_rate * channels * 2;
		mTotalDataSize = 0L;
        return ret;
	}

	@Override
	public void setOnDataListener(OnAudioDataListener listener) {
		// TODO Auto-generated method stub
		mOnDataListener = listener;
	}

	@Override
	public void setOnNotifyListener(OnNotifyListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean addAudioData(byte[] data, int start, int byteCount, long timestamp/*useless*/) {
        if (Constants.VERBOSE) LogUtil.debug(TAG, "addAudioData " + byteCount);

        int left = byteCount;
        int offset = 0;
        int outsize;
        while (mPcmDataOffset + left >= mBufSize) {
            int write = mBufSize;
            if (mPcmDataOffset > 0)
                write -= mPcmDataOffset;
            System.arraycopy(data, start + offset, mPcmData, mPcmDataOffset, write);
            if (mPcmDataOffset > 0)
                mPcmDataOffset = 0;
            mTotalDataSize += mBufSize;
            outsize = EasyAudioEncoderAdd(mPcmData, 0, mBufSize, mEncodedData);
            if (outsize < 0) {
                return false;
            }
            else if (outsize > 0) {
                if (mOnDataListener != null) {
                    long time_usec = mTotalDataSize * 1000000 / mOneSecDataSize;
                    mOnDataListener.OnAudioData(mEncodedData, 0, outsize, time_usec);
                }
            }
            else {
                LogUtil.warn(TAG, "no audio output");
            }

            left -= write;
            offset += write;
        }

        if (offset == 0) {
            // just gather for one sample size to encode
            // assume wont overflow
            // need fix
            System.arraycopy(data, start + offset, mPcmData, mPcmDataOffset, byteCount);
            mPcmDataOffset += byteCount;
        }
        else if (left > 0) {
            System.arraycopy(data, offset, mPcmData, 0, left);
            mPcmDataOffset = left;
        }

        return true;
		/*int outsize = EasyAudioEncoderAdd(data, start, byteCount, mEncodedData);
		if (outsize < 0) {
			return false;
		}
		else if (outsize > 0 && mOnDataListener != null) {
			mOnDataListener.OnAudioData(mEncodedData, 0, outsize, timestamp);
		}

		return true;*/
	}

	@Override
	public void setMuxer(long muxer) {
        LogUtil.info(TAG, "Java: setMuxer " + muxer);
        mMuxerHandle = muxer;
	}
	
	@Override
	public void close() {
		EasyAudioEncoderClose();
	}

    private void onDataCallback(byte[] encoded_data, long timestamp) {
        if (mOnDataListener != null) {
            mOnDataListener.OnAudioData(mEncodedData, 0, encoded_data.length, timestamp);
        }
    }

	// for encoder
	private native boolean EasyAudioEncoderOpen(int sample_rate, int channels, int bitrate,
												boolean bAddAdtsHeader);
	
	private native int EasyAudioEncoderAdd(byte[] pcm_data, int start, int byteCount, byte[] output_data);

	private native void EasyAudioEncoderClose();

    private native void EasyAudioEncoderSetMuxer(long muxer);

    private native int EasyAudioEncoderGetBufSize();
	
    // Load the .so
    static {
    	System.loadLibrary("andcodec");
    }
    
}
