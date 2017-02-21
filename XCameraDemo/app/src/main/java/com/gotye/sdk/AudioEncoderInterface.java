package com.gotye.sdk;

public interface AudioEncoderInterface {
	public abstract boolean open(int sample_rate, int channels, int bitrate);
	
	public abstract void setOnDataListener(OnAudioDataListener listener);
	
	public abstract void setOnNotifyListener(OnNotifyListener listener);
	
	public abstract boolean addAudioData(byte[] data, int start, int byteCount, long timestamp);


	public abstract void close();

	public interface OnAudioDataListener {
		void OnAudioData(byte[] data, int start, int byteCount, long timestamp/*msec*/);

		void OnLATMheader(byte[] data, int start, int byteCount);
	}
	
	public interface OnNotifyListener {
		boolean onInfo(PPEncoder enc, int what, int extra);
		
		boolean onError(PPEncoder enc, int what, int extra);
		
		void onComplete(PPEncoder enc);
	}
}
