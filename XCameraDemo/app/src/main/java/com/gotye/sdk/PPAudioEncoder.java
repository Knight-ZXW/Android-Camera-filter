package com.gotye.sdk;

import android.content.Context;

import com.gotye.bibo.util.LogUtil;


public class PPAudioEncoder implements AudioEncoderInterface {
	private final static String TAG = "PPAudioEncoder";

	private AudioEncoderInterface mEncoder = null;
	private EncodeMode mEncodeMode = EncodeMode.SYSTEM;

	public enum EncodeMode {
		SYSTEM,
		FDK_AAC {
			@Override
            public AudioEncoderInterface newInstance(PPAudioEncoder enc) {
				LogUtil.info(TAG, "audio_encoder_select fdk-aac");
                return new EasyAudioEncoder();
            }
		};

		public AudioEncoderInterface newInstance(PPAudioEncoder enc) {
        	LogUtil.info(TAG, "audio_encoder_select system");
            return new SysAudioEncoder();
        }
	}

	public PPAudioEncoder(Context ctx) {
		this(EncodeMode.SYSTEM);
	}

	public PPAudioEncoder(EncodeMode mode) {
		mEncodeMode = mode;
		mEncoder = mEncodeMode.newInstance(this);
	}

	public EncodeMode getEncodeMode() {
		return mEncodeMode;
	}
	
	@Override
	public boolean open(int sample_rate, int channels, int bitrate) {
		// TODO Auto-generated method stub
		if (mEncoder != null) {
            return mEncoder.open(sample_rate, channels, bitrate);
        }
		
		return false;
	}

	@Override
	public void setOnDataListener(OnAudioDataListener listener) {
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
	public boolean addAudioData(byte[] data, int start, int byteCount, long timestamp) {
		// TODO Auto-generated method stub
		if (mEncoder != null)
			return mEncoder.addAudioData(data, start, byteCount, timestamp);
		
		return false;
	}

    @Override
	public void close() {
		// TODO Auto-generated method stub
		LogUtil.info(TAG, "close()");
		
		if (mEncoder != null)
			mEncoder.close();
	}

}
