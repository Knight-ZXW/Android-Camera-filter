package com.gotye.sdk;

import android.content.Context;

import com.gotye.bibo.util.LogUtil;


public class PPEncoder implements EncoderInterface {
	private final static String TAG = "PPEncoder";
	
	private static Context mContext = null;
	private EncoderInterface mEncoder = null;
	private EncodeMode mEncodeMode = EncodeMode.SYSTEM;
	
	public enum EncodeMode {
		SYSTEM,
		X264 {
			@Override
            public EncoderInterface newInstance(PPEncoder enc) {
				LogUtil.info(TAG, "encoder_select x264");
                return new EasyEncoder(mContext, enc);
            }
		};
		
		public EncoderInterface newInstance(PPEncoder enc) {
        	LogUtil.info(TAG, "encoder_select system");
            return new SystemEncoder(mContext, enc);
        }
	}
	
	public PPEncoder(Context ctx) {
		this(ctx, EncodeMode.SYSTEM);
	}
	
	public PPEncoder(Context ctx, EncodeMode mode) {
		mContext = ctx;
		mEncodeMode = mode;
		mEncoder = mEncodeMode.newInstance(this);
	}
	
	@Override
	public void setEncoderOption(String option) {
		// TODO Auto-generated method stub
		if (mEncoder != null)
			mEncoder.setEncoderOption(option);
	}

	public EncodeMode getEncodeMode() {
		return mEncodeMode;
	}
	
	@Override
	public boolean open(int width, int height, int in_fmt, int framerate, int bitrate) {
		// TODO Auto-generated method stub
		if (mEncoder != null) {
            LogUtil.info(TAG, String.format("Java: open encoder %d x %d, fmt %d, framerate %d, bitrate %d",
                    width, height, in_fmt, framerate, bitrate));
            return mEncoder.open(width, height, in_fmt, framerate, bitrate);
        }
		
		return false;
	}

	@Override
	public void setOnDataListener(OnDataListener listener) {
		// TODO Auto-generated method stub
		if (mEncoder != null)
			mEncoder.setOnDataListener(listener);
	}

	@Override
	public void setOnNotifyListener(OnNotifyListener listener) {
		// TODO Auto-generated method stub
		if (mEncoder != null)
			mEncoder.setOnNotifyListener(listener);
	}

	@Override
	public boolean addFrame(byte[] picdata, long timestamp/* usec */) {
		// TODO Auto-generated method stub
		if (mEncoder != null)
			return mEncoder.addFrame(picdata, timestamp);
		
		return false;
	}

	@Override
	public void setMuxer(long muxer) {
        if (mEncoder != null)
            mEncoder.setMuxer(muxer);
    }

    @Override
	public void close() {
		// TODO Auto-generated method stub
		LogUtil.info(TAG, "close()");
		
		if (mEncoder != null)
			mEncoder.close();
	}

}
